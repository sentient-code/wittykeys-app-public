package project.witty.keys.app.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import project.witty.keys.app.context.UnifiedChatSessionManager;
import project.witty.keys.keyboard.AiChat.ChatItem;

/**
 * AiChatSession — Overlay screenshot chat session model.
 * Build 7.1 MVP.
 *
 * Tracks a single screenshot AI conversation with persistent history.
 * @deprecated Use UnifiedChatSessionManager + Room ChatSession instead. Will be removed in Build 7.2.
 */
@Deprecated
public class AiChatSession {
    public final String sessionId;
    public String title;
    public String lastPreview;
    public long lastUpdated;
    public int messageCount;
    public long roomSessionId; // Maps to Room DB session ID for cross-surface sharing
    public String source;
    public List<ChatItem> messages;
    public List<String> screenshotPaths;

    public AiChatSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.lastUpdated = System.currentTimeMillis();
        this.messages = new ArrayList<>();
        this.screenshotPaths = new ArrayList<>();
        this.messageCount = 0;
        this.source = UnifiedChatSessionManager.SOURCE_OVERLAY;
    }

    public void addScreenshot(String path) {
        if (path != null) {
            screenshotPaths.add(path);
        }
    }

    public void updatePreview(String text) {
        if (text != null && text.length() > 60) {
            this.lastPreview = text.substring(0, 57) + "...";
        } else {
            this.lastPreview = text;
        }
        this.lastUpdated = System.currentTimeMillis();
    }
}
