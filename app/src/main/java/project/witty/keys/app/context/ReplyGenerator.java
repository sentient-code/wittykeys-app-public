package project.witty.keys.app.context;

import android.util.Log;

import java.util.List;

import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import org.json.JSONObject;
import project.witty.keys.BuildConfig;
import project.witty.keys.api.ClaudeApi;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.DemoLogger;
import project.witty.keys.app.helpers.JourneyTracer;

/**
 * ReplyGenerator - Generates smart reply suggestions using Claude API.
 *
 * Takes screen context (conversation, app type) and generates 4 contextually
 * relevant reply suggestions.
 */
public class ReplyGenerator {

    private static final String TAG = "ReplyGenerator";

    // Standard prompt for reply generation (Phase 1: Reply Quality Revolution)
    private static final String SYSTEM_PROMPT =
            "You are WittyKeys, an AI reply assistant that generates 8 contextually perfect replies.\n\n" +
            "CORE RULES:\n" +
            "- Generate exactly 8 replies\n" +
            "- Each reply MUST take a DIFFERENT approach:\n" +
            "  1. Humorous/witty response\n" +
            "  2. Direct/straightforward answer\n" +
            "  3. Question back (show interest)\n" +
            "  4. Empathetic/supportive response\n" +
            "  5. Playful/teasing response\n" +
            "  6. Brief/minimal response\n" +
            "  7. Enthusiastic/excited response\n" +
            "  8. Thoughtful/deep response\n\n" +
            "EMOTION-AWARE RULES:\n" +
            "- Detect the sender's emotion (happy, sad, angry, flirty, urgent, etc.)\n" +
            "- Match your reply tone to their emotion\n" +
            "- For sad messages: be empathetic, NOT dismissive\n" +
            "- For angry messages: acknowledge frustration, NOT defensive\n" +
            "- For excited messages: share the enthusiasm\n" +
            "- For flirty messages: be playful and confident\n\n" +
            "LENGTH CALIBRATION:\n" +
            "- Match reply length to incoming message (±50%)\n" +
            "- Short message (1-5 words) → short replies (1-8 words)\n" +
            "- Medium message (6-15 words) → medium replies (5-20 words)\n" +
            "- Long message (16+ words) → can be longer but still concise\n\n" +
            "BANNED PHRASES (NEVER use these):\n" +
            "- \"That's great!\"\n" +
            "- \"I understand\"\n" +
            "- \"Thank you for sharing\"\n" +
            "- \"I appreciate\"\n" +
            "- \"Sir\" or \"Ma'am\" in casual contexts\n" +
            "- Starting with \"I\" for emotional messages (sounds self-centered)\n\n" +
            "VARIETY ENFORCEMENT:\n" +
            "- NO two replies should start with the same word\n" +
            "- Each reply must offer genuinely different value\n" +
            "- Include 1 relevant emoji per reply if contextually appropriate\n" +
            "- Never generate generic, cookie-cutter responses\n\n" +
            "Return ONLY the 8 replies, one per line, no numbering or explanation.";

    // Enhanced prompt for bilingual reply generation (Phase 1: Reply Quality Revolution)
    private static final String BILINGUAL_SYSTEM_PROMPT =
            "You are WittyKeys, India's first AI keyboard built for how India actually texts.\n\n" +
            "CRITICAL HINGLISH RULES:\n" +
            "- At least 4 of 8 replies MUST be in Hinglish (Hindi words in Roman script)\n" +
            "- Match the sender's Hindi-English ratio\n" +
            "- Use NATURAL transliteration, not formal Hindi\n" +
            "  ✓ \"yaar bahut maza aaya\" NOT \"mitra bahut anand aaya\"\n" +
            "  ✓ \"kya scene hai\" NOT \"kya sthiti hai\"\n" +
            "  ✓ \"chal theek hai\" NOT \"chalo thik hai\"\n" +
            "- Code-switch naturally like young Indians: \"bro party mein maza aaya, everyone was vibing\"\n\n" +
            "GENERATE 8 REPLIES with different approaches:\n" +
            "1. Humorous/witty (Hinglish preferred)\n" +
            "2. Direct answer (can be English)\n" +
            "3. Question back (Hinglish preferred)\n" +
            "4. Empathetic/supportive\n" +
            "5. Playful/teasing (Hinglish preferred)\n" +
            "6. Brief/minimal (Hinglish preferred)\n" +
            "7. Enthusiastic/excited\n" +
            "8. Thoughtful response\n\n" +
            "EMOTION-AWARE RULES:\n" +
            "- Detect emotion (khush, udaas, gussa, flirty, urgent)\n" +
            "- Sad (udaas) → \"yaar don't worry, sab theek ho jayega\"\n" +
            "- Angry (gussa) → acknowledge, don't be defensive\n" +
            "- Flirty → be playful and confident: \"acha ji, aur kya\"\n\n" +
            "LENGTH CALIBRATION:\n" +
            "- Match reply length to incoming message (±50%)\n" +
            "- Short → Short: \"sup\" → \"mast, tu bol\"\n" +
            "- Long → Medium-Long\n\n" +
            "BANNED PHRASES (NEVER use):\n" +
            "- \"That's great!\", \"I understand\", \"Thank you for sharing\"\n" +
            "- \"Sir\", \"Ma'am\", \"Aap\" in casual friend chats\n" +
            "- Formal Hindi like \"Dhanyavaad\", \"Kripya\" in casual contexts\n" +
            "- Starting with \"I\" for emotional messages\n\n" +
            "VARIETY:\n" +
            "- NO two replies start with same word\n" +
            "- Each reply genuinely different\n" +
            "- Mix Hinglish and English naturally\n\n" +
            "Return JSON format:\n" +
            "{\n" +
            "  \"detectedLanguage\": \"hi-en\",\n" +
            "  \"needsTranslation\": false,\n" +
            "  \"quickReplies\": [\"reply1\", \"reply2\", \"reply3\", \"reply4\", \"reply5\", \"reply6\", \"reply7\", \"reply8\"]\n" +
            "}";

    private final ClaudeApi claudeApi;
    private final ConversationPhaseDetector phaseDetector;

    public ReplyGenerator() {
        this.claudeApi = new ClaudeApi();
        this.phaseDetector = new ConversationPhaseDetector();
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[ReplyGen] ReplyGenerator initialized with phase detection");
        }
    }

    /**
     * Generate reply suggestions for a Chat context.
     *
     * @param chat The chat context containing messages and participants
     * @param callback Callback for reply results
     */
    public void generateReplies(Chat chat, ReplyCallback callback) {
        if (chat == null) {
            Log.e(TAG, "[ReplyGen] Chat context is null");
            DemoLogger.logError(DemoLogger.FLOW_AI_FEATURES, "null_context", "Chat context is null");
            callback.onError("No conversation context available");
            return;
        }

        final long startTime = System.currentTimeMillis();
        DemoLogger.logStart(DemoLogger.FLOW_AI_FEATURES);

        if (BuildConfig.DEBUG) {
            Log.i("WK_E2E", "[APP] ReplyGenerator.generateReplies() — chat messages: " + chat.getMessages().size());
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[ReplyGen] Generating replies for app: " + chat.getAppName());
        }

        // Log the context being used
        ChatMessage lastMessage = getLastIncomingMessage(chat.getMessages());
        String lastMessageText = lastMessage != null ? lastMessage.getText() : "";

        // Guard against empty/whitespace-only input (TC-31)
        if (lastMessageText == null || lastMessageText.trim().isEmpty()) {
            Log.w(TAG, "[ReplyGen] Empty or whitespace-only input detected, skipping API call");
            DemoLogger.logError(DemoLogger.FLOW_AI_FEATURES, "empty_input", "No meaningful text to generate replies for");
            callback.onError("No message text to reply to");
            return;
        }

        // Phase 1 Debug: Log all messages to help diagnose emotion detection
        if (DebugConfig.isDebugMode) {
            List<ChatMessage> allMsgs = chat.getMessages();
            Log.d(TAG, "[ReplyGen] === MESSAGE LIST DEBUG ===");
            Log.d(TAG, "[ReplyGen] Total messages: " + (allMsgs != null ? allMsgs.size() : 0));
            if (allMsgs != null) {
                for (int i = 0; i < allMsgs.size(); i++) {
                    ChatMessage m = allMsgs.get(i);
                    String sender = m.isFromCurrentUser() ? "USER" : "OTHER";
                    String text = m.getText();
                    if (text != null && text.length() > 50) {
                        text = text.substring(0, 50) + "...";
                    }
                    Log.d(TAG, "[ReplyGen] [" + i + "] " + sender + ": \"" + text + "\"");
                }
            }
            Log.d(TAG, "[ReplyGen] Last message: \"" + lastMessageText + "\"");
            Log.d(TAG, "[ReplyGen] === END MESSAGE LIST ===");
        }

        // Detect conversation phase
        ConversationPhaseDetector.PhaseResult phase = null;

        List<ChatMessage> messages = chat.getMessages();
        if (messages != null && !messages.isEmpty()) {
            phase = phaseDetector.detectPhase(messages, messages.size() - 1);
            if (DebugConfig.isDebugMode && phase != null) {
                Log.d(TAG, "[ReplyGen] Phase detected: " + phase);
            }
        }

        // Build prompt with phase context
        String userPrompt = buildUserPrompt(chat, phase);

        DemoLogger.logContext(
            DemoLogger.FLOW_AI_FEATURES,
            chat.getAppName(),
            categorizeApp(chat.getAppName()),
            chat.getMessages() != null ? chat.getMessages().size() : 0,
            lastMessageText,
            chat.getParticipants() != null && !chat.getParticipants().isEmpty() ?
                chat.getParticipants().get(0) : null
        );

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[ReplyGen] User prompt:\n" + userPrompt);
        }

        // Phase 7: Detect non-English and use appropriate prompt
        String systemPrompt = getSystemPromptForText(lastMessageText);

        // JourneyTracer: prompt built
        String traceId = JourneyTracer.getCurrentSmartReplyTrace();
        if (traceId != null) {
            try {
                JSONObject dataIn = new JSONObject();
                dataIn.put("message_length", lastMessageText.length());
                dataIn.put("language", mightBeNonEnglish(lastMessageText) ? "hinglish" : "english");
                JSONObject dataOut = new JSONObject();
                dataOut.put("system_prompt_tokens", systemPrompt != null ? systemPrompt.length() / 4 : 0);
                dataOut.put("user_prompt_tokens", userPrompt != null ? userPrompt.length() / 4 : 0);
                JourneyTracer.step(traceId, "PROMPT_BUILT", dataIn, dataOut,
                    "language: " + (mightBeNonEnglish(lastMessageText) ? "hinglish" : "english")
                    + " → using " + (mightBeNonEnglish(lastMessageText) ? "bilingual" : "standard") + " prompt");
            } catch (Exception ignored) {}
        }

                // Performance trace for reply generation
        final Trace replyTrace = FirebasePerformance.getInstance().newTrace("smart_reply_generation");
        replyTrace.start();

        claudeApi.generateReplies(systemPrompt, userPrompt, new ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                long totalTime = System.currentTimeMillis() - startTime;
                DemoLogger.logComplete(DemoLogger.FLOW_AI_FEATURES, true, totalTime);
                replyTrace.putMetric("reply_count", replies.size());
                replyTrace.stop();

                if (BuildConfig.DEBUG) {
                    Log.i("WK_E2E", "[APP] ReplyGenerator — API call completed, reply count: " + replies.size()
                        + " elapsed: " + totalTime + "ms");
                }

                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[ReplyGen] Received " + replies.size() + " replies in " + totalTime + "ms");
                    for (int i = 0; i < replies.size(); i++) {
                        Log.d(TAG, "[ReplyGen] Reply " + (i + 1) + ": " + replies.get(i));
                    }
                }
                callback.onRepliesGenerated(replies);
            }

            @Override
            public void onError(String error) {
                long totalTime = System.currentTimeMillis() - startTime;
                DemoLogger.logComplete(DemoLogger.FLOW_AI_FEATURES, false, totalTime);
                DemoLogger.logError(DemoLogger.FLOW_AI_FEATURES, "reply_generation", error);
                replyTrace.putAttribute("error", error.length() > 100 ? error.substring(0, 100) : error);
                replyTrace.stop();

                Log.e(TAG, "[ReplyGen] Error generating replies: " + error);
                callback.onError(error);
            }
        });
    }

    /**
     * Generate reply suggestions for a generic ScreenContext.
     * Extracts relevant information based on context type.
     *
     * @param context The screen context
     * @param callback Callback for reply results
     */
    public void generateReplies(ScreenContext context, ReplyCallback callback) {
        if (context == null) {
            Log.e(TAG, "[ReplyGen] Screen context is null");
            callback.onError("No context available");
            return;
        }

        if (context instanceof Chat) {
            generateReplies((Chat) context, callback);
        } else {
            Log.e(TAG, "[ReplyGen] Unsupported context type: " + context.getClass().getSimpleName());
            callback.onError("Unsupported context type");
        }
    }

    /**
     * Build the user prompt from chat context.
     */
    private String buildUserPrompt(Chat chat) {
        return buildUserPrompt(chat, null);
    }

    /**
     * Build the user prompt from chat context with phase awareness.
     */
    private String buildUserPrompt(Chat chat,
                                   ConversationPhaseDetector.PhaseResult phase) {
        StringBuilder prompt = new StringBuilder();

        // App context with relationship hint
        String appCategory = categorizeApp(chat.getAppName());
        prompt.append("App: ").append(appCategory).append("\n");

        // Phase 1: Add relationship context hint
        String relationshipHint = getRelationshipHint(appCategory);
        if (!relationshipHint.isEmpty()) {
            prompt.append(relationshipHint).append("\n");
        }

        // Participants/Sender
        List<String> participants = chat.getParticipants();
        if (participants != null && !participants.isEmpty()) {
            String sender = participants.get(0);
            prompt.append("Sender: ").append(sender != null ? sender : "Unknown").append("\n");
        } else {
            prompt.append("Sender: Unknown\n");
        }

        // Messages
        List<ChatMessage> messages = chat.getMessages();
        String lastMessageText = "";
        if (messages != null && !messages.isEmpty()) {
            // Get last message (the one to reply to)
            ChatMessage lastMessage = getLastIncomingMessage(messages);
            if (lastMessage != null) {
                lastMessageText = lastMessage.getText();
                prompt.append("Their message: \"").append(lastMessageText).append("\"\n");

                // Phase 1: Add message length for calibration
                int wordCount = lastMessageText.split("\\s+").length;
                prompt.append("Message length: ").append(wordCount).append(" words\n");
            }

            // Recent context (last few messages)
            String recentContext = getRecentContext(messages);
            if (!recentContext.isEmpty()) {
                prompt.append("Recent context: ").append(recentContext).append("\n");
            }
        }

        // Add conversation phase
        if (phase != null) {
            prompt.append("Conversation phase: ").append(phase.getPhase().name()).append("\n");
            prompt.append(ConversationPhaseDetector.getPhasePromptHint(phase.getPhase())).append("\n");
        }

        // Phase 1: Check if Hinglish
        if (mightBeNonEnglish(lastMessageText)) {
            prompt.append("Language: Hinglish detected - generate at least 4 Hinglish replies\n");
        }

        prompt.append("\nGenerate 8 reply options.");

        return prompt.toString();
    }

    /**
     * Get relationship context hint based on app category.
     * Phase 1: Relationship context for prompts.
     */
    private String getRelationshipHint(String appCategory) {
        if (appCategory == null) return "";

        switch (appCategory.toLowerCase()) {
            case "dating":
                return "RELATIONSHIP: Dating app - be flirty, confident, playful";
            case "email":
                return "RELATIONSHIP: Email - be professional, concise";
            case "messaging":
                return "RELATIONSHIP: Messaging - be casual, match their energy";
            case "social":
                return "RELATIONSHIP: Social media - be engaging, personable";
            default:
                return "";
        }
    }

    /**
     * Get the last incoming message (not from the current user).
     */
    private ChatMessage getLastIncomingMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (!msg.isFromCurrentUser()) {
                return msg;
            }
        }
        // If all messages are from user, return the last one anyway
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /**
     * Get recent conversation context (summarized).
     */
    private String getRecentContext(List<ChatMessage> messages) {
        if (messages == null || messages.size() <= 1) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        int start = Math.max(0, messages.size() - 21);

        for (int i = start; i < messages.size() - 1; i++) {
            ChatMessage msg = messages.get(i);
            if (msg == null || msg.getText() == null || msg.getText().trim().isEmpty()) {
                continue;
            }
            if (context.length() > 0) {
                context.append("\n");
            }
            String sender = msg.getSender();
            String prefix = msg.isFromCurrentUser()
                    ? "You: "
                    : (sender != null && !sender.trim().isEmpty()
                        ? sender.trim() + ": "
                        : "Them: ");
            context.append(prefix).append(msg.getText().trim());
        }

        return context.toString();
    }

    /**
     * Truncate text to max length with ellipsis.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Categorize app by name/package for better context.
     */
    private String categorizeApp(String appName) {
        if (appName == null) return "other";
        String lower = appName.toLowerCase();

        if (lower.contains("whatsapp") || lower.contains("telegram") ||
            lower.contains("messenger") || lower.contains("signal") ||
            lower.contains("messages") || lower.contains("sms")) {
            return "messaging";
        }

        if (lower.contains("gmail") || lower.contains("outlook") ||
            lower.contains("mail") || lower.contains("yahoo")) {
            return "email";
        }

        if (lower.contains("tinder") || lower.contains("bumble") ||
            lower.contains("hinge") || lower.contains("okcupid")) {
            return "dating";
        }

        if (lower.contains("linkedin") || lower.contains("twitter") ||
            lower.contains("instagram") || lower.contains("facebook") ||
            lower.contains("slack") || lower.contains("discord")) {
            return "social";
        }

        return "other";
    }

    /**
     * Detect if text might be non-English (Hindi, Hinglish, or other).
     * Phase 7 - Bilingual support
     *
     * @param text The text to analyze
     * @return true if likely non-English
     */
    public static boolean mightBeNonEnglish(String text) {
        if (text == null || text.isEmpty()) return false;

        // Check for Devanagari script (Hindi)
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.DEVANAGARI) {
                return true;
            }
        }

        // Check for common Hindi/Hinglish words in Roman script
        String lower = text.toLowerCase();
        String[] hinglishIndicators = {
            "kya", "hai", "ho", "haan", "nahi", "kaise", "kyun", "tum", "main",
            "accha", "theek", "mein", "kar", "karo", "aur", "bhi", "abhi",
            "bahut", "bol", "chal", "dekh", "gaya", "hoga", "jao", "kab",
            "mat", "mil", "pata", "raha", "sab", "tera", "woh", "yaar"
        };

        for (String word : hinglishIndicators) {
            if (lower.contains(word)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the appropriate system prompt based on conversation content.
     * Phase 7 - Bilingual support
     *
     * @param conversationText The conversation text to analyze
     * @return The appropriate system prompt
     */
    public String getSystemPromptForText(String conversationText) {
        if (mightBeNonEnglish(conversationText)) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[ReplyGen] Non-English detected, using bilingual prompt");
            }
            return BILINGUAL_SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT;
    }

    // ========== PHASE 2B: PROMPT BUILDERS FOR CTA WIRING ==========

    /**
     * Task 2.29: Build grammar correction prompt.
     * Returns a single corrected version of the text.
     */
    public static String buildGrammarPrompt() {
        return "You are WittyKeys, an AI writing assistant. Fix grammar and spelling errors in the user's text.\n\n" +
               "Rules:\n" +
               "- Return ONLY the corrected text, nothing else\n" +
               "- Preserve the original tone and style\n" +
               "- If the text is in Hinglish, keep it in Hinglish — only fix grammar\n" +
               "- Do not add or remove content, only fix errors\n" +
               "- If text has no errors, return it unchanged";
    }

    /**
     * Task 2.30: Build tone transformation prompt.
     * Returns 3-5 variations in the requested tone.
     */
    public static String buildTonePrompt(String toneName) {
        return "TASK: The user has typed a message that they want to SEND to another person. " +
               "Your job is to REPHRASE / REWRITE the user's message in a " + toneName + " tone.\n\n" +
               "CRITICAL RULES:\n" +
               "- You are REWRITING the user's outgoing message, NOT replying to it\n" +
               "- Do NOT respond AS IF you received this message\n" +
               "- Do NOT generate a reply or answer to the message content\n" +
               "- The output must be something the USER would send, in their voice\n" +
               "- Preserve the original INTENT and MEANING of the message\n" +
               "- Only change the TONE / STYLE / WORDING\n\n" +
               "FORMATTING RULES:\n" +
               "- Keep each variation SHORT (1-3 sentences max)\n" +
               "- Output 3-4 variations, one per line, no numbering\n" +
               "- Each must be ready to send as-is (no quotes, no labels)\n" +
               "- Make each variation meaningfully different\n" +
               "- Write as a real person would text\n" +
               "- Match original length ±30%\n" +
               "- If the text is in Hinglish, keep it in Hinglish\n" +
               "- No bullet points, no markdown headers, no code blocks";
    }

    /**
     * Task 2.31: Build translation prompt.
     * Returns a single translated version.
     */
    public static String buildTranslatePrompt(String targetLanguage) {
        return "You are a translation assistant. Translate the user's text to " + targetLanguage + ".\n\n" +
               "Rules:\n" +
               "- Return ONLY the translated text, nothing else\n" +
               "- Preserve the tone and intent\n" +
               "- One translation only, no alternatives";
    }

    /**
     * Task 2.32: Build custom prompt.
     * Returns 3-6 reply options based on custom instruction.
     */
    public static String buildCustomPrompt(String customInstruction) {
        return "You are WittyKeys, an AI writing assistant. Follow the user's custom instruction.\n\n" +
               "Custom instruction: " + customInstruction + "\n\n" +
               "Rules:\n" +
               "- Generate 3-6 reply options\n" +
               "- Each option should be meaningfully different\n" +
               "- Output one option per line, no numbering\n" +
               "- If the context is Hinglish, include Hinglish options";
    }

    /**
     * Callback interface for reply generation results.
     */
    public interface ReplyCallback {
        /**
         * Called when replies are successfully generated.
         * @param replies List of reply suggestions (typically 4)
         */
        void onRepliesGenerated(List<String> replies);

        /**
         * Called when an error occurs.
         * @param error Error message
         */
        void onError(String error);
    }
}
