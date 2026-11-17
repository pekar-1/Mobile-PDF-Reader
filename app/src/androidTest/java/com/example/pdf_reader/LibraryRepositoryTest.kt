package com.example.pdf_reader

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pdf_reader.data.LibraryRepository
import com.example.pdf_reader.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LibraryRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = LibraryRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun tagsOf(hash: String): List<String> =
        repo.observeDocuments().first().single { it.document.hash == hash }.tags.map { it.name }

    @Test
    fun tagsAreCaseInsensitiveAndShareColor() = runBlocking {
        repo.rememberDocument("a", "content://a", "A.pdf")
        repo.rememberDocument("b", "content://b", "B.pdf")

        repo.addTag("a", "Egzamin", 0x111111)
        repo.addTag("b", " egzamin ", 0x222222)
        repo.addTag("a", "EGZAMIN", 0x333333)

        val tag = db.documentDao().getTag("egzamin")!!
        assertEquals("Egzamin", tag.name)
        assertEquals(0x333333, tag.colorArgb)
        assertEquals(listOf("Egzamin"), tagsOf("a"))
        assertEquals(listOf("Egzamin"), tagsOf("b"))
    }

    @Test
    fun removingLastUseDeletesTag() = runBlocking {
        repo.rememberDocument("a", "content://a", "A.pdf")
        repo.rememberDocument("b", "content://b", "B.pdf")
        repo.addTag("a", "work", 1)
        repo.addTag("b", "work", 1)

        repo.removeTag("a", "WORK")
        assertTrue(tagsOf("a").isEmpty())
        assertEquals(listOf("work"), tagsOf("b"))

        repo.removeTag("b", "work")
        assertNull(db.documentDao().getTag("work"))
    }

    @Test
    fun removeDocumentCascadesAndDropsOrphanTags() = runBlocking {
        repo.rememberDocument("a", "content://a", "A.pdf")
        repo.rememberDocument("b", "content://b", "B.pdf")
        repo.addTag("a", "only-a", 1)
        repo.addTag("a", "shared", 2)
        repo.addTag("b", "shared", 2)

        repo.removeDocument("a")

        assertNull(repo.get("a"))
        assertNull(db.documentDao().getTag("only-a"))
        assertEquals(listOf("shared"), tagsOf("b"))
    }

    @Test
    fun concurrentRememberOfSameDocumentKeepsOneRow() = runBlocking {
        (1..8).map {
            async(Dispatchers.IO) { repo.rememberDocument("same", "content://same/$it", "Same.pdf") }
        }.awaitAll()

        val docs = repo.observeDocuments().first()
        assertEquals(1, docs.size)
    }

    @Test
    fun rememberingExistingDocumentKeepsReadingState() = runBlocking {
        repo.rememberDocument("a", "content://old", "Old.pdf")
        repo.updatePageCount("a", 10)
        repo.updateLastPage("a", 4)
        repo.setPinned("a", true)

        repo.rememberDocument("a", "content://new", "New.pdf")

        val doc = repo.get("a")!!
        assertEquals("content://new", doc.uri)
        assertEquals("New.pdf", doc.displayName)
        assertEquals(4, doc.lastPageIndex)
        assertEquals(10, doc.pageCount)
        assertTrue(doc.pinned)
    }

    @Test
    fun lastPageIsClamped() = runBlocking {
        repo.rememberDocument("a", "content://a", "A.pdf")

        repo.updateLastPage("a", -3)
        assertEquals(0, repo.get("a")!!.lastPageIndex)

        repo.updateLastPage("a", 50)
        assertEquals(50, repo.get("a")!!.lastPageIndex)

        repo.updatePageCount("a", 5)
        repo.updateLastPage("a", 50)
        assertEquals(4, repo.get("a")!!.lastPageIndex)
    }
}
