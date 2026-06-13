package project.witty.keys.app.state;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;

import project.witty.keys.app.entities.Subscription;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.utils.DailyUsageTracker;

public final class AccountEntitlementSnapshotProvider {
    private AccountEntitlementSnapshotProvider() {}

    public static AccountEntitlementSnapshot current(Context context) {
        EncryptedPreferences.initialize(context);
        DailyUsageTracker tracker = DailyUsageTracker.getInstance(context);
        User localUser = EncryptedPreferences.getUserLoggedInInfo();
        boolean firebaseSignedIn = FirebaseAuth.getInstance().getCurrentUser() != null;

        if (localUser != null && !firebaseSignedIn) {
            EncryptedPreferences.clearUserInfo();
            EncryptedPreferences.clearSubscriptionInfo();
            tracker.setUnlimited(false);
            return AccountEntitlementSnapshot.freeAnonymous(
                    tracker.getActionsToday(),
                    tracker.getRemainingActions());
        }

        EncryptedPreferences.SubscriptionInfo subscriptionInfo =
                EncryptedPreferences.getSubscriptionInfo();
        boolean paidActive = localUser != null
                && subscriptionInfo != null
                && Subscription.SubscriptionStatus.ACTIVE.toString().equals(subscriptionInfo.getStatus())
                && !Subscription.FT_PACKAGE_ID_STRING.equals(subscriptionInfo.getPackageId())
                && tracker.isUnlimited();

        if (paidActive) {
            return AccountEntitlementSnapshot.paidActive(
                    displayName(localUser),
                    subscriptionInfo.getName(),
                    true,
                    tracker.getActionsToday());
        }

        if (localUser != null) {
            return AccountEntitlementSnapshot.signedInFree(
                    displayName(localUser),
                    tracker.getActionsToday(),
                    tracker.getRemainingActions());
        }

        return AccountEntitlementSnapshot.freeAnonymous(
                tracker.getActionsToday(),
                tracker.getRemainingActions());
    }

    private static String displayName(User user) {
        if (user == null) return "Signed in";
        String name = user.getName();
        if (name != null && !name.trim().isEmpty()) return name.trim();
        String id = user.getId();
        if (id != null && !id.trim().isEmpty()) return id.trim();
        return "Signed in";
    }
}
