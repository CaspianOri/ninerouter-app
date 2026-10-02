package id.ninerouter.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [SettingsRepository] for ViewModel tests.
 *
 * Mirrors the real implementation's trimming and HTTPS validation so
 * ViewModel tests stay meaningful without DataStore IO.
 */
class FakeSettingsRepository : SettingsRepository {

    private val _serverConfig = MutableStateFlow(ServerConfig("", ""))
    override val serverConfig: Flow<ServerConfig> = _serverConfig

    /** Every (baseUrl, apiKey) pair [save] received, after trimming. */
    val saveCalls = mutableListOf<Pair<String, String>>()

    /** How many times [clear] was called. */
    var clearCalls = 0
        private set

    override suspend fun save(baseUrl: String, apiKey: String) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        require(isValidBaseUrl(cleanUrl)) { "base URL must be https://" }
        val cleanKey = apiKey.trim()
        saveCalls += cleanUrl to cleanKey
        _serverConfig.value = ServerConfig(cleanUrl, cleanKey)
    }

    override suspend fun clear() {
        clearCalls++
        _serverConfig.value = ServerConfig("", "")
    }

    override suspend fun migrateLegacyApiKey() = Unit
}
