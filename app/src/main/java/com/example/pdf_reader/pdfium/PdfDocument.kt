package com.example.pdf_reader.pdfium

import android.os.ParcelFileDescriptor
import com.example.pdf_reader.pdfium.jni.CoreNative
import com.example.pdf_reader.pdfium.jni.ImportNative
import com.example.pdf_reader.pdfium.jni.PageEditNative
import com.example.pdf_reader.pdfium.jni.PdfiumNative
import com.example.pdf_reader.pdfium.jni.SaveNative
import java.io.File

/**
 * PDF document backed by PDFium. The object itself is not thread-safe; native
 * calls are serialized by a global lock. Closing it closes all its open pages.
 */
class PdfDocument private constructor(private var handle: Long) : AutoCloseable {

    private val openPages = mutableListOf<PdfPage>()

    val isClosed: Boolean get() = handle == 0L

    val pageCount: Int get() = CoreNative.nativeGetPageCount(requireHandle())

    /** Size of the page at [index] in PDF points, without loading the page. */
    fun pageSize(index: Int): PdfSize =
        CoreNative.nativeGetPageSizeByIndex(requireHandle(), index).let { PdfSize(it[0], it[1]) }

    fun openPage(index: Int): PdfPage {
        val pageHandle = CoreNative.nativeLoadPage(requireHandle(), index)
        return PdfPage(this, index, pageHandle).also { openPages += it }
    }

    /**
     * Deletes the page at [index] (FPDFPage_Delete). That page must not be open.
     * Open pages after it get their [PdfPage.index] shifted down by one.
     */
    fun deletePage(index: Int) {
        check(openPages.none { it.index == index }) { "Close page $index before deleting it" }
        PageEditNative.nativeDeletePage(requireHandle(), index)
        openPages.filter { it.index > index }.forEach { it.index-- }
    }

    /**
     * Copies pages from [source] into this document at [insertIndex]
     * (FPDF_ImportPages). [pageRange] is 1-based, e.g. `"1,3,5-7"`; `null`
     * imports all pages. Keep [source] open until this document is saved.
     */
    fun importPages(source: PdfDocument, pageRange: String? = null, insertIndex: Int = pageCount) {
        val before = pageCount
        val ok = ImportNative.nativeImportPages(
            requireHandle(), source.requireHandle(), pageRange, insertIndex,
        )
        if (!ok) throw PdfiumException("Cannot import pages", CoreNative.nativeGetLastError())
        val added = pageCount - before
        openPages.filter { it.index >= insertIndex }.forEach { it.index += added }
    }

    /**
     * Writes a copy of the document to [output] (FPDF_SaveAsCopy). The
     * descriptor must be writable and empty, and must not be the one this
     * document reads from. [flags]: see [SaveFlags].
     */
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
        /** Opens a document. The fd is duplicated natively; [input] may be closed afterwards. */
        fun open(input: ParcelFileDescriptor, password: String? = null): PdfDocument {
            PdfiumNative.ensureInitialized()
            return PdfDocument(CoreNative.nativeOpenDocument(input.fd, password))
        }

        fun open(file: File, password: String? = null): PdfDocument =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                .use { open(it, password) }

        /** Creates an empty in-memory document (FPDF_CreateNewDocument). */
        fun createNew(): PdfDocument {
            PdfiumNative.ensureInitialized()
            return PdfDocument(CoreNative.nativeCreateNewDocument())
        }
    }
}
