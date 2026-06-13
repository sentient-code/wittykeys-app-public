package project.witty.keys.app.entitlements;

public final class AiEntitlementPolicy {
    public static final String PLUS_ONLY_PRECOMPUTE_MESSAGE =
            "Background precompute is reserved for Plus. Manual replies still work.";
    public static final String INSUFFICIENT_CREDITS_MESSAGE =
            "Daily limit exhausted. It will reset soon.";

    private AiEntitlementPolicy() {}

    public static AiEntitlementDecision decide(
            AiActionType type,
            int remainingCredits,
            boolean paidPlan) {
        int safeRemaining = Math.max(remainingCredits, 0);

        if (!paidPlan && !type.freeTierAllowed) {
            return AiEntitlementDecision.block(
                    type,
                    safeRemaining,
                    "plus_only",
                    PLUS_ONLY_PRECOMPUTE_MESSAGE);
        }

        if (safeRemaining < type.cost) {
            return AiEntitlementDecision.block(
                    type,
                    safeRemaining,
                    "insufficient_credits",
                    INSUFFICIENT_CREDITS_MESSAGE);
        }

        return AiEntitlementDecision.allow(type, safeRemaining);
    }
}
