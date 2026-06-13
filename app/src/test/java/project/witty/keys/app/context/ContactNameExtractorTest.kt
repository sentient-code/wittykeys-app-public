package project.witty.keys.app.context

import org.junit.Test
import org.junit.Assert.*

class ContactNameExtractorTest {

    // === Known Header IDs ===

    @Test
    fun `hasKnownHeaderId returns true for WhatsApp`() {
        assertTrue(ContactNameExtractor.hasKnownHeaderId("com.whatsapp"))
    }

    @Test
    fun `hasKnownHeaderId returns true for Telegram`() {
        assertTrue(ContactNameExtractor.hasKnownHeaderId("org.telegram.messenger"))
    }

    @Test
    fun `hasKnownHeaderId returns true for Instagram`() {
        assertTrue(ContactNameExtractor.hasKnownHeaderId("com.instagram.android"))
    }

    @Test
    fun `hasKnownHeaderId returns false for unknown package`() {
        assertFalse(ContactNameExtractor.hasKnownHeaderId("com.unknown.app"))
    }

    @Test
    fun `hasKnownHeaderId returns false for empty string`() {
        assertFalse(ContactNameExtractor.hasKnownHeaderId(""))
    }

    // extractFromTree requires AccessibilityNodeInfo — deferred to device test (6.13)
}
