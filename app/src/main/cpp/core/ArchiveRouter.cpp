#include "ArchiveRouter.h"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <optional>
#include <string>
#include <vector>

#include "Common/MyCom.h"
#include "Common/MyString.h"
#include "Windows/FileDir.h"
#include "Windows/PropVariant.h"
#include "Windows/PropVariantConv.h"
#include "7zip/Archive/IArchive.h"
#include "7zip/Common/FileStreams.h"
#include "7zip/IPassword.h"
#include "7zip/PropID.h"
#include "raros.hpp"
#include "dll.hpp"

#ifdef LONG
#undef LONG
#endif

namespace archivekit {
namespace {

extern "C" HRESULT WINAPI CreateObject(const GUID* clsid, const GUID* iid, void** outObject);
extern "C" HRESULT WINAPI GetNumberOfFormats(UInt32* numFormats);
extern "C" HRESULT WINAPI GetHandlerProperty2(UInt32 formatIndex, PROPID propID, PROPVARIANT* value);

constexpr UInt32 kExtractAllItems = static_cast<UInt32>(-1);

enum class BackendKind {
    kNone,
    kSevenZip,
    kUnrar,
};

struct SafeExtractTarget {
    std::string relativePath;
    std::string absolutePath;
};

std::string ToLower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}

bool EndsWith(const std::string& value, const std::string& suffix) {
    return value.size() >= suffix.size() &&
        value.compare(value.size() - suffix.size(), suffix.size(), suffix) == 0;
}

std::optional<int> ParseRarPartNumber(const std::string& path) {
    const auto slash = path.find_last_of("/\\");
    const std::string lower = ToLower(slash == std::string::npos ? path : path.substr(slash + 1));
    const auto partPos = lower.rfind(".part");
    if (partPos == std::string::npos || !EndsWith(lower, ".rar")) {
        return std::nullopt;
    }

    const size_t digitsStart = partPos + 5;
    const size_t digitsEnd = lower.size() - 4;
    if (digitsStart >= digitsEnd) {
        return std::nullopt;
    }

    int value = 0;
    for (size_t i = digitsStart; i < digitsEnd; ++i) {
        const unsigned char ch = static_cast<unsigned char>(lower[i]);
        if (!std::isdigit(ch)) {
            return std::nullopt;
        }
        value = (value * 10) + (ch - '0');
    }
    return value;
}

bool IsLegacyRarLeadVolume(const std::string& path) {
    const auto slash = path.find_last_of("/\\");
    const std::string lower = ToLower(slash == std::string::npos ? path : path.substr(slash + 1));
    return EndsWith(lower, ".rar") && !ParseRarPartNumber(path).has_value();
}

std::string SelectUnrarPrimaryPath(const ArchiveSource& source) {
    for (const auto& path : source.partPaths) {
        if (ParseRarPartNumber(path) == 1) {
            return path;
        }
    }

    for (const auto& path : source.partPaths) {
        if (IsLegacyRarLeadVolume(path)) {
            return path;
        }
    }

    if (!source.primaryPath.empty()) {
        return source.primaryPath;
    }
    if (!source.partPaths.empty()) {
        return source.partPaths.front();
    }
    return {};
}

BackendKind SelectBackend(const ArchiveSource& source) {
    const std::string lower = ToLower(source.primaryPath);
    if (EndsWith(lower, ".rar") || lower.find(".part1.rar") != std::string::npos || EndsWith(lower, ".r00")) {
        return BackendKind::kUnrar;
    }

    if (EndsWith(lower, ".zip") || EndsWith(lower, ".zipx") || EndsWith(lower, ".7z") ||
        EndsWith(lower, ".tar") || EndsWith(lower, ".tgz") || EndsWith(lower, ".gz") ||
        lower.find(".7z.") != std::string::npos || EndsWith(lower, ".z01") || lower.find(".zip.") != std::string::npos) {
        return BackendKind::kSevenZip;
    }

    return BackendKind::kNone;
}

std::wstring Utf8ToWide(const std::string& value) {
    std::wstring wide;
    size_t index = 0;
    while (index < value.size()) {
        const unsigned char ch = static_cast<unsigned char>(value[index]);
        std::uint32_t codePoint = 0;
        size_t width = 0;

        if (ch <= 0x7F) {
            codePoint = ch;
            width = 1;
        } else if ((ch & 0xE0) == 0xC0 && index + 1 < value.size()) {
            const unsigned char ch1 = static_cast<unsigned char>(value[index + 1]);
            if ((ch1 & 0xC0) == 0x80) {
                codePoint = ((ch & 0x1F) << 6) | (ch1 & 0x3F);
                width = 2;
                if (codePoint < 0x80) {
                    width = 0;
                }
            }
        } else if ((ch & 0xF0) == 0xE0 && index + 2 < value.size()) {
            const unsigned char ch1 = static_cast<unsigned char>(value[index + 1]);
            const unsigned char ch2 = static_cast<unsigned char>(value[index + 2]);
            if ((ch1 & 0xC0) == 0x80 && (ch2 & 0xC0) == 0x80) {
                codePoint = ((ch & 0x0F) << 12) | ((ch1 & 0x3F) << 6) | (ch2 & 0x3F);
                width = 3;
                if (codePoint < 0x800) {
                    width = 0;
                }
            }
        } else if ((ch & 0xF8) == 0xF0 && index + 3 < value.size()) {
            const unsigned char ch1 = static_cast<unsigned char>(value[index + 1]);
            const unsigned char ch2 = static_cast<unsigned char>(value[index + 2]);
            const unsigned char ch3 = static_cast<unsigned char>(value[index + 3]);
            if ((ch1 & 0xC0) == 0x80 && (ch2 & 0xC0) == 0x80 && (ch3 & 0xC0) == 0x80) {
                codePoint = ((ch & 0x07) << 18) | ((ch1 & 0x3F) << 12) | ((ch2 & 0x3F) << 6) | (ch3 & 0x3F);
                width = 4;
                if (codePoint < 0x10000 || codePoint > 0x10FFFF) {
                    width = 0;
                }
            }
        }

        if (width == 0 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            wide.push_back(static_cast<wchar_t>(ch));
            ++index;
            continue;
        }

        if constexpr (sizeof(wchar_t) >= 4) {
            wide.push_back(static_cast<wchar_t>(codePoint));
        } else if (codePoint <= 0xFFFF) {
            wide.push_back(static_cast<wchar_t>(codePoint));
        } else {
            codePoint -= 0x10000;
            wide.push_back(static_cast<wchar_t>(0xD800 + (codePoint >> 10)));
            wide.push_back(static_cast<wchar_t>(0xDC00 + (codePoint & 0x3FF)));
        }
        index += width;
    }
    return wide;
}

std::string WideToUtf8(const wchar_t* value) {
    if (value == nullptr) {
        return {};
    }

    std::string narrow;
    while (*value != 0) {
        const auto code = static_cast<std::uint32_t>(*value++);
        if (code <= 0x7F) {
            narrow.push_back(static_cast<char>(code));
        } else if (code <= 0x7FF) {
            narrow.push_back(static_cast<char>(0xC0 | ((code >> 6) & 0x1F)));
            narrow.push_back(static_cast<char>(0x80 | (code & 0x3F)));
        } else if (code <= 0xFFFF) {
            narrow.push_back(static_cast<char>(0xE0 | ((code >> 12) & 0x0F)));
            narrow.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
            narrow.push_back(static_cast<char>(0x80 | (code & 0x3F)));
        } else {
            narrow.push_back(static_cast<char>(0xF0 | ((code >> 18) & 0x07)));
            narrow.push_back(static_cast<char>(0x80 | ((code >> 12) & 0x3F)));
            narrow.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
            narrow.push_back(static_cast<char>(0x80 | (code & 0x3F)));
        }
    }
    return narrow;
}

std::wstring Utf16ToWide(const std::u16string& value) {
    std::wstring wide;
    wide.reserve(value.size());
    for (char16_t ch : value) {
        wide.push_back(static_cast<wchar_t>(ch));
    }
    return wide;
}

std::string Utf16ToUtf8(const std::u16string& value) {
    std::string narrow;
    for (char16_t ch : value) {
        const auto code = static_cast<std::uint32_t>(ch);
        if (code <= 0x7F) {
            narrow.push_back(static_cast<char>(code));
        } else if (code <= 0x7FF) {
            narrow.push_back(static_cast<char>(0xC0 | ((code >> 6) & 0x1F)));
            narrow.push_back(static_cast<char>(0x80 | (code & 0x3F)));
        } else {
            narrow.push_back(static_cast<char>(0xE0 | ((code >> 12) & 0x0F)));
            narrow.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
            narrow.push_back(static_cast<char>(0x80 | (code & 0x3F)));
        }
    }
    return narrow;
}

UString Utf8ToUString(const std::string& value) {
    UString wide;
    for (unsigned char ch : value) {
        wide.Add_Char(static_cast<wchar_t>(ch));
    }
    return wide;
}

std::string UStringToUtf8(const UString& value) {
    return WideToUtf8(value.Ptr());
}

std::string BaseName(const std::string& path) {
    const auto slash = path.find_last_of("/\\");
    return slash == std::string::npos ? path : path.substr(slash + 1);
}

std::string NormalizeSlashes(std::string path) {
    std::replace(path.begin(), path.end(), '\\', '/');
    return path;
}

std::string JoinPath(const std::string& base, const std::string& child) {
    if (base.empty()) {
        return child;
    }
    if (child.empty()) {
        return base;
    }
    if (base.back() == '/') {
        return base + child;
    }
    return base + "/" + child;
}

std::string ParentDir(const std::string& path) {
    const auto slash = path.find_last_of('/');
    return slash == std::string::npos ? std::string() : path.substr(0, slash);
}

std::string DefaultEntryName(const std::string& archivePath) {
    std::string base = BaseName(archivePath);
    const std::string lower = ToLower(base);
    if (EndsWith(lower, ".tar.gz") && base.size() > 3) {
        base.resize(base.size() - 3);
        return base;
    }
    if (EndsWith(lower, ".tgz") && base.size() > 4) {
        return base.substr(0, base.size() - 4) + ".tar";
    }
    const auto dot = base.find_last_of('.');
    if (dot != std::string::npos && dot > 0) {
        base = base.substr(0, dot);
    }
    return base.empty() ? std::string("content") : base;
}

std::optional<std::string> SanitizeRelativePath(std::string path) {
    path = NormalizeSlashes(std::move(path));
    if (path.empty()) {
        return std::nullopt;
    }
    if (path.front() == '/') {
        return std::nullopt;
    }
    if (path.size() >= 2 && std::isalpha(static_cast<unsigned char>(path[0])) && path[1] == ':') {
        return std::nullopt;
    }

    std::vector<std::string> parts;
    size_t start = 0;
    while (start <= path.size()) {
        const size_t end = path.find('/', start);
        const std::string part = path.substr(start, end == std::string::npos ? std::string::npos : end - start);
        if (!part.empty()) {
            if (part == "." || part == "..") {
                return std::nullopt;
            }
            parts.push_back(part);
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }

    if (parts.empty()) {
        return std::nullopt;
    }

    std::string normalized;
    for (size_t index = 0; index < parts.size(); ++index) {
        if (index > 0) {
            normalized.push_back('/');
        }
        normalized += parts[index];
    }
    return normalized;
}

SafeExtractTarget ResolveExtractTarget(
    const std::string& outputDir,
    const std::string& entryPath,
    const std::string& fallbackName
) {
    const auto relativePath = SanitizeRelativePath(entryPath.empty() ? fallbackName : entryPath);
    if (!relativePath.has_value()) {
        throw ArchiveException("ERR_UNKNOWN", "Archive entry path is unsafe");
    }
    return SafeExtractTarget{
        .relativePath = *relativePath,
        .absolutePath = JoinPath(outputDir, *relativePath),
    };
}

void EnsureDirectoryExists(const std::string& path) {
    if (path.empty()) {
        return;
    }
    if (!NWindows::NFile::NDir::CreateComplexDir(path.c_str())) {
        throw ArchiveException("ERR_OUTPUT_DENIED", "Failed to create output directory");
    }
}

std::optional<long long> ToOptionalSize(unsigned int low, unsigned int high) {
    return static_cast<long long>((static_cast<std::uint64_t>(high) << 32) | low);
}

std::optional<long long> ToOptionalLongLong(const PROPVARIANT& value) {
    UInt64 raw = 0;
    if (!ConvertPropVariantToUInt64(value, raw)) {
        return std::nullopt;
    }
    return static_cast<long long>(raw);
}

std::optional<bool> ToOptionalBool(const PROPVARIANT& value) {
    if (value.vt == VT_EMPTY) {
        return std::nullopt;
    }
    if (value.vt != VT_BOOL) {
        return std::nullopt;
    }
    return value.boolVal != VARIANT_FALSE;
}

std::optional<long long> ToOptionalEpochMillis(const PROPVARIANT& value) {
    if (value.vt == VT_EMPTY) {
        return std::nullopt;
    }
    if (value.vt != VT_FILETIME) {
        return std::nullopt;
    }
    const auto ticks = (static_cast<std::uint64_t>(value.filetime.dwHighDateTime) << 32) |
        static_cast<std::uint64_t>(value.filetime.dwLowDateTime);
    constexpr std::uint64_t kUnixEpochOffset100Ns = 116444736000000000ULL;
    if (ticks < kUnixEpochOffset100Ns) {
        return 0;
    }
    return static_cast<long long>((ticks - kUnixEpochOffset100Ns) / 10000ULL);
}

std::optional<std::string> ToOptionalUtf8String(const PROPVARIANT& value) {
    if (value.vt == VT_EMPTY) {
        return std::nullopt;
    }
    if (value.vt != VT_BSTR || value.bstrVal == nullptr) {
        return std::nullopt;
    }
    return WideToUtf8(value.bstrVal);
}

std::string MapUnrarError(int code) {
    switch (code) {
        case ERAR_UNKNOWN_FORMAT:
            return "ERR_UNSUPPORTED_FORMAT";
        case ERAR_BAD_PASSWORD:
            return "ERR_WRONG_PASSWORD";
        case ERAR_MISSING_PASSWORD:
            return "ERR_ENCRYPTED";
        case ERAR_BAD_DATA:
        case ERAR_BAD_ARCHIVE:
            return "ERR_CORRUPTED";
        case ERAR_EREAD:
        case ERAR_EREFERENCE:
            return "ERR_MISSING_VOLUME";
        case ERAR_EOPEN:
            return "ERR_UNKNOWN";
        case ERAR_ECREATE:
        case ERAR_ECLOSE:
        case ERAR_EWRITE:
            return "ERR_OUTPUT_DENIED";
        default:
            return "ERR_UNKNOWN";
    }
}

std::string MapSevenZipOpenError(HRESULT result, bool hasPassword) {
    switch (result) {
        case E_ABORT:
            return hasPassword ? "ERR_UNKNOWN" : "ERR_ENCRYPTED";
        case S_FALSE:
            return hasPassword ? "ERR_WRONG_PASSWORD" : "ERR_CORRUPTED";
        default:
            return "ERR_UNKNOWN";
    }
}

std::string MapSevenZipExtractError(HRESULT result, bool hasPassword) {
    switch (result) {
        case E_ABORT:
            return hasPassword ? "ERR_WRONG_PASSWORD" : "ERR_ENCRYPTED";
        case S_FALSE:
            return hasPassword ? "ERR_WRONG_PASSWORD" : "ERR_CORRUPTED";
        default:
            return "ERR_UNKNOWN";
    }
}

std::string MapSevenZipOperationResult(Int32 operationResult, bool encrypted, bool hasPassword) {
    switch (operationResult) {
        case NArchive::NExtract::NOperationResult::kWrongPassword:
            return "ERR_WRONG_PASSWORD";
        case NArchive::NExtract::NOperationResult::kUnsupportedMethod:
            return "ERR_UNSUPPORTED_FORMAT";
        case NArchive::NExtract::NOperationResult::kUnavailable:
            return "ERR_MISSING_VOLUME";
        case NArchive::NExtract::NOperationResult::kCRCError:
        case NArchive::NExtract::NOperationResult::kDataError:
            if (encrypted) {
                return hasPassword ? "ERR_WRONG_PASSWORD" : "ERR_ENCRYPTED";
            }
            return "ERR_CORRUPTED";
        case NArchive::NExtract::NOperationResult::kUnexpectedEnd:
        case NArchive::NExtract::NOperationResult::kDataAfterEnd:
        case NArchive::NExtract::NOperationResult::kIsNotArc:
        case NArchive::NExtract::NOperationResult::kHeadersError:
            return "ERR_CORRUPTED";
        default:
            return "ERR_UNKNOWN";
    }
}

ArchiveFormatKind MapSevenZipHandlerName(const std::string& name, const std::string& primaryPath) {
    const std::string lowerName = ToLower(name);
    const std::string lowerPath = ToLower(primaryPath);
    if (lowerName == "7z") {
        return ArchiveFormatKind::kSevenZip;
    }
    if (lowerName == "zip") {
        return EndsWith(lowerPath, ".zipx") ? ArchiveFormatKind::kZipx : ArchiveFormatKind::kZip;
    }
    if (lowerName == "tar") {
        return ArchiveFormatKind::kTar;
    }
    if (lowerName == "gzip") {
        return EndsWith(lowerPath, ".tgz") || EndsWith(lowerPath, ".tar.gz")
            ? ArchiveFormatKind::kTgz
            : ArchiveFormatKind::kGz;
    }
    return ArchiveFormatKind::kUnknown;
}

std::string GetSevenZipItemPath(IInArchive* archive, UInt32 index, const std::string& sourcePrimaryPath) {
    NWindows::NCOM::CPropVariant pathProp;
    if (archive->GetProperty(index, kpidPath, &pathProp) != S_OK) {
        throw ArchiveException("ERR_UNKNOWN", "Failed to read archive item path");
    }
    if (pathProp.vt == VT_EMPTY || pathProp.bstrVal == nullptr) {
        return DefaultEntryName(sourcePrimaryPath);
    }
    if (pathProp.vt != VT_BSTR) {
        throw ArchiveException("ERR_UNKNOWN", "Unexpected archive item path type");
    }
    const std::string path = WideToUtf8(pathProp.bstrVal);
    return path.empty() ? DefaultEntryName(sourcePrimaryPath) : path;
}

class ScopedUnrarArchive {
public:
    explicit ScopedUnrarArchive(
        const ArchiveSource& source,
        unsigned int openMode = RAR_OM_LIST_INCSPLIT,
        const std::optional<std::u16string>& password = std::nullopt
    ) {
        path_ = Utf8ToWide(SelectUnrarPrimaryPath(source));
        openData_ = {};
        openData_.ArcNameW = path_.data();
        openData_.OpenMode = openMode;
        handle_ = RAROpenArchiveEx(&openData_);
        if (handle_ == nullptr || openData_.OpenResult != ERAR_SUCCESS) {
            throw ArchiveException(MapUnrarError(openData_.OpenResult), "Failed to open RAR archive");
        }
        if (password.has_value()) {
            passwordUtf8_ = Utf16ToUtf8(*password);
            RARSetPassword(handle_, passwordUtf8_.data());
        }
    }

    ~ScopedUnrarArchive() {
        if (handle_ != nullptr) {
            RARCloseArchive(handle_);
        }
    }

    HANDLE get() const {
        return handle_;
    }

private:
    RAROpenArchiveDataEx openData_{};
    std::wstring path_;
    std::string passwordUtf8_;
    HANDLE handle_ = nullptr;
};

struct SevenZipFormatInfo {
    UInt32 index = 0;
    GUID classId{};
    std::string name;
};

class SevenZipOpenCallback Z7_final:
    public IArchiveOpenCallback,
    public IArchiveOpenVolumeCallback,
    public ICryptoGetTextPassword,
    public CMyUnknownImp {
    Z7_COM_QI_BEGIN2(IArchiveOpenCallback)
    Z7_COM_QI_ENTRY(IArchiveOpenVolumeCallback)
    Z7_COM_QI_ENTRY(ICryptoGetTextPassword)
    Z7_COM_QI_END
    Z7_COM_ADDREF_RELEASE

    Z7_IFACE_COM7_IMP(IArchiveOpenCallback)
    Z7_IFACE_COM7_IMP(IArchiveOpenVolumeCallback)
    Z7_IFACE_COM7_IMP(ICryptoGetTextPassword)

public:
    SevenZipOpenCallback(const ArchiveSource& source, const std::optional<std::u16string>& password)
        : primaryPathUtf8_(source.primaryPath),
          primaryPathWide_(Utf8ToWide(source.primaryPath)) {
        for (const auto& path : source.partPaths) {
            partPaths_.push_back(path);
        }
        if (partPaths_.empty() && !source.primaryPath.empty()) {
            partPaths_.push_back(source.primaryPath);
        }
        if (password.has_value()) {
            password_ = Utf16ToWide(*password);
            hasPassword_ = true;
        }
    }

private:
    std::string primaryPathUtf8_;
    std::wstring primaryPathWide_;
    std::vector<std::string> partPaths_;
    std::wstring password_;
    bool hasPassword_ = false;

    HRESULT FindVolume(const wchar_t* name, CMyComPtr<IInStream>& stream) {
        if (name == nullptr) {
            return E_INVALIDARG;
        }

        const std::string target = WideToUtf8(name);
        auto matches = [&](const std::string& candidate) {
            if (candidate == target) {
                return true;
            }
            const auto slash = candidate.find_last_of("/\\");
            const std::string base = slash == std::string::npos ? candidate : candidate.substr(slash + 1);
            return base == target;
        };

        for (const auto& path : partPaths_) {
            if (!matches(path)) {
                continue;
            }
            auto* fileSpec = new CInFileStream();
            CMyComPtr<IInStream> file = fileSpec;
            if (!fileSpec->Open(path.c_str())) {
                return S_FALSE;
            }
            stream = file;
            return S_OK;
        }
        return S_FALSE;
    }
};

class SevenZipExtractCallback Z7_final:
    public IArchiveExtractCallback,
    public ICryptoGetTextPassword,
    public CMyUnknownImp {
    Z7_IFACES_IMP_UNK_2(IArchiveExtractCallback, ICryptoGetTextPassword)
    Z7_IFACE_COM7_IMP(IProgress)

public:
    SevenZipExtractCallback(
        IInArchive* archive,
        const ArchiveSource& source,
        const std::string& outputDir,
        const std::optional<std::u16string>& password
    )
        : archive_(archive),
          sourcePrimaryPath_(source.primaryPath),
          outputDir_(outputDir) {
        if (password.has_value()) {
            password_ = Utf16ToWide(*password);
            hasPassword_ = true;
        }
    }

    int extractedCount = 0;
    std::vector<std::string> failedEntries;
    std::string extractedFilePath;
    std::string firstErrorCode;
    std::string firstErrorMessage;

private:
    CMyComPtr<IInArchive> archive_;
    std::string sourcePrimaryPath_;
    std::string outputDir_;
    std::wstring password_;
    bool hasPassword_ = false;

    COutFileStream* outFileStreamSpec_ = nullptr;
    CMyComPtr<ISequentialOutStream> outFileStream_;
    std::string currentEntryPath_;
    std::string currentOutputPath_;
    bool currentIsDirectory_ = false;
    bool currentEncrypted_ = false;
    bool currentExtractMode_ = false;

    void SetFatalError(const std::string& code, const std::string& message) {
        if (firstErrorCode.empty()) {
            firstErrorCode = code;
            firstErrorMessage = message;
        }
    }
};

Z7_COM7F_IMF(SevenZipOpenCallback::SetTotal(const UInt64* /* files */, const UInt64* /* bytes */)) {
    return S_OK;
}

Z7_COM7F_IMF(SevenZipOpenCallback::SetCompleted(const UInt64* /* files */, const UInt64* /* bytes */)) {
    return S_OK;
}

Z7_COM7F_IMF(SevenZipOpenCallback::GetProperty(PROPID propID, PROPVARIANT* value)) {
    NWindows::NCOM::PropVariant_Clear(value);
    if (propID != kpidName) {
        return S_OK;
    }
    NWindows::NCOM::CPropVariant prop(primaryPathWide_.c_str());
    return prop.Detach(value);
}

Z7_COM7F_IMF(SevenZipOpenCallback::GetStream(const wchar_t* name, IInStream** inStream)) {
    if (inStream == nullptr) {
        return E_INVALIDARG;
    }
    *inStream = nullptr;
    CMyComPtr<IInStream> stream;
    const HRESULT result = FindVolume(name, stream);
    if (result != S_OK || !stream) {
        return result;
    }
    *inStream = stream.Detach();
    return S_OK;
}

Z7_COM7F_IMF(SevenZipOpenCallback::CryptoGetTextPassword(BSTR* password)) {
    if (password == nullptr) {
        return E_INVALIDARG;
    }
    *password = nullptr;
    if (!hasPassword_) {
        return E_ABORT;
    }
    return StringToBstr(password_.c_str(), password);
}

Z7_COM7F_IMF(SevenZipExtractCallback::SetTotal(UInt64 /* size */)) {
    return S_OK;
}

Z7_COM7F_IMF(SevenZipExtractCallback::SetCompleted(const UInt64* /* completeValue */)) {
    return S_OK;
}

Z7_COM7F_IMF(SevenZipExtractCallback::GetStream(UInt32 index, ISequentialOutStream** outStream, Int32 askExtractMode)) {
    if (outStream == nullptr) {
        return E_INVALIDARG;
    }

    *outStream = nullptr;
    outFileStream_.Release();
    outFileStreamSpec_ = nullptr;
    currentEntryPath_.clear();
    currentOutputPath_.clear();
    currentIsDirectory_ = false;
    currentEncrypted_ = false;
    currentExtractMode_ = askExtractMode == NArchive::NExtract::NAskMode::kExtract;

    try {
        currentEntryPath_ = GetSevenZipItemPath(archive_, index, sourcePrimaryPath_);

        NWindows::NCOM::CPropVariant isDirProp;
        NWindows::NCOM::CPropVariant encryptedProp;
        if (archive_->GetProperty(index, kpidIsDir, &isDirProp) != S_OK ||
            archive_->GetProperty(index, kpidEncrypted, &encryptedProp) != S_OK) {
            throw ArchiveException("ERR_UNKNOWN", "Failed to read archive item properties");
        }
        currentIsDirectory_ = ToOptionalBool(isDirProp).value_or(false);
        currentEncrypted_ = ToOptionalBool(encryptedProp).value_or(false);

        if (!currentExtractMode_) {
            return S_OK;
        }

        const auto target = ResolveExtractTarget(outputDir_, currentEntryPath_, DefaultEntryName(sourcePrimaryPath_));
        currentOutputPath_ = target.absolutePath;

        if (currentIsDirectory_) {
            EnsureDirectoryExists(currentOutputPath_);
            return S_OK;
        }

        EnsureDirectoryExists(ParentDir(currentOutputPath_));

        outFileStreamSpec_ = new COutFileStream();
        CMyComPtr<ISequentialOutStream> outStreamLoc(outFileStreamSpec_);
        if (!outFileStreamSpec_->Create_ALWAYS(currentOutputPath_.c_str())) {
            throw ArchiveException("ERR_OUTPUT_DENIED", "Failed to open output file");
        }

        outFileStream_ = outStreamLoc;
        *outStream = outStreamLoc.Detach();
        return S_OK;
    } catch (const ArchiveException& exception) {
        if (!currentEntryPath_.empty()) {
            failedEntries.push_back(currentEntryPath_);
        }
        SetFatalError(exception.code(), exception.what());
        return E_ABORT;
    }
}

Z7_COM7F_IMF(SevenZipExtractCallback::PrepareOperation(Int32 askExtractMode)) {
    currentExtractMode_ = askExtractMode == NArchive::NExtract::NAskMode::kExtract;
    return S_OK;
}

Z7_COM7F_IMF(SevenZipExtractCallback::SetOperationResult(Int32 operationResult)) {
    if (outFileStream_ != nullptr) {
        outFileStreamSpec_->Close();
        outFileStream_.Release();
        outFileStreamSpec_ = nullptr;
    }

    if (operationResult == NArchive::NExtract::NOperationResult::kOK) {
        if (currentExtractMode_) {
            ++extractedCount;
            if (!currentIsDirectory_ && extractedFilePath.empty()) {
                extractedFilePath = currentOutputPath_;
            }
        }
        return S_OK;
    }

    if (!currentEntryPath_.empty()) {
        failedEntries.push_back(currentEntryPath_);
    }
    SetFatalError(
        MapSevenZipOperationResult(operationResult, currentEncrypted_, hasPassword_),
        "Failed to extract archive entry"
    );
    if (!currentOutputPath_.empty() && !currentIsDirectory_) {
        NWindows::NFile::NDir::DeleteFileAlways(currentOutputPath_.c_str());
    }
    return E_ABORT;
}

Z7_COM7F_IMF(SevenZipExtractCallback::CryptoGetTextPassword(BSTR* password)) {
    if (password == nullptr) {
        return E_INVALIDARG;
    }
    *password = nullptr;
    if (!hasPassword_) {
        return E_ABORT;
    }
    return StringToBstr(password_.c_str(), password);
}

std::optional<SevenZipFormatInfo> FindSevenZipFormat(const ArchiveSource& source) {
    UInt32 numFormats = 0;
    if (GetNumberOfFormats(&numFormats) != S_OK) {
        throw ArchiveException("ERR_UNKNOWN", "Failed to enumerate 7-Zip formats");
    }

    const std::string lowerPath = ToLower(source.primaryPath);
    const auto matches = [&](const std::string& extensions) {
        size_t start = 0;
        while (start < extensions.size()) {
            const size_t end = extensions.find(' ', start);
            const std::string ext = extensions.substr(start, end == std::string::npos ? std::string::npos : end - start);
            if (!ext.empty()) {
                const std::string lowerExt = ToLower(ext);
                if (EndsWith(lowerPath, "." + lowerExt)) {
                    return true;
                }
                if (lowerExt == "7z" && lowerPath.find(".7z.") != std::string::npos) {
                    return true;
                }
                if ((lowerExt == "zip" || lowerExt == "zipx") && (lowerPath.find(".zip.") != std::string::npos || EndsWith(lowerPath, ".z01"))) {
                    return true;
                }
            }
            if (end == std::string::npos) {
                break;
            }
            start = end + 1;
        }
        return false;
    };

    for (UInt32 index = 0; index < numFormats; ++index) {
        NWindows::NCOM::CPropVariant classIdProp;
        if (GetHandlerProperty2(index, NArchive::NHandlerPropID::kClassID, &classIdProp) != S_OK) {
            continue;
        }
        if (classIdProp.vt != VT_BSTR || classIdProp.bstrVal == nullptr || ::SysStringByteLen(classIdProp.bstrVal) != sizeof(GUID)) {
            continue;
        }

        NWindows::NCOM::CPropVariant nameProp;
        if (GetHandlerProperty2(index, NArchive::NHandlerPropID::kName, &nameProp) != S_OK) {
            continue;
        }
        const auto name = ToOptionalUtf8String(nameProp);
        if (!name.has_value()) {
            continue;
        }

        NWindows::NCOM::CPropVariant extProp;
        if (GetHandlerProperty2(index, NArchive::NHandlerPropID::kExtension, &extProp) != S_OK) {
            continue;
        }
        const auto extensions = ToOptionalUtf8String(extProp).value_or("");

        if (!matches(extensions)) {
            continue;
        }

        SevenZipFormatInfo info;
        info.index = index;
        std::memcpy(&info.classId, classIdProp.bstrVal, sizeof(GUID));
        info.name = *name;
        return info;
    }

    return std::nullopt;
}

CMyComPtr<IInArchive> OpenSevenZipArchive(
    const ArchiveSource& source,
    const std::optional<std::u16string>& password,
    SevenZipFormatInfo* outFormat = nullptr
) {
    const auto format = FindSevenZipFormat(source);
    if (!format.has_value()) {
        throw ArchiveException("ERR_UNSUPPORTED_FORMAT", "No 7-Zip handler matched archive path");
    }

    CMyComPtr<IInArchive> archive;
    if (CreateObject(&format->classId, &IID_IInArchive, reinterpret_cast<void**>(&archive)) != S_OK || !archive) {
        throw ArchiveException("ERR_UNKNOWN", "Failed to create 7-Zip archive handler");
    }

    auto* fileSpec = new CInFileStream();
    CMyComPtr<IInStream> file = fileSpec;
    if (!fileSpec->Open(source.primaryPath.c_str())) {
        throw ArchiveException("ERR_UNKNOWN", "Failed to open archive file");
    }

    SevenZipOpenCallback* callbackSpec = new SevenZipOpenCallback(source, password);
    CMyComPtr<IArchiveOpenCallback> callback(callbackSpec);

    const UInt64 scanSize = 1ULL << 23;
    const HRESULT openResult = archive->Open(file, &scanSize, callback);
    if (openResult != S_OK) {
        throw ArchiveException(MapSevenZipOpenError(openResult, password.has_value()), "Failed to open archive with 7-Zip");
    }

    if (outFormat != nullptr) {
        *outFormat = *format;
    }
    return archive;
}

std::optional<UInt32> FindSevenZipItemIndex(IInArchive* archive, const ArchiveSource& source, const std::string& entryPath) {
    UInt32 numItems = 0;
    if (archive->GetNumberOfItems(&numItems) != S_OK) {
        throw ArchiveException("ERR_UNKNOWN", "Failed to enumerate archive items");
    }

    for (UInt32 index = 0; index < numItems; ++index) {
        if (GetSevenZipItemPath(archive, index, source.primaryPath) == entryPath) {
            return index;
        }
    }
    return std::nullopt;
}

ArchiveFormatKind DetectRarFormat(const ArchiveSource& source) {
    ScopedUnrarArchive archive(source);
    return ArchiveFormatKind::kRar;
}

ArchiveFormatKind DetectSevenZipFormat(const ArchiveSource& source) {
    SevenZipFormatInfo format;
    auto archive = OpenSevenZipArchive(source, std::nullopt, &format);
    return MapSevenZipHandlerName(format.name, source.primaryPath);
}

std::vector<ArchiveEntryData> ListRarEntries(
    const ArchiveSource& source,
    const std::optional<std::u16string>& password
) {
    ScopedUnrarArchive archive(source, RAR_OM_LIST_INCSPLIT, password);
    std::vector<ArchiveEntryData> entries;

    while (true) {
        RARHeaderDataEx header{};
        const int readResult = RARReadHeaderEx(archive.get(), &header);
        if (readResult == ERAR_END_ARCHIVE) {
            break;
        }
        if (readResult != ERAR_SUCCESS) {
            throw ArchiveException(MapUnrarError(readResult), "Failed to read RAR header");
        }

        const std::string path = WideToUtf8(header.FileNameW[0] != 0 ? header.FileNameW : nullptr);
        entries.push_back(ArchiveEntryData{
            .path = path,
            .name = BaseName(path),
            .isDirectory = (header.Flags & RHDF_DIRECTORY) != 0,
            .compressedSize = ToOptionalSize(header.PackSize, header.PackSizeHigh),
            .uncompressedSize = ToOptionalSize(header.UnpSize, header.UnpSizeHigh),
            .modifiedAtMillis = std::nullopt,
            .encrypted = (header.Flags & RHDF_ENCRYPTED) != 0,
        });

        const int skipResult = RARProcessFileW(archive.get(), RAR_SKIP, nullptr, nullptr);
        if (skipResult != ERAR_SUCCESS) {
            throw ArchiveException(MapUnrarError(skipResult), "Failed to skip RAR entry");
        }
    }

    return entries;
}

std::vector<ArchiveEntryData> ListSevenZipEntries(
    const ArchiveSource& source,
    const std::optional<std::u16string>& password
) {
    auto archive = OpenSevenZipArchive(source, password);
    UInt32 numItems = 0;
    if (archive->GetNumberOfItems(&numItems) != S_OK) {
        throw ArchiveException("ERR_UNKNOWN", "Failed to enumerate archive items");
    }

    std::vector<ArchiveEntryData> entries;
    entries.reserve(numItems);

    for (UInt32 index = 0; index < numItems; ++index) {
        NWindows::NCOM::CPropVariant pathProp;
        NWindows::NCOM::CPropVariant isDirProp;
        NWindows::NCOM::CPropVariant sizeProp;
        NWindows::NCOM::CPropVariant packSizeProp;
        NWindows::NCOM::CPropVariant mtimeProp;
        NWindows::NCOM::CPropVariant encryptedProp;

        if (archive->GetProperty(index, kpidPath, &pathProp) != S_OK ||
            archive->GetProperty(index, kpidIsDir, &isDirProp) != S_OK ||
            archive->GetProperty(index, kpidSize, &sizeProp) != S_OK ||
            archive->GetProperty(index, kpidPackSize, &packSizeProp) != S_OK ||
            archive->GetProperty(index, kpidMTime, &mtimeProp) != S_OK ||
            archive->GetProperty(index, kpidEncrypted, &encryptedProp) != S_OK) {
            throw ArchiveException("ERR_UNKNOWN", "Failed to read archive item properties");
        }

        const std::string path = ToOptionalUtf8String(pathProp).value_or("");
        entries.push_back(ArchiveEntryData{
            .path = path,
            .name = BaseName(path),
            .isDirectory = ToOptionalBool(isDirProp).value_or(false),
            .compressedSize = ToOptionalLongLong(packSizeProp),
            .uncompressedSize = ToOptionalLongLong(sizeProp),
            .modifiedAtMillis = ToOptionalEpochMillis(mtimeProp),
            .encrypted = ToOptionalBool(encryptedProp).value_or(false),
        });
    }

    return entries;
}

ExtractResultData ExtractRarArchive(
    const ArchiveSource& source,
    const std::string& outputDir,
    const std::optional<std::u16string>& password
) {
    EnsureDirectoryExists(outputDir);

    ScopedUnrarArchive archive(source, RAR_OM_EXTRACT, password);
    int extractedCount = 0;

    while (true) {
        RARHeaderDataEx header{};
        const int readResult = RARReadHeaderEx(archive.get(), &header);
        if (readResult == ERAR_END_ARCHIVE) {
            break;
        }
        if (readResult != ERAR_SUCCESS) {
            throw ArchiveException(MapUnrarError(readResult), "Failed to read RAR header");
        }

        const bool isDirectory = (header.Flags & RHDF_DIRECTORY) != 0;
        const std::string entryPath = WideToUtf8(header.FileNameW[0] != 0 ? header.FileNameW : nullptr);
        const auto target = ResolveExtractTarget(outputDir, entryPath, DefaultEntryName(source.primaryPath));

        if (isDirectory) {
            EnsureDirectoryExists(target.absolutePath);
            const int skipResult = RARProcessFileW(archive.get(), RAR_SKIP, nullptr, nullptr);
            if (skipResult != ERAR_SUCCESS) {
                throw ArchiveException(MapUnrarError(skipResult), "Failed to skip extracted RAR directory");
            }
            ++extractedCount;
            continue;
        }

        EnsureDirectoryExists(ParentDir(target.absolutePath));
        std::wstring outputPathWide = Utf8ToWide(target.absolutePath);
        const int processResult = RARProcessFileW(archive.get(), RAR_EXTRACT, nullptr, outputPathWide.data());
        if (processResult != ERAR_SUCCESS) {
            throw ArchiveException(MapUnrarError(processResult), "Failed to extract RAR entry");
        }
        ++extractedCount;
    }

    return ExtractResultData{
        .extractedCount = extractedCount,
        .failedEntries = {},
        .outputPath = outputDir,
    };
}

ExtractSingleEntryResultData ExtractRarEntry(
    const ArchiveSource& source,
    const std::string& entryPath,
    const std::string& outputDir,
    const std::optional<std::u16string>& password
) {
    EnsureDirectoryExists(outputDir);

    ScopedUnrarArchive archive(source, RAR_OM_EXTRACT, password);

    while (true) {
        RARHeaderDataEx header{};
        const int readResult = RARReadHeaderEx(archive.get(), &header);
        if (readResult == ERAR_END_ARCHIVE) {
            break;
        }
        if (readResult != ERAR_SUCCESS) {
            throw ArchiveException(MapUnrarError(readResult), "Failed to read RAR header");
        }

        const std::string currentPath = WideToUtf8(header.FileNameW[0] != 0 ? header.FileNameW : nullptr);
        if (currentPath != entryPath) {
            const int skipResult = RARProcessFileW(archive.get(), RAR_SKIP, nullptr, nullptr);
            if (skipResult != ERAR_SUCCESS) {
                throw ArchiveException(MapUnrarError(skipResult), "Failed to skip RAR entry");
            }
            continue;
        }

        const bool isDirectory = (header.Flags & RHDF_DIRECTORY) != 0;
        const auto target = ResolveExtractTarget(outputDir, currentPath, DefaultEntryName(source.primaryPath));

        if (isDirectory) {
            EnsureDirectoryExists(target.absolutePath);
            const int skipResult = RARProcessFileW(archive.get(), RAR_SKIP, nullptr, nullptr);
            if (skipResult != ERAR_SUCCESS) {
                throw ArchiveException(MapUnrarError(skipResult), "Failed to skip extracted RAR directory");
            }
            return ExtractSingleEntryResultData{
                .extractedFilePath = target.absolutePath,
            };
        }

        EnsureDirectoryExists(ParentDir(target.absolutePath));
        std::wstring outputPathWide = Utf8ToWide(target.absolutePath);
        const int processResult = RARProcessFileW(archive.get(), RAR_EXTRACT, nullptr, outputPathWide.data());
        if (processResult != ERAR_SUCCESS) {
            throw ArchiveException(MapUnrarError(processResult), "Failed to extract RAR entry");
        }
        return ExtractSingleEntryResultData{
            .extractedFilePath = target.absolutePath,
        };
    }

    throw ArchiveException("ERR_UNKNOWN", "Archive entry not found");
}

ExtractResultData ExtractSevenZipArchive(
    const ArchiveSource& source,
    const std::string& outputDir,
    const std::optional<std::u16string>& password
) {
    EnsureDirectoryExists(outputDir);

    auto archive = OpenSevenZipArchive(source, password);
    auto* callbackSpec = new SevenZipExtractCallback(archive, source, outputDir, password);
    CMyComPtr<IArchiveExtractCallback> callback(callbackSpec);

    const HRESULT extractResult = archive->Extract(nullptr, kExtractAllItems, false, callback);
    if (!callbackSpec->firstErrorCode.empty()) {
        throw ArchiveException(callbackSpec->firstErrorCode, callbackSpec->firstErrorMessage);
    }
    if (extractResult != S_OK) {
        throw ArchiveException(
            MapSevenZipExtractError(extractResult, password.has_value()),
            "Failed to extract archive with 7-Zip"
        );
    }

    return ExtractResultData{
        .extractedCount = callbackSpec->extractedCount,
        .failedEntries = callbackSpec->failedEntries,
        .outputPath = outputDir,
    };
}

ExtractSingleEntryResultData ExtractSevenZipEntry(
    const ArchiveSource& source,
    const std::string& entryPath,
    const std::string& outputDir,
    const std::optional<std::u16string>& password
) {
    EnsureDirectoryExists(outputDir);

    auto archive = OpenSevenZipArchive(source, password);
    const auto index = FindSevenZipItemIndex(archive, source, entryPath);
    if (!index.has_value()) {
        throw ArchiveException("ERR_UNKNOWN", "Archive entry not found");
    }

    UInt32 itemIndex = *index;
    auto* callbackSpec = new SevenZipExtractCallback(archive, source, outputDir, password);
    CMyComPtr<IArchiveExtractCallback> callback(callbackSpec);

    const HRESULT extractResult = archive->Extract(&itemIndex, 1, false, callback);
    if (!callbackSpec->firstErrorCode.empty()) {
        throw ArchiveException(callbackSpec->firstErrorCode, callbackSpec->firstErrorMessage);
    }
    if (extractResult != S_OK) {
        throw ArchiveException(
            MapSevenZipExtractError(extractResult, password.has_value()),
            "Failed to extract archive entry with 7-Zip"
        );
    }

    std::string extractedFilePath = callbackSpec->extractedFilePath;
    if (extractedFilePath.empty()) {
        extractedFilePath = ResolveExtractTarget(outputDir, entryPath, DefaultEntryName(source.primaryPath)).absolutePath;
    }
    return ExtractSingleEntryResultData{
        .extractedFilePath = extractedFilePath,
    };
}

}  // namespace

ArchiveFormatKind DetectFormat(const ArchiveSource& source) {
    switch (SelectBackend(source)) {
        case BackendKind::kUnrar:
            return DetectRarFormat(source);
        case BackendKind::kSevenZip:
            return DetectSevenZipFormat(source);
        case BackendKind::kNone:
        default:
            return ArchiveFormatKind::kUnknown;
    }
}

std::vector<ArchiveEntryData> ListEntries(
    const ArchiveSource& source,
    const std::optional<std::u16string>& password
) {
    switch (SelectBackend(source)) {
        case BackendKind::kUnrar:
            return ListRarEntries(source, password);
        case BackendKind::kSevenZip:
            return ListSevenZipEntries(source, password);
        case BackendKind::kNone:
        default:
            return {};
    }
}

ExtractResultData Extract(
    const ArchiveSource& source,
    const std::string& outputDir,
    const std::optional<std::u16string>& password
) {
    switch (SelectBackend(source)) {
        case BackendKind::kUnrar:
            return ExtractRarArchive(source, outputDir, password);
        case BackendKind::kSevenZip:
            return ExtractSevenZipArchive(source, outputDir, password);
        case BackendKind::kNone:
        default:
            return ExtractResultData{
                .extractedCount = 0,
                .failedEntries = {},
                .outputPath = outputDir,
            };
    }
}

ExtractSingleEntryResultData ExtractEntry(
    const ArchiveSource& source,
    const std::string& entryPath,
    const std::string& outputDir,
    const std::optional<std::u16string>& password
) {
    switch (SelectBackend(source)) {
        case BackendKind::kUnrar:
            return ExtractRarEntry(source, entryPath, outputDir, password);
        case BackendKind::kSevenZip:
            return ExtractSevenZipEntry(source, entryPath, outputDir, password);
        case BackendKind::kNone:
        default:
            return ExtractSingleEntryResultData{
                .extractedFilePath = outputDir,
            };
    }
}

}  // namespace archivekit
