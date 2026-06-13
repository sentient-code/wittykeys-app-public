package project.witty.keys.app.context;

import android.os.Parcel;

import java.util.List;

public class DatingProfile extends ScreenContext {
    private final String name;
    // Using Integer wrapper class to allow for null age
    private final Integer age;
    private final String bio;
    private final List<String> prompts; // For Hinge-like prompts
    private final List<String> interests;

    public DatingProfile(String appName, String name, Integer age, String bio, List<String> prompts, List<String> interests) {
        super(appName, "Profile");
        this.name = name;
        this.age = age;
        this.bio = bio;
        this.prompts = prompts;
        this.interests = interests;
    }

    // --- Getters ---
    public String getName() { return name; }
    public Integer getAge() { return age; }
    public String getBio() { return bio; }
    public List<String> getPrompts() { return prompts; }
    public List<String> getInterests() { return interests; }

    // --- Parcelable Implementation ---
    protected DatingProfile(Parcel in) {
        super(in); // Reads appName and viewType
        name = in.readString();
        // Handle nullable Integer for age
        if (in.readByte() == 0) {
            age = null;
        } else {
            age = in.readInt();
        }
        bio = in.readString();
        prompts = in.createStringArrayList();
        interests = in.createStringArrayList();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags); // Writes appName and viewType
        dest.writeString(name);
        // Handle nullable Integer for age
        if (age == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeInt(age);
        }
        dest.writeString(bio);
        dest.writeStringList(prompts);
        dest.writeStringList(interests);
    }

    public static final Creator<DatingProfile> CREATOR = new Creator<DatingProfile>() {
        @Override
        public DatingProfile createFromParcel(Parcel in) {
            return new DatingProfile(in);
        }

        @Override
        public DatingProfile[] newArray(int size) {
            return new DatingProfile[size];
        }
    };
}