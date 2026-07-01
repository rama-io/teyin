package com.rama.teyin.managers

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.rama.teyin.R
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

class FileManager(private val context: Context) {

    // Navigation stack, index 0 is root, last is current
    private val stack: ArrayDeque<File> = ArrayDeque()

    val currentDir: File get() = stack.last()
    val isAtRoot: Boolean get() = stack.size <= 1

    // Breadcrumb path segments (name only, ordered root→current)
    val breadcrumb: List<String> get() = stack.map { it.name.ifEmpty { "Storage" } }

    private val rootStoragePath: String
        get() = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

    fun init() {
        stack.clear()
        stack.addLast(Environment.getExternalStorageDirectory())
    }

    // Navigate into a child directory. Returns false if not a directory.
    fun enter(dir: File): Boolean {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q && !dir.isDirectory) return false
        stack.addLast(dir)
        return true
    }

    /**
     * Jump directly to any absolute directory, replacing the current stack root.
     * Use this for SD card / USB entries which aren't under the primary storage root.
     */
    fun enterAbsolute(dir: File): Boolean {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q && !dir.isDirectory) return false
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
     * - hidden entries (dot-files) filtered unless showHidden is true
     */
    fun listCurrent(query: String = "", showHidden: Boolean = false): List<FsEntry> {
        // Android 10 (API 29): scoped storage blocks File.listFiles() even with
        // READ_EXTERNAL_STORAGE granted. Use MediaStore to discover files/directories.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            return listCurrentViaMediaStore(query, showHidden)
        }

        val files = currentDir.listFiles() ?: return emptyList()
        val q = query.trim().lowercase()
        return files
            .filter { showHidden || !it.name.startsWith(".") }
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

    /**
     * Android 10 fallback: reconstruct the direct children of [currentDir] from
     * MediaStore. On API 29, [File.listFiles] is blocked by scoped storage even
     * with [READ_EXTERNAL_STORAGE] granted. MediaStore only exposes indexed files
     * (not directory rows), so we infer directories from parent paths and add the
     * standard public folders when at the storage root so empty directories still
     * appear.
     */
    private fun listCurrentViaMediaStore(query: String, showHidden: Boolean): List<FsEntry> {
        val dir = currentDir
        val dirPath = dir.absolutePath.trimEnd('/')
        val prefix = "$dirPath/"
        val q = query.trim().lowercase()

        val rows = queryMediaStoreRows(prefix)
        val directoryPaths = inferDirectoryPaths(rows, prefix)

        val children = buildChildEntries(rows, directoryPaths, dir, prefix, showHidden, q)
        addEmptyDirectories(directoryPaths, children, dir, prefix, showHidden, q)

        return children.values
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .toList()
    }

    /**
     * Queries [MediaStore.Files] for all indexed entries under [prefix].
     */
    private fun queryMediaStoreRows(prefix: String): List<MediaStoreRow> {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val selection = "${MediaStore.MediaColumns.DATA} LIKE ?"
        val selectionArgs = arrayOf("$prefix%")

        return context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            ?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modifiedCol =
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                buildList {
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol) ?: continue
                        val displayName = cursor.getString(nameCol) ?: continue
                        if (!path.startsWith(prefix)) continue
                        add(
                            MediaStoreRow(
                                path = path,
                                displayName = displayName,
                                size = cursor.getLong(sizeCol),
                                modified = cursor.getLong(modifiedCol),
                            )
                        )
                    }
                }
            } ?: emptyList()
    }

    /**
     * Infers directory paths from indexed file paths. When at the storage root,
     * also includes the standard public folders so empty directories still show.
     */
    private fun inferDirectoryPaths(rows: List<MediaStoreRow>, prefix: String): Set<String> {
        val directoryPaths = mutableSetOf<String>()

        for (row in rows) {
            val relative = row.path.removePrefix(prefix)
            if (relative.isEmpty()) continue

            var parent = relative
            while (parent.contains('/')) {
                parent = parent.substringBeforeLast('/')
                directoryPaths.add("$prefix$parent")
            }
        }

        if (prefix.trimEnd('/') == rootStoragePath) {
            directoryPaths.addAll(getStandardRootDirectories())
        }

        return directoryPaths
    }

    /**
     * Returns the well-known public folder paths at the storage root.
     *
     * Note: [Environment.getExternalStoragePublicDirectory] is deprecated in API 29,
     * but for a general file manager on API 29 it remains the most reliable way to
     * obtain the canonical paths for these folders. We suppress the deprecation
     * warning and keep the call isolated to this helper.
     */
    @Suppress("DEPRECATION")
    private fun getStandardRootDirectories(): Set<String> {
        return standardDirectoryNames
            .map { Environment.getExternalStoragePublicDirectory(it).absolutePath }
            .toSet()
    }

    /**
     * Builds [FsEntry] children from the MediaStore rows. Files and directories are
     * classified using the precomputed [directoryPaths] set.
     */
    private fun buildChildEntries(
        rows: List<MediaStoreRow>,
        directoryPaths: Set<String>,
        dir: File,
        prefix: String,
        showHidden: Boolean,
        query: String,
    ): MutableMap<String, FsEntry> {
        val children = mutableMapOf<String, FsEntry>()

        for (row in rows) {
            val relative = row.path.removePrefix(prefix)
            if (relative.isEmpty()) continue

            val firstSegment = relative.substringBefore('/')
            if (firstSegment.isEmpty()) continue
            if (!showHidden && firstSegment.startsWith('.')) continue
            if (query.isNotEmpty() && !firstSegment.lowercase().contains(query)) continue
            if (children.containsKey(firstSegment)) continue

            val childPath = "$prefix$firstSegment"
            val isDirectory = childPath in directoryPaths

            children[firstSegment] = if (isDirectory) {
                FsEntry(
                    file = File(dir, firstSegment),
                    isDirectory = true,
                    name = firstSegment,
                    size = 0L,
                    lastModified = 0L,
                )
            } else {
                FsEntry(
                    file = File(dir, firstSegment),
                    isDirectory = false,
                    name = firstSegment,
                    size = row.size,
                    lastModified = row.modified * 1000L,
                )
            }
        }

        return children
    }

    /**
     * Adds directories that are known to exist but have no indexed files yet.
     */
    private fun addEmptyDirectories(
        directoryPaths: Set<String>,
        children: MutableMap<String, FsEntry>,
        dir: File,
        prefix: String,
        showHidden: Boolean,
        query: String,
    ) {
        for (dirPath in directoryPaths) {
            val relative = dirPath.removePrefix(prefix)
            if (relative.contains('/')) continue
            if (!showHidden && relative.startsWith('.')) continue
            if (query.isNotEmpty() && !relative.lowercase().contains(query)) continue
            if (children.containsKey(relative)) continue

            children[relative] = FsEntry(
                file = File(dir, relative),
                isDirectory = true,
                name = relative,
                size = 0L,
                lastModified = 0L,
            )
        }
    }

    /**
     * A single indexed file entry from [MediaStore.Files].
     */
    private data class MediaStoreRow(
        val path: String,
        val displayName: String,
        val size: Long,
        val modified: Long,
    )

    companion object {

        private val standardDirectoryNames = listOf(
            Environment.DIRECTORY_ALARMS,
            // Environment.DIRECTORY_AUDIOBOOKS was only added in API 29.
            // Referencing the field directly causes a NoSuchFieldError at
            // class-init time on older OS versions (e.g. API 21), which
            // crashes this whole class before any code runs. Use the
            // literal value instead, since it's just a plain string.
            "Audiobooks",
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_NOTIFICATIONS,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_PODCASTS,
            Environment.DIRECTORY_RINGTONES,
        )

        fun formatSize(res: Resources, bytes: Long): String = when {
            bytes < 1_024L -> res.getString(R.string.format_size_bytes, bytes)
            bytes < 1_048_576L -> res.getString(R.string.format_size_kb, bytes / 1_024f)
            bytes < 1_073_741_824L -> res.getString(R.string.format_size_mb, bytes / 1_048_576f)
            else -> res.getString(R.string.format_size_gb, bytes / 1_073_741_824f)
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