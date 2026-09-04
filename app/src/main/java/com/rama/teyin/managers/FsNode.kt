package com.rama.teyin.managers

import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

sealed interface FsNode {
    val name: String
    val path: String
    val isDirectory: Boolean
    val size: Long
    val lastModified: Long
    val extension: String
        get() {
            if (isDirectory) return ""
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(dot + 1).lowercase(java.util.Locale.ROOT) else ""
        }
    val file: File?
    val uri: Uri?

    fun listChildren(query: String = "", showHidden: Boolean = false): List<FsNode>
    fun createFolder(name: String): FsNode?
    fun createFile(name: String, mimeType: String = "application/octet-stream"): FsNode?
    fun delete(): Boolean
    fun rename(newName: String): FsNode?
    fun openInputStream(): InputStream?
    fun openOutputStream(mode: String = "w"): OutputStream?

    companion object {
        fun resolveNonConflictingName(parent: FsNode, baseName: String): String {
            val parentFile = parent.file
            if (parentFile != null) {
                if (!File(parentFile, baseName).exists()) return baseName
                val dotIndex = baseName.lastIndexOf('.')
                val nameNoExt = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
                val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""
                var counter = 2
                while (true) {
                    val candidate = "$nameNoExt ($counter)$ext"
                    if (!File(parentFile, candidate).exists()) return candidate
                    counter++
                }
            } else {
                val existingNames = java.util.TreeSet<String>(java.lang.String.CASE_INSENSITIVE_ORDER).apply {
                    addAll(parent.listChildren(showHidden = true).map { it.name })
                }
                if (baseName !in existingNames) return baseName
                val dotIndex = baseName.lastIndexOf('.')
                val nameNoExt = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
                val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""
                var counter = 2
                while (true) {
                    val candidate = "$nameNoExt ($counter)$ext"
                    if (candidate !in existingNames) return candidate
                    counter++
                }
            }
        }

        fun totalSize(node: FsNode, visited: MutableSet<String> = mutableSetOf()): Long {
            if ((node as? FileNode)?.isSymbolicLink() == true) return 0L
            val canonical = node.uri?.toString() ?: (node as? FileNode)?.let {
                try { it.file.canonicalPath } catch (_: Exception) { it.path }
            } ?: node.path
            if (!visited.add(canonical)) return 0L
            if (!node.isDirectory) return node.size
            var total = 0L
            for (child in node.listChildren(showHidden = true)) {
                total += totalSize(child, visited)
            }
            return total
        }
    }
}

fun FsNode.resolveNonConflictingName(baseName: String): String =
    FsNode.resolveNonConflictingName(this, baseName)

fun FsNode.totalSize(): Long =
    FsNode.totalSize(this)

class FileNode(override val file: File, isDir: Boolean? = null) : FsNode {
    override val name: String get() = file.name
    override val path: String get() = file.absolutePath
    override val isDirectory: Boolean = isDir ?: file.isDirectory
    override val size: Long get() = if (file.isFile) file.length() else 0L
    override val lastModified: Long get() = file.lastModified()
    override val uri: Uri? get() = null

    override fun listChildren(query: String, showHidden: Boolean): List<FsNode> {
        val files = file.listFiles() ?: return emptyList()
        val q = query.trim()
        return files
            .filter { f ->
                (showHidden || !f.name.startsWith('.')) &&
                (q.isEmpty() || f.name.contains(q, ignoreCase = true))
            }
            .map { FileNode(it, it.isDirectory) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override fun createFolder(name: String): FsNode? {
        if (!isDirectory) return null
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.contains('\u0000')) return null
        val target = File(file, name)
        return try {
            if (target.parentFile?.canonicalFile != file.canonicalFile) return null
            if (target.mkdir()) FileNode(target) else null
        } catch (_: Exception) {
            null
        }
    }

    override fun createFile(name: String, mimeType: String): FsNode? {
        if (!isDirectory) return null
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.contains('\u0000')) return null
        val target = File(file, name)
        return try {
            if (target.parentFile?.canonicalFile != file.canonicalFile) return null
            if (target.createNewFile()) FileNode(target) else null
        } catch (_: Exception) {
            null
        }
    }

    fun isSymbolicLink(): Boolean {
        return try {
            val stat = android.system.Os.lstat(file.absolutePath)
            android.system.OsConstants.S_ISLNK(stat.st_mode)
        } catch (_: Throwable) {
            try {
                val canon = if (file.parent == null) file else File(file.parentFile?.canonicalFile, file.name)
                canon.path != canon.canonicalPath
            } catch (_: Throwable) {
                false
            }
        }
    }

    override fun delete(): Boolean {
        return if (isSymbolicLink()) {
            file.delete()
        } else if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    override fun rename(newName: String): FsNode? {
        val parent = file.parentFile ?: return null
        val target = File(parent, newName)
        if (target.exists()) {
            val isCaseOnly = file.name.equals(newName, ignoreCase = true) && file.name != newName
            if (!isCaseOnly) return null
            if (file.renameTo(target)) return FileNode(target)
            val temp = File(parent, ".tmp_rename_${System.nanoTime()}_${newName}")
            if (!file.renameTo(temp)) return null
            if (temp.renameTo(target)) {
                return FileNode(target)
            } else {
                temp.renameTo(file)
                return null
            }
        }
        return if (file.renameTo(target)) FileNode(target) else null
    }

    override fun openInputStream(): InputStream? {
        return try {
            FileInputStream(file)
        } catch (_: Exception) {
            null
        }
    }

    override fun openOutputStream(mode: String): OutputStream? {
        return try {
            val append = mode.contains('a')
            FileOutputStream(file, append)
        } catch (_: Exception) {
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileNode) return false
        return file == other.file
    }

    override fun hashCode(): Int = file.hashCode()

    override fun toString(): String = "FileNode(path=$path)"
}
