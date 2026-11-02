package com.example.pdf_reader.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.example.pdf_reader.domain.model.PdfFileInfo
import com.example.pdf_reader.domain.model.PdfPageInfo
import com.example.pdf_reader.domain.repository.IPdfEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bitmapPool: BitmapPool,
) : IPdfEngine {

    private val pdfiumCore = PdfiumCore()
    private val mutex = Mutex()
    private var session: OpenSession? = null

    override suspend fun open(uri: Uri): PdfFileInfo {
        val displayName = withContext(Dispatchers.IO) {
            DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "PDF"
        }
        val pfd = withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Nie można otworzyć pliku: $uri")
        }

        return mutex.withLock {
            closeLocked()
            val document = withContext(Dispatchers.IO) {
                pdfiumCore.newDocument(pfd)
            }
            val pageCount = withContext(Dispatchers.Default) {
                document.getPageCount()
            }
            val info = PdfFileInfo(
                uri = uri.toString(),
                displayName = displayName,
                pageCount = pageCount,
            )
            session = OpenSession(pfd = pfd, document = document, info = info)
            info
        }
    }

    override suspend fun getPageInfo(pageIndex: Int): PdfPageInfo {
        return mutex.withLock {
            val current = requireSession()
            require(pageIndex in 0 until current.info.pageCount) {
                "Strona $pageIndex poza zakresem 0..${current.info.pageCount - 1}"
            }
            withContext(Dispatchers.Default) {
                current.document.openPage(pageIndex).use { page ->
                    PdfPageInfo(
                        index = pageIndex,
                        widthPt = page.getPageWidthPoint().toFloat(),
                        heightPt = page.getPageHeightPoint().toFloat(),
                    )
                }
            }
        }
    }

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val pageInfo = getPageInfo(pageIndex)
        val width = targetWidthPx.coerceIn(MIN_RENDER_WIDTH, MAX_RENDER_WIDTH)
        val aspect = if (pageInfo.widthPt == 0f) 1f else pageInfo.heightPt / pageInfo.widthPt
        val height = (width * aspect).toInt().coerceIn(1, MAX_RENDER_HEIGHT)
        val bitmap = bitmapPool.acquire(width, height)

        try {
            mutex.withLock {
                val current = requireSession()
                withContext(Dispatchers.Default) {
                    current.document.openPage(pageIndex).use { page ->
                        page.renderPageBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                        )
                    }
                }
            }
            return bitmap
        } catch (t: Throwable) {
            bitmapPool.release(bitmap)
            throw t
        }
    }

    override fun close() {
        session?.let {
            runCatching { it.document.close() }
            runCatching { it.pfd.close() }
        }
        session = null
    }

    private fun closeLocked() {
        session?.let {
            runCatching { it.document.close() }
            runCatching { it.pfd.close() }
        }
        session = null
    }

    private fun requireSession(): OpenSession {
        return session ?: error("Brak otwartego dokumentu PDF")
    }

    private data class OpenSession(
        val pfd: ParcelFileDescriptor,
        val document: PdfDocument,
        val info: PdfFileInfo,
    )

    private companion object {
        const val MIN_RENDER_WIDTH = 320
        const val MAX_RENDER_WIDTH = 2048
        const val MAX_RENDER_HEIGHT = 4096
    }
}
