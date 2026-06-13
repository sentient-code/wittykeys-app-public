package project.witty.keys.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecklistStateTest {
    @Test
    fun razrGrantedStateShowsDoneForGrantedPermissions() {
        val state = SetupChecklistState.fromFacts(
            true,
            true,
            true,
            true,
            true,
            true
        )

        assertEquals(6, state.readyCount)
        assertEquals(6, state.totalCount)
        assertTrue(state.item(SetupChecklistState.ItemId.KEYBOARD_ENABLED).isDone)
        assertTrue(state.item(SetupChecklistState.ItemId.KEYBOARD_DEFAULT).isDone)
        assertTrue(state.item(SetupChecklistState.ItemId.OVERLAY_BUBBLE).isDone)
        assertTrue(state.item(SetupChecklistState.ItemId.APP_NOTIFICATIONS).isDone)
        assertTrue(state.item(SetupChecklistState.ItemId.NOTIFICATION_ACCESS).isDone)
        assertTrue(state.item(SetupChecklistState.ItemId.ACCESSIBILITY_HELPER).isDone)
        assertEquals("Ask when used", state.screenCaptureStatus.label)
    }

    @Test
    fun notificationAccessExplainsQuickReplyBenefit() {
        val state = SetupChecklistState.fromFacts(
            true,
            true,
            true,
            true,
            false,
            false
        )

        val nls = state.item(SetupChecklistState.ItemId.NOTIFICATION_ACCESS)
        assertEquals(SetupChecklistState.Status.OPTIONAL_MISSING, nls.status)
        assertEquals(
            "Catch message notifications so Quick Reply can track recent conversations.",
            nls.benefit
        )
    }

    @Test
    fun appNotificationsAreSeparateFromQuickReplyNotificationAccess() {
        val state = SetupChecklistState.fromFacts(
            true,
            true,
            true,
            false,
            true,
            true
        )

        val push = state.item(SetupChecklistState.ItemId.APP_NOTIFICATIONS)
        assertEquals(SetupChecklistState.Status.REQUIRED_MISSING, push.status)
        assertEquals(
            "Receive push updates for account, subscription, usage, and important WittyKeys alerts.",
            push.benefit
        )
        assertEquals("Enable", push.label)
    }
}
