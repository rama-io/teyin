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
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.rama.teyin.CsActivity
import com.rama.teyin.R
import com.rama.bohio.R as BohioR
import com.rama.teyin.adapters.DirEntry
import com.rama.teyin.adapters.DirectoryListAdapter
import com.rama.teyin.adapters.FileListAdapter
import com.rama.teyin.managers.DocumentNode
import com.rama.teyin.managers.FileManager
import com.rama.teyin.managers.FileNode
import com.rama.teyin.managers.FsNode
import com.rama.teyin.managers.TaskRunner
import com.rama.teyin.managers.totalSize
import com.rama.teyin.managers.PrefsManager as BohioPrefsManager
import com.rama.bohio.managers.ThemeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    //    private lateinit var appSettingsBtn: FrameLayout
    private lateinit var removeBtn: FrameLayout
    private lateinit var infoBtn: FrameLayout

    // Directory list (favorites)
    private lateinit var directoryList: ListView
    private lateinit var addToFavoritesBtn: View
    private lateinit var dirAdapter: DirectoryListAdapter

    private val fileManager = FileManager()
    private lateinit var adapter: FileListAdapter
    private var isSearchExpanded = false
    private var activeRefreshId: Long = 0L
    private var isProgrammaticSearchUpdate = false
    private val searchDebounceHandler = Handler(Looper.getMainLooper())
    private var searchDebounceRunnable: Runnable? = null
    private var currentSearchQuery: String = ""
    private var resumeRefreshRunnable: Runnable? = null
    private var fileSystemReady = false
    private var showDirs = false
    private var lastKnownUiScale: Float = -1f

    private enum class ClipboardMode { COPY, MOVE }

    /** Paths staged for copy/move (null = clipboard empty) */
    private var clipboard: List<FsNode>? = null
    private var clipboardMode: ClipboardMode = ClipboardMode.COPY

    /**
     * Cached SAF tree URIs granted for removable volumes (SD card, USB).
     * Key = volume root path (e.g. "/storage/1234-ABCD/0"), value = tree Uri.
     */
    private val safUriCache = mutableMapOf<String, Uri>()

    /** Pending SAF callback invoked after the user grants access. */
    private var pendingSafCallback: (() -> Unit)? = null
    private var pendingSafVolumeRoot: String? = null
    private val activeTaskTokens = mutableSetOf<TaskRunner.Cancellable>()
    private var activeNavToken: Any? = null

    private val safLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        handleSafResult(uri)
    }

    private class TaskHandle : TaskRunner.Cancellable {
        @Volatile var inner: TaskRunner.Cancellable? = null
        @Volatile override var isCancelled: Boolean = false
            private set

        override fun cancel() {
            isCancelled = true
            inner?.cancel()
        }
    }

    private fun <T> runBackgroundTask(
        task: () -> T,
        onError: ((Throwable) -> Unit)? = null,
        onResult: (T) -> Unit
    ): TaskRunner.Cancellable {
        val handle = TaskHandle()
        synchronized(activeTaskTokens) { activeTaskTokens.add(handle) }
        val token = TaskRunner.execute(
            task = task,
            onError = { err ->
                synchronized(activeTaskTokens) { activeTaskTokens.remove(handle) }
                if (!isFinishing && !isDestroyed) {
                    onError?.invoke(err)
                }
            },
            onResult = { res ->
                synchronized(activeTaskTokens) { activeTaskTokens.remove(handle) }
                if (!isFinishing && !isDestroyed) {
                    onResult(res)
                }
            }
        )
        handle.inner = token
        if (handle.isCancelled) token.cancel()
        return handle
    }
    private fun handleBackPress() {
        when {
            adapter.isSelectionMode -> exitSelectionMode()
            isSearchExpanded -> collapseSearch()
            !fileManager.isAtRoot -> navigateUp()
            else -> finish()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_F10 -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastKnownUiScale = prefs.getUiScale()
        savedInstanceState?.getString("pending_saf_volume_root")?.let { pendingSafVolumeRoot = it }

        BohioPrefsManager.getInstance(this).initPrefs()
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
//        appSettingsBtn = findViewById(R.id.app_settings)
        removeBtn = findViewById(R.id.remove_btn)
        infoBtn = findViewById(R.id.info_btn)

        directoryList = findViewById(R.id.directory_list)
        addToFavoritesBtn = findViewById(R.id.add_to_favorites_button)

        directoriesButton.setOnClickListener {
            showDirs = !showDirs
            if (showDirs) {
                it.setBackgroundColor(resources.getColor(BohioR.color.bg_2))
                directoriesFragment.visibility = View.VISIBLE
                filesFragment.visibility = View.GONE
                refreshFavorites()
            } else {
                it.setBackgroundColor(Color.TRANSPARENT)
                directoriesFragment.visibility = View.GONE
                filesFragment.visibility = View.VISIBLE
            }
        }

        settingsBtn.setOnClickListener { view ->
            val popupView = layoutInflater.inflate(R.layout.popup_topbar, null)
            ThemeManager.applyTheme(this, popupView)

            val hiddenCheckbox = popupView.findViewById<CheckBox>(R.id.popup_hidden_checkbox)
            hiddenCheckbox.isChecked =
                prefs.getBoolean(BohioPrefsManager.FileKeys.SHOW_HIDDEN_FILES, false)

            val popup = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true
            )

            popupView.findViewById<View>(R.id.popup_add_folder).setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                popup.dismiss()
                showCreateFolderDialog()
            }

            popupView.findViewById<View>(R.id.popup_toggle_hidden).setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                hiddenCheckbox.isChecked = !hiddenCheckbox.isChecked
                prefs.setBoolean(
                    BohioPrefsManager.FileKeys.SHOW_HIDDEN_FILES,
                    hiddenCheckbox.isChecked
                )
                popup.dismiss()
                refreshList()
            }

            popupView.findViewById<View>(R.id.popup_settings).setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                popup.dismiss()
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            popup.showAsDropDown(view, 0, 0, Gravity.TOP)
        }

        cancelSelectionBtn.setOnClickListener { exitSelectionMode() }

        copyBtn.setOnClickListener { copySelected() }
        pasteBtn.setOnClickListener { pasteClipboard() }
        renameBtn.setOnClickListener { showRenameDialog() }
        moveToFolderBtn.setOnClickListener { moveSelected() }
//        appSettingsBtn.setOnClickListener {
//            Toast.makeText(this, "Settings for selection", Toast.LENGTH_SHORT).show()
//        }
        removeBtn.setOnClickListener { showDeleteConfirmationDialog() }
        infoBtn.setOnClickListener { showFileInfoDialog() }

        initDirectoryList()
        initSearchbar()
        initFileList()
        requestStoragePermission()
        handleIncomingIntent(intent)

        onBackPressedDispatcher.addCallback(this) { handleBackPress() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val path = data.path ?: return
        val target = File(path)
        if (target.isDirectory) {
            fileManager.enterAbsolute(target)
            if (fileSystemReady) refreshList()
        }
    }

    override fun shouldRecreateOnSettingsChange(): Boolean = false

    override fun onResume() {
        super.onResume()

        val currentUiScale = prefs.getUiScale()
        if (currentUiScale != lastKnownUiScale) {
            lastKnownUiScale = currentUiScale
            recreate()
            return
        }

        applyCurrentTheme(rootView)

        if (fileSystemReady) {
            schedulePostResumeRefresh()
            collapseSearch()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            initFileSystem()
        }
    }

    override fun onPause() {
        super.onPause()
        clearPendingResumeRefresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pending_saf_volume_root", pendingSafVolumeRoot)
    }

    override fun onDestroy() {
        super.onDestroy()
        clearPendingResumeRefresh()
        searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
        synchronized(activeTaskTokens) {
            activeTaskTokens.forEach { it.cancel() }
            activeTaskTokens.clear()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        super.dispatchTouchEvent(ev)

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
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
                handleSafResult(data?.data)
            }
        }
    }

    private fun handleSafResult(treeUri: Uri?) {
        if (treeUri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (_: SecurityException) {
            }
            val targetVol = pendingSafVolumeRoot
            if (targetVol != null) {
                safUriCache[targetVol] = treeUri
            }
            val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
            val uriDecoded = Uri.decode(treeUri.toString())
            val isPrimaryMatch = uriDecoded.contains("primary", ignoreCase = true) || safUriMatchesVolume(treeUri, primaryRoot)

            var resolvedInitVolume: String? = null
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                if (isPrimaryMatch) {
                    safUriCache[primaryRoot] = treeUri
                    resolvedInitVolume = primaryRoot
                }
            }

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
                        } catch (_: Exception) {
                            volumeDir.absolutePath
                        }
                        safUriCache[volRoot] = treeUri
                        if (resolvedInitVolume == null && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && !fileSystemReady) {
                            resolvedInitVolume = volRoot
                        }
                    }
                }

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && !fileSystemReady) {
                if (resolvedInitVolume != null) {
                    val rootDoc = safUriCache[resolvedInitVolume]?.let {
                        DocumentNode.fromTreeUri(this, it, resolvedInitVolume)
                    }
                    if (rootDoc != null) {
                        fileManager.init(rootDoc)
                        fileSystemReady = true
                        refreshList()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.toast_saf_primary_required), Toast.LENGTH_LONG).show()
                    showSafGuidanceDialog(primaryRoot)
                    return
                }
            }

            val callback = pendingSafCallback
            pendingSafCallback = null
            pendingSafVolumeRoot = null
            callback?.invoke()
        } else {
            pendingSafCallback = null
            pendingSafVolumeRoot = null
            if (!fileSystemReady) {
                showPermissionDenied()
            } else {
                Toast.makeText(this, getString(R.string.toast_saf_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initDirectoryList() {
        dirAdapter = DirectoryListAdapter(this) { path ->
            // For user bookmarks, remove from prefs. For USB Fixed entries (removable=true),
            // just refresh — the volume will no longer appear once dismissed.
            BohioPrefsManager.getInstance(this).removeFavoriteDir(path)
            refreshFavorites()
        }
        directoryList.adapter = dirAdapter

        directoryList.setOnItemClickListener { _, _, position, _ ->
            val path = dirAdapter.pathAt(position) ?: return@setOnItemClickListener
            val dir = File(path)
            val volumeRoot = removableVolumeRootFor(dir.absolutePath)
            if (volumeRoot != null) {
                val enterAction = {
                    val treeUri = safUriCache[volumeRoot]
                    val rootDoc = treeUri?.let { DocumentNode.fromTreeUri(this, it, volumeRoot) }
                    if (rootDoc != null) {
                        val relative = dir.absolutePath.removePrefix(volumeRoot).trim('/')
                        val segments = relative.split('/').filter { it.isNotEmpty() }
                        if (segments.isEmpty()) {
                            fileManager.enterNode(rootDoc, asRoot = true)
                            refreshList()
                        } else {
                            fileManager.enterNode(rootDoc, asRoot = true)
                            val navToken = Any()
                            activeNavToken = navToken
                            runBackgroundTask(
                                task = {
                                    val matchedNodes = mutableListOf<FsNode>()
                                    var curr: FsNode = rootDoc
                                    for (segment in segments) {
                                        val next = curr.listChildren(showHidden = true)
                                            .firstOrNull { it.isDirectory && it.name.equals(segment, ignoreCase = true) }
                                            ?: break
                                        matchedNodes.add(next)
                                        curr = next
                                    }
                                    matchedNodes
                                },
                                onResult = { nodes ->
                                    if (activeNavToken != navToken) return@runBackgroundTask
                                    for (node in nodes) {
                                        fileManager.enter(node)
                                    }
                                    refreshList()
                                }
                            )
                        }
                    } else if (dir.isDirectory) {
                        fileManager.enterAbsolute(dir)
                        refreshList()
                    }
                }

                if (hasSafAccess(volumeRoot) || dir.canRead()) {
                    enterAction()
                } else {
                    pendingSafCallback = { enterAction() }
                    requestSafAccess(volumeRoot)
                }
                showDirs = false
                directoriesButton.setBackgroundColor(Color.TRANSPARENT)
                directoriesFragment.visibility = View.GONE
                filesFragment.visibility = View.VISIBLE
                return@setOnItemClickListener
            }

            if (dir.isDirectory) {
                fileManager.enterAbsolute(dir)
                showDirs = false
                directoriesButton.setBackgroundColor(Color.TRANSPARENT)
                directoriesFragment.visibility = View.GONE
                filesFragment.visibility = View.VISIBLE
                refreshList()
            } else {
                val entry = dirAdapter.getItem(position)
                if (entry is DirEntry.Fixed) {
                    // Volume was unplugged — just refresh so it disappears from the list
                    Toast.makeText(
                        this,
                        getString(R.string.toast_storage_not_available),
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshFavorites()
                } else {
                    // Stale user bookmark — remove it
                    Toast.makeText(
                        this,
                        getString(R.string.toast_folder_no_longer_exists),
                        Toast.LENGTH_SHORT
                    ).show()
                    BohioPrefsManager.getInstance(this).removeFavoriteDir(path)
                    refreshFavorites()
                }
            }
        }

        addToFavoritesBtn.setOnClickListener {
            if (!fileSystemReady) return@setOnClickListener
            val prefs = BohioPrefsManager.getInstance(this)
            val path = fileManager.currentNode.path
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
                label = getString(R.string.dir_label_root),
                path = "/",
                iconRes = BohioR.drawable.px_android,
            )
        }

        // Fixed: USB / SD card / external volumes via StorageManager
        val sm = getSystemService(STORAGE_SERVICE) as android.os.storage.StorageManager
        val primaryCanonical = try {
            Environment.getExternalStorageDirectory().canonicalPath
        } catch (_: Exception) {
            ""
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // API 24+: use StorageVolume list
            sm.storageVolumes
                .filter { vol -> !vol.isPrimary && vol.state == android.os.Environment.MEDIA_MOUNTED }
                .forEach { vol ->
                    val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory?.absolutePath
                    } else {
                        try {
                            val m = vol.javaClass.getMethod("getPath")
                            m.invoke(vol) as? String
                        } catch (_: Exception) {
                            null
                        }
                    } ?: return@forEach
                    val dir = File(path)
                    val isAccessible = dir.canRead() || hasSafAccess(dir.absolutePath) || dir.exists()
                    if (!isAccessible) return@forEach
                    val isUsb = vol.isRemovable &&
                            !path.contains("sd", ignoreCase = true) &&
                            !path.matches(Regex(".*/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}(/.*)?"))
                    val label = vol.getDescription(this)
                        ?: if (isUsb) getString(R.string.dir_label_usb) else getString(R.string.dir_label_sd_card)
                    list += DirEntry.Fixed(
                        label = label,
                        path = dir.canonicalPath,
                        iconRes = if (isUsb) BohioR.drawable.px_cassette_tape
                        else BohioR.drawable.px_disk,
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
                        } catch (_: Exception) {
                            null
                        }
                    }
                    ?.forEach { vol ->
                        val uuidPattern = Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")
                        val isUsb = !uuidPattern.matches(vol.parentFile?.name ?: "")
                        val volumeId = vol.parentFile?.name ?: vol.name
                        list += DirEntry.Fixed(
                            label = if (isUsb) getString(R.string.dir_label_usb_with_name, volumeId)
                            else getString(R.string.dir_label_sd_card_with_name, volumeId),
                            path = vol.canonicalPath,
                            iconRes = if (isUsb) BohioR.drawable.px_cassette_tape
                            else BohioR.drawable.px_disk,
                            removable = true,
                        )
                    }
            }
        }

        // Fixed: storage root
        val storageRoot = Environment.getExternalStorageDirectory()
        if (storageRoot.isDirectory) {
            list += DirEntry.Fixed(
                label = getString(R.string.dir_label_storage),
                path = storageRoot.absolutePath,
                iconRes = BohioR.drawable.px_android,
            )
        }

        // Fixed: standard folders (only if they exist)
        val standardDirs = listOf(
            getString(R.string.dir_label_dcim) to Environment.DIRECTORY_DCIM,
            getString(R.string.dir_label_pictures) to Environment.DIRECTORY_PICTURES,
            getString(R.string.dir_label_music) to Environment.DIRECTORY_MUSIC,
            getString(R.string.dir_label_movies) to Environment.DIRECTORY_MOVIES,
//            getString(R.string.dir_label_documents) to Environment.DIRECTORY_DOCUMENTS,
            getString(R.string.dir_label_download) to Environment.DIRECTORY_DOWNLOADS,
        )
        for ((name, envDir) in standardDirs) {
            val dir = Environment.getExternalStoragePublicDirectory(envDir)
            if (dir.isDirectory) {
                list += DirEntry.Fixed(
                    label = name,
                    path = dir.absolutePath,
                    iconRes = BohioR.drawable.px_folder,
                )
            }
        }

        // Divider + user bookmarks
        val userDirs = BohioPrefsManager.getInstance(this).getFavoriteDirs()
        if (userDirs.isNotEmpty()) {
            list += DirEntry.Divider
            userDirs.forEach { list += DirEntry.UserAdded(it) }
        }

        dirAdapter.update(list)
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
                        fileManager.enter(entry)
                        collapseSearch()
                        refreshList()
                    } else {
                        openFile(entry)
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
        if (fileManager.currentNode is DocumentNode) {
            fileSystemReady = true
            refreshList()
            return
        }
        val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
        val rootDoc = safUriCache[primaryRoot]?.let { DocumentNode.fromTreeUri(this, it, primaryRoot) }
        if (rootDoc != null) {
            fileManager.init(rootDoc)
        } else {
            fileManager.init()
        }
        fileSystemReady = true
        refreshList()
    }

    private fun refreshList() {
        if (!fileSystemReady) return

        // If current directory no longer exists (e.g. SD card unplugged mid-browse),
        // fall back to primary storage gracefully
        if (!fileManager.currentNode.isDirectory) {
            Toast.makeText(
                this,
                getString(R.string.toast_storage_no_longer_available),
                Toast.LENGTH_SHORT
            ).show()
            val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
            val rootDoc = safUriCache[primaryRoot]?.let { DocumentNode.fromTreeUri(this, it, primaryRoot) }
            fileManager.init(rootDoc)
        }

        val showHidden = prefs.getBoolean(BohioPrefsManager.FileKeys.SHOW_HIDDEN_FILES, false)
        val query = currentSearchQuery
        val queryId = ++activeRefreshId
        val targetNode = fileManager.currentNode

        runBackgroundTask(
            task = {
                targetNode.listChildren(query, showHidden)
            },
            onResult = { entries ->
                if (queryId != activeRefreshId || targetNode.path != fileManager.currentNode.path) {
                    return@runBackgroundTask
                }
                val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
                val currentNode = fileManager.currentNode
                val hasParent = !fileManager.isAtRoot || currentNode.path != primaryRoot
                adapter.update(entries, hasParent = hasParent)

                val dirName = when {
                    currentNode.path == primaryRoot -> getString(R.string.dir_label_storage)
                    currentNode.name.isEmpty() -> getString(R.string.dir_label_root)
                    else -> currentNode.name
                }
                currentFolderName.text = dirName

                if (adapter.isSelectionMode) updateSelectionBar()

                applyCurrentTheme(rootView)
            }
        )
    }

    private fun navigateUp() {
        if (fileManager.isAtRoot) {
            val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
            val rootDoc = safUriCache[primaryRoot]?.let { DocumentNode.fromTreeUri(this, it, primaryRoot) }
            fileManager.init(rootDoc)
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
        pasteBtn.visibility = if (clipboard != null) View.VISIBLE else View.GONE
        infoBtn.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        menuBar.visibility = if (clipboard != null) View.VISIBLE else View.GONE
        infoBtn.visibility = View.GONE
        if (clipboard != null) {
            selectedCount.text = ""
            pasteBtn.visibility = View.VISIBLE
        }
    }

    private fun copySelected() {
        val entries = adapter.selectedEntries
        if (entries.isEmpty()) return
        clipboard = entries
        clipboardMode = ClipboardMode.COPY
        Toast.makeText(this, getString(R.string.toast_copy_queued), Toast.LENGTH_SHORT).show()
        exitSelectionMode()
    }

    private fun resolveWriteNode(node: FsNode): FsNode {
        val file = node.file ?: return node
        if (file.canWrite()) return node
        val canonicalFilePath = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
        val volumeRoot = removableVolumeRootFor(canonicalFilePath)
            ?: removableVolumeRootFor(file.absolutePath)
            ?: return node
        val treeUri = safUriCache[volumeRoot] ?: return node
        val rootDoc = DocumentNode.fromTreeUri(this, treeUri, volumeRoot) ?: return node
        val canonVolumeRoot = try { File(volumeRoot).canonicalPath } catch (_: Exception) { volumeRoot }
        val relative = canonicalFilePath.removePrefix(canonVolumeRoot).trim('/')
        if (relative.isEmpty()) return rootDoc
        var curr: FsNode = rootDoc
        val segments = relative.split('/').filter { it.isNotEmpty() }
        for ((index, segment) in segments.withIndex()) {
            val isLast = index == segments.lastIndex
            val next = curr.listChildren(showHidden = true).firstOrNull {
                (it.isDirectory || isLast) && it.name.equals(segment, ignoreCase = true)
            } ?: return node
            curr = next
        }
        return curr
    }

    private fun pasteClipboard() {
        val sources = clipboard ?: return
        if (!fileSystemReady) return
        val currentParentNode = fileManager.currentNode
        val isMove = clipboardMode == ClipboardMode.MOVE

        val destFile = currentParentNode.file
        if (destFile != null) {
            val destVolume = removableVolumeRootFor(destFile.absolutePath)
            if (destVolume != null && !hasSafAccess(destVolume) && !destFile.canWrite()) {
                pendingSafCallback = { pasteClipboard() }
                requestSafAccess(destVolume)
                return
            }
        }

        if (isMove) {
            for (src in sources) {
                val srcFile = src.file
                if (srcFile != null) {
                    val srcVolume = removableVolumeRootFor(srcFile.absolutePath)
                    if (srcVolume != null && !hasSafAccess(srcVolume) && !srcFile.canWrite()) {
                        pendingSafCallback = { pasteClipboard() }
                        requestSafAccess(srcVolume)
                        return
                    }
                }
            }
        }

        val destCanon = (currentParentNode as? FileNode)?.let {
            try { it.file.canonicalPath } catch (_: Exception) { it.path }
        } ?: currentParentNode.path
        val destPrefix = destCanon.trimEnd('/') + "/"
        for (src in sources) {
            val srcCanon = (src as? FileNode)?.let {
                try { it.file.canonicalPath } catch (_: Exception) { it.path }
            } ?: src.path
            val srcPrefix = srcCanon.trimEnd('/') + "/"
            if (src.isDirectory && (destPrefix == srcPrefix || destPrefix.startsWith(srcPrefix))) {
                Toast.makeText(this, getString(R.string.toast_paste_failed), Toast.LENGTH_SHORT).show()
                return
            }
        }

        pasteBtn.isEnabled = false

        runBackgroundTask(
            task = {
                val destNode = resolveWriteNode(currentParentNode)
                val requiredBytes = sources.sumOf { it.totalSize() }
                if (!FileManager.hasEnoughSpace(destNode, requiredBytes)) {
                    return@runBackgroundTask -1
                }
                var failed = 0
                val successfulSources = mutableListOf<FsNode>()
                for (src in sources) {
                    val nonConflictingName = FsNode.resolveNonConflictingName(destNode, src.name)
                    val ok = transferNode(src, destNode, nonConflictingName, isMove)
                    if (ok) successfulSources.add(src) else failed++
                }
                if (isMove) {
                    for (src in successfulSources) {
                        val writeSrc = resolveWriteNode(src)
                        writeSrc.delete()
                    }
                }
                failed
            },
            onResult = { result ->
                pasteBtn.isEnabled = true
                when (result) {
                    -1 -> {
                        Toast.makeText(this, getString(R.string.toast_not_enough_space), Toast.LENGTH_LONG).show()
                    }
                    0 -> {
                        clipboard = null
                        pasteBtn.visibility = View.GONE
                        if (menuBar.visibility == View.VISIBLE && !adapter.isSelectionMode) {
                            menuBar.visibility = View.GONE
                        }
                        Toast.makeText(this, getString(R.string.toast_paste_success), Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, getString(R.string.toast_paste_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                refreshList()
            }
        )
    }

    private fun transferNode(
        src: FsNode,
        destParent: FsNode,
        newName: String,
        isMove: Boolean
    ): Boolean {
        if (isMove && src is FileNode && destParent is FileNode) {
            val target = File(destParent.file, newName)
            if (src.file.renameTo(target)) return true
        }
        if (src.isDirectory) {
            val newFolder = destParent.createFolder(newName) ?: return false
            var allOk = true
            for (child in src.listChildren(showHidden = true)) {
                val childOk = transferNode(child, newFolder, child.name, isMove)
                if (!childOk) {
                    allOk = false
                    break
                }
            }
            if (!allOk) {
                newFolder.delete()
            }
            return allOk
        } else {
            val ext = src.extension
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
            val newFile = destParent.createFile(newName, mime) ?: return false
            return try {
                src.openInputStream()?.use { inStream ->
                    val outStream = newFile.openOutputStream() ?: run {
                        newFile.delete()
                        return false
                    }
                    outStream.use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (inStream.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                        }
                        out.flush()
                    }
                    true
                } ?: run {
                    newFile.delete()
                    false
                }
            } catch (_: Exception) {
                newFile.delete()
                false
            }
        }
    }

    private fun showRenameDialog() {
        val entries = adapter.selectedEntries
        if (entries.size != 1) {
            Toast.makeText(this, getString(R.string.toast_select_one_to_rename), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val entry = entries[0]

        // SAF check for rename on removable storage
        val targetFile = entry.file
        val targetVolume = targetFile?.let { removableVolumeRootFor(it.absolutePath) }
        if (targetVolume != null && !hasSafAccess(targetVolume) && !targetFile.canWrite()) {
            pendingSafCallback = { showRenameDialog() }
            requestSafAccess(targetVolume)
            return
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_rename_file, null)
        ThemeManager.applyTheme(this, dialogView)

        val editText = dialogView.findViewById<EditText>(R.id.edit_text)
        editText.setText(entry.name)
        editText.selectAll()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.yes_button).setOnClickListener {
            val newName = editText.text.toString().trim()
            if (newName.isNotEmpty() && newName != entry.name) {
                val parentNode = fileManager.currentNode
                runBackgroundTask(
                    task = {
                        val writeParent = resolveWriteNode(parentNode)
                        val resolvedName = FsNode.resolveNonConflictingName(writeParent, newName)
                        val writeTarget = resolveWriteNode(entry)
                        writeTarget.rename(resolvedName) != null
                    },
                    onResult = { ok ->
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
                )
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

    private fun showCreateFolderDialog() {
        if (!fileSystemReady) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_rename_file, null)
        ThemeManager.applyTheme(this, dialogView)

        val title = dialogView.findViewById<TextView>(R.id.rename_title)
        val editText = dialogView.findViewById<EditText>(R.id.edit_text)
        title.setText(R.string.dialog_create_folder)
        editText.hint = getString(R.string.hint_folder_name)
        editText.text.clear()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.yes_button).setOnClickListener {
            val name = editText.text.toString().trim()
            if (name.isNotEmpty()) {
                val currentParent = fileManager.currentNode
                val destFile = currentParent.file
                val volume = destFile?.let { removableVolumeRootFor(it.absolutePath) }
                if (volume != null && !hasSafAccess(volume) && !destFile.canWrite()) {
                    dialog.dismiss()
                    pendingSafCallback = { showCreateFolderDialog() }
                    requestSafAccess(volume)
                    return@setOnClickListener
                }
                runBackgroundTask(
                    task = {
                        val destNode = resolveWriteNode(currentParent)
                        val resolvedName = FsNode.resolveNonConflictingName(destNode, name)
                        destNode.createFolder(resolvedName) != null
                    },
                    onResult = { ok ->
                        Toast.makeText(
                            this,
                            if (ok) getString(R.string.toast_folder_created)
                            else getString(R.string.toast_folder_create_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        if (ok) refreshList()
                    }
                )
            }
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.no_button).setOnClickListener { dialog.dismiss() }

        dialog.show()
        editText.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
    private fun showDeleteConfirmationDialog() {
        val entries = adapter.selectedEntries
        if (entries.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        ThemeManager.applyTheme(this, dialogView)

        val countLabel = dialogView.findViewById<TextView>(R.id.delete_count_label)
        val count = entries.size
        countLabel.text = resources.getQuantityString(R.plurals.delete_count, count, count)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.yes_button).setOnClickListener {
            val toDelete = adapter.selectedEntries
            val volume = toDelete.mapNotNull { it.file }
                .firstOrNull { removableVolumeRootFor(it.absolutePath) != null && !it.canWrite() }
                ?.let { removableVolumeRootFor(it.absolutePath) }
            if (volume != null && !hasSafAccess(volume)) {
                dialog.dismiss()
                pendingSafCallback = { showDeleteConfirmationDialog() }
                requestSafAccess(volume)
                return@setOnClickListener
            }

            runBackgroundTask(
                task = {
                    var failed = 0
                    for (entry in toDelete) {
                        val target = resolveWriteNode(entry)
                        if (!target.delete()) failed++
                    }
                    failed
                },
                onResult = { failed ->
                    Toast.makeText(
                        this,
                        if (failed == 0) getString(R.string.toast_delete_success)
                        else getString(R.string.toast_delete_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    exitSelectionMode()
                    refreshList()
                }
            )
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.no_button).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showFileInfoDialog() {
        val entries = adapter.selectedEntries
        if (entries.isEmpty()) return
        val entry = entries[0]

        val dialogView = layoutInflater.inflate(R.layout.dialog_file_info, null)
        ThemeManager.applyTheme(this, dialogView)

        dialogView.findViewById<TextView>(R.id.info_name_value).text = entry.name

        val type = if (entry.isDirectory) getString(R.string.info_type_folder)
        else "${getString(R.string.info_type_file)} (${entry.extension.uppercase()})"
        dialogView.findViewById<TextView>(R.id.info_type_value).text = type

        val sizeView = dialogView.findViewById<TextView>(R.id.info_size_value)
        if (entry.isDirectory) {
            sizeView.text = "…"
            runBackgroundTask(
                task = {
                    FileManager.totalSize(entry)
                },
                onResult = { rawBytes ->
                    sizeView.text = FileManager.formatSize(resources, rawBytes)
                }
            )
        } else {
            sizeView.text = FileManager.formatSize(resources, entry.size)
        }

        dialogView.findViewById<TextView>(R.id.info_location_value).text =
            entry.file?.parent ?: entry.path.substringBeforeLast('/', "/")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        dialogView.findViewById<TextView>(R.id.info_modified_value).text =
            sdf.format(Date(entry.lastModified))

        dialogView.findViewById<TextView>(R.id.info_permissions_value).text =
            buildPermissionsString(entry)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.close_button).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun buildPermissionsString(entry: FsNode): String {
        val file = entry.file
        if (file != null) {
            val sb = StringBuilder()
            sb.append(if (file.isDirectory) 'd' else '-')
            sb.append(if (file.canRead()) 'r' else '-')
            sb.append(if (file.canWrite()) 'w' else '-')
            sb.append(if (file.canExecute()) 'x' else '-')
            return sb.toString()
        }
        return if (entry.isDirectory) "drwx" else "-rw-"
    }

    private fun moveSelected() {
        val entries = adapter.selectedEntries
        if (entries.isEmpty()) return
        clipboard = entries
        clipboardMode = ClipboardMode.MOVE
        Toast.makeText(this, getString(R.string.toast_navigate_and_paste), Toast.LENGTH_SHORT)
            .show()
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
    private fun showSafGuidanceDialog(volumeRoot: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_saf_guidance, null)
        ThemeManager.applyTheme(this, dialogView)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.no_button).setOnClickListener {
            dialog.dismiss()
            if (!fileSystemReady) showPermissionDenied()
        }

        dialogView.findViewById<View>(R.id.yes_button).setOnClickListener {
            dialog.dismiss()
            pendingSafVolumeRoot = volumeRoot
            safLauncher.launch(null)
        }

        dialog.show()
    }

    private fun requestSafAccess(volumeRoot: String) {
        val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && volumeRoot == primaryRoot) {
            showSafGuidanceDialog(volumeRoot)
        } else {
            pendingSafVolumeRoot = volumeRoot
            Toast.makeText(this, getString(R.string.toast_saf_required), Toast.LENGTH_LONG).show()
            safLauncher.launch(null)
        }
    }
    /**
     * Checks whether a SAF tree [uri] corresponds to [volumeRoot].
     * SAF URIs look like:
     *   content://com.android.externalstorage.documents/tree/1234-ABCD%3A
     * We extract the volume UUID from both the URI and the file path and compare.
     */
    private fun safUriMatchesVolume(uri: Uri, volumeRoot: String): Boolean {
        val uriStr = Uri.decode(uri.toString())
        val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
        if (volumeRoot == primaryRoot) {
            return uriStr.contains("primary", ignoreCase = true)
        }
        val uuidRegex = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
        val volumeUuid = uuidRegex.find(volumeRoot)?.value
        if (volumeUuid != null) {
            return uriStr.contains(volumeUuid, ignoreCase = true)
        }
        val seg = volumeRoot.trimEnd('/').substringAfterLast('/')
        return seg.isNotEmpty() && seg != "0" && !seg.equals("primary", ignoreCase = true) && uriStr.contains(seg, ignoreCase = true)
    }

    private fun openFile(node: FsNode) {
        val uri: Uri = node.uri ?: node.file?.let {
            androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
        } ?: return
        val ext = node.extension
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: contentResolver.getType(uri)
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.toast_no_app_to_open), Toast.LENGTH_SHORT)
                .show()
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
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
            if (hasSafAccess(primaryRoot)) {
                val uri = safUriCache[primaryRoot]
                val rootDoc = uri?.let { DocumentNode.fromTreeUri(this, it, primaryRoot) }
                if (rootDoc != null) {
                    fileManager.init(rootDoc)
                    fileSystemReady = true
                    refreshList()
                    return
                }
            }
            requestSafAccess(primaryRoot)
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
        Toast.makeText(
            this,
            getString(R.string.toast_storage_permission_required),
            Toast.LENGTH_LONG
        ).show()
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
            val canonicalPath = try {
                File(path).canonicalPath
            } catch (_: Exception) {
                path
            }

            // Collect all primary-storage aliases
            val primaryAliases = mutableSetOf<String>()
            try {
                val ext = Environment.getExternalStorageDirectory()
                primaryAliases += ext.absolutePath
                primaryAliases += ext.canonicalPath
            } catch (_: Exception) {
            }
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
                } catch (_: Exception) {
                    "/storage/$vol"
                }
            }

            // Check /mnt/media_rw/<UUID> (Android 6 raw mount) or /mnt/sdcard etc.
            val mntMatch =
                Regex("^/mnt/(?:media_rw|sdcard|extSdCard|external_sd)/([^/]*)").find(canonicalPath)
            if (mntMatch != null) {
                // Try to find the /storage equivalent
                val vol = mntMatch.groupValues[1].ifEmpty { null }
                if (vol != null) {
                    val storageEquiv = File("/storage/$vol")
                    return try {
                        if (storageEquiv.isDirectory) storageEquiv.canonicalPath
                        else {
                            val end =
                                canonicalPath.indexOf('/', mntMatch.value.length).takeIf { it > 0 }
                                    ?: canonicalPath.length
                            canonicalPath.substring(0, end)
                        }
                    } catch (_: Exception) {
                        storageEquiv.absolutePath
                    }
                }
                return canonicalPath.split("/").take(4).joinToString("/")
            }

            return null
        }
    }
}
