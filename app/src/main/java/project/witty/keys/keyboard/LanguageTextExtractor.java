package project.witty.keys.keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageTextExtractor {

    // Map to store Unicode script blocks and their corresponding languages
    private static final Map<String, String> scriptToLanguage = new HashMap<>();

    static {
        // Initialize the script-to-language map
        scriptToLanguage.put("\\p{IsLatin}&&[^\\p{IsASCII}]", "Spanish"); // Spanish, French, Italian, Portuguese, etc.
        scriptToLanguage.put("\\p{IsHan}", "Chinese"); // Chinese
        scriptToLanguage.put("\\p{IsHiragana}", "Japanese"); // Japanese Hiragana
        scriptToLanguage.put("\\p{IsKatakana}", "Japanese"); // Japanese Katakana
        scriptToLanguage.put("\\p{IsHangul}", "Korean"); // Korean Hangul
        scriptToLanguage.put("\\p{IsCyrillic}", "Russian"); // Russian
        scriptToLanguage.put("\\p{IsDevanagari}", "Hindi"); // Hindi
        scriptToLanguage.put("\\p{IsArabic}", "Arabic"); // Arabic
        scriptToLanguage.put("\\p{IsLatin}&&[^\\p{IsASCII}]", "French"); // French (non-ASCII Latin)
        scriptToLanguage.put("\\p{IsLatin}&&[^\\p{IsASCII}]", "German"); // German (non-ASCII Latin)
        scriptToLanguage.put("\\p{IsLatin}&&[^\\p{IsASCII}]", "Italian"); // Italian (non-ASCII Latin)
        scriptToLanguage.put("\\p{IsLatin}&&[^\\p{IsASCII}]", "Portuguese"); // Portuguese (non-ASCII Latin)
        scriptToLanguage.put("\\p{IsLatin}&&[^\\p{IsASCII}]", "Dutch"); // Dutch (non-ASCII Latin)
        scriptToLanguage.put("\\p{IsLatin}&&[^\\p{IsASCII}]", "Swedish"); // Swedish (non-ASCII Latin)
        scriptToLanguage.put("\\p{IsLatin}&&[^\\p{IsASCII}]", "Turkish"); // Turkish (non-ASCII Latin)
    }

    public static List<Map<String, String>> extractNonEnglishTextWithLanguage(String inputString) {
        List<Map<String, String>> nonEnglishTexts = new ArrayList<>();

        // Regex to match non-English text (Unicode characters outside the ASCII range)
        StringBuilder regexBuilder = new StringBuilder();
        for (String script : scriptToLanguage.keySet()) {
            regexBuilder.append(script).append("+|");
        }
        String regex = regexBuilder.substring(0, regexBuilder.length() - 1); // Remove the last "|"

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(inputString);

        // Find all matches of non-English text
        while (matcher.find()) {
            String matchedText = matcher.group();
            String language = identifyLanguage(matchedText);
            if (language != null) {
                Map<String, String> textWithLanguage = new HashMap<>();
                textWithLanguage.put("value", matchedText);
                textWithLanguage.put("language", language);
                nonEnglishTexts.add(textWithLanguage);
            }
        }

        return nonEnglishTexts;
    }

    // Helper method to get values by language
    public static List<String> getValuesByLanguage(List<Map<String, String>> nonEnglishTexts, String language) {
        List<String> values = new ArrayList<>();
        for (Map<String, String> textWithLanguage : nonEnglishTexts) {
            if (language.contains(textWithLanguage.get("language"))) {
                values.add(textWithLanguage.get("value"));
            }
        }
        return values;
    }

    private static String identifyLanguage(String text) {
        for (Map.Entry<String, String> entry : scriptToLanguage.entrySet()) {
            if (Pattern.compile(entry.getKey()).matcher(text).find()) {
                return entry.getValue();
            }
        }
        return null; // If no language is identified
    }

}