package project.witty.keys.keyboard.shared;

import android.content.Context;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import java.util.ArrayList;
import java.util.Arrays; // Added for Arrays.asList
import java.util.HashSet; // Added for HashSet
import java.util.List;
import java.util.Set;   // Added for Set
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageAnalyzer {
    private final LanguageIdentifier languageIdentifier;
    private final Pattern timestampPattern = Pattern.compile("^\\d{1,2}:\\d{2}$");
    private final Pattern datePattern = Pattern.compile("^(Today|Yesterday|\\d{1,2}/\\d{1,2}/\\d{2,4})$");
    private static final int LARGE_MESSAGE_THRESHOLD = 50; // characters

    // Set of phrases to ignore (case-insensitive)
    private static final Set<String> IGNORED_PHRASES = new HashSet<>(Arrays.asList(
            "this message was deleted",
            "this message is deleted",
            "you deleted this message",
            "message deleted",
            "message was deleted",
            // Common English system messages
            "missed voice call",
            "missed video call",
            "message not sent",
            "couldn't send message",
            // Common Spanish system messages (examples)
            "mensaje eliminado",
            "este mensaje fue eliminado",
            "llamada perdida",
            "videollamada perdida",
            // WhatsApp specific (examples, often appear in italics in the app but raw text might be plain)
            "audio omitido",
            "documento omitido",
            "gif omitido",
            "imagen omitida",
            "sticker omitido",
            "video omitido",
            "ubicación omitida",
            "contacto omitido",
            "mensaje reenviado", // "forwarded message"
            "typing...", // "typing..." (might be too generic, consider context)
            "grabando audio..." // "recording audio..." (Spanish)
            // Add more phrases as needed, in lowercase
    ));

    public MessageAnalyzer(Context context) {
        languageIdentifier = LanguageIdentification.getClient();
    }

    /**
     * Checks if the given text is an ignorable system message.
     *
     * @param text The text to check (expected to be trimmed).
     * @return True if the text should be ignored, false otherwise.
     */
    private boolean isIgnoredMessage(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // Check against the set of known system messages (case-insensitive)
        return IGNORED_PHRASES.contains(text.toLowerCase());
    }

    public void extractMeaningfulMessages(List<String> items, MessageCallback callback) {
        List<String> meaningfulMessages = new ArrayList<>();

        if (items == null) {
            // Or throw new IllegalArgumentException("Input items list cannot be null");
            callback.onComplete(meaningfulMessages); // Return empty list for null input
            return;
        }

        AtomicInteger processedCount = new AtomicInteger(0);
        int totalItems = items.size();

        if (totalItems == 0) {
            callback.onComplete(meaningfulMessages);
            return;
        }

        for (String currentItem : items) {
            // Handle null items within the list
            if (currentItem == null) {
                if (processedCount.incrementAndGet() == totalItems) {
                    callback.onComplete(meaningfulMessages);
                }
                continue; // Skip null item
            }

            String trimmedItem = currentItem.trim();

            // 1. Check if the message should be ignored
            if (isIgnoredMessage(trimmedItem)) {
                // This is a message to be ignored, just update count and do nothing else
                if (processedCount.incrementAndGet() == totalItems) {
                    callback.onComplete(meaningfulMessages);
                }
            }
            // 2. Check if it's a large message (and not ignored)
            else if (isLargeMessage(currentItem)) {
                String messageWithoutNewlines = currentItem.replace("\n", "").replace("\r", "");
                meaningfulMessages.add(messageWithoutNewlines);
                if (processedCount.incrementAndGet() == totalItems) {
                    callback.onComplete(meaningfulMessages);
                }
            }
            // 3. Check if it's a potential message (and not ignored or large)
            else if (isPotentialMessage(currentItem)) {
                // analyzeMessage is async, its callback handles processedCount increment
                analyzeMessage(currentItem, isMessage -> {
                    if (isMessage) {
                        meaningfulMessages.add(currentItem);
                    }
                    if (processedCount.incrementAndGet() == totalItems) {
                        callback.onComplete(meaningfulMessages);
                    }
                });
            }
            // 4. Else (not ignored, not large, not potential) - still counts as processed
            else {
                if (processedCount.incrementAndGet() == totalItems) {
                    callback.onComplete(meaningfulMessages);
                }
            }
        }
    }

    private boolean isLargeMessage(String text) {
        // Assuming text is not null here, as it's checked in extractMeaningfulMessages
        return text.length() >= LARGE_MESSAGE_THRESHOLD;
    }

    private boolean isPotentialMessage(String text) {
        // Assuming text is not null here
        // Skip very short texts (likely names or single words)
        if (text.length() < 10) return false;

        // Skip timestamps (e.g., "20:40")
        if (timestampPattern.matcher(text).matches()) return false;

        // Skip dates (e.g., "Today", "Yesterday")
        if (datePattern.matcher(text).matches()) return false;

        // Skip single words in all caps (likely names)
        if (text.equals(text.toUpperCase()) && text.split("\\s+").length == 1) return false;

        return true;
    }

    private void analyzeMessage(String text, MessageAnalysisCallback callback) {
        // Assuming text is not null here
        // For messages below threshold, perform NLP checks
        languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener(languageCode -> {
                    boolean isMessage = !languageCode.equals("und") &&
                            text.matches(".*[a-zA-Z].*") && // Check for at least one letter
                            text.split("\\s+").length > 3;   // Check for more than 3 words
                    callback.onAnalysisComplete(isMessage);
                })
                .addOnFailureListener(e -> {
                    // Fallback if language identification fails: rely on basic heuristics
                    boolean isMessage = text.matches(".*[a-zA-Z].*") &&
                            text.split("\\s+").length > 3;
                    callback.onAnalysisComplete(isMessage);
                });
    }

    public void close() {
        if (languageIdentifier != null) {
            languageIdentifier.close();
        }
    }

    public interface MessageCallback {
        void onComplete(List<String> meaningfulMessages);
    }

    private interface MessageAnalysisCallback {
        void onAnalysisComplete(boolean isMessage);
    }
}