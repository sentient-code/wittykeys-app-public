package project.witty.keys.app.overlay;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Helper for checking and requesting SYSTEM_ALERT_WINDOW permission.
 */
public class OverlayPermissionHelper {

    private static final String TAG = "WK_OVERLAY_PERM";

    /**
     * Check if the app has permission to draw overlays.
     */
    public static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    /**
     * Open system settings to grant overlay permission.
     * Returns an Intent — caller should startActivityForResult with it.
     */
    public static Intent getOverlayPermissionIntent(Context context) {
        return new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.getPackageName())
        );
    }
}
