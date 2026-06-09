package com.rama.teyin.activities.settings

import android.content.Intent
import com.rama.teyin.R
import com.rama.teyin.activities.AboutActivity
import com.rama.teyin.activities.SettingsActivity
import com.rama.teyin.utils.SettingsUiUtils

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