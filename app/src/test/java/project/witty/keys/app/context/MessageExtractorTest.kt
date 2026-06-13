package project.witty.keys.app.context

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Method

/**
 * Unit tests for message extraction logic in ContextEngine.
 *
 * Tests cover:
 * - Message extraction from WhatsApp-like formats
 * - System message filtering
 * - Emoji handling in messages
 * - Recent message limiting
 * - UI label filtering
 * - Timestamp detection and filtering
 * - Sender extraction
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MessageExtractorTest {

    private lateinit var contextEngine: ContextEngine

    // Reflection access to private methods for testing
    private lateinit var isUILabelMethod: Method
    private lateinit var isTimestampMethod: Method
    private lateinit var isLikelyMessageMethod: Method
    private lateinit var truncateMethod: Method

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        Log.d("[Test]", "MessageExtractorTest setup complete")

        contextEngine = ContextEngine()

        // Get access to private methods via reflection
        isUILabelMethod = ContextEngine::class.java.getDeclaredMethod("isUILabel", String::class.java)
        isUILabelMethod.isAccessible = true

        isTimestampMethod = ContextEngine::class.java.getDeclaredMethod("isTimestamp", String::class.java)
        isTimestampMethod.isAccessible = true

        isLikelyMessageMethod = ContextEngine::class.java.getDeclaredMethod(
            "isLikelyMessage",
            String::class.java,
            AccessibilityNodeInfo::class.java
        )
        isLikelyMessageMethod.isAccessible = true

        truncateMethod = ContextEngine::class.java.getDeclaredMethod("truncate", String::class.java, Int::class.javaPrimitiveType)
        truncateMethod.isAccessible = true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // Helper functions for invoking private methods
    private fun isUILabel(text: String?): Boolean {
        return isUILabelMethod.invoke(contextEngine, text) as Boolean
    }

    private fun isTimestamp(text: String?): Boolean {
        return isTimestampMethod.invoke(contextEngine, text) as Boolean
    }

    private fun isLikelyMessage(text: String, node: AccessibilityNodeInfo): Boolean {
        return isLikelyMessageMethod.invoke(contextEngine, text, node) as Boolean
    }

    private fun truncate(text: String?, maxLen: Int): String {
        return truncateMethod.invoke(contextEngine, text, maxLen) as String
    }

    // ========================================
    // UI LABEL FILTERING TESTS
    // ========================================

    @Test
    fun `extractMessages filters Send button label`() {
        assertTrue(isUILabel("Send"))
        assertTrue(isUILabel("send"))
        assertTrue(isUILabel("SEND"))
    }

    @Test
    fun `extractMessages filters Back button label`() {
        assertTrue(isUILabel("Back"))
        assertTrue(isUILabel("back"))
    }

    @Test
    fun `extractMessages filters Cancel button label`() {
        assertTrue(isUILabel("Cancel"))
        assertTrue(isUILabel("cancel"))
    }

    @Test
    fun `extractMessages filters OK button label`() {
        assertTrue(isUILabel("Ok"))
        assertTrue(isUILabel("ok"))
        assertTrue(isUILabel("OK"))
    }

    @Test
    fun `extractMessages filters Yes and No labels`() {
        assertTrue(isUILabel("Yes"))
        assertTrue(isUILabel("No"))
        assertTrue(isUILabel("yes"))
        assertTrue(isUILabel("no"))
    }

    @Test
    fun `extractMessages filters input placeholder text`() {
        assertTrue(isUILabel("Type a message"))
        assertTrue(isUILabel("Write a message"))
        assertTrue(isUILabel("type a message"))
    }

    @Test
    fun `extractMessages filters search label`() {
        assertTrue(isUILabel("Search"))
        assertTrue(isUILabel("search"))
    }

    @Test
    fun `extractMessages filters settings label`() {
        assertTrue(isUILabel("Settings"))
        assertTrue(isUILabel("settings"))
    }

    @Test
    fun `extractMessages filters message status labels`() {
        assertTrue(isUILabel("Delivered"))
        assertTrue(isUILabel("Read"))
        assertTrue(isUILabel("Sent"))
        assertTrue(isUILabel("Sending"))
        assertTrue(isUILabel("Failed"))
    }

    @Test
    fun `extractMessages filters online status labels`() {
        assertTrue(isUILabel("Online"))
        assertTrue(isUILabel("Offline"))
        assertTrue(isUILabel("Typing..."))
        assertTrue(isUILabel("typing"))
    }

    @Test
    fun `extractMessages filters attachment labels`() {
        assertTrue(isUILabel("Photo"))
        assertTrue(isUILabel("Camera"))
        assertTrue(isUILabel("Gallery"))
        assertTrue(isUILabel("Video"))
        assertTrue(isUILabel("Document"))
        assertTrue(isUILabel("Location"))
        assertTrue(isUILabel("Contact"))
    }

    @Test
    fun `extractMessages filters emoji and sticker labels`() {
        assertTrue(isUILabel("Emoji"))
        assertTrue(isUILabel("Sticker"))
        assertTrue(isUILabel("GIF"))
    }

    @Test
    fun `extractMessages filters encryption notice`() {
        assertTrue(isUILabel("End-to-end encrypted"))
        assertTrue(isUILabel("Encrypted"))
    }

    @Test
    fun `extractMessages does not filter actual messages`() {
        assertFalse(isUILabel("Hey, how are you?"))
        assertFalse(isUILabel("Let's meet tomorrow"))
        assertFalse(isUILabel("I'll send you the document"))
    }

    @Test
    fun `extractMessages handles null text as UI label`() {
        assertTrue(isUILabel(null))
    }

    // ========================================
    // TIMESTAMP FILTERING TESTS
    // ========================================

    @Test
    fun `extractMessages filters simple time format`() {
        assertTrue(isTimestamp("10:30"))
        assertTrue(isTimestamp("2:45"))
        assertTrue(isTimestamp("12:00"))
    }

    @Test
    fun `extractMessages filters time with AM PM`() {
        assertTrue(isTimestamp("10:30 am"))
        assertTrue(isTimestamp("2:45 PM"))
        assertTrue(isTimestamp("12:00 AM"))
        assertTrue(isTimestamp("9:15am"))
        assertTrue(isTimestamp("11:59pm"))
    }

    @Test
    fun `extractMessages filters time with seconds`() {
        assertTrue(isTimestamp("10:30:45"))
        assertTrue(isTimestamp("2:45:00"))
    }

    @Test
    fun `extractMessages filters Today Yesterday labels`() {
        assertTrue(isTimestamp("Today"))
        assertTrue(isTimestamp("today"))
        assertTrue(isTimestamp("Yesterday"))
        assertTrue(isTimestamp("yesterday"))
    }

    @Test
    fun `extractMessages filters date formats`() {
        assertTrue(isTimestamp("1/15"))
        assertTrue(isTimestamp("12/25"))
        assertTrue(isTimestamp("1/15/24"))
        assertTrue(isTimestamp("12/25/2024"))
    }

    @Test
    fun `extractMessages filters short date formats`() {
        assertTrue(isTimestamp("Jan 15"))
        assertTrue(isTimestamp("Dec 25"))
        assertTrue(isTimestamp("Mar 1"))
    }

    @Test
    fun `extractMessages does not filter messages that look like time`() {
        assertFalse(isTimestamp("Meeting at 10:30"))
        assertFalse(isTimestamp("I'll be there by 2 pm"))
        assertFalse(isTimestamp("The score is 10:30"))
    }

    @Test
    fun `extractMessages handles null as not timestamp`() {
        assertFalse(isTimestamp(null))
    }

    // ========================================
    // MESSAGE IDENTIFICATION TESTS
    // ========================================

    @Test
    fun `isLikelyMessage accepts valid message text`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("Hey, how are you doing today?", mockNode))
    }

    @Test
    fun `isLikelyMessage rejects too short text`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertFalse(isLikelyMessage("A", mockNode))
        assertFalse(isLikelyMessage("", mockNode))
    }

    @Test
    fun `isLikelyMessage rejects too long text`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        val longText = "A".repeat(501)
        assertFalse(isLikelyMessage(longText, mockNode))
    }

    @Test
    fun `isLikelyMessage rejects button nodes`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.Button"

        assertFalse(isLikelyMessage("Send Message", mockNode))
    }

    @Test
    fun `isLikelyMessage rejects ImageView nodes`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.ImageView"

        assertFalse(isLikelyMessage("Image description", mockNode))
    }

    @Test
    fun `isLikelyMessage rejects CheckBox nodes`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.CheckBox"

        assertFalse(isLikelyMessage("Accept terms", mockNode))
    }

    @Test
    fun `isLikelyMessage rejects UI label text`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertFalse(isLikelyMessage("Send", mockNode))
        assertFalse(isLikelyMessage("Cancel", mockNode))
        assertFalse(isLikelyMessage("Type a message", mockNode))
    }

    @Test
    fun `isLikelyMessage rejects timestamp text`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertFalse(isLikelyMessage("10:30 AM", mockNode))
        assertFalse(isLikelyMessage("Yesterday", mockNode))
    }

    @Test
    fun `isLikelyMessage accepts emoji messages`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("That's amazing! \ud83d\ude04\ud83d\udc4d", mockNode))
        assertTrue(isLikelyMessage("\ud83d\ude02\ud83d\ude02\ud83d\ude02 So funny!", mockNode))
    }

    @Test
    fun `isLikelyMessage accepts Hinglish messages`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("Kya haal hai bro?", mockNode))
        assertTrue(isLikelyMessage("Party tonight chaloge?", mockNode))
        assertTrue(isLikelyMessage("Bahut accha hai yaar!", mockNode))
    }

    @Test
    fun `isLikelyMessage accepts messages at boundary lengths`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        // Minimum valid length (2 characters)
        assertTrue(isLikelyMessage("Hi", mockNode))

        // Maximum valid length (500 characters)
        assertTrue(isLikelyMessage("A".repeat(500), mockNode))
    }

    // ========================================
    // EMOJI HANDLING TESTS
    // ========================================

    @Test
    fun `message with single emoji is valid`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        // Single emoji is 2 chars (surrogate pair) and meets MIN_MESSAGE_LENGTH
        // The implementation accepts it as a valid message
        assertTrue(isLikelyMessage("\ud83d\ude04", mockNode))
    }

    @Test
    fun `message with multiple emojis is valid`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("\ud83d\ude04\ud83d\ude04\ud83d\ude04", mockNode))
    }

    @Test
    fun `message with text and emojis is valid`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("Great job! \ud83c\udf89\ud83c\udf8a", mockNode))
        assertTrue(isLikelyMessage("\ud83d\udc4b Hello there!", mockNode))
    }

    @Test
    fun `message with emoji between text is valid`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("I'm \ud83d\ude0d loving \ud83d\ude0d it", mockNode))
    }

    // ========================================
    // RECENT MESSAGE LIMITING TESTS
    // ========================================

    @Test
    fun `getLastMessage returns most recent non-user message`() {
        val messages = listOf(
            ChatMessage("Alice", "First message", null, false),
            ChatMessage("Me", "My response", null, true),
            ChatMessage("Alice", "Second message", null, false),
            ChatMessage("Me", "Another response", null, true),
            ChatMessage("Alice", "Third message", null, false)
        )
        val chat = Chat("WhatsApp", listOf("Alice"), messages)

        // Simulate caching for ContextEngine
        val cacheMethod = ContextEngine::class.java.getDeclaredMethod(
            "cacheContext",
            ScreenContext::class.java,
            String::class.java
        )
        cacheMethod.isAccessible = true
        cacheMethod.invoke(contextEngine, chat, "com.whatsapp")

        val lastMessage = contextEngine.getLastMessage()

        assertEquals("Third message", lastMessage)
    }

    @Test
    fun `getLastMessage when all from user returns last message`() {
        val messages = listOf(
            ChatMessage("Me", "First", null, true),
            ChatMessage("Me", "Second", null, true),
            ChatMessage("Me", "Third", null, true)
        )
        val chat = Chat("WhatsApp", listOf(), messages)

        val cacheMethod = ContextEngine::class.java.getDeclaredMethod(
            "cacheContext",
            ScreenContext::class.java,
            String::class.java
        )
        cacheMethod.isAccessible = true
        cacheMethod.invoke(contextEngine, chat, "com.whatsapp")

        val lastMessage = contextEngine.getLastMessage()

        assertEquals("Third", lastMessage)
    }

    @Test
    fun `getLastMessage with empty messages returns null`() {
        val chat = Chat("WhatsApp", listOf("Alice"), emptyList())

        val cacheMethod = ContextEngine::class.java.getDeclaredMethod(
            "cacheContext",
            ScreenContext::class.java,
            String::class.java
        )
        cacheMethod.isAccessible = true
        cacheMethod.invoke(contextEngine, chat, "com.whatsapp")

        val lastMessage = contextEngine.getLastMessage()

        assertNull(lastMessage)
    }

    @Test
    fun `getSenderName returns first participant`() {
        val chat = Chat("WhatsApp", listOf("John", "Jane"), emptyList())

        val cacheMethod = ContextEngine::class.java.getDeclaredMethod(
            "cacheContext",
            ScreenContext::class.java,
            String::class.java
        )
        cacheMethod.isAccessible = true
        cacheMethod.invoke(contextEngine, chat, "com.whatsapp")

        val senderName = contextEngine.senderName

        assertEquals("John", senderName)
    }

    @Test
    fun `getSenderName with no participants returns null`() {
        val chat = Chat("WhatsApp", emptyList(), emptyList())

        val cacheMethod = ContextEngine::class.java.getDeclaredMethod(
            "cacheContext",
            ScreenContext::class.java,
            String::class.java
        )
        cacheMethod.isAccessible = true
        cacheMethod.invoke(contextEngine, chat, "com.whatsapp")

        val senderName = contextEngine.senderName

        assertNull(senderName)
    }

    @Test
    fun `getSenderName for Email returns from field`() {
        val email = Email("Gmail", "sender@example.com", listOf(), "Subject", "Body")

        val cacheMethod = ContextEngine::class.java.getDeclaredMethod(
            "cacheContext",
            ScreenContext::class.java,
            String::class.java
        )
        cacheMethod.isAccessible = true
        cacheMethod.invoke(contextEngine, email, "com.google.android.gm")

        val senderName = contextEngine.senderName

        assertEquals("sender@example.com", senderName)
    }

    // ========================================
    // CACHE TESTS
    // ========================================

    @Test
    fun `cache is invalid when no context cached`() {
        contextEngine.invalidateCache()

        assertFalse(contextEngine.isCacheValid)
    }

    @Test
    fun `cache invalidation clears cached context`() {
        val chat = Chat("WhatsApp", listOf("Alice"), emptyList())

        val cacheMethod = ContextEngine::class.java.getDeclaredMethod(
            "cacheContext",
            ScreenContext::class.java,
            String::class.java
        )
        cacheMethod.isAccessible = true
        cacheMethod.invoke(contextEngine, chat, "com.whatsapp")

        contextEngine.invalidateCache()

        assertNull(contextEngine.cachedContext)
        assertFalse(contextEngine.isCacheValid)
    }

    @Test
    fun `getCachedContext returns cached Chat`() {
        val chat = Chat("WhatsApp", listOf("Alice"), listOf(
            ChatMessage("Alice", "Hello", null, false)
        ))

        val cacheMethod = ContextEngine::class.java.getDeclaredMethod(
            "cacheContext",
            ScreenContext::class.java,
            String::class.java
        )
        cacheMethod.isAccessible = true
        cacheMethod.invoke(contextEngine, chat, "com.whatsapp")

        val cached = contextEngine.cachedContext

        assertNotNull(cached)
        assertTrue(cached is Chat)
        assertEquals("WhatsApp", cached?.appName)
    }

    // ========================================
    // TRUNCATE HELPER TESTS
    // ========================================

    @Test
    fun `truncate with short text returns unchanged`() {
        val result = truncate("Hello", 10)
        assertEquals("Hello", result)
    }

    @Test
    fun `truncate with exact length returns unchanged`() {
        val result = truncate("HelloWorld", 10)
        assertEquals("HelloWorld", result)
    }

    @Test
    fun `truncate with long text adds ellipsis`() {
        // ContextEngine.truncate returns first maxLen chars + "..."
        val result = truncate("Hello World Long Text", 10)
        assertEquals("Hello Worl...", result)
    }

    @Test
    fun `truncate with null returns null string`() {
        val result = truncate(null, 10)
        assertEquals("null", result)
    }

    // ========================================
    // WHATSAPP FORMAT PARSING TESTS
    // ========================================

    @Test
    fun `WhatsApp message format is recognized`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"
        every { mockNode.contentDescription } returns null

        // Standard WhatsApp message
        assertTrue(isLikelyMessage("Hey, are you coming to the party?", mockNode))
    }

    @Test
    fun `WhatsApp voice message label is filtered`() {
        assertTrue(isUILabel("Voice message"))
    }

    @Test
    fun `WhatsApp recording label is filtered`() {
        assertTrue(isUILabel("Record"))
        assertTrue(isUILabel("Mic"))
    }

    @Test
    fun `WhatsApp media label is filtered`() {
        assertTrue(isUILabel("Media"))
    }

    @Test
    fun `WhatsApp last seen is filtered`() {
        assertTrue(isUILabel("Last seen"))
    }

    // ========================================
    // SYSTEM MESSAGE FILTERING TESTS
    // ========================================

    @Test
    fun `system message encryption notice is filtered`() {
        assertTrue(isUILabel("End-to-end encrypted"))
        assertTrue(isUILabel("Tap for more info"))
    }

    @Test
    fun `system message view contact is filtered`() {
        assertTrue(isUILabel("View contact"))
    }

    @Test
    fun `system message search in chat is filtered`() {
        assertTrue(isUILabel("Search in chat"))
    }

    @Test
    fun `system message starred is filtered`() {
        assertTrue(isUILabel("Starred"))
    }

    @Test
    fun `system message links is filtered`() {
        assertTrue(isUILabel("Links"))
    }

    @Test
    fun `system message docs is filtered`() {
        assertTrue(isUILabel("Docs"))
    }

    // ========================================
    // SPECIAL CHARACTER HANDLING TESTS
    // ========================================

    @Test
    fun `message with newlines is valid`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("Line 1\nLine 2\nLine 3", mockNode))
    }

    @Test
    fun `message with tabs is valid`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("Column1\tColumn2\tColumn3", mockNode))
    }

    @Test
    fun `message with special characters is valid`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("Price is \$50 (50% off!)", mockNode))
        assertTrue(isLikelyMessage("Email: test@example.com", mockNode))
        assertTrue(isLikelyMessage("URL: https://example.com/path?query=1", mockNode))
    }

    @Test
    fun `message with Unicode characters is valid`() {
        val mockNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockNode.className } returns "android.widget.TextView"

        assertTrue(isLikelyMessage("\u0928\u092e\u0938\u094d\u0924\u0947! How are you?", mockNode)) // Hindi
        assertTrue(isLikelyMessage("\u4f60\u597d! Nice to meet you!", mockNode)) // Chinese
        assertTrue(isLikelyMessage("Caf\u00e9 Fran\u00e7ais", mockNode)) // French
    }
}
