package project.witty.keys.ui.chat

import android.view.ContextThemeWrapper
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import project.witty.keys.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = WkStripMiniHeaderTest.StubApp::class)
class WkStripMiniHeaderTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }
    private fun themedCtx() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun setTitle_updatesLabel() {
        val h = WkStripMiniHeader(themedCtx())
        h.setTitle("Reply mode")
        assertEquals("Reply mode", h.getTitleForTest())
    }

    @Test
    fun sessionsClick_firesListener() {
        val h = WkStripMiniHeader(themedCtx())
        var clicked = false
        h.setOnSessionsClickListener { clicked = true }
        h.clickSessionsForTest()
        assertTrue(clicked)
    }

    @Test
    fun closeClick_firesListener() {
        val h = WkStripMiniHeader(themedCtx())
        var clicked = false
        h.setOnCloseClickListener { clicked = true }
        h.clickCloseForTest()
        assertTrue(clicked)
    }

    @Test
    fun replyQuote_showsDividerBar() {
        val h = WkStripMiniHeader(themedCtx())
        h.setReplyQuote("\"need slides by EOD\"")
        assertEquals(View.VISIBLE, h.getQuoteBarVisibilityForTest())

        h.setReplyQuote(null)
        assertEquals(View.GONE, h.getQuoteBarVisibilityForTest())
    }
}
