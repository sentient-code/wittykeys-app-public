package project.witty.keys.app.context;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import project.witty.keys.app.helpers.DebugConfig;

/**
 * ReplyValidator - Validates and filters AI-generated replies for quality.
 *
 * Phase 1: Reply Quality Revolution
 *
 * Scores each reply against quality rubric:
 * - Naturalness
 * - Context fit
 * - Tone match
 * - Length appropriateness
 * - Cultural fit
 * - Variety
 *
 * Filters out replies scoring below threshold.
 */
public class ReplyValidator {

    private static final String TAG = "ReplyValidator";
    private static final float MIN_SCORE_THRESHOLD = 6.0f; // Out of 10
    private static final int MAX_SAME_START_WORDS = 2; // Max replies that can start with same word

    // Banned phrases (must NEVER appear in replies)
    private static final Set<String> BANNED_PHRASES = new HashSet<>(Arrays.asList(
            "that's great",
            "thats great",
            "i understand",
            "thank you for sharing",
            "thanks for sharing",
            "i appreciate",
            "i really appreciate",
            "i completely understand",
            "i totally understand",
            "i hear you",
            "noted",
            "duly noted",
            "understood",
            "will do",
            "sure thing",
            "no problem",
            "no worries"
    ));

    // Formal phrases to avoid in casual contexts
    private static final Set<String> FORMAL_PHRASES = new HashSet<>(Arrays.asList(
            "sir", "ma'am", "madam", "dear",
            "dhanyavaad", "kripya", "aap", "aapka",
            "kindly", "please be advised", "for your information",
            "with regards", "best regards", "sincerely"
    ));

    // Generic/low-effort responses to penalize
    private static final Set<String> GENERIC_RESPONSES = new HashSet<>(Arrays.asList(
            "ok", "okay", "k", "hmm", "hmmm", "cool", "nice",
            "lol", "haha", "hehe", "ohh", "ahh", "yeah"
    ));

    public static class ValidationResult {
        private final List<String> validReplies;
        private final List<String> filteredReplies;
        private final List<Float> scores;

        public ValidationResult(List<String> validReplies, List<String> filteredReplies, List<Float> scores) {
            this.validReplies = validReplies;
            this.filteredReplies = filteredReplies;
            this.scores = scores;
        }

        public List<String> getValidReplies() {
            return validReplies;
        }

        public List<String> getFilteredReplies() {
            return filteredReplies;
        }

        public List<Float> getScores() {
            return scores;
        }

        public int getValidCount() {
            return validReplies.size();
        }

        public int getFilteredCount() {
            return filteredReplies.size();
        }
    }

    /**
     * Validate and filter replies.
     *
     * @param replies List of AI-generated replies
     * @param incomingMessage The message being replied to
     * @param isCasualContext Whether context is casual (vs professional)
     * @return ValidationResult with valid and filtered replies
     */
    public ValidationResult validateReplies(
            List<String> replies,
            String incomingMessage,
            boolean isCasualContext) {

        if (replies == null || replies.isEmpty()) {
            return new ValidationResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        List<String> validReplies = new ArrayList<>();
        List<String> filteredReplies = new ArrayList<>();
        List<Float> scores = new ArrayList<>();

        int incomingLength = incomingMessage != null ? wordCount(incomingMessage) : 10;

        for (String reply : replies) {
            float score = scoreReply(reply, incomingMessage, incomingLength, isCasualContext);
            scores.add(score);

            if (score >= MIN_SCORE_THRESHOLD) {
                validReplies.add(reply);
            } else {
                filteredReplies.add(reply);
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[Validator] Filtered: \"" + truncate(reply, 30) + "\" (score=" + score + ")");
                }
            }
        }

        // Enforce variety - if 3+ replies start with same word, remove extras
        validReplies = enforceVariety(validReplies);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[Validator] Valid: " + validReplies.size() + ", Filtered: " + filteredReplies.size());
        }

        return new ValidationResult(validReplies, filteredReplies, scores);
    }

    /**
     * Score a single reply on quality rubric (0-10).
     */
    public float scoreReply(
            String reply,
            String incomingMessage,
            int incomingWordCount,
            boolean isCasualContext) {

        if (reply == null || reply.trim().isEmpty()) {
            return 0.0f;
        }

        String lowerReply = reply.toLowerCase().trim();
        float score = 10.0f; // Start with max, deduct for issues

        // 1. Check banned phrases (-10 points, instant fail)
        for (String banned : BANNED_PHRASES) {
            if (lowerReply.contains(banned)) {
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[Validator] Banned phrase found: \"" + banned + "\"");
                }
                return 0.0f;
            }
        }

        // 2. Check formal phrases in casual context (-3 points each)
        if (isCasualContext) {
            for (String formal : FORMAL_PHRASES) {
                if (lowerReply.contains(formal)) {
                    score -= 3.0f;
                    if (DebugConfig.isDebugMode) {
                        Log.d(TAG, "[Validator] Formal phrase in casual: \"" + formal + "\"");
                    }
                }
            }
        }

        // 3. Check for generic/low-effort responses (-2 points)
        String cleanReply = lowerReply.replaceAll("[^a-zA-Z]", "");
        if (GENERIC_RESPONSES.contains(cleanReply)) {
            score -= 2.0f;
        }

        // 4. Length calibration check (-2 to -4 points for mismatch)
        int replyWordCount = wordCount(reply);
        float lengthRatio = incomingWordCount > 0 ? (float) replyWordCount / incomingWordCount : 1.0f;

        if (lengthRatio > 2.0f) {
            // Reply more than 2x incoming length
            score -= 3.0f;
        } else if (lengthRatio > 1.5f) {
            // Reply more than 1.5x incoming length
            score -= 1.5f;
        } else if (lengthRatio < 0.2f && incomingWordCount > 5) {
            // Very short reply to longer message (unless incoming was short)
            score -= 1.0f;
        }

        // 5. Check for empty/too short (-3 points)
        if (replyWordCount < 1) {
            score -= 3.0f;
        }

        // 6. Check for too long (>50 chars without good reason) (-1 point)
        if (reply.length() > 50 && incomingWordCount < 10) {
            score -= 1.0f;
        }

        // 7. Bonus for emoji presence when appropriate (+0.5)
        if (containsEmoji(reply) && isCasualContext) {
            score += 0.5f;
        }

        // 8. Bonus for question when showing interest (+0.5)
        if (reply.contains("?") && !lowerReply.startsWith("what")) {
            score += 0.5f;
        }

        // Clamp score to 0-10 range
        return Math.max(0.0f, Math.min(10.0f, score));
    }

    /**
     * Enforce variety - ensure no more than MAX_SAME_START_WORDS replies start with same word.
     */
    public List<String> enforceVariety(List<String> replies) {
        if (replies == null || replies.size() <= MAX_SAME_START_WORDS) {
            return replies;
        }

        // Count first words
        java.util.Map<String, Integer> firstWordCounts = new java.util.HashMap<>();
        java.util.Map<String, List<Integer>> firstWordIndices = new java.util.HashMap<>();

        for (int i = 0; i < replies.size(); i++) {
            String firstWord = getFirstWord(replies.get(i));
            if (firstWord != null && !firstWord.isEmpty()) {
                firstWordCounts.merge(firstWord, 1, Integer::sum);
                firstWordIndices.computeIfAbsent(firstWord, k -> new ArrayList<>()).add(i);
            }
        }

        // Find words that exceed limit and mark indices to remove
        Set<Integer> indicesToRemove = new HashSet<>();
        for (java.util.Map.Entry<String, Integer> entry : firstWordCounts.entrySet()) {
            if (entry.getValue() > MAX_SAME_START_WORDS) {
                List<Integer> indices = firstWordIndices.get(entry.getKey());
                // Keep first MAX_SAME_START_WORDS, remove the rest
                for (int i = MAX_SAME_START_WORDS; i < indices.size(); i++) {
                    indicesToRemove.add(indices.get(i));
                }
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[Validator] Variety: removing " + (indices.size() - MAX_SAME_START_WORDS) +
                            " replies starting with \"" + entry.getKey() + "\"");
                }
            }
        }

        // Build result list excluding marked indices
        List<String> result = new ArrayList<>();
        for (int i = 0; i < replies.size(); i++) {
            if (!indicesToRemove.contains(i)) {
                result.add(replies.get(i));
            }
        }

        return result;
    }

    /**
     * Check if replies have sufficient variety (for testing).
     */
    public boolean hasGoodVariety(List<String> replies) {
        if (replies == null || replies.size() < 4) {
            return true; // Not enough to judge
        }

        // Count first words
        java.util.Map<String, Integer> firstWordCounts = new java.util.HashMap<>();
        for (String reply : replies) {
            String firstWord = getFirstWord(reply);
            if (firstWord != null) {
                firstWordCounts.merge(firstWord, 1, Integer::sum);
            }
        }

        // Check if any word exceeds threshold
        for (Integer count : firstWordCounts.values()) {
            if (count > MAX_SAME_START_WORDS) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if reply contains any banned phrase.
     */
    public boolean containsBannedPhrase(String reply) {
        if (reply == null) return false;
        String lower = reply.toLowerCase();
        for (String banned : BANNED_PHRASES) {
            if (lower.contains(banned)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if reply length is within acceptable range of incoming message.
     */
    public boolean isLengthAppropriate(String reply, String incomingMessage) {
        if (reply == null || incomingMessage == null) return true;

        int replyWords = wordCount(reply);
        int incomingWords = wordCount(incomingMessage);

        // Allow ±50% as per design rules
        float minAllowed = incomingWords * 0.5f;
        float maxAllowed = incomingWords * 1.5f;

        // Add some buffer for very short/long messages
        if (incomingWords <= 3) {
            maxAllowed = Math.max(maxAllowed, 8);
        }
        if (incomingWords >= 20) {
            minAllowed = Math.max(minAllowed, 5);
        }

        return replyWords >= minAllowed && replyWords <= maxAllowed;
    }

    private String getFirstWord(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String[] words = text.trim().split("\\s+");
        if (words.length > 0) {
            return words[0].toLowerCase().replaceAll("[^a-zA-Z]", "");
        }
        return null;
    }

    private int wordCount(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return text.trim().split("\\s+").length;
    }

    private boolean containsEmoji(String text) {
        if (text == null) return false;
        // Simple emoji detection
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.codePointAt(i);
            if (codePoint >= 0x1F600 && codePoint <= 0x1F64F) return true; // Emoticons
            if (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) return true; // Misc Symbols
            if (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) return true; // Transport
            if (codePoint >= 0x2600 && codePoint <= 0x26FF) return true;   // Misc symbols
            if (codePoint >= 0x2700 && codePoint <= 0x27BF) return true;   // Dingbats
        }
        return false;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
