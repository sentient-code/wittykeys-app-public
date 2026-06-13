package project.witty.keys.app.entities;

import java.util.Collections;
import java.util.Map;

/**
 * This class holds the context we determine on the device *before* any API call.
 */
public class AppContext {
    public final String appType; // e.g., "Dating", "Chat", "Social", "Mail", "Other"
    public final String viewType; // e.g., "Profile", "Chat", "Story", "Post", "Other"
    public final Map<String, String> extractedText; // Optional extra data

    public AppContext(String appType, String viewType, Map<String, String> extractedText) {
        this.appType = appType;
        this.viewType = viewType;
        this.extractedText = extractedText != null ? extractedText : Collections.emptyMap();
    }

    public AppContext(String appType, String viewType) {
        this(appType, viewType, null);
    }
}