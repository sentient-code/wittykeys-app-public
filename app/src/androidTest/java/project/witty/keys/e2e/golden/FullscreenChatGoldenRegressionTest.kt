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
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import project.witty.keys.e2e.BaseKeyboardE2ETest

/**
 * Recaptures all 10 Full-Screen Chat states and compares them against
 * approved golden baselines. Uses 0.5% pixel diff threshold.
 *
 * Thresholds: Full-Screen Chat → 30 RGB/channel, 0.5% area (same as AI Chat)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FullscreenChatGoldenRegressionTest : BaseKeyboardE2ETest() {

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var comparator: PixelDiffComparator
    private lateinit var goldensDir: String
    private lateinit var capturedDir: String
    private lateinit var diffDir: String

    companion object {
        private const val TAG = "FSChatGoldenRegression"
        private const val ACTION_PREFIX = "project.witty.keys.debug."
        private const val ACTIVITY_LAUNCH_MS = 2000L
        private const val SETTLE_MS = 1500L

        private const val DEFAULT_PIXEL_TOLERANCE = 30
        private const val DEFAULT_AREA_THRESHOLD = 0.5  // NON-NEGOTIABLE — same as AI Chat

        @BeforeClass
        @JvmStatic
        fun setDarkTheme() {
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
        goldensDir = "$baseDir/fullscreen_chat/approved"
        capturedDir = "$baseDir/fullscreen_chat/current"
        diffDir = "$baseDir/fullscreen_chat/diffs"

        device.executeShellCommand("mkdir -p $capturedDir")
        device.executeShellCommand("mkdir -p $diffDir")

        device.executeShellCommand("settings put system font_scale 1.0")

        assertTrue("Keyboard must be visible for regression", waitForKeyboard())
        SystemClock.sleep(800)
        Log.d(TAG, "Full-screen chat regression setup complete")
    }

    @After
    fun regressionCleanup() {
        try {
            // Close any open AiChatActivity
            device.pressBack()
            SystemClock.sleep(500)
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed: ${e.message}")
        }
    }

    // ==================== BROADCAST HELPER ====================

    private fun sendBroadcast(action: String, extras: Map<String, String> = emptyMap()) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}$action").apply {
            setPackage(context.packageName)
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        context.sendBroadcast(intent)
    }

    // ==================== COMPARISON HELPER ====================

    private fun assertGoldenMatch(goldenName: String, stateSetup: () -> Unit) {
        // 1. Navigate to target state
        stateSetup()
        SystemClock.sleep(400)

        // 2. Capture current screenshot (full screen since this is an Activity)
        val currentPath = "$capturedDir/${goldenName}_current.png"
        val captureSuccess = screenshotManager.captureFullScreenshot(currentPath)
        assertTrue("Failed to capture current full-screen state: $goldenName", captureSuccess)

        // 3. Load approved golden
        val goldenPath = "$goldensDir/${goldenName}.png"
        val goldenBitmap = screenshotManager.loadGolden(goldenPath)
        assertNotNull(
            "Approved full-screen golden not found: $goldenPath. Run capture + approve first.",
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

        // 8. Close the activity
        device.pressBack()
        SystemClock.sleep(500)

        // 9. Assert PASS
        Log.d(TAG, "$goldenName: ${result.message}")
        assertTrue(
            "$goldenName regression FAILED: ${result.message}",
            result.passed
        )
    }

    // ==================== REGRESSION TESTS (10) ====================

    @Test fun regress_FS01_empty_session() = assertGoldenMatch("FS01_empty_session") {
        sendBroadcast("FS_SHOW_EMPTY"); SystemClock.sleep(ACTIVITY_LAUNCH_MS)
    }

    @Test fun regress_FS02_active_chat() = assertGoldenMatch("FS02_active_chat") {
        sendBroadcast("FS_SHOW_CHAT"); SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
    }

    @Test fun regress_FS03_loading() = assertGoldenMatch("FS03_loading") {
        sendBroadcast("FS_SHOW_LOADING"); SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
    }

    @Test fun regress_FS04_error() = assertGoldenMatch("FS04_error") {
        sendBroadcast("FS_SHOW_ERROR"); SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
    }

    @Test fun regress_FS05_screenshot_inline() = assertGoldenMatch("FS05_screenshot_inline") {
        sendBroadcast("FS_SHOW_SCREENSHOT"); SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
    }

    @Test fun regress_FS06_nls_context() = assertGoldenMatch("FS06_nls_context") {
        sendBroadcast("FS_SHOW_NLS_CONTEXT"); SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
    }

    @Test fun regress_FS07_session_list() = assertGoldenMatch("FS07_session_list") {
        sendBroadcast("FS_SHOW_SESSION_LIST"); SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
    }

    @Test fun regress_FS08_session_resumed() = assertGoldenMatch("FS08_session_resumed") {
        sendBroadcast("FS_SHOW_SESSION_RESUMED"); SystemClock.sleep(ACTIVITY_LAUNCH_MS + 2000)
    }

    @Test fun regress_FS09_long_conversation() = assertGoldenMatch("FS09_long_conversation") {
        sendBroadcast("FS_SHOW_LONG_CHAT"); SystemClock.sleep(ACTIVITY_LAUNCH_MS + 2000)
    }

    @Test fun regress_FS10_capture_loading() = assertGoldenMatch("FS10_capture_loading") {
        sendBroadcast("FS_SHOW_CAPTURE_ANALYZING"); SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
    }
}
