package project.witty.keys.app.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConversationReplyState {
    public enum BlockedReason {
        NONE,
        LAST_MESSAGE_IS_USER,
        NO_CONTEXT,
        QUOTA_EMPTY,
        WRONG_ACTIVE_APP,
        UNCERTAIN_CONTACT,
        PERMISSION_MISSING,
        IN_FLIGHT
    }

    public static final class Message {
        public final String sender;
        public final String text;
        public final boolean sentByUser;
        public final String stableId;

        public Message(String sender, String text, boolean sentByUser, String stableId) {
            this.sender = sender;
            this.text = text;
            this.sentByUser = sentByUser;
            this.stableId = stableId;
        }
    }

    public final String conversationKey;
    public final String packageName;
    public final String contactName;
    public final List<Message> messages;
    public final List<String> suggestions;
    public final String latestIncomingId;
    public final boolean canShowSuggestions;
    public final BlockedReason blockedReason;
    public final String statusMessage;
    public final boolean badgeEligible;

    private ConversationReplyState(
            String conversationKey,
            String packageName,
            String contactName,
            List<Message> messages,
            List<String> suggestions,
            String latestIncomingId,
            boolean canShowSuggestions,
            BlockedReason blockedReason,
            String statusMessage,
            boolean badgeEligible) {
        this.conversationKey = conversationKey;
        this.packageName = packageName;
        this.contactName = contactName;
        this.messages = Collections.unmodifiableList(messages);
        this.suggestions = Collections.unmodifiableList(suggestions);
        this.latestIncomingId = latestIncomingId;
        this.canShowSuggestions = canShowSuggestions;
        this.blockedReason = blockedReason;
        this.statusMessage = statusMessage;
        this.badgeEligible = badgeEligible;
    }

    public static ConversationReplyState fromMessages(
            String conversationKey,
            String packageName,
            String contactName,
            List<Message> messages,
            List<String> suggestions,
            String cacheIncomingId) {
        List<Message> safeMessages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        List<String> safeSuggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
        if (safeMessages.isEmpty()) {
            return blocked(
                    conversationKey,
                    packageName,
                    contactName,
                    safeMessages,
                    safeSuggestions,
                    null,
                    BlockedReason.NO_CONTEXT,
                    "No messages yet.",
                    false);
        }

        Message latest = safeMessages.get(safeMessages.size() - 1);
        String latestIncomingId = latestIncomingId(safeMessages);
        if (latest.sentByUser) {
            return blocked(
                    conversationKey,
                    packageName,
                    contactName,
                    safeMessages,
                    new ArrayList<>(),
                    latestIncomingId,
                    BlockedReason.LAST_MESSAGE_IS_USER,
                    "Sent. Waiting for their reply.",
                    false);
        }

        boolean cacheMatches = latestIncomingId != null && latestIncomingId.equals(cacheIncomingId);
        boolean canShow = cacheMatches && !safeSuggestions.isEmpty();
        return new ConversationReplyState(
                conversationKey,
                packageName,
                contactName,
                safeMessages,
                canShow ? safeSuggestions : new ArrayList<>(),
                latestIncomingId,
                canShow,
                canShow ? BlockedReason.NONE : BlockedReason.IN_FLIGHT,
                canShow ? "" : "Reply manually while AI suggestions update.",
                true);
    }

    public static ConversationReplyState blocked(
            String conversationKey,
            String packageName,
            String contactName,
            List<Message> messages,
            List<String> suggestions,
            String latestIncomingId,
            BlockedReason reason,
            String statusMessage,
            boolean badgeEligible) {
        return new ConversationReplyState(
                conversationKey,
                packageName,
                contactName,
                messages != null ? new ArrayList<>(messages) : new ArrayList<>(),
                suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>(),
                latestIncomingId,
                false,
                reason,
                statusMessage,
                badgeEligible);
    }

    private static String latestIncomingId(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (!message.sentByUser) return message.stableId;
        }
        return null;
    }
}
