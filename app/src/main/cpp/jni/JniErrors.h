#pragma once

#include <jni.h>

#include <exception>

#include "../core/ArchiveTypes.h"

namespace archivekit {

void ThrowNativeArchiveException(JNIEnv* env, const ArchiveException& exception);
void ThrowUnknownArchiveException(JNIEnv* env, const std::exception& exception);

}  // namespace archivekit
