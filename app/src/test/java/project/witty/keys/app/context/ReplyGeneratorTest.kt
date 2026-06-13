package project.witty.keys.app.context

import android.util.Log
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import project.witty.keys.api.ClaudeApi
import java.lang.reflect.Method

/**
 * Unit tests for ReplyGenerator - smart reply generation logic.
 *
 * Tests cover:
 * - Reply generation for Chat context
 * - App categorization for prompt building
 * - Error handling for null contexts
 * - Last incoming message extraction
 * - Recent context building
 * - Truncation logic
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReplyGeneratorTest {

    private lateinit var replyGenerator: ReplyGenerator
    private lateinit var mockClaudeApi: ClaudeApi

    // Reflection access to private methods
    private lateinit var categorizeAppMethod: Method
    private lateinit var getLastIncomingMessageMethod: Method
    private lateinit var getRecentContextMethod: Method
    private lateinit var truncateMethod: Method
    private lateinit var buildUserPromptMethod: Method

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        Log.d("[Test]", "ReplyGeneratorTest setup complete")

        mockClaudeApi = mockk(relaxed = true)
        replyGenerator = ReplyGenerator()

        // Inject mock via reflection
        val apiField = ReplyGenerator::class.java.getDeclaredField("claudeApi")
        apiField.isAccessible = true
        apiField.set(replyGenerator, mockClaudeApi)

        // Get access to private methods via reflection
        categorizeAppMethod = ReplyGenerator::class.java.getDeclaredMethod("categorizeApp", String::class.java)
        categorizeAppMethod.isAccessible = true

        getLastIncomingMessageMethod = ReplyGenerator::class.java.getDeclaredMethod(
            "getLastIncomingMessage",
            List::class.java
        )
        getLastIncomingMessageMethod.isAccessible = true

        getRecentContextMethod = ReplyGenerator::class.java.getDeclaredMethod(
            "getRecentContext",
            List::class.java
        )
        getRecentContextMethod.isAccessible = true

        truncateMethod = ReplyGenerator::class.java.getDeclaredMethod(
            "truncate",
            String::class.java,
            Int::class.javaPrimitiveType
        )
        truncateMethod.isAccessible = true

        buildUserPromptMethod = ReplyGenerator::class.java.getDeclaredMethod(
            "buildUserPrompt",
            Chat::class.java
        )
        buildUserPromptMethod.isAccessible = true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // Helper functions for invoking private methods
    private fun categorizeApp(appName: String?): String {
        return categorizeAppMethod.invoke(replyGenerator, appName) as String
    }

    @Suppress("UNCHECKED_CAST")
    private fun getLastIncomingMessage(messages: List<ChatMessage>): ChatMessage? {
        return getLastIncomingMessageMethod.invoke(replyGenerator, messages) as? ChatMessage
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRecentContext(messages: List<ChatMessage>): String {
        return getRecentContextMethod.invoke(replyGenerator, messages) as String
    }

    private fun truncate(text: String?, maxLength: Int): String {
        return truncateMethod.invoke(replyGenerator, text, maxLength) as String
    }

    private fun buildUserPrompt(chat: Chat): String {
        return buildUserPromptMethod.invoke(replyGenerator, chat) as String
    }

    // ========================================
    // APP CATEGORIZATION TESTS
    // ========================================

    @Test
    fun `categorizeApp with WhatsApp returns messaging`() {
        assertEquals("messaging", categorizeApp("WhatsApp"))
    }

    @Test
    fun `categorizeApp with Telegram returns messaging`() {
        assertEquals("messaging", categorizeApp("Telegram"))
    }

    @Test
    fun `categorizeApp with Messenger returns messaging`() {
        assertEquals("messaging", categorizeApp("Messenger"))
    }

    @Test
    fun `categorizeApp with Signal returns messaging`() {
        assertEquals("messaging", categorizeApp("Signal"))
    }

    @Test
    fun `categorizeApp with Messages returns messaging`() {
        assertEquals("messaging", categorizeApp("Messages"))
    }

    @Test
    fun `categorizeApp with SMS returns messaging`() {
        assertEquals("messaging", categorizeApp("SMS App"))
    }

    @Test
    fun `categorizeApp with Gmail returns email`() {
        assertEquals("email", categorizeApp("Gmail"))
    }

    @Test
    fun `categorizeApp with Outlook returns email`() {
        assertEquals("email", categorizeApp("Outlook"))
    }

    @Test
    fun `categorizeApp with Yahoo Mail returns email`() {
        assertEquals("email", categorizeApp("Yahoo Mail"))
    }

    @Test
    fun `categorizeApp with generic mail returns email`() {
        assertEquals("email", categorizeApp("ProtonMail"))
    }

    @Test
    fun `categorizeApp with Tinder returns dating`() {
        assertEquals("dating", categorizeApp("Tinder"))
    }

    @Test
    fun `categorizeApp with Bumble returns dating`() {
        assertEquals("dating", categorizeApp("Bumble"))
    }

    @Test
    fun `categorizeApp with Hinge returns dating`() {
        assertEquals("dating", categorizeApp("Hinge"))
    }

    @Test
    fun `categorizeApp with OkCupid returns dating`() {
        assertEquals("dating", categorizeApp("OkCupid"))
    }

    @Test
    fun `categorizeApp with LinkedIn returns social`() {
        assertEquals("social", categorizeApp("LinkedIn"))
    }

    @Test
    fun `categorizeApp with Twitter returns social`() {
        assertEquals("social", categorizeApp("Twitter"))
    }

    @Test
    fun `categorizeApp with Instagram returns social`() {
        assertEquals("social", categorizeApp("Instagram"))
    }

    @Test
    fun `categorizeApp with Facebook returns social`() {
        assertEquals("social", categorizeApp("Facebook"))
    }

    @Test
    fun `categorizeApp with Slack returns social`() {
        assertEquals("social", categorizeApp("Slack"))
    }

    @Test
    fun `categorizeApp with Discord returns social`() {
        assertEquals("social", categorizeApp("Discord"))
    }

    @Test
    fun `categorizeApp with unknown app returns other`() {
        assertEquals("other", categorizeApp("Random App"))
    }

    @Test
    fun `categorizeApp with null returns other`() {
        assertEquals("other", categorizeApp(null))
    }

    // ========================================
    // LAST INCOMING MESSAGE TESTS
    // ========================================

    @Test
    fun `getLastIncomingMessage returns last non-user message`() {
        val messages = listOf(
            ChatMessage("Alice", "Hello", null, false),
            ChatMessage("Me", "Hi", null, true),
            ChatMessage("Alice", "How are you?", null, false)
        )

        val result = getLastIncomingMessage(messages)

        assertNotNull(result)
        assertEquals("How are you?", result?.text)
        assertFalse(result?.isFromCurrentUser ?: true)
    }

    @Test
    fun `getLastIncomingMessage when all from user returns last message`() {
        val messages = listOf(
            ChatMessage("Me", "First", null, true),
            ChatMessage("Me", "Second", null, true),
            ChatMessage("Me", "Third", null, true)
        )

        val result = getLastIncomingMessage(messages)

        assertNotNull(result)
        assertEquals("Third", result?.text)
    }

    @Test
    fun `getLastIncomingMessage with single incoming message returns it`() {
        val messages = listOf(
            ChatMessage("Bob", "Hey there!", null, false)
        )

        val result = getLastIncomingMessage(messages)

        assertNotNull(result)
        assertEquals("Hey there!", result?.text)
    }

    @Test
    fun `getLastIncomingMessage with empty list returns null`() {
        val result = getLastIncomingMessage(emptyList())

        assertNull(result)
    }

    @Test
    fun `getLastIncomingMessage with alternating messages returns correct one`() {
        val messages = listOf(
            ChatMessage("Alice", "Msg 1", null, false),
            ChatMessage("Me", "Reply 1", null, true),
            ChatMessage("Alice", "Msg 2", null, false),
            ChatMessage("Me", "Reply 2", null, true),
            ChatMessage("Alice", "Msg 3", null, false)
        )

        val result = getLastIncomingMessage(messages)

        assertEquals("Msg 3", result?.text)
    }

    // ========================================
    // RECENT CONTEXT TESTS
    // ========================================

    @Test
    fun `getRecentContext with multiple messages returns formatted context`() {
        val messages = listOf(
            ChatMessage("Alice", "Hello", null, false),
            ChatMessage("Me", "Hi there", null, true),
            ChatMessage("Alice", "How are you?", null, false)
        )

        val result = getRecentContext(messages)

        assertTrue(result.contains("Alice: Hello"))
        assertTrue(result.contains("You: Hi there"))
    }

    @Test
    fun `getRecentContext preserves up to last twenty messages excluding latest`() {
        val messages = (1..22).map { index ->
            ChatMessage(if (index % 2 == 0) "Me" else "Alice", "Message $index", null, index % 2 == 0)
        }

        val result = getRecentContext(messages)
        val lines = result.lines()

        assertFalse(lines.any { it.endsWith("Message 1") })
        assertTrue(lines.any { it.endsWith("Message 2") })
        assertTrue(lines.any { it.endsWith("Message 3") })
        assertTrue(lines.any { it.endsWith("Message 21") })
        assertFalse(lines.any { it.endsWith("Message 22") })
    }

    @Test
    fun `getRecentContext with single message returns empty`() {
        val messages = listOf(
            ChatMessage("Alice", "Only message", null, false)
        )

        val result = getRecentContext(messages)

        assertEquals("", result)
    }

    @Test
    fun `getRecentContext with empty list returns empty`() {
        val result = getRecentContext(emptyList())

        assertEquals("", result)
    }

    @Test
    fun `getRecentContext uses newline separator`() {
        val messages = listOf(
            ChatMessage("Alice", "First", null, false),
            ChatMessage("Me", "Second", null, true),
            ChatMessage("Alice", "Third", null, false)
        )

        val result = getRecentContext(messages)

        assertTrue(result.contains("\n"))
    }

    @Test
    fun `getRecentContext prefixes user messages with You`() {
        val messages = listOf(
            ChatMessage("Me", "My message", null, true),
            ChatMessage("Alice", "Their reply", null, false)
        )

        val result = getRecentContext(messages)

        assertTrue(result.contains("You: My message"))
    }

    @Test
    fun `recentContextPreservesDirectionAndDoesNotThirtyCharacterTruncate`() {
        val messages = listOf(
            ChatMessage("Priya", "Can you review the proposal I sent yesterday with all pricing details?", "1", false),
            ChatMessage("You", "Yes, I will review pricing and timeline together.", "2", true),
            ChatMessage("Priya", "Need final answer before the 6 pm client call.", "3", false)
        )
        val prompt = buildUserPrompt(Chat("com.whatsapp", listOf("Priya"), messages))

        assertTrue(prompt.contains("Priya: Can you review the proposal I sent yesterday with all pricing details?"))
        assertTrue(prompt.contains("You: Yes, I will review pricing and timeline together."))
        assertFalse(prompt.contains("Can you review the proposal..."))
    }

    // ========================================
    // TRUNCATE TESTS
    // ========================================

    @Test
    fun `truncate short text returns unchanged`() {
        assertEquals("Hello", truncate("Hello", 30))
    }

    @Test
    fun `truncate long text adds ellipsis`() {
        val result = truncate("This is a very long message that should be truncated", 20)

        assertTrue(result.endsWith("..."))
        assertTrue(result.length <= 20)
    }

    @Test
    fun `truncate exact length returns unchanged`() {
        assertEquals("Hello World!", truncate("Hello World!", 12))
    }

    @Test
    fun `truncate null returns empty string`() {
        assertEquals("", truncate(null, 10))
    }

    @Test
    fun `truncate empty string returns empty`() {
        assertEquals("", truncate("", 10))
    }

    // ========================================
    // BUILD USER PROMPT TESTS
    // ========================================

    @Test
    fun `buildUserPrompt includes app category`() {
        val chat = Chat("WhatsApp", listOf("Alice"), listOf(
            ChatMessage("Alice", "Hello", null, false)
        ))

        val prompt = buildUserPrompt(chat)

        assertTrue(prompt.contains("App: messaging"))
    }

    @Test
    fun `buildUserPrompt includes sender name`() {
        val chat = Chat("WhatsApp", listOf("John Doe"), listOf(
            ChatMessage("John Doe", "Hey!", null, false)
        ))

        val prompt = buildUserPrompt(chat)

        assertTrue(prompt.contains("Sender: John Doe"))
    }

    @Test
    fun `buildUserPrompt includes last message`() {
        val chat = Chat("WhatsApp", listOf("Alice"), listOf(
            ChatMessage("Alice", "Party tonight?", null, false)
        ))

        val prompt = buildUserPrompt(chat)

        assertTrue(prompt.contains("Party tonight?"))
    }

    @Test
    fun `buildUserPrompt with no participants shows Unknown sender`() {
        val chat = Chat("WhatsApp", emptyList(), listOf(
            ChatMessage("Unknown", "Hello", null, false)
        ))

        val prompt = buildUserPrompt(chat)

        assertTrue(prompt.contains("Sender: Unknown"))
    }

    @Test
    fun `buildUserPrompt ends with generate instruction`() {
        val chat = Chat("WhatsApp", listOf("Alice"), listOf(
            ChatMessage("Alice", "Hello", null, false)
        ))

        val prompt = buildUserPrompt(chat)

        assertTrue(prompt.contains("Generate 8 reply options"))
    }

    // ========================================
    // GENERATE REPLIES ERROR HANDLING TESTS
    // ========================================

    @Test
    fun `generateReplies with null Chat calls onError`() {
        var errorCalled = false
        var errorMessage = ""

        replyGenerator.generateReplies(null as Chat?, object : ReplyGenerator.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                fail("Should not succeed")
            }

            override fun onError(error: String) {
                errorCalled = true
                errorMessage = error
            }
        })

        assertTrue(errorCalled)
        assertEquals("No conversation context available", errorMessage)
    }

    @Test
    fun `generateReplies with null ScreenContext calls onError`() {
        var errorCalled = false
        var errorMessage = ""

        replyGenerator.generateReplies(null as ScreenContext?, object : ReplyGenerator.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                fail("Should not succeed")
            }

            override fun onError(error: String) {
                errorCalled = true
                errorMessage = error
            }
        })

        assertTrue(errorCalled)
        assertEquals("No context available", errorMessage)
    }

    @Test
    fun `generateReplies with Generic context calls onError`() {
        var errorCalled = false
        var errorMessage = ""

        val generic = Generic("Chrome", "Web content")

        replyGenerator.generateReplies(generic, object : ReplyGenerator.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                fail("Should not succeed with Generic context")
            }

            override fun onError(error: String) {
                errorCalled = true
                errorMessage = error
            }
        })

        assertTrue(errorCalled)
        assertEquals("Unsupported context type", errorMessage)
    }

    @Test
    fun `generateReplies with Chat context invokes ClaudeApi`() {
        val chat = Chat("WhatsApp", listOf("Alice"), listOf(
            ChatMessage("Alice", "Hey there!", null, false)
        ))

        replyGenerator.generateReplies(chat, object : ReplyGenerator.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                // Success
            }

            override fun onError(error: String) {
                // Error
            }
        })

        verify {
            mockClaudeApi.generateReplies(any(), any(), any())
        }
    }

    @Test
    fun `generateReplies with Email context calls onError`() {
        var errorCalled = false

        val email = Email("Gmail", "sender@test.com", listOf(), "Subject", "Body")

        replyGenerator.generateReplies(email, object : ReplyGenerator.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                fail("Should not succeed with Email context")
            }

            override fun onError(error: String) {
                errorCalled = true
            }
        })

        assertTrue(errorCalled)
    }

    // ========================================
    // INTEGRATION TESTS
    // ========================================

    @Test
    fun `full flow with Chat context builds correct prompt and invokes API`() {
        val messages = listOf(
            ChatMessage("Alice", "Hey!", null, false),
            ChatMessage("Me", "Hi Alice!", null, true),
            ChatMessage("Alice", "Party tonight?", null, false)
        )
        val chat = Chat("WhatsApp", listOf("Alice"), messages)

        var capturedSystemPrompt: String? = null
        var capturedUserPrompt: String? = null

        every {
            mockClaudeApi.generateReplies(any(), any(), any())
        } answers {
            capturedSystemPrompt = firstArg()
            capturedUserPrompt = secondArg()
        }

        replyGenerator.generateReplies(chat, object : ReplyGenerator.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {}
            override fun onError(error: String) {}
        })

        assertNotNull(capturedSystemPrompt)
        assertNotNull(capturedUserPrompt)

        // Verify system prompt contains key instructions
        assertTrue(capturedSystemPrompt!!.contains("generates 8 contextually perfect replies"))
        assertTrue(capturedSystemPrompt!!.contains("Match reply length to incoming message"))

        // Verify user prompt contains context
        assertTrue(capturedUserPrompt!!.contains("App: messaging"))
        assertTrue(capturedUserPrompt!!.contains("Sender: Alice"))
        assertTrue(capturedUserPrompt!!.contains("Party tonight?"))
    }

    @Test
    fun `Hinglish context is passed correctly`() {
        val messages = listOf(
            ChatMessage("Rahul", "Kya haal hai bro?", null, false)
        )
        val chat = Chat("WhatsApp", listOf("Rahul"), messages)

        var capturedUserPrompt: String? = null

        every {
            mockClaudeApi.generateReplies(any(), any(), any())
        } answers {
            capturedUserPrompt = secondArg()
        }

        replyGenerator.generateReplies(chat, object : ReplyGenerator.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {}
            override fun onError(error: String) {}
        })

        assertNotNull(capturedUserPrompt)
        assertTrue(capturedUserPrompt!!.contains("Kya haal hai bro?"))
    }
}
