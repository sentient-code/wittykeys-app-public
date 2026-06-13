package project.witty.keys.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountEntitlementSnapshotTest {
    @Test
    fun paidActiveShowsUnlimitedAndManagePlan() {
        val snapshot = AccountEntitlementSnapshot.paidActive(
            "abhishek8938@gmail.com",
            "Yearly Standard",
            true,
            7
        )

        assertEquals(AccountEntitlementSnapshot.AuthState.SIGNED_IN, snapshot.authState)
        assertEquals(AccountEntitlementSnapshot.SubscriptionState.PAID_ACTIVE, snapshot.subscriptionState)
        assertEquals("Unlimited", snapshot.allowanceDisplay)
        assertEquals("Plus active", snapshot.usageLabel)
        assertEquals(AccountEntitlementSnapshot.PrimaryCta.MANAGE_PLAN, snapshot.primaryCta)
        assertTrue(snapshot.isPaidActive)
    }

    @Test
    fun freeAnonymousShowsTwentyPerDayAndUpgrade() {
        val snapshot = AccountEntitlementSnapshot.freeAnonymous(6, 14)

        assertEquals(AccountEntitlementSnapshot.AuthState.ANONYMOUS, snapshot.authState)
        assertEquals(AccountEntitlementSnapshot.SubscriptionState.FREE, snapshot.subscriptionState)
        assertEquals("20/day", snapshot.allowanceDisplay)
        assertEquals("14 credits", snapshot.usageLabel)
        assertEquals(AccountEntitlementSnapshot.PrimaryCta.UPGRADE, snapshot.primaryCta)
        assertFalse(snapshot.isPaidActive)
    }

    @Test
    fun checkingStateDoesNotRenderZeroCreditsForKnownSignedInUser() {
        val snapshot = AccountEntitlementSnapshot.checkingSignedIn("abhishek8938@gmail.com")

        assertEquals(AccountEntitlementSnapshot.AuthState.SYNCING, snapshot.authState)
        assertEquals(AccountEntitlementSnapshot.SubscriptionState.UNKNOWN_SYNCING, snapshot.subscriptionState)
        assertEquals("Checking plan...", snapshot.allowanceDisplay)
        assertEquals(AccountEntitlementSnapshot.PrimaryCta.RETRY_SYNC, snapshot.primaryCta)
    }
}
