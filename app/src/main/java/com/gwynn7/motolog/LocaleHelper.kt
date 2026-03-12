package com.gwynn7.motolog

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

object LocaleHelper {
    private val languageKey = stringPreferencesKey("language")

    enum class Language(val value: String) {
        ITA("it"),
        ENG("en")
    }

    fun onAttach(context: Context): Context {
        // We still need a synchronous value here because attachBaseContext is called before onCreate
        // and needs to return a Context immediately. 
        // For simple settings like language, runBlocking is sometimes used here, 
        // but it's better to provide a default and update dynamically if possible.
        // However, changing the language requires an activity recreate anyway.
        val lang = getLanguage(context)
        return setLocale(context, lang)
    }

    fun getLanguage(context: Context): Language {
        return fromLanguage(getPersistedData(context, Language.ENG.value))
    }

    fun setLocale(context: Context, language: Language): Context {
        persist(context, language.value)
        return updateResources(context, language.value)
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String {
        return runBlocking {
            context.settings.data.first()[languageKey] ?: defaultLanguage
        }
    }

    private fun persist(context: Context, language: String) {
        CoroutineScope(Dispatchers.IO).launch {
            context.settings.edit { settings -> settings[languageKey] = language }
        }
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    private fun fromLanguage(value: String): Language = Language.entries.first { it.value == value }
}