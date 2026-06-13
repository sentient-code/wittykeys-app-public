package project.witty.keys.app.helpers;

import android.content.Context;
import android.util.Log;
import project.witty.keys.BuildConfig;

/**
 * DebugConfig - Global debug mode toggle for WittyKeys.
 *
 * Enables detailed logging throughout the app for debugging and development.
 * Can be toggled ON/OFF from Settings -> Debug Settings.
 *
 * Usage:
 * if (DebugConfig.isDebugMode) {
 *     Log.d(TAG, "Debug message here");
 * }
 */
public class DebugConfig {
    private static final String TAG = "DebugConfig";
    private static final String PREF_KEY_DEBUG_MODE = "debug_mode_enabled";

    /**
     * Global debug mode flag.
     * When true, verbose logging is enabled throughout the app.
     */
    public static boolean isDebugMode = false;

    // ========== BUILD 6.3 FEATURE FLAGS ==========
    /** Enable SmartAssistantBar (replaces UtilityRow + SuggestionRow) */
    public static boolean USE_SMART_ASSISTANT_BAR = true;

    /** Enable Claude API for reply generation */
    public static boolean USE_CLAUDE_API = true;

    /** Enable proactive context reading when keyboard opens */
    public static boolean USE_PROACTIVE_CONTEXT = true;

    /**
     * Initialize debug config. Should be called in Application.onCreate()
     */
    public static void init(Context context) {
        EncryptedPreferences.initialize(context);
        // Auto-enable in debug builds, check preference for release builds
        isDebugMode = EncryptedPreferences.getBoolean(PREF_KEY_DEBUG_MODE, BuildConfig.DEBUG);

        if (isDebugMode) {
            Log.d(TAG, "🐛 Debug mode is ENABLED");
        }
    }

    /**
     * Enable debug mode - turns on verbose logging
     */
    public static void enableDebugMode(Context context) {
        isDebugMode = true;
        EncryptedPreferences.saveBoolean(PREF_KEY_DEBUG_MODE, true);
        Log.d(TAG, "✅ Debug mode ENABLED");
    }

    /**
     * Disable debug mode - turns off verbose logging
     */
    public static void disableDebugMode(Context context) {
        isDebugMode = false;
        EncryptedPreferences.saveBoolean(PREF_KEY_DEBUG_MODE, false);
        Log.d(TAG, "❌ Debug mode DISABLED");
    }

    /**
     * Toggle debug mode on/off
     */
    public static void toggleDebugMode(Context context) {
        if (isDebugMode) {
            disableDebugMode(context);
        } else {
            enableDebugMode(context);
        }
    }

    /**
     * Get debug mode status as string
     */
    public static String getStatusString() {
        return isDebugMode ? "ON" : "OFF";
    }
}