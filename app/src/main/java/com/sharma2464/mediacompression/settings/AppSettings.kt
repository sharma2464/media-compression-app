package com.sharma2464.mediacompression.settings

import android.content.Context
import androidx.core.content.edit

enum class CompressionMode { LOSSLESS_ONLY, ADAPTIVE }

/** Thin SharedPreferences wrapper — no need for DataStore at this scope. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var rootTreeUri: String?
        get() = prefs.getString(KEY_ROOT_URI, null)
        set(value) = prefs.edit { putString(KEY_ROOT_URI, value) }

    var compressionMode: CompressionMode
        get() = CompressionMode.valueOf(
            prefs.getString(KEY_MODE, CompressionMode.ADAPTIVE.name) ?: CompressionMode.ADAPTIVE.name,
        )
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    companion object {
        private const val KEY_ROOT_URI = "root_tree_uri"
        private const val KEY_MODE = "compression_mode"
    }
}
