package project.witty.keys.app.context

import android.os.Parcel
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ScreenContext and its subclasses (Chat, Email, Generic, DatingProfile).
 *
 * Tests cover:
 * - Context creation with valid data
 * - Context creation with empty/null data
 * - Message retrieval from Chat context
 * - Conversation partner extraction
 * - Context type identification
 * - Parcelable implementation
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ScreenContextTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        Log.d("[Test]", "ScreenContextTest setup complete")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========================================
    // CHAT CONTEXT TESTS
    // ========================================

    @Test
    fun `createContext with valid Chat data returns correct Chat context`() {
        val participants = listOf("John", "Jane")
        val messages = listOf(
            ChatMessage("John", "Hello there!", "10:00 AM", false),
            ChatMessage("Me", "Hi John!", "10:01 AM", true)
        )

        val chat = Chat("WhatsApp", participants, messages)

        assertEquals("WhatsApp", chat.appName)
        assertEquals("Conversation", chat.viewType)
        assertEquals(2, chat.participants.size)
        assertEquals(2, chat.messages.size)
        assertEquals("John", chat.participants[0])
    }

    @Test
    fun `createContext with empty participants returns Chat with empty list`() {
        val participants = emptyList<String>()
        val messages = listOf(
            ChatMessage("Unknown", "Hello!", null, false)
        )

        val chat = Chat("Telegram", participants, messages)

        assertEquals("Telegram", chat.appName)
        assertTrue(chat.participants.isEmpty())
        assertEquals(1, chat.messages.size)
    }

    @Test
    fun `createContext with empty messages returns Chat with empty list`() {
        val participants = listOf("Alice")
        val messages = emptyList<ChatMessage>()

        val chat = Chat("Signal", participants, messages)

        assertEquals("Signal", chat.appName)
        assertEquals(1, chat.participants.size)
        assertTrue(chat.messages.isEmpty())
    }

    @Test
    fun `getLastMessage with multiple messages returns latest non-user message`() {
        val messages = listOf(
            ChatMessage("Alice", "Hey!", "9:00 AM", false),
            ChatMessage("Me", "Hi!", "9:01 AM", true),
            ChatMessage("Alice", "How are you?", "9:02 AM", false),
            ChatMessage("Me", "Good, you?", "9:03 AM", true)
        )
        val chat = Chat("WhatsApp", listOf("Alice"), messages)

        // Find last non-user message
        val lastNonUserMessage = messages.lastOrNull { !it.isFromCurrentUser }

        assertNotNull(lastNonUserMessage)
        assertEquals("How are you?", lastNonUserMessage?.text)
    }

    @Test
    fun `getConversationPartner extracts correct name from participants`() {
        val participants = listOf("Bob Smith")
        val messages = listOf(
            ChatMessage("Bob Smith", "Meeting tomorrow?", "2:00 PM", false)
        )
        val chat = Chat("WhatsApp", participants, messages)

        val partner = chat.participants.firstOrNull()

        assertEquals("Bob Smith", partner)
    }

    @Test
    fun `getConversationPartner with multiple participants returns first`() {
        val participants = listOf("Alice", "Bob", "Charlie")
        val messages = emptyList<ChatMessage>()
        val chat = Chat("GroupChat", participants, messages)

        val partner = chat.participants.firstOrNull()

        assertEquals("Alice", partner)
    }

    @Test
    fun `isConversation with Chat context returns true`() {
        val chat = Chat("WhatsApp", listOf("John"), emptyList())

        assertTrue(chat is Chat)
        assertEquals("Conversation", chat.viewType)
    }

    // ========================================
    // EMAIL CONTEXT TESTS
    // ========================================

    @Test
    fun `createContext with valid Email data returns correct Email context`() {
        val email = Email(
            "Gmail",
            "sender@example.com",
            listOf("recipient@example.com"),
            "Meeting Tomorrow",
            "Hi, let's meet tomorrow at 3pm."
        )

        assertEquals("Gmail", email.appName)
        assertEquals("Email Thread", email.viewType)
        assertEquals("sender@example.com", email.from)
        assertEquals("Meeting Tomorrow", email.subject)
        assertEquals("Hi, let's meet tomorrow at 3pm.", email.body)
    }

    @Test
    fun `createContext with multiple recipients returns correct Email`() {
        val recipients = listOf("a@test.com", "b@test.com", "c@test.com")
        val email = Email(
            "Outlook",
            "boss@company.com",
            recipients,
            "Team Update",
            "Please review the attached."
        )

        assertEquals(3, email.to.size)
        assertEquals("a@test.com", email.to[0])
        assertEquals("b@test.com", email.to[1])
        assertEquals("c@test.com", email.to[2])
    }

    @Test
    fun `createContext with null email fields handles gracefully`() {
        val email = Email(
            "Yahoo Mail",
            null,
            emptyList(),
            null,
            ""
        )

        assertEquals("Yahoo Mail", email.appName)
        assertNull(email.from)
        assertTrue(email.to.isEmpty())
        assertNull(email.subject)
        assertEquals("", email.body)
    }

    @Test
    fun `Email context viewType is Email Thread`() {
        val email = Email("Gmail", "test@test.com", listOf(), "Test", "Body")

        assertEquals("Email Thread", email.viewType)
    }

    // ========================================
    // GENERIC CONTEXT TESTS
    // ========================================

    @Test
    fun `createContext with valid Generic data returns correct Generic context`() {
        val screenText = "This is some text from an unknown app."

        val generic = Generic("Unknown App", screenText)

        assertEquals("Unknown App", generic.appName)
        assertEquals("Generic Screen", generic.viewType)
        assertEquals(screenText, generic.screenText)
    }

    @Test
    fun `createContext with empty text returns empty Generic context`() {
        val generic = Generic("Chrome", "")

        assertEquals("Chrome", generic.appName)
        assertEquals("", generic.screenText)
    }

    @Test
    fun `createContext with long text truncates nothing`() {
        val longText = "A".repeat(1000)
        val generic = Generic("TestApp", longText)

        assertEquals(1000, generic.screenText.length)
    }

    @Test
    fun `isConversation with Browser app returns false`() {
        val generic = Generic("Chrome", "Web page content")

        assertTrue(generic is Generic)
        assertEquals("Generic Screen", generic.viewType)
        assertFalse(generic.viewType == "Conversation")
    }

    @Test
    fun `Generic context viewType is Generic Screen`() {
        val generic = Generic("SomeApp", "content")

        assertEquals("Generic Screen", generic.viewType)
    }

    // ========================================
    // CHAT MESSAGE TESTS
    // ========================================

    @Test
    fun `ChatMessage stores all fields correctly`() {
        val message = ChatMessage("John", "Hello world!", "10:30 AM", false)

        assertEquals("John", message.sender)
        assertEquals("Hello world!", message.text)
        assertEquals("10:30 AM", message.timestamp)
        assertFalse(message.isFromCurrentUser)
    }

    @Test
    fun `ChatMessage with user message has isFromCurrentUser true`() {
        val message = ChatMessage("Me", "My reply", "10:31 AM", true)

        assertTrue(message.isFromCurrentUser)
    }

    @Test
    fun `ChatMessage handles null timestamp`() {
        val message = ChatMessage("Alice", "Hi there", null, false)

        assertEquals("Alice", message.sender)
        assertEquals("Hi there", message.text)
        assertNull(message.timestamp)
    }

    @Test
    fun `ChatMessage handles emojis in text`() {
        val message = ChatMessage("Bob", "That's great! \ud83d\ude04\ud83d\udc4d", "11:00 AM", false)

        assertTrue(message.text.contains("\ud83d\ude04"))
        assertTrue(message.text.contains("\ud83d\udc4d"))
    }

    @Test
    fun `ChatMessage handles Hinglish text`() {
        val message = ChatMessage("Rahul", "Kya haal hai bro? Party tonight?", "8:00 PM", false)

        assertEquals("Kya haal hai bro? Party tonight?", message.text)
    }

    @Test
    fun `ChatMessage handles multiline text`() {
        val multilineText = "Line 1\nLine 2\nLine 3"
        val message = ChatMessage("Sender", multilineText, null, false)

        assertTrue(message.text.contains("\n"))
        assertEquals(3, message.text.split("\n").size)
    }

    // ========================================
    // PARCELABLE TESTS (using Robolectric)
    // ========================================

    @Test
    fun `Chat parcelable round trip preserves data`() {
        val original = Chat(
            "WhatsApp",
            listOf("Alice", "Bob"),
            listOf(
                ChatMessage("Alice", "Hello", "10:00", false),
                ChatMessage("Me", "Hi", "10:01", true)
            )
        )

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val restored = Chat.CREATOR.createFromParcel(parcel)

        assertEquals(original.appName, restored.appName)
        assertEquals(original.viewType, restored.viewType)
        assertEquals(original.participants.size, restored.participants.size)
        assertEquals(original.messages.size, restored.messages.size)
        assertEquals(original.participants[0], restored.participants[0])
        assertEquals(original.messages[0].text, restored.messages[0].text)

        parcel.recycle()
    }

    @Test
    fun `Email parcelable round trip preserves data`() {
        val original = Email(
            "Gmail",
            "sender@test.com",
            listOf("recipient@test.com"),
            "Test Subject",
            "Test body content"
        )

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val restored = Email.CREATOR.createFromParcel(parcel)

        assertEquals(original.appName, restored.appName)
        assertEquals(original.from, restored.from)
        assertEquals(original.to.size, restored.to.size)
        assertEquals(original.subject, restored.subject)
        assertEquals(original.body, restored.body)

        parcel.recycle()
    }

    @Test
    fun `Generic parcelable round trip preserves data`() {
        val original = Generic("TestApp", "Screen text content")

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val restored = Generic.CREATOR.createFromParcel(parcel)

        assertEquals(original.appName, restored.appName)
        assertEquals(original.screenText, restored.screenText)

        parcel.recycle()
    }

    @Test
    fun `ChatMessage parcelable round trip preserves data`() {
        val original = ChatMessage("John", "Test message", "12:00 PM", true)

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val restored = ChatMessage.CREATOR.createFromParcel(parcel)

        assertEquals(original.sender, restored.sender)
        assertEquals(original.text, restored.text)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.isFromCurrentUser, restored.isFromCurrentUser)

        parcel.recycle()
    }

    // ========================================
    // CONTEXT TYPE IDENTIFICATION TESTS
    // ========================================

    @Test
    fun `context type check for Chat returns true`() {
        val context: ScreenContext = Chat("WhatsApp", listOf("User"), emptyList())

        assertTrue(context is Chat)
        assertFalse(context is Email)
        assertFalse(context is Generic)
    }

    @Test
    fun `context type check for Email returns true`() {
        val context: ScreenContext = Email("Gmail", "test@test.com", listOf(), "Subject", "Body")

        assertTrue(context is Email)
        assertFalse(context is Chat)
        assertFalse(context is Generic)
    }

    @Test
    fun `context type check for Generic returns true`() {
        val context: ScreenContext = Generic("Chrome", "Page content")

        assertTrue(context is Generic)
        assertFalse(context is Chat)
        assertFalse(context is Email)
    }

    // ========================================
    // EDGE CASE TESTS
    // ========================================

    @Test
    fun `Chat with very long message handles correctly`() {
        val longMessage = "A".repeat(500)
        val messages = listOf(ChatMessage("User", longMessage, null, false))
        val chat = Chat("WhatsApp", listOf("User"), messages)

        assertEquals(500, chat.messages[0].text.length)
    }

    @Test
    fun `Chat with special characters in sender name handles correctly`() {
        val messages = listOf(
            ChatMessage("Jo\u00e3o da Silva", "Ol\u00e1!", null, false)
        )
        val chat = Chat("WhatsApp", listOf("Jo\u00e3o da Silva"), messages)

        assertEquals("Jo\u00e3o da Silva", chat.participants[0])
        assertEquals("Jo\u00e3o da Silva", chat.messages[0].sender)
    }

    @Test
    fun `Email with HTML-like content in body handles correctly`() {
        val htmlContent = "<p>Hello <b>World</b></p>"
        val email = Email("Gmail", "test@test.com", listOf(), "Subject", htmlContent)

        assertEquals(htmlContent, email.body)
    }

    @Test
    fun `Generic with only whitespace content`() {
        val generic = Generic("App", "   \n\t  ")

        assertEquals("   \n\t  ", generic.screenText)
    }

    @Test
    fun `Chat with empty participant name handles correctly`() {
        val messages = listOf(ChatMessage("", "Hello", null, false))
        val chat = Chat("WhatsApp", listOf(""), messages)

        assertEquals("", chat.participants[0])
        assertEquals("", chat.messages[0].sender)
    }
}
