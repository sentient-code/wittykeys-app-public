package project.witty.keys.keyboard.AiChat;

import java.util.Objects;
import java.util.UUID;

public class UserMessage implements ChatItem {
    private final String id = UUID.randomUUID().toString();
    private final String text;
    private final long timestamp;

    public UserMessage(String text) {
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor with explicit timestamp (for deserialization)
    public UserMessage(String text, long timestamp) {
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_USER_MESSAGE; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserMessage that = (UserMessage) o;
        return id.equals(that.id) && text.equals(that.text);
    }

    @Override
    public int hashCode() { return Objects.hash(id, text); }
}
