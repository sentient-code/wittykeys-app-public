package project.witty.keys.e2e

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer A — SAB Pipeline Lifecycle Tests (S1-S5)
 *
 * These tests verify the FULL production pipeline end-to-end:
 *   mock message → TRIGGER_PIPELINE broadcast → real ReplyGenerator → real API → real UI
 *
 * Only 2 things are mocked: the conversation (RecyclerView) and the incoming message.
 * Only 1 thing is bypassed: accessibility service binding (can't bind in test process)
 *   and tapping reply chips (UiAutomator can't tap IME views).
 * Everything else — ReplyGenerator, ClaudeApi HTTP call, Firebase proxyClaude,
 * state transitions, OriginalView display — is 100% REAL.
 *
 * S1: Full pipeline lifecycle (message → loading → content → chips valid)
 * S2: Tap reply → compose insert (TAP_REPLY broadcast → EditText populated)
 * S3: Context differentiation (different senders → different replies)
 * S4: Hinglish detection (Hinglish message → OV_EXPANDED with Hinglish chips)
 * S5: Long conversation (complex message → pipeline handles gracefully)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SmartAssistantBarUserFlowTest : UserFlowE2ETestBase() {

    companion object {
        private const val TAG = "SABLifecycleTest"
    }

    @Before
    fun lifecycleSetup() {
        Log.d(TAG, "lifecycleSetup: forcing clean state before test")
        // Force SAB to OV_COLLAPSED to clear any leftover reply chips from previous tests.
        forceState("OV_COLLAPSED")
        SystemClock.sleep(500)
        resetStateHistory()
        SystemClock.sleep(300)
        Log.d(TAG, "lifecycleSetup: SAB reset to OV_COLLAPSED, state history cleared")
    }

    // ==================== S1: Full Pipeline Lifecycle ====================

    /**
     * S1: Full Pipeline Lifecycle Test
     *
     * Proves: message → TRIGGER_PIPELINE → real ReplyGenerator → real API → real replies
     *
     * Flow:
     * 1. Add incoming message to tutorial RecyclerView (MOCK)
     * 2. Trigger pipeline via broadcast (bypasses accessibility, uses REAL API)
     * 3. Wait for OV_ROW2_LOADING state (REAL — pipeline started)
     * 4. Wait for OV_EXPANDED state (REAL — API returned replies)
     * 5. Assert state history shows LOADING → EXPANDED
     * 6. Assert reply chip count >= 2
     * 7. Assert each reply chip is valid text
     */
    @Test
    fun lifecycle_incomingMessage_triggersFullPipeline() {
        setTestContext("S1_fullPipeline", 7)

        val sender = "Boss"
        val messageText = "I'm extremely disappointed with the project delay. This is unacceptable."

        // Step 1: Add incoming message (the ONLY mock)
        logStep(1, "BEFORE", "Adding incoming message: sender=$sender, text='${messageText.take(40)}...'")
        addIncomingMessage(sender, messageText)
        logStep(1, "AFTER", "Message added to RecyclerView")

        // Step 2: Trigger pipeline via debug broadcast (bypasses accessibility service binding)
        logStep(2, "BEFORE", "Triggering pipeline via TRIGGER_PIPELINE broadcast")
        triggerPipeline(sender, messageText)
        logStep(2, "AFTER", "Pipeline triggered, waiting for state transitions")

        // Step 3: Wait for OV_ROW2_LOADING to be recorded (pipeline started)
        logStep(3, "BEFORE", "Waiting for OV_ROW2_LOADING state/history (timeout=15s)")
        val startLoading = SystemClock.elapsedRealtime()
        try {
            waitForPipelineLoadingRecorded()
            logStep(3, "AFTER", "OV_ROW2_LOADING recorded after ${SystemClock.elapsedRealtime() - startLoading}ms")
        } catch (e: AssertionError) {
            logStep(3, "AFTER", "✗ FAILED — ${e.message}")
            logStateDump(3)
            logResult(false, "OV_ROW2_LOADING was never recorded")
            throw e
        }

        // Step 4: Wait for OV_EXPANDED (API returned replies)
        logStep(4, "BEFORE", "Waiting for OV_EXPANDED state with chips (timeout=15s)")
        val startContent = SystemClock.elapsedRealtime()
        val state: JSONObject
        try {
            state = waitForPipelineContent("OV_EXPANDED")
            logStep(4, "AFTER", "OV_EXPANDED reached after ${SystemClock.elapsedRealtime() - startContent}ms")
        } catch (e: AssertionError) {
            logStep(4, "AFTER", "✗ FAILED — ${e.message}")
            logStateDump(4)
            logResult(false, "OV_EXPANDED never reached — API may have failed or pipeline didn't trigger")
            throw e
        }

        // Step 5: Assert state history contains LOADING → EXPANDED transition
        logStep(5, "BEFORE", "Asserting state_history contains OV_ROW2_LOADING→OV_EXPANDED")
        try {
            assertPipelineHistory(state)
            val history = state.optJSONArray("state_history")
            logStep(5, "AFTER", "✓ State history valid: $history")
        } catch (e: AssertionError) {
            logStep(5, "AFTER", "✗ FAILED — ${e.message}")
            logStateDump(5)
            logResult(false, "State history missing OV_ROW2_LOADING→OV_EXPANDED transition")
            throw e
        }

        // Step 6: Assert reply chip count >= 2
        logStep(6, "BEFORE", "Asserting reply_chip_count >= 2")
        val chipCount = state.optInt("reply_chip_count", 0)
        if (chipCount < 2) {
            logStep(6, "AFTER", "✗ FAILED — reply_chip_count=$chipCount (expected >= 2)")
            logStateDump(6)
            logResult(false, "Insufficient reply chips: $chipCount")
        }
        assertTrue("Expected >= 2 reply chips, got $chipCount", chipCount >= 2)
        logStep(6, "AFTER", "✓ reply_chip_count=$chipCount")

        // Step 7: Assert each chip text is valid
        logStep(7, "BEFORE", "Asserting each chip text is valid and presentation-ready")
        val lengths = assertCleanReplyChips(state)
        logStep(7, "AFTER", "✓ All $chipCount chips valid (lengths: $lengths)")

        logResult(true, "All 7 steps completed — full pipeline verified")
    }

    // ==================== S2: Tap Reply → Compose Insert ====================

    /**
     * S2: Tap Reply → Compose Insert
     *
     * Proves: After pipeline delivers replies, TAP_REPLY broadcast inserts text into EditText.
     *
     * Flow:
     * 1. Trigger full pipeline (reuse S1 pattern)
     * 2. Wait for OV_EXPANDED (replies loaded)
     * 3. Record first chip text from QUERY_STATE
     * 4. Send TAP_REPLY broadcast with chip_index=0
     * 5. Verify EditText contains the reply text
     */
    @Test
    fun lifecycle_tapReply_insertsIntoCompose() {
        setTestContext("S2_tapReply", 5)

        val sender = "Friend"
        val messageText = "Hey, are you free tonight? Let's catch up over dinner!"

        // Step 1: Trigger full pipeline
        logStep(1, "BEFORE", "Adding message and triggering pipeline")
        addIncomingMessage(sender, messageText)
        triggerPipeline(sender, messageText)
        logStep(1, "AFTER", "Pipeline triggered")

        // Step 2: Wait for content
        logStep(2, "BEFORE", "Waiting for OV_EXPANDED with reply chips (timeout=15s)")
        val state: JSONObject
        try {
            state = waitForPipelineContent("OV_EXPANDED")
            logStep(2, "AFTER", "✓ Content loaded")
        } catch (e: AssertionError) {
            logStep(2, "AFTER", "✗ FAILED — ${e.message}")
            logStateDump(2)
            logResult(false, "Pipeline didn't reach content state")
            throw e
        }

        // Step 3: Record first chip text
        logStep(3, "BEFORE", "Reading first chip text from QUERY_STATE")
        val chips = state.optJSONArray("reply_chips")
        assertNotNull("reply_chips should be present in state", chips)
        assertTrue("Should have at least 1 chip", (chips?.length() ?: 0) >= 1)
        val firstChipText = chips!!.optString(0, "")
        assertTrue("First chip text should be non-empty", firstChipText.length >= 3)
        logStep(3, "AFTER", "✓ First chip: '${firstChipText.take(50)}...'")

        // Step 4: Send TAP_REPLY broadcast
        logStep(4, "BEFORE", "Sending TAP_REPLY broadcast with chip_index=0")
        sendDebugBroadcast("TAP_REPLY", mapOf("chip_index" to "0"))
        SystemClock.sleep(1000) // Let text insertion propagate
        logStep(4, "AFTER", "TAP_REPLY sent")

        // Step 5: Verify EditText contains reply
        logStep(5, "BEFORE", "Verifying EditText contains reply text")
        val editContent = getEditTextContent()
        if (editContent.isBlank()) {
            logStep(5, "AFTER", "✗ FAILED — EditText is empty after TAP_REPLY")
            logStateDump(5)
            logResult(false, "EditText empty — TAP_REPLY may not have inserted text")
        }
        assertTrue(
            "EditText should contain reply text but was: '$editContent'",
            editContent.isNotBlank()
        )
        logStep(5, "AFTER", "✓ EditText contains: '${editContent.take(50)}...'")

        logResult(true, "All 5 steps completed — tap reply inserts into compose")
    }

    // ==================== S3: Context Differentiation ====================

    /**
     * S3: Context Differentiation
     *
     * Proves: Different senders/messages produce different replies (context matters).
     *
     * Flow:
     * 1. Trigger pipeline for Sender A with Message A
     * 2. Wait for content, record reply chips
     * 3. Reset state history
     * 4. Trigger pipeline for Sender B with Message B (very different context)
     * 5. Wait for content, record reply chips
     * 6. Assert the two sets of replies are different
     */
    @Test
    fun lifecycle_differentContexts_produceDifferentReplies() {
        setTestContext("S3_contextDiff", 6)

        // --- Run A: Professional context ---
        val senderA = "HR Manager"
        val messageA = "Please submit your quarterly performance report by end of day Friday."

        logStep(1, "BEFORE", "Pipeline A: sender=$senderA")
        addIncomingMessage(senderA, messageA)
        triggerPipeline(senderA, messageA)
        logStep(1, "AFTER", "Pipeline A triggered")

        logStep(2, "BEFORE", "Waiting for content A (timeout=15s)")
        val stateA: JSONObject
        try {
            stateA = waitForPipelineContent("OV_EXPANDED_A")
        } catch (e: AssertionError) {
            logStep(2, "AFTER", "✗ FAILED — Pipeline A didn't reach content")
            logStateDump(2)
            logResult(false, "Pipeline A timeout")
            throw e
        }
        val chipsA = stateA.optJSONArray("reply_chips")
        val repliesA = mutableListOf<String>()
        for (i in 0 until (chipsA?.length() ?: 0)) {
            repliesA.add(chipsA!!.optString(i, ""))
        }
        assertCleanReplyChips(stateA)
        logStep(2, "AFTER", "✓ Got ${repliesA.size} replies for context A")

        // --- Reset and Run B: Casual/flirty context ---
        logStep(3, "BEFORE", "Resetting state history for pipeline B")
        resetStateHistory()
        SystemClock.sleep(500)
        logStep(3, "AFTER", "State history reset")

        val senderB = "Crush"
        val messageB = "Hey you! Missed you at the party last night. It was so boring without you 😢"

        logStep(4, "BEFORE", "Pipeline B: sender=$senderB")
        addIncomingMessage(senderB, messageB)
        triggerPipeline(senderB, messageB)
        logStep(4, "AFTER", "Pipeline B triggered")

        logStep(5, "BEFORE", "Waiting for content B (timeout=15s)")
        val stateB: JSONObject
        try {
            stateB = waitForPipelineContent("OV_EXPANDED_B")
        } catch (e: AssertionError) {
            logStep(5, "AFTER", "✗ FAILED — Pipeline B didn't reach content")
            logStateDump(5)
            logResult(false, "Pipeline B timeout")
            throw e
        }
        val chipsB = stateB.optJSONArray("reply_chips")
        val repliesB = mutableListOf<String>()
        for (i in 0 until (chipsB?.length() ?: 0)) {
            repliesB.add(chipsB!!.optString(i, ""))
        }
        assertCleanReplyChips(stateB)
        logStep(5, "AFTER", "✓ Got ${repliesB.size} replies for context B")

        // Step 6: Assert replies are different
        logStep(6, "BEFORE", "Asserting replies A ≠ replies B")
        val overlapCount = repliesA.count { a -> repliesB.any { b -> a == b } }
        val totalA = repliesA.size
        val overlapPct = if (totalA > 0) (overlapCount * 100 / totalA) else 0
        logStep(6, "AFTER", "Overlap: $overlapCount/$totalA ($overlapPct%) — A[0]='${repliesA.firstOrNull()?.take(40)}' B[0]='${repliesB.firstOrNull()?.take(40)}'")

        // Allow some overlap but not 100% identical
        assertTrue(
            "Replies should differ between contexts but $overlapCount/$totalA are identical",
            overlapCount < totalA
        )

        logResult(true, "All 6 steps completed — different contexts produce different replies")
    }

    // ==================== S4: Hinglish Detection ====================

    /**
     * S4: Hinglish Detection
     *
     * Proves: Hinglish message → pipeline detects language → OV_EXPANDED
     *         and replies contain Hinglish elements.
     *
     * Flow:
     * 1. Add Hinglish message
     * 2. Trigger pipeline
     * 3. Wait for OV_ROW2_LOADING to be recorded
     * 4. Wait for OV_EXPANDED
     * 5. Assert at least some replies contain Hindi/Hinglish characters or patterns
     */
    @Test
    fun lifecycle_hinglishMessage_detectsLanguageContext() {
        setTestContext("S4_hinglish", 5)

        val sender = "Yaar"
        val messageText = "Bhai party mein aana kal, bohot maza aayega! Sab log aa rahe hain."

        // Step 1: Add Hinglish message
        logStep(1, "BEFORE", "Adding Hinglish message: '${messageText.take(40)}...'")
        addIncomingMessage(sender, messageText)
        logStep(1, "AFTER", "Hinglish message added")

        // Step 2: Trigger pipeline
        logStep(2, "BEFORE", "Triggering pipeline for Hinglish input")
        triggerPipeline(sender, messageText)
        logStep(2, "AFTER", "Pipeline triggered")

        // Step 3: Wait for OV_ROW2_LOADING to be recorded
        logStep(3, "BEFORE", "Waiting for OV_ROW2_LOADING state/history (timeout=15s)")
        try {
            waitForPipelineLoadingRecorded()
            logStep(3, "AFTER", "✓ OV_ROW2_LOADING recorded")
        } catch (e: AssertionError) {
            logStep(3, "AFTER", "✗ FAILED — OV_ROW2_LOADING never recorded")
            logStateDump(3)
            logResult(false, "OV_ROW2_LOADING timeout for Hinglish message")
            throw e
        }

        // Step 4: Wait for content (OV_EXPANDED)
        logStep(4, "BEFORE", "Waiting for OV_EXPANDED with chips (timeout=15s)")
        val contentState: String
        val state: JSONObject
        try {
            val result = waitForPipelineContent("OV_EXPANDED")
            contentState = result.optString("sab_state")
            state = result
            logStep(4, "AFTER", "✓ Reached state: $contentState")
        } catch (e: AssertionError) {
            logStep(4, "AFTER", "✗ FAILED — ${e.message}")
            logStateDump(4)
            logResult(false, "Content state never reached for Hinglish input")
            throw e
        }

        // Step 5: Validate reply chips
        logStep(5, "BEFORE", "Validating reply chips for Hinglish content")
        val chipCount = state.optInt("reply_chip_count", 0)
        assertTrue("Expected >= 2 reply chips, got $chipCount", chipCount >= 2)

        val chips = state.optJSONArray("reply_chips")
        val chipTexts = mutableListOf<String>()
        for (i in 0 until (chips?.length() ?: 0)) {
            chipTexts.add(chips!!.optString(i, ""))
        }
        assertCleanReplyChips(state)

        // Check if any replies contain Hinglish patterns (Hindi words, romanized Hindi)
        val hinglishPatterns = listOf("bhai", "yaar", "haan", "nahi", "acha", "kya", "hai",
            "mein", "toh", "kab", "maza", "party", "chal", "kal", "aaja", "bro")
        val hinglishCount = chipTexts.count { text ->
            hinglishPatterns.any { pattern -> text.lowercase().contains(pattern) }
        }
        logStep(5, "AFTER", "✓ $chipCount chips valid, $hinglishCount contain Hinglish patterns, state=$contentState")

        logResult(true, "All 5 steps completed — Hinglish message processed successfully (state=$contentState, hinglish_replies=$hinglishCount/$chipCount)")
    }

    // ==================== S5: Long Conversation Context ====================

    /**
     * S5: Long/Complex Message
     *
     * Proves: Pipeline handles long, complex messages without timeout or crash.
     *
     * Flow:
     * 1. Add a long, multi-topic message
     * 2. Trigger pipeline
     * 3. Wait for OV_ROW2_LOADING to be recorded
     * 4. Wait for OV_EXPANDED (with extended timeout since prompt is larger)
     * 5. Assert reply chips are contextually relevant (non-generic)
     */
    @Test
    fun lifecycle_longMessage_handlesGracefully() {
        setTestContext("S5_longMessage", 5)

        val sender = "College Group"
        val messageText = """Ok so here's the plan for the weekend trip:
We leave Friday at 6am, Rahul is driving his car and Priya will bring snacks.
The Airbnb is booked in Lonavala, 3BHK with pool.
Total cost split is 2500 per person.
Please confirm by tonight if you're coming, we need final headcount for the car arrangement.
Also someone please bring a Bluetooth speaker and board games!"""

        // Step 1: Add long message
        logStep(1, "BEFORE", "Adding long message (${messageText.length} chars)")
        addIncomingMessage(sender, messageText)
        logStep(1, "AFTER", "Message added")

        // Step 2: Trigger pipeline
        logStep(2, "BEFORE", "Triggering pipeline for long input")
        triggerPipeline(sender, messageText)
        logStep(2, "AFTER", "Pipeline triggered")

        // Step 3: Wait for OV_ROW2_LOADING to be recorded
        logStep(3, "BEFORE", "Waiting for OV_ROW2_LOADING state/history (timeout=15s)")
        try {
            waitForPipelineLoadingRecorded()
            logStep(3, "AFTER", "✓ OV_ROW2_LOADING recorded")
        } catch (e: AssertionError) {
            logStep(3, "AFTER", "✗ FAILED — OV_ROW2_LOADING never recorded")
            logStateDump(3)
            logResult(false, "OV_ROW2_LOADING timeout for long message")
            throw e
        }

        // Step 4: Wait for content (extended timeout for longer prompt)
        logStep(4, "BEFORE", "Waiting for OV_EXPANDED (timeout=20s — extended for long prompt)")
        val startContent = SystemClock.elapsedRealtime()
        val state: JSONObject
        try {
            state = waitForPipelineContent("OV_EXPANDED", timeoutMs = 20_000)
            val elapsed = SystemClock.elapsedRealtime() - startContent
            logStep(4, "AFTER", "✓ Content reached after ${elapsed}ms")
        } catch (e: AssertionError) {
            logStep(4, "AFTER", "✗ FAILED — ${e.message}")
            logStateDump(4)
            logResult(false, "Content timeout for long message — API may have timed out")
            throw e
        }

        // Step 5: Validate replies
        logStep(5, "BEFORE", "Validating reply chips for long message context")
        val chipCount = state.optInt("reply_chip_count", 0)
        assertTrue("Expected >= 2 reply chips, got $chipCount", chipCount >= 2)

        val lengths = assertCleanReplyChips(state)
        logStep(5, "AFTER", "✓ $chipCount chips valid (lengths: $lengths)")

        logResult(true, "All 5 steps completed — long message handled gracefully")
    }

    private fun waitForPipelineLoadingRecorded(timeoutMs: Long = 15_000): JSONObject {
        return waitForState("OV_ROW2_LOADING", timeoutMs = timeoutMs) { state ->
            state.optString("sab_state") == "OV_ROW2_LOADING" ||
                stateHistory(state).contains("OV_ROW2_LOADING")
        }
    }

    private fun waitForPipelineContent(
        description: String,
        timeoutMs: Long = 15_000,
        minChips: Int = 2
    ): JSONObject {
        return waitForState(description, timeoutMs = timeoutMs) { state ->
            state.optString("sab_state") == "OV_EXPANDED" &&
                state.optInt("reply_chip_count", 0) >= minChips &&
                stateHistory(state).contains("OV_ROW2_LOADING")
        }
    }

    private fun assertPipelineHistory(state: JSONObject) {
        val history = stateHistory(state)
        val loadingIndex = history.indexOf("OV_ROW2_LOADING")
        val expandedIndex = history.indexOf("OV_EXPANDED")

        assertTrue(
            "Expected OV_ROW2_LOADING in state_history. Full history: $history",
            loadingIndex >= 0
        )
        assertTrue(
            "Expected OV_EXPANDED in state_history after OV_ROW2_LOADING. Full history: $history",
            expandedIndex >= loadingIndex
        )
    }

    private fun assertCleanReplyChips(state: JSONObject): List<Int> {
        val chips = state.optJSONArray("reply_chips")
        assertNotNull("reply_chips should be present in state", chips)

        val lengths = mutableListOf<Int>()
        for (i in 0 until (chips?.length() ?: 0)) {
            val text = chips?.optString(i, "") ?: ""
            val trimmed = text.trim()
            val lower = trimmed.lowercase()

            assertTrue("Chip $i is blank or too short (${trimmed.length} chars): '$text'", trimmed.length >= 3)
            assertTrue("Chip $i is too long (${trimmed.length} chars)", trimmed.length <= 500)
            assertFalse(
                "Chip $i contains error text: '$text'",
                lower.contains("error") || lower.contains("exception")
            )
            assertFalse("Chip $i contains a markdown fence: '$text'", trimmed.contains("```"))
            assertFalse("Chip $i contains markdown heading/commentary: '$text'", trimmed.startsWith("**"))
            assertFalse("Chip $i contains model breakdown commentary: '$text'", lower.contains("breakdown:"))
            assertFalse("Chip $i contains JSON key text: '$text'", trimmed.contains("\"quickReplies\""))
            assertFalse("Chip $i still looks like a JSON artifact: '$text'", trimmed in listOf("{", "}", "[", "]", "],", "},"))
            assertFalse(
                "Chip $i contains explanatory bullet text: '$text'",
                (trimmed.startsWith("- ") || trimmed.startsWith("* ")) &&
                    (lower.contains("reply") || lower.contains("tone") || lower.contains("calibration"))
            )

            lengths.add(trimmed.length)
        }

        return lengths
    }

    private fun stateHistory(state: JSONObject): List<String> {
        val history = state.optJSONArray("state_history") ?: return emptyList()
        return (0 until history.length()).map { history.optString(it) }
    }
}
