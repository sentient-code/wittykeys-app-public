package project.witty.keys.app.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OverlayReplyPolishContractTest {
    private fun read(path: String) = File(path).readText()

    @Test
    fun `reply panel renders sender and message as distinct typography`() {
        val panel = read("src/main/java/project/witty/keys/app/overlay/OverlayReplyPanel.java")

        assertTrue(panel.contains("new ChatMessage(msg.sender, msg.text, msg.isSent)"))
        assertTrue(panel.contains("formatMessage(msg)"))
        assertTrue(panel.contains("SpannableString"))
        assertTrue(panel.contains("StyleSpan(Typeface.BOLD)"))
        assertTrue(panel.contains("RelativeSizeSpan(0.82f)"))
        assertTrue(panel.contains("ForegroundColorSpan"))
        assertFalse(panel.contains("new ChatMessage(msg.displayText, msg.isSent)"))
    }

    @Test
    fun `reply panel shows minimal loading state while suggestions are in flight`() {
        val panel = read("src/main/java/project/witty/keys/app/overlay/OverlayReplyPanel.java")

        assertTrue(panel.contains("ConversationReplyState.BlockedReason.IN_FLIGHT"))
        assertTrue(panel.contains("showSuggestionsLoading()"))
        assertTrue(panel.contains("AI suggestions are loading"))
        assertTrue(panel.contains("ProgressBar"))
    }
}
