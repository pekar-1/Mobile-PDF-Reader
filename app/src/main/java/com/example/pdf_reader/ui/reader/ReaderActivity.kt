package com.example.pdf_reader.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.pdf_reader.data.AppLanguage
import com.example.pdf_reader.data.LibraryRepository
import com.example.pdf_reader.ui.library.OpenDocument
import com.example.pdf_reader.ui.theme.Pdf_ReaderTheme

class ReaderActivity : ComponentActivity() {
    private lateinit var repository: LibraryRepository
    private var documentHash: String? = null

    internal var viewModel: ReaderViewModel? = null
        private set

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
        val model = ViewModelProvider(this, ReaderViewModel.factory(uri, hash))[ReaderViewModel::class.java]
        viewModel = model

        enableEdgeToEdge()
        setContent {
            Pdf_ReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    ReaderScreen(
                        viewModel = model,
                        displayName = displayName.ifBlank { "PDF" },
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
        val model = viewModel ?: return
        if (model.state.value is ReaderState.Open) repository.saveLastPageAsync(hash, model.pageIndex.value)
    }

    companion object {
        const val EXTRA_HASH = "document_hash"
        const val EXTRA_DISPLAY_NAME = "display_name"

        fun intent(context: Context, target: OpenDocument): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                data = target.uri
                putExtra(EXTRA_HASH, target.hash)
                putExtra(EXTRA_DISPLAY_NAME, target.displayName)
            }
        }
    }
}
