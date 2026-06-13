package project.witty.keys.ui.chat

import android.view.ContextThemeWrapper
import android.view.View
import android.graphics.drawable.GradientDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import project.witty.keys.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = WkSessionCardTest.StubApp::class)
class WkSessionCardTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }
    private fun themedCtx() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun bind_setsAllFields() {
        val c = WkSessionCard(themedCtx())
        c.bind("Reply to Ananya", "On it — 6pm", "2m", Surface.KEYBOARD, false)
        assertEquals("Reply to Ananya", c.getTitleForTest())
        assertEquals("On it — 6pm", c.getPreviewForTest())
        assertEquals("2m", c.getTimeForTest())
    }

    @Test
    fun unreadTrue_showsDot() {
        val c = WkSessionCard(themedCtx())
        c.bind("t", "p", "now", Surface.OVERLAY, true)
        assertEquals(View.VISIBLE, c.getUnreadDotVisibilityForTest())
    }

    @Test
    fun unreadFalse_hidesDot() {
        val c = WkSessionCard(themedCtx())
        c.bind("t", "p", "now", Surface.OVERLAY, false)
        assertEquals(View.GONE, c.getUnreadDotVisibilityForTest())
    }

    @Test
    fun bind_overlaySurface_showsOverlayTag() {
        val c = WkSessionCard(themedCtx())
        c.bind("t", "p", "now", Surface.OVERLAY, false)
        assertEquals(View.VISIBLE, c.getSourceTagVisibilityForTest())
        assertEquals("overlay", c.getSourceTagTextForTest())
    }

    @Test
    fun bind_fullscreenSurface_hidesSourceTag() {
        val c = WkSessionCard(themedCtx())
        c.bind("t", "p", "now", Surface.FULLSCREEN, false)
        assertEquals(View.GONE, c.getSourceTagVisibilityForTest())
    }

    @Test
    fun click_firesListener() {
        val c = WkSessionCard(themedCtx())
        var clicked = false
        c.setOnClickListener { clicked = true }
        c.performClick()
        assertTrue(clicked)
    }

    @Test
    fun deleteClick_firesDeleteListenerOnly() {
        val c = WkSessionCard(themedCtx())
        var cardClicked = false
        var deleteClicked = false

        c.setOnClickListener { cardClicked = true }
        c.setOnDeleteClickListener { deleteClicked = true }
        c.clickDeleteForTest()

        assertTrue(deleteClicked)
        assertFalse(cardClicked)
    }

    @Test
    fun rootCardUsesSharedGradientBackground() {
        val c = WkSessionCard(themedCtx())
        val root = c.findViewById<View>(R.id.wkSessionCardRoot)

        assertTrue(root.background is GradientDrawable)
    }
}
