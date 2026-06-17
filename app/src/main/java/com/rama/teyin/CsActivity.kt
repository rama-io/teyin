package com.rama.teyin

import com.rama.bohio.activity.BohioActivity
import com.rama.teyin.managers.PrefsManager

abstract class CsActivity : BohioActivity() {
    val prefs by lazy { PrefsManager.getInstance(this) }
}
