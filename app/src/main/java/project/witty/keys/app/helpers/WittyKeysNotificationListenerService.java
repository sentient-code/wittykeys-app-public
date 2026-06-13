package project.witty.keys.app.helpers;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;

import project.witty.keys.app.context.AppDetector;
import project.witty.keys.app.context.ConversationMatcher;
import project.witty.keys.app.context.MediaMessageNormalizer;
import project.witty.keys.app.context.MessageDebouncer;
import project.witty.keys.app.context.NlsMessageBuffer;
import project.witty.keys.app.context.ReplyCache;
import project.witty.keys.app.context.ReplyPrecomputeManager;

/**
 * WittyKeysNotificationListenerService — NEW file (Sprint 1, Build 7.0)
 *
 * Captures incoming messages via notification system.
 * Parses EXTRA_MESSAGES / MessagingStyle for structured sender+text+timestamp.
 * Feeds data to ConversationMatcher and MessageDebouncer.
 *
 * CONTRACT FOR DOWNSTREAM PHASES:
 * - P2 (Smart Replies): MessageDebouncer.BatchListener triggers ReplyPrecomputeManager
 * - ConversationMatcher.registerNlsContact() builds the contact database
 */
public class WittyKeysNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "WK_NLS";
    private static final long SELF_REPLY_FILTER_WINDOW_MS = 10_000; // 10 seconds

    private MessageDebouncer debouncer;
    private static WittyKeysNotificationListenerService sInstance;

    // Track recently sent replies to filter self-sent notification echoes
    private static final ConcurrentHashMap<String, Long> recentlySentPackages = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> recentlySentTexts = new ConcurrentHashMap<>();

    public static WittyKeysNotificationListenerService getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        debouncer = new MessageDebouncer();
        // Wire debouncer output to ReplyPrecomputeManager (Build 7.0 P2)
        ReplyPrecomputeManager precomputeManager = ReplyPrecomputeManager.getInstance(this);
        debouncer.setListener(precomputeManager);
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[NLS] Service created");
        }
    }

    /**
     * Static accessor for P2 to register the debouncer listener.
     */
    private static MessageDebouncer.BatchListener externalListener;

    public static void setBatchListener(MessageDebouncer.BatchListener listener) {
        externalListener = listener;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        if (debouncer != null && externalListener != null) {
            debouncer.setListener(externalListener);
        }
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[NLS] Listener connected");
        }
        // Broadcast that NLS is online
        NlsStatusBroadcaster.sendStatus(this, true);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "[NLS] Listener DISCONNECTED — attempting requestRebind");

        // requestRebind() asks Android to reconnect this listener.
        // Only works on API 24+ and only if the user hasn't revoked permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(new android.content.ComponentName(this,
                WittyKeysNotificationListenerService.class));
        }

        // Notify keyboard that NLS is offline (for graceful degradation)
        NlsStatusBroadcaster.sendStatus(this, false);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();
        if (packageName == null) return;

        // Only process messaging and social apps (social includes Instagram DMs, Discord, etc.)
        AppDetector.AppCategory category = AppDetector.categorize(packageName);
        if (category != AppDetector.AppCategory.MESSAGING
                && category != AppDetector.AppCategory.SOCIAL) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        // Try MessagingStyle first (structured data)
        NotificationCompat.MessagingStyle messagingStyle =
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);

        if (messagingStyle != null) {
            processMessagingStyle(packageName, messagingStyle);
            return;
        }

        // Fallback: parse EXTRA_MESSAGES bundle array
        Bundle extras = notification.extras;
        if (extras != null) {
            processExtras(packageName, extras);
        }
    }

    private void processMessagingStyle(String packageName,
            NotificationCompat.MessagingStyle style) {
        java.util.List<NotificationCompat.MessagingStyle.Message> messages = style.getMessages();
        if (messages == null || messages.isEmpty()) return;

        boolean isGroup = style.getConversationTitle() != null;

        // Get the "user" (self) from MessagingStyle to filter self-sent messages
        CharSequence selfName = null;
        if (style.getUser() != null && style.getUser().getName() != null) {
            selfName = style.getUser().getName();
        }

        for (NotificationCompat.MessagingStyle.Message msg : messages) {
            CharSequence senderName = (msg.getPerson() != null)
                ? msg.getPerson().getName() : null;
            CharSequence text = msg.getText();
            long timestamp = msg.getTimestamp();

            if (senderName == null) continue;

            String sender = senderName.toString();
            String messageText = MediaMessageNormalizer.normalizeIncomingText(
                    text == null ? null : text.toString(), msg.getDataMimeType());
            if (messageText == null) continue;

            // Skip self-sent messages (person matches MessagingStyle user)
            if (selfName != null && sender.equals(selfName.toString())) continue;

            // Skip if this looks like our own reply echo
            if (shouldSkipAsSelfSent(packageName, sender, messageText)) continue;

            // JourneyTracer: Start SMART_REPLY trace on incoming message
            String traceId = JourneyTracer.start(JourneyTracer.Journey.SMART_REPLY);
            JourneyTracer.setCurrentSmartReplyTrace(traceId);
            try {
                JSONObject dataIn = new JSONObject();
                dataIn.put("package", packageName);
                dataIn.put("sender_present", sender != null && !sender.isEmpty());
                dataIn.put("text_length", messageText.length());
                JourneyTracer.step(traceId, "NLS_NOTIFICATION_RECEIVED", dataIn, null,
                    "message extracted from MessagingStyle notification");
            } catch (Exception ignored) {}

            // Register contact with ConversationMatcher (always — builds contact database)
            ConversationMatcher.getInstance().registerNlsContact(packageName, sender);

            // Only process if notification is from the active keyboard app.
            // Additionally, if an active contact is already set, only update if the
            // sender matches — prevents a different chat in the same app from hijacking context.
            // Always buffer messages for overlay reply cards
            String conversationKey = packageName + "|" + sender;
            NlsMessageBuffer.getInstance().addReceivedMessage(
                conversationKey, sender, messageText);
            ReplyCache.getInstance().invalidate(conversationKey);
            project.witty.keys.app.overlay.WittyKeysOverlayService.triggerBadgeRefresh();

            // Always trigger precompute for overlay (debouncer deduplicates)
            MessageDebouncer.NlsMessage nlsMsg = new MessageDebouncer.NlsMessage(
                sender, messageText, timestamp, isGroup);
            debouncer.addMessage(packageName, sender, nlsMsg);

            String editorPkg = ConversationMatcher.getInstance().getCurrentEditorPackage();
            if (editorPkg == null || !editorPkg.equals(packageName)) {
                Log.d(TAG, "[NLS] Buffered notification metadata: package=" + packageName
                        + ", sender_present=" + (sender != null && !sender.isEmpty()));
            } else {
                ConversationMatcher.ContactMatch current =
                    ConversationMatcher.getInstance().getActiveContact();
                boolean isActiveContact = (current == null)
                    || sender.equals(current.contactName)
                    || ConversationMatcher.fuzzyMatchStatic(sender, current.contactName);

                if (isActiveContact) {
                    ConversationMatcher.getInstance().setActiveContact(
                        packageName, sender, ConversationMatcher.MatchSource.NLS_RECENCY);
                }

                Log.d(TAG, "[NLS] MessagingStyle metadata: package=" + packageName
                    + ", state=" + (isActiveContact ? "active" : "buffered")
                    + ", text_length=" + messageText.length());
            }
        }
    }

    private void processExtras(String packageName, Bundle extras) {
        // EXTRA_TEXT contains the message text
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        // EXTRA_TITLE usually contains the sender name
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);

        if (text == null || title == null) return;

        String sender = title.toString();
        String messageText = MediaMessageNormalizer.normalizeIncomingText(text.toString(), null);
        if (messageText == null) return;

        // Filter out summary notifications (e.g., "3 new messages")
        if (messageText.matches("\\d+ new messages?")) return;

        // Skip if this looks like our own reply echo
        if (shouldSkipAsSelfSent(packageName, sender, messageText)) return;

        // JourneyTracer: Start SMART_REPLY trace on incoming message (extras path)
        String traceId = JourneyTracer.start(JourneyTracer.Journey.SMART_REPLY);
        JourneyTracer.setCurrentSmartReplyTrace(traceId);
        try {
            JSONObject dataIn = new JSONObject();
            dataIn.put("package", packageName);
            dataIn.put("sender_present", sender != null && !sender.isEmpty());
            dataIn.put("text_length", messageText.length());
            JourneyTracer.step(traceId, "NLS_NOTIFICATION_RECEIVED", dataIn, null,
                "message extracted from extras fallback");
        } catch (Exception ignored) {}

        // Register contact (always — builds contact database)
        ConversationMatcher.getInstance().registerNlsContact(packageName, sender);

        // Always buffer and precompute for overlay reply cards
        String conversationKey = packageName + "|" + sender;
        NlsMessageBuffer.getInstance().addReceivedMessage(
            conversationKey, sender, messageText);
        ReplyCache.getInstance().invalidate(conversationKey);
        project.witty.keys.app.overlay.WittyKeysOverlayService.triggerBadgeRefresh();

        MessageDebouncer.NlsMessage nlsMsg = new MessageDebouncer.NlsMessage(
            sender, messageText, System.currentTimeMillis(), false);
        debouncer.addMessage(packageName, sender, nlsMsg);

        String editorPkg = ConversationMatcher.getInstance().getCurrentEditorPackage();
        if (editorPkg == null || !editorPkg.equals(packageName)) {
            Log.d(TAG, "[NLS] Buffered extras metadata: package=" + packageName
                    + ", sender_present=" + (sender != null && !sender.isEmpty()));
        } else {
            ConversationMatcher.ContactMatch current =
                ConversationMatcher.getInstance().getActiveContact();
            boolean isActiveContact = (current == null)
                || sender.equals(current.contactName)
                || ConversationMatcher.fuzzyMatchStatic(sender, current.contactName);

            if (isActiveContact) {
                ConversationMatcher.getInstance().setActiveContact(
                    packageName, sender, ConversationMatcher.MatchSource.NLS_RECENCY);
            }

            Log.d(TAG, "[NLS] Extras metadata: package=" + packageName
                + ", state=" + (isActiveContact ? "active" : "buffered")
                + ", text_length=" + messageText.length());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap,
            int reason) {
        if (sbn == null) return;

        // REASON_CLICK means user tapped the notification → opened that conversation
        if (reason == REASON_CLICK) {
            String packageName = sbn.getPackageName();
            Notification notification = sbn.getNotification();
            if (notification == null || notification.extras == null) return;

            CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title != null) {
                String contactName = title.toString();
                String conversationKey = packageName + "|" + contactName;
                ConversationMatcher.getInstance()
                    .setActiveContactFromNotificationTap(packageName, contactName, conversationKey);

                Log.d(TAG, "[NLS] Notification tap detected: package=" + packageName
                    + ", contact_present=" + (contactName != null && !contactName.isEmpty()));
            }
        }
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[NLS] Service destroyed");
        }
    }

    // ─── Self-Reply Tracking ───

    /**
     * Track that we sent a reply to this package.
     * Used to filter self-sent notification echoes from creating new tabs.
     */
    public static void trackSentReply(String conversationKey) {
        trackSentReply(conversationKey, null);
    }

    public static void trackSentReply(String conversationKey, String replyText) {
        if (conversationKey == null) return;
        String pkg = conversationKey.contains("|") ? conversationKey.split("\\|")[0] : conversationKey;
        recentlySentPackages.put(pkg, System.currentTimeMillis());
        if (replyText != null && !replyText.trim().isEmpty()) {
            recentlySentTexts.put(conversationKey, replyText.trim());
        }
        // Clean stale entries
        long now = System.currentTimeMillis();
        recentlySentPackages.entrySet().removeIf(e -> now - e.getValue() > SELF_REPLY_FILTER_WINDOW_MS);
        recentlySentTexts.keySet().removeIf(key -> {
            String pkgName = key.contains("|") ? key.split("\\|")[0] : key;
            return !recentlySentPackages.containsKey(pkgName);
        });
    }

    /**
     * Check if a sender is likely a self-sent echo (our own reply appearing in notification).
     * Returns true if we recently sent to this package AND the sender is unknown.
     */
    private static boolean shouldSkipAsSelfSent(String packageName, String sender, String messageText) {
        Long sentTime = recentlySentPackages.get(packageName);
        if (sentTime == null || System.currentTimeMillis() - sentTime > SELF_REPLY_FILTER_WINDOW_MS) {
            return false;
        }

        String recentText = recentlySentTexts.get(packageName + "|" + sender);
        if (recentText != null && messageText != null
                && recentText.equals(messageText.trim())) {
            Log.d(TAG, "[NLS] Filtering self-sent reply echo: package=" + packageName);
            return true;
        }

        // We recently sent a reply to this package — check if sender is already known
        Set<String> knownContacts = ConversationMatcher.getInstance().getKnownContacts(packageName);
        if (knownContacts != null && knownContacts.contains(sender)) {
            return false; // Known contact, not self
        }

        // Unknown sender appearing right after our reply — likely self
        Log.d(TAG, "[NLS] Filtering likely self-sent message: package=" + packageName
                + ", sender_present=" + (sender != null && !sender.isEmpty()));
        return true;
    }

    // ─── Direct Reply via RemoteInput ───

    /**
     * Send a reply via RemoteInput for the given conversation.
     * Returns true if RemoteInput was available and reply was sent.
     */
    public static RemoteInputSendResult sendDirectReply(String conversationKey, String replyText) {
        WittyKeysNotificationListenerService instance = getInstance();
        if (instance == null) return RemoteInputSendResult.failed("notification_listener_unavailable");

        StatusBarNotification[] activeNotifications;
        try {
            activeNotifications = instance.getActiveNotifications();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get active notifications: " + e.getMessage());
            return RemoteInputSendResult.failed("active_notifications_unavailable");
        }
        if (activeNotifications == null) return RemoteInputSendResult.failed("no_active_notifications");

        for (StatusBarNotification sbn : activeNotifications) {
            String contactName = extractContactName(sbn);
            if (contactName == null) continue;

            String key = sbn.getPackageName() + "|" + contactName;
            if (!conversationKey.equals(key)) continue;

            Notification.Action action = findBestReplyAction(sbn.getNotification().actions);
            if (action == null) continue;

            RemoteInput[] remoteInputs = action.getRemoteInputs();
            if (remoteInputs == null || remoteInputs.length == 0) continue;

            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putCharSequence(remoteInputs[0].getResultKey(), replyText);
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle);

            try {
                action.actionIntent.send(instance, 0, intent);
                Log.d(TAG, "Direct reply sent via RemoteInput: conversation_key_present="
                        + (conversationKey != null && !conversationKey.isEmpty()));
                return RemoteInputSendResult.sent();
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "RemoteInput send failed: " + e.getMessage());
                return RemoteInputSendResult.failed("pending_intent_cancelled");
            }
        }
        return RemoteInputSendResult.failed("matching_reply_action_not_found");
    }

    private static Notification.Action findBestReplyAction(Notification.Action[] actions) {
        if (actions == null || actions.length == 0) return null;
        Notification.Action fallback = null;
        for (Notification.Action action : actions) {
            if (action == null) continue;
            RemoteInput[] remoteInputs = action.getRemoteInputs();
            if (remoteInputs == null || remoteInputs.length == 0) continue;
            if (fallback == null) fallback = action;
            CharSequence title = action.title;
            if (title != null) {
                String lower = title.toString().toLowerCase();
                if (lower.contains("reply") || lower.contains("respond") || lower.contains("send")) {
                    return action;
                }
            }
        }
        return fallback;
    }

    /**
     * Extract contact name from a StatusBarNotification.
     */
    private static String extractContactName(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return null;
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return null;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        return title != null ? title.toString() : null;
    }

    public static class RemoteInputSendResult {
        public final boolean sent;
        public final String reason;

        private RemoteInputSendResult(boolean sent, String reason) {
            this.sent = sent;
            this.reason = reason;
        }

        static RemoteInputSendResult sent() {
            return new RemoteInputSendResult(true, "sent");
        }

        static RemoteInputSendResult failed(String reason) {
            return new RemoteInputSendResult(false, reason);
        }
    }
}
