package project.witty.keys.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Build71MvpContractTest {

    private fun read(path: String): String = File(path).readText()

    private fun assertContainsAll(content: String, values: List<String>) {
        values.forEach { value ->
            assertTrue("Expected source to contain '$value'", content.contains(value))
        }
    }

    private fun assertContainsNone(content: String, values: List<String>) {
        values.forEach { value ->
            assertFalse("Expected source not to contain '$value'", content.contains(value))
        }
    }

    @Test
    fun `onboarding activity uses deterministic Build 7_1 value-first states`() {
        val source = read("src/main/java/project/witty/keys/app/tutorial/OnboardingActivity.java")

        assertContainsAll(
            source,
            listOf(
                "ob-welcome",
                "ob-demo-reply",
                "ob-demo-scan",
                "ob-enable-keyboard",
                "ob-keyboard-done",
                "ob-nls-explain",
                "ob-nls-granted",
                "ob-nls-skipped",
                "ob-overlay-intro",
                "ob-complete",
                "20 free AI credits/day"
            )
        )
        assertContainsNone(
            source,
            listOf(
                "ExoPlayer",
                "PictureInPicture",
                "TOKEN_EXPLANATION",
                "2,000 Bonus Tokens",
                "Subscribe for Unlimited Tokens"
            )
        )
    }

    @Test
    fun `tutorial manager does not force accessibility during core onboarding resume`() {
        val source = read("src/main/java/project/witty/keys/app/tutorial/TutorialManager.java")

        assertContainsAll(source, listOf("STATE_ENABLE_KEYBOARD", "STATE_NLS_EXPLAIN"))
        assertContainsNone(source, listOf("ob-a11y-explain"))
    }

    @Test
    fun `onboarding goldens expose the Build 7_1 state contract`() {
        val capture = read("src/androidTest/java/project/witty/keys/e2e/golden/OnboardingGoldenCaptureTest.kt")
        val regression = read("src/androidTest/java/project/witty/keys/e2e/golden/OnboardingGoldenRegressionTest.kt")
        val combined = capture + regression

        assertContainsAll(
            combined,
            listOf(
                "OB01_welcome",
                "OB02_demo_reply",
                "OB03_demo_scan",
                "OB04_enable_keyboard",
                "OB05_keyboard_done",
                "OB06_nls_explain",
                "OB07_nls_granted",
                "OB08_nls_skipped",
                "OB09_overlay_intro",
                "OB10_complete"
            )
        )
        assertContainsNone(combined, listOf("OB07_a11y_explain", "OB08_a11y_granted", "OB09_a11y_skipped"))
    }

    @Test
    fun `home and legal copy use AI credits instead of stale token wording`() {
        val homeCard = read("src/main/res/layout/user_card_home.xml")
        val strings = read("src/main/res/values/strings.xml")
        val privacy = read("../docs/PRIVACY_POLICY.md")

        assertContainsAll(homeCard + strings + privacy, listOf("20 free AI credits/day"))
        assertContainsNone(
            homeCard + strings + privacy,
            listOf("10000 Words", "2000 Words", "2,000 FREE tokens", "token balance")
        )
    }

    @Test
    fun `release build does not ship ad id or meta advertiser collection`() {
        val manifest = read("src/main/AndroidManifest.xml")
        val buildGradle = read("build.gradle")
        val application = read("src/main/java/project/witty/keys/app/MyApplication.java")

        assertContainsAll(
            manifest,
            listOf(
                "xmlns:tools=\"http://schemas.android.com/tools\"",
                "<uses-permission android:name=\"com.google.android.gms.permission.AD_ID\" tools:node=\"remove\" />",
                "<uses-permission android:name=\"android.permission.ACCESS_ADSERVICES_ATTRIBUTION\" tools:node=\"remove\" />",
                "<uses-permission android:name=\"android.permission.ACCESS_ADSERVICES_AD_ID\" tools:node=\"remove\" />",
                "google_analytics_adid_collection_enabled",
                "google_analytics_default_allow_ad_personalization_signals",
                "android.adservices.AD_SERVICES_CONFIG"
            )
        )
        assertContainsNone(
            buildGradle + application,
            listOf(
                "com.facebook.sdk.ApplicationId",
                "com.facebook.sdk.ClientToken",
                "com.facebook.FacebookActivity",
                "com.facebook.CustomTabActivity",
                "facebook-android-sdk",
                "FacebookSdk",
                "setAdvertiserIDCollectionEnabled",
                "AppEventsLogger.activateApp"
            )
        )
    }

    @Test
    fun `manifest disables Android backup restore for private onboarding and account state`() {
        val manifest = read("src/main/AndroidManifest.xml")

        assertContainsAll(
            manifest,
            listOf(
                "android:allowBackup=\"false\"",
                "android:fullBackupOnly=\"false\""
            )
        )
        assertContainsNone(manifest, listOf("android:allowBackup=\"true\"", "android:fullBackupOnly=\"true\""))
    }

    @Test
    fun `approved onboarding theme copy and setup choices are present`() {
        val source = read("src/main/java/project/witty/keys/app/tutorial/OnboardingActivity.java")

        assertContainsAll(
            source,
            listOf(
                "Reply without switching apps.",
                "Ask AI about any screen.",
                "Write better where you type.",
                "Choose how you want to use WittyKeys.",
                "Keyboard is ready.",
                "Enable Quick Replies safely.",
                "Quick Replies are ready.",
                "Quick Replies are paused.",
                "Turn on the floating Overlay.",
                "permissionCard(\"Draw over apps\", \"Required\"",
                "Set up Overlay",
                "Set up Keyboard",
                "Set up both",
                "Telegram",
                "Quick Replies use notification access only if you enable them.",
                "Analytics never logs message text, screenshots, or typed replies.",
                "Start with 20 free AI credits/day across Overlay and Keyboard tools."
            )
        )
        assertContainsNone(
            source,
            listOf(
                "Reply faster in Hinglish",
                "One tap, better replies",
                "Scan helps with long chats",
                "AI one tap away"
            )
        )
    }

    @Test
    fun `approved onboarding mockup exposes the frozen OB state buttons`() {
        val mockup = read("../docs/reference/WittyKeys_UI_Mockup.html")

        assertContainsAll(
            mockup,
            listOf(
                "data-state=\"ob-welcome\"",
                "data-state=\"ob-demo-reply\"",
                "data-state=\"ob-demo-scan\"",
                "data-state=\"ob-enable-keyboard\"",
                "data-state=\"ob-keyboard-done\"",
                "data-state=\"ob-nls-explain\"",
                "data-state=\"ob-nls-granted\"",
                "data-state=\"ob-nls-skipped\"",
                "data-state=\"ob-overlay-intro\"",
                "data-state=\"ob-complete\""
            )
        )
        assertContainsNone(mockup, listOf("docs/references", "ob-a11y-explain", "ob-a11y-granted", "ob-a11y-skipped"))
    }

    @Test
    fun `onboarding references use Razr 50 full screen aspect contract`() {
        val mockup = read("../docs/reference/WittyKeys_UI_Mockup.html")
        val script = read("../scripts/render_mockup_states.js")

        assertContainsAll(
            mockup,
            listOf(
                "--razr-width: 411.428571px",
                "--razr-height: 1005.714286px",
                "width: var(--razr-width)",
                "height: var(--razr-height)",
                "border-radius: 0",
                "padding: 0"
            )
        )
        assertContainsAll(
            script,
            listOf(
                "const RAZR_50_DENSITY_SCALE = 420 / 160",
                "const ONBOARDING_VIEWPORT = { width: 420, height: 1014, deviceScaleFactor: RAZR_50_DENSITY_SCALE }",
                "const viewport = isLaunchSuite(suite) ? ONBOARDING_VIEWPORT : DEFAULT_VIEWPORT",
                "await page.setViewport(viewport)"
            )
        )
    }

    @Test
    fun `onboarding mockup models Razr system chrome for full screen captures`() {
        val mockup = read("../docs/reference/WittyKeys_UI_Mockup.html")

        assertContainsAll(
            mockup,
            listOf(
                "--razr-status-height: 28px",
                "--razr-nav-height: 26px",
                "class=\"system-status\"",
                "class=\"system-nav\"",
                "class=\"gesture-pill\"",
                "top: var(--razr-status-height)",
                "bottom: var(--razr-nav-height)",
                "<span>5:05</span>"
            )
        )
        assertContainsNone(mockup, listOf("<div class=\"status\"><span>9:41</span>"))
    }

    @Test
    fun `onboarding Android preview frames match approved reference dimensions`() {
        val source = read("src/main/java/project/witty/keys/app/tutorial/OnboardingActivity.java")

        assertContainsAll(
            source,
            listOf(
                "LinearLayout.LayoutParams.MATCH_PARENT, dp(374)",
                "params.setMargins(-dp(5), 0, -dp(5), dp(10))"
            )
        )
        assertContainsNone(source, listOf("LinearLayout.LayoutParams.MATCH_PARENT, dp(370)"))
    }

    @Test
    fun `onboarding setup and keyboard ready CTAs match approved reference copy`() {
        val source = read("src/main/java/project/witty/keys/app/tutorial/OnboardingActivity.java")

        assertContainsAll(
            source,
            listOf(
                "Button primary = createPrimaryCta(\"Continue\");\n        primary.setOnClickListener(v -> {\n            chooseSetupMode(selectedSetupMode);",
                "createPrimaryCta(isKeyboardOnlyMode() ? \"Finish setup\" : \"Continue setup\")",
                "createSecondaryAction(isKeyboardOnlyMode() ? \"Choose keyboard again\" : \"Next: optional Quick Replies\"",
                "if (isKeyboardOnlyMode()) showInputMethodPicker(); else showNlsExplainScreen();"
            )
        )
    }

    @Test
    fun `onboarding permission setup screens expose skip paths before opening system settings`() {
        val source = read("src/main/java/project/witty/keys/app/tutorial/OnboardingActivity.java")
        val overlayFlow = read("src/main/java/project/witty/keys/app/overlay/OverlayPermissionFlow.java")
        val overlayPopup = read("src/main/res/layout/overlay_permission_popup.xml")

        assertContainsAll(
            source + overlayFlow + overlayPopup,
            listOf(
                "createSecondaryAction(\"Skip keyboard for now\"",
                "logOnboardingEvent(\"permission_ime_skipped\", null)",
                "TutorialManager.getInstance(this).markKeyboardSkipped()",
                "if (isKeyboardOnlyMode()) showCompleteScreen(); else showNlsExplainScreen();",
                "createSecondaryAction(\"Skip for now\"",
                "logOnboardingEvent(\"permission_nls_skipped\", null)",
                "createSecondaryAction(\"Use keyboard only for now\"",
                "android:text=\"Skip for now\""
            )
        )
    }

    @Test
    fun `onboarding permission buttons actually launch durable NLS and overlay enable flows`() {
        val source = read("src/main/java/project/witty/keys/app/tutorial/OnboardingActivity.java")

        assertContainsAll(
            source,
            listOf(
                "PREF_PENDING_NLS_REQUEST",
                "setPendingNlsRequest(true)",
                "STATE_NLS_EXPLAIN.equals(currentDebugState) || hasPendingNlsRequest()",
                "&& NLSPermissionHelper.isNLSEnabled(this)",
                "overlayPermissionFlow.onResume()",
                "new OverlayPermissionFlow(",
                "completeOverlaySetup()",
                "OverlayServiceManager.setOverlayEnabled(true)",
                "OverlayServiceManager.startService(this)"
            )
        )
        assertContainsNone(
            source,
            listOf(
                "logOnboardingEvent(\"overlay_intro_completed\", null);\n            showCompleteScreen();"
            )
        )
    }

    @Test
    fun `overlay permission flow treats accessibility as optional for MVP overlay enable`() {
        val flow = read("src/main/java/project/witty/keys/app/overlay/OverlayPermissionFlow.java")
        val home = read("src/main/java/project/witty/keys/app/HomeActivity.java")

        assertContainsAll(
            flow + home,
            listOf(
                "Required permissions:",
                "Optional permission:",
                "private static final int STEP_ACCESSIBILITY = 1",
                "completeRequiredOverlaySetup()",
                "public static boolean hasRequiredPermissions(Context context)",
                "return OverlayPermissionHelper.canDrawOverlays(context);",
                "if (skipButton != null) skipButton.setOnClickListener(v -> cancel());",
                "if (skipButton != null) skipButton.setOnClickListener(v -> {\n            dismissCurrent();\n            completeRequiredOverlaySetup();\n        });",
                "OverlayPermissionFlow.hasRequiredPermissions(this)"
            )
        )
        assertContainsNone(home, listOf("OverlayPermissionFlow.hasAllPermissions(this)"))
    }

    @Test
    fun `onboarding Android shell uses approved textured theme without text arrows`() {
        val source = read("src/main/java/project/witty/keys/app/tutorial/OnboardingActivity.java")

        assertContainsAll(
            source,
            listOf(
                "return new OnboardingBackgroundDrawable()",
                "class OnboardingBackgroundDrawable extends Drawable",
                "drawDiagonalTexture(canvas, width, height)"
            )
        )
        assertContainsNone(source, listOf("TextView arrow = text(\">\", 16, COLOR_TEXT_SUBTLE, Typeface.BOLD);"))
    }

    @Test
    fun `render script uses approved onboarding mockup path and OB mapping`() {
        val script = read("../scripts/render_mockup_states.js")

        assertContainsAll(
            script,
            listOf(
                "../docs/reference/WittyKeys_UI_Mockup.html",
                "{ dataState: 'ob-welcome',          filename: 'onboarding_OB01_welcome.png' }",
                "{ dataState: 'ob-demo-reply',       filename: 'onboarding_OB02_demo_reply.png' }",
                "{ dataState: 'ob-demo-scan',        filename: 'onboarding_OB03_demo_scan.png' }",
                "{ dataState: 'ob-enable-keyboard',  filename: 'onboarding_OB04_enable_keyboard.png' }",
                "{ dataState: 'ob-keyboard-done',    filename: 'onboarding_OB05_keyboard_done.png' }",
                "{ dataState: 'ob-nls-explain',      filename: 'onboarding_OB06_nls_explain.png' }",
                "{ dataState: 'ob-nls-granted',      filename: 'onboarding_OB07_nls_granted.png' }",
                "{ dataState: 'ob-nls-skipped',      filename: 'onboarding_OB08_nls_skipped.png' }",
                "{ dataState: 'ob-overlay-intro',    filename: 'onboarding_OB09_overlay_intro.png' }",
                "{ dataState: 'ob-complete',         filename: 'onboarding_OB10_complete.png' }"
            )
        )
        assertContainsNone(
            script,
            listOf(
                "../docs/references/WittyKeys_UI_Mockup.html",
                "{ dataState: 'ob-keyboard',",
                "ob-a11y-explain",
                "ob-a11y-granted",
                "ob-a11y-skipped"
            )
        )
    }

    @Test
    fun `render script snaps onboarding captures to exact Razr physical pixels`() {
        val script = read("../scripts/render_mockup_states.js")

        assertContainsAll(
            script,
            listOf(
                "const RAZR_50_CAPTURE_WIDTH = 1080",
                "const RAZR_50_CAPTURE_HEIGHT = 2640",
                "async function getOnboardingCaptureClip(page)",
                "const physicalX = Math.round((rect.left + window.scrollX) * scale)",
                "width: targetWidth / scale",
                "height: targetHeight / scale"
            )
        )
    }

    @Test
    fun `render script normalizes onboarding PNGs after Chrome rounding`() {
        val script = read("../scripts/render_mockup_states.js")

        assertContainsAll(
            script,
            listOf(
                "const { execFileSync } = require('child_process')",
                "if (/^(ob|hm|st|ov|ka|se|sb|acct|pr|pm|qt)-/.test(dataState))",
                "normalizeRazrReference(outPath)",
                "function normalizeRazrReference(outPath)",
                "'-extent'",
                "`${'$'}{RAZR_50_CAPTURE_WIDTH}x${'$'}{RAZR_50_CAPTURE_HEIGHT}`"
            )
        )
    }

    @Test
    fun `onboarding golden labels match approved Build 7_1 value states`() {
        val capture = read("src/androidTest/java/project/witty/keys/e2e/golden/OnboardingGoldenCaptureTest.kt")

        assertContainsAll(
            capture,
            listOf(
                "OB01: Overlay Quick Replies",
                "OB02: Overlay AI Chat",
                "OB03: Keyboard Value",
                "OB10: Complete"
            )
        )
        assertContainsNone(capture, listOf("Welcome + Video", "Complete + Tokens"))
    }

    @Test
    fun `overlay only setup does not force keyboard resume after onboarding`() {
        val tutorialManager = read("src/main/java/project/witty/keys/app/tutorial/TutorialManager.java")
        val onboarding = read("src/main/java/project/witty/keys/app/tutorial/OnboardingActivity.java")

        assertContainsAll(
            tutorialManager + onboarding,
            listOf(
                "markKeyboardSkipped",
                "wasKeyboardSkipped",
                "SETUP_MODE_OVERLAY",
                "SETUP_MODE_KEYBOARD",
                "SETUP_MODE_BOTH",
                "setup_choice_selected"
            )
        )
        assertContainsAll(
            tutorialManager,
            listOf(
                "if (!keyboardEnabled && !wasKeyboardSkipped())",
                "PREF_KEYBOARD_SKIPPED"
            )
        )
    }

    @Test
    fun `screen one quick reply preview is anchored from overlay bubble`() {
        val source = read("src/main/java/project/witty/keys/app/tutorial/OnboardingActivity.java")

        assertContainsAll(
            source,
            listOf(
                "topAnchoredOverlayPopup",
                "FrameLayout.LayoutParams.MATCH_PARENT, dp(260), Gravity.TOP | Gravity.CENTER_HORIZONTAL",
                "params.setMargins(dp(12), dp(102), dp(12), 0)",
                "addQuickReplyPreview()"
            )
        )
    }
}
