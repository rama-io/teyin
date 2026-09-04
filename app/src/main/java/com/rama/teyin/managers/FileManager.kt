package com.rama.teyin.managers

import android.content.Context
import android.content.res.Resources
import android.os.Environment
import android.os.StatFs
import com.rama.teyin.R
import java.io.File

typealias FsEntry = FsNode

class FileManager {

    // Navigation stack, index 0 is root, last is current
    private val stack: ArrayDeque<FsNode> = ArrayDeque()
    private var rootNode: FsNode = FileNode(Environment.getExternalStorageDirectory())

    val currentNode: FsNode get() = stack.lastOrNull() ?: rootNode
    @Deprecated("Use currentNode instead as DocumentNode may not have a backing File", ReplaceWith("currentNode.file"))
    val currentDir: File? get() = currentNode.file
    val isAtRoot: Boolean get() = stack.size <= 1

    // Breadcrumb path segments (name only, ordered root→current)
    val breadcrumb: List<String> get() = stack.map { it.name.ifEmpty { "Storage" } }

    fun init(customRoot: FsNode? = null) {
        rootNode = customRoot ?: FileNode(Environment.getExternalStorageDirectory())
        stack.clear()
        stack.addLast(rootNode)
    }

    // Navigate into a child directory. Returns false if not a directory.
    fun enter(node: FsNode): Boolean {
        if (!node.isDirectory) return false
        stack.addLast(node)
        return true
    }

    fun enter(dir: File): Boolean = enter(FileNode(dir))

    fun enterNode(node: FsNode, asRoot: Boolean = false): Boolean {
        if (!node.isDirectory) return false
        if (asRoot) {
            rootNode = node
            stack.clear()
            stack.addLast(node)
        } else {
            stack.addLast(node)
        }
        return true
    }

    /**
     * Jump directly to any absolute directory, replacing the current stack root.
     * Use this for SD card / USB entries which aren't under the primary storage root.
     */
    fun enterAbsolute(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val primaryRoot = try {
            Environment.getExternalStorageDirectory().canonicalPath
        } catch (_: Exception) {
            Environment.getExternalStorageDirectory().absolutePath
        }
        val targetCanon = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }
        val isUnderPrimary = targetCanon == primaryRoot || targetCanon.startsWith(primaryRoot + File.separator)
        if (rootNode is DocumentNode) {
            val rootPath = rootNode.path.trimEnd('/')
            val isUnderRoot = targetCanon == rootPath || targetCanon.startsWith(rootPath + File.separator)
            if (isUnderRoot || isUnderPrimary) {
                val rel = targetCanon.removePrefix(rootNode.path).trim('/')
                stack.clear()
                stack.addLast(rootNode)
                if (rel.isNotEmpty()) {
                    var currFile = File(rootNode.path)
                    for (segment in rel.split('/').filter { it.isNotEmpty() }) {
                        val child = stack.last().listChildren(showHidden = true)
                            .firstOrNull { it.isDirectory && it.name.equals(segment, ignoreCase = true) }
                        if (child != null) {
                            stack.addLast(child)
                            currFile = child.file ?: File(currFile, segment)
                        } else {
                            val nextFile = File(currFile, segment)
                            if (nextFile.isDirectory) {
                                stack.addLast(FileNode(nextFile))
                                currFile = nextFile
                            } else {
                                break
                            }
                        }
                    }
                }
                return true
            }
        }
        if (isUnderPrimary) {
            val root = FileNode(Environment.getExternalStorageDirectory())
            rootNode = root
            stack.clear()
            stack.addLast(root)
            val relative = targetCanon.removePrefix(primaryRoot).trimStart('/')
            if (relative.isNotEmpty()) {
                var curr = Environment.getExternalStorageDirectory()
                for (segment in relative.split('/')) {
                    if (segment.isNotEmpty()) {
                        curr = File(curr, segment)
                        stack.addLast(FileNode(curr))
                    }
                }
            }
            return true
        } else {
            val node = FileNode(dir)
            rootNode = node
            stack.clear()
            stack.addLast(node)
            return true
        }
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
    fun listCurrent(query: String = "", showHidden: Boolean = false): List<FsNode> {
        return currentNode.listChildren(query, showHidden)
    }

    companion object {

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

        fun totalSize(node: FsNode): Long = FsNode.totalSize(node)

        /**
         * Returns true if [destDir] has enough space to hold [requiredBytes],
         * with a small safety margin (1 MB).
         */
        fun hasEnoughSpace(destDir: File, requiredBytes: Long): Boolean {
            val free = getFreeBytes(destDir)
            val margin = 1_048_576L // 1 MB safety margin
            return free >= requiredBytes + margin
        }

        fun hasEnoughSpace(destNode: FsNode, requiredBytes: Long): Boolean {
            val destFile = destNode.file
            if (destFile != null) return hasEnoughSpace(destFile, requiredBytes)
            val destPath = destNode.path
            if (destPath.startsWith("/storage/")) {
                var curr: File? = File(destPath)
                while (curr != null && curr.path != "/storage" && curr.path != "/") {
                    if (curr.exists()) return hasEnoughSpace(curr, requiredBytes)
                    curr = curr.parentFile
                }
                val segments = destPath.split('/').filter { it.isNotEmpty() }
                val mountDir = if (segments.size >= 3 && segments[1] == "emulated") {
                    File("/storage/emulated/${segments[2]}")
                } else if (segments.size >= 2) {
                    File("/storage/${segments[1]}")
                } else {
                    null
                }
                if (mountDir != null && mountDir.exists()) {
                    return hasEnoughSpace(mountDir, requiredBytes)
                }
                return true
            }
            return true
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

        fun resolveNonConflictingName(destDir: FsNode, baseName: String): String =
            FsNode.resolveNonConflictingName(destDir, baseName)
    }
}
