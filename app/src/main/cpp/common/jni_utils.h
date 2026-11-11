#pragma once

// Shared JNI helpers used by every module: handle casts, exception throwing,
// string conversion and native method registration.

#include <jni.h>

#include <cstdint>
#include <string>

namespace pdfjni {

// Fully qualified name of the Kotlin exception thrown for PDFium failures.
// Constructor signature: (String message, int errorCode).
inline constexpr const char* kPdfiumExceptionClass =
    "com/example/pdf_reader/pdfium/PdfiumException";

// Converts a jlong coming from Kotlin into a native pointer/handle.
template <typename T>
inline T fromHandle(jlong handle) {
    return reinterpret_cast<T>(static_cast<intptr_t>(handle));
}

// Converts a native pointer/handle into a jlong passed to Kotlin.
template <typename T>
inline jlong toHandle(T pointer) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(pointer));
}

void throwIllegalState(JNIEnv* env, const char* message);
void throwIllegalArgument(JNIEnv* env, const char* message);

// Throws PdfiumException with an explicit PDFium error code.
void throwPdfium(JNIEnv* env, const char* message, unsigned long code);

// Throws PdfiumException using FPDF_GetLastError() as the error code.
void throwPdfiumLastError(JNIEnv* env, const char* message);

// Copies a Java string into a NUL-terminated UTF-16 buffer usable as
// FPDF_WIDESTRING. Returns false (with a pending exception) on failure.
bool toUtf16(JNIEnv* env, jstring value, std::u16string& out);

// Copies a Java string into modified UTF-8. Returns false (with a pending
// exception) on failure.
bool toUtf8(JNIEnv* env, jstring value, std::string& out);

// Creates double[4] = {left, top, right, bottom}. Returns nullptr on OOM.
jdoubleArray newRectArray(JNIEnv* env, double left, double top, double right,
                          double bottom);

// Registers native methods for the given class. Logs and returns false on
// failure.
bool registerNatives(JNIEnv* env, const char* className,
                     const JNINativeMethod* methods, int count);

}  // namespace pdfjni
