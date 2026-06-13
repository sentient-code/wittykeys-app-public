package project.witty.keys.app.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Defines a structured hierarchy of reply options for generating AI responses.
 * The structure is designed to be intuitive for users and provide clear, actionable
 * instructions for the AI prompt generation.
 *
 * The hierarchy is organized into top-level "Intents" (e.g., Respond, React) which
 * contain specific "Actions" (e.g., Agree, Express Humor).
 */
public class ToneData {
    // Existing toneEmojiMap - remains unchanged as per your request
    private static final Map<String, String> toneEmojiMap;
    private static final Map<String, Map<String, String>> replyHierarchy;

    static {

        // Initialize existing toneEmojiMap (copied from your original code)
        Map<String, String> tones = new HashMap<>();
        tones.put("Flirty", "😉");
        tones.put("Formal", "🌟");
        tones.put("Casual", "😎");
        tones.put("Professional", "💼");
        tones.put("Playful", "😂");
        tones.put("Savage", "🔥");
        tones.put("Sarcastic", "🙄");
        tones.put("Sassy", "💅");
        tones.put("Teasing", "😜");
        tones.put("Quirky", "🤪");
        tones.put("Concise", "🎯");
        tones.put("Romantic", "💌");
        tones.put("Descriptive", "📝");
        tones.put("Persuasive", "🧠");
        tones.put("Witty", "🎭");
        tones.put("Inspirational", "✨");
        tones.put("Technical", "⚙️");
        tones.put("Urgent", "🚨");
        tones.put("Calm", "🧘");
        tones.put("Curious", "🤔");
        tones.put("Polite", "🙏");
        toneEmojiMap = Collections.unmodifiableMap(tones);

        Map<String, Map<String, String>> hierarchy = new LinkedHashMap<>();

        //================================================================================
        // LEVEL 1: INITIATE - For starting a new chat or replying to a profile.
        //================================================================================
        Map<String, String> initiateActions = new LinkedHashMap<>();
        initiateActions.put("Opener for Profile", "👋");
        initiateActions.put("Pickup Line (Rizz)", "✨");
        initiateActions.put("Conversation Starter", "💬");
        hierarchy.put("Initiate", Collections.unmodifiableMap(initiateActions));

        //================================================================================
        // LEVEL 1: RESPOND - For directly replying to the previous message.
        //================================================================================
        Map<String, String> respondActions = new LinkedHashMap<>();
        respondActions.put("Agree", "👍");
        respondActions.put("Disagree", "👎");
        respondActions.put("Sharp Comeback", "🔥"); // Covers "Savage" or "Sassy"
        respondActions.put("Savage Comeback", "🔥");
        respondActions.put("Confident Reply", "💪");
        respondActions.put("Reply to Image", "📸");
        hierarchy.put("Respond", Collections.unmodifiableMap(respondActions));

        //================================================================================
        // LEVEL 1: REACT - For expressing an emotion or feeling.
        //================================================================================
        Map<String, String> reactActions = new LinkedHashMap<>();
        reactActions.put("Funny / Witty", "😂");
        reactActions.put("Empathetic", "🤗");
        reactActions.put("Sarcastic Remark", "🙄");
        reactActions.put("Casual / Chill", "😎");
        reactActions.put("Surprised", "😮");
        hierarchy.put("React", Collections.unmodifiableMap(reactActions));

        //================================================================================
        // LEVEL 1: FLIRT & BANTER - For romantic, teasing, or bold interactions.
        // This category safely houses the intents behind "Smash", "Dirty", and "Spicy".
        //================================================================================
        Map<String, String> flirtActions = new LinkedHashMap<>();
        // AI Prompt Hint: "Generate a sweet and romantic message."
        flirtActions.put("Romantic", "❤️");
        // AI Prompt Hint: "Generate a lighthearted, playful, or teasing reply."
        flirtActions.put("Playful Tease", "😉");
        flirtActions.put("Sassy Reply", "💁‍♀️");
        // This is the safe alternative to "Spicy"
        // AI Prompt Hint: "Generate a provocative or edgy reply to make the conversation more exciting. It can be a little sassy or challenging."
        flirtActions.put("Add Some Spice", "🌶️");
        // This is the safe alternative to "Dirty"
        // AI Prompt Hint: "Generate a reply with clever innuendo or a suggestive double-entendre. It should be playful and witty, not vulgar or explicit."
        flirtActions.put("Daring Banter", "😈");
        // This is the safe alternative to "Smash"
        // AI Prompt Hint: "Generate a confident, direct message to express strong romantic or physical interest. Be bold and make a move, but remain charming and respectful, not crude."
        flirtActions.put("Make a Bold Move", "😏");
        hierarchy.put("Rizz/Dating", Collections.unmodifiableMap(flirtActions));


        //================================================================================
        // LEVEL 1: ADVANCE - For proactively moving the conversation forward.
        //================================================================================
        Map<String, String> advanceActions = new LinkedHashMap<>();
        advanceActions.put("Ask a Question", "❓");
        advanceActions.put("Suggest a Plan", "📅");
        advanceActions.put("Give a Compliment", "💖");
        advanceActions.put("Offer Advice", "💡");
        advanceActions.put("Change the Subject", "↪️");
        hierarchy.put("Advance Conv", Collections.unmodifiableMap(advanceActions));

        replyHierarchy = Collections.unmodifiableMap(hierarchy);
    }

    public static Map<String, String> getToneEmojiMap() {
        return toneEmojiMap;
    }

    public static String getEmojiForTone(String tone) {
        return toneEmojiMap.get(tone);
    }

    /**
     * Gets the entire reply hierarchy.
     * @return An unmodifiable map of Level 1 Intents to their Level 2 Actions.
     */
    public static Map<String, Map<String, String>> getReplyHierarchy() {
        return replyHierarchy;
    }

    /**
     * Gets the set of Level 1 intent categories (e.g., "Respond", "React").
     * @return An unmodifiable, ordered set of Level 1 intent names.
     */
    public static Set<String> getLevel1Intents() {
        return replyHierarchy.keySet();
    }

    /**
     * Gets the Level 2 actions (name -> emoji map) for a given Level 1 intent.
     * @param level1Intent The name of the Level 1 intent.
     * @return An unmodifiable, ordered map of Level 2 actions, or an empty map if not found.
     */
    public static Map<String, String> getLevel2Actions(String level1Intent) {
        return replyHierarchy.getOrDefault(level1Intent, Collections.emptyMap());
    }
}