#pragma once

// Registration entry points of every JNI module. Each returns false when
// registration fails; JNI_OnLoad then aborts loading of the library.

#include <jni.h>

namespace pdfjni {

bool registerCore(JNIEnv* env);       // core/core_jni.cpp
bool registerPageEdit(JNIEnv* env);   // edit/page_edit_jni.cpp
bool registerImport(JNIEnv* env);     // ppo/import_pages_jni.cpp
bool registerTextPage(JNIEnv* env);   // text/text_page_jni.cpp
bool registerTextSearch(JNIEnv* env); // text/text_search_jni.cpp
bool registerSave(JNIEnv* env);       // save/save_jni.cpp
bool registerRender(JNIEnv* env);     // render/render_jni.cpp

}  // namespace pdfjni
