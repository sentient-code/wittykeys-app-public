package project.witty.keys.keyboard.AiChat;

import java.util.UUID;

public interface ChatItem {
    // View type constants for our RecyclerView Adapter
    int VIEW_TYPE_USER_MESSAGE = 1;
    int VIEW_TYPE_AI_MESSAGE = 2;
    int VIEW_TYPE_LOADING = 3;
    int VIEW_TYPE_HORIZONTAL_OPTIONS = 4;
    int VIEW_TYPE_GRID_OPTIONS = 5;
    int VIEW_TYPE_METADATA_CARD = 6;
    int VIEW_TYPE_SYSTEM_MESSAGE = 7;
    int VIEW_TYPE_ERROR_MESSAGE = 8;
    int VIEW_TYPE_SCREENSHOT_MESSAGE = 9;
    int VIEW_TYPE_NLS_BANNER = 10;
    int VIEW_TYPE_SESSION_BANNER = 11;
    int VIEW_TYPE_ANALYZING_MESSAGE = 12;

    String getId();
    int getViewType();
}