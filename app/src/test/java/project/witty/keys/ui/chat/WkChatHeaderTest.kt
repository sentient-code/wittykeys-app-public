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
@Config(sdk = [28], manifest = Config.NONE, application = WkChatHeaderTest.StubApp::class)
class WkChatHeaderTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }
    private fun themedCtx() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun setTitle_updatesLabel() {
        val h = WkChatHeader(themedCtx())
        h.setTitle("AI Chat")
        assertEquals("AI Chat", h.getTitleForTest())
    }

    @Test
    fun setBackVisibleFalse_hidesBack() {
        val h = WkChatHeader(themedCtx())
        h.setBackVisible(false)
        assertEquals(View.GONE, h.getBackVisibilityForTest())
    }

    @Test
    fun allThreeClicks_fireListeners() {
        val h = WkChatHeader(themedCtx())
        var back = false; var sessions = false; var close = false
        h.setOnBackClickListener { back = true }
        h.setOnSessionsClickListener { sessions = true }
        h.setOnCloseClickListener { close = true }
        h.clickBackForTest(); h.clickSessionsForTest(); h.clickCloseForTest()
        assertTrue(back); assertTrue(sessions); assertTrue(close)
    }
}
