package project.witty.keys.app.tutorial;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pre-computed conversation scenarios for the Onboarding Simulator.
 * 3 onboarding scenarios (shown to real users) + 4 E2E-only scenarios (test harness).
 */
public class OnboardingConversations {

    public enum ScenarioType {
        CASUAL_FRIEND,
        EXCITED_NEWS,
        PLANNING,
        FRUSTRATED_BOSS,
        HINDI_CASUAL,
        DATING_OPENER,
        GROUP_CHAT
    }

    public static class Message {
        public final String text;
        public final boolean isReceived;
        public final String[] replies;

        public Message(String text, boolean isReceived, String[] replies) {
            this.text = text;
            this.isReceived = isReceived;
            this.replies = replies;
        }
    }

    public static class Conversation {
        public final String contactName;
        public final ScenarioType scenarioType;
        public final List<Message> messages;
        public final String featureToHighlight; // Which CTA to highlight during onboarding

        public Conversation(String contactName, ScenarioType scenarioType,
                            List<Message> messages) {
            this(contactName, scenarioType, messages, null);
        }

        public Conversation(String contactName, ScenarioType scenarioType,
                            List<Message> messages, String featureToHighlight) {
            this.contactName = contactName;
            this.scenarioType = scenarioType;
            this.messages = messages;
            this.featureToHighlight = featureToHighlight;
        }
    }

    /** Returns the 3 mandatory onboarding conversations (for real user flow) */
    public static List<Conversation> getConversations() {
        List<Conversation> conversations = new ArrayList<>();
        conversations.add(createCasualFriend());     // Scenario 1: Smart Replies + Read Screen
        conversations.add(createFrustratedBoss());   // Scenario 2: Custom Generate
        conversations.add(createDatingOpener());     // Scenario 3: AI Chat
        return conversations;
    }

    /** Returns ALL 7 scenarios (3 mandatory + 4 optional) */
    public static List<Conversation> getAllScenarios() {
        List<Conversation> all = new ArrayList<>(getConversations()); // first 3 mandatory
        all.add(createExcitingNews());   // Optional 4
        all.add(createPlanning());       // Optional 5
        all.add(createHindiCasual());    // Optional 6
        all.add(createGroupChat());      // Optional 7
        return all;
    }

    /**
     * Get a specific scenario by ScenarioType name.
     * Used by test harness (EXTRA_SCENARIO intent extra).
     * @param name ScenarioType name, e.g. "FRUSTRATED_BOSS"
     * @return Conversation or null if not found
     */
    public static Conversation getScenarioByName(String name) {
        Log.d("OnboardingConversations", "getScenarioByName: '" + name + "'");
        try {
            ScenarioType type = ScenarioType.valueOf(name);
            for (Conversation c : getAllScenarios()) {
                if (c.scenarioType == type) {
                    Log.d("OnboardingConversations", "getScenarioByName: found " + type);
                    return c;
                }
            }
        } catch (IllegalArgumentException e) {
            Log.e("OnboardingConversations", "getScenarioByName: invalid name '" + name + "'");
        }
        return null;
    }

    // ========== Onboarding Scenarios (1-3) ==========

    private static Conversation createCasualFriend() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(
            "Hey! Haven't seen you in ages \uD83D\uDE04 What have you been up to?",
            true,
            new String[]{
                "Been super busy with work! We should catch up soon \uD83D\uDE4C",
                "Miss you too! Life's been crazy but in a good way",
                "Omg hey!! So much to tell you, let's grab coffee?"
            }
        ));
        messages.add(new Message(
            "Yes! Coffee sounds perfect. This weekend?",
            true,
            new String[]{
                "Saturday works! Name the place \u2615",
                "I'm free Sunday afternoon if that works?",
                "Absolutely! Let me check my schedule and get back to you"
            }
        ));
        return new Conversation("Priya", ScenarioType.CASUAL_FRIEND, messages);
    }

    private static Conversation createExcitingNews() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(
            "GUESS WHAT! I just got promoted!! \uD83C\uDF89\uD83C\uDF89",
            true,
            new String[]{
                "That's AMAZING! You totally deserve it! \uD83C\uDF89\uD83E\uDD73",
                "No way!! Congratulations! So proud of you! \uD83D\uDE4C",
                "OMG finally! They recognized your hard work! \uD83D\uDD25"
            }
        ));
        messages.add(new Message(
            "Thanks!! We're celebrating tonight, you coming?",
            true,
            new String[]{
                "Wouldn't miss it! Where are we going? \uD83E\uDD42",
                "Count me in! Let's make it a proper celebration",
                "100%! This calls for champagne! \uD83C\uDF7E"
            }
        ));
        return new Conversation("Rahul", ScenarioType.EXCITED_NEWS, messages);
    }

    private static Conversation createPlanning() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(
            "Hey, can you bring snacks for the movie night tomorrow?",
            true,
            new String[]{
                "On it! Any preferences? Sweet or savory?",
                "Sure thing! I'll grab some popcorn and chips \uD83C\uDF7F",
                "Yes! Leave the snacks to me, I've got great taste \uD83D\uDE0E"
            }
        ));
        messages.add(new Message(
            "Surprise us! Just no nuts please, Sarah's allergic",
            true,
            new String[]{
                "Got it, nut-free zone! I'll bring the good stuff \uD83C\uDFAC",
                "No worries, I'll keep it safe and delicious!",
                "Noted! Expect an epic snack spread tomorrow \uD83E\uDD29"
            }
        ));
        return new Conversation("Movie Night Group", ScenarioType.PLANNING, messages);
    }

    // ========== E2E-Only Scenarios (4-7) ==========

    private static Conversation createFrustratedBoss() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(
            "This report is completely wrong. Fix it by end of day.",
            true,
            new String[]{
                "I'll review and correct it right away. Sorry for the oversight.",
                "Understood, I'll prioritize this and have it fixed within the hour.",
                "Thanks for flagging this \u2014 I'll get the corrected version to you ASAP."
            }
        ));
        messages.add(new Message(
            "I needed this for the board meeting tomorrow. Don't let this happen again.",
            true,
            new String[]{
                "Absolutely, it won't happen again. I'll double-check everything this time.",
                "I understand the urgency. The corrected report will be in your inbox by 5pm.",
                "Noted. I'll set up a review process to prevent this going forward."
            }
        ));
        return new Conversation("Boss", ScenarioType.FRUSTRATED_BOSS, messages, "CUSTOM_GENERATE");
    }

    private static Conversation createHindiCasual() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(
            "Yaar party kab hai? \uD83C\uDF89",
            true,
            new String[]{
                "Arre Saturday ko! Tu aa raha hai na? \uD83D\uDE4C",
                "Bhai Friday night, tere ghar pe? \uD83D\uDE0E",
                "Kal shaam ko, pakka aana! \uD83E\uDD73"
            }
        ));
        messages.add(new Message(
            "Haan bhai, main aa raha hoon. Kya laun?",
            true,
            new String[]{
                "Kuch nahi lana, bas time pe aa ja! \uD83D\uDE04",
                "Cold drinks le aana, baaki sab hai \uD83C\uDF7B",
                "Snacks le aana bhai, biryani main bana raha hoon \uD83C\uDF57"
            }
        ));
        return new Conversation("Amit", ScenarioType.HINDI_CASUAL, messages);
    }

    private static Conversation createDatingOpener() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(
            "Hey! I saw you're into hiking too. What's your favorite trail?",
            true,
            new String[]{
                "Yes! I love the mountain trails. Have you tried the Ridge Path? \uD83D\uDE0A",
                "Hiking is the best! I'm always looking for new trails to explore \u26F0\uFE0F",
                "Such a good way to spend weekends! We should hike together sometime \uD83D\uDE04"
            }
        ));
        messages.add(new Message(
            "Omg yes! I've been wanting to try Ridge Path. Want to go this weekend?",
            true,
            new String[]{
                "I'd love that! Saturday morning works for me \u2600\uFE0F",
                "Sounds like a plan! I'll bring the trail snacks \uD83E\uDD7E",
                "Absolutely! Fair warning though, I stop for every scenic view \uD83D\uDE04"
            }
        ));
        return new Conversation("Alex", ScenarioType.DATING_OPENER, messages, "AI_CHAT");
    }

    private static Conversation createGroupChat() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(
            "Team, we need to finalize the venue for the retreat. Any preferences?",
            true,
            new String[]{
                "I'm flexible! Anywhere with good WiFi and outdoor space works \uD83D\uDC4D",
                "How about that resort we discussed last time? It had great reviews.",
                "Mountain lodge gets my vote! Perfect for team bonding activities \uD83C\uDFD4\uFE0F"
            }
        ));
        messages.add(new Message(
            "Great options! Let's vote: 1) Beach resort 2) Mountain lodge 3) City hotel",
            true,
            new String[]{
                "I vote 2 \u2014 mountain lodge! \uD83C\uDFD4\uFE0F",
                "Beach resort for me! \uD83C\uDFD6\uFE0F",
                "I'm happy with any, but mountain lodge sounds amazing!"
            }
        ));
        return new Conversation("Team Retreat", ScenarioType.GROUP_CHAT, messages);
    }
}
