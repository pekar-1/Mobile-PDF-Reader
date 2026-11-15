package com.example.pdf_reader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.pdf_reader.data.db.AppDatabase
import com.example.pdf_reader.data.db.DocumentEntity
import com.example.pdf_reader.data.db.DocumentTagEntity
import com.example.pdf_reader.data.db.DocumentWithTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

class LibraryRepository private constructor(private val appContext: Context) {
    private val dao = AppDatabase.get(appContext).documentDao()

    fun observeDocuments(): Flow<List<DocumentWithTags>> = dao.observeAll()

    suspend fun get(hash: String): DocumentEntity? = dao.get(hash)

    suspend fun rememberPicked(uri: Uri): DocumentEntity = withContext(Dispatchers.IO) {
        val hash = sha256(uri)
        val name = displayName(uri)
        val now = System.currentTimeMillis()
        val existing = dao.get(hash)
        if (existing == null) {
            val created = DocumentEntity(
                hash = hash,
                uri = uri.toString(),
                displayName = name,
                lastOpenedAt = now,
                lastPageIndex = 0,
                pageCount = 0,
                thumbnailPath = null,
                pinned = false,
            )
            dao.insert(created)
            created
        } else {
            val updated = existing.copy(
                uri = uri.toString(),
                displayName = name,
                lastOpenedAt = now,
            )
            dao.update(updated)
            updated
        }
    }

    suspend fun touchOpened(hash: String) {
        val existing = dao.get(hash) ?: return
        dao.update(existing.copy(lastOpenedAt = System.currentTimeMillis()))
    }

    suspend fun updateLastPage(hash: String, pageIndex: Int) {
        val existing = dao.get(hash) ?: return
        val count = existing.pageCount
        val clamped =
            if (count > 0) pageIndex.coerceIn(0, count - 1) else pageIndex.coerceAtLeast(0)
        dao.updateLastPage(hash, clamped)
    }

    suspend fun updatePreview(hash: String, pageCount: Int, thumbnailPath: String) {
        dao.updatePreview(hash, pageCount, thumbnailPath)
    }

    suspend fun updatePageCount(hash: String, pageCount: Int) {
        dao.updatePageCount(hash, pageCount)
    }

    suspend fun setPinned(hash: String, pinned: Boolean) = dao.setPinned(hash, pinned)

    suspend fun addTag(hash: String, raw: String, colorArgb: Int) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        dao.insertTag(DocumentTagEntity(hash, tag, colorArgb))
    }

    suspend fun removeTag(hash: String, tag: String) = dao.deleteTag(hash, tag)

    fun canRead(uri: Uri): Boolean {
        return try {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: FileNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot read $uri")
        input.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun displayName(uri: Uri): String {
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "PDF"
    }

    companion object {
        @Volatile
        private var instance: LibraryRepository? = null

        fun get(context: Context): LibraryRepository {
            return instance ?: synchronized(this) {
                instance ?: LibraryRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

object ThumbnailStore {
    private const val WIDTH_PX = 288

    fun file(context: Context, hash: String): File =
        File(File(context.filesDir, "thumbs").apply { mkdirs() }, "$hash.jpg")

    const val renderWidthPx: Int = WIDTH_PX
}
