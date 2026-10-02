package id.ninerouter.app.ui

import id.ninerouter.app.R
import id.ninerouter.app.data.NineRouterError
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for the [NineRouterError] to string-resource mapping in [UiErrorMessages]. */
class UiErrorMessagesTest {

    @Test
    fun `Unauthorized maps to the invalid key string`() {
        val message = NineRouterError.Unauthorized.toErrorMessage()

        assertEquals(R.string.error_invalid_key, message.resId)
        assertEquals(0, message.args.size)
    }

    @Test
    fun `InvalidUrl maps to the invalid url string`() {
        val message = NineRouterError.InvalidUrl.toErrorMessage()

        assertEquals(R.string.error_invalid_url, message.resId)
        assertEquals(0, message.args.size)
    }

    @Test
    fun `ModelUnavailable maps to the model unavailable string with the http code`() {
        val message = NineRouterError.ModelUnavailable(503).toErrorMessage()

        assertEquals(R.string.error_model_unavailable, message.resId)
        assertEquals(listOf(503), message.args.toList())
    }

    @Test
    fun `NetworkError maps to the network error string`() {
        val message = NineRouterError.NetworkError("timeout").toErrorMessage()

        assertEquals(R.string.error_network, message.resId)
    }

    @Test
    fun `ServerError maps to the server error string with the http code`() {
        val message = NineRouterError.ServerError(500).toErrorMessage()

        assertEquals(R.string.error_server, message.resId)
        assertEquals(listOf(500), message.args.toList())
    }

    @Test
    fun `Unknown maps to the unknown error string with the message`() {
        val message = NineRouterError.Unknown("weird").toErrorMessage()

        assertEquals(R.string.error_unknown, message.resId)
        assertEquals(listOf("weird"), message.args.toList())
    }
}
