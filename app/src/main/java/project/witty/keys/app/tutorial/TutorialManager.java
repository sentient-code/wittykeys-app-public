package project.witty.keys.app.tutorial;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.Activity;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.firebase.analytics.FirebaseAnalytics;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.ActivationManager;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.NLSPermissionHelper;

/**
 * Tutorial Manager - Singleton
 * Manages tutorial state and communication between keyboard and tutorial activity
 */
public class TutorialManager {

    private static final String TAG = "TutorialManager";
    private static final String PREFS_NAME = "tutorial_state";
    private static final String KEY_TUTORIAL_MODE = "tutorial_mode_active";
    private static final String KEY_CURRENT_TASK = "current_task";
    private static final String KEY_TUTORIAL_COMPLETED = "tutorial_completed";

    // Broadcast actions
    public static final String ACTION_AI_CHAT_CLICKED = "com.wittykeys.tutorial.AI_CHAT_CLICKED";
    public static final String ACTION_READ_SCREEN_CLICKED = "com.wittykeys.tutorial.READ_SCREEN_CLICKED";
    public static final String ACTION_TONALITY_CLICKED = "com.wittykeys.tutorial.TONALITY_CLICKED";
    public static final String ACTION_GRAMMAR_CLICKED = "com.wittykeys.tutorial.GRAMMAR_CLICKED";
    public static final String ACTION_TRANSLATE_CLICKED = "com.wittykeys.tutorial.TRANSLATE_CLICKED";
    public static final String ACTION_AI_RESPONSE_APPLIED = "com.wittykeys.tutorial.AI_RESPONSE_APPLIED";
    public static final String ACTION_TEXT_TYPED = "com.wittykeys.tutorial.TEXT_TYPED";
    public static final String ACTION_TASK_COMPLETED = "com.wittykeys.tutorial.TASK_COMPLETED";

    private static TutorialManager instance;
    private Context context;
    private SharedPreferences prefs;
    private ActivationManager activationManager; // Store instance
    private FirebaseAnalytics firebaseAnalytics; // For EventHelpers

    private TutorialManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.activationManager = new ActivationManager(this.context); // Create instance
        this.firebaseAnalytics = FirebaseAnalytics.getInstance(this.context);
    }

    public static synchronized TutorialManager getInstance(Context context) {
        if (instance == null) {
            instance = new TutorialManager(context);
        }
        return instance;
    }

    // ========== Tutorial State Management ==========

    public boolean isTutorialMode() {
        return prefs.getBoolean(KEY_TUTORIAL_MODE, false);
    }

    public void startTutorialMode() {
        if (DebugConfig.isDebugMode) {
            android.util.Log.d(TAG, "🎓 Tutorial mode STARTED");
        }
        prefs.edit()
                .putBoolean(KEY_TUTORIAL_MODE, true)
                .putString(KEY_CURRENT_TASK, TutorialTask.ENABLE_KEYBOARD.name())
                .apply();
    }

    public void endTutorialMode() {
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎓 Tutorial mode ENDED");
        }
        prefs.edit()
                .putBoolean(KEY_TUTORIAL_MODE, false)
                .putBoolean(KEY_TUTORIAL_COMPLETED, true)
                .remove(KEY_CURRENT_TASK)
                .apply();

        // FIXED: Use tracking ID that works for anonymous users
        String trackingId = getTrackingId();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 Firing EVENT: tutorial_completed");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
        }

        EventHelpers.triggerTutorialCompleted(trackingId, firebaseAnalytics);
    }

    public boolean hasTutorialCompleted() {
        return prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false);
    }

    public TutorialTask getCurrentTask() {
        String taskName = prefs.getString(KEY_CURRENT_TASK, TutorialTask.ENABLE_KEYBOARD.name());
        try {
            return TutorialTask.valueOf(taskName);
        } catch (IllegalArgumentException e) {
            return TutorialTask.ENABLE_KEYBOARD;
        }
    }

    public void setCurrentTask(TutorialTask task) {
        if (DebugConfig.isDebugMode) {
            android.util.Log.d(TAG, "📋 Current task set to: " + task.name());
        }
        prefs.edit().putString(KEY_CURRENT_TASK, task.name()).apply();
    }

    public void markTaskCompleted(TutorialTask task) {
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "✅ Task completed: " + task.name());
        }

        // FIXED: Use tracking ID that works for anonymous users
        String trackingId = getTrackingId();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 Firing EVENT: tutorial_step_completed");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   step_name: " + task.name());
        }

        EventHelpers.triggerTutorialStepCompleted(trackingId, task.name(), firebaseAnalytics);


        // Send broadcast to tutorial activity
        Intent intent = new Intent(ACTION_TASK_COMPLETED);
        intent.putExtra("task", task.name());
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    // ========== Feature Highlight State ==========

    public boolean shouldHighlightButton(String buttonType) {
        if (!isTutorialMode()) return false;

        TutorialTask currentTask = getCurrentTask();
        switch (currentTask) {
            case AI_CHAT_TASK:
                return buttonType.equals("AI_CHAT");
            case READ_SCREEN_TASK:
                return buttonType.equals("READ_SCREEN");
            case TONALITY_TASK:
                return buttonType.equals("TONALITY");
            case GRAMMAR_TASK:
                return buttonType.equals("GRAMMAR");
            default:
                return false;
        }
    }

    /**
     * Returns the button type that should be highlighted for the current task.
     * Returns null if no button should be highlighted (e.g., ENABLE_KEYBOARD or TOKEN_EXPLANATION tasks).
     *
     * @return Button type string: "AI_CHAT", "READ_SCREEN", "TONALITY", "GRAMMAR", or null
     */
    public String getButtonToHighlight() {
        if (!isTutorialMode()) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "getButtonToHighlight: Not in tutorial mode");
            }
            return null;
        }

        TutorialTask currentTask = getCurrentTask();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "getButtonToHighlight: Current task = " + currentTask.name());
        }

        switch (currentTask) {
            case AI_CHAT_TASK:
                return "AI_CHAT";
            case READ_SCREEN_TASK:
                return "READ_SCREEN";
            case TONALITY_TASK:
                return "TONALITY";
            case GRAMMAR_TASK:
                return "GRAMMAR";
            case ENABLE_KEYBOARD:
            case TOKEN_EXPLANATION:
            default:
                // These tasks don't require CTA highlight
                return null;
        }
    }


    // ========== Event Broadcasting (Keyboard → Activity) ==========

    public void notifyButtonClicked(String buttonType) {
        if (!isTutorialMode()) return;

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎯 Button clicked: " + buttonType);
        }

        String userId = getUserId();
        if (userId == null) return;

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 Firing EVENT: tutorial_" + buttonType.toLowerCase() + "_clicked");
            Log.d(TAG, "   user_present: " + hasValue(userId));
            Log.d(TAG, "   context: tutorial");
        }

        String action = null;
        switch (buttonType) {
            case "AI_CHAT":
                action = ACTION_AI_CHAT_CLICKED;
                EventHelpers.triggerAiChatClicked(userId, firebaseAnalytics);
                break;
            case "READ_SCREEN":
                action = ACTION_READ_SCREEN_CLICKED;
                EventHelpers.triggerReadScreenClicked(userId, firebaseAnalytics);
                break;
            case "TONALITY":
                action = ACTION_TONALITY_CLICKED;
                EventHelpers.triggerTonalityClicked(userId, firebaseAnalytics);
                break;
            case "GRAMMAR":
                action = ACTION_GRAMMAR_CLICKED;
                EventHelpers.triggerGrammarClicked(userId, firebaseAnalytics);
                break;
            case "TRANSLATE":
                action = ACTION_TRANSLATE_CLICKED;
                EventHelpers.triggerTranslateClicked(userId, firebaseAnalytics);
                break;
        }

        if (action != null) {
            Intent intent = new Intent(action);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }

    public void notifyTextApplied(String appliedText) {
        if (!isTutorialMode()) return;

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📝 AI text applied: length=" + appliedText.length());
        }

        String userId = getUserId();
        if (userId != null) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "📊 Firing EVENT: tutorial_ai_text_applied");
                Log.d(TAG, "   user_present: " + hasValue(userId));
                Log.d(TAG, "   text_length: " + appliedText.length());
                Log.d(TAG, "   context: tutorial");
            }
            EventHelpers.triggerAiTextApplied(userId, appliedText.length(), firebaseAnalytics);
        }

        Intent intent = new Intent(ACTION_AI_RESPONSE_APPLIED);
        intent.putExtra("text", appliedText);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }

    public void notifyTextTyped(String text) {
        if (!isTutorialMode()) return;

        if (DebugConfig.isDebugMode) {
            android.util.Log.d(TAG, "⌨️ Text typed: " + text);
        }

        Intent intent = new Intent(ACTION_TEXT_TYPED);
        intent.putExtra("text", text);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    // ========== Helper Methods ==========

    /**
     * Get current user ID from EncryptedPreferences
     */
    private String getUserId() {
        project.witty.keys.app.entities.User user = EncryptedPreferences.getUserLoggedInInfo();
        return user != null ? user.getId() : null;
    }

    // ========== Onboarding Simulator Integration ==========

    /**
     * Check if the onboarding simulator should be shown (first-time user who hasn't
     * completed or skipped the onboarding simulator yet).
     */
    public boolean shouldShowOnboarding() {
        SharedPreferences onboardingPrefs = context.getSharedPreferences("wk_onboarding", Context.MODE_PRIVATE);
        return !onboardingPrefs.getBoolean("wk_onboarding_completed", false)
               && !hasTutorialCompleted();
    }

    // ========== Skip Tracking ==========

    private static final String PREF_NLS_SKIPPED = "onboarding_nls_skipped";
    private static final String PREF_KEYBOARD_SKIPPED = "onboarding_keyboard_skipped";

    public boolean wasNlsSkipped() {
        return prefs.getBoolean(PREF_NLS_SKIPPED, false);
    }

    public void markNlsSkipped() {
        prefs.edit().putBoolean(PREF_NLS_SKIPPED, true).apply();
    }

    public boolean wasKeyboardSkipped() {
        return prefs.getBoolean(PREF_KEYBOARD_SKIPPED, false);
    }

    public void markKeyboardSkipped() {
        prefs.edit().putBoolean(PREF_KEYBOARD_SKIPPED, true).apply();
    }

    // ========== Video Onboarding State (Build 7.0 — separate from tutorial) ==========

    private static final String PREF_ONBOARDING_COMPLETED = "onboarding_completed";

    /**
     * Check if the NEW video onboarding flow (OnboardingActivity) has been completed.
     * Separate from hasTutorialCompleted() which tracks InteractiveTutorialActivity.
     */
    public boolean hasOnboardingCompleted() {
        return prefs.getBoolean(PREF_ONBOARDING_COMPLETED, false);
    }

    /**
     * Mark the NEW video onboarding flow as completed.
     * Called by OnboardingActivity.finishOnboarding().
     */
    public void markOnboardingCompleted() {
        prefs.edit().putBoolean(PREF_ONBOARDING_COMPLETED, true).apply();
        Log.d(TAG, "Video onboarding marked complete");
    }

    /**
     * Updated first-launch routing (Build 7.0):
     * 1. If video onboarding NOT completed → OnboardingActivity
     * 2. Else if tutorial NOT completed → InteractiveTutorialActivity (existing)
     * 3. Else → null (go to HomeActivity)
     *
     * @param context Context for creating Intents
     * @return Intent to launch, or null if no onboarding/tutorial needed
     */
    public Intent getFirstLaunchIntent(Context context) {
        if (!hasOnboardingCompleted()) {
            return new Intent(context, OnboardingActivity.class);
        }
        return null;
    }

    /**
     * Check if any required permissions are missing and return an Intent to
     * OnboardingActivity that resumes from the first missing permission step.
     * Returns null if all permissions are granted and onboarding is complete.
     */
    public Intent getResumeOnboardingIntent(Context context) {
        if (!hasOnboardingCompleted()) {
            return new Intent(context, OnboardingActivity.class);
        }

        String resumeFrom = null;

        // 1. Check keyboard enabled
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean keyboardEnabled = false;
        if (imm != null) {
            String imePackageName = context.getPackageName();
            for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
                if (imi.getPackageName().equals(imePackageName)) {
                    keyboardEnabled = true;
                    break;
                }
            }
        }
        if (!keyboardEnabled && !wasKeyboardSkipped()) {
            resumeFrom = OnboardingActivity.STATE_ENABLE_KEYBOARD;
        }

        // 2. Check NLS (only if not skipped)
        if (resumeFrom == null && !wasNlsSkipped()) {
            if (!NLSPermissionHelper.isNLSEnabled(context)) {
                resumeFrom = OnboardingActivity.STATE_NLS_EXPLAIN;
            }
        }

        if (resumeFrom == null) {
            return null;
        }

        Intent intent = new Intent(context, OnboardingActivity.class);
        intent.putExtra("resume_from", resumeFrom);
        return intent;
    }

    public void resetTutorial() {
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🔄 Tutorial reset");
        }
        prefs.edit().clear().apply();
    }

    public int getTutorialProgress() {
        TutorialTask currentTask = getCurrentTask();
        return currentTask.getStepNumber();
    }

    public int getTotalTutorialSteps() {
        return TutorialTask.values().length;
    }

    /**
     * Get tracking ID for analytics (userId or device ID fallback)
     */
    private String getTrackingId() {
        String userId = getUserId();
        return EventHelpers.getTrackingId(context, userId);
    }
}
