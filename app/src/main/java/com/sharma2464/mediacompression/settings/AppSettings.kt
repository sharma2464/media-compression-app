package com.sharma2464.mediacompression.settings

import android.content.Context
import androidx.core.content.edit
import com.sharma2464.mediacompression.data.FileKind

enum class CompressionMode { LOSSLESS_ONLY, ADAPTIVE }

/**
 * Where compressed output ends up:
 * - [COMPRESSED_COPY]: original is left untouched; compressed file goes into a `COMPRESSED/`
 *   folder mirroring the source tree, nested under the picked root.
 * - [REPLACE_IN_PLACE]: the original is backed up into an `ORIGINALS/` mirrored folder
 *   (nested under the picked root), then overwritten in place with the compressed result.
 */
enum class StorageMode { COMPRESSED_COPY, REPLACE_IN_PLACE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Groups [FileKind]s under one user-facing toggle (photos cover both stills and motion photos). */
enum class FeatureGroup(val label: String, val kinds: Set<FileKind>) {
    PHOTOS("Photos", setOf(FileKind.PHOTO, FileKind.LIVE_PHOTO)),
    VIDEOS("Videos", setOf(FileKind.VIDEO)),
    PDFS("PDFs", setOf(FileKind.PDF)),
    DOCUMENTS("Documents", setOf(FileKind.DOCUMENT)),
    TEXT("Text files", setOf(FileKind.TEXT)),
    OTHER("Other files", setOf(FileKind.OTHER)),
}

/** Thin SharedPreferences wrapper — no need for DataStore at this scope. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var rootTreeUri: String?
        get() = prefs.getString(KEY_ROOT_URI, null)
        set(value) = prefs.edit { putString(KEY_ROOT_URI, value) }

    // Null means "use the default": a COMPRESSED/ folder mirroring the source tree, created
    // alongside it. Set to send compressed output to a different SAF tree instead.
    var destinationTreeUri: String?
        get() = prefs.getString(KEY_DESTINATION_URI, null)
        set(value) = prefs.edit { putString(KEY_DESTINATION_URI, value) }

    /** Per-batch override from compress preview; not persisted. */
    var sessionDestinationTreeUri: String? = null

    var compressionMode: CompressionMode
        get() = CompressionMode.valueOf(
            prefs.getString(KEY_MODE, CompressionMode.ADAPTIVE.name) ?: CompressionMode.ADAPTIVE.name,
        )
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    var storageMode: StorageMode
        get() = StorageMode.valueOf(
            prefs.getString(KEY_STORAGE_MODE, StorageMode.COMPRESSED_COPY.name) ?: StorageMode.COMPRESSED_COPY.name,
        )
        set(value) = prefs.edit { putString(KEY_STORAGE_MODE, value.name) }

    // Defaults to every kind enabled; stored as a comma-joined set of FileKind names.
    var enabledKinds: Set<FileKind>
        get() = prefs.getStringSet(KEY_ENABLED_KINDS, null)
            ?.mapNotNull { runCatching { FileKind.valueOf(it) }.getOrNull() }?.toSet()
            ?: FileKind.entries.toSet()
        set(value) = prefs.edit { putStringSet(KEY_ENABLED_KINDS, value.map { it.name }.toSet()) }

    var themeMode: ThemeMode
        get() = ThemeMode.valueOf(
            prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name,
        )
        set(value) = prefs.edit { putString(KEY_THEME_MODE, value.name) }

    companion object {
        private const val KEY_ROOT_URI = "root_tree_uri"
        private const val KEY_DESTINATION_URI = "destination_tree_uri"
        private const val KEY_MODE = "compression_mode"
        private const val KEY_STORAGE_MODE = "storage_mode"
        private const val KEY_ENABLED_KINDS = "enabled_kinds"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
