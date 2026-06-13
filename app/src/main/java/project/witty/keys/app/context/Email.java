package project.witty.keys.app.context;

import android.os.Parcel;
import java.util.List;

public class Email extends ScreenContext {
    private final String from;
    private final List<String> to;
    private final String subject;
    private final String body;

    public Email(String appName, String from, List<String> to, String subject, String body) {
        super(appName, "Email Thread");
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.body = body;
    }

    // --- Getters ---
    public String getFrom() { return from; }
    public List<String> getTo() { return to; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }

    // --- Parcelable Implementation ---
    protected Email(Parcel in) {
        super(in); // Reads appName and viewType
        from = in.readString();
        to = in.createStringArrayList();
        subject = in.readString();
        body = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags); // Writes appName and viewType
        dest.writeString(from);
        dest.writeStringList(to);
        dest.writeString(subject);
        dest.writeString(body);
    }

    public static final Creator<Email> CREATOR = new Creator<Email>() {
        @Override
        public Email createFromParcel(Parcel in) {
            return new Email(in);
        }

        @Override
        public Email[] newArray(int size) {
            return new Email[size];
        }
    };
}