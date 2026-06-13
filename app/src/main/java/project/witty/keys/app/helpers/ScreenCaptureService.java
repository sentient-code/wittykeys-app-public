package project.witty.keys.app.helpers;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import project.witty.keys.R;
import project.witty.keys.app.EntranceActivity;
import project.witty.keys.app.context.ConversationMatcher;
import project.witty.keys.app.database.ChatSession;
import project.witty.keys.app.database.WittyKeysDatabase;
import project.witty.keys.app.overlay.WittyKeysOverlayService;
import project.witty.keys.keyboard.KeyboardSwitcher;

/**
 * ScreenCaptureService — Build 7.0 Phase 3
 *
 * Captures the user's screen via MediaProjection, saves to internal storage,
 * and delegates analysis to ScreenshotAnalyzer (Claude Haiku Vision).
 *
 * Refactored from Build 6.3:
 * - Removed: CommunicationService binding, ScreenContextParser, accessibility tree,
 *   OpenAI/GPT-4o API calls, token tracking
 * - Added: File-based capture, ScreenshotAnalyzer delegation, NLS context,
 *   JSON result delivery via KeyboardSwitcher
 */
public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_FROM_OVERLAY = "from_overlay";

    private static final String TAG = "ScreenCaptureSvc";
    private static final int NOTIFICATION_ID = 124;
    private static final String NOTIFICATION_CHANNEL_ID = "ScreenCaptureChannelJava";

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread handlerThread;
    private Handler handler;
    private Handler mainHandler;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    private volatile boolean imageProcessedThisSession = false;
    private MediaProjection.Callback mediaProjectionCallback;
    private String mTargetPackageName;
    private boolean mFromOverlay = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        handlerThread = new HandlerThread("ScreenCaptureHandler", Thread.NORM_PRIORITY);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        createNotificationChannel();
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        Log.d(TAG, "ScreenCaptureService onCreate.");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Screen Capture Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, EntranceActivity.class);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("WittyKeys Capture Active")
                .setContentText("Preparing screen capture...")
                .setSmallIcon(R.drawable.settings_icon)
                .setContentIntent(pendingIntent)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received: intent_present=" + (intent != null));

        if (intent == null) {
            Log.e(TAG, "Intent is null, cannot start projection. Stopping self.");
            stopSelfSafely();
            return START_NOT_STICKY;
        }

        mTargetPackageName = intent.getStringExtra("SCREEN_TARGET_PACKAGE_NAME");
        mFromOverlay = intent.getBooleanExtra(EXTRA_FROM_OVERLAY, false);
        Log.d(TAG, "Capture target metadata: package=" + mTargetPackageName
                + ", fromOverlay=" + mFromOverlay);
        if ((mTargetPackageName == null || mTargetPackageName.isEmpty()) && !mFromOverlay) {
            Log.e(TAG, "Target package name not provided in intent. Stopping service.");
            stopSelfSafely();
            return START_NOT_STICKY;
        }

        final int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        final Intent data = intent.getParcelableExtra(EXTRA_DATA);
        Log.d(TAG, "Projection permission metadata: resultCode=" + resultCode
                + ", data_present=" + (data != null));

        startForeground(NOTIFICATION_ID, createNotification());

        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);

                if (mediaProjection == null) {
                    Log.e(TAG, "MediaProjection is null even with permission. Stopping.");
                    stopSelfSafely();
                    return START_NOT_STICKY;
                }

                mediaProjectionCallback = new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.w(TAG, "[CAPTURE] MediaProjection stopped by system");
                        deliverError("MediaProjection stopped by system");
                        stopSelfSafely();
                    }
                };
                mediaProjection.registerCallback(mediaProjectionCallback, handler);
                handler.postDelayed(this::captureScreen, 500);

            } catch (Exception e) {
                Log.e(TAG, "FATAL: Exception creating MediaProjection. Check AndroidManifest.xml.", e);
                Toast.makeText(this, "Failed to start capture due to a security policy.", Toast.LENGTH_LONG).show();
                KeyboardSwitcher.getInstance().resetKeyboardDefaultState();
                stopSelfSafely();
            }
        } else {
            Log.e(TAG, "Permission was not granted by the user.");
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show();
            KeyboardSwitcher.getInstance().resetKeyboardDefaultState();
            stopSelfSafely();
        }

        return START_NOT_STICKY;
    }

    private void captureScreen() {
        if (mediaProjection == null || handler == null) {
            Log.e(TAG, "MediaProjection or handler is null. Cannot capture.");
            stopSelfSafely();
            return;
        }
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.e(TAG, "Invalid screen dimensions: " + screenWidth + "x" + screenHeight);
            stopSelfSafely();
            return;
        }

        if (imageReader != null) {
            imageReader.close();
        }
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        Log.d(TAG, "ImageReader created.");

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, handler
        );
        Log.d(TAG, "VirtualDisplay created.");

        // Timeout: if no image received within 5 seconds, abort
        mainHandler.postDelayed(() -> {
            if (!imageProcessedThisSession) {
                Log.e(TAG, "[CAPTURE] Timeout — no image received in 5s");
                deliverError("Screen capture timed out");
                stopSelfSafely();
            }
        }, 5000);

        imageReader.setOnImageAvailableListener(reader -> {
            if (imageProcessedThisSession) {
                try (Image img = reader.acquireLatestImage()) { /* Drain */ }
                return;
            }

            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    imageProcessedThisSession = true;
                    Log.d(TAG, "Image acquired. Processing capture.");

                    // Convert to bitmap immediately and close the image resource.
                    final Bitmap bitmap = imageToBitmap(image);
                    image.close();
                    image = null;

                    if (bitmap == null) {
                        Log.e(TAG, "Bitmap conversion failed.");
                        stopSelfSafely();
                        return;
                    }

                    // === Build 7.0: Save to file and delegate to ScreenshotAnalyzer ===

                    // 1. Save bitmap to internal storage
                    File screenshotDir = new File(getFilesDir(), "screenshots");
                    if (!screenshotDir.exists()) screenshotDir.mkdirs();
                    String filename = "capture_" + System.currentTimeMillis() + ".jpg";
                    File screenshotFile = new File(screenshotDir, filename);

                    try (FileOutputStream fos = new FileOutputStream(screenshotFile)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                        fos.flush();
                        Log.d(TAG, "Screenshot saved to private app storage: bytes_pending_analysis=true");
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to save screenshot", e);
                        bitmap.recycle();
                        stopSelfSafely();
                        return;
                    }
                    bitmap.recycle();

                    // 2. Get NLS conversation context
                    String conversationContext = "";
                    try {
                        ConversationMatcher matcher = ConversationMatcher.getInstance();
                        ConversationMatcher.ContactMatch activeContact = matcher.getActiveContact();
                        if (activeContact != null) {
                            conversationContext = "Currently chatting with: " + activeContact.contactName
                                    + " (app: " + activeContact.packageName + ", confidence: "
                                    + activeContact.confidence + ")";
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not get NLS context: " + e.getMessage());
                    }

                    // 3. Get session ID (if active chat session exists)
                    long sessionId = 0;
                    try {
                        ConversationMatcher.ContactMatch contact =
                                ConversationMatcher.getInstance().getActiveContact();
                        if (contact != null) {
                            ChatSession session = WittyKeysDatabase.getInstance(this)
                                    .chatSessionDao().getActiveByConversationKey(contact.conversationKey);
                            if (session != null) {
                                sessionId = session.id;
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not get session ID: " + e.getMessage());
                    }

                    // 3.5. Notify UI that screenshot is captured (show loading state immediately)
                    final String imagePath = screenshotFile.getAbsolutePath();
                    if (mFromOverlay) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            WittyKeysOverlayService overlay = WittyKeysOverlayService.getInstance();
                            if (overlay != null) {
                                overlay.onScreenshotCaptured(imagePath);
                            }
                        });
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            project.witty.keys.keyboard.KeyboardSwitcher ks =
                                project.witty.keys.keyboard.KeyboardSwitcher.getInstance();
                            if (ks != null) ks.onScreenshotCaptured(imagePath);
                        });
                    }

                    // 4. Delegate analysis to ScreenshotAnalyzer
                    final String context = conversationContext;
                    ScreenshotAnalyzer.getInstance().analyze(this, imagePath, context, sessionId,
                            new ScreenshotAnalyzer.AnalysisCallback() {
                                @Override
                                public void onSuccess(String analysis) {
                                    Log.d(TAG, "[CAPTURE] Analysis complete: analysis_length="
                                            + (analysis != null ? analysis.length() : 0));
                                    deliverResult(imagePath, analysis);
                                    stopSelfSafely();
                                }

                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "[CAPTURE] Analysis failed: " + error);
                                    deliverError(error);
                                    stopSelfSafely();
                                }
                            });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onImageAvailable: " + e.getMessage(), e);
                KeyboardSwitcher.getInstance().resetKeyboardDefaultState();
                stopSelfSafely();
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, handler);
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        Image.Plane plane = planes[0];
        java.nio.ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;

        Bitmap bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);

        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
        if (bitmap != croppedBitmap) {
            bitmap.recycle();
        }
        return croppedBitmap;
    }

    /**
     * Deliver screenshot analysis result to the keyboard UI.
     * Uses KeyboardSwitcher so both SAB and UnifiedAiView can receive it.
     */
    private void deliverResult(String imagePath, String analysis) {
        if (mFromOverlay) {
            deliverToOverlay(imagePath, analysis);
            return;
        }
        try {
            JSONObject resultJson = new JSONObject();
            resultJson.put("type", "screenshot_analysis");
            resultJson.put("image_path", imagePath);
            resultJson.put("analysis", analysis);
            resultJson.put("timestamp", System.currentTimeMillis());

            Handler uiHandler = new Handler(Looper.getMainLooper());
            final String json = resultJson.toString();
            uiHandler.post(() -> {
                try {
                    KeyboardSwitcher.getInstance().onScreenshotAnalysisReceived(json);
                } catch (Exception e) {
                    Log.e(TAG, "Error delivering result to keyboard", e);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error building result JSON", e);
        }
    }

    private void deliverError(String error) {
        if (mFromOverlay) {
            deliverOverlayError(error);
            return;
        }
        try {
            JSONObject errorJson = new JSONObject();
            errorJson.put("type", "screenshot_analysis_error");
            errorJson.put("error", error);

            Handler uiHandler = new Handler(Looper.getMainLooper());
            final String json = errorJson.toString();
            uiHandler.post(() -> {
                try {
                    KeyboardSwitcher.getInstance().onScreenshotAnalysisReceived(json);
                } catch (Exception e) {
                    Log.e(TAG, "Error delivering error to keyboard", e);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error building error JSON", e);
        }
    }

    private void deliverToOverlay(String imagePath, String analysis) {
        Log.d(TAG, "Delivering screenshot analysis to overlay");
        new Handler(Looper.getMainLooper()).post(() -> {
            WittyKeysOverlayService overlay = WittyKeysOverlayService.getInstance();
            if (overlay != null) {
                overlay.onScreenshotAnalysisReceived(imagePath, analysis);
            } else {
                Log.e(TAG, "Overlay service not available for delivery");
            }
        });
    }

    private void deliverOverlayError(String error) {
        Log.e(TAG, "Screenshot analysis error for overlay: error_present="
                + (error != null && !error.isEmpty()));
        new Handler(Looper.getMainLooper()).post(() -> {
            WittyKeysOverlayService overlay = WittyKeysOverlayService.getInstance();
            if (overlay != null) {
                overlay.onScreenshotAnalysisError(error);
            }
        });
    }

    private void stopCapture() {
        Log.d(TAG, "Stopping screen capture resources.");
        try {
            if (mediaProjection != null) {
                if (mediaProjectionCallback != null) {
                    mediaProjection.unregisterCallback(mediaProjectionCallback);
                    mediaProjectionCallback = null;
                }
                mediaProjection.stop();
                mediaProjection = null;
                Log.d(TAG, "MediaProjection stopped.");
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
                Log.d(TAG, "VirtualDisplay released.");
            }
            if (imageReader != null) {
                imageReader.setOnImageAvailableListener(null, null);
                imageReader.close();
                imageReader = null;
                Log.d(TAG, "ImageReader closed.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during stopCapture: " + e.getMessage());
        }
    }

    private void stopSelfSafely() {
        Log.d(TAG, "Requesting to stop ScreenCaptureService.");
        stopCapture();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ScreenCaptureService onDestroy.");
        stopCapture();

        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
            handler = null;
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler = null;
        }
        Log.d(TAG, "ScreenCaptureService fully destroyed.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
