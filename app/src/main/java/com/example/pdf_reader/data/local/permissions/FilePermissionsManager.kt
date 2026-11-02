package com.example.pdf_reader.data.local.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilePermissionsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Remember file permissions for the given URI
    fun takePersistableReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
    }
}
