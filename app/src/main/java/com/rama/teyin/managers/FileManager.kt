package com.rama.teyin.managers

import android.os.Environment
import android.os.StatFs
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

    /**
     * Jump directly to any absolute directory, replacing the current stack root.
     * Use this for SD card / USB entries which aren't under the primary storage root.
     */
    fun enterAbsolute(dir: File): Boolean {
        if (!dir.isDirectory) return false
        stack.clear()
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

        /**
         * Returns the free bytes on the volume that contains [dir].
         * Uses StatFs for accuracy on both internal and removable storage.
         */
        fun getFreeBytes(dir: File): Long {
            return try {
                val stat = StatFs(dir.absolutePath)
                stat.availableBlocksLong * stat.blockSizeLong
            } catch (_: Exception) {
                Long.MAX_VALUE // be permissive if we can't stat
            }
        }

        /**
         * Recursively sums the size of [src] (file or directory).
         */
        fun totalSize(src: File): Long = when {
            src.isFile -> src.length()
            src.isDirectory -> src.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            else -> 0L
        }

        /**
         * Returns true if [destDir] has enough space to hold [requiredBytes],
         * with a small safety margin (1 MB).
         */
        fun hasEnoughSpace(destDir: File, requiredBytes: Long): Boolean {
            val free = getFreeBytes(destDir)
            val margin = 1_048_576L // 1 MB safety margin
            return free >= requiredBytes + margin
        }

        /**
         * Returns a [File] in [destDir] that does not collide with any existing entry.
         * If [baseName] is free, it is returned as-is.
         * Otherwise names are tried: "baseName (2)", "baseName (3)", etc.
         * For files the extension is preserved: "photo (2).jpg".
         */
        fun resolveNonConflictingName(destDir: File, baseName: String): File {
            val candidate = File(destDir, baseName)
            if (!candidate.exists()) return candidate

            // Split name and extension for files
            val dotIndex = baseName.lastIndexOf('.')
            val nameNoExt = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
            val ext = if (dotIndex > 0) baseName.substring(dotIndex) else "" // includes the dot

            var counter = 2
            while (true) {
                val newName = "$nameNoExt ($counter)$ext"
                val f = File(destDir, newName)
                if (!f.exists()) return f
                counter++
            }
        }
    }
}
