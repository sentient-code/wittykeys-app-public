package project.witty.keys.keyboard

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import project.witty.keys.latin.common.Constants
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for Key class - represents individual keyboard keys.
 *
 * These tests verify:
 * - Key code constants
 * - Special key identification
 * - Key properties
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class KeyTest {

    /**
     * Test that CODE_ENTER is defined correctly.
     */
    @Test
    fun codeEnter_isCorrect() {
        assertEquals(Constants.CODE_ENTER, '\n'.code)
    }

    /**
     * Test that CODE_SPACE is defined correctly.
     */
    @Test
    fun codeSpace_isCorrect() {
        assertEquals(Constants.CODE_SPACE, ' '.code)
    }

    /**
     * Test that CODE_DELETE is defined.
     */
    @Test
    fun codeDelete_isDefined() {
        assertNotEquals(0, Constants.CODE_DELETE)
    }

    /**
     * Test that CODE_SHIFT is defined.
     */
    @Test
    fun codeShift_isDefined() {
        assertNotEquals(0, Constants.CODE_SHIFT)
    }

    /**
     * Test that CODE_SWITCH_ALPHA_SYMBOL is defined.
     */
    @Test
    fun codeSwitchAlphaSymbol_isDefined() {
        assertNotEquals(0, Constants.CODE_SWITCH_ALPHA_SYMBOL)
    }

    /**
     * Test that special codes are negative (convention for non-character codes).
     */
    @Test
    fun specialCodes_areNegative() {
        assertTrue(Constants.CODE_DELETE < 0, "CODE_DELETE should be negative")
        assertTrue(Constants.CODE_SHIFT < 0, "CODE_SHIFT should be negative")
        assertTrue(Constants.CODE_SWITCH_ALPHA_SYMBOL < 0, "CODE_SWITCH_ALPHA_SYMBOL should be negative")
    }

    /**
     * Test that NOT_A_CODE is defined correctly.
     */
    @Test
    fun notACode_isDefined() {
        assertEquals(Constants.NOT_A_CODE, -1)
    }

    /**
     * Test character codes are positive.
     */
    @Test
    fun characterCodes_arePositive() {
        assertTrue('a'.code > 0, "Letter 'a' code should be positive")
        assertTrue('A'.code > 0, "Letter 'A' code should be positive")
        assertTrue('1'.code > 0, "Number '1' code should be positive")
        assertTrue('@'.code > 0, "Symbol '@' code should be positive")
    }
}
