package com.sharma2464.tindercompression.scan

import androidx.documentfile.provider.DocumentFile

/** Walks a `/`-joined relative path down from [root], one segment per directory hop. */
fun findByRelativePath(root: DocumentFile, relativePath: String): DocumentFile? {
    var current = root
    for (segment in relativePath.split("/")) {
        current = current.findFile(segment) ?: return null
    }
    return current
}
