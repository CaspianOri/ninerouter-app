package id.ninerouter.app.data

/**
 * In-memory [ChatHistoryRepository] for ViewModel tests.
 *
 * No IO, no dispatchers: loads and saves complete immediately, so
 * `advanceUntilIdle()` in `runTest` is fully deterministic.
 */
class FakeChatHistoryRepository : ChatHistoryRepository {

    private val store = mutableMapOf<String, List<ChatMessage>>()

    override suspend fun load(modelId: String): List<ChatMessage> =
        store[modelId].orEmpty()

    override suspend fun save(modelId: String, messages: List<ChatMessage>) {
        store[modelId] = messages.takeLast(ChatHistoryRepository.MAX_MESSAGES_PER_MODEL)
    }
}
