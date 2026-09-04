package com.rama.teyin.managers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class DocumentNode(
    context: Context,
    val treeUri: Uri,
    override val uri: Uri,
    override val name: String,
    override val isDirectory: Boolean,
    override val size: Long,
    override val lastModified: Long,
    override val path: String
) : FsNode {
    val context: Context = context.applicationContext

    override val file: File? get() = null

    override fun listChildren(query: String, showHidden: Boolean): List<FsNode> {
        if (!isDirectory) return emptyList()

        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (_: Exception) {
            return emptyList()
        }

        val childUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        } catch (_: Exception) {
            return emptyList()
        }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val children = mutableListOf<DocumentNode>()
        val q = query.trim()

        try {
            context.contentResolver.query(childUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childDocId = (if (idIndex != -1) cursor.getString(idIndex) else null) ?: continue
                    val rawName = if (nameIndex != -1 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                    val displayName = rawName?.takeIf { it.isNotEmpty() }
                        ?: childDocId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "unnamed" }
                    val mimeType = (if (mimeIndex != -1) cursor.getString(mimeIndex) else null) ?: ""
                    val childSize = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    val childModified = if (modifiedIndex != -1 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L

                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                    if (!showHidden && displayName.startsWith('.')) continue
                    if (q.isNotEmpty() && !displayName.contains(q, ignoreCase = true)) continue

                    val itemUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    val childPath = if (path.isEmpty()) displayName else if (path.endsWith('/')) "$path$displayName" else "$path/$displayName"

                    children.add(
                        DocumentNode(
                            context = context,
                            treeUri = treeUri,
                            uri = itemUri,
                            name = displayName,
                            isDirectory = isDir,
                            size = if (isDir) 0L else childSize,
                            lastModified = childModified,
                            path = childPath
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return children.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override fun createFolder(name: String): FsNode? {
        if (!isDirectory) return null
        val childUri = try {
            DocumentsContract.createDocument(
                context.contentResolver,
                uri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name
            )
        } catch (_: Exception) {
            null
        } ?: return null
        val treeDocUri = try {
            val childDocId = DocumentsContract.getDocumentId(childUri)
            DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
        } catch (_: Exception) {
            childUri
        }
        val actualName = queryDisplayName(treeDocUri) ?: name
        val childPath = if (path.isEmpty()) actualName else if (path.endsWith('/')) "$path$actualName" else "$path/$actualName"
        return DocumentNode(
            context = context,
            treeUri = treeUri,
            uri = treeDocUri,
            name = actualName,
            isDirectory = true,
            size = 0L,
            lastModified = System.currentTimeMillis(),
            path = childPath
        )
    }

    override fun createFile(name: String, mimeType: String): FsNode? {
        if (!isDirectory) return null
        val childUri = try {
            DocumentsContract.createDocument(
                context.contentResolver,
                uri,
                mimeType,
                name
            )
        } catch (_: Exception) {
            null
        } ?: return null
        val treeDocUri = try {
            val childDocId = DocumentsContract.getDocumentId(childUri)
            DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
        } catch (_: Exception) {
            childUri
        }
        val actualName = queryDisplayName(treeDocUri) ?: name
        val childPath = if (path.isEmpty()) actualName else if (path.endsWith('/')) "$path$actualName" else "$path/$actualName"
        return DocumentNode(
            context = context,
            treeUri = treeUri,
            uri = treeDocUri,
            name = actualName,
            isDirectory = false,
            size = 0L,
            lastModified = System.currentTimeMillis(),
            path = childPath
        )
    }

    override fun delete(): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }

    override fun rename(newName: String): FsNode? {
        return try {
            val rawNewUri = DocumentsContract.renameDocument(context.contentResolver, uri, newName) ?: return null
            val newUri = try {
                val docId = DocumentsContract.getDocumentId(rawNewUri)
                DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            } catch (_: Exception) {
                rawNewUri
            }
            val actualName = queryDisplayName(newUri) ?: newName
            val trimmedPath = path.trimEnd('/')
            val newPath = if (trimmedPath.contains('/')) trimmedPath.substringBeforeLast('/') + "/$actualName" else actualName
            DocumentNode(
                context = context,
                treeUri = treeUri,
                uri = newUri,
                name = actualName,
                isDirectory = isDirectory,
                size = size,
                lastModified = System.currentTimeMillis(),
                path = newPath
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun openInputStream(): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
    }

    override fun openOutputStream(mode: String): OutputStream? {
        return try {
            context.contentResolver.openOutputStream(uri, mode)
        } catch (_: Exception) {
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentNode) return false
        return uri == other.uri
    }

    override fun hashCode(): Int = uri.hashCode()

    private fun queryDisplayName(docUri: Uri): String? {
        return try {
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            context.contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx != -1 && !cursor.isNull(idx)) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
    override fun toString(): String = "DocumentNode(path=$path, uri=$uri)"

    companion object {
        fun fromTreeUri(context: Context, treeUri: Uri, rootPath: String = ""): DocumentNode? {
            return try {
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri) ?: return null
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId) ?: return null
                var displayName: String? = null
                val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                context.contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                }
                val resolvedName = displayName?.takeIf { it.isNotEmpty() } ?: rootPath.trimEnd('/').substringAfterLast('/').ifEmpty { "Storage" }
                val resolvedPath = rootPath.ifEmpty { docUri.toString() }
                DocumentNode(
                    context = context,
                    treeUri = treeUri,
                    uri = docUri,
                    name = resolvedName,
                    isDirectory = true,
                    size = 0L,
                    lastModified = 0L,
                    path = resolvedPath
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
