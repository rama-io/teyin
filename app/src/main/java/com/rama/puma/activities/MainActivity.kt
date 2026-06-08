package com.rama.puma.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.rama.puma.CsActivity
import com.rama.puma.R
import com.rama.puma.adapters.FileListAdapter
import com.rama.puma.managers.FileManager
import com.rama.puma.managers.PrefsManager

class MainActivity : CsActivity() {
    private lateinit var rootView: View
    private lateinit var listView: ListView
    private lateinit var searchBar: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var searchButton: FrameLayout
    private lateinit var clearBtn: FrameLayout
    private lateinit var settingsBtn: FrameLayout
    private lateinit var currentDir: LinearLayout
    private lateinit var currentFolderName: TextView
    private lateinit var menuBar: LinearLayout
    private lateinit var selectedCount: TextView
    private lateinit var cancelSelectionBtn: FrameLayout
    private lateinit var renameBtn: FrameLayout
    private lateinit var moveToGroupBtn: FrameLayout
    private lateinit var appSettingsBtn: FrameLayout
    private val fileManager = FileManager()
    private lateinit var adapter: FileListAdapter
    private var isSearchExpanded = false
    private var isProgrammaticSearchUpdate = false
    private val searchDebounceHandler = Handler(Looper.getMainLooper())
    private var searchDebounceRunnable: Runnable? = null
    private var currentSearchQuery: String = ""
    private var resumeRefreshRunnable: Runnable? = null
    private var fileSystemReady = false

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

        menuBar = findViewById(R.id.menu_bar)
        selectedCount = findViewById(R.id.selected_count)
        cancelSelectionBtn = findViewById(R.id.multi_select_cancel_button)
        renameBtn = findViewById(R.id.rename_btn)
        moveToGroupBtn = findViewById(R.id.move_to_group_button)
        appSettingsBtn = findViewById(R.id.app_settings)

        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        cancelSelectionBtn.setOnClickListener { exitSelectionMode() }

        // TODO: Placeholder actions, waiting for real logic
        renameBtn.setOnClickListener {
            Toast.makeText(this, "${adapter.selectedCount} item(s) selected", Toast.LENGTH_SHORT)
                .show()
        }
        moveToGroupBtn.setOnClickListener {
            Toast.makeText(this, "Move: ${adapter.selectedCount} item(s)", Toast.LENGTH_SHORT)
                .show()
        }
        appSettingsBtn.setOnClickListener {
            Toast.makeText(this, "Settings for selection", Toast.LENGTH_SHORT).show()
        }

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
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) initFileSystem()
            else showPermissionDenied()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MANAGE_ALL_FILES) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
                initFileSystem()
            else showPermissionDenied()
        }
    }

    private fun initFileList() {
        adapter = FileListAdapter(this)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            when {
                adapter.isUpRow(position) -> navigateUp()

                // In selection mode tap toggles selection
                adapter.isSelectionMode -> {
                    adapter.toggleSelection(adapter.getEntry(position))
                    updateSelectionBar()
                }

                // Normal tap
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
                // Already in selection mode, toggle this item
                adapter.toggleSelection(entry)
                updateSelectionBar()
            } else {
                // Enter selection mode with this item pre-selected
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
        adapter.update(entries, hasParent = !fileManager.isAtRoot)

        val dirName =
            if (fileManager.isAtRoot) "Storage"
            else fileManager.currentDir.name

        currentFolderName.text = dirName

        if (adapter.isSelectionMode) updateSelectionBar()

        applyCurrentTheme(rootView)
    }

    private fun navigateUp() {
        fileManager.goUp()
        exitSelectionMode()   // always clear selection when changing directory
        refreshList()
    }

    private fun showSelectionBar() {
        menuBar.visibility = View.VISIBLE
        updateSelectionBar()
    }

    private fun updateSelectionBar() {
        val count = adapter.selectedCount
        selectedCount.text = resources.getQuantityString(
            R.plurals.selected_count, count, count
        )
        // If all items were deselected via tapping, exit automatically
        if (count == 0) exitSelectionMode()
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        menuBar.visibility = View.GONE
    }

    private fun openFile(file: java.io.File) {
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
            // Permissions are granted at install time on API 21-22
            initFileSystem()
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
    }
}
