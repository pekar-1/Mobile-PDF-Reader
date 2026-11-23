package com.example.pdf_reader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pdf_reader.R
import com.example.pdf_reader.data.AppLanguage
import com.example.pdf_reader.data.AppSettings

@Composable
fun LibrarySidebar(
    language: String,
    onLanguageChange: (String) -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerShape = RectangleShape,
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.settings_language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            (listOf(AppLanguage.SYSTEM) + AppLanguage.supported).forEach { tag ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLanguageChange(tag) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = tag == language,
                        onClick = { onLanguageChange(tag) },
                    )
                    Text(languageLabel(tag), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            AppSettings.themes.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onThemeModeChange(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = mode == themeMode,
                        onClick = { onThemeModeChange(mode) },
                    )
                    Text(themeLabel(mode), style = MaterialTheme.typography.bodyLarge)
                }
            }
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
private fun themeLabel(mode: String): String = when (mode) {
    AppSettings.THEME_LIGHT -> stringResource(R.string.theme_light)
    AppSettings.THEME_DARK -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.language_system)
}
