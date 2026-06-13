package project.witty.keys.app.utils; // Or your preferred package

import android.util.Log;

import androidx.annotation.Nullable; // For @Nullable

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// Assuming these model classes are in this package or imported correctly
import project.witty.keys.app.entities.ChatGptScreenAnalysis;
import project.witty.keys.app.entities.ConversationMessage;
import project.witty.keys.keyboard.AiChat.AIFeatureType;

public class PromptGenerator {

    private static final String TAG = "PromptGenerator";

    // --- HELPER METHODS AND CONSTANTS (EXACT_SYSTEM_MESSAGES, PREFIX_SYSTEM_PATTERNS, ---
    // --- extractContentForSecondaryPrompt, ctaTypeSupportsSenderPrefix, ---
    // --- isUserGeneratedMessageType, isLikelySystemTextMessage) ---
    // --- These should be the same as in the previous version you provided ---

    // Generic system message patterns (lowercase for case-insensitive matching)
    private static final Set<String> EXACT_SYSTEM_MESSAGES = new HashSet<>(Arrays.asList(
            "voice call", "video call", "call ended", "call declined",
            "missed voice call", "missed video call", "missed call",
            "message deleted", "this message was deleted",
            "you deleted this message", "typing...", "recording audio...",
            "online", "offline", "last seen", "no answer",
            "today", "yesterday", "pinned message", "you were added", "created group", "changed the subject"
    ));

    private static final List<Pattern> PREFIX_SYSTEM_PATTERNS = Arrays.asList(
            Pattern.compile("^you replied to.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^missed call at.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^call back at.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\d+ (new messages|unread messages).*", Pattern.CASE_INSENSITIVE)
    );

    public static String extractContentForSecondaryPrompt(ChatGptScreenAnalysis analysis, AIFeatureType ctaType) {
        if (analysis == null) {
            Log.w(TAG, "Screen analysis is null, cannot extract content.");
            return "";
        }
        StringBuilder relevantContent = new StringBuilder();
        switch (analysis.category) {
            case "USER_CONVERSATION":
            case "GROUP_CONVERSATION":
                if (analysis.conversation != null && !analysis.conversation.isEmpty()) {
                    for (ConversationMessage msg : analysis.conversation) {
                        if (!isUserGeneratedMessageType(msg.messageType)) {
                            Log.v(TAG, "Skipping message due to non-user-generated type: " + msg.messageType + " (" + msg.message + ")");
                            continue;
                        }
                        if ("text".equalsIgnoreCase(msg.messageType) && isLikelySystemTextMessage(msg.message)) {
                            Log.v(TAG, "Skipping likely system text message: " + msg.message);
                            continue;
                        }
                        // Conditional sender prefix (can be adjusted)
                        // For GENERATE_READ_REPLY, the sender context is handled in its own prompt generation
                        // For other CTAs, extractContentForSecondaryPrompt will provide more general content.
                        if (ctaTypeSupportsSenderPrefix(ctaType) && msg.sender != null && !msg.sender.isEmpty()) {
                            // For general extraction, include sender. For reply generation, it's handled differently.
                            if (ctaType != AIFeatureType.GENERATE_READ_REPLY) { // Avoid doubling sender for reply gen
                                relevantContent.append(msg.sender).append(": ");
                            }
                        }
                        relevantContent.append(msg.message);
                        if ("image".equalsIgnoreCase(msg.messageType) && msg.imageDescription != null && !msg.imageDescription.isEmpty()) {
                            relevantContent.append(" [Image Description: ").append(msg.imageDescription).append("]");
                        }
                        relevantContent.append("\n\n"); // Double newline for better separation
                    }
                }
                if (relevantContent.length() == 0 && analysis.summary != null && !analysis.summary.isEmpty()) {
                    Log.w(TAG, "No specific conversation messages extracted/kept, using summary for " + analysis.category);
                    relevantContent.append(analysis.summary);
                }
                break;
            case "MAIL":
            case "REEL":
            case "STORY":
            case "OTHER":
            default:
                if (analysis.summary != null && !analysis.summary.isEmpty()) {
                    relevantContent.append(analysis.summary);
                }
                break;
        }
        String extracted = relevantContent.toString().trim();
        // Limit logging length
        String loggableExtracted = extracted.length() > 200 ? extracted.substring(0, 200) + "..." : extracted;
        Log.d(TAG, "Extracted content for secondary prompt (" + ctaType + ", length " + extracted.length() + "): " + loggableExtracted);
        return extracted;
    }

    private static boolean ctaTypeSupportsSenderPrefix(AIFeatureType ctaType) {
        switch (ctaType) {
            case TRANSLATE_READ:
            case SIMPLIFY_READ:
                return false;
            // For GENERATE_READ_REPLY, the context will be built with sender info inside its specific logic
            // For other CTAs like SUMMARISE_READ, EXPLAIN_READ, sender might be useful.
            default:
                return true;
        }
    }

    private static boolean isUserGeneratedMessageType(String messageType) {
        if (messageType == null) return false;
        String typeLower = messageType.toLowerCase();
        return typeLower.equals("text") ||
                typeLower.equals("image") ||
                typeLower.equals("link") ||
                typeLower.equals("video") ||
                typeLower.equals("audio") ||
                typeLower.equals("document");
    }

    private static boolean isLikelySystemTextMessage(String message) {
        if (message == null || message.trim().isEmpty()) return true;
        String lowerMsg = message.trim().toLowerCase();
        if (EXACT_SYSTEM_MESSAGES.contains(lowerMsg)) return true;
        for (Pattern pattern : PREFIX_SYSTEM_PATTERNS) {
            if (pattern.matcher(lowerMsg).matches()) return true;
        }
        return false;
    }


    // --- MODIFIED METHOD BELOW ---
    /**
     * Generates a custom prompt for CTAs that generate replies or rephrase text.
     *
     * @param ctaType             The type of action (CTA).
     * @param screenAnalysis      The analyzed screen content (used for GENERATE_READ_REPLY).
     * @param toneCategory        The desired tone for the response (e.g., "formal", "casual").
     * @param currentKeyboardInput Text currently typed by the user (used for REPHRASE_WRITTEN).
     * @return The generated prompt string.
     */
    public String getCustomPromptForGenerativeActions(AIFeatureType ctaType, @Nullable ChatGptScreenAnalysis screenAnalysis, String toneCategory, @Nullable String currentKeyboardInput) {
        String customPrompt;
        String baseInstruction = "Your response should be direct and should not mention that the information came from a screen analysis or screenshot. ";

        if (ctaType == AIFeatureType.GENERATE_READ_REPLY) {
            String lastMessageContext = ""; // This will hold the last relevant message text

            if (screenAnalysis != null &&
                    (screenAnalysis.category.equals("USER_CONVERSATION") || screenAnalysis.category.equals("GROUP_CONVERSATION")) &&
                    screenAnalysis.conversation != null && !screenAnalysis.conversation.isEmpty()) {

                // Iterate backwards to find the absolute last relevant (non-system) message
                for (int i = screenAnalysis.conversation.size() - 1; i >= 0; i--) {
                    ConversationMessage msg = screenAnalysis.conversation.get(i);
                    if (isUserGeneratedMessageType(msg.messageType) && !isLikelySystemTextMessage(msg.message)) {
                        // We found the last relevant message. Construct its context.
                        StringBuilder messageBuilder = new StringBuilder();
                        if (msg.sender != null && !msg.sender.isEmpty()) {
                            messageBuilder.append(msg.sender).append(": ");
                        }
                        messageBuilder.append(msg.message);
                        if (msg.imageDescription != null && !msg.imageDescription.isEmpty()) {
                            messageBuilder.append(" [Image Description: ").append(msg.imageDescription).append("]");
                        }
                        lastMessageContext = messageBuilder.toString().trim();
                        Log.d(TAG, "GENERATE_READ_REPLY using last message: " + lastMessageContext);
                        break; // Found the last relevant message, no need to look further
                    }
                }
            }

            // Fallback to the overall screen summary if no specific last message was found
            // or if the category wasn't a conversation.
            if (lastMessageContext.isEmpty() && screenAnalysis != null && screenAnalysis.summary != null && !screenAnalysis.summary.isEmpty()) {
                Log.w(TAG, "No specific last message for reply context, using screen summary.");
                lastMessageContext = screenAnalysis.summary;
            }

            if (!lastMessageContext.isEmpty()) {
                customPrompt = baseInstruction + "The last message or context is: \n\"" + lastMessageContext + "\"\n\n" +
                        "Suggest three different, concise quick replies (each under 15 words) that 'You' could send next. " +
                        "The replies should be in a " + (toneCategory != null ? toneCategory.toLowerCase() : "neutral") + " tone. " +
                        "Format each reply on a new line. Respond with only the replies and nothing else.";
            } else {
                Log.w(TAG, "Could not find a relevant conversation context or summary to generate a reply for.");
                customPrompt = "I couldn't find enough context on the screen to suggest replies. Please ensure the conversation or relevant content is visible.";
            }

        } else if (ctaType == AIFeatureType.REPHRASE_WRITTEN) {
            if (currentKeyboardInput != null && !currentKeyboardInput.trim().isEmpty()){
                customPrompt = baseInstruction + "Rephrase the following text in three different ways, maintaining its original meaning. " +
                        "Each rephrased option should be concise (e.g., similar length or shorter) and in a " + (toneCategory != null ? toneCategory.toLowerCase() : "neutral") + " tone. " +
                        "Format each option on a new line. Respond with only the rephrased options and nothing else: \n\n\"" + currentKeyboardInput.trim() + "\"";
            } else {
                Log.w(TAG, "No current keyboard input provided to rephrase.");
                customPrompt = "Please type some text into the keyboard for me to rephrase.";
            }
        } else {
            Log.e(TAG, "Unhandled AiView.CtaType for generative action: " + ctaType);
            customPrompt = "This action is not supported with the current context.";
        }
        Log.d(TAG, "Generated prompt for " + ctaType + ": " + customPrompt);
        return customPrompt;
    }
    // --- END OF MODIFIED METHOD ---


    public String getCustomPromptForKeyboardInput(AIFeatureType ctaType, String keyboardQuery, @Nullable String language) {
        if (keyboardQuery == null || keyboardQuery.trim().isEmpty()) {
            Log.w(TAG, "Keyboard query is empty for CTA: " + ctaType);
            if (ctaType == AIFeatureType.TRANSLATE_WRITTEN) return "Please type text to translate.";
            if (ctaType == AIFeatureType.GRAMMAR) return "Please type text to correct grammar.";
            return "No input text provided.";
        }
        String customPrompt = "";
        switch (ctaType) {
            case TRANSLATE_WRITTEN:
                if (language != null && !language.isEmpty()) {
                    customPrompt = "Translate the following text into " + language + ". Respond with only the translated text and nothing else: \n\n\"" + keyboardQuery.trim() + "\"";
                } else {
                    customPrompt = "Error: Target language for translation is missing.";
                }
                break;
            case GRAMMAR:
                customPrompt = "Correct the grammar of the following text. Respond with only the corrected text and nothing else: \n\n\"" + keyboardQuery.trim() + "\"";
                break;
            default:
                Log.e(TAG, "Unsupported AiView.CtaType for getCustomPromptForKeyboardInput: " + ctaType);
                customPrompt = "Error: This action is not supported for direct keyboard input via this method.";
                break;
        }
        Log.d(TAG, "Generated prompt for " + ctaType + " (keyboard input): " + customPrompt);
        return customPrompt;
    }
}