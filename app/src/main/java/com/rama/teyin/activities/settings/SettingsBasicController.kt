package com.rama.teyin.activities.settings

import android.content.Intent
import com.rama.teyin.R
import com.rama.teyin.activities.AboutActivity
import com.rama.teyin.activities.SettingsActivity
import com.rama.bohio.util.UiActions

class SettingsBasicController(private val activity: SettingsActivity) {

    fun setup() {
        UiActions.setupButton(activity, R.id.about_button) {
            activity.startActivity(Intent(activity, AboutActivity::class.java))
        }

        UiActions.setupButton(activity, R.id.close_button) {
            activity.finish()
        }
    }
}