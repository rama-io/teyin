package com.rama.puma.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ListView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.rama.puma.CsActivity
import com.rama.puma.R
import com.rama.puma.managers.PrefsManager

class MainActivity : CsActivity() {
    private lateinit var listView: ListView
    private lateinit var rootView: View
    private lateinit var searchField: EditText
    private lateinit var searchIcon: FrameLayout
    private lateinit var clearBtn: FrameLayout
    private lateinit var settingsBtn: FrameLayout
    private var isSearchExpanded = false
    private var isProgrammaticSearchUpdate = false
    private val searchDebounceHandler = Handler(Looper.getMainLooper())
    private var searchDebounceRunnable: Runnable? = null
    private var resumeRefreshRunnable: Runnable? = null
    private var currentSearchQuery: String = ""

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_F10 -> {
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
        settingsBtn = findViewById<FrameLayout>(R.id.settings_btn)
        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }

        initSearchbar()
    }

    private fun initSearchbar() {
        searchField = findViewById(R.id.search_field)
        searchIcon = findViewById(R.id.search_icon)
        clearBtn = findViewById(R.id.clear_field)

        searchField.visibility = View.GONE
        clearBtn.visibility = View.GONE

        searchIcon.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

            if (isSearchExpanded) {
                collapseSearch()
            } else {
                expandSearch()
            }
        }

        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isProgrammaticSearchUpdate) return

                val query = s.toString()

                searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }

                searchDebounceRunnable = Runnable {
                    currentSearchQuery = query
                }
                searchDebounceHandler.postDelayed(searchDebounceRunnable!!, 300)

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
        }
    }

    private fun expandSearch() {
        searchField.visibility = View.VISIBLE
        searchField.requestFocus()

        val scaleX = ObjectAnimator.ofFloat(searchField, "scaleX", 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(searchField, "scaleY", 0.8f, 1f)
        val alpha = ObjectAnimator.ofFloat(searchField, "alpha", 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 300
            interpolator = OvershootInterpolator(1.5f)
            start()
        }

        val imm =
            getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(searchField, 0)
    }

    private fun collapseSearch(clearQuery: Boolean = true, hideKeyboard: Boolean = true) {
        searchField.visibility = View.GONE
        clearBtn.visibility = View.GONE
        searchField.clearFocus()

        if (clearQuery) {
            currentSearchQuery = ""
            searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
            isProgrammaticSearchUpdate = true
            searchField.text.clear()
            isProgrammaticSearchUpdate = false
        }

        if (hideKeyboard) {
            val imm =
                getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(searchField.windowToken, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        syncSettings()
        schedulePostResumeRefresh()
        expandSearch()
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return super.dispatchTouchEvent(ev)
    }

    private fun syncSettings() {
    }

    private fun schedulePostResumeRefresh() {
        clearPendingResumeRefresh()

        resumeRefreshRunnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
        }

        rootView.post(resumeRefreshRunnable)
    }

    private fun clearPendingResumeRefresh() {
        resumeRefreshRunnable?.let {
            rootView.removeCallbacks(it)
        }
        resumeRefreshRunnable = null
    }
}
