package project.witty.keys.app.helpers;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * ActivationManager - Manages user activation scoring and milestone tracking.
 * <p>
 * IMPORTANT: This manager uses DEVICE ID as fallback when user is not logged in.
 * This allows tracking activation during the onboarding tutorial before login.
 * When user logs in, the device ID is saved to their Firestore profile for linking.
 * <p>
 * Activation Score Breakdown:
 * - Keyboard Enabled: +10 points
 * - First Message Typed: +15 points
 * - First AI Feature Used: +30 points (CRITICAL)
 * - Second AI Feature Used: +20 points
 * - Fifth AI Feature Used: +25 points
 * <p>
 * User is considered "activated" at 75+ points.
 */
public class ActivationManager {
    private static final String TAG = "ActivationManager";

    // EncryptedPreferences Keys
    private static final String KEY_ACTIVATION_SCORE = "activation_score";
    private static final String KEY_IS_ACTIVATED = "is_activated";
    private static final String KEY_KEYBOARD_ENABLED_MILESTONE = "milestone_keyboard_enabled";
    private static final String KEY_FIRST_MESSAGE_MILESTONE = "milestone_first_message";
    private static final String KEY_FIRST_AI_USE_MILESTONE = "milestone_first_ai_use";
    private static final String KEY_SECOND_AI_USE_MILESTONE = "milestone_second_ai_use";
    private static final String KEY_FIFTH_AI_USE_MILESTONE = "milestone_fifth_ai_use";
    private static final String KEY_AI_FEATURE_USE_COUNT = "ai_feature_use_count";
    private static final String KEY_ACTIVATION_TIMESTAMP = "activation_timestamp";
    private static final String KEY_DEVICE_ID = "device_unique_id";

    // Milestone Point Values
    private static final int POINTS_KEYBOARD_ENABLED = 10;
    private static final int POINTS_FIRST_MESSAGE = 15;
    private static final int POINTS_FIRST_AI_USE = 30;
    private static final int POINTS_SECOND_AI_USE = 20;
    private static final int POINTS_FIFTH_AI_USE = 25;

    // Activation Threshold
    private static final int ACTIVATION_THRESHOLD = 75;

    private final Context context;
    private final FirebaseAnalytics firebaseAnalytics;
    private final FirebaseFirestore firestore;

    private static boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }

    public ActivationManager(Context context) {
        this.context = context.getApplicationContext();
        this.firebaseAnalytics = FirebaseAnalytics.getInstance(this.context);
        this.firestore = FirebaseFirestore.getInstance();
        EncryptedPreferences.initialize(this.context);

        // Set Firebase user property with device ID for analytics linking
        String deviceId = getDeviceId();
        if (deviceId != null) {
            firebaseAnalytics.setUserProperty("device_id", deviceId);
        }
    }

    // ========== DEVICE ID MANAGEMENT ==========

    /**
     * Get the unique device ID. Uses Android's ANDROID_ID.
     * This ID persists across app reinstalls on Android 8.0+
     *
     * @return Device ID string, or "unknown_device" if unavailable
     */
    public String getDeviceId() {
        try {
            // First check if we have a cached device ID
            String cachedId = EncryptedPreferences.getString(KEY_DEVICE_ID, null);
            if (cachedId != null && !cachedId.isEmpty()) {
                return cachedId;
            }

            // Get Android ID
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );

            if (androidId != null && !androidId.isEmpty()) {
                // Cache it for future use
                EncryptedPreferences.saveString(KEY_DEVICE_ID, androidId);

                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "📱 Device ID generated: " + androidId.substring(0, Math.min(8, androidId.length())) + "...");
                }
                return androidId;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting device ID", e);
        }

        return "unknown_device";
    }

    /**
     * Get the tracking ID - prefers userId if logged in, falls back to device ID.
     * This ensures we can track users even before they log in.
     *
     * @return userId if logged in, otherwise deviceId
     */
    public String getTrackingId() {
        // First try to get logged-in user ID
        project.witty.keys.app.entities.User user = EncryptedPreferences.getUserLoggedInInfo();
        if (user != null && user.getId() != null && !user.getId().isEmpty()) {
            return user.getId();
        }

        // Fall back to device ID
        return "device_" + getDeviceId();
    }

    /**
     * Check if we're tracking with device ID (user not logged in)
     */
    public boolean isTrackingWithDeviceId() {
        project.witty.keys.app.entities.User user = EncryptedPreferences.getUserLoggedInInfo();
        return user == null || user.getId() == null || user.getId().isEmpty();
    }

    /**
     * Save device ID to user's Firestore profile after login.
     * Call this from your login success handler.
     *
     * @param userId The logged-in user's ID
     */
    public void linkDeviceIdToUser(String userId) {
        if (userId == null || userId.isEmpty()) return;

        String deviceId = getDeviceId();

        Map<String, Object> deviceData = new HashMap<>();
        deviceData.put("deviceId", deviceId);
        deviceData.put("deviceLinkedAt", new Date());
        deviceData.put("activationScoreAtLink", getActivationScore());
        deviceData.put("wasActivatedBeforeLogin", isActivated());

        firestore.collection("users")
                .document(userId)
                .update(deviceData)
                .addOnSuccessListener(aVoid -> {
                    if (DebugConfig.isDebugMode) {
                        Log.d(TAG, "✅ Device ID linked to user: user_present=" + hasValue(userId));
                    }

                    // Also sync full activation data now that we have a user
                    syncActivationScoreToFirestore(userId);
                })
                .addOnFailureListener(e -> {
                    // If update fails, try set with merge (user doc might not exist)
                    firestore.collection("users")
                            .document(userId)
                            .set(deviceData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(aVoid2 -> {
                                if (DebugConfig.isDebugMode) {
                                    Log.d(TAG, "✅ Device ID linked to user via merge: user_present=" + hasValue(userId));
                                }
                            })
                            .addOnFailureListener(e2 -> {
                                Log.e(TAG, "❌ Failed to link device ID to user", e2);
                            });
                });
    }

    // ========== SCORE GETTERS ==========

    /**
     * Get current activation score
     */
    public int getActivationScore() {
        return EncryptedPreferences.getInt(KEY_ACTIVATION_SCORE, 0);
    }

    /**
     * Check if user is activated (score >= 75)
     */
    public boolean isActivated() {
        return EncryptedPreferences.getBoolean(KEY_IS_ACTIVATED, false);
    }

    /**
     * Get number of times AI features have been used
     */
    public int getAiFeatureUseCount() {
        return EncryptedPreferences.getInt(KEY_AI_FEATURE_USE_COUNT, 0);
    }

    /**
     * Increment AI feature use count
     */
    private void incrementAiFeatureUseCount() {
        int currentCount = getAiFeatureUseCount();
        EncryptedPreferences.saveInt(KEY_AI_FEATURE_USE_COUNT, currentCount + 1);
    }

    // ========== MILESTONE TRACKING ==========

    /**
     * Track keyboard enabled milestone.
     * Now uses tracking ID (userId or deviceId) automatically.
     *
     * @param userId Can be null - will use device ID as fallback
     */
    public void trackKeyboardEnabled(String userId) {
        String trackingId = (userId != null && !userId.isEmpty()) ? userId : getTrackingId();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "=== KEYBOARD_ENABLE_DEBUG: ActivationManager.trackKeyboardEnabled() ===");
            Log.d(TAG, "   Timestamp: " + System.currentTimeMillis());
            Log.d(TAG, "   input_user_present: " + hasValue(userId));
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   Is Tracking With Device ID: " + isTrackingWithDeviceId());
            Log.d(TAG, "   device_id_present: " + hasValue(getDeviceId()));
            Log.d(TAG, "   Firebase Analytics: " + (firebaseAnalytics != null));
        }

        boolean alreadyTracked = EncryptedPreferences.getBoolean(KEY_KEYBOARD_ENABLED_MILESTONE, false);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "   Already Tracked (milestone_keyboard_enabled): " + alreadyTracked);
        }

        if (alreadyTracked) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "   SKIPPED: Keyboard enabled already tracked");
                Log.d(TAG, "   NOTE: activation_milestone event will NOT be sent to Firebase");
            }
            return;
        }

        // Mark as tracked
        EncryptedPreferences.saveBoolean(KEY_KEYBOARD_ENABLED_MILESTONE, true);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "   Saved milestone flag to EncryptedPreferences");
        }

        // Add points
        int newScore = addPoints(POINTS_KEYBOARD_ENABLED, "keyboard_enabled");

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "   FIRING activation_milestone event");
            Log.d(TAG, "   Milestone: keyboard_enabled");
            Log.d(TAG, "   Points: " + POINTS_KEYBOARD_ENABLED);
            Log.d(TAG, "   New Score: " + newScore);
        }

        // Log analytics event
        EventHelpers.triggerActivationMilestone(
                trackingId,
                "keyboard_enabled",
                POINTS_KEYBOARD_ENABLED,
                newScore,
                firebaseAnalytics
        );

        // Sync to Firestore (will use appropriate ID)
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "   Syncing to Firestore with trackingId: " + trackingId);
        }
        syncActivationScoreToFirestore(trackingId);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "   COMPLETE: Keyboard enabled milestone tracked successfully");
            Log.d(TAG, "   Final Score: " + newScore);
        }
    }

    /**
     * Track first message typed milestone.
     * Now uses tracking ID (userId or deviceId) automatically.
     *
     * @param userId Can be null - will use device ID as fallback
     */
    public void trackFirstMessage(String userId) {
        String trackingId = (userId != null && !userId.isEmpty()) ? userId : getTrackingId();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "✏️ trackFirstMessage called. TrackingId: " + trackingId +
                    " (isDeviceId: " + isTrackingWithDeviceId() + ")");
        }

        boolean alreadyTracked = EncryptedPreferences.getBoolean(KEY_FIRST_MESSAGE_MILESTONE, false);
        if (alreadyTracked) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "⏭️ First message already tracked, skipping");
            }
            return;
        }

        // Mark as tracked
        EncryptedPreferences.saveBoolean(KEY_FIRST_MESSAGE_MILESTONE, true);

        // Add points
        int newScore = addPoints(POINTS_FIRST_MESSAGE, "first_message");

        // Log analytics event
        EventHelpers.triggerActivationMilestone(
                trackingId,
                "first_message_typed",
                POINTS_FIRST_MESSAGE,
                newScore,
                firebaseAnalytics
        );

        // Sync to Firestore
        syncActivationScoreToFirestore(trackingId);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "✅ First message milestone tracked. New score: " + newScore);
        }
    }

    /**
     * Track AI feature usage - handles 1st, 2nd, 5th usage milestones.
     * Now uses tracking ID (userId or deviceId) automatically.
     *
     * @param userId Can be null - will use device ID as fallback
     * @param featureName Name of the AI feature used (AI_CHAT, GRAMMAR, etc.)
     */
    public void trackAiFeatureUsed(String userId, String featureName) {
        String trackingId = (userId != null && !userId.isEmpty()) ? userId : getTrackingId();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🤖 trackAiFeatureUsed called. Feature: " + featureName +
                    ", TrackingId: " + trackingId + " (isDeviceId: " + isTrackingWithDeviceId() + ")");
        }

        // Increment use count
        incrementAiFeatureUseCount();
        int useCount = getAiFeatureUseCount();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 AI Feature Use Count: " + useCount);
        }

        // Check for milestone achievements
        if (useCount == 1) {
            // First AI use - CRITICAL milestone
            boolean alreadyTracked = EncryptedPreferences.getBoolean(KEY_FIRST_AI_USE_MILESTONE, false);
            if (!alreadyTracked) {
                EncryptedPreferences.saveBoolean(KEY_FIRST_AI_USE_MILESTONE, true);
                int newScore = addPoints(POINTS_FIRST_AI_USE, "first_ai_use");

                EventHelpers.triggerActivationMilestone(
                        trackingId,
                        "first_ai_feature_used",
                        POINTS_FIRST_AI_USE,
                        newScore,
                        firebaseAnalytics
                );

                EventHelpers.triggerFirstAiFeatureUsed(trackingId, featureName, firebaseAnalytics);

                syncActivationScoreToFirestore(trackingId);

                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "🎉 FIRST AI USE MILESTONE! Score: " + newScore);
                }
            }
        } else if (useCount == 2) {
            // Second AI use
            boolean alreadyTracked = EncryptedPreferences.getBoolean(KEY_SECOND_AI_USE_MILESTONE, false);
            if (!alreadyTracked) {
                EncryptedPreferences.saveBoolean(KEY_SECOND_AI_USE_MILESTONE, true);
                int newScore = addPoints(POINTS_SECOND_AI_USE, "second_ai_use");

                EventHelpers.triggerActivationMilestone(
                        trackingId,
                        "second_ai_feature_used",
                        POINTS_SECOND_AI_USE,
                        newScore,
                        firebaseAnalytics
                );

                syncActivationScoreToFirestore(trackingId);

                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "🎯 Second AI use milestone! Score: " + newScore);
                }
            }
        } else if (useCount == 5) {
            // Fifth AI use
            boolean alreadyTracked = EncryptedPreferences.getBoolean(KEY_FIFTH_AI_USE_MILESTONE, false);
            if (!alreadyTracked) {
                EncryptedPreferences.saveBoolean(KEY_FIFTH_AI_USE_MILESTONE, true);
                int newScore = addPoints(POINTS_FIFTH_AI_USE, "fifth_ai_use");

                EventHelpers.triggerActivationMilestone(
                        trackingId,
                        "fifth_ai_feature_used",
                        POINTS_FIFTH_AI_USE,
                        newScore,
                        firebaseAnalytics
                );

                syncActivationScoreToFirestore(trackingId);

                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "🚀 Fifth AI use milestone! Score: " + newScore);
                }
            }
        }
        syncActivationScoreToFirestore(trackingId);

    }

    // ========== INTERNAL HELPERS ==========

    /**
     * Add points to activation score and check if user reached activation threshold
     */
    private int addPoints(int points, String milestoneName) {
        int currentScore = getActivationScore();
        int newScore = currentScore + points;

        // Save new score
        EncryptedPreferences.saveInt(KEY_ACTIVATION_SCORE, newScore);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "➕ Added " + points + " points for " + milestoneName + ". Score: " + currentScore + " → " + newScore);
        }

        // Check if user just reached activation threshold
        if (currentScore < ACTIVATION_THRESHOLD && newScore >= ACTIVATION_THRESHOLD) {
            markUserAsActivated();
        }

        return newScore;
    }

    /**
     * Mark user as activated when they reach 75+ points
     */
    private void markUserAsActivated() {
        EncryptedPreferences.saveBoolean(KEY_IS_ACTIVATED, true);
        EncryptedPreferences.saveLong(KEY_ACTIVATION_TIMESTAMP, System.currentTimeMillis());

        String trackingId = getTrackingId();
        EventHelpers.triggerUserActivated(trackingId, getActivationScore(), firebaseAnalytics);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎊 USER ACTIVATED! Score: " + getActivationScore() + ", TrackingId: " + trackingId);
        }
    }

    /**
     * Sync activation score to Firestore.
     * If tracking with device ID, stores in a separate 'device_activations' collection.
     * If tracking with user ID, stores in user document.
     */
    private void syncActivationScoreToFirestore(String trackingId) {
        if (trackingId == null || trackingId.isEmpty()) return;

        Map<String, Object> activationData = new HashMap<>();
        activationData.put("activationScore", getActivationScore());
        activationData.put("isActivated", isActivated());
        activationData.put("aiFeatureUseCount", getAiFeatureUseCount());
        activationData.put("lastUpdated", new Date());
        activationData.put("deviceId", getDeviceId());

        // Check if this is a device ID tracking (before login)
        if (trackingId.startsWith("device_")) {
            // Store in device_activations collection for later linking
            String deviceId = trackingId.replace("device_", "");
            activationData.put("linkedUserId", null); // Will be updated on login

            firestore.collection("device_activations")
                    .document(deviceId)
                    .set(activationData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "✅ Activation score synced to device_activations/" + deviceId);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to sync activation score to device_activations", e);
                    });
        } else {
            // Store in user document
            firestore.collection("users")
                    .document(trackingId)
                    .update(activationData)
                    .addOnSuccessListener(aVoid -> {
                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "✅ Activation score synced to users collection: tracking_id_present=" + hasValue(trackingId));
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Try set with merge if update fails
                        firestore.collection("users")
                                .document(trackingId)
                                .set(activationData, com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener(aVoid2 -> {
                                    if (DebugConfig.isDebugMode) {
                                        Log.d(TAG, "✅ Activation score synced to users collection via merge: tracking_id_present=" + hasValue(trackingId));
                                    }
                                })
                                .addOnFailureListener(e2 -> {
                                    Log.e(TAG, "❌ Failed to sync activation score to Firestore", e2);
                                });
                    });
        }
    }

    // ========== DEBUG & RESET ==========

    /**
     * Reset all activation data (for testing/debugging)
     */
    public void resetActivationData() {
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🔄 Resetting all activation data");
        }

        EncryptedPreferences.saveInt(KEY_ACTIVATION_SCORE, 0);
        EncryptedPreferences.saveBoolean(KEY_IS_ACTIVATED, false);
        EncryptedPreferences.saveBoolean(KEY_KEYBOARD_ENABLED_MILESTONE, false);
        EncryptedPreferences.saveBoolean(KEY_FIRST_MESSAGE_MILESTONE, false);
        EncryptedPreferences.saveBoolean(KEY_FIRST_AI_USE_MILESTONE, false);
        EncryptedPreferences.saveBoolean(KEY_SECOND_AI_USE_MILESTONE, false);
        EncryptedPreferences.saveBoolean(KEY_FIFTH_AI_USE_MILESTONE, false);
        EncryptedPreferences.saveInt(KEY_AI_FEATURE_USE_COUNT, 0);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "✅ Activation data reset complete");
        }
    }

    /**
     * Get detailed activation status for debugging
     */
    public String getActivationStatusDebug() {
        return "Activation Status:\n" +
                "TrackingId: " + getTrackingId() + "\n" +
                "DeviceId: " + getDeviceId() + "\n" +
                "IsTrackingWithDeviceId: " + isTrackingWithDeviceId() + "\n" +
                "Score: " + getActivationScore() + " / " + ACTIVATION_THRESHOLD + "\n" +
                "Activated: " + isActivated() + "\n" +
                "AI Use Count: " + getAiFeatureUseCount() + "\n" +
                "Milestones:\n" +
                "  - Keyboard Enabled: " + EncryptedPreferences.getBoolean(KEY_KEYBOARD_ENABLED_MILESTONE, false) + "\n" +
                "  - First Message: " + EncryptedPreferences.getBoolean(KEY_FIRST_MESSAGE_MILESTONE, false) + "\n" +
                "  - First AI Use: " + EncryptedPreferences.getBoolean(KEY_FIRST_AI_USE_MILESTONE, false) + "\n" +
                "  - Second AI Use: " + EncryptedPreferences.getBoolean(KEY_SECOND_AI_USE_MILESTONE, false) + "\n" +
                "  - Fifth AI Use: " + EncryptedPreferences.getBoolean(KEY_FIFTH_AI_USE_MILESTONE, false);
    }
}
