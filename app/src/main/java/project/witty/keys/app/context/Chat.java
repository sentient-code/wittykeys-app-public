package project.witty.keys.app.context;

import android.os.Parcel;
import java.util.List;

public class Chat extends ScreenContext {
    private final List<String> participants;
    private final List<ChatMessage> messages;

    public Chat(String appName, List<String> participants, List<ChatMessage> messages) {
        super(appName, "Conversation");
        this.participants = participants;
        this.messages = messages;
    }

    public List<String> getParticipants() { return participants; }
    public List<ChatMessage> getMessages() { return messages; }

    protected Chat(Parcel in) {
        super(in);
        participants = in.createStringArrayList();
        messages = in.createTypedArrayList(ChatMessage.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags); // Writes appName and viewType
        dest.writeStringList(participants);
        dest.writeTypedList(messages);
    }

    public static final Creator<Chat> CREATOR = new Creator<Chat>() {
        @Override
        public Chat createFromParcel(Parcel in) {
            return new Chat(in);
        }

        @Override
        public Chat[] newArray(int size) {
            return new Chat[size];
        }
    };
}