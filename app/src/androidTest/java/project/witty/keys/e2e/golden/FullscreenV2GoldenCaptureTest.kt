package project.witty.keys.e2e.golden

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import project.witty.keys.e2e.BaseKeyboardE2ETest

@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FullscreenV2GoldenCaptureTest : BaseKeyboardE2ETest() {

    companion object {
        private const val TAG = "FullscreenV2Capture"
        private const val SUITE = "ai_chat_v2"
        private const val SETTLE_MS = 2000L
    }

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var goldensDir: String

    @Before
    fun captureSetup() {
        screenshotManager = GoldenScreenshotManager(device)
        goldensDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/$SUITE/captured"
        device.executeShellCommand("mkdir -p $goldensDir")
        device.executeShellCommand("cmd uimode night yes")
        waitForKeyboard()
        SystemClock.sleep(800)
    }

    @After
    fun captureCleanup() {
        device.pressBack()
        SystemClock.sleep(500)
    }

    private fun captureGolden(goldenName: String) {
        SystemClock.sleep(400)
        val outputPath = "$goldensDir/${goldenName}.png"
        val success = screenshotManager.captureFullScreenshot(outputPath)
        assertTrue("Failed to capture: $goldenName", success)
        Log.d(TAG, "Captured: $goldenName")
    }

    @Test fun capture_F01_initial_load() {
        sendDebugBroadcast("FV2_INITIAL_LOAD")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("F01_initial_load")
    }

    @Test fun capture_F02_sessions_empty() {
        sendDebugBroadcast("FV2_SESSIONS_EMPTY")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("F02_sessions_empty")
    }

    @Test fun capture_F03_sessions_populated() {
        sendDebugBroadcast("FV2_SESSIONS_POPULATED")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("F03_sessions_populated")
    }

    @Test fun capture_F04_chat_empty() {
        sendDebugBroadcast("FV2_CHAT_EMPTY")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("F04_chat_empty")
    }

    @Test fun capture_F05_chat_populated() {
        sendDebugBroadcast("FV2_CHAT_POPULATED")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("F05_chat_populated")
    }

    @Test fun capture_F06_chat_error() {
        sendDebugBroadcast("FV2_CHAT_ERROR")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("F06_chat_error")
    }
}
