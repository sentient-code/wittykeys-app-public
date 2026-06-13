package project.witty.keys.app.state;

public final class AccountEntitlementSnapshot {
    public enum AuthState {
        ANONYMOUS,
        SIGNED_IN,
        INVALID_LOCAL_USER,
        SYNCING,
        SYNC_FAILED
    }

    public enum SubscriptionState {
        FREE,
        PAID_ACTIVE,
        PAID_EXPIRED,
        BILLING_ISSUE,
        UNKNOWN_SYNCING
    }

    public enum PrimaryCta {
        UPGRADE,
        MANAGE_PLAN,
        RESTORE,
        SIGN_IN_TO_MANAGE,
        RETRY_SYNC,
        NONE
    }

    public static final int FREE_DAILY_LIMIT = 20;

    public final AuthState authState;
    public final String userDisplay;
    public final SubscriptionState subscriptionState;
    public final String planName;
    public final String allowanceDisplay;
    public final String usageLabel;
    public final int actionsUsedToday;
    public final int actionsRemainingToday;
    public final boolean isPaidActive;
    public final PrimaryCta primaryCta;

    private AccountEntitlementSnapshot(
            AuthState authState,
            String userDisplay,
            SubscriptionState subscriptionState,
            String planName,
            String allowanceDisplay,
            String usageLabel,
            int actionsUsedToday,
            int actionsRemainingToday,
            boolean isPaidActive,
            PrimaryCta primaryCta) {
        this.authState = authState;
        this.userDisplay = nonEmpty(userDisplay, "Anonymous mode");
        this.subscriptionState = subscriptionState;
        this.planName = nonEmpty(planName, "Free");
        this.allowanceDisplay = nonEmpty(allowanceDisplay, FREE_DAILY_LIMIT + "/day");
        this.usageLabel = nonEmpty(usageLabel, "0 credits");
        this.actionsUsedToday = Math.max(0, actionsUsedToday);
        this.actionsRemainingToday = Math.max(0, actionsRemainingToday);
        this.isPaidActive = isPaidActive;
        this.primaryCta = primaryCta;
    }

    public static AccountEntitlementSnapshot paidActive(
            String userDisplay,
            String planName,
            boolean fresh,
            int actionsUsedToday) {
        return new AccountEntitlementSnapshot(
                AuthState.SIGNED_IN,
                nonEmpty(userDisplay, "Signed in"),
                SubscriptionState.PAID_ACTIVE,
                nonEmpty(planName, "WittyKeys Plus"),
                "Unlimited",
                "Plus active",
                actionsUsedToday,
                Integer.MAX_VALUE,
                true,
                PrimaryCta.MANAGE_PLAN);
    }

    public static AccountEntitlementSnapshot freeAnonymous(
            int actionsUsedToday,
            int actionsRemainingToday) {
        return new AccountEntitlementSnapshot(
                AuthState.ANONYMOUS,
                "Anonymous mode",
                SubscriptionState.FREE,
                "Free",
                FREE_DAILY_LIMIT + "/day",
                Math.max(0, actionsRemainingToday) + " credits",
                actionsUsedToday,
                actionsRemainingToday,
                false,
                PrimaryCta.UPGRADE);
    }

    public static AccountEntitlementSnapshot checkingSignedIn(String userDisplay) {
        return new AccountEntitlementSnapshot(
                AuthState.SYNCING,
                nonEmpty(userDisplay, "Signed in"),
                SubscriptionState.UNKNOWN_SYNCING,
                "",
                "Checking plan...",
                "Checking plan...",
                0,
                0,
                false,
                PrimaryCta.RETRY_SYNC);
    }

    public static AccountEntitlementSnapshot signedInFree(
            String userDisplay,
            int actionsUsedToday,
            int actionsRemainingToday) {
        AccountEntitlementSnapshot free = freeAnonymous(actionsUsedToday, actionsRemainingToday);
        return new AccountEntitlementSnapshot(
                AuthState.SIGNED_IN,
                nonEmpty(userDisplay, "Signed in"),
                free.subscriptionState,
                free.planName,
                free.allowanceDisplay,
                free.usageLabel,
                free.actionsUsedToday,
                free.actionsRemainingToday,
                false,
                PrimaryCta.UPGRADE);
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
