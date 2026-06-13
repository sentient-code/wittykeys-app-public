package project.witty.keys.keyboard.AiChat;

import java.util.UUID;

/**
 * Chat item representing an inline screenshot capture with analysis.
 * Rendered by ChatAdapter.ScreenshotViewHolder.
 *
 * Maps to AC11 (ac-screenshot-inline) golden state.
 */
public class ScreenshotMessage implements ChatItem {

    private final String id = UUID.randomUUID().toString();
    private final String imagePath;       // Local file path to screenshot
    private final String analysisText;    // AI analysis of the screenshot
    private final int imageWidth;         // Screenshot width in pixels
    private final int imageHeight;        // Screenshot height in pixels
    private final long timestamp;

    public ScreenshotMessage(String imagePath, String analysisText,
                             int imageWidth, int imageHeight) {
        this.imagePath = imagePath;
        this.analysisText = analysisText;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.timestamp = System.currentTimeMillis();
    }

    public String getImagePath() { return imagePath; }
    public String getAnalysisText() { return analysisText; }
    public int getImageWidth() { return imageWidth; }
    public int getImageHeight() { return imageHeight; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_SCREENSHOT_MESSAGE; }
}
