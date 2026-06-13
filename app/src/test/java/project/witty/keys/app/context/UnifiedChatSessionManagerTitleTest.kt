package project.witty.keys.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UnifiedChatSessionManagerTitleTest {

    @Test
    fun buildTitle_returnsContentOnlyTitleForOverlaySessions() {
        val title = UnifiedChatSessionManager.buildTitle(
            UnifiedChatSessionManager.SOURCE_OVERLAY,
            "Screenshot captured"
        )

        assertEquals("Screenshot captured", title)
        assertFalse(title.contains("Overlay"))
    }

    @Test
    fun buildTitle_returnsNewChatForBlankContext() {
        assertEquals("New Chat", UnifiedChatSessionManager.buildTitle(UnifiedChatSessionManager.SOURCE_KEYBOARD, null))
        assertEquals("New Chat", UnifiedChatSessionManager.buildTitle(UnifiedChatSessionManager.SOURCE_OVERLAY, ""))
    }

    @Test
    fun buildTitle_truncatesLongContextWithoutSurfacePrefix() {
        val title = UnifiedChatSessionManager.buildTitle(
            UnifiedChatSessionManager.SOURCE_FULLSCREEN,
            "This is a very long opening message that should be shortened for the session list"
        )

        assertEquals(50, title.length)
        assertFalse(title.contains("Fullscreen"))
    }
}
