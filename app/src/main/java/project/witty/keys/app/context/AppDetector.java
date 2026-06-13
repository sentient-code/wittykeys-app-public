package project.witty.keys.app.context;

import android.util.Log;

import project.witty.keys.BuildConfig;
import project.witty.keys.app.helpers.DebugConfig;

/**
 * AppDetector - Detects and categorizes apps by their package name.
 *
 * Used by ContextEngine to determine extraction strategy for different app types.
 */
public class AppDetector {

    private static final String TAG = "AppDetector";

    /**
     * App categories for context-aware extraction
     */
    public enum AppCategory {
        MESSAGING,  // WhatsApp, Telegram, Signal, etc.
        EMAIL,      // Gmail, Outlook, Yahoo Mail, etc.
        DATING,     // Tinder, Bumble, Hinge, etc.
        SOCIAL,     // LinkedIn, Twitter, Instagram, etc.
        OTHER       // Unknown/generic apps
    }

    /**
     * Categorize an app based on its package name
     *
     * @param packageName The app's package name (e.g., "com.whatsapp")
     * @return The detected AppCategory
     */
    public static AppCategory categorize(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return AppCategory.OTHER;
        }

        String pkg = packageName.toLowerCase();

        // [E2E TEST SUPPORT] Treat WittyKeys tutorial as messaging app for lifecycle tests
        if (BuildConfig.DEBUG && pkg.equals("project.witty.keys")) {
            Log.i("WK_E2E", "[APP] AppDetector: DEBUG override — treating " + packageName + " as MESSAGING");
            return AppCategory.MESSAGING;
        }

        // Messaging apps
        if (isMessagingApp(pkg)) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[CE] App categorized as MESSAGING: " + packageName);
            }
            return AppCategory.MESSAGING;
        }

        // Email apps
        if (isEmailApp(pkg)) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[CE] App categorized as EMAIL: " + packageName);
            }
            return AppCategory.EMAIL;
        }

        // Dating apps
        if (isDatingApp(pkg)) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[CE] App categorized as DATING: " + packageName);
            }
            return AppCategory.DATING;
        }

        // Social apps
        if (isSocialApp(pkg)) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[CE] App categorized as SOCIAL: " + packageName);
            }
            return AppCategory.SOCIAL;
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] App categorized as OTHER: " + packageName);
        }
        return AppCategory.OTHER;
    }

    /**
     * Check if package is a messaging app
     */
    private static boolean isMessagingApp(String pkg) {
        return pkg.contains("whatsapp") ||
               pkg.contains("telegram") ||
               pkg.contains("messenger") ||
               pkg.contains("com.facebook.orca") ||
               pkg.contains("signal") ||
               pkg.contains("viber") ||
               pkg.contains("wechat") ||
               pkg.contains("com.tencent.mm") ||
               pkg.contains("sms") ||
               pkg.contains("messages") ||
               pkg.contains("com.google.android.apps.messaging") ||
               pkg.contains("com.google.android.apps.dynamite") || // Google Messages (alternate package)
               pkg.contains("dynamite") ||
               pkg.contains("mms") ||
               pkg.contains("textra") ||
               pkg.contains("chomp") ||
               pkg.contains("handcent") ||
               pkg.contains("imo") ||
               pkg.contains("line.android") ||
               pkg.contains("kakaotalk") ||
               pkg.contains("skype");
    }

    /**
     * Check if package is an email app
     */
    private static boolean isEmailApp(String pkg) {
        return pkg.contains("gmail") ||
               pkg.contains("com.google.android.gm") ||
               pkg.contains("outlook") ||
               pkg.contains("com.microsoft.office.outlook") ||
               pkg.contains("mail") ||
               pkg.contains("yahoo") ||
               pkg.contains("proton") ||
               pkg.contains("protonmail") ||
               pkg.contains("email") ||
               pkg.contains("aquamail") ||
               pkg.contains("bluemail") ||
               pkg.contains("spark") ||
               pkg.contains("edison");
    }

    /**
     * Check if package is a dating app
     */
    private static boolean isDatingApp(String pkg) {
        return pkg.contains("tinder") ||
               pkg.contains("bumble") ||
               pkg.contains("hinge") ||
               pkg.contains("okcupid") ||
               pkg.contains("badoo") ||
               pkg.contains("happn") ||
               pkg.contains("grindr") ||
               pkg.contains("her") ||
               pkg.contains("match") ||
               pkg.contains("plentyoffish") ||
               pkg.contains("pof") ||
               pkg.contains("coffee") ||
               pkg.contains("meetscoffee") ||
               pkg.contains("tantan") ||
               pkg.contains("zoosk");
    }

    /**
     * Check if package is a social app
     */
    private static boolean isSocialApp(String pkg) {
        return pkg.contains("linkedin") ||
               pkg.contains("twitter") ||
               pkg.contains("com.twitter.android") ||
               pkg.contains("instagram") ||
               pkg.contains("facebook") && !pkg.contains("messenger") && !pkg.contains("orca") ||
               pkg.contains("reddit") ||
               pkg.contains("discord") ||
               pkg.contains("slack") ||
               pkg.contains("snapchat") ||
               pkg.contains("pinterest") ||
               pkg.contains("tumblr") ||
               pkg.contains("threads") ||
               pkg.contains("mastodon") ||
               pkg.contains("bluesky");
    }

    /**
     * Check if an app is a contextual app that supports smart replies.
     * Contextual apps are apps where we can extract conversation context
     * and provide smart replies (MESSAGING, EMAIL, DATING, SOCIAL).
     *
     * @param packageName The app's package name
     * @return true if the app is contextual (supports smart replies), false otherwise
     */
    public static boolean isContextualApp(String packageName) {
        AppCategory category = categorize(packageName);
        boolean isContextual = category != AppCategory.OTHER;

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] isContextualApp(" + packageName + ") = " + isContextual +
                  " (category=" + category.name() + ")");
        }

        return isContextual;
    }

    /**
     * Get a human-readable name for the category
     *
     * @param category The AppCategory
     * @return Human-readable category name
     */
    public static String getCategoryName(AppCategory category) {
        switch (category) {
            case MESSAGING:
                return "messaging";
            case EMAIL:
                return "email";
            case DATING:
                return "dating";
            case SOCIAL:
                return "social";
            case OTHER:
            default:
                return "other";
        }
    }

    /**
     * Get a friendly app name from package name
     *
     * @param packageName The app's package name
     * @return Friendly app name or the package name if unknown
     */
    public static String getAppName(String packageName) {
        if (packageName == null) return "Unknown";

        String pkg = packageName.toLowerCase();

        // Common app mappings
        if (pkg.contains("whatsapp")) return "WhatsApp";
        if (pkg.contains("telegram")) return "Telegram";
        if (pkg.contains("com.facebook.orca") || pkg.contains("messenger")) return "Messenger";
        if (pkg.contains("signal")) return "Signal";
        if (pkg.contains("viber")) return "Viber";
        if (pkg.contains("wechat") || pkg.contains("com.tencent.mm")) return "WeChat";
        if (pkg.contains("com.google.android.apps.messaging")) return "Messages";
        if (pkg.contains("com.google.android.apps.dynamite") || pkg.contains("dynamite")) return "Messages";
        if (pkg.contains("gmail") || pkg.contains("com.google.android.gm")) return "Gmail";
        if (pkg.contains("outlook")) return "Outlook";
        if (pkg.contains("tinder")) return "Tinder";
        if (pkg.contains("bumble")) return "Bumble";
        if (pkg.contains("hinge")) return "Hinge";
        if (pkg.contains("linkedin")) return "LinkedIn";
        if (pkg.contains("twitter")) return "Twitter";
        if (pkg.contains("instagram")) return "Instagram";
        if (pkg.contains("facebook")) return "Facebook";
        if (pkg.contains("reddit")) return "Reddit";
        if (pkg.contains("discord")) return "Discord";
        if (pkg.contains("slack")) return "Slack";
        if (pkg.contains("snapchat")) return "Snapchat";

        // Return last part of package name if unknown
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < packageName.length() - 1) {
            String lastPart = packageName.substring(lastDot + 1);
            // Capitalize first letter
            return lastPart.substring(0, 1).toUpperCase() + lastPart.substring(1);
        }

        return packageName;
    }
}
