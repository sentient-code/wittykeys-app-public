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
 * Captures 10 golden screenshots matching the 10 Full-Screen Chat states
 * defined in WittyKeys_UI_Mockup.html (fs-empty … fs-capture-loading).
 *
 * Each test launches AiChatActivity via debug broadcast → waits for settle →
 * captures full-screen screenshot → saves to device.
 *
 * Output: /data/local/tmp/wittykeys_goldens/fullscreen_chat/captured/
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FullscreenChatGoldenCaptureTest : BaseKeyboardE2ETest() {

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var goldensDir: String

    companion object {
        private const val TAG = "FSChatGoldenCapture"
        private const val ACTION_PREFIX = "project.witty.keys.debug."
        private const val ACTIVITY_LAUNCH_MS = 2000L
        private const val SETTLE_MS = 1500L
    }

    @Before
    fun captureSetup() {
        screenshotManager = GoldenScreenshotManager(device)
        goldensDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/fullscreen_chat/captured"
        device.executeShellCommand("mkdir -p $goldensDir")

        // Reset font scale and force dark theme
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(1500)

        assertTrue("Keyboard must be visible for capture", waitForKeyboard())
        SystemClock.sleep(800)
        Log.d(TAG, "Full-screen chat capture setup complete (dark theme)")
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

    // ==================== CAPTURE HELPER ====================

    private fun captureFullscreenGolden(goldenName: String) {
        SystemClock.sleep(400)
        val outputPath = "$goldensDir/${goldenName}.png"
        val success = screenshotManager.captureFullScreenshot(outputPath)
        assertTrue("Failed to capture full-screen golden: $goldenName", success)
        assertTrue("Golden file not created: $outputPath",
            screenshotManager.fileExists(outputPath))
        Log.d(TAG, "Captured: $goldenName")
    }

    private fun finishActivity() {
        // Press back to close AiChatActivity and return to keyboard
        device.pressBack()
        SystemClock.sleep(800)
    }

    // ==================== 10 FULL-SCREEN CHAT GOLDEN STATES ====================

    /** FS01: Empty Session — toolbar + placeholder + input */
    @Test
    fun capture_FS01_empty_session() {
        Log.d(TAG, "FS01: Empty Session")
        sendBroadcast("FS_SHOW_EMPTY")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS)
        captureFullscreenGolden("FS01_empty_session")
        finishActivity()
    }

    /** FS02: Active Chat — multi-turn conversation */
    @Test
    fun capture_FS02_active_chat() {
        Log.d(TAG, "FS02: Active Chat")
        sendBroadcast("FS_SHOW_CHAT")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
        captureFullscreenGolden("FS02_active_chat")
        finishActivity()
    }

    /** FS03: Loading — typing indicator */
    @Test
    fun capture_FS03_loading() {
        Log.d(TAG, "FS03: Loading")
        sendBroadcast("FS_SHOW_LOADING")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
        captureFullscreenGolden("FS03_loading")
        finishActivity()
    }

    /** FS04: Error — error card with retry */
    @Test
    fun capture_FS04_error() {
        Log.d(TAG, "FS04: Error")
        sendBroadcast("FS_SHOW_ERROR")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
        captureFullscreenGolden("FS04_error")
        finishActivity()
    }

    /** FS05: Screenshot Inline — 280x170px thumbnail */
    @Test
    fun capture_FS05_screenshot_inline() {
        Log.d(TAG, "FS05: Screenshot Inline")
        sendBroadcast("FS_SHOW_SCREENSHOT")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
        captureFullscreenGolden("FS05_screenshot_inline")
        finishActivity()
    }

    /** FS06: NLS Context — purple banner + emotion */
    @Test
    fun capture_FS06_nls_context() {
        Log.d(TAG, "FS06: NLS Context")
        sendBroadcast("FS_SHOW_NLS_CONTEXT")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
        captureFullscreenGolden("FS06_nls_context")
        finishActivity()
    }

    /** FS07: Session List — grouped by date */
    @Test
    fun capture_FS07_session_list() {
        Log.d(TAG, "FS07: Session List")
        sendBroadcast("FS_SHOW_SESSION_LIST")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
        captureFullscreenGolden("FS07_session_list")
        finishActivity()
    }

    /** FS08: Session Resumed — old messages at 70% opacity */
    @Test
    fun capture_FS08_session_resumed() {
        Log.d(TAG, "FS08: Session Resumed")
        sendBroadcast("FS_SHOW_SESSION_RESUMED")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS + 2000)
        captureFullscreenGolden("FS08_session_resumed")
        finishActivity()
    }

    /** FS09: Long Conversation — 12 messages */
    @Test
    fun capture_FS09_long_conversation() {
        Log.d(TAG, "FS09: Long Conversation")
        sendBroadcast("FS_SHOW_LONG_CHAT")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS + 2000)
        captureFullscreenGolden("FS09_long_conversation")
        finishActivity()
    }

    /** FS10: Screenshot Analyzing — handoff from compact */
    @Test
    fun capture_FS10_capture_loading() {
        Log.d(TAG, "FS10: Screenshot Analyzing (Handoff)")
        sendBroadcast("FS_SHOW_CAPTURE_ANALYZING")
        SystemClock.sleep(ACTIVITY_LAUNCH_MS + SETTLE_MS)
        captureFullscreenGolden("FS10_capture_loading")
        finishActivity()
    }
}
