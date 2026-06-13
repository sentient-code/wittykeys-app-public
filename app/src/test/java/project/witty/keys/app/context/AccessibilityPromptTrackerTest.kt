package project.witty.keys.app.context

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for AccessibilityPromptTracker and DefaultReplyCache.
 * Uses Robolectric for SharedPreferences access.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AccessibilityPromptTrackerTest {

    private lateinit var context: Context
    private lateinit var tracker: AccessibilityPromptTracker

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Clear prefs before each test
        context.getSharedPreferences("wk_onboarding", Context.MODE_PRIVATE)
            .edit().clear().apply()
        // Reset singleton for clean state
        val field = AccessibilityPromptTracker::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        tracker = AccessibilityPromptTracker.getInstance(context)
    }

    @Test
    fun `initial tap count is zero`() {
        assertEquals(0, tracker.tapCount)
    }

    @Test
    fun `recordReplyTap increments count`() {
        tracker.recordReplyTap()
        tracker.recordReplyTap()
        assertEquals(2, tracker.tapCount)
    }

    @Test
    fun `shouldShowPrompt false when tap count below threshold`() {
        tracker.recordReplyTap()  // 1
        tracker.recordReplyTap()  // 2
        assertFalse(tracker.shouldShowPrompt())
    }

    @Test
    fun `shouldShowPrompt true at exactly 3 taps`() {
        repeat(3) { tracker.recordReplyTap() }
        assertTrue(tracker.shouldShowPrompt())
    }

    @Test
    fun `shouldShowPrompt false after prompt shown`() {
        repeat(3) { tracker.recordReplyTap() }
        tracker.markPromptShown()
        assertFalse(tracker.shouldShowPrompt())
    }

    @Test
    fun `shouldShowPrompt false after prompt dismissed`() {
        repeat(3) { tracker.recordReplyTap() }
        tracker.markPromptShown()
        tracker.markPromptDismissed()
        assertFalse(tracker.shouldShowPrompt())
    }

    @Test
    fun `prompt does not re-trigger after dismissal`() {
        repeat(3) { tracker.recordReplyTap() }
        tracker.markPromptDismissed()
        tracker.recordReplyTap()  // 4th tap
        assertFalse(tracker.shouldShowPrompt())
    }

    @Test
    fun `tap count persists across instances`() {
        tracker.recordReplyTap()
        tracker.recordReplyTap()

        // Get new instance (reset singleton)
        val field = AccessibilityPromptTracker::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        val newTracker = AccessibilityPromptTracker.getInstance(context)

        assertEquals(2, newTracker.tapCount)
    }

    // ========== DefaultReplyCache Tests ==========

    @Test
    fun `DefaultReplyCache returns app-specific replies for WhatsApp`() {
        val replies = DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")
        assertNotNull(replies)
        assertEquals(3, replies!!.size)
    }

    @Test
    fun `DefaultReplyCache returns generic for unknown apps`() {
        val replies = DefaultReplyCache.getDefaultReplies(context, "com.unknown.app")
        assertNotNull(replies)
        assertEquals(3, replies!!.size)
    }

    @Test
    fun `DefaultReplyCache returns different replies for different apps`() {
        val whatsapp = DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")
        // Reset open count for second call
        context.getSharedPreferences("wk_onboarding", Context.MODE_PRIVATE)
            .edit().putInt("wk_onboarding_keyboard_open_count", 0).apply()
        val tinder = DefaultReplyCache.getDefaultReplies(context, "com.tinder")

        assertNotNull(whatsapp)
        assertNotNull(tinder)
        assertNotEquals(whatsapp!![0], tinder!![0])
    }

    @Test
    fun `DefaultReplyCache exhausted after 5 uses`() {
        repeat(5) { DefaultReplyCache.getDefaultReplies(context, "com.whatsapp") }
        val result = DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")
        assertNull(result)
    }

    @Test
    fun `DefaultReplyCache hasDefaults returns true initially`() {
        assertTrue(DefaultReplyCache.hasDefaults(context))
    }

    @Test
    fun `DefaultReplyCache hasDefaults returns false after exhaustion`() {
        repeat(5) { DefaultReplyCache.getDefaultReplies(context, "com.whatsapp") }
        assertFalse(DefaultReplyCache.hasDefaults(context))
    }

    @Test
    fun `DefaultReplyCache increments open count per call`() {
        DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")
        DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")
        DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")

        val prefs = context.getSharedPreferences("wk_onboarding", Context.MODE_PRIVATE)
        assertEquals(3, prefs.getInt("wk_onboarding_keyboard_open_count", 0))
    }
}
