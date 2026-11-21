package com.example.pdf_reader

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pdf_reader.pdfium.PdfDocumentManager
import com.example.pdf_reader.ui.reader.ReaderActivity
import com.example.pdf_reader.ui.reader.ReaderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// Recreate (e.g. rotation) must keep the open document and the page.
@RunWith(AndroidJUnit4::class)
class ReaderRotationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun readerIntent(): Intent {
        val pages = List(10) { "Page ${it + 1}" }
        val file = File(context.cacheDir, "rotation.pdf").apply { writeBytes(TestPdfs.build(pages)) }
        return Intent(context, ReaderActivity::class.java)
            .setData(Uri.fromFile(file))
            .putExtra(ReaderActivity.EXTRA_HASH, "rotation-test")
    }

    private fun <T> ActivityScenario<ReaderActivity>.read(block: (ReaderActivity) -> T): T {
        var result: T? = null
        onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun ActivityScenario<ReaderActivity>.awaitManager(): PdfDocumentManager {
        repeat(100) {
            instrumentation.waitForIdleSync()
            val state = read { it.viewModel?.state?.value }
            if (state is ReaderState.Open) return state.manager
            Thread.sleep(50)
        }
        throw AssertionError("Document did not open")
    }

    private fun ActivityScenario<ReaderActivity>.awaitPage(expected: Int) {
        repeat(100) {
            instrumentation.waitForIdleSync()
            if (read { it.viewModel?.pageIndex?.value } == expected) return
            Thread.sleep(50)
        }
        assertEquals(expected, read { it.viewModel?.pageIndex?.value })
    }

    @Test
    fun recreateKeepsDocumentAndPage() {
        val scenario = ActivityScenario.launch<ReaderActivity>(readerIntent())
        val manager = scenario.awaitManager()
        scenario.read { it.viewModel!!.onPageChanged(5) }

        scenario.recreate()

        assertSame(manager, scenario.awaitManager())
        assertFalse(manager.isClosed)
        scenario.awaitPage(5)

        scenario.moveToState(Lifecycle.State.DESTROYED)
        assertTrue(manager.isClosed)
    }
}
