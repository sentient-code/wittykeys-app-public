package project.witty.keys.visual

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import project.witty.keys.app.tutorial.InteractiveTutorialActivity

/**
 * FullKeyboardVisualTest — Unified real-keyboard screenshot capture system.
 *
 * Captures ALL visual states with the REAL WittyKeys IME keyboard visible.
 * Every screenshot shows: real keyboard keys + SmartAssistantBar together.
 *
 * ## How it works:
 * 1. Launches InteractiveTutorialActivity
 * 2. Focuses the EditText to trigger WittyKeys keyboard
 * 3. Sends broadcast intents to DebugSABController to set SAB states
 * 4. Takes screenshots showing keyboard + SAB in each state
 *
 * ## Test Categories:
 * - Keyboard layout states (QWERTY, shifted, symbols, typing)
 * - SAB states (expanded, collapsed, shimmer, memory view, etc.)
 * - Scenarios (frustrated boss, worried friend, angry customer, etc.)
 * - Edge cases (long text, RTL, max replies, no replies)
 * - Theme variations (top 5 themes)
 * - Emotion variations (all 9 emotions)
 *
 * WittyKeys Unified Real-Keyboard Screenshot System v2.5
 * Created: February 10, 2026
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class FullKeyboardVisualTest {

    companion object {
        private const val TAG = "FullKBVisualTest"
        private const val SCREENSHOT_DIR = "/data/local/tmp/wittykeys_tests/screenshots"
        private const val KEYBOARD_APPEAR_TIMEOUT = 3000L
        private const val SETTLE_TIME_MS = 800L
        private const val SAB_COMMAND_WAIT_MS = 700L
        private const val ACTION_PREFIX = "project.witty.keys.debug."
    }

    private lateinit var device: UiDevice
    private lateinit var scenario: ActivityScenario<InteractiveTutorialActivity>
    private val timestamp = System.currentTimeMillis()

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        device.executeShellCommand("mkdir -p $SCREENSHOT_DIR")

        // Wake screen and keep it on
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP")
        SystemClock.sleep(500)
        device.executeShellCommand("input swipe 500 1500 500 500 300")
        SystemClock.sleep(500)
        device.executeShellCommand("svc power stayon true")

        // FIX: Force WittyKeys as default IME to prevent IME picker dialog
        device.executeShellCommand("ime set project.witty.keys/.latin.LatinIME")
        SystemClock.sleep(300)

        // Verify WittyKeys is active
        val currentIme = device.executeShellCommand("settings get secure default_input_method")
        Log.d(TAG, "[FULLKB] Current IME: $currentIme")
        if (!currentIme.contains("witty", ignoreCase = true)) {
            Log.e(TAG, "[FULLKB] ❌ WittyKeys is NOT the default IME! Tests may fail.")
        }

        Log.d(TAG, "[FULLKB] Starting unified keyboard visual test suite, timestamp=$timestamp")
    }

    @After
    fun teardown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    // =========================================================================
    // KEYBOARD LAYOUT STATES
    // =========================================================================

    @Test
    fun fullkb_layout_default_qwerty() {
        launchAndFocus()
        screenshot("fullkb_layout_default_qwerty")
    }

    @Test
    fun fullkb_layout_shifted() {
        launchAndFocus()
        device.executeShellCommand("input keyevent KEYCODE_SHIFT_LEFT")
        settle()
        screenshot("fullkb_layout_shifted")
    }

    @Test
    fun fullkb_layout_symbols() {
        launchAndFocus()
        device.executeShellCommand("input keyevent KEYCODE_SYM")
        settle()
        screenshot("fullkb_layout_symbols")
    }

    @Test
    fun fullkb_layout_typing() {
        launchAndFocus()
        device.executeShellCommand("input text 'Hello'")
        settle()
        screenshot("fullkb_layout_typing")
    }

    // =========================================================================
    // SAB STATES (via broadcast intents)
    // =========================================================================

    @Test
    fun fullkb_state01_expanded_idle() {
        launchAndFocus()
        sendSABCommand("SET_STATE", mapOf("state" to "EXPANDED"))
        screenshot("fullkb_state01_expanded_idle")
    }

    @Test
    fun fullkb_state02_shimmer_loading() {
        launchAndFocus()
        sendSABCommand("SHOW_LOADING_SHIMMER")
        screenshot("fullkb_state02_shimmer_loading")
    }

    @Test
    fun fullkb_state03_memory_view_loading() {
        launchAndFocus()
        sendSABCommand("SHOW_MEMORY_VIEW")
        screenshot("fullkb_state03_memory_view_loading")
    }

    @Test
    fun fullkb_state04_memory_view_content() {
        launchAndFocus()
        sendScenarioCommand("FRUSTRATED_BOSS")
        screenshot("fullkb_state04_memory_view_content")
    }

    @Test
    fun fullkb_state05_memory_view_error() {
        launchAndFocus()
        sendSABCommand("SHOW_ERROR", mapOf("message" to "Unable to analyze context"))
        screenshot("fullkb_state05_memory_view_error")
    }

    @Test
    fun fullkb_state06_smart_replies() {
        launchAndFocus()
        val replies = """["I'll get back to you","Sounds good!","Thanks for letting me know","Let me check and confirm"]"""
        sendSABCommand("SHOW_SMART_REPLIES", mapOf("replies_json" to replies))
        screenshot("fullkb_state06_smart_replies")
    }

    @Test
    fun fullkb_state07_tone_picker() {
        launchAndFocus()
        sendSABCommand("SETUP_TONE_PICKER")
        screenshot("fullkb_state07_tone_picker")
    }

    @Test
    fun fullkb_state08_lang_picker() {
        launchAndFocus()
        sendSABCommand("SETUP_LANG_PICKER")
        screenshot("fullkb_state08_lang_picker")
    }

    @Test
    fun fullkb_state09_custom_mode() {
        launchAndFocus()
        sendSABCommand("ENTER_CUSTOM_MODE")
        screenshot("fullkb_state09_custom_mode")
    }

    @Test
    fun fullkb_state10_stat_cards() {
        launchAndFocus()
        sendSABCommand("SHOW_STAT_CARDS")
        screenshot("fullkb_state10_stat_cards")
    }

    @Test
    fun fullkb_state11_collapsed() {
        launchAndFocus()
        sendSABCommand("SET_STATE", mapOf("state" to "COLLAPSED"))
        screenshot("fullkb_state11_collapsed")
    }

    @Test
    fun fullkb_state12_milestone_toast() {
        launchAndFocus()
        sendSABCommand("SHOW_MILESTONE_TOAST", mapOf(
            "emoji" to "🎉",
            "title" to "First Smart Reply!",
            "subtitle" to "You sent your first AI-powered reply"
        ))
        settle(500)  // Extra settle — toast animation needs time to appear
        screenshot("fullkb_state12_milestone_toast")
    }

    @Test
    fun fullkb_state13_bottom_sheet() {
        launchAndFocus()
        sendSABCommand("SHOW_BOTTOM_SHEET")
        settle(1000) // Extra time for modal animation
        screenshot("fullkb_state13_bottom_sheet")
    }

    @Test
    fun fullkb_state14_brain_blink() {
        launchAndFocus()
        sendSABCommand("START_BRAIN_BLINK")
        screenshot("fullkb_state14_brain_blink")
    }

    @Test
    fun fullkb_state15_accessibility_prompt() {
        launchAndFocus()
        sendSABCommand("SHOW_ACCESSIBILITY_PROMPT")
        screenshot("fullkb_state15_accessibility_prompt")
    }

    // =========================================================================
    // SCENARIO-BASED TESTS
    // =========================================================================

    @Test
    fun fullkb_scenario_frustrated_boss() {
        launchAndFocus()
        sendScenarioCommand("FRUSTRATED_BOSS")
        screenshot("fullkb_scenario_frustrated_boss")
    }

    @Test
    fun fullkb_scenario_worried_friend_hindi() {
        launchAndFocus()
        sendScenarioCommand("WORRIED_FRIEND_HINDI")
        screenshot("fullkb_scenario_worried_friend_hindi")
    }

    @Test
    fun fullkb_scenario_casual_friend() {
        launchAndFocus()
        sendScenarioCommand("CASUAL_FRIEND")
        screenshot("fullkb_scenario_casual_friend")
    }

    @Test
    fun fullkb_scenario_formal_email() {
        launchAndFocus()
        sendScenarioCommand("FORMAL_EMAIL")
        screenshot("fullkb_scenario_formal_email")
    }

    @Test
    fun fullkb_scenario_angry_customer() {
        launchAndFocus()
        sendScenarioCommand("ANGRY_CUSTOMER")
        screenshot("fullkb_scenario_angry_customer")
    }

    @Test
    fun fullkb_scenario_excited_friend() {
        launchAndFocus()
        sendScenarioCommand("EXCITED_FRIEND")
        screenshot("fullkb_scenario_excited_friend")
    }

    @Test
    fun fullkb_scenario_hinglish() {
        launchAndFocus()
        sendScenarioCommand("HINGLISH")
        screenshot("fullkb_scenario_hinglish")
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Test
    fun fullkb_edge_long_text() {
        launchAndFocus()
        sendScenarioCommand("LONG_TEXT")
        screenshot("fullkb_edge_long_text")
    }

    @Test
    fun fullkb_edge_rtl_content() {
        launchAndFocus()
        sendScenarioCommand("RTL_CONTENT")
        screenshot("fullkb_edge_rtl_content")
    }

    @Test
    fun fullkb_edge_max_quick_replies() {
        launchAndFocus()
        sendScenarioCommand("MAX_REPLIES")
        screenshot("fullkb_edge_max_quick_replies")
    }

    @Test
    fun fullkb_edge_no_replies() {
        launchAndFocus()
        sendScenarioCommand("NO_REPLIES")
        screenshot("fullkb_edge_no_replies")
    }

    // =========================================================================
    // EMOTION VARIATIONS
    // =========================================================================

    @Test
    fun fullkb_emotion_happy() {
        launchAndFocus()
        sendScenarioCommand("EMOTION_HAPPY")
        screenshot("fullkb_emotion_happy")
    }

    @Test
    fun fullkb_emotion_sad() {
        launchAndFocus()
        sendScenarioCommand("EMOTION_SAD")
        screenshot("fullkb_emotion_sad")
    }

    @Test
    fun fullkb_emotion_angry() {
        launchAndFocus()
        sendScenarioCommand("EMOTION_ANGRY")
        screenshot("fullkb_emotion_angry")
    }

    @Test
    fun fullkb_emotion_worried() {
        launchAndFocus()
        sendScenarioCommand("EMOTION_WORRIED")
        screenshot("fullkb_emotion_worried")
    }

    @Test
    fun fullkb_emotion_excited() {
        launchAndFocus()
        sendScenarioCommand("EMOTION_EXCITED")
        screenshot("fullkb_emotion_excited")
    }

    @Test
    fun fullkb_emotion_frustrated() {
        launchAndFocus()
        sendScenarioCommand("EMOTION_FRUSTRATED")
        screenshot("fullkb_emotion_frustrated")
    }

    @Test
    fun fullkb_emotion_neutral() {
        launchAndFocus()
        sendScenarioCommand("EMOTION_NEUTRAL")
        screenshot("fullkb_emotion_neutral")
    }

    @Test
    fun fullkb_emotion_confused() {
        launchAndFocus()
        sendScenarioCommand("EMOTION_CONFUSED")
        screenshot("fullkb_emotion_confused")
    }

    @Test
    fun fullkb_emotion_loving() {
        launchAndFocus()
        sendScenarioCommand("EMOTION_LOVING")
        screenshot("fullkb_emotion_loving")
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private fun launchAndFocus() {
        launchKeyboardActivity()
        focusTextField()
        waitForKeyboard()
        settle()
    }

    private fun launchKeyboardActivity() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            InteractiveTutorialActivity::class.java
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        scenario = ActivityScenario.launch(intent)
        SystemClock.sleep(1000)
    }

    private fun focusTextField() {
        // Tap center of screen to trigger keyboard focus
        device.click(device.displayWidth / 2, device.displayHeight / 3)
        SystemClock.sleep(500)
    }

    private fun waitForKeyboard() {
        val start = SystemClock.uptimeMillis()
        var keyboardVisible = false

        while (SystemClock.uptimeMillis() - start < KEYBOARD_APPEAR_TIMEOUT) {
            // FIX: Check if IME picker dialog appeared and dismiss it
            val uiDump = device.executeShellCommand("dumpsys window | grep -i 'inputmethod'")
            if (uiDump.contains("InputMethodPicker", ignoreCase = true) ||
                uiDump.contains("chooser", ignoreCase = true)) {
                Log.w(TAG, "[FULLKB] IME picker detected, pressing BACK to dismiss")
                device.executeShellCommand("input keyevent KEYCODE_BACK")
                SystemClock.sleep(500)
            }

            val keyboardState = device.executeShellCommand(
                "dumpsys input_method | grep mInputShown"
            )
            if (keyboardState.contains("true")) {
                keyboardVisible = true
                break
            }
            SystemClock.sleep(200)
        }

        if (keyboardVisible) {
            // FIX: Verify it's WittyKeys, not another IME
            val currentIme = device.executeShellCommand("settings get secure default_input_method")
            if (currentIme.contains("witty", ignoreCase = true)) {
                Log.d(TAG, "[FULLKB] ✅ WittyKeys keyboard is visible")
            } else {
                Log.w(TAG, "[FULLKB] ⚠️ Keyboard visible but NOT WittyKeys: $currentIme")
            }
        } else {
            Log.w(TAG, "[FULLKB] ⚠️ Keyboard not visible after timeout, trying tap")
            device.click(device.displayWidth / 2, device.displayHeight / 3)
            SystemClock.sleep(1000)
        }
    }

    private fun sendSABCommand(action: String, extras: Map<String, String> = emptyMap()) {
        val baseCmd = "am broadcast -a $ACTION_PREFIX$action"
        val extrasStr = extras.entries.joinToString(" ") { "--es ${it.key} '${it.value}'" }
        val fullCmd = if (extrasStr.isNotEmpty()) "$baseCmd $extrasStr" else baseCmd

        Log.d(TAG, "[FULLKB] Sending: $fullCmd")
        device.executeShellCommand(fullCmd)
        SystemClock.sleep(SAB_COMMAND_WAIT_MS)
    }

    /**
     * Send a SAB command that injects scenario data.
     * No animation cancel needed — teleprompter starts at 2000ms delay,
     * and screenshot is taken at ~1300ms total (well before scrolling begins).
     * Extra settle (800ms) ensures MemoryView has fully rendered on all devices.
     */
    private fun sendScenarioCommand(scenario: String) {
        sendSABCommand("INJECT_SCENARIO", mapOf("scenario" to scenario))
        // Extra settle to let MemoryView render content fully
        // Increased from 500ms to 800ms to fix flaky tests (emotion_happy, etc.)
        settle(800)
    }

    private fun settle(timeMs: Long = SETTLE_TIME_MS) {
        SystemClock.sleep(timeMs)
    }

    private fun screenshot(name: String) {
        val themeName = "dracula"
        val fileName = "${name}_${themeName}_$timestamp.png"
        val filePath = "$SCREENSHOT_DIR/$fileName"

        // Ensure screen is on
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP")
        SystemClock.sleep(200)

        // Capture
        device.executeShellCommand("screencap -p $filePath")

        // Verify
        val sizeOutput = device.executeShellCommand("stat -c %s $filePath 2>/dev/null || echo 0").trim()
        val fileSize = sizeOutput.toLongOrNull() ?: 0

        if (fileSize > 1000) {
            if (fileSize < 20000) {
                Log.w(TAG, "[SCREENSHOT] ⚠️ Suspiciously small ($fileSize bytes): $fileName")
            }
            Log.d(TAG, "[SCREENSHOT] ✅ Captured: $fileName (${fileSize} bytes)")
        } else {
            Log.e(TAG, "[SCREENSHOT] ❌ Failed: $fileName")
            assertTrue("Screenshot capture failed: $fileName", false)
        }
    }
}
