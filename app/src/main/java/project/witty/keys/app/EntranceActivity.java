
//EntranceActivity.java
package project.witty.keys.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.FirebaseFirestore;

import project.witty.keys.R;
import project.witty.keys.app.entities.Subscription;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.tutorial.TutorialManager;

public class EntranceActivity extends BaseActivity {
    private FirebaseFirestore db;
    private static final String TAG = "EntranceActivity";
    private static final String NOT_FIRST_TIME_STRING = "notFirstTime";

    // Member variable to hold the animator so we can cancel it
    private AnimatorSet iconAnimatorSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entrance);
        setupToolbar();
        setToolbarTitle("WittyKeys");
        showLogo(true);
        FirebaseApp.initializeApp(this);

        FirebaseAnalytics mFirebaseAnalytics =  FirebaseAnalytics.getInstance(this);
        db = FirebaseFirestore.getInstance();
        EncryptedPreferences.initialize(this);

        DebugConfig.init(this);
        // Find the ImageView
        ImageView checkIcon = findViewById(R.id.check_icon);

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

        // --- Start Icon Animation ---
        if (checkIcon != null) {
            startIconAnimation(checkIcon);
        } else {
            Log.e(TAG, "Check icon ImageView not found!");
        }
        // --- End Icon Animation ---

        boolean notFirstTime = EncryptedPreferences.getBoolean(NOT_FIRST_TIME_STRING, false);
        Log.d(TAG, "notFirstTime: " + notFirstTime);

        if (notFirstTime) {
            User user = EncryptedPreferences.getUserLoggedInInfo();
            Log.d(TAG, "User state: user_present=" + (user != null));
            if (user != null) {
                runOnUiThread(() -> {
                    Subscription.fetchSubscriptionFromFirestore(user.getId(), db);
                    User.fetchUserFromFirestore(user.getId(), db);
                });
                EventHelpers.triggerAppLaunchEvent(user.getId(), mFirebaseAnalytics);
            }

            // ALWAYS check onboarding + permission state via TutorialManager
            TutorialManager tutorialManager = TutorialManager.getInstance(this);
            Intent onboardingIntent = tutorialManager.getResumeOnboardingIntent(this);
            if (onboardingIntent != null) {
                startActivity(onboardingIntent);
                finish();
            } else {
                navigateToHome();
            }

        } else {
            final long DELAY_MILLIS = 2000;
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent = TutorialManager.getInstance(EntranceActivity.this).getFirstLaunchIntent(EntranceActivity.this);
                    if (intent == null) {
                        navigateToHome();
                        return;
                    }
                    startActivity(intent);
                    finish();
                }
            }, DELAY_MILLIS);
        }
    }

    // --- Method to create and start the icon animation ---
    private void startIconAnimation(View targetView) {
        // Clear any previous animator
        if (iconAnimatorSet != null) {
            iconAnimatorSet.cancel();
        }

        // Scale factor (e.g., 1.0f to 1.15f)
        float scaleTo = 1.05f;
        // Duration for one way (grow or shrink) in milliseconds
        long duration = 500; // Makes full cycle 1000ms (1 second)

        // Animate scaleX
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(targetView, View.SCALE_X, 1.0f, scaleTo);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setRepeatMode(ValueAnimator.REVERSE); // Grow then shrink back
        scaleX.setDuration(duration);

        // Animate scaleY
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(targetView, View.SCALE_Y, 1.0f, scaleTo);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE); // Grow then shrink back
        scaleY.setDuration(duration);

        // Apply a bouncy interpolator
        OvershootInterpolator overshootInterpolator = new OvershootInterpolator(1.2f); // Adjust tension (e.g., 1.0f, 2.0f)
        scaleX.setInterpolator(overshootInterpolator);
        scaleY.setInterpolator(overshootInterpolator);

        // Play X and Y scaling together
        iconAnimatorSet = new AnimatorSet();
        iconAnimatorSet.playTogether(scaleX, scaleY);
        iconAnimatorSet.start();
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
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
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // --- Stop animation when Activity is destroyed ---
        if (iconAnimatorSet != null) {
            iconAnimatorSet.cancel();
            iconAnimatorSet = null; // Release reference
            Log.d(TAG, "Icon animation cancelled.");
        }
    }
}
