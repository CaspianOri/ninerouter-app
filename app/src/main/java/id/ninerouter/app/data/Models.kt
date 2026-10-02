package id.ninerouter.app.data

import kotlinx.serialization.Serializable

/** A single chat turn sent to or received from the 9Router API. */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

/** A model id as reported by `GET /v1/models`. */
data class AiModel(
    val id: String,
    val ownedBy: String?,
)

/**
 * A model is a "combo" (9Router's routing alias, e.g. `toptools-default`) when its id
 * is a bare name; provider-qualified ids like `apmix/gpt-6-luna-free` are not combos.
 */
val AiModel.isCombo: Boolean get() = !id.contains("/")

/** Typed failure modes of the 9Router API. */
sealed interface NineRouterError {
    /** The API key was rejected (HTTP 401). Never retry with the same key. */
    data object Unauthorized : NineRouterError

    /** The configured server URL is not a valid HTTPS URL. */
    data object InvalidUrl : NineRouterError

    /** The model is dead or unknown (HTTP 404/503). */
    data class ModelUnavailable(val httpCode: Int) : NineRouterError

    /** Transport-level failure (DNS, TLS, timeout, connection reset). */
    data class NetworkError(val cause: String) : NineRouterError

    /** Any other 4xx/5xx response from the server. */
    data class ServerError(val httpCode: Int) : NineRouterError

    /** Anything else: malformed payloads, unexpected states. */
    data class Unknown(val message: String) : NineRouterError
}

/** Exception thrown by [NineRouterApi] implementations; carries the typed [error]. */
class NineRouterException(
    val error: NineRouterError,
    message: String,
) : Exception(message)

/** Server connection settings. The API key is only ever persisted in DataStore. */
data class ServerConfig(
    val baseUrl: String,
    val apiKey: String,
)

/** True only when both the base URL and the API key are non-blank. */
val ServerConfig.isConfigured: Boolean
    get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
