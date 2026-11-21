#include <jni.h>

#include <string>

#include "common/jni_utils.h"
#include "common/native_document.h"
#include "common/pdfium_lock.h"
#include "fpdf_ppo.h"
#include "fpdfview.h"
#include "modules.h"

namespace pdfjni {
namespace {

constexpr const char* kClass = "com/example/pdf_reader/pdfium/jni/ImportNative";

// pageRange: 1-based, e.g. "1,3,5-7". null imports all pages.
// insertIndex: 0-based position in the destination document.
// The source document must stay open until the destination is saved.
jboolean importPages(JNIEnv* env, jclass, jlong destHandle, jlong srcHandle,
                     jstring pageRange, jint insertIndex) {
    std::string range;
    if (pageRange != nullptr && !toUtf8(env, pageRange, range)) return JNI_FALSE;

    PDFIUM_LOCK();
    FPDF_DOCUMENT dest = requireDocument(env, destHandle);
    if (dest == nullptr) return JNI_FALSE;
    FPDF_DOCUMENT src = requireDocument(env, srcHandle);
    if (src == nullptr) return JNI_FALSE;
    if (dest == src) {
        throwIllegalArgument(env, "Source and destination must differ");
        return JNI_FALSE;
    }
    if (insertIndex < 0 || insertIndex > FPDF_GetPageCount(dest)) {
        throwIllegalArgument(env, "Insert index out of range");
        return JNI_FALSE;
    }

    const FPDF_BOOL ok = FPDF_ImportPages(
        dest, src, pageRange != nullptr ? range.c_str() : nullptr, insertIndex);
    return ok ? JNI_TRUE : JNI_FALSE;
}

const JNINativeMethod kMethods[] = {
    {"nativeImportPages", "(JJLjava/lang/String;I)Z",
     reinterpret_cast<void*>(importPages)},
};

}  // namespace

bool registerImport(JNIEnv* env) {
    return registerNatives(env, kClass, kMethods,
                           static_cast<int>(sizeof(kMethods) / sizeof(kMethods[0])));
}

}  // namespace pdfjni
