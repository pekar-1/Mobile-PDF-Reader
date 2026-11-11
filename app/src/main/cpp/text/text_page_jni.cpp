// Text page module: FPDFText_LoadPage, FPDFText_ClosePage, FPDFText_GetText,
// FPDFText_GetCharBox, FPDFText_GetLooseCharBox, FPDFText_GetCharIndexAtPos,
// FPDFText_GetRect (+ CountChars/CountRects helpers).
// All coordinates are PDF page space: points, origin at bottom-left.
// Rect arrays are always {left, top, right, bottom}.
// Kotlin side: com.example.pdf_reader.pdfium.jni.TextNative

#include <jni.h>

#include <vector>

#include "common/jni_utils.h"
#include "common/native_document.h"
#include "common/pdfium_lock.h"
#include "fpdf_text.h"
#include "modules.h"

namespace pdfjni {
namespace {

constexpr const char* kClass = "com/example/pdf_reader/pdfium/jni/TextNative";
constexpr const char* kTextPageClosed = "Text page is closed";

jlong loadTextPage(JNIEnv* env, jclass, jlong pageHandle) {
    PDFIUM_LOCK();
    auto page = requireHandle<FPDF_PAGE>(env, pageHandle, "Page is closed");
    if (page == nullptr) return 0;
    FPDF_TEXTPAGE textPage = FPDFText_LoadPage(page);
    if (textPage == nullptr) {
        throwPdfiumLastError(env, "Cannot load text page");
        return 0;
    }
    return toHandle(textPage);
}

// Must be called before FPDF_ClosePage of the owning page.
void closeTextPage(JNIEnv*, jclass, jlong textPageHandle) {
    PDFIUM_LOCK();
    auto textPage = fromHandle<FPDF_TEXTPAGE>(textPageHandle);
    if (textPage != nullptr) FPDFText_ClosePage(textPage);
}

jint countChars(JNIEnv* env, jclass, jlong textPageHandle) {
    PDFIUM_LOCK();
    auto textPage = requireHandle<FPDF_TEXTPAGE>(env, textPageHandle, kTextPageClosed);
    if (textPage == nullptr) return 0;
    return FPDFText_CountChars(textPage);
}

jstring getText(JNIEnv* env, jclass, jlong textPageHandle, jint startIndex,
                jint count) {
    if (startIndex < 0 || count < 0) {
        throwIllegalArgument(env, "startIndex and count must be >= 0");
        return nullptr;
    }
    PDFIUM_LOCK();
    auto textPage = requireHandle<FPDF_TEXTPAGE>(env, textPageHandle, kTextPageClosed);
    if (textPage == nullptr) return nullptr;
    if (count == 0) return env->NewStringUTF("");

    // PDFium writes up to `count` chars plus a NUL terminator.
    std::vector<unsigned short> buffer(static_cast<size_t>(count) + 1, 0);
    const int written = FPDFText_GetText(textPage, startIndex, count, buffer.data());
    const jsize length = written > 0 ? written - 1 : 0;  // Drop the terminator.
    return env->NewString(reinterpret_cast<const jchar*>(buffer.data()), length);
}

// Returns {left, top, right, bottom} or null when the index is invalid.
jdoubleArray getCharBox(JNIEnv* env, jclass, jlong textPageHandle, jint index) {
    PDFIUM_LOCK();
    auto textPage = requireHandle<FPDF_TEXTPAGE>(env, textPageHandle, kTextPageClosed);
    if (textPage == nullptr) return nullptr;
    double left = 0, right = 0, bottom = 0, top = 0;
    // Note PDFium's argument order: left, right, bottom, top.
    if (!FPDFText_GetCharBox(textPage, index, &left, &right, &bottom, &top)) {
        return nullptr;
    }
    return newRectArray(env, left, top, right, bottom);
}

// Returns {left, top, right, bottom} of the loose box (full font ascent and
// descent, stable per line), or null when the index is invalid.
jdoubleArray getLooseCharBox(JNIEnv* env, jclass, jlong textPageHandle, jint index) {
    PDFIUM_LOCK();
    auto textPage = requireHandle<FPDF_TEXTPAGE>(env, textPageHandle, kTextPageClosed);
    if (textPage == nullptr) return nullptr;
    FS_RECTF rect{};
    if (!FPDFText_GetLooseCharBox(textPage, index, &rect)) return nullptr;
    return newRectArray(env, rect.left, rect.top, rect.right, rect.bottom);
}

// Char index at or near page point (x, y), tolerances in points.
// Returns -1 when nothing is near, -3 on error.
jint getCharIndexAtPos(JNIEnv* env, jclass, jlong textPageHandle, jdouble x,
                       jdouble y, jdouble xTolerance, jdouble yTolerance) {
    PDFIUM_LOCK();
    auto textPage = requireHandle<FPDF_TEXTPAGE>(env, textPageHandle, kTextPageClosed);
    if (textPage == nullptr) return -3;
    return FPDFText_GetCharIndexAtPos(textPage, x, y, xTolerance, yTolerance);
}

// Computes rectangles for a char range. Must precede getRect().
jint countRects(JNIEnv* env, jclass, jlong textPageHandle, jint startIndex,
                jint count) {
    PDFIUM_LOCK();
    auto textPage = requireHandle<FPDF_TEXTPAGE>(env, textPageHandle, kTextPageClosed);
    if (textPage == nullptr) return 0;
    return FPDFText_CountRects(textPage, startIndex, count);
}

// Returns {left, top, right, bottom} of a rect computed by the last
// countRects() call, or null when the index is invalid.
jdoubleArray getRect(JNIEnv* env, jclass, jlong textPageHandle, jint rectIndex) {
    PDFIUM_LOCK();
    auto textPage = requireHandle<FPDF_TEXTPAGE>(env, textPageHandle, kTextPageClosed);
    if (textPage == nullptr) return nullptr;
    double left = 0, top = 0, right = 0, bottom = 0;
    if (!FPDFText_GetRect(textPage, rectIndex, &left, &top, &right, &bottom)) {
        return nullptr;
    }
    return newRectArray(env, left, top, right, bottom);
}

// Atomic countRects + getRect under one lock. Returns a flat array
// {l0, t0, r0, b0, l1, t1, ...}. Safe against interleaved calls.
jdoubleArray getRects(JNIEnv* env, jclass, jlong textPageHandle, jint startIndex,
                      jint count) {
    PDFIUM_LOCK();
    auto textPage = requireHandle<FPDF_TEXTPAGE>(env, textPageHandle, kTextPageClosed);
    if (textPage == nullptr) return nullptr;
    const int rectCount = FPDFText_CountRects(textPage, startIndex, count);
    std::vector<jdouble> values;
    values.reserve(static_cast<size_t>(rectCount > 0 ? rectCount : 0) * 4);
    for (int i = 0; i < rectCount; ++i) {
        double left = 0, top = 0, right = 0, bottom = 0;
        if (FPDFText_GetRect(textPage, i, &left, &top, &right, &bottom)) {
            values.insert(values.end(), {left, top, right, bottom});
        }
    }
    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(values.size()));
    if (result == nullptr) return nullptr;
    env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(values.size()),
                              values.data());
    return result;
}

const JNINativeMethod kMethods[] = {
    {"nativeLoadTextPage", "(J)J", reinterpret_cast<void*>(loadTextPage)},
    {"nativeCloseTextPage", "(J)V", reinterpret_cast<void*>(closeTextPage)},
    {"nativeCountChars", "(J)I", reinterpret_cast<void*>(countChars)},
    {"nativeGetText", "(JII)Ljava/lang/String;", reinterpret_cast<void*>(getText)},
    {"nativeGetCharBox", "(JI)[D", reinterpret_cast<void*>(getCharBox)},
    {"nativeGetLooseCharBox", "(JI)[D", reinterpret_cast<void*>(getLooseCharBox)},
    {"nativeGetCharIndexAtPos", "(JDDDD)I", reinterpret_cast<void*>(getCharIndexAtPos)},
    {"nativeCountRects", "(JII)I", reinterpret_cast<void*>(countRects)},
    {"nativeGetRect", "(JI)[D", reinterpret_cast<void*>(getRect)},
    {"nativeGetRects", "(JII)[D", reinterpret_cast<void*>(getRects)},
};

}  // namespace

bool registerTextPage(JNIEnv* env) {
    return registerNatives(env, kClass, kMethods,
                           static_cast<int>(sizeof(kMethods) / sizeof(kMethods[0])));
}

}  // namespace pdfjni
