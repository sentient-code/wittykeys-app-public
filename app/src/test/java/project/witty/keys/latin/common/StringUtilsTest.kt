package project.witty.keys.latin.common

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for StringUtils - common string manipulation utilities.
 *
 * These tests verify string operations used throughout the keyboard:
 * - Capitalization
 * - Word detection
 * - String parsing
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class StringUtilsTest {

    /**
     * Test capitalizing first character.
     */
    @Test
    fun capitalizeFirstCodePoint_capitalizes() {
        val result = StringUtils.capitalizeFirstCodePoint("hello", java.util.Locale.ENGLISH)
        assertEquals("Hello", result)
    }

    /**
     * Test capitalizing empty string.
     */
    @Test
    fun capitalizeFirstCodePoint_emptyString_returnsEmpty() {
        val result = StringUtils.capitalizeFirstCodePoint("", java.util.Locale.ENGLISH)
        assertEquals("", result)
    }

    /**
     * Test capitalizing already capitalized string.
     */
    @Test
    fun capitalizeFirstCodePoint_alreadyCapitalized_noChange() {
        val result = StringUtils.capitalizeFirstCodePoint("Hello", java.util.Locale.ENGLISH)
        assertEquals("Hello", result)
    }

    /**
     * Test contains in array.
     */
    @Test
    fun containsInArray_withMatch_returnsTrue() {
        val array = arrayOf("hello", "world", "test")
        assertTrue(StringUtils.containsInArray("hello", array))
    }

    /**
     * Test contains in array with no match.
     */
    @Test
    fun containsInArray_noMatch_returnsFalse() {
        val array = arrayOf("hello", "world", "test")
        assertFalse(StringUtils.containsInArray("foo", array))
    }

    /**
     * Test newSingleCodePointString for ASCII.
     */
    @Test
    fun newSingleCodePointString_ascii_returnsString() {
        val result = StringUtils.newSingleCodePointString('a'.code)
        assertEquals("a", result)
    }

    /**
     * Test detecting whitespace.
     */
    @Test
    fun isWhitespace_space_returnsTrue() {
        assertTrue(Character.isWhitespace(' '))
    }

    /**
     * Test detecting non-whitespace.
     */
    @Test
    fun isWhitespace_letter_returnsFalse() {
        assertFalse(Character.isWhitespace('a'))
    }

    /**
     * Test null safe length check.
     */
    @Test
    fun nullSafeLength_withNull_returnsZero() {
        val result = StringUtils.codePointCount(null)
        assertEquals(0, result)
    }

    /**
     * Test code point count for ASCII.
     */
    @Test
    fun codePointCount_ascii_returnsCorrect() {
        val result = StringUtils.codePointCount("Hello")
        assertEquals(5, result)
    }

    /**
     * Test code point count for empty string.
     */
    @Test
    fun codePointCount_empty_returnsZero() {
        val result = StringUtils.codePointCount("")
        assertEquals(0, result)
    }
}
