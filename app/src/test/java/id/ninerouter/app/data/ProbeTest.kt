package id.ninerouter.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for [NineRouterApi.probeModel] using a fake API. Probe semantics:
 * true on success, false on ModelUnavailable/NetworkError/ServerError,
 * and Unauthorized must be rethrown.
 */
class ProbeTest {

    /** Fake NineRouterApi with scripted chatCompletion behavior; records probe calls. */
    private class FakeApi(
        private val handler: suspend (modelId: String) -> String,
    ) : NineRouterApi {
        val calls = mutableListOf<Triple<String, List<ChatMessage>, Int>>()

        override suspend fun listModels(): List<AiModel> = emptyList()

        override suspend fun chatCompletion(
            modelId: String,
            messages: List<ChatMessage>,
            maxTokens: Int,
        ): String {
            calls += Triple(modelId, messages, maxTokens)
            return handler(modelId)
        }
    }

    private fun failingApi(error: NineRouterError) =
        FakeApi { throw NineRouterException(error, "fake failure") }

    @Test
    fun `probeModel returns true on success and sends a ping`() = runTest {
        val api = FakeApi { "pong" }

        assertTrue(api.probeModel("toptools-default"))

        assertEquals(1, api.calls.size)
        val (modelId, messages, maxTokens) = api.calls.single()
        assertEquals("toptools-default", modelId)
        assertEquals(listOf(ChatMessage("user", "ping")), messages)
        assertEquals(5, maxTokens)
    }

    @Test
    fun `probeModel returns false on ModelUnavailable`() = runTest {
        assertFalse(failingApi(NineRouterError.ModelUnavailable(404)).probeModel("dead-model"))
    }

    @Test
    fun `probeModel returns false on NetworkError`() = runTest {
        assertFalse(failingApi(NineRouterError.NetworkError("timeout")).probeModel("any-model"))
    }

    @Test
    fun `probeModel returns false on ServerError`() = runTest {
        assertFalse(failingApi(NineRouterError.ServerError(500)).probeModel("any-model"))
    }

    @Test
    fun `probeModel rethrows Unauthorized`() = runTest {
        val api = failingApi(NineRouterError.Unauthorized)
        try {
            api.probeModel("any-model")
            fail("expected NineRouterException")
        } catch (e: NineRouterException) {
            assertEquals(NineRouterError.Unauthorized, e.error)
        }
    }
}
