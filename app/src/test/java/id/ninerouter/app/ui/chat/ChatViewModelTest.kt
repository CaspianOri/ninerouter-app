package id.ninerouter.app.ui.chat

import androidx.datastore.preferences.PreferenceDataStoreFactory
import id.ninerouter.app.FakeNineRouterApi
import id.ninerouter.app.data.AiModel
import id.ninerouter.app.data.ChatHistoryRepository
import id.ninerouter.app.data.ChatMessage
import id.ninerouter.app.data.NineRouterError
import id.ninerouter.app.nineRouterFailure
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
 * Tests for [ChatViewModel] using a fake API and a temp-file history store
 * (on the test dispatcher, so history round-trips deterministically).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tempFiles = mutableListOf<File>()

    private val modelA = AiModel("model-a", "owner")
    private val modelB = AiModel("model-b", "owner")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempFiles.forEach { it.delete() }
    }

    private fun TestScope.history(): ChatHistoryRepository {
        val file = File.createTempFile("chat_vm_test", ".preferences_pb").also { it.delete() }
        tempFiles += file
        return ChatHistoryRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }),
        )
    }

    private fun TestScope.viewModel(
        api: FakeNineRouterApi,
        history: ChatHistoryRepository,
        models: List<AiModel> = listOf(modelA, modelB),
        initialModelId: String? = "model-a",
    ): ChatViewModel {
        val vm = ChatViewModel(api, history, models, initialModelId)
        advanceUntilIdle() // let the initial model selection / history load settle
        return vm
    }

    @Test
    fun `send appends user and assistant messages`() = runTest(testDispatcher) {
        val history = history()
        val api = FakeNineRouterApi(chatHandler = { modelId, _, _ -> "reply from $modelId" })
        val vm = viewModel(api, history)

        vm.onInputChange("hello")
        vm.send()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(
            listOf(
                ChatMessage("user", "hello"),
                ChatMessage("assistant", "reply from model-a"),
            ),
            state.messages,
        )
        assertEquals("", state.input)
        assertFalse(state.isSending)
        assertNull(state.error)

        // The API saw the full conversation and the exchange was persisted.
        assertEquals(1, api.chatCalls.size)
        assertEquals("model-a", api.chatCalls.single().first)
        assertEquals(listOf(ChatMessage("user", "hello")), api.chatCalls.single().second)
        assertEquals(state.messages, history.load("model-a"))
    }

    @Test
    fun `send with blank input does nothing`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi()
        val vm = viewModel(api, history())

        vm.onInputChange("   ")
        vm.send()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.messages.isEmpty())
        assertTrue(api.chatCalls.isEmpty())
    }

    @Test
    fun `per-model histories are isolated across model switches`() = runTest(testDispatcher) {
        val history = history()
        val api = FakeNineRouterApi(chatHandler = { modelId, _, _ -> "reply-$modelId" })
        val vm = viewModel(api, history)

        vm.onInputChange("question for a")
        vm.send()
        advanceUntilIdle()

        vm.selectModel("model-b")
        advanceUntilIdle()
        assertEquals("model-b", vm.uiState.value.selectedModelId)
        assertTrue(vm.uiState.value.messages.isEmpty())

        vm.onInputChange("question for b")
        vm.send()
        advanceUntilIdle()
        assertEquals(
            listOf(
                ChatMessage("user", "question for b"),
                ChatMessage("assistant", "reply-model-b"),
            ),
            vm.uiState.value.messages,
        )

        vm.selectModel("model-a")
        advanceUntilIdle()
        assertEquals(
            listOf(
                ChatMessage("user", "question for a"),
                ChatMessage("assistant", "reply-model-a"),
            ),
            vm.uiState.value.messages,
        )
    }

    @Test
    fun `failed send surfaces error and keeps the user message`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi(
            chatHandler = { _, _, _ -> nineRouterFailure(NineRouterError.NetworkError("timeout")) },
        )
        val vm = viewModel(api, history())

        vm.onInputChange("hello")
        vm.send()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(NineRouterError.NetworkError("timeout"), state.error)
        assertEquals(listOf(ChatMessage("user", "hello")), state.messages)
        assertFalse(state.isSending)
    }

    @Test
    fun `clearError resets the error`() = runTest(testDispatcher) {
        val api = FakeNineRouterApi(
            chatHandler = { _, _, _ -> nineRouterFailure(NineRouterError.Unauthorized) },
        )
        val vm = viewModel(api, history())

        vm.onInputChange("hello")
        vm.send()
        advanceUntilIdle()
        assertEquals(NineRouterError.Unauthorized, vm.uiState.value.error)

        vm.clearError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `selectModel restores previously saved history`() = runTest(testDispatcher) {
        val history = history()
        history.save("model-b", listOf(ChatMessage("user", "old"), ChatMessage("assistant", "older")))
        val vm = viewModel(FakeNineRouterApi(), history)

        vm.selectModel("model-b")
        advanceUntilIdle()

        assertEquals("model-b", vm.uiState.value.selectedModelId)
        assertEquals(
            listOf(ChatMessage("user", "old"), ChatMessage("assistant", "older")),
            vm.uiState.value.messages,
        )
    }
}
