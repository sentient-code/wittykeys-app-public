package project.witty.keys.app;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import project.witty.keys.R;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.ScreenReaderAccessibility;
import project.witty.keys.keyboard.AccessibilityUtils;

/**
 * PermissionDisclosureDialog - Play Store compliant disclosure dialog for Screen Read permission.
 *
 * Shows a privacy-focused disclosure before opening accessibility settings.
 * Required by Play Store policy for apps using accessibility services.
 *
 * Usage:
 *   PermissionDisclosureDialog.show(context, () -> {
 *       // Called when permission is granted
 *   });
 */
public class PermissionDisclosureDialog extends DialogFragment {

    private static final String TAG = "PermissionDisclosure";
    public static final String FRAGMENT_TAG = "PermissionDisclosureDialog";

    private OnPermissionGrantedListener permissionGrantedListener;
    private boolean waitingForPermission = false;

    /**
     * Callback interface for when permission is granted
     */
    public interface OnPermissionGrantedListener {
        void onPermissionGranted();
    }

    /**
     * Check if accessibility permission is already granted
     */
    public static boolean isAccessibilityEnabled(Context context) {
        return AccessibilityUtils.isAccessibilityServiceEnabled(
                context,
                ScreenReaderAccessibility.class
        );
    }

    /**
     * Show the permission disclosure dialog.
     * If permission is already granted, calls the listener immediately.
     *
     * @param context The context (must be a FragmentActivity or have access to one)
     * @param listener Callback when permission is granted
     */
    public static void show(Context context, OnPermissionGrantedListener listener) {
        // Check if already enabled
        if (isAccessibilityEnabled(context)) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "Accessibility already enabled, calling listener directly");
            }
            if (listener != null) {
                listener.onPermissionGranted();
            }
            return;
        }

        // Need to show dialog
        if (context instanceof FragmentActivity) {
            FragmentActivity activity = (FragmentActivity) context;
            FragmentManager fm = activity.getSupportFragmentManager();

            // Check if dialog is already showing
            if (fm.findFragmentByTag(FRAGMENT_TAG) != null) {
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "Dialog already showing, ignoring");
                }
                return;
            }

            PermissionDisclosureDialog dialog = new PermissionDisclosureDialog();
            dialog.setOnPermissionGrantedListener(listener);
            dialog.show(fm, FRAGMENT_TAG);

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "Showing permission disclosure dialog");
            }
        } else {
            Log.e(TAG, "Context is not a FragmentActivity, cannot show dialog");
            // Fallback: open settings directly
            openAccessibilitySettings(context);
        }
    }

    /**
     * Show the dialog from a keyboard service context.
     * Since InputMethodService doesn't have FragmentManager, this opens settings directly
     * with a toast explanation.
     *
     * @param context The service context
     * @param listener Callback when permission is granted (called on next keyboard open)
     */
    public static void showFromKeyboard(Context context, OnPermissionGrantedListener listener) {
        // Check if already enabled
        if (isAccessibilityEnabled(context)) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "Accessibility already enabled");
            }
            if (listener != null) {
                listener.onPermissionGranted();
            }
            return;
        }

        // Show toast with explanation
        Toast.makeText(
                context,
                "Enable WittyKeys in Accessibility Settings for Smart Replies",
                Toast.LENGTH_LONG
        ).show();

        // Open settings
        openAccessibilitySettings(context);
    }

    public void setOnPermissionGrantedListener(OnPermissionGrantedListener listener) {
        this.permissionGrantedListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.Theme_WittyKeys_Dialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_permission_disclosure, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button cancelButton = view.findViewById(R.id.btn_cancel);
        Button continueButton = view.findViewById(R.id.btn_continue);

        cancelButton.setOnClickListener(v -> {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "User cancelled permission dialog");
            }
            dismiss();
        });

        continueButton.setOnClickListener(v -> {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "User clicked Continue, opening accessibility settings");
            }
            waitingForPermission = true;
            openAccessibilitySettings(requireContext());
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        // Make dialog background transparent for rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Set dialog width
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Check if user just enabled accessibility
        if (waitingForPermission && isAccessibilityEnabled(requireContext())) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "Accessibility enabled! Calling listener and dismissing");
            }

            // Show success toast
            Toast.makeText(requireContext(), "Smart Replies enabled!", Toast.LENGTH_SHORT).show();

            // Notify listener
            if (permissionGrantedListener != null) {
                permissionGrantedListener.onPermissionGranted();
            }

            // Dismiss dialog
            dismiss();
        }
    }

    /**
     * Open the system accessibility settings
     */
    private static void openAccessibilitySettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening accessibility settings", e);
            Toast.makeText(
                    context,
                    "Please enable WittyKeys in Settings > Accessibility",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}
