package project.witty.keys.app.helpers;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import project.witty.keys.R;
import project.witty.keys.app.context.ContactNameExtractor;
import project.witty.keys.app.context.ConversationMatcher;
import project.witty.keys.app.context.ContextEngine;
import project.witty.keys.app.context.AppDetector;
import project.witty.keys.app.context.ScreenContext;
import project.witty.keys.latin.LatinIME;

public class ScreenReaderAccessibility extends AccessibilityService {

    private static final String TAG = "ScreenReaderAccessibility";
    private boolean isListening = false;

    private ContextEngine contextEngine;
    private CommunicationService communicationService;
    private boolean communicationServiceBound = false;
    private static ScreenReaderAccessibility sInstance;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // Phase 4: New message detection for J13
    private String lastSeenMessageText = null;
    private String lastSeenSenderName = null;
    private long lastUserTypingTime = 0;  // Timestamp of last user typing
    private static final long TYPING_DEBOUNCE_MS = 3000;  // Ignore messages within 3s of user typing

    public static ScreenReaderAccessibility getInstance() {
        return sInstance;
    }

    public AccessibilityNodeInfo getLatestRootNode() {
        return getRootInActiveWindow();
    }

    /**
     * Get the ContextEngine instance for intelligent screen context extraction
     */
    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    private ServiceConnection communicationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CommunicationService.LocalBinder binder = (CommunicationService.LocalBinder) service;
            communicationService = binder.getService();
            communicationService.setAccessibilityService(ScreenReaderAccessibility.this);
            communicationServiceBound = true;
            Log.d("WK_AI_DEBUG", "[ACCESSIBILITY] CommunicationService CONNECTED to accessibility");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            communicationServiceBound = false;
            communicationService = null;
            Log.w("WK_AI_DEBUG", "[ACCESSIBILITY] CommunicationService DISCONNECTED from accessibility");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("WK_AI_DEBUG", "[ACCESSIBILITY] ScreenReaderAccessibility.onCreate() CALLED");
        sInstance = this;
        contextEngine = new ContextEngine();
        Intent intent = new Intent(this, CommunicationService.class);
        bindService(intent, communicationServiceConnection, Context.BIND_AUTO_CREATE);
        Log.d("WK_AI_DEBUG", "[ACCESSIBILITY] ContextEngine initialized, binding to CommunicationService...");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (communicationServiceBound) {
            unbindService(communicationServiceConnection);
        }
        if (contextEngine != null) {
            contextEngine.invalidateCache();
            contextEngine = null;
        }
        sInstance = null;
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] onDestroy - ContextEngine cleaned up");
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            if (DebugConfig.isDebugMode) {
                Log.e(TAG, "[CE] onAccessibilityEvent: event is NULL");
            }
            return;
        }
        final CharSequence pkg = event.getPackageName();
        final int type = event.getEventType();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] onAccessibilityEvent: type=" + type + " pkg=" + pkg);
        }

        // === BUILD 7.0: Contact name extraction for ConversationMatcher ===
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkgName = event.getPackageName();
            if (pkgName != null) {
                String packageName = pkgName.toString();
                if (AppDetector.isContextualApp(packageName)) {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root != null) {
                        try {
                            // Extract contact name — LOCAL OPERATION ONLY
                            String contactName = ContactNameExtractor.extractFromTree(root, packageName);
                            if (contactName != null) {
                                // Pure local matching — compare against NLS-captured contacts
                                // NO network call, NO API call, NO storage of accessibility data
                                ConversationMatcher.getInstance()
                                    .setActiveContact(packageName, contactName,
                                        ConversationMatcher.MatchSource.ACCESSIBILITY);
                                if (DebugConfig.isDebugMode) {
                                    Log.d(TAG, "[A11Y-MATCH] Contact identified: " + contactName
                                        + " in " + packageName);
                                }
                            }
                        } finally {
                            root.recycle();
                        }
                    }
                }
            }
        }
        // === END BUILD 7.0 ===

        AccessibilityNodeInfo source = event.getSource();
        if (source == null) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[CE] onAccessibilityEvent: source is NULL (no view to traverse)");
            }
            return;
        }

        // Use ContextEngine for intelligent extraction
        if (contextEngine != null) {
            ScreenContext context = contextEngine.extractContext(source);

            if (context != null) {
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[CE] Context extracted: " + context.getViewType() +
                            " from " + context.getAppName());
                }

                if (communicationServiceBound && communicationService != null) {
                    communicationService.sendScreenContextToLatinIME(context);

                    // Also send raw text for backward compatibility
                    String lastMessage = contextEngine.getLastMessage();
                    if (lastMessage != null && !lastMessage.isEmpty()) {
                        communicationService.sendTextToLatinIME(lastMessage);

                        // Phase 4 (J13): Detect new incoming message while user is typing
                        String currentSender = context.getAppName();
                        if (!lastMessage.equals(lastSeenMessageText)) {
                            // New message detected!
                            if (lastSeenMessageText != null) {
                                // Phase 4 Fix: Filter out typing indicators (not real messages)
                                if (isTypingIndicator(lastMessage)) {
                                    Log.d(TAG, "[J13] Typing indicator ignored: text_length="
                                            + lastMessage.length());
                                } else if (isWithinTypingDebounce()) {
                                    // User recently typed - this is likely their own sent message, ignore
                                    Log.d(TAG, "[J13] Message change ignored after user typing: text_length="
                                            + lastMessage.length());
                                } else {
                                    // This is a genuine INCOMING message (not user's own)
                                    Log.d(TAG, "[J13] New incoming message metadata: app=" + currentSender
                                            + ", text_length=" + lastMessage.length());
                                    communicationService.notifyNewMessageReceived(currentSender, lastMessage);
                                }
                            }
                            lastSeenMessageText = lastMessage;
                            lastSeenSenderName = currentSender;
                        }
                    }
                } else {
                    if (DebugConfig.isDebugMode) {
                        Log.e(TAG, "[CE] CommunicationService NOT bound; cannot forward context");
                    }
                }
            } else {
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[CE] No context extracted from event");
                }
            }
        } else {
            // Fallback to old method if ContextEngine not available
            StringBuilder buf = new StringBuilder();
            int[] counters = new int[]{0, 0};
            collectAllText(source, buf, counters);

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[CE] Fallback: collected " + buf.length() + " chars");
            }

            if (buf.length() > 0) {
                String payload = buf.toString();
                if (communicationServiceBound && communicationService != null) {
                    communicationService.sendTextToLatinIME(payload);
                }
            }
        }
    }

    private void getTextFromNode(AccessibilityNodeInfo node) {
        if (node.getClassName() != null && node.getClassName().equals("android.widget.TextView")) {
            CharSequence text = node.getText();
            if (text != null && !text.toString().isEmpty()) {
                Log.d(TAG, "Captured text metadata: length=" + text.length());
                if (communicationServiceBound) {
                    communicationService.sendTextToLatinIME(text.toString());
                    disableAccessibility();
                } else {
                    Log.e(TAG, "Communication Service not bound when sending text.");
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                getTextFromNode(child);
                child.recycle();
            }
        }
    }
    @Override
    public void onInterrupt() {
        Log.w(TAG, "onInterrupt()");
    }

    private void collectAllText(AccessibilityNodeInfo node, StringBuilder out, int[] counters) {
        if (node == null) return;
        counters[0]++;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0) {
            counters[1]++;
            out.append(text).append("\n");
        } else if (desc != null && desc.length() > 0) {
            counters[1]++;
            out.append(desc).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectAllText(node.getChild(i), out, counters);
        }
    }
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.d("WK_AI_DEBUG", "[ACCESSIBILITY] onServiceConnected() - Accessibility service is NOW LIVE!");
        ui.post(() -> Toast.makeText(this, "WittyKeys AI: Accessibility connected!", Toast.LENGTH_SHORT).show());
    }
    public void enableAccessibility() {
        isListening = true;
        Log.d(TAG, "Accessibility enabled : " + isListening );
    }

    public void disableAccessibility() {
        isListening = false;
        Log.d(TAG, "Accessibility disabled");
    }

    /**
     * Phase 4: Notify that user is typing.
     * Called by LatinIME when user types/sends a message.
     * This prevents user's own sent messages from triggering "new message" detection.
     */
    public void notifyUserTyping() {
        lastUserTypingTime = System.currentTimeMillis();
        Log.d(TAG, "[J13] User typing notified - will ignore new messages for " + TYPING_DEBOUNCE_MS + "ms");
    }

    /**
     * Phase 4: Check if we're within the debounce period after user typing.
     * If user recently typed, we should ignore message changes (they're likely user's own messages).
     */
    private boolean isWithinTypingDebounce() {
        long elapsed = System.currentTimeMillis() - lastUserTypingTime;
        return elapsed < TYPING_DEBOUNCE_MS;
    }

    /**
     * Phase 4: Reset last seen message when switching apps.
     * Called when user switches to a different app.
     */
    public void resetLastSeenMessage() {
        lastSeenMessageText = null;
        lastSeenSenderName = null;
        Log.d(TAG, "[J13] Last seen message reset");
    }

    /**
     * Phase 4: Check if the message is a typing indicator or status message (not a real message).
     * These should not trigger new message detection.
     */
    private boolean isTypingIndicator(String message) {
        if (message == null || message.isEmpty()) return false;
        String lower = message.toLowerCase().trim();

        // Typing indicators
        if (lower.contains("is typing") || lower.contains("typing...")) return true;
        if (lower.contains("recording audio") || lower.contains("recording voice")) return true;

        // Time indicators (e.g., "3 min", "14 min", "1 hour")
        if (lower.matches("\\d+\\s*min(s)?") || lower.matches("\\d+\\s*hour(s)?")) return true;

        // Status indicators
        if (lower.equals("online") || lower.equals("offline")) return true;
        if (lower.startsWith("last seen")) return true;

        // System messages
        if (lower.equals("history is on") || lower.equals("history is off")) return true;
        if (lower.contains("end-to-end encrypted")) return true;
        if (lower.contains("messages are secured")) return true;
        if (lower.equals("today") || lower.equals("yesterday")) return true;

        // Very short messages that are likely status (under 3 chars)
        if (lower.length() <= 3) return true;

        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // or START_REDELIVER_INTENT
    }
}
