package id.ninerouter.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class ChatRequestDto(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
)

/**
 * OkHttp implementation of [NineRouterApi].
 *
 * Connect timeout 15s, read timeout 60s. The API key is sent only as an
 * `Authorization: Bearer` header and is never logged.
 */
class NineRouterClient(
    private val baseUrl: String,
    private val apiKey: String,
) : NineRouterApi {

    private val normalizedBaseUrl: String = baseUrl.trim().trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$normalizedBaseUrl/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        execute(request, ::parseModelsResponse)
    }

    override suspend fun chatCompletion(
        modelId: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): String = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(ChatRequestDto(modelId, messages, maxTokens))
        val request = Request.Builder()
            .url("$normalizedBaseUrl/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        execute(request, ::parseChatResponse)
    }

    private fun <T> execute(request: Request, parse: (String) -> T): T {
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = mapHttpError(response.code)
                    throw NineRouterException(error, "HTTP ${response.code}")
                }
                return parse(body)
            }
        } catch (e: NineRouterException) {
            throw e
        } catch (e: IOException) {
            throw NineRouterException(
                NineRouterError.NetworkError(e.message ?: "network failure"),
                "Network failure: ${e.message}",
            )
        }
    }
}
