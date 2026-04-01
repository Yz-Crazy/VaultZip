#pragma once

#include <optional>
#include <stdexcept>
#include <string>
#include <vector>

namespace archivekit {

enum class ArchiveFormatKind {
    kZip,
    kZipx,
    kRar,
    kSevenZip,
    kTar,
    kTgz,
    kGz,
    kUnknown,
};

struct ArchiveSource {
    std::string primaryPath;
    std::vector<std::string> partPaths;
};

struct ArchiveEntryData {
    std::string path;
    std::string name;
    bool isDirectory = false;
    std::optional<long long> compressedSize;
    std::optional<long long> uncompressedSize;
    std::optional<long long> modifiedAtMillis;
    bool encrypted = false;
};

struct ExtractResultData {
    int extractedCount = 0;
    std::vector<std::string> failedEntries;
    std::string outputPath;
};

struct ExtractSingleEntryResultData {
    std::string extractedFilePath;
};

class ArchiveException : public std::runtime_error {
public:
    ArchiveException(std::string code, std::string message)
        : std::runtime_error(std::move(message)), code_(std::move(code)) {}

    const std::string& code() const {
        return code_;
    }

private:
    std::string code_;
};

}  // namespace archivekit
