package project.witty.keys.keyboard.AiChat;

public enum CtaType {
    NONE,            // For static/debug transcript bubbles with no action chips
    REPLY_COPY,      // For standard AI Chat
    APPLY_COPY,      // For Grammar/Translation
    SUGGESTIONS      // For Tones/Replies
    ,
    /**
     * For flows that support copying the result, replying with context, and requesting a fresh
     * variation of the same prompt ("regenerate"). The UI will present three buttons: Copy,
     * Reply, and Regenerate. Clicking Regenerate will repeat the last AI call with the same
     * prompt to fetch a new alternative response.
     */
    REGENERATE_COPY_REPLY
}
