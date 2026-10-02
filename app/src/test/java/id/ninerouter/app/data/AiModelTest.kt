package id.ninerouter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the combo-vs-provider model id distinction. */
class AiModelTest {

    @Test
    fun `plain id is a combo`() {
        assertTrue(AiModel("toptools-default", "top-tools-ai").isCombo)
    }

    @Test
    fun `provider qualified id is not a combo`() {
        assertFalse(AiModel("apmix/gpt-6-luna-free", null).isCombo)
    }
}
