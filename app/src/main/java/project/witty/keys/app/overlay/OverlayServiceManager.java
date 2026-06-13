package project.witty.keys.app.overlay;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import project.witty.keys.app.helpers.EncryptedPreferences;

/**
 * Manages starting/stopping the WittyKeysOverlayService.
 */
public class OverlayServiceManager {

    private static final String TAG = "WK_OVERLAY_MGR";
    private static final String KEY_OVERLAY_ENABLED = "overlay_enabled";

    /**
     * Start the overlay service if permission is granted and user has enabled it.
     */
    public static void startIfEnabled(Context context) {
        if (!isOverlayEnabled()) {
            Log.d(TAG, "Overlay disabled by user");
            return;
        }
        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted");
            return;
        }
        if (!OverlayPermissionFlow.isAccessibilityEnabled(context)) {
            Log.w(TAG, "Accessibility service not enabled — overlay may have limited functionality");
            // Still start — overlay works for screenshots without accessibility
        }
        startService(context);
    }

    /**
     * Force start the overlay service.
     */
    public static void startService(Context context) {
        Intent intent = new Intent(context, WittyKeysOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        Log.d(TAG, "Overlay service started");
    }

    /**
     * Stop the overlay service.
     */
    public static void stopService(Context context) {
        Intent intent = new Intent(context, WittyKeysOverlayService.class);
        context.stopService(intent);
        Log.d(TAG, "Overlay service stopped");
    }

    /**
     * Check if user has enabled the overlay feature.
     */
    public static boolean isOverlayEnabled() {
        return EncryptedPreferences.getBoolean(KEY_OVERLAY_ENABLED, false);
    }

    /**
     * Set overlay enabled state.
     */
    public static void setOverlayEnabled(boolean enabled) {
        EncryptedPreferences.saveBoolean(KEY_OVERLAY_ENABLED, enabled);
    }

    /**
     * Check if the overlay service is currently running.
     */
    public static boolean isOverlayRunning(Context context) {
        return WittyKeysOverlayService.getInstance() != null;
    }

    /**
     * Show the overlay if not already running.
     */
    public static void showOverlay(Context context) {
        if (!isOverlayRunning(context)) {
            startService(context);
        }
    }

    /**
     * Hide the overlay if running.
     */
    public static void hideOverlay(Context context) {
        if (isOverlayRunning(context)) {
            stopService(context);
        }
    }

    /**
     * Toggle overlay visibility.
     */
    public static void toggleOverlay(Context context) {
        if (isOverlayRunning(context)) {
            hideOverlay(context);
        } else {
            showOverlay(context);
        }
    }
}
