package project.witty.keys.keyboard.AiChat;

import java.util.UUID;

public class SystemMessage implements ChatItem {
    private final String id = UUID.randomUUID().toString();
    private final String text;

    public SystemMessage(String text) { this.text = text; }

    public String getText() { return text; }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_SYSTEM_MESSAGE; }
}