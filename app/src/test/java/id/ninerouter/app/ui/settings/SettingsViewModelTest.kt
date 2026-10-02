package id.ninerouter.app.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import id.ninerouter.app.FakeNineRouterApi
import id.ninerouter.app.R
import id.ninerouter.app.data.AiModel
import id.ninerouter.app.data.NineRouterError
import id.ninerouter.app.data.ServerConfig
import id.ninerouter.app.data.SettingsRepository
import id.ninerouter.app.nineRouterFailure
import id.ninerouter.app.ui.toErrorMessage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [SettingsViewModel] using a temp-file DataStore (on the test
 * dispatcher, so every save round-trips deterministically) and a fake API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempFiles.forEach { it.delete() }
    }

    private fun TestScope.repository(): SettingsRepository {
        // createTempFile creates the file; delete it so DataStore starts from a clean slate.
        val file = File.createTempFile("settings_vm_test", ".preferences_pb").also { it.delete() }
        tempFiles += file
        return SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }),
        )
    }

    private fun TestScope.viewModel(api: FakeNineRouterApi): SettingsViewModel {
        val vm = SettingsViewModel(repository(), apiFactory = { _, _ -> api })
        advanceUntilIdle() // let the init serverConfig collect settle
        return vm
    }

    @Test
    fun `save delegates trimmed values to the repository`() = runTest(testDispatcher) {
        val repository = repository()
        val vm = SettingsViewModel(repository, apiFactory = { _, _ -> FakeNineRouterApi() })
        advanceUntilIdle()

        vm.onBaseUrlChange("  https://router.example.com/  ")
        vm.onApiKeyChange("  secret-key  ")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        repository.serverConfig.test {
            assertEquals(ServerConfig("https://router.example.com", "secret-key"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save marks nothing saved when fields are blank`() = runTest(testDispatcher) {
        val vm = viewModel(FakeNineRouterApi())

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.saved)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `testConnection success reports the model count`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi(
            models = listOf(
                AiModel("combo-a", "owner"),
                AiModel("combo-b", "owner"),
                AiModel("provider/raw", "owner"),
            ),
        )
        val vm = viewModel(api)

        vm.onBaseUrlChange("https://router.example.com")
        vm.onApiKeyChange("secret-key")
        vm.testConnection()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(3, state.modelCount)
        assertNull(state.error)
        assertFalse(state.isTesting)
        assertEquals(1, api.listModelsCalls.size)
    }

    @Test
    fun `testConnection with 401 surfaces the invalid key error`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi(
            listModelsHandler = { nineRouterFailure(NineRouterError.Unauthorized) },
        )
        val vm = viewModel(api)

        vm.onBaseUrlChange("https://router.example.com")
        vm.onApiKeyChange("bad-key")
        vm.testConnection()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(NineRouterError.Unauthorized, state.error)
        // The human-readable message for this error is the "invalid key" string.
        assertEquals(R.string.error_invalid_key, state.error!!.toErrorMessage().resId)
        assertNull(state.modelCount)
        assertFalse(state.isTesting)
    }

    @Test
    fun `testConnection maps network failure to the network error`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi(
            listModelsHandler = { nineRouterFailure(NineRouterError.NetworkError("timeout")) },
        )
        val vm = viewModel(api)

        vm.testConnection()
        advanceUntilIdle()

        assertEquals(NineRouterError.NetworkError("timeout"), vm.uiState.value.error)
    }

    @Test
    fun `testConnection never crashes on a throwing api factory`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(
            repository(),
            apiFactory = { _, _ -> throw IllegalArgumentException("bad url") },
        )
        advanceUntilIdle()

        vm.testConnection()
        advanceUntilIdle()

        val error = vm.uiState.value.error
        assertTrue(error is NineRouterError.Unknown)
        assertFalse(vm.uiState.value.isTesting)
    }

    @Test
    fun `uiState emits field edits`() = runTest(testDispatcher) {
        val vm = viewModel(FakeNineRouterApi())

        vm.uiState.test {
            assertEquals("", awaitItem().baseUrl)

            vm.onBaseUrlChange("https://router.example.com")
            assertEquals("https://router.example.com", awaitItem().baseUrl)

            vm.onApiKeyChange("secret-key")
            assertEquals("secret-key", awaitItem().apiKey)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
