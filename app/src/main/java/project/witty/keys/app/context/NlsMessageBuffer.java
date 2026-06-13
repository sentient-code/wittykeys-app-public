package project.witty.keys.app.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

/**
 * NlsMessageBuffer — Simple ring buffer for NLS messages.
 * Build 7.0 Phase 4.
 *
 * WittyKeysNotificationListenerService writes messages here.
 * UnifiedAiView reads them when AI Chat opens to inject NLS context.
 *
 * Stores last MAX_PER_CONVERSATION messages per conversationKey.
 */
public class NlsMessageBuffer {

    public static final long OPEN_CONVERSATION_TTL_MS = 2 * 60 * 60 * 1000L;
    private static final int MAX_PER_CONVERSATION = 20;

    private static NlsMessageBuffer instance;

    // conversationKey → recent messages
    private final Map<String, List<BufferedMessage>> buffer = new LinkedHashMap<>();
    private Long nowForTest;

    private NlsMessageBuffer() {}

    public static synchronized NlsMessageBuffer getInstance() {
        if (instance == null) {
            instance = new NlsMessageBuffer();
        }
        return instance;
    }

    /**
     * Add a message to the buffer.
     * Called by WittyKeysNotificationListenerService or MessageDebouncer.
     */
    public synchronized void addMessage(String conversationKey, String sender, String text) {
        addReceivedMessage(conversationKey, sender, text);
    }

    public synchronized void addReceivedMessage(String conversationKey, String sender, String text) {
        addMessageInternal(conversationKey, sender, text, false);
    }

    public synchronized void addSentMessage(String conversationKey, String text) {
        addMessageInternal(conversationKey, "You", text, true);
    }

    private void addMessageInternal(String conversationKey, String sender, String text, boolean isSent) {
        if (conversationKey == null || conversationKey.trim().isEmpty()
                || text == null || text.trim().isEmpty()) {
            return;
        }

        List<BufferedMessage> messages = buffer.get(conversationKey);
        if (messages == null) {
            messages = new ArrayList<>();
            buffer.put(conversationKey, messages);
        }

        // Deduplicate: skip if this exact message already exists
        BufferedMessage entry = new BufferedMessage(
                conversationKey,
                sender != null && !sender.trim().isEmpty() ? sender : "Unknown",
                text,
                isSent,
                now());
        if (!isSent && containsDisplayText(messages, entry.displayText)) {
            return;
        }

        messages.add(entry);

        // Trim to max size
        while (messages.size() > MAX_PER_CONVERSATION) {
            messages.remove(0);
        }
    }

    private boolean containsDisplayText(List<BufferedMessage> messages, String displayText) {
        for (BufferedMessage message : messages) {
            if (message.displayText.equals(displayText)) return true;
        }
        return false;
    }

    /**
     * Get recent messages for a conversation.
     * Returns up to `limit` messages, most recent last.
     */
    public synchronized List<String> getRecentMessages(String conversationKey, int limit) {
        pruneExpired();
        List<BufferedMessage> messages = buffer.get(conversationKey);
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        int start = Math.max(0, messages.size() - limit);
        List<String> result = new ArrayList<>();
        for (BufferedMessage message : messages.subList(start, messages.size())) {
            result.add(message.displayText);
        }
        return result;
    }

    public synchronized List<BufferedMessage> getRecentConversationMessages(String conversationKey, int limit) {
        pruneExpired();
        List<BufferedMessage> messages = buffer.get(conversationKey);
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    public synchronized List<ConversationSnapshot> getOpenConversations() {
        pruneExpired();
        List<ConversationSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, List<BufferedMessage>> entry : buffer.entrySet()) {
            ConversationSnapshot snapshot = openConversation(entry.getKey());
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        snapshots.sort(Comparator.comparingLong((ConversationSnapshot s) -> s.updatedAt).reversed());
        return snapshots;
    }

    public synchronized ConversationSnapshot openConversation(String conversationKey) {
        pruneExpired();
        List<BufferedMessage> messages = buffer.get(conversationKey);
        if (messages == null || messages.isEmpty()) return null;
        int separator = conversationKey.indexOf('|');
        if (separator <= 0 || separator >= conversationKey.length() - 1) return null;
        return new ConversationSnapshot(
                conversationKey,
                conversationKey.substring(0, separator),
                conversationKey.substring(separator + 1),
                new ArrayList<>(messages),
                latestTimestamp(messages),
                messages.get(messages.size() - 1).isSent,
                latestIncomingId(messages));
    }

    /**
     * Clear all messages for a conversation.
     */
    public synchronized void clear(String conversationKey) {
        buffer.remove(conversationKey);
    }

    /**
     * Clear entire buffer.
     */
    public synchronized void clearAll() {
        buffer.clear();
    }

    public synchronized void setNowForTest(Long fixedNowMs) {
        nowForTest = fixedNowMs;
    }

    private void pruneExpired() {
        long now = now();
        buffer.entrySet().removeIf(entry -> {
            List<BufferedMessage> messages = entry.getValue();
            return messages == null || messages.isEmpty()
                    || now - latestTimestamp(messages) > OPEN_CONVERSATION_TTL_MS;
        });
    }

    private long latestTimestamp(List<BufferedMessage> messages) {
        long latest = 0L;
        for (BufferedMessage message : messages) {
            latest = Math.max(latest, message.timestamp);
        }
        return latest;
    }

    private String latestIncomingId(List<BufferedMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            BufferedMessage message = messages.get(i);
            if (!message.isSent) return message.stableId;
        }
        return null;
    }

    private long now() {
        return nowForTest != null ? nowForTest : System.currentTimeMillis();
    }

    public static class BufferedMessage {
        public final String sender;
        public final String text;
        public final String displayText;
        public final boolean isSent;
        public final long timestamp;
        public final String stableId;

        BufferedMessage(String conversationKey, String sender, String text, boolean isSent, long timestamp) {
            this.sender = sender;
            this.text = text;
            this.isSent = isSent;
            this.timestamp = timestamp;
            this.displayText = sender + ": " + text;
            this.stableId = String.valueOf((conversationKey + "|" + timestamp + "|" + this.sender + "|" + text).hashCode());
        }
    }

    public static class ConversationSnapshot {
        public final String conversationKey;
        public final String packageName;
        public final String contactName;
        public final List<BufferedMessage> messages;
        public final long updatedAt;
        public final boolean latestMessageSentByUser;
        public final String latestIncomingId;

        ConversationSnapshot(String conversationKey, String packageName, String contactName,
                             List<BufferedMessage> messages, long updatedAt,
                             boolean latestMessageSentByUser, String latestIncomingId) {
            this.conversationKey = conversationKey;
            this.packageName = packageName;
            this.contactName = contactName;
            this.messages = messages;
            this.updatedAt = updatedAt;
            this.latestMessageSentByUser = latestMessageSentByUser;
            this.latestIncomingId = latestIncomingId;
        }
    }
}
