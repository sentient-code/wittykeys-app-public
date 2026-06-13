package project.witty.keys.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationReplyStateTest {
    @Test
    fun latestSentMessageSuppressesSuggestions() {
        val messages = listOf(
            ConversationReplyState.Message("Priya", "Can you send it?", false, "in-1"),
            ConversationReplyState.Message("You", "Sending now", true, "out-1")
        )

        val state = ConversationReplyState.fromMessages(
            "com.whatsapp|Priya",
            "com.whatsapp",
            "Priya",
            messages,
            listOf("Yes"),
            "in-1"
        )

        assertFalse(state.canShowSuggestions)
        assertEquals(ConversationReplyState.BlockedReason.LAST_MESSAGE_IS_USER, state.blockedReason)
        assertEquals("Sent. Waiting for their reply.", state.statusMessage)
    }

    @Test
    fun latestIncomingMessageAllowsSuggestionsForMatchingIncomingId() {
        val messages = listOf(
            ConversationReplyState.Message("You", "I can help", true, "out-1"),
            ConversationReplyState.Message("Priya", "Can you send it before 6?", false, "in-2")
        )

        val state = ConversationReplyState.fromMessages(
            "com.whatsapp|Priya",
            "com.whatsapp",
            "Priya",
            messages,
            listOf("Yes, before 6."),
            "in-2"
        )

        assertTrue(state.canShowSuggestions)
        assertEquals(ConversationReplyState.BlockedReason.NONE, state.blockedReason)
        assertEquals("in-2", state.latestIncomingId)
    }
}
