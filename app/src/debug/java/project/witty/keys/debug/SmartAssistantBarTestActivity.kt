package project.witty.keys.debug

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import android.app.Activity
import project.witty.keys.R
import project.witty.keys.keyboard.AssistantViews.SmartAssistantBar
import project.witty.keys.keyboard.AssistantViews.SmartAssistantBar.BarState
import project.witty.keys.keyboard.AssistantViews.SmartAssistantBar.Row2State
import project.witty.keys.keyboard.KeyboardTheme

/**
 * SmartAssistantBarTestActivity — Debug-only test harness
 *
 * Hosts a real SmartAssistantBar in isolation (outside the IME service)
 * so we can drive it with mock data, capture screenshots, and run
 * automated visual QA + journey tests.
 *
 * ## Launch via ADB:
 * ```
 * adb shell am start -n project.witty.keys/.debug.SmartAssistantBarTestActivity \
 *     --es bar_state "EXPANDED" \
 *     --es row2_state "MEMORY_VIEW" \
 *     --ei theme_id 7 \
 *     --es scenario "FRUSTRATED_BOSS"
 * ```
 *
 * ## Intent Extras:
 * - EXTRA_BAR_STATE: "EXPANDED" or "COLLAPSED"
 * - EXTRA_ROW2_STATE: Any Row2State enum name
 * - EXTRA_THEME_ID: Theme ID (1-15), default 7 (Dracula)
 * - EXTRA_SCENARIO: Scenario name from MockChatScenarios
 * - EXTRA_MEMORY_DATA_JSON: Raw JSON string (legacy, ignored)
 *
 * ## Programmatic API (for instrumentation tests):
 * - setBarState(BarState)
 * - setRow2State(Row2State)
 * - showSmartReplies(replies)
 * - showMilestoneToast(emoji, title, subtitle)
 * - applyTheme(themeId)
 * - getCurrentBarState(): BarState
 * - getCurrentRow2State(): Row2State
 *
 * WittyKeys Automated Visual QA & Log Analysis Testing System
 * Created: February 9, 2026
 */
class SmartAssistantBarTestActivity : Activity() {

    companion object {
        private const val TAG = "SABTestActivity"

        // Intent extra keys
        const val EXTRA_BAR_STATE = "bar_state"
        const val EXTRA_ROW2_STATE = "row2_state"
        const val EXTRA_THEME_ID = "theme_id"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_MEMORY_DATA_JSON = "memory_data_json"

        // Default values
        const val DEFAULT_THEME_ID = KeyboardTheme.THEME_ID_SYSTEM

        /**
         * Create a launch Intent with optional configuration.
         */
        fun createIntent(
            context: Context,
            barState: BarState = BarState.EXPANDED,
            row2State: Row2State? = null,
            themeId: Int = DEFAULT_THEME_ID,
            scenario: String? = null,
            memoryDataJson: String? = null
        ): Intent {
            return Intent(context, SmartAssistantBarTestActivity::class.java).apply {
                putExtra(EXTRA_BAR_STATE, barState.name)
                row2State?.let { putExtra(EXTRA_ROW2_STATE, it.name) }
                putExtra(EXTRA_THEME_ID, themeId)
                scenario?.let { putExtra(EXTRA_SCENARIO, it) }
                memoryDataJson?.let { putExtra(EXTRA_MEMORY_DATA_JSON, it) }
            }
        }
    }

    // --- Public references for instrumentation tests ---
    lateinit var smartAssistantBar: SmartAssistantBar
        private set

    private lateinit var testTitle: TextView
    private lateinit var testStateLabel: TextView
    private lateinit var testThemeLabel: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var currentThemeId: Int = DEFAULT_THEME_ID

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme BEFORE setContentView
        currentThemeId = intent.getIntExtra(EXTRA_THEME_ID, DEFAULT_THEME_ID)
        applyThemeStyle(currentThemeId)

        super.onCreate(savedInstanceState)

        // Keep screen on and turn it on if off (fixes black screenshots on remote/foldable devices)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_smartassistantbar_test)

        Log.d(TAG, "[TEST] SmartAssistantBarTestActivity created, theme=$currentThemeId")

        // Bind views
        smartAssistantBar = findViewById(R.id.smart_assistant_bar)
        testTitle = findViewById(R.id.test_title)
        testStateLabel = findViewById(R.id.test_state_label)
        testThemeLabel = findViewById(R.id.test_theme_label)

        // Process Intent extras
        processIntentExtras()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntentExtras()
    }

    // =========================================================================
    // Intent Processing
    // =========================================================================

    private fun processIntentExtras() {
        val intent = intent ?: return

        // 1. Apply bar state
        val barStateName = intent.getStringExtra(EXTRA_BAR_STATE)
        if (barStateName != null) {
            try {
                val state = BarState.valueOf(barStateName)
                setBarState(state)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "[TEST] Unknown BarState: $barStateName")
            }
        }

        // 2. Apply scenario (provides mock reply data)
        val scenarioName = intent.getStringExtra(EXTRA_SCENARIO)
        if (scenarioName != null) {
            applyScenario(scenarioName)
            return // Scenario sets everything, skip individual row2/data
        }

        // 3. Legacy memory data JSON — ignored (MemoryViewData removed in Build 7.1)
        // val memoryJson = intent.getStringExtra(EXTRA_MEMORY_DATA_JSON)

        // 4. Apply row2 state (without data)
        val row2StateName = intent.getStringExtra(EXTRA_ROW2_STATE)
        if (row2StateName != null) {
            try {
                val row2 = Row2State.valueOf(row2StateName)
                setRow2State(row2)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "[TEST] Unknown Row2State: $row2StateName")
            }
        }

        updateStatusLabels()
    }

    private fun applyScenario(scenarioName: String) {
        Log.d(TAG, "[TEST] Applying scenario: $scenarioName")
        val data = TestDataFactory.getScenarioData(scenarioName)
        if (data != null) {
            if (data.quickReplies.isNotEmpty()) {
                showSmartReplies(data.quickReplies)
            }
        } else {
            Log.e(TAG, "[TEST] Unknown scenario: $scenarioName")
            testStateLabel.text = "ERROR: Unknown scenario '$scenarioName'"
        }
    }

    // =========================================================================
    // Public API — Called by instrumentation tests
    // =========================================================================

    /**
     * Set the top-level bar state (EXPANDED or COLLAPSED).
     */
    fun setBarState(state: BarState) {
        Log.d(TAG, "[TEST] setBarState → $state")
        runOnUiThread {
            smartAssistantBar.setState(state)
            updateStatusLabels()
        }
    }

    /**
     * Set the Row2 substate within EXPANDED mode.
     */
    fun setRow2State(state: Row2State) {
        Log.d(TAG, "[TEST] setRow2State → $state")
        runOnUiThread {
            smartAssistantBar.showRow2State(state)
            updateStatusLabels()
        }
    }

    /**
     * Show smart replies with given text.
     */
    fun showSmartReplies(replies: List<String>) {
        Log.d(TAG, "[TEST] showSmartReplies: ${replies.size} replies")
        runOnUiThread {
            smartAssistantBar.showSmartReplies(replies)
            updateStatusLabels()
        }
    }

    /**
     * Trigger the milestone toast (auto-hides after 2.5s).
     */
    fun showMilestoneToast(emoji: String, title: String, subtitle: String) {
        Log.d(TAG, "[TEST] showMilestoneToast: $emoji $title")
        runOnUiThread {
            smartAssistantBar.showMilestoneToast(emoji, title, subtitle)
            updateStatusLabels()
        }
    }

    /**
     * Simulate receiving a new message with reply suggestions.
     */
    fun simulateNewMessage(senderName: String, replies: List<String>) {
        Log.d(TAG, "[TEST] simulateNewMessage: sender=$senderName, replies=${replies.size}")
        runOnUiThread {
            smartAssistantBar.onNewMessageReceived(senderName, replies)
            updateStatusLabels()
        }
    }

    /**
     * Simulate user typing (triggers COLLAPSED state).
     */
    fun simulateUserInput(text: String) {
        Log.d(TAG, "[TEST] simulateUserInput: '${text.take(20)}...'")
        runOnUiThread {
            smartAssistantBar.onUserInput(text)
            updateStatusLabels()
        }
    }

    /**
     * Setup tone picker chips.
     * NOTE: Method disabled - setupTonePickerChips is private in SmartAssistantBar
     */
    fun setupTonePicker() {
        Log.d(TAG, "[TEST] setupTonePicker - using showRow2State instead")
        runOnUiThread {
            smartAssistantBar.showRow2State(SmartAssistantBar.Row2State.TONE_PICKER)
            updateStatusLabels()
        }
    }

    /**
     * Setup language picker chips.
     * NOTE: Method disabled - setupLangPickerChips is private in SmartAssistantBar
     */
    fun setupLangPicker() {
        Log.d(TAG, "[TEST] setupLangPicker - using showRow2State instead")
        runOnUiThread {
            smartAssistantBar.showRow2State(SmartAssistantBar.Row2State.LANG_PICKER)
            updateStatusLabels()
        }
    }

    /**
     * Enter custom mode.
     */
    fun enterCustomMode() {
        Log.d(TAG, "[TEST] enterCustomMode")
        runOnUiThread {
            smartAssistantBar.enterCustomMode()
            updateStatusLabels()
        }
    }

    /**
     * Show stat cards.
     */
    fun showStatCards() {
        Log.d(TAG, "[TEST] showStatCards")
        runOnUiThread {
            smartAssistantBar.showStatCards()
            updateStatusLabels()
        }
    }

    /**
     * Start brain blink animation.
     */
    fun startBrainBlink() {
        Log.d(TAG, "[TEST] startBrainBlink")
        runOnUiThread {
            smartAssistantBar.startBrainBlinkAnimation()
            updateStatusLabels()
        }
    }

    /**
     * Show loading shimmer state.
     */
    fun showLoadingShimmer() {
        Log.d(TAG, "[TEST] showLoadingShimmer")
        runOnUiThread {
            smartAssistantBar.showRow2State(Row2State.SHIMMER_LOADING)
            updateStatusLabels()
        }
    }

    /**
     * Show error state.
     */
    fun showError(message: String = "Something went wrong. Please try again.") {
        Log.d(TAG, "[TEST] showError: $message")
        runOnUiThread {
            smartAssistantBar.showRow2State(Row2State.SHIMMER_LOADING)
            // Error state is now handled via OV_ERROR SabState
            updateStatusLabels()
        }
    }

    /**
     * Apply a different keyboard theme.
     * Note: This recreates the Activity to apply the new theme style.
     */
    fun applyTheme(themeId: Int) {
        if (themeId == currentThemeId) return
        Log.d(TAG, "[TEST] applyTheme: $currentThemeId → $themeId")
        currentThemeId = themeId

        // Re-launch with the new theme
        val newIntent = Intent(intent).apply {
            putExtra(EXTRA_THEME_ID, themeId)
        }
        finish()
        startActivity(newIntent)
    }

    /**
     * Get current bar state.
     */
    fun getCurrentBarState(): BarState {
        return try {
            val field = SmartAssistantBar::class.java.getDeclaredField("currentState")
            field.isAccessible = true
            field.get(smartAssistantBar) as BarState
        } catch (e: Exception) {
            Log.w(TAG, "[TEST] Could not read currentState via reflection", e)
            BarState.EXPANDED
        }
    }

    /**
     * Get current Row2 state.
     */
    fun getCurrentRow2State(): Row2State? {
        return try {
            smartAssistantBar.currentRow2State
        } catch (e: Exception) {
            Log.w(TAG, "[TEST] Could not read currentRow2State", e)
            null
        }
    }

    // =========================================================================
    // Theme Application
    // =========================================================================

    private fun applyThemeStyle(themeId: Int) {
        val styleRes = ThemePresets.getStyleResForThemeId(themeId)
        if (styleRes != 0) {
            setTheme(styleRes)
            Log.d(TAG, "[TEST] Applied theme style: themeId=$themeId")
        } else {
            // Fallback to System
            setTheme(R.style.KeyboardTheme_LXX_System)
            Log.w(TAG, "[TEST] Unknown themeId=$themeId, falling back to System")
        }
    }

    // =========================================================================
    // Status Display
    // =========================================================================

    private fun updateStatusLabels() {
        handler.post {
            val barState = getCurrentBarState()
            val row2State = getCurrentRow2State()
            testStateLabel.text = "State: $barState | Row2: ${row2State ?: "N/A"}"
            testThemeLabel.text = "Theme: ${ThemePresets.getThemeName(currentThemeId)} ($currentThemeId)"
        }
    }
}
