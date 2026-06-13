package project.witty.keys.ui.chat

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import project.witty.keys.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = WkInputBarTest.StubApp::class)
class WkInputBarTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }
    private fun themedCtx() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun defaultState_captureVisible_sendDisabled() {
        val bar = WkInputBar(themedCtx())
        assertEquals(View.VISIBLE, bar.getCaptureVisibilityForTest())
        assertFalse(bar.isSendActiveForTest())
    }

    @Test
    fun setCaptureEnabledFalse_hidesCapture() {
        val bar = WkInputBar(themedCtx())
        bar.setCaptureEnabled(false)
        assertEquals(View.GONE, bar.getCaptureVisibilityForTest())
    }

    @Test
    fun setText_activatesSendButton() {
        val bar = WkInputBar(themedCtx())
        bar.setText("hello")
        assertTrue(bar.isSendActiveForTest())
    }

    @Test
    fun clearText_deactivatesSendButton() {
        val bar = WkInputBar(themedCtx())
        bar.setText("hi")
        bar.setText("")
        assertFalse(bar.isSendActiveForTest())
    }

    @Test
    fun `getText returns typed text`() {
        val bar = WkInputBar(themedCtx())
        bar.setText("hello world")
        assertEquals("hello world", bar.getText())
    }

    @Test
    fun `clearText empties the input`() {
        val bar = WkInputBar(themedCtx())
        bar.setText("some text")
        bar.clearText()
        assertEquals("", bar.getText())
    }

    @Test
    fun setDisabled_dropsAlpha() {
        val bar = WkInputBar(themedCtx())
        bar.setDisabled(true)
        assertEquals(0.5f, bar.alpha, 0.001f)
        bar.setDisabled(false)
        assertEquals(1.0f, bar.alpha, 0.001f)
    }

    @Test
    fun inputPillLeavesTextPaddingToInternalChatInput() {
        val bar = WkInputBar(themedCtx())
        val pill = bar.findViewById<View>(R.id.wkInputPill)

        assertEquals(0, pill.paddingTop)
        assertEquals(0, pill.paddingBottom)
    }

    @Test
    fun activeInputDoesNotShowInlineClearButton() {
        val bar = WkInputBar(themedCtx())
        val input = bar.getEditText()

        input.activate()
        input.setText("On it — 6pm")

        val clear = findTextView(bar, "✕")
        assertEquals(View.GONE, clear?.visibility)
    }

    @Test
    fun sendClick_firesListener_whenActive() {
        val bar = WkInputBar(themedCtx())
        var sent: String? = null
        bar.setOnSendListener { sent = it }
        bar.setText("go")
        bar.clickSendForTest()
        assertEquals("go", sent)
    }

    @Test
    fun sendClick_doesNotFire_whenInactive() {
        val bar = WkInputBar(themedCtx())
        var sent: String? = null
        bar.setOnSendListener { sent = it }
        bar.clickSendForTest()
        assertNull(sent)
    }

    @Test
    fun captureClick_firesListener() {
        val bar = WkInputBar(themedCtx())
        var clicked = false
        bar.setOnCaptureListener { clicked = true }
        bar.clickCaptureForTest()
        assertTrue(clicked)
    }

    @Test
    fun systemImeMode_routesTextAndSendThroughEditableInput() {
        val bar = WkInputBar(themedCtx())
        var sent: String? = null

        bar.setUseSystemIme(true)
        bar.setOnSendListener { sent = it }
        bar.setText("overlay follow up")
        bar.clickSendForTest()

        assertEquals("overlay follow up", sent)
        assertEquals("overlay follow up", bar.getSystemEditTextForTest()?.text.toString())
        assertTrue(bar.isSystemImeModeForTest())
    }

    @Test
    fun systemImeMode_tappingVisiblePillFocusesSystemInputInsteadOfInternalInput() {
        val bar = WkInputBar(themedCtx())

        bar.setUseSystemIme(true)
        bar.getEditText().performClick()

        assertFalse(bar.getEditText().isActive)
        assertTrue(bar.getSystemEditTextForTest()?.hasFocus() == true)
    }

    @Test
    fun disablingSystemImeModePreservesInternalInputDefault() {
        val bar = WkInputBar(themedCtx())

        bar.setUseSystemIme(true)
        bar.setUseSystemIme(false)
        bar.setText("keyboard chat")

        assertEquals("keyboard chat", bar.getText())
        assertFalse(bar.isSystemImeModeForTest())
    }

    @Test
    fun overlayDarkStyle_makesSystemInputReadableOnOverlaySurface() {
        val ctx = themedCtx()
        val bar = WkInputBar(ctx)

        bar.setUseSystemIme(true)
        bar.setOverlayDarkStyle()

        val editText = bar.getSystemEditTextForTest()
        assertEquals(ContextCompat.getColor(ctx, R.color.wk_overlay_dark_text), editText?.currentTextColor)
        assertEquals(ContextCompat.getColor(ctx, R.color.wk_overlay_dark_text2), editText?.currentHintTextColor)
    }

    @Test
    fun overlayDarkStyle_roundsInputContainerWithoutSquareRootBackground() {
        val bar = WkInputBar(themedCtx())

        bar.setOverlayDarkStyle()

        val rootBg = bar.background as? ColorDrawable
        val container = bar.findViewById<View>(R.id.wkInputBarContainer)
        assertEquals(Color.TRANSPARENT, rootBg?.color)
        assertTrue(container.background is GradientDrawable)
    }

    @Test
    fun defaultInputBarUsesSharedGradientContainer() {
        val bar = WkInputBar(themedCtx())
        val container = bar.findViewById<View>(R.id.wkInputBarContainer)

        assertTrue(container.background is GradientDrawable)
    }

    private fun findTextView(root: View, text: String): TextView? {
        if (root is TextView && root.text?.toString() == text) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findTextView(root.getChildAt(i), text)?.let { return it }
            }
        }
        return null
    }
}
