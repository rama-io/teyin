package com.rama.teyin.activities

import com.rama.bohio.activity.BohioAboutActivity
import com.rama.teyin.R

class AboutActivity : BohioAboutActivity() {
    override val appIconRes = R.drawable.teyin
    override val appDescriptionRes = R.string.app_desc
    override val appNameRes = R.string.app_name
    override val appClaimsArrayRes = R.array.app_claims
}
