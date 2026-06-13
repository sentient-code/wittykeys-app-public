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
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import project.witty.keys.e2e.BaseKeyboardE2ETest

/**
 * Emoji Keyboard Golden Capture Test
 *
 * Navigates to each of the 13 emoji keyboard states and captures a screenshot.
 * Each test is fully self-contained with state isolation to prevent contamination.
 *
 * ## State Isolation Strategy:
 * - @Before resets: close emoji keyboard, set light theme, wait for keyboard
 * - Tests run in NAME_ASCENDING order for deterministic sequence
 * - Theme tests (EK12/EK13) tap edit text after theme change to force keyboard back
 *
 * ## Usage:
 * Run all captures:
 *   adb shell "am instrument -w \
 *     -e class project.witty.keys.e2e.golden.EmojiGoldenCaptureTest \
 *     project.witty.keys.test/androidx.test.runner.AndroidJUnitRunner"
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EmojiGoldenCaptureTest : BaseKeyboardE2ETest() {

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var goldensDir: String

    companion object {
        private const val TAG = "EmojiGoldenCapture"
        private const val ACTION_PREFIX = "project.witty.keys.debug."

        private const val STATE_SETTLE_MS = 800L
        private const val MODE_SWITCH_MS = 1200L
        private const val SEARCH_SETTLE_MS = 1000L
        private const val THEME_SETTLE_MS = 2000L
        private const val SHORT_SETTLE_MS = 400L
    }

    @Before
    fun captureSetup() {
        screenshotManager = GoldenScreenshotManager(device)

        goldensDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/emoji/captured"
        device.executeShellCommand("mkdir -p $goldensDir")

        assertTrue("Keyboard must be visible for capture", waitForKeyboard())

        // === STATE ISOLATION: Reset to known baseline ===
        // 1. Force close emoji keyboard → alphabet
        sendBroadcast("CLOSE_EMOJI_KEYBOARD")
        // 2. Reset theme to dark mode (matches HTML mockup — dark theme everywhere)
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(THEME_SETTLE_MS)
        // 3. Tap edit text to ensure keyboard reappears after theme change
        ensureKeyboardVisible()

        Log.d(TAG, "Capture setup complete — keyboard visible, state reset")
    }

    // ==================== BROADCAST HELPERS ====================

    private fun sendBroadcast(action: String, extras: Map<String, String> = emptyMap()) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}$action").apply {
            setPackage(context.packageName)
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        context.sendBroadcast(intent)
        SystemClock.sleep(STATE_SETTLE_MS)
    }

    private fun openEmojiKeyboard() {
        sendBroadcast("SHOW_EMOJI_KEYBOARD")
        SystemClock.sleep(MODE_SWITCH_MS)
    }

    private fun selectCategory(category: String) {
        sendBroadcast("EMOJI_SELECT_CATEGORY", mapOf("category" to category))
        SystemClock.sleep(STATE_SETTLE_MS)
    }

    /**
     * Tap edit text area and wait for keyboard to reappear.
     * Used after theme changes which may dismiss the keyboard.
     */
    private fun ensureKeyboardVisible() {
        // Tap upper area of screen where edit text is
        device.executeShellCommand("input tap 540 200")
        SystemClock.sleep(1000)
        assertTrue("Keyboard must be visible", waitForKeyboard())
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    // ==================== SCREENSHOT HELPER ====================

    private fun captureGolden(goldenName: String) {
        val outputPath = "$goldensDir/${goldenName}.png"
        val success = screenshotManager.captureSABScreenshot(outputPath)
        assertTrue("Failed to capture emoji golden: $goldenName", success)
        assertTrue("Golden file not created: $outputPath",
            screenshotManager.fileExists(outputPath))
    }

    // ==================== EMOJI GOLDEN STATES (13 Tests) ====================

    @Test
    fun capture_EK01_emoji_food_drink() {
        Log.d(TAG, "capture_EK01_emoji_food_drink: starting")
        openEmojiKeyboard()
        selectCategory("Food & Drink")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("EK01_emoji_food_drink")
    }

    @Test
    fun capture_EK02_emoji_recents_empty() {
        Log.d(TAG, "capture_EK02_emoji_recents_empty: starting")
        sendBroadcast("EMOJI_CLEAR_RECENTS")
        SystemClock.sleep(SHORT_SETTLE_MS)
        openEmojiKeyboard()
        selectCategory("Recents")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("EK02_emoji_recents_empty")
    }

    @Test
    fun capture_EK03_emoji_smileys() {
        Log.d(TAG, "capture_EK03_emoji_smileys: starting")
        openEmojiKeyboard()
        selectCategory("Smileys & People")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("EK03_emoji_smileys")
    }

    @Test
    fun capture_EK04_emoji_dating() {
        Log.d(TAG, "capture_EK04_emoji_dating: starting")
        openEmojiKeyboard()
        selectCategory("Dating & Romance")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("EK04_emoji_dating")
    }

    @Test
    fun capture_EK05_emoji_animals() {
        Log.d(TAG, "capture_EK05_emoji_animals: starting")
        openEmojiKeyboard()
        selectCategory("Animals & Nature")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("EK05_emoji_animals")
    }

    @Test
    fun capture_EK06_emoji_search_inactive() {
        Log.d(TAG, "capture_EK06_emoji_search_inactive: starting")
        openEmojiKeyboard()
        selectCategory("Smileys & People")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("EK06_emoji_search_inactive")
    }

    @Test
    fun capture_EK07_emoji_search_active() {
        Log.d(TAG, "capture_EK07_emoji_search_active: starting")
        openEmojiKeyboard()
        sendBroadcast("EMOJI_ACTIVATE_SEARCH")
        SystemClock.sleep(SEARCH_SETTLE_MS)
        captureGolden("EK07_emoji_search_active")
    }

    @Test
    fun capture_EK08_emoji_search_results() {
        Log.d(TAG, "capture_EK08_emoji_search_results: starting")
        openEmojiKeyboard()
        sendBroadcast("EMOJI_ACTIVATE_SEARCH")
        SystemClock.sleep(SHORT_SETTLE_MS)
        sendBroadcast("EMOJI_SEARCH_TEXT", mapOf("query" to "heart"))
        SystemClock.sleep(SEARCH_SETTLE_MS)
        captureGolden("EK08_emoji_search_results")
    }

    @Test
    fun capture_EK09_emoji_search_no_results() {
        Log.d(TAG, "capture_EK09_emoji_search_no_results: starting")
        openEmojiKeyboard()
        sendBroadcast("EMOJI_ACTIVATE_SEARCH")
        SystemClock.sleep(SHORT_SETTLE_MS)
        sendBroadcast("EMOJI_SEARCH_TEXT", mapOf("query" to "xyzabc"))
        SystemClock.sleep(SEARCH_SETTLE_MS)
        captureGolden("EK09_emoji_search_no_results")
    }

    @Test
    fun capture_EK10_emoji_skin_tone() {
        Log.d(TAG, "capture_EK10_emoji_skin_tone: starting")
        openEmojiKeyboard()
        selectCategory("Gestures & Body")
        SystemClock.sleep(STATE_SETTLE_MS)
        sendBroadcast("EMOJI_SHOW_SKIN_TONE")
        SystemClock.sleep(MODE_SWITCH_MS)
        captureGolden("EK10_emoji_skin_tone")
    }

    @Test
    fun capture_EK11_emoji_gif_browse() {
        Log.d(TAG, "capture_EK11_emoji_gif_browse: starting")
        openEmojiKeyboard()
        sendBroadcast("EMOJI_SWITCH_GIF")
        SystemClock.sleep(3000L) // Extra settle for Giphy API
        captureGolden("EK11_emoji_gif_browse")
    }

    @Test
    fun capture_EK12_emoji_dark_theme() {
        Log.d(TAG, "capture_EK12_emoji_dark_theme: starting")
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(THEME_SETTLE_MS)
        // Theme change may dismiss keyboard — force it back
        ensureKeyboardVisible()
        openEmojiKeyboard()
        selectCategory("Smileys & People")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("EK12_emoji_dark_theme")
    }

    @Test
    fun capture_EK13_emoji_light_theme() {
        Log.d(TAG, "capture_EK13_emoji_light_theme: starting")
        device.executeShellCommand("cmd uimode night no")
        SystemClock.sleep(THEME_SETTLE_MS)
        // Theme change may dismiss keyboard — force it back
        ensureKeyboardVisible()
        openEmojiKeyboard()
        selectCategory("Smileys & People")
        SystemClock.sleep(SHORT_SETTLE_MS)
        captureGolden("EK13_emoji_light_theme")
    }
}
