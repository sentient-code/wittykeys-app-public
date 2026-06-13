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
@Config(sdk = [28], manifest = Config.NONE, application = WkUserBubbleTest.StubApp::class)
class WkUserBubbleTest {
    class StubApp : android.app.Application() {
        override fun onCreate() {}
    }
    private fun themedCtx() = ContextThemeWrapper(
        RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys
    )

    @Test
    fun bind_setsText() {
        val bubble = WkUserBubble(themedCtx())
        bubble.bind("Hello world")
        assertEquals("Hello world", bubble.getTextForTest())
    }

    @Test
    fun emptyText_stillRenders() {
        val bubble = WkUserBubble(themedCtx())
        bubble.bind("")
        assertEquals("", bubble.getTextForTest())
    }
}
