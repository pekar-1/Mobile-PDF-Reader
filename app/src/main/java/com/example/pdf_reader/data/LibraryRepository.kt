package com.example.pdf_reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.example.pdf_reader.data.db.AppDatabase
import com.example.pdf_reader.data.db.DocumentEntity
import com.example.pdf_reader.data.db.DocumentTagCrossRef
import com.example.pdf_reader.data.db.DocumentWithTags
import com.example.pdf_reader.data.db.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

class LibraryRepository(
    private val appContext: Context,
    private val db: AppDatabase,
) {
    private val dao = db.documentDao()

    /** Outlives screens, so writes started from onPause finish after the Activity is gone. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeDocuments(): Flow<List<DocumentWithTags>> = dao.observeAll()

    suspend fun get(hash: String): DocumentEntity? = dao.get(hash)

    suspend fun rememberPicked(uri: Uri): DocumentEntity {
        val (hash, name) = withContext(Dispatchers.IO) { sha256(uri) to displayName(uri) }
        return rememberDocument(hash, uri.toString(), name)
    }

    /** Inserts a new document or refreshes uri/name/lastOpenedAt of an existing one, atomically. */
    suspend fun rememberDocument(hash: String, uri: String, name: String): DocumentEntity {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val existing = dao.get(hash)
            if (existing == null) {
                DocumentEntity(
                    hash = hash,
                    uri = uri,
                    displayName = name,
                    lastOpenedAt = now,
                    lastPageIndex = 0,
                    pageCount = 0,
                    thumbnailUpdatedAt = null,
                    pinned = false,
                ).also { dao.insert(it) }
            } else {
                existing.copy(uri = uri, displayName = name, lastOpenedAt = now)
                    .also { dao.update(it) }
            }
        }
    }

    suspend fun touchOpened(hash: String) = dao.touchOpened(hash, System.currentTimeMillis())

    suspend fun updateLastPage(hash: String, pageIndex: Int) = dao.updateLastPage(hash, pageIndex)

    /** Fire-and-forget variant for lifecycle callbacks that must not block the main thread. */
    fun saveLastPageAsync(hash: String, pageIndex: Int) {
        appScope.launch { dao.updateLastPage(hash, pageIndex) }
    }

    suspend fun updatePreview(hash: String, pageCount: Int) {
        dao.updatePreview(hash, pageCount, System.currentTimeMillis())
    }

    suspend fun updatePageCount(hash: String, pageCount: Int) {
        dao.updatePageCount(hash, pageCount)
    }

    suspend fun setPinned(hash: String, pinned: Boolean) = dao.setPinned(hash, pinned)

    /**
     * Tags are global and case-insensitive. The first spelling of a tag is kept; the color is
     * shared by every document and the most recent choice wins.
     */
    suspend fun addTag(hash: String, raw: String, colorArgb: Int) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        db.withTransaction {
            dao.insertTagIfAbsent(TagEntity(tag, colorArgb))
            dao.updateTagColor(tag, colorArgb)
            dao.insertCrossRef(DocumentTagCrossRef(hash, tag))
        }
    }

    suspend fun removeTag(hash: String, tag: String) {
        db.withTransaction {
            dao.deleteCrossRef(hash, tag)
            dao.deleteOrphanTags()
        }
    }

    /** Removes the document from the library. The PDF file itself is never touched. */
    suspend fun removeDocument(hash: String) {
        val document = db.withTransaction {
            dao.get(hash).also {
                dao.deleteDocument(hash)
                dao.deleteOrphanTags()
            }
        } ?: return
        withContext(Dispatchers.IO) {
            ThumbnailStore.file(appContext, hash).delete()
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    document.uri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    fun canRead(uri: Uri): Boolean {
        return try {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
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
                instance ?: LibraryRepository(
                    context.applicationContext,
                    AppDatabase.get(context),
                ).also { instance = it }
            }
        }
    }
}

object ThumbnailStore {
    private const val WIDTH_PX = 288
    const val DIR = "thumbs"

    fun file(context: Context, hash: String): File =
        File(File(context.filesDir, DIR).apply { mkdirs() }, "$hash.jpg")

    const val renderWidthPx: Int = WIDTH_PX
}
