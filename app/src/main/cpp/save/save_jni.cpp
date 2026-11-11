// Save module: FPDF_SaveAsCopy to a file descriptor.
// Kotlin side: com.example.pdf_reader.pdfium.jni.SaveNative

#include <jni.h>

#include "common/fd_io.h"
#include "common/jni_utils.h"
#include "common/native_document.h"
#include "common/pdfium_lock.h"
#include "fpdf_save.h"
#include "modules.h"

namespace pdfjni {
namespace {

constexpr const char* kClass = "com/example/pdf_reader/pdfium/jni/SaveNative";

// fd: writable, empty/truncated descriptor. Not closed here. Must not be the
// descriptor the document is being read from.
// flags: 0, FPDF_INCREMENTAL, FPDF_NO_INCREMENTAL, optionally | FPDF_REMOVE_SECURITY.
jboolean saveAsCopy(JNIEnv* env, jclass, jlong documentHandle, jint fd,
                    jint flags) {
    if (fd < 0) {
        throwIllegalArgument(env, "Invalid file descriptor");
        return JNI_FALSE;
    }
    if ((flags & FPDF_INCREMENTAL) && (flags & FPDF_NO_INCREMENTAL)) {
        throwIllegalArgument(env, "INCREMENTAL and NO_INCREMENTAL are exclusive");
        return JNI_FALSE;
    }

    PDFIUM_LOCK();
    FPDF_DOCUMENT document = requireDocument(env, documentHandle);
    if (document == nullptr) return JNI_FALSE;

    FdFileWrite writer(fd);
    const FPDF_BOOL ok = FPDF_SaveAsCopy(document, &writer.base,
                                         static_cast<FPDF_DWORD>(flags));
    return (ok && !writer.failed) ? JNI_TRUE : JNI_FALSE;
}

const JNINativeMethod kMethods[] = {
    {"nativeSaveAsCopy", "(JII)Z", reinterpret_cast<void*>(saveAsCopy)},
};

}  // namespace

bool registerSave(JNIEnv* env) {
    return registerNatives(env, kClass, kMethods,
                           static_cast<int>(sizeof(kMethods) / sizeof(kMethods[0])));
}

}  // namespace pdfjni
