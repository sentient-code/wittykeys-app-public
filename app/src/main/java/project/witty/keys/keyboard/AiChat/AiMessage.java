package project.witty.keys.keyboard.AiChat;

import java.util.Objects;
import java.util.UUID;

public class AiMessage implements ChatItem {
    private final String id = UUID.randomUUID().toString();
    private final String markdownText;
    private final CtaType ctaType;
    private final long timestamp;

    public AiMessage(String markdownText, CtaType ctaType) {
        this.markdownText = markdownText;
        this.ctaType = ctaType;
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor with explicit timestamp (for deserialization)
    public AiMessage(String markdownText, CtaType ctaType, long timestamp) {
        this.markdownText = markdownText;
        this.ctaType = ctaType;
        this.timestamp = timestamp;
    }

    public String getMarkdownText() { return markdownText; }
    public CtaType getCtaType() { return ctaType; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_AI_MESSAGE; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiMessage aiMessage = (AiMessage) o;
        return id.equals(aiMessage.id) && markdownText.equals(aiMessage.markdownText) && ctaType == aiMessage.ctaType;
    }

    @Override
    public int hashCode() { return Objects.hash(id, markdownText, ctaType); }
}
