// SetUpKeyboardActivity.java
package project.witty.keys.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.analytics.FirebaseAnalytics;

import project.witty.keys.R;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.ActivationManager;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.helpers.NotchHandler;

public class SetUpKeyboardActivity extends BaseActivity {
    private static final String TAG = SetUpKeyboardActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_keyboard);
        setupToolbar();
        setToolbarTitle("WittyKeys");
        showLogo(true);

        // --- Customize System Bar Colors ---
        Window window = getWindow();
        if (window != null) {
            // Initialize the controller for system bar appearance
            WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());

            // *** 1. Navigation Bar ***
            // Set Navigation bar background color (Currently intro_bg_1, change to black if needed)
            int navBarColor = R.color.intro_bg_1; // Set desired Nav Bar Color here (e.g., R.color.black)
            window.setNavigationBarColor(ContextCompat.getColor(this, navBarColor));
            // Set Navigation bar icons light (white)
            insetsController.setAppearanceLightNavigationBars(true); // false = light icons

            // *** 2. Status Bar ***
            // Set Status bar background color to Black
            int statusBarColor = R.color.intro_bg_1; // Use your black color resource
            window.setStatusBarColor(ContextCompat.getColor(this, statusBarColor));
            // Set Status bar icons light (white) for contrast with black background
            insetsController.setAppearanceLightStatusBars(false); // false = light icons
        }
        // --- End Customize System Bar Colors ---

        Button settingsButton = findViewById(R.id.go_to_settings_button);
        settingsButton.setOnClickListener(v -> {
            boolean enabled = false;
            try {
                enabled = isInputMethodOfThisImeEnabled();
            } catch (Exception e) {
                Log.e(TAG, "Exception in check if input method is enabled", e);
            }
            if (!enabled) {
                final Context context = this;
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.setup_message);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        dialog.dismiss();
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
                builder.setCancelable(false);
                builder.create().show();
            }
        });
        EncryptedPreferences.initialize(this);
    }


    @Override
    protected void onStart() {
        super.onStart();

        // DEBUG: Log entry point
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "=== KEYBOARD_ENABLE_DEBUG: SetUpKeyboardActivity.onStart() ===");
            Log.d(TAG, "   Timestamp: " + System.currentTimeMillis());
        }

        boolean enabled = false;
        try {
            enabled = isInputMethodOfThisImeEnabled();
        } catch (Exception e) {
            Log.e(TAG, "Exception in check if input method is enabled", e);
        }

        // DEBUG: Log IME check result
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "   IME Enabled Check Result: " + enabled);
        }

        if (enabled) {
            FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
            User user = EncryptedPreferences.getUserLoggedInInfo();

            // DEBUG: Log user state and Firebase instance
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "   User Logged In: " + (user != null));
                Log.d(TAG, "   user_present: " + (user != null && user.getId() != null && !user.getId().isEmpty()));
                Log.d(TAG, "   Firebase Analytics: " + (mFirebaseAnalytics != null));
            }

            // Track keyboard enabled via ActivationManager (works for anonymous users too)
            ActivationManager activationManager = new ActivationManager(this);
            String trackingId = activationManager.getTrackingId();

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "   tracking_id_present: " + (trackingId != null && !trackingId.isEmpty()));
                Log.d(TAG, "   FIRING keyboard_enabled event via ActivationManager");
            }

            // Track milestone (handles both logged-in and anonymous users)
            activationManager.trackKeyboardEnabled(user != null ? user.getId() : null);

            // Also fire legacy event for logged-in users (BUG FIX: was triggerKeyboardDisabledEvent)
            if (user != null) {
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "   FIRING legacy keyboard_enabled event: user_present=true");
                }
                EventHelpers.triggerKeyboardEnabledEvent(user.getId(), mFirebaseAnalytics);
            } else {
                if (DebugConfig.isDebugMode) {
                    Log.w(TAG, "   WARNING: Legacy keyboard_enabled NOT fired - user is null (anonymous)");
                    Log.d(TAG, "   Note: ActivationManager tracking used instead with device ID");
                }
            }

            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
            this.finish();
        }
    }

    /**
     * Check if this IME is enabled in the system.
     *
     * @return whether this IME is enabled in the system.
     */
    private boolean isInputMethodOfThisImeEnabled() {
        final InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        final String imePackageName = getPackageName();
        for (final InputMethodInfo imi : imm.getEnabledInputMethodList()) {
            if (imi.getPackageName().equals(imePackageName)) {
                return true;
            }
        }
        return false;
    }

}
