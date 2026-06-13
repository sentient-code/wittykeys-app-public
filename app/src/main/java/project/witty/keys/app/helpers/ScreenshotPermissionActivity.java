// In ScreenshotPermissionActivity.java

package project.witty.keys.app.helpers;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import project.witty.keys.app.overlay.WittyKeysOverlayService;
import project.witty.keys.keyboard.KeyboardSwitcher;

public class ScreenshotPermissionActivity extends Activity implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "ScreenshotPermissionAct";
    private static final int SCREEN_CAPTURE_REQUEST_CODE = 1002;
    private static final String KEY_IS_AWAITING_RESULT = "is_awaiting_result";

    private MediaProjectionManager mediaProjectionManager;
    private boolean isAwaitingResult = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        getApplication().registerActivityLifecycleCallbacks(this);

        if (savedInstanceState != null) {
            isAwaitingResult = savedInstanceState.getBoolean(KEY_IS_AWAITING_RESULT, false);
        }

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (!isAwaitingResult) {
            if (mediaProjectionManager != null) {
                try {
                    Log.d(TAG, "Requesting screen capture permission.");
                    startActivityForResult(
                            mediaProjectionManager.createScreenCaptureIntent(),
                            SCREEN_CAPTURE_REQUEST_CODE
                    );
                    isAwaitingResult = true;
                } catch (Exception e) {
                    Log.e(TAG, "Error starting media projection intent", e);
                    Toast.makeText(this, "Could not start screen capture.", Toast.LENGTH_LONG).show();
                    finishAndShowKeyboard(); // Cleanup and show keyboard on failure
                }
            } else {
                Log.e(TAG, "MediaProjectionManager is null.");
                Toast.makeText(this, "Screen capture not supported on this device.", Toast.LENGTH_LONG).show();
                finishAndShowKeyboard(); // Cleanup and show keyboard on failure
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_AWAITING_RESULT, isAwaitingResult);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        isAwaitingResult = false;

        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // Permission Granted: Start the service.
                Intent originalIntent = getIntent();
                String packageName = originalIntent.getStringExtra("SCREEN_TARGET_PACKAGE_NAME");
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_DATA, data);
                serviceIntent.putExtra("SCREEN_TARGET_PACKAGE_NAME", packageName);
                // Pass overlay flag through
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_FROM_OVERLAY,
                    originalIntent.getBooleanExtra(ScreenCaptureService.EXTRA_FROM_OVERLAY, false));
                ContextCompat.startForegroundService(this, serviceIntent);
            } else {
                // Permission Denied
                Log.w(TAG, "Screen capture permission DENIED.");
                Toast.makeText(this, "Permission not granted.", Toast.LENGTH_SHORT).show();
                if (getIntent().getBooleanExtra(ScreenCaptureService.EXTRA_FROM_OVERLAY, false)) {
                    // Overlay — re-show the bubble
                    WittyKeysOverlayService overlay = WittyKeysOverlayService.getInstance();
                    if (overlay != null) {
                        overlay.showBubble();
                    }
                } else {
                    // Keyboard — permission denied, stay on whatever screen the user was on
                    Log.d(TAG, "Screenshot permission denied — leaving keyboard state unchanged");
                }
            }
        }
        finish(); // Always finish this transparent activity.
    }

    /**
     * Finishes this activity and ensures the keyboard is shown.
     * This is our centralized cleanup method.
     */
    private void finishAndShowKeyboard() {
        Log.d(TAG, "finishAndShowKeyboard: Cleaning up and showing keyboard.");
        finish();
    }

    // --- ACTIVITY LIFECYCLE CALLBACKS ---

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

    @Override
    public void onActivityStarted(@NonNull Activity activity) {}

    @Override
    public void onActivityResumed(@NonNull Activity activity) {}

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // We only care about this activity being paused.
        if (activity == this) {
            Log.d(TAG, "onActivityPaused: ScreenshotPermissionActivity is being paused.");
            // If we are paused and NOT waiting for a result (meaning onActivityResult has
            // been called or was never needed), it's time to finish.
            if (!isAwaitingResult) {
                finish();
            }
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        // Unregister the callback when this activity is destroyed to prevent memory leaks.
        if (activity == this) {
            Log.d(TAG, "onActivityDestroyed: Unregistering lifecycle callbacks.");
            getApplication().unregisterActivityLifecycleCallbacks(this);
        }
    }
}