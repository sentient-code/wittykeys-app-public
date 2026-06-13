package project.witty.keys.keyboard.AiChat;

import java.util.UUID;
import project.witty.keys.app.context.ScreenContext;

public class MetadataCard implements ChatItem {
    private final String id = UUID.randomUUID().toString();
    private final ScreenContext context;
    private boolean isExpanded;
    private String generatedDetails;
    private String emotion;
    private String timeActive;
    private int messageCount;

    public String getGeneratedDetails() { return generatedDetails; }
    public void setGeneratedDetails(String details) { this.generatedDetails = details; }

    public MetadataCard(ScreenContext context) {
        this.context = context;
        this.isExpanded = false;
    }

    public ScreenContext getContext() { return context; }
    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { isExpanded = expanded; }

    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }

    public String getTimeActive() { return timeActive; }
    public void setTimeActive(String timeActive) { this.timeActive = timeActive; }

    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_METADATA_CARD; }
}
