package com.sharma2464.mediacompression.settings

import android.content.Context
import androidx.core.content.edit
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressionStrength
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

    /** Per-batch strength from compress preview; not persisted. */
    var sessionCompressionStrength: CompressionStrength? = null

    /** Per-batch compress UI settings; not persisted. */
    var sessionCompressJobSettings: CompressJobSettings? = null

    var compressionStrength: CompressionStrength
        get() = prefs.getString(KEY_STRENGTH, CompressionStrength.BALANCED.name)
            ?.let { runCatching { CompressionStrength.valueOf(it) }.getOrNull() }
            ?: CompressionStrength.BALANCED
        set(value) = prefs.edit { putString(KEY_STRENGTH, value.name) }

    var compressionMode: CompressionMode
        get() = prefs.getString(KEY_MODE, CompressionMode.ADAPTIVE.name)
            ?.let { runCatching { CompressionMode.valueOf(it) }.getOrNull() }
            ?: CompressionMode.ADAPTIVE
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    var storageMode: StorageMode
        get() = prefs.getString(KEY_STORAGE_MODE, StorageMode.COMPRESSED_COPY.name)
            ?.let { runCatching { StorageMode.valueOf(it) }.getOrNull() }
            ?: StorageMode.COMPRESSED_COPY
        set(value) = prefs.edit { putString(KEY_STORAGE_MODE, value.name) }

    // Defaults to every kind enabled; stored as a comma-joined set of FileKind names.
    var enabledKinds: Set<FileKind>
        get() = prefs.getStringSet(KEY_ENABLED_KINDS, null)
            ?.mapNotNull { runCatching { FileKind.valueOf(it) }.getOrNull() }?.toSet()
            ?: FileKind.entries.toSet()
        set(value) = prefs.edit { putStringSet(KEY_ENABLED_KINDS, value.map { it.name }.toSet()) }

    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        set(value) = prefs.edit { putString(KEY_THEME_MODE, value.name) }

    companion object {
        private const val KEY_ROOT_URI = "root_tree_uri"
        private const val KEY_DESTINATION_URI = "destination_tree_uri"
        private const val KEY_MODE = "compression_mode"
        private const val KEY_STRENGTH = "compression_strength"
        private const val KEY_STORAGE_MODE = "storage_mode"
        private const val KEY_ENABLED_KINDS = "enabled_kinds"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
