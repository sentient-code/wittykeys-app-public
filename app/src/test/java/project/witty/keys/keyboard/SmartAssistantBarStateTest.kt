package project.witty.keys.keyboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SmartAssistantBar state machine logic.
 *
 * Tests state transitions between EXPANDED and COLLAPSED states,
 * debounce behavior for typing detection, and UI state emissions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartAssistantBarStateTest {

    private lateinit var stateMachine: SmartAssistantBarStateMachine
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        stateMachine = SmartAssistantBarStateMachine(testScope)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ========== INITIAL STATE TESTS ==========

    @Test
    fun `initial state is EXPANDED`() {
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    @Test
    fun `initial state has no active input`() {
        assertFalse(stateMachine.isUserTyping.value)
    }

    // ========== TYPING STATE TRANSITION TESTS ==========

    @Test
    fun `onUserStartsTyping transitions to COLLAPSED`() {
        // Act
        stateMachine.onUserStartsTyping()

        // Assert
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)
    }

    @Test
    fun `onUserStartsTyping sets isUserTyping to true`() {
        // Act
        stateMachine.onUserStartsTyping()

        // Assert
        assertTrue(stateMachine.isUserTyping.value)
    }

    @Test
    fun `onUserStopsTyping after delay transitions to EXPANDED`() = testScope.runTest {
        // Arrange
        stateMachine.onUserStartsTyping()
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Act
        stateMachine.onUserStopsTyping()
        advanceTimeBy(SmartAssistantBarStateMachine.TYPING_DEBOUNCE_MS + 100)

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    @Test
    fun `onUserStopsTyping before debounce delay stays COLLAPSED`() = testScope.runTest {
        // Arrange
        stateMachine.onUserStartsTyping()
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Act
        stateMachine.onUserStopsTyping()
        advanceTimeBy(SmartAssistantBarStateMachine.TYPING_DEBOUNCE_MS / 2)

        // Assert - still collapsed because debounce hasn't completed
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)
    }

    @Test
    fun `rapid typing does not trigger multiple transitions`() = testScope.runTest {
        // Simulate rapid typing: type -> stop -> type -> stop
        stateMachine.onUserStartsTyping()
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        stateMachine.onUserStopsTyping()
        advanceTimeBy(500) // Not enough time for debounce (1500ms default)

        stateMachine.onUserStartsTyping()
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Verify it never went back to EXPANDED during rapid typing
        assertTrue(stateMachine.isUserTyping.value)
    }

    @Test
    fun `continuous typing resets debounce timer`() = testScope.runTest {
        // Start typing
        stateMachine.onUserStartsTyping()
        stateMachine.onUserStopsTyping()

        // Wait partially
        advanceTimeBy(1000)

        // Type again before debounce completes
        stateMachine.onUserStartsTyping()
        stateMachine.onUserStopsTyping()

        // Wait partially again
        advanceTimeBy(1000)

        // Should still be collapsed (timer was reset)
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Now wait for full debounce
        advanceTimeBy(600) // 1000 + 600 > 1500
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    // ========== KEYBOARD OPEN/CLOSE TESTS ==========

    @Test
    fun `onKeyboardOpens state is EXPANDED`() {
        // Act
        stateMachine.onKeyboardOpens()

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    @Test
    fun `onKeyboardOpens resets typing state`() {
        // Arrange
        stateMachine.onUserStartsTyping()
        assertTrue(stateMachine.isUserTyping.value)

        // Act
        stateMachine.onKeyboardOpens()

        // Assert
        assertFalse(stateMachine.isUserTyping.value)
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    @Test
    fun `onKeyboardCloses cancels pending transitions`() = testScope.runTest {
        // Arrange
        stateMachine.onUserStartsTyping()
        stateMachine.onUserStopsTyping()

        // Act - close keyboard before debounce completes
        advanceTimeBy(500)
        stateMachine.onKeyboardCloses()

        // Assert - no state change after debounce would have completed
        advanceTimeBy(2000)
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    // ========== EXPAND CTA TESTS ==========

    @Test
    fun `onExpandCtaTap transitions to EXPANDED from COLLAPSED`() {
        // Arrange
        stateMachine.onUserStartsTyping()
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Act
        stateMachine.onExpandCtaTap()

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    @Test
    fun `onExpandCtaTap when already EXPANDED stays EXPANDED`() {
        // Arrange
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)

        // Act
        stateMachine.onExpandCtaTap()

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    // ========== SUGGESTION TAP TESTS ==========

    @Test
    fun `onSuggestionTap stays in EXPANDED state`() {
        // Arrange
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)

        // Act
        stateMachine.onSuggestionTap("Sure! 👍")

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    @Test
    fun `onSuggestionTap from COLLAPSED transitions to EXPANDED`() {
        // Arrange
        stateMachine.onUserStartsTyping()
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Act
        stateMachine.onSuggestionTap("Sure! 👍")

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    @Test
    fun `onSuggestionTap emits insert text event`() {
        // Arrange
        var insertedText: String? = null
        stateMachine.setOnTextInsertListener { text ->
            insertedText = text
        }

        // Act
        stateMachine.onSuggestionTap("Hello there!")

        // Assert
        assertEquals("Hello there!", insertedText)
    }

    // ========== ASK AI TESTS ==========

    @Test
    fun `onAskAITap opens EXPANDED with input focus`() {
        // Arrange
        stateMachine.onUserStartsTyping()
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Act
        stateMachine.onAskAITap()

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
        assertTrue(stateMachine.isAIInputFocused.value)
    }

    @Test
    fun `onAskAITap emits show input event`() {
        // Arrange
        var showInputCalled = false
        stateMachine.setOnShowAIInputListener {
            showInputCalled = true
        }

        // Act
        stateMachine.onAskAITap()

        // Assert
        assertTrue(showInputCalled)
    }

    // ========== TEXT INPUT HANDLING TESTS ==========

    @Test
    fun `onTextInput with non-empty text transitions to COLLAPSED`() {
        // Act
        stateMachine.onTextInput("Hello")

        // Assert
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)
    }

    @Test
    fun `onTextInput with empty text transitions to EXPANDED`() {
        // Arrange - start in collapsed
        stateMachine.onTextInput("Hello")
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Act
        stateMachine.onTextInput("")

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    @Test
    fun `onTextInput with whitespace only transitions to EXPANDED`() {
        // Arrange - start in collapsed
        stateMachine.onTextInput("Hello")
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Act
        stateMachine.onTextInput("   ")

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }

    // ========== STATE TRANSITION EVENT EMISSION TESTS ==========

    @Test
    fun `stateTransition emits correct UIState for EXPANDED`() {
        // The initial state should emit EXPANDED UIState
        val uiState = stateMachine.uiState.value

        assertTrue(uiState is UIState.Expanded)
    }

    @Test
    fun `stateTransition emits correct UIState for COLLAPSED`() {
        // Act
        stateMachine.onUserStartsTyping()

        // Assert
        val uiState = stateMachine.uiState.value
        assertTrue(uiState is UIState.Collapsed)
    }

    @Test
    fun `UIState includes smart replies when context available`() {
        // Arrange
        stateMachine.setSmartReplies(listOf("Sure!", "What time?", "Can't make it", "OK"))

        // Assert
        val uiState = stateMachine.uiState.value
        assertTrue(uiState is UIState.Expanded)
        val expandedState = uiState as UIState.Expanded
        assertEquals(4, expandedState.smartReplies.size)
    }

    @Test
    fun `UIState collapsed includes predictions`() {
        // Arrange
        stateMachine.onTextInput("Hel")
        stateMachine.setPredictions(listOf("Hello", "Help", "Helicopter"))

        // Assert
        val uiState = stateMachine.uiState.value
        assertTrue(uiState is UIState.Collapsed)
        val collapsedState = uiState as UIState.Collapsed
        assertEquals(3, collapsedState.predictions.size)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `multiple onUserStartsTyping calls stay COLLAPSED`() {
        // Act
        stateMachine.onUserStartsTyping()
        stateMachine.onUserStartsTyping()
        stateMachine.onUserStartsTyping()

        // Assert
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)
    }

    @Test
    fun `state unchanged when setting same state`() {
        // Arrange
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
        var stateChangeCount = 0
        stateMachine.setOnStateChangeListener { stateChangeCount++ }

        // Act - try to set same state
        stateMachine.forceState(BarState.EXPANDED)

        // Assert - no additional state change
        assertEquals(0, stateChangeCount)
    }

    @Test
    fun `null text input treated as empty`() {
        // Arrange
        stateMachine.onTextInput("Hello")
        assertEquals(BarState.COLLAPSED, stateMachine.currentState.value)

        // Act
        stateMachine.onTextInput(null)

        // Assert
        assertEquals(BarState.EXPANDED, stateMachine.currentState.value)
    }
}

// ========== SUPPORTING CLASSES FOR TESTS ==========

/**
 * State enum for SmartAssistantBar
 */
enum class BarState {
    EXPANDED,   // AI buttons visible, smart replies or hint in row 2
    COLLAPSED   // Predictions visible, tone chips + context actions in row 2
}

/**
 * UI State sealed class for representing different SmartAssistantBar states
 */
sealed class UIState {
    data class Expanded(
        val smartReplies: List<String> = emptyList(),
        val hasContext: Boolean = false,
        val isLoading: Boolean = false
    ) : UIState()

    data class Collapsed(
        val predictions: List<String> = emptyList(),
        val toneChips: List<String> = listOf("Formal", "Casual", "Friendly"),
        val contextActions: List<ContextAction> = emptyList()
    ) : UIState()

    data class Loading(val message: String = "Loading...") : UIState()
    data class Error(val message: String) : UIState()
}

/**
 * Context action types for Row 2 collapsed state
 */
sealed class ContextAction {
    data class GrammarFix(val errorCount: Int) : ContextAction()
    data class Translate(val targetLanguage: String) : ContextAction()
    data class EmojiSuggestions(val emojis: List<String>) : ContextAction()
    object Shorten : ContextAction()
    object Expand : ContextAction()
}

/**
 * State machine for SmartAssistantBar state transitions.
 * Extracted from SmartAssistantBar for testability.
 */
class SmartAssistantBarStateMachine(
    private val scope: CoroutineScope
) {
    companion object {
        const val TYPING_DEBOUNCE_MS = 1500L
    }

    // State flows
    private val _currentState = MutableStateFlow(BarState.EXPANDED)
    val currentState: StateFlow<BarState> = _currentState

    private val _isUserTyping = MutableStateFlow(false)
    val isUserTyping: StateFlow<Boolean> = _isUserTyping

    private val _isAIInputFocused = MutableStateFlow(false)
    val isAIInputFocused: StateFlow<Boolean> = _isAIInputFocused

    private val _uiState = MutableStateFlow<UIState>(UIState.Expanded())
    val uiState: StateFlow<UIState> = _uiState

    // Smart replies and predictions
    private var smartReplies: List<String> = emptyList()
    private var predictions: List<String> = emptyList()

    // Listeners
    private var onTextInsertListener: ((String) -> Unit)? = null
    private var onShowAIInputListener: (() -> Unit)? = null
    private var onStateChangeListener: ((BarState) -> Unit)? = null

    // Debounce job
    private var debounceJob: Job? = null

    fun onUserStartsTyping() {
        _isUserTyping.value = true
        debounceJob?.cancel()
        setState(BarState.COLLAPSED)
    }

    fun onUserStopsTyping() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(TYPING_DEBOUNCE_MS)
            _isUserTyping.value = false
            setState(BarState.EXPANDED)
        }
    }

    fun onKeyboardOpens() {
        debounceJob?.cancel()
        _isUserTyping.value = false
        setState(BarState.EXPANDED)
    }

    fun onKeyboardCloses() {
        debounceJob?.cancel()
        _isUserTyping.value = false
        setState(BarState.EXPANDED)
    }

    fun onExpandCtaTap() {
        setState(BarState.EXPANDED)
    }

    fun onSuggestionTap(text: String) {
        onTextInsertListener?.invoke(text)
        setState(BarState.EXPANDED)
    }

    fun onAskAITap() {
        _isAIInputFocused.value = true
        onShowAIInputListener?.invoke()
        setState(BarState.EXPANDED)
    }

    fun onTextInput(text: String?) {
        if (text.isNullOrBlank()) {
            _isUserTyping.value = false
            setState(BarState.EXPANDED)
        } else {
            _isUserTyping.value = true
            setState(BarState.COLLAPSED)
        }
    }

    fun setSmartReplies(replies: List<String>) {
        smartReplies = replies
        updateUIState()
    }

    fun setPredictions(newPredictions: List<String>) {
        predictions = newPredictions
        updateUIState()
    }

    fun forceState(state: BarState) {
        if (_currentState.value != state) {
            setState(state)
        }
    }

    private fun setState(newState: BarState) {
        if (_currentState.value == newState) return
        _currentState.value = newState
        onStateChangeListener?.invoke(newState)
        updateUIState()
    }

    private fun updateUIState() {
        _uiState.value = when (_currentState.value) {
            BarState.EXPANDED -> UIState.Expanded(
                smartReplies = smartReplies,
                hasContext = smartReplies.isNotEmpty()
            )
            BarState.COLLAPSED -> UIState.Collapsed(
                predictions = predictions
            )
        }
    }

    fun setOnTextInsertListener(listener: (String) -> Unit) {
        onTextInsertListener = listener
    }

    fun setOnShowAIInputListener(listener: () -> Unit) {
        onShowAIInputListener = listener
    }

    fun setOnStateChangeListener(listener: (BarState) -> Unit) {
        onStateChangeListener = listener
    }
}
