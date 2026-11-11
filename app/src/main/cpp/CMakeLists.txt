cmake_minimum_required(VERSION 3.22.1)
project("pdf_engine")

add_library(pdfium SHARED IMPORTED)
set_target_properties(pdfium PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpdfium.so)

# One source file per PDFium area; register new modules in modules.h and
# jni_onload.cpp.
add_library(pdf_engine SHARED
    jni_onload.cpp
    common/jni_utils.cpp
    common/fd_io.cpp
    core/core_jni.cpp
    edit/page_edit_jni.cpp
    ppo/import_pages_jni.cpp
    text/text_page_jni.cpp
    text/text_search_jni.cpp
    save/save_jni.cpp
    render/render_jni.cpp
)

target_include_directories(pdf_engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/pdfium/include
)

target_compile_options(pdf_engine PRIVATE -Wall -Wextra)

target_link_libraries(pdf_engine
    pdfium
    android
    log
    jnigraphics
)
