package project.witty.keys.keyboard.AiChat;

import java.util.UUID;

/**
 * Chat item representing a session resumption banner.
 * Shows "Session resumed · N earlier messages loaded".
 * Rendered by ChatAdapter.SessionBannerViewHolder.
 *
 * Maps to AC13 (ac-session-resumed) golden state.
 */
public class SessionBannerMessage implements ChatItem {

    private final String id = UUID.randomUUID().toString();
    private final int messageCount;       // Number of restored messages
    private final long timestamp;

    public SessionBannerMessage(int messageCount) {
        this.messageCount = messageCount;
        this.timestamp = System.currentTimeMillis();
    }

    public int getMessageCount() { return messageCount; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_SESSION_BANNER; }
}
