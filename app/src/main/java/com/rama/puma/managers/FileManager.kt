package com.rama.puma.managers

import android.os.Environment
import java.io.File

data class FsEntry(
    val file: File,
    val isDirectory: Boolean,
    val name: String,
    val size: Long, // 0 for directories
    val lastModified: Long,
) {
    val extension: String get() = if (isDirectory) "" else file.extension.lowercase()
}

class FileManager {

    // Navigation stack, index 0 is root, last is current
    private val stack: ArrayDeque<File> = ArrayDeque()

    val currentDir: File get() = stack.last()
    val isAtRoot: Boolean get() = stack.size <= 1

    // Breadcrumb path segments (name only, ordered root→current)
    val breadcrumb: List<String> get() = stack.map { it.name.ifEmpty { "Storage" } }

    fun init() {
        stack.clear()
        stack.addLast(Environment.getExternalStorageDirectory())
    }

    // Navigate into a child directory. Returns false if not a directory.
    fun enter(dir: File): Boolean {
        if (!dir.isDirectory) return false
        stack.addLast(dir)
        return true
    }

    // Go up one level. Returns false if already at root.
    fun goUp(): Boolean {
        if (isAtRoot) return false
        stack.removeLast()
        return true
    }

    // Pop back to root.
    fun goToRoot() {
        while (!isAtRoot) stack.removeLast()
    }

    /**
     * List the current directory contents:
     * - directories first, then files
     * - each group sorted alphabetically (case-insensitive)
     * - hidden entries (dot-files) excluded
     */
    fun listCurrent(query: String = ""): List<FsEntry> {
        val files = currentDir.listFiles() ?: return emptyList()
        val q = query.trim().lowercase()
        return files
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { f ->
                FsEntry(
                    file = f,
                    isDirectory = f.isDirectory,
                    name = f.name,
                    size = if (f.isFile) f.length() else 0L,
                    lastModified = f.lastModified(),
                )
            }
    }

    companion object {
        fun formatSize(bytes: Long): String = when {
            bytes < 1_024L -> "$bytes B"
            bytes < 1_048_576L -> "%.1f KB".format(bytes / 1_024f)
            bytes < 1_073_741_824L -> "%.1f MB".format(bytes / 1_048_576f)
            else -> "%.1f GB".format(bytes / 1_073_741_824f)
        }
    }
}
