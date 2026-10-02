package id.ninerouter.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val SETTINGS_DATASTORE_NAME = "ninerouter_settings"

private val KEY_BASE_URL = stringPreferencesKey("server_base_url")

/** Legacy plain-DataStore key, kept only for one-time migration into encrypted storage. */
private val KEY_API_KEY_LEGACY = stringPreferencesKey("server_api_key")

/**
 * Lazily-initialized Preferences DataStore holding the non-secret server settings.
 *
 * The API key is NOT stored here — it lives in [CredentialStore] (encrypted).
 * Obtain a [SettingsRepository] with:
 * ```
 * val repository = SettingsRepository(
 *     context.settingsDataStore,
 *     EncryptedCredentialStore(context),
 * )
 * ```
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
)

/**
 * Persists and exposes the 9Router server configuration.
 *
 * The base URL stays in DataStore; the API key is delegated to [credentialStore]
 * (encrypted at rest) and is never logged. [save] trims both inputs, strips
 * trailing `/` characters from the base URL, and rejects non-HTTPS URLs —
 * callers should check [isValidBaseUrl] first.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val credentialStore: CredentialStore,
) {

    /** Emits the current [ServerConfig] on every change; blank/blank when unset. */
    val serverConfig: Flow<ServerConfig> = dataStore.data.map { prefs ->
        ServerConfig(
            baseUrl = prefs[KEY_BASE_URL].orEmpty(),
            apiKey = credentialStore.getApiKey().orEmpty(),
        )
    }

    /** Saves the server settings. Throws [IllegalArgumentException] on non-HTTPS URLs. */
    suspend fun save(baseUrl: String, apiKey: String) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        require(isValidBaseUrl(cleanUrl)) { "base URL must be https://" }
        credentialStore.saveApiKey(apiKey.trim())
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = cleanUrl
        }
    }

    /** Wipes the base URL and the encrypted API key. */
    suspend fun clear() {
        credentialStore.clear()
        dataStore.edit { prefs -> prefs.clear() }
    }

    /**
     * One-time migration: moves an API key stored by older versions in the
     * plain DataStore into the encrypted [CredentialStore], then deletes the
     * plain copy. Safe to call on every launch.
     */
    suspend fun migrateLegacyApiKey() {
        val legacy = dataStore.data.first()[KEY_API_KEY_LEGACY].orEmpty()
        if (legacy.isNotBlank() && credentialStore.getApiKey().isNullOrBlank()) {
            credentialStore.saveApiKey(legacy)
        }
        dataStore.edit { prefs -> prefs.remove(KEY_API_KEY_LEGACY) }
    }
}
