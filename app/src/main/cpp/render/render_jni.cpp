// Render module: progressive, cancellable rendering of a page into an
// Android Bitmap (FPDF_RenderPageBitmap_Start / FPDF_RenderPage_Continue).
// Kotlin side: com.example.pdf_reader.pdfium.jni.RenderNative

#include <android/bitmap.h>
#include <jni.h>

#include <atomic>
#include <cstddef>

#include "common/jni_utils.h"
#include "common/native_document.h"
#include "common/pdfium_lock.h"
#include "fpdf_progressive.h"
#include "fpdfview.h"
#include "modules.h"

namespace pdfjni {
namespace {

constexpr const char* kClass = "com/example/pdf_reader/pdfium/jni/RenderNative";

// Result codes returned to Kotlin. Keep in sync with RenderNative.kt.
constexpr jint kRenderDone = 0;
constexpr jint kRenderCancelled = 1;
constexpr jint kRenderFailed = 2;

constexpr unsigned long kWhite = 0xFFFFFFFF;

// Cancellation flag polled by PDFium between page objects. IFSDK_PAUSE must
// stay the first member: PDFium passes it back as `pThis`.
struct CancelToken {
    IFSDK_PAUSE base{};
    std::atomic<bool> cancelled{false};

    CancelToken() {
        base.version = 1;
        base.NeedToPauseNow = &CancelToken::needToPauseNow;
        base.user = nullptr;
    }

    static FPDF_BOOL needToPauseNow(IFSDK_PAUSE* self) {
        auto* token = reinterpret_cast<CancelToken*>(self);
        return token->cancelled.load(std::memory_order_relaxed) ? 1 : 0;
    }
};

static_assert(offsetof(CancelToken, base) == 0,
              "IFSDK_PAUSE must be the first member of CancelToken");

jlong createCancelToken(JNIEnv*, jclass) {
    return toHandle(new CancelToken());
}

// Lock-free on purpose: called from any thread while a render holds the
// PDFium lock.
void cancel(JNIEnv*, jclass, jlong tokenHandle) {
    auto* token = fromHandle<CancelToken*>(tokenHandle);
    if (token != nullptr) token->cancelled.store(true, std::memory_order_relaxed);
}

void destroyCancelToken(JNIEnv*, jclass, jlong tokenHandle) {
    delete fromHandle<CancelToken*>(tokenHandle);
}

// Renders the page into `bitmap` (ARGB_8888). The page area
// (startX, startY, sizeX, sizeY) is in bitmap pixels, so a bitmap smaller
// than the page area renders one tile. Returns kRenderDone/Cancelled/Failed.
jint renderPage(JNIEnv* env, jclass, jlong pageHandle, jobject bitmap,
                jint startX, jint startY, jint sizeX, jint sizeY, jint rotate,
                jint flags, jlong tokenHandle) {
    auto* token = fromHandle<CancelToken*>(tokenHandle);
    if (token == nullptr) {
        throwIllegalArgument(env, "Cancel token is required");
        return kRenderFailed;
    }
    if (token->cancelled.load()) return kRenderCancelled;

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        throwIllegalArgument(env, "Cannot read bitmap info");
        return kRenderFailed;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        throwIllegalArgument(env, "Bitmap must be ARGB_8888");
        return kRenderFailed;
    }

    PDFIUM_LOCK();
    auto page = requireHandle<FPDF_PAGE>(env, pageHandle, "Page is closed");
    if (page == nullptr) return kRenderFailed;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        throwIllegalState(env, "Cannot lock bitmap pixels");
        return kRenderFailed;
    }

    jint result = kRenderFailed;
    FPDF_BITMAP pdfBitmap = FPDFBitmap_CreateEx(
        static_cast<int>(info.width), static_cast<int>(info.height),
        FPDFBitmap_BGRA, pixels, static_cast<int>(info.stride));
    if (pdfBitmap != nullptr) {
        FPDFBitmap_FillRect(pdfBitmap, 0, 0, static_cast<int>(info.width),
                            static_cast<int>(info.height), kWhite);

        // Android stores ARGB_8888 as RGBA bytes; PDFium writes BGRA unless
        // FPDF_REVERSE_BYTE_ORDER is set.
        int status = FPDF_RenderPageBitmap_Start(
            pdfBitmap, page, startX, startY, sizeX, sizeY, rotate,
            flags | FPDF_REVERSE_BYTE_ORDER, &token->base);
        while (status == FPDF_RENDER_TOBECONTINUED && !token->cancelled.load()) {
            status = FPDF_RenderPage_Continue(page, &token->base);
        }
        FPDF_RenderPage_Close(page);  // Required after every _Start.

        if (status == FPDF_RENDER_DONE) {
            result = kRenderDone;
        } else if (token->cancelled.load()) {
            result = kRenderCancelled;
        }
        FPDFBitmap_Destroy(pdfBitmap);  // Does not free the Android pixels.
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return result;
}

const JNINativeMethod kMethods[] = {
    {"nativeCreateCancelToken", "()J", reinterpret_cast<void*>(createCancelToken)},
    {"nativeCancel", "(J)V", reinterpret_cast<void*>(cancel)},
    {"nativeDestroyCancelToken", "(J)V", reinterpret_cast<void*>(destroyCancelToken)},
    {"nativeRenderPage", "(JLandroid/graphics/Bitmap;IIIIIIJ)I",
     reinterpret_cast<void*>(renderPage)},
};

}  // namespace

bool registerRender(JNIEnv* env) {
    return registerNatives(env, kClass, kMethods,
                           static_cast<int>(sizeof(kMethods) / sizeof(kMethods[0])));
}

}  // namespace pdfjni
