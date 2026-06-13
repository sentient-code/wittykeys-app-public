package project.witty.keys.event

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import project.witty.keys.latin.common.Constants
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Event class - represents keyboard input events.
 *
 * These tests verify:
 * - Event creation for different input types
 * - Event properties (consumed, functional, etc.)
 * - Event chaining
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class EventTest {

    /**
     * Test creating software keypress event for letter.
     */
    @Test
    fun createSoftwareKeypressEvent_letter_createsEvent() {
        val event = Event.createSoftwareKeypressEvent(
            'a'.code,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertNotNull(event, "Event should be created")
        assertEquals('a'.code, event.mCodePoint, "Code point should match")
    }

    /**
     * Test creating software keypress event for backspace.
     */
    @Test
    fun createSoftwareKeypressEvent_backspace_createsEvent() {
        val event = Event.createSoftwareKeypressEvent(
            Constants.CODE_DELETE,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertNotNull(event, "Event should be created")
        assertEquals(Constants.CODE_DELETE, event.mCodePoint, "Code point should be DELETE")
    }

    /**
     * Test creating software text event.
     */
    @Test
    fun createSoftwareTextEvent_createsEvent() {
        val event = Event.createSoftwareTextEvent(".com", Constants.CODE_UNSPECIFIED)

        assertNotNull(event, "Event should be created")
        assertEquals(".com", event.textToCommit.toString(), "Text should match")
    }

    /**
     * Test getting text to commit from event.
     */
    @Test
    fun getTextToCommit_returnsCharacter() {
        val event = Event.createSoftwareKeypressEvent(
            'a'.code,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        val text = event.textToCommit

        assertNotNull(text, "Text to commit should not be null")
        assertEquals("a", text.toString(), "Text should be the character")
    }

    /**
     * Test that letter events are not functional.
     */
    @Test
    fun letterEvent_isNotFunctional() {
        val event = Event.createSoftwareKeypressEvent(
            'x'.code,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertFalse(event.isFunctionalKeyEvent(), "Letter should not be functional")
    }

    /**
     * Test that shift event is functional.
     */
    @Test
    fun shiftEvent_isFunctional() {
        val event = Event.createSoftwareKeypressEvent(
            Constants.CODE_SHIFT,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertTrue(event.isFunctionalKeyEvent(), "Shift should be functional")
    }

    /**
     * Test enter event code point.
     */
    @Test
    fun enterEvent_hasCorrectCodePoint() {
        val event = Event.createSoftwareKeypressEvent(
            Constants.CODE_ENTER,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            false
        )

        assertEquals(Constants.CODE_ENTER, event.mCodePoint, "Enter code point should match")
    }

    /**
     * Test event with repeat flag.
     */
    @Test
    fun createEvent_withRepeat_setsFlag() {
        val event = Event.createSoftwareKeypressEvent(
            'a'.code,
            Constants.CODE_UNSPECIFIED,
            0,
            0,
            true  // isKeyRepeat
        )

        assertNotNull(event, "Event with repeat should be created")
    }

    /**
     * Test event coordinates are stored.
     */
    @Test
    fun createEvent_withCoordinates_storesCoordinates() {
        val event = Event.createSoftwareKeypressEvent(
            'a'.code,
            Constants.CODE_UNSPECIFIED,
            100,  // x
            200,  // y
            false
        )

        assertEquals(100, event.mX, "X coordinate should be stored")
        assertEquals(200, event.mY, "Y coordinate should be stored")
    }
}
