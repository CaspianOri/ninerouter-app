package id.ninerouter.app.data

/**
 * Security helpers for handling the 9Router API key.
 *
 * The key grants access to paid/proxied LLM providers, so it is never logged,
 * never shown in full in the UI, and only persisted via [CredentialStore].
 */

private const val MASK_BULLETS = "\u2022\u2022\u2022\u2022"
private const val VISIBLE_TAIL_LENGTH = 4

/**
 * Returns a masked representation of [apiKey] showing only the last 4
 * characters (e.g. `••••ab12`). Keys of 4 characters or fewer — and blank
 * keys — render as bullets only, so nothing sensitive leaks.
 */
fun maskApiKey(apiKey: String): String =
    if (apiKey.length <= VISIBLE_TAIL_LENGTH) MASK_BULLETS
    else MASK_BULLETS + apiKey.takeLast(VISIBLE_TAIL_LENGTH)

/**
 * Returns true when [url] is a well-formed HTTPS base URL.
 *
 * Plain `http://` is rejected: the API key travels in the Authorization
 * header and must never go over cleartext.
 */
fun isValidBaseUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
    if (!trimmed.startsWith("https://", ignoreCase = true)) return false
    val afterScheme = trimmed.substring(trimmed.indexOf("://") + 3)
    val host = afterScheme.substringBefore('/').substringBefore(':')
    if (host.isEmpty()) return false
    // Reject malformed hosts: no leading/trailing dots or dashes, has at least
    // a dot or is localhost / an IP literal.
    if (host.startsWith('.') || host.startsWith('-')) return false
    return true
}
