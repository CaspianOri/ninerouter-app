package id.ninerouter.app

import id.ninerouter.app.data.AiModel
import id.ninerouter.app.data.ChatMessage
import id.ninerouter.app.data.NineRouterApi
import id.ninerouter.app.data.NineRouterException

/**
 * Scriptable fake of [NineRouterApi] for UI-layer tests.
 *
 * Handlers default to benign behavior; override per test. Call lists record
 * every invocation for verification.
 */
class FakeNineRouterApi(
    var models: List<AiModel> = emptyList(),
    var listModelsHandler: suspend () -> List<AiModel> = { models },
    var chatHandler: suspend (modelId: String, messages: List<ChatMessage>, maxTokens: Int) -> String =
        { _, _, _ -> "fake reply" },
    /** When null, probeModel falls back to the default implementation (via chatCompletion). */
    var probeHandler: (suspend (modelId: String) -> Boolean)? = null,
) : NineRouterApi {

    val listModelsCalls = mutableListOf<Unit>()
    val chatCalls = mutableListOf<Triple<String, List<ChatMessage>, Int>>()
    val probeCalls = mutableListOf<String>()

    override suspend fun listModels(): List<AiModel> {
        listModelsCalls += Unit
        return listModelsHandler()
    }

    override suspend fun chatCompletion(
        modelId: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): String {
        chatCalls += Triple(modelId, messages, maxTokens)
        return chatHandler(modelId, messages, maxTokens)
    }

    override suspend fun probeModel(modelId: String): Boolean {
        probeCalls += modelId
        return probeHandler?.invoke(modelId) ?: super.probeModel(modelId)
    }
}

/** Throws a [NineRouterException] from a fake handler with less ceremony. */
fun nineRouterFailure(error: id.ninerouter.app.data.NineRouterError): Nothing =
    throw NineRouterException(error, "fake failure: $error")
