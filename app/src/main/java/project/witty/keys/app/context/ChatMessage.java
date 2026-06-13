package project.witty.keys.app.context;

import android.os.Parcel;
import android.os.Parcelable;

public class ChatMessage implements Parcelable {
    private final String sender;
    private final String text;
    private final String timestamp;
    private final boolean isFromCurrentUser;

    public ChatMessage(String sender, String text, String timestamp, boolean isFromCurrentUser) {
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
        this.isFromCurrentUser = isFromCurrentUser;
    }

    // --- Getters ---
    public String getSender() { return sender; }
    public String getText() { return text; }
    public String getTimestamp() { return timestamp; }
    public boolean isFromCurrentUser() { return isFromCurrentUser; }

    protected ChatMessage(Parcel in) {
        sender = in.readString();
        text = in.readString();
        timestamp = in.readString();
        isFromCurrentUser = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(sender);
        dest.writeString(text);
        dest.writeString(timestamp);
        dest.writeByte((byte) (isFromCurrentUser ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ChatMessage> CREATOR = new Creator<ChatMessage>() {
        @Override
        public ChatMessage createFromParcel(Parcel in) {
            return new ChatMessage(in);
        }

        @Override
        public ChatMessage[] newArray(int size) {
            return new ChatMessage[size];
        }
    };
}