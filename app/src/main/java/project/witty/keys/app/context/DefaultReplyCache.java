package project.witty.keys.app.context;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

import android.util.Log;

/**
 * Pre-computed reply cache for first 5 keyboard opens WITHOUT accessibility enabled.
 * Provides good-quality default replies based on the detected app type alone
 * (no screen reading needed). After 5 uses, returns null to signal the caller
 * to show "Enable AI reading" prompt.
 */
public class DefaultReplyCache {

    private static final String TAG = "DefaultReplyCache";
    private static final String PREFS_NAME = "wk_onboarding";
    private static final String KEY_OPEN_COUNT = "wk_onboarding_keyboard_open_count";
    private static final int MAX_DEFAULT_USES = 5;

    // App category → default replies
    private static final Map<String, String[]> APP_DEFAULTS = new HashMap<>();
    static {
        // Dating apps
        APP_DEFAULTS.put("com.tinder", new String[]{
            "Hey! Love your profile, what's your favorite weekend adventure? \uD83D\uDE0A",
            "That's such a cool hobby! Tell me more about it",
            "I'd love to grab coffee sometime, what do you think? \u2615"
        });
        APP_DEFAULTS.put("com.bumble.app", new String[]{
            "Hey! Your photos are amazing, where was that taken?",
            "That sounds like a great time! I'm totally into that too",
            "Would love to continue this over drinks sometime \uD83C\uDF77"
        });

        // Messaging (WhatsApp, Telegram)
        APP_DEFAULTS.put("com.whatsapp", new String[]{
            "Sounds good! Let me know when works \uD83D\uDC4D",
            "Haha that's hilarious \uD83D\uDE02",
            "Sure thing, I'll get back to you on that!"
        });
        APP_DEFAULTS.put("org.telegram.messenger", new String[]{
            "Got it, thanks for letting me know!",
            "That's awesome! Happy for you \uD83D\uDE4C",
            "Let me think about it and I'll let you know"
        });

        // Social media
        APP_DEFAULTS.put("com.instagram.android", new String[]{
            "This looks amazing! \uD83D\uDD25",
            "Love this! Where is this?",
            "So cool! Thanks for sharing \uD83D\uDE0D"
        });

        // Email
        APP_DEFAULTS.put("com.google.android.gm", new String[]{
            "Thank you for your email. I'll review and get back to you shortly.",
            "Sounds good, I'm available for a call this week.",
            "Noted. I'll send over the details by end of day."
        });

        // Professional (LinkedIn, Slack)
        APP_DEFAULTS.put("com.linkedin.android", new String[]{
            "Thanks for connecting! Would love to learn more about your work.",
            "That's a great insight, thanks for sharing.",
            "I'd be happy to discuss further. Feel free to reach out!"
        });
    }

    // Generic fallback for unknown apps
    private static final String[] GENERIC_DEFAULTS = new String[]{
        "Sounds great! \uD83D\uDC4D",
        "Thanks for letting me know!",
        "Sure, I'll get back to you on that"
    };

    /** Returns default replies or null if cache exhausted */
    public static String[] getDefaultReplies(Context context, String appPackage) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int openCount = prefs.getInt(KEY_OPEN_COUNT, 0);

        if (openCount >= MAX_DEFAULT_USES) {
            Log.d(TAG, "[CACHE] Exhausted — returning null (openCount=" + openCount + ")");
            return null;
        }

        // Increment open count
        prefs.edit().putInt(KEY_OPEN_COUNT, openCount + 1).apply();

        String[] replies = APP_DEFAULTS.getOrDefault(appPackage, GENERIC_DEFAULTS);
        Log.d(TAG, "[CACHE] Returning defaults for " + appPackage + " (use " + (openCount + 1) + "/" + MAX_DEFAULT_USES + ")");
        return replies;
    }

    /** Check if defaults are still available */
    public static boolean hasDefaults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_OPEN_COUNT, 0) < MAX_DEFAULT_USES;
    }
}
