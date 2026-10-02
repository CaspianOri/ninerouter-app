package id.ninerouter.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists per-model chat histories.
 *
 * Histories are capped at [MAX_MESSAGES_PER_MODEL] messages per model
 * (the most recent messages are kept). Corrupt payloads read back as empty
 * rather than crashing.
 */
interface ChatHistoryRepository {

    /** Loads the stored history for [modelId], or an empty list when none exists. */
    suspend fun load(modelId: String): List<ChatMessage>

    /**
     * Replaces the stored history for [modelId], keeping only the most recent
     * [MAX_MESSAGES_PER_MODEL] messages.
     */
    suspend fun save(modelId: String, messages: List<ChatMessage>)

    companion object {
        /** Maximum number of messages retained per model; older messages are dropped. */
        const val MAX_MESSAGES_PER_MODEL = 100
    }
}

/**
 * [ChatHistoryRepository] persisted in a Preferences DataStore as a single
 * JSON object mapping model id to its message list.
 *
 * Obtain with the same DataStore the settings use:
 * ```
 * val history = DataStoreChatHistoryRepository(context.settingsDataStore)
 * ```
 */
class DataStoreChatHistoryRepository(
    private val dataStore: DataStore<Preferences>,
) : ChatHistoryRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(modelId: String): List<ChatMessage> {
        return readAll(dataStore.data.first()[KEY_HISTORY_JSON].orEmpty())[modelId].orEmpty()
    }

    override suspend fun save(modelId: String, messages: List<ChatMessage>) {
        dataStore.edit { prefs ->
            val current = readAll(prefs[KEY_HISTORY_JSON].orEmpty())
            val updated = current + (modelId to messages.takeLast(ChatHistoryRepository.MAX_MESSAGES_PER_MODEL))
            prefs[KEY_HISTORY_JSON] = json.encodeToString(updated)
        }
    }

    private fun readAll(raw: String): Map<String, List<ChatMessage>> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, List<ChatMessage>>>(raw)
        }.getOrDefault(emptyMap())
    }

    companion object {
        private val KEY_HISTORY_JSON = stringPreferencesKey("chat_history_json")
    }
}
