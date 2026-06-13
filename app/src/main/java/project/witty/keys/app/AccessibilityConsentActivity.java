// AccessibilityConsentActivity.java
package project.witty.keys.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import project.witty.keys.R;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.NotchHandler;
import project.witty.keys.app.helpers.ScreenReaderAccessibility;
import project.witty.keys.app.tutorial.TutorialManager;

public class AccessibilityConsentActivity extends BaseActivity {
    private static final String KEY_FROM_TUTORIAL = "from_tutorial";

    private static final String TAG = AccessibilityConsentActivity.class.getSimpleName();
    private boolean isFromTutorial = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotchHandler.configureEdgeToEdge(this);
        setContentView(R.layout.activity_accessibility_keyboard);
        NotchHandler.handleSystemBars(this);
        setupToolbar();
        setToolbarTitle("WittyKeys");
        showLogo(true);

        // 🔍 DIAGNOSTIC: Check intent extras
        Bundle extras = getIntent().getExtras();
        if (DebugConfig.isDebugMode) {
            android.util.Log.d(TAG, "🔍 Intent extras: " + extras);
            if (extras != null) {
                android.util.Log.d(TAG, "🔍 from_tutorial in extras: " + extras.containsKey("from_tutorial"));
                android.util.Log.d(TAG, "🔍 from_tutorial value: " + extras.getBoolean("from_tutorial", false));
            }
        }

        // Restore or read isFromTutorial
        if (savedInstanceState != null) {
            isFromTutorial = savedInstanceState.getBoolean(KEY_FROM_TUTORIAL, false);
            if (DebugConfig.isDebugMode) {
                android.util.Log.d(TAG, "🔄 State RESTORED - From Tutorial: " + isFromTutorial);
            }
        } else {
            isFromTutorial = getIntent().getBooleanExtra("from_tutorial", false);
            if (DebugConfig.isDebugMode) {
                android.util.Log.d(TAG, "🎓 AccessibilityConsent - From Tutorial: " + isFromTutorial);
            }
        }

        showEndText(true, (v) -> {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "⬅️ Skip button — going to HomeActivity");
            }
            Intent intent = new Intent(this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            this.finish();
        });

        Button settingsButton = findViewById(R.id.go_to_settings_button);
        settingsButton.setOnClickListener(v -> showAccessibilityConsentDialog());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FROM_TUTORIAL, isFromTutorial);
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "💾 State SAVED - From Tutorial: " + isFromTutorial);
        }
    }

    private void showAccessibilityConsentDialog() {
        showCustomAlertDialog(
                "Accessibility Service Permission",
                "WittyKeys requires Accessibility Service permission to function properly. Please grant the permission in the settings.",
                "Grant Permission",
                null,
                isFromTutorial ? "Back to Tutorial" : "Cancel",
                v -> {
                    if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "🔓 Opening Accessibility Settings");
                    }
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    this.startActivity(intent);
                },
                null,
                v -> {
                    if (isFromTutorial) {
                        if (DebugConfig.isDebugMode) {
                            android.util.Log.d(TAG, "⬅️ Cancel button — going to HomeActivity");
                        }
                        Intent intent = new Intent(this, HomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        this.finish();
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isFromTutorial) {
            boolean isEnabled = project.witty.keys.keyboard.AccessibilityUtils.isAccessibilityServiceEnabled(
                    this,
                    ScreenReaderAccessibility.class
            );

            if (DebugConfig.isDebugMode) {
                android.util.Log.d(TAG, "🔄 onResume - Accessibility enabled: " + isEnabled);
            }

            if (isEnabled) {
                if (DebugConfig.isDebugMode) {
                    android.util.Log.d(TAG, "✅ Accessibility ENABLED — going to HomeActivity");
                }
                Intent intent = new Intent(this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                this.finish();
            }
        }
    }


    private void showCustomAlertDialog(String title, String message, String positiveButtonText, String negativeButtonText, String cancelButtonText, View.OnClickListener positiveButtonListener, View.OnClickListener negativeButtonListener, View.OnClickListener cancelButtonListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View customLayout = getLayoutInflater().inflate(R.layout.custom_alert_dialog, null);
        builder.setView(customLayout);

        TextView dialogTitle = customLayout.findViewById(R.id.dialog_title_custom);
        TextView dialogMessage = customLayout.findViewById(R.id.dialog_message);
        Button positiveButton = customLayout.findViewById(R.id.dialog_positive_button);
//        Button negativeButton = customLayout.findViewById(R.id.dialog_negative_button);
        Button cancelButton = customLayout.findViewById(R.id.dialog_cancel_button);

        dialogTitle.setText(title);
        dialogMessage.setText(message);
        positiveButton.setText(positiveButtonText);
//        negativeButton.setText(negativeButtonText);
        if (cancelButtonText != null) {
//            cancelButton.setVisibility(View.VISIBLE);
            cancelButton.setText(cancelButtonText);
        }

        AlertDialog dialog = builder.create();

        positiveButton.setOnClickListener(v -> {
            positiveButtonListener.onClick(v);
            dialog.dismiss();
        });

//        negativeButton.setOnClickListener(v -> {
//            negativeButtonListener.onClick(v);
//            dialog.dismiss();
//        });

        if (cancelButtonListener != null) {
            cancelButton.setOnClickListener(v -> {
                cancelButtonListener.onClick(v);
                dialog.dismiss();
            });
        }


        dialog.show();
    }

}
