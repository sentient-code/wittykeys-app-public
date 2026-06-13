package project.witty.keys.app.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OverlayHeldSurfaceContractTest {
    private fun read(path: String) = File(path).readText()

    @Test
    fun overlayChatKeepsSharedSessionListContract() {
        val source = read("src/main/java/project/witty/keys/app/overlay/OverlayChatPanel.java")

        assertTrue(source.contains("WkSessionDisplay.displayTitle(session.title)"))
        assertTrue(source.contains("WkSessionDisplay.preview(session.title, session.lastPreview)"))
        assertTrue(source.contains("WkSessionDisplay.surfaceFromSource(session.source)"))
        assertTrue(source.contains("card.setOnDeleteClickListener(v -> deleteOverlaySession(session))"))
        assertTrue(source.contains("WkSessionListFormat.dateBucket"))
        assertTrue(source.contains("WkSessionListFormat.hasMorePage"))
        assertTrue(source.contains("createLoadMoreFooter()"))
        assertTrue(source.contains("unifiedSessionManager.getMessagesSync(session.roomSessionId)"))
        assertFalse(source.contains("titleView.setText(\"Overlay"))
    }

    @Test
    fun overlayAiChatAndQuickReplyExposeInputBars() {
        val chatLayout = read("src/main/res/layout/overlay_screenshot_popup.xml")
        val replyLayout = read("src/main/res/layout/overlay_reply_popup.xml")

        assertTrue(chatLayout.contains("@+id/overlay_input_bar"))
        assertTrue(chatLayout.contains("project.witty.keys.ui.chat.WkInputBar"))
        assertTrue(replyLayout.contains("@+id/overlay_reply_input"))
        assertTrue(replyLayout.contains("android:inputType=\"text\""))
    }

    @Test
    fun quickReplyPanelHasReopenSafeEntryPoints() {
        val source = read("src/main/java/project/witty/keys/app/overlay/OverlayReplyPanel.java")

        assertTrue(source.contains("show("))
        assertTrue(source.contains("hide("))
        assertTrue(source.contains("replyInput"))
        assertTrue(source.contains("hidePopupFromPanel()"))
        assertFalse(source.contains("static boolean"))
    }

    @Test
    fun quickReplyUsesConversationFirstTwoHourStateAndKeepsSentBubbleVisible() {
        val source = read("src/main/java/project/witty/keys/app/overlay/OverlayReplyPanel.java")
        val buffer = read("src/main/java/project/witty/keys/app/context/NlsMessageBuffer.java")
        val nls = read("src/main/java/project/witty/keys/app/helpers/WittyKeysNotificationListenerService.java")
        val layout = read("src/main/res/layout/overlay_reply_popup.xml")
        val sendMethod = source
            .substringAfter("private void sendReply()")
            .substringBefore("private void openChatApp()")
        val suggestionsMethod = source
            .substringAfter("private void updateSuggestions(ContactTab tab)")
            .substringBefore("private View createSuggestionChip")

        assertTrue(source.contains("msgBuffer.getOpenConversations()"))
        assertTrue(sendMethod.contains("addSentMessage(tab.conversationKey, text)"))
        assertTrue(source.contains("showQuotaUpgradeMessage("))
        assertTrue(source.contains("showWaitingForSuggestions("))
        assertTrue(suggestionsMethod.indexOf("canRun(AiActionType.SHORT_TEXT)") <
            suggestionsMethod.indexOf("if (state == null || !state.canShowSuggestions)"))
        assertTrue(sendMethod.contains("replyInput.setText(\"\")"))
        assertFalse(sendMethod.contains("overlayService.hidePopupFromPanel()"))
        assertFalse(sendMethod.contains("isShowing = false"))

        assertTrue(buffer.contains("OPEN_CONVERSATION_TTL_MS"))
        assertTrue(buffer.contains("addSentMessage"))
        assertTrue(buffer.contains("getOpenConversations"))

        assertTrue(nls.contains("RemoteInputSendResult"))
        assertTrue(nls.contains("findBestReplyAction"))
        assertTrue(nls.contains("trackSentReply(String conversationKey, String replyText)"))
        assertTrue(nls.contains("ReplyCache.getInstance().invalidate(conversationKey)"))
        assertTrue(nls.contains("WittyKeysOverlayService.triggerBadgeRefresh()"))

        assertTrue(layout.contains("@drawable/overlay_reply_input_bar_bg"))
        assertTrue(layout.contains("android:layout_marginHorizontal=\"10dp\""))
    }

    @Test
    fun overlayReplyPanelUsesConversationReplyStateAndStableQuotaMessage() {
        val panel = read("src/main/java/project/witty/keys/app/overlay/OverlayReplyPanel.java")

        assertTrue(panel.contains("ConversationReplyStateBuilder.fromSnapshot"))
        assertTrue(panel.contains("state.canShowSuggestions"))
        assertTrue(panel.contains("Daily limit exhausted. It will reset soon."))
        assertTrue(panel.contains("Sent. Waiting for their reply."))
        assertTrue(panel.contains("invalidateConversation"))
        assertTrue(panel.contains("getPendingReplyCount"))
        assertFalse(panel.contains("cache.get(conversation.conversationKey)"))
        assertFalse(panel.contains("showQuotaUpgradeMessage(decision.userMessage)"))
    }

    @Test
    fun quickReplyTreatsImageNotificationsAsSafeMediaPlaceholders() {
        val nls = read("src/main/java/project/witty/keys/app/helpers/WittyKeysNotificationListenerService.java")
        val precompute = read("src/main/java/project/witty/keys/app/context/ReplyPrecomputeManager.java")
        val normalizer = read("src/main/java/project/witty/keys/app/context/MediaMessageNormalizer.java")

        assertTrue(nls.contains("MediaMessageNormalizer.normalizeIncomingText"))
        assertTrue(precompute.contains("MediaMessageNormalizer.isMediaPlaceholderText"))
        assertTrue(precompute.contains("safeRepliesForMediaPlaceholder"))
        assertTrue(normalizer.contains("Photo received"))
        assertFalse(normalizer.contains("I can see"))
    }

    @Test
    fun overlayNewChatDoesNotRenderChatTwiceFromOneTap() {
        val source = read("src/main/java/project/witty/keys/app/overlay/OverlayChatPanel.java")

        assertFalse(source.contains("startNewChat();\n            showPopupInMode(Mode.CHAT, null);"))
    }

    @Test
    fun overlayDebugStatesCoverRequiredCaptureContract() {
        val source = read("src/main/java/project/witty/keys/app/overlay/OverlayChatPanel.java") +
            read("src/main/java/project/witty/keys/app/overlay/WittyKeysOverlayService.java") +
            read("src/main/java/project/witty/keys/app/overlay/OverlayReplyPanel.java")

        listOf(
            "popup_empty",
            "popup_populated",
            "popup_loading",
            "popup_chat_empty",
            "popup_chat_loading",
            "popup_chat_populated",
            "action_panel",
            "reply_empty",
            "reply_populated"
        ).forEach { state ->
            assertTrue("Missing overlay debug state $state", source.contains(state))
        }
    }

    @Test
    fun overlayGoldenCaptureUsesOfficialDebugStateNames() {
        val source = read("src/androidTest/java/project/witty/keys/e2e/golden/OverlayGoldenCaptureTest.kt")

        assertTrue(source.contains("\"popup_empty\""))
        assertTrue(source.contains("\"popup_populated\""))
        assertFalse(source.contains("\"popup_history_empty\""))
        assertFalse(source.contains("\"popup_history_populated\""))
    }
}
