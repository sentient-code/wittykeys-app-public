package project.witty.keys.keyboard.AiChat;


public class CategoryOption {
    private final int iconRes;
    private final String title;
    // We'll use this action string to identify what the user clicked
    private final String action;

    public CategoryOption(int iconRes, String title, String action) {
        this.iconRes = iconRes;
        this.title = title;
        this.action = action;
    }

    public int getIconRes() {
        return iconRes;
    }
    public String getTitle() {
        return title;
    }
    public String getAction() {
        return action;
    }
}