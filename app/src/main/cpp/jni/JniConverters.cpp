#include "JniConverters.h"

namespace archivekit {
namespace {

jobject BoxLong(JNIEnv* env, const std::optional<long long>& value) {
    if (!value.has_value()) {
        return nullptr;
    }

    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID ctor = env->GetMethodID(longClass, "<init>", "(J)V");
    return env->NewObject(longClass, ctor, static_cast<jlong>(*value));
}

}  // namespace

std::string JStringToUtf8(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars != nullptr ? chars : "";
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

std::vector<std::string> JObjectArrayToStrings(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> values;
    if (array == nullptr) {
        return values;
    }

    const jsize size = env->GetArrayLength(array);
    values.reserve(static_cast<size_t>(size));
    for (jsize index = 0; index < size; ++index) {
        auto* item = static_cast<jstring>(env->GetObjectArrayElement(array, index));
        values.push_back(JStringToUtf8(env, item));
        env->DeleteLocalRef(item);
    }
    return values;
}

std::optional<std::u16string> JCharArrayToUtf16(JNIEnv* env, jcharArray array) {
    if (array == nullptr) {
        return std::nullopt;
    }

    const jsize size = env->GetArrayLength(array);
    jboolean isCopy = JNI_FALSE;
    jchar* chars = env->GetCharArrayElements(array, &isCopy);
    if (chars == nullptr) {
        return std::u16string();
    }

    std::u16string result(reinterpret_cast<char16_t*>(chars), static_cast<size_t>(size));
    env->ReleaseCharArrayElements(array, chars, JNI_ABORT);
    return result;
}

jstring ToJavaString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

jobject ToJavaEntryList(JNIEnv* env, const std::vector<ArchiveEntryData>& entries) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID listCtor = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID addMethod = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    jclass entryClass = env->FindClass("com/vaultzip/archive/bridge/NativeArchiveEntryDto");
    jmethodID entryCtor = env->GetMethodID(
        entryClass,
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/Long;Ljava/lang/Long;Ljava/lang/Long;Z)V"
    );

    jobject list = env->NewObject(arrayListClass, listCtor);
    for (const auto& entry : entries) {
        jstring path = ToJavaString(env, entry.path);
        jstring name = ToJavaString(env, entry.name);
        jobject compressed = BoxLong(env, entry.compressedSize);
        jobject uncompressed = BoxLong(env, entry.uncompressedSize);
        jobject modified = BoxLong(env, entry.modifiedAtMillis);

        jobject dto = env->NewObject(
            entryClass,
            entryCtor,
            path,
            name,
            static_cast<jboolean>(entry.isDirectory),
            compressed,
            uncompressed,
            modified,
            static_cast<jboolean>(entry.encrypted)
        );

        env->CallBooleanMethod(list, addMethod, dto);

        env->DeleteLocalRef(path);
        env->DeleteLocalRef(name);
        if (compressed != nullptr) {
            env->DeleteLocalRef(compressed);
        }
        if (uncompressed != nullptr) {
            env->DeleteLocalRef(uncompressed);
        }
        if (modified != nullptr) {
            env->DeleteLocalRef(modified);
        }
        env->DeleteLocalRef(dto);
    }

    return list;
}

jobject ToJavaExtractResultDto(JNIEnv* env, const ExtractResultData& result) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID listCtor = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID addMethod = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    jobject failedEntries = env->NewObject(arrayListClass, listCtor);
    for (const auto& failedEntry : result.failedEntries) {
        jstring value = ToJavaString(env, failedEntry);
        env->CallBooleanMethod(failedEntries, addMethod, value);
        env->DeleteLocalRef(value);
    }

    jclass resultClass = env->FindClass("com/vaultzip/archive/bridge/NativeExtractResultDto");
    jmethodID resultCtor = env->GetMethodID(resultClass, "<init>", "(ILjava/util/List;Ljava/lang/String;)V");
    jstring outputPath = result.outputPath.empty() ? nullptr : ToJavaString(env, result.outputPath);
    jobject dto = env->NewObject(
        resultClass,
        resultCtor,
        static_cast<jint>(result.extractedCount),
        failedEntries,
        outputPath
    );

    env->DeleteLocalRef(failedEntries);
    if (outputPath != nullptr) {
        env->DeleteLocalRef(outputPath);
    }
    return dto;
}

jobject ToJavaExtractSingleEntryResultDto(JNIEnv* env, const ExtractSingleEntryResultData& result) {
    jclass resultClass = env->FindClass("com/vaultzip/archive/bridge/NativeExtractSingleEntryResultDto");
    jmethodID resultCtor = env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;)V");
    jstring outputPath = ToJavaString(env, result.extractedFilePath);
    jobject dto = env->NewObject(resultClass, resultCtor, outputPath);
    env->DeleteLocalRef(outputPath);
    return dto;
}

}  // namespace archivekit
