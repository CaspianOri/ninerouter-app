package id.ninerouter.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.ninerouter.app.data.AiModel
import id.ninerouter.app.data.ChatHistoryRepository
import id.ninerouter.app.data.NineRouterApi
import id.ninerouter.app.data.NineRouterClient
import id.ninerouter.app.data.ServerConfig
import id.ninerouter.app.data.SettingsRepository
import id.ninerouter.app.data.isConfigured
import id.ninerouter.app.ui.chat.ChatScreen
import id.ninerouter.app.ui.chat.ChatViewModel
import id.ninerouter.app.ui.models.ModelsScreen
import id.ninerouter.app.ui.models.ModelsViewModel
import id.ninerouter.app.ui.settings.SettingsScreen
import id.ninerouter.app.ui.settings.SettingsViewModel

/** Navigation routes. */
object Routes {
    const val SETTINGS = "settings"
    const val MODELS = "models"
    const val CHAT = "chat"
}

/**
 * Root navigation for the app.
 *
 * @param config the current server configuration.
 * @param api the API client, or null when [config] is not [isConfigured]
 *   (setup mode: only the settings route is reachable until the user saves).
 */
@Composable
fun AppNav(
    config: ServerConfig,
    api: NineRouterApi?,
    settingsRepository: SettingsRepository,
    historyRepository: ChatHistoryRepository,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val setupMode = !config.isConfigured
    val startDestination = if (setupMode) Routes.SETTINGS else Routes.MODELS

    val settingsVm: SettingsViewModel = viewModel(factory = SettingsVmFactory(settingsRepository))
    // Keyed by base URL so editing the server in settings rebuilds the client-backed VM.
    val modelsVm: ModelsViewModel = viewModel(
        key = "models:${config.baseUrl}",
        factory = ModelsVmFactory(requireApi(api)),
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsVm,
                setupMode = setupMode,
                onSaved = {
                    navController.navigate(Routes.MODELS) {
                        popUpTo(Routes.SETTINGS) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Routes.MODELS) {
            ModelsScreen(
                viewModel = modelsVm,
                onOpenChat = { navController.navigate(Routes.CHAT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CHAT) {
            val modelsState by modelsVm.uiState.collectAsStateWithLifecycle()
            val chatVm: ChatViewModel = viewModel(
                key = "chat:${config.baseUrl}",
                factory = ChatVmFactory(
                    requireApi(api),
                    historyRepository,
                    modelsState.items.map { it.model },
                ),
            )
            ChatScreen(
                viewModel = chatVm,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
    }
}

/**
 * Returns the API client, building a throwaway one from [config] when [api] is
 * null. The models/chat routes are unreachable in setup mode, so this is only
 * a safety net and never hits the network on its own.
 */
private fun requireApi(api: NineRouterApi?): NineRouterApi = api ?: NineRouterClient("", "")

private class SettingsVmFactory(
    private val repository: SettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        SettingsViewModel(repository) as T
}

private class ModelsVmFactory(
    private val api: NineRouterApi,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        ModelsViewModel(api) as T
}

private class ChatVmFactory(
    private val api: NineRouterApi,
    private val history: ChatHistoryRepository,
    private val models: List<AiModel>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        ChatViewModel(api, history, models) as T
}

/** Builds a [NineRouterClient] from [config], or null when it is not configured. */
fun ServerConfig.apiClient(): NineRouterApi? =
    if (isConfigured) NineRouterClient(baseUrl, apiKey) else null

/** Remembers an API client for [config], rebuilding it when the config changes. */
@Composable
fun rememberApiClient(config: ServerConfig): NineRouterApi? =
    remember(config.baseUrl, config.apiKey) { config.apiClient() }
