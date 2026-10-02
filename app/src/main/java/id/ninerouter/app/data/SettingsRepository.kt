package id.ninerouter.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_DATASTORE_NAME = "ninerouter_settings"

private val KEY_BASE_URL = stringPreferencesKey("server_base_url")
private val KEY_API_KEY = stringPreferencesKey("server_api_key")

/**
 * Lazily-initialized Preferences DataStore holding the server settings.
 *
 * The UI layer obtains a [SettingsRepository] with:
 * ```
 * val repository = SettingsRepository(context.settingsDataStore)
 * ```
 * The API key is stored only in this DataStore; it is never logged.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
)

/**
 * Persists and exposes the 9Router server configuration.
 *
 * [save] trims both inputs and strips trailing `/` characters from the base URL.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    /** Emits the current [ServerConfig] on every change; blank/blank when unset. */
    val serverConfig: Flow<ServerConfig> = dataStore.data.map { prefs ->
        ServerConfig(
            baseUrl = prefs[KEY_BASE_URL].orEmpty(),
            apiKey = prefs[KEY_API_KEY].orEmpty(),
        )
    }

    /** Saves the server settings, trimming inputs and stripping trailing `/` from [baseUrl]. */
    suspend fun save(baseUrl: String, apiKey: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[KEY_API_KEY] = apiKey.trim()
        }
    }

    /** Resets the server settings to blank. */
    suspend fun clear() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}
