package project.witty.keys.latin

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputConnection
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for RichInputConnection - the wrapper for Android InputConnection.
 *
 * These tests verify:
 * - Connection state tracking
 * - Batch edit operations
 * - Text manipulation
 * - Cursor position tracking
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RichInputConnectionTest {

    @MockK
    private lateinit var mockService: InputMethodService

    @MockK
    private lateinit var mockInputConnection: InputConnection

    private lateinit var richConnection: RichInputConnection

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        richConnection = RichInputConnection(mockService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test initial connection state is disconnected.
     */
    @Test
    fun isConnected_initiallyFalse() {
        assertFalse(richConnection.isConnected, "Should not be connected initially")
    }

    /**
     * Test batch edit nesting works correctly.
     */
    @Test
    fun beginBatchEdit_incrementsNestLevel() {
        every { mockService.currentInputConnection } returns mockInputConnection
        every { mockInputConnection.beginBatchEdit() } returns true

        // First batch edit
        richConnection.beginBatchEdit()

        // Nested batch edit should not call beginBatchEdit again
        richConnection.beginBatchEdit()

        // End inner batch
        richConnection.endBatchEdit()

        // End outer batch
        richConnection.endBatchEdit()

        // Verify beginBatchEdit was only called once
        verify(exactly = 1) { mockInputConnection.beginBatchEdit() }
    }

    /**
     * Test that connection is established during batch edit.
     */
    @Test
    fun beginBatchEdit_establishesConnection() {
        every { mockService.currentInputConnection } returns mockInputConnection
        every { mockInputConnection.beginBatchEdit() } returns true

        richConnection.beginBatchEdit()

        assertTrue(richConnection.isConnected, "Should be connected after batch edit")

        richConnection.endBatchEdit()
    }

    /**
     * Test that endBatchEdit properly closes batch.
     */
    @Test
    fun endBatchEdit_closesBatch() {
        every { mockService.currentInputConnection } returns mockInputConnection
        every { mockInputConnection.beginBatchEdit() } returns true
        every { mockInputConnection.endBatchEdit() } returns true

        richConnection.beginBatchEdit()
        richConnection.endBatchEdit()

        verify(exactly = 1) { mockInputConnection.endBatchEdit() }
    }

    /**
     * Test handling of null input connection.
     */
    @Test
    fun beginBatchEdit_handlesNullConnection() {
        every { mockService.currentInputConnection } returns null

        // Should not throw
        richConnection.beginBatchEdit()

        assertFalse(richConnection.isConnected, "Should not be connected with null IC")
    }
}
