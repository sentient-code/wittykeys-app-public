package project.witty.keys.app.context

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for DefaultReplyCache.
 * Uses Robolectric for SharedPreferences context.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DefaultReplyCacheTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        // Clear shared preferences to reset state
        context.getSharedPreferences("wk_onboarding", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun `getDefaultReplies returns non-null for WhatsApp`() {
        val replies = DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")
        assertNotNull("WhatsApp should have default replies", replies)
        assertTrue("Should have at least 1 reply", replies!!.isNotEmpty())
    }

    @Test
    fun `getDefaultReplies returns non-null for Tinder`() {
        val replies = DefaultReplyCache.getDefaultReplies(context, "com.tinder")
        assertNotNull("Tinder should have default replies", replies)
    }

    @Test
    fun `getDefaultReplies returns different replies for Tinder vs WhatsApp`() {
        val tinder = DefaultReplyCache.getDefaultReplies(context, "com.tinder")
        // Reset usage counter between calls
        context.getSharedPreferences("wk_onboarding", Context.MODE_PRIVATE)
            .edit().clear().apply()
        val whatsapp = DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")

        assertNotNull(tinder)
        assertNotNull(whatsapp)
        // At least one reply should differ (dating vs messaging)
        assertFalse("Tinder and WhatsApp defaults should differ",
            tinder!!.contentEquals(whatsapp!!))
    }

    @Test
    fun `getDefaultReplies returns null after 5 uses`() {
        for (i in 1..5) {
            DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")
        }
        // 6th call should return null
        val result = DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")
        assertNull("Should return null after 5 uses", result)
    }

    @Test
    fun `hasDefaults returns true before exhaustion`() {
        assertTrue(DefaultReplyCache.hasDefaults(context))
    }

    @Test
    fun `hasDefaults returns false after 5 uses`() {
        for (i in 1..5) {
            DefaultReplyCache.getDefaultReplies(context, "com.whatsapp")
        }
        assertFalse(DefaultReplyCache.hasDefaults(context))
    }

    @Test
    fun `getDefaultReplies returns non-null for unknown package`() {
        val replies = DefaultReplyCache.getDefaultReplies(context, "com.random.app")
        assertNotNull("Unknown package should get generic defaults", replies)
    }
}
