package project.witty.keys.keyboard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

public class AccessibilityUtils {

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityServiceClass) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        Log.d("AccessibilityCheck", "Enabled Services: " + enabledServices);

        for (AccessibilityServiceInfo enabledService : enabledServices) {
            Log.d("AccessibilityCheck", "Service: " + enabledService.getResolveInfo().serviceInfo.name);
            if (enabledService.getResolveInfo().serviceInfo.name.equals(accessibilityServiceClass.getName())) {
                return true;
            }
        }

        // Fallback for MIUI devices
        String settingValue = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        Log.d("AccessibilityCheck", "Settings.Secure Value: " + settingValue);

        if (settingValue != null) {
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(settingValue);
            while (splitter.hasNext()) {
                String service = splitter.next();
                if (service.contains(accessibilityServiceClass.getSimpleName())) {
                    return true;
                }
            }
        }

        return false;
    }
}