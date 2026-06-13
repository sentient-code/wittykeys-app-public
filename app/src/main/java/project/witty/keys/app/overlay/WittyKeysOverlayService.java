package project.witty.keys.app.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.lang.ref.WeakReference;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import project.witty.keys.BuildConfig;
import project.witty.keys.R;
import project.witty.keys.app.EntranceActivity;

/**
 * WittyKeysOverlayService — Build 7.1 MVP
 *
 * Foreground service that manages the floating AI assistant overlay.
 * Draws a floating bubble on top of all apps via SYSTEM_ALERT_WINDOW.
 *
 * Components:
 * - Floating bubble (always visible, draggable)
 * - Action panel (expands from bubble: Screenshot AI + Smart Replies)
 * - Overlay panels (AI Chat, Reply Cards — added in S2-OVERLAY-SCREEN/REPLY)
 */
public class WittyKeysOverlayService extends Service {

    private static final String TAG = "WK_OVERLAY";
    private static final String CHANNEL_ID = "wk_overlay_channel";
    private static final int NOTIFICATION_ID = 301;

    private WindowManager windowManager;
    private View bubbleView;
    private View actionPanelView;

    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams actionPanelParams;

    private boolean isActionPanelShowing = false;
    private OverlayChatPanel chatPanel;
    private OverlayReplyPanel replyPanel;
    private static WeakReference<WittyKeysOverlayService> sInstanceRef;

    // ─── Debug state receiver (golden screenshot testing) ───
    private static final String ACTION_DEBUG_STATE = "project.witty.keys.overlay.DEBUG_STATE";
    private final BroadcastReceiver debugStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_DEBUG_STATE.equals(intent.getAction())) return;
            if (!BuildConfig.DEBUG) return;
            String state = intent.getStringExtra("state");
            Log.d(TAG, "[DEBUG_STATE] received: " + state);
            if (state != null) {
                new Handler(Looper.getMainLooper()).post(() -> applyDebugState(state));
            }
        }
    };

    // ─── Popup management (Task 2) ───
    private View popupView;
    private WindowManager.LayoutParams popupParams;
    private boolean isPopupShowing = false;
    private String currentPopupType = null; // "action", "reply", "screenshot-history", "screenshot-loading", "screenshot-chat", "perm"
    private boolean suppressPopupDismissCallback = false;
    private View dimView;
    private int savedBubbleX = 0;
    private int savedBubbleY = 300;

    public static WittyKeysOverlayService getInstance() {
        return sInstanceRef != null ? sInstanceRef.get() : null;
    }

    private void applyDebugState(String state) {
        if (!BuildConfig.DEBUG || state == null) return;
        switch (state) {
            case "bubble_idle":
                hidePopup();
                showBubble();
                break;
            case "action_panel":
                showActionPanelPopup();
                break;
            case "reply_empty":
            case "reply_populated":
                if (replyPanel != null) replyPanel.setDebugState(state);
                break;
            default:
                if (chatPanel != null) chatPanel.setDebugState(state);
                break;
        }
    }

    // ─── Lifecycle ───

    @Override
    public void onCreate() {
        super.onCreate();
        sInstanceRef = new WeakReference<>(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        chatPanel = new OverlayChatPanel(this, this);
        replyPanel = new OverlayReplyPanel(this, this);
        IntentFilter debugFilter = new IntentFilter(ACTION_DEBUG_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugStateReceiver, debugFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(debugStateReceiver, debugFilter);
        }
        Log.d(TAG, "OverlayService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        showBubble();
        Log.d(TAG, "OverlayService started as foreground");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(debugStateReceiver); } catch (Exception ignored) {}
        if (chatPanel != null) {
            chatPanel.shutdown();
            chatPanel.hide();
            chatPanel = null;
        }
        if (replyPanel != null) {
            replyPanel.hide();
            replyPanel = null;
        }
        hidePopup();
        hideDim();
        removeBubble();
        sInstanceRef = null;
        Log.d(TAG, "OverlayService destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Notification (required for foreground service) ───

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "WittyKeys AI Assistant",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the floating AI assistant running");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, EntranceActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WittyKeys AI")
            .setContentText("Tap the floating bubble for AI assistance")
            .setSmallIcon(R.drawable.settings_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    // ─── Bubble Management ───

    public void showBubble() {
        if (bubbleView != null) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        bubbleView = inflater.inflate(R.layout.overlay_bubble, null);

        int bubbleSize = dpToPx(52);
        bubbleParams = new WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 0;
        bubbleParams.y = 300;

        setupBubbleTouchListener();

        try {
            windowManager.addView(bubbleView, bubbleParams);
            Log.d(TAG, "Bubble added to window");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add bubble: " + e.getMessage());
        }

        // Refresh badge count
        refreshBadge();
    }

    private void removeBubble() {
        if (bubbleView != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
            bubbleView = null;
        }
    }

    /**
     * Restore bubble at its previously saved position.
     */
    private void showBubbleAtSavedPosition() {
        showBubble();
        if (bubbleParams != null) {
            bubbleParams.x = savedBubbleX;
            bubbleParams.y = savedBubbleY;
            try {
                windowManager.updateViewLayout(bubbleView, bubbleParams);
            } catch (Exception ignored) {}
        }
    }

    // ─── Bubble Touch: Drag + Tap ───

    private void setupBubbleTouchListener() {
        if (bubbleView == null) return;

        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;
            private static final int CLICK_THRESHOLD = 10;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > CLICK_THRESHOLD || Math.abs(dy) > CLICK_THRESHOLD) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            bubbleParams.x = initialX + dx;
                            bubbleParams.y = initialY + dy;
                            try {
                                windowManager.updateViewLayout(bubbleView, bubbleParams);
                            } catch (Exception ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            onBubbleTapped();
                        } else {
                            snapBubbleToEdge();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void snapBubbleToEdge() {
        if (bubbleView == null || windowManager == null) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int bubbleCenter = bubbleParams.x + (bubbleView.getWidth() / 2);

        if (bubbleCenter < screenWidth / 2) {
            bubbleParams.x = 0;
        } else {
            bubbleParams.x = screenWidth - bubbleView.getWidth();
        }

        try {
            windowManager.updateViewLayout(bubbleView, bubbleParams);
        } catch (Exception ignored) {}
    }

    // ─── Bubble Tap + Popup Management (Task 2-3) ───

    private void onBubbleTapped() {
        Log.d(TAG, "Bubble tapped");
        if (isPopupShowing) {
            hidePopup(); // ✕ close behavior
        } else {
            showActionPanelPopup(); // open action panel as popup
        }
    }

    /**
     * Show a popup card below the bubble.
     * Positions popup 6dp below bubble, adds dim layer, toggles bubble state to ✕.
     */
    public void showPopup(View view, String type, int widthDp, int maxHeightDp) {
        // Ensure bubble is present before positioning popup
        if (bubbleView == null) {
            showBubbleAtSavedPosition();
        }

        hidePopup(); // Remove any existing popup

        popupView = view;
        currentPopupType = type;

        int popupWidth = dpToPx(widthDp);
        int maxPopupHeight = dpToPx(maxHeightDp);
        int bubbleHeight = dpToPx(52);
        int gap = dpToPx(6);
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // Input-capable popups (reply, screenshot chat) need focus for keyboard
        boolean needsKeyboard = "reply".equals(type) || type.startsWith("screenshot-chat")
            || type.startsWith("screenshot-loading");
        int windowFlags = needsKeyboard
            ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        // Use fixed max height to prevent popup from growing unbounded
        // For action panel, use WRAP_CONTENT (small content); for others, enforce maxHeight
        boolean useFixedHeight = !"action".equals(type);
        int popupHeight = useFixedHeight ? maxPopupHeight : WindowManager.LayoutParams.WRAP_CONTENT;

        popupParams = new WindowManager.LayoutParams(
            popupWidth,
            popupHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags,
            PixelFormat.TRANSLUCENT
        );

        if (needsKeyboard) {
            // Use ADJUST_PAN to keep popup at fixed height — keyboard pans content up
            popupParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        }

        // Check if popup would go off-screen below bubble — reposition bubble up if needed
        int actualPopupHeight = useFixedHeight ? maxPopupHeight : dpToPx(200);
        int popupTop = bubbleParams.y + bubbleHeight + gap;
        int popupBottom = popupTop + actualPopupHeight;
        int bottomMargin = dpToPx(16);

        if (popupBottom > screenHeight - bottomMargin) {
            int newBubbleY = screenHeight - actualPopupHeight - bubbleHeight - gap - bottomMargin;
            if (newBubbleY < 0) newBubbleY = 0;
            bubbleParams.y = newBubbleY;
            try {
                windowManager.updateViewLayout(bubbleView, bubbleParams);
            } catch (Exception ignored) {}
        }

        // Position below bubble with 6dp gap
        popupParams.x = bubbleParams.x;
        popupParams.y = bubbleParams.y + bubbleHeight + gap;
        popupParams.gravity = Gravity.TOP | Gravity.START;

        showDim();
        toggleBubbleState(true); // Show ✕

        try {
            Log.w("WK_DIAG", "[ISSUE3] WittyKeysOverlayService.addOverlayView for popup type=" + type);
            addOverlayView(popupView, popupParams);
            isPopupShowing = true;
            Log.d(TAG, "Popup shown: type=" + type);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show popup: " + e.getMessage());
            isPopupShowing = false;
        }
    }

    /**
     * Hide the current popup.
     */
    public void hidePopup() {
        String dismissedType = currentPopupType;
        if (popupView != null) {
            try {
                removeOverlayView(popupView);
            } catch (Exception ignored) {}
            popupView = null;
        }
        currentPopupType = null;
        isPopupShowing = false;
        toggleBubbleState(false); // Show logo
        hideDim();
        // Restore badge visibility
        refreshBadge();
        Log.d(TAG, "Popup hidden");
        if (!suppressPopupDismissCallback
            && dismissedType != null
            && dismissedType.startsWith("screenshot-")
            && chatPanel != null) {
            chatPanel.onPopupDismissedByService(dismissedType);
        }
        if (!suppressPopupDismissCallback
            && "reply".equals(dismissedType)
            && replyPanel != null) {
            replyPanel.onPopupDismissedByService();
        }
    }

    void hidePopupFromPanel() {
        suppressPopupDismissCallback = true;
        try {
            hidePopup();
        } finally {
            suppressPopupDismissCallback = false;
        }
    }

    /**
     * Toggle bubble between logo (idle) and ✕ (popup open) states.
     */
    private void toggleBubbleState(boolean isClose) {
        if (bubbleView == null) return;
        View logo = bubbleView.findViewById(R.id.overlay_bubble_logo);
        View close = bubbleView.findViewById(R.id.overlay_bubble_close);
        View badge = bubbleView.findViewById(R.id.overlay_bubble_badge);

        if (logo != null) {
            logo.setVisibility(isClose ? View.GONE : View.VISIBLE);
        }
        if (close != null) {
            close.setVisibility(isClose ? View.VISIBLE : View.GONE);
        }
        if (badge != null && !isClose) {
            // Restore badge visibility when returning to logo state
            badge.setVisibility(View.GONE); // Will be updated by refreshBadge
        }
    }

    /**
     * Show semi-transparent dim layer behind bubble and popup.
     */
    private void showDim() {
        if (dimView != null) return;
        dimView = new View(this);
        dimView.setBackgroundColor(getResources().getColor(R.color.wk_overlay_dim, null));
        dimView.setOnClickListener(v -> hidePopup()); // tap outside dismisses

        WindowManager.LayoutParams dimParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );

        try {
            windowManager.addView(dimView, dimParams);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add dim view: " + e.getMessage());
        }
    }

    /**
     * Hide and remove the dim layer.
     */
    private void hideDim() {
        if (dimView != null) {
            try {
                windowManager.removeView(dimView);
            } catch (Exception ignored) {}
            dimView = null;
        }
    }

    // ─── Action Panel (now via popup pattern in Task 4) ───

    /**
     * Show action panel as a popup card (Task 4).
     * 230dp wide × ~200dp tall, positioned below bubble with 6dp gap.
     */
    private void showActionPanelPopup() {
        View panel = LayoutInflater.from(this).inflate(R.layout.overlay_action_panel, null);

        View screenshotBtn = panel.findViewById(R.id.overlay_btn_screenshot);
        View repliesBtn = panel.findViewById(R.id.overlay_btn_replies);

        if (screenshotBtn != null) {
            screenshotBtn.setOnClickListener(v -> {
                hidePopup();
                // Always show session list — it handles empty state with New Chat + Capture CTAs
                if (chatPanel != null) {
                    chatPanel.showHistory();
                }
            });
        }

        if (repliesBtn != null) {
            repliesBtn.setOnClickListener(v -> {
                hidePopup();
                onRepliesTapped();
            });
        }

        // Update badge on action panel
        android.widget.TextView badge = panel.findViewById(R.id.overlay_action_reply_badge);
        if (badge != null && replyPanel != null) {
            int count = replyPanel.getPendingReplyCount();
            if (count > 0) {
                badge.setText(String.valueOf(count));
                badge.setVisibility(View.VISIBLE);
            }
        }

        showPopup(panel, "action", 230, 200);
    }

    // Legacy methods for backward compatibility (deprecated)
    public void showActionPanel() {
        showActionPanelPopup();
    }

    private void hideActionPanel() {
        hidePopup();
    }

    private void removeActionPanel() {
        hidePopup();
    }

    // ─── Action Handlers (stubs — implemented in S2-OVERLAY-SCREEN and S2-OVERLAY-REPLY) ───

    /**
     * Public entry point for triggering a screenshot capture.
     * Called from OverlayChatPanel's "New Screenshot" button.
     */
    public void triggerScreenshot() {
        onScreenshotTapped();
    }

    private void onScreenshotTapped() {
        Log.d(TAG, "Screenshot action triggered");

        // Save bubble position before removing (for restoration after capture)
        if (bubbleParams != null) {
            savedBubbleX = bubbleParams.x;
            savedBubbleY = bubbleParams.y;
        }

        // Hide bubble during capture (so it doesn't appear in screenshot)
        removeBubble();

        // Small delay to ensure bubble is gone before capture
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            launchScreenCapture();
        }, 300);
    }

    private void launchScreenCapture() {
        try {
            // Get the foreground app package for context
            String targetPackage = null;
            try {
                project.witty.keys.app.context.ConversationMatcher matcher =
                    project.witty.keys.app.context.ConversationMatcher.getInstance();
                if (matcher != null) {
                    project.witty.keys.app.context.ConversationMatcher.ContactMatch contact =
                        matcher.getActiveContact();
                    if (contact != null) {
                        targetPackage = contact.packageName;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get active contact for screenshot context: " + e.getMessage());
            }

            // Launch ScreenshotPermissionActivity with FROM_OVERLAY flag
            Intent intent = new Intent(this, project.witty.keys.app.helpers.ScreenshotPermissionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(project.witty.keys.app.helpers.ScreenCaptureService.EXTRA_FROM_OVERLAY, true);
            if (targetPackage != null) {
                intent.putExtra("SCREEN_TARGET_PACKAGE_NAME", targetPackage);
            }
            startActivity(intent);

            Log.d(TAG, "ScreenshotPermissionActivity launched for overlay capture");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch screenshot capture: " + e.getMessage());
            showBubble();
        }
    }

    /**
     * Called by ScreenCaptureService when screenshot is captured (before API analysis).
     * Shows the loading state immediately so user sees feedback.
     */
    public void onScreenshotCaptured(String imagePath) {
        Log.d(TAG, "Screenshot captured, showing loading state");

        // Restore bubble at saved position
        showBubbleAtSavedPosition();

        if (chatPanel != null) {
            chatPanel.showAnalyzing(imagePath);
        }
    }

    /**
     * Called by ScreenCaptureService when screenshot analysis completes.
     */
    public void onScreenshotAnalysisReceived(String imagePath, String analysis) {
        Log.d(TAG, "Screenshot analysis received for overlay: " + (analysis != null ? analysis.length() : 0) + " chars");

        // Record AI usage for screenshot analysis
        project.witty.keys.app.utils.DailyUsageTracker.getInstance(this).recordUsage();

        // Ensure bubble is present
        if (bubbleView == null) {
            showBubbleAtSavedPosition();
        }

        if (chatPanel != null) {
            if (chatPanel.isShowing()) {
                // Loading state already showing — transition to chat mode
                chatPanel.onAnalysisComplete(analysis);
            } else {
                // No loading state (edge case) — show full chat directly
                chatPanel.show(imagePath, analysis);
            }
        } else {
            Log.e(TAG, "chatPanel is null — cannot show analysis");
            showBubble();
        }
    }

    /**
     * Called by ScreenCaptureService on analysis error.
     */
    public void onScreenshotAnalysisError(String error) {
        Log.e(TAG, "Screenshot analysis error: " + error);

        // Ensure bubble is present
        if (bubbleView == null) {
            showBubbleAtSavedPosition();
        }

        if (chatPanel != null && chatPanel.isShowing()) {
            chatPanel.onAnalysisError(error);
        } else {
            android.widget.Toast.makeText(this, "Screenshot analysis failed", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void onRepliesTapped() {
        Log.d(TAG, "Replies action triggered");
        // Reply panel now uses showPopup() internally
        if (replyPanel != null) {
            replyPanel.show();
        }
    }

    // ─── Badge (for pending reply count) ───

    /**
     * Refresh the bubble badge count based on pending replies in ReplyCache.
     */
    public void refreshBadge() {
        if (replyPanel != null) {
            int count = replyPanel.getPendingReplyCount();
            Log.d(TAG, "refreshBadge: count=" + count + " bubbleView=" + (bubbleView != null));
            updateBadge(count);
        } else {
            Log.d(TAG, "refreshBadge: replyPanel is null");
        }
    }

    /**
     * Trigger a badge refresh from outside the service (e.g., after reply precomputation).
     * Safe to call from any thread.
     */
    public static void triggerBadgeRefresh() {
        WittyKeysOverlayService instance = getInstance();
        Log.d(TAG, "triggerBadgeRefresh called, instance=" + (instance != null));
        if (instance != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                instance.refreshBadge();
                // Also refresh reply panel if it's currently showing
                if (instance.replyPanel != null && instance.replyPanel.isShowing()) {
                    instance.replyPanel.refreshIfShowing();
                }
            });
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Configuration changed: rotation/screen size");
        // Re-snap bubble to edge for new screen dimensions
        snapBubbleToEdge();
        // If popup showing, reposition
        if (isPopupShowing && popupParams != null) {
            repositionPopup();
        }
    }

    private void repositionPopup() {
        if (popupParams == null) return;
        try {
            popupParams.y = bubbleParams.y + dpToPx(52) + dpToPx(6);
            windowManager.updateViewLayout(popupView, popupParams);
            Log.d(TAG, "Popup repositioned");
        } catch (Exception e) {
            Log.e(TAG, "Failed to reposition popup: " + e.getMessage());
        }
    }

    public void updateBadge(int count) {
        if (bubbleView == null) return;
        View badge = bubbleView.findViewById(R.id.overlay_bubble_badge);
        if (badge instanceof android.widget.TextView) {
            android.widget.TextView badgeText = (android.widget.TextView) badge;
            if (count > 0) {
                badgeText.setText(String.valueOf(count));
                badgeText.setVisibility(View.VISIBLE);
            } else {
                badgeText.setVisibility(View.GONE);
            }
        }
    }

    // ─── Public API for other overlay panels ───

    public WindowManager getWindowManager() {
        return windowManager;
    }

    /**
     * Add a view to the overlay with permission and exception handling (Task 8.6).
     * Checks SYSTEM_ALERT_WINDOW permission before adding to prevent crashes
     * if user revokes permission mid-session.
     */
    public void addOverlayView(View view, WindowManager.LayoutParams params) {
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission revoked — cannot add view");
            stopSelf();
            return;
        }
        try {
            windowManager.addView(view, params);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay view: " + e.getMessage());
        }
    }

    public void removeOverlayView(View view) {
        try {
            windowManager.removeView(view);
        } catch (Exception ignored) {}
    }

    // ─── Util ───

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
