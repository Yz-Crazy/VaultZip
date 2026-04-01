#pragma once

#include <jni.h>

#include <optional>
#include <string>
#include <vector>

#include "../core/ArchiveTypes.h"

namespace archivekit {

std::string JStringToUtf8(JNIEnv* env, jstring value);
std::vector<std::string> JObjectArrayToStrings(JNIEnv* env, jobjectArray array);
std::optional<std::u16string> JCharArrayToUtf16(JNIEnv* env, jcharArray array);

jstring ToJavaString(JNIEnv* env, const std::string& value);
jobject ToJavaEntryList(JNIEnv* env, const std::vector<ArchiveEntryData>& entries);
jobject ToJavaExtractResultDto(JNIEnv* env, const ExtractResultData& result);
jobject ToJavaExtractSingleEntryResultDto(JNIEnv* env, const ExtractSingleEntryResultData& result);

}  // namespace archivekit
