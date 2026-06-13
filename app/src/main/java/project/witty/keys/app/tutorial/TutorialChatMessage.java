package project.witty.keys.app.tutorial;

/**
 * Chat message model for tutorial conversation
 * Supports different message types for bot, user, and action buttons
 */
public class TutorialChatMessage {

    public enum MessageType {
        BOT_MESSAGE,      // Instructions from bot
        USER_MESSAGE,     // User's typed messages
        BOT_CELEBRATION,  // Appreciation messages
        ACTION_BUTTON     // Inline action buttons (Enable Keyboard, etc.)
    }

    private String text;
    private MessageType type;
    private long timestamp;
    private String actionButtonText;    // For ACTION_BUTTON type
    private String actionId;            // Identifier for button action
    private boolean isAnimated;         // Whether message has been animated in
    private String senderName;          // [E2E TEST SUPPORT] Sender name for accessibility

    // Constructor for text messages
    public TutorialChatMessage(String text, MessageType type) {
        this.text = text;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.isAnimated = false;
    }

    // Constructor for action buttons
    public TutorialChatMessage(String buttonText, String actionId) {
        this.type = MessageType.ACTION_BUTTON;
        this.actionButtonText = buttonText;
        this.actionId = actionId;
        this.timestamp = System.currentTimeMillis();
        this.isAnimated = false;
    }

    // Getters
    public String getText() { return text; }
    public MessageType getType() { return type; }
    public long getTimestamp() { return timestamp; }
    public String getActionButtonText() { return actionButtonText; }
    public String getActionId() { return actionId; }
    public boolean isAnimated() { return isAnimated; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    // Setters
    public void setAnimated(boolean animated) { this.isAnimated = animated; }

    /**
     * Get formatted timestamp (e.g., "3:15 PM")
     */
    public String getFormattedTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}