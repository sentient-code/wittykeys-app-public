package project.witty.keys.app.entitlements;

public final class AiEntitlementDecision {
    public final boolean allowed;
    public final AiActionType actionType;
    public final int requiredCredits;
    public final int remainingCredits;
    public final String reason;
    public final String userMessage;

    private AiEntitlementDecision(
            boolean allowed,
            AiActionType actionType,
            int requiredCredits,
            int remainingCredits,
            String reason,
            String userMessage) {
        this.allowed = allowed;
        this.actionType = actionType;
        this.requiredCredits = requiredCredits;
        this.remainingCredits = remainingCredits;
        this.reason = reason;
        this.userMessage = userMessage;
    }

    static AiEntitlementDecision allow(AiActionType type, int remainingCredits) {
        return new AiEntitlementDecision(
                true,
                type,
                type.cost,
                Math.max(remainingCredits, 0),
                "allowed",
                "");
    }

    static AiEntitlementDecision block(
            AiActionType type,
            int remainingCredits,
            String reason,
            String userMessage) {
        return new AiEntitlementDecision(
                false,
                type,
                type.cost,
                Math.max(remainingCredits, 0),
                reason,
                userMessage);
    }
}
