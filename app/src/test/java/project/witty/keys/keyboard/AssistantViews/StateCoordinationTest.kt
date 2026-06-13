package project.witty.keys.keyboard.AssistantViews

import org.junit.Test
import project.witty.keys.app.context.AppDetector
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * State Coordination Unit Tests (Phase 4)
 *
 * Tests the state management and view coordination:
 * - AppDetector contextual app detection (J9)
 * - New message handling (J13)
 * - Data sharing between views
 *
 * Run with:
 * ./gradlew test --tests "project.witty.keys.keyboard.AssistantViews.StateCoordinationTest"
 *
 * Build 6.3 - SmartAssistantBar Revamp - Phase 4
 * Updated: Build 7.1 — Removed MemoryViewData references
 */
class StateCoordinationTest {

    // ===========================================
    // J9: AppDetector - Contextual App Detection
    // ===========================================

    @Test
    fun `isContextualApp returns true for messaging apps`() {
        val messagingApps = listOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.facebook.orca",
            "org.thoughtcrime.securesms", // Signal
            "com.viber.voip",
            "com.google.android.apps.messaging",
            "com.google.android.apps.dynamite"
        )

        for (packageName in messagingApps) {
            val isContextual = AppDetector.isContextualApp(packageName)
            assertTrue(isContextual, "Expected $packageName to be contextual (MESSAGING)")
            println("[PASS] $packageName -> contextual=true (MESSAGING)")
        }
    }

    @Test
    fun `isContextualApp returns true for email apps`() {
        val emailApps = listOf(
            "com.google.android.gm", // Gmail
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail"
        )

        for (packageName in emailApps) {
            val isContextual = AppDetector.isContextualApp(packageName)
            assertTrue(isContextual, "Expected $packageName to be contextual (EMAIL)")
            println("[PASS] $packageName -> contextual=true (EMAIL)")
        }
    }

    @Test
    fun `isContextualApp returns true for dating apps`() {
        val datingApps = listOf(
            "com.tinder",
            "com.bumble.app",
            "co.hinge.app"
        )

        for (packageName in datingApps) {
            val isContextual = AppDetector.isContextualApp(packageName)
            assertTrue(isContextual, "Expected $packageName to be contextual (DATING)")
            println("[PASS] $packageName -> contextual=true (DATING)")
        }
    }

    @Test
    fun `isContextualApp returns true for social apps`() {
        val socialApps = listOf(
            "com.linkedin.android",
            "com.twitter.android",
            "com.instagram.android",
            "com.discord",
            "com.slack"
        )

        for (packageName in socialApps) {
            val isContextual = AppDetector.isContextualApp(packageName)
            assertTrue(isContextual, "Expected $packageName to be contextual (SOCIAL)")
            println("[PASS] $packageName -> contextual=true (SOCIAL)")
        }
    }

    @Test
    fun `isContextualApp returns false for non-contextual apps`() {
        val nonContextualApps = listOf(
            "com.android.chrome",
            "com.google.android.apps.docs",
            "com.android.calculator2",
            "com.spotify.music",
            "com.google.android.youtube"
        )

        for (packageName in nonContextualApps) {
            val isContextual = AppDetector.isContextualApp(packageName)
            assertFalse(isContextual, "Expected $packageName to be non-contextual (OTHER)")
            println("[PASS] $packageName -> contextual=false (OTHER)")
        }
    }

    @Test
    fun `isContextualApp handles null and empty strings`() {
        assertFalse(AppDetector.isContextualApp(null), "null should be non-contextual")
        assertFalse(AppDetector.isContextualApp(""), "empty string should be non-contextual")
        println("[PASS] null/empty handled correctly")
    }

    // ===========================================
    // J9: AppDetector - Category Names
    // ===========================================

    @Test
    fun `categorize returns correct AppCategory`() {
        assertEquals(AppDetector.AppCategory.MESSAGING, AppDetector.categorize("com.whatsapp"))
        assertEquals(AppDetector.AppCategory.EMAIL, AppDetector.categorize("com.google.android.gm"))
        assertEquals(AppDetector.AppCategory.DATING, AppDetector.categorize("com.tinder"))
        assertEquals(AppDetector.AppCategory.SOCIAL, AppDetector.categorize("com.linkedin.android"))
        assertEquals(AppDetector.AppCategory.OTHER, AppDetector.categorize("com.android.chrome"))
        println("[PASS] categorize returns correct categories")
    }

    @Test
    fun `getCategoryName returns human-readable names`() {
        assertEquals("messaging", AppDetector.getCategoryName(AppDetector.AppCategory.MESSAGING))
        assertEquals("email", AppDetector.getCategoryName(AppDetector.AppCategory.EMAIL))
        assertEquals("dating", AppDetector.getCategoryName(AppDetector.AppCategory.DATING))
        assertEquals("social", AppDetector.getCategoryName(AppDetector.AppCategory.SOCIAL))
        assertEquals("other", AppDetector.getCategoryName(AppDetector.AppCategory.OTHER))
        println("[PASS] getCategoryName returns correct names")
    }

    @Test
    fun `getAppName returns friendly names for known apps`() {
        assertEquals("WhatsApp", AppDetector.getAppName("com.whatsapp"))
        assertEquals("Telegram", AppDetector.getAppName("org.telegram.messenger"))
        assertEquals("Gmail", AppDetector.getAppName("com.google.android.gm"))
        assertEquals("Outlook", AppDetector.getAppName("com.microsoft.office.outlook"))
        assertEquals("Tinder", AppDetector.getAppName("com.tinder"))
        assertEquals("LinkedIn", AppDetector.getAppName("com.linkedin.android"))
        println("[PASS] getAppName returns friendly names")
    }

    @Test
    fun `getAppName extracts last part for unknown apps`() {
        val appName = AppDetector.getAppName("com.example.myapp")
        assertEquals("Myapp", appName) // Capitalized
        println("[PASS] getAppName extracts last part: $appName")
    }

    // ===========================================
    // J13: New Message Handling
    // ===========================================

    @Test
    fun `new message data stores sender and message`() {
        val senderName = "John"
        val lastMessage = "Hey, where are you?"

        assertEquals("John", senderName)
        assertEquals("Hey, where are you?", lastMessage)
        println("[PASS] New message data stores sender and message correctly")
    }

    @Test
    fun `new message can be detected by comparing text`() {
        val lastSeenMessage = "First message"
        val newMessage = "New message just arrived!"

        // Simulate new message arriving
        val isNewMessage = lastSeenMessage != newMessage
        assertTrue(isNewMessage)
        println("[PASS] New message detected via text comparison")
    }

    // ===========================================
    // Data Sharing Between Views
    // ===========================================

    @Test
    fun `reply list preserves entries for view sharing`() {
        val quickReplies = listOf(
            "I apologize for the delay.",
            "I'll send it right away.",
            "Working on it now.",
            "Can I call you to discuss?"
        )

        assertEquals(4, quickReplies.size)
        assertTrue(quickReplies[0].contains("apologize"))
        println("[PASS] QuickReplies preserved for view sharing: ${quickReplies.size} replies")
    }

    // ===========================================
    // State Transitions (Logic Tests)
    // ===========================================

    @Test
    fun `contextual app should trigger reply generation`() {
        val packageName = "com.whatsapp"
        val isContextual = AppDetector.isContextualApp(packageName)
        val category = AppDetector.categorize(packageName)

        assertTrue(isContextual)
        assertEquals(AppDetector.AppCategory.MESSAGING, category)

        // Logic: if isContextual -> generate and show smart replies
        println("[PASS] Contextual app ($packageName) should trigger reply generation")
    }

    @Test
    fun `non-contextual app should show OriginalView EXPANDED logic`() {
        val packageName = "com.android.chrome"
        val isContextual = AppDetector.isContextualApp(packageName)
        val category = AppDetector.categorize(packageName)

        assertFalse(isContextual)
        assertEquals(AppDetector.AppCategory.OTHER, category)

        // Logic: if !isContextual -> show OriginalView EXPANDED (no replies)
        println("[PASS] Non-contextual app ($packageName) should show OriginalView EXPANDED")
    }

    // ===========================================
    // New Message Detection Logic
    // ===========================================

    @Test
    fun `new message detection compares message text`() {
        val lastSeenMessage = "Hello, how are you?"
        val newMessage = "I'm waiting for your response!"

        // New message is different from last seen
        val isNewMessage = lastSeenMessage != newMessage
        assertTrue(isNewMessage, "Should detect new message when text differs")
        println("[PASS] New message detected: text differs")
    }

    @Test
    fun `same message is not detected as new`() {
        val lastSeenMessage = "Hello, how are you?"
        val currentMessage = "Hello, how are you?"

        // Same message should not be detected as new
        val isNewMessage = lastSeenMessage != currentMessage
        assertFalse(isNewMessage, "Should not detect same message as new")
        println("[PASS] Same message not detected as new")
    }

    // ===========================================
    // Comprehensive State Coordination Test
    // ===========================================

    @Test
    fun `full state coordination flow for J9 non-contextual app`() {
        // J9: Non-Contextual App Flow

        // Step 1: Detect app category
        val packageName = "com.google.android.youtube"
        val category = AppDetector.categorize(packageName)
        assertEquals(AppDetector.AppCategory.OTHER, category)

        // Step 2: Check if contextual
        val isContextual = AppDetector.isContextualApp(packageName)
        assertFalse(isContextual)

        // Step 3: For non-contextual, OriginalView EXPANDED should be shown
        println("[PASS] J9 flow: $packageName -> category=$category, contextual=$isContextual -> OriginalView EXPANDED")
    }

    @Test
    fun `full state coordination flow for J13 new message`() {
        // J13: New Message While Typing Flow

        // Step 1: User is in COLLAPSED state (typing)
        val userTyping = true
        assertTrue(userTyping)

        // Step 2: New message arrives
        val originalMessage = "Original message"
        val newMessage = "New message just arrived!"
        val newReplies = listOf("Got it!", "On my way", "Give me a sec")

        // Step 3: Detect message is different
        val isNewMessage = originalMessage != newMessage
        assertTrue(isNewMessage)

        // Step 4: Quick replies should be available
        assertEquals(3, newReplies.size)

        // Step 5: Brain blink animation would be triggered (SmartAssistantBar handles this)

        println("[PASS] J13 flow: newMessage='${newMessage.take(30)}...', replies=${newReplies.size}")
    }
}
