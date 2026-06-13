package project.witty.keys.app.entities;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableReference;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import project.witty.keys.app.AuthenticationActivity;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.utils.DailyUsageTracker;

public class Subscription {
    private static final String TAG = "Subscription";
    private static final String SUBSCRIPTIONS = "subscriptions";

    public static final String PACKAGE_ID = "package_id";
    public static final String USER_ID = "user_id";
    public static final String CONSUMED_TOKENS = "consumed_tokens";
    public static final String SUBSCRIPTION_END_DATE = "subscription_end_date";
    public static final String SUBSCRIPTION_RENEWAL_DATE = "subscription_renewal_date";
    public static final String STATUS = "status";
    public static final String LAST_UPDATED = "lastUpdated";
    public static final String DATE_CREATED = "dateCreated";
    public static final String PURCHASE_TOKEN = "purchase_token";
    public static final String SUBSCRIPTION_ID_GOOGLE = "subscription_id_google";
    public static final String NAME = "name";
    public static final String OFFER = "offer";
    public static final String FT_PACKAGE_ID_STRING = "FREE_TRIAL";
    public static final String FT_PACKAGE_NAME_STRING = "Free Trial";
    public static final int MAX_FREE_TRIAL_TOKENS = 2000;
    public enum SubscriptionStatus {
        ACTIVE,
        INACTIVE,
        USER_CANCELLED,
        MANDATE_CANCELLED,
        PAYMENT_FAILED,
        EXPIRED,
        RENEWED,
    }

    public interface PaidEntitlementSyncCallback {
        void onSynced(boolean paidActive);
    }

    private String package_id; // package_id
    private String userId;
    private int consumedTokens;
    private Date subscriptionMandateEndDate;
    private Date subscriptionRenewalDate;
    private SubscriptionStatus status; // Use enum
    private Date lastUpdated;
    private Date dateCreated;
    private String transactionId; // info from Google
    private String name;

    public Subscription() {
    }

    public Subscription(String package_id, String userId, int consumedTokens, Date subscriptionMandateEndDate, Date subscriptionRenewalDate, SubscriptionStatus status, Date lastUpdated, Date dateCreated, String transactionId, String name) {
        this.package_id = package_id;
        this.userId = userId;
        this.consumedTokens = consumedTokens;
        this.subscriptionMandateEndDate = subscriptionMandateEndDate;
        this.subscriptionRenewalDate = subscriptionRenewalDate;
        this.status = status;
        this.lastUpdated = lastUpdated;
        this.dateCreated = dateCreated;
        this.transactionId = transactionId;
        this.name = name;
    }

    // Getters and Setters
    public String getPackageId() {
        return package_id;
    }

    public void setPackageId(String package_id) {
        this.package_id = package_id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getConsumedTokens() {
        return consumedTokens;
    }

    public void setConsumedTokens(int consumedTokens) {
        this.consumedTokens = consumedTokens;
    }

    public Date getSubscriptionMandateEndDate() {
        return subscriptionMandateEndDate;
    }

    public void setSubscriptionMandateEndDate(Date subscriptionMandateEndDate) {
        this.subscriptionMandateEndDate = subscriptionMandateEndDate;
    }

    public Date getSubscriptionRenewalDate() {
        return subscriptionRenewalDate;
    }

    public void setSubscriptionRenewalDate(Date subscriptionRenewalDate) {
        this.subscriptionRenewalDate = subscriptionRenewalDate;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Method to save a new subscription and mark the previous one as inactive
    public static void saveFreeTrialSubscription(String userId, FirebaseFirestore db, Runnable onSuccess) {
        // Mark the previous subscription as inactive
        markPreviousSubscriptionInactive(userId, db, () -> {
            // Create a new subscription
            Map<String, Object> subscription = new HashMap<>();
            subscription.put(PACKAGE_ID, FT_PACKAGE_ID_STRING);
            subscription.put(USER_ID, userId);
            subscription.put(CONSUMED_TOKENS, 0);
            subscription.put(SUBSCRIPTION_END_DATE, new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3)));
            subscription.put(SUBSCRIPTION_RENEWAL_DATE, null);
            subscription.put(STATUS, SubscriptionStatus.ACTIVE.name());
            subscription.put(LAST_UPDATED, new Date());
            subscription.put(DATE_CREATED, new Date());
            subscription.put(NAME, FT_PACKAGE_NAME_STRING);

            db.collection(SUBSCRIPTIONS).add(subscription)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Free trial subscription added to Firestore");
                        EncryptedPreferences.saveFreeTrialInfo(0, false, SubscriptionStatus.ACTIVE);
                        onSuccess.run();
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "Error adding free trial subscription to Firestore", e));
        });
    }

    public static void saveSubscription(String userId, String packageId, String name, String purchaseToken, String subscriptionIdGoogle, Date endDate, Date renewalDate, String couponCode, FirebaseFirestore db, Runnable onSuccess) {
        // Mark the previous subscription as inactive
        markPreviousSubscriptionInactive(userId, db, () -> {
            // Create a new subscription
            Map<String, Object> subscription = new HashMap<>();
            subscription.put(PACKAGE_ID, packageId);
            subscription.put(USER_ID, userId);
            subscription.put(CONSUMED_TOKENS, 0);
            subscription.put(SUBSCRIPTION_END_DATE, endDate);
            subscription.put(SUBSCRIPTION_RENEWAL_DATE, renewalDate);
            subscription.put(STATUS, SubscriptionStatus.ACTIVE.name());
            subscription.put(LAST_UPDATED, new Date());
            subscription.put(DATE_CREATED, new Date());
            subscription.put(PURCHASE_TOKEN, purchaseToken);
            subscription.put(SUBSCRIPTION_ID_GOOGLE, subscriptionIdGoogle);
            subscription.put(OFFER, couponCode);
            subscription.put(NAME, name);

            db.collection(SUBSCRIPTIONS).add(subscription)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Subscription added to Firestore");
                        EncryptedPreferences.saveSubscriptionInfo(name, packageId, endDate.getTime(), 0, SubscriptionStatus.ACTIVE);
                        onSuccess.run();
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "Error adding subscription to Firestore", e));
        });
    }

    // Method to mark the previous subscription as inactive
    private static void markPreviousSubscriptionInactive(String userId, FirebaseFirestore db, Runnable onSuccess) {
        db.collection(SUBSCRIPTIONS)
                .whereEqualTo(USER_ID, userId)
                .whereEqualTo(STATUS, SubscriptionStatus.ACTIVE.name())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            for (QueryDocumentSnapshot document : querySnapshot) {
                                document.getReference().update(STATUS, SubscriptionStatus.INACTIVE.name());
                            }
                        }
                        onSuccess.run();
                    } else {
                        Log.w(TAG, "Error marking previous subscription as inactive", task.getException());
                    }
                });
    }

    // Method to check the free trial status
    public static void getExistingSubscription(String userId, FirebaseFirestore db, Runnable onSuccess) {
        db.collection(SUBSCRIPTIONS)
                .whereEqualTo(USER_ID, userId)
                .whereNotEqualTo(STATUS, SubscriptionStatus.RENEWED.name())
                .orderBy(LAST_UPDATED, Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                            String packageId = document.getString(PACKAGE_ID);
                            if (FT_PACKAGE_ID_STRING.equals(packageId)) {
                                int freeTrialTokens = document.getLong(CONSUMED_TOKENS).intValue();
                                boolean isFreeTrialEnded = new Date().after(document.getDate(SUBSCRIPTION_END_DATE));
                                SubscriptionStatus status = SubscriptionStatus.valueOf(document.getString(STATUS));
                                EncryptedPreferences.saveFreeTrialInfo(freeTrialTokens, isFreeTrialEnded, status);
                                Log.d(TAG, "Free trial subscription fetched and saved locally.");
                            } else {
                                String name = document.getString(NAME);
                                String id = document.getString(PACKAGE_ID);
                                long expiry = document.getDate(SUBSCRIPTION_END_DATE).getTime();
                                int tokensConsumed = document.getLong(CONSUMED_TOKENS).intValue();
                                SubscriptionStatus status = SubscriptionStatus.valueOf(document.getString(STATUS));
                                EncryptedPreferences.saveSubscriptionInfo(name, id, expiry, tokensConsumed, status);
                                Log.d(TAG, "Subscription fetched and saved locally.");
                            }
                        }
                        onSuccess.run();
                    } else {
                        Log.w(TAG, "Error fetching subscription from Firestore", task.getException());
                    }
                });
    }

    // Method to fetch the latest subscription from Firestore
    public static void fetchSubscriptionFromFirestore(String userId, FirebaseFirestore db) {
        db.collection(SUBSCRIPTIONS)
                .whereEqualTo(USER_ID, userId)
                .whereNotEqualTo(STATUS, SubscriptionStatus.RENEWED.name())
                .orderBy(LAST_UPDATED, Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                            String packageId = document.getString(PACKAGE_ID);
                            if (FT_PACKAGE_ID_STRING.equals(packageId)) {
                                int freeTrialTokens = document.getLong(CONSUMED_TOKENS).intValue();
                                boolean isFreeTrialEnded = new Date().after(document.getDate(SUBSCRIPTION_END_DATE));
                                SubscriptionStatus status = SubscriptionStatus.valueOf(document.getString(STATUS));
                                EncryptedPreferences.saveFreeTrialInfo(freeTrialTokens, isFreeTrialEnded, status);
                                Log.d(TAG, "Free trial subscription fetched and saved locally.");
                            } else {
                                String name = document.getString(NAME);
                                String id = document.getString(PACKAGE_ID);
                                long expiry = document.getDate(SUBSCRIPTION_END_DATE).getTime();
                                int tokensConsumed = document.getLong(CONSUMED_TOKENS).intValue();
                                SubscriptionStatus status = SubscriptionStatus.valueOf(document.getString(STATUS));
                                EncryptedPreferences.saveSubscriptionInfo(name, id, expiry, tokensConsumed, status);
                                Log.d(TAG, "Subscription fetched and saved locally.");
                            }
                        } else {
                            Log.w(TAG, "Subscription document does not exist.");
                        }
                    } else {
                        Log.w(TAG, "Error fetching subscription from Firestore", task.getException());
                    }
                });
    }

    public static void getLatestSubscription(String userId, FirebaseFirestore db, OnCompleteListener<QuerySnapshot> onCompleteListener) {
        db.collection(SUBSCRIPTIONS)
                .whereEqualTo(USER_ID, userId)
                .whereEqualTo(STATUS, SubscriptionStatus.ACTIVE.name())
                .orderBy(LAST_UPDATED, Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnCompleteListener(onCompleteListener);
    }

    public static void syncPaidEntitlementFromFirestore(Context context, String userId, FirebaseFirestore db, PaidEntitlementSyncCallback callback) {
        if (context == null || userId == null || userId.trim().isEmpty() || db == null) {
            if (context != null) {
                DailyUsageTracker.getInstance(context).setUnlimited(false);
            }
            if (callback != null) callback.onSynced(false);
            return;
        }

        getLatestSubscription(userId, db, task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Paid entitlement sync failed", task.getException());
                if (callback != null) {
                    callback.onSynced(DailyUsageTracker.getInstance(context).isUnlimited());
                }
                return;
            }

            QuerySnapshot querySnapshot = task.getResult();
            boolean isPaidSub = false;

            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                String subscriptionPackageId = document.getString(PACKAGE_ID);
                String statusString = document.getString(STATUS);
                SubscriptionStatus firestoreStatus = SubscriptionStatus.ACTIVE;
                try {
                    if (statusString != null && !statusString.isEmpty()) {
                        firestoreStatus = SubscriptionStatus.valueOf(statusString);
                    }
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Invalid paid entitlement status from Firestore: " + statusString);
                    firestoreStatus = SubscriptionStatus.INACTIVE;
                }

                isPaidSub = subscriptionPackageId != null
                        && !FT_PACKAGE_ID_STRING.equals(subscriptionPackageId)
                        && firestoreStatus == SubscriptionStatus.ACTIVE;

                if (isPaidSub) {
                    String name = document.getString(NAME);
                    Date endDate = document.getDate(SUBSCRIPTION_END_DATE);
                    Long consumedTokens = document.getLong(CONSUMED_TOKENS);
                    EncryptedPreferences.saveSubscriptionInfo(
                            name != null ? name : subscriptionPackageId,
                            subscriptionPackageId,
                            endDate != null ? endDate.getTime() : 0,
                            consumedTokens != null ? consumedTokens.intValue() : 0,
                            firestoreStatus);
                } else if (FT_PACKAGE_ID_STRING.equals(subscriptionPackageId)) {
                    Long freeTrialTokens = document.getLong(CONSUMED_TOKENS);
                    Date endDate = document.getDate(SUBSCRIPTION_END_DATE);
                    boolean isFreeTrialEnded = endDate != null && new Date().after(endDate);
                    EncryptedPreferences.clearSubscriptionInfo();
                    EncryptedPreferences.saveFreeTrialInfo(
                            freeTrialTokens != null ? freeTrialTokens.intValue() : 0,
                            isFreeTrialEnded,
                            firestoreStatus);
                }
            } else {
                EncryptedPreferences.clearSubscriptionInfo();
            }

            DailyUsageTracker.getInstance(context).setUnlimited(isPaidSub);
            if (callback != null) callback.onSynced(isPaidSub);
            Log.d(TAG, "Paid entitlement synced from Firestore: " + isPaidSub);
        });
    }

    public static void acknowledgePurchase(Purchase purchase, String couponCode, BillingClient billingClient, Context context, Runnable onSuccess, Runnable hideLoadingOverlay) {
        AcknowledgePurchaseParams acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
        billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                // Purchase acknowledged
                Log.d(TAG, "Purchase acknowledged successfully");
                User user = EncryptedPreferences.getUserLoggedInInfo();
                // Save subscription after acknowledgment
                String packageId = purchase.getProducts().get(0);
                String name = formatSubscriptionName(packageId); // Replace with actual subscription name
                String transactionId = purchase.getPurchaseToken();
                String subscriptionIdGoogle = purchase.getOrderId();
                // End date will be 5 years from the current date
                Date endDate = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(5 * 365));

                // Renewal date will be 1 month after the current date for monthly subscriptions
                Date renewalDateMonthly = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30));

                // Renewal date will be 1 year after the current date for yearly subscriptions
                Date renewalDateYearly = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365));
                Subscription.saveSubscription(user.getId(), packageId, name, transactionId, subscriptionIdGoogle, endDate, packageId.contains("monthly") ? renewalDateMonthly : renewalDateYearly, couponCode, FirebaseFirestore.getInstance(), onSuccess);

            } else {
                // Handle the error
                hideLoadingOverlay.run();
                Log.e(TAG, "Error acknowledging purchase: " + billingResult.getDebugMessage());
                Toast.makeText(context, "Error acknowledging purchase. Please try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    public static String formatSubscriptionName(String subscriptionId) {
        String[] words = subscriptionId.split("_");
        StringBuilder formattedName = new StringBuilder();
        for (String word : words) {
            formattedName.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return formattedName.toString().trim();
    }
}
