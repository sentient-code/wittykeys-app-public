package project.witty.keys.app.context;

import android.os.Parcel;

public class Generic extends ScreenContext {
    private final String screenText;

    public Generic(String appName, String screenText) {
        super(appName, "Generic Screen");
        this.screenText = screenText;
    }

    // --- Getter ---
    public String getScreenText() {
        return screenText;
    }

    // --- Parcelable Implementation ---
    protected Generic(Parcel in) {
        super(in); // Reads appName and viewType
        screenText = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags); // Writes appName and viewType
        dest.writeString(screenText);
    }

    public static final Creator<Generic> CREATOR = new Creator<Generic>() {
        @Override
        public Generic createFromParcel(Parcel in) {
            return new Generic(in);
        }

        @Override
        public Generic[] newArray(int size) {
            return new Generic[size];
        }
    };
}