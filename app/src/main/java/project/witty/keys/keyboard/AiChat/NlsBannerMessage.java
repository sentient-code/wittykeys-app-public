package project.witty.keys.keyboard.AiChat;

import java.util.List;
import java.util.UUID;

/**
 * Chat item representing an NLS (notification) context banner.
 * Shows recent messages from a contact, injected as conversation context.
 * Rendered by ChatAdapter.NlsBannerViewHolder.
 *
 * Maps to AC12 (ac-nls-context) golden state.
 */
public class NlsBannerMessage implements ChatItem {

    private final String id = UUID.randomUUID().toString();
    private final String contactName;
    private final List<NlsEntry> messages;
    private final long timestamp;

    public NlsBannerMessage(String contactName, List<NlsEntry> messages) {
        this.contactName = contactName;
        this.messages = messages;
        this.timestamp = System.currentTimeMillis();
    }

    public String getContactName() { return contactName; }
    public List<NlsEntry> getMessages() { return messages; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_NLS_BANNER; }

    /**
     * A single notification message entry (sender + text).
     */
    public static class NlsEntry {
        private final String sender;
        private final String text;

        public NlsEntry(String sender, String text) {
            this.sender = sender;
            this.text = text;
        }

        public String getSender() { return sender; }
        public String getText() { return text; }
    }
}
