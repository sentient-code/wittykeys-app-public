package project.witty.keys.app.context;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class ScreenContext implements Parcelable {
    private final String appName;
    private final String viewType;

    protected ScreenContext(String appName, String viewType) {
        this.appName = appName;
        this.viewType = viewType;
    }

    // Parcelable constructor
    protected ScreenContext(Parcel in) {
        appName = in.readString();
        viewType = in.readString();
    }

    public String getAppName() {
        return appName;
    }

    public String getViewType() {
        return viewType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(appName);
        dest.writeString(viewType);
    }
}