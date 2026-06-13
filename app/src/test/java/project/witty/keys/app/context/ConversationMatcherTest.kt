package project.witty.keys.app.context

import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.Assert.*

class ConversationMatcherTest {

    private lateinit var matcher: ConversationMatcher

    @Before
    fun setup() {
        // Reset singleton to ensure clean state between tests
        val field = ConversationMatcher::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        matcher = ConversationMatcher.getInstance()
    }

    @After
    fun teardown() {
        val field = ConversationMatcher::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    // === NLS Registration ===

    @Test
    fun `registerNlsContact adds to database`() {
        matcher.registerNlsContact("com.whatsapp", "Priya")
        assertTrue(matcher.hasNlsData())
        assertTrue(matcher.getKnownContacts("com.whatsapp").contains("Priya"))
    }

    @Test
    fun `getKnownContacts returns empty for unknown package`() {
        assertTrue(matcher.getKnownContacts("com.unknown").isEmpty())
    }

    @Test
    fun `registerNlsContact multiple contacts same package`() {
        matcher.registerNlsContact("com.whatsapp", "Priya")
        matcher.registerNlsContact("com.whatsapp", "Boss")
        matcher.registerNlsContact("com.whatsapp", "Alex")
        val contacts = matcher.getKnownContacts("com.whatsapp")
        assertEquals(3, contacts.size)
        assertTrue(contacts.containsAll(listOf("Priya", "Boss", "Alex")))
    }

    @Test
    fun `registerNlsContact same contact different packages`() {
        matcher.registerNlsContact("com.whatsapp", "Priya")
        matcher.registerNlsContact("org.telegram.messenger", "Priya")
        assertTrue(matcher.getKnownContacts("com.whatsapp").contains("Priya"))
        assertTrue(matcher.getKnownContacts("org.telegram.messenger").contains("Priya"))
    }

    // === Confidence Scoring ===

    @Test
    fun `setActiveContact with exact NLS match scores 99 percent`() {
        matcher.registerNlsContact("com.whatsapp", "Priya")
        matcher.setCurrentEditorPackage("com.whatsapp")
        matcher.setActiveContact("com.whatsapp", "Priya", ConversationMatcher.MatchSource.ACCESSIBILITY)
        val contact = matcher.getActiveContact()
        assertNotNull(contact)
        assertEquals(0.99f, contact!!.confidence, 0.01f)
    }

    @Test
    fun `setActiveContact with no NLS data scores 80 percent`() {
        // No NLS contacts registered
        matcher.setActiveContact("com.whatsapp", "Priya", ConversationMatcher.MatchSource.ACCESSIBILITY)
        val contact = matcher.getActiveContact()
        assertNotNull(contact)
        assertEquals(0.80f, contact!!.confidence, 0.01f)
    }

    @Test
    fun `setActiveContactFromNotificationTap scores 95 percent`() {
        matcher.setActiveContactFromNotificationTap("com.whatsapp", "Priya", "com.whatsapp|Priya")
        val contact = matcher.getActiveContact()
        assertNotNull(contact)
        assertEquals(0.95f, contact!!.confidence, 0.01f)
    }

    @Test
    fun `setUserSelectedContact scores 100 percent`() {
        matcher.setUserSelectedContact("com.whatsapp", "Priya")
        val contact = matcher.getActiveContact()
        assertNotNull(contact)
        assertEquals(1.0f, contact!!.confidence, 0.01f)
    }

    // === Fuzzy Matching ===

    @Test
    fun `fuzzyMatchStatic exact match returns true`() {
        assertTrue(ConversationMatcher.fuzzyMatchStatic("Priya", "Priya"))
    }

    @Test
    fun `fuzzyMatchStatic completely different names dont match`() {
        assertFalse(ConversationMatcher.fuzzyMatchStatic("Priya", "Alex"))
    }

    @Test
    fun `fuzzyMatchStatic null inputs return false`() {
        assertFalse(ConversationMatcher.fuzzyMatchStatic(null, "Priya"))
        assertFalse(ConversationMatcher.fuzzyMatchStatic("Priya", null))
        assertFalse(ConversationMatcher.fuzzyMatchStatic(null, null))
    }

    @Test
    fun `fuzzyMatchStatic case insensitive`() {
        assertTrue(ConversationMatcher.fuzzyMatchStatic("priya", "PRIYA"))
    }

    @Test
    fun `setActiveContact with fuzzy NLS match scores 90 percent`() {
        // Register with emoji variant
        matcher.registerNlsContact("com.whatsapp", "Mom \u2764\uFE0F")
        matcher.setCurrentEditorPackage("com.whatsapp")
        matcher.setActiveContact("com.whatsapp", "Mom", ConversationMatcher.MatchSource.ACCESSIBILITY)
        val contact = matcher.getActiveContact()
        assertNotNull(contact)
        // Should be fuzzy match (0.90) since "Mom" fuzzy matches "Mom ❤️"
        assertTrue(contact!!.confidence >= 0.90f)
    }

    // === Contact Match Fields ===

    @Test
    fun `ContactMatch has correct conversation key format`() {
        matcher.setActiveContact("com.whatsapp", "Priya", ConversationMatcher.MatchSource.ACCESSIBILITY)
        val contact = matcher.getActiveContact()
        assertNotNull(contact)
        assertEquals("com.whatsapp|Priya", contact!!.conversationKey)
    }

    @Test
    fun `ContactMatch preserves contact name and package`() {
        matcher.setActiveContact("com.whatsapp", "Priya", ConversationMatcher.MatchSource.ACCESSIBILITY)
        val contact = matcher.getActiveContact()
        assertNotNull(contact)
        assertEquals("Priya", contact!!.contactName)
        assertEquals("com.whatsapp", contact.packageName)
    }

    // === Stale Contact Expiration ===

    @Test
    fun `getActiveContact returns contact within 5 minutes`() {
        matcher.setActiveContact("com.whatsapp", "Priya", ConversationMatcher.MatchSource.ACCESSIBILITY)
        assertNotNull(matcher.getActiveContact())
    }

    @Test
    fun `getActiveContact returns null after 5 minutes`() {
        matcher.setActiveContact("com.whatsapp", "Priya", ConversationMatcher.MatchSource.ACCESSIBILITY)

        // Use reflection to set the timestamp to 6 minutes ago
        val activeContactField = ConversationMatcher::class.java.getDeclaredField("activeContact")
        activeContactField.isAccessible = true
        val contactMatch = activeContactField.get(matcher) as? ConversationMatcher.ContactMatch
        if (contactMatch != null) {
            val timestampField = ConversationMatcher.ContactMatch::class.java.getDeclaredField("timestamp")
            timestampField.isAccessible = true
            timestampField.setLong(contactMatch, System.currentTimeMillis() - 6 * 60 * 1000)
        }

        assertNull(matcher.getActiveContact())
    }

    // === Clear / Reset ===

    @Test
    fun `clearActiveContact removes active contact`() {
        matcher.setActiveContact("com.whatsapp", "Priya", ConversationMatcher.MatchSource.ACCESSIBILITY)
        assertNotNull(matcher.getActiveContact())
        matcher.clearActiveContact()
        assertNull(matcher.getActiveContact())
    }

    // === Editor Package ===

    @Test
    fun `getCurrentEditorPackage returns set package`() {
        matcher.setCurrentEditorPackage("com.whatsapp")
        assertEquals("com.whatsapp", matcher.getCurrentEditorPackage())
    }

    @Test
    fun `getCurrentEditorPackage returns null initially`() {
        assertNull(matcher.getCurrentEditorPackage())
    }
}
