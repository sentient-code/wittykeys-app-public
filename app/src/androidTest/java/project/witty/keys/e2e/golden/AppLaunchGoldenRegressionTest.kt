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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import project.witty.keys.debug.LaunchStateActivity

/**
 * Regression suite for the 37 non-onboarding, non-held launch states.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppLaunchGoldenRegressionTest {

    private lateinit var device: UiDevice
    private lateinit var screenshotManager: GoldenScreenshotManager
    private val comparator = PixelDiffComparator(pixelTolerance = 30, areaThreshold = 0.75)

    companion object {
        private const val TAG = "AppLaunchGoldenRegression"
        private const val ACTIVITY_LAUNCH_MS = 1500L

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
    fun regressionSetup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        screenshotManager = GoldenScreenshotManager(device)
        device.wakeUp()
        device.executeShellCommand("input keyevent KEYCODE_MENU")
        device.executeShellCommand("svc power stayon true")
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("cmd uimode night yes")
        STATES.map { it.suite }.distinct().forEach { suite ->
            device.executeShellCommand("mkdir -p ${GoldenScreenshotManager.GOLDEN_BASE_DIR}/$suite/current")
            device.executeShellCommand("mkdir -p ${GoldenScreenshotManager.GOLDEN_BASE_DIR}/$suite/diffs")
        }
        SystemClock.sleep(1000)
        Log.d(TAG, "App launch regression setup complete")
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

    private fun regressState(state: State) {
        launchState(state)
        val currentPath = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/${state.suite}/current/${state.goldenName}.png"
        val approvedPath = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/${state.suite}/approved/${state.goldenName}.png"
        assertTrue("Failed to capture current ${state.goldenName}", screenshotManager.captureFullScreenshot(currentPath))

        val currentBitmap = screenshotManager.loadGolden(currentPath)
        val approvedBitmap = screenshotManager.loadGolden(approvedPath)
        assertNotNull("Current screenshot missing: $currentPath", currentBitmap)
        assertNotNull("Approved golden missing: $approvedPath", approvedBitmap)

        val result = comparator.compare(approvedBitmap!!, currentBitmap!!)
        if (!result.passed) {
            result.diffBitmap?.let {
                screenshotManager.saveBitmap(it, "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/${state.suite}/diffs/${state.goldenName}_diff.png")
            }
            val sideBySide = comparator.generateSideBySide(approvedBitmap, currentBitmap, result.diffBitmap)
            sideBySide?.let {
                screenshotManager.saveBitmap(it, "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/${state.suite}/diffs/${state.goldenName}_sidebyside.png")
            }
        }
        currentBitmap.recycle()
        approvedBitmap.recycle()
        result.diffBitmap?.recycle()
        device.pressBack()
        SystemClock.sleep(300)

        assertTrue("${state.goldenName} regression FAILED: ${result.message}", result.passed)
    }

    @Test fun regress_01_HM01_anonymous_ready() = regressState(STATES[0])
    @Test fun regress_02_HM02_setup_recovery() = regressState(STATES[1])
    @Test fun regress_03_HM03_quota_low() = regressState(STATES[2])
    @Test fun regress_04_HM04_quota_empty() = regressState(STATES[3])
    @Test fun regress_05_HM05_paid_active() = regressState(STATES[4])
    @Test fun regress_06_HM06_backend_error() = regressState(STATES[5])
    @Test fun regress_07_ST01_hub() = regressState(STATES[6])
    @Test fun regress_08_ST02_overlay() = regressState(STATES[7])
    @Test fun regress_09_ST03_keyboard() = regressState(STATES[8])
    @Test fun regress_10_ST04_ai_usage() = regressState(STATES[9])
    @Test fun regress_11_ST05_privacy_permissions() = regressState(STATES[10])
    @Test fun regress_12_ST06_support() = regressState(STATES[11])
    @Test fun regress_13_SB01_plus_offer() = regressState(STATES[12])
    @Test fun regress_14_SB02_restore() = regressState(STATES[13])
    @Test fun regress_15_SB03_active() = regressState(STATES[14])
    @Test fun regress_16_SB04_expired() = regressState(STATES[15])
    @Test fun regress_17_SB05_billing_error() = regressState(STATES[16])
    @Test fun regress_18_SB06_quota_upgrade() = regressState(STATES[17])
    @Test fun regress_19_ACCT01_signin_reason() = regressState(STATES[18])
    @Test fun regress_20_ACCT02_auth_options() = regressState(STATES[19])
    @Test fun regress_21_ACCT03_profile_anonymous() = regressState(STATES[20])
    @Test fun regress_22_ACCT04_profile_signed_in() = regressState(STATES[21])
    @Test fun regress_23_ACCT05_delete_account() = regressState(STATES[22])
    @Test fun regress_24_ACCT06_logout() = regressState(STATES[23])
    @Test fun regress_25_PR01_privacy_summary() = regressState(STATES[24])
    @Test fun regress_26_PR02_terms() = regressState(STATES[25])
    @Test fun regress_27_PR03_data_controls() = regressState(STATES[26])
    @Test fun regress_28_PM01_keyboard_missing() = regressState(STATES[27])
    @Test fun regress_29_PM02_overlay_missing() = regressState(STATES[28])
    @Test fun regress_30_PM03_accessibility_missing() = regressState(STATES[29])
    @Test fun regress_31_PM04_nls_optional() = regressState(STATES[30])
    @Test fun regress_32_PM05_screen_capture() = regressState(STATES[31])
    @Test fun regress_33_QT01_free_balance() = regressState(STATES[32])
    @Test fun regress_34_QT02_low_balance() = regressState(STATES[33])
    @Test fun regress_35_QT03_empty_balance() = regressState(STATES[34])
    @Test fun regress_36_QT04_plus_balance() = regressState(STATES[35])
    @Test fun regress_37_QT05_action_cost() = regressState(STATES[36])
}
