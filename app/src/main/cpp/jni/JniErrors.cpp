#include "JniErrors.h"

namespace archivekit {
namespace {

void ThrowException(JNIEnv* env, const char* code, const char* message) {
    jclass exceptionClass = env->FindClass("com/vaultzip/archive/bridge/NativeArchiveException");
    jmethodID ctor = env->GetMethodID(exceptionClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
    jstring codeValue = env->NewStringUTF(code);
    jstring messageValue = env->NewStringUTF(message);
    jobject exception = env->NewObject(exceptionClass, ctor, codeValue, messageValue);
    env->Throw(static_cast<jthrowable>(exception));
    env->DeleteLocalRef(codeValue);
    env->DeleteLocalRef(messageValue);
    env->DeleteLocalRef(exception);
}

}  // namespace

void ThrowNativeArchiveException(JNIEnv* env, const ArchiveException& exception) {
    ThrowException(env, exception.code().c_str(), exception.what());
}

void ThrowUnknownArchiveException(JNIEnv* env, const std::exception& exception) {
    ThrowException(env, "ERR_UNKNOWN", exception.what());
}

}  // namespace archivekit
