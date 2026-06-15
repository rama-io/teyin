package com.rama.teyin.activities.settings

import android.view.View
import com.rama.teyin.R
import com.rama.teyin.activities.SettingsActivity
import com.rama.bohio.managers.PrefsManager.PrefKeys
import com.rama.bohio.widgets.WdCheckbox

class SettingsCheckboxController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        bindWdCheckbox(R.id.show_system_bar, PrefKeys.SYSTEM_BAR_VISIBLE, true)
        bindWdCheckbox(
            R.id.prevent_home_screen_rotation,
            PrefKeys.SYSTEM_PREVENT_ROTATION,
            false,
            onChange = { isChecked ->
                activity.applyRotationLock(isChecked)
            }
        )
    }

    private fun bindWdCheckbox(
        wdCheckboxId: Int,
        key: String,
        defaultValue: Boolean,
        dependentViewIds: List<Int>? = null,
        onChange: ((Boolean) -> Unit)? = null
    ) {
        val checkbox = activity.findViewById<WdCheckbox>(wdCheckboxId)
        val dependents = dependentViewIds?.map { activity.findViewById<View>(it) }

        val isChecked = prefs.getBoolean(key, defaultValue)
        checkbox.setChecked(isChecked)

        dependents?.forEach {
            it.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        checkbox.setOnCheckedChangeListener { checked ->
            prefs.setBoolean(key, checked)
            dependents?.forEach {
                it.visibility = if (checked) View.VISIBLE else View.GONE
            }
            onChange?.invoke(checked)
        }
    }
}
