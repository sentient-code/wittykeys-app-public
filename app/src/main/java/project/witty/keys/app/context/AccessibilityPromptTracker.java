package project.witty.keys.app.context;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.util.Log;

/**
 * Tracks reply suggestion taps in real keyboard usage and triggers an inline
 * accessibility permission prompt after 3+ taps. Replaces the aggressive
 * upfront permission request with a deferred, value-first approach.
 */
public class AccessibilityPromptTracker {

    private static final String TAG = "AccPromptTracker";
    private static final String PREFS_NAME = "wk_onboarding";
    private static final String KEY_TAP_COUNT = "wk_onboarding_reply_tap_count";
    private static final String KEY_PROMPT_SHOWN = "wk_onboarding_acc_prompt_shown";
    private static final String KEY_PROMPT_DISMISSED = "wk_onboarding_acc_prompt_dismissed";
    private static final int TAP_THRESHOLD = 3;

    private static AccessibilityPromptTracker instance;
    private final SharedPreferences prefs;
    private final Context context;

    public static synchronized AccessibilityPromptTracker getInstance(Context context) {
        if (instance == null) {
            instance = new AccessibilityPromptTracker(context.getApplicationContext());
        }
        return instance;
    }

    private AccessibilityPromptTracker(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Call this every time user taps a reply suggestion in SmartAssistantBar */
    public void recordReplyTap() {
        int count = prefs.getInt(KEY_TAP_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_TAP_COUNT, count).apply();
        Log.d(TAG, "[TAP] Reply tap count: " + count);
    }

    /** Returns true when it's time to show the accessibility prompt */
    public boolean shouldShowPrompt() {
        if (isAccessibilityEnabled()) return false;
        if (prefs.getBoolean(KEY_PROMPT_SHOWN, false)) return false;
        if (prefs.getBoolean(KEY_PROMPT_DISMISSED, false)) return false;
        return prefs.getInt(KEY_TAP_COUNT, 0) >= TAP_THRESHOLD;
    }

    public void markPromptShown() {
        prefs.edit().putBoolean(KEY_PROMPT_SHOWN, true).apply();
        Log.d(TAG, "[PROMPT] Accessibility prompt shown");
    }

    public void markPromptDismissed() {
        prefs.edit().putBoolean(KEY_PROMPT_DISMISSED, true).apply();
        Log.d(TAG, "[PROMPT] User tapped Later — prompt dismissed");
    }

    public void openAccessibilitySettings() {
        Log.d(TAG, "[PROMPT] User tapped Enable — opening settings");
        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public boolean isAccessibilityEnabled() {
        try {
            String enabledServices = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            return enabledServices != null && enabledServices.contains(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    public int getTapCount() {
        return prefs.getInt(KEY_TAP_COUNT, 0);
    }
}
