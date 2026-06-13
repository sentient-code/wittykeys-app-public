package project.witty.keys.debug

/**
 * TestDataFactory — Generates mock scenario data for debug/test builds.
 *
 * Build 7.1: MemoryViewData has been removed. This factory now provides
 * simple scenario data (sender, message, replies) for debug controllers
 * and instrumentation tests.
 *
 * ## Available Scenarios:
 * - frustratedBoss()       — WhatsApp, 4 replies
 * - worriedFriendHindi()   — Hindi, bilingual replies
 * - casualFriend()         — Casual, 4 happy replies
 * - formalEmail()          — Gmail, professional replies
 * - hinglish()             — Mixed Hindi-English
 * - angryCustomer()        — Zendesk, apology replies
 * - excitedFriend()        — WhatsApp, celebratory replies
 *
 * ## Edge Cases:
 * - emptyData()            — Blank scenario
 * - longTextContent()      — Very long summary (500+ chars)
 * - rtlContent()           — Arabic RTL content
 * - maxQuickReplies()      — 8 quick replies (stress test)
 * - loadingState()         — Data in loading state
 * - noReplies()            — Content with 0 quick replies
 * - datingContext()        — Dating app context
 * - socialMediaContext()   — Social media context
 *
 * WittyKeys Automated Visual QA & Log Analysis Testing System
 * Created: February 9, 2026
 * Updated: March 23, 2026 — Removed MemoryViewData dependency (Build 7.1)
 */
object TestDataFactory {

    /**
     * Simple data class replacing MemoryViewData for test scenarios.
     */
    data class ScenarioData(
        val appName: String = "",
        val appPackage: String = "",
        val senderName: String = "",
        val lastMessage: String = "",
        val summary: String = "",
        val detectedLanguage: String = "en",
        val quickReplies: List<String> = emptyList()
    )

    // =========================================================================
    // Core Test Scenarios
    // =========================================================================

    fun frustratedBoss(): ScenarioData = ScenarioData(
        appName = "WhatsApp",
        appPackage = "com.whatsapp",
        senderName = "Boss",
        lastMessage = "This report was supposed to be done yesterday!",
        summary = "Your boss is frustrated about the delayed quarterly report.",
        detectedLanguage = "en",
        quickReplies = listOf(
            "I apologize, I'll have it ready in an hour",
            "Working on it now, almost done",
            "Sorry for the delay, had some blockers",
            "I'll prioritize this immediately"
        )
    )

    fun worriedFriendHindi(): ScenarioData = ScenarioData(
        appName = "WhatsApp",
        appPackage = "com.whatsapp",
        senderName = "Priya",
        lastMessage = "Tum theek ho na? Bahut time se baat nahi hui",
        summary = "Priya is worried because you haven't talked in a while",
        detectedLanguage = "hi-en",
        quickReplies = listOf(
            "Haan yaar, sab theek hai! Busy tha",
            "Sorry yaar, call karta hoon aaj",
            "I'm okay! Let's catch up soon",
            "Bas kaam mein busy tha, tension mat le"
        )
    )

    fun casualFriend(): ScenarioData = ScenarioData(
        appName = "WhatsApp",
        appPackage = "com.whatsapp",
        senderName = "Rahul",
        lastMessage = "Bro weekend pe party hai, aa raha hai?",
        summary = "Rahul is inviting you to a weekend party",
        detectedLanguage = "hi-en",
        quickReplies = listOf(
            "Definitely! Count me in",
            "Sounds fun! What time?",
            "Let me check and confirm",
            "I'll try to make it!"
        )
    )

    fun formalEmail(): ScenarioData = ScenarioData(
        appName = "Gmail",
        appPackage = "com.google.android.gm",
        senderName = "HR Team",
        lastMessage = "Please confirm your attendance for the annual review meeting",
        summary = "HR is requesting confirmation for annual review meeting",
        detectedLanguage = "en",
        quickReplies = listOf(
            "I confirm my attendance for the meeting",
            "Thank you for the reminder. I'll be there.",
            "Could you please share the meeting agenda?",
            "I'll attend. Please send calendar invite."
        )
    )

    fun hinglish(): ScenarioData = ScenarioData(
        appName = "WhatsApp",
        appPackage = "com.whatsapp",
        senderName = "Amit",
        lastMessage = "Yaar mujhe help chahiye, project stuck hai",
        summary = "Amit needs help with a stuck project",
        detectedLanguage = "hi-en",
        quickReplies = listOf(
            "Kya problem hai? Bata",
            "Chill, let's figure it out together",
            "Send me the details, I'll help",
            "Abhi free hoon, call kar"
        )
    )

    fun angryCustomer(): ScenarioData = ScenarioData(
        appName = "Zendesk",
        appPackage = "com.zendesk.android",
        senderName = "Customer #4521",
        lastMessage = "I've been waiting 2 weeks for my refund! This is unacceptable!",
        summary = "Customer is angry about delayed refund",
        detectedLanguage = "en",
        quickReplies = listOf(
            "I sincerely apologize for the delay. Let me look into this immediately.",
            "I understand your frustration. I'm escalating this right now.",
            "Sorry for the inconvenience. I'll process your refund today.",
            "I apologize. Can I have your order number to expedite this?"
        )
    )

    fun excitedFriend(): ScenarioData = ScenarioData(
        appName = "WhatsApp",
        appPackage = "com.whatsapp",
        senderName = "Neha",
        lastMessage = "OMG I GOT THE JOB!!!",
        summary = "Neha got the job she wanted and is celebrating",
        detectedLanguage = "en",
        quickReplies = listOf(
            "CONGRATULATIONS!!!",
            "That's amazing news! So proud of you!",
            "You deserve it! Let's celebrate!",
            "Knew you'd get it! Party time!"
        )
    )

    // =========================================================================
    // Edge Case Data
    // =========================================================================

    fun emptyData(): ScenarioData = ScenarioData()

    fun errorState(message: String = "Something went wrong. Please try again."): ScenarioData =
        ScenarioData(summary = message)

    fun loadingState(): ScenarioData = ScenarioData(
        appName = "WhatsApp",
        appPackage = "com.whatsapp",
        senderName = "Loading Test",
        lastMessage = "This message is being analyzed..."
    )

    fun longTextContent(): ScenarioData {
        val longSummary = buildString {
            append("The sender is experiencing a complex emotional situation. ")
            append("They've been dealing with multiple work deadlines while also ")
            append("managing personal family issues. The tone of their messages ")
            append("suggests they are overwhelmed and looking for understanding. ")
            append("There are indications of stress, anxiety, and a desire for ")
            append("practical help rather than just sympathy. The conversation ")
            append("has been ongoing for several hours with increasing urgency. ")
            append("Previous messages mentioned a project deadline tomorrow, a ")
            append("sick family member, and concerns about financial obligations. ")
            append("The sender seems to appreciate directness and practical advice ")
            append("rather than emotional support.")
        }

        return ScenarioData(
            appName = "WhatsApp",
            appPackage = "com.whatsapp",
            senderName = "Sarah",
            lastMessage = "I really need help with everything that's going on right now...",
            summary = longSummary,
            detectedLanguage = "en",
            quickReplies = listOf(
                "I hear you. Let's tackle one thing at a time.",
                "That sounds really overwhelming. What's the most urgent?",
                "I'm here for you. Want to talk it through?",
                "Let me help with the work stuff first."
            )
        )
    }

    fun rtlContent(): ScenarioData = ScenarioData(
        appName = "WhatsApp",
        appPackage = "com.whatsapp",
        senderName = "\u0623\u062D\u0645\u062F",
        lastMessage = "\u0645\u0631\u062D\u0628\u0627\u060C \u0643\u064A\u0641 \u062D\u0627\u0644\u0643 \u0627\u0644\u064A\u0648\u0645\u061F",
        summary = "Ahmed is asking how you are and wishes you a happy day",
        detectedLanguage = "ar",
        quickReplies = listOf(
            "\u0623\u0646\u0627 \u0628\u062E\u064A\u0631\u060C \u0634\u0643\u0631\u0627\u064B \u0644\u0643!",
            "\u0627\u0644\u062D\u0645\u062F \u0644\u0644\u0647\u060C \u0643\u064A\u0641 \u062D\u0627\u0644\u0643 \u0623\u0646\u062A\u061F",
            "\u0634\u0643\u0631\u0627\u064B \u0639\u0644\u0649 \u0627\u0644\u0633\u0624\u0627\u0644"
        )
    )

    fun maxQuickReplies(): ScenarioData = ScenarioData(
        appName = "Telegram",
        appPackage = "org.telegram.messenger",
        senderName = "Test Group",
        lastMessage = "What should we do this weekend?",
        summary = "The group is planning weekend activities and wants suggestions",
        detectedLanguage = "en",
        quickReplies = listOf(
            "Let's go hiking!",
            "Movie night at my place?",
            "How about a beach trip?",
            "We could try that new restaurant",
            "Game night sounds fun!",
            "Let's go to the mall",
            "Bowling would be great",
            "Why not a picnic in the park?"
        )
    )

    fun noReplies(): ScenarioData = ScenarioData(
        appName = "Gmail",
        appPackage = "com.google.android.gm",
        senderName = "System Notification",
        lastMessage = "Your subscription has been renewed successfully",
        summary = "Automated notification about subscription renewal. No reply needed.",
        detectedLanguage = "en",
        quickReplies = emptyList()
    )

    fun datingContext(): ScenarioData = ScenarioData(
        appName = "Tinder",
        appPackage = "com.tinder",
        senderName = "Alex",
        lastMessage = "Hey! I saw you like hiking too. What's your favorite trail?",
        summary = "Alex is interested in your hiking hobby and starting a conversation",
        detectedLanguage = "en",
        quickReplies = listOf(
            "Hey! Yeah I love hiking! My favorite is...",
            "Hi Alex! Great taste, I love the mountain trails",
            "Hey there! I'm more of a coastal trail person"
        )
    )

    fun socialMediaContext(): ScenarioData = ScenarioData(
        appName = "Instagram",
        appPackage = "com.instagram.android",
        senderName = "@travel_buddy",
        lastMessage = "OMG your Bali pics are incredible!",
        summary = "A friend is complimenting your travel photos and is very impressed",
        detectedLanguage = "en",
        quickReplies = listOf(
            "Thank you so much! It was amazing!",
            "Haha thanks! You should totally go!",
            "Right?! Bali is paradise!"
        )
    )

    // =========================================================================
    // API Response JSON Strings (for MockWebServer)
    // =========================================================================

    fun frustratedBossApiResponse(): String = """{"summary":"Boss frustrated about delayed report","replies":["I apologize","Working on it"]}"""
    fun worriedFriendHindiApiResponse(): String = """{"summary":"Friend worried about you","replies":["Sab theek hai","Call karta hoon"]}"""
    fun casualFriendApiResponse(): String = """{"summary":"Casual party invite","replies":["Count me in","Sounds fun"]}"""
    fun formalEmailApiResponse(): String = """{"summary":"HR meeting confirmation","replies":["I confirm","Thank you"]}"""
    fun hinglishApiResponse(): String = """{"summary":"Friend needs help","replies":["Kya problem hai","Let's figure it out"]}"""
    fun angryCustomerApiResponse(): String = """{"summary":"Customer angry about refund","replies":["I apologize","Let me escalate"]}"""
    fun excitedFriendApiResponse(): String = """{"summary":"Friend got the job","replies":["Congratulations","So proud"]}"""

    // =========================================================================
    // Milestone Toast Data
    // =========================================================================

    val milestoneToasts = listOf(
        Triple("brain", "First Reply Sent!", "You used WittyKeys for the first time"),
        Triple("fire", "10 Replies!", "You're on a roll!"),
        Triple("zap", "Speed Demon!", "3 replies in under a minute"),
        Triple("globe", "Polyglot!", "Used replies in 3+ languages"),
        Triple("100", "Century!", "100 replies sent with WittyKeys")
    )

    // =========================================================================
    // Lookup by Scenario Name (for ADB Intent extras)
    // =========================================================================

    fun getScenarioData(name: String): ScenarioData? {
        return when (name.uppercase().replace(" ", "_")) {
            "FRUSTRATED_BOSS" -> frustratedBoss()
            "WORRIED_FRIEND_HINDI", "WORRIED_FRIEND" -> worriedFriendHindi()
            "CASUAL_FRIEND" -> casualFriend()
            "FORMAL_EMAIL" -> formalEmail()
            "HINGLISH" -> hinglish()
            "ANGRY_CUSTOMER" -> angryCustomer()
            "EXCITED_FRIEND" -> excitedFriend()
            "EMPTY" -> emptyData()
            "ERROR" -> errorState()
            "LOADING" -> loadingState()
            "LONG_TEXT" -> longTextContent()
            "RTL" -> rtlContent()
            "MAX_REPLIES" -> maxQuickReplies()
            "NO_REPLIES" -> noReplies()
            "DATING" -> datingContext()
            "SOCIAL" -> socialMediaContext()
            else -> null
        }
    }

    fun getAllScenarioNames(): List<String> = listOf(
        "FRUSTRATED_BOSS", "WORRIED_FRIEND_HINDI", "CASUAL_FRIEND",
        "FORMAL_EMAIL", "HINGLISH", "ANGRY_CUSTOMER", "EXCITED_FRIEND",
        "EMPTY", "ERROR", "LOADING", "LONG_TEXT", "RTL",
        "MAX_REPLIES", "NO_REPLIES", "DATING", "SOCIAL"
    )
}
