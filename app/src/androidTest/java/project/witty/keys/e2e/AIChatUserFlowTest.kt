package project.witty.keys.e2e

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer A — AI Chat Lifecycle Tests (A1-A4)
 *
 * These tests verify the AI Chat feature (UnifiedAiView) end-to-end:
 *   open AI Chat → send message → REAL API → real AI response → verify state
 *
 * A1: Open AI Chat → General mode → QUERY_STATE shows ai_chat_open=true, mode=GENERAL
 * A2: Send message → Real API → ai_chat_message_count increases, AI response present
 * A3: Personal mode → Open with context → mode=PERSONAL
 * A4: Multi-turn → Send 2 messages → message_count grows, both have AI responses
 *
 * SEND_AI_CHAT_MESSAGE uses KeyboardSwitcher.performAiAction(AI_CHAT, text)
 * which triggers the REAL Claude API pipeline (not UI-only AI_CHAT_ADD_USER_MSG).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AIChatUserFlowTest : UserFlowE2ETestBase() {

    companion object {
        private const val TAG = "AIChatLifecycleTest"
    }

    @Before
    fun aiChatSetup() {
        Log.d(TAG, "aiChatSetup: clearing any existing AI Chat session")
        // Close any open AI Chat session
        sendDebugBroadcast("AI_CHAT_CLEAR")
        SystemClock.sleep(500)
        sendDebugBroadcast("SHOW_REPLY_MODE")
        SystemClock.sleep(STATE_SETTLE_MS)
    }

    // ==================== A1: Open AI Chat → General Mode ====================

    /**
     * A1: Open AI Chat in General Mode
     *
     * Proves: OPEN_AI_CHAT broadcast opens AI Chat, QUERY_STATE reports correct state.
     *
     * Flow:
     * 1. Verify AI Chat is not open initially
     * 2. Send OPEN_AI_CHAT broadcast with mode=general
     * 3. Wait for ai_chat_open=true in QUERY_STATE
     * 4. Verify ai_chat_mode=GENERAL
     * 5. Verify ai_chat_message_count=0 (fresh session)
     */
    @Test
    fun lifecycle_openAIChat_generalModeReported() {
        setTestContext("A1_openGeneral", 5)

        // Step 1: Verify initial state
        logStep(1, "BEFORE", "Querying initial AI Chat state")
        val initialState = queryState()
        val initialOpen = initialState.optBoolean("ai_chat_open", false)
        logStep(1, "AFTER", "Initial ai_chat_open=$initialOpen")

        // Step 2: Open AI Chat in general mode
        logStep(2, "BEFORE", "Sending OPEN_AI_CHAT mode=general")
        sendDebugBroadcast("OPEN_AI_CHAT", mapOf("mode" to "general"))
        SystemClock.sleep(1500)
        logStep(2, "AFTER", "OPEN_AI_CHAT broadcast sent")

        // Step 3: Wait for ai_chat_open=true
        logStep(3, "BEFORE", "Waiting for ai_chat_open=true (timeout=10s)")
        try {
            waitForState("ai_chat_open=true", timeoutMs = 10_000) {
                it.optBoolean("ai_chat_open", false)
            }
            logStep(3, "AFTER", "✓ AI Chat is open")
        } catch (e: AssertionError) {
            logStep(3, "AFTER", "✗ FAILED — ai_chat_open never became true")
            logStateDump(3)
            logResult(false, "AI Chat didn't open")
            throw e
        }

        // Step 4: Verify mode=GENERAL
        logStep(4, "BEFORE", "Verifying ai_chat_mode=GENERAL")
        val state = queryState()
        val mode = state.optString("ai_chat_mode", "NONE")
        logStep(4, "AFTER", "ai_chat_mode=$mode")
        assertEquals("AI Chat should be in GENERAL mode", "GENERAL", mode)

        // Step 5: Verify fresh session (0 messages)
        logStep(5, "BEFORE", "Verifying ai_chat_message_count=0 (fresh session)")
        val msgCount = state.optInt("ai_chat_message_count", -1)
        logStep(5, "AFTER", "ai_chat_message_count=$msgCount")
        assertEquals("Fresh session should have 0 messages", 0, msgCount)

        // Cleanup
        sendDebugBroadcast("AI_CHAT_CLEAR")
        SystemClock.sleep(500)
        sendDebugBroadcast("SHOW_REPLY_MODE")
        SystemClock.sleep(STATE_SETTLE_MS)

        logResult(true, "All 5 steps completed — AI Chat opens in GENERAL mode")
    }

    // ==================== A2: Send Message → Real API Response ====================

    /**
     * A2: Send Message → Real API Response
     *
     * Proves: SEND_AI_CHAT_MESSAGE → real Claude API → AI response appears in chat.
     *
     * Flow:
     * 1. Open AI Chat in general mode
     * 2. Send a message via SEND_AI_CHAT_MESSAGE (real API pipeline)
     * 3. Wait for ai_chat_message_count >= 2 (user msg + AI response)
     * 4. Verify last message is from AI (role=ai)
     * 5. Verify AI response is non-empty and valid
     */
    @Test
    fun lifecycle_sendMessage_getsRealAPIResponse() {
        setTestContext("A2_sendMessage", 5)

        // Step 1: Open AI Chat
        logStep(1, "BEFORE", "Opening AI Chat in general mode")
        sendDebugBroadcast("OPEN_AI_CHAT", mapOf("mode" to "general"))
        SystemClock.sleep(1500)
        try {
            waitForState("ai_chat_open", timeoutMs = 10_000) {
                it.optBoolean("ai_chat_open", false)
            }
            logStep(1, "AFTER", "✓ AI Chat open")
        } catch (e: AssertionError) {
            logStep(1, "AFTER", "✗ FAILED — AI Chat didn't open")
            logStateDump(1)
            logResult(false, "AI Chat didn't open")
            throw e
        }

        // Step 2: Send message via real API pipeline
        val userMessage = "What are some tips for staying productive while working from home?"
        logStep(2, "BEFORE", "Sending message via SEND_AI_CHAT_MESSAGE: '${userMessage.take(50)}'")
        sendDebugBroadcast("SEND_AI_CHAT_MESSAGE", mapOf("text" to userMessage))
        logStep(2, "AFTER", "Message sent, waiting for AI response")

        // Step 3: Wait for AI response (message_count >= 2)
        logStep(3, "BEFORE", "Waiting for ai_chat_message_count >= 2 (timeout=20s)")
        val startWait = SystemClock.elapsedRealtime()
        try {
            waitForState("ai_chat_message_count>=2", timeoutMs = 20_000) {
                it.optInt("ai_chat_message_count", 0) >= 2
            }
            val elapsed = SystemClock.elapsedRealtime() - startWait
            logStep(3, "AFTER", "✓ AI response received after ${elapsed}ms")
        } catch (e: AssertionError) {
            logStep(3, "AFTER", "✗ FAILED — AI response never arrived")
            logStateDump(3)
            logResult(false, "AI Chat response timeout — API may have failed")
            throw e
        }

        // Step 4: Verify last message is from AI
        logStep(4, "BEFORE", "Verifying last message has role=ai")
        val state = queryState()
        val messages = state.optJSONArray("ai_chat_messages")
        assertNotNull("ai_chat_messages should be present", messages)
        val msgCount = messages!!.length()
        assertTrue("Should have at least 2 messages", msgCount >= 2)

        val lastMsg = messages.optJSONObject(msgCount - 1)
        val lastRole = lastMsg?.optString("role", "") ?: ""
        logStep(4, "AFTER", "Last message role='$lastRole', total messages=$msgCount")
        assertEquals("Last message should be from AI", "ai", lastRole)

        // Step 5: Verify AI response content is valid
        logStep(5, "BEFORE", "Validating AI response text")
        val aiText = lastMsg?.optString("text", "") ?: ""
        assertTrue("AI response should be non-empty (got ${aiText.length} chars)", aiText.length >= 10)
        assertFalse(
            "AI response should not contain error text: '${aiText.take(50)}'",
            aiText.lowercase().contains("error") || aiText.lowercase().contains("exception")
        )
        logStep(5, "AFTER", "✓ AI response valid (${aiText.length} chars): '${aiText.take(80)}...'")

        // Cleanup
        sendDebugBroadcast("AI_CHAT_CLEAR")
        SystemClock.sleep(500)
        sendDebugBroadcast("SHOW_REPLY_MODE")
        SystemClock.sleep(STATE_SETTLE_MS)

        logResult(true, "All 5 steps completed — real API response received in AI Chat")
    }

    // ==================== A3: Personal Mode ====================

    /**
     * A3: Open AI Chat in Personal Mode
     *
     * Proves: Opening AI Chat with mode=personal → QUERY_STATE reports PERSONAL.
     *
     * Flow:
     * 1. Open AI Chat with mode=personal
     * 2. Verify ai_chat_open=true
     * 3. Verify ai_chat_mode=PERSONAL
     * 4. Send a message and verify response (personal mode still uses real API)
     */
    @Test
    fun lifecycle_personalMode_reportsCorrectMode() {
        setTestContext("A3_personalMode", 4)

        // Step 1: Open AI Chat in personal mode
        logStep(1, "BEFORE", "Sending OPEN_AI_CHAT mode=personal")
        sendDebugBroadcast("OPEN_AI_CHAT", mapOf("mode" to "personal"))
        SystemClock.sleep(1500)
        logStep(1, "AFTER", "OPEN_AI_CHAT personal sent")

        // Step 2: Verify open
        logStep(2, "BEFORE", "Waiting for ai_chat_open=true")
        try {
            waitForState("ai_chat_open", timeoutMs = 10_000) {
                it.optBoolean("ai_chat_open", false)
            }
            logStep(2, "AFTER", "✓ AI Chat is open")
        } catch (e: AssertionError) {
            logStep(2, "AFTER", "✗ FAILED — AI Chat didn't open in personal mode")
            logStateDump(2)
            logResult(false, "AI Chat didn't open")
            throw e
        }

        // Step 3: Verify mode=PERSONAL
        logStep(3, "BEFORE", "Verifying ai_chat_mode=PERSONAL")
        val state = queryState()
        val mode = state.optString("ai_chat_mode", "NONE")
        logStep(3, "AFTER", "ai_chat_mode=$mode")
        assertEquals("AI Chat should be in PERSONAL mode", "PERSONAL", mode)

        // Step 4: Send a message and verify API works in personal mode
        logStep(4, "BEFORE", "Sending message in personal mode to verify API works")
        sendDebugBroadcast("SEND_AI_CHAT_MESSAGE", mapOf("text" to "Tell me a fun fact about India"))
        val startWait = SystemClock.elapsedRealtime()
        try {
            waitForState("personal_mode_response", timeoutMs = 20_000) {
                it.optInt("ai_chat_message_count", 0) >= 2
            }
            val elapsed = SystemClock.elapsedRealtime() - startWait
            logStep(4, "AFTER", "✓ Personal mode API response received after ${elapsed}ms")
        } catch (e: AssertionError) {
            logStep(4, "AFTER", "✗ FAILED — Personal mode API response timeout")
            logStateDump(4)
            logResult(false, "Personal mode API call failed")
            throw e
        }

        // Cleanup
        sendDebugBroadcast("AI_CHAT_CLEAR")
        SystemClock.sleep(500)
        sendDebugBroadcast("SHOW_REPLY_MODE")
        SystemClock.sleep(STATE_SETTLE_MS)

        logResult(true, "All 4 steps completed — AI Chat personal mode works with real API")
    }

    // ==================== A4: Multi-turn Conversation ====================

    /**
     * A4: Multi-turn Conversation
     *
     * Proves: Sending multiple messages accumulates in chat, each gets a real AI response.
     *
     * Flow:
     * 1. Open AI Chat in general mode
     * 2. Send first message, wait for AI response (count >= 2)
     * 3. Send second message, wait for AI response (count >= 4)
     * 4. Verify all messages present: user1, ai1, user2, ai2
     * 5. Verify messages are in correct order (alternating user/ai)
     */
    @Test
    fun lifecycle_multiTurn_accumulatesMessages() {
        setTestContext("A4_multiTurn", 5)

        // Step 1: Open AI Chat
        logStep(1, "BEFORE", "Opening AI Chat in general mode")
        sendDebugBroadcast("OPEN_AI_CHAT", mapOf("mode" to "general"))
        SystemClock.sleep(1500)
        try {
            waitForState("ai_chat_open", timeoutMs = 10_000) {
                it.optBoolean("ai_chat_open", false)
            }
            logStep(1, "AFTER", "✓ AI Chat open")
        } catch (e: AssertionError) {
            logStep(1, "AFTER", "✗ FAILED — AI Chat didn't open")
            logStateDump(1)
            logResult(false, "AI Chat didn't open")
            throw e
        }

        // Step 2: Send first message and wait for response
        logStep(2, "BEFORE", "Sending first message")
        sendDebugBroadcast("SEND_AI_CHAT_MESSAGE", mapOf("text" to "What is the capital of France?"))
        try {
            waitForState("first_response", timeoutMs = 20_000) {
                it.optInt("ai_chat_message_count", 0) >= 2
            }
            logStep(2, "AFTER", "✓ First AI response received")
        } catch (e: AssertionError) {
            logStep(2, "AFTER", "✗ FAILED — First response timeout")
            logStateDump(2)
            logResult(false, "First message API timeout")
            throw e
        }

        // Small delay between turns
        SystemClock.sleep(1000)

        // Step 3: Send second message and wait for response
        logStep(3, "BEFORE", "Sending second message (follow-up)")
        sendDebugBroadcast("SEND_AI_CHAT_MESSAGE", mapOf("text" to "What about Germany?"))
        try {
            waitForState("second_response", timeoutMs = 20_000) {
                it.optInt("ai_chat_message_count", 0) >= 4
            }
            logStep(3, "AFTER", "✓ Second AI response received")
        } catch (e: AssertionError) {
            logStep(3, "AFTER", "✗ FAILED — Second response timeout")
            logStateDump(3)
            logResult(false, "Second message API timeout — multi-turn may be broken")
            throw e
        }

        // Step 4: Verify all messages present
        logStep(4, "BEFORE", "Verifying all 4 messages present")
        val state = queryState()
        val messages = state.optJSONArray("ai_chat_messages")
        assertNotNull("ai_chat_messages should be present", messages)
        val count = messages!!.length()
        assertTrue("Should have at least 4 messages (got $count)", count >= 4)

        // Build role sequence
        val roles = mutableListOf<String>()
        val previews = mutableListOf<String>()
        for (i in 0 until count) {
            val msg = messages.optJSONObject(i)
            roles.add(msg?.optString("role", "?") ?: "?")
            previews.add((msg?.optString("text", "") ?: "").take(30))
        }
        logStep(4, "AFTER", "Messages ($count): roles=$roles, previews=$previews")

        // Step 5: Verify alternating user/ai pattern
        logStep(5, "BEFORE", "Verifying user/ai alternation")
        // First message should be user, second ai, third user, fourth ai
        assertEquals("Message 0 should be user", "user", roles[0])
        assertEquals("Message 1 should be ai", "ai", roles[1])
        assertEquals("Message 2 should be user", "user", roles[2])
        assertEquals("Message 3 should be ai", "ai", roles[3])
        logStep(5, "AFTER", "✓ Correct alternating pattern: ${roles.take(4)}")

        // Cleanup
        sendDebugBroadcast("AI_CHAT_CLEAR")
        SystemClock.sleep(500)
        sendDebugBroadcast("SHOW_REPLY_MODE")
        SystemClock.sleep(STATE_SETTLE_MS)

        logResult(true, "All 5 steps completed — multi-turn conversation works ($count messages)")
    }
}
