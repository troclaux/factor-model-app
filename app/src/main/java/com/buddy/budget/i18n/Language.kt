package com.buddy.budget.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Language(val label: String) { EN("English"), PT("Português") }

/** The app's language, kept in shared preferences. Screens recompose when it changes. */
object AppLanguage {
    private const val PREFERENCES = "settings"
    private const val KEY = "language"

    /** Set through [change], or directly in tests. */
    var current by mutableStateOf(Language.EN)
        internal set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)
        current = Language.entries.find { it.name == saved } ?: Language.EN
    }

    fun change(context: Context, language: Language) {
        current = language
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, language.name).apply()
    }
}

/**
 * [english] in the app's language. The English text is the key into [portuguese]; it may hold %s and %d
 * placeholders for String.format. Text with no translation stays in English.
 */
fun tr(english: String): String = if (AppLanguage.current == Language.PT) portuguese[english] ?: english else english
