package project.witty.keys.latin

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import project.witty.keys.event.Event
import project.witty.keys.latin.common.Constants
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Event class used in InputLogic code input handling.
 *
 * These tests verify:
 * - Event creation for different key types
 * - Event properties (code points, coordinates)
 * - Event repeat flag handling
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class InputLogicCodeInputTest {

    /**
     * Test creating event for character key.
     */
    @Test
    fun createEvent_characterKey_hasCorrectCodePoint() {
        val event = Event.createSoftwareKeypressEvent(
            'a'.code,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertEquals('a'.code, event.mCodePoint, "Code point should match")
        assertFalse(event.isFunctionalKeyEvent(), "Letter should not be functional")
    }

    /**
     * Test creating event for backspace key.
     */
    @Test
    fun createEvent_backspace_hasDeleteCode() {
        val event = Event.createSoftwareKeypressEvent(
            Constants.CODE_DELETE,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertEquals(Constants.CODE_DELETE, event.mCodePoint, "Should have DELETE code")
    }

    /**
     * Test creating event for enter key.
     */
    @Test
    fun createEvent_enter_hasEnterCode() {
        val event = Event.createSoftwareKeypressEvent(
            Constants.CODE_ENTER,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertEquals(Constants.CODE_ENTER, event.mCodePoint, "Should have ENTER code")
    }

    /**
     * Test creating event for shift key.
     */
    @Test
    fun createEvent_shift_isFunctional() {
        val event = Event.createSoftwareKeypressEvent(
            Constants.CODE_SHIFT,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        // CODE_SHIFT = -1 = NOT_A_CODE_POINT, so it's functional
        assertTrue(event.isFunctionalKeyEvent(), "Shift should be functional")
    }

    /**
     * Test creating event for space key.
     */
    @Test
    fun createEvent_space_hasSpaceCode() {
        val event = Event.createSoftwareKeypressEvent(
            Constants.CODE_SPACE,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertEquals(Constants.CODE_SPACE, event.mCodePoint, "Should have SPACE code")
    }

    /**
     * Test creating text event.
     */
    @Test
    fun createTextEvent_hasCorrectText() {
        val event = Event.createSoftwareTextEvent(".com", Constants.CODE_UNSPECIFIED)

        assertEquals(".com", event.textToCommit.toString(), "Text should match")
    }

    /**
     * Test key repeat flag.
     */
    @Test
    fun createEvent_withRepeat_isKeyRepeat() {
        val event = Event.createSoftwareKeypressEvent(
            'a'.code,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            true
        )

        assertTrue(event.isKeyRepeat, "Should be key repeat")
    }

    /**
     * Test key without repeat flag.
     */
    @Test
    fun createEvent_withoutRepeat_isNotKeyRepeat() {
        val event = Event.createSoftwareKeypressEvent(
            'a'.code,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertFalse(event.isKeyRepeat, "Should not be key repeat")
    }

    /**
     * Test numeric key.
     */
    @Test
    fun createEvent_numericKey_hasCorrectCodePoint() {
        val event = Event.createSoftwareKeypressEvent(
            '5'.code,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertEquals('5'.code, event.mCodePoint, "Should have number code point")
        assertFalse(event.isFunctionalKeyEvent(), "Number should not be functional")
    }

    /**
     * Test symbol key.
     */
    @Test
    fun createEvent_symbolKey_hasCorrectCodePoint() {
        val event = Event.createSoftwareKeypressEvent(
            '@'.code,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertEquals('@'.code, event.mCodePoint, "Should have symbol code point")
        assertFalse(event.isFunctionalKeyEvent(), "Symbol should not be functional")
    }

    /**
     * Test event coordinates.
     */
    @Test
    fun createEvent_withCoordinates_storesCoordinates() {
        val event = Event.createSoftwareKeypressEvent(
            'a'.code,
            Constants.CODE_UNSPECIFIED,
            150,
            250,
            false
        )

        assertEquals(150, event.mX, "X should match")
        assertEquals(250, event.mY, "Y should match")
    }
}
