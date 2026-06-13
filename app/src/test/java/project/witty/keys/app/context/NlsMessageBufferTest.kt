package project.witty.keys.app.context

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NlsMessageBufferTest {
    private lateinit var buffer: NlsMessageBuffer

    @Before
    fun setUp() {
        buffer = NlsMessageBuffer.getInstance()
        buffer.clearAll()
        buffer.setNowForTest(1_000L)
    }

    @After
    fun tearDown() {
        buffer.clearAll()
        buffer.setNowForTest(null)
    }

    @Test
    fun `stores received and sent messages in one two hour conversation`() {
        buffer.addReceivedMessage("com.whatsapp|Priya", "Priya", "Can you send it?")
        buffer.addSentMessage("com.whatsapp|Priya", "Yes, sending now.")

        val messages = buffer.getRecentConversationMessages("com.whatsapp|Priya", 10)

        assertEquals(2, messages.size)
        assertEquals("Priya: Can you send it?", messages[0].displayText)
        assertFalse(messages[0].isSent)
        assertEquals("You: Yes, sending now.", messages[1].displayText)
        assertTrue(messages[1].isSent)
    }

    @Test
    fun `repeated sent replies remain visible in quick reply history`() {
        buffer.addSentMessage("com.whatsapp|Priya", "Okay")
        buffer.addSentMessage("com.whatsapp|Priya", "Okay")

        val messages = buffer.getRecentConversationMessages("com.whatsapp|Priya", 10)

        assertEquals(2, messages.size)
        assertEquals("You: Okay", messages[0].displayText)
        assertEquals("You: Okay", messages[1].displayText)
        assertTrue(messages.all { it.isSent })
    }

    @Test
    fun `open conversations stay visible for two hours then expire`() {
        buffer.addReceivedMessage("com.whatsapp|Priya", "Priya", "Ping")
        buffer.addReceivedMessage("org.telegram.messenger|Rahul", "Rahul", "Dinner?")

        assertEquals(2, buffer.getOpenConversations().size)

        buffer.setNowForTest(1_000L + NlsMessageBuffer.OPEN_CONVERSATION_TTL_MS + 1L)

        assertTrue(buffer.getOpenConversations().isEmpty())
    }

    @Test
    fun sentMessageMakesLatestDirectionSentAndPreservesIncomingId() {
        buffer.addReceivedMessage("com.whatsapp|Priya", "Priya", "Can you send it?")
        buffer.setNowForTest(2_000L)
        buffer.addSentMessage("com.whatsapp|Priya", "Sending now")

        val snapshot = buffer.openConversation("com.whatsapp|Priya")

        assertEquals(true, snapshot?.latestMessageSentByUser)
        assertEquals(
            "com.whatsapp|Priya|1000|Priya|Can you send it?".hashCode().toString(),
            snapshot?.latestIncomingId
        )
    }
}
