package com.rama.puma.activities.settings

import android.content.Intent
import com.rama.puma.R
import com.rama.puma.activities.AboutActivity
import com.rama.puma.activities.SettingsActivity
import com.rama.puma.utils.SettingsUiUtils

class SettingsBasicController(private val activity: SettingsActivity) {

    fun setup() {
        SettingsUiUtils.setupButton(activity, R.id.about_button) {
            activity.startActivity(Intent(activity, AboutActivity::class.java))
        }

        SettingsUiUtils.setupButton(activity, R.id.close_button) {
            activity.finish()
        }
    }
}