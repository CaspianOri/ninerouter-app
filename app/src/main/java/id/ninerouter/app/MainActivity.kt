package id.ninerouter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import id.ninerouter.app.data.ChatHistoryRepository
import id.ninerouter.app.data.EncryptedCredentialStore
import id.ninerouter.app.data.SettingsRepository
import id.ninerouter.app.data.settingsDataStore
import id.ninerouter.app.ui.AppNav
import id.ninerouter.app.ui.rememberApiClient
import kotlinx.coroutines.launch

/**
 * Entry point of the 9Router app.
 *
 * Reads the server configuration once at startup: when nothing is configured
 * the app starts on the settings screen in setup mode, otherwise on the
 * models screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The API key lives in encrypted storage (AndroidX Security); the
        // base URL stays in DataStore. Migrate any legacy plain-DataStore key
        // exactly once before the UI reads the config.
        val settingsRepository = SettingsRepository(
            applicationContext.settingsDataStore,
            EncryptedCredentialStore(applicationContext),
        )
        lifecycleScope.launch {
            settingsRepository.migrateLegacyApiKey()
        }
        val historyRepository = ChatHistoryRepository(applicationContext.settingsDataStore)
        setContent {
            MaterialTheme {
                NineRouterApp(
                    settingsRepository = settingsRepository,
                    historyRepository = historyRepository,
                )
            }
        }
    }
}

/** Root composable: splash while the config loads, then the nav graph. */
@Composable
private fun NineRouterApp(
    settingsRepository: SettingsRepository,
    historyRepository: ChatHistoryRepository,
) {
    val config by settingsRepository.serverConfig.collectAsStateWithLifecycle(initialValue = null)
    Surface(modifier = Modifier.fillMaxSize()) {
        val currentConfig = config
        if (currentConfig == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            AppNav(
                config = currentConfig,
                api = rememberApiClient(currentConfig),
                settingsRepository = settingsRepository,
                historyRepository = historyRepository,
            )
        }
    }
}
