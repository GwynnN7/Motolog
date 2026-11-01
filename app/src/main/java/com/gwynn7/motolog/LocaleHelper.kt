package com.gwynn7.motolog

import android.content.Context
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "language"

    enum class Language(val value: String) {
        ITA("it"),
        ENG("en")
    }

    fun onAttach(context: Context): Context {
        return updateResources(context, getLanguage(context).value)
    }

    fun getLanguage(context: Context): Language {
        return fromLanguage(getPersistedData(context, Language.ENG.value))
    }

    fun setLocale(context: Context, language: Language): Context {
        persist(context, language.value)
        return updateResources(context, language.value)
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, defaultLanguage) ?: defaultLanguage
    }

    private fun persist(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    private fun fromLanguage(value: String): Language =
        Language.entries.firstOrNull { it.value == value } ?: Language.ENG
}
