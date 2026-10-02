package id.ninerouter.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.ninerouter.app.R
import id.ninerouter.app.data.NineRouterError

/**
 * A string resource plus optional format arguments describing a [NineRouterError].
 * Kept as a resource id (not a resolved string) so the UI layer stays localizable.
 */
data class ErrorMessage(
    val resId: Int,
    val args: Array<Any> = emptyArray(),
)

/**
 * Maps a [NineRouterError] to the user-facing string resource used to display it.
 *
 * - [NineRouterError.Unauthorized] -> "Invalid API key (401)"
 * - [NineRouterError.InvalidUrl] -> "Server URL must use https://"
 * - [NineRouterError.ModelUnavailable] -> "Model unavailable (HTTP x)"
 * - [NineRouterError.NetworkError] -> "Network error — check server URL"
 * - [NineRouterError.ServerError] -> "Server error (HTTP x)"
 * - [NineRouterError.Unknown] -> the wrapped message
 */
fun NineRouterError.toErrorMessage(): ErrorMessage = when (this) {
    NineRouterError.Unauthorized ->
        ErrorMessage(R.string.error_invalid_key)
    NineRouterError.InvalidUrl ->
        ErrorMessage(R.string.error_invalid_url)
    is NineRouterError.ModelUnavailable ->
        ErrorMessage(R.string.error_model_unavailable, arrayOf(httpCode))
    is NineRouterError.NetworkError ->
        ErrorMessage(R.string.error_network)
    is NineRouterError.ServerError ->
        ErrorMessage(R.string.error_server, arrayOf(httpCode))
    is NineRouterError.Unknown ->
        ErrorMessage(R.string.error_unknown, arrayOf(message))
}

/** Resolves [toErrorMessage] against the current locale. */
@Composable
fun NineRouterError.localizedMessage(): String {
    val errorMessage = toErrorMessage()
    return stringResource(errorMessage.resId, *errorMessage.args)
}
