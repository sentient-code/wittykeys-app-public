package project.witty.keys.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import project.witty.keys.app.context.UnifiedChatSessionManager

class WkSessionDisplayTest {

    @Test
    fun displayTitle_stripsSurfacePrefixes() {
        assertEquals("Screenshot captured", WkSessionDisplay.displayTitle("Overlay · Screenshot captured"))
        assertEquals("Help me reply", WkSessionDisplay.displayTitle("Keyboard · Help me reply"))
        assertEquals("Draft email", WkSessionDisplay.displayTitle("Fullscreen · Draft email"))
    }

    @Test
    fun displayTitle_stripsCaseInsensitiveSurfacePrefixesAndSeparators() {
        assertEquals("Project plan", WkSessionDisplay.displayTitle("overlay - Project plan"))
        assertEquals("Quick reply draft", WkSessionDisplay.displayTitle("KEYBOARD: Quick reply draft"))
        assertEquals("Invoice follow-up", WkSessionDisplay.displayTitle("[fullscreen] Invoice follow-up"))
    }

    @Test
    fun displayTitle_replacesBareSurfaceTitlesWithNewChat() {
        assertEquals("New Chat", WkSessionDisplay.displayTitle("Overlay"))
        assertEquals("New Chat", WkSessionDisplay.displayTitle("Keyboard"))
        assertEquals("New Chat", WkSessionDisplay.displayTitle("Fullscreen"))
        assertEquals("New Chat", WkSessionDisplay.displayTitle(""))
        assertEquals("New Chat", WkSessionDisplay.displayTitle(null))
    }

    @Test
    fun surfaceFromSource_mapsKnownSources() {
        assertEquals(Surface.OVERLAY, WkSessionDisplay.surfaceFromSource(UnifiedChatSessionManager.SOURCE_OVERLAY))
        assertEquals(Surface.FULLSCREEN, WkSessionDisplay.surfaceFromSource(UnifiedChatSessionManager.SOURCE_FULLSCREEN))
        assertEquals(Surface.KEYBOARD, WkSessionDisplay.surfaceFromSource(UnifiedChatSessionManager.SOURCE_KEYBOARD))
        assertEquals(Surface.KEYBOARD, WkSessionDisplay.surfaceFromSource("unknown"))
        assertEquals(Surface.KEYBOARD, WkSessionDisplay.surfaceFromSource(null))
    }

    @Test
    fun previewHidesWhenItRepeatsTheDisplayTitle() {
        assertEquals("", WkSessionDisplay.preview("Overlay · Screenshot captured", "Screenshot captured"))
        assertEquals("AI found three tasks", WkSessionDisplay.preview("Overlay · Screenshot captured", "AI found three tasks"))
    }
}
