package com.example.pdf_reader.ui.library

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdf_reader.ui.reader.ReaderActivity
import com.example.pdf_reader.ui.theme.Pdf_ReaderTheme

class LibraryActivity : ComponentActivity() {
    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Pdf_ReaderTheme {
                val documents by viewModel.documents.collectAsStateWithLifecycle()
                val query by viewModel.query.collectAsStateWithLifecycle()
                val opening by viewModel.opening.collectAsStateWithLifecycle()

                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { picked ->
                    if (picked == null) return@rememberLauncherForActivityResult
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            picked,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    viewModel.onPicked(picked)
                }

                LaunchedEffect(viewModel) {
                    viewModel.messages.collect { message ->
                        Toast.makeText(this@LibraryActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                LaunchedEffect(viewModel) {
                    viewModel.openEvents.collect { target ->
                        startActivity(ReaderActivity.intent(this@LibraryActivity, target))
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    LibraryScreen(
                        documents = documents,
                        query = query,
                        opening = opening,
                        onQueryChange = { viewModel.query.value = it },
                        onOpenPicker = { picker.launch(arrayOf("application/pdf")) },
                        onOpenDocument = viewModel::openExisting,
                        onTogglePin = viewModel::togglePinned,
                        onAddTag = viewModel::addTag,
                        onRemoveTag = viewModel::removeTag,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
