package com.example.pdf_reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf_reader.ui.pdf.ReadingMode

@Composable
fun ReaderTopBar(
    displayName: String,
    pageIndex: Int,
    pageCount: Int,
    mode: ReadingMode,
    onModeChange: (ReadingMode) -> Unit,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (pageCount > 0) {
            PageBadge(page = pageIndex + 1, total = pageCount)
        }
        ReadingModeSelector(mode = mode, onModeChange = onModeChange)
        IconButton(onClick = onToggleSearch) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun PageBadge(page: Int, total: Int) {
    Text(
        "$page / $total",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ReadingModeSelector(
    mode: ReadingMode,
    onModeChange: (ReadingMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeButton(
            selected = mode == ReadingMode.Vertical,
            icon = Icons.Outlined.ViewAgenda,
            onClick = { onModeChange(ReadingMode.Vertical) },
        )
        ModeButton(
            selected = mode == ReadingMode.Horizontal,
            icon = Icons.Outlined.ViewWeek,
            onClick = { onModeChange(ReadingMode.Horizontal) },
        )
        ModeButton(
            selected = mode == ReadingMode.SinglePage,
            icon = Icons.Outlined.CropPortrait,
            onClick = { onModeChange(ReadingMode.SinglePage) },
        )
    }
}

@Composable
private fun ModeButton(
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
