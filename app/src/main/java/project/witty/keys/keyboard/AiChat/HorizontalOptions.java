package project.witty.keys.keyboard.AiChat;

import java.util.List;
import java.util.UUID;

public class HorizontalOptions implements ChatItem {
    private final String id = UUID.randomUUID().toString();
    private final String title;
    private final List<String> options;
    private final OptionsType type;

    public HorizontalOptions(String title, List<String> options, OptionsType type) {
        this.title = title;
        this.options = options;
        this.type = type;
    }

    public String getTitle() {
        return title;
    }
    public List<String> getOptions() {
        return options;
    }
    public OptionsType getType() {
        return type;
    }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_HORIZONTAL_OPTIONS; }
}