#include <jni.h>

#include <exception>
#include <optional>
#include <string>

#include "core/ArchiveRouter.h"
#include "jni/JniConverters.h"
#include "jni/JniErrors.h"

namespace {

archivekit::ArchiveSource BuildSource(JNIEnv* env, jstring primaryPath, jobjectArray partPaths) {
    archivekit::ArchiveSource source{
        .primaryPath = archivekit::JStringToUtf8(env, primaryPath),
        .partPaths = archivekit::JObjectArrayToStrings(env, partPaths),
    };
    if (source.partPaths.empty() && !source.primaryPath.empty()) {
        source.partPaths.push_back(source.primaryPath);
    }
    return source;
}

jstring FormatToJava(JNIEnv* env, archivekit::ArchiveFormatKind format) {
    switch (format) {
        case archivekit::ArchiveFormatKind::kZip:
            return env->NewStringUTF("ZIP");
        case archivekit::ArchiveFormatKind::kZipx:
            return env->NewStringUTF("ZIPX");
        case archivekit::ArchiveFormatKind::kRar:
            return env->NewStringUTF("RAR");
        case archivekit::ArchiveFormatKind::kSevenZip:
            return env->NewStringUTF("SEVEN_Z");
        case archivekit::ArchiveFormatKind::kTar:
            return env->NewStringUTF("TAR");
        case archivekit::ArchiveFormatKind::kTgz:
            return env->NewStringUTF("TGZ");
        case archivekit::ArchiveFormatKind::kGz:
            return env->NewStringUTF("GZ");
        case archivekit::ArchiveFormatKind::kUnknown:
        default:
            return env->NewStringUTF("UNKNOWN");
    }
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_vaultzip_archive_bridge_NativeArchiveBridgeImpl_nativeDetectFormat(
    JNIEnv* env,
    jobject /* thiz */,
    jstring primaryPath,
    jobjectArray partPaths
) {
    try {
        const auto source = BuildSource(env, primaryPath, partPaths);
        return FormatToJava(env, archivekit::DetectFormat(source));
    } catch (const archivekit::ArchiveException& exception) {
        archivekit::ThrowNativeArchiveException(env, exception);
    } catch (const std::exception& exception) {
        archivekit::ThrowUnknownArchiveException(env, exception);
    }
    return nullptr;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_vaultzip_archive_bridge_NativeArchiveBridgeImpl_nativeListEntries(
    JNIEnv* env,
    jobject /* thiz */,
    jstring primaryPath,
    jobjectArray partPaths,
    jcharArray passwordChars
) {
    try {
        const auto source = BuildSource(env, primaryPath, partPaths);
        const auto password = archivekit::JCharArrayToUtf16(env, passwordChars);
        return archivekit::ToJavaEntryList(env, archivekit::ListEntries(source, password));
    } catch (const archivekit::ArchiveException& exception) {
        archivekit::ThrowNativeArchiveException(env, exception);
    } catch (const std::exception& exception) {
        archivekit::ThrowUnknownArchiveException(env, exception);
    }
    return nullptr;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_vaultzip_archive_bridge_NativeArchiveBridgeImpl_nativeExtract(
    JNIEnv* env,
    jobject /* thiz */,
    jstring primaryPath,
    jobjectArray partPaths,
    jstring outputDir,
    jcharArray passwordChars
) {
    try {
        const auto source = BuildSource(env, primaryPath, partPaths);
        const auto password = archivekit::JCharArrayToUtf16(env, passwordChars);
        return archivekit::ToJavaExtractResultDto(
            env,
            archivekit::Extract(source, archivekit::JStringToUtf8(env, outputDir), password)
        );
    } catch (const archivekit::ArchiveException& exception) {
        archivekit::ThrowNativeArchiveException(env, exception);
    } catch (const std::exception& exception) {
        archivekit::ThrowUnknownArchiveException(env, exception);
    }
    return nullptr;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_vaultzip_archive_bridge_NativeArchiveBridgeImpl_nativeExtractEntry(
    JNIEnv* env,
    jobject /* thiz */,
    jstring primaryPath,
    jobjectArray partPaths,
    jstring entryPath,
    jstring outputDir,
    jcharArray passwordChars
) {
    try {
        const auto source = BuildSource(env, primaryPath, partPaths);
        const auto password = archivekit::JCharArrayToUtf16(env, passwordChars);
        return archivekit::ToJavaExtractSingleEntryResultDto(
            env,
            archivekit::ExtractEntry(
                source,
                archivekit::JStringToUtf8(env, entryPath),
                archivekit::JStringToUtf8(env, outputDir),
                password
            )
        );
    } catch (const archivekit::ArchiveException& exception) {
        archivekit::ThrowNativeArchiveException(env, exception);
    } catch (const std::exception& exception) {
        archivekit::ThrowUnknownArchiveException(env, exception);
    }
    return nullptr;
}
