package com.example.pdf_reader.ui.library

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf_reader.data.ThumbnailStore
import com.example.pdf_reader.data.db.DocumentWithTags
import com.example.pdf_reader.data.db.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val CardShape = RoundedCornerShape(14.dp)
private val ChipShape = RoundedCornerShape(20.dp)
private val ThumbWidth = 72.dp
private val ThumbHeight = 96.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentCard(
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
                    "${relativeOpenedAt(resources, doc.lastOpenedAt, nowMs)}  ·  ${pageCountLabel(resources, doc.pageCount)}",
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
