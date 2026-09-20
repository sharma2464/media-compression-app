package com.sharma2464.mediacompression.data

import com.sharma2464.mediacompression.ui.BrowserEntry
import java.io.File

class BrowserCache(private val dao: BrowserCacheDao) {

    suspend fun loadChildren(parentDir: File): List<BrowserEntry>? {
        val parentPath = parentDir.absolutePath
        if (dao.getMeta(parentPath) == null) return null
        return dao.getChildren(parentPath).map { it.toBrowserEntry() }
    }

    suspend fun saveListing(parentDir: File, entries: List<BrowserEntry>) {
        val now = System.currentTimeMillis()
        val parentPath = parentDir.absolutePath
        val children = entries.map { it.toCached(parentPath, now) }
        val meta = DirectoryListingMeta(
            parentPath = parentPath,
            listedAt = now,
            parentLastModified = parentDir.lastModified(),
        )
        dao.replaceChildren(parentPath, children, meta)
    }
}

private fun CachedBrowserEntry.toBrowserEntry(): BrowserEntry {
    val file = File(absolutePath)
    val fileKind = kind?.let { runCatching { FileKind.valueOf(it) }.getOrNull() }
    return BrowserEntry(
        file = file,
        name = name,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        kind = fileKind,
        dirTotalSizeBytes = dirTotalSizeBytes,
        dirFileCount = dirFileCount,
        dateTakenMs = dateTakenMs,
    )
}

private fun BrowserEntry.toCached(parentPath: String, listedAt: Long): CachedBrowserEntry =
    CachedBrowserEntry(
        absolutePath = file.absolutePath,
        parentPath = parentPath,
        name = name,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        dirTotalSizeBytes = dirTotalSizeBytes,
        dirFileCount = dirFileCount,
        kind = kind?.name,
        dateTakenMs = dateTakenMs,
        listedAt = listedAt,
    )
