package project.witty.keys.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import project.witty.keys.keyboard.AiChat.*
import project.witty.keys.keyboard.ProductViews.UnifiedAiView
import project.witty.keys.keyboard.AssistantViews.SmartAssistantBar
import project.witty.keys.keyboard.KeyboardSwitcher
import java.util.Collections

/**
 * DebugSABController — Debug-only BroadcastReceiver for controlling SAB states.
 *
 * This receiver is ONLY registered in debug builds. It allows instrumentation tests
 * (GoldenCaptureTest) to drive SmartAssistantBar states via broadcast intents,
 * enabling real-keyboard screenshot capture for all 32 UI states.
 *
 * ## CRITICAL: All state changes go through sabManager.switchToState() via
 * sab.debugSwitchState(). Old SmartAssistantBar methods
 * like enterCustomMode(), showRow2State(), showStatCards(), performGrammarFix() etc.
 * are BLOCKED by TestModeController.isTestMode() guard in test mode.
 * handleAction() calls TestModeController.enterTestMode() to activate test harness.
 *
 * ## Supported Actions (SAB goldens + AI Chat + Emoji):
 *
 * OriginalView (G05-G11, G31):
 * - SHOW_SMART_REPLIES: OV_EXPANDED with reply chips
 * - COLLAPSE_VIEW: OV_COLLAPSED
 * - ENTER_CUSTOM_MODE: OV_CUSTOM
 * - SHOW_NO_CONTEXT: OV_NO_CONTEXT
 * - SHOW_ACCESSIBILITY_PROMPT: OV_ACCESSIBILITY
 * - SHOW_ROW2_SHIMMER: OV_ROW2_LOADING
 * - SHOW_OV_ERROR: OV_ERROR
 *
 * Contact Picker (G31):
 * - SHOW_CONTACT_PICKER: OV_CONTACT_PICKER with mock contact chips
 *
 * Screen Capture CTA (G32):
 * - SHOW_CAPTURE_CTA: Scan button highlighted with blue accent
 *
 * AI Chat P4 States (AC11-AC13):
 * - AI_CHAT_SHOW_SCREENSHOT: Personal chat with screenshot thumbnail inline (AC11)
 * - AI_CHAT_SHOW_NLS_CONTEXT: Personal chat with NLS notification context banner (AC12)
 * - AI_CHAT_SHOW_SESSION_RESUMED: Personal chat with session resumed + old messages (AC13)
 *
 * CTA Interactions (G12-G20):
 * - SETUP_TONE_PICKER: CTA_TONE_SELECT (or CTA_TONE_CUSTOM if custom mode active)
 * - SHOW_TONE_LOADING: CTA_TONE_ACTIVE_LOADING shimmer
 * - ACTIVATE_TONE: TONE_xxx with pinned chip + suggestions
 * - SETUP_LANG_PICKER: CTA_TRANSLATE (or CTA_TRANSLATE_CUSTOM if custom mode active)
 * - SHOW_TRANSLATE_LOADING: CTA_TRANSLATE_ACTIVE_LOADING shimmer
 * - SHOW_TRANSLATE_ACTIVE: CTA_TRANSLATE_ACTIVE with translated result
 * - SETUP_GRAMMAR_CTA: CTA_GRAMMAR
 *
 * Tone Variants (G21-G25):
 * - ACTIVATE_TONE(tone=PROFESSIONAL/CASUAL/SAVAGE/SARCASTIC/CALM)
 *
 * Special (G26-G27):
 * - SHOW_MILESTONE_TOAST: OV_MILESTONE toast overlay
 * - START_BRAIN_BLINK: OV_BRAIN_BLINK animation
 *
 * Bottom Sheets (G30):
 * - SHOW_CONSENT_SHEET: BS_ACC_CONSENT
 *
 * Utility:
 * - CANCEL_ANIMATIONS: No-op stabilizer for screenshot timing
 *
 * WittyKeys Unified Real-Keyboard Screenshot System
 * Created: February 10, 2026
 * Updated: March 11, 2026 — 32-golden SAB standard (added G32 capture CTA)
 */
class DebugSABController(
    private val keyboardSwitcher: KeyboardSwitcher
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "DebugSABController"
        private const val ACTION_PREFIX = "project.witty.keys.debug."
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Track custom mode for compound states (G15: custom + tone, G19: custom + translate)
    private var customModeActive = false

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "[DEBUG] Received action: $action")

        // Run on main thread since SAB methods must be called from UI thread
        mainHandler.post {
            try {
                handleAction(action, intent)
            } catch (e: Exception) {
                Log.e(TAG, "[DEBUG] Error handling action $action: ${e.message}", e)
            }
        }
    }

    private fun handleAction(action: String, intent: Intent) {
        // Emoji keyboard actions don't need test mode or SAB access —
        // handle them first to avoid test mode side effects
        if (action.startsWith("${ACTION_PREFIX}EMOJI_") ||
            action.startsWith("${ACTION_PREFIX}CLOSE_EMOJI") ||
            action.startsWith("${ACTION_PREFIX}RESET_EMOJI") ||
            action == "${ACTION_PREFIX}SHOW_EMOJI_KEYBOARD") {
            handleEmojiAction(action, intent)
            return
        }

        // AI Chat actions don't need SAB or test mode — handle before SAB block
        if (action.startsWith("${ACTION_PREFIX}OPEN_AI_CHAT") ||
            action.startsWith("${ACTION_PREFIX}AI_CHAT_") ||
            action.startsWith("${ACTION_PREFIX}SET_AI_CHAT_") ||
            action == "${ACTION_PREFIX}SHOW_REPLY_MODE" ||
            action == "${ACTION_PREFIX}SEND_AI_CHAT_MESSAGE") {
            handleAiChatAction(action, intent)
            return
        }

        // Full-Screen Chat actions — launch AiChatActivity with debug state
        if (action.startsWith("${ACTION_PREFIX}FS_")) {
            handleFullScreenAction(action, intent)
            return
        }

        // Keyboard V2 golden states (K01-K08)
        if (action.startsWith("${ACTION_PREFIX}K_")) {
            handleKeyboardV2Action(action, intent)
            return
        }

        // Overlay V2 golden states (O01-O07)
        if (action.startsWith("${ACTION_PREFIX}O_")) {
            handleOverlayV2Action(action, intent)
            return
        }

        // Fullscreen V2 golden states (F01-F06)
        if (action.startsWith("${ACTION_PREFIX}FV2_")) {
            handleFullscreenV2Action(action, intent)
            return
        }

        // SAB-related actions: activate test mode so SmartAssistantBarManager + MockData are used
        TestModeController.enterTestMode()

        val sab = keyboardSwitcher.smartAssistantBar
        if (sab == null) {
            Log.w(TAG, "[DEBUG] SmartAssistantBar not available")
            return
        }

        when (action) {
            // === Legacy MemoryView States (removed in Build 7.1) ===

            "${ACTION_PREFIX}SHOW_LOADING_SHIMMER" -> {
                // MV_LOADING removed — show OV_ROW2_LOADING instead
                sab.debugSwitchState("OV_ROW2_LOADING")
                Log.d(TAG, "[DEBUG] Showing loading shimmer (OV_ROW2_LOADING) — MV_LOADING removed")
            }

            "${ACTION_PREFIX}INJECT_SCENARIO" -> {
                val scenario = intent.getStringExtra("scenario") ?: "FRUSTRATED_BOSS"
                val data = getScenarioData(scenario)
                // MemoryViewData removed — show smart replies directly
                sab.debugShowSmartReplies(data.quickReplies)
                Log.d(TAG, "[DEBUG] Injected scenario as smart replies: $scenario")
            }

            "${ACTION_PREFIX}SHOW_ERROR" -> {
                sab.debugSwitchState("OV_ERROR")
                Log.d(TAG, "[DEBUG] Showing error (OV_ERROR)")
            }

            "${ACTION_PREFIX}SHOW_MEMORY_VIEW" -> {
                // MemoryView removed — show OV_EXPANDED instead
                sab.debugSwitchState("OV_EXPANDED")
                Log.d(TAG, "[DEBUG] MemoryView removed, showing OV_EXPANDED")
            }

            // === OriginalView States (G05-G11) ===

            "${ACTION_PREFIX}SHOW_SMART_REPLIES" -> {
                val repliesJson = intent.getStringExtra("replies_json") ?: "[\"Reply 1\",\"Reply 2\",\"Reply 3\",\"Reply 4\"]"
                val replies = parseJsonArray(repliesJson)
                sab.debugShowSmartReplies(replies)
                Log.d(TAG, "[DEBUG] Showing ${replies.size} smart replies via debugShowSmartReplies")
            }

            "${ACTION_PREFIX}COLLAPSE_VIEW" -> {
                sab.debugSwitchState("OV_COLLAPSED")
                Log.d(TAG, "[DEBUG] Collapsed view (OV_COLLAPSED)")
            }

            "${ACTION_PREFIX}ENTER_CUSTOM_MODE" -> {
                customModeActive = true
                sab.debugSwitchState("OV_CUSTOM")
                Log.d(TAG, "[DEBUG] Entered custom mode (OV_CUSTOM), flag set for compound states")
            }

            "${ACTION_PREFIX}SHOW_NO_CONTEXT" -> {
                sab.debugSwitchState("OV_NO_CONTEXT")
                Log.d(TAG, "[DEBUG] Showing no-context state (OV_NO_CONTEXT)")
            }

            "${ACTION_PREFIX}SHOW_ACCESSIBILITY_PROMPT" -> {
                sab.debugSwitchState("OV_ACCESSIBILITY")
                Log.d(TAG, "[DEBUG] Showing accessibility prompt (OV_ACCESSIBILITY)")
            }

            "${ACTION_PREFIX}SHOW_ROW2_SHIMMER" -> {
                sab.debugSwitchState("OV_ROW2_LOADING")
                Log.d(TAG, "[DEBUG] Showing Row2 shimmer (OV_ROW2_LOADING)")
            }

            "${ACTION_PREFIX}SHOW_OV_ERROR" -> {
                sab.debugSwitchState("OV_ERROR")
                Log.d(TAG, "[DEBUG] Showing OV error (OV_ERROR)")
            }

            // === Contact Picker (G31) ===

            "${ACTION_PREFIX}SHOW_CONTACT_PICKER" -> {
                // Uses debugShowContactPicker which sets Row2State.CONTACT_PICKER
                // and populates mock contact chips via sabManager.showContactPicker()
                val contactsJson = intent.getStringExtra("contacts_json")
                val contacts: List<String> = if (contactsJson != null) {
                    parseJsonArray(contactsJson)
                } else {
                    listOf("Mom", "Rahul", "Priya", "Arjun")
                }
                sab.debugShowContactPicker(contacts)
                Log.d(TAG, "[DEBUG] Showing contact picker with ${contacts.size} chips")
            }

            // === Screen Capture CTA (G32) ===

            "${ACTION_PREFIX}SHOW_CAPTURE_CTA" -> {
                sab.debugSwitchState("OV_EXPANDED")
                sab.setScreenCaptureHighlighted(true)
                Log.d(TAG, "[DEBUG] Showing capture CTA highlighted (G32)")
            }

            "${ACTION_PREFIX}SHOW_ORIGINAL_VIEW" -> {
                sab.showOriginalViewWithContext()
                Log.d(TAG, "[DEBUG] Showing OriginalView")
            }

            // === CTA Interaction States (G12-G20) ===

            "${ACTION_PREFIX}SETUP_TONE_PICKER" -> {
                if (customModeActive) {
                    customModeActive = false
                    sab.debugSwitchState("CTA_TONE_CUSTOM")
                    Log.d(TAG, "[DEBUG] Tone picker + custom mode -> CTA_TONE_CUSTOM")
                } else {
                    sab.debugSwitchState("CTA_TONE_SELECT")
                    Log.d(TAG, "[DEBUG] Tone picker -> CTA_TONE_SELECT")
                }
            }

            "${ACTION_PREFIX}SHOW_TONE_LOADING" -> {
                sab.debugSwitchState("CTA_TONE_ACTIVE_LOADING")
                Log.d(TAG, "[DEBUG] Showing tone loading shimmer (CTA_TONE_ACTIVE_LOADING)")
            }

            "${ACTION_PREFIX}ACTIVATE_TONE" -> {
                val tone = intent.getStringExtra("tone") ?: "PROFESSIONAL"
                val stateName = "TONE_$tone"
                sab.debugSwitchState(stateName)
                Log.d(TAG, "[DEBUG] Activated tone: $tone -> $stateName")
            }

            "${ACTION_PREFIX}SETUP_LANG_PICKER" -> {
                if (customModeActive) {
                    customModeActive = false
                    sab.debugSwitchState("CTA_TRANSLATE_CUSTOM")
                    Log.d(TAG, "[DEBUG] Language picker + custom mode -> CTA_TRANSLATE_CUSTOM")
                } else {
                    sab.debugSwitchState("CTA_TRANSLATE")
                    Log.d(TAG, "[DEBUG] Language picker -> CTA_TRANSLATE")
                }
            }

            "${ACTION_PREFIX}SHOW_TRANSLATE_LOADING" -> {
                sab.debugSwitchState("CTA_TRANSLATE_ACTIVE_LOADING")
                Log.d(TAG, "[DEBUG] Showing translate loading shimmer (CTA_TRANSLATE_ACTIVE_LOADING)")
            }

            "${ACTION_PREFIX}SHOW_TRANSLATE_ACTIVE" -> {
                sab.debugSwitchState("CTA_TRANSLATE_ACTIVE_RESULT")
                Log.d(TAG, "[DEBUG] Showing translate active with result (CTA_TRANSLATE_ACTIVE_RESULT)")
            }

            "${ACTION_PREFIX}SETUP_GRAMMAR_CTA" -> {
                sab.debugSwitchState("CTA_GRAMMAR")
                Log.d(TAG, "[DEBUG] Showing Grammar CTA state")
            }

            // === Special States (G26-G28) ===

            "${ACTION_PREFIX}SHOW_MILESTONE_TOAST" -> {
                sab.debugSwitchState("OV_MILESTONE")
                Log.d(TAG, "[DEBUG] Showing milestone toast (OV_MILESTONE)")
            }

            "${ACTION_PREFIX}START_BRAIN_BLINK" -> {
                sab.debugSwitchState("OV_BRAIN_BLINK")
                Log.d(TAG, "[DEBUG] Started brain blink (OV_BRAIN_BLINK)")
            }

            // === Bottom Sheets ===

            "${ACTION_PREFIX}SHOW_BOTTOM_SHEET" -> {
                // BS_MV_MODAL removed in Build 7.1 — no-op
                Log.d(TAG, "[DEBUG] SHOW_BOTTOM_SHEET ignored — BS_MV_MODAL removed in Build 7.1")
            }

            "${ACTION_PREFIX}SHOW_STAT_CARDS" -> {
                // OV_STATS removed in Build 7.1 — show OV_EXPANDED instead
                sab.debugSwitchState("OV_EXPANDED")
                Log.d(TAG, "[DEBUG] OV_STATS removed, showing OV_EXPANDED instead")
            }

            "${ACTION_PREFIX}SHOW_CONSENT_SHEET" -> {
                sab.debugSwitchState("BS_ACC_CONSENT")
                Log.d(TAG, "[DEBUG] Showing accessibility consent bottom sheet (BS_ACC_CONSENT)")
            }

            // === Utility ===

            "${ACTION_PREFIX}CANCEL_ANIMATIONS" -> {
                TestModeController.exitTestMode()
                Log.d(TAG, "[DEBUG] Cancel animations + exited test mode")
            }

            "${ACTION_PREFIX}TRIGGER_AI_REPLIES" -> {
                val scenario = intent.getStringExtra("scenario") ?: "FRUSTRATED_BOSS"
                val data = getScenarioData(scenario)
                val lastMessage = data.lastMessage.ifEmpty { "Hello" }
                val senderName = data.senderName.ifEmpty { "User" }
                val emotion = "NEUTRAL"
                val appName = data.appName.ifEmpty { "WhatsApp" }

                // Build system prompt matching production format
                val systemPrompt = """You are an AI keyboard assistant. Generate 4 contextually appropriate reply suggestions.
The user received a message in $appName from $senderName.
Detected emotion: $emotion.
Reply in the same language and tone as the incoming message.
Return one reply per line, no numbering, no quotes."""

                Log.d(TAG, "[DEBUG] TRIGGER_AI_REPLIES: scenario=$scenario, calling ClaudeApi")

                // Call real AI pipeline
                val claudeApi = project.witty.keys.api.ClaudeApi()
                claudeApi.generateReplies(systemPrompt, lastMessage, object : project.witty.keys.api.ClaudeApi.ReplyCallback {
                    override fun onRepliesGenerated(replies: List<String>) {
                        mainHandler.post {
                            Log.d(TAG, "[DEBUG] TRIGGER_AI_REPLIES: got ${replies.size} real AI replies")
                            sab.debugShowSmartReplies(replies)
                        }
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "[DEBUG] TRIGGER_AI_REPLIES: AI error: $error")
                        // Fallback: show mock replies so test doesn't hang
                        mainHandler.post {
                            val mockReplies = data.quickReplies.ifEmpty { listOf("Reply 1", "Reply 2") }
                            sab.debugShowSmartReplies(mockReplies)
                            Log.w(TAG, "[DEBUG] TRIGGER_AI_REPLIES: fell back to mock replies")
                        }
                    }
                })
            }

            // === Theme Control ===

            "${ACTION_PREFIX}SET_DARK_THEME" -> {
                try {
                    val uiModeManager = keyboardSwitcher.getmLatinIME()?.getSystemService(Context.UI_MODE_SERVICE)
                    if (uiModeManager is android.app.UiModeManager) {
                        uiModeManager.nightMode = android.app.UiModeManager.MODE_NIGHT_YES
                        Log.d(TAG, "[DEBUG] Dark theme set")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[DEBUG] SET_DARK_THEME failed: ${e.message}", e)
                }
            }

            "${ACTION_PREFIX}SET_LIGHT_THEME" -> {
                try {
                    val uiModeManager = keyboardSwitcher.getmLatinIME()?.getSystemService(Context.UI_MODE_SERVICE)
                    if (uiModeManager is android.app.UiModeManager) {
                        uiModeManager.nightMode = android.app.UiModeManager.MODE_NIGHT_NO
                        Log.d(TAG, "[DEBUG] Light theme set")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[DEBUG] SET_LIGHT_THEME failed: ${e.message}", e)
                }
            }

            // === E2E Lifecycle Test Support ===

            "${ACTION_PREFIX}QUERY_STATE" -> {
                Log.i("WK_E2E", "[APP] Received QUERY_STATE broadcast")
                val state = JSONObject()

                // Current SAB state
                val currentState = sab.getSabManager()?.currentState?.name ?: "NO_SAB"
                state.put("sab_state", currentState)
                Log.i("WK_E2E", "[APP] Current state: $currentState")

                // State history
                val history = sab.getSabManager()?.getStateHistory() ?: emptyList()
                val historyArray = JSONArray()
                history.forEach { historyArray.put(it) }
                state.put("state_history", historyArray)

                // Reply chips (from current smart replies)
                val chips = JSONArray()
                var chipCount = 0
                sab.getCurrentReplies()?.let { replies ->
                    replies.forEach { reply ->
                        chips.put(reply ?: "")
                        chipCount++
                    }
                }
                state.put("reply_chips", chips)
                state.put("reply_chip_count", chipCount)

                // AI Chat state
                val aiView = getAiChatView()
                state.put("ai_chat_open", aiView?.isShown == true)
                val modeInt = aiView?.currentMode ?: -1
                state.put("ai_chat_mode", when (modeInt) {
                    UnifiedAiView.MODE_GENERAL -> "GENERAL"
                    UnifiedAiView.MODE_PERSONAL -> "PERSONAL"
                    else -> "NONE"
                })
                val chatMessages = JSONArray()
                aiView?.chatItems?.forEach { item ->
                    val msgObj = JSONObject()
                    when (item) {
                        is UserMessage -> {
                            msgObj.put("role", "user")
                            msgObj.put("text", item.text ?: "")
                            chatMessages.put(msgObj)
                        }
                        is AiMessage -> {
                            msgObj.put("role", "ai")
                            msgObj.put("text", item.markdownText ?: "")
                            chatMessages.put(msgObj)
                        }
                    }
                }
                state.put("ai_chat_messages", chatMessages)
                state.put("ai_chat_message_count", chatMessages.length())

                // Emoji state
                val emojiKb = getEmojiKeyboard()
                var emojiOpen = false
                var emojiCategory = ""
                var emojiGifMode = false
                var emojiSearchActive = false
                if (emojiKb != null) {
                    try {
                        val isShownMethod = emojiKb.javaClass.getMethod("isShown")
                        emojiOpen = isShownMethod.invoke(emojiKb) as? Boolean ?: false

                        val catField = emojiKb.javaClass.getDeclaredField("lastSearchCategory")
                        catField.isAccessible = true
                        emojiCategory = catField.get(emojiKb) as? String ?: ""

                        val modeField = emojiKb.javaClass.getDeclaredField("currentMode")
                        modeField.isAccessible = true
                        val mode = modeField.get(emojiKb)
                        emojiGifMode = mode?.toString() == "GIPHY"

                        val searchMethod = emojiKb.javaClass.getMethod("isSearchActive")
                        emojiSearchActive = searchMethod.invoke(emojiKb) as? Boolean ?: false
                    } catch (e: Exception) {
                        Log.w("WK_E2E", "[APP] Emoji state read error: ${e.message}")
                    }
                }
                state.put("emoji_open", emojiOpen)
                state.put("emoji_category", emojiCategory)
                state.put("emoji_gif_mode", emojiGifMode)
                state.put("emoji_search_active", emojiSearchActive)

                // Write to file for test to read — use app's external files dir for cross-process access
                val dir = keyboardSwitcher.getmLatinIME()?.getExternalFilesDir(null)
                    ?: java.io.File("/data/local/tmp")
                val file = java.io.File(dir, "wk_test_state.json")
                file.writeText(state.toString())
                Log.i("WK_E2E", "[APP] State written to ${file.absolutePath}: chipCount=$chipCount")
            }

            "${ACTION_PREFIX}RESET_STATE_HISTORY" -> {
                Log.i("WK_E2E", "[APP] Received RESET_STATE_HISTORY broadcast")
                sab.getSabManager()?.clearStateHistory()
            }

            "${ACTION_PREFIX}TAP_REPLY" -> {
                val index = intent.getIntExtra("index", 0)
                Log.i("WK_E2E", "[APP] Received TAP_REPLY broadcast: index=$index")
                sab.getCurrentReplies()?.let { replies ->
                    if (index < replies.size) {
                        val replyText = replies[index] ?: ""
                        Log.i("WK_E2E", "[APP] Tapping reply: '$replyText'")
                        // Simulate reply selection — insert text via InputConnection
                        keyboardSwitcher.getmLatinIME()?.currentInputConnection?.let { ic ->
                            ic.commitText(replyText, 1)
                            Log.i("WK_E2E", "[APP] Reply text committed to InputConnection")
                        }
                    } else {
                        Log.e("WK_E2E", "[APP] TAP_REPLY index $index out of bounds (${replies.size} replies)")
                    }
                }
            }

            "${ACTION_PREFIX}TRIGGER_PIPELINE" -> {
                // E2E Test: Trigger the REAL AI pipeline bypassing accessibility service.
                // Constructs a Chat from broadcast extras, then calls real ReplyGenerator.
                // This is needed because ScreenReaderAccessibility can't bind in the
                // instrumentation test process.
                val sender = intent.getStringExtra("sender") ?: "Unknown"
                val messageText = intent.getStringExtra("message") ?: ""
                Log.i("WK_E2E", "[APP] TRIGGER_PIPELINE: sender=$sender message=${messageText.take(50)}")

                val latinIME = keyboardSwitcher.getmLatinIME()
                val replyGenerator = latinIME?.replyGenerator
                if (replyGenerator == null) {
                    Log.e("WK_E2E", "[APP] TRIGGER_PIPELINE FAILED: ReplyGenerator is null")
                    return
                }

                // Build a Chat context from the broadcast extras
                val messages = mutableListOf<project.witty.keys.app.context.ChatMessage>()
                // Add the incoming message
                messages.add(
                    project.witty.keys.app.context.ChatMessage(
                        sender, messageText, "", false
                    )
                )
                val participants = listOf(sender, "You")
                val chat = project.witty.keys.app.context.Chat(
                    "WittyKeys Tutorial", participants, messages
                )

                // Step 1: Show loading state immediately
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    sab.getSabManager()?.switchToState(
                        project.witty.keys.keyboard.AssistantViews.SabState.OV_ROW2_LOADING
                    )
                    Log.i("WK_E2E", "[APP] TRIGGER_PIPELINE: OV_ROW2_LOADING shown")
                }

                // Step 2: Call real ReplyGenerator -> real API
                Log.i("WK_E2E", "[APP] TRIGGER_PIPELINE: Calling generateReplies (REAL API)")
                replyGenerator.generateReplies(chat, object : project.witty.keys.app.context.ReplyGenerator.ReplyCallback {
                    override fun onRepliesGenerated(replies: MutableList<String>) {
                        Log.i("WK_E2E", "[APP] TRIGGER_PIPELINE: API SUCCESS — ${replies.size} replies")

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            sab.debugShowSmartReplies(replies)
                            Log.i("WK_E2E", "[APP] TRIGGER_PIPELINE: Smart replies displayed with ${replies.size} replies")
                        }
                    }

                    override fun onError(error: String?) {
                        Log.e("WK_E2E", "[APP] TRIGGER_PIPELINE: API ERROR — $error")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            sab.getSabManager()?.switchToState(
                                project.witty.keys.keyboard.AssistantViews.SabState.OV_ERROR
                            )
                        }
                    }
                })
            }

            else -> {
                Log.w(TAG, "[DEBUG] Unknown action: $action")
            }
        }
    }

    /**
     * Handle emoji keyboard actions WITHOUT entering test mode.
     * Test mode activates SAB state management which can interfere with
     * emoji keyboard visibility (e.g., skin tone popup over emoji grid).
     */
    private fun handleEmojiAction(action: String, intent: Intent) {
        when (action) {
            "${ACTION_PREFIX}SHOW_EMOJI_KEYBOARD" -> {
                keyboardSwitcher.toggleEmojiKeyboardView()
                Log.d(TAG, "[DEBUG] Toggled emoji keyboard view")
            }

            "${ACTION_PREFIX}EMOJI_SELECT_CATEGORY" -> {
                val category = intent.getStringExtra("category") ?: "Recents"
                val emojiKb = getEmojiKeyboard()
                if (emojiKb != null) {
                    try {
                        val showMethod = emojiKb.javaClass.getDeclaredMethod("showEmojisForCategory", String::class.java)
                        showMethod.isAccessible = true
                        showMethod.invoke(emojiKb, category)
                        val catViewField = emojiKb.javaClass.getDeclaredField("emojiCategoryView")
                        catViewField.isAccessible = true
                        val catView = catViewField.get(emojiKb)
                        if (catView != null) {
                            val selectMethod = catView.javaClass.getDeclaredMethod("selectCategory", String::class.java)
                            selectMethod.invoke(catView, category)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[DEBUG] EMOJI_SELECT_CATEGORY reflection failed: ${e.message}", e)
                    }
                }
                Log.d(TAG, "[DEBUG] Emoji category selected: ${intent.getStringExtra("category")}")
            }

            "${ACTION_PREFIX}EMOJI_CLEAR_RECENTS" -> {
                try {
                    val ime = keyboardSwitcher.getmLatinIME()
                    val prefs = ime?.applicationContext
                        ?.getSharedPreferences("WittyKeys.EmojiRecents", Context.MODE_PRIVATE)
                    prefs?.edit()?.clear()?.apply()
                    Log.d(TAG, "[DEBUG] Emoji recents cleared")
                } catch (e: Exception) {
                    Log.e(TAG, "[DEBUG] Failed to clear emoji recents: ${e.message}")
                }
            }

            "${ACTION_PREFIX}EMOJI_ACTIVATE_SEARCH" -> {
                val emojiKb = getEmojiKeyboard()
                if (emojiKb != null) {
                    try {
                        val activateMethod = emojiKb.javaClass.getDeclaredMethod("activateSearch")
                        activateMethod.isAccessible = true
                        activateMethod.invoke(emojiKb)
                        Log.d(TAG, "[DEBUG] Emoji search activated")
                    } catch (e: Exception) {
                        Log.e(TAG, "[DEBUG] EMOJI_ACTIVATE_SEARCH failed: ${e.message}", e)
                    }
                }
            }

            "${ACTION_PREFIX}EMOJI_SEARCH_TEXT" -> {
                val query = intent.getStringExtra("query") ?: "heart"
                val emojiKb = getEmojiKeyboard()
                if (emojiKb != null) {
                    try {
                        val getSearchViewMethod = emojiKb.javaClass.getMethod("getSearchView")
                        val searchView = getSearchViewMethod.invoke(emojiKb)
                        if (searchView != null) {
                            val setTextMethod = searchView.javaClass.getMethod("setSearchText", String::class.java)
                            setTextMethod.invoke(searchView, query)
                        }
                        Log.d(TAG, "[DEBUG] Emoji search text set: $query")
                    } catch (e: Exception) {
                        Log.e(TAG, "[DEBUG] EMOJI_SEARCH_TEXT failed: ${e.message}", e)
                    }
                }
            }

            "${ACTION_PREFIX}EMOJI_DEACTIVATE_SEARCH" -> {
                val emojiKb = getEmojiKeyboard()
                if (emojiKb != null) {
                    try {
                        val deactivateMethod = emojiKb.javaClass.getDeclaredMethod("deactivateSearch")
                        deactivateMethod.isAccessible = true
                        deactivateMethod.invoke(emojiKb)
                        Log.d(TAG, "[DEBUG] Emoji search deactivated")
                    } catch (e: Exception) {
                        Log.e(TAG, "[DEBUG] EMOJI_DEACTIVATE_SEARCH failed: ${e.message}", e)
                    }
                }
            }

            "${ACTION_PREFIX}EMOJI_SHOW_SKIN_TONE" -> {
                val emojiKb = getEmojiKeyboard()
                if (emojiKb != null) {
                    try {
                        val popupMethod = emojiKb.javaClass.getMethod("showSkinTonePopupForTest")
                        popupMethod.invoke(emojiKb)
                        Log.d(TAG, "[DEBUG] Skin tone popup triggered")
                    } catch (e: Exception) {
                        Log.e(TAG, "[DEBUG] EMOJI_SHOW_SKIN_TONE failed: ${e.message}", e)
                    }
                } else {
                    Log.w(TAG, "[DEBUG] EMOJI_SHOW_SKIN_TONE: emoji keyboard not available")
                }
            }

            "${ACTION_PREFIX}EMOJI_SWITCH_GIF" -> {
                val emojiKb = getEmojiKeyboard()
                if (emojiKb != null) {
                    try {
                        val switchMethod = emojiKb.javaClass.getDeclaredMethod(
                            "switchToMode",
                            Class.forName("project.witty.keys.keyboard.EmojiKeyboard.EmojiKeyboard\$DisplayMode")
                        )
                        switchMethod.isAccessible = true
                        val giphyMode = Class.forName("project.witty.keys.keyboard.EmojiKeyboard.EmojiKeyboard\$DisplayMode")
                            .enumConstants?.find { (it as Enum<*>).name == "GIPHY" }
                        if (giphyMode != null) switchMethod.invoke(emojiKb, giphyMode)
                        Log.d(TAG, "[DEBUG] Switched to GIF mode")
                    } catch (e: Exception) {
                        Log.e(TAG, "[DEBUG] EMOJI_SWITCH_GIF failed: ${e.message}", e)
                    }
                }
            }

            "${ACTION_PREFIX}EMOJI_SWITCH_EMOJI" -> {
                val emojiKb = getEmojiKeyboard()
                if (emojiKb != null) {
                    try {
                        val switchMethod = emojiKb.javaClass.getDeclaredMethod(
                            "switchToMode",
                            Class.forName("project.witty.keys.keyboard.EmojiKeyboard.EmojiKeyboard\$DisplayMode")
                        )
                        switchMethod.isAccessible = true
                        val emojiMode = Class.forName("project.witty.keys.keyboard.EmojiKeyboard.EmojiKeyboard\$DisplayMode")
                            .enumConstants?.find { (it as Enum<*>).name == "EMOJI" }
                        if (emojiMode != null) switchMethod.invoke(emojiKb, emojiMode)
                        Log.d(TAG, "[DEBUG] Switched to Emoji mode")
                    } catch (e: Exception) {
                        Log.e(TAG, "[DEBUG] EMOJI_SWITCH_EMOJI failed: ${e.message}", e)
                    }
                }
            }

            "${ACTION_PREFIX}CLOSE_EMOJI_KEYBOARD" -> {
                // Dismiss skin tone popup before closing emoji keyboard
                val emojiKb = getEmojiKeyboard()
                if (emojiKb != null) {
                    try {
                        val dismissMethod = emojiKb.javaClass.getMethod("dismissSkinTonePopup")
                        dismissMethod.invoke(emojiKb)
                    } catch (e: Exception) {
                        Log.w(TAG, "[DEBUG] dismissSkinTonePopup not available: ${e.message}")
                    }
                }
                keyboardSwitcher.showKeyboardView()
                Log.d(TAG, "[DEBUG] Force-closed emoji keyboard → alphabet")
            }

            "${ACTION_PREFIX}EMOJI_SET_RECENTS" -> {
                val emojisJson = intent.getStringExtra("emojis")
                    ?: "[\"😀\",\"😍\",\"👍\",\"❤️\",\"🔥\",\"😂\",\"🎉\",\"✨\",\"🥰\"]"
                try {
                    val ime = keyboardSwitcher.getmLatinIME()
                    val prefs = ime?.applicationContext
                        ?.getSharedPreferences("WittyKeys.EmojiRecents", Context.MODE_PRIVATE)
                    prefs?.edit()?.putString("recent_emojis", emojisJson)?.apply()
                    Log.d(TAG, "[DEBUG] Emoji recents set to: $emojisJson")
                } catch (e: Exception) {
                    Log.e(TAG, "[DEBUG] Failed to set emoji recents: ${e.message}")
                }
            }

            "${ACTION_PREFIX}RESET_EMOJI_STATE" -> {
                keyboardSwitcher.showKeyboardView()
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("cmd", "uimode", "night", "no"))
                    process.waitFor()
                } catch (e: Exception) {
                    Log.w(TAG, "[DEBUG] uimode reset via Runtime failed")
                }
                Log.d(TAG, "[DEBUG] Full emoji state reset complete")
            }

            else -> Log.w(TAG, "[DEBUG] Unknown emoji action: $action")
        }
    }

    /**
     * Handle AI Chat actions WITHOUT entering test mode.
     * These operate on UnifiedAiView directly, not SAB.
     */
    private fun handleAiChatAction(action: String, intent: Intent) {
        when (action) {
            "${ACTION_PREFIX}OPEN_AI_CHAT" -> {
                Log.d(TAG, "[DEBUG] OPEN_AI_CHAT: starting...")
                val view = showAiChat()
                if (view != null) {
                    view.clearSession()
                    val mode = intent.getStringExtra("mode") ?: "general"
                    if (mode == "personal") {
                        view.setMode(UnifiedAiView.MODE_PERSONAL)
                    } else {
                        view.setMode(UnifiedAiView.MODE_GENERAL)
                    }
                    Log.d(TAG, "[DEBUG] OPEN_AI_CHAT: success in $mode mode, visibility=${view.visibility}")
                } else {
                    Log.e(TAG, "[DEBUG] OPEN_AI_CHAT: FAILED — showAiChat() returned null")
                }
            }

            "${ACTION_PREFIX}AI_CHAT_ADD_USER_MSG" -> {
                val view = getAiChatView()
                if (view == null) {
                    Log.e(TAG, "[DEBUG] AI_CHAT_ADD_USER_MSG: view is null, cannot add message")
                    return
                }
                val text = intent.getStringExtra("text") ?: "Hello, how are you?"
                view.addItem(UserMessage(text))
                Log.d(TAG, "[DEBUG] Added user message: ${text.take(50)}")
            }

            "${ACTION_PREFIX}AI_CHAT_ADD_AI_MSG" -> {
                val view = getAiChatView() ?: return
                val text = intent.getStringExtra("text") ?: "I'm doing great!"
                val ctaTypeStr = intent.getStringExtra("cta_type") ?: "REPLY_COPY"
                val ctaType = try {
                    CtaType.valueOf(ctaTypeStr)
                } catch (e: Exception) {
                    CtaType.REPLY_COPY
                }
                view.addItem(AiMessage(text, ctaType))
                Log.d(TAG, "[DEBUG] Added AI message with CTA=$ctaType")
            }

            "${ACTION_PREFIX}AI_CHAT_SHOW_LOADING" -> {
                val view = getAiChatView() ?: return
                view.addItem(Loading.INSTANCE)
                Log.d(TAG, "[DEBUG] Added loading indicator")
            }

            "${ACTION_PREFIX}AI_CHAT_SHOW_ERROR" -> {
                val view = getAiChatView() ?: return
                val text = intent.getStringExtra("text") ?: "Network error. Please try again."
                view.addItem(ErrorMessage(text) { Log.d(TAG, "[DEBUG] Retry tapped") })
                Log.d(TAG, "[DEBUG] Added error message: $text")
            }

            "${ACTION_PREFIX}AI_CHAT_ADD_SYSTEM_MSG" -> {
                val view = getAiChatView() ?: return
                val text = intent.getStringExtra("text") ?: "System message"
                view.addItem(SystemMessage(text))
                Log.d(TAG, "[DEBUG] Added system message: $text")
            }

            "${ACTION_PREFIX}AI_CHAT_ADD_METADATA_CARD" -> {
                val view = getAiChatView() ?: return
                val jsonStr = intent.getStringExtra("json") ?: """{"app":"WhatsApp","contact":"John","screen":"Chat"}"""
                try {
                    val json = JSONObject(jsonStr)
                    val appName = json.optString("app", "App")
                    val contact = json.optString("contact", "User")
                    val emotion = json.optString("emotion", "")
                    val timeActive = json.optString("timeActive", "")
                    val messageCount = json.optInt("messageCount", 0)
                    val participants = listOf(contact)
                    val messages = emptyList<project.witty.keys.app.context.ChatMessage>()
                    val chatCtx = project.witty.keys.app.context.Chat(appName, participants, messages)
                    val card = MetadataCard(chatCtx)
                    if (emotion.isNotEmpty()) card.emotion = emotion
                    if (timeActive.isNotEmpty()) card.timeActive = timeActive
                    if (messageCount > 0) card.messageCount = messageCount
                    val expanded = json.optBoolean("expanded", false)
                    if (expanded) card.isExpanded = expanded
                    view.addItem(card)
                } catch (e: Exception) {
                    Log.e(TAG, "[DEBUG] Failed to parse metadata JSON: ${e.message}")
                }
                Log.d(TAG, "[DEBUG] Added metadata card")
            }

            "${ACTION_PREFIX}AI_CHAT_SET_CONTEXT" -> {
                val view = getAiChatView() ?: return
                val appName = intent.getStringExtra("app_name") ?: ""
                val contactName = intent.getStringExtra("contact_name") ?: ""
                val emotion = intent.getStringExtra("emotion") ?: ""
                view.setContextInfo(appName, contactName, emotion)
                Log.d(TAG, "[DEBUG] Set context: app=$appName, contact=$contactName, emotion=$emotion")
            }

            "${ACTION_PREFIX}SET_AI_CHAT_UI_STATE" -> {
                val view = getAiChatView()
                if (view == null) {
                    Log.e(TAG, "[DEBUG] SET_AI_CHAT_UI_STATE: view is null")
                    return
                }
                val state = intent.getIntExtra("state", UnifiedAiView.STATE_AI_VIEW)
                val mode = intent.getIntExtra("mode", -1)
                view.setUIState(state)
                if (mode >= 0) {
                    view.setMode(mode)
                }
                Log.d(TAG, "[DEBUG] Set UI state=$state, mode=$mode")
            }

            "${ACTION_PREFIX}SET_AI_CHAT_INPUT_TEXT" -> {
                val view = getAiChatView()
                if (view == null) {
                    Log.e(TAG, "[DEBUG] SET_AI_CHAT_INPUT_TEXT: view is null")
                    return
                }
                val text = intent.getStringExtra("text") ?: ""
                val chatInput = view.getChatInput()
                if (chatInput != null) {
                    chatInput.activate()
                    chatInput.setText(text)
                    Log.d(TAG, "[DEBUG] Set input text: ${text.take(50)}")
                } else {
                    Log.e(TAG, "[DEBUG] SET_AI_CHAT_INPUT_TEXT: chatInput is null")
                }
            }

            "${ACTION_PREFIX}AI_CHAT_SCROLL_TO_TOP" -> {
                val view = getAiChatView() ?: return
                view.scrollToTop()
                Log.d(TAG, "[DEBUG] Scrolled AI Chat to top")
            }

            "${ACTION_PREFIX}AI_CHAT_CLEAR" -> {
                val view = getAiChatView() ?: return
                view.clearSession()
                Log.d(TAG, "[DEBUG] Cleared AI Chat session")
            }

            "${ACTION_PREFIX}AI_CHAT_SET_REPLY_BAR" -> {
                val view = getAiChatView() ?: return
                val visible = intent.getBooleanExtra("visible", true)
                view.setReplyBarVisible(visible)
                Log.d(TAG, "[DEBUG] Set reply bar visible=$visible")
            }

            "${ACTION_PREFIX}SHOW_REPLY_MODE" -> {
                Log.d(TAG, "[DEBUG] SHOW_REPLY_MODE: starting...")
                // Show keyboard with AI header strip above it
                keyboardSwitcher.showReplyModeView()
                android.os.SystemClock.sleep(300)

                val view = getAiChatView()
                if (view != null) {
                    view.clearSession()
                    view.setUIState(UnifiedAiView.STATE_REPLY_MODE)
                    val mode = intent.getStringExtra("mode") ?: "general"
                    if (mode == "personal") {
                        view.setMode(UnifiedAiView.MODE_PERSONAL)
                    } else {
                        view.setMode(UnifiedAiView.MODE_GENERAL)
                    }
                    Log.d(TAG, "[DEBUG] SHOW_REPLY_MODE: success in $mode mode")
                } else {
                    Log.e(TAG, "[DEBUG] SHOW_REPLY_MODE: view is null")
                }
            }

            "${ACTION_PREFIX}SEND_AI_CHAT_MESSAGE" -> {
                // E2E Test: Send message via REAL AI pipeline (not just UI insert)
                val text = intent.getStringExtra("text") ?: return
                Log.i("WK_E2E", "[APP] SEND_AI_CHAT_MESSAGE: text='${text.take(50)}'")
                // performAiAction(AI_CHAT, text) adds user message, shows loading, calls real API
                keyboardSwitcher.performAiAction(
                    KeyboardSwitcher.AiAction.AI_CHAT, text
                )
            }

            // === Screen Capture Button in AI Chat (AC10) ===

            "${ACTION_PREFIX}AI_CHAT_SHOW_CAPTURE" -> {
                val view = getAiChatView()
                if (view != null) {
                    view.setCaptureButtonHighlighted(true)
                    Log.d(TAG, "[DEBUG] AI Chat capture button highlighted (AC10)")
                } else {
                    Log.e(TAG, "[DEBUG] AI_CHAT_SHOW_CAPTURE: view is null")
                }
            }

            // === AI Chat Screenshot Inline (AC11) ===
            "${ACTION_PREFIX}AI_CHAT_SHOW_SCREENSHOT" -> {
                showAiChat()
                mainHandler.postDelayed({
                    val view = getAiChatView() ?: return@postDelayed
                    view.clearSession()
                    view.setMode(UnifiedAiView.MODE_PERSONAL)
                    view.setContextInfo("WhatsApp", "Mom", "\uD83D\uDE0A Happy")

                    // Add screenshot message
                    view.addScreenshotMessage(
                        "",  // Empty = placeholder mode
                        "I can see a WhatsApp chat with Mom. She sent \"Beta dinner pe aao kal\" \u2014 looks like a dinner invitation for tomorrow.",
                        641, 1568
                    )

                    mainHandler.postDelayed({
                        // AI response referencing the screenshot
                        view.addItem(AiMessage(
                            "**Screenshot Analysis:** Mom sent a dinner invite on WhatsApp.\n\n" +
                            "She said *\"Beta dinner pe aao kal\"* \u2014 this is a warm, casual invitation.\n\n" +
                            "**Suggested reply:** *\"Haan mummy, pakka! Kya special bana rahi ho? \uD83D\uDE0A\"*",
                            CtaType.REGENERATE_COPY_REPLY
                        ))
                    }, 200)
                }, 400)
                Log.d(TAG, "[DEBUG] Showing AI Chat with screenshot inline (AC11)")
            }

            // === AI Chat NLS Context Banner (AC12) ===
            "${ACTION_PREFIX}AI_CHAT_SHOW_NLS_CONTEXT" -> {
                showAiChat()
                mainHandler.postDelayed({
                    val view = getAiChatView() ?: return@postDelayed
                    view.clearSession()
                    view.setMode(UnifiedAiView.MODE_PERSONAL)
                    view.setContextInfo("WhatsApp", "Mom", "\uD83D\uDE0A Happy")

                    // Add NLS banner
                    view.addNlsBanner("Mom", """[{"sender":"Mom","text":"Beta dinner pe aao kal"},{"sender":"Mom","text":"Dad bhi aa rahe hain"},{"sender":"You","text":"Ok mummy"}]""")

                    mainHandler.postDelayed({
                        // User question referencing context
                        view.addItem(UserMessage("What did Mom say about dinner?"))

                        mainHandler.postDelayed({
                            // AI response using NLS context
                            view.addItem(AiMessage(
                                "**Based on Mom's recent messages:**\n\n" +
                                "She invited you for dinner tomorrow and mentioned Dad is coming too. " +
                                "You already replied *\"Ok mummy\"* so she knows you're coming.\n\n" +
                                "**You could follow up with:** *\"Kya laun mummy? Dessert le aata hun \uD83C\uDF70\"*",
                                CtaType.REGENERATE_COPY_REPLY
                            ))
                        }, 200)
                    }, 200)
                }, 400)
                Log.d(TAG, "[DEBUG] Showing AI Chat with NLS context banner (AC12)")
            }

            // === AI Chat Session Resumed (AC13) ===
            "${ACTION_PREFIX}AI_CHAT_SHOW_SESSION_RESUMED" -> {
                showAiChat()
                mainHandler.postDelayed({
                    val view = getAiChatView() ?: return@postDelayed
                    view.clearSession()
                    view.setMode(UnifiedAiView.MODE_PERSONAL)
                    view.setContextInfo("WhatsApp", "Mom", "\uD83D\uDE0A Happy")

                    // Session banner
                    view.addSessionBanner(3)

                    mainHandler.postDelayed({
                        // Old messages (restored)
                        view.addItem(UserMessage("How should I reply to Mom?"))

                        mainHandler.postDelayed({
                            view.addItem(AiMessage(
                                "**Mom seems happy** \u2014 she's inviting you for dinner.",
                                CtaType.REPLY_COPY
                            ))

                            mainHandler.postDelayed({
                                // NLS banner for fresh context
                                view.addNlsBanner("Mom", """[{"sender":"Mom","text":"Beta aaj 7 baje aa jaana"},{"sender":"Mom","text":"Paneer tikka bana rahi hun"}]""")

                                mainHandler.postDelayed({
                                    // New user message at full opacity
                                    view.addItem(UserMessage("She just texted again, what should I say?"))

                                    mainHandler.postDelayed({
                                        // Show typing dots
                                        view.addItem(Loading.INSTANCE)
                                    }, 200)
                                }, 200)
                            }, 200)
                        }, 200)
                    }, 200)
                }, 400)
                Log.d(TAG, "[DEBUG] Showing AI Chat with session resumed (AC13)")
            }

            // === AI Chat Screenshot Analyzing (AC14) ===
            "${ACTION_PREFIX}AI_CHAT_SHOW_CAPTURE_ANALYZING" -> {
                showAiChat()
                mainHandler.postDelayed({
                    val view = getAiChatView() ?: return@postDelayed
                    view.clearSession()
                    view.setMode(UnifiedAiView.MODE_PERSONAL)
                    view.setContextInfo("WhatsApp", "Mom", "\uD83D\uDE0A Happy")

                    // User asks to analyze screen
                    view.addItem(UserMessage("Analyze what's on my screen right now"))

                    mainHandler.postDelayed({
                        // Show analyzing indicator
                        view.showAnalyzingMessage()
                    }, 200)
                }, 400)
                // Disable scan button during analysis
                mainHandler.postDelayed({
                    getAiChatView()?.setCaptureButtonEnabled(false)
                }, 700)
                Log.d(TAG, "[DEBUG] Showing AI Chat with capture analyzing (AC14)")
            }

            // === Analyzing Message helper ===
            "${ACTION_PREFIX}AI_CHAT_SHOW_ANALYZING" -> {
                getAiChatView()?.showAnalyzingMessage()
                Log.d(TAG, "[DEBUG] Added analyzing message to chat")
            }

            // === Screenshot Message (helper broadcast) ===
            "${ACTION_PREFIX}AI_CHAT_ADD_SCREENSHOT_MSG" -> {
                val view = getAiChatView() ?: return
                val imagePath = intent.getStringExtra("image_path") ?: ""
                val analysis = intent.getStringExtra("analysis") ?: ""
                val width = intent.getIntExtra("width", 0)
                val height = intent.getIntExtra("height", 0)
                view.addScreenshotMessage(imagePath, analysis, width, height)
                Log.d(TAG, "[DEBUG] Added screenshot message to chat")
            }

            // === NLS Banner (helper broadcast) ===
            "${ACTION_PREFIX}AI_CHAT_ADD_NLS_BANNER" -> {
                val view = getAiChatView() ?: return
                val contact = intent.getStringExtra("contact") ?: "Contact"
                val messagesJson = intent.getStringExtra("messages_json") ?: "[]"
                view.addNlsBanner(contact, messagesJson)
                Log.d(TAG, "[DEBUG] Added NLS banner to chat")
            }

            // === Session Banner (helper broadcast) ===
            "${ACTION_PREFIX}AI_CHAT_ADD_SESSION_BANNER" -> {
                val view = getAiChatView() ?: return
                val count = intent.getIntExtra("message_count", 0)
                view.addSessionBanner(count)
                Log.d(TAG, "[DEBUG] Added session banner to chat")
            }

            else -> Log.w(TAG, "[DEBUG] Unknown AI Chat action: $action")
        }
    }

    /**
     * Handle Keyboard V2 golden states (K01-K08).
     * These drive UnifiedAiView into specific UI states for screenshot capture.
     */
    private fun handleKeyboardV2Action(action: String, intent: Intent) {
        when (action) {
            "${ACTION_PREFIX}K_NEW_CHAT" -> {
                // K1: Strip header + input + QWERTY
                val view = showAiChat() ?: return
                view.clearSession()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    view.setUIState(UnifiedAiView.STATE_NEW_CHAT)
                    Log.d(TAG, "[K01] NEW_CHAT state set")
                }, 100)
            }

            "${ACTION_PREFIX}K_REPLY_MODE" -> {
                // K2: Strip header with purple dot + reply quote + QWERTY
                keyboardSwitcher.showReplyModeView()
                android.os.SystemClock.sleep(300)
                val view = getAiChatView() ?: return
                view.clearSession()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    view.setReplyText("\"need slides by EOD\"")
                    view.setUIState(UnifiedAiView.STATE_REPLY_MODE)
                    view.getChatInput()?.setText("On it — 6pm")
                    Log.d(TAG, "[K02] REPLY_MODE state set")
                }, 100)
            }

            "${ACTION_PREFIX}K_SESSIONS_EMPTY" -> {
                // K3: Chat header + dual CTA + empty state
                // Wipes Room sessions so the empty state view actually shows (not real data)
                val view = showAiChat() ?: return
                view.clearSession()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    view.setUIState(UnifiedAiView.STATE_SESSIONS_LIST)
                    view.clearAllSessionsAndShowEmpty()
                    Log.d(TAG, "[K03] SESSIONS_EMPTY state set")
                }, 100)
            }

            "${ACTION_PREFIX}K_SESSIONS_POPULATED" -> {
                // K4: Chat header + dual CTA + 3 demo sessions
                val view = showAiChat() ?: return
                view.clearSession()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    view.injectDemoSessions()
                    view.setUIState(UnifiedAiView.STATE_SESSIONS_LIST)
                    Log.d(TAG, "[K04] SESSIONS_POPULATED state set")
                }, 100)
            }

            "${ACTION_PREFIX}K_AI_VIEW_EMPTY" -> {
                // K5: Chat header + single user message
                val view = showAiChat() ?: return
                view.clearSession()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    view.setReplyText("Chat with Aanya")
                    view.setUIState(UnifiedAiView.STATE_AI_VIEW)
                    view.addItem(UserMessage("Draft a calm reply."))
                    Log.d(TAG, "[K05] AI_VIEW_EMPTY state set")
                }, 100)
            }

            "${ACTION_PREFIX}K_AI_VIEW_POPULATED" -> {
                // K6: System banner + mixed messages with tags
                val view = showAiChat() ?: return
                view.clearSession()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    view.setReplyText("Chat with Aanya")
                    view.setUIState(UnifiedAiView.STATE_AI_VIEW)
                    view.setDebugChatItems(
                        listOf<ChatItem>(
                            SessionBannerMessage(4),
                            AiMessage("· overlay\nShort reply: \"Slides by 6pm — on it.\"", CtaType.NONE),
                            UserMessage("Make it more urgent"),
                            AiMessage("✨ Smart reply\n\"Landing in your inbox by 5:30.\"", CtaType.NONE)
                        ),
                        "4",
                        false,
                        false
                    )
                    Log.d(TAG, "[K06] AI_VIEW_POPULATED state set")
                }, 100)
            }

            "${ACTION_PREFIX}K_AI_VIEW_LOADING" -> {
                // K7: User message + typing indicator + disabled input
                val view = showAiChat() ?: return
                view.clearSession()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    view.setReplyText("Chat with Aanya")
                    view.setUIState(UnifiedAiView.STATE_AI_VIEW)
                    view.setDebugChatItems(
                        listOf<ChatItem>(
                            UserMessage("Rewrite this in Hinglish"),
                            Loading.INSTANCE
                        ),
                        null,
                        true,
                        false
                    )
                    Log.d(TAG, "[K07] AI_VIEW_LOADING state set")
                }, 100)
            }

            "${ACTION_PREFIX}K_AI_VIEW_SCREENSHOT" -> {
                // K8: Screenshot thumb + analyzing + disabled input
                val view = showAiChat() ?: return
                view.clearSession()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    view.setReplyText("Chat with Aanya")
                    view.setUIState(UnifiedAiView.STATE_AI_VIEW)
                    view.setDebugChatItems(
                        listOf<ChatItem>(
                            ScreenshotMessage("", "", 0, 0),
                            AnalyzingMessage()
                        ),
                        null,
                        true,
                        true
                    )
                    Log.d(TAG, "[K08] AI_VIEW_SCREENSHOT state set")
                }, 100)
            }

            else -> Log.w(TAG, "[DEBUG] Unknown keyboard V2 action: $action")
        }
    }

    /**
     * Handle Overlay V2 golden states (O01-O07).
     * Sends a secondary broadcast to the overlay service to inject UI state.
     */
    private fun handleOverlayV2Action(action: String, intent: Intent) {
        val context = keyboardSwitcher.getmLatinIME() ?: return

        when (action) {
            "${ACTION_PREFIX}O_BUBBLE_IDLE" -> {
                Log.d(TAG, "[O01] BUBBLE_IDLE — overlay service should be running")
            }

            "${ACTION_PREFIX}O_POPUP_EMPTY" -> {
                val overlayIntent = Intent("project.witty.keys.overlay.DEBUG_STATE")
                    .putExtra("state", "popup_history_empty")
                    .setPackage(context.packageName)
                context.sendBroadcast(overlayIntent)
                Log.d(TAG, "[O02] POPUP_HISTORY_EMPTY")
            }

            "${ACTION_PREFIX}O_POPUP_POPULATED" -> {
                val overlayIntent = Intent("project.witty.keys.overlay.DEBUG_STATE")
                    .putExtra("state", "popup_history_populated")
                    .setPackage(context.packageName)
                context.sendBroadcast(overlayIntent)
                Log.d(TAG, "[O03] POPUP_HISTORY_POPULATED")
            }

            "${ACTION_PREFIX}O_POPUP_LOADING" -> {
                val overlayIntent = Intent("project.witty.keys.overlay.DEBUG_STATE")
                    .putExtra("state", "popup_loading")
                    .setPackage(context.packageName)
                context.sendBroadcast(overlayIntent)
                Log.d(TAG, "[O04] POPUP_LOADING")
            }

            "${ACTION_PREFIX}O_POPUP_CHAT_EMPTY" -> {
                val overlayIntent = Intent("project.witty.keys.overlay.DEBUG_STATE")
                    .putExtra("state", "popup_chat_empty")
                    .setPackage(context.packageName)
                context.sendBroadcast(overlayIntent)
                Log.d(TAG, "[O05] POPUP_CHAT_EMPTY")
            }

            "${ACTION_PREFIX}O_POPUP_CHAT_LOADING" -> {
                val overlayIntent = Intent("project.witty.keys.overlay.DEBUG_STATE")
                    .putExtra("state", "popup_chat_loading")
                    .setPackage(context.packageName)
                context.sendBroadcast(overlayIntent)
                Log.d(TAG, "[O06] POPUP_CHAT_LOADING")
            }

            "${ACTION_PREFIX}O_POPUP_CHAT_POPULATED" -> {
                val overlayIntent = Intent("project.witty.keys.overlay.DEBUG_STATE")
                    .putExtra("state", "popup_chat_populated")
                    .setPackage(context.packageName)
                context.sendBroadcast(overlayIntent)
                Log.d(TAG, "[O07] POPUP_CHAT_POPULATED")
            }

            else -> Log.w(TAG, "[DEBUG] Unknown overlay V2 action: $action")
        }
    }

    /**
     * Handle Fullscreen V2 golden states (F01-F06).
     * Launches AiChatActivity with a debug_state extra for UI state injection.
     */
    private fun handleFullscreenV2Action(action: String, intent: Intent) {
        val context = keyboardSwitcher.getmLatinIME() ?: return

        when (action) {
            "${ACTION_PREFIX}FV2_INITIAL_LOAD" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fv2_initial_load")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[F01] INITIAL_LOAD")
            }

            "${ACTION_PREFIX}FV2_SESSIONS_EMPTY" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fv2_sessions_empty")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[F02] SESSIONS_EMPTY")
            }

            "${ACTION_PREFIX}FV2_SESSIONS_POPULATED" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fv2_sessions_populated")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[F03] SESSIONS_POPULATED")
            }

            "${ACTION_PREFIX}FV2_CHAT_EMPTY" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fv2_chat_empty")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[F04] CHAT_EMPTY")
            }

            "${ACTION_PREFIX}FV2_CHAT_POPULATED" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fv2_chat_populated")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[F05] CHAT_POPULATED")
            }

            "${ACTION_PREFIX}FV2_CHAT_ERROR" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fv2_chat_error")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[F06] CHAT_ERROR")
            }

            else -> Log.w(TAG, "[DEBUG] Unknown fullscreen V2 action: $action")
        }
    }

    /**
     * Handle Full-Screen AiChatActivity actions — launch Activity with debug state extras.
     * These broadcasts are for FS01-FS10 golden pipeline states.
     */
    private fun handleFullScreenAction(action: String, intent: Intent) {
        val context = keyboardSwitcher.getmLatinIME() ?: return

        when (action) {
            // === Full-Screen Empty Session (FS01) ===
            "${ACTION_PREFIX}FS_SHOW_EMPTY" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_empty")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — empty session (FS01)")
            }

            // === Full-Screen Active Chat (FS02) ===
            "${ACTION_PREFIX}FS_SHOW_CHAT" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_chat")
                    .putExtra("contact_name", "Mom")
                    .putExtra("app_name", "WhatsApp")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — active chat (FS02)")
            }

            // === Full-Screen Loading (FS03) ===
            "${ACTION_PREFIX}FS_SHOW_LOADING" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_loading")
                    .putExtra("contact_name", "Mom")
                    .putExtra("app_name", "WhatsApp")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — loading (FS03)")
            }

            // === Full-Screen Error (FS04) ===
            "${ACTION_PREFIX}FS_SHOW_ERROR" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_error")
                    .putExtra("contact_name", "Mom")
                    .putExtra("app_name", "WhatsApp")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — error (FS04)")
            }

            // === Full-Screen Screenshot Inline (FS05) ===
            "${ACTION_PREFIX}FS_SHOW_SCREENSHOT" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_screenshot")
                    .putExtra("contact_name", "Priya")
                    .putExtra("app_name", "WhatsApp")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — screenshot inline (FS05)")
            }

            // === Full-Screen NLS Context (FS06) ===
            "${ACTION_PREFIX}FS_SHOW_NLS_CONTEXT" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_nls_context")
                    .putExtra("contact_name", "Mom")
                    .putExtra("app_name", "WhatsApp")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — NLS context (FS06)")
            }

            // === Full-Screen Session List (FS07) ===
            "${ACTION_PREFIX}FS_SHOW_SESSION_LIST" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_session_list")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — session list (FS07)")
            }

            // === Full-Screen Session Resumed (FS08) ===
            "${ACTION_PREFIX}FS_SHOW_SESSION_RESUMED" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_session_resumed")
                    .putExtra("contact_name", "Arjun")
                    .putExtra("app_name", "WhatsApp")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — session resumed (FS08)")
            }

            // === Full-Screen Long Conversation (FS09) ===
            "${ACTION_PREFIX}FS_SHOW_LONG_CHAT" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_long_chat")
                    .putExtra("contact_name", "Priya")
                    .putExtra("app_name", "WhatsApp")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — long conversation (FS09)")
            }

            // === Full-Screen Screenshot Analyzing / Handoff (FS10) ===
            "${ACTION_PREFIX}FS_SHOW_CAPTURE_ANALYZING" -> {
                val fsIntent = Intent(context, project.witty.keys.keyboard.AiChat.AiChatActivity::class.java)
                    .putExtra("debug_state", "fs_capture_analyzing")
                    .putExtra("contact_name", "Priya")
                    .putExtra("app_name", "WhatsApp")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fsIntent)
                Log.d(TAG, "[DEBUG] Launching AiChatActivity — capture analyzing handoff (FS10)")
            }

            else -> Log.w(TAG, "[DEBUG] Unknown full-screen action: $action")
        }
    }

    private fun getEmojiKeyboard(): Any? {
        return try {
            val field = keyboardSwitcher.javaClass.getDeclaredField("emojiKeyboard")
            field.isAccessible = true
            field.get(keyboardSwitcher)
        } catch (e: Exception) {
            Log.e(TAG, "[DEBUG] Failed to access emoji keyboard: ${e.message}")
            null
        }
    }

    private fun getAiChatView(): UnifiedAiView? {
        return try {
            val field = keyboardSwitcher.javaClass.getDeclaredField("mUnifiedAiView")
            field.isAccessible = true
            val view = field.get(keyboardSwitcher) as? UnifiedAiView
            if (view == null) {
                Log.e(TAG, "[DEBUG] getAiChatView: mUnifiedAiView field is null")
            } else {
                Log.d(TAG, "[DEBUG] getAiChatView: found view, visibility=${view.visibility}, shown=${view.isShown}")
            }
            view
        } catch (e: Exception) {
            Log.e(TAG, "[DEBUG] getAiChatView: reflection failed: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    private fun showAiChat(): UnifiedAiView? {
        val view = getAiChatView()
        if (view == null) {
            Log.e(TAG, "[DEBUG] showAiChat: mUnifiedAiView is null")
            return null
        }

        // Use the new public method — no reflection needed
        keyboardSwitcher.showAiChatView()
        Log.d(TAG, "[DEBUG] showAiChat: called showAiChatView()")

        // Wait briefly for layout to complete
        android.os.SystemClock.sleep(300)

        // Set UI state to AI view
        view.setUIState(UnifiedAiView.STATE_AI_VIEW)
        Log.d(TAG, "[DEBUG] showAiChat: complete — visibility=${view.visibility}, shown=${view.isShown}")
        return view
    }

    private fun parseJsonArray(json: String): List<String> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "[DEBUG] Failed to parse JSON array: $json", e)
            listOf("Reply 1", "Reply 2", "Reply 3", "Reply 4")
        }
    }

    private fun getScenarioData(scenario: String): TestDataFactory.ScenarioData {
        return when (scenario.uppercase()) {
            "FRUSTRATED_BOSS" -> TestDataFactory.frustratedBoss()
            "WORRIED_FRIEND_HINDI" -> TestDataFactory.worriedFriendHindi()
            "CASUAL_FRIEND" -> TestDataFactory.casualFriend()
            "FORMAL_EMAIL" -> TestDataFactory.formalEmail()
            "ANGRY_CUSTOMER" -> TestDataFactory.angryCustomer()
            "EXCITED_FRIEND" -> TestDataFactory.excitedFriend()
            "HINGLISH" -> TestDataFactory.hinglish()
            "LONG_TEXT" -> TestDataFactory.longTextContent()
            "RTL_CONTENT" -> TestDataFactory.rtlContent()
            "MAX_REPLIES" -> TestDataFactory.maxQuickReplies()
            "NO_REPLIES" -> TestDataFactory.noReplies()
            "LOADING" -> TestDataFactory.loadingState()
            "ERROR" -> TestDataFactory.errorState()
            "EMOTION_HAPPY" -> TestDataFactory.casualFriend()
            "EMOTION_SAD" -> createEmotionScenario("SAD")
            "EMOTION_ANGRY" -> TestDataFactory.angryCustomer()
            "EMOTION_WORRIED" -> TestDataFactory.worriedFriendHindi()
            "EMOTION_EXCITED" -> TestDataFactory.excitedFriend()
            "EMOTION_FRUSTRATED" -> TestDataFactory.frustratedBoss()
            "EMOTION_NEUTRAL" -> TestDataFactory.formalEmail()
            "EMOTION_CONFUSED" -> createEmotionScenario("CONFUSED")
            "EMOTION_LOVING" -> createEmotionScenario("LOVING")
            "DATING" -> TestDataFactory.datingContext()
            else -> TestDataFactory.frustratedBoss()
        }
    }

    private fun createEmotionScenario(emotion: String): TestDataFactory.ScenarioData {
        return TestDataFactory.ScenarioData(
            appName = "WhatsApp",
            appPackage = "com.whatsapp",
            senderName = "Test Sender",
            lastMessage = "Test message for $emotion emotion",
            summary = "Testing $emotion emotion display",
            detectedLanguage = "en",
            quickReplies = listOf(
                "Reply option 1",
                "Reply option 2",
                "Reply option 3",
                "Reply option 4"
            )
        )
    }
}
