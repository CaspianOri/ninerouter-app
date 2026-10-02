package id.ninerouter.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class ModelsResponseDto(
    val data: List<ModelDto> = emptyList(),
)

@Serializable
private data class ModelDto(
    val id: String,
    @SerialName("owned_by") val ownedBy: String? = null,
)

@Serializable
private data class ChatResponseDto(
    val choices: List<ChoiceDto> = emptyList(),
)

@Serializable
private data class ChoiceDto(
    val message: MessageDto,
)

@Serializable
private data class MessageDto(
    val content: String? = null,
)

/**
 * Parses a `GET /v1/models` response body into [AiModel]s.
 *
 * @throws NineRouterException wrapping [NineRouterError.Unknown] when the payload is malformed.
 */
fun parseModelsResponse(body: String): List<AiModel> {
    val dto = try {
        json.decodeFromString<ModelsResponseDto>(body)
    } catch (e: Exception) {
        throw NineRouterException(
            NineRouterError.Unknown("malformed models response: ${e.message}"),
            "Failed to parse models response",
        )
    }
    return dto.data.map { AiModel(it.id, it.ownedBy) }
}

/**
 * Extracts the assistant content of the first choice from a
 * `POST /v1/chat/completions` response body.
 *
 * @throws NineRouterException wrapping [NineRouterError.Unknown] when the payload is
 *   malformed or contains no usable choice.
 */
fun parseChatResponse(body: String): String {
    val dto = try {
        json.decodeFromString<ChatResponseDto>(body)
    } catch (e: Exception) {
        throw NineRouterException(
            NineRouterError.Unknown("malformed chat response: ${e.message}"),
            "Failed to parse chat response",
        )
    }
    return dto.choices.firstOrNull()?.message?.content
        ?: throw NineRouterException(
            NineRouterError.Unknown("chat response contained no choices"),
            "Chat response contained no choices",
        )
}

/**
 * Maps an HTTP status code to a typed [NineRouterError]:
 * 401 -> Unauthorized, 404/503 -> ModelUnavailable, any other 4xx/5xx -> ServerError.
 */
fun mapHttpError(httpCode: Int): NineRouterError = when (httpCode) {
    401 -> NineRouterError.Unauthorized
    404, 503 -> NineRouterError.ModelUnavailable(httpCode)
    else -> NineRouterError.ServerError(httpCode)
}
