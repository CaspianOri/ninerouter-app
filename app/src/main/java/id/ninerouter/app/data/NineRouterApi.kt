package id.ninerouter.app.data

/**
 * Contract for talking to a 9Router-compatible OpenAI-style API.
 *
 * All methods throw [NineRouterException] on failure.
 */
interface NineRouterApi {

    /**
     * Lists available models (`GET /v1/models`).
     *
     * @throws NineRouterException with [NineRouterError.Unauthorized] on HTTP 401,
     *   [NineRouterError.ServerError] on other 4xx/5xx, [NineRouterError.NetworkError]
     *   on transport failure.
     */
    suspend fun listModels(): List<AiModel>

    /**
     * Runs a chat completion (`POST /v1/chat/completions`).
     *
     * @return the assistant message content of the first choice.
     * @throws NineRouterException on any failure, including [NineRouterError.ModelUnavailable]
     *   when the model is dead (HTTP 404/503).
     */
    suspend fun chatCompletion(
        modelId: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): String

    /**
     * Liveness probe: sends a minimal `ping` completion.
     *
     * @return true when the model answered; false on [NineRouterError.ModelUnavailable],
     *   [NineRouterError.NetworkError], [NineRouterError.ServerError] or [NineRouterError.Unknown].
     * @throws NineRouterException rethrows [NineRouterError.Unauthorized] — a bad key is a
     *   configuration problem, not a dead model, and must surface to the caller.
     */
    suspend fun probeModel(modelId: String): Boolean {
        return try {
            chatCompletion(modelId, listOf(ChatMessage("user", "ping")), 5)
            true
        } catch (e: NineRouterException) {
            when (e.error) {
                NineRouterError.Unauthorized -> throw e
                is NineRouterError.ModelUnavailable,
                is NineRouterError.NetworkError,
                is NineRouterError.ServerError,
                is NineRouterError.Unknown,
                -> false
            }
        }
    }
}
