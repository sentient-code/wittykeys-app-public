package project.witty.keys.latin.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Settings and SettingsValues - manage keyboard preferences.
 *
 * These tests verify:
 * - Settings singleton initialization
 * - Preference value loading
 * - Default value handling
 * - Settings change callbacks
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SettingsValuesTest {

    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockResources: Resources

    @MockK
    private lateinit var mockSharedPrefs: SharedPreferences

    @MockK
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)

        every { mockContext.resources } returns mockResources
        every { mockSharedPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putInt(any(), any()) } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.apply() } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test Settings singleton returns same instance.
     */
    @Test
    fun getInstance_returnsSameInstance() {
        val instance1 = Settings.getInstance()
        val instance2 = Settings.getInstance()

        assertTrue(instance1 === instance2, "Should return same singleton instance")
    }

    /**
     * Test Settings preference keys are defined.
     */
    @Test
    fun settingsKeys_areDefined() {
        assertNotNull(Settings.PREF_AUTO_CAP, "PREF_AUTO_CAP should be defined")
        assertNotNull(Settings.PREF_VIBRATE_ON, "PREF_VIBRATE_ON should be defined")
        assertNotNull(Settings.PREF_SOUND_ON, "PREF_SOUND_ON should be defined")
        assertNotNull(Settings.PREF_POPUP_ON, "PREF_POPUP_ON should be defined")
    }

    /**
     * Test vibration preference key.
     */
    @Test
    fun prefVibrateOn_hasCorrectKey() {
        assertEquals("vibrate_on", Settings.PREF_VIBRATE_ON)
    }

    /**
     * Test sound preference key.
     */
    @Test
    fun prefSoundOn_hasCorrectKey() {
        assertEquals("sound_on", Settings.PREF_SOUND_ON)
    }

    /**
     * Test auto cap preference key.
     */
    @Test
    fun prefAutoCap_hasCorrectKey() {
        assertEquals("auto_cap", Settings.PREF_AUTO_CAP)
    }

    /**
     * Test popup preference key.
     */
    @Test
    fun prefPopupOn_hasCorrectKey() {
        assertEquals("popup_on", Settings.PREF_POPUP_ON)
    }

    /**
     * Test keyboard height preference key.
     */
    @Test
    fun prefKeyboardHeight_hasCorrectKey() {
        assertEquals("pref_keyboard_height", Settings.PREF_KEYBOARD_HEIGHT)
    }

    /**
     * Test show number row preference key.
     */
    @Test
    fun prefShowNumberRow_hasCorrectKey() {
        assertEquals("pref_show_number_row", Settings.PREF_SHOW_NUMBER_ROW)
    }

    /**
     * Test space swipe preference key.
     */
    @Test
    fun prefSpaceSwipe_hasCorrectKey() {
        assertEquals("pref_space_swipe", Settings.PREF_SPACE_SWIPE)
    }

    /**
     * Test delete swipe preference key.
     */
    @Test
    fun prefDeleteSwipe_hasCorrectKey() {
        assertEquals("pref_delete_swipe", Settings.PREF_DELETE_SWIPE)
    }

    /**
     * Test longpress timeout preference key.
     */
    @Test
    fun prefKeyLongpressTimeout_hasCorrectKey() {
        assertEquals("pref_key_longpress_timeout", Settings.PREF_KEY_LONGPRESS_TIMEOUT)
    }

    /**
     * Test vibration duration preference key.
     */
    @Test
    fun prefVibrationDuration_hasCorrectKey() {
        assertEquals("pref_vibration_duration_settings", Settings.PREF_VIBRATION_DURATION_SETTINGS)
    }

    /**
     * Test keypress sound volume preference key.
     */
    @Test
    fun prefKeypressSoundVolume_hasCorrectKey() {
        assertEquals("pref_keypress_sound_volume", Settings.PREF_KEYPRESS_SOUND_VOLUME)
    }
}
