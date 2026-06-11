package com.rama.teyin.activities

import android.Manifest
import android.animation.AnimatorSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.rama.teyin.CsActivity
import com.rama.teyin.R
import com.rama.teyin.adapters.DirEntry
import com.rama.teyin.adapters.DirectoryListAdapter
import com.rama.teyin.adapters.FileListAdapter
import com.rama.teyin.managers.FileManager
import com.rama.teyin.managers.PrefsManager
import java.io.File

class MainActivity : CsActivity() {
    private lateinit var rootView: View
    private lateinit var listView: ListView
    private lateinit var searchBar: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var searchButton: FrameLayout
    private lateinit var clearBtn: FrameLayout
    private lateinit var directoriesButton: FrameLayout
    private lateinit var directoriesFragment: LinearLayout
    private lateinit var filesFragment: LinearLayout
    private lateinit var settingsBtn: FrameLayout
    private lateinit var currentDir: LinearLayout
    private lateinit var currentFolderName: TextView
    private lateinit var menuBar: LinearLayout
    private lateinit var selectedCount: TextView
    private lateinit var cancelSelectionBtn: FrameLayout
    private lateinit var renameBtn: FrameLayout
    private lateinit var moveToFolderBtn: FrameLayout
    private lateinit var copyBtn: FrameLayout
    private lateinit var pasteBtn: FrameLayout
    private lateinit var appSettingsBtn: FrameLayout

    // Directory list (favorites)
    private lateinit var directoryList: ListView
    private lateinit var addToFavoritesBtn: View
    private lateinit var dirAdapter: DirectoryListAdapter

    private val fileManager = FileManager()
    private lateinit var adapter: FileListAdapter
    private var isSearchExpanded = false
    private var isProgrammaticSearchUpdate = false
    private val searchDebounceHandler = Handler(Looper.getMainLooper())
    private var searchDebounceRunnable: Runnable? = null
    private var currentSearchQuery: String = ""
    private var resumeRefreshRunnable: Runnable? = null
    private var fileSystemReady = false
    private var showDirs = false

    private enum class ClipboardMode { COPY, MOVE }

    /** Paths staged for copy/move (null = clipboard empty) */
    private var clipboard: List<String>? = null
    private var clipboardMode: ClipboardMode = ClipboardMode.COPY

    /**
     * Cached SAF tree URIs granted for removable volumes (SD card, USB).
     * Key = volume root path (e.g. "/storage/1234-ABCD/0"), value = tree Uri.
     */
    private val safUriCache = mutableMapOf<String, Uri>()

    /** Pending SAF callback invoked after the user grants access. */
    private var pendingSafCallback: (() -> Unit)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                when {
                    adapter.isSelectionMode -> exitSelectionMode()
                    isSearchExpanded -> collapseSearch()
                    !fileManager.isAtRoot -> navigateUp()
                }
                true
            }

            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_F10 -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.getInstance(this).initPrefs()
        setContentView(R.layout.view_home)

        rootView = findViewById(R.id.root)
        applyEdgeToEdgePadding(rootView)
        applyCurrentTheme(rootView)
        rootView.isFocusableInTouchMode = false
        rootView.requestFocus()

        listView = findViewById(R.id.file_list)
        searchBar = findViewById(R.id.search_bar)
        settingsBtn = findViewById(R.id.settings_btn)
        currentDir = findViewById(R.id.current_dir)
        currentFolderName = findViewById(R.id.current_folder_name)
        directoriesButton = findViewById(R.id.toggle_directories)

        directoriesFragment = findViewById(R.id.directories_fragment)
        filesFragment = findViewById(R.id.files_fragment)
        menuBar = findViewById(R.id.menu_bar)
        selectedCount = findViewById(R.id.selected_count)
        cancelSelectionBtn = findViewById(R.id.multi_select_cancel_button)
        renameBtn = findViewById(R.id.rename_btn)
        moveToFolderBtn = findViewById(R.id.move_to_folder_button)
        copyBtn = findViewById(R.id.copy_btn)
        pasteBtn = findViewById(R.id.paste_btn)
        appSettingsBtn = findViewById(R.id.app_settings)

        directoryList = findViewById(R.id.directory_list)
        addToFavoritesBtn = findViewById(R.id.add_to_favorites_button)

        directoriesButton.setOnClickListener {
            showDirs = !showDirs
            if (showDirs) {
                it.setBackgroundColor(resources.getColor(R.color.button_selected))
                directoriesFragment.visibility = View.VISIBLE
                filesFragment.visibility = View.GONE
                refreshFavorites()
            } else {
                it.setBackgroundColor(Color.TRANSPARENT)
                directoriesFragment.visibility = View.GONE
                filesFragment.visibility = View.VISIBLE
            }
        }

        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        cancelSelectionBtn.setOnClickListener { exitSelectionMode() }

        copyBtn.setOnClickListener { copySelected() }
        pasteBtn.setOnClickListener { pasteClipboard() }
        renameBtn.setOnClickListener { showRenameDialog() }
        moveToFolderBtn.setOnClickListener { moveSelected() }
        appSettingsBtn.setOnClickListener {
            Toast.makeText(this, "Settings for selection", Toast.LENGTH_SHORT).show()
        }

        initDirectoryList()
        initSearchbar()
        initFileList()
        requestStoragePermission()
    }

    override fun shouldRecreateOnSettingsChange(): Boolean = false

    override fun onResume() {
        super.onResume()
        applyCurrentTheme(rootView)

        if (fileSystemReady) {
            schedulePostResumeRefresh()
            collapseSearch()
        }
    }

    override fun onPause() {
        super.onPause()
        clearPendingResumeRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearPendingResumeRefresh()
        searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        super.dispatchTouchEvent(ev)

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) initFileSystem() else showPermissionDenied()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_MANAGE_ALL_FILES -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
                    initFileSystem()
                else showPermissionDenied()
            }
            REQ_SAF -> {
                val treeUri = data?.data
                if (resultCode == RESULT_OK && treeUri != null) {
                    // Persist the grant so it survives reboots
                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    // Cache against every removable volume whose UUID appears in the granted URI
                    val uriDecoded = Uri.decode(treeUri.toString())
                    val uuidRegex = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
                    val uuidDirPattern = Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")
                    val storageDir = File("/storage")
                    storageDir.listFiles()
                        ?.filter { it.isDirectory && uuidDirPattern.matches(it.name) }
                        ?.forEach { volumeDir ->
                            val volUuid = uuidRegex.find(volumeDir.name)?.value ?: volumeDir.name
                            if (uriDecoded.contains(volUuid, ignoreCase = true)) {
                                val volRoot = try {
                                    File(volumeDir, "0").takeIf { it.isDirectory }?.canonicalPath
                                        ?: volumeDir.canonicalPath
                                } catch (_: Exception) { volumeDir.absolutePath }
                                safUriCache[volRoot] = treeUri
                            }
                        }
                    // Retry the pending operation
                    pendingSafCallback?.invoke()
                } else {
                    Toast.makeText(this, getString(R.string.toast_saf_denied), Toast.LENGTH_SHORT).show()
                }
                pendingSafCallback = null
            }
        }
    }

    private fun initDirectoryList() {
        dirAdapter = DirectoryListAdapter(this) { path ->
            // For user bookmarks, remove from prefs. For USB Fixed entries (removable=true),
            // just refresh — the volume will no longer appear once dismissed.
            PrefsManager.getInstance(this).removeFavoriteDir(path)
            refreshFavorites()
        }
        directoryList.adapter = dirAdapter

        directoryList.setOnItemClickListener { _, _, position, _ ->
            val path = dirAdapter.pathAt(position) ?: return@setOnItemClickListener
            val dir = File(path)
            if (dir.isDirectory) {
                // enterAbsolute works for SD card, USB, and internal paths alike
                fileManager.enterAbsolute(dir)
                showDirs = false
                directoriesButton.setBackgroundColor(Color.TRANSPARENT)
                directoriesFragment.visibility = View.GONE
                filesFragment.visibility = View.VISIBLE
                refreshList()
            } else {
                Toast.makeText(this, "Folder no longer exists", Toast.LENGTH_SHORT).show()
                PrefsManager.getInstance(this).removeFavoriteDir(path)
                refreshFavorites()
            }
        }

        addToFavoritesBtn.setOnClickListener {
            if (!fileSystemReady) return@setOnClickListener
            val prefs = PrefsManager.getInstance(this)
            val path = fileManager.currentDir.absolutePath
            val existing = prefs.getFavoriteDirs()
            if (path in existing) {
                Toast.makeText(this, getString(R.string.toast_dir_exists), Toast.LENGTH_SHORT)
                    .show()
            } else {
                prefs.addFavoriteDir(path)
                Toast.makeText(this, getString(R.string.toast_dir_added), Toast.LENGTH_SHORT).show()
                refreshFavorites()
            }
        }
    }

    private fun refreshFavorites() {
        val list = mutableListOf<DirEntry>()

        // Fixed: root (only if accessible — rooted devices)
        val rootDir = File("/")
        if (rootDir.canRead()) {
            list += DirEntry.Fixed(
                label = "Root",
                path = "/",
                iconRes = R.drawable.icon_android,
            )
        }

        // Fixed: USB / SD card / external volumes via StorageManager
        val sm = getSystemService(STORAGE_SERVICE) as android.os.storage.StorageManager
        val primaryCanonical = try {
            Environment.getExternalStorageDirectory().canonicalPath
        } catch (_: Exception) { "" }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // API 24+: use StorageVolume list
            sm.storageVolumes
                .filter { vol -> !vol.isPrimary && vol.state == android.os.Environment.MEDIA_MOUNTED }
                .forEach { vol ->
                    val path = try {
                        val m = vol.javaClass.getMethod("getPath")
                        m.invoke(vol) as? String
                    } catch (_: Exception) { null } ?: return@forEach
                    val dir = File(path)
                    if (!dir.canRead()) return@forEach
                    val isUsb = vol.isRemovable &&
                        !path.contains("sd", ignoreCase = true) &&
                        !path.matches(Regex(".*/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}(/.*)?"))
                    val label = vol.getDescription(this)
                        ?: if (isUsb) "USB" else "SD Card"
                    list += DirEntry.Fixed(
                        label = label,
                        path = dir.canonicalPath,
                        iconRes = if (isUsb) R.drawable.icon_cassette_tape_solid
                                  else R.drawable.icon_disk,
                        removable = true,
                    )
                }
        } else {
            // API 21-23: fallback — scan /storage for anything that isn't primary or known system dirs
            val storageDir = File("/storage")
            val skipNames = setOf("emulated", "self", "enc_emulated")
            if (storageDir.isDirectory) {
                storageDir.listFiles()
                    ?.filter { it.isDirectory && it.name !in skipNames }
                    ?.mapNotNull { volumeDir ->
                        val candidate = File(volumeDir, "0").takeIf { it.isDirectory } ?: volumeDir
                        try {
                            if (candidate.canRead() && candidate.canonicalPath != primaryCanonical)
                                candidate else null
                        } catch (_: Exception) { null }
                    }
                    ?.forEach { vol ->
                        val uuidPattern = Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")
                        val isUsb = !uuidPattern.matches(vol.parentFile?.name ?: "")
                        list += DirEntry.Fixed(
                            label = if (isUsb) "USB – ${vol.parentFile?.name ?: vol.name}"
                                    else "SD Card – ${vol.parentFile?.name ?: vol.name}",
                            path = vol.canonicalPath,
                            iconRes = if (isUsb) R.drawable.icon_cassette_tape_solid
                                      else R.drawable.icon_disk,
                            removable = true,
                        )
                    }
            }
        }

        // Fixed: storage root
        val storageRoot = Environment.getExternalStorageDirectory()
        if (storageRoot.isDirectory) {
            list += DirEntry.Fixed(
                label = "Storage",
                path = storageRoot.absolutePath,
                iconRes = R.drawable.icon_android,
            )
        }

        // Fixed: standard folders (only if they exist)
        val standardDirs = listOf(
            "DCIM" to Environment.DIRECTORY_DCIM,
            "Pictures" to Environment.DIRECTORY_PICTURES,
            "Music" to Environment.DIRECTORY_MUSIC,
            "Movies" to Environment.DIRECTORY_MOVIES,
//            "Documents" to Environment.DIRECTORY_DOCUMENTS,
            "Download" to Environment.DIRECTORY_DOWNLOADS,
        )
        for ((name, envDir) in standardDirs) {
            val dir = Environment.getExternalStoragePublicDirectory(envDir)
            if (dir.isDirectory) {
                list += DirEntry.Fixed(
                    label = name,
                    path = dir.absolutePath,
                    iconRes = R.drawable.icon_folder_solid,
                )
            }
        }

        // Divider + user bookmarks
        val userDirs = PrefsManager.getInstance(this).getFavoriteDirs()
        if (userDirs.isNotEmpty()) {
            list += DirEntry.Divider
            userDirs.forEach { list += DirEntry.UserAdded(it) }
        }

        dirAdapter.update(list)
    }

    private fun navigateToAbsolutePath(target: File) {
        val root = Environment.getExternalStorageDirectory().canonicalPath
        val targetCanon = target.canonicalPath

        if (!targetCanon.startsWith(root)) return

        // Build segment list between root and target
        val relative = targetCanon.removePrefix(root).trimStart('/')
        if (relative.isEmpty()) return   // target IS root

        for (segment in relative.split("/")) {
            val next = File(fileManager.currentDir, segment)
            if (next.isDirectory) fileManager.enter(next)
        }
    }

    private fun initFileList() {
        adapter = FileListAdapter(this)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            when {
                adapter.isUpRow(position) -> navigateUp()

                adapter.isSelectionMode -> {
                    adapter.toggleSelection(adapter.getEntry(position))
                    updateSelectionBar()
                }

                else -> {
                    val entry = adapter.getEntry(position)
                    if (entry.isDirectory) {
                        fileManager.enter(entry.file)
                        collapseSearch()
                        refreshList()
                    } else {
                        openFile(entry.file)
                    }
                }
            }
        }

        listView.setOnItemLongClickListener { _, view, position, _ ->
            if (adapter.isUpRow(position)) return@setOnItemLongClickListener false

            val entry = adapter.getEntry(position)
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            if (adapter.isSelectionMode) {
                adapter.toggleSelection(entry)
                updateSelectionBar()
            } else {
                adapter.enterSelectionMode(entry)
                showSelectionBar()
            }
            true
        }
    }

    private fun initFileSystem() {
        fileManager.init()
        fileSystemReady = true
        refreshList()
    }

    private fun refreshList() {
        if (!fileSystemReady) return

        val entries = fileManager.listCurrent(currentSearchQuery)
        val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
        val hasParent = !fileManager.isAtRoot ||
            fileManager.currentDir.absolutePath != primaryRoot
        adapter.update(entries, hasParent = hasParent)

        val dirName = fileManager.currentDir.let { dir ->
            when {
                dir.absolutePath == Environment.getExternalStorageDirectory().absolutePath -> "Storage"
                dir.name.isEmpty() -> "/"
                else -> dir.name
            }
        }
        currentFolderName.text = dirName

        if (adapter.isSelectionMode) updateSelectionBar()

        applyCurrentTheme(rootView)
    }

    private fun navigateUp() {
        if (fileManager.isAtRoot) {
            // We're at an external volume root (SD card / USB) — go back to primary storage
            fileManager.init()
        } else {
            fileManager.goUp()
        }
        exitSelectionMode()
        refreshList()
    }

    private fun showSelectionBar() {
        menuBar.visibility = View.VISIBLE
        updateSelectionBar()
    }

    private fun updateSelectionBar() {
        val count = adapter.selectedCount
        selectedCount.text = resources.getQuantityString(R.plurals.selected_count, count, count)
        if (count == 0) exitSelectionMode()
        // Show paste button only when clipboard is non-empty and nothing is selected
        // (paste is context-aware, not dependent on selection count)
        pasteBtn.visibility = if (clipboard != null) View.VISIBLE else View.GONE
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        menuBar.visibility = if (clipboard != null) View.VISIBLE else View.GONE
        if (clipboard != null) {
            // Keep bar visible for paste, reset count label
            selectedCount.text = ""
            pasteBtn.visibility = View.VISIBLE
        }
    }

    private fun copySelected() {
        val paths = adapter.selectedEntries.map { it.file.absolutePath }
        if (paths.isEmpty()) return
        clipboard = paths
        clipboardMode = ClipboardMode.COPY
        Toast.makeText(this, getString(R.string.toast_copy_queued), Toast.LENGTH_SHORT).show()
        exitSelectionMode()
    }

    private fun pasteClipboard() {
        val sources = clipboard ?: return
        if (!fileSystemReady) return
        val destDir = fileManager.currentDir
        val isMove = clipboardMode == ClipboardMode.MOVE

        // ── 1. Space check ──────────────────────────────────────────────────
        val requiredBytes = sources.sumOf { FileManager.totalSize(File(it)) }
        if (!FileManager.hasEnoughSpace(destDir, requiredBytes)) {
            Toast.makeText(this, getString(R.string.toast_not_enough_space), Toast.LENGTH_LONG).show()
            return
        }

        // ── 2. SAF permission check for removable storage ──────────────────
        val destVolume = removableVolumeRootFor(destDir.absolutePath)
        if (destVolume != null && !hasSafAccess(destVolume)) {
            pendingSafCallback = { pasteClipboard() }
            requestSafAccess(destVolume)
            return
        }

        // ── 3. Execute copy / move ─────────────────────────────────────────
        var failed = 0
        val successfullySources = mutableListOf<File>()

        for (sourcePath in sources) {
            val src = File(sourcePath)
            // Resolve a non-conflicting destination name
            val dest = FileManager.resolveNonConflictingName(destDir, src.name)
            try {
                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = false)
                    val srcSize = src.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                    val destSize = dest.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                    if (destSize == srcSize) successfullySources.add(src)
                    else { dest.deleteRecursively(); failed++ }
                } else {
                    src.copyTo(dest, overwrite = false)
                    if (dest.length() == src.length()) successfullySources.add(src)
                    else { dest.delete(); failed++ }
                }
            } catch (_: Exception) {
                failed++
            }
        }

        // For move: delete sources that were verified successfully
        if (isMove) {
            for (src in successfullySources) {
                if (src.isDirectory) src.deleteRecursively() else src.delete()
            }
        }

        clipboard = null
        pasteBtn.visibility = View.GONE
        if (menuBar.visibility == View.VISIBLE && !adapter.isSelectionMode) {
            menuBar.visibility = View.GONE
        }

        val msg = if (failed == 0) getString(R.string.toast_paste_success)
        else getString(R.string.toast_paste_failed)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun showRenameDialog() {
        val entries = adapter.selectedEntries
        if (entries.size != 1) {
            Toast.makeText(this, "Select exactly one item to rename", Toast.LENGTH_SHORT).show()
            return
        }
        val target = entries[0].file

        // SAF check for rename on removable storage
        val targetVolume = removableVolumeRootFor(target.absolutePath)
        if (targetVolume != null && !hasSafAccess(targetVolume)) {
            pendingSafCallback = { showRenameDialog() }
            requestSafAccess(targetVolume)
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_rename_file, null)
        val editText = dialogView.findViewById<EditText>(R.id.edit_text)
        editText.setText(target.name)
        editText.selectAll()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<FrameLayout>(R.id.yes_button).setOnClickListener {
            val newName = editText.text.toString().trim()
            if (newName.isNotEmpty() && newName != target.name) {
                // If a file/folder with that name already exists, use a unique name
                val resolvedDest = FileManager.resolveNonConflictingName(
                    target.parentFile ?: fileManager.currentDir,
                    newName
                )
                val ok = target.renameTo(resolvedDest)
                Toast.makeText(
                    this,
                    if (ok) getString(R.string.toast_rename_success)
                    else getString(R.string.toast_rename_failed),
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) {
                    exitSelectionMode()
                    refreshList()
                }
            }
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.no_button).setOnClickListener { dialog.dismiss() }

        dialog.show()

        // Show keyboard
        editText.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun moveSelected() {
        val paths = adapter.selectedEntries.map { it.file.absolutePath }
        if (paths.isEmpty()) return
        clipboard = paths
        clipboardMode = ClipboardMode.MOVE
        Toast.makeText(this, "Navigate to destination and paste", Toast.LENGTH_SHORT).show()
        exitSelectionMode()
    }

    // ── SAF (Storage Access Framework) helpers ─────────────────────────────

    /**
     * Returns true if we already have a persistent write grant for [volumeRoot].
     */
    private fun hasSafAccess(volumeRoot: String): Boolean {
        if (safUriCache.containsKey(volumeRoot)) return true
        // Check persisted grants across restarts
        val persisted = contentResolver.persistedUriPermissions
        return persisted.any { perm ->
            perm.isWritePermission && safUriMatchesVolume(perm.uri, volumeRoot)
        }.also { found ->
            if (found) {
                val uri = persisted.first { perm ->
                    perm.isWritePermission && safUriMatchesVolume(perm.uri, volumeRoot)
                }.uri
                safUriCache[volumeRoot] = uri
            }
        }
    }

    /**
     * Launches the SAF directory picker so the user can grant write access to [volumeRoot].
     *
     * Note: FLAG_GRANT_* flags must NOT be set on the outgoing intent on API < 26 —
     * they are declared by the picker and returned on the result uri. We only need
     * FLAG_GRANT_PERSISTABLE_URI_PERMISSION when calling takePersistableUriPermission().
     */
    private fun requestSafAccess(volumeRoot: String) {
        Toast.makeText(this, getString(R.string.toast_saf_required), Toast.LENGTH_LONG).show()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQ_SAF)
    }

    /**
     * Checks whether a SAF tree [uri] corresponds to [volumeRoot].
     * SAF URIs look like:
     *   content://com.android.externalstorage.documents/tree/1234-ABCD%3A
     * We extract the volume UUID from both the URI and the file path and compare.
     */
    private fun safUriMatchesVolume(uri: Uri, volumeRoot: String): Boolean {
        val uriStr = Uri.decode(uri.toString())
        // Extract UUID-like segment from the volume root path
        // e.g. /storage/1234-ABCD → "1234-ABCD", /storage/1234-ABCD/0 → "1234-ABCD"
        val uuidRegex = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
        val volumeUuid = uuidRegex.find(volumeRoot)?.value
        if (volumeUuid != null) {
            return uriStr.contains(volumeUuid, ignoreCase = true)
        }
        // Fallback: match on the last meaningful path segment of volumeRoot
        val seg = volumeRoot.trimEnd('/').substringAfterLast('/')
        return seg.isNotEmpty() && uriStr.contains(seg, ignoreCase = true)
    }

    private fun openFile(file: File) {
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
            this, "${packageName}.fileprovider", file
        )
        val mime = contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) initFileSystem()
            else startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                ), REQ_MANAGE_ALL_FILES
            )
        } else {
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
            val needsRequest = perms.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needsRequest) ActivityCompat.requestPermissions(this, perms, REQ_STORAGE)
            else initFileSystem()
        }
    }

    private fun showPermissionDenied() {
        Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show()
    }

    private fun initSearchbar() {
        searchField = findViewById(R.id.search_field)
        searchButton = findViewById(R.id.search_btn_toggle)
        clearBtn = findViewById(R.id.clear_field)

        searchBar.visibility = View.GONE
        clearBtn.visibility = View.GONE

        searchButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (isSearchExpanded) collapseSearch() else expandSearch()
        }

        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isProgrammaticSearchUpdate) return
                val query = s.toString()
                searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
                searchDebounceRunnable = Runnable {
                    currentSearchQuery = query
                    refreshList()
                }
                searchDebounceHandler.postDelayed(searchDebounceRunnable!!, 250)
                clearBtn.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        clearBtn.setOnClickListener {
            currentSearchQuery = ""
            searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
            isProgrammaticSearchUpdate = true
            searchField.text.clear()
            isProgrammaticSearchUpdate = false
            clearBtn.visibility = View.GONE
            refreshList()
        }
    }

    private fun expandSearch() {
        searchBar.visibility = View.VISIBLE
        currentDir.visibility = View.GONE
        searchField.requestFocus()

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(searchField, "scaleX", 0.8f, 1f),
                ObjectAnimator.ofFloat(searchField, "scaleY", 0.8f, 1f),
                ObjectAnimator.ofFloat(searchField, "alpha", 0f, 1f),
            )
            duration = 300
            interpolator = OvershootInterpolator(1.5f)
            start()
        }
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showSoftInput(searchField, 0)
        isSearchExpanded = true
    }

    private fun collapseSearch(clearQuery: Boolean = true, hideKeyboard: Boolean = true) {
        searchBar.visibility = View.GONE
        clearBtn.visibility = View.GONE
        currentDir.visibility = View.VISIBLE
        searchField.clearFocus()

        if (clearQuery) {
            currentSearchQuery = ""
            searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
            isProgrammaticSearchUpdate = true
            searchField.text.clear()
            isProgrammaticSearchUpdate = false
            refreshList()
        }
        if (hideKeyboard) {
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(searchField.windowToken, 0)
        }
        isSearchExpanded = false
    }

    private fun schedulePostResumeRefresh() {
        clearPendingResumeRefresh()
        resumeRefreshRunnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            refreshList()
        }
        rootView.post(resumeRefreshRunnable)
    }

    private fun clearPendingResumeRefresh() {
        resumeRefreshRunnable?.let { rootView.removeCallbacks(it) }
        resumeRefreshRunnable = null
    }

    companion object {
        private const val REQ_STORAGE = 1001
        private const val REQ_MANAGE_ALL_FILES = 1002
        private const val REQ_SAF = 1003

        /**
         * Returns the canonical volume root for [path] if it lives on a removable
         * volume (SD card, USB), or null if it is on primary/internal storage.
         *
         * Android exposes removable volumes under several roots depending on version:
         *   /storage/<UUID>          – API 21+ standard
         *   /storage/<UUID>/0        – some OEMs
         *   /mnt/media_rw/<UUID>     – Android 6 raw mount point
         *   /mnt/sdcard, /mnt/extSdCard, /sdcard1 – older OEM paths
         *
         * We collect all known primary-storage canonical paths and treat anything
         * else under /storage or /mnt as removable.
         */
        fun removableVolumeRootFor(path: String): String? {
            val canonicalPath = try { File(path).canonicalPath } catch (_: Exception) { path }

            // Collect all primary-storage aliases
            val primaryAliases = mutableSetOf<String>()
            try {
                val ext = Environment.getExternalStorageDirectory()
                primaryAliases += ext.absolutePath
                primaryAliases += ext.canonicalPath
            } catch (_: Exception) {}
            // /storage/emulated/0 and /storage/self/primary are always primary
            primaryAliases += "/storage/emulated/0"
            primaryAliases += "/storage/self/primary"

            if (primaryAliases.any { canonicalPath.startsWith(it) }) return null

            // Check /storage/<UUID> and /storage/<UUID>/0
            val storageMatch = Regex("^/storage/([^/]+)").find(canonicalPath)
            if (storageMatch != null) {
                val vol = storageMatch.groupValues[1]
                if (vol == "emulated" || vol == "self") return null
                val deep = File("/storage/$vol/0")
                return try {
                    if (deep.isDirectory) deep.canonicalPath else File("/storage/$vol").canonicalPath
                } catch (_: Exception) { "/storage/$vol" }
            }

            // Check /mnt/media_rw/<UUID> (Android 6 raw mount) or /mnt/sdcard etc.
            val mntMatch = Regex("^/mnt/(?:media_rw|sdcard|extSdCard|external_sd)/([^/]*)").find(canonicalPath)
            if (mntMatch != null) {
                // Try to find the /storage equivalent
                val vol = mntMatch.groupValues[1].ifEmpty { null }
                if (vol != null) {
                    val storageEquiv = File("/storage/$vol")
                    return try {
                        if (storageEquiv.isDirectory) storageEquiv.canonicalPath
                        else {
                            val end = canonicalPath.indexOf('/', mntMatch.value.length).takeIf { it > 0 } ?: canonicalPath.length
                            canonicalPath.substring(0, end)
                        }
                    } catch (_: Exception) { storageEquiv.absolutePath }
                }
                return canonicalPath.split("/").take(4).joinToString("/")
            }

            return null
        }
    }
}
