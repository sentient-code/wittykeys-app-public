package project.witty.keys.keyboard.AiChat;
import java.util.UUID;

public class ErrorMessage implements ChatItem {
    private final String id = UUID.randomUUID().toString();
    private final String text;
    private final Runnable retryAction; // Runnable is a perfect Java functional interface for this

    public ErrorMessage(String text, Runnable retryAction) {
        this.text = text;
        this.retryAction = retryAction;
    }

    public String getText() { return text; }
    public Runnable getRetryAction() { return retryAction; }

    @Override
    public String getId() { return id; }

    @Override
    public int getViewType() { return VIEW_TYPE_ERROR_MESSAGE; }
}