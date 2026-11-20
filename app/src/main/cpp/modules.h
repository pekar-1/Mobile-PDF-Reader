#pragma once

// Called from JNI_OnLoad, false = loading fails.

#include <jni.h>

namespace pdfjni {

bool registerCore(JNIEnv* env);
bool registerPageEdit(JNIEnv* env);
bool registerImport(JNIEnv* env);
bool registerTextPage(JNIEnv* env);
bool registerTextSearch(JNIEnv* env);
bool registerSave(JNIEnv* env);
bool registerRender(JNIEnv* env);
bool registerLink(JNIEnv* env);

}  // namespace pdfjni
