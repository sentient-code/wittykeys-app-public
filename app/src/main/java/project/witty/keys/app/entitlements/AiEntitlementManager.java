package project.witty.keys.app.entitlements;

import android.content.Context;

import project.witty.keys.app.utils.DailyUsageTracker;

public final class AiEntitlementManager {
    private static AiEntitlementManager instance;

    private final DailyUsageTracker tracker;

    private AiEntitlementManager(Context context) {
        tracker = DailyUsageTracker.getInstance(context.getApplicationContext());
    }

    public static synchronized AiEntitlementManager getInstance(Context context) {
        if (instance == null) {
            instance = new AiEntitlementManager(context);
        }
        return instance;
    }

    public AiEntitlementDecision canRun(AiActionType type) {
        return AiEntitlementPolicy.decide(type, tracker.getRemainingActions(), tracker.isUnlimited());
    }

    public void record(AiActionType type) {
        tracker.recordUsage(type.cost);
    }

    public boolean shouldRunBackgroundPrecompute() {
        return canRun(AiActionType.BACKGROUND_PRECOMPUTE).allowed;
    }
}
