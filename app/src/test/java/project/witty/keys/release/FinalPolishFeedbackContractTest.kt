package project.witty.keys.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class FinalPolishFeedbackContractTest {
    private fun read(path: String) = File(path).readText()

    @Test
    fun `keyboard defaults are quiet and launch home routes plan management to new plus surface`() {
        val configCommon = read("src/main/res/values/config-common.xml")
        val configPhone = read("src/main/res/values/config-per-form-factor.xml")
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")

        assertTrue(configCommon.contains("<bool name=\"config_default_vibration_enabled\">false</bool>"))
        assertTrue(configPhone.contains("<bool name=\"config_default_key_preview_popup\">false</bool>"))
        assertTrue(homeActivity.contains("openSubscriptionLaunchDetail(\"home_upgrade\")"))
        assertTrue(homeActivity.contains("openLaunchDetail(LaunchStateActivity.STATE_SUBSCRIPTION_PLUS_OFFER)"))
        assertFalse(homeActivity.contains("new Intent(this, SubscriptionListingActivity.class)"))
    }

    @Test
    fun `home shows value first and only renders pending setup actions`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val homeLayout = read("src/main/res/layout/activity_home.xml")
        val homeXml = parseXml("src/main/res/layout/activity_home.xml")
        val actionList = findElementByAndroidId(homeXml.documentElement, "@+id/home_action_list")
        val productStage = findElementByAndroidId(homeXml.documentElement, "@+id/home_product_stage")

        assertTrue(homeLayout.contains("@+id/home_action_list"))
        assertTrue(homeLayout.contains("android:id=\"@+id/home_credit_strip\""))
        assertEquals(productStage, actionList?.parentNode)
        assertTrue(homeActivity.contains("stateMark = \"\\u2713\""))
        assertTrue(homeActivity.contains("renderPendingSetupCards("))
        assertTrue(homeActivity.contains("homeActionList.removeAllViews()"))
        assertTrue(homeActivity.contains("setVisible(homeCreditStrip, !state.showSetupRecovery)"))
        assertTrue(homeActivity.contains("if (pendingCount == 0)"))
        assertTrue(homeActivity.contains("SetupChecklistStateProvider.current(this)"))
        assertTrue(homeActivity.contains("SetupChecklistState.ItemId.KEYBOARD_ENABLED"))
        assertTrue(homeActivity.contains("SetupChecklistState.ItemId.OVERLAY_BUBBLE"))
        assertTrue(homeActivity.contains("SetupChecklistState.ItemId.APP_NOTIFICATIONS"))
        assertFalse(homeActivity.contains("featureOverlay.setOnClickListener"))
        assertFalse(homeActivity.contains("featureKeyboard.setOnClickListener"))
    }

    @Test
    fun `home H04 setup complete block uses approved accent check styling`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val homeLayout = read("src/main/res/layout/activity_home.xml")

        assertTrue(homeActivity.contains("setSetupCheck(homeSetupCheck1, \"Overlay\")"))
        assertTrue(homeActivity.contains("SpannableString"))
        assertTrue(homeActivity.contains("ForegroundColorSpan(ContextCompat.getColor(this, R.color.wk_overlay_dark_accent))"))
        assertTrue(homeActivity.contains("RelativeSizeSpan(0.78f)"))
        assertTrue(homeLayout.contains("android:id=\"@+id/home_setup_checks\""))
        assertTrue(homeLayout.contains("android:textSize=\"10sp\""))
    }

    @Test
    fun `debug build can force every approved Home state for device screenshots`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")

        assertTrue(homeActivity.contains("EXTRA_DEBUG_HOME_STATE"))
        assertTrue(homeActivity.contains("BuildConfig.DEBUG"))
        assertTrue(homeActivity.contains("getSharedPreferences(\"wk_debug_home\", MODE_PRIVATE)"))
        assertTrue(homeActivity.contains("debugHomeFixture()"))
        listOf("H01", "H02", "H03", "H04", "H05", "H06").forEach { state ->
            assertTrue("Missing debug fixture for $state", homeActivity.contains("case \"$state\""))
        }
        assertTrue(homeActivity.contains("renderDebugSetupCards"))
        assertTrue(homeActivity.contains("\"Floating overlay\""))
        assertTrue(homeActivity.contains("\"Keyboard configuration\""))
    }

    @Test
    fun `launch detail headers and bottom tabs align with Home and Settings shell`() {
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val themes = read("src/main/res/values/themes.xml")

        assertTrue(themes.contains("<item name=\"android:layout_marginBottom\">20dp</item>"))
        assertTrue(launchDetail.contains("private const val APP_NAV_LAUNCH_BOTTOM_MARGIN_DP = 20"))
        assertTrue(launchDetail.contains("setPadding(dp(17), dp(24), dp(17)"))
    }

    @Test
    fun `settings are simplified with one permission source version footer and safe headers`() {
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val settingsLayout = read("src/main/res/layout/activity_settings_hub.xml")
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val notchHandler = read("src/main/java/project/witty/keys/app/helpers/NotchHandler.java")

        assertTrue(launchDetail.contains("NotchHandler.configureEdgeToEdge(this)"))
        assertTrue(launchDetail.contains("NotchHandler.handleSystemBars(this)"))
        assertTrue(launchDetail.contains("CardItem(\"KBD\", \"Keyboard configuration\""))
        assertTrue(launchDetail.contains("CardItem(\"PERM\", \"Permissions\""))
        assertFalse(launchDetail.contains("setupCard(setup.item(SetupChecklistState.ItemId.OVERLAY_BUBBLE), \"OVR\"),\n                        setupCard(setup.item(SetupChecklistState.ItemId.KEYBOARD_DEFAULT), \"KBD\""))
        assertFalse(launchDetail.contains("CardItem(\"DATA\", \"Data controls\""))
        assertTrue(settingsLayout.contains("@+id/settings_version_text"))
        assertTrue(settingsHub.contains("BuildConfig.VERSION_NAME"))
        assertTrue(settingsHub.contains("BuildConfig.VERSION_CODE"))
        assertTrue(notchHandler.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"))
        assertTrue(notchHandler.contains("WindowInsetsCompat.Type.displayCutout()"))
        assertTrue(notchHandler.contains("WindowInsetsCompat.Type.systemBars()"))
        assertTrue(notchHandler.contains("ViewCompat.setOnApplyWindowInsetsListener"))
        assertTrue(notchHandler.contains("ViewCompat.requestApplyInsets"))
        assertFalse(notchHandler.contains("FLAG_LAYOUT_NO_LIMITS"))
        assertFalse(notchHandler.contains("hide(WindowInsets.Type.statusBars())"))
        assertFalse(notchHandler.contains("SYSTEM_UI_FLAG_FULLSCREEN"))
    }

    @Test
    fun `permission settings return refreshes setup state and exits to Home when granted`() {
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val setupProvider = read("src/main/java/project/witty/keys/app/state/SetupChecklistStateProvider.java")
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")

        assertTrue(launchDetail.contains("pendingPermissionReturnState"))
        assertTrue(launchDetail.contains("override fun onResume()"))
        assertTrue(launchDetail.contains("permissionSatisfied"))
        assertTrue(launchDetail.contains("schedulePermissionReturnRecheck"))
        assertTrue(launchDetail.contains("returnHomeAfterPermissionCompletion"))
        assertTrue(launchDetail.contains("SetupChecklistState.ItemId.NOTIFICATION_ACCESS"))
        assertTrue(launchDetail.contains("SetupChecklistState.ItemId.APP_NOTIFICATIONS"))
        assertTrue(launchDetail.contains("STATE_APP_NOTIFICATION_PERMISSION"))
        assertTrue(homeActivity.contains("requestAppNotificationPermission"))
        assertTrue(homeActivity.contains("Manifest.permission.POST_NOTIFICATIONS"))
        assertTrue(homeActivity.contains("NotificationService.getFCMToken()"))
        assertTrue(launchDetail.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP"))
        assertTrue(setupProvider.contains("NLSPermissionHelper.isNLSEnabled(context)"))
        assertTrue(setupProvider.contains("areAppNotificationsEnabled(context)"))
        assertFalse(setupProvider.contains("\"enabled_notification_listeners\""))
        assertTrue(homeActivity.contains("refreshHomeLaunchStateAfterPermissionSettles"))
        assertTrue(homeActivity.contains("postDelayed(homeSettlingRefreshShort"))
        assertTrue(homeActivity.contains("postDelayed(homeSettlingRefreshLong"))
    }

    @Test
    fun `keyboard picker selection refreshes Home setup state without leaving screen`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")

        assertTrue(homeActivity.contains("Intent.ACTION_INPUT_METHOD_CHANGED"))
        assertTrue(homeActivity.contains("refreshHomeLaunchStateAfterKeyboardSelectionSettles"))
        assertTrue(homeActivity.contains("showInputMethodPicker()"))
        assertTrue(homeActivity.contains("postDelayed(homeSettlingRefreshLong, 3200)"))
    }

    private fun parseXml(path: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))

    private fun findElementByAndroidId(element: Element, id: String): Element? {
        if (element.getAttribute("android:id") == id) {
            return element
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val match = findElementByAndroidId(child, id)
                if (match != null) return match
            }
        }
        return null
    }
}
