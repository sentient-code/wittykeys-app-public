package project.witty.keys.app.state;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

import project.witty.keys.app.helpers.NLSPermissionHelper;
import project.witty.keys.app.overlay.OverlayPermissionFlow;
import project.witty.keys.app.overlay.OverlayPermissionHelper;

public final class SetupChecklistStateProvider {
    private static final String WITTY_IME_ID = "project.witty.keys/.latin.LatinIME";

    private SetupChecklistStateProvider() {}

    public static SetupChecklistState current(Context context) {
        return SetupChecklistState.fromFacts(
                isWittyKeysEnabled(context),
                isWittyKeysDefault(context),
                OverlayPermissionHelper.canDrawOverlays(context),
                areAppNotificationsEnabled(context),
                NLSPermissionHelper.isNLSEnabled(context),
                OverlayPermissionFlow.isAccessibilityEnabled(context));
    }

    static boolean isWittyKeysDefault(Context context) {
        String current = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        return WITTY_IME_ID.equals(current);
    }

    static boolean isWittyKeysEnabled(Context context) {
        InputMethodManager imm =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        List<InputMethodInfo> methods = imm.getEnabledInputMethodList();
        for (InputMethodInfo method : methods) {
            if (context.getPackageName().equals(method.getPackageName())) return true;
        }
        return false;
    }

    static boolean areAppNotificationsEnabled(Context context) {
        boolean notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return notificationsEnabled;
        }
        return notificationsEnabled
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

}
