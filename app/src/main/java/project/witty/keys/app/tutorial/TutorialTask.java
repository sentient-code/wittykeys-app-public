package project.witty.keys.app.tutorial;

/**
 * Tutorial Task Enum
 * Defines each step in the interactive tutorial
 */
public enum TutorialTask {

    ENABLE_KEYBOARD(
            1,
            "Enable WittyKeys Keyboard",
            "Let's set up your AI keyboard! Tap the button below to enable WittyKeys in your device settings.",
            null,
            false
    ),

    AI_CHAT_TASK(
            2,
            "Try AI Chat",
            "Type a message, then tap the 'AI Chat' button to improve it with AI!",
            "AI_CHAT",
            true
    ),

    READ_SCREEN_TASK(
            3,
            "Use Read Screen",
            "Tap 'Read Screen' to get smart suggestions based on what's on your screen!",
            "READ_SCREEN",
            true
    ),

    TONALITY_TASK(
            4,
            "Adjust Tone",
            "Type a message and use 'Tonality' to change its tone - make it more professional, casual, or friendly!",
            "TONALITY",
            true
    ),

    GRAMMAR_TASK(
            5,
            "Fix Grammar",
            "Type a message with errors and tap 'Grammar Correction' to fix it instantly!",
            "GRAMMAR",
            true
    ),

    TOKEN_EXPLANATION(
            6,
            "Daily AI Actions",
            "Learn how daily AI actions work and start using WittyKeys!",
            null,
            false
    );

    private final int stepNumber;
    private final String title;
    private final String instructions;
    private final String requiredButton; // Which button needs to be clicked
    private final boolean requiresKeyboard; // Does this step need keyboard interaction?

    TutorialTask(int stepNumber, String title, String instructions, String requiredButton, boolean requiresKeyboard) {
        this.stepNumber = stepNumber;
        this.title = title;
        this.instructions = instructions;
        this.requiredButton = requiredButton;
        this.requiresKeyboard = requiresKeyboard;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getRequiredButton() {
        return requiredButton;
    }

    public boolean requiresKeyboard() {
        return requiresKeyboard;
    }

    public TutorialTask getNextTask() {
        TutorialTask[] tasks = TutorialTask.values();
        int currentIndex = this.ordinal();
        if (currentIndex < tasks.length - 1) {
            return tasks[currentIndex + 1];
        }
        return null; // Tutorial complete
    }

    public boolean isLastTask() {
        return this == TOKEN_EXPLANATION;
    }

    // Get detailed instructions for each task
    public String getDetailedInstructions() {
        switch (this) {
            case ENABLE_KEYBOARD:
                return "WittyKeys needs to be enabled as an input method.\n\n" +
                        "1. Tap 'Enable Keyboard' below\n" +
                        "2. Find 'WittyKeys' in the list\n" +
                        "3. Toggle it ON\n" +
                        "4. Come back here!";

            case AI_CHAT_TASK:
                return "Let's try your first AI feature!\n\n" +
                        "1. Type any message in the box below\n" +
                        "2. Tap the 'AI Chat' button (🤖 icon)\n" +
                        "3. AI will improve your message\n" +
                        "4. Tap 'Apply' to use it!";

            case READ_SCREEN_TASK:
                return "WittyKeys can read your screen and suggest replies!\n\n" +
                        "1. Look at the conversation above\n" +
                        "2. Tap the 'Read Screen' button (📖 icon)\n" +
                        "3. Choose a suggestion type\n" +
                        "4. AI will create the perfect response!";

            case TONALITY_TASK:
                return "Change your message tone with one tap!\n\n" +
                        "1. Type a simple message\n" +
                        "2. Tap the 'Tonality' button (🎭 icon)\n" +
                        "3. Select a tone (Professional, Casual, etc.)\n" +
                        "4. See your message transform!";

            case GRAMMAR_TASK:
                return "Never worry about typos again!\n\n" +
                        "1. Type a message with mistakes\n" +
                        "2. Tap 'Grammar Correction' (📝 icon)\n" +
                        "3. AI will fix all errors\n" +
                        "4. Apply the corrected text!";

            case TOKEN_EXPLANATION:
                return "You have 20 free AI credits/day to start.\n\n" +
                        "• Reply, Scan, Tone, Grammar, Translate, and AI Chat spend AI credits\n" +
                        "• The counter resets daily\n" +
                        "• WittyKeys Plus raises your daily AI credits\n\n" +
                        "Ready to experience the future of typing?";

            default:
                return instructions;
        }
    }

    // Get example text for practice
    public String getExampleText() {
        switch (this) {
            case AI_CHAT_TASK:
                return "hey can u help me with something";

            case TONALITY_TASK:
                return "I need this done asap";

            case GRAMMAR_TASK:
                return "i dont no how too do this their are to many options";

            default:
                return "";
        }
    }

    // Get mock conversation for Read Screen task
    public String[] getMockConversation() {
        if (this == READ_SCREEN_TASK) {
            return new String[] {
                    "Hey! How was your weekend?",
                    "It was great! Went hiking and tried a new restaurant.",
                    "That sounds amazing! Which restaurant?",
                    "A cozy Italian place downtown. The pasta was incredible!",
                    "Oh nice! I've been looking for good Italian food. What's it called?"
            };
        }
        return new String[0];
    }
}
