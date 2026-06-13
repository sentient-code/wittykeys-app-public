package project.witty.keys.app.context

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for MessageDebouncer.
 * Uses Robolectric for Handler/Looper support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MessageDebouncerTest {

    private lateinit var debouncer: MessageDebouncer
    private val deliveredBatches = mutableListOf<Triple<String, String, List<MessageDebouncer.NlsMessage>>>()

    @Before
    fun setup() {
        debouncer = MessageDebouncer()
        deliveredBatches.clear()

        debouncer.setListener(object : MessageDebouncer.BatchListener {
            override fun onMessageBatchReady(
                packageName: String,
                contactName: String,
                messages: MutableList<MessageDebouncer.NlsMessage>
            ) {
                deliveredBatches.add(Triple(packageName, contactName, messages.toList()))
            }
        })
    }

    // === Batch Delivery ===

    @Test
    fun `single message delivered after quiet window`() {
        val msg = MessageDebouncer.NlsMessage("Priya", "Hey!", System.currentTimeMillis(), false)
        debouncer.addMessage("com.whatsapp", "Priya", msg)

        // Advance past quiet window (2s) + buffer
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS)

        assertEquals(1, deliveredBatches.size)
        assertEquals("Priya", deliveredBatches[0].second)
        assertEquals(1, deliveredBatches[0].third.size)
    }

    @Test
    fun `multiple messages batched within quiet window`() {
        for (i in 1..3) {
            val msg = MessageDebouncer.NlsMessage("Priya", "msg$i", System.currentTimeMillis(), false)
            debouncer.addMessage("com.whatsapp", "Priya", msg)
            ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        }

        // Advance past quiet window after last message
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS)

        assertEquals(1, deliveredBatches.size)
        assertEquals(3, deliveredBatches[0].third.size)
    }

    @Test
    fun `max wait forces flush at 8 seconds`() {
        // Add messages every 1.5s (quiet window resets each time)
        for (i in 1..7) {
            val msg = MessageDebouncer.NlsMessage("Priya", "msg$i", System.currentTimeMillis(), false)
            debouncer.addMessage("com.whatsapp", "Priya", msg)
            ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS)
        }

        // Should have flushed by now (max wait 8s exceeded)
        assertTrue("Batch should have been delivered within max wait window",
            deliveredBatches.isNotEmpty())
    }

    // === Conversation Switching ===

    @Test
    fun `conversation switch flushes previous batch immediately`() {
        val msg1 = MessageDebouncer.NlsMessage("Priya", "Hey!", System.currentTimeMillis(), false)
        debouncer.addMessage("com.whatsapp", "Priya", msg1)

        // Switch to different contact immediately
        val msg2 = MessageDebouncer.NlsMessage("Boss", "Hi", System.currentTimeMillis(), false)
        debouncer.addMessage("com.whatsapp", "Boss", msg2)

        // Priya's batch should be flushed immediately on switch
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        assertTrue("Previous batch should be flushed on conversation switch",
            deliveredBatches.any { it.second == "Priya" })
    }

    // === Listener ===

    @Test
    fun `no crash if listener is null`() {
        debouncer.setListener(null)
        val msg = MessageDebouncer.NlsMessage("Priya", "Hey!", System.currentTimeMillis(), false)
        debouncer.addMessage("com.whatsapp", "Priya", msg)
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS)
        // Should not crash — just silently drop
    }

    // === NlsMessage Construction ===

    @Test
    fun `NlsMessage preserves fields`() {
        val now = System.currentTimeMillis()
        val msg = MessageDebouncer.NlsMessage("Priya", "Hey there!", now, true)
        assertEquals("Priya", msg.sender)
        assertEquals("Hey there!", msg.text)
        assertEquals(now, msg.timestamp)
        assertTrue(msg.isGroup)
    }
}
