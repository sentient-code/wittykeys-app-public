package project.witty.keys.app.context

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ReplyQualityTest - Tests for ReplyValidator and reply quality rules.
 *
 * Phase 1: Reply Quality Revolution
 *
 * Test cases covering:
 * - Phase detection
 * - Variety enforcement
 * - Anti-pattern filtering
 * - Hinglish ratio validation
 * - Length calibration
 */
class ReplyQualityTest {

    private lateinit var validator: ReplyValidator
    private lateinit var phaseDetector: ConversationPhaseDetector

    @Before
    fun setUp() {
        validator = ReplyValidator()
        phaseDetector = ConversationPhaseDetector()
    }

    // ========== BANNED PHRASE TESTS ==========

    @Test
    fun `containsBannedPhrase returns true for That's great`() {
        assertTrue(validator.containsBannedPhrase("That's great! Good for you."))
    }

    @Test
    fun `containsBannedPhrase returns true for I understand`() {
        assertTrue(validator.containsBannedPhrase("I understand how you feel."))
    }

    @Test
    fun `containsBannedPhrase returns true for Thank you for sharing`() {
        assertTrue(validator.containsBannedPhrase("Thank you for sharing that with me."))
    }

    @Test
    fun `containsBannedPhrase returns true for I appreciate`() {
        assertTrue(validator.containsBannedPhrase("I appreciate your concern."))
    }

    @Test
    fun `containsBannedPhrase returns false for normal reply`() {
        assertFalse(validator.containsBannedPhrase("Let's grab coffee tomorrow!"))
    }

    @Test
    fun `containsBannedPhrase returns false for Hinglish reply`() {
        assertFalse(validator.containsBannedPhrase("yaar kal milte hai dinner ke liye"))
    }

    // ========== VARIETY ENFORCEMENT TESTS ==========

    @Test
    fun `hasGoodVariety returns true when all replies start differently`() {
        val replies = listOf(
            "Sounds good!",
            "Let's do it",
            "What time?",
            "I'm in!"
        )
        assertTrue(validator.hasGoodVariety(replies))
    }

    @Test
    fun `hasGoodVariety returns false when 3+ replies start with same word`() {
        val replies = listOf(
            "I would love to",
            "I think so too",
            "I agree completely",
            "Sure thing"
        )
        assertFalse(validator.hasGoodVariety(replies))
    }

    @Test
    fun `enforceVariety removes duplicate-start replies`() {
        val replies = listOf(
            "I would love to",
            "I think so too",
            "I agree completely",
            "Sure thing",
            "What time?"
        )
        val enforced = validator.enforceVariety(replies)

        // Should keep max 2 "I" starts
        val iStarts = enforced.count { it.lowercase().startsWith("i ") }
        assertTrue("Should have max 2 'I' starts, got $iStarts", iStarts <= 2)
    }

    @Test
    fun `enforceVariety preserves all replies when variety is good`() {
        val replies = listOf(
            "Sounds good!",
            "Let's do it",
            "What time?",
            "I'm in!"
        )
        val enforced = validator.enforceVariety(replies)
        assertEquals(4, enforced.size)
    }

    // ========== LENGTH CALIBRATION TESTS ==========

    @Test
    fun `isLengthAppropriate returns true for matching lengths`() {
        val incomingMessage = "Want to grab lunch tomorrow?"
        val reply = "Sure, where do you want to go?"
        assertTrue(validator.isLengthAppropriate(reply, incomingMessage))
    }

    @Test
    fun `isLengthAppropriate returns false for very long reply to short message`() {
        val incomingMessage = "sup"
        val reply = "Hey! I was just thinking about you actually. How have you been? It's been so long since we caught up properly. We should definitely hang out soon!"
        assertFalse(validator.isLengthAppropriate(reply, incomingMessage))
    }

    @Test
    fun `isLengthAppropriate allows short replies to short messages`() {
        val incomingMessage = "hey"
        val reply = "yo!"
        assertTrue(validator.isLengthAppropriate(reply, incomingMessage))
    }

    // ========== VALIDATION TESTS ==========

    @Test
    fun `validateReplies filters out banned phrases`() {
        val replies = listOf(
            "That's great! So happy for you!",
            "Let's celebrate tonight!",
            "I understand how you feel",
            "Congratulations!"
        )
        val result = validator.validateReplies(
            replies,
            "I got the job!",
            true
        )

        // Should filter out "That's great" and "I understand"
        assertTrue(result.filteredCount >= 2)
        result.validReplies.forEach { reply ->
            assertFalse("Should not contain banned phrase: $reply",
                validator.containsBannedPhrase(reply))
        }
    }

    @Test
    fun `validateReplies penalizes formal phrases in casual context`() {
        val replies = listOf(
            "Hey sounds fun!",
            "Dear Sir, I would be delighted",
            "Let's do it!",
            "Kindly confirm at your earliest convenience"
        )
        val result = validator.validateReplies(
            replies,
            "party tonight?",
            true // casual context
        )

        // Formal replies should be filtered or scored lower
        val formalInValid = result.validReplies.any {
            it.contains("Sir") || it.contains("Kindly")
        }
        assertFalse("Formal phrases should be filtered in casual context", formalInValid)
    }

    // ========== CONVERSATION PHASE DETECTION TESTS ==========

    @Test
    fun `phaseDetector identifies opener phase`() {
        val result = phaseDetector.detectPhase("Hey! How are you?", 1)
        assertEquals(ConversationPhaseDetector.ConversationPhase.OPENER, result.phase)
    }

    @Test
    fun `phaseDetector identifies ender phase`() {
        val result = phaseDetector.detectPhase("Bye! Talk to you later", 5)
        assertEquals(ConversationPhaseDetector.ConversationPhase.ENDER, result.phase)
    }

    @Test
    fun `phaseDetector identifies mid-conversation phase`() {
        val result = phaseDetector.detectPhase("Also, I wanted to ask about the meeting", 5)
        assertEquals(ConversationPhaseDetector.ConversationPhase.MID_CONVERSATION, result.phase)
    }

    @Test
    fun `phaseDetector identifies Hinglish opener`() {
        val result = phaseDetector.detectPhase("Kya haal hai yaar?", 1)
        assertEquals(ConversationPhaseDetector.ConversationPhase.OPENER, result.phase)
    }

    // ========== SCORING TESTS ==========

    @Test
    fun `scoreReply returns 0 for banned phrase`() {
        val score = validator.scoreReply(
            "That's great! Thanks for sharing!",
            "I got promoted!",
            4,
            true
        )
        assertEquals(0.0f, score, 0.01f)
    }

    @Test
    fun `scoreReply returns high score for good reply`() {
        val score = validator.scoreReply(
            "Congrats! Let's celebrate! 🎉",
            "I got promoted!",
            4,
            true
        )
        assertTrue("Good reply should score >= 7, got $score", score >= 7.0f)
    }

    // ========== FULL PIPELINE TESTS ==========

    @Test
    fun `full pipeline produces quality validated replies`() {
        val rawReplies = listOf(
            "That's great!",           // Should be filtered (banned)
            "I understand completely", // Should be filtered (banned)
            "Congratulations! 🎉",     // Should pass
            "Let's celebrate!",        // Should pass
            "How exciting!",           // Should pass
            "I'm so happy for you",    // Might be filtered (I-start)
            "Well deserved!",          // Should pass
            "Time to party!"           // Should pass
        )

        val result = validator.validateReplies(
            rawReplies,
            "I got my dream job!",
            true
        )

        // Should have at least 4 valid replies
        assertTrue("Should have at least 4 valid replies", result.validCount >= 4)

        // None should contain banned phrases
        result.validReplies.forEach { reply ->
            assertFalse("Valid reply should not contain banned phrase: $reply",
                validator.containsBannedPhrase(reply))
        }

        // Should have good variety
        assertTrue("Valid replies should have good variety",
            validator.hasGoodVariety(result.validReplies))
    }
}
