package project.witty.keys.app

import android.content.Context
import android.util.Log
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import project.witty.keys.app.helpers.ScreenReaderAccessibility
import project.witty.keys.keyboard.AccessibilityUtils

/**
 * Unit tests for PermissionDisclosureDialog - Play Store compliant accessibility permission dialog.
 *
 * Tests cover:
 * - Static isAccessibilityEnabled check
 * - Show method behavior when permission is already granted
 * - Show method behavior when permission is not granted
 * - showFromKeyboard fallback behavior
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PermissionDisclosureDialogTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)

        // Mock AccessibilityUtils
        mockkStatic(AccessibilityUtils::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========================================
    // ACCESSIBILITY CHECK TESTS
    // ========================================

    @Test
    fun `isAccessibilityEnabled returns true when service is enabled`() {
        every {
            AccessibilityUtils.isAccessibilityServiceEnabled(any(), ScreenReaderAccessibility::class.java)
        } returns true

        val result = PermissionDisclosureDialog.isAccessibilityEnabled(mockContext)

        assertTrue(result)
    }

    @Test
    fun `isAccessibilityEnabled returns false when service is disabled`() {
        every {
            AccessibilityUtils.isAccessibilityServiceEnabled(any(), ScreenReaderAccessibility::class.java)
        } returns false

        val result = PermissionDisclosureDialog.isAccessibilityEnabled(mockContext)

        assertFalse(result)
    }

    // ========================================
    // SHOW METHOD TESTS
    // ========================================

    @Test
    fun `show calls listener immediately when permission already granted`() {
        every {
            AccessibilityUtils.isAccessibilityServiceEnabled(any(), ScreenReaderAccessibility::class.java)
        } returns true

        var listenerCalled = false
        val listener = PermissionDisclosureDialog.OnPermissionGrantedListener {
            listenerCalled = true
        }

        PermissionDisclosureDialog.show(mockContext, listener)

        assertTrue("Listener should be called when permission is already granted", listenerCalled)
    }

    @Test
    fun `show does not call listener immediately when permission not granted`() {
        every {
            AccessibilityUtils.isAccessibilityServiceEnabled(any(), ScreenReaderAccessibility::class.java)
        } returns false

        var listenerCalled = false
        val listener = PermissionDisclosureDialog.OnPermissionGrantedListener {
            listenerCalled = true
        }

        // Note: This will try to show dialog which requires FragmentActivity
        // In test, it will fail gracefully due to mock context
        PermissionDisclosureDialog.show(mockContext, listener)

        assertFalse("Listener should NOT be called when permission is not granted", listenerCalled)
    }

    @Test
    fun `show with null listener does not crash when permission granted`() {
        every {
            AccessibilityUtils.isAccessibilityServiceEnabled(any(), ScreenReaderAccessibility::class.java)
        } returns true

        // Should not throw
        PermissionDisclosureDialog.show(mockContext, null)
    }

    // ========================================
    // SHOW FROM KEYBOARD TESTS
    // ========================================

    @Test
    fun `showFromKeyboard calls listener immediately when permission granted`() {
        every {
            AccessibilityUtils.isAccessibilityServiceEnabled(any(), ScreenReaderAccessibility::class.java)
        } returns true

        var listenerCalled = false
        val listener = PermissionDisclosureDialog.OnPermissionGrantedListener {
            listenerCalled = true
        }

        PermissionDisclosureDialog.showFromKeyboard(mockContext, listener)

        assertTrue("Listener should be called when permission is granted", listenerCalled)
    }

    @Test
    fun `showFromKeyboard does not call listener when permission not granted`() {
        every {
            AccessibilityUtils.isAccessibilityServiceEnabled(any(), ScreenReaderAccessibility::class.java)
        } returns false

        var listenerCalled = false
        val listener = PermissionDisclosureDialog.OnPermissionGrantedListener {
            listenerCalled = true
        }

        PermissionDisclosureDialog.showFromKeyboard(mockContext, listener)

        assertFalse("Listener should NOT be called when permission is not granted", listenerCalled)
    }

    @Test
    fun `showFromKeyboard with null listener does not crash when permission granted`() {
        every {
            AccessibilityUtils.isAccessibilityServiceEnabled(any(), ScreenReaderAccessibility::class.java)
        } returns true

        // Should not throw
        PermissionDisclosureDialog.showFromKeyboard(mockContext, null)
    }

    @Test
    fun `showFromKeyboard with null listener does not crash when permission not granted`() {
        every {
            AccessibilityUtils.isAccessibilityServiceEnabled(any(), ScreenReaderAccessibility::class.java)
        } returns false

        // Should not throw - will show toast and open settings
        PermissionDisclosureDialog.showFromKeyboard(mockContext, null)
    }

    // ========================================
    // FRAGMENT TAG TESTS
    // ========================================

    @Test
    fun `FRAGMENT_TAG constant is defined`() {
        assertEquals("PermissionDisclosureDialog", PermissionDisclosureDialog.FRAGMENT_TAG)
    }
}
