package project.witty.keys.e2e.golden

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import project.witty.keys.e2e.BaseKeyboardE2ETest

/**
 * Golden Capture Test — S2.5
 *
 * Navigates to each of the 32 golden states and captures a keyboard+SAB screenshot.
 * Extends BaseKeyboardE2ETest which launches InteractiveTutorialActivity and
 * waits for keyboard visibility.
 *
 * ## 30 Goldens mapped 1:1 to WittyKeys_UI_Mockup.html states:
 * - G01-G04: MemoryView states (mv_loading, mv_content, mv_hinglish, mv_error)
 * - G05-G11: OriginalView primary states
 * - G12-G20: CTA interaction states
 * - G21-G25: Tone variants (professional, casual, savage, sarcastic, calm)
 * - G26-G28: Special states (milestone, brain_blink, stats)
 * - G29-G30: Bottom sheets (full screen capture)
 *
 * ## Usage:
 * Run all captures:
 *   adb shell "am instrument -w \
 *     -e class project.witty.keys.e2e.golden.GoldenCaptureTest \
 *     project.witty.keys.test/androidx.test.runner.AndroidJUnitRunner"
 *
 * Run single golden:
 *   adb shell "am instrument -w \
 *     -e class 'project.witty.keys.e2e.golden.GoldenCaptureTest#capture_G01_mv_loading' \
 *     project.witty.keys.test/androidx.test.runner.AndroidJUnitRunner"
 *
 * ## Broadcast Format:
 * Implicit broadcasts only (no -n flag) — DebugSABController is dynamically
 * registered at runtime, not in manifest.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GoldenCaptureTest : BaseKeyboardE2ETest() {

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var goldensDir: String

    companion object {
        private const val TAG = "GoldenCapture"
        private const val ACTION_PREFIX = "project.witty.keys.debug."

        private const val STATE_SETTLE_MS = 800L
        private const val SCENARIO_SETTLE_MS = 1200L
        private const val SHORT_SETTLE_MS = 400L
    }

    @Before
    fun captureSetup() {
        screenshotManager = GoldenScreenshotManager(device)

        goldensDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/sab/captured"
        device.executeShellCommand("mkdir -p $goldensDir")

        // Force dark theme — golden pipeline requires dark theme everywhere
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(1500)

        assertTrue("Keyboard must be visible for capture", waitForKeyboard())
        SystemClock.sleep(800)

        Log.d(TAG, "Capture setup complete — keyboard visible with SAB (dark theme)")
    }

    // ==================== BROADCAST HELPERS ====================

    /**
     * Send broadcast via Context.sendBroadcast() — avoids shell quoting issues
     * with JSON extras. The test process runs in the same UID as the app (debug),
     * so Context broadcasts reach the dynamically registered DebugSABController.
     */
    private fun sendBroadcast(action: String, extras: Map<String, String> = emptyMap()) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}$action").apply {
            setPackage(context.packageName)
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        context.sendBroadcast(intent)
        SystemClock.sleep(STATE_SETTLE_MS)
    }

    private fun resetToDefault() {
        sendBroadcast("SHOW_ORIGINAL_VIEW")
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    // ==================== SCREENSHOT HELPER ====================

    private fun captureGolden(goldenName: String, fullScreen: Boolean = false) {
        val outputPath = "$goldensDir/${goldenName}.png"

        val success = if (fullScreen) {
            screenshotManager.captureFullScreenshot(outputPath)
        } else {
            screenshotManager.captureSABScreenshot(outputPath)
        }

        assertTrue("Failed to capture golden: $goldenName", success)
        assertTrue("Golden file not created: $outputPath",
            screenshotManager.fileExists(outputPath))
    }

    // ==================== GOLDEN STATES (30 Tests) ====================

    // --- MemoryView States (G01-G04) ---

    @Test
    fun capture_G01_mv_loading() {
        Log.d(TAG, "capture_G01_mv_loading: starting")
        sendBroadcast("SHOW_LOADING_SHIMMER")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("G01_mv_loading")
    }

    @Test
    fun capture_G02_mv_content() {
        Log.d(TAG, "capture_G02_mv_content: starting")
        sendBroadcast("INJECT_SCENARIO", mapOf("scenario" to "CASUAL_FRIEND"))
        SystemClock.sleep(SCENARIO_SETTLE_MS)
        sendBroadcast("CANCEL_ANIMATIONS")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("G02_mv_content")
    }

    @Test
    fun capture_G03_mv_hinglish() {
        Log.d(TAG, "capture_G03_mv_hinglish: starting")
        sendBroadcast("INJECT_SCENARIO", mapOf("scenario" to "HINGLISH"))
        SystemClock.sleep(SCENARIO_SETTLE_MS)
        sendBroadcast("CANCEL_ANIMATIONS")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("G03_mv_hinglish")
    }

    @Test
    fun capture_G04_mv_error() {
        Log.d(TAG, "capture_G04_mv_error: starting")
        sendBroadcast("SHOW_ERROR")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G04_mv_error")
    }

    // --- OriginalView Primary States (G05-G11) ---

    @Test
    fun capture_G05_ov_expanded() {
        Log.d(TAG, "capture_G05_ov_expanded: starting")
        resetToDefault()
        sendBroadcast("SHOW_SMART_REPLIES",
            mapOf("replies_json" to """["Sure!","What time?","Can't make it","Sounds good!"]"""))
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("G05_ov_expanded")
    }

    @Test
    fun capture_G06_ov_collapsed() {
        Log.d(TAG, "capture_G06_ov_collapsed: starting")
        resetToDefault()
        sendBroadcast("COLLAPSE_VIEW")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G06_ov_collapsed")
    }

    @Test
    fun capture_G07_ov_custom() {
        Log.d(TAG, "capture_G07_ov_custom: starting")
        resetToDefault()
        sendBroadcast("ENTER_CUSTOM_MODE")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G07_ov_custom")
    }

    @Test
    fun capture_G08_ov_no_context() {
        Log.d(TAG, "capture_G08_ov_no_context: starting")
        resetToDefault()
        sendBroadcast("SHOW_NO_CONTEXT")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G08_ov_no_context")
    }

    @Test
    fun capture_G09_ov_accessibility() {
        Log.d(TAG, "capture_G09_ov_accessibility: starting")
        resetToDefault()
        sendBroadcast("SHOW_ACCESSIBILITY_PROMPT")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G09_ov_accessibility")
    }

    @Test
    fun capture_G10_ov_row2_loading() {
        Log.d(TAG, "capture_G10_ov_row2_loading: starting")
        resetToDefault()
        sendBroadcast("SHOW_ROW2_SHIMMER")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("G10_ov_row2_loading")
    }

    @Test
    fun capture_G11_ov_error() {
        Log.d(TAG, "capture_G11_ov_error: starting")
        resetToDefault()
        sendBroadcast("SHOW_OV_ERROR")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G11_ov_error")
    }

    // --- CTA Interaction States (G12-G20) ---

    @Test
    fun capture_G12_cta_tone_select() {
        Log.d(TAG, "capture_G12_cta_tone_select: starting")
        resetToDefault()
        sendBroadcast("SETUP_TONE_PICKER")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G12_cta_tone_select")
    }

    @Test
    fun capture_G13_cta_tone_loading() {
        Log.d(TAG, "capture_G13_cta_tone_loading: starting")
        resetToDefault()
        sendBroadcast("SHOW_TONE_LOADING", mapOf("tone" to "PROFESSIONAL"))
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("G13_cta_tone_loading")
    }

    @Test
    fun capture_G14_cta_tone_active() {
        Log.d(TAG, "capture_G14_cta_tone_active: starting")
        resetToDefault()
        sendBroadcast("ACTIVATE_TONE", mapOf("tone" to "PROFESSIONAL"))
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G14_cta_tone_active")
    }

    @Test
    fun capture_G15_cta_tone_custom() {
        Log.d(TAG, "capture_G15_cta_tone_custom: starting")
        resetToDefault()
        sendBroadcast("ENTER_CUSTOM_MODE")
        SystemClock.sleep(STATE_SETTLE_MS)
        sendBroadcast("SETUP_TONE_PICKER")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G15_cta_tone_custom")
    }

    @Test
    fun capture_G16_cta_translate() {
        Log.d(TAG, "capture_G16_cta_translate: starting")
        resetToDefault()
        sendBroadcast("SETUP_LANG_PICKER")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G16_cta_translate")
    }

    @Test
    fun capture_G17_cta_translate_loading() {
        Log.d(TAG, "capture_G17_cta_translate_loading: starting")
        resetToDefault()
        sendBroadcast("SHOW_TRANSLATE_LOADING", mapOf("language" to "HINDI"))
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("G17_cta_translate_loading")
    }

    @Test
    fun capture_G18_cta_translate_active() {
        Log.d(TAG, "capture_G18_cta_translate_active: starting")
        resetToDefault()
        sendBroadcast("SHOW_TRANSLATE_ACTIVE", mapOf("language" to "HINDI"))
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G18_cta_translate_active")
    }

    @Test
    fun capture_G19_cta_translate_custom() {
        Log.d(TAG, "capture_G19_cta_translate_custom: starting")
        resetToDefault()
        sendBroadcast("ENTER_CUSTOM_MODE")
        SystemClock.sleep(STATE_SETTLE_MS)
        sendBroadcast("SETUP_LANG_PICKER")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G19_cta_translate_custom")
    }

    @Test
    fun capture_G20_cta_grammar() {
        Log.d(TAG, "capture_G20_cta_grammar: starting")
        resetToDefault()
        sendBroadcast("SETUP_GRAMMAR_CTA")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G20_cta_grammar")
    }

    // --- Tone Variants (G21-G25) ---

    @Test
    fun capture_G21_tone_professional() {
        Log.d(TAG, "capture_G21_tone_professional: starting")
        resetToDefault()
        sendBroadcast("ACTIVATE_TONE", mapOf("tone" to "PROFESSIONAL"))
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G21_tone_professional")
    }

    @Test
    fun capture_G22_tone_casual() {
        Log.d(TAG, "capture_G22_tone_casual: starting")
        resetToDefault()
        sendBroadcast("ACTIVATE_TONE", mapOf("tone" to "CASUAL"))
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G22_tone_casual")
    }

    @Test
    fun capture_G23_tone_savage() {
        Log.d(TAG, "capture_G23_tone_savage: starting")
        resetToDefault()
        sendBroadcast("ACTIVATE_TONE", mapOf("tone" to "SAVAGE"))
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G23_tone_savage")
    }

    @Test
    fun capture_G24_tone_sarcastic() {
        Log.d(TAG, "capture_G24_tone_sarcastic: starting")
        resetToDefault()
        sendBroadcast("ACTIVATE_TONE", mapOf("tone" to "SARCASTIC"))
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G24_tone_sarcastic")
    }

    @Test
    fun capture_G25_tone_calm() {
        Log.d(TAG, "capture_G25_tone_calm: starting")
        resetToDefault()
        sendBroadcast("ACTIVATE_TONE", mapOf("tone" to "CALM"))
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G25_tone_calm")
    }

    // --- Special States (G26-G28) ---

    @Test
    fun capture_G26_ov_milestone() {
        Log.d(TAG, "capture_G26_ov_milestone: starting")
        resetToDefault()
        sendBroadcast("SHOW_SMART_REPLIES",
            mapOf("replies_json" to """["Sure!","What time?","Can't make it"]"""))
        SystemClock.sleep(SHORT_SETTLE_MS)
        sendBroadcast("SHOW_MILESTONE_TOAST", mapOf(
            "emoji" to "\uD83C\uDF89",
            "title" to "First Reply",
            "subtitle" to "You sent your first AI reply!"))
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G26_ov_milestone")
    }

    @Test
    fun capture_G27_ov_brain_blink() {
        Log.d(TAG, "capture_G27_ov_brain_blink: starting")
        resetToDefault()
        sendBroadcast("SHOW_SMART_REPLIES",
            mapOf("replies_json" to """["Sure!","What time?","Can't make it"]"""))
        SystemClock.sleep(SHORT_SETTLE_MS)
        sendBroadcast("START_BRAIN_BLINK")
        SystemClock.sleep(400)
        captureGolden("G27_ov_brain_blink")
    }

    @Test
    fun capture_G28_ov_stats() {
        Log.d(TAG, "capture_G28_ov_stats: starting")
        resetToDefault()
        sendBroadcast("SHOW_STAT_CARDS")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G28_ov_stats")
    }

    // --- Bottom Sheets (G29-G30) — FULL SCREEN capture ---

    @Test
    fun capture_G29_bs_mv_modal() {
        Log.d(TAG, "capture_G29_bs_mv_modal: starting")
        sendBroadcast("SHOW_BOTTOM_SHEET")
        SystemClock.sleep(2000L)  // Extra wait for PopupWindow animation
        captureGolden("G29_bs_mv_modal", fullScreen = true)
    }

    @Test
    fun capture_G30_bs_acc_consent() {
        Log.d(TAG, "capture_G30_bs_acc_consent: starting")
        resetToDefault()
        sendBroadcast("SHOW_CONSENT_SHEET")
        SystemClock.sleep(2000L)  // Extra wait for PopupWindow animation
        captureGolden("G30_bs_acc_consent", fullScreen = true)
    }

    @Test
    fun capture_G31_ov_contact_picker() {
        Log.d(TAG, "capture_G31_ov_contact_picker: starting")
        sendBroadcast("SHOW_CONTACT_PICKER")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G31_ov_contact_picker")
    }

    // --- Screen Capture CTA (G32) ---

    @Test
    fun capture_G32_capture_cta() {
        Log.d(TAG, "capture_G32_capture_cta: starting")
        resetToDefault()
        sendBroadcast("SHOW_CAPTURE_CTA")
        SystemClock.sleep(STATE_SETTLE_MS)
        captureGolden("G32_capture_cta")
    }
}
