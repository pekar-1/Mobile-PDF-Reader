#pragma once

// PDFium is not thread-safe. Every JNI entry point that touches PDFium must
// hold this lock for the whole call.

#include <mutex>

namespace pdfjni {

inline std::recursive_mutex& pdfiumMutex() {
    static std::recursive_mutex mutex;
    return mutex;
}

using PdfiumGuard = std::lock_guard<std::recursive_mutex>;

}  // namespace pdfjni

#define PDFIUM_LOCK() ::pdfjni::PdfiumGuard pdfium_guard_(::pdfjni::pdfiumMutex())
