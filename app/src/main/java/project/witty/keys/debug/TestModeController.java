package project.witty.keys.debug;

import android.util.Log;

/**
 * Single source of truth: test harness mode vs production mode.
 *
 * DEFAULT: OFF (production) — real ClaudeApi, real typing detection, real predictions.
 * ON: Only when DebugSABController activates via ADB broadcast for golden screenshots.
 *
 * Usage: Replace all USE_NEW_SAB_MANAGER checks with TestModeController.isTestMode()
 */
public class TestModeController {
    private static final String TAG = "TestMode";
    private static boolean sTestModeActive = false;

    public static void enterTestMode() {
        sTestModeActive = true;
        Log.d(TAG, "[TestMode] ENTERED test mode — MockData + SmartAssistantBarManager active");
    }

    public static void exitTestMode() {
        sTestModeActive = false;
        Log.d(TAG, "[TestMode] EXITED test mode — production flow restored");
    }

    public static boolean isTestMode() {
        return sTestModeActive;
    }

    public static boolean shouldUseMockData() {
        return sTestModeActive;
    }
}
