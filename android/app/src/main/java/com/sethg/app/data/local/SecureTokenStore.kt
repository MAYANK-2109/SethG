package com.sethg.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure token store backed by EncryptedSharedPreferences (AES-256-GCM).
 * Uses security-crypto 1.0.0 stable API (MasterKeys).
 */
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val FILE_NAME  = "sethg_secure_prefs"
        private const val KEY_ACCESS  = "access_token"
        private const val KEY_REFRESH = "refresh_token"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_ACCESS).apply()
            else prefs.edit().putString(KEY_ACCESS, value).apply()
        }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_REFRESH).apply()
            else prefs.edit().putString(KEY_REFRESH, value).apply()
        }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
