package id.ninerouter.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for mapping HTTP status codes to typed 9Router errors. */
class ErrorMappingTest {

    @Test
    fun `401 maps to Unauthorized`() {
        assertEquals(NineRouterError.Unauthorized, mapHttpError(401))
    }

    @Test
    fun `404 maps to ModelUnavailable`() {
        assertEquals(NineRouterError.ModelUnavailable(404), mapHttpError(404))
    }

    @Test
    fun `503 maps to ModelUnavailable`() {
        assertEquals(NineRouterError.ModelUnavailable(503), mapHttpError(503))
    }

    @Test
    fun `500 maps to ServerError`() {
        assertEquals(NineRouterError.ServerError(500), mapHttpError(500))
    }

    @Test
    fun `other 4xx maps to ServerError`() {
        assertEquals(NineRouterError.ServerError(429), mapHttpError(429))
    }
}
