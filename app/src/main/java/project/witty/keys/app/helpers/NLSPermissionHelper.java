package project.witty.keys.app.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * NLSPermissionHelper — NEW file (Sprint 1, Build 7.0)
 *
 * Checks and manages NotificationListenerService permission.
 *
 * CONTRACT FOR DOWNSTREAM PHASES:
 * - P6 (Onboarding): Uses isNLSEnabled() and openNLSSettings() in tutorial flow
 */
public class NLSPermissionHelper {

    /**
     * Check if our NotificationListenerService is enabled.
     */
    public static boolean isNLSEnabled(Context context) {
        String packageName = context.getPackageName();
        String flat = Settings.Secure.getString(context.getContentResolver(),
            "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && cn.getPackageName().equals(packageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Open the notification listener settings page.
     */
    public static void openNLSSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Get the component name of our NLS for registration checks.
     */
    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context.getPackageName(),
            "project.witty.keys.app.helpers.WittyKeysNotificationListenerService");
    }
}
