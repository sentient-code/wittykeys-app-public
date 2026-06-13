package project.witty.keys.app.entities; // Or your appropriate package

import org.json.JSONObject;

public class ConversationMessage {
    public String sender;
    public String message; // This is the field we'll update
    public String time;
    public String messageType;
    public String imageDescription; // Optional

    public static ConversationMessage fromJson(JSONObject jsonObject) {
        ConversationMessage msg = new ConversationMessage();
        msg.sender = jsonObject.optString("sender");
        msg.message = jsonObject.optString("message");
        msg.time = jsonObject.optString("time");
        msg.messageType = jsonObject.optString("message_type");
        msg.imageDescription = jsonObject.optString("image_description", null); // Handle optional field
        return msg;
    }

    @Override
    public String toString() {
        return "ConversationMessage{" +
                "sender='" + sender + '\'' +
                ", message='" + message + '\'' + // Truncate long messages
                ", time='" + time + '\'' +
                ", messageType='" + messageType + '\'' +
                (imageDescription != null ? ", imageDescription='" + imageDescription + '\'' : "") +
                '}';
    }
}
