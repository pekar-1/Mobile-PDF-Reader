// Text search module: FPDFText_FindStart, FPDFText_FindNext
// (+ FindPrev, GetSchResultIndex, GetSchCount, FindClose).
// Kotlin side: com.example.pdf_reader.pdfium.jni.SearchNative

#include <jni.h>

#include <string>

#include "common/jni_utils.h"
#include "common/native_document.h"
#include "common/pdfium_lock.h"
#include "fpdf_text.h"
#include "modules.h"

namespace pdfjni {
namespace {

constexpr const char* kClass = "com/example/pdf_reader/pdfium/jni/SearchNative";
constexpr const char* kSearchClosed = "Search is closed";

// flags: FPDF_MATCHCASE | FPDF_MATCHWHOLEWORD | FPDF_CONSECUTIVE.
// startIndex: char index to start from, -1 for end of page.
jlong findStart(JNIEnv* env, jclass, jlong textPageHandle, jstring query,
                jint flags, jint startIndex) {
    std::u16string pattern;
    if (!toUtf16(env, query, pattern)) return 0;
    if (pattern.empty()) {
        throwIllegalArgument(env, "Query must not be empty");
        return 0;
    }

    PDFIUM_LOCK();
    auto textPage = requireHandle<FPDF_TEXTPAGE>(env, textPageHandle,
                                                 "Text page is closed");
    if (textPage == nullptr) return 0;
    FPDF_SCHHANDLE search = FPDFText_FindStart(
        textPage, reinterpret_cast<FPDF_WIDESTRING>(pattern.c_str()),
        static_cast<unsigned long>(flags), startIndex);
    if (search == nullptr) {
        throwPdfiumLastError(env, "Cannot start search");
        return 0;
    }
    return toHandle(search);
}

jboolean findNext(JNIEnv* env, jclass, jlong searchHandle) {
    PDFIUM_LOCK();
    auto search = requireHandle<FPDF_SCHHANDLE>(env, searchHandle, kSearchClosed);
    if (search == nullptr) return JNI_FALSE;
    return FPDFText_FindNext(search) ? JNI_TRUE : JNI_FALSE;
}

jboolean findPrev(JNIEnv* env, jclass, jlong searchHandle) {
    PDFIUM_LOCK();
    auto search = requireHandle<FPDF_SCHHANDLE>(env, searchHandle, kSearchClosed);
    if (search == nullptr) return JNI_FALSE;
    return FPDFText_FindPrev(search) ? JNI_TRUE : JNI_FALSE;
}

// Char index of the current match start.
jint getResultIndex(JNIEnv* env, jclass, jlong searchHandle) {
    PDFIUM_LOCK();
    auto search = requireHandle<FPDF_SCHHANDLE>(env, searchHandle, kSearchClosed);
    if (search == nullptr) return -1;
    return FPDFText_GetSchResultIndex(search);
}

// Char count of the current match.
jint getResultCount(JNIEnv* env, jclass, jlong searchHandle) {
    PDFIUM_LOCK();
    auto search = requireHandle<FPDF_SCHHANDLE>(env, searchHandle, kSearchClosed);
    if (search == nullptr) return 0;
    return FPDFText_GetSchCount(search);
}

// Must be called before FPDFText_ClosePage of the owning text page.
void findClose(JNIEnv*, jclass, jlong searchHandle) {
    PDFIUM_LOCK();
    auto search = fromHandle<FPDF_SCHHANDLE>(searchHandle);
    if (search != nullptr) FPDFText_FindClose(search);
}

const JNINativeMethod kMethods[] = {
    {"nativeFindStart", "(JLjava/lang/String;II)J", reinterpret_cast<void*>(findStart)},
    {"nativeFindNext", "(J)Z", reinterpret_cast<void*>(findNext)},
    {"nativeFindPrev", "(J)Z", reinterpret_cast<void*>(findPrev)},
    {"nativeGetSchResultIndex", "(J)I", reinterpret_cast<void*>(getResultIndex)},
    {"nativeGetSchCount", "(J)I", reinterpret_cast<void*>(getResultCount)},
    {"nativeFindClose", "(J)V", reinterpret_cast<void*>(findClose)},
};

}  // namespace

bool registerTextSearch(JNIEnv* env) {
    return registerNatives(env, kClass, kMethods,
                           static_cast<int>(sizeof(kMethods) / sizeof(kMethods[0])));
}

}  // namespace pdfjni
