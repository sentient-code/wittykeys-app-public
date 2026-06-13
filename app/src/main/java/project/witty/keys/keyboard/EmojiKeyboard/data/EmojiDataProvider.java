package project.witty.keys.keyboard.EmojiKeyboard.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import project.witty.keys.R;

/**
 * Parses emojis.json and provides categorised emoji data.
 * <p>
 * Lazy singleton — data is parsed on first access and cached in memory.
 * Category assignment uses keyword matching on description, aliases and tags
 * rather than Unicode code-point ranges, because the JSON dataset includes
 * composite sequences (ZWJ families, flag pairs) whose code points span
 * multiple Unicode blocks.
 */
public final class EmojiDataProvider {

    private static final String TAG = "WK_EMOJI_DATA";

    // Category names — order defines display order
    public static final String CAT_RECENTS          = "Recents";
    public static final String CAT_SMILEYS          = "Smileys & People";
    public static final String CAT_DATING           = "Dating & Romance";
    public static final String CAT_GESTURES         = "Gestures & Body";
    public static final String CAT_ANIMALS          = "Animals & Nature";
    public static final String CAT_FOOD             = "Food & Drink";
    public static final String CAT_ACTIVITIES       = "Activities & Sports";
    public static final String CAT_TRAVEL           = "Travel & Places";
    public static final String CAT_OBJECTS          = "Objects";
    public static final String CAT_SYMBOLS_FLAGS    = "Symbols & Flags";

    private static final String[] CATEGORY_ORDER = {
            CAT_RECENTS, CAT_SMILEYS, CAT_DATING, CAT_GESTURES,
            CAT_ANIMALS, CAT_FOOD, CAT_ACTIVITIES, CAT_TRAVEL,
            CAT_OBJECTS, CAT_SYMBOLS_FLAGS
    };

    // Singleton
    private static EmojiDataProvider sInstance;
    private boolean mLoaded = false;

    // All emojis (order preserved from JSON)
    private final List<EmojiEntry> mAllEmojis = new ArrayList<>();

    // Category → list of EmojiEntry (preserves insertion order)
    private final LinkedHashMap<String, List<EmojiEntry>> mCategoryMap = new LinkedHashMap<>();

    private EmojiDataProvider() {
        for (String cat : CATEGORY_ORDER) {
            mCategoryMap.put(cat, new ArrayList<>());
        }
    }

    public static synchronized EmojiDataProvider getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new EmojiDataProvider();
        }
        if (!sInstance.mLoaded) {
            sInstance.loadFromJson(context.getApplicationContext());
        }
        return sInstance;
    }

    // ──────────────────────── Public API ────────────────────────

    /** Category names in display order (includes "Recents" at index 0). Static — no instance needed. */
    public static String[] getCategoryNamesStatic() {
        return CATEGORY_ORDER.clone();
    }

    /** Category names in display order (includes "Recents" at index 0). */
    public String[] getCategoryNames() {
        return CATEGORY_ORDER.clone();
    }

    /** Category names excluding Recents — for data categories only. */
    public String[] getDataCategoryNames() {
        return Arrays.copyOfRange(CATEGORY_ORDER, 1, CATEGORY_ORDER.length);
    }

    /** Emoji characters for a given category. Returns empty array if unknown. */
    public String[] getEmojisForCategory(String category) {
        List<EmojiEntry> entries = mCategoryMap.get(category);
        if (entries == null || entries.isEmpty()) return new String[0];
        String[] result = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            result[i] = entries.get(i).emojiChar;
        }
        return result;
    }

    /** Full EmojiEntry list for a category. */
    public List<EmojiEntry> getEntriesForCategory(String category) {
        List<EmojiEntry> entries = mCategoryMap.get(category);
        return entries != null ? Collections.unmodifiableList(entries) : Collections.emptyList();
    }

    /** Every emoji entry. */
    public List<EmojiEntry> getAllEmojis() {
        return Collections.unmodifiableList(mAllEmojis);
    }

    /** Total number of parsed emojis. */
    public int getTotalCount() {
        return mAllEmojis.size();
    }

    /** Count per category (for diagnostics). */
    public Map<String, Integer> getCategoryCounts() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, List<EmojiEntry>> e : mCategoryMap.entrySet()) {
            counts.put(e.getKey(), e.getValue().size());
        }
        return counts;
    }

    // ──────────────────────── Parsing ────────────────────────

    private void loadFromJson(Context context) {
        long start = System.currentTimeMillis();
        try {
            String json = readRawResource(context, R.raw.emojis);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String emojiChar = obj.optString("emojiChar", "");
                if (emojiChar.isEmpty()) continue;

                String description = obj.optString("description", "").toLowerCase(Locale.US);
                List<String> aliases = jsonArrayToList(obj.optJSONArray("aliases"));
                List<String> tags = jsonArrayToList(obj.optJSONArray("tags"));

                String category = categorise(emojiChar, description, aliases, tags);

                EmojiEntry entry = new EmojiEntry(emojiChar, description, aliases, tags, category);
                mAllEmojis.add(entry);

                List<EmojiEntry> list = mCategoryMap.get(category);
                if (list != null) {
                    list.add(entry);
                }
            }
            mLoaded = true;
            long elapsed = System.currentTimeMillis() - start;
            Log.d(TAG, "Parsed " + mAllEmojis.size() + " emojis in " + elapsed + "ms");
            for (Map.Entry<String, List<EmojiEntry>> e : mCategoryMap.entrySet()) {
                if (!e.getKey().equals(CAT_RECENTS)) {
                    Log.d(TAG, "  " + e.getKey() + ": " + e.getValue().size());
                }
            }
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to parse emojis.json", e);
        }
    }

    // ──────────────────────── Categorisation ────────────────────────

    /**
     * Assign an emoji to a category based on its description, aliases and tags.
     * Order matters — first match wins, so more specific categories (Dating)
     * are checked before broader ones (Smileys).
     */
    private String categorise(String emojiChar, String desc,
                              List<String> aliases, List<String> tags) {
        // Combine all text for keyword matching
        String combined = desc;
        for (String a : aliases) combined += " " + a.toLowerCase(Locale.US);
        for (String t : tags) combined += " " + t.toLowerCase(Locale.US);

        // ── Flags first (very specific pattern) ──
        if (containsAny(tags, "flag") || desc.contains("regional indicator")
                || desc.contains("flag")) {
            return CAT_SYMBOLS_FLAGS;
        }

        // ── Dating & Romance (WK custom, check early) ──
        if (containsAny(tags, "love", "heart", "kiss", "romance", "valentine",
                "couple", "wedding", "bride", "ring") ||
            containsAny(aliases, "heart", "kiss", "cupid", "love_letter",
                "love_you", "couple", "wedding", "ring", "bouquet", "rose",
                "sparkling_heart", "two_hearts", "revolving_hearts",
                "heart_with_arrow", "heart_with_ribbon", "broken_heart",
                "red_heart", "orange_heart", "yellow_heart", "green_heart",
                "blue_heart", "purple_heart", "black_heart", "white_heart",
                "brown_heart", "heart_exclamation", "growing_heart",
                "beating_heart", "heartbeat", "heartpulse",
                "heart_decoration", "heavy_heart_exclamation_mark_ornament") ||
            desc.contains("heart") || desc.contains("kiss") ||
            desc.contains("love") || desc.contains("couple") ||
            desc.contains("wedding") || desc.contains("bride") ||
            desc.contains("ring")) {
            return CAT_DATING;
        }

        // ── Animals & Nature ──
        if (containsAny(tags, "animal", "nature", "pet", "bird", "fish",
                "insect", "plant", "flower", "weather", "ocean", "sea") ||
            desc.contains("face") && (desc.contains("cat") || desc.contains("dog")
                || desc.contains("monkey") || desc.contains("bear")
                || desc.contains("frog") || desc.contains("mouse")
                || desc.contains("rabbit") || desc.contains("hamster")
                || desc.contains("tiger") || desc.contains("lion")) ||
            matchesAnimalNatureDescription(desc)) {
            return CAT_ANIMALS;
        }

        // ── Food & Drink ──
        if (containsAny(tags, "food", "fruit", "drink", "vegetable", "meal") ||
            matchesFoodDescription(desc)) {
            return CAT_FOOD;
        }

        // ── Activities & Sports ──
        if (containsAny(tags, "sport", "activity", "game", "competition",
                "medal", "trophy", "ball", "running", "swimming",
                "cycling", "skiing", "basketball", "football", "soccer",
                "tennis", "cricket", "baseball", "golf", "surfing") ||
            matchesActivityDescription(desc)) {
            return CAT_ACTIVITIES;
        }

        // ── Travel & Places ──
        if (containsAny(tags, "travel", "place", "building", "vehicle",
                "transport", "city", "country", "map", "globe") ||
            matchesTravelDescription(desc)) {
            return CAT_TRAVEL;
        }

        // ── Gestures & Body (exclude face emojis — those belong in Smileys) ──
        if (!desc.contains("face") &&
            (containsAny(tags, "hand", "gesture", "body", "finger", "fist") ||
            matchesGestureDescription(desc))) {
            return CAT_GESTURES;
        }

        // ── Smileys & People (face, person, emoji) ──
        if (matchesSmileyDescription(desc) || containsAny(tags, "face", "happy",
                "sad", "angry", "laugh", "cry", "smile", "person", "man",
                "woman", "boy", "girl", "baby", "family", "people")) {
            return CAT_SMILEYS;
        }

        // ── Symbols (math, arrow, zodiac, etc.) ──
        if (matchesSymbolDescription(desc) || containsAny(tags, "symbol",
                "arrow", "sign", "number", "letter", "zodiac", "math",
                "music", "note")) {
            return CAT_SYMBOLS_FLAGS;
        }

        // ── Default: Objects ──
        return CAT_OBJECTS;
    }

    // ──────────────────────── Keyword matchers ────────────────────────

    private boolean matchesAnimalNatureDescription(String desc) {
        String[] keywords = {"dog", "cat", "mouse", "hamster", "rabbit", "fox",
                "bear", "panda", "koala", "tiger", "lion", "cow", "pig",
                "frog", "monkey", "chicken", "penguin", "bird", "eagle",
                "duck", "owl", "bat", "shark", "whale", "dolphin",
                "fish", "octopus", "snail", "butterfly", "bug", "ant",
                "bee", "beetle", "spider", "scorpion", "crab", "lobster",
                "shrimp", "squid", "turtle", "snake", "lizard",
                "dinosaur", "dragon", "horse", "unicorn", "deer",
                "goat", "sheep", "camel", "llama", "giraffe", "elephant",
                "rhinoceros", "hippopotamus", "gorilla", "orangutan",
                "chipmunk", "hedgehog", "otter", "sloth", "skunk",
                "kangaroo", "beaver", "bison", "mammoth", "seal",
                "tree", "flower", "blossom", "rose", "tulip", "hibiscus",
                "sunflower", "bouquet", "seedling", "herb", "leaf",
                "clover", "maple", "mushroom", "cactus", "palm",
                "evergreen", "deciduous", "wood", "fallen_leaf",
                "sun", "moon", "star", "cloud", "rain", "snow",
                "lightning", "tornado", "fog", "wind", "rainbow",
                "wave", "ocean", "droplet", "volcano", "earth",
                "globe", "comet", "fire", "snowflake", "snowman",
                "thermometer"};
        return containsAnyWord(desc, keywords);
    }

    private boolean matchesFoodDescription(String desc) {
        String[] keywords = {"apple", "pear", "peach", "cherry", "strawberry",
                "grape", "melon", "watermelon", "banana", "pineapple",
                "mango", "lemon", "orange", "kiwi", "tomato", "avocado",
                "eggplant", "potato", "carrot", "corn", "pepper",
                "cucumber", "broccoli", "garlic", "onion", "coconut",
                "bread", "croissant", "baguette", "pancake", "waffle",
                "cheese", "egg", "bacon", "burger", "hamburger", "pizza",
                "hot dog", "sandwich", "taco", "burrito", "falafel",
                "sushi", "ramen", "spaghetti", "curry", "stew",
                "dumpling", "cake", "pie", "cookie", "donut",
                "chocolate", "candy", "lollipop", "ice cream", "shaved",
                "custard", "pudding", "honey", "milk", "coffee", "tea",
                "sake", "beer", "wine", "cocktail", "glass", "cup",
                "bottle", "juice", "mate", "bubble_tea", "rice",
                "meat", "poultry", "bone", "fried", "food", "fruit",
                "dish", "bowl", "chopstick", "fork", "knife", "spoon",
                "plate"};
        return containsAnyWord(desc, keywords);
    }

    private boolean matchesActivityDescription(String desc) {
        String[] keywords = {"sport", "ball", "tennis", "basketball", "football",
                "soccer", "baseball", "softball", "volleyball", "rugby",
                "badminton", "cricket", "hockey", "lacrosse", "ping_pong",
                "table_tennis", "ski", "sled", "curling", "snowboard",
                "ice_skate", "swim", "surf", "row", "canoe", "kayak",
                "climb", "run", "walk", "bicycle", "cycle", "golf",
                "horse_racing", "skateboard", "wrestling", "boxing",
                "martial", "judo", "fencing", "medal", "trophy",
                "championship", "competition", "ticket", "fishing",
                "diving", "water_polo", "handball", "yoga", "gymnast",
                "cartwheel", "juggling", "playground", "ferris",
                "roller_coaster", "carousel", "game", "chess", "dart",
                "bowling", "billiard", "pool", "arcade", "video_game",
                "joystick", "puzzle", "kite", "firecracker",
                "sparkler", "art", "paint", "performing", "theatre",
                "circus", "guitar", "violin", "saxophone", "trumpet",
                "drum", "piano", "banjo", "accordion", "microphone"};
        return containsAnyWord(desc, keywords);
    }

    private boolean matchesTravelDescription(String desc) {
        String[] keywords = {"car", "taxi", "bus", "trolley", "train", "railway",
                "metro", "tram", "monorail", "locomotive", "truck",
                "ambulance", "fire_engine", "police", "motorcycle",
                "bicycle", "scooter", "airplane", "plane", "helicopter",
                "rocket", "satellite", "ship", "boat", "ferry",
                "speedboat", "sailboat", "canoe", "anchor", "fuel",
                "construction", "traffic", "vertical_traffic",
                "stop_sign", "railway", "station", "mountain",
                "camping", "tent", "beach", "desert", "island",
                "national_park", "stadium", "classical_building",
                "building_construction", "factory", "house", "home",
                "school", "hospital", "bank", "hotel", "store",
                "castle", "church", "mosque", "synagogue", "temple",
                "statue", "tower", "bridge", "fountain", "sunrise",
                "sunset", "cityscape", "city", "night", "milky_way",
                "map", "compass", "luggage", "backpack", "suitcase",
                "passport", "world", "globe"};
        return containsAnyWord(desc, keywords);
    }

    private boolean matchesGestureDescription(String desc) {
        String[] keywords = {"hand", "finger", "thumb", "fist", "palm",
                "pointing", "pinch", "wave", "raised", "ok_hand",
                "victory", "crossed_fingers", "vulcan", "horns",
                "call_me", "backhand", "clap", "handshake", "pray",
                "folded_hands", "writing", "nail_polish", "selfie",
                "flexed", "bicep", "leg", "foot", "ear", "nose",
                "eye", "tongue", "lip", "mouth", "tooth", "bone",
                "brain", "anatomical", "heart_organ", "lungs",
                "muscle", "mechanical"};
        return containsAnyWord(desc, keywords);
    }

    private boolean matchesSmileyDescription(String desc) {
        String[] keywords = {"face", "grinning", "smile", "smiling", "laugh",
                "joy", "rofl", "wink", "blush", "savour", "relieved",
                "sunglasses", "smirk", "neutral", "expressionless",
                "unamused", "sweat", "pensive", "confused",
                "confounded", "disappointed", "worried", "angry",
                "pouting", "crying", "persevering", "triumph",
                "frowning", "anguished", "fearful", "weary",
                "sleepy", "tired", "grimacing", "astonished",
                "flushed", "sleeping", "dizzy", "mouth", "mask",
                "thermometer", "bandage", "nauseated", "vomiting",
                "sneezing", "hot_face", "cold_face", "woozy",
                "exploding_head", "cowboy", "partying", "disguised",
                "pleading", "monocle", "nerd", "thinking",
                "eyebrow", "shushing", "symbols_on_mouth",
                "money_mouth", "hugging", "yawning", "zany",
                "rolling_eyes", "zipper_mouth", "lying",
                "drooling", "face_palm", "shrug", "person",
                "man", "woman", "boy", "girl", "baby", "child",
                "adult", "older", "family", "people",
                "superhero", "villain", "mage", "fairy", "vampire",
                "merperson", "elf", "genie", "zombie", "troll",
                "skull", "ghost", "alien", "robot", "smiley_cat",
                "clown", "ogre", "goblin", "poo", "imp",
                "angel", "santa", "mrs_claus", "detective",
                "guard", "construction_worker", "prince", "princess",
                "turban", "skullcap", "headscarf", "blond_hair",
                "red_hair", "curly_hair", "white_hair", "bald",
                "beard", "pregnant", "breast_feeding", "ninja"};
        return containsAnyWord(desc, keywords);
    }

    private boolean matchesSymbolDescription(String desc) {
        String[] keywords = {"arrow", "triangle", "diamond", "circle", "square",
                "button", "cross", "check", "star_of", "six_pointed",
                "asterisk", "eight_spoked", "sparkle", "exclamation",
                "question", "copyright", "registered", "trademark",
                "hash", "keycap", "number", "letter", "abc",
                "input", "cool", "free", "new", "sos", "up", "ok",
                "atm", "zodiac", "aries", "taurus", "gemini",
                "cancer", "leo", "virgo", "libra", "scorpio",
                "sagittarius", "capricorn", "aquarius", "pisces",
                "ophiuchus", "warning", "caution", "radioactive",
                "biohazard", "no_entry", "prohibited", "recycle",
                "fleur_de_lis", "trident", "infinity", "wheelchair",
                "medical", "pirate", "peace", "yin_yang",
                "latin", "ideograph", "congratulations", "secret",
                "bangbang", "interrobang", "hundred", "currency",
                "dollar", "euro", "pound", "yen", "heavy_plus",
                "heavy_minus", "heavy_multiplication", "heavy_division",
                "wavy_dash", "curly_loop", "tm", "information",
                "musical_note", "notes"};
        return containsAnyWord(desc, keywords);
    }

    // ──────────────────────── Helpers ────────────────────────

    private static boolean containsAny(List<String> list, String... keywords) {
        for (String item : list) {
            String lower = item.toLowerCase(Locale.US);
            for (String kw : keywords) {
                if (lower.contains(kw)) return true;
            }
        }
        return false;
    }

    private static boolean containsAnyWord(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static List<String> jsonArrayToList(JSONArray arr) {
        if (arr == null) return Collections.emptyList();
        List<String> list = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            String val = arr.optString(i, "");
            if (!val.isEmpty()) list.add(val);
        }
        return list;
    }

    private static String readRawResource(Context context, int resId) throws IOException {
        InputStream is = context.getResources().openRawResource(resId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int read;
        while ((read = reader.read(buf)) != -1) {
            sb.append(buf, 0, read);
        }
        reader.close();
        return sb.toString();
    }
}
