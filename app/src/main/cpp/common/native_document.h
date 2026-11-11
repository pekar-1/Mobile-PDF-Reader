#pragma once

// Native document wrapper. Kotlin holds a pointer to NativeDocument (not the
// raw FPDF_DOCUMENT), so the read source lives exactly as long as the document.

#include <jni.h>

#include <memory>

#include "common/fd_io.h"
#include "common/jni_utils.h"
#include "fpdfview.h"

namespace pdfjni {

struct NativeDocument {
    // Declared first, destroyed last: PDFium may read from it until
    // FPDF_CloseDocument returns. Null for documents created in memory.
    std::unique_ptr<FdFileAccess> source;
    FPDF_DOCUMENT document = nullptr;

    NativeDocument() = default;
    NativeDocument(const NativeDocument&) = delete;
    NativeDocument& operator=(const NativeDocument&) = delete;

    ~NativeDocument() {
        if (document != nullptr) FPDF_CloseDocument(document);
    }
};

// Resolves a document handle. Throws IllegalStateException and returns
// nullptr when the handle is 0.
inline FPDF_DOCUMENT requireDocument(JNIEnv* env, jlong handle) {
    auto* native = fromHandle<NativeDocument*>(handle);
    if (native == nullptr || native->document == nullptr) {
        throwIllegalState(env, "Document is closed");
        return nullptr;
    }
    return native->document;
}

// Resolves any non-document handle (page, text page, search). Throws
// IllegalStateException and returns nullptr when the handle is 0.
template <typename T>
inline T requireHandle(JNIEnv* env, jlong handle, const char* what) {
    T value = fromHandle<T>(handle);
    if (value == nullptr) throwIllegalState(env, what);
    return value;
}

}  // namespace pdfjni
