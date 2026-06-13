package project.witty.keys.e2e.golden

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import project.witty.keys.e2e.BaseKeyboardE2ETest

/**
 * AI Chat Golden Regression Test
 *
 * Recaptures all 14 AI Chat states and compares them against approved
 * reference screenshots using PixelDiffComparator.
 *
 * ## State Setup:
 * - Mirrors AiChatGoldenCaptureTest exactly (same broadcasts, same timing)
 * - AC01/AC02 use composite approach (captureReplyModeComposite)
 * - AC03-AC09 use standard captureAiChatScreenshot
 *
 * ## Threshold:
 * - Default: 0.5% pixel area threshold
 * - Per-pixel tolerance: 30 RGB units per channel
 *
 * ## On Failure:
 * - Diff image saved to ai_chat/diffs/
 * - Side-by-side comparison saved for human review
 *
 * ## Usage:
 *   adb shell am instrument -w \
 *     -e class project.witty.keys.e2e.golden.AiChatGoldenRegressionTest \
 *     project.witty.keys.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AiChatGoldenRegressionTest : BaseKeyboardE2ETest() {

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var comparator: PixelDiffComparator
    private lateinit var goldensDir: String
    private lateinit var capturedDir: String
    private lateinit var diffDir: String

    companion object {
        private const val TAG = "AiChatGoldenRegression"
        private const val ACTION_PREFIX = "project.witty.keys.debug."
        private const val STATE_SETTLE_MS = 800L
        private const val MSG_SETTLE_MS = 600L
        private const val OPEN_SETTLE_MS = 1500L
        private const val SHORT_SETTLE_MS = 400L

        private const val DEFAULT_PIXEL_TOLERANCE = 30
        // Root cause fixed: clearSession() now resets capture button highlight state
        private const val DEFAULT_AREA_THRESHOLD = 0.5

        @BeforeClass
        @JvmStatic
        fun setDarkTheme() {
            // Force dark mode once before all tests to match approved golden baselines
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            uiAutomation.executeShellCommand("cmd uimode night yes").close()
            SystemClock.sleep(2000)
        }
    }

    @Before
    fun regressionSetup() {
        screenshotManager = GoldenScreenshotManager(device)
        comparator = PixelDiffComparator(
            pixelTolerance = DEFAULT_PIXEL_TOLERANCE,
            areaThreshold = DEFAULT_AREA_THRESHOLD
        )

        val baseDir = GoldenScreenshotManager.GOLDEN_BASE_DIR
        goldensDir = "$baseDir/ai_chat/approved"
        capturedDir = "$baseDir/ai_chat/current"
        diffDir = "$baseDir/ai_chat/diffs"

        device.executeShellCommand("mkdir -p $capturedDir")
        device.executeShellCommand("mkdir -p $diffDir")

        // Reset font scale to 1.0 for consistent text sizing (matches capture test P10)
        device.executeShellCommand("settings put system font_scale 1.0")

        assertTrue("Keyboard must be visible for regression", waitForKeyboard())
        SystemClock.sleep(800)
        Log.d(TAG, "AI Chat regression setup complete")
    }

    @After
    fun regressionCleanup() {
        try {
            sendBroadcast("CLOSE_EMOJI_KEYBOARD")
            SystemClock.sleep(500)
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed: ${e.message}")
        }
    }

    // ==================== BROADCAST HELPERS ====================

    private fun sendBroadcast(action: String, extras: Map<String, String> = emptyMap()) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}$action").apply {
            setPackage(context.packageName)
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        context.sendBroadcast(intent)
    }

    private fun sendBroadcastWithInts(action: String, extras: Map<String, Int>) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}$action").apply {
            setPackage(context.packageName)
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        context.sendBroadcast(intent)
    }

    private fun sendBroadcastWithBool(action: String, extras: Map<String, Boolean>) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}$action").apply {
            setPackage(context.packageName)
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        context.sendBroadcast(intent)
    }

    private fun openAiChat(mode: String = "general") {
        sendBroadcast("OPEN_AI_CHAT", mapOf("mode" to mode))
        SystemClock.sleep(OPEN_SETTLE_MS)
    }

    private fun showReplyMode(mode: String = "general") {
        sendBroadcast("SHOW_REPLY_MODE", mapOf("mode" to mode))
        SystemClock.sleep(OPEN_SETTLE_MS)
    }

    private fun clearChat() {
        sendBroadcast("AI_CHAT_CLEAR")
        SystemClock.sleep(300)
    }

    private fun setInputText(text: String) {
        sendBroadcast("SET_AI_CHAT_INPUT_TEXT", mapOf("text" to text))
        SystemClock.sleep(MSG_SETTLE_MS)
    }

    private fun addUserMsg(text: String) {
        sendBroadcast("AI_CHAT_ADD_USER_MSG", mapOf("text" to text))
        SystemClock.sleep(MSG_SETTLE_MS)
    }

    private fun addAiMsg(text: String, ctaType: String = "REGENERATE_COPY_REPLY") {
        sendBroadcast("AI_CHAT_ADD_AI_MSG", mapOf("text" to text, "cta_type" to ctaType))
        SystemClock.sleep(MSG_SETTLE_MS)
    }

    private fun addLoading() {
        sendBroadcast("AI_CHAT_SHOW_LOADING")
        SystemClock.sleep(MSG_SETTLE_MS)
    }

    private fun addError(text: String = "Something went wrong. Please try again.") {
        sendBroadcast("AI_CHAT_SHOW_ERROR", mapOf("text" to text))
        SystemClock.sleep(MSG_SETTLE_MS)
    }

    private fun addSystemMsg(text: String) {
        sendBroadcast("AI_CHAT_ADD_SYSTEM_MSG", mapOf("text" to text))
        SystemClock.sleep(MSG_SETTLE_MS)
    }

    private fun addMetadataCard(json: String) {
        sendBroadcast("AI_CHAT_ADD_METADATA_CARD", mapOf("json" to json))
        SystemClock.sleep(MSG_SETTLE_MS)
    }

    private fun setContext(appName: String, contactName: String, emotion: String = "") {
        sendBroadcast("AI_CHAT_SET_CONTEXT", mapOf(
            "app_name" to appName, "contact_name" to contactName, "emotion" to emotion))
        SystemClock.sleep(300)
    }

    private fun scrollToTop() {
        sendBroadcast("AI_CHAT_SCROLL_TO_TOP")
        SystemClock.sleep(500)
    }

    private fun setReplyBarVisible(visible: Boolean) {
        sendBroadcastWithBool("AI_CHAT_SET_REPLY_BAR", mapOf("visible" to visible))
        SystemClock.sleep(300)
    }

    // ==================== COMPARISON HELPERS ====================

    /**
     * Compare an AI Chat screenshot against the approved golden.
     * Used for AC03-AC09 (standard captureAiChatScreenshot).
     */
    private fun assertGoldenMatch(goldenName: String, stateSetup: () -> Unit) {
        // 1. Navigate to target state
        stateSetup()
        SystemClock.sleep(SHORT_SETTLE_MS)

        // 2. Capture current screenshot
        val currentPath = "$capturedDir/${goldenName}_current.png"
        val captureSuccess = screenshotManager.captureAiChatScreenshot(currentPath)
        assertTrue("Failed to capture current AI Chat state: $goldenName", captureSuccess)

        // 3. Load approved golden
        val goldenPath = "$goldensDir/${goldenName}.png"
        val goldenBitmap = screenshotManager.loadGolden(goldenPath)
        assertNotNull(
            "Approved AI Chat golden not found: $goldenPath. " +
            "Run capture + approve first.",
            goldenBitmap
        )

        // 4. Load current screenshot
        val currentBitmap = screenshotManager.loadGolden(currentPath)
        assertNotNull("Failed to load current screenshot: $goldenName", currentBitmap)

        // 5. Compare
        val result = comparator.compare(goldenBitmap!!, currentBitmap!!)

        // 6. Save diff image if there are changes
        if (result.diffBitmap != null) {
            screenshotManager.saveBitmap(result.diffBitmap, "$diffDir/${goldenName}_diff.png")

            val sideBySide = comparator.generateSideBySide(
                goldenBitmap, currentBitmap, result.diffBitmap)
            if (sideBySide != null) {
                screenshotManager.saveBitmap(sideBySide, "$diffDir/${goldenName}_sidebyside.png")
                sideBySide.recycle()
            }
        }

        // 7. Cleanup bitmaps
        goldenBitmap.recycle()
        currentBitmap.recycle()
        result.diffBitmap?.recycle()

        // 8. Assert PASS
        Log.d(TAG, "$goldenName: ${result.message}")
        assertTrue(
            "$goldenName regression FAILED: ${result.message}",
            result.passed
        )
    }

    /**
     * Compare a Reply Mode composite screenshot against the approved golden.
     * Used for AC01 and AC02 (composite keyboard + AI header).
     */
    private fun assertReplyModeGoldenMatch(goldenName: String, stateSetup: () -> Unit) {
        // 1. Capture keyboard screenshot BEFORE switching to reply mode
        SystemClock.sleep(500)
        val kbBitmap = screenshotManager.captureFullBitmap()
        assertTrue("Failed to capture keyboard screenshot for $goldenName", kbBitmap != null)

        // 2. Compute keyboard rows top (skip SAB)
        val kbViewBounds = screenshotManager.getMainKeyboardViewBounds()
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val sabHeightPx = (101 * density).toInt()
        val kbBlockTop = kbViewBounds?.top ?: 1783
        val keysTop = kbBlockTop + sabHeightPx
        Log.d(TAG, "$goldenName: kbBlockTop=$kbBlockTop, sabH=$sabHeightPx, keysTop=$keysTop")

        // 3. Set up the reply mode state
        stateSetup()

        // 4. Capture composite
        val currentPath = "$capturedDir/${goldenName}_current.png"
        val captureSuccess = screenshotManager.captureReplyModeComposite(kbBitmap!!, keysTop, currentPath)
        kbBitmap.recycle()
        assertTrue("Failed to capture composite for $goldenName", captureSuccess)

        // 5. Load approved golden
        val goldenPath = "$goldensDir/${goldenName}.png"
        val goldenBitmap = screenshotManager.loadGolden(goldenPath)
        assertNotNull(
            "Approved AI Chat golden not found: $goldenPath. " +
            "Run capture + approve first.",
            goldenBitmap
        )

        // 6. Load current screenshot
        val currentBitmap = screenshotManager.loadGolden(currentPath)
        assertNotNull("Failed to load current screenshot: $goldenName", currentBitmap)

        // 7. Compare
        val result = comparator.compare(goldenBitmap!!, currentBitmap!!)

        // 8. Save diff image if there are changes
        if (result.diffBitmap != null) {
            screenshotManager.saveBitmap(result.diffBitmap, "$diffDir/${goldenName}_diff.png")

            val sideBySide = comparator.generateSideBySide(
                goldenBitmap, currentBitmap, result.diffBitmap)
            if (sideBySide != null) {
                screenshotManager.saveBitmap(sideBySide, "$diffDir/${goldenName}_sidebyside.png")
                sideBySide.recycle()
            }
        }

        // 9. Cleanup bitmaps
        goldenBitmap.recycle()
        currentBitmap.recycle()
        result.diffBitmap?.recycle()

        // 10. Assert PASS
        Log.d(TAG, "$goldenName: ${result.message}")
        assertTrue(
            "$goldenName regression FAILED: ${result.message}",
            result.passed
        )
    }

    // ==================== REGRESSION TESTS (13) ====================

    /** AC01: Reply Mode — Empty (General) — composite: keyboard + AI header strip */
    @Test
    fun regress_AC01_reply_empty() = assertReplyModeGoldenMatch("AC01_reply_empty") {
        showReplyMode("general")
    }

    /** AC02: Reply Mode — Typing (Personal) — composite: keyboard + AI header with text */
    @Test
    fun regress_AC02_reply_typing() = assertReplyModeGoldenMatch("AC02_reply_typing") {
        showReplyMode("personal")
        setInputText("How should I reply to Mom's dinner invite?")
    }

    /** AC03: General Chat — Basic Q&A */
    @Test
    fun regress_AC03_general_chat() = assertGoldenMatch("AC03_general_chat") {
        openAiChat("general")
        clearChat()
        addUserMsg("Explain async/await in JavaScript")
        addAiMsg("**async/await** is syntactic sugar over Promises.\n\n**async** marks a function as asynchronous. **await** pauses execution until a Promise resolves.\n\n`const data = await fetch('/api');`\n\nAlways wrap in **try/catch** for error handling.")
        scrollToTop()
    }

    /** AC04: General Chat — Markdown Response */
    @Test
    fun regress_AC04_general_markdown() = assertGoldenMatch("AC04_general_markdown") {
        openAiChat("general")
        clearChat()
        SystemClock.sleep(500)
        addUserMsg("Explain async/await in JavaScript with examples")
        addAiMsg("## Async/Await in JavaScript\n\n**async/await** is syntactic sugar over Promises that makes asynchronous code look synchronous.\n\n**Key concepts:**\n- **async** \u2014 marks a function as asynchronous\n- **await** \u2014 pauses execution until a Promise resolves\n- Error handling uses standard **try/catch**\n\n**Example:**\n```\nasync function fetchUser(id) {\n  try {\n    const res = await fetch('/api/users/' + id);\n    const user = await res.json();\n    return user;\n  } catch (err) {\n    console.error('Failed:', err);\n  }\n}\n```\n\n> Always use try/catch with await \u2014 unhandled rejections will crash your app in strict mode.")
        scrollToTop()
    }

    /** AC05: Personal Chat — Emotion Analysis */
    @Test
    fun regress_AC05_personal_chat() = assertGoldenMatch("AC05_personal_chat") {
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        addSystemMsg("Context: Mom on WhatsApp \u00B7 Feeling: \uD83D\uDE0A Happy")
        addUserMsg("How should I respond to Mom's dinner invite?")
        addAiMsg("**Mom seems happy and warm** \u2014 she's inviting you for dinner and mentioned Dad too. This feels like a family get-together she's excited about.\n\n**Suggested replies:**\n1. **Casual:** *\"Would love to! What time works?\"*\n2. **Enthusiastic:** *\"Yes!! Can't wait \uD83D\uDE0A Should I bring anything?\"*\n3. **Brief:** *\"Sounds great, see you then!\"*")
        scrollToTop()
    }

    /** AC06: Personal Counsel — Multi-turn Relationship Coaching */
    @Test
    fun regress_AC06_personal_counsel() = assertGoldenMatch("AC06_personal_counsel") {
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Arjun", "\uD83D\uDE24 Impatient")
        addSystemMsg("Context: Arjun on WhatsApp \u00B7 Feeling: \uD83D\uDE24 Impatient")
        addUserMsg("He keeps asking about the party, what should I say?")
        addAiMsg("**Arjun sounds impatient** \u2014 he's asked twice now. Give a concrete answer or a clear timeline.\n\nTry: *\"Saturday 8pm pakka! Tu venue dekh le, main food handle karta hun \uD83C\uDF89\"*")
        addUserMsg("But I've been avoiding his messages for 2 days now...")
        addUserMsg("I've been avoiding his messages for 2 days...")
        addAiMsg("**I notice you've been avoiding Arjun.** Here's what's happening:\n\nHe values your friendship and sees you as the organizer. Avoidance can create distance.\n\n**Try this:** Be honest and brief \u2014 *\"Sorry yaar, hectic week. Saturday 8pm pakka, tu venue dekh le!\"*\n\n**Communication tip:** When friends are persistent, a quick *\"noted, will confirm by X time\"* prevents anxiety buildup on both sides.")
        scrollToTop()
    }

    /** AC07: Loading — Typing Dots */
    @Test
    fun regress_AC07_loading() = assertGoldenMatch("AC07_loading") {
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        addSystemMsg("Context: Mom on WhatsApp \u00B7 Feeling: \uD83D\uDE0A Happy")
        addUserMsg("How should I respond to Mom's dinner invite?")
        addLoading()
        setReplyBarVisible(false)
        scrollToTop()
    }

    /** AC08: Error State */
    @Test
    fun regress_AC08_error() = assertGoldenMatch("AC08_error") {
        openAiChat("general")
        clearChat()
        addUserMsg("How do I center a div in CSS?")
        addError("Something went wrong. Please try again.")
        scrollToTop()
    }

    /** AC09: Context Card — Expanded Metadata */
    @Test
    fun regress_AC09_context_card() = assertGoldenMatch("AC09_context_card") {
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        addMetadataCard("""{"app":"WhatsApp","contact":"Mom","emotion":"\uD83D\uDE0A Happy","timeActive":"Active 5min ago","messageCount":3,"lastMessage":"Beta dinner pe aao kal","screen":"Chat","expanded":true}""")
        addUserMsg("What's Mom's mood and how should I reply?")
        addAiMsg("**Mom seems happy and warm** \u2014 she's inviting you for dinner and mentioning Dad is coming too. This feels like a family get-together she's excited about.\n\n**Best approach:** Match her warmth. Show enthusiasm about seeing both parents. Confirm quickly since she's waiting.\n\nTry: *\"Haan mummy, definitely! Dad bhi aa rahe hain toh aur maza aayega \uD83D\uDE0A Kya laun?\"*")
        scrollToTop()
    }

    /** AC10: Screen Capture Button — Highlighted capture pill */
    @Test
    fun regress_AC10_capture_button() = assertGoldenMatch("AC10_capture_button") {
        openAiChat("general")
        clearChat()
        addUserMsg("What's on my screen?")
        sendBroadcast("AI_CHAT_SHOW_CAPTURE")
        SystemClock.sleep(STATE_SETTLE_MS)
        scrollToTop()
    }

    /** AC11: Screenshot Inline — Screenshot thumbnail bubble in chat */
    @Test
    fun regress_AC11_screenshot_inline() = assertGoldenMatch("AC11_screenshot_inline") {
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        // Add screenshot message — mixed extras (strings + ints)
        val context: Context = ApplicationProvider.getApplicationContext()
        val screenshotIntent = Intent("${ACTION_PREFIX}AI_CHAT_ADD_SCREENSHOT_MSG").apply {
            setPackage(context.packageName)
            putExtra("image_path", "")
            putExtra("analysis", "I can see a WhatsApp chat with Mom. She sent \"Beta dinner pe aao kal\" \u2014 looks like a dinner invitation for tomorrow.")
            putExtra("width", 641)
            putExtra("height", 1568)
        }
        context.sendBroadcast(screenshotIntent)
        SystemClock.sleep(MSG_SETTLE_MS)
        addAiMsg("**Screenshot Analysis:** Mom sent a dinner invite on WhatsApp.\n\n" +
            "She said *\"Beta dinner pe aao kal\"* \u2014 this is a warm, casual invitation.\n\n" +
            "**Suggested reply:** *\"Haan mummy, pakka! Kya special bana rahi ho? \uD83D\uDE0A\"*")
        scrollToTop()
    }

    /** AC12: NLS Context Banner — Purple notification context card */
    @Test
    fun regress_AC12_nls_context() = assertGoldenMatch("AC12_nls_context") {
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        sendBroadcast("AI_CHAT_ADD_NLS_BANNER", mapOf(
            "contact" to "Mom",
            "messages_json" to """[{"sender":"Mom","text":"Beta dinner pe aao kal"},{"sender":"Mom","text":"Dad bhi aa rahe hain"},{"sender":"You","text":"Ok mummy"}]"""
        ))
        SystemClock.sleep(MSG_SETTLE_MS)
        addUserMsg("What did Mom say about dinner?")
        addAiMsg("**Based on Mom's recent messages:**\n\n" +
            "She invited you for dinner tomorrow and mentioned Dad is coming too. " +
            "You already replied *\"Ok mummy\"* so she knows you're coming.\n\n" +
            "**You could follow up with:** *\"Kya laun mummy? Dessert le aata hun \uD83C\uDF70\"*")
        scrollToTop()
    }

    /** AC13: Session Resumed — Banner with old messages at 70% opacity */
    @Test
    fun regress_AC13_session_resumed() = assertGoldenMatch("AC13_session_resumed") {
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        sendBroadcastWithInts("AI_CHAT_ADD_SESSION_BANNER", mapOf("message_count" to 3))
        SystemClock.sleep(MSG_SETTLE_MS)
        addUserMsg("How should I reply to Mom?")
        addAiMsg("**Mom seems happy** \u2014 she's inviting you for dinner.", "REPLY_COPY")
        sendBroadcast("AI_CHAT_ADD_NLS_BANNER", mapOf(
            "contact" to "Mom",
            "messages_json" to """[{"sender":"Mom","text":"Beta aaj 7 baje aa jaana"},{"sender":"Mom","text":"Paneer tikka bana rahi hun"}]"""
        ))
        SystemClock.sleep(MSG_SETTLE_MS)
        addUserMsg("She just texted again, what should I say?")
        addLoading()
        scrollToTop()
    }

    /** AC14: Screenshot Analyzing — in-chat indicator */
    @Test
    fun regress_AC14_capture_analyzing() = assertGoldenMatch("AC14_capture_analyzing") {
        sendBroadcast("AI_CHAT_SHOW_CAPTURE_ANALYZING")
        SystemClock.sleep(OPEN_SETTLE_MS + 1200)
    }
}
