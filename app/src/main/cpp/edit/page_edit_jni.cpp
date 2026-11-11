// Page edit module: FPDFPage_Delete, FPDFPage_SetRotation.
// Kotlin side: com.example.pdf_reader.pdfium.jni.PageEditNative

#include <jni.h>

#include "common/jni_utils.h"
#include "common/native_document.h"
#include "common/pdfium_lock.h"
#include "fpdf_edit.h"
#include "fpdfview.h"
#include "modules.h"

namespace pdfjni {
namespace {

constexpr const char* kClass = "com/example/pdf_reader/pdfium/jni/PageEditNative";

// The page at `index` must not be loaded (FPDF_PAGE open) when deleted.
// The Kotlin wrapper enforces this.
void deletePage(JNIEnv* env, jclass, jlong documentHandle, jint index) {
    PDFIUM_LOCK();
    FPDF_DOCUMENT document = requireDocument(env, documentHandle);
    if (document == nullptr) return;
    if (index < 0 || index >= FPDF_GetPageCount(document)) {
        throwIllegalArgument(env, "Page index out of range");
        return;
    }
    FPDFPage_Delete(document, index);
}

// rotation: 0 = 0°, 1 = 90°, 2 = 180°, 3 = 270° clockwise.
void setRotation(JNIEnv* env, jclass, jlong pageHandle, jint rotation) {
    if (rotation < 0 || rotation > 3) {
        throwIllegalArgument(env, "Rotation must be in 0..3");
        return;
    }
    PDFIUM_LOCK();
    auto page = requireHandle<FPDF_PAGE>(env, pageHandle, "Page is closed");
    if (page == nullptr) return;
    FPDFPage_SetRotation(page, rotation);
}

const JNINativeMethod kMethods[] = {
    {"nativeDeletePage", "(JI)V", reinterpret_cast<void*>(deletePage)},
    {"nativeSetRotation", "(JI)V", reinterpret_cast<void*>(setRotation)},
};

}  // namespace

bool registerPageEdit(JNIEnv* env) {
    return registerNatives(env, kClass, kMethods,
                           static_cast<int>(sizeof(kMethods) / sizeof(kMethods[0])));
}

}  // namespace pdfjni
