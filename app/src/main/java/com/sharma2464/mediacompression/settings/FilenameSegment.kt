package com.sharma2464.mediacompression.settings

import org.json.JSONArray
import org.json.JSONObject

/** Josh Atticus Compressor filename builder segments (MIT). */
sealed class FilenameSegment {
    data class Text(val value: String) : FilenameSegment()
    data class Token(val key: String) : FilenameSegment()

    companion object {
        val defaultSegments: List<FilenameSegment> = listOf(
            Token("original_name"),
            Text("_"),
            Token("compressed"),
        )

        val availableTokens: List<Pair<String, String>> = listOf(
            "original_name" to "Original name",
            "compressed" to "Compressed",
            "date" to "Date",
            "time" to "Time",
            "random" to "Random ID",
            "resolution" to "Resolution",
            "fps" to "Framerate",
            "bitrate" to "Video bitrate",
            "audio_bitrate" to "Audio bitrate",
            "codec" to "Codec",
            "audio_status" to "Audio on/off",
            "preset" to "Quality preset",
        )

        fun normalize(segments: List<FilenameSegment>): List<FilenameSegment> {
            val result = mutableListOf<FilenameSegment>()
            var text = ""
            for (segment in segments) {
                when (segment) {
                    is Text -> text += segment.value
                    is Token -> {
                        if (text.isNotEmpty()) {
                            result.add(Text(text))
                            text = ""
                        }
                        result.add(segment)
                    }
                }
            }
            if (text.isNotEmpty() || result.isEmpty()) {
                result.add(Text(text))
            }
            return result
        }

        fun serialize(segments: List<FilenameSegment>): String {
            val arr = JSONArray()
            normalize(segments)
                .filter { it !is Text || it.value.isNotEmpty() || segments.size == 1 }
                .forEach { seg ->
                    when (seg) {
                        is Text -> arr.put(JSONObject().apply { put("type", "text"); put("value", seg.value) })
                        is Token -> arr.put(JSONObject().apply { put("type", "token"); put("key", seg.key) })
                    }
                }
            return arr.toString()
        }

        fun deserialize(raw: String?): List<FilenameSegment> {
            if (raw.isNullOrBlank()) return defaultSegments
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        when (o.optString("type")) {
                            "text" -> add(Text(o.optString("value", "")))
                            "token" -> add(Token(o.optString("key", "")))
                        }
                    }
                }.let { if (it.isEmpty()) defaultSegments else normalize(it) }
            }.getOrDefault(defaultSegments)
        }
    }
}
