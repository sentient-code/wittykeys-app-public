package project.witty.keys.app.context;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import project.witty.keys.app.database.ChatMessage;

/**
 * ContextWindowManager — Hybrid summary-buffer for Claude API context.
 * Build 7.0 Phase 4.
 *
 * Strategy:
 * 1. System prompt (always included)
 * 2. Session summary (if > MAX_RECENT messages, summarize older ones)
 * 3. Recent messages (last N messages in full)
 * 4. NLS context (injected as system context)
 *
 * Token counting uses a simple 4-chars-per-token estimate.
 * Claude Haiku has ~200K context but we limit to ~4K tokens for cost control.
 */
public class ContextWindowManager {

    private static final String TAG = "ContextWindowManager";
    private static final int MAX_TOTAL_TOKENS = 4000;      // Cost control limit
    private static final int SYSTEM_PROMPT_TOKENS = 200;    // Reserved for system prompt
    private static final int MAX_RECENT_MESSAGES = 10;      // Keep last N in full
    private static final int CHARS_PER_TOKEN = 4;           // Rough estimate

    private static ContextWindowManager instance;

    private String systemPrompt;

    private ContextWindowManager() {
        this.systemPrompt = buildSystemPrompt();
    }

    public static synchronized ContextWindowManager getInstance() {
        if (instance == null) {
            instance = new ContextWindowManager();
        }
        return instance;
    }

    /**
     * Build the messages array for Claude API call.
     *
     * @param messages     All messages in the session (chronological)
     * @param nlsContext   Recent NLS messages as context string (nullable)
     * @param sessionSummary  Summary of older messages (nullable, from DB)
     * @return JSONArray of {role, content} objects ready for Claude API
     */
    public JSONArray buildMessagesArray(List<ChatMessage> messages,
            String nlsContext, String sessionSummary) throws JSONException {

        JSONArray apiMessages = new JSONArray();
        int usedTokens = SYSTEM_PROMPT_TOKENS;

        // 1. If we have more messages than MAX_RECENT, include summary
        if (messages.size() > MAX_RECENT_MESSAGES && sessionSummary != null
                && !sessionSummary.isEmpty()) {
            JSONObject summaryMsg = new JSONObject();
            summaryMsg.put("role", "user");
            summaryMsg.put("content", "[Previous conversation summary: " + sessionSummary + "]");
            apiMessages.put(summaryMsg);

            JSONObject ack = new JSONObject();
            ack.put("role", "assistant");
            ack.put("content", "I understand the context from our previous conversation.");
            apiMessages.put(ack);

            usedTokens += estimateTokens(sessionSummary) + 20;
        }

        // 2. Add NLS context if available
        if (nlsContext != null && !nlsContext.isEmpty()) {
            JSONObject nlsMsg = new JSONObject();
            nlsMsg.put("role", "user");
            nlsMsg.put("content", "[Recent messages from this conversation:\n" + nlsContext + "]");
            apiMessages.put(nlsMsg);

            JSONObject ack = new JSONObject();
            ack.put("role", "assistant");
            ack.put("content", "I see the recent messages. How can I help?");
            apiMessages.put(ack);

            usedTokens += estimateTokens(nlsContext) + 20;
        }

        // 3. Add recent messages (last N, respecting token budget)
        int startIdx = Math.max(0, messages.size() - MAX_RECENT_MESSAGES);
        for (int i = startIdx; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);

            // Skip context-type messages (already handled above)
            if ("nls_context".equals(msg.type) || "context".equals(msg.role)) {
                continue;
            }

            int msgTokens = estimateTokens(msg.content);
            if (usedTokens + msgTokens > MAX_TOTAL_TOKENS) {
                Log.w(TAG, "Token budget exceeded at message " + i + ", truncating");
                break;
            }

            JSONObject apiMsg = new JSONObject();
            // Map internal roles to Claude API roles
            String apiRole = "user".equals(msg.role) ? "user" : "assistant";
            apiMsg.put("role", apiRole);

            // Include screenshot analysis context if present
            if ("screenshot_analysis".equals(msg.type) && msg.content != null) {
                apiMsg.put("content", "[Screenshot analysis: " + msg.content + "]");
            } else {
                apiMsg.put("content", msg.content);
            }

            apiMessages.put(apiMsg);
            usedTokens += msgTokens;
        }

        Log.d(TAG, "Built context: " + apiMessages.length() + " messages, ~" + usedTokens + " tokens");
        return apiMessages;
    }

    /**
     * Get the system prompt for Claude API calls.
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Estimate token count for a string.
     */
    public int estimateTokens(String text) {
        if (text == null) return 0;
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Check if session needs summarization (too many messages for context window).
     */
    public boolean needsSummarization(List<ChatMessage> messages) {
        if (messages.size() <= MAX_RECENT_MESSAGES) return false;
        int totalTokens = 0;
        for (ChatMessage msg : messages) {
            totalTokens += estimateTokens(msg.content);
        }
        return totalTokens > MAX_TOTAL_TOKENS;
    }

    /**
     * Build a summarization prompt for older messages.
     * Send this to Claude to get a summary, then store in ChatSession.summary.
     */
    public String buildSummarizationPrompt(List<ChatMessage> olderMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summarize this conversation in 2-3 sentences, ");
        sb.append("preserving key facts, names, decisions, and action items:\n\n");
        for (ChatMessage msg : olderMessages) {
            sb.append(msg.role).append(": ").append(msg.content).append("\n");
        }
        return sb.toString();
    }

    private String buildSystemPrompt() {
        return "You are WittyKeys AI, a helpful assistant integrated into an Android keyboard. "
                + "You help users compose messages, analyze screenshots, and provide contextual suggestions. "
                + "Keep responses concise (under 150 words) since you're in a compact keyboard view. "
                + "Be conversational and helpful. "
                + "When analyzing screenshots, describe what you see and suggest relevant reply options. "
                + "When given conversation context from notifications, use it to provide better, more relevant help.";
    }
}
