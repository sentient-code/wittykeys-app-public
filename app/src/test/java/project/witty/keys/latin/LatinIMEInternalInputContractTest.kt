package project.witty.keys.latin

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LatinIMEInternalInputContractTest {
    private fun read(path: String) = File(path).readText()

    @Test
    fun internalInputStillUpdatesKeyboardShiftStateAfterPrintableInput() {
        val latinIme = read("src/main/java/project/witty/keys/latin/LatinIME.java")

        assertTrue(latinIme.contains("handleInternalInputCodePoint("))
        assertTrue(latinIme.contains("activeTarget.onCodeInput(codePoint);"))
        assertTrue(latinIme.contains("mKeyboardSwitcher.onEvent(event, getCurrentAutoCapsState(), getCurrentRecapitalizeState())"))
    }
}
