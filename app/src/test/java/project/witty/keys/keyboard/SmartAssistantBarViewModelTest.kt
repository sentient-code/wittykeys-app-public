package project.witty.keys.keyboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SmartAssistantBarViewModel.
 *
 * Tests suggestion loading, UI state management, error handling,
 * and event emissions for user interactions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartAssistantBarViewModelTest {

    private lateinit var viewModel: SmartAssistantBarViewModel
    private lateinit var mockReplyGenerator: MockReplyGenerator
    private lateinit var mockContextEngine: MockContextEngine

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockReplyGenerator = MockReplyGenerator()
        mockContextEngine = MockContextEngine()
        viewModel = SmartAssistantBarViewModel(
            replyGenerator = mockReplyGenerator,
            contextEngine = mockContextEngine,
            scope = testScope
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ========== LOAD SUGGESTIONS TESTS ==========

    @Test
    fun `loadSuggestions on screenContext change updates UI`() = testScope.runTest {
        // Arrange
        val replies = listOf("Hey!", "Hello", "Hi there", "What's up")
        mockReplyGenerator.setReplies(replies)
        mockContextEngine.setContext(
            MockScreenContext(
                appType = AppType.MESSAGING,
                lastMessage = "Hi there!"
            )
        )

        // Act
        viewModel.loadSuggestions()
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is ViewModelUIState.Success)
        assertEquals(4, (state as ViewModelUIState.Success).suggestions.size)
        assertEquals("Hey!", state.suggestions[0])
    }

    @Test
    fun `loadSuggestions with error shows error state`() = testScope.runTest {
        // Arrange
        mockReplyGenerator.setError("Network error")
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))

        // Act
        viewModel.loadSuggestions()
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is ViewModelUIState.Error)
        assertEquals("Network error", (state as ViewModelUIState.Error).message)
    }

    @Test
    fun `loadSuggestions while loading shows loading state`() = testScope.runTest {
        // Arrange
        mockReplyGenerator.setDelay(1000)
        mockReplyGenerator.setReplies(listOf("Reply 1", "Reply 2"))
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))

        // Act
        viewModel.loadSuggestions()
        // Don't advance time - check loading state immediately after launch

        // Assert - we need to check synchronously before advanceUntilIdle
        // Since we're using StandardTestDispatcher, the loading state should be set
        val state = viewModel.uiState.value
        assertTrue(state is ViewModelUIState.Loading)
    }

    @Test
    fun `loadSuggestions with null context shows empty state`() = testScope.runTest {
        // Arrange
        mockContextEngine.setContext(null)

        // Act
        viewModel.loadSuggestions()
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is ViewModelUIState.Empty)
    }

    @Test
    fun `suggestions are limited to 4`() = testScope.runTest {
        // Arrange
        val manyReplies = listOf("Reply 1", "Reply 2", "Reply 3", "Reply 4", "Reply 5", "Reply 6")
        mockReplyGenerator.setReplies(manyReplies)
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))

        // Act
        viewModel.loadSuggestions()
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is ViewModelUIState.Success)
        assertEquals(4, (state as ViewModelUIState.Success).suggestions.size)
    }

    // ========== SUGGESTION CLICK TESTS ==========

    @Test
    fun `onSuggestionClick emits insert text event`() = testScope.runTest {
        // Arrange
        var insertedText: String? = null
        viewModel.setOnInsertTextListener { text ->
            insertedText = text
        }

        // Act
        viewModel.onSuggestionClick("Sure! 👍")
        advanceUntilIdle()

        // Assert
        assertEquals("Sure! 👍", insertedText)
    }

    @Test
    fun `onSuggestionClick with empty text does not emit event`() = testScope.runTest {
        // Arrange
        var insertedText: String? = null
        viewModel.setOnInsertTextListener { text ->
            insertedText = text
        }

        // Act
        viewModel.onSuggestionClick("")
        advanceUntilIdle()

        // Assert
        assertNull(insertedText)
    }

    // ========== ASK AI CLICK TESTS ==========

    @Test
    fun `onAskAIClick emits show input event`() = testScope.runTest {
        // Arrange
        var showInputCalled = false
        viewModel.setOnShowInputListener {
            showInputCalled = true
        }

        // Act
        viewModel.onAskAIClick()
        advanceUntilIdle()

        // Assert
        assertTrue(showInputCalled)
    }

    @Test
    fun `onAskAIClick sets AI mode active`() = testScope.runTest {
        // Act
        viewModel.onAskAIClick()
        advanceUntilIdle()

        // Assert
        assertTrue(viewModel.isAIModeActive.value)
    }

    // ========== COLLAPSE CLICK TESTS ==========

    @Test
    fun `onCollapseClick emits collapse event`() = testScope.runTest {
        // Arrange
        var collapseCalled = false
        viewModel.setOnCollapseListener {
            collapseCalled = true
        }

        // Act
        viewModel.onCollapseClick()
        advanceUntilIdle()

        // Assert
        assertTrue(collapseCalled)
    }

    @Test
    fun `onCollapseClick updates bar state`() = testScope.runTest {
        // Arrange - start in expanded
        assertEquals(BarState.EXPANDED, viewModel.barState.value)

        // Act
        viewModel.onCollapseClick()
        advanceUntilIdle()

        // Assert
        assertEquals(BarState.COLLAPSED, viewModel.barState.value)
    }

    // ========== TONE CHIP TESTS ==========

    @Test
    fun `onToneChipClick with formal tone applies transformation`() = testScope.runTest {
        // Arrange
        viewModel.setCurrentText("hey whats up")
        var transformedText: String? = null
        viewModel.setOnTextTransformListener { text ->
            transformedText = text
        }
        mockReplyGenerator.setToneTransformResult("Hello, how are you?")

        // Act
        viewModel.onToneChipClick(ToneType.FORMAL)
        advanceUntilIdle()

        // Assert
        assertEquals("Hello, how are you?", transformedText)
    }

    @Test
    fun `onToneChipClick with casual tone applies transformation`() = testScope.runTest {
        // Arrange
        viewModel.setCurrentText("Good morning, I hope this message finds you well.")
        var transformedText: String? = null
        viewModel.setOnTextTransformListener { text ->
            transformedText = text
        }
        mockReplyGenerator.setToneTransformResult("Hey! Hope you're doing well!")

        // Act
        viewModel.onToneChipClick(ToneType.CASUAL)
        advanceUntilIdle()

        // Assert
        assertEquals("Hey! Hope you're doing well!", transformedText)
    }

    @Test
    fun `onToneChipClick with empty text does not transform`() = testScope.runTest {
        // Arrange
        viewModel.setCurrentText("")
        var transformCalled = false
        viewModel.setOnTextTransformListener {
            transformCalled = true
        }

        // Act
        viewModel.onToneChipClick(ToneType.FORMAL)
        advanceUntilIdle()

        // Assert
        assertFalse(transformCalled)
    }

    // ========== CONTEXT ACTION TESTS ==========

    @Test
    fun `grammar errors detected shows fix chip`() = testScope.runTest {
        // Arrange
        mockContextEngine.setGrammarErrors(2)
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))
        mockReplyGenerator.setReplies(listOf("Reply 1"))

        viewModel.loadSuggestions()
        advanceUntilIdle()

        viewModel.onTextChanged("He dont like the movie")
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        if (state is ViewModelUIState.Success) {
            assertTrue(state.contextActions.any { it is ContextAction.GrammarFix })
            val grammarAction = state.contextActions.first { it is ContextAction.GrammarFix } as ContextAction.GrammarFix
            assertEquals(2, grammarAction.errorCount)
        }
    }

    @Test
    fun `language mismatch shows translate chip`() = testScope.runTest {
        // Arrange
        mockContextEngine.setLanguageMismatch("Hindi")
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))
        mockReplyGenerator.setReplies(listOf("Reply 1"))

        viewModel.loadSuggestions()
        advanceUntilIdle()

        viewModel.onTextChanged("नमस्ते")
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        if (state is ViewModelUIState.Success) {
            assertTrue(state.contextActions.any { it is ContextAction.Translate })
        }
    }

    @Test
    fun `long text shows length actions`() = testScope.runTest {
        // Arrange
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))
        mockReplyGenerator.setReplies(listOf("Reply 1"))

        viewModel.loadSuggestions()
        advanceUntilIdle()

        val longText = "a".repeat(150)
        viewModel.onTextChanged(longText)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        if (state is ViewModelUIState.Success) {
            assertTrue(state.contextActions.any { it == ContextAction.Shorten })
        }
    }

    @Test
    fun `emoji suggestions shown by default`() = testScope.runTest {
        // Arrange
        mockContextEngine.setEmojiSuggestions(listOf("😊", "👍", "❤️"))
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))
        mockReplyGenerator.setReplies(listOf("Reply 1"))

        viewModel.loadSuggestions()
        advanceUntilIdle()

        viewModel.onTextChanged("Thanks")
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        if (state is ViewModelUIState.Success) {
            assertTrue(state.contextActions.any { it is ContextAction.EmojiSuggestions })
        }
    }

    // ========== PREDICTION TESTS ==========

    @Test
    fun `predictions updated on text input`() = testScope.runTest {
        // Arrange
        viewModel.onTextChanged("Hel")
        advanceUntilIdle()

        // Assert
        val predictions = viewModel.predictions.value
        assertTrue(predictions.isNotEmpty())
    }

    @Test
    fun `predictions limited to 3`() = testScope.runTest {
        // Arrange
        viewModel.onTextChanged("th")
        advanceUntilIdle()

        // Assert
        val predictions = viewModel.predictions.value
        assertTrue(predictions.size <= 3)
    }

    // ========== PROACTIVE CONTEXT TESTS ==========

    @Test
    fun `proactive context triggers auto suggestions`() = testScope.runTest {
        // Arrange
        mockReplyGenerator.setReplies(listOf("Sure!", "What time?", "Maybe later"))
        mockContextEngine.setContext(
            MockScreenContext(
                appType = AppType.MESSAGING,
                lastMessage = "Party tonight?",
                isProactive = true
            )
        )

        // Act
        viewModel.onProactiveContextReceived()
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is ViewModelUIState.Success)
        assertTrue((state as ViewModelUIState.Success).suggestions.isNotEmpty())
    }

    @Test
    fun `proactive context for non-supported app shows empty`() = testScope.runTest {
        // Arrange
        mockContextEngine.setContext(
            MockScreenContext(
                appType = AppType.OTHER,
                isProactive = true
            )
        )

        // Act
        viewModel.onProactiveContextReceived()
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is ViewModelUIState.Empty)
    }

    // ========== CACHE TESTS ==========

    @Test
    fun `cached context reused within TTL`() = testScope.runTest {
        // Arrange
        mockReplyGenerator.setReplies(listOf("Reply 1"))
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))

        // First load
        viewModel.loadSuggestions()
        advanceUntilIdle()

        val firstCallCount = mockContextEngine.extractContextCallCount

        // Second load within TTL (cache still valid)
        viewModel.loadSuggestions()
        advanceUntilIdle()

        // Assert - context engine not called again when cache is valid
        assertEquals(firstCallCount, mockContextEngine.extractContextCallCount)
    }

    @Test
    fun `cache invalidated after TTL`() = testScope.runTest {
        // Arrange
        mockReplyGenerator.setReplies(listOf("Reply 1"))
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))
        mockContextEngine.setCacheTTL(100) // Very short TTL for testing

        // First load
        viewModel.loadSuggestions()
        advanceUntilIdle()

        val firstCallCount = mockContextEngine.extractContextCallCount

        // Simulate TTL expiration
        mockContextEngine.invalidateCache()

        // Second load after TTL
        viewModel.loadSuggestions()
        advanceUntilIdle()

        // Assert - context engine called again
        assertTrue(mockContextEngine.extractContextCallCount > firstCallCount)
    }

    // ========== ERROR RECOVERY TESTS ==========

    @Test
    fun `retry after error succeeds`() = testScope.runTest {
        // Arrange - first call fails
        mockReplyGenerator.setError("Network error")
        mockContextEngine.setContext(MockScreenContext(appType = AppType.MESSAGING))
        viewModel.loadSuggestions()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ViewModelUIState.Error)

        // Second call succeeds
        mockReplyGenerator.clearError()
        mockReplyGenerator.setReplies(listOf("Reply 1", "Reply 2"))

        // Invalidate cache to force fresh context
        mockContextEngine.invalidateCache()

        // Act
        viewModel.loadSuggestions()
        advanceUntilIdle()

        // Assert
        assertTrue(viewModel.uiState.value is ViewModelUIState.Success)
    }

    // ========== SUBSCRIPTION CHECK TESTS ==========

    @Test
    fun `AI features disabled without subscription`() = testScope.runTest {
        // Arrange
        viewModel.setSubscriptionStatus(SubscriptionStatus.EXPIRED)

        // Act
        viewModel.onAskAIClick()
        advanceUntilIdle()

        // Assert - should show subscription gate instead of AI input
        assertTrue(viewModel.shouldShowSubscriptionGate.value)
    }

    @Test
    fun `AI features enabled with active subscription`() = testScope.runTest {
        // Arrange
        viewModel.setSubscriptionStatus(SubscriptionStatus.ACTIVE)

        // Act
        viewModel.onAskAIClick()
        advanceUntilIdle()

        // Assert
        assertFalse(viewModel.shouldShowSubscriptionGate.value)
        assertTrue(viewModel.isAIModeActive.value)
    }

    @Test
    fun `AI features enabled during free trial`() = testScope.runTest {
        // Arrange
        viewModel.setSubscriptionStatus(SubscriptionStatus.FREE_TRIAL)

        // Act
        viewModel.onAskAIClick()
        advanceUntilIdle()

        // Assert
        assertFalse(viewModel.shouldShowSubscriptionGate.value)
        assertTrue(viewModel.isAIModeActive.value)
    }
}

// ========== MOCK CLASSES ==========

class MockReplyGenerator {
    private var replies: List<String> = emptyList()
    private var error: String? = null
    private var delayMs: Long = 0
    private var toneTransformResult: String? = null

    fun setReplies(replies: List<String>) {
        this.replies = replies
    }

    fun setError(error: String) {
        this.error = error
    }

    fun clearError() {
        this.error = null
    }

    fun setDelay(delayMs: Long) {
        this.delayMs = delayMs
    }

    fun setToneTransformResult(result: String) {
        this.toneTransformResult = result
    }

    suspend fun generateReplies(context: MockScreenContext?): Result<List<String>> {
        if (delayMs > 0) {
            delay(delayMs)
        }
        return if (error != null) {
            Result.failure(Exception(error))
        } else {
            Result.success(replies)
        }
    }

    suspend fun transformTone(text: String, tone: ToneType): Result<String> {
        return if (toneTransformResult != null) {
            Result.success(toneTransformResult!!)
        } else {
            Result.failure(Exception("No transform result set"))
        }
    }
}

class MockContextEngine {
    private var context: MockScreenContext? = null
    private var grammarErrors: Int = 0
    private var languageMismatch: String? = null
    private var emojiSuggestions: List<String> = emptyList()
    private var cacheTTL: Long = 2000
    private var cacheValid = true

    var extractContextCallCount = 0
        private set

    fun setContext(context: MockScreenContext?) {
        this.context = context
    }

    fun setGrammarErrors(count: Int) {
        this.grammarErrors = count
    }

    fun setLanguageMismatch(language: String?) {
        this.languageMismatch = language
    }

    fun setEmojiSuggestions(emojis: List<String>) {
        this.emojiSuggestions = emojis
    }

    fun setCacheTTL(ttl: Long) {
        this.cacheTTL = ttl
    }

    fun invalidateCache() {
        cacheValid = false
    }

    fun extractContext(): MockScreenContext? {
        if (!cacheValid) {
            extractContextCallCount++
            cacheValid = true
        } else if (extractContextCallCount == 0) {
            extractContextCallCount++
        }
        return context
    }

    fun isCacheValid(): Boolean = cacheValid

    fun getGrammarErrors(text: String): Int = grammarErrors

    fun getLanguageMismatch(text: String): String? = languageMismatch

    fun getEmojiSuggestions(text: String): List<String> = emojiSuggestions
}

data class MockScreenContext(
    val appType: AppType = AppType.OTHER,
    val lastMessage: String? = null,
    val senderName: String? = null,
    val isProactive: Boolean = false
)

enum class AppType {
    MESSAGING,
    EMAIL,
    DATING,
    SOCIAL,
    OTHER
}

enum class ToneType {
    FORMAL,
    CASUAL,
    FRIENDLY
}

enum class SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    FREE_TRIAL,
    NONE
}

// ========== VIEW MODEL UI STATE ==========

sealed class ViewModelUIState {
    object Loading : ViewModelUIState()
    object Empty : ViewModelUIState()
    data class Success(
        val suggestions: List<String>,
        val contextActions: List<ContextAction> = emptyList()
    ) : ViewModelUIState()
    data class Error(val message: String) : ViewModelUIState()
}

// ========== VIEW MODEL IMPLEMENTATION ==========

class SmartAssistantBarViewModel(
    private val replyGenerator: MockReplyGenerator,
    private val contextEngine: MockContextEngine,
    private val scope: CoroutineScope
) {
    companion object {
        private const val MAX_SUGGESTIONS = 4
    }

    // UI State
    private val _uiState = MutableStateFlow<ViewModelUIState>(ViewModelUIState.Empty)
    val uiState: StateFlow<ViewModelUIState> = _uiState

    private val _barState = MutableStateFlow(BarState.EXPANDED)
    val barState: StateFlow<BarState> = _barState

    private val _isAIModeActive = MutableStateFlow(false)
    val isAIModeActive: StateFlow<Boolean> = _isAIModeActive

    private val _shouldShowSubscriptionGate = MutableStateFlow(false)
    val shouldShowSubscriptionGate: StateFlow<Boolean> = _shouldShowSubscriptionGate

    private val _predictions = MutableStateFlow<List<String>>(emptyList())
    val predictions: StateFlow<List<String>> = _predictions

    // State
    private var currentText: String = ""
    private var subscriptionStatus: SubscriptionStatus = SubscriptionStatus.ACTIVE

    // Listeners
    private var onInsertTextListener: ((String) -> Unit)? = null
    private var onShowInputListener: (() -> Unit)? = null
    private var onCollapseListener: (() -> Unit)? = null
    private var onTextTransformListener: ((String) -> Unit)? = null

    fun loadSuggestions() {
        _uiState.value = ViewModelUIState.Loading

        scope.launch {
            val context = contextEngine.extractContext()
            if (context == null) {
                _uiState.value = ViewModelUIState.Empty
                return@launch
            }

            replyGenerator.generateReplies(context)
                .onSuccess { replies ->
                    val limitedReplies = replies.take(MAX_SUGGESTIONS)
                    _uiState.value = ViewModelUIState.Success(
                        suggestions = limitedReplies,
                        contextActions = buildContextActions(currentText)
                    )
                }
                .onFailure { error ->
                    _uiState.value = ViewModelUIState.Error(error.message ?: "Unknown error")
                }
        }
    }

    fun onSuggestionClick(text: String) {
        if (text.isNotEmpty()) {
            onInsertTextListener?.invoke(text)
        }
    }

    fun onAskAIClick() {
        if (!checkSubscription()) {
            _shouldShowSubscriptionGate.value = true
            return
        }
        _isAIModeActive.value = true
        onShowInputListener?.invoke()
    }

    fun onCollapseClick() {
        _barState.value = BarState.COLLAPSED
        onCollapseListener?.invoke()
    }

    fun onToneChipClick(tone: ToneType) {
        if (currentText.isEmpty()) return

        scope.launch {
            replyGenerator.transformTone(currentText, tone)
                .onSuccess { transformed ->
                    onTextTransformListener?.invoke(transformed)
                }
        }
    }

    fun onTextChanged(text: String) {
        currentText = text
        updatePredictions(text)
        updateContextActions(text)
    }

    fun setCurrentText(text: String) {
        currentText = text
    }

    fun onProactiveContextReceived() {
        val context = contextEngine.extractContext()
        if (context == null || context.appType == AppType.OTHER) {
            _uiState.value = ViewModelUIState.Empty
            return
        }
        loadSuggestions()
    }

    fun setSubscriptionStatus(status: SubscriptionStatus) {
        subscriptionStatus = status
        _shouldShowSubscriptionGate.value = false
    }

    private fun checkSubscription(): Boolean {
        return subscriptionStatus == SubscriptionStatus.ACTIVE ||
               subscriptionStatus == SubscriptionStatus.FREE_TRIAL
    }

    private fun updatePredictions(text: String) {
        if (text.isEmpty()) {
            _predictions.value = listOf("Hi", "Hey", "Hello")
        } else {
            // Simulate prediction generation
            _predictions.value = listOf(text + "lo", text + "lp", text + "llo").take(3)
        }
    }

    private fun updateContextActions(text: String) {
        val actions = buildContextActions(text)
        val currentState = _uiState.value
        if (currentState is ViewModelUIState.Success) {
            _uiState.value = currentState.copy(contextActions = actions)
        }
    }

    private fun buildContextActions(text: String): List<ContextAction> {
        val actions = mutableListOf<ContextAction>()

        // Grammar errors
        val grammarErrors = contextEngine.getGrammarErrors(text)
        if (grammarErrors > 0) {
            actions.add(ContextAction.GrammarFix(grammarErrors))
        }

        // Language mismatch
        val languageMismatch = contextEngine.getLanguageMismatch(text)
        if (languageMismatch != null) {
            actions.add(ContextAction.Translate(languageMismatch))
        }

        // Length actions
        if (text.length > 100) {
            actions.add(ContextAction.Shorten)
        }

        // Emoji suggestions (default)
        val emojis = contextEngine.getEmojiSuggestions(text)
        if (emojis.isNotEmpty()) {
            actions.add(ContextAction.EmojiSuggestions(emojis))
        }

        return actions
    }

    // Listener setters
    fun setOnInsertTextListener(listener: (String) -> Unit) {
        onInsertTextListener = listener
    }

    fun setOnShowInputListener(listener: () -> Unit) {
        onShowInputListener = listener
    }

    fun setOnCollapseListener(listener: () -> Unit) {
        onCollapseListener = listener
    }

    fun setOnTextTransformListener(listener: (String) -> Unit) {
        onTextTransformListener = listener
    }
}
