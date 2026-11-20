#include <jni.h>

#include <string>
#include <vector>

#include "common/jni_utils.h"
#include "common/native_document.h"
#include "common/pdfium_lock.h"
#include "fpdf_doc.h"
#include "fpdf_text.h"
#include "modules.h"

namespace pdfjni {
namespace {

constexpr const char* kClass = "com/example/pdf_reader/pdfium/jni/LinkNative";

jstring pageResult(JNIEnv* env, int pageIndex) {
    if (pageIndex < 0) return nullptr;
    return env->NewStringUTF(("P:" + std::to_string(pageIndex)).c_str());
}

jstring uriResult(JNIEnv* env, FPDF_DOCUMENT document, FPDF_ACTION action) {
    const unsigned long length = FPDFAction_GetURIPath(document, action, nullptr, 0);
    if (length <= 1) return nullptr;
    std::string buffer(length, '\0');
    FPDFAction_GetURIPath(document, action, buffer.data(), length);
    buffer.resize(length - 1);
    for (const char c : buffer) {
        if (static_cast<unsigned char>(c) >= 0x80) return nullptr;
    }
    return env->NewStringUTF(("U:" + buffer).c_str());
}

jstring annotationLink(JNIEnv* env, FPDF_DOCUMENT document, FPDF_PAGE page,
                       double x, double y) {
    FPDF_LINK link = FPDFLink_GetLinkAtPoint(page, x, y);
    if (link == nullptr) return nullptr;
    if (FPDF_DEST dest = FPDFLink_GetDest(document, link)) {
        return pageResult(env, FPDFDest_GetDestPageIndex(document, dest));
    }
    FPDF_ACTION action = FPDFLink_GetAction(link);
    if (action == nullptr) return nullptr;
    switch (FPDFAction_GetType(action)) {
        case PDFACTION_GOTO: {
            FPDF_DEST dest = FPDFAction_GetDest(document, action);
            return dest ? pageResult(env, FPDFDest_GetDestPageIndex(document, dest)) : nullptr;
        }
        case PDFACTION_URI:
            return uriResult(env, document, action);
        default:
            return nullptr;
    }
}

jstring webLink(JNIEnv* env, FPDF_TEXTPAGE textPage, double x, double y) {
    FPDF_PAGELINK links = FPDFLink_LoadWebLinks(textPage);
    if (links == nullptr) return nullptr;
    jstring result = nullptr;
    const int count = FPDFLink_CountWebLinks(links);
    for (int i = 0; i < count && result == nullptr; ++i) {
        const int rects = FPDFLink_CountRects(links, i);
        for (int r = 0; r < rects; ++r) {
            double left = 0, top = 0, right = 0, bottom = 0;
            if (!FPDFLink_GetRect(links, i, r, &left, &top, &right, &bottom)) continue;
            if (x < left || x > right || y < bottom || y > top) continue;
            const int length = FPDFLink_GetURL(links, i, nullptr, 0);
            if (length > 1) {
                std::vector<unsigned short> url(static_cast<size_t>(length), 0);
                FPDFLink_GetURL(links, i, url.data(), length);
                std::u16string text(u"U:");
                text.append(url.begin(), url.end() - 1);
                result = env->NewString(reinterpret_cast<const jchar*>(text.data()),
                                        static_cast<jsize>(text.size()));
            }
            break;
        }
    }
    FPDFLink_CloseWebLinks(links);
    return result;
}

jstring linkAt(JNIEnv* env, jclass, jlong documentHandle, jlong pageHandle,
               jlong textPageHandle, jdouble x, jdouble y) {
    PDFIUM_LOCK();
    FPDF_DOCUMENT document = requireDocument(env, documentHandle);
    if (document == nullptr) return nullptr;
    auto page = requireHandle<FPDF_PAGE>(env, pageHandle, "Page is closed");
    if (page == nullptr) return nullptr;
    if (jstring found = annotationLink(env, document, page, x, y)) return found;
    auto textPage = fromHandle<FPDF_TEXTPAGE>(textPageHandle);
    return textPage != nullptr ? webLink(env, textPage, x, y) : nullptr;
}

const JNINativeMethod kMethods[] = {
    {"nativeLinkAt", "(JJJDD)Ljava/lang/String;", reinterpret_cast<void*>(linkAt)},
};

}

bool registerLink(JNIEnv* env) {
    return registerNatives(env, kClass, kMethods,
                           static_cast<int>(sizeof(kMethods) / sizeof(kMethods[0])));
}

}