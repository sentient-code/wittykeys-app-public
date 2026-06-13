package project.witty.keys.keyboard.AiChat;

public class Loading implements ChatItem {
    // Create a single, reusable instance
    public static final Loading INSTANCE = new Loading();

    // Private constructor to prevent instantiation
    private Loading() {}

    @Override
    public String getId() {
        // A singleton can have a fixed ID
        return "loading_singleton";
    }

    @Override
    public int getViewType() {
        return VIEW_TYPE_LOADING;
    }
}