package com.sethg.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
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
        val KEY_NOTIFIED_IDS      = stringSetPreferencesKey("notified_ids")        // offers / slots already alerted
        val KEY_NEARBY_SINCE      = stringPreferencesKey("recycler_nearby_since")  // server time of last lot check
    }

    /** Returns only the ids not alerted before, and remembers them. */
    suspend fun takeUnnotified(ids: Collection<String>): List<String> {
        val seen = context.dataStore.data.first()[KEY_NOTIFIED_IDS].orEmpty()
        val fresh = ids.filter { it !in seen }
        if (fresh.isNotEmpty()) {
            context.dataStore.edit { it[KEY_NOTIFIED_IDS] = (seen + fresh).toList().takeLast(500).toSet() }
        }
        return fresh
    }

    suspend fun nearbySince(): String? = context.dataStore.data.first()[KEY_NEARBY_SINCE]

    suspend fun setNearbySince(serverTime: String) {
        context.dataStore.edit { it[KEY_NEARBY_SINCE] = serverTime }
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
