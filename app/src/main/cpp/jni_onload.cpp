#include <jni.h>

#include "modules.h"

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    const bool ok = pdfjni::registerCore(env) &&
                    pdfjni::registerPageEdit(env) &&
                    pdfjni::registerImport(env) &&
                    pdfjni::registerTextPage(env) &&
                    pdfjni::registerTextSearch(env) &&
                    pdfjni::registerSave(env) &&
                    pdfjni::registerRender(env);

    return ok ? JNI_VERSION_1_6 : JNI_ERR;
}
