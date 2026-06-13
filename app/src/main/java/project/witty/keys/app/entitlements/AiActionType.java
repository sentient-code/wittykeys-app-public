package project.witty.keys.app.entitlements;

public enum AiActionType {
    SHORT_TEXT(1, true, "Short text action"),
    AI_CHAT(2, true, "AI chat"),
    SCREEN_AI(4, true, "Ask AI about screen"),
    BACKGROUND_PRECOMPUTE(1, false, "Background smart replies");

    public final int cost;
    public final boolean freeTierAllowed;
    public final String label;

    AiActionType(int cost, boolean freeTierAllowed, String label) {
        this.cost = Math.max(1, cost);
        this.freeTierAllowed = freeTierAllowed;
        this.label = label;
    }
}
