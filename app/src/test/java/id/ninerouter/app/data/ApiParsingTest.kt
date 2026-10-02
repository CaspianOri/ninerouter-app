package id.ninerouter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Parsing tests for the 9Router API JSON payloads. */
class ApiParsingTest {

    private val sampleModelsJson =
        """{"object":"list","data":[{"id":"toptools-default","object":"model","created":1736899200,"owned_by":"top-tools-ai"}]}"""

    @Test
    fun `parseModelsResponse parses sample payload`() {
        val expected = listOf(AiModel("toptools-default", "top-tools-ai"))
        assertEquals(expected, parseModelsResponse(sampleModelsJson))
    }

    @Test
    fun `parseModelsResponse returns empty list for empty data`() {
        assertEquals(emptyList<AiModel>(), parseModelsResponse("""{"object":"list","data":[]}"""))
    }

    @Test
    fun `parseModelsResponse throws on malformed json`() {
        assertThrows(NineRouterException::class.java) { parseModelsResponse("{not valid json") }
    }

    @Test
    fun `parseChatResponse returns assistant content`() {
        val json =
            """{"id":"chatcmpl-1","choices":[{"index":0,"message":{"role":"assistant","content":"hi"}}],"usage":{"total_tokens":7}}"""
        assertEquals("hi", parseChatResponse(json))
    }

    @Test
    fun `parseChatResponse throws NineRouterException on empty choices`() {
        try {
            parseChatResponse("""{"id":"chatcmpl-1","choices":[]}""")
            fail("expected NineRouterException")
        } catch (e: NineRouterException) {
            assertTrue(e.error is NineRouterError.Unknown)
        }
    }

    @Test
    fun `parseChatResponse throws on malformed json`() {
        assertThrows(NineRouterException::class.java) { parseChatResponse("garbage{{{") }
    }
}
