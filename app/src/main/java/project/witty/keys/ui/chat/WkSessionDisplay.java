package project.witty.keys.ui.chat;

import androidx.annotation.Nullable;

import project.witty.keys.app.context.UnifiedChatSessionManager;

public final class WkSessionDisplay {

    private static final String NEW_CHAT = "New Chat";
    private static final String[] SURFACE_PREFIXES = {
        "keyboard", "overlay", "fullscreen"
    };

    private WkSessionDisplay() {}

    public static String displayTitle(@Nullable String rawTitle) {
        if (rawTitle == null) return NEW_CHAT;
        String title = rawTitle.trim();
        if (title.isEmpty()) return NEW_CHAT;

        String cleanTitle = stripSurfacePrefix(title);
        return cleanTitle.isEmpty() ? NEW_CHAT : cleanTitle;
    }

    public static String preview(@Nullable String rawTitle, @Nullable String summary) {
        if (summary == null) return "";
        String cleanSummary = summary.trim();
        if (cleanSummary.isEmpty()) return "";
        return cleanSummary.equals(displayTitle(rawTitle)) ? "" : cleanSummary;
    }

    public static Surface surfaceFromSource(@Nullable String source) {
        if (UnifiedChatSessionManager.SOURCE_OVERLAY.equals(source)) return Surface.OVERLAY;
        if (UnifiedChatSessionManager.SOURCE_FULLSCREEN.equals(source)) return Surface.FULLSCREEN;
        return Surface.KEYBOARD;
    }

    private static String stripSurfacePrefix(String title) {
        String clean = title.trim();
        String lower = clean.toLowerCase();
        for (String prefix : SURFACE_PREFIXES) {
            if (lower.equals(prefix)) return "";
            if (lower.startsWith(prefix + " · ")) {
                return clean.substring(prefix.length() + 3).trim();
            }
            if (lower.startsWith(prefix + " - ")) {
                return clean.substring(prefix.length() + 3).trim();
            }
            if (lower.startsWith(prefix + ": ")) {
                return clean.substring(prefix.length() + 2).trim();
            }
            if (lower.startsWith("[" + prefix + "] ")) {
                return clean.substring(prefix.length() + 3).trim();
            }
        }
        return clean;
    }
}
