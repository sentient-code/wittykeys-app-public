package project.witty.keys.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppLaunchSequenceContractTest {

    private fun read(path: String): String = File(path).takeIf { it.exists() }?.readText().orEmpty()

    private fun assertContainsAll(content: String, values: List<String>) {
        values.forEach { value ->
            assertTrue("Expected content to contain '$value'", content.contains(value))
        }
    }

    private fun assertContainsNone(content: String, values: List<String>) {
        values.forEach { value ->
            assertFalse("Expected content not to contain '$value'", content.contains(value))
        }
    }

    @Test
    fun `master tracker parks overlay and keyboard until app shell account and security surfaces are done`() {
        val plan = read("../docs/superpowers/plans/2026-05-04-wittykeys-complete-launch-revamp-master-plan.md")

        assertContainsAll(
            plan,
            listOf(
                "2026-05-05 sequencing update",
                "Overlay and keyboard implementation are parked until the rest of the Android app is coherent.",
                "Effective Android order: Home and Settings, AI credits and entitlements, Auth/Profile/Subscription, Android privacy and logging hardening, then shared chat, Overlay, and Keyboard.",
                "Do not touch overlay or keyboard UI for visual theme work until the earlier app sections pass their focused checks."
            )
        )
    }

    @Test
    fun `home launch hub does not force keyboard setup or notification permission on entry`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val homeLayout = read("src/main/res/layout/activity_home.xml")
        val homeState = read("src/main/java/project/witty/keys/app/home/HomeLaunchState.java")

        assertContainsAll(
            homeLayout + homeState,
            listOf(
                "@drawable/wk_app_shell_bg",
                "Use AI without signing in.",
                "Finish setup to unlock WittyKeys.",
                "@+id/home_action_list"
            )
        )
        assertContainsNone(
            homeActivity + homeLayout,
            listOf(
                "checkAndRequestNotificationPermission();",
                "PLEASE ENABLE WITTYKEYS KEYBOARD FIRST!!!"
            )
        )
    }

    @Test
    fun `home removes lower legacy overlay toggle and footer link clutter`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val homeLayout = read("src/main/res/layout/activity_home.xml")

        assertContainsAll(
            homeLayout,
            listOf(
                "@+id/home_product_stage",
                "@+id/home_feature_overlay",
                "@+id/home_feature_keyboard",
                "@+id/home_credit_strip"
            )
        )
        assertContainsNone(
            homeActivity + homeLayout,
            listOf(
                "@+id/overlay_toggle_container",
                "@+id/overlay_toggle_switch",
                "Ask AI and Quick Reply from any app",
                "@+id/privacy_policy",
                "@string/terms_and_privacy_text",
                "setupOverlayToggle();",
                "setupOverlayToggle()",
                "syncOverlayToggle();",
                "syncOverlayToggle()",
                "overlayToggleUserAction",
                "androidx.appcompat.widget.SwitchCompat"
            )
        )
    }

    @Test
    fun `home card uses launch theme language instead of legacy pro checklist language`() {
        val homeCard = read("src/main/res/layout/user_card_home.xml")

        assertContainsAll(
            homeCard,
            listOf(
                "Your WittyKeys setup",
                "Use the tools you enabled. Finish the rest anytime.",
                "20 free AI credits/day",
                "Upgrade for more AI credits"
            )
        )
        assertContainsNone(
            homeCard,
            listOf(
                "Steps to become a pro",
                "Ready to write like a genius?",
                "Subscribe now"
            )
        )
    }

    @Test
    fun `production Home uses approved launch hub structure and upgrade credit CTA`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val homeLayout = read("src/main/res/layout/activity_home.xml")
        val homeCard = read("src/main/res/layout/user_card_home.xml")
        val homeState = read("src/main/java/project/witty/keys/app/home/HomeLaunchState.java")

        assertContainsAll(
            homeActivity + homeLayout + homeCard + homeState,
            listOf(
                "HomeLaunchState.from",
                "SIGNED_IN_FREE",
                "@+id/home_headline",
                "@+id/home_state_mark",
                "@+id/home_product_stage",
                "@+id/home_credit_strip",
                "@+id/home_upgrade_button",
                "@drawable/wk_app_card_bg",
                "@drawable/wk_app_card_quiet_bg",
                "@drawable/wk_app_card_hero_bg",
                "Upgrade"
            )
        )
        assertContainsNone(
            homeActivity + homeLayout + homeCard,
            listOf(
                "@drawable/home_graphics",
                "android:src=\"@drawable/home_graphics\"",
                "banner_view",
                "checkAndRequestNotificationPermission();"
            )
        )
    }

    @Test
    fun `production Home breathes with focused hero two action rows and one credit strip`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val homeLayout = read("src/main/res/layout/activity_home.xml")

        assertContainsAll(
            homeActivity + homeLayout,
            listOf(
                "@+id/home_product_stage",
                "@+id/home_action_list",
                "@+id/home_feature_overlay",
                "@+id/home_feature_keyboard",
                "@+id/home_credit_strip",
                "Open Overlay",
                "Ask AI about screens",
                "Keyboard tools",
                "Write better anywhere",
                "handleHomeUsageAction",
                "setVisible(homeCreditStrip, !state.showSetupRecovery)"
            )
        )
        assertContainsNone(
            homeActivity + homeLayout,
            listOf(
                "@+id/home_feature_reply",
                "@+id/home_stage_credit_pill",
                "@+id/home_stage_upgrade_button",
                "homeUpgradeButton.setVisibility(state.showUpgrade",
                "android:text=\"OVR\"",
                "android:text=\"KBD\"",
                "Try now",
                "Typing AI.",
                "Across apps."
            )
        )
    }

    @Test
    fun `Home hero is stable product value preview not permission status panel`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val homeLayout = read("src/main/res/layout/activity_home.xml")
        val homeState = read("src/main/java/project/witty/keys/app/home/HomeLaunchState.java")

        assertContainsAll(
            homeActivity + homeLayout + homeState,
            listOf(
                "@+id/home_product_stage",
                "@+id/home_state_mark",
                "Use AI without signing in.",
                "Finish setup to unlock WittyKeys.",
                "Daily AI limit exhausted.",
                "Ready to use everywhere.",
                "homeStageTitle.setText(state.statusTitle)",
                "homeStageSubtitle.setText(state.statusSubtitle)"
            )
        )
        assertContainsNone(
            homeActivity + homeLayout,
            listOf(
                "stageTitleFor(",
                "stageSubtitleFor(",
                "Permission recovery",
                "Quick Reply ready",
                "Plus workspace",
                "AI service paused",
                "Finish optional setup from Settings",
                "Across WhatsApp, Instagram, Google Chat, Telegram",
                "Priya - WhatsApp"
            )
        )
    }

    @Test
    fun `release Home is not a legacy toolbar variant of the approved Gate 3 Home`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val homeLayout = read("src/main/res/layout/activity_home.xml")

        assertContainsAll(
            homeLayout,
            listOf(
                "@+id/home_top_identity",
                "Anonymous mode",
                "@+id/home_feature_overlay",
                "Open Overlay",
                "@+id/home_feature_keyboard",
                "Keyboard tools",
                "@+id/home_bottom_nav",
                "Usage"
            )
        )
        assertContainsNone(
            homeActivity + homeLayout,
            listOf(
                "showLogo(true)",
                "setToolbarTitle(\"WittyKeys\")",
                "showSettingIcon(true",
                "<include layout=\"@layout/action_bar\"",
                "@+id/fab_share",
                "@+id/fab_whatsapp",
                "@+id/fab_mail",
                "@+id/onboarding_card_include"
            )
        )
    }

    @Test
    fun `launch shell uses icon back buttons and Home has no overlay logo marker art`() {
        val authLayout = read("src/main/res/layout/activity_authentication.xml")
        val homeLayout = read("src/main/res/layout/activity_home.xml")
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")

        assertContainsAll(
            authLayout + launchDetail,
            listOf(
                "@+id/auth_back_button",
                "android:src=\"@drawable/ic_wk_back\"",
                "ImageButton(this@LaunchStateActivity)",
                "setImageResource(R.drawable.ic_wk_back)"
            )
        )
        assertContainsAll(
            homeLayout,
            listOf(
                "@+id/home_product_stage",
                "@+id/home_state_mark",
                "Use AI without signing in."
            )
        )
        assertContainsNone(
            authLayout + launchDetail + homeLayout,
            listOf(
                "@+id/home_stage_overlay_bubble",
                "@+id/home_stage_overlay_logo",
                "@+id/home_stage_overlay_badge",
                "@drawable/wk_logo_overlay",
                "@drawable/overlay_action_badge_bg",
                "AI overlay preview",
                "contentDescription=\"Overlay\"",
                "android:text=\"&lt;\"",
                "text = \"<\"",
                "android:layout_width=\"30dp\"\n                            android:layout_height=\"30dp\"",
                "android:text=\"4\""
            )
        )
    }

    @Test
    fun `settings hub exists as app level navigation without replacing legacy keyboard settings`() {
        val manifest = read("src/main/AndroidManifest.xml")
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val settingsLayout = read("src/main/res/layout/activity_settings_hub.xml")

        assertContainsAll(
            manifest,
            listOf(
                ".app.settings.SettingsHubActivity",
                ".latin.settings.SettingsActivity"
            )
        )
        assertContainsAll(
            homeActivity,
            listOf(
                "project.witty.keys.app.settings.SettingsHubActivity",
                "new Intent(v.getContext(), SettingsHubActivity.class)"
            )
        )
        assertContainsAll(
            settingsHub,
            listOf(
                "openKeyboardSettings",
                "STATE_SUBSCRIPTION_PLUS_OFFER",
                "openAccountLaunchDetail",
                "openLaunchDetail"
            )
        )
        assertContainsAll(
            settingsLayout,
            listOf(
                "@drawable/wk_app_shell_bg",
                "Account",
                "Subscription",
                "AI usage",
                "App setup",
                "Help &amp; privacy"
            )
        )
    }

    @Test
    fun `settings hub uses launch shell header instead of legacy toolbar`() {
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val settingsLayout = read("src/main/res/layout/activity_settings_hub.xml")

        assertContainsAll(
            settingsLayout,
            listOf(
                "@+id/settings_top_identity",
                "@+id/settings_profile_button",
                "Account, plan, setup, help",
                "@drawable/wk_logo_rounded_square_bg",
                "@drawable/wk_app_card_quiet_bg"
            )
        )
        assertContainsAll(
            settingsHub,
            listOf(
                "bindRow(R.id.settings_profile_button, this::openAccountLaunchDetail)"
            )
        )
        assertContainsNone(
            settingsHub + settingsLayout,
            listOf(
                "<include layout=\"@layout/action_bar\"",
                "setupToolbar();",
                "showBackButton(true);",
                "setToolbarTitle(\"Settings\")"
            )
        )
    }

    @Test
    fun `production settings hub is grouped and uses icons instead of letter badges`() {
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val settingsLayout = read("src/main/res/layout/activity_settings_hub.xml")
        val themes = read("src/main/res/values/themes.xml")

        assertContainsAll(
            settingsHub + settingsLayout + themes,
            listOf(
                "settings_app_setup_row",
                "settings_help_privacy_row",
                "openLaunchDetail(LaunchStateActivity.STATE_APP_SETUP)",
                "openLaunchDetail(LaunchStateActivity.STATE_SUPPORT)",
                "WkSettingsIconFrame",
                "WkSettingsIconImage",
                "WkSettingsValue",
                "@drawable/wk_app_card_bg"
            )
        )
        assertContainsNone(
            settingsLayout,
            listOf(
                "android:text=\"OV\"",
                "android:text=\"KB\"",
                "android:text=\"AI\"",
                "android:text=\"PL\"",
                "android:text=\"AC\"",
                "android:text=\"PR\"",
                "android:text=\"SP\"",
                "settings_permission_row",
                "settings_privacy_row",
                "settings_support_row",
                "AI and account",
                "Trust"
            )
        )
    }

    @Test
    fun `settings detail screens do not show a fake search strip`() {
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")

        assertContainsNone(
            launchDetail,
            listOf(
                "Search settings, permissions, credits",
                "searchBox("
            )
        )
    }

    @Test
    fun `settings uses five simple user choices with setup and help grouped inside`() {
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val settingsLayout = read("src/main/res/layout/activity_settings_hub.xml")
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val launchHeaderAndActions = launchDetail.substringBefore("private fun homeStates()")
        val settingsStatesOnly = launchDetail
            .substringAfter("private fun settingsStates()")
            .substringBefore("private fun subscriptionStates()")
        val combined = settingsHub + settingsLayout + launchHeaderAndActions + settingsStatesOnly

        assertContainsAll(
            combined,
            listOf(
                "@+id/settings_account_row",
                "@+id/settings_subscription_row",
                "@+id/settings_ai_usage_row",
                "@+id/settings_app_setup_row",
                "@+id/settings_help_privacy_row",
                "openLaunchDetail(LaunchStateActivity.STATE_APP_SETUP)",
                "openLaunchDetail(LaunchStateActivity.STATE_SUPPORT)",
                "const val STATE_APP_SETUP = \"st-app-setup\"",
                "\"App setup\"",
                "\"Help & privacy\"",
                "CardItem(\"KBD\", \"Keyboard configuration\"",
                "CardItem(\"PERM\", \"Permissions\"",
                "CardItem(\"WA\", \"WhatsApp support\"",
                "CardItem(\"PRIV\", \"Privacy policy\"",
                "CardItem(\"TERMS\", \"Terms of use\""
            )
        )
        assertContainsNone(
            combined,
            listOf(
                "@+id/settings_overlay_row",
                "@+id/settings_keyboard_row",
                "@+id/settings_permission_row",
                "@+id/settings_privacy_row",
                "@+id/settings_support_row",
                "Control center",
                "Everything launch-critical",
                "AI and account",
                "Trust",
                "Bubble position",
                "Supported apps",
                "Sensitive analytics",
                "Release notes",
                "Action costs",
                "Permission recovery"
            )
        )
    }

    @Test
    fun `settings AI usage shows upgrade as bottom CTA instead of settings card`() {
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val settingsStatesOnly = launchDetail
            .substringAfter("private fun settingsStates()")
            .substringBefore("private fun subscriptionStates()")

        assertContainsAll(
            launchDetail + settingsStatesOnly,
            listOf(
                "state.dataState == STATE_SUPPORT || state.dataState == STATE_AI_USAGE",
                "STATE_AI_USAGE -> openLaunchDetail(STATE_SUBSCRIPTION_PLUS_OFFER)",
                "state(\"ST05\", STATE_AI_USAGE",
                "primary = \"Upgrade\"",
                "secondary = \"Back\"",
                "CardItem(\"AI\", \"Daily AI actions\""
            )
        )
        assertContainsNone(
            settingsStatesOnly,
            listOf(
                "CardItem(\"PLUS\", \"Upgrade\"",
                "STATE_AI_USAGE -> when (card.icon)"
            )
        )
    }

    @Test
    fun `settings support exposes whatsapp and email contact actions`() {
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val strings = read("src/main/res/values/strings.xml")

        assertContainsAll(
            strings + launchDetail,
            listOf(
                "contact_whatsapp",
                "\"st-support\"",
                "\"WhatsApp support\"",
                "\"Email support\"",
                "openWhatsAppSupport()",
                "openEmailSupport()",
                "https://wa.me/",
                "Intent.ACTION_SENDTO",
                "R.string.contact_whatsapp",
                "R.string.contact_mail",
                "settingsSupportActions(state)",
                "private fun settingsSupportActions"
            )
        )
        assertContainsNone(
            launchDetail,
            listOf(
                "\"st-support\", \"Help and version\", \"Support\", \"Get help, send feedback, and verify release information.\""
            )
        )
    }

    @Test
    fun `settings overlay privacy and permission detail ctas are routed actions not static rows`() {
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")

        assertContainsAll(
            launchDetail,
            listOf(
                "const val STATE_PRIVACY_PERMISSIONS = \"st-privacy-permissions\"",
                "const val STATE_NLS_PERMISSION = \"pm-nls-optional\"",
                "const val STATE_ACCESSIBILITY_PERMISSION = \"pm-accessibility-missing\"",
                "const val STATE_SCREEN_CAPTURE_PERMISSION = \"pm-screen-capture\"",
                "settingsGroup(group.first, group.second, state)",
                "private fun settingsCardAction",
                "setOnClickListener { action.invoke() }",
                "openOverlayBubbleControl()",
                "openAccessibilityConsent()",
                "confirmClearLocalSessions()",
                "UnifiedChatSessionManager.getInstance(this).deleteAllSessions()",
                "STATE_PRIVACY -> openLaunchDetail(\"pr-data-controls\")",
                "\"pr-data-controls\" -> openLaunchDetail(STATE_PRIVACY_PERMISSIONS)"
            )
        )
        assertContainsNone(
            launchDetail,
            listOf(
                "state.groups.forEach { group ->\n                addView(settingsGroup(group.first, group.second), topMargin = 14)"
            )
        )
    }

    @Test
    fun `app shell brand marks use real logo asset instead of placeholder W text`() {
        val homeLayout = read("src/main/res/layout/activity_home.xml")
        val settingsLayout = read("src/main/res/layout/activity_settings_hub.xml")
        val authLayout = read("src/main/res/layout/activity_authentication.xml")
        val subscriptionLayout = read("src/main/res/layout/activity_subscription_listing.xml")
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val combined = homeLayout + settingsLayout + authLayout + subscriptionLayout + launchDetail

        assertContainsAll(
            combined,
            listOf(
                "@drawable/ic_witty_logo",
                "R.drawable.ic_witty_logo",
                "@drawable/wk_logo_rounded_square_bg",
                "roundedLogoSquareBg()",
                "contentDescription=\"WittyKeys logo\"",
                "contentDescription = \"WittyKeys logo\"",
                "android:clipToOutline=\"true\"",
                "clipToOutline = true"
            )
        )
        assertContainsNone(
            combined,
            listOf(
                "android:text=\"W\"",
                "text = \"W\""
            )
        )
    }

    @Test
    fun `app tab navigation is consistent and functional across Home Settings and launch detail screens`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val homeLayout = read("src/main/res/layout/activity_home.xml")
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val settingsLayout = read("src/main/res/layout/activity_settings_hub.xml")
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val themes = read("src/main/res/values/themes.xml")
        val bottomNavBg = read("src/main/res/drawable/wk_app_bottom_nav_bg.xml")
        val activeTabBg = read("src/main/res/drawable/wk_app_bottom_nav_item_active_bg.xml")

        assertContainsAll(
            homeLayout + settingsLayout + themes + bottomNavBg + activeTabBg,
            listOf(
                "WkAppBottomNav",
                "WkAppBottomNavItem",
                "WkAppBottomNavItemActive",
                "@drawable/wk_app_bottom_nav_bg",
                "@drawable/wk_app_bottom_nav_item_active_bg",
                "@+id/home_nav_home",
                "@+id/settings_bottom_nav",
                "@+id/settings_nav_home",
                "@+id/settings_nav_usage",
                "@+id/settings_nav_settings",
                "<LinearLayout\n        android:id=\"@+id/home_bottom_nav\"\n        style=\"@style/WkAppBottomNav\"\n        android:layout_gravity=\"bottom\">",
                "android:layout_marginBottom=\"106dp\"",
                "android:layout_marginBottom=\"104dp\"",
                "android:scrollbars=\"none\""
            )
        )
        assertContainsAll(
            homeActivity + settingsHub + launchDetail,
            listOf(
                "bindBottomNavigation",
                "openUsageTab",
                "openSettingsTab",
                "startBottomTabActivity",
                "overridePendingTransition(0, 0)",
                "navigateBottomTab(tab)",
                "activeBottomTabFor(state)",
                "private const val APP_NAV_HEIGHT_DP = 64",
                "private const val APP_NAV_HORIZONTAL_MARGIN_DP = 16",
                "private const val APP_NAV_LAUNCH_BOTTOM_MARGIN_DP = 20",
                "FrameLayout.LayoutParams(match, dp(APP_NAV_HEIGHT_DP), Gravity.BOTTOM)",
                "bottomMargin = dp(APP_NAV_LAUNCH_BOTTOM_MARGIN_DP)",
                "getDrawable(R.drawable.wk_app_bottom_nav_bg)",
                "getDrawable(R.drawable.wk_app_bottom_nav_item_active_bg)",
                "if (state.dataState == STATE_AI_USAGE) return \"Usage\"",
                "setOnClickListener"
            )
        )
        assertContainsNone(
            homeLayout + settingsLayout + launchDetail,
            listOf(
                "android:layout_height=\"76dp\"",
                "@+id/home_nav_chats",
                "@+id/settings_nav_chats",
                "android:text=\"Chats\"",
                "listOf(\"Home\", \"Chats\", \"Usage\", \"Settings\")",
                "\"Chats\" -> openChatsTab()",
                "private void openChatsTab()",
                "private fun openChatsTab()",
                "root.addView(body, LinearLayout.LayoutParams(match, 0, 1f))",
                "root.addView(bottomTabs(if (state.type == ScreenType.SETTINGS) \"Settings\" else \"Home\"))"
            )
        )
    }

    @Test
    fun `account auth routing uses real session state and exposes direct account actions`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val profileActivity = read("src/main/java/project/witty/keys/app/UserProfileActivity.java")

        assertContainsAll(
            homeActivity + settingsHub,
            listOf(
                "getValidatedAccountUser()",
                "FirebaseAuth.getInstance().getCurrentUser()",
                "EncryptedPreferences.clearUserInfo();",
                "LaunchStateActivity.STATE_ACCOUNT_SIGNIN_REASON",
                "LaunchStateActivity.STATE_ACCOUNT_PROFILE_SIGNED_IN"
            )
        )
        assertContainsAll(
            launchDetail,
            listOf(
                "\"acct-signin-reason\"",
                "\"Sign in\"",
                "\"Use anonymously\"",
                "\"acct-profile-signed-in\" -> openLaunchDetail(STATE_SUBSCRIPTION_PLUS_OFFER)",
                "signOutAndReturnHome()",
                "FirebaseAuth.getInstance().signOut()",
                "DailyUsageTracker.getInstance(this).setUnlimited(false)",
                "EncryptedPreferences.clearUserInfo()"
            )
        )
        assertContainsAll(
            profileActivity,
            listOf(
                "LaunchStateActivity.class",
                "LaunchStateActivity.STATE_ACCOUNT_PROFILE_SIGNED_IN",
                "LaunchStateActivity.STATE_ACCOUNT_SIGNIN_REASON"
            )
        )
        assertContainsNone(
            launchDetail + profileActivity,
            listOf(
                "\"acct-profile-signed-in\" -> openActivity(SubscriptionListingActivity::class.java)",
                "if (state.dataState == \"acct-profile-signed-in\" || state.dataState == \"acct-delete-account\" || state.dataState == \"acct-logout\")",
                "\"acct-profile-signed-in\" -> openActivity(UserProfileActivity::class.java)",
                "setContentView(R.layout.activity_user_profile)",
                "No user is currently logged in."
            )
        )
    }

    @Test
    fun `account profile has one synced destination and legacy profile does not render separately`() {
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val profileActivity = read("src/main/java/project/witty/keys/app/UserProfileActivity.java")
        val subscriptionActivity = read("src/main/java/project/witty/keys/app/SubscriptionListingActivity.java")

        assertContainsAll(
            launchDetail + profileActivity + subscriptionActivity,
            listOf(
                "AccountEntitlementSnapshotProvider.current",
                "STATE_ACCOUNT_PROFILE_SIGNED_IN",
                "STATE_SUBSCRIPTION_PLUS_OFFER",
                "Manage plan"
            )
        )
        assertContainsNone(
            profileActivity,
            listOf(
                "setContentView(R.layout.activity_user_profile)",
                "setupToolbar();",
                "showUserIcon(true"
            )
        )
    }

    @Test
    fun `production launch detail does not render debug state ids or letter badges`() {
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")

        assertContainsAll(
            launchDetail,
            listOf(
                "displayIconFor(card.icon)",
                "trustPanel(state.heroTitle, state.heroTitle, state.body)"
            )
        )
        assertContainsNone(
            launchDetail,
            listOf(
                "addView(text(state.id",
                "text(state.id,",
                "circleText(card.icon",
                "ACCT04",
                "SB01"
            )
        )
    }

    @Test
    fun `keyboard assistant uses logo toggle and suppresses uncertain smart replies`() {
        val layout = read("src/main/res/layout/wk_original_view.xml")
        val bar = read("src/main/java/project/witty/keys/keyboard/AssistantViews/SmartAssistantBar.java")
        val manager = read("src/main/java/project/witty/keys/keyboard/AssistantViews/SmartAssistantBarManager.java")

        assertContainsAll(
            layout + bar + manager,
            listOf(
                "@+id/wk_ov_brain",
                "@drawable/wk_logo_overlay",
                "suppressKeyboardSuggestions",
                "new ChipData(\"custom\", \"\", \"\\u270F\\uFE0F\", \"Custom\", ChipData.TapAction.OPEN_CUSTOM_MODE)",
                "new ChipData(\"more\", \"\", \"\", \"+ More\", ChipData.TapAction.EXPAND_FULL_PANEL)"
            )
        )
        assertContainsNone(
            layout + bar,
            listOf(
                "android:text=\"🧠\"",
                "showContactPickerStrip(match.packageName)"
            )
        )
    }

    @Test
    fun `release navigation lands on production launch detail screens not debug harness placeholders`() {
        val manifest = read("src/main/AndroidManifest.xml")
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val debugManifest = read("src/debug/AndroidManifest.xml")

        assertContainsAll(
            manifest,
            listOf(
                "android:name=\".app.launch.LaunchStateActivity\"",
                "android:exported=\"false\"",
                "android:theme=\"@style/Theme.WittyKeys.NoActionBar\""
            )
        )
        assertContainsAll(
            launchDetail,
            listOf(
                "package project.witty.keys.app.launch",
                "const val STATE_APP_SETUP = \"st-app-setup\"",
                "const val STATE_OVERLAY_SETTINGS = \"st-overlay\"",
                "const val STATE_PERMISSION_RECOVERY = \"pm-overlay-missing\"",
                "const val STATE_AI_USAGE = \"st-ai-usage\"",
                "const val STATE_PRIVACY = \"pr-privacy-summary\"",
                "const val STATE_SUPPORT = \"st-support\"",
                "private fun resolveState"
            )
        )
        assertContainsAll(
            settingsHub,
            listOf(
                "openLaunchDetail(LaunchStateActivity.STATE_APP_SETUP)",
                "openLaunchDetail(LaunchStateActivity.STATE_AI_USAGE)",
                "openLaunchDetail(LaunchStateActivity.STATE_SUBSCRIPTION_PLUS_OFFER)",
                "openLaunchDetail(LaunchStateActivity.STATE_SUPPORT)"
            )
        )
        assertContainsAll(
            homeActivity,
            listOf(
                "LaunchStateActivity.STATE_AI_USAGE",
                "new Intent(this, LaunchStateActivity.class)"
            )
        )
        assertContainsNone(
            settingsHub,
            listOf(
                "bindRow(R.id.settings_overlay_row, this::openHomeControls)",
                "bindRow(R.id.settings_permission_row, this::openHomeControls)",
                "bindRow(R.id.settings_ai_usage_row, this::openHomeControls)",
                "Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_link))",
                "Intent.ACTION_SENDTO"
            )
        )
        assertContainsAll(
            debugManifest,
            listOf(
                "project.witty.keys.debug.LaunchStateActivity"
            )
        )
    }

    @Test
    fun `subscription and account routes show approved launch entry before legacy functional flows`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")

        assertContainsAll(
            launchDetail,
            listOf(
                "const val STATE_SUBSCRIPTION_PLUS_OFFER = \"sb-plus-offer\"",
                "const val STATE_ACCOUNT_SIGNIN_REASON = \"acct-signin-reason\"",
                "const val STATE_ACCOUNT_PROFILE_SIGNED_IN = \"acct-profile-signed-in\"",
                "private fun handlePrimaryAction",
                "SubscriptionListingActivity::class.java",
                "AuthenticationActivity::class.java",
                "openLaunchDetail(STATE_SUBSCRIPTION_PLUS_OFFER)"
            )
        )
        assertContainsAll(
            settingsHub,
            listOf(
                "openLaunchDetail(LaunchStateActivity.STATE_SUBSCRIPTION_PLUS_OFFER)",
                "openAccountLaunchDetail()"
            )
        )
        assertContainsAll(
            homeActivity,
            listOf(
                "LaunchStateActivity.STATE_ACCOUNT_SIGNIN_REASON",
                "LaunchStateActivity.STATE_ACCOUNT_PROFILE_SIGNED_IN"
            )
        )
        assertContainsNone(
            settingsHub,
            listOf(
                "startActivity(new Intent(this, SubscriptionListingActivity.class))",
                "user == null ? AuthenticationActivity.class : UserProfileActivity.class"
            )
        )
        assertContainsNone(
            homeActivity,
            listOf(
                "openAccountOrProfile()",
                "intent = new Intent(this, UserProfileActivity.class)"
            )
        )
    }

    @Test
    fun `production subscription billing screen matches approved Plus reference shell`() {
        val subscriptionActivity = read("src/main/java/project/witty/keys/app/SubscriptionListingActivity.java")
        val subscriptionLayout = read("src/main/res/layout/activity_subscription_listing.xml")
        val subscriptionCard = read("src/main/res/layout/fragment_subscription_card.xml")
        val subscriptionAdapter = read("src/main/java/project/witty/keys/app/SubscriptionAdapter.java")

        assertContainsAll(
            subscriptionActivity + subscriptionLayout + subscriptionCard + subscriptionAdapter,
            listOf(
                "@+id/subscription_back_button",
                "@+id/subscription_hero_card",
                "WittyKeys Plus",
                "Google Play billing",
                "Use more AI when WittyKeys becomes daily.",
                "Free stays useful. Plus exists for heavier AI users so the product remains sustainable.",
                "Daily essentials stay useful without sign-in.",
                "20 credits",
                "For heavier screen AI, chat, and reply usage.",
                "More AI",
                "Upgrade only after value is clear",
                "Credit limits protect product cost",
                "@+id/subscription_card_content",
                "Larger allowance",
                "Higher credit pool for chat, quick reply, and screen AI.",
                "Google Play price",
                "@drawable/wk_subscription_plan_tabs_bg",
                "@drawable/wk_subscription_plan_tab_indicator",
                "app:tabIndicatorGravity=\"center\"",
                "app:tabIndicatorHeight=\"42dp\"",
                "displayBenefitsFor",
                "Works across Overlay, Keyboard, and AI Chat.",
                "Credit limits keep free use sustainable.",
                "@+id/promo_code_panel",
                "@drawable/wk_app_search_bg",
                "android:hint=\"Enter promo code\"",
                "android:singleLine=\"true\"",
                "<com.google.android.material.button.MaterialButton\n                android:id=\"@+id/apply_coupon_button\"",
                "app:strokeColor=\"@color/wk_overlay_dark_accent\"",
                "app:strokeWidth=\"1dp\"",
                "findViewById(R.id.subscription_back_button)"
            )
        )
        assertContainsNone(
            subscriptionActivity + subscriptionLayout + subscriptionCard + subscriptionAdapter,
            listOf(
                "setToolbarTitle(\"Subscription Plans\")",
                "<include layout=\"@layout/action_bar\"",
                "Original Price:",
                "Daily limit on AIbot",
                "Professional advice cross checking",
                "android:background=\"@drawable/rounded_tone_button_background\"",
                "holder.cardView.getContext().getResources().getColor(R.color.third_app_color)",
                "holder.cardView.getContext().getResources().getColor(R.color.fifth_app_color)"
            )
        )
    }

    @Test
    fun `auth profile and subscription screens use launch theme without sensitive auth logs`() {
        val authLayout = read("src/main/res/layout/activity_authentication.xml")
        val profileLayout = read("src/main/res/layout/activity_user_profile.xml")
        val subscriptionLayout = read("src/main/res/layout/activity_subscription_listing.xml")
        val subscriptionCard = read("src/main/res/layout/fragment_subscription_card.xml")
        val authActivity = read("src/main/java/project/witty/keys/app/AuthenticationActivity.java")
        val profileActivity = read("src/main/java/project/witty/keys/app/UserProfileActivity.java")

        assertContainsAll(
            authLayout,
            listOf(
                "@drawable/wk_app_shell_bg",
                "@+id/auth_back_button",
                "Account sign in",
                "Phone or Google",
                "Manage your account when you need it.",
                "Your AI credits and tools stay usable anonymously. Sign in only for plan, billing, and account controls.",
                "Phone sign in",
                "Continue with Google",
                "Send OTP"
            )
        )
        assertContainsAll(
            authLayout,
            listOf(
                "@+id/phone_edit_text",
                "@+id/login_with_phone_button",
                "@+id/sign_in_button",
                "@+id/otp_layout",
                "@+id/verify_otp_button",
                "@+id/resend_otp_button",
                "Resend OTP",
                "@drawable/ic_brand_google"
            )
        )
        assertContainsAll(
            authActivity,
            listOf(
                "verificationResendToken",
                "resendOtp",
                "setForceResendingToken",
                "resolvePhoneAccountName(user)"
            )
        )
        assertContainsAll(
            profileLayout + subscriptionLayout + subscriptionCard,
            listOf(
                "@drawable/wk_app_shell_bg",
                "@drawable/wk_app_card_bg",
                "Account and plan",
                "Request account deletion",
                "WittyKeys Plus",
                "More AI credits"
            )
        )
        assertContainsNone(
            authLayout + authActivity,
            listOf(
                "<include layout=\"@layout/action_bar\"",
                "Sign In / Sign Up"
            )
        )
        assertContainsNone(
            authLayout + profileLayout + subscriptionLayout + subscriptionCard + profileActivity,
            listOf(
                "Phone Number With Country Code",
                "@+id/name_edit_text",
                "nameEditText",
                "android:hint=\"Name\"",
                "textPersonName",
                "android:text=\"OR\"",
                "Delete Account",
                "IN-ACTIVE",
                "Ready to write like a genius",
                "More AI actions",
                "Subscribe for unlimited"
            )
        )
        assertContainsNone(
            authActivity,
            listOf(
                "Log.d(TAG, \"Phone number: \" + phoneNumber)",
                "Log.d(TAG, \"firebaseAuthWithGoogle:\" + acct.getId())"
            )
        )
    }

    @Test
    fun `daily usage tracker keeps free plan limited and paid plan bounded without unlimited promise`() {
        val tracker = read("src/main/java/project/witty/keys/app/utils/DailyUsageTracker.java")

        assertContainsAll(
            tracker,
            listOf(
                "private static final int FREE_DAILY_LIMIT = 20;",
                "private static final int PLUS_DAILY_LIMIT",
                "public boolean canUseAI(int actionCost)",
                "return getActionsToday() + cost <= getDailyLimit();",
                "int dailyLimit = getDailyLimit();",
                "public void recordUsage(int actionCost)",
                "Math.max(0, dailyLimit - getActionsToday())"
            )
        )
        assertContainsNone(
            tracker,
            listOf(
                "Integer.MAX_VALUE",
                "Paid subscribers: Unlimited",
                "Unlimited mode:",
                "(unlimited)"
            )
        )
    }

    @Test
    fun `AI entitlement contract gates costly and invisible AI work`() {
        val tracker = read("src/main/java/project/witty/keys/app/utils/DailyUsageTracker.java")
        val manager = read("src/main/java/project/witty/keys/app/entitlements/AiEntitlementManager.java")
        val actionType = read("src/main/java/project/witty/keys/app/entitlements/AiActionType.java")
        val policy = read("src/main/java/project/witty/keys/app/entitlements/AiEntitlementPolicy.java")
        val overlayEngine = read("src/main/java/project/witty/keys/app/overlay/OverlayAiEngine.java")
        val precompute = read("src/main/java/project/witty/keys/app/context/ReplyPrecomputeManager.java")

        assertContainsAll(
            tracker + manager + actionType + policy + overlayEngine + precompute,
            listOf(
                "AiEntitlementManager",
                "AiActionType.SCREEN_AI",
                "AiActionType.BACKGROUND_PRECOMPUTE",
                "canUseAI(int actionCost)",
                "recordUsage(int actionCost)",
                "Background precompute is reserved for Plus",
                "shouldRunBackgroundPrecompute"
            )
        )
        assertContainsNone(
            overlayEngine,
            listOf(
                "tracker.getRemainingActions() <= 0",
                "tracker.recordUsage();"
            )
        )
    }

    @Test
    fun `Home does not grant paid AI tier from locally cached subscription info`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val subscriptionActivity = read("src/main/java/project/witty/keys/app/SubscriptionListingActivity.java")
        val latinIme = read("src/main/java/project/witty/keys/latin/LatinIME.java")

        assertContainsAll(
            homeActivity + subscriptionActivity + latinIme,
            listOf(
                "Granting the paid tier is reserved for Billing purchase success and Firestore sync.",
                "DailyUsageTracker.getInstance(this).setUnlimited(false)",
                "DailyUsageTracker.getInstance(this).setUnlimited(true)",
                "DailyUsageTracker.getInstance(LatinIME.this).setUnlimited(isPaidSub)"
            )
        )
        assertContainsNone(
            homeActivity,
            listOf(
                "DailyUsageTracker.getInstance(this).setUnlimited(true)"
            )
        )
    }

    @Test
    fun `final regression uses shared entitlement snapshot across app surfaces`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")
        val settingsHub = read("src/main/java/project/witty/keys/app/settings/SettingsHubActivity.java")
        val launchDetail = read("src/main/java/project/witty/keys/app/launch/LaunchStateActivity.kt")
        val subscriptionActivity = read("src/main/java/project/witty/keys/app/SubscriptionListingActivity.java")

        assertContainsAll(
            homeActivity + settingsHub + launchDetail + subscriptionActivity,
            listOf(
                "AccountEntitlementSnapshotProvider",
                "AccountEntitlementSnapshot",
                "allowanceDisplay",
                "Unlimited",
                "PrimaryCta.MANAGE_PLAN"
            )
        )
        assertContainsNone(
            launchDetail,
            listOf(
                "Plus credits",
                "\"20/day\""
            )
        )
    }

    @Test
    fun `AI proxy logging records metadata only and never logs prompts replies or raw bodies`() {
        val claudeApi = read("src/main/java/project/witty/keys/api/ClaudeApi.java")
        val demoLogger = read("src/main/java/project/witty/keys/app/helpers/DemoLogger.java")

        assertContainsAll(
            demoLogger,
            listOf(
                "logApiRequestMetadata",
                "system_prompt_length",
                "user_prompt_length",
                "logApiResponseMetadata",
                "avg_reply_length"
            )
        )
        assertContainsNone(
            claudeApi + demoLogger,
            listOf(
                "system_prompt_preview",
                "user_prompt\", truncate",
                "data.put(\"replies\"",
                "Log.d(TAG, \"[Claude] Response body: \" + responseBody)",
                "Log.e(TAG, \"[Claude] HTTP error: \" + statusCode + \" - \" + errorBody)"
            )
        )
    }

    @Test
    fun `chat session surfaces log metadata only and never raw titles or message text`() {
        val chatSurfaces = listOf(
            "src/main/java/project/witty/keys/app/context/UnifiedChatSessionManager.java",
            "src/main/java/project/witty/keys/app/overlay/OverlayChatPanel.java",
            "src/main/java/project/witty/keys/keyboard/AiChat/AiChatActivity.java",
            "src/main/java/project/witty/keys/keyboard/ProductViews/UnifiedAiView.java"
        ).joinToString("\n") { read(it) }

        assertContainsAll(
            chatSurfaces,
            listOf(
                "Created session \" + id + \" source=\" + source",
                "Sent message to API. length=\" + text.length()",
                "Created keyboard session: \" + sessionId"
            )
        )
        assertContainsNone(
            chatSurfaces,
            listOf(
                "Created session \" + id + \" [\" + source + \"] \" + title",
                "Sent message to API: \" + text.substring",
                "Toolbar: \" + (session.title",
                "title=\" + session.title",
                "\" title=\" + title",
                "Created keyboard session: \" + sessionId + \" title: \" + title",
                "Replaced last item with: \" + newItem.toString()",
                "\" for \" + contact.contactName",
                "\"No NLS messages to inject for \" + contact.contactName"
            )
        )
    }

    @Test
    fun `live AI integration tests are opt in and redact prompt previews`() {
        val integrationTest = read("src/test/java/project/witty/keys/api/ClaudeApiIntegrationTest.kt")
        val liveAiTests = listOf(
            "src/test/java/project/witty/keys/api/ClaudeApiIntegrationTest.kt",
            "src/test/java/project/witty/keys/app/context/ReplyGeneratorIntegrationTest.kt",
            "src/test/java/project/witty/keys/evaluation/AIResponseQualityTest.kt",
            "src/test/java/project/witty/keys/evaluation/PerformanceBaselineTest.kt"
        ).joinToString("\n") { read(it) }

        assertContainsAll(
            liveAiTests,
            listOf(
                "WK_RUN_CLAUDE_INTEGRATION",
                "assumeTrue("
            )
        )
        assertContainsAll(
            integrationTest,
            listOf(
                "System prompt length",
                "User prompt length",
                "Body length",
                "Content length"
            )
        )
        assertContainsNone(
            integrationTest,
            listOf(
                "System: ${'$'}{systemPrompt.take",
                "User: ${'$'}{userMessage.take",
                "Body: ${'$'}{responseBody.take",
                "Content: ${'$'}content"
            )
        )
    }

    @Test
    fun `Android user facing AI limit copy avoids unlimited promises`() {
        val copySurfaces = listOf(
            "src/main/java/project/witty/keys/keyboard/shared/ErrorInfo.java",
            "src/main/java/project/witty/keys/keyboard/AssistantViews/SuggestionRow.java",
            "src/main/java/project/witty/keys/app/overlay/OverlayAiEngine.java",
            "src/main/java/project/witty/keys/app/tutorial/TutorialTask.java",
            "src/main/res/layout/product_error_view.xml"
        ).joinToString("\n") { read(it) }

        assertContainsAll(
            copySurfaces,
            listOf(
                "Upgrade for more AI credits"
            )
        )
        assertContainsNone(
            copySurfaces,
            listOf(
                "Subscribe for unlimited",
                "unlimited AI actions",
                "unlimited access"
            )
        )
    }

    @Test
    fun `account notification and activation logs never print tokens or raw identifiers`() {
        val sensitiveSurfaces = listOf(
            "src/main/java/project/witty/keys/app/entities/User.java",
            "src/main/java/project/witty/keys/app/helpers/NotificationService.java",
            "src/main/java/project/witty/keys/app/helpers/EventHelpers.java",
            "src/main/java/project/witty/keys/app/helpers/ActivationManager.java",
            "src/main/java/project/witty/keys/app/tutorial/TutorialManager.java",
            "src/main/java/project/witty/keys/app/HomeActivity.java",
            "src/main/java/project/witty/keys/app/EntranceActivity.java",
            "src/main/java/project/witty/keys/app/SetUpKeyboardActivity.java",
            "src/main/java/project/witty/keys/latin/LatinIME.java",
            "src/main/java/project/witty/keys/keyboard/ProductViews/ProductContainerView.java"
        ).joinToString("\n") { read(it) }

        assertContainsAll(
            sensitiveSurfaces,
            listOf(
                "idPresent",
                "tokenPresent",
                "tracking_id_present",
                "user_present"
            )
        )
        assertContainsNone(
            sensitiveSurfaces,
            listOf(
                "fcmToken token: \" + fcmToken",
                "FCM Token\"+ token",
                "FCM Token: \" + token",
                "Log.d(TAG, \"User: \" + user)",
                "Log.d(TAG, \"User info: \" + user)",
                "User fetched and saved locally: \" + user",
                "\"   User ID: \" + (user != null ? user.getId() : \"null\")",
                "\"   User ID: \" + (user_id != null ? user_id : \"null\")",
                "\"   user_id: \" + userId",
                "\"   tracking_id: \" + trackingId",
                "\"   Bundle: label=\" + user_id",
                "\"   Tracking ID: \" + trackingId",
                "\"   Device Tracking ID: \" + trackingId",
                "\"   Device ID: \" + getDeviceId()",
                "\"   Resolved TrackingId: \" + trackingId",
                "\"   Input userId: \" + (userId != null ? userId : \"null\")",
                "\"No subscription found for user: \" + userId",
                "\"✅ Device activation linked to user: \" + userId",
                "\"✅ Device ID linked to user: \" + userId",
                "\"✅ Device ID linked to user (via merge): \" + userId",
                "\"✅ Activation score synced to users/\" + trackingId",
                "\"   FIRING legacy keyboard_enabled event for user: \" + user.getId()"
            )
        )
    }

    @Test
    fun `release manifest and SDK surface are hardened for internal Play candidate`() {
        val manifest = read("src/main/AndroidManifest.xml")
        val buildGradle = read("build.gradle")
        val application = read("src/main/java/project/witty/keys/app/MyApplication.java")
        val proguard = read("proguard-rules.pro")

        assertContainsAll(
            manifest + buildGradle + application + proguard,
            listOf(
                "versionCode 65",
                "versionName \"7.1\"",
                "minifyEnabled true",
                "xmlns:tools=\"http://schemas.android.com/tools\"",
                "<uses-permission android:name=\"com.google.android.gms.permission.AD_ID\" tools:node=\"remove\" />",
                "google_analytics_adid_collection_enabled",
                "google_analytics_default_allow_ad_personalization_signals",
                "android.adservices.AD_SERVICES_CONFIG",
                "android:allowBackup=\"false\"",
                "android:fullBackupOnly=\"false\"",
                "android:usesCleartextTraffic=\"false\"",
                "android:name=\".ui.chat.preview.WkDsPreviewActivity\"\n            android:exported=\"false\"",
                "play-services-ads-identifier",
                "ads-adservices",
                "FirebaseApp.initializeApp(this)",
                "AdvertisingIdClient",
                "-assumenosideeffects class android.util.Log"
            )
        )
        assertContainsNone(
            manifest + buildGradle + application,
            listOf(
                "android:allowBackup=\"true\"",
                "android:fullBackupOnly=\"true\"",
                "android:usesCleartextTraffic=\"true\"",
                "android:name=\".ui.chat.preview.WkDsPreviewActivity\"\n            android:exported=\"true\"",
                "com.facebook.sdk.ApplicationId",
                "com.facebook.sdk.ClientToken",
                "com.facebook.FacebookActivity",
                "com.facebook.CustomTabActivity",
                "facebook-android-sdk",
                "FacebookSdk",
                "AppEventsLogger.activateApp",
                "setAdvertiserIDCollectionEnabled",
                "exoplayer-core",
                "exoplayer-ui",
                "supportsPictureInPicture"
            )
        )
    }
}
