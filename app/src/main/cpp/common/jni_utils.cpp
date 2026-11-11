#include "common/jni_utils.h"

#include <android/log.h>

#include "fpdfview.h"

namespace pdfjni {
namespace {

constexpr const char* kLogTag = "pdf_engine";

void throwByName(JNIEnv* env, const char* className, const char* message) {
    if (env->ExceptionCheck()) return;  // Keep the first pending exception.
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) return;  // NoClassDefFoundError is now pending.
    env->ThrowNew(clazz, message);
    env->DeleteLocalRef(clazz);
}

}  // namespace

void throwIllegalState(JNIEnv* env, const char* message) {
    throwByName(env, "java/lang/IllegalStateException", message);
}

void throwIllegalArgument(JNIEnv* env, const char* message) {
    throwByName(env, "java/lang/IllegalArgumentException", message);
}

void throwPdfium(JNIEnv* env, const char* message, unsigned long code) {
    if (env->ExceptionCheck()) return;
    jclass clazz = env->FindClass(kPdfiumExceptionClass);
    if (clazz == nullptr) return;
    jmethodID ctor = env->GetMethodID(clazz, "<init>", "(Ljava/lang/String;I)V");
    if (ctor != nullptr) {
        jstring jmessage = env->NewStringUTF(message);
        if (jmessage != nullptr) {
            auto exception = static_cast<jthrowable>(
                env->NewObject(clazz, ctor, jmessage, static_cast<jint>(code)));
            if (exception != nullptr) {
                env->Throw(exception);
                env->DeleteLocalRef(exception);
            }
            env->DeleteLocalRef(jmessage);
        }
    }
    env->DeleteLocalRef(clazz);
}

void throwPdfiumLastError(JNIEnv* env, const char* message) {
    throwPdfium(env, message, FPDF_GetLastError());
}

bool toUtf16(JNIEnv* env, jstring value, std::u16string& out) {
    if (value == nullptr) {
        throwIllegalArgument(env, "String must not be null");
        return false;
    }
    const jsize length = env->GetStringLength(value);
    out.resize(static_cast<size_t>(length));  // u16string keeps a trailing NUL.
    env->GetStringRegion(value, 0, length, reinterpret_cast<jchar*>(out.data()));
    return !env->ExceptionCheck();
}

bool toUtf8(JNIEnv* env, jstring value, std::string& out) {
    if (value == nullptr) {
        throwIllegalArgument(env, "String must not be null");
        return false;
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return false;  // OutOfMemoryError pending.
    out.assign(chars);
    env->ReleaseStringUTFChars(value, chars);
    return true;
}

jdoubleArray newRectArray(JNIEnv* env, double left, double top, double right,
                          double bottom) {
    jdoubleArray array = env->NewDoubleArray(4);
    if (array == nullptr) return nullptr;
    const jdouble values[4] = {left, top, right, bottom};
    env->SetDoubleArrayRegion(array, 0, 4, values);
    return array;
}

bool registerNatives(JNIEnv* env, const char* className,
                     const JNINativeMethod* methods, int count) {
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Class not found: %s",
                            className);
        return false;
    }
    const jint result = env->RegisterNatives(clazz, methods, count);
    env->DeleteLocalRef(clazz);
    if (result != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "RegisterNatives failed for %s", className);
        return false;
    }
    return true;
}

}  // namespace pdfjni
