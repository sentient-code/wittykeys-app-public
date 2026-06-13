package project.witty.keys.app.helpers

import org.junit.Test
import org.junit.Assert.*
import project.witty.keys.app.context.AppDetector
import project.witty.keys.app.context.MediaMessageNormalizer
import project.witty.keys.app.context.MessageDebouncer

/**
 * Tests the components used in NLS message parsing.
 * Note: WittyKeysNotificationListenerService.onNotificationPosted() requires
 * a real NLS service context — full parsing tests deferred to device test (6.13).
 * Here we test the static/extractable components.
 */
class NlsMessageParsingTest {

    // === AppDetector — messaging app recognition ===

    @Test
    fun `AppDetector recognizes WhatsApp as MESSAGING`() {
        assertEquals(AppDetector.AppCategory.MESSAGING, AppDetector.categorize("com.whatsapp"))
    }

    @Test
    fun `AppDetector recognizes Telegram as MESSAGING`() {
        assertEquals(AppDetector.AppCategory.MESSAGING, AppDetector.categorize("org.telegram.messenger"))
    }

    @Test
    fun `AppDetector recognizes Signal as MESSAGING`() {
        assertEquals(AppDetector.AppCategory.MESSAGING, AppDetector.categorize("org.thoughtcrime.securesms"))
    }

    @Test
    fun `AppDetector recognizes Instagram as SOCIAL`() {
        assertEquals(AppDetector.AppCategory.SOCIAL, AppDetector.categorize("com.instagram.android"))
    }

    @Test
    fun `AppDetector recognizes Tinder as DATING`() {
        assertEquals(AppDetector.AppCategory.DATING, AppDetector.categorize("com.tinder"))
    }

    @Test
    fun `AppDetector recognizes Gmail as EMAIL`() {
        assertEquals(AppDetector.AppCategory.EMAIL, AppDetector.categorize("com.google.android.gm"))
    }

    @Test
    fun `AppDetector returns OTHER for unknown app`() {
        assertEquals(AppDetector.AppCategory.OTHER, AppDetector.categorize("com.random.calculator"))
    }

    @Test
    fun `AppDetector rejects null package`() {
        assertEquals(AppDetector.AppCategory.OTHER, AppDetector.categorize(null))
    }

    // === isContextualApp ===

    @Test
    fun `isContextualApp returns true for messaging apps`() {
        assertTrue(AppDetector.isContextualApp("com.whatsapp"))
    }

    @Test
    fun `isContextualApp returns true for social apps`() {
        assertTrue(AppDetector.isContextualApp("com.instagram.android"))
    }

    @Test
    fun `isContextualApp returns true for dating apps`() {
        assertTrue(AppDetector.isContextualApp("com.tinder"))
    }

    @Test
    fun `isContextualApp returns false for OTHER apps`() {
        assertFalse(AppDetector.isContextualApp("com.random.calculator"))
    }

    // === NlsMessage construction ===

    @Test
    fun `NlsMessage preserves all fields`() {
        val now = System.currentTimeMillis()
        val msg = MessageDebouncer.NlsMessage("Priya", "Hey there!", now, false)
        assertEquals("Priya", msg.sender)
        assertEquals("Hey there!", msg.text)
        assertEquals(now, msg.timestamp)
        assertFalse(msg.isGroup)
    }

    @Test
    fun `NlsMessage handles group flag`() {
        val msg = MessageDebouncer.NlsMessage("Alex", "Group msg", 0L, true)
        assertTrue(msg.isGroup)
    }

    @Test
    fun `image notification with mime type becomes photo placeholder`() {
        assertEquals(
            "Photo received",
            MediaMessageNormalizer.normalizeIncomingText(null, "image/jpeg")
        )
    }

    @Test
    fun `image notification with caption keeps caption without claiming image access`() {
        assertEquals(
            "Photo received: Check this design",
            MediaMessageNormalizer.normalizeIncomingText("Check this design", "image/png")
        )
    }

    @Test
    fun `common app media text becomes photo placeholder`() {
        assertEquals(
            "Photo received",
            MediaMessageNormalizer.normalizeIncomingText("📷 Photo", null)
        )
        assertEquals(
            "Photo received",
            MediaMessageNormalizer.normalizeIncomingText("sent you a photo", null)
        )
    }

    @Test
    fun `media placeholders use safe local replies`() {
        val replies = MediaMessageNormalizer.safeRepliesForMediaPlaceholder("Photo received")

        assertEquals(listOf(
            "I'll check and reply",
            "Can you share more details?",
            "Got it, one sec"
        ), replies)
    }

    @Test
    fun `non photo attachment mime types become honest placeholders`() {
        assertEquals("Video received", MediaMessageNormalizer.normalizeIncomingText(null, "video/mp4"))
        assertEquals("Audio received", MediaMessageNormalizer.normalizeIncomingText(null, "audio/mpeg"))
        assertEquals("Document received", MediaMessageNormalizer.normalizeIncomingText(null, "application/pdf"))
        assertEquals("Contact shared", MediaMessageNormalizer.normalizeIncomingText(null, "text/vcard"))
        assertEquals("Attachment received", MediaMessageNormalizer.normalizeIncomingText(null, "application/octet-stream"))
    }

    @Test
    fun `non photo attachment captions are preserved after placeholder`() {
        assertEquals(
            "Video received: Watch this later",
            MediaMessageNormalizer.normalizeIncomingText("Watch this later", "video/mp4")
        )
        assertEquals(
            "Document received: invoice May",
            MediaMessageNormalizer.normalizeIncomingText("invoice May", "application/pdf")
        )
    }

    @Test
    fun `common app attachment text becomes matching placeholder`() {
        assertEquals("Video received", MediaMessageNormalizer.normalizeIncomingText("🎥 Video", null))
        assertEquals("GIF received", MediaMessageNormalizer.normalizeIncomingText("GIF", null))
        assertEquals("Sticker received", MediaMessageNormalizer.normalizeIncomingText("Sticker", null))
        assertEquals("Voice message received", MediaMessageNormalizer.normalizeIncomingText("🎤 Voice message", null))
        assertEquals("Location shared", MediaMessageNormalizer.normalizeIncomingText("Location", null))
        assertEquals("Contact shared", MediaMessageNormalizer.normalizeIncomingText("Contact card", null))
        assertEquals("Document received", MediaMessageNormalizer.normalizeIncomingText("sent you a document", null))
    }

    @Test
    fun `all attachment placeholders use safe local reply path`() {
        listOf(
            "Photo received",
            "Video received",
            "GIF received",
            "Sticker received",
            "Voice message received",
            "Audio received",
            "Document received",
            "Contact shared",
            "Location shared",
            "Attachment received",
            "Video received: Watch this later"
        ).forEach { placeholder ->
            assertTrue("$placeholder should be a media placeholder",
                MediaMessageNormalizer.isMediaPlaceholderText(placeholder))
        }

        assertFalse(MediaMessageNormalizer.isMediaPlaceholderText("Are you coming today?"))
    }
}
