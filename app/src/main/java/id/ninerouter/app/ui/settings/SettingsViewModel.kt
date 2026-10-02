package id.ninerouter.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.ninerouter.app.data.NineRouterApi
import id.ninerouter.app.data.NineRouterClient
import id.ninerouter.app.data.NineRouterError
import id.ninerouter.app.data.NineRouterException
import id.ninerouter.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the settings / first-run setup screen. */
data class SettingsUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    /** Number of models reported by the last successful connection test, if any. */
    val modelCount: Int? = null,
    val error: NineRouterError? = null,
    /** Set when [save] persisted the fields; the screen consumes it to navigate away. */
    val saved: Boolean = false,
)

/**
 * Edits the server configuration, persists it through [SettingsRepository],
 * and can probe the server with a throwaway API client built by [apiFactory].
 *
 * The repository trims both fields on save. All client calls are wrapped so a
 * malformed server URL surfaces as [NineRouterError.Unknown] instead of crashing.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val apiFactory: (baseUrl: String, apiKey: String) -> NineRouterApi = ::NineRouterClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.serverConfig.collect { config ->
                _uiState.update { it.copy(baseUrl = config.baseUrl, apiKey = config.apiKey) }
            }
        }
    }

    /** Updates the server base URL field. */
    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    /** Updates the API key field. */
    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value) }
    }

    /** Persists the current fields; no-op when either field is blank. */
    fun save() {
        val state = _uiState.value
        if (state.baseUrl.isBlank() || state.apiKey.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                repository.save(state.baseUrl, state.apiKey)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = NineRouterError.Unknown(e.message ?: "save failed"))
                }
            }
        }
    }

    /** Lists models through a throwaway client to verify the current fields. */
    fun testConnection() {
        if (_uiState.value.isTesting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, error = null, modelCount = null) }
            val baseUrl = _uiState.value.baseUrl.trim()
            val apiKey = _uiState.value.apiKey.trim()
            val result = runCatching {
                apiFactory(baseUrl, apiKey).listModels().size
            }
            val error = result.exceptionOrNull()?.let { throwable ->
                (throwable as? NineRouterException)?.error
                    ?: NineRouterError.Unknown(throwable.message ?: "connection failed")
            }
            _uiState.update {
                it.copy(
                    isTesting = false,
                    modelCount = result.getOrNull(),
                    error = error,
                )
            }
        }
    }

    /** Clears the current error. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Resets [SettingsUiState.saved] after the screen has consumed it. */
    fun onSavedConsumed() {
        _uiState.update { it.copy(saved = false) }
    }
}
