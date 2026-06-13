package project.witty.keys.keyboard.AssistantViews;

import java.util.Arrays;
import java.util.List;

/**
 * MockChatScenarios - Realistic test data for SmartAssistantBar testing
 *
 * Contains 7 real-world chat scenarios covering:
 * - Different emotions (frustrated, worried, happy, angry, excited)
 * - Different languages (English, Hindi, Hinglish)
 * - Different contexts (work, friends, customer support)
 * - Different intensities (low, medium, high)
 *
 * Build 6.3 - SmartAssistantBar Revamp
 * Updated: Build 7.1 — Removed MemoryViewData dependency. Scenarios now provide
 * raw data (messages, expected replies, API responses) without MemoryViewData wrappers.
 */
public class MockChatScenarios {

    /**
     * Simple scenario data holder replacing MemoryViewData in test scenarios.
     */
    public static class ScenarioExpected {
        public final String appName;
        public final String appPackage;
        public final String senderName;
        public final String emotion;
        public final String intensity;
        public final String summary;
        public final String detectedLanguage;
        public final boolean needsTranslation;
        public final String translatedSummary;
        public final List<String> quickReplies;
        public final List<String> quickRepliesTranslated;

        public ScenarioExpected(String appName, String appPackage, String senderName,
                                String emotion, String intensity, String summary,
                                String detectedLanguage, boolean needsTranslation,
                                String translatedSummary,
                                List<String> quickReplies, List<String> quickRepliesTranslated) {
            this.appName = appName;
            this.appPackage = appPackage;
            this.senderName = senderName;
            this.emotion = emotion;
            this.intensity = intensity;
            this.summary = summary;
            this.detectedLanguage = detectedLanguage;
            this.needsTranslation = needsTranslation;
            this.translatedSummary = translatedSummary;
            this.quickReplies = quickReplies;
            this.quickRepliesTranslated = quickRepliesTranslated;
        }
    }

    // ========== SCENARIO 1: Frustrated Boss (English) ==========

    public static final String FRUSTRATED_BOSS_MESSAGES =
            "John: Hey, where's the project update?\n" +
            "John: I've been waiting for 3 days!!!\n" +
            "John: The client is really upset";

    public static final ScenarioExpected FRUSTRATED_BOSS_EXPECTED = new ScenarioExpected(
            "WhatsApp", "com.whatsapp", "John",
            "FRUSTRATED", "HIGH",
            "John is upset about the project delay. He's been waiting 3 days and the client is pressuring him.",
            "en", false, null,
            Arrays.asList(
                    "I apologize for the delay. Let me give you an update.",
                    "I understand your frustration. Working on it now.",
                    "Can we hop on a quick call to discuss?",
                    "The update will be ready in 2 hours."
            ), null
    );

    public static final String FRUSTRATED_BOSS_API_RESPONSE = "{\n" +
            "  \"emotion\": \"FRUSTRATED\",\n" +
            "  \"intensity\": \"HIGH\",\n" +
            "  \"summary\": \"John is upset about the project delay. He's been waiting 3 days and the client is pressuring him.\",\n" +
            "  \"detectedLanguage\": \"en\",\n" +
            "  \"needsTranslation\": false,\n" +
            "  \"senderName\": \"John\",\n" +
            "  \"appName\": \"WhatsApp\",\n" +
            "  \"contextType\": \"CHAT\",\n" +
            "  \"quickReplies\": [\n" +
            "    \"I apologize for the delay. Let me give you an update.\",\n" +
            "    \"I understand your frustration. Working on it now.\",\n" +
            "    \"Can we hop on a quick call to discuss?\",\n" +
            "    \"The update will be ready in 2 hours.\"\n" +
            "  ],\n" +
            "  \"recommendedActions\": [\n" +
            "    {\"label\": \"Schedule a call\", \"icon\": \"\uD83D\uDCDE\", \"type\": \"SCHEDULE_CALL\"},\n" +
            "    {\"label\": \"Share update doc\", \"icon\": \"\uD83D\uDCC4\", \"type\": \"SHARE_DOC\"}\n" +
            "  ]\n" +
            "}";

    // ========== SCENARIO 2: Worried Friend (Hindi) ==========

    public static final String WORRIED_FRIEND_HINDI_MESSAGES =
            "Amit: Kahan ho? Bahut der ho gayi.\n" +
            "Amit: Ghar pe sab theek hai na?";

    public static final ScenarioExpected WORRIED_FRIEND_HINDI_EXPECTED = new ScenarioExpected(
            "WhatsApp", "com.whatsapp", "Amit",
            "WORRIED", "MEDIUM",
            "Amit is asking where you are and if everything is okay at home. He's concerned about the late hour.",
            "hi", true,
            "Where are you? It's been very late. Is everything okay at home?",
            Arrays.asList(
                    "Main aa raha hoon, 10 min mein pahunch jaunga.",
                    "Sorry, traffic mein fas gaya."
            ),
            Arrays.asList(
                    "I'm on my way, will be there in 10 minutes.",
                    "Almost there, just 5 more minutes."
            )
    );

    public static final String WORRIED_FRIEND_HINDI_API_RESPONSE = "{\n" +
            "  \"emotion\": \"WORRIED\",\n" +
            "  \"intensity\": \"MEDIUM\",\n" +
            "  \"summary\": \"Amit is asking where you are and if everything is okay at home. He's concerned about the late hour.\",\n" +
            "  \"detectedLanguage\": \"hi\",\n" +
            "  \"needsTranslation\": true,\n" +
            "  \"translatedSummary\": \"Where are you? It's been very late. Is everything okay at home?\",\n" +
            "  \"senderName\": \"Amit\",\n" +
            "  \"appName\": \"WhatsApp\",\n" +
            "  \"contextType\": \"CHAT\",\n" +
            "  \"quickReplies\": [\n" +
            "    \"Main aa raha hoon, 10 min mein pahunch jaunga.\",\n" +
            "    \"Sorry, traffic mein fas gaya.\"\n" +
            "  ],\n" +
            "  \"quickRepliesTranslated\": [\n" +
            "    \"I'm on my way, will be there in 10 minutes.\",\n" +
            "    \"Almost there, just 5 more minutes.\"\n" +
            "  ]\n" +
            "}";

    // ========== SCENARIO 3: Casual Friend (English) ==========

    public static final String CASUAL_FRIEND_MESSAGES =
            "Mike: yo what's up?\n" +
            "Mike: wanna grab lunch today?\n" +
            "Mike: heard there's a new place downtown";

    public static final ScenarioExpected CASUAL_FRIEND_EXPECTED = new ScenarioExpected(
            "WhatsApp", "com.whatsapp", "Mike",
            "HAPPY", "LOW",
            "Mike is casually inviting you to lunch and mentioning a new restaurant downtown.",
            "en", false, null,
            Arrays.asList(
                    "Hey! Yeah sounds good \uD83D\uDC4D",
                    "Sure! What time works for you?",
                    "Let's do it! Which place?",
                    "I'm down, send me the location"
            ), null
    );

    public static final String CASUAL_FRIEND_API_RESPONSE = "{\n" +
            "  \"emotion\": \"HAPPY\",\n" +
            "  \"intensity\": \"LOW\",\n" +
            "  \"summary\": \"Mike is casually inviting you to lunch and mentioning a new restaurant downtown.\",\n" +
            "  \"detectedLanguage\": \"en\",\n" +
            "  \"needsTranslation\": false,\n" +
            "  \"senderName\": \"Mike\",\n" +
            "  \"appName\": \"WhatsApp\",\n" +
            "  \"contextType\": \"CHAT\",\n" +
            "  \"quickReplies\": [\n" +
            "    \"Hey! Yeah sounds good \uD83D\uDC4D\",\n" +
            "    \"Sure! What time works for you?\",\n" +
            "    \"Let's do it! Which place?\",\n" +
            "    \"I'm down, send me the location\"\n" +
            "  ]\n" +
            "}";

    // ========== SCENARIO 4: Formal Email Thread ==========

    public static final String FORMAL_EMAIL_MESSAGES =
            "Dear Team,\n" +
            "Please find attached the quarterly report.\n" +
            "Kindly review and provide feedback by EOD.\n" +
            "Best regards, Sarah";

    public static final ScenarioExpected FORMAL_EMAIL_EXPECTED = new ScenarioExpected(
            "Gmail", "com.google.android.gm", "Sarah",
            "NEUTRAL", "LOW",
            "Sarah shared the quarterly report and is requesting feedback by end of day.",
            "en", false, null,
            Arrays.asList(
                    "Thank you, Sarah. I'll review and get back to you shortly.",
                    "Received. Will provide feedback by EOD.",
                    "Thanks for sharing. I have a few questions - can we discuss?",
                    "Acknowledged. Will review and revert."
            ), null
    );

    public static final String FORMAL_EMAIL_API_RESPONSE = "{\n" +
            "  \"emotion\": \"NEUTRAL\",\n" +
            "  \"intensity\": \"LOW\",\n" +
            "  \"summary\": \"Sarah shared the quarterly report and is requesting feedback by end of day.\",\n" +
            "  \"detectedLanguage\": \"en\",\n" +
            "  \"needsTranslation\": false,\n" +
            "  \"senderName\": \"Sarah\",\n" +
            "  \"appName\": \"Gmail\",\n" +
            "  \"contextType\": \"EMAIL\",\n" +
            "  \"quickReplies\": [\n" +
            "    \"Thank you, Sarah. I'll review and get back to you shortly.\",\n" +
            "    \"Received. Will provide feedback by EOD.\",\n" +
            "    \"Thanks for sharing. I have a few questions - can we discuss?\",\n" +
            "    \"Acknowledged. Will review and revert.\"\n" +
            "  ]\n" +
            "}";

    // ========== SCENARIO 5: Mixed Language (Hinglish) ==========

    public static final String HINGLISH_CHAT_MESSAGES =
            "Priya: Hey, meeting cancel ho gayi kya?\n" +
            "Priya: Please confirm ASAP";

    public static final ScenarioExpected HINGLISH_EXPECTED = new ScenarioExpected(
            "WhatsApp", "com.whatsapp", "Priya",
            "NEUTRAL", "MEDIUM",
            "Priya is asking if the meeting was cancelled and needs confirmation urgently.",
            "hi-en", false, null,
            Arrays.asList(
                    "Haan, cancel ho gayi. Will reschedule soon.",
                    "No, it's still on. See you there!",
                    "Let me check and confirm in 5 mins",
                    "Yes cancelled. New time tomorrow 3pm?"
            ), null
    );

    public static final String HINGLISH_API_RESPONSE = "{\n" +
            "  \"emotion\": \"NEUTRAL\",\n" +
            "  \"intensity\": \"MEDIUM\",\n" +
            "  \"summary\": \"Priya is asking if the meeting was cancelled and needs confirmation urgently.\",\n" +
            "  \"detectedLanguage\": \"hi-en\",\n" +
            "  \"needsTranslation\": false,\n" +
            "  \"senderName\": \"Priya\",\n" +
            "  \"appName\": \"WhatsApp\",\n" +
            "  \"contextType\": \"CHAT\",\n" +
            "  \"quickReplies\": [\n" +
            "    \"Haan, cancel ho gayi. Will reschedule soon.\",\n" +
            "    \"No, it's still on. See you there!\",\n" +
            "    \"Let me check and confirm in 5 mins\",\n" +
            "    \"Yes cancelled. New time tomorrow 3pm?\"\n" +
            "  ]\n" +
            "}";

    // ========== SCENARIO 6: Angry Customer Support ==========

    public static final String ANGRY_CUSTOMER_MESSAGES =
            "Customer: This is ridiculous! I've been waiting 2 weeks!\n" +
            "Customer: I want a refund NOW\n" +
            "Customer: Your service is terrible";

    public static final ScenarioExpected ANGRY_CUSTOMER_EXPECTED = new ScenarioExpected(
            "Zendesk", "com.zendesk.iris", "Customer",
            "ANGRY", "HIGH",
            "Customer is extremely upset about a 2-week delay and demanding an immediate refund. They're criticizing the service quality.",
            "en", false, null,
            Arrays.asList(
                    "I sincerely apologize for the delay. Let me expedite this immediately.",
                    "I understand your frustration. I'm escalating this to our priority team right now.",
                    "I'm so sorry for this experience. I'll process your refund immediately.",
                    "This is unacceptable and I take full responsibility. How can I make this right?"
            ), null
    );

    public static final String ANGRY_CUSTOMER_API_RESPONSE = "{\n" +
            "  \"emotion\": \"ANGRY\",\n" +
            "  \"intensity\": \"HIGH\",\n" +
            "  \"summary\": \"Customer is extremely upset about a 2-week delay and demanding an immediate refund. They're criticizing the service quality.\",\n" +
            "  \"detectedLanguage\": \"en\",\n" +
            "  \"needsTranslation\": false,\n" +
            "  \"senderName\": \"Customer\",\n" +
            "  \"appName\": \"Zendesk\",\n" +
            "  \"contextType\": \"CHAT\",\n" +
            "  \"quickReplies\": [\n" +
            "    \"I sincerely apologize for the delay. Let me expedite this immediately.\",\n" +
            "    \"I understand your frustration. I'm escalating this to our priority team right now.\",\n" +
            "    \"I'm so sorry for this experience. I'll process your refund immediately.\",\n" +
            "    \"This is unacceptable and I take full responsibility. How can I make this right?\"\n" +
            "  ],\n" +
            "  \"recommendedActions\": [\n" +
            "    {\"label\": \"Process refund\", \"icon\": \"\uD83D\uDCB0\", \"type\": \"PROCESS_REFUND\"},\n" +
            "    {\"label\": \"Escalate to manager\", \"icon\": \"\uD83D\uDCE2\", \"type\": \"ESCALATE\"}\n" +
            "  ]\n" +
            "}";

    // ========== SCENARIO 7: Happy/Excited Friend ==========

    public static final String EXCITED_FRIEND_MESSAGES =
            "Lisa: OMG guess what?!?!\n" +
            "Lisa: I got the job!!!\n" +
            "Lisa: Can't believe it! \uD83C\uDF89";

    public static final ScenarioExpected EXCITED_FRIEND_EXPECTED = new ScenarioExpected(
            "WhatsApp", "com.whatsapp", "Lisa",
            "EXCITED", "HIGH",
            "Lisa just got the job she wanted and is sharing the exciting news with you!",
            "en", false, null,
            Arrays.asList(
                    "CONGRATULATIONS!!! \uD83C\uDF89\uD83C\uDF89\uD83C\uDF89 So happy for you!",
                    "That's AMAZING news! You deserve it! \uD83E\uDD73",
                    "YESSS! I knew you'd get it! Let's celebrate! \uD83C\uDF7E",
                    "SO PROUD OF YOU! \uD83D\uDCAA When do you start?"
            ), null
    );

    public static final String EXCITED_FRIEND_API_RESPONSE = "{\n" +
            "  \"emotion\": \"EXCITED\",\n" +
            "  \"intensity\": \"HIGH\",\n" +
            "  \"summary\": \"Lisa just got the job she wanted and is sharing the exciting news with you!\",\n" +
            "  \"detectedLanguage\": \"en\",\n" +
            "  \"needsTranslation\": false,\n" +
            "  \"senderName\": \"Lisa\",\n" +
            "  \"appName\": \"WhatsApp\",\n" +
            "  \"contextType\": \"CHAT\",\n" +
            "  \"quickReplies\": [\n" +
            "    \"CONGRATULATIONS!!! \uD83C\uDF89\uD83C\uDF89\uD83C\uDF89 So happy for you!\",\n" +
            "    \"That's AMAZING news! You deserve it! \uD83E\uDD73\",\n" +
            "    \"YESSS! I knew you'd get it! Let's celebrate! \uD83C\uDF7E\",\n" +
            "    \"SO PROUD OF YOU! \uD83D\uDCAA When do you start?\"\n" +
            "  ]\n" +
            "}";

    // ========== ERROR SCENARIOS ==========

    public static final String EMPTY_RESPONSE = "";

    public static final String INVALID_JSON_RESPONSE = "{ this is not valid JSON }";

    public static final String MISSING_FIELDS_RESPONSE = "{\n" +
            "  \"emotion\": \"HAPPY\"\n" +
            "}";

    public static final String ERROR_API_RESPONSE = "{\n" +
            "  \"error\": true,\n" +
            "  \"message\": \"API rate limit exceeded\"\n" +
            "}";

    // ========== UTILITY METHODS ==========

    /**
     * Get all scenario names for testing
     */
    public static List<String> getAllScenarioNames() {
        return Arrays.asList(
                "FRUSTRATED_BOSS",
                "WORRIED_FRIEND_HINDI",
                "CASUAL_FRIEND",
                "FORMAL_EMAIL",
                "HINGLISH",
                "ANGRY_CUSTOMER",
                "EXCITED_FRIEND"
        );
    }

    /**
     * Get API response for a scenario name
     */
    public static String getApiResponse(String scenarioName) {
        switch (scenarioName) {
            case "FRUSTRATED_BOSS": return FRUSTRATED_BOSS_API_RESPONSE;
            case "WORRIED_FRIEND_HINDI": return WORRIED_FRIEND_HINDI_API_RESPONSE;
            case "CASUAL_FRIEND": return CASUAL_FRIEND_API_RESPONSE;
            case "FORMAL_EMAIL": return FORMAL_EMAIL_API_RESPONSE;
            case "HINGLISH": return HINGLISH_API_RESPONSE;
            case "ANGRY_CUSTOMER": return ANGRY_CUSTOMER_API_RESPONSE;
            case "EXCITED_FRIEND": return EXCITED_FRIEND_API_RESPONSE;
            default: return null;
        }
    }

    /**
     * Get expected data for a scenario name
     */
    public static ScenarioExpected getExpectedData(String scenarioName) {
        switch (scenarioName) {
            case "FRUSTRATED_BOSS": return FRUSTRATED_BOSS_EXPECTED;
            case "WORRIED_FRIEND_HINDI": return WORRIED_FRIEND_HINDI_EXPECTED;
            case "CASUAL_FRIEND": return CASUAL_FRIEND_EXPECTED;
            case "FORMAL_EMAIL": return FORMAL_EMAIL_EXPECTED;
            case "HINGLISH": return HINGLISH_EXPECTED;
            case "ANGRY_CUSTOMER": return ANGRY_CUSTOMER_EXPECTED;
            case "EXCITED_FRIEND": return EXCITED_FRIEND_EXPECTED;
            default: return null;
        }
    }

    /**
     * Get messages for a scenario name
     */
    public static String getMessages(String scenarioName) {
        switch (scenarioName) {
            case "FRUSTRATED_BOSS": return FRUSTRATED_BOSS_MESSAGES;
            case "WORRIED_FRIEND_HINDI": return WORRIED_FRIEND_HINDI_MESSAGES;
            case "CASUAL_FRIEND": return CASUAL_FRIEND_MESSAGES;
            case "FORMAL_EMAIL": return FORMAL_EMAIL_MESSAGES;
            case "HINGLISH": return HINGLISH_CHAT_MESSAGES;
            case "ANGRY_CUSTOMER": return ANGRY_CUSTOMER_MESSAGES;
            case "EXCITED_FRIEND": return EXCITED_FRIEND_MESSAGES;
            default: return null;
        }
    }
}
