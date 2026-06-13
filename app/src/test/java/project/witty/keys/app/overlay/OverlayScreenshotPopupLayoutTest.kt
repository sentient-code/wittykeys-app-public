package project.witty.keys.app.overlay

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import project.witty.keys.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = OverlayScreenshotPopupLayoutTest.StubApp::class)
class OverlayScreenshotPopupLayoutTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }

    @Test
    fun screenshotPopup_placesContentDirectlyUnderHeaderWithoutDivider() {
        val ctx = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)
        val root = LayoutInflater.from(ctx)
            .inflate(R.layout.overlay_screenshot_popup, null) as ViewGroup

        val contentIndex = (0 until root.childCount)
            .first { root.getChildAt(it).id == R.id.overlay_screenshot_content }

        assertEquals(1, contentIndex)
    }
}
