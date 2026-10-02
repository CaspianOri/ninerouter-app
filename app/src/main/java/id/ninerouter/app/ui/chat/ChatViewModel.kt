package id.ninerouter.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.ninerouter.app.data.AiModel
import id.ninerouter.app.data.ChatHistoryRepository
import id.ninerouter.app.data.ChatMessage
import id.ninerouter.app.data.NineRouterApi
import id.ninerouter.app.data.NineRouterError
import id.ninerouter.app.data.NineRouterException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the chat screen. */
data class ChatUiState(
    val models: List<AiModel> = emptyList(),
    val selectedModelId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val error: NineRouterError? = null,
)

/**
 * Drives a single chat session with per-model histories.
 *
 * The model list is supplied by the caller (the nav graph passes the catalog
 * from [id.ninerouter.app.ui.models.ModelsViewModel]). Selecting a model loads
 * its history from [ChatHistoryRepository]; every exchange (including failed
 * ones) is saved back so histories survive process death and model switches.
 *
 * Any exception from the client — including a malformed server URL — is
 * converted to a [NineRouterError] instead of crashing.
 */
class ChatViewModel(
    private val api: NineRouterApi,
    private val history: ChatHistoryRepository,
    models: List<AiModel>,
    initialModelId: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(models = models))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        val first = initialModelId ?: models.firstOrNull()?.id
        if (first != null) selectModel(first)
    }

    /** Updates the message input field. */
    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    /** Switches to [modelId], loading its stored history. */
    fun selectModel(modelId: String) {
        // Publish the selection synchronously so the UI (and send()) sees it
        // immediately; the stored history fills in when the load completes.
        // The guard below drops stale loads if the user switches again fast.
        _uiState.update { it.copy(selectedModelId = modelId, messages = emptyList(), error = null) }
        viewModelScope.launch {
            val loaded = try {
                history.load(modelId)
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.update { state ->
                if (state.selectedModelId == modelId) state.copy(messages = loaded) else state
            }
        }
    }

    /**
     * Sends the current input to the selected model.
     *
     * On failure the error is surfaced in [ChatUiState.error] while the user
     * message stays in the conversation; the exchange is persisted either way.
     */
    fun send() {
        val state = _uiState.value
        val text = state.input.trim()
        val modelId = state.selectedModelId
        if (text.isBlank() || modelId == null || state.isSending) return
        viewModelScope.launch {
            val userMessage = ChatMessage("user", text)
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    input = "",
                    isSending = true,
                    error = null,
                )
            }
            try {
                val reply = api.chatCompletion(modelId, _uiState.value.messages, MAX_TOKENS)
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage("assistant", reply),
                        isSending = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = (e as? NineRouterException)?.error
                            ?: NineRouterError.Unknown(e.message ?: "unexpected error"),
                    )
                }
            } finally {
                // Persist even on failure: the user message is kept in the history.
                try {
                    history.save(modelId, _uiState.value.messages)
                } catch (e: Exception) {
                    // History is best-effort; a storage failure must not break the chat.
                }
            }
        }
    }

    /** Clears the current error. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        /** Max tokens requested per chat completion. */
        const val MAX_TOKENS = 1024
    }
}
