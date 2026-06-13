package project.witty.keys.keyboard.EmojiKeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

import project.witty.keys.R;

public class EmojiRecentsManager {

    private static final String PREFS_NAME = "WittyKeys.EmojiRecents";
    private static final String KEY_RECENT_EMOJIS = "recent_emojis";

    private final int maxRecents;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public EmojiRecentsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        maxRecents = context.getResources().getInteger(R.integer.wk_emoji_max_recents);
    }

    public List<String> getRecents() {
        String json = prefs.getString(KEY_RECENT_EMOJIS, "[]");
        Type type = new TypeToken<LinkedList<String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void addRecent(String emoji) {
        if (emoji == null || emoji.trim().isEmpty()) {
            return;
        }
        LinkedList<String> recents = new LinkedList<>(getRecents());

        // Remove the emoji if it already exists to move it to the front
        recents.remove(emoji);

        // Add the new emoji to the front of the list
        recents.addFirst(emoji);

        // Trim the list if it's too long
        while (recents.size() > maxRecents) {
            recents.removeLast();
        }

        // Save the updated list
        String json = gson.toJson(recents);
        prefs.edit().putString(KEY_RECENT_EMOJIS, json).apply();
    }
}