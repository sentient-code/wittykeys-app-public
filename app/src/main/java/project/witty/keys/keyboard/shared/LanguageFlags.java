package project.witty.keys.keyboard.shared;

import java.util.HashMap;
import java.util.Map;

public class LanguageFlags {

    public static Map<String, String> getLanguageFlags() {
        Map<String, String> languageFlags = new HashMap<>();
        languageFlags.put("English", "🇺🇸");
        languageFlags.put("Spanish", "🇪🇸");
        languageFlags.put("French", "🇫🇷");
        languageFlags.put("German", "🇩🇪");
        languageFlags.put("Chinese", "🇨🇳");
        languageFlags.put("Japanese", "🇯🇵");
        languageFlags.put("Korean", "🇰🇷");
        languageFlags.put("Italian", "🇮🇹");
        languageFlags.put("Portuguese", "🇵🇹");
        languageFlags.put("Russian", "🇷🇺");
        languageFlags.put("Hindi", "🇮🇳");
        languageFlags.put("Arabic", "🇸🇦");
        languageFlags.put("Dutch", "🇳🇱");
        languageFlags.put("Swedish", "🇸🇪");
        languageFlags.put("Turkish", "🇹🇷");

        return languageFlags;
    }
}