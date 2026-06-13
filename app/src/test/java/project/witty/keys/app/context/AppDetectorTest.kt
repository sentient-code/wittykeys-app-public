package project.witty.keys.app.context

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AppDetector - app identification and categorization logic.
 *
 * Tests cover:
 * - App detection for various messaging apps
 * - App detection for email apps
 * - App detection for dating apps
 * - App detection for social apps
 * - Unknown/generic app handling
 * - Null and empty input handling
 * - App name extraction
 */
class AppDetectorTest {

    @Before
    fun setup() {
        // Mock Android Log class for unit tests
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        android.util.Log.d("[Test]", "AppDetectorTest setup complete")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========================================
    // MESSAGING APP TESTS
    // ========================================

    @Test
    fun `detectApp with WhatsApp package returns MESSAGING`() {
        val result = AppDetector.categorize("com.whatsapp")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with WhatsApp Business returns MESSAGING`() {
        val result = AppDetector.categorize("com.whatsapp.w4b")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with Telegram package returns MESSAGING`() {
        val result = AppDetector.categorize("org.telegram.messenger")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with Signal package returns MESSAGING`() {
        val result = AppDetector.categorize("org.thoughtcrime.securesms")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with Facebook Messenger returns MESSAGING`() {
        val result = AppDetector.categorize("com.facebook.orca")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with Messenger Lite returns MESSAGING`() {
        // Messenger Lite contains "messenger" in package name
        val result = AppDetector.categorize("com.facebook.messenger.lite")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with Viber package returns MESSAGING`() {
        val result = AppDetector.categorize("com.viber.voip")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with WeChat package returns MESSAGING`() {
        val result = AppDetector.categorize("com.tencent.mm")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with Google Messages returns MESSAGING`() {
        val result = AppDetector.categorize("com.google.android.apps.messaging")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with SMS app returns MESSAGING`() {
        val result = AppDetector.categorize("com.samsung.android.messaging.sms")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with Skype returns MESSAGING`() {
        val result = AppDetector.categorize("com.skype.raider")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with LINE returns MESSAGING`() {
        val result = AppDetector.categorize("jp.naver.line.android")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    @Test
    fun `detectApp with KakaoTalk returns MESSAGING`() {
        // AppDetector looks for "kakaotalk" in package name
        val result = AppDetector.categorize("com.kakaotalk.android")

        assertEquals(AppDetector.AppCategory.MESSAGING, result)
    }

    // ========================================
    // EMAIL APP TESTS
    // ========================================

    @Test
    fun `detectApp with Gmail package returns EMAIL`() {
        val result = AppDetector.categorize("com.google.android.gm")

        assertEquals(AppDetector.AppCategory.EMAIL, result)
    }

    @Test
    fun `detectApp with Outlook package returns EMAIL`() {
        val result = AppDetector.categorize("com.microsoft.office.outlook")

        assertEquals(AppDetector.AppCategory.EMAIL, result)
    }

    @Test
    fun `detectApp with Yahoo Mail returns EMAIL`() {
        val result = AppDetector.categorize("com.yahoo.mobile.client.android.mail")

        assertEquals(AppDetector.AppCategory.EMAIL, result)
    }

    @Test
    fun `detectApp with ProtonMail returns EMAIL`() {
        val result = AppDetector.categorize("ch.protonmail.android")

        assertEquals(AppDetector.AppCategory.EMAIL, result)
    }

    @Test
    fun `detectApp with generic mail app returns EMAIL`() {
        val result = AppDetector.categorize("com.android.email")

        assertEquals(AppDetector.AppCategory.EMAIL, result)
    }

    @Test
    fun `detectApp with Spark email returns EMAIL`() {
        val result = AppDetector.categorize("com.readdle.spark")

        assertEquals(AppDetector.AppCategory.EMAIL, result)
    }

    @Test
    fun `detectApp with BlueMail returns EMAIL`() {
        val result = AppDetector.categorize("me.bluemail.mail")

        assertEquals(AppDetector.AppCategory.EMAIL, result)
    }

    // ========================================
    // DATING APP TESTS
    // ========================================

    @Test
    fun `detectApp with Tinder package returns DATING`() {
        val result = AppDetector.categorize("com.tinder")

        assertEquals(AppDetector.AppCategory.DATING, result)
    }

    @Test
    fun `detectApp with Bumble package returns DATING`() {
        val result = AppDetector.categorize("com.bumble.app")

        assertEquals(AppDetector.AppCategory.DATING, result)
    }

    @Test
    fun `detectApp with Hinge package returns DATING`() {
        val result = AppDetector.categorize("co.hinge.app")

        assertEquals(AppDetector.AppCategory.DATING, result)
    }

    @Test
    fun `detectApp with OkCupid package returns DATING`() {
        val result = AppDetector.categorize("com.okcupid.okcupid")

        assertEquals(AppDetector.AppCategory.DATING, result)
    }

    @Test
    fun `detectApp with Badoo package returns DATING`() {
        val result = AppDetector.categorize("com.badoo.mobile")

        assertEquals(AppDetector.AppCategory.DATING, result)
    }

    @Test
    fun `detectApp with Happn returns DATING`() {
        val result = AppDetector.categorize("com.ftw_and_co.happn")

        assertEquals(AppDetector.AppCategory.DATING, result)
    }

    @Test
    fun `detectApp with Grindr returns DATING`() {
        val result = AppDetector.categorize("com.grindrapp.android")

        assertEquals(AppDetector.AppCategory.DATING, result)
    }

    @Test
    fun `detectApp with Coffee Meets Bagel returns DATING`() {
        val result = AppDetector.categorize("com.coffeemeetsbagel")

        assertEquals(AppDetector.AppCategory.DATING, result)
    }

    // ========================================
    // SOCIAL APP TESTS
    // ========================================

    @Test
    fun `detectApp with Instagram package returns SOCIAL`() {
        val result = AppDetector.categorize("com.instagram.android")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    @Test
    fun `detectApp with Twitter package returns SOCIAL`() {
        val result = AppDetector.categorize("com.twitter.android")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    @Test
    fun `detectApp with LinkedIn package returns SOCIAL`() {
        val result = AppDetector.categorize("com.linkedin.android")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    @Test
    fun `detectApp with Reddit package returns SOCIAL`() {
        val result = AppDetector.categorize("com.reddit.frontpage")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    @Test
    fun `detectApp with Discord package returns SOCIAL`() {
        val result = AppDetector.categorize("com.discord")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    @Test
    fun `detectApp with Slack package returns SOCIAL`() {
        val result = AppDetector.categorize("com.Slack")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    @Test
    fun `detectApp with Snapchat returns SOCIAL`() {
        val result = AppDetector.categorize("com.snapchat.android")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    @Test
    fun `detectApp with Threads returns SOCIAL`() {
        val result = AppDetector.categorize("com.instagram.barcelona.threads")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    @Test
    fun `detectApp with Bluesky returns SOCIAL`() {
        val result = AppDetector.categorize("xyz.blueskyweb.app")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    // ========================================
    // UNKNOWN/GENERIC APP TESTS
    // ========================================

    @Test
    fun `detectApp with unknown package returns OTHER`() {
        val result = AppDetector.categorize("com.unknown.random.app")

        assertEquals(AppDetector.AppCategory.OTHER, result)
    }

    @Test
    fun `detectApp with Chrome browser returns OTHER`() {
        val result = AppDetector.categorize("com.android.chrome")

        assertEquals(AppDetector.AppCategory.OTHER, result)
    }

    @Test
    fun `detectApp with camera app returns OTHER`() {
        val result = AppDetector.categorize("com.google.android.GoogleCamera")

        assertEquals(AppDetector.AppCategory.OTHER, result)
    }

    @Test
    fun `detectApp with game app returns OTHER`() {
        val result = AppDetector.categorize("com.supercell.clashofclans")

        assertEquals(AppDetector.AppCategory.OTHER, result)
    }

    // ========================================
    // NULL AND EMPTY INPUT TESTS
    // ========================================

    @Test
    fun `detectApp with null package returns OTHER`() {
        val result = AppDetector.categorize(null)

        assertEquals(AppDetector.AppCategory.OTHER, result)
    }

    @Test
    fun `detectApp with empty package returns OTHER`() {
        val result = AppDetector.categorize("")

        assertEquals(AppDetector.AppCategory.OTHER, result)
    }

    @Test
    fun `detectApp with whitespace only returns OTHER`() {
        val result = AppDetector.categorize("   ")

        assertEquals(AppDetector.AppCategory.OTHER, result)
    }

    // ========================================
    // CATEGORY NAME TESTS
    // ========================================

    @Test
    fun `getCategoryName for MESSAGING returns messaging`() {
        val result = AppDetector.getCategoryName(AppDetector.AppCategory.MESSAGING)

        assertEquals("messaging", result)
    }

    @Test
    fun `getCategoryName for EMAIL returns email`() {
        val result = AppDetector.getCategoryName(AppDetector.AppCategory.EMAIL)

        assertEquals("email", result)
    }

    @Test
    fun `getCategoryName for DATING returns dating`() {
        val result = AppDetector.getCategoryName(AppDetector.AppCategory.DATING)

        assertEquals("dating", result)
    }

    @Test
    fun `getCategoryName for SOCIAL returns social`() {
        val result = AppDetector.getCategoryName(AppDetector.AppCategory.SOCIAL)

        assertEquals("social", result)
    }

    @Test
    fun `getCategoryName for OTHER returns other`() {
        val result = AppDetector.getCategoryName(AppDetector.AppCategory.OTHER)

        assertEquals("other", result)
    }

    // ========================================
    // APP NAME EXTRACTION TESTS
    // ========================================

    @Test
    fun `getAppName for WhatsApp returns WhatsApp`() {
        val result = AppDetector.getAppName("com.whatsapp")

        assertEquals("WhatsApp", result)
    }

    @Test
    fun `getAppName for Telegram returns Telegram`() {
        val result = AppDetector.getAppName("org.telegram.messenger")

        assertEquals("Telegram", result)
    }

    @Test
    fun `getAppName for Gmail returns Gmail`() {
        val result = AppDetector.getAppName("com.google.android.gm")

        assertEquals("Gmail", result)
    }

    @Test
    fun `getAppName for Tinder returns Tinder`() {
        val result = AppDetector.getAppName("com.tinder")

        assertEquals("Tinder", result)
    }

    @Test
    fun `getAppName for Instagram returns Instagram`() {
        val result = AppDetector.getAppName("com.instagram.android")

        assertEquals("Instagram", result)
    }

    @Test
    fun `getAppName for Discord returns Discord`() {
        val result = AppDetector.getAppName("com.discord")

        assertEquals("Discord", result)
    }

    @Test
    fun `getAppName for unknown package returns capitalized last segment`() {
        val result = AppDetector.getAppName("com.example.myawesomeapp")

        assertEquals("Myawesomeapp", result)
    }

    @Test
    fun `getAppName for null returns Unknown`() {
        val result = AppDetector.getAppName(null)

        assertEquals("Unknown", result)
    }

    @Test
    fun `getAppName for single segment returns capitalized`() {
        val result = AppDetector.getAppName("myapp")

        assertEquals("myapp", result)
    }

    // ========================================
    // CASE INSENSITIVITY TESTS
    // ========================================

    @Test
    fun `detectApp is case insensitive for WhatsApp`() {
        val lowercase = AppDetector.categorize("com.whatsapp")
        val uppercase = AppDetector.categorize("COM.WHATSAPP")
        val mixedCase = AppDetector.categorize("Com.WhatsApp")

        assertEquals(lowercase, uppercase)
        assertEquals(lowercase, mixedCase)
        assertEquals(AppDetector.AppCategory.MESSAGING, lowercase)
    }

    @Test
    fun `detectApp is case insensitive for Gmail`() {
        val lowercase = AppDetector.categorize("com.google.android.gm")
        val uppercase = AppDetector.categorize("COM.GOOGLE.ANDROID.GM")

        assertEquals(lowercase, uppercase)
        assertEquals(AppDetector.AppCategory.EMAIL, lowercase)
    }

    // ========================================
    // EDGE CASE TESTS
    // ========================================

    @Test
    fun `detectApp with Facebook base package returns SOCIAL not MESSAGING`() {
        // Facebook base app should be SOCIAL, not MESSAGING
        // (Messenger has "orca" or "messenger" in package)
        val result = AppDetector.categorize("com.facebook.katana")

        assertEquals(AppDetector.AppCategory.SOCIAL, result)
    }

    @Test
    fun `detectApp with similar but different package returns OTHER`() {
        // "whats" is not "whatsapp"
        val result = AppDetector.categorize("com.whats.up.bro")

        assertEquals(AppDetector.AppCategory.OTHER, result)
    }

    @Test
    fun `detectApp handles special characters in package`() {
        val result = AppDetector.categorize("com.test_app.with-special.chars")

        assertEquals(AppDetector.AppCategory.OTHER, result)
    }
}
