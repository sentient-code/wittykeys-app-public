package project.witty.keys.e2e.golden

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import project.witty.keys.app.tutorial.OnboardingActivity
import kotlin.math.abs

/**
 * Regression tests for 10 Build 7.1 Onboarding golden states (OB01-OB10).
 *
 * Compares device screenshots against approved goldens using
 * pixel-level diff with configurable thresholds.
 *
 * Threshold: 30px RGB tolerance, 0.5% pixel difference area.
 * (Same as AI Chat & Full-Screen suites)
 *
 * NOTE: Uses OnboardingActivity (NOT InteractiveTutorialActivity).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OnboardingGoldenRegressionTest {

    private lateinit var device: UiDevice
    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var goldensDir: String
    private lateinit var diffsDir: String

    companion object {
        private const val TAG = "OBGoldenRegression"
        private const val ACTIVITY_LAUNCH_MS = 2500L
        private const val THRESHOLD_PX = 30       // RGB channel tolerance
        private const val THRESHOLD_PCT = 0.5      // Max % of pixels that can differ
    }

    @Before
    fun regressionSetup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        screenshotManager = GoldenScreenshotManager(device)
        goldensDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/onboarding/approved"
        diffsDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/onboarding/diffs"
        device.executeShellCommand("mkdir -p $diffsDir")

        // Wake screen
        device.wakeUp()
        SystemClock.sleep(500)
        device.executeShellCommand("input keyevent KEYCODE_MENU")
        SystemClock.sleep(500)
        device.executeShellCommand("svc power stayon true")

        // Reset font scale and force dark theme
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(1500)

        Log.d(TAG, "Onboarding regression setup complete")
    }

    // ==================== HELPERS ====================

    private fun launchOnboardingWithState(state: String) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent(context, OnboardingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("debug_state", state)
        }
        context.startActivity(intent)
        SystemClock.sleep(ACTIVITY_LAUNCH_MS)
    }

    private fun regressState(state: String, goldenFilename: String) {
        launchOnboardingWithState(state)
        SystemClock.sleep(400)

        // Capture current screenshot
        val currentBitmap = screenshotManager.captureFullBitmap()
        assertNotNull("Failed to capture screenshot for $goldenFilename", currentBitmap)

        // Load approved golden
        val goldenPath = "$goldensDir/${goldenFilename}.png"
        val goldenBitmap = screenshotManager.loadGolden(goldenPath)
        assertNotNull("Golden file not found: $goldenPath", goldenBitmap)

        // Compare
        val diffResult = compareBitmaps(currentBitmap!!, goldenBitmap!!, goldenFilename)

        // Save diff if failed
        if (diffResult.diffPercent > THRESHOLD_PCT) {
            val diffPath = "$diffsDir/${goldenFilename}_diff.png"
            screenshotManager.saveBitmap(diffResult.diffBitmap, diffPath)
            Log.e(TAG, "REGRESSION FAIL: $goldenFilename — ${diffResult.diffPercent}% diff (threshold: $THRESHOLD_PCT%)")
        }

        // Cleanup
        currentBitmap.recycle()
        goldenBitmap.recycle()
        diffResult.diffBitmap.recycle()

        device.pressBack()
        SystemClock.sleep(800)

        assertTrue(
            "REGRESSION FAIL: $goldenFilename — ${diffResult.diffPercent}% diff (threshold: $THRESHOLD_PCT%)",
            diffResult.diffPercent <= THRESHOLD_PCT
        )
    }

    private data class DiffResult(val diffPercent: Double, val diffBitmap: Bitmap)

    private fun compareBitmaps(current: Bitmap, golden: Bitmap, name: String): DiffResult {
        val width = minOf(current.width, golden.width)
        val height = minOf(current.height, golden.height)
        val diffBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        var diffPixels = 0
        val totalPixels = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = current.getPixel(x, y)
                val g = golden.getPixel(x, y)

                val dr = abs(((c shr 16) and 0xFF) - ((g shr 16) and 0xFF))
                val dg = abs(((c shr 8) and 0xFF) - ((g shr 8) and 0xFF))
                val db = abs((c and 0xFF) - (g and 0xFF))

                if (dr > THRESHOLD_PX || dg > THRESHOLD_PX || db > THRESHOLD_PX) {
                    diffPixels++
                    diffBitmap.setPixel(x, y, 0xFFFF0000.toInt()) // Red for diff
                } else {
                    diffBitmap.setPixel(x, y, c)
                }
            }
        }

        val diffPercent = (diffPixels.toDouble() / totalPixels) * 100.0
        Log.d(TAG, "Compare $name: $diffPixels/$totalPixels pixels differ (${String.format("%.2f", diffPercent)}%)")
        return DiffResult(diffPercent, diffBitmap)
    }

    // ==================== 10 ONBOARDING REGRESSION TESTS ====================

    @Test fun regress_OB01_welcome()         { regressState("ob-welcome", "OB01_welcome") }
    @Test fun regress_OB02_demo_reply()      { regressState("ob-demo-reply", "OB02_demo_reply") }
    @Test fun regress_OB03_demo_scan()       { regressState("ob-demo-scan", "OB03_demo_scan") }
    @Test fun regress_OB04_enable_keyboard() { regressState("ob-enable-keyboard", "OB04_enable_keyboard") }
    @Test fun regress_OB05_keyboard_done()   { regressState("ob-keyboard-done", "OB05_keyboard_done") }
    @Test fun regress_OB06_nls_explain()     { regressState("ob-nls-explain", "OB06_nls_explain") }
    @Test fun regress_OB07_nls_granted()     { regressState("ob-nls-granted", "OB07_nls_granted") }
    @Test fun regress_OB08_nls_skipped()     { regressState("ob-nls-skipped", "OB08_nls_skipped") }
    @Test fun regress_OB09_overlay_intro()   { regressState("ob-overlay-intro", "OB09_overlay_intro") }
    @Test fun regress_OB10_complete()        { regressState("ob-complete", "OB10_complete") }
}
