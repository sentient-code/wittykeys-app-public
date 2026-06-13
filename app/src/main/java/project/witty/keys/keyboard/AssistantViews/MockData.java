package project.witty.keys.keyboard.AssistantViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides mock data for each golden screenshot state.
 * Call this in test/debug builds to force specific UI states.
 */
public class MockData {

    public static String getContactName(SabState state) {
        return "Arjun";
    }

    public static String getEmotion(SabState state) {
        return ""; // Emotion display removed with MemoryView
    }

    public static String getSummary(SabState state) {
        return "";
    }

    public static List<SmartAssistantBarManager.ReplyChip> getReplies(SabState state) {
        List<SmartAssistantBarManager.ReplyChip> replies = new ArrayList<>();
        switch (state) {
            case OV_EXPANDED:
            case OV_COLLAPSED:
            case OV_MILESTONE:
            case OV_DATING:
            case OV_BRAIN_BLINK:
                replies.add(new SmartAssistantBarManager.ReplyChip("Kal raat pakka! Sab ko bata de 🔥", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Saturday 8pm confirmed", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Shaam tak bata dunga", null));
                break;
            case CTA_TONE_ACTIVE:
            case TONE_CASUAL:
                replies.add(new SmartAssistantBarManager.ReplyChip("Haan bhai party toh hogi! 🎉", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Chill kar, weekend pe fix hai", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Bol kitne log, arrange karta hun", null));
                break;
            case TONE_PROFESSIONAL:
                replies.add(new SmartAssistantBarManager.ReplyChip("I'll confirm the date by this evening", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Let me coordinate with everyone first", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("I'll share the plan shortly", null));
                break;
            case TONE_SAVAGE:
                replies.add(new SmartAssistantBarManager.ReplyChip("Bhai patience rakh, koi nahi bhaag raha 😤", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Itna urgent hai toh tu book kar na", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Chill maar, hoga sab", null));
                break;
            case TONE_SARCASTIC:
                replies.add(new SmartAssistantBarManager.ReplyChip("I know right, you should frame this moment 🖼️", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("I was feeling generous today, don't get used to it", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Your expectations of me are truly inspiring 😌", null));
                break;
            case TONE_CALM:
                replies.add(new SmartAssistantBarManager.ReplyChip("Maa aaj hi aata hun, 8 baje tak 🙏", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Haan maa, is weekend pakka, miss you both ❤️", null));
                replies.add(new SmartAssistantBarManager.ReplyChip("Kal dinner pe aata hun, kuch banana mat zyada 😊", null));
                break;
        }
        return replies;
    }

    public static String[] getPredictions(SabState state) {
        return new String[]{"Saturday", "Satish", "🎉"};
    }

    /**
     * Context action chips for OV_COLLAPSED Row 2.
     * Each entry: [emoji, label]. First entry is the clipboard chip (special style).
     * Last entry "More →" is accent-colored.
     */
    public static String[][] getContextChips(SabState state) {
        return new String[][]{
            {"📋", "meeting at 5pm tomorrow..."},  // clipboard chip
            {"📝", "Casual"},                       // tone chip
            {"✓", "Grammar"},                       // grammar chip
            {"🌐", "→ English"},                    // translate chip
            {"", "More →"}                          // accent more chip
        };
    }

    public static String getGrammarCorrected() {
        return "I have finished the report";
    }

    public static String getGrammarOriginal() {
        return "I have finish the report";
    }
}
