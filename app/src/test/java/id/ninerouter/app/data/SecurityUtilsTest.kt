package id.ninerouter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [maskApiKey] and [isValidBaseUrl]. */
class SecurityUtilsTest {

    // maskApiKey

    @Test
    fun `maskApiKey shows only last 4 chars`() {
        assertEquals("\u2022\u2022\u2022\u2022c123", maskApiKey("sk-abc123"))
    }

    @Test
    fun `maskApiKey with exactly 4 chars shows only bullets`() {
        assertEquals("\u2022\u2022\u2022\u2022", maskApiKey("ab12"))
    }

    @Test
    fun `maskApiKey with short key shows only bullets`() {
        assertEquals("\u2022\u2022\u2022\u2022", maskApiKey("ab"))
    }

    @Test
    fun `maskApiKey with blank key shows only bullets`() {
        assertEquals("\u2022\u2022\u2022\u2022", maskApiKey(""))
    }

    // isValidBaseUrl

    @Test
    fun `isValidBaseUrl accepts https tunnel url`() {
        assertTrue(isValidBaseUrl("https://e8656a9e68f56f.lhr.life"))
    }

    @Test
    fun `isValidBaseUrl accepts https url with port and path`() {
        assertTrue(isValidBaseUrl("https://example.com:8443/v1"))
    }

    @Test
    fun `isValidBaseUrl rejects plain http`() {
        assertFalse(isValidBaseUrl("http://example.com"))
    }

    @Test
    fun `isValidBaseUrl rejects missing scheme`() {
        assertFalse(isValidBaseUrl("example.com"))
    }

    @Test
    fun `isValidBaseUrl rejects blank`() {
        assertFalse(isValidBaseUrl(""))
        assertFalse(isValidBaseUrl("   "))
    }

    @Test
    fun `isValidBaseUrl rejects bare scheme`() {
        assertFalse(isValidBaseUrl("https://"))
    }

    @Test
    fun `isValidBaseUrl rejects url with spaces`() {
        assertFalse(isValidBaseUrl("https://exa mple.com"))
    }
}
