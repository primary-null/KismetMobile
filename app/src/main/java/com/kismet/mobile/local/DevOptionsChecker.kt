package com.kismet.mobile.local

import android.content.Context
import android.provider.Settings

object DevOptionsChecker {
    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) != 0
}
