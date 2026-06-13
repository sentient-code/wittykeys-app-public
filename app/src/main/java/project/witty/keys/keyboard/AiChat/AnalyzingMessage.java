package project.witty.keys.keyboard.AiChat;

import java.util.UUID;

/**
 * Chat item representing "Analyzing screenshot..." loading state.
 * Shown as an in-chat system message while ScreenshotAnalyzer processes.
 * Rendered by ChatAdapter.AnalyzingViewHolder.
 *
 * Shared between compact UnifiedAiView (AC14) and full-screen AiChatActivity (FS10).
 * Replaced by ScreenshotMessage + AI response on completion.
 */
public class AnalyzingMessage implements ChatItem {

    private final String id;
    private final long timestamp;

    public AnalyzingMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() { return timestamp; }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_ANALYZING_MESSAGE; }
}
