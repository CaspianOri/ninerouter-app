package id.ninerouter.app.ui.models

import id.ninerouter.app.FakeNineRouterApi
import id.ninerouter.app.data.AiModel
import id.ninerouter.app.data.NineRouterError
import id.ninerouter.app.data.isCombo
import id.ninerouter.app.nineRouterFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** Tests for [ModelsViewModel] using a scripted fake API. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val threeModels = listOf(
        AiModel("combo-a", "owner-a"),
        AiModel("provider/raw", "owner-b"),
        AiModel("combo-c", "owner-c"),
    )

    @Test
    fun `refresh lists models with correct live flags and combo badges`() = runTest(testDispatcher) {
        val probeResults = mapOf("combo-a" to true, "provider/raw" to false, "combo-c" to true)
        val api = FakeNineRouterApi(
            models = threeModels,
            probeHandler = { probeResults.getValue(it) },
        )
        val vm = ModelsViewModel(api)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(3, state.items.size)

        val byId = state.items.associateBy { it.model.id }
        assertEquals(true, byId.getValue("combo-a").live)
        assertEquals(false, byId.getValue("provider/raw").live)
        assertEquals(true, byId.getValue("combo-c").live)

        assertTrue(byId.getValue("combo-a").model.isCombo)
        assertFalse(byId.getValue("provider/raw").model.isCombo)
        assertTrue(byId.getValue("combo-c").model.isCombo)

        assertEquals(listOf("combo-a", "combo-c", "provider/raw"), api.probeCalls.sorted())
    }

    @Test
    fun `refresh while loading is ignored`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<List<AiModel>>()
        var listCalls = 0
        val api = FakeNineRouterApi(
            listModelsHandler = {
                listCalls++
                gate.await()
            },
        )
        val vm = ModelsViewModel(api)

        vm.refresh()
        advanceUntilIdle() // the refresh coroutine is now suspended on the gate, isLoading == true
        assertTrue(vm.uiState.value.isLoading)

        vm.refresh() // must be ignored
        assertEquals(1, listCalls)

        gate.complete(emptyList())
        advanceUntilIdle()

        assertEquals(1, listCalls)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `unauthorized from listModels surfaces error state`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi(
            listModelsHandler = { nineRouterFailure(NineRouterError.Unauthorized) },
        )
        val vm = ModelsViewModel(api)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(NineRouterError.Unauthorized, state.error)
        assertFalse(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertTrue(api.probeCalls.isEmpty())
    }

    @Test
    fun `server error from listModels surfaces error state`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi(
            listModelsHandler = { nineRouterFailure(NineRouterError.ServerError(500)) },
        )
        val vm = ModelsViewModel(api)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(NineRouterError.ServerError(500), vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `unauthorized from a probe surfaces error state`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi(
            models = threeModels,
            probeHandler = { modelId ->
                if (modelId == "provider/raw") nineRouterFailure(NineRouterError.Unauthorized)
                else true
            },
        )
        val vm = ModelsViewModel(api)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(NineRouterError.Unauthorized, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `clearError resets the error`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi(
            listModelsHandler = { nineRouterFailure(NineRouterError.Unauthorized) },
        )
        val vm = ModelsViewModel(api)

        vm.refresh()
        advanceUntilIdle()
        assertEquals(NineRouterError.Unauthorized, vm.uiState.value.error)

        vm.clearError()
        assertNull(vm.uiState.value.error)
    }
}
