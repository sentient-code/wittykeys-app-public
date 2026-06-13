package project.witty.keys.app.tutorial;

public enum OnboardingStep {
    WELCOME(OnboardingActivity.STATE_WELCOME, 0, "Welcome", "Value-first intro"),
    DEMO_REPLY(OnboardingActivity.STATE_DEMO_REPLY, 1, "Smart Reply", "Preview reply chips"),
    DEMO_SCAN(OnboardingActivity.STATE_DEMO_SCAN, 2, "Scan", "Preview screenshot AI"),
    ENABLE_KEYBOARD(OnboardingActivity.STATE_ENABLE_KEYBOARD, 3, "Enable Keyboard", "Set WittyKeys as your keyboard"),
    KEYBOARD_DONE(OnboardingActivity.STATE_KEYBOARD_DONE, 3, "Keyboard Enabled", ""),
    ENABLE_NLS(OnboardingActivity.STATE_NLS_EXPLAIN, 4, "Smart Replies", "Enable notification access"),
    NLS_GRANTED(OnboardingActivity.STATE_NLS_GRANTED, 4, "Smart Replies Enabled", ""),
    NLS_SKIPPED(OnboardingActivity.STATE_NLS_SKIPPED, 4, "Smart Replies Skipped", ""),
    OVERLAY_INTRO(OnboardingActivity.STATE_OVERLAY_INTRO, 0, "Overlay", "Explain optional overlay permissions"),
    COMPLETE(OnboardingActivity.STATE_COMPLETE, 0, "All Set", "");

    private final String debugState;
    private final int stepNumber;
    private final String title;
    private final String description;

    OnboardingStep(String debugState, int stepNumber, String title, String description) {
        this.debugState = debugState;
        this.stepNumber = stepNumber;
        this.title = title;
        this.description = description;
    }

    public String getDebugState() { return debugState; }
    public int getStepNumber() { return stepNumber; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }

    public static OnboardingStep fromDebugState(String state) {
        for (OnboardingStep step : values()) {
            if (step.debugState.equals(state)) return step;
        }
        return WELCOME;
    }
}
