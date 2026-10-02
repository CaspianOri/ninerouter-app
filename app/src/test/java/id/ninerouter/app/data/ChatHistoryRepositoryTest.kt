package id.ninerouter.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests for [ChatHistoryRepository] backed by a temp-file Preferences DataStore. */
class ChatHistoryRepositoryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File
    private lateinit var repo: ChatHistoryRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File.createTempFile("chat_history_test", ".preferences_pb").also { it.delete() }
        repo = ChatHistoryRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { prefsFile }),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        prefsFile.delete()
    }

    @Test
    fun `save and load round-trip per model id`() = runBlocking {
        val historyA = listOf(
            ChatMessage("user", "hello a"),
            ChatMessage("assistant", "hi a"),
        )
        val historyB = listOf(ChatMessage("user", "hello b"))

        repo.save("model-a", historyA)
        repo.save("model-b", historyB)

        assertEquals(historyA, repo.load("model-a"))
        assertEquals(historyB, repo.load("model-b"))
    }

    @Test
    fun `load returns empty list for unknown model id`() = runBlocking {
        repo.save("model-a", listOf(ChatMessage("user", "hello")))

        assertEquals(emptyList<ChatMessage>(), repo.load("model-unknown"))
    }

    @Test
    fun `save overwrites previous history for the same model`() = runBlocking {
        repo.save("model-a", listOf(ChatMessage("user", "first")))
        repo.save("model-a", listOf(ChatMessage("user", "second")))

        assertEquals(listOf(ChatMessage("user", "second")), repo.load("model-a"))
    }

    @Test
    fun `save caps history at 100 messages per model`() = runBlocking {
        repo.save("model-a", (1..150).map { ChatMessage("user", "message $it") })

        val loaded = repo.load("model-a")
        assertEquals(100, loaded.size)
        // The most recent 100 messages are kept.
        assertEquals("message 51", loaded.first().content)
        assertEquals("message 150", loaded.last().content)
    }

    @Test
    fun `load returns empty list when nothing was saved`() = runBlocking {
        assertTrue(repo.load("model-a").isEmpty())
    }

    @Test
    fun `histories for different models do not interfere`() = runBlocking {
        repo.save("model-a", (1..120).map { ChatMessage("user", "a$it") })
        repo.save("model-b", listOf(ChatMessage("user", "b1")))

        assertEquals(100, repo.load("model-a").size)
        assertEquals(listOf(ChatMessage("user", "b1")), repo.load("model-b"))
    }
}
