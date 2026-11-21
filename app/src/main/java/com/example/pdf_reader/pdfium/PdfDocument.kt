package com.example.pdf_reader.pdfium

import android.os.ParcelFileDescriptor
import com.example.pdf_reader.pdfium.jni.CoreNative
import com.example.pdf_reader.pdfium.jni.ImportNative
import com.example.pdf_reader.pdfium.jni.PageEditNative
import com.example.pdf_reader.pdfium.jni.PdfiumNative
import com.example.pdf_reader.pdfium.jni.SaveNative
import java.io.File

// Not thread-safe, use it through PdfDocumentManager. Closing closes all open pages.
class PdfDocument private constructor(private var handle: Long) : AutoCloseable {

    private val openPages = mutableListOf<PdfPage>()

    val isClosed: Boolean get() = handle == 0L

    val pageCount: Int get() = CoreNative.nativeGetPageCount(requireHandle())

    fun pageSize(index: Int): PdfSize =
        CoreNative.nativeGetPageSizeByIndex(requireHandle(), index).let { PdfSize(it[0], it[1]) }

    fun openPage(index: Int): PdfPage {
        val pageHandle = CoreNative.nativeLoadPage(requireHandle(), index)
        return PdfPage(this, index, pageHandle).also { openPages += it }
    }

    // Page must not be open. Open pages after it get index - 1.
    fun deletePage(index: Int) {
        check(openPages.none { it.index == index }) { "Close page $index before deleting it" }
        PageEditNative.nativeDeletePage(requireHandle(), index)
        openPages.filter { it.index > index }.forEach { it.index-- }
    }

    // pageRange is 1-based, like "1,3,5-7", null = all pages.
    fun importPages(source: PdfDocument, pageRange: String? = null, insertIndex: Int = pageCount) {
        val before = pageCount
        val ok = ImportNative.nativeImportPages(
            requireHandle(), source.requireHandle(), pageRange, insertIndex,
        )
        if (!ok) throw PdfiumException("Cannot import pages", CoreNative.nativeGetLastError())
        val added = pageCount - before
        openPages.filter { it.index >= insertIndex }.forEach { it.index += added }
    }

    // output must be empty and not the same file we read from.
    fun saveTo(output: ParcelFileDescriptor, flags: Int = SaveFlags.NONE) {
        if (!SaveNative.nativeSaveAsCopy(requireHandle(), output.fd, flags)) {
            throw PdfiumException("Cannot save document", CoreNative.nativeGetLastError())
        }
    }

    fun saveTo(file: File, flags: Int = SaveFlags.NONE) {
        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE,
        ).use { saveTo(it, flags) }
    }

    override fun close() {
        if (handle == 0L) return
        openPages.toList().forEach { it.close() }
        CoreNative.nativeCloseDocument(handle)
        handle = 0L
    }

    internal fun requireHandle(): Long {
        check(handle != 0L) { "Document is closed" }
        return handle
    }

    internal fun onPageClosed(page: PdfPage) {
        openPages.remove(page)
    }

    companion object {
        // Native code dups the fd, so input can be closed after this.
        fun open(input: ParcelFileDescriptor, password: String? = null): PdfDocument {
            PdfiumNative.ensureInitialized()
            return PdfDocument(CoreNative.nativeOpenDocument(input.fd, password))
        }

        fun open(file: File, password: String? = null): PdfDocument =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                .use { open(it, password) }

        fun createNew(): PdfDocument {
            PdfiumNative.ensureInitialized()
            return PdfDocument(CoreNative.nativeCreateNewDocument())
        }
    }
}
