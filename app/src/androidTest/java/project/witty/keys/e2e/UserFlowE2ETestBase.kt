package project.witty.keys.e2e

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Abstract base class for all user flow E2E tests.
 *
 * Extends BaseKeyboardE2ETest with shared helpers specific to user flow testing:
 * - Dark theme enforcement
 * - Animation cancellation for deterministic assertions
 * - Tone selection via ADB broadcast
 * - Reply loading and verification helpers
 * - Compose area interaction helpers
 *
 * ## Layer A of Frozen E2E Lifecycle
 * These tests cover real user journeys:
 *   message in -> tone select -> get suggestions -> tap reply -> send
 *
 * No screenshot capture — that's handled by the UI golden regression suite.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
abstract class UserFlowE2ETestBase : BaseKeyboardE2ETest() {

    companion object {
        private const val TAG = "UserFlowE2ETestBase"
        private const val ACTION_PREFIX = "project.witty.keys.debug."

        /** Max time to wait for reply chips to appear after tone selection */
        const val REPLY_LOAD_TIMEOUT = 10_000L

        /** Time for SAB to settle after a state broadcast */
        const val SAB_SETTLE_MS = 1_000L

        /** Time for animations to complete after tone selection */
        const val TONE_SETTLE_MS = 1_500L
    }

    @Before
    fun userFlowSetup() {
        Log.d(TAG, "userFlowSetup: starting")

        val accService = "$KEYBOARD_PKG/$KEYBOARD_PKG.app.helpers.ScreenReaderAccessibility"

        // Force-restart accessibility service by toggling setting.
        // After app process recreation (by test framework), the old service instance is stale.
        // Toggle off → on forces Android to restart the service in the current process.
        val services = device.executeShellCommand(
            "settings get secure enabled_accessibility_services"
        ).trim()
        Log.d(TAG, "userFlowSetup: current accessibility services=$services")

        if (!services.contains("project.witty.keys")) {
            // Not enabled at all — enable it
            Log.w("WK_E2E", "Accessibility service not enabled — enabling now")
            device.executeShellCommand(
                "settings put secure enabled_accessibility_services $accService"
            )
            SystemClock.sleep(3000)
        } else {
            // Already enabled but may be stale — toggle off then on
            Log.d(TAG, "userFlowSetup: toggling accessibility service to force restart")
            device.executeShellCommand(
                "settings put secure enabled_accessibility_services null"
            )
            SystemClock.sleep(2000)
            device.executeShellCommand(
                "settings put secure enabled_accessibility_services $accService"
            )
            SystemClock.sleep(3000)
        }

        // Verify it's now enabled
        val servicesAfter = device.executeShellCommand(
            "settings get secure enabled_accessibility_services"
        ).trim()
        Log.d(TAG, "userFlowSetup: accessibility services after toggle=$servicesAfter")

        // Force dark theme (project standard)
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(1000)

        // Ensure keyboard is visible
        assertTrue("Keyboard must be visible for user flow test", waitForKeyboard())
        SystemClock.sleep(STATE_SETTLE_MS)

        // Cancel running animations for deterministic testing
        sendCancelAnimations()
        SystemClock.sleep(ANIMATION_WAIT)

        Log.d(TAG, "userFlowSetup: complete — dark theme, keyboard visible, animations cancelled")
    }

    // ==================== Scenario & State Helpers ====================

    /**
     * Inject a test scenario to trigger SAB with context data.
     * This simulates the user receiving a message in a messaging app.
     */
    protected fun injectScenarioAndWaitForSAB(scenario: String) {
        Log.d(TAG, "injectScenarioAndWaitForSAB: $scenario")
        loadTestScenario(scenario)
        SystemClock.sleep(SAB_SETTLE_MS)

        // Verify SAB is in a content state (not loading/error)
        val mvContainer = findSABElement(RES_MV_CONTAINER)
        val ovContainer = findSABElement(RES_OV_CONTAINER)
        assertTrue(
            "SAB should be visible after scenario injection (MV or OV)",
            mvContainer != null || ovContainer != null
        )
        Log.d(TAG, "injectScenarioAndWaitForSAB: SAB visible (mv=${mvContainer != null}, ov=${ovContainer != null})")
    }

    /**
     * Trigger real AI replies for a scenario via TRIGGER_AI_REPLIES broadcast.
     * This calls the real Claude API and waits for replies to appear.
     */
    protected fun triggerAIRepliesAndWait(scenario: String) {
        Log.d(TAG, "triggerAIRepliesAndWait: $scenario")
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}TRIGGER_AI_REPLIES").apply {
            setPackage(ctx.packageName)
            putExtra("scenario", scenario)
        }
        ctx.sendBroadcast(intent)

        // Wait for reply chips to appear (real API call)
        val repliesFound = device.wait(
            Until.hasObject(By.res(RES_REPLY_SCROLL)),
            REPLY_LOAD_TIMEOUT
        )
        if (repliesFound) {
            SystemClock.sleep(ANIMATION_WAIT) // Let chips render
        }
        Log.d(TAG, "triggerAIRepliesAndWait: repliesFound=$repliesFound")
    }

    // ==================== Tone Selection ====================

    /**
     * Select a tone via the tone picker CTA.
     * Sends SETUP_TONE_PICKER to open picker, then ACTIVATE_TONE to select.
     */
    protected fun selectTone(tone: String) {
        Log.d(TAG, "selectTone: $tone")

        // Open tone picker
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val pickerIntent = Intent("${ACTION_PREFIX}SETUP_TONE_PICKER").apply {
            setPackage(ctx.packageName)
        }
        ctx.sendBroadcast(pickerIntent)
        SystemClock.sleep(ANIMATION_WAIT)

        // Activate the specific tone
        val toneIntent = Intent("${ACTION_PREFIX}ACTIVATE_TONE").apply {
            setPackage(ctx.packageName)
            putExtra("tone", tone.uppercase())
        }
        ctx.sendBroadcast(toneIntent)
        SystemClock.sleep(TONE_SETTLE_MS)

        Log.d(TAG, "selectTone: $tone activated")
    }

    // ==================== Reply Verification ====================

    /**
     * Assert that reply chips have loaded in the SAB with the expected count.
     * @param minCount Minimum number of reply chips expected (default 1)
     * @param timeout Max wait time in ms
     */
    protected fun assertRepliesLoaded(minCount: Int = 1, timeout: Long = REPLY_LOAD_TIMEOUT) {
        Log.d(TAG, "assertRepliesLoaded: waiting for $minCount+ replies (timeout=${timeout}ms)")

        val scrollViewFound = device.wait(
            Until.hasObject(By.res(RES_REPLY_SCROLL)),
            timeout
        )
        assertTrue("Reply scroll view should be visible", scrollViewFound)

        SystemClock.sleep(ANIMATION_WAIT) // Let chips finish rendering

        val chips = getReplyChipTexts()
        assertTrue(
            "Expected at least $minCount reply chips but found ${chips.size}",
            chips.size >= minCount
        )
        Log.d(TAG, "assertRepliesLoaded: found ${chips.size} chips: ${chips.take(3)}...")
    }

    /**
     * Assert SAB is visible (either MemoryView or OriginalView container present).
     */
    protected fun assertSABVisible() {
        val mvContainer = findSABElement(RES_MV_CONTAINER)
        val ovContainer = findSABElement(RES_OV_CONTAINER)
        assertTrue(
            "SAB should be visible (MV or OV container)",
            mvContainer != null || ovContainer != null
        )
    }

    /**
     * Get the text of a reply chip at a given position (0-indexed).
     */
    protected fun getReplyChipText(position: Int): String? {
        val chips = getReplyChipTexts()
        return if (position < chips.size) chips[position] else null
    }

    /**
     * Tap a reply chip at a given position (0-indexed).
     */
    protected fun tapReplyAtPosition(position: Int) {
        Log.d(TAG, "tapReplyAtPosition: $position")
        val scrollView = device.wait(Until.findObject(By.res(RES_REPLY_SCROLL)), SAB_TIMEOUT)
        assertNotNull("Reply scroll view not found", scrollView)

        val chips = scrollView!!.findObjects(By.clazz("android.widget.TextView"))
        val textChips = chips.filter { it.text?.isNotBlank() == true }

        assertTrue(
            "Position $position out of bounds (${textChips.size} chips available)",
            position < textChips.size
        )

        textChips[position].click()
        Log.d(TAG, "tapReplyAtPosition: clicked chip at $position: '${textChips[position].text}'")
        SystemClock.sleep(ANIMATION_WAIT)
    }

    /**
     * Verify that a reply was inserted into the compose/edit text area.
     * Checks that edit text is non-empty after tapping a reply chip.
     */
    protected fun verifyReplyInserted() {
        val content = getEditTextContent()
        assertTrue(
            "Edit text should contain inserted reply text but was empty",
            content.isNotBlank()
        )
        Log.d(TAG, "verifyReplyInserted: content='${content.take(50)}...'")
    }

    /**
     * Clear the compose area text by selecting all and deleting.
     */
    protected fun clearCompose() {
        // Use shell command to clear focused text field
        device.executeShellCommand("input keyevent KEYCODE_MOVE_HOME")
        device.executeShellCommand("input keyevent --longpress KEYCODE_DEL")
        // Select all + delete as fallback
        device.executeShellCommand("input keyevent 29 --meta 4096") // Ctrl+A
        device.executeShellCommand("input keyevent KEYCODE_DEL")
        SystemClock.sleep(TRANSITION_WAIT)
        Log.d(TAG, "clearCompose: done")
    }

    // ==================== Smart Reply Flow Helpers ====================

    /**
     * Show smart replies for a scenario by injecting it and showing the OV expanded state.
     */
    protected fun showSmartRepliesForScenario(scenario: String) {
        Log.d(TAG, "showSmartRepliesForScenario: $scenario")
        val ctx: Context = ApplicationProvider.getApplicationContext()

        // Inject the scenario data
        loadTestScenario(scenario)
        SystemClock.sleep(SAB_SETTLE_MS)

        // Show smart replies (OV with reply chips)
        val intent = Intent("${ACTION_PREFIX}SHOW_SMART_REPLIES").apply {
            setPackage(ctx.packageName)
        }
        ctx.sendBroadcast(intent)
        SystemClock.sleep(SAB_SETTLE_MS)
    }

    // ==================== Utility ====================

    /**
     * Send CANCEL_ANIMATIONS broadcast to stabilize UI for assertions.
     */
    private fun sendCancelAnimations() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}CANCEL_ANIMATIONS").apply {
            setPackage(ctx.packageName)
        }
        ctx.sendBroadcast(intent)
    }

    /**
     * Force SAB back to initial collapsed state for test isolation.
     */
    protected fun resetSABState() {
        Log.d(TAG, "resetSABState: collapsing SAB")
        forceState("OV_COLLAPSED")
        SystemClock.sleep(STATE_SETTLE_MS)
    }
}
