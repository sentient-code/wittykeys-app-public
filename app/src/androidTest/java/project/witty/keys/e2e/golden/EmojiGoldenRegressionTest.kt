package project.witty.keys.e2e.golden

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.AfterClass
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import project.witty.keys.e2e.BaseKeyboardE2ETest

/**
 * Emoji Keyboard Golden Regression Test
 *
 * Recaptures all 13 emoji keyboard states and compares them against approved
 * reference screenshots using PixelDiffComparator.
 *
 * ## State Isolation (must match EmojiGoldenCaptureTest exactly):
 * - @Before resets: close emoji keyboard, set light theme, wait for keyboard
 * - Tests run in NAME_ASCENDING order (same as capture test)
 * - Theme tests tap edit text after theme change to force keyboard back
 *
 * ## Threshold:
 * - Default: 5.0% pixel area threshold (relaxed for full-screen emoji rendering variance)
 * - Per-pixel tolerance: 30 RGB units per channel
 *
 * ## Usage:
 *   adb shell am instrument -w \
 *     -e class project.witty.keys.e2e.golden.EmojiGoldenRegressionTest \
 *     project.witty.keys.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EmojiGoldenRegressionTest : BaseKeyboardE2ETest() {

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var comparator: PixelDiffComparator
    private lateinit var goldensDir: String
    private lateinit var capturedDir: String
    private lateinit var diffDir: String

    companion object {
        private const val TAG = "EmojiGoldenRegression"
        private const val ACTION_PREFIX = "project.witty.keys.debug."

        private const val STATE_SETTLE_MS = 800L
        private const val MODE_SWITCH_MS = 1200L
        private const val SEARCH_SETTLE_MS = 1000L
        private const val THEME_SETTLE_MS = 2000L
        private const val SHORT_SETTLE_MS = 400L

        private const val DEFAULT_PIXEL_TOLERANCE = 30
        private const val DEFAULT_AREA_THRESHOLD = 5.0

        @AfterClass
        @JvmStatic
        fun restoreDarkTheme() {
            // EK13 sets light mode — restore dark mode for subsequent suites
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            uiAutomation.executeShellCommand("cmd uimode night yes").close()
            SystemClock.sleep(2000)
        }
    }

    @Before
    fun regressionSetup() {
        screenshotManager = GoldenScreenshotManager(device)
        comparator = PixelDiffComparator(
            pixelTolerance = DEFAULT_PIXEL_TOLERANCE,
            areaThreshold = DEFAULT_AREA_THRESHOLD
        )

        val baseDir = GoldenScreenshotManager.GOLDEN_BASE_DIR
        goldensDir = "$baseDir/emoji/approved"
        capturedDir = "$baseDir/emoji/current"
        diffDir = "$baseDir/emoji/diffs"

        device.executeShellCommand("mkdir -p $capturedDir")
        device.executeShellCommand("mkdir -p $diffDir")

        assertTrue("Keyboard must be visible for regression", waitForKeyboard())

        // === STATE ISOLATION: Must match capture test exactly ===
        // 1. Force close emoji keyboard → alphabet
        sendBroadcast("CLOSE_EMOJI_KEYBOARD")
        // 2. Reset theme to dark mode (matches HTML mockup — dark theme everywhere)
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(THEME_SETTLE_MS)
        // 3. Tap edit text to ensure keyboard reappears after theme change
        ensureKeyboardVisible()
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

    private fun ensureKeyboardVisible() {
        device.executeShellCommand("input tap 540 200")
        SystemClock.sleep(1000)
        assertTrue("Keyboard must be visible", waitForKeyboard())
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    // ==================== COMPARISON HELPER ====================

    private fun assertGoldenMatch(goldenName: String, stateSetup: () -> Unit) {
        // 1. Navigate to target state
        stateSetup()

        // 2. Capture current screenshot
        val currentPath = "$capturedDir/${goldenName}_current.png"
        val captureSuccess = screenshotManager.captureSABScreenshot(currentPath)
        assertTrue("Failed to capture current state: $goldenName", captureSuccess)

        // 3. Load approved golden
        val goldenPath = "$goldensDir/${goldenName}.png"
        val goldenBitmap = screenshotManager.loadGolden(goldenPath)
        assertNotNull(
            "Approved emoji golden not found: $goldenPath. " +
            "Run capture + approve first.",
            goldenBitmap
        )

        // 4. Load current screenshot
        val currentBitmap = screenshotManager.loadGolden(currentPath)
        assertNotNull("Failed to load current screenshot: $goldenName", currentBitmap)

        // 5. Compare
        val result = comparator.compare(goldenBitmap!!, currentBitmap!!)

        // 6. Save diff image if there are changes
        if (result.diffBitmap != null) {
            screenshotManager.saveBitmap(result.diffBitmap, "$diffDir/${goldenName}_diff.png")

            val sideBySide = comparator.generateSideBySide(
                goldenBitmap, currentBitmap, result.diffBitmap)
            if (sideBySide != null) {
                screenshotManager.saveBitmap(sideBySide, "$diffDir/${goldenName}_sidebyside.png")
                sideBySide.recycle()
            }
        }

        // 7. Cleanup bitmaps
        goldenBitmap.recycle()
        currentBitmap.recycle()
        result.diffBitmap?.recycle()

        // 8. Assert PASS
        Log.d(TAG, "$goldenName: ${result.message}")
        assertTrue(
            "$goldenName regression FAILED: ${result.message}",
            result.passed
        )
    }

    // ==================== REGRESSION TESTS (13) ====================

    @Test
    fun regress_EK01_emoji_food_drink() = assertGoldenMatch("EK01_emoji_food_drink") {
        openEmojiKeyboard()
        selectCategory("Food & Drink")
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    @Test
    fun regress_EK02_emoji_recents_empty() = assertGoldenMatch("EK02_emoji_recents_empty") {
        sendBroadcast("EMOJI_CLEAR_RECENTS")
        SystemClock.sleep(SHORT_SETTLE_MS)
        openEmojiKeyboard()
        selectCategory("Recents")
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    @Test
    fun regress_EK03_emoji_smileys() = assertGoldenMatch("EK03_emoji_smileys") {
        openEmojiKeyboard()
        selectCategory("Smileys & People")
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    @Test
    fun regress_EK04_emoji_dating() = assertGoldenMatch("EK04_emoji_dating") {
        openEmojiKeyboard()
        selectCategory("Dating & Romance")
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    @Test
    fun regress_EK05_emoji_animals() = assertGoldenMatch("EK05_emoji_animals") {
        openEmojiKeyboard()
        selectCategory("Animals & Nature")
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    @Test
    fun regress_EK06_emoji_search_inactive() = assertGoldenMatch("EK06_emoji_search_inactive") {
        openEmojiKeyboard()
        selectCategory("Smileys & People")
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    @Test
    fun regress_EK07_emoji_search_active() = assertGoldenMatch("EK07_emoji_search_active") {
        openEmojiKeyboard()
        sendBroadcast("EMOJI_ACTIVATE_SEARCH")
        SystemClock.sleep(SEARCH_SETTLE_MS)
    }

    @Test
    fun regress_EK08_emoji_search_results() = assertGoldenMatch("EK08_emoji_search_results") {
        openEmojiKeyboard()
        sendBroadcast("EMOJI_ACTIVATE_SEARCH")
        SystemClock.sleep(SHORT_SETTLE_MS)
        sendBroadcast("EMOJI_SEARCH_TEXT", mapOf("query" to "heart"))
        SystemClock.sleep(SEARCH_SETTLE_MS)
    }

    @Test
    fun regress_EK09_emoji_search_no_results() = assertGoldenMatch("EK09_emoji_search_no_results") {
        openEmojiKeyboard()
        sendBroadcast("EMOJI_ACTIVATE_SEARCH")
        SystemClock.sleep(SHORT_SETTLE_MS)
        sendBroadcast("EMOJI_SEARCH_TEXT", mapOf("query" to "xyzabc"))
        SystemClock.sleep(SEARCH_SETTLE_MS)
    }

    @Test
    fun regress_EK10_emoji_skin_tone() = assertGoldenMatch("EK10_emoji_skin_tone") {
        openEmojiKeyboard()
        selectCategory("Gestures & Body")
        SystemClock.sleep(STATE_SETTLE_MS)
        sendBroadcast("EMOJI_SHOW_SKIN_TONE")
        SystemClock.sleep(MODE_SWITCH_MS)
    }

    @Test
    fun regress_EK11_emoji_gif_browse() = assertGoldenMatch("EK11_emoji_gif_browse") {
        openEmojiKeyboard()
        sendBroadcast("EMOJI_SWITCH_GIF")
        SystemClock.sleep(3000L) // Extra settle for Giphy API
    }

    @Test
    fun regress_EK12_emoji_dark_theme() = assertGoldenMatch("EK12_emoji_dark_theme") {
        // Extra reset: close any leftover GIF/emoji state from EK11
        sendBroadcast("CLOSE_EMOJI_KEYBOARD")
        SystemClock.sleep(STATE_SETTLE_MS)
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(THEME_SETTLE_MS)
        ensureKeyboardVisible()
        openEmojiKeyboard()
        selectCategory("Smileys & People")
        SystemClock.sleep(SHORT_SETTLE_MS)
    }

    @Test
    fun regress_EK13_emoji_light_theme() = assertGoldenMatch("EK13_emoji_light_theme") {
        device.executeShellCommand("cmd uimode night no")
        SystemClock.sleep(THEME_SETTLE_MS)
        ensureKeyboardVisible()
        openEmojiKeyboard()
        selectCategory("Smileys & People")
        SystemClock.sleep(SHORT_SETTLE_MS)
    }
}
