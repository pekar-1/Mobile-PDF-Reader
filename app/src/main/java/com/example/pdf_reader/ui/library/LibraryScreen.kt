package com.example.pdf_reader.ui.library

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf_reader.R
import com.example.pdf_reader.data.ThumbnailStore
import com.example.pdf_reader.data.db.TagEntity
import com.example.pdf_reader.data.db.DocumentWithTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private val CardShape = RoundedCornerShape(14.dp)
private val ThumbWidth = 72.dp
private val ThumbHeight = 96.dp
private val ChipShape = RoundedCornerShape(20.dp)
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
                                                    onRemoveTag(
                                                        item.document.hash,
                                                        it
                                                    )
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
                                modifier = Modifier.padding(
                                    start = 4.dp,
                                    bottom = 2.dp,
                                    top = 4.dp
                                ),
                            )
                        }
                        items(recent, key = { it.document.hash }) { item ->
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
                IconButton(onClick = { }) {
                    Icon(Icons.Outlined.Menu, contentDescription = null)
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
            defaultElevation = 3.dp,
            pressedElevation = 5.dp
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentCard(
    item: DocumentWithTags,
    nowMs: Long,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onTogglePin: () -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val doc = item.document
    val tags = item.tags.sortedBy { it.name.lowercase() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val thumbFile = remember(doc.hash) { ThumbnailStore.file(context, doc.hash) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress),
            verticalAlignment = Alignment.Top,
        ) {
            DocumentThumb(
                file = thumbFile,
                version = doc.thumbnailUpdatedAt,
                displayName = doc.displayName,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        doc.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (doc.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = if (doc.pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    "${
                        relativeOpenedAt(
                            resources,
                            doc.lastOpenedAt,
                            nowMs
                        )
                    }  ·  ${pageCountLabel(resources, doc.pageCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    tags.forEach { tag ->
                        TagChip(tag = tag, onRemove = { onRemoveTag(tag.name) })
                    }
                    AddTagButton(onClick = onAddTag)
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: TagEntity, onRemove: () -> Unit) {
    val color = Color(tag.colorArgb)
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(ChipShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.4f), ChipShape)
            .clickable(onClick = {})
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tag.name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = color,
            )
        }
    }
}

@Composable
private fun AddTagButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DocumentThumb(file: File, version: Long?, displayName: String) {
    // `version` changes whenever the thumbnail is re-rendered to the same path, forcing a reload.
    val bitmap by produceState(initialValue = null as android.graphics.Bitmap?, file, version) {
        value = withContext(Dispatchers.IO) {
            version
                ?.takeIf { file.isFile }
                ?.let { BitmapFactory.decodeFile(file.path) }
        }
    }
    val thumbShape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
    Box(
        Modifier
            .size(ThumbWidth, ThumbHeight)
            .clip(thumbShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val letter =
                displayName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "P"
            Text(
                letter,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    val start = remember { TagColors.toHsv(TagColors.palette.first()) }
    var hue by remember { mutableStateOf(start[0]) }
    var sat by remember { mutableStateOf(start[1]) }
    var valueV by remember { mutableStateOf(start[2]) }
    val current = TagColors.fromHsv(hue, sat, valueV)

    fun applyPreset(color: Color) {
        val hsv = TagColors.toHsv(color)
        hue = hsv[0]
        sat = hsv[1]
        valueV = hsv[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.tag_dialog_placeholder)) },
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.tag_dialog_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    TagColors.palette.forEach { color ->
                        val chosen = color.toArgb() == current.toArgb()
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (chosen) 2.dp else 1.dp,
                                    color = if (chosen) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        Color.Black.copy(alpha = 0.12f)
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { applyPreset(color) },
                        )
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(current)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(contentAlignment = Alignment.Center) {
                    HueBar(Modifier.padding(horizontal = 10.dp))
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        modifier = Modifier.height(30.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = current,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        ),
                    )
                }
                Text(stringResource(R.string.tag_dialog_saturation), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = sat,
                    onValueChange = { sat = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.height(30.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = current,
                        activeTrackColor = current
                    ),
                )
                Text(stringResource(R.string.tag_dialog_brightness), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = valueV,
                    onValueChange = { valueV = it },
                    valueRange = 0.15f..1f,
                    modifier = Modifier.height(30.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = current,
                        activeTrackColor = current
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value, current.toArgb()) }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun HueBar(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    ),
                ),
            ),
    )
}
