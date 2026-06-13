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
@Config(sdk = [28], manifest = Config.NONE, application = WkEmptyStateTest.StubApp::class)
class WkEmptyStateTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }
    private fun themedCtx() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun bind_setsTitleAndSub() {
        val e = WkEmptyState(themedCtx())
        e.bind("Start chatting", "Tap the pill below.")
        assertEquals("Start chatting", e.getTitleForTest())
        assertEquals("Tap the pill below.", e.getSubForTest())
    }

    @Test
    fun withoutCta_hidesCtaRow() {
        val e = WkEmptyState(themedCtx())
        e.bind("T", "S")
        assertEquals(View.GONE, e.getCtaVisibilityForTest())
    }

    @Test
    fun withCta_showsCtaRow() {
        val e = WkEmptyState(themedCtx())
        e.bind("T", "S")
        e.showCta("New chat", {}, "Open overlay", {})
        assertEquals(View.VISIBLE, e.getCtaVisibilityForTest())
    }
}
