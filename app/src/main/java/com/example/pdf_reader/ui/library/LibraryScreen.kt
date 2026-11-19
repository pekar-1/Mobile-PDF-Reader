package com.example.pdf_reader.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pdf_reader.R
import com.example.pdf_reader.data.AppLanguage
import com.example.pdf_reader.data.db.DocumentWithTags
import kotlinx.coroutines.delay

private val CollapseSpec = tween<Float>(durationMillis = 120)
private val CollapseSizeSpec = tween<IntSize>(durationMillis = 120)

@Composable
fun LibraryScreen(
    documents: List<DocumentWithTags>,
    query: String,
    opening: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenDocument: (DocumentWithTags) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onAddTag: (String, String, Int) -> Unit,
    onRemoveTag: (String, String) -> Unit,
    removal: RemovalRequest?,
    onRequestRemove: (DocumentWithTags) -> Unit,
    onConfirmRemove: () -> Unit,
    onDismissRemove: () -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var pinnedExpanded by rememberSaveable { mutableStateOf(true) }
    var tagTarget by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }

    val searching = searchOpen || query.isNotEmpty()
    val pinned = documents.filter { it.document.pinned }
    val recent = documents.filter { !it.document.pinned }
    val emptyLibrary = documents.isEmpty() && query.isBlank()
    val noHits = documents.isEmpty() && query.isNotBlank()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LibraryHeader(
                query = query,
                searchOpen = searching,
                onQueryChange = onQueryChange,
                onOpenSearch = { searchOpen = true },
                onCloseSearch = {
                    searchOpen = false
                    onQueryChange("")
                },
                language = language,
                onLanguageChange = onLanguageChange,
            )

            when {
                emptyLibrary -> EmptyLibrary(Modifier.fillMaxSize())
                noHits -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.library_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (pinned.isNotEmpty()) {
                        item(key = "pinned-block") {
                            Column {
                                SectionHeader(
                                    title = stringResource(R.string.library_pinned),
                                    expanded = pinnedExpanded,
                                    onToggle = { pinnedExpanded = !pinnedExpanded },
                                )
                                AnimatedVisibility(
                                    visible = pinnedExpanded,
                                    enter = expandVertically(CollapseSizeSpec) + fadeIn(CollapseSpec),
                                    exit = shrinkVertically(CollapseSizeSpec) + fadeOut(CollapseSpec),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        pinned.forEach { item ->
                                            DocumentCard(
                                                item = item,
                                                nowMs = nowMs,
                                                onOpen = { onOpenDocument(item) },
                                                onLongPress = { onRequestRemove(item) },
                                                onTogglePin = {
                                                    onTogglePin(
                                                        item.document.hash,
                                                        !item.document.pinned
                                                    )
                                                },
                                                onAddTag = { tagTarget = item.document.hash },
                                                onRemoveTag = {
                                                    onRemoveTag(item.document.hash, it)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item(key = "recent-header") {
                            if (pinned.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.library_recent),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp, top = 4.dp),
                            )
                        }
                        items(recent, key = { it.document.hash }) { item ->
                            DocumentCard(
                                item = item,
                                nowMs = nowMs,
                                onOpen = { onOpenDocument(item) },
                                onLongPress = { onRequestRemove(item) },
                                onTogglePin = {
                                    onTogglePin(item.document.hash, !item.document.pinned)
                                },
                                onAddTag = { tagTarget = item.document.hash },
                                onRemoveTag = { onRemoveTag(item.document.hash, it) },
                            )
                        }
                    }
                }
            }
        }

        OpenPdfButton(
            onClick = onOpenPicker,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 16.dp),
        )

        if (opening) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    val hashForTag = tagTarget
    if (hashForTag != null) {
        AddTagDialog(
            onDismiss = { tagTarget = null },
            onConfirm = { value, color ->
                onAddTag(hashForTag, value, color)
                tagTarget = null
            },
        )
    }

    if (removal != null) {
        RemoveDocumentDialog(
            request = removal,
            onConfirm = onConfirmRemove,
            onDismiss = onDismissRemove,
        )
    }
}

@Composable
private fun RemoveDocumentDialog(
    request: RemovalRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_remove_title)) },
        text = {
            Text(
                stringResource(
                    if (request.unavailable) R.string.library_file_unavailable else R.string.library_remove_body,
                    request.displayName,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun LibraryHeader(
    query: String,
    searchOpen: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        if (searchOpen) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = onCloseSearch) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.menu_open))
                    }
                    LanguageMenu(
                        expanded = menuOpen,
                        current = language,
                        onDismiss = { menuOpen = false },
                        onSelect = {
                            menuOpen = false
                            onLanguageChange(it)
                        },
                    )
                }
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun LanguageMenu(
    expanded: Boolean,
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.settings_language),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        (listOf(AppLanguage.SYSTEM) + AppLanguage.supported).forEach { tag ->
            DropdownMenuItem(
                text = { Text(languageLabel(tag)) },
                onClick = { onSelect(tag) },
                leadingIcon = {
                    if (tag == current) Icon(Icons.Filled.Check, contentDescription = null)
                },
            )
        }
    }
}

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "pl" -> "Polski"
    "en" -> "English"
    else -> stringResource(R.string.language_system)
}

@Composable
private fun OpenPdfButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 3.dp
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stringResource(R.string.library_open_pdf), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(if (expanded) R.string.library_collapse else R.string.library_expand),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
