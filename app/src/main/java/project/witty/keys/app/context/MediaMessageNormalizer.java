package project.witty.keys.app.context;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Normalizes media-only notification text before it enters quick reply state.
 * WittyKeys can see notification metadata, not the image content itself.
 */
public final class MediaMessageNormalizer {
    public static final String PHOTO_PLACEHOLDER = "Photo received";
    public static final String VIDEO_PLACEHOLDER = "Video received";
    public static final String GIF_PLACEHOLDER = "GIF received";
    public static final String STICKER_PLACEHOLDER = "Sticker received";
    public static final String VOICE_PLACEHOLDER = "Voice message received";
    public static final String AUDIO_PLACEHOLDER = "Audio received";
    public static final String DOCUMENT_PLACEHOLDER = "Document received";
    public static final String CONTACT_PLACEHOLDER = "Contact shared";
    public static final String LOCATION_PLACEHOLDER = "Location shared";
    public static final String ATTACHMENT_PLACEHOLDER = "Attachment received";

    private static final List<String> SAFE_MEDIA_REPLIES = Arrays.asList(
            "I'll check and reply",
            "Can you share more details?",
            "Got it, one sec"
    );

    private static final List<String> ALL_PLACEHOLDERS = Arrays.asList(
            PHOTO_PLACEHOLDER,
            VIDEO_PLACEHOLDER,
            GIF_PLACEHOLDER,
            STICKER_PLACEHOLDER,
            VOICE_PLACEHOLDER,
            AUDIO_PLACEHOLDER,
            DOCUMENT_PLACEHOLDER,
            CONTACT_PLACEHOLDER,
            LOCATION_PLACEHOLDER,
            ATTACHMENT_PLACEHOLDER
    );

    private MediaMessageNormalizer() {}

    public static String normalizeIncomingText(String rawText, String dataMimeType) {
        String text = normalizeWhitespace(rawText);
        String mimePlaceholder = placeholderForMimeType(dataMimeType);

        if (mimePlaceholder != null) {
            if (text.isEmpty() || placeholderForNotificationText(text) != null) {
                return mimePlaceholder;
            }
            return mimePlaceholder + ": " + text;
        }

        String textPlaceholder = placeholderForNotificationText(text);
        if (textPlaceholder != null) {
            return textPlaceholder;
        }

        return text.isEmpty() ? null : text;
    }

    public static boolean isMediaPlaceholderText(String text) {
        if (text == null) return false;
        String normalized = normalizeWhitespace(text);
        for (String placeholder : ALL_PLACEHOLDERS) {
            if (normalized.equals(placeholder) || normalized.startsWith(placeholder + ":")) {
                return true;
            }
        }
        return false;
    }

    public static List<String> safeRepliesForMediaPlaceholder(String text) {
        return SAFE_MEDIA_REPLIES;
    }

    private static String placeholderForMimeType(String dataMimeType) {
        if (dataMimeType == null) return null;

        String lower = dataMimeType.toLowerCase(Locale.US).trim();
        if (lower.startsWith("image/gif")) return GIF_PLACEHOLDER;
        if (lower.startsWith("image/")) return PHOTO_PLACEHOLDER;
        if (lower.startsWith("video/")) return VIDEO_PLACEHOLDER;
        if (lower.startsWith("audio/")) return AUDIO_PLACEHOLDER;
        if (lower.contains("vcard")) return CONTACT_PLACEHOLDER;
        if (lower.startsWith("application/pdf")
                || lower.startsWith("application/msword")
                || lower.startsWith("application/vnd.")
                || lower.startsWith("application/zip")
                || lower.startsWith("application/x-zip")
                || lower.startsWith("application/x-rar")
                || lower.startsWith("application/x-7z")) {
            return DOCUMENT_PLACEHOLDER;
        }
        if (lower.startsWith("application/")) return ATTACHMENT_PLACEHOLDER;
        if (lower.startsWith("text/") && !lower.startsWith("text/plain")) return DOCUMENT_PLACEHOLDER;
        return null;
    }

    private static String placeholderForNotificationText(String text) {
        if (text == null || text.isEmpty()) return null;

        String lower = text.toLowerCase(Locale.US)
                .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (lower.equals("photo")
                || lower.equals("image")
                || lower.equals("picture")
                || lower.equals("photo received")
                || lower.equals("image received")
                || lower.equals("sent a photo")
                || lower.equals("sent an image")
                || lower.equals("sent you a photo")
                || lower.equals("sent you an image")
                || lower.equals("shared a photo")
                || lower.equals("shared an image")) {
            return PHOTO_PLACEHOLDER;
        }
        if (lower.equals("video")
                || lower.equals("video received")
                || lower.equals("sent a video")
                || lower.equals("sent you a video")
                || lower.equals("shared a video")) {
            return VIDEO_PLACEHOLDER;
        }
        if (lower.equals("gif")
                || lower.equals("gif received")
                || lower.equals("sent a gif")
                || lower.equals("sent you a gif")
                || lower.equals("shared a gif")) {
            return GIF_PLACEHOLDER;
        }
        if (lower.equals("sticker")
                || lower.equals("sticker received")
                || lower.equals("sent a sticker")
                || lower.equals("sent you a sticker")
                || lower.equals("shared a sticker")) {
            return STICKER_PLACEHOLDER;
        }
        if (lower.equals("voice message")
                || lower.equals("voice note")
                || lower.equals("sent a voice message")
                || lower.equals("sent you a voice message")
                || lower.equals("shared a voice message")) {
            return VOICE_PLACEHOLDER;
        }
        if (lower.equals("audio")
                || lower.equals("audio message")
                || lower.equals("sent audio")
                || lower.equals("sent an audio")
                || lower.equals("sent you audio")
                || lower.equals("sent you an audio")) {
            return AUDIO_PLACEHOLDER;
        }
        if (lower.equals("document")
                || lower.equals("file")
                || lower.equals("document received")
                || lower.equals("sent a document")
                || lower.equals("sent you a document")
                || lower.equals("shared a document")
                || lower.equals("sent a file")
                || lower.equals("sent you a file")) {
            return DOCUMENT_PLACEHOLDER;
        }
        if (lower.equals("contact")
                || lower.equals("contact card")
                || lower.equals("contact shared")
                || lower.equals("sent a contact")
                || lower.equals("sent you a contact")
                || lower.equals("shared a contact")) {
            return CONTACT_PLACEHOLDER;
        }
        if (lower.equals("location")
                || lower.equals("live location")
                || lower.equals("location shared")
                || lower.equals("sent a location")
                || lower.equals("sent you a location")
                || lower.equals("shared a location")) {
            return LOCATION_PLACEHOLDER;
        }
        if (lower.equals("attachment")
                || lower.equals("attachment received")
                || lower.equals("sent an attachment")
                || lower.equals("sent you an attachment")
                || lower.equals("shared an attachment")) {
            return ATTACHMENT_PLACEHOLDER;
        }
        return null;
    }

    private static String normalizeWhitespace(String rawText) {
        if (rawText == null) return "";
        return rawText.trim().replaceAll("\\s+", " ");
    }
}
