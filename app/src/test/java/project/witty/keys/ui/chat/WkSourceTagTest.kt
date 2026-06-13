package project.witty.keys.ui.chat

import android.view.ContextThemeWrapper
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import project.witty.keys.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = WkSourceTagTest.StubApp::class)
class WkSourceTagTest {

    class StubApp : android.app.Application() {
        override fun onCreate() { /* skip Firebase init */ }
    }

    private fun themedCtx() = ContextThemeWrapper(
        RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys
    )

    @Test
    fun keyboardSurface_rendersKbdLabel() {
        val tag = WkSourceTag(themedCtx())
        tag.setSurface(Surface.KEYBOARD)
        assertEquals("kbd", tag.findViewById<TextView>(R.id.wkSourceTagLabel).text.toString())
    }

    @Test
    fun overlaySurface_rendersOverlayLabel() {
        val tag = WkSourceTag(themedCtx())
        tag.setSurface(Surface.OVERLAY)
        assertEquals("overlay", tag.findViewById<TextView>(R.id.wkSourceTagLabel).text.toString())
    }

    @Test
    fun fullscreenSurface_rendersFullscreenLabel() {
        val tag = WkSourceTag(themedCtx())
        tag.setSurface(Surface.FULLSCREEN)
        assertEquals("fullscreen", tag.findViewById<TextView>(R.id.wkSourceTagLabel).text.toString())
    }

    @Test
    fun sourceTagDoesNotUseExtraLetterSpacing() {
        val tag = WkSourceTag(themedCtx())
        val label = tag.findViewById<TextView>(R.id.wkSourceTagLabel)

        assertEquals(0f, label.letterSpacing)
    }
}
