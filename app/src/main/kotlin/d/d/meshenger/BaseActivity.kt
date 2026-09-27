/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/*
 * Base class for every Activity
*/
open class BaseActivity : AppCompatActivity() {
    private var currentTheme = R.style.AppTheme_SkyBlue

    fun setAppLanguage(language: String) {
        val localeList = if (language == "system" || language.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun setDefaultNightMode(nightMode: String) {
        defaultNightMode = when (nightMode) {
            "on" -> AppCompatDelegate.MODE_NIGHT_YES
            "off" -> AppCompatDelegate.MODE_NIGHT_NO
            "auto" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                }
            else -> {
                Log.e(this, "Unknown night mode setting: $nightMode")
                return
            }
        }
    }

    fun setDefaultThemeName(themeName: String) {
        defaultThemeName = when (themeName) {
            "fire_red" -> R.style.AppTheme_FireRed
            "grass_green" -> R.style.AppTheme_GrassGreen
            "sky_blue" -> R.style.AppTheme_SkyBlue
            "night_grey" -> R.style.AppTheme_NightGrey
            else -> {
                Log.e(this, "Unknown theme name: $themeName")
                R.style.AppTheme_SkyBlue
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentTheme != defaultThemeName) {
            currentTheme = defaultThemeName
            recreate()
        }
    }

    // set theme, night mode and language
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Database.isDatabaseLoaded()) {
            val savedLanguage = Database.getSettings().language
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val expectedTag = if (savedLanguage == "system") "" else savedLanguage
            val currentTag = if (currentLocales.isEmpty) "" else currentLocales.toLanguageTags()
            if (expectedTag != currentTag) {
                setAppLanguage(savedLanguage)
            }
        }

        if (currentTheme != defaultThemeName) {
            currentTheme = defaultThemeName
            setTheme(defaultThemeName)
        }

        if (defaultNightMode != AppCompatDelegate.getDefaultNightMode()) {
            Log.d(this, "Change night mode to $defaultNightMode")
            AppCompatDelegate.setDefaultNightMode(defaultNightMode)
        }

        super.onCreate(savedInstanceState)
    }

    companion object {
        private var defaultNightMode = AppCompatDelegate.getDefaultNightMode()
        private var defaultThemeName = R.style.AppTheme_SkyBlue

        fun isNightmodeEnabled(context: Context): Boolean {
            val mode = context.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
            return (mode == Configuration.UI_MODE_NIGHT_YES)
        }
    }
}
