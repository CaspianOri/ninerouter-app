package id.ninerouter.app.data

import androidx.datastore.preferences.PreferenceDataStoreFactory
import app.cash.turbine.test
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests for [SettingsRepository] backed by a temp-file Preferences DataStore. */
class SettingsRepositoryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // createTempFile creates the file; delete it so DataStore starts from a clean slate.
        prefsFile = File.createTempFile("settings_repo_test", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { prefsFile },
        )
        repo = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        prefsFile.delete()
    }

    @Test
    fun `save emits trimmed ServerConfig with trailing slash stripped`() = runBlocking {
        repo.save("  https://router.example.com/  ", "  secret-key  ")

        repo.serverConfig.test {
            assertEquals(ServerConfig("https://router.example.com", "secret-key"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `serverConfig defaults to blank and isConfigured is false`() = runBlocking {
        repo.serverConfig.test {
            val config = awaitItem()
            assertEquals(ServerConfig("", ""), config)
            assertFalse(config.isConfigured)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isConfigured is false when only one field is blank`() = runBlocking {
        repo.save("https://router.example.com", "   ")

        repo.serverConfig.test {
            assertFalse(awaitItem().isConfigured)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isConfigured is true when both fields are set`() = runBlocking {
        repo.save("https://router.example.com", "secret-key")

        repo.serverConfig.test {
            assertTrue(awaitItem().isConfigured)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clear resets config to blank`() = runBlocking {
        repo.save("https://router.example.com", "secret-key")
        repo.clear()

        repo.serverConfig.test {
            assertEquals(ServerConfig("", ""), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
