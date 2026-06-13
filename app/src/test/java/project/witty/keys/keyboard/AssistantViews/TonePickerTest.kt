package project.witty.keys.keyboard.AssistantViews

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import project.witty.keys.app.utils.ToneData

/**
 * Task 2.37: Unit tests for Tone Picker functionality.
 *
 * Tests tone selection, pinning, and Row2State transitions.
 */
class TonePickerTest {

    private lateinit var tonePicker: TonePickerController

    @Before
    fun setup() {
        tonePicker = TonePickerController()
    }

    // ========== TONE DATA TESTS ==========

    @Test
    fun `ToneData has 21 tones`() {
        val toneEmojiMap = ToneData.getToneEmojiMap()
        assertEquals(21, toneEmojiMap.size)
    }

    @Test
    fun `each tone has emoji`() {
        val toneEmojiMap = ToneData.getToneEmojiMap()
        for ((tone, emoji) in toneEmojiMap) {
            assertNotNull("Tone $tone should have emoji", emoji)
            assertTrue("Emoji for $tone should not be empty", emoji.isNotEmpty())
        }
    }

    @Test
    fun `getEmojiForTone returns correct emoji`() {
        val professionalEmoji = ToneData.getEmojiForTone("Professional")
        assertNotNull("Professional tone should have emoji", professionalEmoji)
        assertTrue("Professional emoji should not be empty", professionalEmoji.isNotEmpty())
    }

    // ========== TONE PICKER STATE TESTS ==========

    @Test
    fun `initial state is not showing`() {
        assertFalse(tonePicker.isShowing)
    }

    @Test
    fun `show sets isShowing to true`() {
        tonePicker.show()
        assertTrue(tonePicker.isShowing)
    }

    @Test
    fun `hide sets isShowing to false`() {
        tonePicker.show()
        tonePicker.hide()
        assertFalse(tonePicker.isShowing)
    }

    @Test
    fun `initial selected tone is null`() {
        assertEquals(null, tonePicker.selectedTone)
    }

    // ========== TONE SELECTION TESTS ==========

    @Test
    fun `selectTone sets selected tone`() {
        tonePicker.selectTone("Professional")
        assertEquals("Professional", tonePicker.selectedTone)
    }

    @Test
    fun `selectTone triggers transition to TONE_ACTIVE`() {
        var transitionedState: Row2State? = null
        tonePicker.onStateChange = { state -> transitionedState = state }

        tonePicker.selectTone("Casual")

        assertEquals(Row2State.TONE_ACTIVE, transitionedState)
    }

    @Test
    fun `selectTone with same tone does not re-trigger transition`() {
        var transitionCount = 0
        tonePicker.onStateChange = { transitionCount++ }

        tonePicker.selectTone("Professional")
        tonePicker.selectTone("Professional") // Same tone

        assertEquals(1, transitionCount)
    }

    @Test
    fun `clearSelection resets selected tone`() {
        tonePicker.selectTone("Friendly")
        tonePicker.clearSelection()

        assertEquals(null, tonePicker.selectedTone)
    }

    @Test
    fun `clearSelection triggers transition to SMART_REPLIES`() {
        tonePicker.selectTone("Casual")

        var transitionedState: Row2State? = null
        tonePicker.onStateChange = { state -> transitionedState = state }

        tonePicker.clearSelection()

        assertEquals(Row2State.SMART_REPLIES, transitionedState)
    }

    // ========== PINNED TONE TESTS ==========

    @Test
    fun `pinned tone is displayed in TONE_ACTIVE state`() {
        tonePicker.selectTone("Witty")

        val pinnedDisplay = tonePicker.getPinnedToneDisplay()

        assertNotNull(pinnedDisplay)
        assertTrue(pinnedDisplay!!.contains("Witty"))
    }

    @Test
    fun `pinned tone display includes emoji`() {
        tonePicker.selectTone("Professional")

        val pinnedDisplay = tonePicker.getPinnedToneDisplay()

        // Should contain the emoji for Professional tone
        val emoji = ToneData.getEmojiForTone("Professional")
        assertTrue("Pinned display should include emoji", pinnedDisplay?.contains(emoji) == true)
    }

    // ========== REGENERATE TESTS ==========

    @Test
    fun `regenerate with pinned tone triggers callback`() {
        tonePicker.selectTone("Casual")

        var regenerateCalled = false
        var regenerateTone: String? = null
        tonePicker.onRegenerate = { tone ->
            regenerateCalled = true
            regenerateTone = tone
        }

        tonePicker.regenerate()

        assertTrue(regenerateCalled)
        assertEquals("Casual", regenerateTone)
    }

    @Test
    fun `regenerate without selected tone does nothing`() {
        var regenerateCalled = false
        tonePicker.onRegenerate = { regenerateCalled = true }

        tonePicker.regenerate()

        assertFalse(regenerateCalled)
    }

    // ========== CUSTOM TONE TESTS ==========

    @Test
    fun `custom tone button triggers custom mode`() {
        var customModeCalled = false
        tonePicker.onCustomMode = { customModeCalled = true }

        tonePicker.openCustomMode()

        assertTrue(customModeCalled)
    }

    @Test
    fun `custom mode transitions to CUSTOM_MODE state`() {
        var transitionedState: Row2State? = null
        tonePicker.onStateChange = { state -> transitionedState = state }

        tonePicker.openCustomMode()

        assertEquals(Row2State.CUSTOM_MODE, transitionedState)
    }

    // ========== TONE CATEGORY TESTS ==========

    @Test
    fun `tones are organized logically`() {
        val toneEmojiMap = ToneData.getToneEmojiMap()

        // Verify some common tones exist (from ToneData.java)
        assertTrue(toneEmojiMap.containsKey("Professional"))
        assertTrue(toneEmojiMap.containsKey("Casual"))
        assertTrue(toneEmojiMap.containsKey("Formal"))
        assertTrue(toneEmojiMap.containsKey("Witty"))
    }

    @Test
    fun `tone count matches expected hierarchy size`() {
        val toneEmojiMap = ToneData.getToneEmojiMap()

        // Should have at least 10 tones (minimum viable set)
        assertTrue("Should have at least 10 tones", toneEmojiMap.size >= 10)
    }
}

/**
 * Controller for tone picker state and interactions
 */
class TonePickerController {
    var isShowing: Boolean = false
        private set

    var selectedTone: String? = null
        private set

    var onStateChange: ((Row2State) -> Unit)? = null
    var onRegenerate: ((String) -> Unit)? = null
    var onCustomMode: (() -> Unit)? = null

    fun show() {
        isShowing = true
    }

    fun hide() {
        isShowing = false
    }

    fun selectTone(tone: String) {
        if (selectedTone != tone) {
            selectedTone = tone
            onStateChange?.invoke(Row2State.TONE_ACTIVE)
        }
    }

    fun clearSelection() {
        selectedTone = null
        onStateChange?.invoke(Row2State.SMART_REPLIES)
    }

    fun getPinnedToneDisplay(): String? {
        val tone = selectedTone ?: return null
        val emoji = ToneData.getEmojiForTone(tone)
        return "$emoji $tone"
    }

    fun regenerate() {
        selectedTone?.let { tone ->
            onRegenerate?.invoke(tone)
        }
    }

    fun openCustomMode() {
        onCustomMode?.invoke()
        onStateChange?.invoke(Row2State.CUSTOM_MODE)
    }
}
