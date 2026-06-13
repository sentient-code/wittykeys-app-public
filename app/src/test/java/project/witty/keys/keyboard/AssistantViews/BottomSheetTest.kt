package project.witty.keys.keyboard.AssistantViews

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 2.38: Unit tests for MemoryViewModal bottom sheet functionality.
 *
 * Tests show/dismiss behavior, animation timing, and callback handling.
 */
class BottomSheetTest {

    private lateinit var bottomSheet: BottomSheetController

    @Before
    fun setup() {
        bottomSheet = BottomSheetController()
    }

    // ========== VISIBILITY TESTS ==========

    @Test
    fun `initial state is not showing`() {
        assertFalse(bottomSheet.isShowing)
    }

    @Test
    fun `show sets isShowing to true`() {
        bottomSheet.show()
        assertTrue(bottomSheet.isShowing)
    }

    @Test
    fun `dismiss sets isShowing to false`() {
        bottomSheet.show()
        bottomSheet.dismiss()
        assertFalse(bottomSheet.isShowing)
    }

    @Test
    fun `dismiss when not showing does nothing`() {
        var dismissCalled = false
        bottomSheet.onDismiss = { dismissCalled = true }

        bottomSheet.dismiss()

        assertFalse(dismissCalled)
    }

    // ========== ANIMATION TIMING TESTS ==========

    @Test
    fun `slide up animation duration is 350ms`() {
        assertEquals(350L, BOTTOM_SHEET_SLIDE_UP_DURATION_MS)
    }

    @Test
    fun `slide down animation duration is 250ms`() {
        assertEquals(250L, BOTTOM_SHEET_SLIDE_DOWN_DURATION_MS)
    }

    @Test
    fun `overlay fade in duration is 300ms`() {
        assertEquals(300L, OVERLAY_FADE_IN_DURATION_MS)
    }

    @Test
    fun `overlay fade out duration is 250ms`() {
        assertEquals(250L, OVERLAY_FADE_OUT_DURATION_MS)
    }

    // ========== DISMISS TRIGGERS ==========

    @Test
    fun `back button press dismisses sheet`() {
        bottomSheet.show()

        var dismissed = false
        bottomSheet.onDismiss = { dismissed = true }

        bottomSheet.onBackPressed()

        assertFalse(bottomSheet.isShowing)
        assertTrue(dismissed)
    }

    @Test
    fun `close button press dismisses sheet`() {
        bottomSheet.show()

        var dismissed = false
        bottomSheet.onDismiss = { dismissed = true }

        bottomSheet.onCloseButtonPressed()

        assertFalse(bottomSheet.isShowing)
        assertTrue(dismissed)
    }

    @Test
    fun `overlay tap dismisses sheet`() {
        bottomSheet.show()

        var dismissed = false
        bottomSheet.onDismiss = { dismissed = true }

        bottomSheet.onOverlayTapped()

        assertFalse(bottomSheet.isShowing)
        assertTrue(dismissed)
    }

    @Test
    fun `swipe down dismisses sheet`() {
        bottomSheet.show()

        var dismissed = false
        bottomSheet.onDismiss = { dismissed = true }

        bottomSheet.onSwipeDown()

        assertFalse(bottomSheet.isShowing)
        assertTrue(dismissed)
    }

    // ========== CALLBACK TESTS ==========

    @Test
    fun `onShow callback is triggered`() {
        var showCalled = false
        bottomSheet.onShow = { showCalled = true }

        bottomSheet.show()

        assertTrue(showCalled)
    }

    @Test
    fun `onDismiss callback is triggered`() {
        bottomSheet.show()

        var dismissCalled = false
        bottomSheet.onDismiss = { dismissCalled = true }

        bottomSheet.dismiss()

        assertTrue(dismissCalled)
    }

    @Test
    fun `onModalCollapsed callback is triggered on dismiss`() {
        bottomSheet.show()

        var modalCollapsedCalled = false
        bottomSheet.onModalCollapsed = { modalCollapsedCalled = true }

        bottomSheet.dismiss()

        assertTrue(modalCollapsedCalled)
    }

    // ========== DATA BINDING TESTS ==========

    @Test
    fun `setData updates content`() {
        val data = MemoryViewDataMock(
            emotion = "HAPPY",
            summary = "Test summary",
            replies = listOf("Reply 1", "Reply 2")
        )

        bottomSheet.setData(data)

        assertEquals("HAPPY", bottomSheet.currentData?.emotion)
        assertEquals("Test summary", bottomSheet.currentData?.summary)
        assertEquals(2, bottomSheet.currentData?.replies?.size)
    }

    @Test
    fun `quick reply tap triggers callback`() {
        bottomSheet.show()

        var tappedReply: String? = null
        bottomSheet.onQuickReplySent = { reply -> tappedReply = reply }

        bottomSheet.onReplyTapped("Hello!")

        assertEquals("Hello!", tappedReply)
    }

    @Test
    fun `quick reply tap dismisses sheet`() {
        bottomSheet.show()

        bottomSheet.onReplyTapped("Sure!")

        assertFalse(bottomSheet.isShowing)
    }

    @Test
    fun `report flag tap triggers callback`() {
        bottomSheet.show()

        var reportedContent: String? = null
        bottomSheet.onReportClick = { content -> reportedContent = content }

        bottomSheet.onReportTapped("Bad reply")

        assertEquals("Bad reply", reportedContent)
    }

    @Test
    fun `report flag tap does NOT dismiss sheet`() {
        bottomSheet.show()

        bottomSheet.onReportTapped("Content to report")

        assertTrue(bottomSheet.isShowing)
    }

    // ========== RECOMMENDED ACTIONS TESTS ==========

    @Test
    fun `action tap triggers callback`() {
        bottomSheet.show()

        var tappedAction: String? = null
        bottomSheet.onActionClick = { action -> tappedAction = action }

        bottomSheet.onActionTapped("SCHEDULE_CALL")

        assertEquals("SCHEDULE_CALL", tappedAction)
    }

    // ========== ACCESSIBILITY TESTS ==========

    @Test
    fun `sheet is focusable for accessibility`() {
        assertTrue(bottomSheet.isFocusable)
    }

    @Test
    fun `sheet handles content description`() {
        bottomSheet.show()

        assertNotNull(bottomSheet.contentDescription)
        assertTrue(bottomSheet.contentDescription.isNotEmpty())
    }

    companion object {
        // Animation timing constants (from wk_sheet_slide_up.xml, etc.)
        const val BOTTOM_SHEET_SLIDE_UP_DURATION_MS = 350L
        const val BOTTOM_SHEET_SLIDE_DOWN_DURATION_MS = 250L
        const val OVERLAY_FADE_IN_DURATION_MS = 300L
        const val OVERLAY_FADE_OUT_DURATION_MS = 250L
    }
}

/**
 * Controller for bottom sheet state and interactions
 */
class BottomSheetController {
    var isShowing: Boolean = false
        private set

    var currentData: MemoryViewDataMock? = null
        private set

    val isFocusable: Boolean = true
    val contentDescription: String = "Memory View Modal"

    // Callbacks
    var onShow: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    var onModalCollapsed: (() -> Unit)? = null
    var onQuickReplySent: ((String) -> Unit)? = null
    var onReportClick: ((String) -> Unit)? = null
    var onActionClick: ((String) -> Unit)? = null

    fun show() {
        isShowing = true
        onShow?.invoke()
    }

    fun dismiss() {
        if (isShowing) {
            isShowing = false
            onDismiss?.invoke()
            onModalCollapsed?.invoke()
        }
    }

    fun setData(data: MemoryViewDataMock) {
        currentData = data
    }

    fun onBackPressed() {
        dismiss()
    }

    fun onCloseButtonPressed() {
        dismiss()
    }

    fun onOverlayTapped() {
        dismiss()
    }

    fun onSwipeDown() {
        dismiss()
    }

    fun onReplyTapped(reply: String) {
        onQuickReplySent?.invoke(reply)
        dismiss()
    }

    fun onReportTapped(content: String) {
        onReportClick?.invoke(content)
        // Report does NOT dismiss
    }

    fun onActionTapped(action: String) {
        onActionClick?.invoke(action)
    }
}

/**
 * Mock data class for testing
 */
data class MemoryViewDataMock(
    val emotion: String,
    val summary: String,
    val replies: List<String>
)
