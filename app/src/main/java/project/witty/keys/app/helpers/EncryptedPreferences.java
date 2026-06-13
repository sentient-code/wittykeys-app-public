// EncryptedPreferences.java
package project.witty.keys.app.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;

import project.witty.keys.app.entities.Subscription;
import project.witty.keys.app.entities.User;

public class EncryptedPreferences {

    private static final String TAG = "EncryptedPrefs";
    private static final String PREFS_FILE_NAME = "secret_shared_prefs"; // Consistent file name
    private static SharedPreferences sharedPreferences;

    // Extracted string keys
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_OBFUSCATED_ID = "user_obfuscated_id";

    //free trial info
    private static final String KEY_FREE_TRIAL_TOKENS = "freeTrialTokens";
    private static final String KEY_IS_FREE_TRIAL_ENDED = "isFreeTrialEnded";
    private static final String KEY_FT_STATUS = "ftStatus";

    //subscription info
    private static final String KEY_SUBSCRIPTION_NAME = "subscriptionName";
    private static final String KEY_SUBSCRIPTION_PACKAGE_ID = "subscriptionPackageId";
    private static final String KEY_SUBSCRIPTION_EXPIRY = "subscriptionExpiry";
    private static final String KEY_TOKENS_CONSUMED = "tokensConsumed";
    private static final String KEY_STATUS = "status";

    private static final String FCM_TOKEN = "fcm_token";

    public static void initialize(Context context) {
        if (sharedPreferences == null) { // Initialize only once
            synchronized (EncryptedPreferences.class) { // Thread safety
                if (sharedPreferences == null) {
                    Context applicationContext = context.getApplicationContext();
                    sharedPreferences = createSecurePreferences(applicationContext);
                    sharedPreferences.edit().remove("user_token").apply();
                }
            }
        }
    }

    private static SharedPreferences createSecurePreferences(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    PREFS_FILE_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            if (isJvmUnitTestRuntime()) {
                return context.getSharedPreferences(PREFS_FILE_NAME + "_test", Context.MODE_PRIVATE);
            }
            throw new IllegalStateException("Unable to initialize encrypted preferences.", e);
        }
    }

    private static boolean isJvmUnitTestRuntime() {
        return !"Dalvik".equals(System.getProperty("java.vm.name"));
    }

    public static void saveString(String key, String value) {
        checkInitialized();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public static void saveLong(String key, Long value) {
        checkInitialized();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    public static String getString(String key, String defaultValue) {
        checkInitialized();
        return sharedPreferences.getString(key, defaultValue);
    }

    public static void saveInt(String key, int value) {
        checkInitialized();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public static int getInt(String key, int defaultValue) {
        checkInitialized();
        return sharedPreferences.getInt(key, defaultValue);
    }

    public static void saveBoolean(String key, boolean value) {
        checkInitialized();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        checkInitialized();
        return sharedPreferences.getBoolean(key, defaultValue);
    }

    public static void clearAll() {
        checkInitialized();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    private static void checkInitialized() {
        if (sharedPreferences == null) {
            throw new IllegalStateException("EncryptedPreferences must be initialized in your Application class.");
        }
    }

    // New methods for freeTrialInfo
    public static void saveFreeTrialInfo(int freeTrialTokens, boolean isFreeTrialEnded, Subscription.SubscriptionStatus status) {
        Log.d(TAG, "Saving free trial info: " + freeTrialTokens + ", " + isFreeTrialEnded + ", " + status);
        saveInt(KEY_FREE_TRIAL_TOKENS, freeTrialTokens);
        saveBoolean(KEY_IS_FREE_TRIAL_ENDED, isFreeTrialEnded);
        saveString(KEY_FT_STATUS, status.toString());
    }
    public static void saveFreeTrialTokenConsumed(int tokensConsumed) {
        saveInt(KEY_FREE_TRIAL_TOKENS, getInt(KEY_FREE_TRIAL_TOKENS, 0) + tokensConsumed);
    }

    public static void saveSubscriptionTokenConsumed(int tokensConsumed) {
        saveInt(KEY_TOKENS_CONSUMED, getInt(KEY_TOKENS_CONSUMED, 0) + tokensConsumed);
    }

    public static FreeTrialInfo getFreeTrialInfo() {
        int freeTrialTokens = getInt(KEY_FREE_TRIAL_TOKENS, 0);
        boolean isFreeTrialEnded = getBoolean(KEY_IS_FREE_TRIAL_ENDED, true);
        String ftStatus = getString(KEY_FT_STATUS, null);
        Log.d(TAG, "Retrieved free trial info: " + freeTrialTokens + ", " + isFreeTrialEnded + ", " + ftStatus);
        if (ftStatus == null) {
            return null;
        }
        return new FreeTrialInfo(freeTrialTokens, isFreeTrialEnded, Subscription.SubscriptionStatus.valueOf(ftStatus));
    }

    // New methods for subscriptionInfo
    public static void saveSubscriptionInfo(String name, String id, long expiry, int tokensConsumed, Subscription.SubscriptionStatus status) {
        clearFreeTrialInfo();
        saveString(KEY_SUBSCRIPTION_NAME, name);
        saveString(KEY_SUBSCRIPTION_PACKAGE_ID, id);
        saveLong(KEY_SUBSCRIPTION_EXPIRY, expiry);
        saveInt(KEY_TOKENS_CONSUMED, tokensConsumed);
        saveString(KEY_STATUS, status.toString());
    }

    public static SubscriptionInfo getSubscriptionInfo() {
        String name = getString(KEY_SUBSCRIPTION_NAME, null);
        String id = getString(KEY_SUBSCRIPTION_PACKAGE_ID, null);
        long expiry = getLong(KEY_SUBSCRIPTION_EXPIRY, 0);
        int tokensConsumed = getInt(KEY_TOKENS_CONSUMED, 0);
        String status = getString(KEY_STATUS, null);
        if (id == null || name == null || status == null) {
            return null;
        }
        return new SubscriptionInfo(name, id, expiry, tokensConsumed, Subscription.SubscriptionStatus.valueOf(status));
    }

    // New method for userLoggedInInfo
    public static User getUserLoggedInInfo() {
        String name = getString(KEY_USER_NAME, null);
        String id = getString(KEY_USER_ID, null);
        if (name == null || id == null) {
            return null;
        }
        return new User(name, id);
    }

    public static void saveUserInfoLocally(User user) {
        saveString(KEY_USER_NAME, user.getName());
        saveString(KEY_USER_ID, user.getId());
        saveString(KEY_USER_OBFUSCATED_ID, user.getObfuscatedAccountId());
    }

    // Helper method to get long value
    public static long getLong(String key, long defaultValue) {
        checkInitialized();
        return sharedPreferences.getLong(key, defaultValue);
    }

    // FreeTrialInfo class
    public static class FreeTrialInfo {
        private int freeTrialTokens;
        private boolean isFreeTrialEnded;
        private String ftStatus;

        public FreeTrialInfo(int freeTrialTokens, boolean isFreeTrialEnded, Subscription.SubscriptionStatus status) {
            this.freeTrialTokens = freeTrialTokens;
            this.isFreeTrialEnded = isFreeTrialEnded;
            this.ftStatus = status.toString();
        }

        public int getFreeTrialTokens() {
            return freeTrialTokens;
        }

        public boolean isFreeTrialEnded() {
            return isFreeTrialEnded;
        }

        public String getFtStatus() {
            return ftStatus;
        }

    }

    // SubscriptionInfo class
    public static class SubscriptionInfo {
        private String name;
        private String packageId;
        private long expiry;
        private int tokensConsumed;
        private String status;

        public SubscriptionInfo(String name, String id, long expiry, int tokensConsumed, Subscription.SubscriptionStatus status) {
            this.name = name;
            this.packageId = id;
            this.expiry = expiry;
            this.tokensConsumed = tokensConsumed;
            this.status = status.toString();
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return packageId;
        }

        public long getExpiry() {
            return expiry;
        }

        public int getTokensConsumed() {
            return tokensConsumed;
        }
        public String getStatus() {
            return status;
        }

        public String getPackageId() {
            return packageId;
        }
    }

    // Methods to clear user, subscription, and free trial information
    public static void clearUserInfo() {
        saveString(KEY_USER_NAME, null);
        saveString(KEY_USER_ID, null);
        saveString(KEY_USER_OBFUSCATED_ID, null);
        sharedPreferences.edit().remove("user_token").apply();
    }

    public static void clearSubscriptionInfo() {
        saveString(KEY_SUBSCRIPTION_NAME, null);
        saveString(KEY_SUBSCRIPTION_PACKAGE_ID, null);
        saveLong(KEY_SUBSCRIPTION_EXPIRY, 0L);
        saveInt(KEY_TOKENS_CONSUMED, 0);
        saveString(KEY_STATUS, null);
    }

    public static void clearFreeTrialInfo() {
        saveInt(KEY_FREE_TRIAL_TOKENS, 0);
        saveBoolean(KEY_IS_FREE_TRIAL_ENDED, false);
        saveString(KEY_FT_STATUS,null);
    }

    public static void saveFcmToken(String fcmToken) {
        saveString(FCM_TOKEN, fcmToken);
    }

    public static String getFcmToken() {
        return getString(FCM_TOKEN, null);
    }

}
