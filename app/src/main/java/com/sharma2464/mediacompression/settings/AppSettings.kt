package com.sharma2464.mediacompression.settings

import android.content.Context
import androidx.core.content.edit
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressionStrength
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.FilenameSegment

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

    var showBitrate: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BITRATE, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_BITRATE, value) }

    var useMbpsForBitrate: Boolean
        get() = prefs.getBoolean(KEY_USE_MBPS, true)
        set(value) = prefs.edit { putBoolean(KEY_USE_MBPS, value) }

    var showTargetSizePresets: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TARGET_SIZE_PRESET, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_TARGET_SIZE_PRESET, value) }

    var bestQualityPreset: QualityPresetConfig
        get() = prefs.getString(KEY_PRESET_BEST, null)?.let { QualityPresetConfig.fromJson(it) }
            ?: QualityPresetConfig.defaultBest
        set(value) = prefs.edit { putString(KEY_PRESET_BEST, value.toJson().toString()) }

    var highQualityPreset: QualityPresetConfig
        get() = prefs.getString(KEY_PRESET_HIGH, null)?.let { QualityPresetConfig.fromJson(it) }
            ?: QualityPresetConfig.defaultHigh
        set(value) = prefs.edit { putString(KEY_PRESET_HIGH, value.toJson().toString()) }

    var mediumQualityPreset: QualityPresetConfig
        get() = prefs.getString(KEY_PRESET_MEDIUM, null)?.let { QualityPresetConfig.fromJson(it) }
            ?: QualityPresetConfig.defaultMedium
        set(value) = prefs.edit { putString(KEY_PRESET_MEDIUM, value.toJson().toString()) }

    var lowQualityPreset: QualityPresetConfig
        get() = prefs.getString(KEY_PRESET_LOW, null)?.let { QualityPresetConfig.fromJson(it) }
            ?: QualityPresetConfig.defaultLow
        set(value) = prefs.edit { putString(KEY_PRESET_LOW, value.toJson().toString()) }

    var targetSizePresets: List<TargetSizePreset>
        get() = decodeTargetSizePresets(prefs.getString(KEY_TARGET_SIZE_PRESETS, null))
        set(value) = prefs.edit { putString(KEY_TARGET_SIZE_PRESETS, encodeTargetSizePresets(value)) }

    var defaultVideoConfig: DefaultVideoConfig
        get() = DefaultVideoConfig.fromJson(prefs.getString(KEY_DEFAULT_VIDEO, null))
        set(value) = prefs.edit { putString(KEY_DEFAULT_VIDEO, value.toJson().toString()) }

    var defaultAudioConfig: DefaultAudioConfig
        get() = DefaultAudioConfig.fromJson(prefs.getString(KEY_DEFAULT_AUDIO, null))
        set(value) = prefs.edit { putString(KEY_DEFAULT_AUDIO, value.toJson().toString()) }

    var allCodecsUnlocked: Boolean
        get() = prefs.getBoolean(KEY_ALL_CODECS_UNLOCKED, false)
        set(value) = prefs.edit { putBoolean(KEY_ALL_CODECS_UNLOCKED, value) }

    var allCodecsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALL_CODECS_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ALL_CODECS_ENABLED, value) }

    var videoEngine: VideoEngine
        get() = prefs.getString(KEY_VIDEO_ENGINE, VideoEngine.MEDIA3.name)
            ?.let { runCatching { VideoEngine.valueOf(it) }.getOrNull() }
            ?: VideoEngine.MEDIA3
        set(value) = prefs.edit { putString(KEY_VIDEO_ENGINE, value.name) }

    var filenameSegments: List<FilenameSegment>
        get() = FilenameSegment.deserialize(prefs.getString(KEY_FILENAME_SEGMENTS, null))
        set(value) = prefs.edit {
            putString(KEY_FILENAME_SEGMENTS, FilenameSegment.serialize(value))
        }

    fun qualityPresetFor(tier: com.sharma2464.mediacompression.compress.PresetTier): QualityPresetConfig =
        when (tier) {
            com.sharma2464.mediacompression.compress.PresetTier.BEST -> bestQualityPreset
            com.sharma2464.mediacompression.compress.PresetTier.HIGH -> highQualityPreset
            com.sharma2464.mediacompression.compress.PresetTier.MEDIUM -> mediumQualityPreset
            com.sharma2464.mediacompression.compress.PresetTier.LOW -> lowQualityPreset
        }

    fun resetQualityPresets() {
        bestQualityPreset = QualityPresetConfig.defaultBest
        highQualityPreset = QualityPresetConfig.defaultHigh
        mediumQualityPreset = QualityPresetConfig.defaultMedium
        lowQualityPreset = QualityPresetConfig.defaultLow
    }

    fun resetTargetSizePresets() {
        targetSizePresets = TargetSizePreset.defaults
    }

    fun resetFilenameSegments() {
        filenameSegments = FilenameSegment.defaultSegments
    }

    fun addTargetSizePreset(label: String, sizeMb: Float) {
        val id = "custom_${System.currentTimeMillis()}"
        targetSizePresets = targetSizePresets + TargetSizePreset(id, sizeMb, label.trim())
    }

    fun updateTargetSizePreset(id: String, label: String, sizeMb: Float) {
        targetSizePresets = targetSizePresets.map {
            if (it.id == id) it.copy(label = label.trim(), sizeMb = sizeMb) else it
        }
    }

    fun deleteTargetSizePreset(id: String) {
        targetSizePresets = targetSizePresets.filterNot { it.id == id }
    }

    fun resetDefaultVideoConfig() {
        defaultVideoConfig = DefaultVideoConfig()
    }

    fun resetDefaultAudioConfig() {
        defaultAudioConfig = DefaultAudioConfig()
    }

    fun enableAllCodecsFeature() {
        allCodecsUnlocked = true
        allCodecsEnabled = true
    }

    fun disableAllCodecsFeature() {
        allCodecsUnlocked = false
        allCodecsEnabled = false
    }

    companion object {
        private const val KEY_ROOT_URI = "root_tree_uri"
        private const val KEY_DESTINATION_URI = "destination_tree_uri"
        private const val KEY_MODE = "compression_mode"
        private const val KEY_STRENGTH = "compression_strength"
        private const val KEY_STORAGE_MODE = "storage_mode"
        private const val KEY_ENABLED_KINDS = "enabled_kinds"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SHOW_BITRATE = "show_bitrate"
        private const val KEY_USE_MBPS = "use_mbps"
        private const val KEY_SHOW_TARGET_SIZE_PRESET = "show_target_size_preset"
        private const val KEY_PRESET_BEST = "preset_best"
        private const val KEY_PRESET_HIGH = "preset_high"
        private const val KEY_PRESET_MEDIUM = "preset_medium"
        private const val KEY_PRESET_LOW = "preset_low"
        private const val KEY_TARGET_SIZE_PRESETS = "target_size_presets"
        private const val KEY_DEFAULT_VIDEO = "default_video_config"
        private const val KEY_DEFAULT_AUDIO = "default_audio_config"
        private const val KEY_ALL_CODECS_UNLOCKED = "all_codecs_unlocked"
        private const val KEY_ALL_CODECS_ENABLED = "all_codecs_enabled"
        private const val KEY_FILENAME_SEGMENTS = "filename_segments_v2"
        private const val KEY_VIDEO_ENGINE = "video_engine"
    }
}
