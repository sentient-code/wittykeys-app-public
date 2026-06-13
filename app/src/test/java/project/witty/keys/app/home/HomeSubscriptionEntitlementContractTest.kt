package project.witty.keys.app.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeSubscriptionEntitlementContractTest {
    private fun read(path: String) = File(path).readText()

    @Test
    fun `home refreshes paid wallet state from firestore entitlement sync`() {
        val homeActivity = read("src/main/java/project/witty/keys/app/HomeActivity.java")

        assertTrue(homeActivity.contains("Subscription.syncPaidEntitlementFromFirestore("))
        assertTrue(homeActivity.contains("refreshHomeLaunchState()"))
    }

    @Test
    fun `subscription firestore sync owns paid allowance flag`() {
        val subscription = read("src/main/java/project/witty/keys/app/entities/Subscription.java")

        assertTrue(subscription.contains("syncPaidEntitlementFromFirestore"))
        assertTrue(subscription.contains("DailyUsageTracker.getInstance(context).setUnlimited(isPaidSub)"))
        assertTrue(subscription.contains("EncryptedPreferences.saveSubscriptionInfo("))
    }
}
