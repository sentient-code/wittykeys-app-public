package project.witty.keys.app.utils;

import android.content.Context;
import android.util.Log;
import project.witty.keys.app.helpers.EncryptedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Simple daily AI action counter. Replaces TokenManager for Build 7.1 MVP.
 *
 * Free users: 20 AI credits per day (resets at midnight local time).
 * Paid subscribers: larger daily allowance, still bounded for cost control.
 *
 * What counts as 1 AI action:
 *   - Tone change, Grammar fix, Translation, Custom prompt
 *   - AI chat message (each send)
 *   - Screenshot analysis
 *   - Smart reply generation (per batch)
 *
 * What does NOT count:
 *   - Word predictions, clipboard copy/paste, voice-to-text
 *   - Viewing already-generated replies
 */
public class DailyUsageTracker {

    private static final String TAG = "WK_USAGE";

    // EncryptedPreferences keys
    private static final String KEY_ACTIONS_TODAY = "daily_actions_today";
    private static final String KEY_LAST_RESET_DATE = "daily_last_reset_date";
    private static final String KEY_UNLIMITED = "daily_unlimited";

    private static final int FREE_DAILY_LIMIT = 20;
    private static final int PLUS_DAILY_LIMIT = 200;

    private static DailyUsageTracker instance;
    private final Context appContext;

    private DailyUsageTracker(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized DailyUsageTracker getInstance(Context context) {
        if (instance == null) {
            instance = new DailyUsageTracker(context);
        }
        return instance;
    }

    // ─── Public API ───

    /**
     * Check if user can perform an AI action for the active daily allowance.
     */
    public boolean canUseAI() {
        return canUseAI(1);
    }

    /**
     * Check if user can perform an AI action with a specific credit cost.
     */
    public boolean canUseAI(int actionCost) {
        resetIfNewDay();
        int cost = Math.max(1, actionCost);
        return getActionsToday() + cost <= getDailyLimit();
    }

    /**
     * Record one AI action usage. Call AFTER successful API response.
     * Does not block — always records even if over limit (for analytics).
     */
    public void recordUsage() {
        recordUsage(1);
    }

    /**
     * Record AI credit usage after a successful response.
     */
    public void recordUsage(int actionCost) {
        resetIfNewDay();
        int current = getActionsToday();
        int cost = Math.max(1, actionCost);
        int newCount = current + cost;
        int dailyLimit = getDailyLimit();
        EncryptedPreferences.saveInt(KEY_ACTIONS_TODAY, newCount);
        Log.d(TAG, "AI action recorded: " + newCount + "/" + dailyLimit
              + (isUnlimited() ? " (paid plan)" : ""));
    }

    /**
     * Get remaining actions for today.
     */
    public int getRemainingActions() {
        resetIfNewDay();
        int dailyLimit = getDailyLimit();
        return Math.max(0, dailyLimit - getActionsToday());
    }

    /**
     * Get how many actions used today.
     */
    public int getActionsToday() {
        return EncryptedPreferences.getInt(KEY_ACTIONS_TODAY, 0);
    }

    /**
     * Get the active daily limit.
     */
    public int getDailyLimit() {
        return isUnlimited() ? PLUS_DAILY_LIMIT : FREE_DAILY_LIMIT;
    }

    /**
     * User-facing allowance label. Paid users still keep the internal safety
     * limit above, but product UI should present the active plan as unlimited.
     */
    public String getAllowanceDisplay() {
        return isUnlimited() ? "Unlimited" : FREE_DAILY_LIMIT + "/day";
    }

    /**
     * Compatibility API: true means the verified paid plan allowance is active.
     * Called from LatinIME when subscription status is checked.
     */
    public void setUnlimited(boolean unlimited) {
        EncryptedPreferences.saveBoolean(KEY_UNLIMITED, unlimited);
        Log.d(TAG, "Paid AI plan enabled: " + unlimited);
    }

    /**
     * Compatibility API for older callers checking paid subscriber state.
     */
    public boolean isUnlimited() {
        return EncryptedPreferences.getBoolean(KEY_UNLIMITED, false);
    }

    // ─── Internal ───

    /**
     * Reset counter if it's a new day (midnight local time).
     */
    private void resetIfNewDay() {
        String today = getTodayString();
        String lastReset = EncryptedPreferences.getString(KEY_LAST_RESET_DATE, "");

        if (!today.equals(lastReset)) {
            EncryptedPreferences.saveInt(KEY_ACTIONS_TODAY, 0);
            EncryptedPreferences.saveString(KEY_LAST_RESET_DATE, today);
            Log.d(TAG, "Daily counter reset for " + today);
        }
    }

    private String getTodayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
