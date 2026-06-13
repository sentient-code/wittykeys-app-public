package project.witty.keys.e2e.golden

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import project.witty.keys.app.overlay.WittyKeysOverlayService
import project.witty.keys.e2e.BaseKeyboardE2ETest

@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OverlayGoldenCaptureTest : BaseKeyboardE2ETest() {

    companion object {
        private const val TAG = "OverlayCapture"
        private const val SUITE = "ai_chat_v2"
        private const val SETTLE_MS = 1500L
        private const val OVERLAY_DEBUG_ACTION = "project.witty.keys.overlay.DEBUG_STATE"
        private const val RES_BUBBLE = "$KEYBOARD_PKG:id/overlay_bubble_root"
        private const val RES_ACTION_BUTTON = "$KEYBOARD_PKG:id/overlay_btn_screenshot"
        private const val RES_SCREENSHOT_TITLE = "$KEYBOARD_PKG:id/overlay_screenshot_title"
        private const val RES_SCREENSHOT_INPUT = "$KEYBOARD_PKG:id/overlay_input_bar"
        private const val RES_REPLY_TITLE = "$KEYBOARD_PKG:id/overlay_reply_contact_name"
        private const val RES_REPLY_INPUT = "$KEYBOARD_PKG:id/overlay_reply_input"
    }

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var goldensDir: String

    @Before
    fun captureSetup() {
        screenshotManager = GoldenScreenshotManager(device)
        goldensDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/$SUITE/captured"
        device.executeShellCommand("mkdir -p $goldensDir")
        device.executeShellCommand("cmd uimode night yes")
        ensureOverlayService()
        SystemClock.sleep(800)
    }

    @After
    fun captureCleanup() {
        context.stopService(Intent(context, WittyKeysOverlayService::class.java))
        SystemClock.sleep(500)
    }

    private fun ensureOverlayService() {
        device.executeShellCommand("appops set $KEYBOARD_PKG SYSTEM_ALERT_WINDOW allow")
        ContextCompat.startForegroundService(
            context,
            Intent(context, WittyKeysOverlayService::class.java)
        )
        assertTrue(
            "Overlay bubble must be visible before overlay capture",
            device.wait(Until.hasObject(By.res(RES_BUBBLE)), 5000)
        )
    }

    private fun sendOverlayState(
        state: String,
        requiredResource: String,
        requireInput: Boolean = false
    ) {
        context.sendBroadcast(Intent(OVERLAY_DEBUG_ACTION).apply {
            setPackage(KEYBOARD_PKG)
            putExtra("state", state)
        })
        SystemClock.sleep(SETTLE_MS)
        assertTrue(
            "Overlay state '$state' did not render required resource $requiredResource",
            device.wait(Until.hasObject(By.res(requiredResource)), 5000)
        )
        if (requireInput) {
            assertTrue(
                "Overlay state '$state' must show its input bar",
                device.wait(
                    Until.hasObject(By.res(
                        if (requiredResource == RES_REPLY_TITLE) RES_REPLY_INPUT else RES_SCREENSHOT_INPUT
                    )),
                    5000
                )
            )
        }
    }

    private fun captureGolden(goldenName: String) {
        SystemClock.sleep(400)
        val outputPath = "$goldensDir/${goldenName}.png"
        val success = screenshotManager.captureFullScreenshot(outputPath)
        assertTrue("Failed to capture: $goldenName", success)
        Log.d(TAG, "Captured: $goldenName")
    }

    @Test fun capture_O01_bubble_idle() {
        sendOverlayState("bubble_idle", RES_BUBBLE)
        captureGolden("O01_bubble_idle")
    }

    @Test fun capture_O02_popup_empty() {
        sendOverlayState("popup_empty", RES_SCREENSHOT_TITLE)
        captureGolden("O02_popup_empty")
    }

    @Test fun capture_O03_popup_populated() {
        sendOverlayState("popup_populated", RES_SCREENSHOT_TITLE)
        captureGolden("O03_popup_populated")
    }

    @Test fun capture_O04_popup_loading() {
        sendOverlayState("popup_loading", RES_SCREENSHOT_TITLE)
        captureGolden("O04_popup_loading")
    }

    @Test fun capture_O05_popup_chat_empty() {
        sendOverlayState("popup_chat_empty", RES_SCREENSHOT_TITLE, requireInput = true)
        captureGolden("O05_popup_chat_empty")
    }

    @Test fun capture_O06_popup_chat_loading() {
        sendOverlayState("popup_chat_loading", RES_SCREENSHOT_TITLE, requireInput = true)
        captureGolden("O06_popup_chat_loading")
    }

    @Test fun capture_O07_popup_chat_populated() {
        sendOverlayState("popup_chat_populated", RES_SCREENSHOT_TITLE, requireInput = true)
        captureGolden("O07_popup_chat_populated")
    }

    @Test fun capture_O08_action_panel() {
        sendOverlayState("action_panel", RES_ACTION_BUTTON)
        captureGolden("O08_action_panel")
    }

    @Test fun capture_O09_reply_empty() {
        sendOverlayState("reply_empty", RES_REPLY_TITLE, requireInput = true)
        captureGolden("O09_reply_empty")
    }

    @Test fun capture_O10_reply_populated() {
        sendOverlayState("reply_populated", RES_REPLY_TITLE, requireInput = true)
        captureGolden("O10_reply_populated")
    }
}
