package project.witty.keys.e2e.golden

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import project.witty.keys.app.tutorial.OnboardingActivity

/**
 * Captures 10 golden screenshots for the Build 7.1 onboarding states (OB01-OB10).
 *
 * Each test launches OnboardingActivity with debug_state Intent extra →
 * waits for settle → captures full-screen screenshot.
 *
 * Output: /data/local/tmp/wittykeys_goldens/onboarding/captured/
 *
 * NOTE: Uses OnboardingActivity (NOT InteractiveTutorialActivity which hosts 69 golden tests).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OnboardingGoldenCaptureTest {

    private lateinit var device: UiDevice
    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var goldensDir: String

    companion object {
        private const val TAG = "OBGoldenCapture"
        private const val ACTIVITY_LAUNCH_MS = 2500L
        private const val SETTLE_MS = 1000L
    }

    @Before
    fun captureSetup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        screenshotManager = GoldenScreenshotManager(device)
        goldensDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/onboarding/captured"
        device.executeShellCommand("mkdir -p $goldensDir")

        // Wake screen and unlock
        device.wakeUp()
        SystemClock.sleep(500)
        device.executeShellCommand("input keyevent KEYCODE_MENU")
        SystemClock.sleep(500)
        // Keep screen on during tests
        device.executeShellCommand("svc power stayon true")

        // Reset font scale and force dark theme
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(1500)

        Log.d(TAG, "Onboarding capture setup complete (dark theme)")
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

    private fun captureGolden(goldenName: String) {
        SystemClock.sleep(400)
        val outputPath = "$goldensDir/${goldenName}.png"
        val success = screenshotManager.captureFullScreenshot(outputPath)
        assertTrue("Failed to capture onboarding golden: $goldenName", success)
        assertTrue("Golden file not created: $outputPath",
            screenshotManager.fileExists(outputPath))
        Log.d(TAG, "Captured: $goldenName")
    }

    private fun finishActivity() {
        device.pressBack()
        SystemClock.sleep(800)
    }

    // ==================== 10 ONBOARDING GOLDEN STATES ====================

    @Test fun capture_OB01_welcome() {
        Log.d(TAG, "OB01: Overlay Quick Replies")
        launchOnboardingWithState("ob-welcome")
        captureGolden("OB01_welcome")
        finishActivity()
    }

    @Test fun capture_OB02_demo_reply() {
        Log.d(TAG, "OB02: Overlay AI Chat")
        launchOnboardingWithState("ob-demo-reply")
        captureGolden("OB02_demo_reply")
        finishActivity()
    }

    @Test fun capture_OB03_demo_scan() {
        Log.d(TAG, "OB03: Keyboard Value")
        launchOnboardingWithState("ob-demo-scan")
        captureGolden("OB03_demo_scan")
        finishActivity()
    }

    @Test fun capture_OB04_enable_keyboard() {
        Log.d(TAG, "OB04: Enable Keyboard")
        launchOnboardingWithState("ob-enable-keyboard")
        captureGolden("OB04_enable_keyboard")
        finishActivity()
    }

    @Test fun capture_OB05_keyboard_done() {
        Log.d(TAG, "OB05: Keyboard Done")
        launchOnboardingWithState("ob-keyboard-done")
        captureGolden("OB05_keyboard_done")
        finishActivity()
    }

    @Test fun capture_OB06_nls_explain() {
        Log.d(TAG, "OB06: NLS Explain")
        launchOnboardingWithState("ob-nls-explain")
        captureGolden("OB06_nls_explain")
        finishActivity()
    }

    @Test fun capture_OB07_nls_granted() {
        Log.d(TAG, "OB07: NLS Granted")
        launchOnboardingWithState("ob-nls-granted")
        captureGolden("OB07_nls_granted")
        finishActivity()
    }

    @Test fun capture_OB08_nls_skipped() {
        Log.d(TAG, "OB08: NLS Skipped")
        launchOnboardingWithState("ob-nls-skipped")
        captureGolden("OB08_nls_skipped")
        finishActivity()
    }

    @Test fun capture_OB09_overlay_intro() {
        Log.d(TAG, "OB09: Overlay Intro")
        launchOnboardingWithState("ob-overlay-intro")
        captureGolden("OB09_overlay_intro")
        finishActivity()
    }

    @Test fun capture_OB10_complete() {
        Log.d(TAG, "OB10: Complete")
        launchOnboardingWithState("ob-complete")
        captureGolden("OB10_complete")
        finishActivity()
    }
}
