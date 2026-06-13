package project.witty.keys.app.entities;// In ChatGptScreenAnalysis.java

import org.json.JSONArray;
import org.json.JSONException; // Import JSONException
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ChatGptScreenAnalysis {
    public String category;
    public String summary;
    public List<ConversationMessage> conversation;

    public ChatGptScreenAnalysis() {
        this.conversation = new ArrayList<>();
    }

    // Modified fromJson to accept a String
    public static ChatGptScreenAnalysis fromJsonString(String jsonString) throws JSONException { // Add throws declaration
        if (jsonString == null || jsonString.isEmpty()) {
            return new ChatGptScreenAnalysis(); // Return empty or handle as error
        }
        JSONObject jsonObject = new JSONObject(jsonString); // Parse the string here
        ChatGptScreenAnalysis analysis = new ChatGptScreenAnalysis();
        analysis.category = jsonObject.optString("category");
        analysis.summary = jsonObject.optString("summary");
        JSONArray conversationArray = jsonObject.optJSONArray("conversation");
        if (conversationArray != null) {
            for (int i = 0; i < conversationArray.length(); i++) {
                JSONObject msgObject = conversationArray.optJSONObject(i);
                if (msgObject != null) {
                    analysis.conversation.add(ConversationMessage.fromJson(msgObject)); // Assuming ConversationMessage.fromJson still takes JSONObject
                }
            }
        }
        return analysis;
    }

    // Keep the original if you still have use cases for it, or rename/remove
    public static ChatGptScreenAnalysis fromJson(JSONObject jsonObject) {
        ChatGptScreenAnalysis analysis = new ChatGptScreenAnalysis();
        analysis.category = jsonObject.optString("category");
        analysis.summary = jsonObject.optString("summary");
        JSONArray conversationArray = jsonObject.optJSONArray("conversation");
        if (conversationArray != null) {
            for (int i = 0; i < conversationArray.length(); i++) {
                JSONObject msgObject = conversationArray.optJSONObject(i);
                if (msgObject != null) {
                    analysis.conversation.add(ConversationMessage.fromJson(msgObject));
                }
            }
        }
        return analysis;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ChatGptScreenAnalysis{\n");
        sb.append("  category='").append(category).append("',\n");
        sb.append("  summary='").append(summary != null ? summary : "null").append("',\n"); // Truncate summary
        sb.append("  conversation (count=").append(conversation != null ? conversation.size() : 0).append("): [\n");
        if (conversation != null && !conversation.isEmpty()) {
            for (int i = 0; i < conversation.size(); i++) {
                sb.append("    ").append(i).append(": ").append(conversation.get(i).toString());
                if (i < conversation.size() - 1) {
                    sb.append(",\n");
                } else {
                    sb.append("\n");
                }
            }
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }
}