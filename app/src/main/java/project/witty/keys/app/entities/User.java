package project.witty.keys.app.entities;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import project.witty.keys.app.helpers.ActivationManager;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.NotificationService;


public class User {
    public static final String USERS = "users";
    private static final String TAG = "User";
    public static final String LAST_UPDATED = "lastUpdated";
    public static final String DATE_CREATED = "dateCreated";

    private String name;
    private String id; // Phone / email
    private String token;
    private String obfuscatedAccountId;
    private String fcmToken;
    private Date lastUpdated;
    private Date dateCreated;

    public User() {
    }

    public User(String name, String id, String token, String fcmToken, Date lastUpdated, Date dateCreated) {
        this.name = name;
        this.id = id;
        this.token = token;
        this.obfuscatedAccountId = getObfuscatedAccountId(id);
        this.fcmToken = fcmToken;
        this.lastUpdated = lastUpdated;
        this.dateCreated = lastUpdated;
    }

    public User(String name, String id, String token) {
        this.name = name;
        this.id = id;
        this.token = token;
        this.obfuscatedAccountId = getObfuscatedAccountId(id);
    }

    public User(String name, String id) {
        this.name = name;
        this.id = id;
        this.token = null;
        this.obfuscatedAccountId = getObfuscatedAccountId(id);
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public String getObfuscatedAccountId() {
        return obfuscatedAccountId;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setObfuscatedAccountId(String obfuscatedAccountId) {
        this.obfuscatedAccountId = obfuscatedAccountId;
    }


    @Override
    public String toString() {
        return "User{" +
                "namePresent=" + hasValue(name) +
                ", idPresent=" + hasValue(id) +
                ", tokenPresent=" + hasValue(token) +
                ", fcmTokenPresent=" + hasValue(fcmToken) +
                ", dateCreatedPresent=" + (dateCreated != null) +
                ", lastUpdatedPresent=" + (lastUpdated != null) +
                '}';
    }

    // ========== UPDATED: Added Context parameter for activation linking ==========

    /**
     * Check if user exists in Firestore, or create new user if not.
     * Also links device activation data to user account.
     *
     * @param context Application context for ActivationManager
     * @param user FirebaseUser object
     * @param userId User's unique ID
     * @param userName User's display name
     * @param db Firestore instance
     * @param onSuccess Callback on success
     */
    public static void checkUserInFirestore(Context context, FirebaseUser user, String userId, String userName, FirebaseFirestore db, Runnable onSuccess) {
        if (userId == null) {
            Log.w(TAG, "User ID is null. Cannot check user.");
            return;
        }
        String fcmToken = EncryptedPreferences.getFcmToken();
        Log.d(TAG, "FCM token loaded: tokenPresent=" + hasValue(fcmToken));
        db.collection(USERS).document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document != null && document.exists()) {
                            // EXISTING USER
                            User existingUser = document.toObject(User.class);
                            if (existingUser != null) {
                                EncryptedPreferences.saveUserInfoLocally(existingUser);

                                // ✅ NEW: Link device activation data to existing user
                                linkDeviceActivationToUser(context, userId);

                                Subscription.getExistingSubscription(userId, db, onSuccess);
                                updateFcmTokenInFirestore(userId, fcmToken, db);
                            } else {
                                Log.w(TAG, "Error converting Firestore document to User object.");
                            }
                        } else {
                            // NEW USER - pass context to saveUserInFirestore
                            saveUserInFirestore(context, user, userId, userName, db, onSuccess);
                        }

                    } else {
                        Log.w(TAG, "Error checking user in Firestore", task.getException());
                    }
                });
    }

    /**
     * Save new user to Firestore.
     * Also links device activation data to user account.
     *
     * @param context Application context for ActivationManager
     * @param user FirebaseUser object
     * @param userId User's unique ID
     * @param userName User's display name
     * @param db Firestore instance
     * @param onSuccess Callback on success
     */
    public static void saveUserInFirestore(Context context, FirebaseUser user, String userId, String userName, FirebaseFirestore db, Runnable onSuccess) {
        if (userId == null) {
            Log.w(TAG, "User ID is null. Cannot save user.");
            return;
        }
        String fcmToken = EncryptedPreferences.getFcmToken();
        User createdUser = new User(userName, userId, null, fcmToken, new Date(), new Date());
        db.collection(USERS).document(userId).set(createdUser)
                .addOnSuccessListener(aVoid -> {
                    EncryptedPreferences.saveUserInfoLocally(createdUser);

                    // ✅ NEW: Link device activation data to new user
                    linkDeviceActivationToUser(context, userId);

                    Subscription.saveFreeTrialSubscription(userId, db, onSuccess);
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error adding user to Firestore", e));
    }

    // ========== NEW: Backward compatible overloads (without context) ==========

    /**
     * @deprecated Use {@link #checkUserInFirestore(Context, FirebaseUser, String, String, FirebaseFirestore, Runnable)} instead
     * This overload exists for backward compatibility but won't link device activation.
     */
    @Deprecated
    public static void checkUserInFirestore(FirebaseUser user, String userId, String userName, FirebaseFirestore db, Runnable onSuccess) {
        Log.w(TAG, "⚠️ checkUserInFirestore called without context - device activation won't be linked!");
        checkUserInFirestore(null, user, userId, userName, db, onSuccess);
    }

    /**
     * @deprecated Use {@link #saveUserInFirestore(Context, FirebaseUser, String, String, FirebaseFirestore, Runnable)} instead
     * This overload exists for backward compatibility but won't link device activation.
     */
    @Deprecated
    public static void saveUserInFirestore(FirebaseUser user, String userId, String userName, FirebaseFirestore db, Runnable onSuccess) {
        Log.w(TAG, "⚠️ saveUserInFirestore called without context - device activation won't be linked!");
        saveUserInFirestore(null, user, userId, userName, db, onSuccess);
    }

    // ========== NEW: Helper method for activation linking ==========

    /**
     * Links device activation data to user account after login.
     * This transfers any activation milestones achieved during onboarding
     * (before login) to the user's account.
     *
     * @param context Application context
     * @param userId User's ID to link to
     */
    private static void linkDeviceActivationToUser(Context context, String userId) {
        if (context == null) {
            Log.w(TAG, "⚠️ Context is null, cannot link device activation to user");
            return;
        }

        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "⚠️ UserId is null or empty, cannot link device activation");
            return;
        }

        try {
            ActivationManager activationManager = new ActivationManager(context);
            activationManager.linkDeviceIdToUser(userId);

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "✅ Device activation linked to user: user_present=" + hasValue(userId));
                Log.d(TAG, "📊 Activation status after link:\n" + activationManager.getActivationStatusDebug());
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error linking device activation to user", e);
        }
    }

    // ========== UNCHANGED METHODS ==========

    public static void fetchUserFromFirestore(String userId, FirebaseFirestore db) {
        db.collection(User.USERS).document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document != null && document.exists()) {
                            User user = document.toObject(User.class);
                            if (user != null) {
                                EncryptedPreferences.saveUserInfoLocally(user);
                                Log.d(TAG, "User fetched and saved locally: idPresent=" + hasValue(user.id)
                                        + ", tokenPresent=" + hasValue(user.token));
                            } else {
                                Log.w(TAG, "Error converting Firestore document to User object.");
                            }
                        } else {
                            Log.w(TAG, "User document does not exist.");
                        }
                    } else {
                        Log.w(TAG, "Error fetching user from Firestore", task.getException());
                    }
                });
    }

    private String getObfuscatedAccountId(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "User ID is null or empty. Cannot obfuscate.");
            return null;
        }

        try {
            // SHA-256 hashing
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userId.getBytes(StandardCharsets.UTF_8));

            // Convert byte array to hexadecimal string
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 algorithm not available.", e);
            return null; // Or throw an exception if you prefer
        }
    }

    public static void updateFcmTokenInFirestore(String userId, String newFcmToken, FirebaseFirestore db) {
        if (userId == null || newFcmToken == null) {
            Log.w(TAG, "User id or FCM token missing. Cannot update FCM token.");
            return;
        }

        db.collection(USERS).document(userId)
                .update("fcmToken", newFcmToken)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated successfully"))
                .addOnFailureListener(e -> Log.w(TAG, "Error updating FCM token", e));
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }
}
