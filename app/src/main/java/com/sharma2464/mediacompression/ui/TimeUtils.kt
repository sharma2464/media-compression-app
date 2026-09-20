package com.sharma2464.mediacompression.ui

fun formatTimeFromNow(timestampMs: Long?): String {
    if (timestampMs == null || timestampMs == 0L) return ""

    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs

    return when {
        diffMs < 60_000 -> "now"
        diffMs < 3_600_000 -> {
            val mins = diffMs / 60_000
            if (mins == 1L) "1 min ago" else "$mins mins ago"
        }
        diffMs < 86_400_000 -> {
            val hours = diffMs / 3_600_000
            if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }
        diffMs < 604_800_000 -> {
            val days = diffMs / 86_400_000
            if (days == 1L) "1 day ago" else "$days days ago"
        }
        else -> {
            val weeks = diffMs / 604_800_000
            if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
        }
    }
}
