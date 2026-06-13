// HomeActivity.java
package project.witty.keys.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import nl.dionsegijn.konfetti.core.Angle;
import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.Position;
import nl.dionsegijn.konfetti.core.Spread;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.core.emitter.EmitterConfig;
import nl.dionsegijn.konfetti.xml.KonfettiView;
import project.witty.keys.BuildConfig;
import project.witty.keys.R;
import project.witty.keys.app.entities.Subscription;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.ActivationManager;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.home.HomeLaunchState;
import project.witty.keys.app.launch.LaunchStateActivity;
import project.witty.keys.app.state.AccountEntitlementSnapshot;
import project.witty.keys.app.state.AccountEntitlementSnapshotProvider;
import project.witty.keys.app.state.SetupChecklistState;
import project.witty.keys.app.state.SetupChecklistStateProvider;
import project.witty.keys.app.utils.DailyUsageTracker;
import project.witty.keys.app.overlay.OverlayPermissionFlow;
import project.witty.keys.app.overlay.OverlayServiceManager;
import project.witty.keys.app.helpers.NotchHandler;
import project.witty.keys.app.helpers.NotificationService;
import project.witty.keys.app.settings.SettingsHubActivity;


public class HomeActivity extends BaseActivity {
    private static final String TAG = "HomeActivity";
    private static final String NOT_FIRST_TIME_STRING = "notFirstTime";
    private static final String EXTRA_DEBUG_HOME_STATE = "wk_debug_home_state";
    private String userId;
    private CardView enableInstruction;
    private BroadcastReceiver inputMethodReceiver;
    private static final int APP_NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;
    private KonfettiView konfettiView = null;
    private FirebaseAnalytics mFirebaseAnalytics; // Make it a member variable
    // Views from user_card_home
    private View userCardView;
    private ImageView itemIcon1, itemIcon2, itemIcon3, itemIcon4, itemIcon5;
    private TextView itemTitle1, itemTitle2, itemTitle3, itemTitle4, itemTitle5;
    private TextView itemSubtitle3, itemSubtitle4;
    private MaterialButton itemButton3, itemButton4;
    private LinearLayout itemProgressLayout3;
    private ProgressBar itemProgressBar3;
    private TextView itemProgressText3;
    private LinearLayout itemLayout5; // Reference to layout 5 for visibility
    private TextView homeHeadline, homeSubhead, homeStageTitle, homeStageSubtitle;
    private TextView homeStageMessage, homeStageReply, homeCreditValue, homeCreditCaption;
    private TextView homeStateMark, homeKeyboardButton;
    private TextView homeSetupCheck1, homeSetupCheck2, homeSetupCheck3;
    private TextView homeTopSubtitle;
    private LinearLayout homeActionList, homePrimaryActions, homeSetupChecks;
    private View homeCreditStrip;
    private ProgressBar homeCreditMeter;
    private MaterialButton homePrimaryButton, homeUpgradeButton;
    private static final int SMS_PERMISSION_REQUEST_CODE = 1002; // New constant for SMS
    private final Handler homeRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable homeSettlingRefreshShort = this::refreshHomeLaunchState;
    private final Runnable homeSettlingRefreshLong = this::refreshHomeLaunchState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotchHandler.configureEdgeToEdge(this);
        setContentView(R.layout.activity_home);
        NotchHandler.handleSystemBars(this);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this); // Initialize here
        konfettiView = findViewById(R.id.konfettiView);
        bindHomeLaunchViews();
        EncryptedPreferences.initialize(this);

        enableInstruction = findViewById(R.id.enable_instruction_home);

        Boolean notFirstTime = EncryptedPreferences.getBoolean(NOT_FIRST_TIME_STRING, false);
        if (!notFirstTime) {
            showExplosionConfetti();
        }
        // Save notFirstTime in EncryptedPreferences
        EncryptedPreferences.saveBoolean(NOT_FIRST_TIME_STRING, true);

        inputMethodReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // DEBUG: Log broadcast received
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "=== KEYBOARD_ENABLE_DEBUG: HomeActivity BroadcastReceiver ===");
                    Log.d(TAG, "   Timestamp: " + System.currentTimeMillis());
                    Log.d(TAG, "   Intent Action: " + (intent != null ? intent.getAction() : "null"));
                }

                // Handle the change in default input method
                boolean isEnabled = isWittyKeysEnabled(); // Check if enabled first
                boolean isDefault = isWittyKeysDefaultInputMethod();
                Log.d(TAG, "Default input method changed. isWittyKeysEnabled: " + isEnabled + ", isWittyKeysDefault: " + isDefault);

                // DEBUG: Log keyboard state
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "   WittyKeys Enabled: " + isEnabled);
                    Log.d(TAG, "   WittyKeys Default: " + isDefault);
                }

                updateKeyboardStatusUI(isEnabled, isDefault); // Update UI based on checks
                refreshHomeLaunchStateAfterKeyboardSelectionSettles();

                User user = EncryptedPreferences.getUserLoggedInInfo();

                // DEBUG: Log user state and decision
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "   User Logged In: " + (user != null));
                    Log.d(TAG, "   user_present: " + (user != null && user.getId() != null && !user.getId().isEmpty()));
                    Log.d(TAG, "   Firebase Analytics: " + (mFirebaseAnalytics != null));
                }

                if (user != null) {
                    if (!isDefault) { // Log only if it becomes non-default
                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "   DECISION: Firing keyboard_disabled (not default)");
                        }
                        EventHelpers.triggerKeyboardDisabledEvent(user.getId(), mFirebaseAnalytics);
                    } else {
                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "   DECISION: Firing keyboard_enabled (is default)");
                        }
                        EventHelpers.triggerKeyboardEnabledEvent(user.getId(), mFirebaseAnalytics);

                        // Also track via ActivationManager for milestone tracking
                        ActivationManager activationManager = new ActivationManager(context);
                        activationManager.trackKeyboardEnabled(user.getId());
                    }
                } else {
                    // DEBUG: Log the tracking gap for anonymous users
                    if (DebugConfig.isDebugMode) {
                        Log.w(TAG, "   WARNING: User is null - legacy keyboard event NOT fired!");
                        Log.w(TAG, "   This is a tracking gap for anonymous users");
                        ActivationManager activationManager = new ActivationManager(context);
                        String trackingId = activationManager.getTrackingId();
                        Log.w(TAG, "   tracking_id_present: " + (trackingId != null && !trackingId.isEmpty()));
                    }

                    // Track via ActivationManager even for anonymous users
                    if (isDefault) {
                        ActivationManager activationManager = new ActivationManager(context);
                        activationManager.trackKeyboardEnabled(null);
                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "   Tracked keyboard_enabled via ActivationManager (anonymous)");
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_INPUT_METHOD_CHANGED);
        registerReceiver(inputMethodReceiver, filter, RECEIVER_EXPORTED); // Added RECEIVER_EXPORTED for compatibility
    }

    private void bindHomeLaunchViews() {
        homeTopSubtitle = findViewById(R.id.home_top_subtitle);
        homeHeadline = findViewById(R.id.home_headline);
        homeSubhead = findViewById(R.id.home_subhead);
        homeStageTitle = findViewById(R.id.home_stage_title);
        homeStageSubtitle = findViewById(R.id.home_stage_subtitle);
        homeStageMessage = findViewById(R.id.home_stage_message);
        homeStageReply = null;
        homeStateMark = findViewById(R.id.home_state_mark);
        homeCreditValue = findViewById(R.id.home_credit_value);
        homeCreditCaption = findViewById(R.id.home_credit_caption);
        homePrimaryActions = findViewById(R.id.home_primary_actions);
        homeActionList = findViewById(R.id.home_action_list);
        homeCreditStrip = findViewById(R.id.home_credit_strip);
        homeCreditMeter = findViewById(R.id.home_credit_meter);
        homeSetupChecks = findViewById(R.id.home_setup_checks);
        homeSetupCheck1 = findViewById(R.id.home_setup_check_1);
        homeSetupCheck2 = findViewById(R.id.home_setup_check_2);
        homeSetupCheck3 = findViewById(R.id.home_setup_check_3);
        homePrimaryButton = findViewById(R.id.home_primary_button);
        homeKeyboardButton = findViewById(R.id.home_keyboard_button);
        homeUpgradeButton = findViewById(R.id.home_upgrade_button);

        View settingsButton = findViewById(R.id.home_action_menu);
        View profileButton = findViewById(R.id.home_profile_button);
        View overlayFeature = findViewById(R.id.home_feature_overlay);
        View keyboardFeature = findViewById(R.id.home_feature_keyboard);
        bindBottomNavigation();

        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> startActivity(new Intent(v.getContext(), SettingsHubActivity.class)));
        }
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> openAccountLaunchDetail());
        }
        if (overlayFeature != null) {
            overlayFeature.setOnClickListener(v -> OverlayServiceManager.startIfEnabled(this));
        }
        if (keyboardFeature != null) {
            keyboardFeature.setOnClickListener(v -> openLaunchDetail(LaunchStateActivity.STATE_KEYBOARD_SETTINGS));
        }
        if (homeKeyboardButton != null) {
            homeKeyboardButton.setOnClickListener(v -> openLaunchDetail(LaunchStateActivity.STATE_KEYBOARD_SETTINGS));
        }
    }

    private void bindBottomNavigation() {
        View navHome = findViewById(R.id.home_nav_home);
        View navUsage = findViewById(R.id.home_nav_usage);
        View navSettings = findViewById(R.id.home_nav_settings);

        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                ScrollView scrollView = findViewById(R.id.home_scroll);
                if (scrollView != null) {
                    scrollView.smoothScrollTo(0, 0);
                }
            });
        }
        if (navUsage != null) {
            navUsage.setOnClickListener(v -> openUsageTab());
        }
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> openSettingsTab());
        }
    }

    private void openUsageTab() {
        Intent intent = new Intent(this, LaunchStateActivity.class);
        intent.putExtra(LaunchStateActivity.EXTRA_STATE, LaunchStateActivity.STATE_AI_USAGE);
        startBottomTabActivity(intent);
    }

    private void openSettingsTab() {
        startBottomTabActivity(new Intent(this, SettingsHubActivity.class));
    }

    private void startBottomTabActivity(Intent intent) {
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void requestAppNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {

                // Permission is not granted, request it
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        APP_NOTIFICATION_PERMISSION_REQUEST_CODE
                );
            } else {
                // Permission already granted
                NotificationService.getFCMToken();
                refreshHomeLaunchStateAfterPermissionSettles();
            }
        } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            try {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
        } else {
            // Permission not required for versions below Android 13
            NotificationService.getFCMToken();
            refreshHomeLaunchStateAfterPermissionSettles();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == APP_NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with FCM token retrieval
                NotificationService.getFCMToken();
                Toast.makeText(this, "App notifications enabled.", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied
                Toast.makeText(this, "App notifications are needed for WittyKeys push updates.", Toast.LENGTH_LONG).show();
            }
            refreshHomeLaunchStateAfterPermissionSettles();
        }
    }
    private void showInputMethodPicker() {
        InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext().getSystemService(INPUT_METHOD_SERVICE);
        inputMethodManager.showInputMethodPicker();
        refreshHomeLaunchStateAfterKeyboardSelectionSettles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        User user = getValidatedAccountUser();
        userId = user != null ? user.getId() : null;

        EncryptedPreferences.FreeTrialInfo freeTrialInfo = EncryptedPreferences.getFreeTrialInfo();
        EncryptedPreferences.SubscriptionInfo subscriptionInfo = EncryptedPreferences.getSubscriptionInfo();

        // Local subscription cache can disable paid mode, but it must not grant it.
        // Granting the paid tier is reserved for Billing purchase success and Firestore sync.
        if (user == null) {
            // No user logged in — not unlimited
            DailyUsageTracker.getInstance(this).setUnlimited(false);
        } else if (subscriptionInfo == null
                || !"ACTIVE".equals(subscriptionInfo.getStatus())
                || Subscription.FT_PACKAGE_ID_STRING.equals(subscriptionInfo.getPackageId())) {
            DailyUsageTracker.getInstance(this).setUnlimited(false);
        }

        refreshHomeLaunchStateAfterPermissionSettles();
        syncPaidEntitlementIfSignedIn(user);

        OverlayServiceManager.hideOverlay(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the BroadcastReceiver
        if (inputMethodReceiver != null) {
            try {
                unregisterReceiver(inputMethodReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver not registered or already unregistered", e);
            }
        }
        homeRefreshHandler.removeCallbacks(homeSettlingRefreshShort);
        homeRefreshHandler.removeCallbacks(homeSettlingRefreshLong);
    }

    private void refreshHomeLaunchStateAfterPermissionSettles() {
        refreshHomeLaunchState();
        homeRefreshHandler.removeCallbacks(homeSettlingRefreshShort);
        homeRefreshHandler.removeCallbacks(homeSettlingRefreshLong);
        homeRefreshHandler.postDelayed(homeSettlingRefreshShort, 450);
        homeRefreshHandler.postDelayed(homeSettlingRefreshLong, 1400);
    }

    private void refreshHomeLaunchStateAfterKeyboardSelectionSettles() {
        refreshHomeLaunchStateAfterPermissionSettles();
        homeRefreshHandler.postDelayed(homeSettlingRefreshLong, 3200);
    }

    private void refreshHomeLaunchState() {
        User user = getValidatedAccountUser();
        EncryptedPreferences.FreeTrialInfo freeTrialInfo = EncryptedPreferences.getFreeTrialInfo();
        EncryptedPreferences.SubscriptionInfo subscriptionInfo = EncryptedPreferences.getSubscriptionInfo();
        boolean isKeyboardEnabled = isWittyKeysEnabled();
        boolean isKeyboardDefault = isWittyKeysDefaultInputMethod();

        Log.d(TAG, "onResume - isWittyKeysEnabled: " + isKeyboardEnabled + ", isWittyKeysDefault: " + isKeyboardDefault);

        if (enableInstruction != null) {
            enableInstruction.setVisibility(View.GONE);
        }

        updateUserCardState(user, freeTrialInfo, subscriptionInfo, isKeyboardEnabled, isKeyboardDefault);

        Log.d(TAG, "User state: user_present=" + (user != null));
    }

    private void syncPaidEntitlementIfSignedIn(User user) {
        if (user == null || user.getId() == null || user.getId().trim().isEmpty()) {
            return;
        }
        Subscription.syncPaidEntitlementFromFirestore(
                this,
                user.getId(),
                com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                paidActive -> runOnUiThread(this::refreshHomeLaunchState));
    }

    // --- Helper method to update the launch Home UI ---
    private void updateUserCardState(User user, EncryptedPreferences.FreeTrialInfo freeTrialInfo, EncryptedPreferences.SubscriptionInfo subscriptionInfo, boolean isKeyboardEnabled, boolean isKeyboardDefault) {
        DailyUsageTracker tracker = DailyUsageTracker.getInstance(this);
        AccountEntitlementSnapshot snapshot = AccountEntitlementSnapshotProvider.current(this);
        int remainingActions = snapshot.actionsRemainingToday;
        SetupChecklistState setup = SetupChecklistStateProvider.current(this);
        DebugHomeFixture debugFixture = debugHomeFixture();
        if (debugFixture != null) {
            renderHomeLaunchState(debugFixture.state, debugFixture.remainingActions, 20);
            if (debugFixture.showSetupRows) {
                renderDebugSetupCards();
            } else if (homeActionList != null) {
                homeActionList.removeAllViews();
                homeActionList.setVisibility(View.GONE);
            }
            Log.d(TAG, "Rendered debug Home state: " + debugFixture.debugState);
            return;
        }
        HomeLaunchState launchState = HomeLaunchState.from(
                snapshot.authState == AccountEntitlementSnapshot.AuthState.SIGNED_IN,
                isKeyboardEnabled,
                isKeyboardDefault,
                OverlayPermissionFlow.hasRequiredPermissions(this),
                snapshot.isPaidActive,
                remainingActions,
                true,
                pendingSetupCount(setup)
        );
        renderHomeLaunchState(launchState, remainingActions, tracker.getDailyLimit());
        renderPendingSetupCards(setup);

        Log.d(TAG, "Updating Home Launch State - User: " + (snapshot.authState == AccountEntitlementSnapshot.AuthState.SIGNED_IN) + ", KB Enabled: " + isKeyboardEnabled + ", KB Default: " + isKeyboardDefault + ", Actions remaining: " + remainingActions + ", Unlimited: " + snapshot.isPaidActive);
    }

    private void renderHomeLaunchState(HomeLaunchState state, int remainingActions, int dailyLimit) {
        if (homeHeadline != null) homeHeadline.setText(state.headline);
        if (homeSubhead != null) homeSubhead.setText(state.subhead);
        if (homeTopSubtitle != null) homeTopSubtitle.setText(state.topSubtitle);
        if (homeStageTitle != null) homeStageTitle.setText(state.statusTitle);
        if (homeStageSubtitle != null) homeStageSubtitle.setText(state.statusSubtitle);
        if (homeCreditValue != null) homeCreditValue.setText(state.creditLabel);
        applyHomeStateVisuals(state);
        setVisible(homePrimaryActions, !state.showSetupRecovery);
        setVisible(homeCreditStrip, !state.showSetupRecovery);

        if (homePrimaryButton != null) {
            homePrimaryButton.setText(state.primaryAction);
            homePrimaryButton.setOnClickListener(v -> handleHomePrimaryAction(state));
        }
        if (homeKeyboardButton != null) {
            homeKeyboardButton.setText(state.secondaryAction);
            homeKeyboardButton.setOnClickListener(v -> handleHomeSecondaryAction(state));
        }
        View overlayFeature = findViewById(R.id.home_feature_overlay);
        if (overlayFeature != null) {
            overlayFeature.setOnClickListener(v -> handleHomePrimaryAction(state));
        }
        View keyboardFeature = findViewById(R.id.home_feature_keyboard);
        if (keyboardFeature != null) {
            keyboardFeature.setOnClickListener(v -> handleHomeSecondaryAction(state));
        }
        if (homeUpgradeButton != null) {
            homeUpgradeButton.setText(state.usageAction);
            homeUpgradeButton.setVisibility(state.usageAction == null || state.usageAction.trim().isEmpty()
                    ? View.GONE
                    : View.VISIBLE);
            homeUpgradeButton.setOnClickListener(v -> handleHomeUsageAction(state));
        }
        if (homeCreditCaption != null) {
            homeCreditCaption.setText(state.usageTitle);
        }
        if (homeCreditMeter != null) {
            int max = Math.max(dailyLimit, 1);
            homeCreditMeter.setMax(max);
            homeCreditMeter.setProgress(state.mode == HomeLaunchState.Mode.PAID_ACTIVE
                    ? max
                    : Math.max(0, Math.min(remainingActions, max)));
            homeCreditMeter.setVisibility(state.mode == HomeLaunchState.Mode.PAID_ACTIVE
                    || state.mode == HomeLaunchState.Mode.BACKEND_ERROR
                    ? View.GONE
                    : View.VISIBLE);
        }
        if (homeSetupChecks != null) {
            boolean showReadyChecks = state.mode == HomeLaunchState.Mode.PAID_ACTIVE;
            homeSetupChecks.setVisibility(showReadyChecks ? View.VISIBLE : View.GONE);
        }
        if (homeStageMessage != null) {
            boolean showMessage = state.mode == HomeLaunchState.Mode.QUOTA_EMPTY
                    || state.mode == HomeLaunchState.Mode.BACKEND_ERROR;
            homeStageMessage.setText(state.statusSubtitle);
            homeStageMessage.setVisibility(showMessage ? View.VISIBLE : View.GONE);
        }
        if (homeStageReply != null) {
            homeStageReply.setVisibility(View.GONE);
        }
        setSetupCheck(homeSetupCheck1, "Overlay");
        setSetupCheck(homeSetupCheck2, "Quick Reply");
        setSetupCheck(homeSetupCheck3, "Keyboard");
    }

    private void setSetupCheck(TextView view, String label) {
        if (view == null) {
            return;
        }
        SpannableString text = new SpannableString("ON\n" + label);
        text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.wk_overlay_dark_accent)), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(0.78f), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
    }

    private void applyHomeStateVisuals(HomeLaunchState state) {
        int accent = ContextCompat.getColor(this, R.color.wk_overlay_dark_accent);
        int red = Color.rgb(248, 113, 113);
        int orange = Color.rgb(251, 146, 60);
        int purple = Color.rgb(167, 139, 250);
        int stateColor;
        String stateMark;

        switch (state.mode) {
            case QUOTA_EMPTY:
                stateColor = red;
                stateMark = "0";
                break;
            case QUOTA_LOW:
                stateColor = orange;
                stateMark = "LOW";
                break;
            case BACKEND_ERROR:
                stateColor = purple;
                stateMark = "AI";
                break;
            case SETUP_RECOVERY:
                stateColor = accent;
                stateMark = setupMarkText(state.statusTitle);
                break;
            case PAID_ACTIVE:
                stateColor = accent;
                stateMark = "\u2713";
                break;
            case SIGNED_IN_FREE:
            case ANONYMOUS_READY:
            default:
                stateColor = accent;
                stateMark = "\u2713";
                break;
        }

        if (homeStateMark != null) {
            homeStateMark.setText(stateMark);
            homeStateMark.setTextColor(ContextCompat.getColor(this, R.color.primary_app_color));
            homeStateMark.setBackground(rounded(stateColor, dp(16), Color.TRANSPARENT));
        }
        if (homeCreditValue != null) {
            boolean filledPill = state.mode == HomeLaunchState.Mode.PAID_ACTIVE;
            homeCreditValue.setTextColor(filledPill
                    ? ContextCompat.getColor(this, R.color.primary_app_color)
                    : stateColor);
            homeCreditValue.setBackground(rounded(
                    filledPill ? stateColor : softColor(stateColor, 34),
                    dp(17),
                    filledPill ? Color.TRANSPARENT : softColor(stateColor, 56)));
        }
    }

    private String setupMarkText(String statusTitle) {
        if (statusTitle == null) return "!";
        if (statusTitle.startsWith("One")) return "1";
        if (statusTitle.startsWith("Two")) return "2";
        return "!";
    }

    private int softColor(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void renderPendingSetupCards(SetupChecklistState setup) {
        if (homeActionList == null || setup == null) {
            return;
        }
        homeActionList.removeAllViews();
        int pendingCount = 0;

        SetupChecklistState.Item keyboardEnabled = setup.item(SetupChecklistState.ItemId.KEYBOARD_ENABLED);
        SetupChecklistState.Item keyboardDefault = setup.item(SetupChecklistState.ItemId.KEYBOARD_DEFAULT);
        if (!keyboardEnabled.isDone()) {
            addSetupCard(new SetupDisplayItem(
                    SetupChecklistState.ItemId.KEYBOARD_ENABLED,
                    "Enable Keyboard",
                    "Write with AI suggestions in any text field.",
                    "Enable"
            ), R.drawable.ic_keyboard_24);
            pendingCount++;
        } else if (!keyboardDefault.isDone()) {
            addSetupCard(new SetupDisplayItem(
                    SetupChecklistState.ItemId.KEYBOARD_DEFAULT,
                    "Keyboard configuration",
                    "Select WittyKeys when you want the assistant bar while typing.",
                    "Configure"
            ), R.drawable.ic_keyboard_24);
            pendingCount++;
        }

        SetupChecklistState.Item overlay = setup.item(SetupChecklistState.ItemId.OVERLAY_BUBBLE);
        if (!overlay.isDone()) {
            addSetupCard(new SetupDisplayItem(
                    SetupChecklistState.ItemId.OVERLAY_BUBBLE,
                    "Enable Floating Overlay",
                    "Ask AI about screens and reply from any app.",
                    "Enable"
            ), R.drawable.wk_logo_overlay);
            pendingCount++;
        }

        SetupChecklistState.Item appNotifications = setup.item(SetupChecklistState.ItemId.APP_NOTIFICATIONS);
        if (!appNotifications.isDone()) {
            addSetupCard(new SetupDisplayItem(
                    SetupChecklistState.ItemId.APP_NOTIFICATIONS,
                    "Enable app notifications",
                    "Receive push updates for account, subscription, usage, and important WittyKeys alerts.",
                    "Enable"
            ), R.drawable.ic_check_circle_24);
            pendingCount++;
        }

        SetupChecklistState.Item notifications = setup.item(SetupChecklistState.ItemId.NOTIFICATION_ACCESS);
        if (!notifications.isDone()) {
            addSetupCard(new SetupDisplayItem(
                    SetupChecklistState.ItemId.NOTIFICATION_ACCESS,
                    "Enable Quick Reply access",
                    "Read new-message notifications to prepare replies. Message text is not logged.",
                    "Enable"
            ), R.drawable.ic_check_circle_24);
            pendingCount++;
        }

        SetupChecklistState.Item accessibility = setup.item(SetupChecklistState.ItemId.ACCESSIBILITY_HELPER);
        if (!accessibility.isDone()) {
            addSetupCard(new SetupDisplayItem(
                    SetupChecklistState.ItemId.ACCESSIBILITY_HELPER,
                    "Enable Send helper",
                    "Send overlay replies into supported apps after your confirmation.",
                    "Enable"
            ), R.drawable.screen_reader_v2_icon);
            pendingCount++;
        }

        if (pendingCount == 0) {
            homeActionList.setVisibility(View.GONE);
        } else {
            homeActionList.setVisibility(View.VISIBLE);
        }
    }

    private void renderDebugSetupCards() {
        if (homeActionList == null) {
            return;
        }
        homeActionList.removeAllViews();
        addSetupCard(new SetupDisplayItem(
                SetupChecklistState.ItemId.OVERLAY_BUBBLE,
                "Floating overlay",
                "Ask AI about any screen without switching apps.",
                "Enable"
        ), R.drawable.wk_logo_overlay);
        addSetupCard(new SetupDisplayItem(
                SetupChecklistState.ItemId.KEYBOARD_DEFAULT,
                "Keyboard configuration",
                "Use WittyKeys while typing in any app.",
                "Open"
        ), R.drawable.ic_keyboard_24);
        homeActionList.setVisibility(View.VISIBLE);
    }

    private int pendingSetupCount(SetupChecklistState setup) {
        if (setup == null) {
            return 0;
        }
        int pendingCount = 0;
        SetupChecklistState.Item keyboardEnabled = setup.item(SetupChecklistState.ItemId.KEYBOARD_ENABLED);
        SetupChecklistState.Item keyboardDefault = setup.item(SetupChecklistState.ItemId.KEYBOARD_DEFAULT);
        if (!keyboardEnabled.isDone() || !keyboardDefault.isDone()) {
            pendingCount++;
        }
        if (!setup.item(SetupChecklistState.ItemId.OVERLAY_BUBBLE).isDone()) {
            pendingCount++;
        }
        if (!setup.item(SetupChecklistState.ItemId.APP_NOTIFICATIONS).isDone()) {
            pendingCount++;
        }
        if (!setup.item(SetupChecklistState.ItemId.NOTIFICATION_ACCESS).isDone()) {
            pendingCount++;
        }
        if (!setup.item(SetupChecklistState.ItemId.ACCESSIBILITY_HELPER).isDone()) {
            pendingCount++;
        }
        return pendingCount;
    }

    private DebugHomeFixture debugHomeFixture() {
        if (!BuildConfig.DEBUG || getIntent() == null) {
            return null;
        }
        String requestedState = getIntent().getStringExtra(EXTRA_DEBUG_HOME_STATE);
        if (requestedState == null || requestedState.trim().isEmpty()) {
            requestedState = getSharedPreferences("wk_debug_home", MODE_PRIVATE)
                    .getString(EXTRA_DEBUG_HOME_STATE, null);
        }
        if (requestedState == null || requestedState.trim().isEmpty()) {
            return null;
        }
        switch (requestedState) {
            case "H01":
                return new DebugHomeFixture(
                        requestedState,
                        HomeLaunchState.from(false, true, true, true, false, 18, true, 0),
                        18,
                        false);
            case "H02":
                return new DebugHomeFixture(
                        requestedState,
                        HomeLaunchState.from(false, false, false, false, false, 20, true, 2),
                        20,
                        true);
            case "H03":
                return new DebugHomeFixture(
                        requestedState,
                        HomeLaunchState.from(true, true, true, true, false, 12, true, 0),
                        12,
                        false);
            case "H04":
                return new DebugHomeFixture(
                        requestedState,
                        HomeLaunchState.from(true, true, true, true, true, 20, true, 0),
                        20,
                        false);
            case "H05":
                return new DebugHomeFixture(
                        requestedState,
                        HomeLaunchState.from(true, true, true, true, false, 0, true, 0),
                        0,
                        false);
            case "H06":
                return new DebugHomeFixture(
                        requestedState,
                        HomeLaunchState.from(true, true, true, true, false, 20, false, 0),
                        20,
                        false);
            default:
                return null;
        }
    }

    private void addSetupCard(SetupChecklistState.Item item, int iconRes) {
        addSetupCard(new SetupDisplayItem(item.id, item.title, item.benefit, item.label), iconRes);
    }

    private void addSetupCard(SetupDisplayItem item, int iconRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundResource(R.drawable.wk_app_card_bg);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> handleSetupCardAction(item.id));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        if (homeActionList.getChildCount() > 0) {
            rowParams.topMargin = dp(10);
        }

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setBackground(rounded(Color.argb(36, 255, 255, 255), dp(14), Color.argb(26, 255, 255, 255)));
        iconFrame.setPadding(dp(10), dp(10), dp(10), dp(10));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ContextCompat.getColor(this, R.color.wk_overlay_dark_accent));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconFrame.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        row.addView(iconFrame, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(ContextCompat.getColor(this, R.color.wk_overlay_dark_text));
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView benefit = new TextView(this);
        benefit.setText(item.benefit);
        benefit.setTextColor(ContextCompat.getColor(this, R.color.wk_overlay_dark_text2));
        benefit.setTextSize(12);
        benefit.setPadding(0, dp(4), 0, 0);
        copy.addView(title);
        copy.addView(benefit);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(12);
        copyParams.rightMargin = dp(10);
        row.addView(copy, copyParams);

        TextView cta = new TextView(this);
        cta.setText(item.label);
        cta.setGravity(Gravity.CENTER);
        cta.setTextColor(ContextCompat.getColor(this, R.color.primary_app_color));
        cta.setTextSize(12);
        cta.setTypeface(Typeface.DEFAULT_BOLD);
        cta.setPadding(dp(12), 0, dp(12), 0);
        cta.setMinWidth(dp(72));
        cta.setBackground(rounded(ContextCompat.getColor(this, R.color.wk_overlay_dark_accent), dp(15), Color.TRANSPARENT));
        row.addView(cta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        homeActionList.addView(row, rowParams);
    }

    private void handleSetupCardAction(SetupChecklistState.ItemId id) {
        switch (id) {
            case KEYBOARD_ENABLED:
                try {
                    startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                } catch (ActivityNotFoundException e) {
                    showInputMethodPicker();
                }
                break;
            case KEYBOARD_DEFAULT:
                showInputMethodPicker();
                break;
            case OVERLAY_BUBBLE:
                openLaunchDetail(LaunchStateActivity.STATE_PERMISSION_RECOVERY);
                break;
            case APP_NOTIFICATIONS:
                requestAppNotificationPermission();
                break;
            case NOTIFICATION_ACCESS:
                openLaunchDetail(LaunchStateActivity.STATE_NLS_PERMISSION);
                break;
            case ACCESSIBILITY_HELPER:
                openLaunchDetail(LaunchStateActivity.STATE_ACCESSIBILITY_PERMISSION);
                break;
            case SCREEN_CAPTURE:
            default:
                openLaunchDetail(LaunchStateActivity.STATE_SCREEN_CAPTURE_PERMISSION);
                break;
        }
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SetupDisplayItem {
        final SetupChecklistState.ItemId id;
        final String title;
        final String benefit;
        final String label;

        SetupDisplayItem(SetupChecklistState.ItemId id, String title, String benefit, String label) {
            this.id = id;
            this.title = title;
            this.benefit = benefit;
            this.label = label;
        }
    }

    private static final class DebugHomeFixture {
        final String debugState;
        final HomeLaunchState state;
        final int remainingActions;
        final boolean showSetupRows;

        DebugHomeFixture(String debugState, HomeLaunchState state, int remainingActions, boolean showSetupRows) {
            this.debugState = debugState;
            this.state = state;
            this.remainingActions = remainingActions;
            this.showSetupRows = showSetupRows;
        }
    }

    private void handleHomePrimaryAction(HomeLaunchState state) {
        switch (state.mode) {
            case SETUP_RECOVERY:
                openLaunchDetail(LaunchStateActivity.STATE_APP_SETUP);
                break;
            case BACKEND_ERROR:
                refreshHomeLaunchState();
                break;
            case QUOTA_LOW:
            case QUOTA_EMPTY:
                openSubscriptionLaunchDetail("home_primary");
                break;
            case PAID_ACTIVE:
            case SIGNED_IN_FREE:
            case ANONYMOUS_READY:
            default:
                OverlayServiceManager.startIfEnabled(this);
                Toast.makeText(this, "Use Overlay from the floating bubble or Keyboard where you type.", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void handleHomeSecondaryAction(HomeLaunchState state) {
        switch (state.mode) {
            case BACKEND_ERROR:
                openSettingsTab();
                break;
            case QUOTA_EMPTY:
                openUsageTab();
                break;
            case SETUP_RECOVERY:
                openLaunchDetail(LaunchStateActivity.STATE_APP_SETUP);
                break;
            case QUOTA_LOW:
            case PAID_ACTIVE:
            case SIGNED_IN_FREE:
            case ANONYMOUS_READY:
            default:
                openLaunchDetail(LaunchStateActivity.STATE_KEYBOARD_SETTINGS);
                break;
        }
    }

    private void handleHomeUsageAction(HomeLaunchState state) {
        switch (state.mode) {
            case BACKEND_ERROR:
                refreshHomeLaunchState();
                break;
            case PAID_ACTIVE:
                openSubscriptionLaunchDetail("home_manage_plus");
                break;
            case SETUP_RECOVERY:
                openLaunchDetail(LaunchStateActivity.STATE_APP_SETUP);
                break;
            case QUOTA_LOW:
            case QUOTA_EMPTY:
            case SIGNED_IN_FREE:
            case ANONYMOUS_READY:
            default:
                openSubscriptionLaunchDetail("home_upgrade");
                break;
        }
    }

    private void openSubscriptionLaunchDetail(String action) {
        openLaunchDetail(LaunchStateActivity.STATE_SUBSCRIPTION_PLUS_OFFER);
    }

    private void openLaunchDetail(String state) {
        Intent intent = new Intent(this, LaunchStateActivity.class);
        intent.putExtra(LaunchStateActivity.EXTRA_STATE, state);
        startActivity(intent);
    }

    private void openAccountLaunchDetail() {
        User user = getValidatedAccountUser();
        openLaunchDetail(user == null
                ? LaunchStateActivity.STATE_ACCOUNT_SIGNIN_REASON
                : LaunchStateActivity.STATE_ACCOUNT_PROFILE_SIGNED_IN);
    }

    private User getValidatedAccountUser() {
        User user = EncryptedPreferences.getUserLoggedInInfo();
        if (user == null) {
            return null;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            EncryptedPreferences.clearUserInfo();
            EncryptedPreferences.clearSubscriptionInfo();
            DailyUsageTracker.getInstance(this).setUnlimited(false);
            return null;
        }
        return user;
    }

    // --- Helper to update Keyboard Status UI (Instruction Card and Step Items) ---
    private void updateKeyboardStatusUI(boolean isEnabled, boolean isDefault) {
        if (enableInstruction != null) {
            enableInstruction.setVisibility(View.GONE);
        }
        // Update steps 1 and 2 in the user card
        updateItemCompletion(itemIcon1, itemTitle1, isEnabled);
        updateItemCompletion(itemIcon2, itemTitle2, isDefault);
    }

    // --- Helper method to apply/remove strikethrough and change icon ---
    private void updateItemCompletion(ImageView icon, TextView title, boolean completed) {
        if (icon == null || title == null) return;

        if (completed) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(ContextCompat.getColor(this, R.color.third_app_color)); // Use a disabled color
            icon.setImageResource(R.drawable.ic_check_box); // Use a filled circle check
            icon.setColorFilter(ContextCompat.getColor(this, R.color.third_app_color)); // Use a success color (e.g., green)
        } else {
            title.setPaintFlags(title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            title.setTextColor(ContextCompat.getColor(this, R.color.third_app_color)); // Restore original color
            icon.setImageResource(R.drawable.ic_check_box_blank); // Use the checkbox outline
            icon.setColorFilter(ContextCompat.getColor(this, R.color.third_app_color)); // Restore original tint
        }
    }


    private boolean isWittyKeysDefaultInputMethod() {
        String wittyKeysLabel = "WittyKeys";
        String wittyKeysId = "project.witty.keys/.latin.LatinIME";

        // Get the current default input method ID
        String currentInputMethodId = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);

        // Get the InputMethodManager
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> inputMethods = imm.getEnabledInputMethodList();

        // Check if the current input method matches WittyKeys
        for (InputMethodInfo inputMethod : inputMethods) {
            String id = inputMethod.getId();
            CharSequence label = inputMethod.loadLabel(getPackageManager());

            if (id.equals(currentInputMethodId) && (id.equals(wittyKeysId) || label.toString().equals(wittyKeysLabel))) {
                return true;
            }
        }

        return false;
    }

    // Check if WittyKeys is *enabled* in Android settings
    private boolean isWittyKeysEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        List<InputMethodInfo> enabledMethods = imm.getEnabledInputMethodList();
        String wittyKeysPackageName = getPackageName(); // Use package name for reliability

        for (InputMethodInfo method : enabledMethods) {
            if (wittyKeysPackageName.equals(method.getPackageName())) {
                Log.d(TAG, "WittyKeys package found in enabled list.");
                return true;
            }
        }
        Log.d(TAG, "WittyKeys package NOT found in enabled list.");
        return false;
    }

    public void mailUs(String subject) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // Only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getResources().getString(R.string.contact_mail)});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        try {
            startActivity(Intent.createChooser(intent, "Send Email"));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email client installed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsAppChat(String phoneNumber) {
        try {
            String url = "https://api.whatsapp.com/send?phone=" + phoneNumber;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp is not installed on your device", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTopConfetti() {
        EmitterConfig emitterConfig = new Emitter(5L, TimeUnit.SECONDS).perSecond(100);
        Party party = new PartyFactory(emitterConfig)
                .angle(Angle.BOTTOM)
                .spread(Spread.ROUND)
                .colors(Arrays.asList(0xfce18a, 0xff726d, 0xf4306d, 0xb48def)) // Example colors
                .setSpeedBetween(0f, 15f) // Slow fall speed
                .position(new Position.Relative(0.5, 0.0).between(new Position.Relative(1.0, 0.0))) // Emit from top edge
                .fadeOutEnabled(true)
                .timeToLive(3000L) // Live for 3 seconds
                .build();

        konfettiView.start(party);
    }

    private void showExplosionConfetti() {
        EmitterConfig emitterConfig = new Emitter(100L, TimeUnit.MILLISECONDS).max(100);
        Party party = new PartyFactory(emitterConfig)
                .spread(360)
                .colors(Arrays.asList(0xfce18a, 0xff726d, 0xf4306d, 0xb48def))
                .setSpeedBetween(0f, 30f)
                .position(new Position.Relative(0.5, 0.3)) // Emit from near center
                .fadeOutEnabled(true)
                .timeToLive(2000L)
                .build();
        konfettiView.start(party);
    }

}
