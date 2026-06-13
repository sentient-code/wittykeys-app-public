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
@Config(sdk = [28], manifest = Config.NONE, application = WkDualCtaRowTest.StubApp::class)
class WkDualCtaRowTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }
    private fun themedCtx() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun setLabels_updatesBothButtons() {
        val row = WkDualCtaRow(themedCtx())
        row.setPrimary("New chat") { }
        row.setGhost("Open keyboard") { }
        assertEquals("New chat", row.getPrimaryLabelForTest())
        assertEquals("Open keyboard", row.getGhostLabelForTest())
    }

    @Test
    fun primaryClick_fires() {
        val row = WkDualCtaRow(themedCtx())
        var clicked = false
        row.setPrimary("P") { clicked = true }
        row.clickPrimaryForTest()
        assertTrue(clicked)
    }

    @Test
    fun ghostClick_fires() {
        val row = WkDualCtaRow(themedCtx())
        var clicked = false
        row.setGhost("G") { clicked = true }
        row.clickGhostForTest()
        assertTrue(clicked)
    }
}
