package project.witty.keys.e2e.golden

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.After
import project.witty.keys.e2e.BaseKeyboardE2ETest

/**
 * AI Chat Golden Capture Test — Cycle 3
 *
 * Captures 14 golden screenshots matching the 14 AI Chat states
 * defined in WittyKeys_UI_Mockup.html (ac-reply-empty … ac-session-resumed).
 *
 * ## Cycle 3 Fixes:
 * - P1: AC01/AC02 use SHOW_REPLY_MODE (keyboard visible with AI header strip)
 * - P2: AC09 metadata card uses rich icon format with emotion/time/count
 * - P3: AI avatar increased to 28dp
 * - P4: Header format changed to "Name on App · Emotion"
 * - P5: Navigation bar cropped from screenshots
 * - P6: General tab color hardcoded to blue
 * - P7: Markwon heading underline removed
 * - P8: Error card red border removed
 * - P9: Reply to AI button dark background
 * - P10: Font scale reset to 1.0 before capture
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AiChatGoldenCaptureTest : BaseKeyboardE2ETest() {

    private lateinit var screenshotManager: GoldenScreenshotManager
    private lateinit var goldensDir: String

    companion object {
        private const val TAG = "AiChatGoldenCapture"
        private const val ACTION_PREFIX = "project.witty.keys.debug."
        private const val STATE_SETTLE_MS = 800L
        private const val MSG_SETTLE_MS = 600L
        private const val OPEN_SETTLE_MS = 1500L

        // UnifiedAiView constants
        private const val STATE_REPLY_MODE = 0
        private const val STATE_AI_VIEW = 1
        private const val MODE_GENERAL = 0
        private const val MODE_PERSONAL = 1
    }

    @Before
    fun captureSetup() {
        screenshotManager = GoldenScreenshotManager(device)
        goldensDir = "${GoldenScreenshotManager.GOLDEN_BASE_DIR}/ai_chat/captured"
        device.executeShellCommand("mkdir -p $goldensDir")

        // P10: Reset font scale to 1.0 for consistent text sizing
        device.executeShellCommand("settings put system font_scale 1.0")

        // Force dark theme — golden pipeline requires dark theme everywhere
        device.executeShellCommand("cmd uimode night yes")
        SystemClock.sleep(1500)

        assertTrue("Keyboard must be visible for capture", waitForKeyboard())
        SystemClock.sleep(800)
        Log.d(TAG, "AI Chat capture setup complete (dark theme)")
    }

    @After
    fun captureCleanup() {
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

    private fun setUiState(state: Int, mode: Int) {
        sendBroadcastWithInts("SET_AI_CHAT_UI_STATE", mapOf("state" to state, "mode" to mode))
        SystemClock.sleep(STATE_SETTLE_MS)
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

    // ==================== CAPTURE HELPER ====================

    private fun captureGolden(goldenName: String) {
        SystemClock.sleep(400)
        val outputPath = "$goldensDir/${goldenName}.png"
        val success = screenshotManager.captureAiChatScreenshot(outputPath)
        assertTrue("Failed to capture AI Chat golden: $goldenName", success)
        assertTrue("Golden file not created: $outputPath",
            screenshotManager.fileExists(outputPath))
        Log.d(TAG, "Captured: $goldenName")
    }

    // ==================== 13 AI CHAT GOLDEN STATES ====================

    /** AC01: Reply Mode — Empty (General) — keyboard visible with AI header strip */
    @Test
    fun capture_AC01_reply_empty() {
        Log.d(TAG, "AC01: Reply Mode Empty (General)")
        // P1: Composite approach — capture keyboard keys, then AI header strip, combine
        // Step 1: Keyboard is showing. Capture full screenshot and compute QWERTY keys position.
        // The keyboard_block_container has SAB (101dp) above MainKeyboardView.
        // We need to skip the SAB to get just the QWERTY key rows.
        SystemClock.sleep(500)
        val kbBitmap = screenshotManager.captureFullBitmap()
        assertTrue("Failed to capture keyboard screenshot for AC01", kbBitmap != null)

        // Compute QWERTY keys top: keyboard_view bounds give us the container top,
        // then we add the SAB height (101dp) to skip past it.
        val kbViewBounds = screenshotManager.getMainKeyboardViewBounds()
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val sabHeightPx = (101 * density).toInt() // wk_bar_height = 101dp
        val kbBlockTop = kbViewBounds?.top ?: 1783  // keyboard_block_container start
        val keysTop = kbBlockTop + sabHeightPx
        Log.d(TAG, "AC01: kbBlockTop=$kbBlockTop, sabH=$sabHeightPx, keysTop=$keysTop, density=$density")

        // Step 2: Switch to Reply Mode to show AI header strip
        showReplyMode("general")

        // Step 3: Composite header + keyboard rows (keysTop skips SAB)
        val outputPath = "$goldensDir/AC01_reply_empty.png"
        val success = screenshotManager.captureReplyModeComposite(kbBitmap!!, keysTop, outputPath)
        kbBitmap.recycle()
        assertTrue("Failed to capture AC01 composite", success)
        assertTrue("AC01 file not created", screenshotManager.fileExists(outputPath))
        Log.d(TAG, "Captured: AC01_reply_empty (composite)")
    }

    /** AC02: Reply Mode — Typing (Personal) — keyboard visible with AI header + text */
    @Test
    fun capture_AC02_reply_typing() {
        Log.d(TAG, "AC02: Reply Mode Typing (Personal)")
        // P1: Composite approach — capture keyboard keys, then AI header strip with text
        SystemClock.sleep(500)
        val kbBitmap = screenshotManager.captureFullBitmap()
        assertTrue("Failed to capture keyboard screenshot for AC02", kbBitmap != null)

        val kbViewBounds = screenshotManager.getMainKeyboardViewBounds()
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val sabHeightPx = (101 * density).toInt()
        val kbBlockTop = kbViewBounds?.top ?: 1783
        val keysTop = kbBlockTop + sabHeightPx
        Log.d(TAG, "AC02: kbBlockTop=$kbBlockTop, sabH=$sabHeightPx, keysTop=$keysTop, density=$density")

        // Step 2: Switch to Reply Mode (Personal) and type text
        showReplyMode("personal")
        setInputText("How should I reply to Mom's dinner invite?")

        // Step 3: Composite header + keyboard rows (keysTop skips SAB)
        val outputPath = "$goldensDir/AC02_reply_typing.png"
        val success = screenshotManager.captureReplyModeComposite(kbBitmap!!, keysTop, outputPath)
        kbBitmap.recycle()
        assertTrue("Failed to capture AC02 composite", success)
        assertTrue("AC02 file not created", screenshotManager.fileExists(outputPath))
        Log.d(TAG, "Captured: AC02_reply_typing (composite)")
    }

    /** AC03: General Chat — Basic Q&A — matches mockup ac-general-chat */
    @Test
    fun capture_AC03_general_chat() {
        Log.d(TAG, "AC03: General Chat Basic")
        openAiChat("general")
        clearChat()
        addUserMsg("Explain async/await in JavaScript")
        addAiMsg("**async/await** is syntactic sugar over Promises.\n\n**async** marks a function as asynchronous. **await** pauses execution until a Promise resolves.\n\n`const data = await fetch('/api');`\n\nAlways wrap in **try/catch** for error handling.")
        scrollToTop()
        captureGolden("AC03_general_chat")
    }

    /** AC04: General Chat — Markdown Response — matches mockup ac-general-markdown */
    @Test
    fun capture_AC04_general_markdown() {
        Log.d(TAG, "AC04: General Chat Markdown")
        openAiChat("general")
        clearChat()
        SystemClock.sleep(500)
        addUserMsg("Explain async/await in JavaScript with examples")
        addAiMsg("## Async/Await in JavaScript\n\n**async/await** is syntactic sugar over Promises that makes asynchronous code look synchronous.\n\n**Key concepts:**\n- **async** \u2014 marks a function as asynchronous\n- **await** \u2014 pauses execution until a Promise resolves\n- Error handling uses standard **try/catch**\n\n**Example:**\n```\nasync function fetchUser(id) {\n  try {\n    const res = await fetch('/api/users/' + id);\n    const user = await res.json();\n    return user;\n  } catch (err) {\n    console.error('Failed:', err);\n  }\n}\n```\n\n> Always use try/catch with await \u2014 unhandled rejections will crash your app in strict mode.")
        scrollToTop()
        captureGolden("AC04_general_markdown")
    }

    /** AC05: Personal Chat — Emotion Analysis — matches mockup ac-personal-chat */
    @Test
    fun capture_AC05_personal_chat() {
        Log.d(TAG, "AC05: Personal Chat")
        openAiChat("personal")
        clearChat()
        // P4: Separate contact name and emotion for correct format
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        addSystemMsg("Context: Mom on WhatsApp \u00B7 Feeling: \uD83D\uDE0A Happy")
        addUserMsg("How should I respond to Mom's dinner invite?")
        addAiMsg("**Mom seems happy and warm** \u2014 she's inviting you for dinner and mentioned Dad too. This feels like a family get-together she's excited about.\n\n**Suggested replies:**\n1. **Casual:** *\"Would love to! What time works?\"*\n2. **Enthusiastic:** *\"Yes!! Can't wait \uD83D\uDE0A Should I bring anything?\"*\n3. **Brief:** *\"Sounds great, see you then!\"*")
        scrollToTop()
        captureGolden("AC05_personal_chat")
    }

    /** AC06: Personal Counsel — Multi-turn Relationship Coaching — matches mockup ac-personal-counsel */
    @Test
    fun capture_AC06_personal_counsel() {
        Log.d(TAG, "AC06: Personal Counsel Multi-turn")
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
        captureGolden("AC06_personal_counsel")
    }

    /** AC07: Loading — Typing Dots — matches mockup ac-loading */
    @Test
    fun capture_AC07_loading() {
        Log.d(TAG, "AC07: Loading / Typing Dots (Personal)")
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        addSystemMsg("Context: Mom on WhatsApp \u00B7 Feeling: \uD83D\uDE0A Happy")
        addUserMsg("How should I respond to Mom's dinner invite?")
        addLoading()
        setReplyBarVisible(false)
        scrollToTop()
        captureGolden("AC07_loading")
    }

    /** AC08: Error State — matches mockup ac-error */
    @Test
    fun capture_AC08_error() {
        Log.d(TAG, "AC08: Error State (General)")
        openAiChat("general")
        clearChat()
        addUserMsg("How do I center a div in CSS?")
        addError("Something went wrong. Please try again.")
        scrollToTop()
        captureGolden("AC08_error")
    }

    /** AC09: Context Card — Expanded Metadata — matches mockup ac-context-card */
    @Test
    fun capture_AC09_context_card() {
        Log.d(TAG, "AC09: Context Card (Personal)")
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        // P2: Rich metadata card with emotion, time, and message count
        addMetadataCard("""{"app":"WhatsApp","contact":"Mom","emotion":"\uD83D\uDE0A Happy","timeActive":"Active 5min ago","messageCount":3,"lastMessage":"Beta dinner pe aao kal","screen":"Chat","expanded":true}""")
        addUserMsg("What's Mom's mood and how should I reply?")
        addAiMsg("**Mom seems happy and warm** \u2014 she's inviting you for dinner and mentioning Dad is coming too. This feels like a family get-together she's excited about.\n\n**Best approach:** Match her warmth. Show enthusiasm about seeing both parents. Confirm quickly since she's waiting.\n\nTry: *\"Haan mummy, definitely! Dad bhi aa rahe hain toh aur maza aayega \uD83D\uDE0A Kya laun?\"*")
        scrollToTop()
        captureGolden("AC09_context_card")
    }

    /** AC10: Screen Capture Button — Highlighted capture pill in AI Chat */
    @Test
    fun capture_AC10_capture_button() {
        Log.d(TAG, "AC10: Capture Button Highlighted")
        openAiChat("general")
        clearChat()
        addUserMsg("What's on my screen?")
        sendBroadcast("AI_CHAT_SHOW_CAPTURE")
        SystemClock.sleep(STATE_SETTLE_MS)
        scrollToTop()
        captureGolden("AC10_capture_button")
    }

    /** AC11: Screenshot Inline — Screenshot thumbnail bubble in chat */
    @Test
    fun capture_AC11_screenshot_inline() {
        Log.d(TAG, "AC11: Screenshot Inline")
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
        // AI response referencing the screenshot
        addAiMsg("**Screenshot Analysis:** Mom sent a dinner invite on WhatsApp.\n\n" +
            "She said *\"Beta dinner pe aao kal\"* \u2014 this is a warm, casual invitation.\n\n" +
            "**Suggested reply:** *\"Haan mummy, pakka! Kya special bana rahi ho? \uD83D\uDE0A\"*")
        scrollToTop()
        captureGolden("AC11_screenshot_inline")
    }

    /** AC12: NLS Context Banner — Purple notification context card */
    @Test
    fun capture_AC12_nls_context() {
        Log.d(TAG, "AC12: NLS Context Banner")
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        // Add NLS banner via broadcast
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
        captureGolden("AC12_nls_context")
    }

    /** AC13: Session Resumed — Banner with old messages at 70% opacity */
    @Test
    fun capture_AC13_session_resumed() {
        Log.d(TAG, "AC13: Session Resumed")
        openAiChat("personal")
        clearChat()
        setContext("WhatsApp", "Mom", "\uD83D\uDE0A Happy")
        // Session banner
        sendBroadcastWithInts("AI_CHAT_ADD_SESSION_BANNER", mapOf("message_count" to 3))
        SystemClock.sleep(MSG_SETTLE_MS)
        // Old messages (restored)
        addUserMsg("How should I reply to Mom?")
        addAiMsg("**Mom seems happy** \u2014 she's inviting you for dinner.", "REPLY_COPY")
        // NLS banner for fresh context
        sendBroadcast("AI_CHAT_ADD_NLS_BANNER", mapOf(
            "contact" to "Mom",
            "messages_json" to """[{"sender":"Mom","text":"Beta aaj 7 baje aa jaana"},{"sender":"Mom","text":"Paneer tikka bana rahi hun"}]"""
        ))
        SystemClock.sleep(MSG_SETTLE_MS)
        // New user message
        addUserMsg("She just texted again, what should I say?")
        // Show typing dots
        addLoading()
        scrollToTop()
        captureGolden("AC13_session_resumed")
    }

    /** AC14: Screenshot Analyzing — in-chat indicator (shared with FS10) */
    @Test
    fun capture_AC14_capture_analyzing() {
        Log.d(TAG, "AC14: Screenshot Analyzing")
        sendBroadcast("AI_CHAT_SHOW_CAPTURE_ANALYZING")
        SystemClock.sleep(OPEN_SETTLE_MS + 1200)
        captureGolden("AC14_capture_analyzing")
    }
}
