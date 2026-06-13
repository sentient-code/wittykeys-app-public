package project.witty.keys.app.entitlements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEntitlementPolicyTest {

    @Test
    fun `action costs classify cheap chat screen and background work`() {
        assertEquals(1, AiActionType.SHORT_TEXT.cost)
        assertEquals(2, AiActionType.AI_CHAT.cost)
        assertEquals(4, AiActionType.SCREEN_AI.cost)
        assertFalse(AiActionType.BACKGROUND_PRECOMPUTE.freeTierAllowed)
    }

    @Test
    fun `screen AI requires enough remaining free credits`() {
        val blocked = AiEntitlementPolicy.decide(
            AiActionType.SCREEN_AI,
            3,
            false
        )
        val allowed = AiEntitlementPolicy.decide(
            AiActionType.SCREEN_AI,
            4,
            false
        )

        assertFalse(blocked.allowed)
        assertEquals("Daily limit exhausted. It will reset soon.", blocked.userMessage)
        assertTrue(allowed.allowed)
        assertEquals(4, allowed.requiredCredits)
    }

    @Test
    fun `background precompute is plus only because it spends AI invisibly`() {
        val freeDecision = AiEntitlementPolicy.decide(
            AiActionType.BACKGROUND_PRECOMPUTE,
            20,
            false
        )
        val paidDecision = AiEntitlementPolicy.decide(
            AiActionType.BACKGROUND_PRECOMPUTE,
            20,
            true
        )

        assertFalse(freeDecision.allowed)
        assertEquals(
            "Background precompute is reserved for Plus. Manual replies still work.",
            freeDecision.userMessage
        )
        assertTrue(paidDecision.allowed)
    }
}
