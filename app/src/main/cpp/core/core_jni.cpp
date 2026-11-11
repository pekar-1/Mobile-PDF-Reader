// Core module: library lifetime, opening/creating/closing documents and
// loading pages. Provides the handles every other module works on.
// Kotlin side: com.example.pdf_reader.pdfium.jni.CoreNative

#include <jni.h>

#include <algorithm>
#include <cmath>
#include <memory>
#include <string>
#include <vector>

#include "common/fd_io.h"
#include "common/jni_utils.h"
#include "common/native_document.h"
#include "common/pdfium_lock.h"
#include "fpdf_edit.h"
#include "fpdfview.h"
#include "modules.h"

namespace pdfjni {
namespace {

constexpr const char* kClass = "com/example/pdf_reader/pdfium/jni/CoreNative";

void initLibrary(JNIEnv*, jclass) {
    PDFIUM_LOCK();
    FPDF_LIBRARY_CONFIG config{};
    config.version = 2;
    config.m_pUserFontPaths = nullptr;
    config.m_pIsolate = nullptr;
    config.m_v8EmbedderSlot = 0;
    FPDF_InitLibraryWithConfig(&config);
}

void destroyLibrary(JNIEnv*, jclass) {
    PDFIUM_LOCK();
    FPDF_DestroyLibrary();
}

jlong openDocument(JNIEnv* env, jclass, jint fd, jstring password) {
    std::string passwordUtf8;
    if (password != nullptr && !toUtf8(env, password, passwordUtf8)) return 0;

    auto native = std::make_unique<NativeDocument>();
    native->source = FdFileAccess::create(fd);
    if (!native->source) {
        throwPdfium(env, "Cannot read file descriptor", FPDF_ERR_FILE);
        return 0;
    }

    PDFIUM_LOCK();
    native->document = FPDF_LoadCustomDocument(
        native->source->access(),
        password != nullptr ? passwordUtf8.c_str() : nullptr);
    if (native->document == nullptr) {
        throwPdfiumLastError(env, "Cannot open document");
        return 0;
    }
    return toHandle(native.release());
}

jlong createNewDocument(JNIEnv* env, jclass) {
    auto native = std::make_unique<NativeDocument>();
    PDFIUM_LOCK();
    native->document = FPDF_CreateNewDocument();
    if (native->document == nullptr) {
        throwPdfiumLastError(env, "Cannot create document");
        return 0;
    }
    return toHandle(native.release());
}

void closeDocument(JNIEnv*, jclass, jlong documentHandle) {
    PDFIUM_LOCK();
    delete fromHandle<NativeDocument*>(documentHandle);
}

jint getPageCount(JNIEnv* env, jclass, jlong documentHandle) {
    PDFIUM_LOCK();
    FPDF_DOCUMENT document = requireDocument(env, documentHandle);
    if (document == nullptr) return 0;
    return FPDF_GetPageCount(document);
}

jlong loadPage(JNIEnv* env, jclass, jlong documentHandle, jint index) {
    PDFIUM_LOCK();
    FPDF_DOCUMENT document = requireDocument(env, documentHandle);
    if (document == nullptr) return 0;
    if (index < 0 || index >= FPDF_GetPageCount(document)) {
        throwIllegalArgument(env, "Page index out of range");
        return 0;
    }
    FPDF_PAGE page = FPDF_LoadPage(document, index);
    if (page == nullptr) {
        throwPdfium(env, "Cannot load page", FPDF_ERR_PAGE);
        return 0;
    }
    return toHandle(page);
}

void closePage(JNIEnv*, jclass, jlong pageHandle) {
    PDFIUM_LOCK();
    FPDF_PAGE page = fromHandle<FPDF_PAGE>(pageHandle);
    if (page != nullptr) FPDF_ClosePage(page);
}

jint getPageRotation(JNIEnv* env, jclass, jlong pageHandle) {
    PDFIUM_LOCK();
    auto page = requireHandle<FPDF_PAGE>(env, pageHandle, "Page is closed");
    if (page == nullptr) return 0;
    return FPDFPage_GetRotation(page);
}

// Returns double[2] = {width, height} in PDF points (rotation applied).
jdoubleArray getPageSize(JNIEnv* env, jclass, jlong pageHandle) {
    PDFIUM_LOCK();
    auto page = requireHandle<FPDF_PAGE>(env, pageHandle, "Page is closed");
    if (page == nullptr) return nullptr;
    jdoubleArray result = env->NewDoubleArray(2);
    if (result == nullptr) return nullptr;
    const jdouble size[2] = {FPDF_GetPageWidthF(page), FPDF_GetPageHeightF(page)};
    env->SetDoubleArrayRegion(result, 0, 2, size);
    return result;
}

// Returns double[2] = {width, height} in PDF points without loading the page.
jdoubleArray getPageSizeByIndex(JNIEnv* env, jclass, jlong documentHandle,
                                jint index) {
    PDFIUM_LOCK();
    FPDF_DOCUMENT document = requireDocument(env, documentHandle);
    if (document == nullptr) return nullptr;
    FS_SIZEF size{};
    if (!FPDF_GetPageSizeByIndexF(document, index, &size)) {
        throwIllegalArgument(env, "Page index out of range");
        return nullptr;
    }
    jdoubleArray result = env->NewDoubleArray(2);
    if (result == nullptr) return nullptr;
    const jdouble values[2] = {size.width, size.height};
    env->SetDoubleArrayRegion(result, 0, 2, values);
    return result;
}

// Virtual device used for page <-> normalized conversion. FPDF_PageToDevice
// works in int pixels; 2^20 per axis keeps the error around 1e-6.
constexpr int kNormDevice = 1 << 20;

// Maps flat page-space rects {l, t, r, b, ...} (points, origin bottom-left)
// to normalized rects {l, t, r, b, ...} (0..1, origin top-left), using the
// same display matrix as rendering with rotate = 0. Corners are re-ordered,
// since page rotation can swap them.
jdoubleArray pageToNormRects(JNIEnv* env, jclass, jlong pageHandle,
                             jdoubleArray pageRects) {
    if (pageRects == nullptr) {
        throwIllegalArgument(env, "pageRects must not be null");
        return nullptr;
    }
    const jsize length = env->GetArrayLength(pageRects);
    if (length % 4 != 0) {
        throwIllegalArgument(env, "pageRects length must be a multiple of 4");
        return nullptr;
    }
    std::vector<jdouble> values(static_cast<size_t>(length));
    env->GetDoubleArrayRegion(pageRects, 0, length, values.data());

    PDFIUM_LOCK();
    auto page = requireHandle<FPDF_PAGE>(env, pageHandle, "Page is closed");
    if (page == nullptr) return nullptr;
    constexpr double kScale = 1.0 / kNormDevice;
    for (jsize i = 0; i < length; i += 4) {
        int x1 = 0, y1 = 0, x2 = 0, y2 = 0;
        FPDF_PageToDevice(page, 0, 0, kNormDevice, kNormDevice, 0, values[i],
                          values[i + 1], &x1, &y1);
        FPDF_PageToDevice(page, 0, 0, kNormDevice, kNormDevice, 0, values[i + 2],
                          values[i + 3], &x2, &y2);
        values[i] = std::min(x1, x2) * kScale;
        values[i + 1] = std::min(y1, y2) * kScale;
        values[i + 2] = std::max(x1, x2) * kScale;
        values[i + 3] = std::max(y1, y2) * kScale;
    }
    jdoubleArray result = env->NewDoubleArray(length);
    if (result == nullptr) return nullptr;
    env->SetDoubleArrayRegion(result, 0, length, values.data());
    return result;
}

// Maps a normalized point (0..1, origin top-left) to page space.
// Returns double[2] = {x, y} in points.
jdoubleArray normToPage(JNIEnv* env, jclass, jlong pageHandle, jdouble nx,
                        jdouble ny) {
    PDFIUM_LOCK();
    auto page = requireHandle<FPDF_PAGE>(env, pageHandle, "Page is closed");
    if (page == nullptr) return nullptr;
    double x = 0, y = 0;
    const int deviceX = static_cast<int>(std::lround(nx * kNormDevice));
    const int deviceY = static_cast<int>(std::lround(ny * kNormDevice));
    if (!FPDF_DeviceToPage(page, 0, 0, kNormDevice, kNormDevice, 0, deviceX,
                           deviceY, &x, &y)) {
        throwPdfiumLastError(env, "Cannot map point to page");
        return nullptr;
    }
    jdoubleArray result = env->NewDoubleArray(2);
    if (result == nullptr) return nullptr;
    const jdouble values[2] = {x, y};
    env->SetDoubleArrayRegion(result, 0, 2, values);
    return result;
}

jint getLastError(JNIEnv*, jclass) {
    PDFIUM_LOCK();
    return static_cast<jint>(FPDF_GetLastError());
}

const JNINativeMethod kMethods[] = {
    {"nativeInitLibrary", "()V", reinterpret_cast<void*>(initLibrary)},
    {"nativeDestroyLibrary", "()V", reinterpret_cast<void*>(destroyLibrary)},
    {"nativeOpenDocument", "(ILjava/lang/String;)J",
     reinterpret_cast<void*>(openDocument)},
    {"nativeCreateNewDocument", "()J", reinterpret_cast<void*>(createNewDocument)},
    {"nativeCloseDocument", "(J)V", reinterpret_cast<void*>(closeDocument)},
    {"nativeGetPageCount", "(J)I", reinterpret_cast<void*>(getPageCount)},
    {"nativeLoadPage", "(JI)J", reinterpret_cast<void*>(loadPage)},
    {"nativeClosePage", "(J)V", reinterpret_cast<void*>(closePage)},
    {"nativeGetPageRotation", "(J)I", reinterpret_cast<void*>(getPageRotation)},
    {"nativeGetPageSize", "(J)[D", reinterpret_cast<void*>(getPageSize)},
    {"nativeGetPageSizeByIndex", "(JI)[D", reinterpret_cast<void*>(getPageSizeByIndex)},
    {"nativePageToNormRects", "(J[D)[D", reinterpret_cast<void*>(pageToNormRects)},
    {"nativeNormToPage", "(JDD)[D", reinterpret_cast<void*>(normToPage)},
    {"nativeGetLastError", "()I", reinterpret_cast<void*>(getLastError)},
};

}  // namespace

bool registerCore(JNIEnv* env) {
    return registerNatives(env, kClass, kMethods,
                           static_cast<int>(sizeof(kMethods) / sizeof(kMethods[0])));
}

}  // namespace pdfjni
