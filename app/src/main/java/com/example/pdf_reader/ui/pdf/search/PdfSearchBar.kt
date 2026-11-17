package com.example.pdf_reader.ui.pdf.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.pdf_reader.R
import com.example.pdf_reader.pdfium.SearchFlags

/** Search field, hit counter, next/prev and match options. Focuses the field when shown. */
@Composable
fun PdfSearchBar(search: SearchState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search.query,
                onValueChange = search::updateQuery,
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search.next() }),
            )
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_close))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = search.flags and SearchFlags.MATCH_CASE != 0,
                onClick = { search.toggleFlag(SearchFlags.MATCH_CASE) },
                label = { Text("Aa") },
            )
            FilterChip(
                selected = search.flags and SearchFlags.MATCH_WHOLE_WORD != 0,
                onClick = { search.toggleFlag(SearchFlags.MATCH_WHOLE_WORD) },
                label = { Text(stringResource(R.string.search_whole_words)) },
            )
            Spacer(Modifier.weight(1f))
            if (search.isSearching) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(4.dp))
            }
            Text(counterText(search, stringResource(R.string.search_no_results)), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = search::prev, enabled = search.results.hits.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = stringResource(R.string.search_previous))
            }
            IconButton(onClick = search::next, enabled = search.results.hits.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = stringResource(R.string.search_next))
            }
        }
    }
}

private fun counterText(search: SearchState, noResults: String): String {
    if (search.query.isBlank()) return ""
    val total = search.results.hits.size
    val more = if (search.results.isComplete) "" else "+"
    return when {
        total == 0 && search.results.isComplete -> noResults
        total == 0 -> "…"
        else -> "${search.currentIndex + 1}/$total$more"
    }
}
