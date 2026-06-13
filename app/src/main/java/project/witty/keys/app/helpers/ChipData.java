package project.witty.keys.app.helpers;

/**
 * Data model for a dynamic Row 2 chip.
 * Used by ActionTracker.buildDynamicRow2() and SmartAssistantBarManager.
 */
public class ChipData {

    public enum TapAction {
        APPLY_TONE_DIRECT,   // Apply this specific tone immediately (skip picker)
        TRANSLATE_DIRECT,     // Translate to this specific language immediately (skip picker)
        GRAMMAR_DIRECT,       // One-tap grammar fix
        RERUN_CUSTOM,         // Re-run a saved custom prompt
        OPEN_TONE_PICKER,     // Open the full tone picker (default when no history)
        OPEN_LANG_PICKER,     // Open the full language picker
        OPEN_CUSTOM_MODE,     // Open custom prompt input
        EXPAND_FULL_PANEL,    // Expand to show all actions (+ More)
        CLIPBOARD_ACTION      // Paste clipboard + offer AI actions (handled in S2-CLIP)
    }

    public final String actionType;   // "tone", "translate", "grammar", "custom", "clipboard", "more"
    public final String parameter;     // Specific value (e.g., "casual", "hi", custom prompt text)
    public final String emoji;         // Display emoji
    public final String label;         // Display text
    public final TapAction tapAction;  // What happens on tap

    public ChipData(String actionType, String parameter, String emoji, String label, TapAction tapAction) {
        this.actionType = actionType;
        this.parameter = parameter;
        this.emoji = emoji;
        this.label = label;
        this.tapAction = tapAction;
    }

    /** Full display text for the chip (emoji + label) */
    public String getDisplayText() {
        if (emoji == null || emoji.isEmpty()) return label;
        return emoji + " " + label;
    }
}
