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
@Config(sdk = [28], manifest = Config.NONE, application = WkAiBubbleTest.StubApp::class)
class WkAiBubbleTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }
    private fun themedCtx() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun bindNormal_hidesBadgeAndRetry() {
        val b = WkAiBubble(themedCtx())
        b.bindNormal("hello")
        assertEquals("hello", b.getTextForTest())
        assertEquals(View.GONE, b.getBadgeVisibilityForTest())
        assertEquals(View.GONE, b.getRetryVisibilityForTest())
    }

    @Test
    fun bindWithReplyBadge_showsBadge() {
        val b = WkAiBubble(themedCtx())
        b.bindWithReplyBadge("landing at 5:30")
        assertEquals("landing at 5:30", b.getTextForTest())
        assertEquals(View.VISIBLE, b.getBadgeVisibilityForTest())
        assertEquals(View.GONE, b.getRetryVisibilityForTest())
    }

    @Test
    fun bindError_showsRetryAndCallsBack() {
        val b = WkAiBubble(themedCtx())
        var clicked = false
        b.bindError { clicked = true }
        assertEquals(View.VISIBLE, b.getRetryVisibilityForTest())
        b.clickRetryForTest()
        assertTrue(clicked)
    }
}
