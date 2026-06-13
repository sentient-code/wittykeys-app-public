package project.witty.keys.app.helpers;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import project.witty.keys.app.context.ContextEngine;
import project.witty.keys.app.context.ScreenContext;
import project.witty.keys.keyboard.KeyboardSwitcher;
import project.witty.keys.latin.LatinIME;

public class CommunicationService extends Service {

    private static final String TAG = "CommunicationService";

    // Static references to share across all instances/processes
    private static ScreenReaderAccessibility sAccessibilityService;
    private static LatinIME sLatinIME;

    // Instance references (for backward compatibility)
    private ScreenReaderAccessibility accessibilityService;
    private LatinIME latinIME;
    private final IBinder mBinder = new LocalBinder();
    private static final int MAX_CHUNKS = 200;
    private static final int MAX_TOTAL_CHARS = 8000;

    private final ArrayDeque<String> recentTextChunks = new ArrayDeque<>();
    public class LocalBinder extends Binder {
        public CommunicationService getService() {
            return CommunicationService.this;
        }
    }


    public void setAccessibilityService(ScreenReaderAccessibility service) {
        this.accessibilityService = service;
        sAccessibilityService = service;  // Also set static reference
        Log.d("WK_AI_DEBUG", "[COMM_SVC] setAccessibilityService: " + (service != null));
    }


    public void setLatinIME(LatinIME ime) {
        this.latinIME = ime;
        sLatinIME = ime;  // Also set static reference
        Log.d("WK_AI_DEBUG", "[COMM_SVC] setLatinIME: " + (ime != null));
    }

    /**
     * Get the ScreenReaderAccessibility instance (tries static first, then instance)
     */
    private ScreenReaderAccessibility getAccessibilityService() {
        // Try static reference first (works across service restarts)
        if (sAccessibilityService != null) {
            return sAccessibilityService;
        }
        // Try instance reference
        if (accessibilityService != null) {
            return accessibilityService;
        }
        // Try the static instance from ScreenReaderAccessibility directly
        return ScreenReaderAccessibility.getInstance();
    }

    @Override public IBinder onBind(Intent intent) {
        Log.d("WK_AI_DEBUG", "[COMM_SVC] onBind() called");
        return mBinder;
    }

    public void enableAccessibility() {
        ScreenReaderAccessibility service = getAccessibilityService();
        if (service != null) {
            service.enableAccessibility();
            Log.d(TAG, "Enable accessibility called by Communication Service");
        } else {
            Log.e(TAG, "Accessibility service not set.");
        }
    }

    public void disableAccessibility() {
        ScreenReaderAccessibility service = getAccessibilityService();
        if (service != null) {
            service.disableAccessibility();
            Log.d(TAG, "Disable accessibility called by Communication Service");
        } else {
            Log.e(TAG, "Accessibility service not set.");
        }
    }
    public void sendTextToLatinIME(String text) {
        if (latinIME != null) {
            Log.w(TAG, "sendTextToLatinIME: length=" + (text == null ? 0 : text.length()));
            if (text != null && text.length() > 0) {
                Log.v(TAG, "first200=" + text.substring(0, Math.min(200, text.length())).replace("\n", " "));
            }
            // NEW: keep a copy in rolling buffer
            appendAccessibilityDump(text);

            latinIME.receiveTextFromAccessibility(text);
        } else {
            Log.e(TAG, "LatinIME not set (sendTextToLatinIME dropped)");
        }
    }

    public void sendRepliesToLatinIME(String replies) {
        if (latinIME != null) {
            latinIME.onRepliesReceived(replies);
        } else {
            Log.e("CommunicationService", "Cannot send replies, LatinIME instance is not registered.");
        }
    }

    public void sendScreenContextToLatinIME(ScreenContext context) {
        if (latinIME != null) {
            // Get the KeyboardSwitcher instance via LatinIME or directly if you have a reference
            KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
            if (switcher != null) {
                switcher.onScreenContextReceived(context);
            }
        } else {
            Log.e(TAG, "Cannot send ScreenContext, LatinIME instance is not registered.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public AccessibilityNodeInfo getRootNodeFromAccessibility() {
        ScreenReaderAccessibility service = getAccessibilityService();
        if (service != null) {
            AccessibilityNodeInfo root = service.getLatestRootNode();
            Log.d("WK_AI_DEBUG", "[COMM_SVC] getRootNode: root=" + (root != null ? root.getPackageName() : "null"));
            return root;
        } else {
            Log.e("WK_AI_DEBUG", "[COMM_SVC] getRootNode FAILED: accessibilityService is null!");
            return null;
        }
    }

    // Debug-only: standalone ContextEngine for E2E testing when accessibility service can't bind
    private static ContextEngine sDebugContextEngine;

    /**
     * Set a standalone ContextEngine for E2E testing.
     * Used when ScreenReaderAccessibility can't bind in the instrumentation process.
     * Debug builds only.
     */
    public void setDebugContextEngine(ContextEngine engine) {
        sDebugContextEngine = engine;
        Log.i("WK_AI_DEBUG", "[COMM_SVC] setDebugContextEngine: " + (engine != null));
    }

    /**
     * Get the ContextEngine for intelligent screen context extraction (Build 6.3)
     */
    public ContextEngine getContextEngine() {
        ScreenReaderAccessibility service = getAccessibilityService();
        if (service != null) {
            ContextEngine engine = service.getContextEngine();
            Log.d("WK_AI_DEBUG", "[COMM_SVC] getContextEngine: engine=" + (engine != null));
            return engine;
        }
        // Debug fallback: use standalone ContextEngine set by E2E test infrastructure
        if (sDebugContextEngine != null) {
            Log.i("WK_AI_DEBUG", "[COMM_SVC] getContextEngine: using debug fallback ContextEngine");
            return sDebugContextEngine;
        }
        Log.e("WK_AI_DEBUG", "[COMM_SVC] getContextEngine FAILED: accessibilityService is null!");
        return null;
    }

    private void appendAccessibilityDump(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (recentTextChunks) {
            recentTextChunks.addLast(text);
            // keep chunk count reasonable
            while (recentTextChunks.size() > MAX_CHUNKS) {
                recentTextChunks.removeFirst();
            }
            // also cap by total chars (rough)
            int total = 0;
            for (String s : recentTextChunks) {
                total += s.length();
            }
            while (total > MAX_TOTAL_CHARS && !recentTextChunks.isEmpty()) {
                String removed = recentTextChunks.removeFirst();
                total -= (removed != null ? removed.length() : 0);
            }
        }
    }

    public List<String> getRecentTextsSnapshot() {
        synchronized (recentTextChunks) {
            return new ArrayList<>(recentTextChunks);
        }
    }

    /**
     * Phase 4 (J13): Notify LatinIME that a new message was received while user is typing.
     * This triggers the brain blink animation and updates quick replies.
     *
     * @param senderName The name of the sender (or app name)
     * @param messageText The new message text
     */
    public void notifyNewMessageReceived(String senderName, String messageText) {
        if (latinIME != null) {
            Log.d(TAG, "[J13] Notifying LatinIME of new message: sender_present="
                    + (senderName != null && !senderName.isEmpty())
                    + ", text_length=" + (messageText != null ? messageText.length() : 0));
            latinIME.onNewMessageWhileTyping(senderName, messageText);
        } else {
            Log.e(TAG, "[J13] Cannot notify new message, LatinIME is null");
        }
    }


}
