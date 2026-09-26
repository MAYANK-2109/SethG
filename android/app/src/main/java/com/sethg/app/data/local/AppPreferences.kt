package com.sethg.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sethg_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_LANGUAGE          = stringPreferencesKey("selected_language")
        val KEY_LANGUAGE_SELECTED = booleanPreferencesKey("language_selected")
        val KEY_PRICE_CITY        = stringPreferencesKey("price_city")
    }

    /** City whose scrap rates are used for price estimates (null = not chosen yet). */
    val priceCityFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[KEY_PRICE_CITY] }

    suspend fun setPriceCity(name: String) {
        context.dataStore.edit { prefs -> prefs[KEY_PRICE_CITY] = name }
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "en"
    }

    val languageSelectedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE_SELECTED] ?: false
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE]          = code
            prefs[KEY_LANGUAGE_SELECTED] = true
        }
    }

    suspend fun clearLanguageSelection() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_LANGUAGE_SELECTED)
        }
    }
}
