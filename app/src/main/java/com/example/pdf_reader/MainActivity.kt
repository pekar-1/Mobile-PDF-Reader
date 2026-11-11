package com.example.pdf_reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pdf_reader.pdfium.PdfDocumentManager
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import com.example.pdf_reader.ui.pdf.PdfViewer
import com.example.pdf_reader.ui.pdf.search.PdfSearchBar
import com.example.pdf_reader.ui.pdf.search.rememberSearchState
import com.example.pdf_reader.ui.theme.Pdf_ReaderTheme
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Pdf_ReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // imePadding: the viewer shrinks above the keyboard, so search
                    // hits centered in it stay visible.
                    PdfDemoScreen(
                        Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .imePadding(),
                    )
                }
            }
        }
    }
}

private sealed interface DocumentState {
    data object Empty : DocumentState
    data object Loading : DocumentState
    data class Open(val manager: PdfDocumentManager) : DocumentState
    data class Failed(val error: Throwable) : DocumentState
}

/** Demo: pick a PDF with the system picker and scroll through its pages. */
@Composable
fun PdfDemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var state by remember { mutableStateOf<DocumentState>(DocumentState.Empty) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            // Keep read access after activity recreation; not all providers allow it.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            uri = picked
        }
    }

    LaunchedEffect(uri) {
        val target = uri ?: return@LaunchedEffect
        state = DocumentState.Loading
        state = try {
            DocumentState.Open(PdfDocumentManager.open(context, target))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DocumentState.Failed(e)
        }
    }

    // Closes the document when it is replaced or the screen leaves composition.
    val manager = (state as? DocumentState.Open)?.manager
    DisposableEffect(manager) {
        onDispose { manager?.close() }
    }

    val search = manager?.let { rememberSearchState(it) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                Text("Open PDF")
            }
            if (manager != null) {
                val pageCount by manager.pageCount.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pages: $pageCount", style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
                    }
                }
            }
        }

        if (search != null && searchOpen) {
            PdfSearchBar(
                search = search,
                onClose = {
                    search.clear()
                    searchOpen = false
                },
            )
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val current = state) {
                DocumentState.Empty -> Text("Choose a PDF file")
                DocumentState.Loading -> CircularProgressIndicator()
                is DocumentState.Failed -> Text(
                    text = "Cannot open: ${current.error.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                is DocumentState.Open -> PdfViewer(current.manager, Modifier.fillMaxSize(), search = search)
            }
        }
    }
}
