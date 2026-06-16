package com.rama.teyin.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.rama.bohio.objects.PrefFontStyle
import com.rama.teyin.CsActivity
import com.rama.teyin.R
import com.rama.teyin.activities.settings.SettingsAppearanceController
import com.rama.teyin.activities.settings.SettingsBasicController
import com.rama.teyin.activities.settings.SettingsCheckboxController
import com.rama.teyin.activities.settings.SettingsLanguageController

class SettingsActivity : CsActivity() {
    private lateinit var checkboxController: SettingsCheckboxController
    private lateinit var appearanceController: SettingsAppearanceController
    internal lateinit var settingsRootView: View

    val FONT_PICK_REQUEST = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_settings)

        settingsRootView = findViewById(R.id.settings_root)
        applyEdgeToEdgePadding(settingsRootView)
        applyCurrentTheme(settingsRootView)

        SettingsBasicController(this).setup()
        appearanceController = SettingsAppearanceController(this).also { it.setup() }
        SettingsLanguageController(this).setup()
        checkboxController = SettingsCheckboxController(this).also { it.setup() }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        appearanceController.onActivityResult(requestCode, resultCode, data)
    }
}
