package project.witty.keys.keyboard.AiChat;

import java.util.List;
import java.util.UUID;

public class GridOptions implements ChatItem {
    private final String id = UUID.randomUUID().toString();
    private final String title;
    private final List<CategoryOption> options;

    public GridOptions(String title, List<CategoryOption> options) {
        this.title = title;
        this.options = options;
    }

    public String getTitle() {
        return title;
    }
    public List<CategoryOption> getOptions() {
        return options;
    }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_GRID_OPTIONS; }
}