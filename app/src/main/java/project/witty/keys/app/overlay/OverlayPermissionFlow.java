package project.witty.keys.app.overlay;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;

import project.witty.keys.R;

/**
 * OverlayPermissionFlow — Build 7.1 MVP
 *
 * Guides the user through granting required permissions for the overlay feature.
 * Shows step-by-step dialogs for each missing permission.
 *
 * Required permissions:
 * 1. SYSTEM_ALERT_WINDOW — draw floating bubble
 *
 * Optional permission:
 * 2. Accessibility Service — contact matching for smart replies
 */
public class OverlayPermissionFlow {

    private static final String TAG = "WK_OVERLAY_PERM_FLOW";

    // Accessibility service component name — MUST match AndroidManifest registration
    private static final String ACCESSIBILITY_SERVICE_CLASS =
        "project.witty.keys.app.helpers.ScreenReaderAccessibility";

    private final AppCompatActivity activity;
    private final Runnable onAllGranted;
    private final Runnable onCancelled;

    private AlertDialog currentDialog;
    private int currentStep = 0;

    // Permission steps
    private static final int STEP_OVERLAY = 0;
    private static final int STEP_ACCESSIBILITY = 1;

    public OverlayPermissionFlow(AppCompatActivity activity, Runnable onAllGranted) {
        this(activity, onAllGranted, null);
    }

    public OverlayPermissionFlow(AppCompatActivity activity, Runnable onAllGranted, Runnable onCancelled) {
        this.activity = activity;
        this.onAllGranted = onAllGranted;
        this.onCancelled = onCancelled;
    }

    /**
     * Start the permission flow. Skips already-granted permissions.
     */
    public void start() {
        boolean needOverlay = !OverlayPermissionHelper.canDrawOverlays(activity);
        boolean needAccessibility = !isAccessibilityEnabled(activity);

        if (!needOverlay) {
            if (needAccessibility) {
                currentStep = STEP_ACCESSIBILITY;
                showAccessibilityPermissionDialog(1, 1);
            } else {
                completeRequiredOverlaySetup();
            }
            return;
        }

        int totalSteps = (needOverlay ? 1 : 0) + (needAccessibility ? 1 : 0);

        currentStep = STEP_OVERLAY;
        showOverlayPermissionDialog(1, totalSteps);
    }

    /**
     * Resume the flow after returning from system settings.
     * Call this from the Activity's onResume().
     */
    public void onResume() {
        if (currentDialog == null || !currentDialog.isShowing()) return;

        switch (currentStep) {
            case STEP_OVERLAY:
                if (OverlayPermissionHelper.canDrawOverlays(activity)) {
                    dismissCurrent();
                    if (!isAccessibilityEnabled(activity)) {
                        currentStep = STEP_ACCESSIBILITY;
                        showAccessibilityPermissionDialog(2, 2);
                    } else {
                        completeRequiredOverlaySetup();
                    }
                }
                break;

            case STEP_ACCESSIBILITY:
                if (isAccessibilityEnabled(activity)) {
                    dismissCurrent();
                    completeRequiredOverlaySetup();
                }
                break;
        }
    }

    // ─── Permission Dialogs ───

    private void showOverlayPermissionDialog(int stepNum, int totalSteps) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.overlay_permission_popup, null);

        TextView titleText = dialogView.findViewById(R.id.overlay_perm_title);
        TextView descText = dialogView.findViewById(R.id.overlay_perm_desc);
        Button actionButton = dialogView.findViewById(R.id.overlay_perm_grant_btn);
        TextView skipButton = dialogView.findViewById(R.id.overlay_perm_skip);

        configurePermissionCopy(
            dialogView,
            "Step " + stepNum + " of " + totalSteps,
            "Turn on the floating overlay",
            "Android needs your approval before WittyKeys can show the bubble above other apps.",
            "Shows the WittyKeys bubble only when overlay is enabled.",
            "Lets AI Chat and Quick Replies open without switching apps.",
            "Screen capture still starts only after you tap Capture.",
            "Open Overlay Settings"
        );
        if (actionButton != null) {
            actionButton.setOnClickListener(v -> {
                Intent intent = OverlayPermissionHelper.getOverlayPermissionIntent(activity);
                activity.startActivity(intent);
            });
        }

        if (skipButton != null) skipButton.setOnClickListener(v -> cancel());

        currentDialog = new AlertDialog.Builder(activity, R.style.Theme_AppCompat_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create();
        currentDialog.show();
        setDialogWidth();
    }

    private void showAccessibilityPermissionDialog(int stepNum, int totalSteps) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.overlay_permission_popup, null);

        TextView titleText = dialogView.findViewById(R.id.overlay_perm_title);
        TextView descText = dialogView.findViewById(R.id.overlay_perm_desc);
        Button actionButton = dialogView.findViewById(R.id.overlay_perm_grant_btn);
        TextView skipButton = dialogView.findViewById(R.id.overlay_perm_skip);

        configurePermissionCopy(
            dialogView,
            "Step " + stepNum + " of " + totalSteps,
            "Help replies match this chat",
            "Accessibility lets WittyKeys identify the active app and visible chat context so Quick Replies stay relevant.",
            "Matches suggestions to the right conversation.",
            "Keeps reply ideas aware of the current chat context.",
            "Optional for onboarding; you can skip and enable it later.",
            "Open Accessibility Settings"
        );
        if (actionButton != null) {
            actionButton.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                activity.startActivity(intent);
            });
        }

        if (skipButton != null) skipButton.setOnClickListener(v -> {
            dismissCurrent();
            completeRequiredOverlaySetup();
        });

        currentDialog = new AlertDialog.Builder(activity, R.style.Theme_AppCompat_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create();
        currentDialog.show();
        setDialogWidth();
    }

    private void setDialogWidth() {
        if (currentDialog == null) return;
        Window window = currentDialog.getWindow();
        if (window != null) {
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (screenWidth * 0.9);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.y = dpToPxStatic(activity, 22);
            params.dimAmount = 0.48f;
            window.setAttributes(params);
            window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private static void configurePermissionCopy(View dialogView, String stepLabel, String title,
                                                String description, String detail1,
                                                String detail2, String detail3, String cta) {
        TextView stepText = dialogView.findViewById(R.id.overlay_perm_step_label);
        TextView titleText = dialogView.findViewById(R.id.overlay_perm_title);
        TextView descText = dialogView.findViewById(R.id.overlay_perm_desc);
        TextView detailText1 = dialogView.findViewById(R.id.overlay_perm_detail_1_text);
        TextView detailText2 = dialogView.findViewById(R.id.overlay_perm_detail_2_text);
        TextView detailText3 = dialogView.findViewById(R.id.overlay_perm_detail_3_text);
        Button actionButton = dialogView.findViewById(R.id.overlay_perm_grant_btn);

        if (stepText != null) stepText.setText(stepLabel);
        if (titleText != null) titleText.setText(title);
        if (descText != null) descText.setText(description);
        if (detailText1 != null) detailText1.setText(detail1);
        if (detailText2 != null) detailText2.setText(detail2);
        if (detailText3 != null) detailText3.setText(detail3);
        if (actionButton != null) {
            actionButton.setText(cta);
            actionButton.setBackgroundResource(R.drawable.wk_button_gradient);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                actionButton.setBackgroundTintList(null);
            }
        }
    }

    // ─── Flow Control ───

    private void completeRequiredOverlaySetup() {
        Log.d(TAG, "Required overlay permission granted — starting overlay");
        if (onAllGranted != null) {
            onAllGranted.run();
        }
    }

    private void cancel() {
        Log.d(TAG, "Permission flow cancelled by user");
        dismissCurrent();
        if (onCancelled != null) {
            onCancelled.run();
        }
    }

    private void dismissCurrent() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }
    }

    // ─── Popup Card Path (from overlay service) ───

    /**
     * Show the permission flow as a popup card within the overlay.
     * Used when triggered from within the overlay service itself.
     */
    public static void showAsPopup(WittyKeysOverlayService overlayService, Runnable onGranted, Runnable onSkipped) {
        Context context = overlayService;
        boolean needOverlay = !OverlayPermissionHelper.canDrawOverlays(context);
        boolean needAccessibility = !isAccessibilityEnabled(context);

        if (!needOverlay && !needAccessibility) {
            if (onGranted != null) onGranted.run();
            return;
        }

        View popupView = LayoutInflater.from(context)
            .inflate(R.layout.overlay_permission_popup, null);

        Button grantBtn = popupView.findViewById(R.id.overlay_perm_grant_btn);
        TextView skipBtn = popupView.findViewById(R.id.overlay_perm_skip);

        int totalSteps = (needOverlay ? 1 : 0) + (needAccessibility ? 1 : 0);
        if (needOverlay) {
            configurePermissionCopy(
                popupView,
                "Step 1 of " + totalSteps,
                "Turn on the floating overlay",
                "Android needs your approval before WittyKeys can show the bubble above other apps.",
                "Shows the WittyKeys bubble only when overlay is enabled.",
                "Lets AI Chat and Quick Replies open without switching apps.",
                "Screen capture still starts only after you tap Capture.",
                "Open Overlay Settings"
            );
        } else {
            configurePermissionCopy(
                popupView,
                "Optional step",
                "Help replies match this chat",
                "Accessibility lets WittyKeys identify the active app and visible chat context so Quick Replies stay relevant.",
                "Matches suggestions to the right conversation.",
                "Keeps reply ideas aware of the current chat context.",
                "Optional for overlay; you can skip and enable it later.",
                "Open Accessibility Settings"
            );
        }

        if (grantBtn != null) {
            grantBtn.setOnClickListener(v -> {
                Intent intent;
                if (needOverlay) {
                    intent = OverlayPermissionHelper.getOverlayPermissionIntent(context);
                } else {
                    intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                overlayService.hidePopup();
            });
        }

        if (skipBtn != null) {
            skipBtn.setOnClickListener(v -> {
                overlayService.hidePopup();
                if (needOverlay) {
                    if (onSkipped != null) onSkipped.run();
                } else if (onGranted != null) {
                    onGranted.run();
                }
            });
        }

        overlayService.showPopup(popupView, "perm", 340, 400);
    }

    private static void addPermissionStep(Context context, LinearLayout container,
                                           int stepNum, String label, boolean isDone) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPxStatic(context, 4), dpToPxStatic(context, 6),
            dpToPxStatic(context, 4), dpToPxStatic(context, 6));

        TextView indicator = new TextView(context);
        indicator.setTextSize(14);
        indicator.setGravity(Gravity.CENTER);
        indicator.setMinWidth(dpToPxStatic(context, 24));
        indicator.setMinHeight(dpToPxStatic(context, 24));

        if (isDone) {
            indicator.setText("✓");
            indicator.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_green));
        } else {
            indicator.setText(String.valueOf(stepNum));
            indicator.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_accent));
            indicator.setTypeface(null, Typeface.BOLD);
        }

        row.addView(indicator);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(ContextCompat.getColor(context,
            isDone ? R.color.wk_overlay_dark_text3 : R.color.wk_overlay_dark_text));
        labelView.setPadding(dpToPxStatic(context, 8), 0, 0, 0);
        row.addView(labelView);

        container.addView(row);
    }

    private static int dpToPxStatic(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    // ─── Accessibility Check ───

    /**
     * Check if ScreenReaderAccessibility service is enabled.
     */
    public static boolean isAccessibilityEnabled(Context context) {
        try {
            AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;

            List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

            String pkg = context.getPackageName();

            for (AccessibilityServiceInfo info : enabledServices) {
                String serviceId = info.getId();
                if (serviceId == null) continue;
                // Android may return either format:
                //   "project.witty.keys/.app.helpers.ScreenReaderAccessibility" (shorthand)
                //   "project.witty.keys/project.witty.keys.app.helpers.ScreenReaderAccessibility" (full)
                if (serviceId.startsWith(pkg + "/") && serviceId.endsWith("ScreenReaderAccessibility")) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking accessibility: " + e.getMessage());
        }
        return false;
    }

    /**
     * Check if all overlay permissions are granted.
     */
    public static boolean hasAllPermissions(Context context) {
        return hasRequiredPermissions(context);
    }

    /**
     * Check if required overlay permissions are granted.
     * Accessibility is optional for the MVP overlay path.
     */
    public static boolean hasRequiredPermissions(Context context) {
        return OverlayPermissionHelper.canDrawOverlays(context);
    }
}
