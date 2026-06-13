package project.witty.keys.app.home;

import java.util.Locale;

public final class HomeLaunchState {
    private static final int LOW_CREDIT_THRESHOLD = 5;
    private static final int FREE_DAILY_LIMIT = 20;

    public enum Mode {
        ANONYMOUS_READY,
        SIGNED_IN_FREE,
        SETUP_RECOVERY,
        QUOTA_LOW,
        QUOTA_EMPTY,
        PAID_ACTIVE,
        BACKEND_ERROR
    }

    public final Mode mode;
    public final String topSubtitle;
    public final String headline;
    public final String subhead;
    public final String statusTitle;
    public final String statusSubtitle;
    public final String creditLabel;
    public final String usageTitle;
    public final String usageAction;
    public final String primaryAction;
    public final String secondaryAction;
    public final boolean showUpgrade;
    public final boolean showSetupRecovery;

    private HomeLaunchState(
            Mode mode,
            String topSubtitle,
            String headline,
            String subhead,
            String statusTitle,
            String statusSubtitle,
            String creditLabel,
            String usageTitle,
            String usageAction,
            String primaryAction,
            String secondaryAction,
            boolean showUpgrade,
            boolean showSetupRecovery) {
        this.mode = mode;
        this.topSubtitle = topSubtitle;
        this.headline = headline;
        this.subhead = subhead;
        this.statusTitle = statusTitle;
        this.statusSubtitle = statusSubtitle;
        this.creditLabel = creditLabel;
        this.usageTitle = usageTitle;
        this.usageAction = usageAction;
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
        this.showUpgrade = showUpgrade;
        this.showSetupRecovery = showSetupRecovery;
    }

    public static HomeLaunchState from(
            boolean signedIn,
            boolean keyboardEnabled,
            boolean keyboardDefault,
            boolean overlayReady,
            boolean unlimited,
            int remainingActions,
            boolean backendAvailable) {
        return from(
                signedIn,
                keyboardEnabled,
                keyboardDefault,
                overlayReady,
                unlimited,
                remainingActions,
                backendAvailable,
                missingSetupCount(keyboardEnabled, keyboardDefault, overlayReady));
    }

    public static HomeLaunchState from(
            boolean signedIn,
            boolean keyboardEnabled,
            boolean keyboardDefault,
            boolean overlayReady,
            boolean unlimited,
            int remainingActions,
            boolean backendAvailable,
            int missingSetupCount) {
        int safeRemaining = Math.max(remainingActions, 0);
        int safeMissingSetupCount = Math.max(missingSetupCount, 0);

        if (!backendAvailable) {
            return new HomeLaunchState(
                    Mode.BACKEND_ERROR,
                    "AI unavailable",
                    "AI is resting. Your tools still work.",
                    "No credits are spent while the backend is unavailable.",
                    "Backend unavailable",
                    "AI is resting. Overlay, Keyboard, settings, and setup remain open.",
                    "Retry",
                    "No credits spent",
                    "Retry",
                    "Retry",
                    "Open settings",
                    false,
                    false);
        }

        if (safeMissingSetupCount > 0) {
            return new HomeLaunchState(
                    Mode.SETUP_RECOVERY,
                    signedIn ? "Setup needed" : "Anonymous mode",
                    "Finish setup to unlock WittyKeys.",
                    signedIn
                            ? "Enable only the features you want. Each permission explains exactly what it unlocks."
                            : "No login required. Enable only the features you want to use.",
                    pluralizeSetup(safeMissingSetupCount),
                    "Each permission explains the benefit before you enable it.",
                    "Setup",
                    "Setup needed",
                    "Review",
                    "Setup",
                    "Use available tools",
                    false,
                    true);
        }

        if (unlimited) {
            return new HomeLaunchState(
                    Mode.PAID_ACTIVE,
                    "Plus active",
                    "Ready to use everywhere.",
                    "Open the overlay for messages and screens, or use keyboard tools while typing.",
                    "WittyKeys is ready",
                    "Overlay, Quick Reply, and Keyboard are available across your apps.",
                    "Unlimited",
                    "Setup complete",
                    "Manage Plus",
                    "Open Overlay",
                    "Keyboard tools",
                    false,
                    false);
        }

        if (safeRemaining == 0) {
            return new HomeLaunchState(
                    Mode.QUOTA_EMPTY,
                    signedIn ? "Free plan" : "Anonymous mode",
                    "Daily AI limit exhausted.",
                    "Overlay and Keyboard stay available. AI actions will reset soon.",
                    "AI actions paused today",
                    "exhausted daily limit will reset soon",
                    "0 left",
                    "0 of 20 actions left",
                    "Upgrade",
                    "Upgrade to Plus",
                    "Usage",
                    true,
                    false);
        }

        if (safeRemaining <= LOW_CREDIT_THRESHOLD) {
            return new HomeLaunchState(
                    Mode.QUOTA_LOW,
                    signedIn ? "Free plan" : "Anonymous mode",
                    signedIn ? "Ready to use everywhere." : "Use AI without signing in.",
                    signedIn
                            ? "Your account is synced. Upgrade only if you need more daily AI actions."
                            : "Start with free daily actions. Sign in only for Plus and account management.",
                    signedIn ? "Free account ready" : "Free plan ready",
                    "Overlay, Quick Reply, and Keyboard are available.",
                    formatRemaining(safeRemaining),
                    usageTitle(signedIn, safeRemaining),
                    "Upgrade",
                    "Open Overlay",
                    "Keyboard tools",
                    true,
                    false);
        }

        return new HomeLaunchState(
                signedIn ? Mode.SIGNED_IN_FREE : Mode.ANONYMOUS_READY,
                signedIn ? "Free plan" : "Anonymous mode",
                signedIn ? "Ready to use everywhere." : "Use AI without signing in.",
                signedIn
                        ? "Your account is synced. Upgrade only if you need more daily AI actions."
                        : "Start with free daily actions. Sign in only for Plus and account management.",
                signedIn ? "Free account ready" : "Free plan ready",
                "Overlay, Quick Reply, and Keyboard are available.",
                formatRemaining(safeRemaining),
                usageTitle(signedIn, safeRemaining),
                "Upgrade",
                "Open Overlay",
                "Keyboard tools",
                true,
                false);
    }

    private static int missingSetupCount(boolean keyboardEnabled, boolean keyboardDefault, boolean overlayReady) {
        int count = 0;
        if (!keyboardEnabled || !keyboardDefault) count++;
        if (!overlayReady) count++;
        return count;
    }

    private static String pluralizeSetup(int count) {
        if (count == 1) return "One setup item left";
        if (count == 2) return "Two setup items left";
        return String.format(Locale.US, "%d setup items left", count);
    }

    private static String usageTitle(boolean signedIn, int remainingActions) {
        return signedIn
                ? String.format(Locale.US, "%d of %d actions left", remainingActions, FREE_DAILY_LIMIT)
                : String.format(Locale.US, "%d AI actions/day", FREE_DAILY_LIMIT);
    }

    private static String formatRemaining(int remainingActions) {
        return String.format(Locale.US, "%d left", remainingActions);
    }
}
