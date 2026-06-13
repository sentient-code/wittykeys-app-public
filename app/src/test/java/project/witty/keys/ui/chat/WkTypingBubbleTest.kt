package project.witty.keys.ui.chat

import android.view.ContextThemeWrapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import project.witty.keys.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = WkTypingBubbleTest.StubApp::class)
class WkTypingBubbleTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }
    private fun themedCtx() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun renders_threeDots() {
        val b = WkTypingBubble(themedCtx())
        assertEquals(3, b.getDotCountForTest())
    }

    @Test
    fun startStop_togglesAnimationState() {
        val b = WkTypingBubble(themedCtx())
        b.start()
        assertTrue(b.isAnimatingForTest())
        b.stop()
        assertFalse(b.isAnimatingForTest())
    }
}
