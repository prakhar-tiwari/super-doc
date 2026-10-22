package com.superdoc.app.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLocale {
    const val HINDI = "hi"
    const val ENGLISH = "en"

    fun current(): Locale {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) Locale.getDefault() else locales[0] ?: Locale.getDefault()
    }

    fun isHindi(): Boolean = current().language == HINDI

    fun set(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    /** A context whose resources follow the in-app language, also for non-Activity code on older Android. */
    fun localized(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(current())
        return context.createConfigurationContext(config)
    }

    fun string(context: Context, resId: Int, vararg args: Any): String =
        localized(context).getString(resId, *args)
}
