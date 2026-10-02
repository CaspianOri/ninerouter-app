package id.ninerouter.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private lateinit var credentialStore: FakeCredentialStore
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
        credentialStore = FakeCredentialStore()
        repo = SettingsRepository(dataStore, credentialStore)
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

    @Test
    fun `api key is stored in credential store not plain datastore`() = runBlocking {
        repo.save("https://router.example.com", "secret-key")

        // The encrypted store holds the key...
        assertEquals("secret-key", credentialStore.getApiKey())
        // ...and the plain DataStore file contains no trace of it.
        val raw = prefsFile.readText()
        assertFalse(raw.contains("secret-key"))
    }

    @Test
    fun `clear wipes the encrypted key`() = runBlocking {
        repo.save("https://router.example.com", "secret-key")
        repo.clear()

        assertEquals(null, credentialStore.getApiKey())
    }

    @Test
    fun `migrateLegacyApiKey moves plain key into encrypted store`() = runBlocking {
        // Simulate an old install: key sitting in the plain DataStore.
        val legacyKey = stringPreferencesKey("server_api_key")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { prefsFile },
        )
        dataStore.edit { it[legacyKey] = "legacy-key" }
        val migrating = SettingsRepository(dataStore, credentialStore)

        migrating.migrateLegacyApiKey()

        assertEquals("legacy-key", credentialStore.getApiKey())
        migrating.serverConfig.test {
            assertEquals(ServerConfig("", "legacy-key"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `migrateLegacyApiKey does not overwrite an existing encrypted key`() = runBlocking {
        credentialStore.saveApiKey("new-key")
        val legacyKey = stringPreferencesKey("server_api_key")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { prefsFile },
        )
        dataStore.edit { it[legacyKey] = "legacy-key" }
        val migrating = SettingsRepository(dataStore, credentialStore)

        migrating.migrateLegacyApiKey()

        assertEquals("new-key", credentialStore.getApiKey())
    }
}
