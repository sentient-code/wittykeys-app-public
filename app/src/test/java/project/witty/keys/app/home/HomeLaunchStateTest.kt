package project.witty.keys.app.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLaunchStateTest {

    @Test
    fun `anonymous ready state keeps app usable without sign in`() {
        val state = HomeLaunchState.from(
            false,
            true,
            true,
            true,
            false,
            18,
            true
        )

        assertEquals(HomeLaunchState.Mode.ANONYMOUS_READY, state.mode)
        assertEquals("Anonymous mode", state.topSubtitle)
        assertEquals("Use AI without signing in.", state.headline)
        assertEquals("Start with free daily actions. Sign in only for Plus and account management.", state.subhead)
        assertEquals("Free plan ready", state.statusTitle)
        assertEquals("18 left", state.creditLabel)
        assertEquals("20 AI actions/day", state.usageTitle)
        assertEquals("Upgrade", state.usageAction)
        assertTrue(state.showUpgrade)
        assertFalse(state.showSetupRecovery)
    }

    @Test
    fun `anonymous setup state shows missing permissions without requiring login`() {
        val state = HomeLaunchState.from(
            false,
            true,
            false,
            false,
            false,
            18,
            true
        )

        assertEquals(HomeLaunchState.Mode.SETUP_RECOVERY, state.mode)
        assertEquals("Anonymous mode", state.topSubtitle)
        assertEquals("Finish setup to unlock WittyKeys.", state.headline)
        assertEquals("No login required. Enable only the features you want to use.", state.subhead)
        assertEquals("Two setup items left", state.statusTitle)
        assertEquals("Setup", state.creditLabel)
        assertEquals("Setup", state.primaryAction)
        assertTrue(state.showSetupRecovery)
        assertFalse(state.showUpgrade)
    }

    @Test
    fun `setup state can count optional permission rows from the app checklist`() {
        val state = HomeLaunchState.from(
            true,
            true,
            true,
            true,
            false,
            20,
            true,
            3
        )

        assertEquals(HomeLaunchState.Mode.SETUP_RECOVERY, state.mode)
        assertEquals("Setup needed", state.topSubtitle)
        assertEquals("3 setup items left", state.statusTitle)
        assertEquals("Review", state.usageAction)
    }

    @Test
    fun `signed in free state shows synced free allowance`() {
        val state = HomeLaunchState.from(
            true,
            true,
            true,
            true,
            false,
            12,
            true
        )

        assertEquals(HomeLaunchState.Mode.SIGNED_IN_FREE, state.mode)
        assertEquals("Free plan", state.topSubtitle)
        assertEquals("Ready to use everywhere.", state.headline)
        assertEquals("Your account is synced. Upgrade only if you need more daily AI actions.", state.subhead)
        assertEquals("Free account ready", state.statusTitle)
        assertEquals("12 left", state.creditLabel)
        assertEquals("12 of 20 actions left", state.usageTitle)
        assertTrue(state.showUpgrade)
    }

    @Test
    fun `quota empty state pauses AI without blocking non AI tools`() {
        val state = HomeLaunchState.from(
            false,
            true,
            true,
            true,
            false,
            0,
            true
        )

        assertEquals(HomeLaunchState.Mode.QUOTA_EMPTY, state.mode)
        assertEquals("Daily AI limit exhausted.", state.headline)
        assertEquals("Overlay and Keyboard stay available. AI actions will reset soon.", state.subhead)
        assertEquals("AI actions paused today", state.statusTitle)
        assertEquals("exhausted daily limit will reset soon", state.statusSubtitle)
        assertEquals("0 left", state.creditLabel)
        assertEquals("Upgrade to Plus", state.primaryAction)
        assertEquals("Usage", state.secondaryAction)
        assertTrue(state.showUpgrade)
    }

    @Test
    fun `paid active state shows plan management instead of upgrade pressure`() {
        val state = HomeLaunchState.from(
            true,
            true,
            true,
            true,
            true,
            20,
            true
        )

        assertEquals(HomeLaunchState.Mode.PAID_ACTIVE, state.mode)
        assertEquals("Plus active", state.topSubtitle)
        assertEquals("Ready to use everywhere.", state.headline)
        assertEquals("WittyKeys is ready", state.statusTitle)
        assertEquals("Unlimited", state.creditLabel)
        assertEquals("Manage Plus", state.usageAction)
        assertFalse(state.showUpgrade)
    }

    @Test
    fun `backend error state says no credits are spent`() {
        val state = HomeLaunchState.from(
            false,
            true,
            true,
            true,
            false,
            18,
            false
        )

        assertEquals(HomeLaunchState.Mode.BACKEND_ERROR, state.mode)
        assertEquals("AI is resting. Your tools still work.", state.headline)
        assertEquals("Backend unavailable", state.statusTitle)
        assertEquals("AI is resting. Overlay, Keyboard, settings, and setup remain open.", state.statusSubtitle)
        assertEquals("Retry", state.primaryAction)
        assertFalse(state.showUpgrade)
    }
}
