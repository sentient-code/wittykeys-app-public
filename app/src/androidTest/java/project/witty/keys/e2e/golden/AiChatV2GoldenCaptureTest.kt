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
import androidx.test.uiautomator.By
import project.witty.keys.e2e.BaseKeyboardE2ETest

@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AiChatV2GoldenCaptureTest : BaseKeyboardE2ETest() {

    companion object {
        private const val TAG = "AiChatV2Capture"
        private const val SUITE = "ai_chat_v2"
        private const val SETTLE_MS = 1500L
    }

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var goldensDir: String

    @Before
    fun captureSetup() {
        screenshotManager = GoldenScreenshotManager(device)
        goldensDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/$SUITE/captured"
        device.executeShellCommand("mkdir -p $goldensDir")
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("cmd uimode night yes")
        waitForKeyboard()
        device.findObject(By.clazz("android.widget.EditText"))?.click()
        SystemClock.sleep(500)
        SystemClock.sleep(800)
    }

    @After
    fun captureCleanup() {
        sendDebugBroadcast("K_SESSIONS_EMPTY")
        SystemClock.sleep(500)
    }

    private fun captureGolden(goldenName: String) {
        SystemClock.sleep(400)
        val outputPath = "$goldensDir/${goldenName}.png"
        val success = screenshotManager.captureAiChatScreenshot(outputPath)
        assertTrue("Failed to capture: $goldenName", success)
        assertTrue("File not created: $outputPath", screenshotManager.fileExists(outputPath))
        Log.d(TAG, "Captured: $goldenName")
    }

    @Test
    fun capture_K01_new_chat() {
        sendDebugBroadcast("K_NEW_CHAT")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("K01_new_chat")
    }

    @Test
    fun capture_K02_reply_mode() {
        sendDebugBroadcast("K_REPLY_MODE")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("K02_reply_mode")
    }

    @Test
    fun capture_K03_sessions_empty() {
        sendDebugBroadcast("K_SESSIONS_EMPTY")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("K03_sessions_empty")
    }

    @Test
    fun capture_K04_sessions_populated() {
        sendDebugBroadcast("K_SESSIONS_POPULATED")
        SystemClock.sleep(SETTLE_MS + 500)
        captureGolden("K04_sessions_populated")
    }

    @Test
    fun capture_K05_ai_view_empty() {
        sendDebugBroadcast("K_AI_VIEW_EMPTY")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("K05_ai_view_empty")
    }

    @Test
    fun capture_K06_ai_view_populated() {
        sendDebugBroadcast("K_AI_VIEW_POPULATED")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("K06_ai_view_populated")
    }

    @Test
    fun capture_K07_ai_view_loading() {
        sendDebugBroadcast("K_AI_VIEW_LOADING")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("K07_ai_view_loading")
    }

    @Test
    fun capture_K08_ai_view_screenshot() {
        sendDebugBroadcast("K_AI_VIEW_SCREENSHOT")
        SystemClock.sleep(SETTLE_MS)
        captureGolden("K08_ai_view_screenshot")
    }
}
