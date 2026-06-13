package project.witty.keys.keyboard.ProductViews

import android.view.ContextThemeWrapper
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import project.witty.keys.R
import project.witty.keys.ui.chat.WkDualCtaRow
import project.witty.keys.ui.chat.WkInputBar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = UnifiedAiViewStateTest.StubApp::class)
class UnifiedAiViewStateTest {
    class StubApp : android.app.Application() {
        override fun onCreate() {}
    }

    private fun themedCtx() =
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun sessionsListKeepsSharedCtasAndInputBarOwnership() {
        val view = UnifiedAiView(themedCtx(), null)

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.wkSessionListContainer).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.wkInputBar).visibility)

        val cta = view.findViewById<WkDualCtaRow>(R.id.wkDualCtaRow)
        cta.clickGhostForTest()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(UnifiedAiView.STATE_NEW_CHAT, view.currentState)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.wkInputBar).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.wkSessionListContainer).visibility)
    }

    @Test
    fun newChatCtaEntersCompactStripAndHidesSessionsState() {
        val view = UnifiedAiView(themedCtx(), null)

        val cta = view.findViewById<WkDualCtaRow>(R.id.wkDualCtaRow)
        cta.clickGhostForTest()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(UnifiedAiView.STATE_NEW_CHAT, view.currentState)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.wkDsHeader).visibility)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.wkInputBar).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.wkDsChatHeader).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.wkBodyContainer).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.wkSessionListContainer).visibility)
    }

    @Test
    fun newChatInputBarSendUsesVisibleInputText() {
        val view = UnifiedAiView(themedCtx(), null)
        val cta = view.findViewById<WkDualCtaRow>(R.id.wkDualCtaRow)
        cta.clickGhostForTest()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val inputBar = view.findViewById<WkInputBar>(R.id.wkInputBar)
        inputBar.setText("Hello")
        inputBar.clickSendForTest()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(UnifiedAiView.STATE_AI_VIEW, view.currentState)
    }

    @Test
    fun screenshotAnalysisResultReEnablesFollowUpInput() {
        val view = UnifiedAiView(themedCtx(), null)

        view.onScreenshotCaptured("/tmp/screen.png")
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val inputBar = view.findViewById<WkInputBar>(R.id.wkInputBar)
        assertFalse(inputBar.getEditText().isEnabled)

        view.onScreenshotAnalysisReceived("/tmp/screen.png", "This screen shows a message.")
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertTrue(inputBar.getEditText().isEnabled)
        assertEquals(View.VISIBLE, inputBar.visibility)
    }
}
