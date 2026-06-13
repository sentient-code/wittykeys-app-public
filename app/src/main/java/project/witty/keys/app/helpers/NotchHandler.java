package project.witty.keys.app.helpers;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class NotchHandler {

    public static void configureEdgeToEdge(Activity activity) {
        if (activity == null) return;

        Window window = activity.getWindow();
        if (window == null) return;

        // Remove title bar if needed
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Configure notch display mode
        configureNotchDisplayMode(window);

        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
    }

    public static void handleSystemBars(Activity activity) {
        if (activity == null) return;

        Window window = activity.getWindow();
        if (window == null) return;

        final View decorView = window.getDecorView();
        if (decorView == null) return;

        // Handle system bars visibility
        handleSystemBarsVisibility(decorView);

        // Handle content insets
        handleContentInsets(activity);
    }

    private static void configureNotchDisplayMode(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            window.setAttributes(params);
        }
    }

    private static void handleSystemBarsVisibility(View decorView) {
        // For Android R (API 30) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.post(() -> {
                WindowInsetsController controller = decorView.getWindowInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.systemBars());
                }
            });
        }
        // For older versions
        else {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private static void handleContentInsets(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;

        ViewGroup contentGroup = (ViewGroup) content;
        View root = contentGroup.getChildCount() > 0 ? contentGroup.getChildAt(0) : content;
        final int initialLeft = root.getPaddingLeft();
        final int initialTop = root.getPaddingTop();
        final int initialRight = root.getPaddingRight();
        final int initialBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int left = Math.max(systemBars.left, cutout.left);
            int top = Math.max(systemBars.top, cutout.top);
            int right = Math.max(systemBars.right, cutout.right);

            v.setPadding(
                    initialLeft + left,
                    initialTop + top,
                    initialRight + right,
                    initialBottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
