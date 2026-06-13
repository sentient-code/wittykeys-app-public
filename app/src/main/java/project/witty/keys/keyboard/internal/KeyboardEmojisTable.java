package project.witty.keys.keyboard.internal;

import java.util.HashMap;

public final class KeyboardEmojisTable {
    // Name to index map.
    private static final HashMap<String, HashMap<String, Integer>> sCategoryNameToIndexesMap = new HashMap<>();
    private static final HashMap<String, String[]> sCategoryToEmojisMap = new HashMap<>();

    // Smileys category
    private static final String[] EMOJI_SMILEYS_NAMES = {
            "grinning_face", "grinning_face_with_smiling_eyes", "face_with_tears_of_joy",
            "smiling_face_with_open_mouth", "smiling_face_with_open_mouth_and_smiling_eyes",
            "smiling_face_with_open_mouth_and_cold_sweat", "smiling_face_with_open_mouth_and_tightly_closed_eyes",
            "smiling_face_with_halo", "smiling_face_with_horns", "winking_face",
            "smiling_face_with_smiling_eyes", "face_savouring_delicious_food", "relieved_face",
            "smiling_face_with_heart_shaped_eyes", "smiling_face_with_sunglasses", "smirking_face",
            "neutral_face", "expressionless_face", "unamused_face", "face_with_cold_sweat",
            "pensive_face", "confused_face", "confounded_face", "kissing_face",
            "face_throwing_a_kiss", "kissing_face_with_smiling_eyes", "kissing_face_with_closed_eyes",
            "face_with_stuck_out_tongue", "face_with_stuck_out_tongue_and_winking_eye",
            "face_with_stuck_out_tongue_and_tightly_closed_eyes", "disappointed_face",
            "worried_face", "angry_face", "pouting_face", "crying_face", "persevering_face",
            "face_with_look_of_triumph", "disappointed_but_relieved_face", "frowning_face_with_open_mouth",
            "anguished_face", "fearful_face", "weary_face", "sleepy_face", "tired_face",
            "grimacing_face", "loudly_crying_face", "face_with_open_mouth", "hushed_face",
            "face_with_open_mouth_and_cold_sweat", "face_screaming_in_fear", "astonished_face",
            "flushed_face", "sleeping_face", "dizzy_face", "face_without_mouth", "face_with_medical_mask",
            "face_with_thermometer", "face_with_head_bandage", "nauseated_face", "face_vomiting",
            "sneezing_face", "hot_face", "cold_face", "woozy_face", "knocked_out_face",
            "exploding_head", "cowboy_hat_face", "partying_face", "disguised_face",
            "pleading_face", "face_with_monocle", "nerd_face", "thinking_face",
            "face_with_raised_eyebrow", "shushing_face", "face_with_symbols_on_mouth",
            "money_mouth_face", "hugging_face", "face_with_hand_over_mouth", "yawning_face",
            "zany_face", "rolling_eyes_face", "zipper_mouth_face", "lying_face",
            "drooling_face", "face_palm", "shrug", "smiling_face_with_tear", "saluting_face",
            "melting_face", "face_with_peeking_eye", "face_holding_back_tears"
    };

    private static final String[] EMOJIS_SMILEYS = {
            "😀", "😁", "😂", "😃", "😄", "😅", "😆", "😇", "😈", "😉",
            "😊", "😋", "😌", "😍", "😎", "😏", "😐", "😑", "😒", "😓",
            "😔", "😕", "😖", "😗", "😘", "😙", "😚", "😛", "😜", "😝",
            "😞", "😟", "😠", "😡", "😢", "😣", "😤", "😥", "😦", "😧",
            "😨", "😩", "😪", "😫", "😬", "😭", "😮", "😯", "😰", "😱",
            "😲", "😳", "😴", "😵", "😶", "😷", "🤒", "🤕", "🤢", "🤮",
            "🤧", "🥵", "🥶", "🥴", "😵‍💫", "🤯", "🤠", "🥳", "🥸",
            "🥺", "🧐", "🤓", "🤔", "🤨", "🤫", "🤬", "🤑", "🤗",
            "🤭", "🥱", "🤪", "🙄", "🤐", "🤥", "🤤", "🤦", "🤷", "🥲", "🫡",
            "🫠", "🫣", "🥹"
    };

    // New Dating category
    private static final String[] DATING_EMOJIS = {
            "😉", "😘", "😍", "😏", "😈", "😇", "❤️", "🔥", "💋", "🌹",
            "🍑", "🍆", "💦", "🍷", "🍸", "🥂", "🛏️", "💘", "💖", "💕",
            "✨", "😜", "🥵", "🥺", "🤗", "👉", "👇", "🤙", "🤟", "🫶",
            "💌", "🔑", "🍒", "🍭", "👀", "💃", "🕺", "🛌", "🛀", "-1"
    };

    private static final String[] DATING_EMOJI_NAMES = {
            "winking_face", "face_throwing_a_kiss", "smiling_face_with_heart_shaped_eyes", "smirking_face",
            "smiling_face_with_horns", "smiling_face_with_halo", "red_heart", "fire", "kiss_mark", "rose",
            "peach", "eggplant", "sweat_droplets", "wine_glass", "cocktail_glass", "clinking_glasses",
            "bed", "heart_with_arrow", "sparkling_heart", "two_hearts", "sparkles",
            "face_with_stuck_out_tongue_and_winking_eye", "hot_face", "pleading_face", "hugging_face",
            "pointing_right", "pointing_down", "call_me_hand", "love_you_gesture", "heart_hands",
            "love_letter", "key", "cherries", "lollipop", "eyes", "woman_dancing", "man_dancing",
            "person_in_bed", "person_taking_bath", "drooling_face"
    };

    // Hand Gestures category
    private static final String[] HAND_GESTURE_EMOJIS = {
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "✌️", "🤞", "🤟",
            "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎",
            "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏",
            "✍️", "💅", "🤳", "💪", "🦶", "🦵", "🦴", "👊🏻", "👊🏿", "👍🏻",
            "🤏", "🫶", "🫲", "🫱", "🫳", "🫴"
    };

    private static final String[] HAND_GESTURE_EMOJI_NAMES = {
            "waving_hand", "raised_back_of_hand", "raised_hand_with_fingers_splayed",
            "raised_hand", "vulcan_salute", "ok_hand", "pinched_fingers", "victory_hand",
            "crossed_fingers", "love_you_gesture", "sign_of_the_horns", "call_me_hand",
            "pointing_left", "pointing_right", "pointing_up", "middle_finger",
            "pointing_down", "pointing_up_index", "thumbs_up", "thumbs_down",
            "raised_fist", "oncoming_fist", "left_facing_fist", "right_facing_fist",
            "clapping_hands", "raising_hands", "open_hands", "palms_up_together",
            "handshake", "folded_hands", "writing_hand", "nail_polish", "selfie",
            "flexed_biceps", "foot", "leg", "bone", "oncoming_fist_light_skin",
            "oncoming_fist_dark_skin", "thumbs_up_light_skin", "pinching_hand",
            "heart_hands", "leftwards_hand", "rightwards_hand", "palm_down_hand",
            "palm_up_hand"
    };

    // Animals category
    private static final String[] ANIMAL_EMOJIS = {
            "🐶", "🐱", "🐭", "🐹", "🐰", "🐻", "🐼", "🐨", "🐯", "🐮",
            "🐷", "🐽", "🐸", "🐵", "🐒", "🦍", "🦧", "🐔", "🐧", "🐦",
            "🐤", "🐣", "🐥", "🦄", "🐴", "🐗", "🐺", "🦊", "🦝", "🦁",
            "🐘", "🦒", "🦓", "🦘", "🦙", "🐪", "🐫", "🦌", "🐐", "🐑",
            "🐏", "🐖", "🐄", "🐃", "🐂", "🐀", "🐿️", "🦔", "🦇", "🐾",
            "🦃", "🦅", "🦆", "🦉", "🦚", "🦜", "🦢", "🦩", "🐍", "🦎",
            "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟",
            "🐬", "🦈", "🐳", "🐋", "🐊", "🐅", "🐆", "🦦", "🦥", "🦨",
            "🦬", "🦫", "🦭", "🐛", "🐜", "🐝", "🐞", "🦗", "🪳", "🕷️",
            "🦂", "🦟", "🪰", "🪱", "🦠"
    };

    private static final String[] ANIMAL_EMOJI_NAMES = {
            "dog_face", "cat_face", "mouse_face", "hamster_face", "rabbit_face",
            "bear_face", "panda_face", "koala", "tiger_face", "cow_face",
            "pig_face", "pig_nose", "frog", "monkey_face", "monkey",
            "gorilla", "orangutan", "chicken", "penguin", "bird",
            "baby_chick", "hatching_chick", "hatched_chick", "unicorn",
            "horse", "boar", "wolf", "fox", "raccoon", "lion",
            "elephant", "giraffe", "zebra", "kangaroo", "llama",
            "camel", "two_hump_camel", "deer", "goat", "sheep",
            "ram", "pig", "cow", "water_buffalo", "ox",
            "rat", "chipmunk", "hedgehog", "bat", "paw_prints",
            "turkey", "eagle", "duck", "owl", "peacock",
            "parrot", "swan", "flamingo", "snake", "lizard",
            "t_rex", "sauropod", "octopus", "squid", "shrimp",
            "lobster", "crab", "puffer_fish", "tropical_fish", "fish",
            "dolphin", "shark", "whale", "spouting_whale", "crocodile",
            "tiger", "leopard", "otter", "sloth", "skunk", "bison", "beaver", "seal",
            "bug", "ant", "bee", "lady_beetle", "cricket", "cockroach", "spider",
            "scorpion", "mosquito", "fly", "worm", "microbe"
    };

    // Food category
    private static final String[] FOOD_EMOJIS = {
            "🍎", "🍏", "🍐", "🍑", "🍒", "🍓", "🍔", "🍕", "🍖", "🍗",
            "🍍", "🥭", "🍌", "🍋", "🍊", "🍉", "🍇", "🥥", "🥝", "🍅",
            "🥕", "🥔", "🍠", "🥐", "🥖", "🥨", "🥯", "🥞", "🧀", "🍳",
            "🥚", "🥓", "🥩", "🍟", "🌭", "🌮", "🌯", "🥪", "🥙", "🍝",
            "🍜", "🍲", "🍛", "🍤", "🍣", "🍱", "🥟", "🦪", "🍦", "🍧",
            "🍨", "🍩", "🍪", "🎂", "🍰", "🧁", "🥧", "🍫", "🍬", "🍭",
            "☕", "🍵", "🥤", "🧃", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸",
            "🥑", "🧄", "🧅", "🧇", "🧆", "🧈", "🧋", "🧉", "🧊", "🫕", "🫖"
    };

    private static final String[] FOOD_EMOJI_NAMES = {
            "red_apple", "green_apple", "pear", "peach", "cherries",
            "strawberry", "hamburger", "pizza", "meat_on_bone", "poultry_leg",
            "pineapple", "mango", "banana", "lemon", "orange",
            "watermelon", "grapes", "coconut", "kiwi", "tomato",
            "carrot", "potato", "sweet_potato", "croissant", "baguette",
            "pretzel", "bagel", "pancakes", "cheese", "fried_egg",
            "egg", "bacon", "cut_of_meat", "french_fries", "hot_dog",
            "taco", "burrito", "sandwich", "stuffed_flatbread", "spaghetti",
            "ramen", "stew", "curry", "shrimp", "sushi",
            "bento_box", "dumpling", "oyster", "ice_cream", "shaved_ice",
            "ice_cream_dessert", "donut", "cookie", "birthday_cake", "cake",
            "cupcake", "pie", "chocolate_bar", "candy", "lollipop",
            "coffee", "tea", "cup_with_straw", "juice_box", "beer",
            "clinking_beers", "clinking_glasses", "wine", "tumbler_glass", "cocktail",
            "avocado", "garlic", "onion", "waffle", "falafel", "butter", "bubble_tea",
            "mate", "ice_cube", "fondue", "teapot"
    };

    // Android Devices category
    private static final String[] ANDROID_DEVICE_EMOJIS = {
            "📱", "📲", "💻", "🖥️", "📷", "📞", "📹", "📺", "🔋",
            "📸", "💾", "💿", "📀", "🖨️", "🖱️", "🖲️", "⌨️", "📟", "☎️",
            "📠", "🎥", "📽️", "🎞️", "💡", "🔦", "🕹️", "🎮", "📡", "🧮",
            "⚙️", "🧲", "🧪", "🧫", "🧬", "🔬", "🔭", "🛰️"
    };

    private static final String[] ANDROID_DEVICE_EMOJI_NAMES = {
            "mobile_phone", "mobile_phone_with_arrow", "laptop", "desktop_computer",
            "camera", "telephone_receiver", "video_camera", "television",
            "battery", "camera_with_flash", "floppy_disk", "cd", "dvd",
            "printer", "computer_mouse", "trackball", "keyboard", "pager",
            "telephone", "fax_machine", "movie_camera", "film_projector", "film_frames",
            "light_bulb", "flashlight", "joystick", "game_controller", "satellite_antenna",
            "abacus", "gear", "magnet", "test_tube", "petri_dish", "dna", "microscope",
            "telescope", "satellite"
    };

    // Tech Symbols category
    private static final String[] TECH_SYMBOL_EMOJIS = {
            "🔊", "🔍", "🔎", "🔑", "🔒", "🔓", "🔔", "🔌", "📠",
            "🔋", "🔐", "🔏", "🔗", "💾", "📶", "📳", "📴", "🔅", "🔆",
            "🔇", "🔈", "🔉", "⏰", "⌛", "⏳", "⌚", "📅", "📈",
            "▶️", "⏸️", "⏹️", "⏺️", "⏩", "⏪", "🔼", "🔽", "🔁", "🔀", "⏏️"
    };

    private static final String[] TECH_SYMBOL_EMOJI_NAMES = {
            "speaker_high_volume", "magnifying_glass_left", "magnifying_glass_right",
            "key", "locked", "unlocked", "bell", "electric_plug",
            "fax_machine", "battery", "locked_with_key", "locked_with_pen", "link",
            "floppy_disk", "wifi", "vibration_mode", "mobile_phone_off",
            "low_brightness", "high_brightness", "muted_speaker", "speaker_low_volume",
            "speaker_medium_volume", "alarm_clock", "hourglass",
            "hourglass_flowing", "watch", "calendar", "chart_increasing",
            "play_button", "pause_button", "stop_button", "record_button", "fast_forward",
            "fast_reverse", "arrow_up", "arrow_down", "repeat_button",
            "shuffle_tracks_button", "eject_button"
    };

    // The main list of all categories, now including "Dating"
    private static final Object[] EMOJI_CATEGORY_NAMES = {
            "Smileys", EMOJIS_SMILEYS,
            "Dating", DATING_EMOJIS,
            "Hand Gestures", HAND_GESTURE_EMOJIS,
            "Animals", ANIMAL_EMOJIS,
            "Food", FOOD_EMOJIS,
            "Android Devices", ANDROID_DEVICE_EMOJIS,
            "Tech Symbols", TECH_SYMBOL_EMOJIS,
    };

    static {
        for (int i = 0; i < EMOJI_CATEGORY_NAMES.length; i += 2) {
            final String category = (String) EMOJI_CATEGORY_NAMES[i];
            final String[] emojiTable = (String[]) EMOJI_CATEGORY_NAMES[i + 1];
            sCategoryToEmojisMap.put(category, emojiTable);
        }
        // Initialize the name to index map.
        for (HashMap.Entry<String, String[]> entry : sCategoryToEmojisMap.entrySet()) {
            HashMap<String, Integer> sNameToIndexesMap = new HashMap<>();
            for (int i = 0; i < entry.getValue().length; i++) {
                sNameToIndexesMap.put(entry.getValue()[i], i);
            }
            sCategoryNameToIndexesMap.put(entry.getKey(), sNameToIndexesMap);
        }
    }

    public static String[] getEmojisForCategory(String category) {
        return sCategoryToEmojisMap.get(category);
    }

    public static String[] getCategoryNames() {
        String[] categoryNames = new String[EMOJI_CATEGORY_NAMES.length / 2];
        for (int i = 0; i < EMOJI_CATEGORY_NAMES.length; i += 2) {
            categoryNames[i / 2] = (String) EMOJI_CATEGORY_NAMES[i];
        }
        return categoryNames;
    }

    public static String getDefaultCategory() {
        return EMOJI_CATEGORY_NAMES[0].toString();
    }
}