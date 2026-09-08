package com.sharma2464.mediacompression.scan

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/** True once the user has granted "All files access" (MANAGE_EXTERNAL_STORAGE). */
fun hasFullStorageAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

/**
 * Best-effort SAF tree/document [Uri] -> real on-disk [File] resolution, for primary
 * external storage only. Callers must check [hasFullStorageAccess] before actually
 * reading/writing the result — without that permission the path resolves but isn't
 * accessible outside the SAF grant.
 * ponytail: SD cards / cloud providers resolve to a non-"primary" volume and return
 * null here; those callers should keep using plain DocumentFile SAF calls.
 */
fun resolveRealFile(uri: Uri): File? {
    val docId = runCatching {
        if (DocumentsContract.isDocumentUri(null, uri)) DocumentsContract.getDocumentId(uri)
        else DocumentsContract.getTreeDocumentId(uri)
    }.getOrNull() ?: return null
    val colon = docId.indexOf(':')
    if (colon == -1) return null
    val volume = docId.substring(0, colon)
    if (volume != "primary") return null
    val relativePath = docId.substring(colon + 1)
    val base = Environment.getExternalStorageDirectory()
    return if (relativePath.isEmpty()) base else File(base, relativePath)
}
