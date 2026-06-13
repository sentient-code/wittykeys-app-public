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
import project.witty.keys.debug.LaunchStateActivity

/**
 * Captures the 37 non-onboarding, non-held launch states approved in Gate 1.
 *
 * Together with OnboardingGoldenCaptureTest (10 states), this covers the
 * 47-state non-hold launch scope while keeping Overlay, Keyboard AI, and
 * Sessions on hold.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppLaunchGoldenCaptureTest {

    private lateinit var device: UiDevice
    private lateinit var screenshotManager: GoldenScreenshotManager

    companion object {
        private const val TAG = "AppLaunchGoldenCapture"
        private const val ACTIVITY_LAUNCH_MS = 1500L
        private const val SETTLE_MS = 500L

        private val STATES = listOf(
            State("home", "HM01_anonymous_ready", "hm-anonymous-ready"),
            State("home", "HM02_setup_recovery", "hm-setup-recovery"),
            State("home", "HM03_quota_low", "hm-quota-low"),
            State("home", "HM04_quota_empty", "hm-quota-empty"),
            State("home", "HM05_paid_active", "hm-paid-active"),
            State("home", "HM06_backend_error", "hm-backend-error"),
            State("settings", "ST01_hub", "st-hub"),
            State("settings", "ST02_overlay", "st-overlay"),
            State("settings", "ST03_keyboard", "st-keyboard"),
            State("settings", "ST04_ai_usage", "st-ai-usage"),
            State("settings", "ST05_privacy_permissions", "st-privacy-permissions"),
            State("settings", "ST06_support", "st-support"),
            State("subscription", "SB01_plus_offer", "sb-plus-offer"),
            State("subscription", "SB02_restore", "sb-restore"),
            State("subscription", "SB03_active", "sb-active"),
            State("subscription", "SB04_expired", "sb-expired"),
            State("subscription", "SB05_billing_error", "sb-billing-error"),
            State("subscription", "SB06_quota_upgrade", "sb-quota-upgrade"),
            State("account", "ACCT01_signin_reason", "acct-signin-reason"),
            State("account", "ACCT02_auth_options", "acct-auth-options"),
            State("account", "ACCT03_profile_anonymous", "acct-profile-anonymous"),
            State("account", "ACCT04_profile_signed_in", "acct-profile-signed-in"),
            State("account", "ACCT05_delete_account", "acct-delete-account"),
            State("account", "ACCT06_logout", "acct-logout"),
            State("privacy", "PR01_privacy_summary", "pr-privacy-summary"),
            State("privacy", "PR02_terms", "pr-terms"),
            State("privacy", "PR03_data_controls", "pr-data-controls"),
            State("permissions", "PM01_keyboard_missing", "pm-keyboard-missing"),
            State("permissions", "PM02_overlay_missing", "pm-overlay-missing"),
            State("permissions", "PM03_accessibility_missing", "pm-accessibility-missing"),
            State("permissions", "PM04_nls_optional", "pm-nls-optional"),
            State("permissions", "PM05_screen_capture", "pm-screen-capture"),
            State("quota", "QT01_free_balance", "qt-free-balance"),
            State("quota", "QT02_low_balance", "qt-low-balance"),
            State("quota", "QT03_empty_balance", "qt-empty-balance"),
            State("quota", "QT04_plus_balance", "qt-plus-balance"),
            State("quota", "QT05_action_cost", "qt-action-cost")
        )
    }

    data class State(val suite: String, val goldenName: String, val dataState: String)

    @Before
    fun captureSetup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        screenshotManager = GoldenScreenshotManager(device)

        device.wakeUp()
        SystemClock.sleep(300)
        device.executeShellCommand("input keyevent KEYCODE_MENU")
        device.executeShellCommand("svc power stayon true")
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("cmd uimode night yes")
        STATES.map { it.suite }.distinct().forEach { suite ->
            device.executeShellCommand("mkdir -p ${GoldenScreenshotManager.GOLDEN_BASE_DIR}/$suite/captured")
        }
        SystemClock.sleep(1000)
        Log.d(TAG, "App launch capture setup complete")
    }

    private fun launchState(state: State) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent(context, LaunchStateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(LaunchStateActivity.EXTRA_STATE, state.dataState)
        }
        context.startActivity(intent)
        SystemClock.sleep(ACTIVITY_LAUNCH_MS)
    }

    private fun captureState(state: State) {
        launchState(state)
        SystemClock.sleep(SETTLE_MS)
        val outputPath = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/${state.suite}/captured/${state.goldenName}.png"
        val success = screenshotManager.captureFullScreenshot(outputPath)
        assertTrue("Failed to capture ${state.goldenName}", success)
        assertTrue("Golden file not created: $outputPath", screenshotManager.fileExists(outputPath))
        device.pressBack()
        SystemClock.sleep(300)
    }

    @Test fun capture_01_HM01_anonymous_ready() = captureState(STATES[0])
    @Test fun capture_02_HM02_setup_recovery() = captureState(STATES[1])
    @Test fun capture_03_HM03_quota_low() = captureState(STATES[2])
    @Test fun capture_04_HM04_quota_empty() = captureState(STATES[3])
    @Test fun capture_05_HM05_paid_active() = captureState(STATES[4])
    @Test fun capture_06_HM06_backend_error() = captureState(STATES[5])
    @Test fun capture_07_ST01_hub() = captureState(STATES[6])
    @Test fun capture_08_ST02_overlay() = captureState(STATES[7])
    @Test fun capture_09_ST03_keyboard() = captureState(STATES[8])
    @Test fun capture_10_ST04_ai_usage() = captureState(STATES[9])
    @Test fun capture_11_ST05_privacy_permissions() = captureState(STATES[10])
    @Test fun capture_12_ST06_support() = captureState(STATES[11])
    @Test fun capture_13_SB01_plus_offer() = captureState(STATES[12])
    @Test fun capture_14_SB02_restore() = captureState(STATES[13])
    @Test fun capture_15_SB03_active() = captureState(STATES[14])
    @Test fun capture_16_SB04_expired() = captureState(STATES[15])
    @Test fun capture_17_SB05_billing_error() = captureState(STATES[16])
    @Test fun capture_18_SB06_quota_upgrade() = captureState(STATES[17])
    @Test fun capture_19_ACCT01_signin_reason() = captureState(STATES[18])
    @Test fun capture_20_ACCT02_auth_options() = captureState(STATES[19])
    @Test fun capture_21_ACCT03_profile_anonymous() = captureState(STATES[20])
    @Test fun capture_22_ACCT04_profile_signed_in() = captureState(STATES[21])
    @Test fun capture_23_ACCT05_delete_account() = captureState(STATES[22])
    @Test fun capture_24_ACCT06_logout() = captureState(STATES[23])
    @Test fun capture_25_PR01_privacy_summary() = captureState(STATES[24])
    @Test fun capture_26_PR02_terms() = captureState(STATES[25])
    @Test fun capture_27_PR03_data_controls() = captureState(STATES[26])
    @Test fun capture_28_PM01_keyboard_missing() = captureState(STATES[27])
    @Test fun capture_29_PM02_overlay_missing() = captureState(STATES[28])
    @Test fun capture_30_PM03_accessibility_missing() = captureState(STATES[29])
    @Test fun capture_31_PM04_nls_optional() = captureState(STATES[30])
    @Test fun capture_32_PM05_screen_capture() = captureState(STATES[31])
    @Test fun capture_33_QT01_free_balance() = captureState(STATES[32])
    @Test fun capture_34_QT02_low_balance() = captureState(STATES[33])
    @Test fun capture_35_QT03_empty_balance() = captureState(STATES[34])
    @Test fun capture_36_QT04_plus_balance() = captureState(STATES[35])
    @Test fun capture_37_QT05_action_cost() = captureState(STATES[36])
}
