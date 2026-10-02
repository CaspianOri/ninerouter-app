package id.ninerouter.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * [CredentialStore] backed by AndroidX Security's [EncryptedSharedPreferences].
 *
 * Keys are encrypted with AES256-SIV and values with AES256-GCM; the master
 * key lives in the Android Keystore and never leaves the device. The API key
 * is never logged — callers display only [maskApiKey].
 */
class EncryptedCredentialStore(context: Context) : CredentialStore {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun getApiKey(): String? =
        prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotEmpty() }

    override fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    private companion object {
        const val PREFS_NAME = "ninerouter_credentials"
        const val KEY_API_KEY = "api_key"
    }
}
