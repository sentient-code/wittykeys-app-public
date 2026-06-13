package project.witty.keys.app.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Detects OEM battery optimization settings that kill background services.
 * Provides intents to open the correct settings page per OEM.
 *
 * Reference: https://dontkillmyapp.com/
 */
public class BatteryOptimizationHelper {

    private static final String TAG = "WK_BATTERY";

    /**
     * Returns true if the app is being optimized (i.e., NOT on the whitelist).
     * When this returns true, Android may kill our NLS/A11y services aggressively.
     */
    public static boolean isOptimized(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return false;
        return !pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /**
     * Returns an intent to request battery optimization exemption.
     * Uses the standard Android API first, with OEM-specific fallbacks.
     */
    public static Intent getBatterySettingsIntent(Context context) {
        // First try: standard Android battery optimization settings
        Intent standardIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        standardIntent.setData(Uri.parse("package:" + context.getPackageName()));
        if (isIntentResolvable(context, standardIntent)) {
            return standardIntent;
        }

        // Second try: OEM-specific intents
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        Intent oemIntent = null;
        switch (manufacturer) {
            case "xiaomi":
            case "redmi":
            case "poco":
                oemIntent = new Intent();
                oemIntent.setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                break;
            case "samsung":
                oemIntent = new Intent();
                oemIntent.setComponent(new ComponentName("com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"));
                break;
            case "oneplus":
            case "oppo":
            case "realme":
                oemIntent = new Intent();
                oemIntent.setComponent(new ComponentName("com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"));
                break;
            case "huawei":
            case "honor":
                oemIntent = new Intent();
                oemIntent.setComponent(new ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                break;
            case "vivo":
                oemIntent = new Intent();
                oemIntent.setComponent(new ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                break;
            default:
                break;
        }

        if (oemIntent != null && isIntentResolvable(context, oemIntent)) {
            return oemIntent;
        }

        // Last resort: generic battery settings
        Intent genericIntent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
        if (isIntentResolvable(context, genericIntent)) {
            return genericIntent;
        }

        // Absolute fallback: app info page
        Intent appInfoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        appInfoIntent.setData(Uri.parse("package:" + context.getPackageName()));
        return appInfoIntent;
    }

    /**
     * Detects if background services appear to be dying unexpectedly.
     * Call this from TutorialManager or a periodic health check.
     *
     * Heuristic: if NLS was granted but the listener is disconnected AND
     * battery optimization is active, it's likely being killed by the OEM.
     */
    public static boolean isLikelyBeingKilled(Context context) {
        boolean nlsGranted = isNlsEnabled(context);
        boolean nlsConnected = NlsStatusBroadcaster.isNlsConnected();
        boolean optimized = isOptimized(context);

        // NLS was granted but not connected AND we're being optimized → OEM is killing us
        boolean likely = nlsGranted && !nlsConnected && optimized;
        if (likely) {
            Log.w(TAG, "[BATTERY] Likely being killed: NLS granted but disconnected, optimization ON");
        }
        return likely;
    }

    /**
     * Checks if the NLS permission is currently granted.
     */
    public static boolean isNlsEnabled(Context context) {
        String enabledListeners = Settings.Secure.getString(
            context.getContentResolver(), "enabled_notification_listeners");
        if (enabledListeners == null) return false;
        return enabledListeners.contains(context.getPackageName());
    }

    private static boolean isIntentResolvable(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        return intent.resolveActivity(pm) != null;
    }
}
