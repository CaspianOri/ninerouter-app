package id.ninerouter.app.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.ninerouter.app.data.AiModel
import id.ninerouter.app.data.NineRouterApi
import id.ninerouter.app.data.NineRouterError
import id.ninerouter.app.data.NineRouterException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A model together with its liveness probe result (`null` while probing). */
data class ModelItem(
    val model: AiModel,
    val live: Boolean?,
)

/** UI state for the models screen. */
data class ModelsUiState(
    val isLoading: Boolean = false,
    val items: List<ModelItem> = emptyList(),
    val error: NineRouterError? = null,
)

/**
 * Loads the model catalog and probes each model for liveness.
 *
 * [refresh] lists models, then probes them concurrently (at most
 * [MAX_CONCURRENT_PROBES] at a time, isolated from each other via
 * [supervisorScope]). A refresh started while one is in flight is ignored.
 * Any exception from the client — including a malformed server URL — is
 * converted to a [NineRouterError] instead of crashing.
 */
class ModelsViewModel(
    private val api: NineRouterApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    /** Reloads the catalog and re-probes every model; ignored while loading. */
    fun refresh() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val models = try {
                api.listModels()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toRouterError()) }
                return@launch
            }
            _uiState.update { it.copy(items = models.map { model -> ModelItem(model, live = null) }) }
            try {
                probeAll(models)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.toRouterError()) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun probeAll(models: List<AiModel>) {
        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        supervisorScope {
            models.map { model ->
                async {
                    val live = try {
                        semaphore.withPermit { api.probeModel(model.id) }
                    } catch (e: NineRouterException) {
                        // A bad key is a configuration problem: surface it, don't mark models dead.
                        if (e.error is NineRouterError.Unauthorized) throw e
                        false
                    } catch (e: Exception) {
                        false
                    }
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map { item ->
                                if (item.model.id == model.id) item.copy(live = live) else item
                            },
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /** Clears the current error. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun Exception.toRouterError(): NineRouterError =
        (this as? NineRouterException)?.error
            ?: NineRouterError.Unknown(message ?: "unexpected error")

    companion object {
        private const val MAX_CONCURRENT_PROBES = 6
    }
}
