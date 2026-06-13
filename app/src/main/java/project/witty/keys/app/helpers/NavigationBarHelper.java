package project.witty.keys.app.helpers;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;

/**
 * Helper class to get navigation bar height for proper bottom padding.
 *
 * Used by:
 * - EmojiKeyboard: To add bottom padding to EmojiFunctionalView
 * - Other keyboard views: To ensure CTAs are not cropped by gesture navigation
 *
 * DEBUG: Enable DebugConfig.isDebugMode to see navigation bar calculations
 */
public class NavigationBarHelper {

    private static final String TAG = "NavigationBarHelper";

    /**
     * Get the navigation bar height in pixels.
     * Works for both gesture navigation and 3-button navigation.
     *
     * @param context Application or Activity context
     * @return Navigation bar height in pixels, or 0 if not available
     */
    public static int getNavigationBarHeight(Context context) {
        if (context == null) return 0;

        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");

        int height = 0;
        if (resourceId > 0) {
            height = resources.getDimensionPixelSize(resourceId);
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📐 Navigation bar height: " + height + "px");
            Log.d(TAG, "   Gesture navigation: " + isGestureNavigation(context));
        }

        return height;
    }

    /**
     * Get bottom padding using live WindowInsets from an attached View (API 30+).
     * More reliable than the Context overload on gesture-nav devices (e.g. Razr 50 / Android 14)
     * where navigation_bar_height resource is 0 in gesture mode.
     */
    public static int getSafeBottomPadding(View view) {
        if (view == null) return 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets insets = view.getRootWindowInsets();
            if (insets != null) {
                int navBars  = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                int tappable = insets.getInsets(WindowInsets.Type.tappableElement()).bottom;
                int best = Math.max(navBars, tappable);
                if (best > 0) return best;
            }
        }
        return getSafeBottomPadding(view.getContext());
    }

    /**
     * Get bottom padding that accounts for navigation bar + gesture hint area.
     * On devices with gesture navigation, this includes the gesture indicator area.
     *
     * @param context Application or Activity context
     * @return Safe bottom padding in pixels
     */
    public static int getSafeBottomPadding(Context context) {
        if (context == null) return 0;

        int navBarHeight = getNavigationBarHeight(context);
        boolean isGesture = isGestureNavigation(context);

        // On gesture navigation devices, we may need extra padding for the gesture hint
        if (isGesture) {
            // Gesture hint area is typically 16-24dp
            int gestureHintPadding = dpToPx(context, 8);
            navBarHeight = Math.max(navBarHeight, gestureHintPadding);
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📐 Safe bottom padding: " + navBarHeight + "px");
        }

        return navBarHeight;
    }

    /**
     * Check if the device is using gesture navigation (3-button vs gesture).
     *
     * @param context Application context
     * @return true if using gesture navigation
     */
    public static boolean isGestureNavigation(Context context) {
        if (context == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Resources resources = context.getResources();
                int resourceId = resources.getIdentifier(
                        "config_navBarInteractionMode", "integer", "android");
                if (resourceId > 0) {
                    int mode = resources.getInteger(resourceId);
                    // 0 = 3-button, 1 = 2-button, 2 = gesture
                    return mode == 2;
                }
            } catch (Exception e) {
                if (DebugConfig.isDebugMode) {
                    Log.w(TAG, "⚠️ Could not determine navigation mode", e);
                }
            }
        }
        return false;
    }

    /**
     * Apply bottom padding to a view for navigation bar safety.
     * Preserves existing top, left, right padding.
     *
     * @param view View to apply padding to
     * @param context Context for getting navigation bar height
     */
    public static void applyNavigationBarPadding(View view, Context context) {
        if (view == null || context == null) return;

        int bottomPadding = getSafeBottomPadding(context);
        view.setPadding(
                view.getPaddingLeft(),
                view.getPaddingTop(),
                view.getPaddingRight(),
                view.getPaddingBottom() + bottomPadding
        );

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "✅ Applied navigation bar padding: " + bottomPadding + "px to " +
                    view.getClass().getSimpleName());
        }
    }

    /**
     * Set bottom padding to a view (replaces existing bottom padding).
     *
     * @param view View to set padding on
     * @param bottomPadding Bottom padding in pixels
     */
    public static void setBottomPadding(View view, int bottomPadding) {
        if (view == null) return;

        view.setPadding(
                view.getPaddingLeft(),
                view.getPaddingTop(),
                view.getPaddingRight(),
                bottomPadding
        );

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "✅ Set bottom padding: " + bottomPadding + "px to " +
                    view.getClass().getSimpleName());
        }
    }

    /**
     * Convert dp to pixels.
     */
    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}