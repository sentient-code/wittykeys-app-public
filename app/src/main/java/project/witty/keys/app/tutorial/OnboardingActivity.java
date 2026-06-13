package project.witty.keys.app.tutorial;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.Locale;

import project.witty.keys.R;
import project.witty.keys.app.HomeActivity;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.NLSPermissionHelper;
import project.witty.keys.app.overlay.OverlayPermissionFlow;
import project.witty.keys.app.overlay.OverlayServiceManager;

/**
 * Build 7.1 value-first onboarding.
 *
 * The flow is deterministic for screenshot/golden testing and avoids remote
 * video, PIP, token rewards, or forced Accessibility setup.
 */
public class OnboardingActivity extends AppCompatActivity {
    private static final String TAG = "WK_ONBOARD";
    private static final int TOTAL_STEPS = 5;

    public static final String STATE_WELCOME = "ob-welcome";
    public static final String STATE_DEMO_REPLY = "ob-demo-reply";
    public static final String STATE_DEMO_SCAN = "ob-demo-scan";
    public static final String STATE_ENABLE_KEYBOARD = "ob-enable-keyboard";
    public static final String STATE_KEYBOARD_DONE = "ob-keyboard-done";
    public static final String STATE_NLS_EXPLAIN = "ob-nls-explain";
    public static final String STATE_NLS_GRANTED = "ob-nls-granted";
    public static final String STATE_NLS_SKIPPED = "ob-nls-skipped";
    public static final String STATE_OVERLAY_INTRO = "ob-overlay-intro";
    public static final String STATE_COMPLETE = "ob-complete";

    private static final String PREFS_ONBOARDING = "wk_onboarding_7_1";
    private static final String PREF_SETUP_MODE = "setup_mode";
    private static final String PREF_PENDING_NLS_REQUEST = "pending_nls_request";
    private static final String SETUP_MODE_OVERLAY = "overlay";
    private static final String SETUP_MODE_KEYBOARD = "keyboard";
    private static final String SETUP_MODE_BOTH = "both";

    private static final int COLOR_BG = 0xFF0D0D0F;
    private static final int COLOR_SURFACE = 0xFF161619;
    private static final int COLOR_SURFACE_2 = 0xFF1F1F24;
    private static final int COLOR_SURFACE_3 = 0xFF2A2A30;
    private static final int COLOR_TEXT = 0xFFF0F0F0;
    private static final int COLOR_TEXT_MUTED = 0xFFA0A0A8;
    private static final int COLOR_TEXT_SUBTLE = 0xFF606068;
    private static final int COLOR_GREEN = 0xFF4ADE80;
    private static final int COLOR_ACCENT = 0xFF6CB4EE;
    private static final int COLOR_ACCENT_DARK = 0xFF1D6F42;
    private static final int COLOR_BLUE = 0xFF6CB4EE;
    private static final int COLOR_PURPLE = 0xFFA78BFA;
    private static final int COLOR_BORDER = 0x22FFFFFF;

    private FrameLayout rootView;
    private LinearLayout stepIndicatorContainer;
    private LinearLayout contentContainer;
    private LinearLayout bottomContainer;

    private FirebaseAnalytics firebaseAnalytics;
    private long onboardingStartTime;
    private boolean debugModeActive = false;
    private boolean nlsEnabled = false;
    private boolean nlsSkipped = false;
    private String selectedSetupMode = SETUP_MODE_BOTH;
    private String currentDebugState = STATE_WELCOME;
    private OverlayPermissionFlow overlayPermissionFlow;

    private final BroadcastReceiver debugReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra("debug_state");
            if (!TextUtils.isEmpty(state)) {
                debugModeActive = true;
                handleDebugState(state);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(COLOR_BG);
            getWindow().setNavigationBarColor(COLOR_BG);
        }

        rootView = findViewById(R.id.ob_root);
        stepIndicatorContainer = findViewById(R.id.ob_step_indicator);
        contentContainer = findViewById(R.id.ob_content);
        bottomContainer = findViewById(R.id.ob_bottom);
        selectedSetupMode = getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
                .getString(PREF_SETUP_MODE, SETUP_MODE_BOTH);

        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        } catch (Exception e) {
            Log.w(TAG, "Firebase Analytics not available", e);
        }
        onboardingStartTime = System.currentTimeMillis();

        LocalBroadcastManager.getInstance(this).registerReceiver(
                debugReceiver, new IntentFilter("com.wittykeys.DEBUG_ONBOARDING_STATE"));

        String debugState = getIntent().getStringExtra("debug_state");
        String resumeFrom = getIntent().getStringExtra("resume_from");
        if (!TextUtils.isEmpty(debugState)) {
            debugModeActive = true;
            handleDebugState(debugState);
        } else if (!TextUtils.isEmpty(resumeFrom)) {
            handleDebugState(resumeFrom);
        } else {
            showWelcomeScreen();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String debugState = intent.getStringExtra("debug_state");
        if (!TextUtils.isEmpty(debugState)) {
            debugModeActive = true;
            handleDebugState(debugState);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (debugModeActive) return;

        if (overlayPermissionFlow != null) {
            overlayPermissionFlow.onResume();
        }

        if (STATE_ENABLE_KEYBOARD.equals(currentDebugState) && isKeyboardEnabled()) {
            showKeyboardDoneScreen();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    this::showInputMethodPicker, 500);
        } else if ((STATE_NLS_EXPLAIN.equals(currentDebugState) || hasPendingNlsRequest())
                && NLSPermissionHelper.isNLSEnabled(this)) {
            nlsEnabled = true;
            setPendingNlsRequest(false);
            showNlsGrantedScreen();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(debugReceiver);
    }

    public void handleDebugState(String state) {
        switch (state) {
            case STATE_WELCOME:
                showWelcomeScreen();
                break;
            case STATE_DEMO_REPLY:
                showDemoReplyScreen();
                break;
            case STATE_DEMO_SCAN:
                showDemoScanScreen();
                break;
            case STATE_ENABLE_KEYBOARD:
                showEnableKeyboardScreen();
                break;
            case STATE_KEYBOARD_DONE:
                showKeyboardDoneScreen();
                break;
            case STATE_NLS_EXPLAIN:
                showNlsExplainScreen();
                break;
            case STATE_NLS_GRANTED:
                showNlsGrantedScreen();
                break;
            case STATE_NLS_SKIPPED:
                showNlsSkippedScreen();
                break;
            case STATE_OVERLAY_INTRO:
                showOverlayIntroScreen();
                break;
            case STATE_COMPLETE:
                showCompleteScreen();
                break;
            default:
                showWelcomeScreen();
                break;
        }
    }

    private void showWelcomeScreen() {
        startScreen(STATE_WELCOME, 1, "Overlay", "Reply without switching apps.",
                "Open WittyKeys above any app and send polished AI replies in seconds.");

        addQuickReplyPreview();
        addTrustNote("Quick Replies use notification access only if you enable them.");

        Button primary = createPrimaryCta("Continue");
        primary.setOnClickListener(v -> {
            logOnboardingEvent("onboarding_started", null);
            logOnboardingEvent("onboarding_value_preview_started", eventParams("preview", "overlay_reply"));
            showDemoReplyScreen();
        });
        bottomContainer.addView(primary);

        bottomContainer.addView(createSecondaryAction("Next: ask AI about any screen", v -> showDemoReplyScreen()));
    }

    private void showDemoReplyScreen() {
        startScreen(STATE_DEMO_REPLY, 1, "Overlay", "Ask AI about any screen.",
                "Capture what you are looking at and continue the conversation right there.");

        addAiChatPreview();
        addTrustNote("WittyKeys asks for screen sharing only when you choose AI Chat capture.");

        Button primary = createPrimaryCta("Continue");
        primary.setOnClickListener(v -> {
            logOnboardingEvent("onboarding_value_preview_completed", eventParams("preview", "overlay_reply"));
            logOnboardingEvent("onboarding_value_preview_started", eventParams("preview", "overlay_ai_chat"));
            showDemoScanScreen();
        });
        bottomContainer.addView(primary);

        bottomContainer.addView(createSecondaryAction("Next: keyboard AI tools", v -> showDemoScanScreen()));
    }

    private void showDemoScanScreen() {
        startScreen(STATE_DEMO_SCAN, 2, "Keyboard", "Write better where you type.",
                "Use AI Chat, grammar, tone, and replies without leaving the text field.");

        addKeyboardValuePreview();
        addTrustNote("Keyboard setup is needed only if you want WittyKeys while typing.");

        Button primary = createPrimaryCta("Choose setup");
        primary.setOnClickListener(v -> {
            logOnboardingEvent("onboarding_value_preview_completed", eventParams("preview", "overlay_ai_chat"));
            logOnboardingEvent("onboarding_value_preview_started", eventParams("preview", "keyboard"));
            showEnableKeyboardScreen();
        });
        bottomContainer.addView(primary);

        bottomContainer.addView(createSecondaryAction("Overlay, Keyboard, or both", v -> showEnableKeyboardScreen()));
    }

    private void showEnableKeyboardScreen() {
        startScreen(STATE_ENABLE_KEYBOARD, 2, "Setup", "Choose how you want to use WittyKeys.",
                "Set up only what you need now. You can enable the rest later.");

        addSetupChoicePreview();
        addTrustNote("No default choice. Overlay and Keyboard are equally supported.");

        Button primary = createPrimaryCta("Continue");
        primary.setOnClickListener(v -> {
            chooseSetupMode(selectedSetupMode);
            if (isOverlayOnlyMode()) {
                TutorialManager.getInstance(this).markKeyboardSkipped();
                showNlsExplainScreen();
            } else if (isKeyboardOnlyMode()) {
                TutorialManager.getInstance(this).markNlsSkipped();
                showKeyboardSetupInstructionsScreen();
            } else {
                showKeyboardSetupInstructionsScreen();
            }
        });
        bottomContainer.addView(primary);

        bottomContainer.addView(createSecondaryAction("You can change this from Home", v -> showEnableKeyboardScreen()));
    }

    private void showKeyboardSetupInstructionsScreen() {
        startScreen(STATE_ENABLE_KEYBOARD, 2, "Keyboard", "Enable WittyKeys keyboard",
                "Android shows its standard keyboard warning for every keyboard app. WittyKeys does not store your typed replies.");

        addSetupStep("1", "Open Language and input settings");
        addSetupStep("2", "Turn on WittyKeys");
        addSetupStep("3", "Come back here and choose WittyKeys as default");
        addTrustNote("Keyboard setup is used only when you choose the Keyboard experience.");

        Button primary = createPrimaryCta("Open keyboard settings");
        primary.setOnClickListener(v -> {
            logOnboardingEvent("permission_ime_started", null);
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        });
        bottomContainer.addView(primary);

        bottomContainer.addView(createSecondaryAction("Skip keyboard for now", v -> {
            TutorialManager.getInstance(this).markKeyboardSkipped();
            logOnboardingEvent("permission_ime_skipped", null);
            if (isKeyboardOnlyMode()) showCompleteScreen(); else showNlsExplainScreen();
        }));
    }

    private void showKeyboardDoneScreen() {
        startScreen(STATE_KEYBOARD_DONE, 3, "Keyboard", "Keyboard is ready.",
                isKeyboardOnlyMode()
                        ? "WittyKeys can now appear in any text field when you select it."
                        : "WittyKeys can now appear in any text field when you select it.");

        addKeyboardDonePreview();
        addTrustNote(isKeyboardOnlyMode()
                ? "You can enable Overlay later from Home."
                : "You can switch keyboards from Android input settings anytime.");

        Button primary = createPrimaryCta(isKeyboardOnlyMode() ? "Finish setup" : "Continue setup");
        primary.setOnClickListener(v -> {
            logOnboardingEvent("permission_ime_completed", null);
            if (isKeyboardOnlyMode()) {
                showCompleteScreen();
            } else {
                showNlsExplainScreen();
            }
        });
        bottomContainer.addView(primary);

        bottomContainer.addView(createSecondaryAction(isKeyboardOnlyMode() ? "Choose keyboard again" : "Next: optional Quick Replies", v -> {
            if (isKeyboardOnlyMode()) showInputMethodPicker(); else showNlsExplainScreen();
        }));
    }

    private void showNlsExplainScreen() {
        startScreen(STATE_NLS_EXPLAIN, 3, "Quick Replies", "Enable Quick Replies safely.",
                "Notification access lets WittyKeys prepare reply suggestions for messages from supported apps.");

        addNlsExplainPreview();
        addTrustNote("Analytics never logs message text, screenshots, or typed replies.");

        Button primary = createPrimaryCta("Enable Quick Replies");
        primary.setOnClickListener(v -> {
            logOnboardingEvent("permission_nls_started", null);
            setPendingNlsRequest(true);
            NLSPermissionHelper.openNLSSettings(this);
        });
        bottomContainer.addView(primary);

        bottomContainer.addView(createSecondaryAction("Skip for now", v -> {
            nlsSkipped = true;
            setPendingNlsRequest(false);
            TutorialManager.getInstance(this).markNlsSkipped();
            logOnboardingEvent("permission_nls_skipped", null);
            showNlsSkippedScreen();
        }));
    }

    private void showNlsGrantedScreen() {
        nlsEnabled = true;
        setPendingNlsRequest(false);
        startScreen(STATE_NLS_GRANTED, 4, "Quick Replies", "Quick Replies are ready.",
                "Overlay can now surface reply suggestions from your messaging notifications.");

        addNlsGrantedPreview();
        addTrustNote("You stay in control. Suggestions are shown only inside WittyKeys.");

        Button primary = createPrimaryCta("Continue");
        primary.setOnClickListener(v -> {
            logOnboardingEvent("permission_nls_completed", null);
            showOverlayIntroScreen();
        });
        bottomContainer.addView(primary);
    }

    private void showNlsSkippedScreen() {
        startScreen(STATE_NLS_SKIPPED, 4, "Quick Replies", "Quick Replies are paused.",
                "You can still use Overlay AI Chat and turn notification replies on later.");

        addNlsSkippedPreview();
        addTrustNote("Skipping notification access does not block the rest of onboarding.");

        Button primary = createPrimaryCta("Continue");
        primary.setOnClickListener(v -> showOverlayIntroScreen());
        bottomContainer.addView(primary);
    }

    private void showOverlayIntroScreen() {
        startScreen(STATE_OVERLAY_INTRO, 4, "Overlay", "Turn on the floating Overlay.",
                "The bubble opens AI Chat and Quick Replies above the app you are using.");

        addOverlayIntroPreview();
        addTrustNote("Overlay, Accessibility, and screen capture are explained before Android asks.");

        Button primary = createPrimaryCta("Enable Overlay");
        primary.setOnClickListener(v -> {
            logOnboardingEvent("overlay_intro_started", null);
            overlayPermissionFlow = new OverlayPermissionFlow(
                    this,
                    this::completeOverlaySetup,
                    () -> {
                        logOnboardingEvent("overlay_intro_skipped", null);
                        showCompleteScreen();
                    });
            overlayPermissionFlow.start();
        });
        bottomContainer.addView(primary);

        bottomContainer.addView(createSecondaryAction("Use keyboard only for now", v -> {
            logOnboardingEvent("overlay_intro_skipped", null);
            showCompleteScreen();
        }));
    }

    private void completeOverlaySetup() {
        OverlayServiceManager.setOverlayEnabled(true);
        OverlayServiceManager.startService(this);
        logOnboardingEvent("overlay_intro_completed", null);
        showCompleteScreen();
    }

    private void showCompleteScreen() {
        startScreen(STATE_COMPLETE, 5, "Ready", "WittyKeys is ready.",
                "Start with 20 free AI credits/day across Overlay and Keyboard tools.");

        addCompletePreview();
        addTrustNote("You can change setup choices, permissions, and privacy controls from Home.");

        Button primary = createPrimaryCta("Start using WittyKeys");
        primary.setOnClickListener(v -> finishOnboarding());
        bottomContainer.addView(primary);
    }

    private void startScreen(String state, int activeStep, String eyebrow, String title, String subtitle) {
        currentDebugState = state;
        contentContainer.removeAllViews();
        bottomContainer.removeAllViews();
        rootView.setBackground(appBackground());
        updateStepIndicator(activeStep);
        stepIndicatorContainer.setVisibility(View.GONE);
        contentContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        contentContainer.setPadding(dp(22), dp(8), dp(22), dp(8));
        bottomContainer.setPadding(dp(22), dp(8), dp(22), dp(24));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, 0, dp(18));
        top.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView eyebrowView = text(eyebrow, 11, 0xFFBDE3FF, Typeface.BOLD);
        eyebrowView.setGravity(Gravity.CENTER);
        eyebrowView.setBackground(rounded(0x1A6CB4EE, dp(14), 0x386CB4EE, dp(1)));
        eyebrowView.setPadding(dp(10), dp(6), dp(10), dp(6));
        top.addView(eyebrowView);

        LinearLayout spacer = new LinearLayout(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
        top.addView(spacer);

        LinearLayout inlineProgress = new LinearLayout(this);
        inlineProgress.setGravity(Gravity.CENTER);
        inlineProgress.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i <= TOTAL_STEPS; i++) {
            View dot = new View(this);
            int width = i == activeStep ? dp(22) : dp(7);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(7));
            params.setMargins(dp(3), 0, dp(3), 0);
            dot.setLayoutParams(params);
            dot.setBackground(rounded(i == activeStep ? COLOR_ACCENT : 0x2EFFFFFF, dp(7), 0, 0));
            inlineProgress.addView(dot);
        }
        top.addView(inlineProgress);
        contentContainer.addView(top);

        TextView titleView = text(title, 32, COLOR_TEXT, Typeface.BOLD);
        titleView.setGravity(Gravity.START);
        titleView.setLineSpacing(dp(2), 1.0f);
        titleView.setPadding(0, 0, 0, dp(8));
        contentContainer.addView(titleView);

        TextView subtitleView = text(subtitle, 14, COLOR_TEXT_MUTED, Typeface.NORMAL);
        subtitleView.setGravity(Gravity.START);
        subtitleView.setLineSpacing(dp(3), 1.0f);
        subtitleView.setPadding(0, 0, 0, dp(16));
        contentContainer.addView(subtitleView);
    }

    private void chooseSetupMode(String mode) {
        selectedSetupMode = mode;
        getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
                .edit()
                .putString(PREF_SETUP_MODE, mode)
                .apply();
        logOnboardingEvent("setup_choice_selected", eventParams("mode", mode));
    }

    private boolean hasPendingNlsRequest() {
        return getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
                .getBoolean(PREF_PENDING_NLS_REQUEST, false);
    }

    private void setPendingNlsRequest(boolean pending) {
        getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_PENDING_NLS_REQUEST, pending)
                .apply();
    }

    private boolean isOverlayOnlyMode() {
        return SETUP_MODE_OVERLAY.equals(selectedSetupMode);
    }

    private boolean isKeyboardOnlyMode() {
        return SETUP_MODE_KEYBOARD.equals(selectedSetupMode);
    }

    private void addQuickReplyPreview() {
        FrameLayout preview = createPreviewFrame("Your reply panel appears only when you open Overlay.");
        addOverlayBubble(preview, true);

        LinearLayout popup = topAnchoredOverlayPopup();
        popup.addView(overlayHeader("QR", "Quick Replies", "4 apps waiting", "Open", true));
        popup.addView(sourceTabs("wa"));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(8));
        body.addView(messageCard(R.drawable.ic_brand_whatsapp, "Priya - WhatsApp", "Can you send the launch notes before 6?"));
        popup.addView(body);

        LinearLayout suggestions = new LinearLayout(this);
        suggestions.setOrientation(LinearLayout.VERTICAL);
        suggestions.setPadding(dp(12), 0, dp(12), dp(6));
        suggestions.addView(suggestion("Sure, I will send both versions before 6."));
        suggestions.addView(suggestion("On it. I will share the softer client copy too."));
        popup.addView(suggestions);
        preview.addView(popup);
    }

    private void addAiChatPreview() {
        FrameLayout preview = createPreviewFrame("Screen capture starts only after you tap capture.");
        addOverlayBubble(preview, false);

        LinearLayout popup = overlayPopup();
        popup.addView(overlayHeader("AI", "AI Chat", "Screen ready", "Capture", false));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(8));
        body.addView(screenshotCard());
        body.addView(chatBubble("What should I reply to this?", true));
        body.addView(chatBubble("Send a short answer first, then offer the detail in a follow-up.", false));
        popup.addView(body);
        popup.addView(replyInput("Ask anything..."));
        preview.addView(popup);
    }

    private void addKeyboardValuePreview() {
        FrameLayout preview = createPreviewFrame("Keyboard tools stay close to where you type.");

        LinearLayout scene = new LinearLayout(this);
        scene.setOrientation(LinearLayout.VERTICAL);
        scene.setPadding(dp(12), 0, dp(12), dp(12));
        FrameLayout.LayoutParams sceneParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        scene.setLayoutParams(sceneParams);

        LinearLayout smartCard = verticalCard();
        smartCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        smartCard.setBackground(rounded(COLOR_SURFACE, dp(20), COLOR_BORDER, dp(1)));
        TextView smartTitle = text("WittyKeys AI", 13, COLOR_TEXT, Typeface.BOLD);
        smartCard.addView(smartTitle);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(0, dp(10), 0, dp(8));
        tools.addView(toolChip("Reply"));
        tools.addView(toolChip("Tone"));
        tools.addView(toolChip("Grammar"));
        smartCard.addView(tools);
        smartCard.addView(suggestion("Sounds good. I will send the final version today."));
        scene.addView(smartCard);

        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(10), dp(10), dp(10), dp(10));
        keyboard.setBackground(rounded(0xEB0F1014, dp(22), 0x12FFFFFF, dp(1)));
        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        keyboardParams.setMargins(0, dp(10), 0, 0);
        keyboard.setLayoutParams(keyboardParams);
        for (int row = 0; row < 3; row++) {
            LinearLayout keyRow = new LinearLayout(this);
            keyRow.setOrientation(LinearLayout.HORIZONTAL);
            keyRow.setGravity(Gravity.CENTER);
            keyRow.setPadding(0, 0, 0, dp(6));
            int count = row == 2 ? 7 : 10;
            for (int i = 0; i < count; i++) {
                View key = new View(this);
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, dp(25), 1);
                keyParams.setMargins(dp(2), 0, dp(2), 0);
                key.setLayoutParams(keyParams);
                key.setBackground(rounded(row == 2 && i == 3 ? COLOR_SURFACE_3 : COLOR_SURFACE_2, dp(8), COLOR_BORDER, dp(1)));
                keyRow.addView(key);
            }
            keyboard.addView(keyRow);
        }
        scene.addView(keyboard);
        preview.addView(scene);
    }

    private void addSetupChoicePreview() {
        FrameLayout preview = createPreviewFrame(null);

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setGravity(Gravity.CENTER);
        choices.setPadding(dp(12), dp(14), dp(12), dp(14));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        choices.setLayoutParams(params);

        choices.addView(setupChoiceCard("OVR", "Set up Overlay", "Quick Replies and AI Chat above any app.", v -> {
            chooseSetupMode(SETUP_MODE_OVERLAY);
            TutorialManager.getInstance(this).markKeyboardSkipped();
            showNlsExplainScreen();
        }));
        choices.addView(setupChoiceCard("KBD", "Set up Keyboard", "AI writing tools directly where you type.", v -> {
            chooseSetupMode(SETUP_MODE_KEYBOARD);
            TutorialManager.getInstance(this).markNlsSkipped();
            showKeyboardSetupInstructionsScreen();
        }));
        choices.addView(setupChoiceCard("ALL", "Set up both", "Best experience across apps and typing.", v -> {
            chooseSetupMode(SETUP_MODE_BOTH);
            showKeyboardSetupInstructionsScreen();
        }));

        preview.addView(choices);
    }

    private void addPermissionTrustPreview() {
        FrameLayout preview = createPreviewFrame("Permission screens appear after value, not before.");

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), 0, dp(12), dp(12));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        list.setLayoutParams(params);
        if (!isOverlayOnlyMode()) {
            list.addView(permissionCard("Keyboard", "Required", "Lets WittyKeys appear as a typing keyboard when selected.", true));
        }
        list.addView(permissionCard("Notifications", "Optional", "Reads message notifications only to prepare Quick Replies when you enable them.", false));
        list.addView(permissionCard("Overlay and Capture", "Optional", "Shows the bubble above apps and captures a screen only after you start AI Chat.", false));
        preview.addView(list);
    }

    private void addKeyboardDonePreview() {
        FrameLayout preview = createPreviewFrame(null);
        addSuccessMark(preview);

        LinearLayout scene = new LinearLayout(this);
        scene.setOrientation(LinearLayout.VERTICAL);
        scene.setPadding(dp(12), 0, dp(12), dp(12));
        FrameLayout.LayoutParams sceneParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        scene.setLayoutParams(sceneParams);

        LinearLayout smartCard = verticalCard();
        smartCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        smartCard.setBackground(rounded(COLOR_SURFACE, dp(20), COLOR_BORDER, dp(1)));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("WittyKeys selected", 13, COLOR_TEXT, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        head.addView(title);
        head.addView(statusPill("Ready", true));
        smartCard.addView(head);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(0, dp(10), 0, 0);
        tools.addView(toolChip("AI Chat"));
        tools.addView(toolChip("Tone"));
        tools.addView(toolChip("Grammar"));
        smartCard.addView(tools);
        scene.addView(smartCard);
        scene.addView(createKeyboardMock());
        preview.addView(scene);
    }

    private void addNlsExplainPreview() {
        FrameLayout preview = createPreviewFrame("Android asks only after this explanation.");
        LinearLayout list = previewList(Gravity.BOTTOM);
        list.addView(permissionCard("Notification Access", "Optional", "Reads supported messaging notifications only to prepare Quick Replies you choose to use.", false));
        list.addView(permissionCard("Supported apps", "Replies", "WhatsApp, Instagram, Google Chat, Telegram, and similar message notifications.", true));
        list.addView(permissionCard("Privacy boundary", "Local first", "No onboarding analytics event stores message text or notification bodies.", false));
        preview.addView(list);
    }

    private void addNlsGrantedPreview() {
        FrameLayout preview = createPreviewFrame(null);
        addOverlayBubble(preview, true);

        LinearLayout popup = topAnchoredOverlayPopup();
        popup.addView(overlayHeader("QR", "Quick Replies ready", "Notifications connected", "Live", true));
        popup.addView(sourceTabs("ig"));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(8));
        body.addView(messageCard(R.drawable.instagram_icon, "Rohan - Instagram", "Can we move the call to tomorrow morning?"));
        popup.addView(body);

        LinearLayout suggestions = new LinearLayout(this);
        suggestions.setOrientation(LinearLayout.VERTICAL);
        suggestions.setPadding(dp(12), 0, dp(12), dp(6));
        suggestions.addView(suggestion("Tomorrow morning works. Send me your preferred time."));
        popup.addView(suggestions);
        preview.addView(popup);
    }

    private void addNlsSkippedPreview() {
        FrameLayout preview = createPreviewFrame(null);
        LinearLayout list = previewList(Gravity.CENTER);
        list.addView(statusCard("Quick Replies", "Paused", "Notification suggestions will stay off until you enable access from Home.", false));
        list.addView(statusCard("Overlay AI Chat", "Available", "Ask AI about any screen manually with capture when you need it.", true));
        list.addView(statusCard("Keyboard tools", "Available", "AI Chat, tone, grammar, and replies still work from the keyboard.", true));
        preview.addView(list);
    }

    private void addOverlayIntroPreview() {
        FrameLayout preview = createPreviewFrame("The bubble opens only after you turn Overlay on.");
        addOverlayBubble(preview, false);
        LinearLayout list = previewList(Gravity.BOTTOM);
        list.addView(permissionCard("Draw over apps", "Required", "Lets WittyKeys show the floating bubble and expanded overlay above the current app.", true));
        list.addView(permissionCard("Accessibility helper", "Optional", "Helps assisted overlay actions only after clear disclosure and your choice.", false));
        list.addView(permissionCard("Screen capture", "On demand", "Used only after you tap capture in AI Chat for the current screen.", false));
        preview.addView(list);
    }

    private void addCompletePreview() {
        FrameLayout preview = createPreviewFrame(null);
        addSuccessMark(preview);
        LinearLayout list = previewList(Gravity.BOTTOM);
        list.addView(statusCard("Daily AI credits", "20/day", "Your free credits work across Overlay and Keyboard.", true));
        list.addView(statusCard("Setup choices", "Editable", "Turn Overlay, Keyboard, Quick Replies, and capture on or off from Home.", true));
        preview.addView(list);
    }

    private LinearLayout previewList(int gravity) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(14), dp(12), dp(12));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, gravity);
        list.setLayoutParams(params);
        return list;
    }

    private void addSuccessMark(FrameLayout preview) {
        TextView mark = text("OK", 26, COLOR_GREEN, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(gradient(0x2E6CB4EE, 0x244ADE80, dp(30)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(86), dp(86), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.setMargins(0, dp(20), 0, 0);
        preview.addView(mark, params);
    }

    private TextView statusPill(String tag, boolean positive) {
        TextView pill = text(tag, 9, positive ? COLOR_GREEN : 0xFFFB923C, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(8), dp(5), dp(8), dp(5));
        pill.setBackground(rounded(positive ? 0x1F4ADE80 : 0x1FFB923C, dp(12), 0, 0));
        return pill;
    }

    private LinearLayout statusCard(String title, String tag, String body, boolean positive) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        card.setBackground(rounded(COLOR_SURFACE_2, dp(16), 0x0DFFFFFF, dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(params);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = text(title, 13, COLOR_TEXT, Typeface.BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(titleView);
        top.addView(statusPill(tag, positive));
        card.addView(top);

        TextView bodyView = text(body, 11, COLOR_TEXT_MUTED, Typeface.NORMAL);
        bodyView.setPadding(0, dp(5), 0, 0);
        bodyView.setLineSpacing(dp(2), 1.0f);
        card.addView(bodyView);
        return card;
    }

    private FrameLayout createPreviewFrame(String label) {
        FrameLayout preview = new FrameLayout(this);
        preview.setBackground(previewBackground());
        preview.setClipToOutline(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(374));
        params.setMargins(-dp(5), 0, -dp(5), dp(10));
        preview.setLayoutParams(params);
        contentContainer.addView(preview);

        if (!TextUtils.isEmpty(label)) {
            TextView labelView = text(label, 10, 0xA6FFFFFF, Typeface.NORMAL);
            labelView.setLineSpacing(dp(2), 1.0f);
            labelView.setPadding(dp(10), dp(8), dp(10), dp(8));
            labelView.setBackground(rounded(0x8A080A0E, dp(14), 0x12FFFFFF, dp(1)));
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(dp(190), FrameLayout.LayoutParams.WRAP_CONTENT);
            labelParams.setMargins(dp(14), dp(14), 0, 0);
            labelView.setLayoutParams(labelParams);
            preview.addView(labelView);
        }
        return preview;
    }

    private void addOverlayBubble(FrameLayout preview, boolean showBadge) {
        FrameLayout bubble = new FrameLayout(this);
        bubble.setBackgroundResource(R.drawable.overlay_bubble_circle);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.END);
        params.setMargins(0, dp(44), dp(16), 0);
        bubble.setLayoutParams(params);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.wk_logo_overlay);
        logo.setContentDescription("WittyKeys overlay icon");
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(dp(24), dp(20), Gravity.CENTER);
        bubble.addView(logo, logoParams);

        if (showBadge) {
            TextView badge = text("4", 10, 0xFF07130A, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded(COLOR_GREEN, dp(10), COLOR_BG, dp(2)));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP | Gravity.END);
            badgeParams.setMargins(0, -dp(3), -dp(3), 0);
            bubble.addView(badge, badgeParams);
        }
        preview.addView(bubble);
    }

    private LinearLayout overlayPopup() {
        LinearLayout popup = new LinearLayout(this);
        popup.setOrientation(LinearLayout.VERTICAL);
        popup.setBackground(rounded(COLOR_SURFACE, dp(20), 0x0CFFFFFF, dp(1)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        params.setMargins(dp(12), 0, dp(12), dp(18));
        popup.setLayoutParams(params);
        return popup;
    }

    private LinearLayout topAnchoredOverlayPopup() {
        LinearLayout popup = new LinearLayout(this);
        popup.setOrientation(LinearLayout.VERTICAL);
        popup.setBackground(rounded(COLOR_SURFACE, dp(20), 0x0CFFFFFF, dp(1)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(260), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.setMargins(dp(12), dp(102), dp(12), 0);
        popup.setLayoutParams(params);
        return popup;
    }

    private LinearLayout overlayHeader(String icon, String title, String subtitle, String action, boolean greenIcon) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(13), dp(11), dp(13), dp(11));

        TextView iconView = text(icon, 11, greenIcon ? 0xFF25D366 : COLOR_ACCENT, Typeface.BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(rounded(greenIcon ? 0x2625D366 : 0x266CB4EE, dp(7), 0, 0));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        iconParams.setMargins(0, 0, dp(8), 0);
        header.addView(iconView, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        copy.addView(text(title, 13, COLOR_TEXT, Typeface.BOLD));
        copy.addView(text(subtitle, 10, COLOR_TEXT_MUTED, Typeface.NORMAL));
        header.addView(copy);

        TextView actionView = text(action, 10, COLOR_ACCENT, Typeface.BOLD);
        actionView.setGravity(Gravity.CENTER);
        actionView.setPadding(dp(10), dp(6), dp(10), dp(6));
        actionView.setBackground(rounded(COLOR_SURFACE_2, dp(10), 0x0DFFFFFF, dp(1)));
        header.addView(actionView);
        return header;
    }

    private View sourceTabs(String active) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), dp(8), dp(12), dp(8));
        tabs.addView(sourceTab(R.drawable.ic_brand_whatsapp, "WhatsApp", "wa".equals(active)));
        tabs.addView(sourceTab(R.drawable.instagram_icon, "Instagram", "ig".equals(active)));
        tabs.addView(sourceTab(R.drawable.ic_brand_google_chat, "Google Chat", "gc".equals(active)));
        tabs.addView(sourceTab(R.drawable.ic_brand_telegram, "Telegram", "tg".equals(active)));
        scroller.addView(tabs, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    private LinearLayout sourceTab(int logoRes, String label, boolean active) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(8), 0, dp(10), 0);
        tab.setBackground(rounded(active ? 0x1F6CB4EE : 0x0FFFFFFF, dp(15), active ? 0x596CB4EE : 0x12FFFFFF, dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
        params.setMargins(0, 0, dp(7), 0);
        tab.setLayoutParams(params);

        ImageView logo = new ImageView(this);
        logo.setImageResource(logoRes);
        logo.setContentDescription(label);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        logoParams.setMargins(0, 0, dp(6), 0);
        tab.addView(logo, logoParams);

        TextView labelView = text(label, 10, active ? COLOR_TEXT : 0xBDFFFFFF, Typeface.BOLD);
        tab.addView(labelView);
        return tab;
    }

    private LinearLayout messageCard(int logoRes, String meta, String message) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(10), dp(11), dp(10));
        card.setBackground(rounded(COLOR_SURFACE_2, dp(14), 0x0AFFFFFF, dp(1)));

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(logoRes);
        logo.setContentDescription(meta);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        logoParams.setMargins(0, 0, dp(7), 0);
        metaRow.addView(logo, logoParams);
        metaRow.addView(text(meta, 10, COLOR_TEXT_MUTED, Typeface.BOLD));
        card.addView(metaRow);

        TextView body = text(message, 12, 0xDBFFFFFF, Typeface.NORMAL);
        body.setPadding(0, dp(5), 0, 0);
        body.setLineSpacing(dp(2), 1.0f);
        card.addView(body);
        return card;
    }

    private TextView suggestion(String value) {
        TextView chip = text("AI  " + value, 11, 0xE6FFFFFF, Typeface.NORMAL);
        chip.setLineSpacing(dp(2), 1.0f);
        chip.setPadding(dp(10), dp(9), dp(10), dp(9));
        chip.setBackground(rounded(0x186CB4EE, dp(14), 0x2E6CB4EE, dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(6));
        chip.setLayoutParams(params);
        return chip;
    }

    private LinearLayout replyInput(String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView field = text(hint, 12, COLOR_TEXT_SUBTLE, Typeface.NORMAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(13), 0, dp(13), 0);
        field.setBackground(rounded(COLOR_SURFACE_2, dp(18), 0x0DFFFFFF, dp(1)));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, dp(34), 1);
        fieldParams.setMargins(0, 0, dp(6), 0);
        row.addView(field, fieldParams);

        TextView send = text(">", 14, Color.WHITE, Typeface.BOLD);
        send.setGravity(Gravity.CENTER);
        send.setBackground(gradient(COLOR_ACCENT, COLOR_PURPLE, dp(16)));
        row.addView(send, new LinearLayout.LayoutParams(dp(32), dp(32)));
        return row;
    }

    private View screenshotCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(11), dp(11), dp(11));
        card.setBackground(gradient(0x296CB4EE, 0x20A78BFA, dp(15)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76));
        params.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(params);
        card.addView(shimmerLine(LinearLayout.LayoutParams.MATCH_PARENT));
        card.addView(shimmerLine(LinearLayout.LayoutParams.MATCH_PARENT));
        card.addView(shimmerLine(dp(150)));
        return card;
    }

    private View shimmerLine(int width) {
        View line = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(7));
        params.setMargins(0, 0, 0, dp(7));
        line.setLayoutParams(params);
        line.setBackground(rounded(0x28FFFFFF, dp(7), 0, 0));
        return line;
    }

    private TextView chatBubble(String value, boolean user) {
        TextView bubble = text(value, 11, 0xE6FFFFFF, Typeface.NORMAL);
        bubble.setLineSpacing(dp(2), 1.0f);
        bubble.setPadding(dp(11), dp(9), dp(11), dp(9));
        bubble.setBackground(rounded(user ? 0x336CB4EE : 0x0FFFFFFF, dp(15), 0x10FFFFFF, dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(user ? dp(50) : 0, 0, user ? 0 : dp(50), dp(8));
        bubble.setGravity(Gravity.START);
        bubble.setLayoutParams(params);
        return bubble;
    }

    private TextView toolChip(String value) {
        TextView chip = text(value, 10, 0xC7FFFFFF, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(rounded(0x0FFFFFFF, dp(13), 0x12FFFFFF, dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(34), 1);
        params.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private LinearLayout createKeyboardMock() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(10), dp(10), dp(10), dp(10));
        keyboard.setBackground(rounded(0xEB0F1014, dp(22), 0x12FFFFFF, dp(1)));
        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        keyboardParams.setMargins(0, dp(10), 0, 0);
        keyboard.setLayoutParams(keyboardParams);
        for (int row = 0; row < 3; row++) {
            LinearLayout keyRow = new LinearLayout(this);
            keyRow.setOrientation(LinearLayout.HORIZONTAL);
            keyRow.setGravity(Gravity.CENTER);
            keyRow.setPadding(0, 0, 0, dp(6));
            int count = row == 2 ? 7 : 10;
            for (int i = 0; i < count; i++) {
                View key = new View(this);
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, dp(25), 1);
                keyParams.setMargins(dp(2), 0, dp(2), 0);
                key.setLayoutParams(keyParams);
                key.setBackground(rounded(row == 2 && i == 3 ? COLOR_SURFACE_3 : COLOR_SURFACE_2, dp(8), COLOR_BORDER, dp(1)));
                keyRow.addView(key);
            }
            keyboard.addView(keyRow);
        }
        return keyboard;
    }

    private LinearLayout setupChoiceCard(String icon, String title, String body, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        card.setBackground(rounded(0xEB161619, dp(18), 0x12FFFFFF, dp(1)));
        card.setOnClickListener(listener);
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        TextView iconView = text(icon, 11, COLOR_ACCENT, Typeface.BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(rounded(0x246CB4EE, dp(15), 0, 0));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        iconParams.setMargins(0, 0, dp(11), 0);
        card.addView(iconView, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        copy.addView(text(title, 14, COLOR_TEXT, Typeface.BOLD));
        TextView bodyView = text(body, 11, COLOR_TEXT_MUTED, Typeface.NORMAL);
        bodyView.setPadding(0, dp(3), 0, 0);
        bodyView.setLineSpacing(dp(2), 1.0f);
        copy.addView(bodyView);
        card.addView(copy);

        return card;
    }

    private LinearLayout permissionCard(String title, String tag, String body, boolean required) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        card.setBackground(rounded(COLOR_SURFACE_2, dp(16), 0x0DFFFFFF, dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(params);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = text(title, 13, COLOR_TEXT, Typeface.BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(titleView);
        TextView tagView = text(tag, 9, required ? COLOR_GREEN : COLOR_TEXT_MUTED, Typeface.BOLD);
        tagView.setGravity(Gravity.CENTER);
        tagView.setPadding(dp(8), dp(5), dp(8), dp(5));
        tagView.setBackground(rounded(required ? 0x1F4ADE80 : 0x0FFFFFFF, dp(12), 0, 0));
        top.addView(tagView);
        card.addView(top);

        TextView bodyView = text(body, 11, COLOR_TEXT_MUTED, Typeface.NORMAL);
        bodyView.setPadding(0, dp(5), 0, 0);
        bodyView.setLineSpacing(dp(2), 1.0f);
        card.addView(bodyView);
        return card;
    }

    private void addTrustNote(String note) {
        TextView noteView = text(note, 11, 0x7AFFFFFF, Typeface.NORMAL);
        noteView.setGravity(Gravity.CENTER);
        noteView.setLineSpacing(dp(2), 1.0f);
        noteView.setPadding(dp(6), 0, dp(6), dp(2));
        bottomContainer.addView(noteView);
    }

    private void addMiniKeyboardPreview() {
        LinearLayout card = verticalCard();
        TextView strip = text("Reply   Scan   Tone   Fix   Hindi", 14, COLOR_ACCENT, Typeface.BOLD);
        strip.setGravity(Gravity.CENTER);
        strip.setPadding(0, 0, 0, dp(12));
        card.addView(strip);

        String[] rows = {"Q  W  E  R  T  Y  U  I  O  P", "A  S  D  F  G  H  J  K  L", "Z  X  C  V  B  N  M"};
        for (String row : rows) {
            TextView rowView = text(row, 13, COLOR_TEXT_MUTED, Typeface.NORMAL);
            rowView.setGravity(Gravity.CENTER);
            rowView.setPadding(0, dp(4), 0, dp(4));
            card.addView(rowView);
        }
        contentContainer.addView(card);
    }

    private void addFeatureCard(String title, String body, int accent) {
        LinearLayout card = verticalCard();
        card.setBackground(rounded(COLOR_SURFACE, dp(16), COLOR_BORDER, dp(1)));
        TextView titleView = text(title, 17, accent, Typeface.BOLD);
        TextView bodyView = text(body, 14, COLOR_TEXT_MUTED, Typeface.NORMAL);
        bodyView.setPadding(0, dp(6), 0, 0);
        bodyView.setLineSpacing(dp(3), 1.0f);
        card.addView(titleView);
        card.addView(bodyView);
        contentContainer.addView(card);
    }

    private void addChatBubble(String sender, String message, boolean incoming) {
        LinearLayout bubble = verticalCard();
        bubble.setBackground(rounded(incoming ? COLOR_SURFACE_2 : COLOR_ACCENT_DARK, dp(16), COLOR_BORDER, dp(1)));
        TextView senderView = text(sender, 12, incoming ? COLOR_BLUE : COLOR_TEXT, Typeface.BOLD);
        TextView messageView = text(message, 16, COLOR_TEXT, Typeface.NORMAL);
        messageView.setPadding(0, dp(6), 0, 0);
        bubble.addView(senderView);
        bubble.addView(messageView);
        contentContainer.addView(bubble);
    }

    private void addReplyChip(String reply) {
        TextView chip = text(reply, 15, COLOR_TEXT, Typeface.BOLD);
        chip.setBackground(rounded(0xFF1D3F2B, dp(18), 0x5538B86A, dp(1)));
        chip.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        chip.setLayoutParams(params);
        contentContainer.addView(chip);
    }

    private void addScreenshotMock() {
        LinearLayout card = verticalCard();
        card.setBackground(rounded(0xFF141822, dp(18), 0x336CB4EE, dp(1)));
        TextView header = text("Screenshot context", 13, COLOR_BLUE, Typeface.BOLD);
        TextView body = text("Long email: Need a polite reply confirming the deadline and asking for one clarification.", 15, COLOR_TEXT, Typeface.NORMAL);
        body.setPadding(0, dp(8), 0, 0);
        body.setLineSpacing(dp(4), 1.0f);
        card.addView(header);
        card.addView(body);
        contentContainer.addView(card);
    }

    private void addAiAnswerMock() {
        LinearLayout card = verticalCard();
        card.setBackground(rounded(0xFF18261E, dp(18), 0x554ADE80, dp(1)));
        TextView header = text("WittyKeys suggestion", 13, COLOR_ACCENT, Typeface.BOLD);
        TextView body = text("Reply with: Thanks, I can send this by Friday. Could you confirm whether you want the summary or full report?", 15, COLOR_TEXT, Typeface.NORMAL);
        body.setPadding(0, dp(8), 0, 0);
        body.setLineSpacing(dp(4), 1.0f);
        card.addView(header);
        card.addView(body);
        contentContainer.addView(card);
    }

    private void addSetupStep(String number, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(rounded(COLOR_SURFACE, dp(14), COLOR_BORDER, dp(1)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);

        TextView numberView = text(number, 14, Color.BLACK, Typeface.BOLD);
        numberView.setGravity(Gravity.CENTER);
        numberView.setBackground(rounded(COLOR_ACCENT, dp(12), 0, 0));
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        numberParams.setMargins(0, 0, dp(12), 0);
        numberView.setLayoutParams(numberParams);
        row.addView(numberView);

        TextView labelView = text(label, 15, COLOR_TEXT, Typeface.BOLD);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(labelView);
        contentContainer.addView(row);
    }

    private void addDisclosure(String title, String body) {
        LinearLayout card = verticalCard();
        card.setBackground(rounded(COLOR_SURFACE, dp(14), COLOR_BORDER, dp(1)));
        TextView titleView = text(title, 15, COLOR_TEXT, Typeface.BOLD);
        TextView bodyView = text(body, 13, COLOR_TEXT_MUTED, Typeface.NORMAL);
        bodyView.setPadding(0, dp(5), 0, 0);
        bodyView.setLineSpacing(dp(3), 1.0f);
        card.addView(titleView);
        card.addView(bodyView);
        contentContainer.addView(card);
    }

    private void addStatusCard(String title, String body, boolean positive) {
        LinearLayout card = verticalCard();
        card.setBackground(rounded(positive ? 0xFF152B1E : 0xFF2A2417, dp(16),
                positive ? 0x554ADE80 : 0x55F59E0B, dp(1)));
        TextView titleView = text(title, 17, positive ? COLOR_ACCENT : 0xFFF59E0B, Typeface.BOLD);
        TextView bodyView = text(body, 14, COLOR_TEXT_MUTED, Typeface.NORMAL);
        bodyView.setPadding(0, dp(6), 0, 0);
        bodyView.setLineSpacing(dp(3), 1.0f);
        card.addView(titleView);
        card.addView(bodyView);
        contentContainer.addView(card);
    }

    private void addDailyActionCounter() {
        LinearLayout card = verticalCard();
        card.setGravity(Gravity.CENTER);
        card.setBackground(rounded(0xFF152B1E, dp(20), 0x554ADE80, dp(1)));

        TextView number = text("20", 46, COLOR_ACCENT, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        TextView label = text("AI credits/day", 14, COLOR_TEXT, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        TextView sub = text("Tone, Grammar, Translate, AI Chat, Scan, and Smart Reply generation each count as one action.", 13, COLOR_TEXT_MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(3), 1.0f);
        sub.setPadding(0, dp(8), 0, 0);

        card.addView(number);
        card.addView(label);
        card.addView(sub);
        contentContainer.addView(card);
    }

    private LinearLayout verticalCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        card.setBackground(rounded(COLOR_SURFACE, dp(18), COLOR_BORDER, dp(1)));
        return card;
    }

    private Button createPrimaryCta(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.BLACK);
        button.setBackground(gradient(COLOR_ACCENT, COLOR_PURPLE, dp(18)));
        button.setMinHeight(dp(54));
        button.setPadding(dp(20), dp(12), dp(20), dp(12));
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView createSecondaryAction(String text, View.OnClickListener listener) {
        TextView action = text(text, 14, COLOR_TEXT_SUBTLE, Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setPadding(0, dp(16), 0, dp(4));
        action.setOnClickListener(listener);
        return action;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Drawable appBackground() {
        return new OnboardingBackgroundDrawable();
    }

    private static class OnboardingBackgroundDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            if (width <= 0 || height <= 0) return;

            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            paint.setShader(new LinearGradient(
                    0, 0, 0, height,
                    new int[]{0xFF111217, 0xFF0D0D0F, 0xFF07080A},
                    new float[]{0f, 0.52f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(null);

            drawRadialGlow(canvas, width * 0.24f, height * 0.16f, width * 0.46f, 0x244ADE80);
            drawRadialGlow(canvas, width * 0.84f, height * 0.22f, width * 0.42f, 0x2A6CB4EE);
            drawRadialGlow(canvas, width * 0.70f, height * 0.82f, width * 0.44f, 0x26A78BFA);
            drawDiagonalTexture(canvas, width, height);
            canvas.restore();
        }

        private void drawRadialGlow(Canvas canvas, float cx, float cy, float radius, int color) {
            paint.setShader(new RadialGradient(
                    cx, cy, radius,
                    color,
                    0x00000000,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null);
        }

        private void drawDiagonalTexture(Canvas canvas, int width, int height) {
            paint.setColor(0x07FFFFFF);
            paint.setStrokeWidth(1f);
            for (int x = -height; x < width; x += 44) {
                canvas.drawLine(x, height, x + height, 0, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private GradientDrawable previewBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x1F4ADE80, 0x246CB4EE, 0x1FA78BFA});
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(28));
        drawable.setStroke(dp(1), 0x14FFFFFF);
        return drawable;
    }

    private GradientDrawable gradient(int startColor, int endColor, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private void updateStepIndicator(int activeStep) {
        stepIndicatorContainer.removeAllViews();
        if (activeStep <= 0) {
            stepIndicatorContainer.setVisibility(View.GONE);
            return;
        }
        stepIndicatorContainer.setVisibility(View.VISIBLE);
        stepIndicatorContainer.setGravity(Gravity.CENTER);

        for (int i = 1; i <= TOTAL_STEPS; i++) {
            View dot = new View(this);
            int size = dp(i == activeStep ? 28 : 10);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, dp(10));
            params.setMargins(dp(5), 0, dp(5), 0);
            dot.setLayoutParams(params);
            int color = i <= activeStep ? COLOR_ACCENT : 0x33FFFFFF;
            dot.setBackground(rounded(color, dp(8), 0, 0));
            stepIndicatorContainer.addView(dot);
        }
    }

    private boolean isKeyboardEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        String imePackageName = getPackageName();
        for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
            if (imi.getPackageName().equals(imePackageName)) return true;
        }
        return false;
    }

    private void showInputMethodPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private void finishOnboarding() {
        TutorialManager.getInstance(this).markOnboardingCompleted();
        EncryptedPreferences.initialize(this);
        EncryptedPreferences.saveBoolean("notFirstTime", true);

        Bundle params = new Bundle();
        params.putBoolean("nls_enabled", nlsEnabled);
        params.putBoolean("nls_skipped", nlsSkipped);
        params.putInt("daily_free_actions", 20);
        params.putLong("duration_seconds", (System.currentTimeMillis() - onboardingStartTime) / 1000);
        logOnboardingEvent("onboarding_completed", params);

        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private Bundle eventParams(String key, String value) {
        Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    private void logOnboardingEvent(String event, Bundle params) {
        if (firebaseAnalytics == null) return;
        Bundle safeParams = params == null ? new Bundle() : new Bundle(params);
        safeParams.putString("source", "build_7_1_onboarding");
        safeParams.putString("state", currentDebugState);
        firebaseAnalytics.logEvent(event, safeParams);
        Log.d(TAG, "event=" + event + " state=" + currentDebugState);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
