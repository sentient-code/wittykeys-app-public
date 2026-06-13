package project.witty.keys.e2e

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import project.witty.keys.app.tutorial.InteractiveTutorialActivity
import project.witty.keys.keyboard.AssistantViews.SabState
import java.io.File

/**
 * Abstract base class for all E2E keyboard tests.
 * Provides shared helpers for UI Automator (keyboard/SAB interaction),
 * Espresso (host app verification), and ADB broadcast (state control).
 *
 * Updated for Sprint 1: Uses InteractiveTutorialActivity as host with test mode intent.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
abstract class BaseKeyboardE2ETest {

    @get:Rule
    val activityRule = ActivityScenarioRule<InteractiveTutorialActivity>(
        Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            InteractiveTutorialActivity::class.java
        )
    )

    protected lateinit var device: UiDevice
    protected lateinit var context: Context

    companion object {
        private const val TAG = "BaseKeyboardE2ETest"
        const val KEYBOARD_PKG = "project.witty.keys"
        private const val ACTION_PREFIX = "project.witty.keys.debug."
        const val STATE_SETTLE_MS = 800L
        const val SAB_TIMEOUT = 5000L
        const val API_TIMEOUT = 10000L
        const val ANIMATION_WAIT = 500L
        const val TRANSITION_WAIT = 300L

        const val IME_ID = "$KEYBOARD_PKG/.latin.LatinIME"

        // Keyboard roots. These prove the IME is actually visible, unlike a package-only wait.
        const val RES_SAB_ROOT = "$KEYBOARD_PKG:id/smart_assistant_bar_root"
        const val RES_KEYBOARD_VIEW = "$KEYBOARD_PKG:id/keyboard_view"

        // SAB element resource IDs — UPDATED to wk_* convention
        const val RES_BRAIN_BUTTON = "$KEYBOARD_PKG:id/wk_ov_brain"
        const val RES_TONE_BUTTON = "$KEYBOARD_PKG:id/wk_ov_tone_btn"
        const val RES_GRAMMAR_BUTTON = "$KEYBOARD_PKG:id/wk_ov_grammar_btn"
        const val RES_TRANSLATE_BUTTON = "$KEYBOARD_PKG:id/wk_ov_translate_btn"
        const val RES_COLLAPSE_BUTTON = "$KEYBOARD_PKG:id/wk_ov_collapse_btn"
        const val RES_EXPAND_BUTTON = "$KEYBOARD_PKG:id/wk_ov_expand_btn"
        const val RES_REPLY_SCROLL = "$KEYBOARD_PKG:id/wk_ov_row2_chips"
        const val RES_TONE_PICKER = "$KEYBOARD_PKG:id/wk_ov_row2_tone_picker"
        const val RES_LANG_PICKER = "$KEYBOARD_PKG:id/wk_ov_row2_lang_picker"
        const val RES_CUSTOM_MODE = "$KEYBOARD_PKG:id/wk_ov_row2_custom"
        const val RES_SHIMMER = "$KEYBOARD_PKG:id/wk_ov_row2_shimmer"

        // Memory View IDs
        const val RES_MV_CONTAINER = "$KEYBOARD_PKG:id/wk_memory_view"
        const val RES_MV_REPLIES = "$KEYBOARD_PKG:id/wk_mv_replies_container"
        const val RES_MV_LOADING = "$KEYBOARD_PKG:id/wk_mv_loading"
        const val RES_MV_ERROR = "$KEYBOARD_PKG:id/wk_mv_error"

        // Original View IDs
        const val RES_OV_CONTAINER = "$KEYBOARD_PKG:id/wk_original_view"
        const val RES_OV_CUSTOM_INPUT = "$KEYBOARD_PKG:id/wk_ov_custom_msg"
        const val RES_OV_CUSTOM_GENERATE = "$KEYBOARD_PKG:id/wk_ov_custom_generate"
        const val RES_OV_CUSTOM_CANCEL = "$KEYBOARD_PKG:id/wk_ov_custom_cancel"
    }

    @Before
    fun baseSetup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = InstrumentationRegistry.getInstrumentation().targetContext
        Log.d(TAG, "baseSetup: device and context initialized")
        ensureWittyKeysImeSelected()
        prepareKeyboardHostForTest()
        focusHostEditText()
        waitForKeyboard()
        SystemClock.sleep(800) // Wait for SAB to fully initialize before broadcasts
        Log.d(TAG, "baseSetup: keyboard visible, SAB settled")
    }

    // --- UI Automator Helpers ---

    protected fun ensureWittyKeysImeSelected() {
        device.executeShellCommand("ime enable $IME_ID")
        device.executeShellCommand("ime set $IME_ID")
        SystemClock.sleep(500)
    }

    protected fun focusHostEditText() {
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3000)?.click()
        SystemClock.sleep(500)
    }

    protected fun prepareKeyboardHostForTest() {
        activityRule.scenario.onActivity { activity ->
            activity.prepareKeyboardHostForTest()
        }
        SystemClock.sleep(500)
    }

    protected fun waitForKeyboard(): Boolean {
        if (waitForKeyboardRoots()) return true
        focusHostEditText()
        return waitForKeyboardRoots()
    }

    private fun waitForKeyboardRoots(): Boolean {
        val foundSab = device.wait(Until.hasObject(By.res(RES_SAB_ROOT)), SAB_TIMEOUT)
        if (foundSab) {
            Log.d(TAG, "waitForKeyboard: found SAB root")
            return true
        }
        val foundKeyboard = device.wait(Until.hasObject(By.res(RES_KEYBOARD_VIEW)), SAB_TIMEOUT)
        Log.d(TAG, "waitForKeyboard: found keyboard view=$foundKeyboard")
        return foundKeyboard
    }

    protected fun findSABElement(resourceId: String): UiObject2? {
        val element = device.findObject(By.res(resourceId))
        Log.d(TAG, "findSABElement: $resourceId -> ${if (element != null) "found" else "null"}")
        return element
    }

    protected fun tapSABButton(resourceId: String) {
        Log.d(TAG, "tapSABButton: waiting for $resourceId")
        val button = device.wait(Until.findObject(By.res(resourceId)), SAB_TIMEOUT)
        assertNotNull("SAB button $resourceId not found", button)
        button?.click()
        Log.d(TAG, "tapSABButton: clicked $resourceId")
        Thread.sleep(ANIMATION_WAIT)
    }

    protected fun getReplyChipTexts(): List<String> {
        val scrollView = findSABElement(RES_REPLY_SCROLL) ?: return emptyList()
        val chips = scrollView.findObjects(By.clazz("android.widget.TextView"))
        val texts = chips.map { it.text ?: "" }.filter { it.isNotBlank() }
        Log.d(TAG, "getReplyChipTexts: found ${texts.size} chips")
        return texts
    }

    protected fun tapFirstReplyChip() {
        val scrollView = device.wait(Until.findObject(By.res(RES_REPLY_SCROLL)), SAB_TIMEOUT)
        val chips = scrollView?.findObjects(By.clazz("android.widget.TextView"))
        val firstChip = chips?.firstOrNull { it.text?.isNotBlank() == true }
        assertNotNull("No reply chips found", firstChip)
        firstChip?.click()
        Log.d(TAG, "tapFirstReplyChip: clicked '${firstChip?.text}'")
        Thread.sleep(ANIMATION_WAIT)
    }

    // --- ADB Broadcast Helpers ---

    protected fun sendDebugBroadcast(action: String, extras: Map<String, String> = emptyMap()) {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}$action").apply {
            setPackage(ctx.packageName)
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        Log.d(TAG, "sendDebugBroadcast: action=${ACTION_PREFIX}$action extras=$extras")
        ctx.sendBroadcast(intent)
        SystemClock.sleep(TRANSITION_WAIT)
    }

    protected fun forceState(state: String) {
        Log.d(TAG, "forceState (broadcast): $state")
        sendDebugBroadcast("SET_STATE", mapOf("state" to state))
    }

    protected fun forceRow2State(state: String) {
        Log.d(TAG, "forceRow2State: $state")
        sendDebugBroadcast("SET_ROW2_STATE", mapOf("row2_state" to state))
    }

    // --- Espresso Helpers (via Activity Test API) ---

    protected fun getEditTextContent(): String {
        var text = ""
        activityRule.scenario.onActivity { activity ->
            text = activity.getMessageText()
            Log.d(TAG, "getEditTextContent: '$text'")
        }
        return text
    }

    protected fun verifyEditTextContains(expected: String) {
        var actual = ""
        activityRule.scenario.onActivity { activity ->
            actual = activity.getMessageText()
        }
        Log.d(TAG, "verifyEditTextContains: expected='$expected', actual='$actual'")
        assertTrue("EditText should contain '$expected' but was '$actual'",
            actual.contains(expected))
    }

    // --- Screenshot Helpers ---

    protected fun takeScreenshot(name: String): File {
        val dir = File(context.getExternalFilesDir(null), "e2e_screenshots")
        dir.mkdirs()
        val file = File(dir, "${name}_${System.currentTimeMillis()}.png")
        device.takeScreenshot(file)
        Log.d(TAG, "takeScreenshot: saved to ${file.absolutePath}")
        return file
    }

    // --- Activity Test API Helpers ---

    /**
     * Run a block on the activity instance (for test API calls).
     */
    protected fun withActivity(block: (InteractiveTutorialActivity) -> Unit) {
        activityRule.scenario.onActivity { activity ->
            Log.d(TAG, "withActivity: executing block")
            block(activity)
        }
    }

    /**
     * Force SAB to a specific state via broadcast.
     */
    protected fun forceSabState(state: SabState) {
        Log.d(TAG, "forceSabState: $state")
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}SET_STATE").apply {
            setPackage(ctx.packageName)
            putExtra("state", state.name)
        }
        ctx.sendBroadcast(intent)
        SystemClock.sleep(STATE_SETTLE_MS)
    }

    /**
     * Load a specific scenario via broadcast.
     */
    protected fun loadTestScenario(scenarioName: String) {
        Log.d(TAG, "loadTestScenario: $scenarioName")
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent("${ACTION_PREFIX}INJECT_SCENARIO").apply {
            setPackage(ctx.packageName)
            putExtra("scenario", scenarioName)
        }
        ctx.sendBroadcast(intent)
        SystemClock.sleep(STATE_SETTLE_MS)
    }

    // ==================== Lifecycle Logging ====================

    private var currentTestName: String = ""
    private var currentStepTotal: Int = 0

    protected fun setTestContext(testName: String, totalSteps: Int) {
        currentTestName = testName
        currentStepTotal = totalSteps
        Log.i("WK_E2E", "[$currentTestName] [START] Beginning test with $totalSteps steps")
    }

    protected fun logStep(step: Int, phase: String, message: String) {
        Log.i("WK_E2E", "[$currentTestName] [STEP $step/$currentStepTotal] [$phase] $message")
    }

    protected fun logResult(passed: Boolean, summary: String) {
        val icon = if (passed) "✓ PASS" else "✗ FAIL"
        Log.i("WK_E2E", "[$currentTestName] [RESULT] $icon — $summary")
    }

    protected fun logStateDump(step: Int) {
        try {
            val state = queryState()
            Log.i("WK_E2E", "[$currentTestName] [STEP $step/$currentStepTotal] [STATE_DUMP] ${state.toString()}")
        } catch (e: Exception) {
            Log.e("WK_E2E", "[$currentTestName] [STEP $step/$currentStepTotal] [STATE_DUMP] Failed: ${e.message}")
        }
    }

    // ==================== State Query (Broadcast → JSON File) ====================

    /**
     * Send QUERY_STATE broadcast and read the resulting JSON state file.
     * The app writes state to its external files dir via getExternalFilesDir(null).
     * We read it via: run-as project.witty.keys (internal) or direct path (external).
     */
    protected fun queryState(): JSONObject {
        sendDebugBroadcast("QUERY_STATE")
        SystemClock.sleep(500) // Let broadcast handler write file

        // Try external files dir first (getExternalFilesDir path)
        val externalPath = "/storage/emulated/0/Android/data/$KEYBOARD_PKG/files/wk_test_state.json"
        var json = device.executeShellCommand("cat $externalPath 2>/dev/null").trim()

        // Fallback: try run-as for internal files
        if (json.isEmpty() || json.startsWith("cat:")) {
            json = device.executeShellCommand(
                "run-as $KEYBOARD_PKG cat /data/data/$KEYBOARD_PKG/files/wk_test_state.json 2>/dev/null"
            ).trim()
        }

        // Fallback: legacy path
        if (json.isEmpty() || json.startsWith("cat:") || json.startsWith("run-as:")) {
            json = device.executeShellCommand("cat /data/local/tmp/wk_test_state.json 2>/dev/null").trim()
        }

        Log.d(TAG, "queryState: raw json (${json.length} chars): ${json.take(200)}")
        return JSONObject(json)
    }

    /**
     * Poll state until condition met or timeout. Uses QUERY_STATE broadcast.
     */
    protected fun waitForState(
        description: String,
        timeoutMs: Long = 15_000,
        intervalMs: Long = 500,
        condition: (JSONObject) -> Boolean
    ): JSONObject {
        val start = SystemClock.elapsedRealtime()
        var lastState: JSONObject? = null
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            try {
                lastState = queryState()
                if (condition(lastState)) return lastState
            } catch (e: Exception) {
                Log.w("WK_E2E", "queryState failed (will retry): ${e.message}")
            }
            SystemClock.sleep(intervalMs)
        }
        fail("Timeout waiting for: $description (${timeoutMs}ms)\nLast state: ${lastState?.toString(2) ?: "null"}")
        throw AssertionError("unreachable")
    }

    /**
     * Verify state transition sequence appeared in order within state_history.
     */
    protected fun assertStateHistoryContains(vararg expectedSequence: String) {
        val state = queryState()
        val history = state.getJSONArray("state_history")
        val historyList = (0 until history.length()).map { history.getString(it) }

        var searchIdx = 0
        for (expected in expectedSequence) {
            val foundIdx = historyList.indexOfFirst { it == expected && historyList.indexOf(it) >= searchIdx }
            assertTrue(
                "Expected '$expected' in state_history after index $searchIdx. " +
                "Full history: $historyList",
                foundIdx >= searchIdx
            )
            searchIdx = foundIdx + 1
        }
    }

    // ==================== Lifecycle Test Helpers ====================

    /**
     * Add a mock incoming message to the tutorial activity's RecyclerView.
     * This is the ONLY mock — everything downstream (accessibility, API) is real.
     */
    protected fun addIncomingMessage(sender: String, text: String) {
        activityRule.scenario.onActivity { activity ->
            activity.addTestIncomingMessage(sender, text)
        }
        SystemClock.sleep(500) // Let accessibility event propagate
    }

    /**
     * Trigger keyboard re-read by hiding and showing the keyboard.
     * This fires onStartInputViewInternal() → triggerProactiveContextReading().
     */
    protected fun cycleKeyboard() {
        device.pressBack() // Dismiss keyboard
        SystemClock.sleep(300)
        val editText = device.findObject(By.clazz("android.widget.EditText"))
        editText?.click() // Re-show keyboard
        SystemClock.sleep(800) // Wait for keyboard + pipeline to start
    }

    /**
     * Reset state history between tests for clean assertions.
     */
    protected fun resetStateHistory() {
        sendDebugBroadcast("RESET_STATE_HISTORY")
        SystemClock.sleep(200)
    }

    /**
     * Trigger the AI pipeline directly via TRIGGER_PIPELINE broadcast.
     * This bypasses the accessibility service (which can't bind in the test process)
     * but uses the REAL ReplyGenerator and Claude API.
     */
    protected fun triggerPipeline(sender: String, messageText: String) {
        Log.d(TAG, "triggerPipeline: sender=$sender message=${messageText.take(50)}")
        sendDebugBroadcast("TRIGGER_PIPELINE", mapOf(
            "sender" to sender,
            "message" to messageText
        ))
    }
}
