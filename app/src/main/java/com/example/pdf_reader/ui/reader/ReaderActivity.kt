package com.example.pdf_reader.ui.reader

import android.content.Context
import android.content.Intent
import com.example.pdf_reader.data.AppLanguage
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.pdf_reader.data.LibraryRepository
import com.example.pdf_reader.ui.library.OpenDocument
import com.example.pdf_reader.ui.theme.Pdf_ReaderTheme

class ReaderActivity : ComponentActivity() {
    private lateinit var repository: LibraryRepository
    private var documentHash: String? = null
    @Volatile
    private var currentPage: Int = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = LibraryRepository.get(this)
        val uri = intent.data
        val hash = intent.getStringExtra(EXTRA_HASH)
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        if (uri == null || hash.isNullOrBlank()) {
            finish()
            return
        }
        documentHash = hash

        enableEdgeToEdge()
        setContent {
            Pdf_ReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    ReaderScreen(
                        uri = uri,
                        hash = hash,
                        displayName = displayName.ifBlank { "PDF" },
                        repository = repository,
                        onPageChanged = { currentPage = it },
                        onBack = ::finish,
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val hash = documentHash ?: return
        val page = currentPage
        repository.saveLastPageAsync(hash, page)
    }

    companion object {
        const val EXTRA_HASH = "document_hash"
        const val EXTRA_DISPLAY_NAME = "display_name"

        fun intent(context: Context, target: OpenDocument): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                data = target.uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(EXTRA_HASH, target.hash)
                putExtra(EXTRA_DISPLAY_NAME, target.displayName)
            }
        }
    }
}
