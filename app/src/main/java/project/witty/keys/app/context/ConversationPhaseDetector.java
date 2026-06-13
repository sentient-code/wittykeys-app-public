package project.witty.keys.app.context;

import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import project.witty.keys.app.helpers.DebugConfig;

/**
 * ConversationPhaseDetector - Detects conversation phase from context.
 *
 * Phase 1: Reply Quality Revolution
 *
 * Detects 3 conversation phases:
 * - OPENER: First message in a conversation (greeting, introduction)
 * - MID_CONVERSATION: Ongoing conversation (replies, follow-ups)
 * - ENDER: Conversation closing (goodbyes, sign-offs)
 *
 * Uses message count, time gaps, and content patterns for detection.
 */
public class ConversationPhaseDetector {

    private static final String TAG = "ConversationPhaseDetector";

    public enum ConversationPhase {
        OPENER,          // Starting a new conversation
        MID_CONVERSATION, // In the middle of a conversation
        ENDER            // Ending/closing a conversation
    }

    public static class PhaseResult {
        private final ConversationPhase phase;
        private final float confidence;
        private final String reason;

        public PhaseResult(ConversationPhase phase, float confidence, String reason) {
            this.phase = phase;
            this.confidence = confidence;
            this.reason = reason;
        }

        public ConversationPhase getPhase() {
            return phase;
        }

        public float getConfidence() {
            return confidence;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return phase.name() + " (" + String.format("%.2f", confidence) + "): " + reason;
        }
    }

    // Opener phrases (greetings, introductions)
    private static final Set<String> OPENER_PHRASES = new HashSet<>(Arrays.asList(
            "hi", "hello", "hey", "hii", "hiii", "sup", "yo", "hola",
            "good morning", "good afternoon", "good evening", "good night",
            "howdy", "greetings", "wassup", "whats up", "what's up",
            "how are you", "how r u", "how ru", "hows it going", "how's it going",
            "long time", "long time no see", "been a while"
    ));

    // Hinglish opener phrases
    private static final Set<String> OPENER_HINGLISH = new HashSet<>(Arrays.asList(
            "kya haal", "kaise ho", "kya chal raha", "suno", "arre",
            "namaste", "namaskar", "pranam", "kem cho", "ki haal",
            "bhai", "yaar", "dost", "kaisa hai", "kaisi hai", "kaisi ho"
    ));

    // Ender phrases (goodbyes, sign-offs)
    private static final Set<String> ENDER_PHRASES = new HashSet<>(Arrays.asList(
            "bye", "goodbye", "good bye", "byebye", "bye bye", "cya", "see ya",
            "see you", "later", "laterz", "ttyl", "talk later", "talk soon",
            "gotta go", "got to go", "have to go", "need to go", "leaving",
            "goodnight", "good night", "gn", "take care", "tc",
            "catch you later", "catch up later", "peace", "peace out",
            "signing off", "logging off", "brb", "be right back",
            "thanks for", "thank you for", "nice talking", "nice chatting"
    ));

    // Hinglish ender phrases
    private static final Set<String> ENDER_HINGLISH = new HashSet<>(Arrays.asList(
            "chalo", "chalte hai", "phir milte hai", "phir baat karte hai",
            "baad mein", "milte hai", "bye bye", "tata", "alvida",
            "kal baat karte", "raat ko baat karte", "shukriya", "dhanyavaad"
    ));

    // Time-based thresholds (in milliseconds)
    private static final long NEW_CONVERSATION_GAP = 4 * 60 * 60 * 1000; // 4 hours
    private static final long LIKELY_OPENER_GAP = 1 * 60 * 60 * 1000;    // 1 hour

    /**
     * Detect conversation phase from chat context.
     *
     * @param messages List of chat messages in order
     * @param currentMessageIndex Index of the message to reply to (usually last)
     * @return PhaseResult with detected phase, confidence, and reason
     */
    public PhaseResult detectPhase(List<ChatMessage> messages, int currentMessageIndex) {
        if (messages == null || messages.isEmpty()) {
            return new PhaseResult(ConversationPhase.OPENER, 0.5f, "No messages - assuming opener");
        }

        int msgCount = messages.size();
        int idx = currentMessageIndex >= 0 ? currentMessageIndex : msgCount - 1;

        if (idx >= msgCount) {
            idx = msgCount - 1;
        }

        ChatMessage currentMessage = messages.get(idx);
        String text = currentMessage.getText();

        // 1. Check message count heuristics
        if (msgCount == 1) {
            // Single message - likely opener unless it's a goodbye
            if (isEnderPhrase(text)) {
                return new PhaseResult(ConversationPhase.ENDER, 0.8f, "Single message with goodbye phrase");
            }
            return new PhaseResult(ConversationPhase.OPENER, 0.8f, "Single message in conversation");
        }

        // 2. Check for explicit opener/ender phrases
        if (isOpenerPhrase(text)) {
            return new PhaseResult(ConversationPhase.OPENER, 0.9f, "Contains greeting phrase");
        }

        if (isEnderPhrase(text)) {
            return new PhaseResult(ConversationPhase.ENDER, 0.9f, "Contains goodbye phrase");
        }

        // 3. Check time gaps (if timestamps available as epoch milliseconds)
        // Note: ChatMessage.getTimestamp() returns String (e.g., "12:30 PM")
        // so time-based comparison is not currently supported.
        // In future, if timestamps are stored as epoch ms, enable this:
        /*
        if (idx > 0 && currentMessage.getTimestamp() != null) {
            ChatMessage previousMessage = messages.get(idx - 1);
            if (previousMessage.getTimestamp() != null) {
                try {
                    long currentTime = Long.parseLong(currentMessage.getTimestamp());
                    long previousTime = Long.parseLong(previousMessage.getTimestamp());
                    long timeDiff = currentTime - previousTime;

                    if (timeDiff > NEW_CONVERSATION_GAP) {
                        return new PhaseResult(ConversationPhase.OPENER, 0.85f,
                                "Large time gap since last message");
                    }
                } catch (NumberFormatException e) {
                    // Timestamp is not numeric, skip time-based detection
                }
            }
        }
        */

        // 4. Position-based heuristics
        if (idx <= 1) {
            // Near the start of conversation
            return new PhaseResult(ConversationPhase.OPENER, 0.6f, "Near conversation start");
        }

        // 5. Content-based mid-conversation indicators
        if (isContinuationMessage(text)) {
            return new PhaseResult(ConversationPhase.MID_CONVERSATION, 0.85f, "Continuation/follow-up message");
        }

        // 6. Default to mid-conversation if we have multiple messages
        return new PhaseResult(ConversationPhase.MID_CONVERSATION, 0.7f,
                "Multiple messages, no opener/ender detected");
    }

    /**
     * Simplified detection when only the message text is available.
     *
     * @param text The message text
     * @param messageCount Total messages in conversation (0 if unknown)
     * @return PhaseResult
     */
    public PhaseResult detectPhase(String text, int messageCount) {
        if (text == null || text.trim().isEmpty()) {
            return new PhaseResult(ConversationPhase.MID_CONVERSATION, 0.5f, "Empty message");
        }

        // Check explicit phrases first
        if (isOpenerPhrase(text)) {
            return new PhaseResult(ConversationPhase.OPENER, 0.9f, "Contains greeting phrase");
        }

        if (isEnderPhrase(text)) {
            return new PhaseResult(ConversationPhase.ENDER, 0.9f, "Contains goodbye phrase");
        }

        // Use message count if available
        if (messageCount == 1) {
            if (isShortGreeting(text)) {
                return new PhaseResult(ConversationPhase.OPENER, 0.8f, "Single short greeting");
            }
            return new PhaseResult(ConversationPhase.OPENER, 0.6f, "Single message");
        }

        if (messageCount == 0) {
            // Unknown count - check if it looks like an opener
            if (isShortGreeting(text)) {
                return new PhaseResult(ConversationPhase.OPENER, 0.7f, "Looks like greeting");
            }
        }

        // Check for continuation patterns
        if (isContinuationMessage(text)) {
            return new PhaseResult(ConversationPhase.MID_CONVERSATION, 0.85f, "Continuation message");
        }

        // Default to mid-conversation
        return new PhaseResult(ConversationPhase.MID_CONVERSATION, 0.6f, "Default - no clear phase indicators");
    }

    private boolean isOpenerPhrase(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase().trim();

        // Check exact matches first
        for (String phrase : OPENER_PHRASES) {
            if (lower.equals(phrase) || lower.startsWith(phrase + " ") ||
                    lower.startsWith(phrase + "!") || lower.startsWith(phrase + ",")) {
                return true;
            }
        }

        for (String phrase : OPENER_HINGLISH) {
            if (lower.contains(phrase)) {
                return true;
            }
        }

        return false;
    }

    private boolean isEnderPhrase(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase().trim();

        for (String phrase : ENDER_PHRASES) {
            if (lower.equals(phrase) || lower.startsWith(phrase + " ") ||
                    lower.startsWith(phrase + "!") || lower.endsWith(" " + phrase)) {
                return true;
            }
        }

        for (String phrase : ENDER_HINGLISH) {
            if (lower.contains(phrase)) {
                return true;
            }
        }

        return false;
    }

    private boolean isShortGreeting(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase().trim();

        // Short greetings: hi, hey, hello, sup, yo (with optional punctuation/emoji)
        String cleaned = lower.replaceAll("[^a-zA-Z]", "");
        Set<String> shortGreetings = new HashSet<>(Arrays.asList(
                "hi", "hii", "hiii", "hey", "hello", "sup", "yo", "hola", "heyy", "heyyy"
        ));

        return shortGreetings.contains(cleaned) || cleaned.length() <= 10 && isOpenerPhrase(lower);
    }

    private boolean isContinuationMessage(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase().trim();

        // Patterns that suggest continuing a conversation
        String[] continuationPatterns = {
                "also", "and also", "btw", "by the way", "anyway",
                "so", "then", "after that", "plus", "moreover",
                "i was thinking", "i forgot to", "oh and", "one more thing",
                "about that", "regarding", "speaking of",
                "waise", "aur", "ek aur baat", "aur sun", "vaise"
        };

        for (String pattern : continuationPatterns) {
            if (lower.startsWith(pattern) || lower.contains(" " + pattern + " ")) {
                return true;
            }
        }

        // References to previous messages
        if (lower.contains("you said") || lower.contains("like i said") ||
                lower.contains("as i mentioned") || lower.contains("earlier")) {
            return true;
        }

        return false;
    }

    /**
     * Get a prompt hint based on conversation phase.
     *
     * @param phase The detected conversation phase
     * @return String hint to include in the prompt
     */
    public static String getPhasePromptHint(ConversationPhase phase) {
        switch (phase) {
            case OPENER:
                return "This is a conversation opener. Reply should be warm, welcoming, and open the conversation naturally.";
            case ENDER:
                return "This is a conversation closing. Reply should acknowledge the goodbye warmly and leave a positive final impression.";
            case MID_CONVERSATION:
            default:
                return "This is mid-conversation. Reply should naturally continue the flow and be contextually relevant.";
        }
    }
}
