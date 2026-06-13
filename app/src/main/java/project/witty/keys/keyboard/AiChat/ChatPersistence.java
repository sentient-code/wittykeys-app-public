package project.witty.keys.keyboard.AiChat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists AI Chat conversation history to SharedPreferences as JSON.
 *
 * Storage format:
 * - Key: "wk_chat_history"
 * - Value: JSON array of message objects with {type, text, ctaType?, timestamp}
 * - Max: 50 messages per conversation
 * - Only persists UserMessage, AiMessage, and SystemMessage (not Loading, Error, etc.)
 */
public class ChatPersistence {
    private static final String TAG = "ChatPersistence";
    private static final String PREFS_NAME = "wk_ai_chat";
    private static final String KEY_CHAT_HISTORY = "wk_chat_history";
    private static final int MAX_PERSISTED_MESSAGES = 50;

    private final SharedPreferences prefs;

    public ChatPersistence(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Save the current chat items to SharedPreferences.
     * Only persists UserMessage, AiMessage, and SystemMessage types.
     */
    public void saveConversation(List<ChatItem> items) {
        try {
            JSONArray array = new JSONArray();
            int count = 0;
            // Save from the end (most recent) up to MAX_PERSISTED_MESSAGES
            int startIdx = Math.max(0, items.size() - MAX_PERSISTED_MESSAGES);
            for (int i = startIdx; i < items.size(); i++) {
                ChatItem item = items.get(i);
                JSONObject obj = new JSONObject();

                if (item instanceof UserMessage) {
                    UserMessage msg = (UserMessage) item;
                    obj.put("type", "user");
                    obj.put("text", msg.getText());
                    obj.put("timestamp", msg.getTimestamp());
                    array.put(obj);
                    count++;
                } else if (item instanceof AiMessage) {
                    AiMessage msg = (AiMessage) item;
                    obj.put("type", "ai");
                    obj.put("text", msg.getMarkdownText());
                    obj.put("ctaType", msg.getCtaType().name());
                    obj.put("timestamp", msg.getTimestamp());
                    array.put(obj);
                    count++;
                } else if (item instanceof SystemMessage) {
                    SystemMessage msg = (SystemMessage) item;
                    obj.put("type", "system");
                    obj.put("text", msg.getText());
                    array.put(obj);
                    count++;
                }
                // Skip Loading, ErrorMessage, Options, MetadataCard — not persisted
            }

            prefs.edit().putString(KEY_CHAT_HISTORY, array.toString()).apply();
            Log.d(TAG, "Saved " + count + " messages to SharedPreferences");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save conversation", e);
        }
    }

    /**
     * Load the persisted conversation from SharedPreferences.
     * Returns an empty list if no conversation is saved.
     */
    public List<ChatItem> loadConversation() {
        List<ChatItem> items = new ArrayList<>();
        try {
            String json = prefs.getString(KEY_CHAT_HISTORY, null);
            if (json == null || json.isEmpty()) return items;

            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String type = obj.getString("type");
                long timestamp = obj.has("timestamp") ? obj.getLong("timestamp") : System.currentTimeMillis();

                switch (type) {
                    case "user":
                        items.add(new UserMessage(obj.getString("text"), timestamp));
                        break;
                    case "ai":
                        String ctaName = obj.has("ctaType") ? obj.getString("ctaType") : "REPLY_COPY";
                        CtaType ctaType;
                        try {
                            ctaType = CtaType.valueOf(ctaName);
                        } catch (IllegalArgumentException e) {
                            ctaType = CtaType.REPLY_COPY;
                        }
                        items.add(new AiMessage(obj.getString("text"), ctaType, timestamp));
                        break;
                    case "system":
                        items.add(new SystemMessage(obj.getString("text")));
                        break;
                }
            }
            Log.d(TAG, "Loaded " + items.size() + " messages from SharedPreferences");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load conversation", e);
            // Clear corrupted data
            prefs.edit().remove(KEY_CHAT_HISTORY).apply();
        }
        return items;
    }

    /**
     * Clear the persisted conversation.
     */
    public void clearConversation() {
        prefs.edit().remove(KEY_CHAT_HISTORY).apply();
        Log.d(TAG, "Cleared persisted conversation");
    }

    /**
     * Check if there's a persisted conversation.
     */
    public boolean hasPersistedConversation() {
        String json = prefs.getString(KEY_CHAT_HISTORY, null);
        return json != null && !json.isEmpty() && !json.equals("[]");
    }
}
