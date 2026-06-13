package project.witty.keys.app.context;

import java.util.ArrayList;
import java.util.List;

public final class ConversationReplyStateBuilder {
    private ConversationReplyStateBuilder() {}

    public static ConversationReplyState fromSnapshot(
            NlsMessageBuffer.ConversationSnapshot snapshot,
            ReplyCache cache) {
        if (snapshot == null) {
            return ConversationReplyState.blocked(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    ConversationReplyState.BlockedReason.NO_CONTEXT,
                    "No messages yet.",
                    false);
        }

        List<ConversationReplyState.Message> messages = new ArrayList<>();
        for (NlsMessageBuffer.BufferedMessage msg : snapshot.messages) {
            messages.add(new ConversationReplyState.Message(
                    msg.sender,
                    msg.text,
                    msg.isSent,
                    msg.stableId));
        }

        List<String> suggestions = cache != null
                ? cache.get(snapshot.conversationKey, snapshot.latestIncomingId)
                : null;
        return ConversationReplyState.fromMessages(
                snapshot.conversationKey,
                snapshot.packageName,
                snapshot.contactName,
                messages,
                suggestions,
                snapshot.latestIncomingId);
    }
}
