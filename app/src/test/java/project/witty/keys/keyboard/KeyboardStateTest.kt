package project.witty.keys.keyboard

import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import project.witty.keys.keyboard.internal.KeyboardState
import kotlin.test.assertNotNull

/**
 * Unit tests for KeyboardState - manages keyboard layout state transitions.
 *
 * These tests verify:
 * - KeyboardState creation
 * - Basic state management
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class KeyboardStateTest {

    @MockK
    private lateinit var mockSwitchActions: KeyboardState.SwitchActions

    private lateinit var keyboardState: KeyboardState

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        keyboardState = KeyboardState(mockSwitchActions)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test initial keyboard state creation.
     */
    @Test
    fun keyboardState_initialCreation_succeeds() {
        assertNotNull(keyboardState, "KeyboardState should be created successfully")
    }

    /**
     * Test that onLoadKeyboard initializes state.
     */
    @Test
    fun onLoadKeyboard_initializesState() {
        // Load keyboard - this should not throw
        keyboardState.onLoadKeyboard(0, 0)

        // Verify setAlphabetKeyboard was called
        verify { mockSwitchActions.setAlphabetKeyboard() }
    }

    /**
     * Test that onSaveKeyboardState doesn't throw.
     */
    @Test
    fun onSaveKeyboardState_savesWithoutCrash() {
        // Should not throw
        keyboardState.onSaveKeyboardState()
    }

    /**
     * Test key press handling.
     */
    @Test
    fun onPressKey_handlesKeyPress() {
        val shiftKeyCode = project.witty.keys.latin.common.Constants.CODE_SHIFT

        // Should not throw
        keyboardState.onPressKey(shiftKeyCode, true, 0, 0)
    }

    /**
     * Test key release handling.
     */
    @Test
    fun onReleaseKey_handlesKeyRelease() {
        val shiftKeyCode = project.witty.keys.latin.common.Constants.CODE_SHIFT

        // Press then release
        keyboardState.onPressKey(shiftKeyCode, true, 0, 0)
        keyboardState.onReleaseKey(shiftKeyCode, false, 0, 0)

        // Should not throw
    }

    /**
     * Test update shift state.
     */
    @Test
    fun onUpdateShiftState_updatesState() {
        // Should not throw
        keyboardState.onUpdateShiftState(0, 0, true, false)
    }

    /**
     * Test finish sliding input.
     */
    @Test
    fun onFinishSlidingInput_finishesWithoutCrash() {
        // Should not throw
        keyboardState.onFinishSlidingInput(0, 0)
    }

    /**
     * Test reset keyboard state.
     */
    @Test
    fun onResetKeyboardStateToAlphabet_resetsState() {
        // Should not throw
        keyboardState.onResetKeyboardStateToAlphabet(0, 0)
    }
}
