package project.witty.keys.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeyboardAndLoadingPolishContractTest {
    private fun read(path: String) = File(path).readText()

    @Test
    fun `shared loading overlay uses branded glass treatment`() {
        val layout = read("src/main/res/layout/loading_overlay.xml")

        assertTrue(layout.contains("@drawable/wk_loading_panel_bg"))
        assertTrue(layout.contains("@drawable/wk_logo_overlay"))
        assertTrue(layout.contains("progressBar"))
        assertTrue(layout.contains("loading_text"))
        assertTrue(layout.contains("android:indeterminateTint=\"@color/wk_accent\""))
        assertFalse(layout.contains("app:indicatorColor=\"@color/third_app_color\""))
    }

    @Test
    fun `keyboard row one and row two polish stays production ready`() {
        val layout = read("src/main/res/layout/wk_original_view.xml")
        val bar = read("src/main/java/project/witty/keys/keyboard/AssistantViews/SmartAssistantBar.java")
        val manager = read("src/main/java/project/witty/keys/keyboard/AssistantViews/SmartAssistantBarManager.java")
        val bubbleBg = read("src/main/res/drawable/overlay_bubble_circle.xml")

        assertTrue(layout.contains("@drawable/wk_ic_mic_simple"))
        assertTrue(layout.contains("android:padding=\"@dimen/wk_mic_icon_padding\""))
        assertTrue(read("src/main/res/values/wk_dimens.xml").contains("<dimen name=\"wk_mic_icon_padding\">6dp</dimen>"))
        assertTrue(layout.contains("android:tint=\"@color/wk_text\""))
        assertFalse(layout.contains("android:text=\"🎤\""))
        assertFalse(layout.contains("android:text=\"🎙\""))
        assertTrue(manager.contains("private final ImageView ovMicCollapsed;"))
        assertTrue(manager.contains("private final ImageView ovMicBtn;"))
        assertTrue(manager.contains("ovMicCollapsed.setColorFilter(getColor(R.color.wk_text))"))
        assertTrue(manager.contains("ovMicBtn.setColorFilter(getColor(R.color.wk_text))"))
        assertFalse(manager.contains("ovBrain.setBackground(activeBg)"))
        assertTrue(manager.contains("STARTER_PREDICTIONS"))
        assertTrue(manager.contains("populatePredictions(resolvePredictionsForCollapsed())"))
        assertTrue(manager.contains("new ChipData(\"custom\", \"\", \"\\u270F\\uFE0F\", \"Custom\", ChipData.TapAction.OPEN_CUSTOM_MODE)"))
        assertTrue(manager.contains("new ChipData(\"more\", \"\", \"\", \"+ More\", ChipData.TapAction.EXPAND_FULL_PANEL)"))
        assertFalse(manager.contains("createUtilityChip("))
        assertTrue(bar.contains("sabManager.setPredictions(null);"))
        assertTrue(bubbleBg.contains("#4A4A52"))
        assertTrue(bubbleBg.contains("#3A3A42"))
        assertFalse(bubbleBg.contains("@color/wk_bg"))
    }

    @Test
    fun `internal keyboard inputs show cursor and update shift from their own text`() {
        val chatInput = read("src/main/java/project/witty/keys/keyboard/internal/InternalChatInput.java")
        val emojiSearch = read("src/main/java/project/witty/keys/keyboard/EmojiKeyboard/InternalSearchView.java")
        val latinIme = read("src/main/java/project/witty/keys/latin/LatinIME.java")

        assertTrue(chatInput.contains("cursorVisible ? \"\\u2758 \" + placeholderText"))
        assertTrue(chatInput.contains("displayText = displayText + \"\\u2758\""))
        assertTrue(emojiSearch.contains("cursorVisible ? \"\\u2758 \" + placeholderText"))
        assertTrue(emojiSearch.contains("displayText = displayText + \"\\u2758\""))
        assertTrue(latinIme.contains("refreshInternalInputShiftState(activeTarget);"))
        assertTrue(latinIme.contains("getInternalInputAutoCapsState"))
        assertTrue(latinIme.contains("Constants.TextUtils.CAP_MODE_OFF"))
    }
}
