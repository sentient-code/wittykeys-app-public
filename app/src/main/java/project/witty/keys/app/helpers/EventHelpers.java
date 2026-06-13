package project.witty.keys.app.helpers;

import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import android.content.Context;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Static helper class that encapsulates all analytics event logging for WittyKeys.
 * Each method ensures the user ID (if present) is applied to the analytics instance
 * and then logs a named event with relevant parameters.
 */
public class EventHelpers {

    private static final String TAG = "EventHelpers";

    private static boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }

    private static String safeAnalyticsId(String rawId) {
        if (!hasValue(rawId)) {
            return null;
        }
        return "wk_" + hashIdentifier(rawId);
    }

    private static String hashIdentifier(String rawId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String value = Integer.toHexString(0xff & b);
                if (value.length() == 1) {
                    hex.append('0');
                }
                hex.append(value);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(rawId.hashCode());
        }
    }

    private static void attachSafeUser(Bundle bundle, String rawId, FirebaseAnalytics analytics) {
        String safeId = safeAnalyticsId(rawId);
        if (safeId == null) {
            return;
        }
        bundle.putString("analytics_user_id", safeId);
        analytics.setUserId(safeId);
    }

    public static void triggerAppLaunchEvent(String user_id, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, bundle);
    }

    public static void triggerKeyboardSetEvent(String user_id, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        mFirebaseAnalytics.logEvent("keyboard_set", bundle);
    }

    public static void triggerKeyboardEnabledEvent(String user_id, FirebaseAnalytics mFirebaseAnalytics) {
        final String TAG = "EventHelpers";

        // DEBUG: Entry logging
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "=== KEYBOARD_ENABLE_DEBUG: triggerKeyboardEnabledEvent() ===");
            Log.d(TAG, "   Timestamp: " + System.currentTimeMillis());
            Log.d(TAG, "   user_present: " + hasValue(user_id));
            Log.d(TAG, "   Firebase Analytics: " + (mFirebaseAnalytics != null));
        }

        // Safety check for Firebase Analytics
        if (mFirebaseAnalytics == null) {
            Log.e(TAG, "   ERROR: FirebaseAnalytics is null! keyboard_enabled event NOT sent.");
            return;
        }

        // Warn if user_id is null/empty
        if (user_id == null || user_id.isEmpty()) {
            Log.w(TAG, "   WARNING: user_id is null/empty. Event will have empty label.");
        }

        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        mFirebaseAnalytics.logEvent("keyboard_enabled", bundle);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "   SUCCESS: keyboard_enabled event logged to Firebase");
            Log.d(TAG, "   Event Name: keyboard_enabled");
            Log.d(TAG, "   label_present: " + hasValue(user_id));
        }
    }

    public static void triggerKeyboardDisabledEvent(String user_id, FirebaseAnalytics mFirebaseAnalytics) {
        final String TAG = "EventHelpers";

        // DEBUG: Entry logging
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "=== KEYBOARD_ENABLE_DEBUG: triggerKeyboardDisabledEvent() ===");
            Log.d(TAG, "   Timestamp: " + System.currentTimeMillis());
            Log.d(TAG, "   user_present: " + hasValue(user_id));
            Log.d(TAG, "   Firebase Analytics: " + (mFirebaseAnalytics != null));
        }

        // Safety check for Firebase Analytics
        if (mFirebaseAnalytics == null) {
            Log.e(TAG, "   ERROR: FirebaseAnalytics is null! keyboard_disabled event NOT sent.");
            return;
        }

        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        mFirebaseAnalytics.logEvent("keyboard_disabled", bundle);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "   SUCCESS: keyboard_disabled event logged to Firebase");
        }
    }

    public static void triggerSignInEvent(String user_id, String type, String value, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        bundle.putString("type", type);
        bundle.putString("value", value);
        mFirebaseAnalytics.logEvent("sign_in", bundle);
    }

    public static void triggerFreeTrialEndedEvent(String user_id, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        mFirebaseAnalytics.logEvent("free_trial_ended", bundle);
    }

    public static void triggerSubscriptionJourneyStartedEvent(String user_id, String value, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        bundle.putString("value", value);
        mFirebaseAnalytics.logEvent("subscription_journey_started", bundle);
    }

    public static void triggerSubscriptionJourneyCancelledEvent(String user_id, String value, String reason, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        bundle.putString("value", value);
        bundle.putString("reason", reason);
        mFirebaseAnalytics.logEvent("subscription_journey_cancelled", bundle);
    }

    public static void triggerSubscriptionJourneySuccessEvent(String user_id, String value, FirebaseAnalytics mFirebaseAnalytics ) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        bundle.putString("value", value);
        mFirebaseAnalytics.logEvent("subscription_journey_success", bundle);
    }

    public static void triggerSubscriptionJourneyCompletedEvent(String user_id, String value, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        bundle.putString("value", value);
        mFirebaseAnalytics.logEvent("subscription_journey_completed", bundle);
    }

    public static void triggerUserLogOutEvent(String user_id, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        mFirebaseAnalytics.logEvent("user_logged_out", bundle);
    }

    public static void triggerRatingNudgeEvent(String user_id, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        mFirebaseAnalytics.logEvent("rating_nudge", bundle);
    }

    public static void triggerLocalTrialInfo(String user_id, String value, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle bundle = new Bundle();
        attachSafeUser(bundle, user_id, mFirebaseAnalytics);
        bundle.putString("value", value);
        mFirebaseAnalytics.logEvent("local_trial_info_missing", bundle);
    }

    /**
     * Log when a user initiates an AI chat conversation.  This event records
     * the length of the prompt text so that we can understand how much
     * information users typically provide when starting a chat.  The user id
     * and length are recorded in the event parameters.
     */
    public static void triggerAiChatInitiatedEvent(String userId, int textLength, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        bundle.putInt("text_length", textLength);
        mFirebaseAnalytics.logEvent("ai_chat_initiated", bundle);
    }

    /**
     * Log when a user requests grammar correction.  This event records
     * the length of the text being corrected.
     */
    public static void triggerGrammarCorrectionEvent(String userId, int textLength, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        bundle.putInt("text_length", textLength);
        mFirebaseAnalytics.logEvent("grammar_correction_initiated", bundle);
    }

    /**
     * Log when a user opens the tone change feature.  Records the length
     * of the original text being rephrased.
     */
    public static void triggerToneChangeInitiatedEvent(String userId, int textLength, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        bundle.putInt("text_length", textLength);
        mFirebaseAnalytics.logEvent("tone_change_initiated", bundle);
    }

    /**
     * Log when a specific tone is selected.  Includes the chosen tone as a
     * parameter so that tone popularity can be analysed.
     */
    public static void triggerToneSelectedEvent(String userId, String tone, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        if (tone != null) {
            bundle.putString("tone", tone);
        }
        mFirebaseAnalytics.logEvent("tone_selected", bundle);
    }

    /**
     * Log when the translation feature is initiated.  Records the length
     * of the text to be translated.
     */
    public static void triggerTranslationInitiatedEvent(String userId, int textLength, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        bundle.putInt("text_length", textLength);
        mFirebaseAnalytics.logEvent("translation_initiated", bundle);
    }

    /**
     * Log when a translation language is selected.  Includes the language
     * name or code to understand which languages are most commonly chosen.
     */
    public static void triggerLanguageSelectedEvent(String userId, String language, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        if (language != null) {
            bundle.putString("language", language);
        }
        mFirebaseAnalytics.logEvent("translation_language_selected", bundle);
    }

    /**
     * Log when the read screen (scan) options are shown.  Records how
     * many options were presented to the user.
     */
    public static void triggerScanOptionsShownEvent(String userId, int optionCount, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        bundle.putInt("option_count", optionCount);
        mFirebaseAnalytics.logEvent("scan_options_shown", bundle);
    }

    /**
     * Log when a level 1 scan category is selected.
     */
    public static void triggerScanOptionSelectedEvent(String userId, String optionName, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        if (optionName != null) {
            bundle.putString("option", optionName);
        }
        mFirebaseAnalytics.logEvent("scan_option_selected", bundle);
    }

    /**
     * Log when a level 2 scan intent is selected.
     */
    public static void triggerScanIntentSelectedEvent(String userId, String intentName, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        if (intentName != null) {
            bundle.putString("intent", intentName);
        }
        mFirebaseAnalytics.logEvent("scan_intent_selected", bundle);
    }

    /**
     * Log when a suggestion pill is selected.  The suggestion text is
     * included in the event parameters.
     */
    public static void triggerSuggestionSelectedEvent(String userId, String suggestion, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        if (suggestion != null) {
            bundle.putString("suggestion", suggestion);
        }
        mFirebaseAnalytics.logEvent("suggestion_selected", bundle);
    }

    /**
     * Log when a CTA (copy, apply, reply, regenerate) is clicked.  Records
     * the type of CTA so that we can understand which actions are most
     * frequently performed.
     */
    public static void triggerCtaClickedEvent(String userId, String cta, FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        if (cta != null) {
            bundle.putString("cta", cta);
        }
        mFirebaseAnalytics.logEvent("cta_clicked", bundle);
    }

    /**
     * Log when the user taps the primary voice input icon.
     * Triggered for voice dictation that inserts text directly.
     */
    public static void triggerVoiceInputStartedEvent(String trackingId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        bundle.putLong("timestamp", System.currentTimeMillis());

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("voice_input_started", bundle);
    }

    /**
     * Log when the user taps the voice prompt icon.
     * Triggered when voice command is sent to AI.
     */
    public static void triggerVoicePromptStartedEvent(String trackingId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        bundle.putLong("timestamp", System.currentTimeMillis());

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("voice_prompt_started", bundle);
    }


    /**
     * Log the result of a voice recognition session.
     * Records the length of recognised text.
     */
    public static void triggerVoiceInputResultEvent(String trackingId, int textLength, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        bundle.putInt("text_length", textLength);
        bundle.putLong("timestamp", System.currentTimeMillis());

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("voice_input_result", bundle);
    }



/**
 * NEW ANALYTICS EVENTS FOR TASK 1: ONBOARDING & ACTIVATION TRACKING
 *
 * These methods should be added to the existing EventHelpers.java class
 */

    // ==================== ONBOARDING EVENTS ====================

    /**
     * Log when user starts the onboarding flow (enters tutorial)
     *
     * @param trackingId User/device identifier
     * @param analytics FirebaseAnalytics instance
     */
    public static void triggerOnboardingStarted(String trackingId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        long timestamp = System.currentTimeMillis();
        bundle.putLong("timestamp", timestamp);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: onboarding_started");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   timestamp: " + timestamp);
        }

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("onboarding_started", bundle);
    }


    /**
     * Log when user completes a specific onboarding step
     *
     * @param trackingId User/device identifier
     * @param stepNumber Step number (1, 2, 3, etc.)
     * @param stepName Name of the step (e.g., "ENABLE_KEYBOARD", "AI_CHAT_TASK")
     * @param timeSpentMs Time spent on this step in milliseconds
     * @param analytics FirebaseAnalytics instance
     */
    public static void triggerOnboardingStepCompleted(
            String trackingId,
            int stepNumber,
            String stepName,
            long timeSpentMs,
            FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        bundle.putInt("step_number", stepNumber);
        bundle.putString("step_name", stepName);
        bundle.putLong("time_spent_ms", timeSpentMs);
        long timestamp = System.currentTimeMillis();
        bundle.putLong("timestamp", timestamp);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: onboarding_step_completed");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   step_number: " + stepNumber);
            Log.d(TAG, "   step_name: " + stepName);
            Log.d(TAG, "   time_spent_ms: " + timeSpentMs);
            Log.d(TAG, "   timestamp: " + timestamp);
        }

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("onboarding_step_completed", bundle);
    }

    /**
     * Log when user completes all onboarding steps
     *
     * @param trackingId User/device identifier
     * @param totalSteps Total number of steps completed
     * @param totalTimeMs Total time spent in onboarding
     * @param analytics FirebaseAnalytics instance
     */
    public static void triggerOnboardingCompleted(
            String trackingId,
            int totalSteps,
            long totalTimeMs,
            FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        bundle.putInt("total_steps", totalSteps);
        bundle.putLong("total_time_ms", totalTimeMs);
        long timestamp = System.currentTimeMillis();
        bundle.putLong("timestamp", timestamp);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: onboarding_completed");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   total_steps: " + totalSteps);
            Log.d(TAG, "   total_time_ms: " + totalTimeMs);
            Log.d(TAG, "   timestamp: " + timestamp);
        }

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("onboarding_completed", bundle);
    }


    /**
     * Log when user skips onboarding
     *
     * @param trackingId User/device identifier
     * @param stepsCompleted Number of steps completed before skipping
     * @param analytics FirebaseAnalytics instance
     */
    public static void triggerOnboardingSkipped(
            String trackingId,
            int stepsCompleted,
            FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        bundle.putInt("steps_completed", stepsCompleted);
        long timestamp = System.currentTimeMillis();
        bundle.putLong("timestamp", timestamp);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: onboarding_skipped");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   steps_completed: " + stepsCompleted);
            Log.d(TAG, "   timestamp: " + timestamp);
        }

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("onboarding_skipped", bundle);
    }

    // ==================== ACTIVATION MILESTONE EVENTS ====================

    /**
     * Log when user reaches an activation milestone
     *
     * @param userId User identifier
     * @param milestoneName Name of milestone (e.g., "keyboard_enabled", "first_message_typed")
     * @param pointsEarned Points earned for this milestone
     * @param totalScore Total activation score after this milestone
     */
    public static void triggerActivationMilestone(
            String userId,
            String milestoneName,
            int pointsEarned,
            int totalScore,
            FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        bundle.putString("milestone", milestoneName);
        bundle.putInt("points_earned", pointsEarned);
        bundle.putInt("total_score", totalScore);
        bundle.putLong("timestamp", System.currentTimeMillis());
        mFirebaseAnalytics.logEvent("activation_milestone", bundle);
    }

    /**
     * Log the CRITICAL event when user uses their first AI feature
     *
     * @param userId User identifier
     * @param featureName Name of the first AI feature used
     */
    public static void triggerFirstAiFeatureUsed(
            String userId,
            String featureName,
            FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        bundle.putString("feature_name", featureName);
        bundle.putLong("timestamp", System.currentTimeMillis());
        mFirebaseAnalytics.logEvent("first_ai_feature_used", bundle);
    }

    /**
     * Log when user becomes "activated" (reaches 75+ activation score)
     *
     * @param userId User identifier
     * @param finalScore Final activation score when activated
     */
    public static void triggerUserActivated(
            String userId,
            int finalScore,
            FirebaseAnalytics mFirebaseAnalytics) {
        if (mFirebaseAnalytics == null) return;
        Bundle bundle = new Bundle();
        if (userId != null) {
            attachSafeUser(bundle, userId, mFirebaseAnalytics);
        }
        bundle.putInt("activation_score", finalScore);
        bundle.putLong("timestamp", System.currentTimeMillis());
        mFirebaseAnalytics.logEvent("user_activated", bundle);
    }

    // ==================== KEYBOARD USAGE TRACKING ====================

    /**
     * Log when user types their first message (any input)
     *
     * @param trackingId User/device identifier
     * @param inputLength Length of the input text
     * @param analytics FirebaseAnalytics instance
     */
    public static void triggerFirstMessageTyped(
            String trackingId,
            int inputLength,
            FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        bundle.putInt("input_length", inputLength);
        bundle.putLong("timestamp", System.currentTimeMillis());

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("first_message_typed", bundle);
    }

    /**
     * Log session start event (when keyboard is opened)
     * CRITICAL: This enables true keyboard DAU measurement
     *
     * @param trackingId User/device identifier
     * @param sessionId Unique session identifier
     * @param analytics FirebaseAnalytics instance
     */
    public static void triggerKeyboardSessionStarted(
            String trackingId,
            String sessionId,
            FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        bundle.putString("session_id", sessionId);
        bundle.putLong("timestamp", System.currentTimeMillis());

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("keyboard_session_started", bundle);
    }

    /**
     * Log session end event (when keyboard is closed)
     * Includes session duration and characters typed for engagement metrics
     *
     * @param trackingId User/device identifier
     * @param sessionId Unique session identifier
     * @param sessionDurationMs Session duration in milliseconds
     * @param charactersTyped Total characters typed in session
     * @param analytics FirebaseAnalytics instance
     */
    public static void triggerKeyboardSessionEnded(
            String trackingId,
            String sessionId,
            long sessionDurationMs,
            int charactersTyped,
            FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("analytics_user_id", safeAnalyticsId(trackingId));
        bundle.putString("session_id", sessionId);
        bundle.putLong("session_duration_ms", sessionDurationMs);
        bundle.putInt("characters_typed", charactersTyped);
        bundle.putLong("timestamp", System.currentTimeMillis());

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("keyboard_session_ended", bundle);
    }

    // ========== TUTORIAL EVENT METHODS ==========

    /**
     * Log when tutorial starts
     * UPDATED: Now properly sets tracking ID for anonymous users
     */
    public static void triggerTutorialStarted(String trackingId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(trackingId));
        long timestamp = System.currentTimeMillis();
        params.putLong("timestamp", timestamp);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_started");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   timestamp: " + timestamp);
        }

        // Set user ID properly (handles anonymous users)
        setTrackingUserId(trackingId, analytics);

        analytics.logEvent("tutorial_started", params);
    }


    /**
     * Log when tutorial step starts
     * UPDATED: Now properly sets tracking ID for anonymous users
     */
    public static void triggerTutorialStepStarted(String trackingId, String stepName, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(trackingId));
        params.putString("step_name", stepName);
        long timestamp = System.currentTimeMillis();
        params.putLong("timestamp", timestamp);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_step_started");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   step_name: " + stepName);
            Log.d(TAG, "   timestamp: " + timestamp);
        }

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("tutorial_step_started", params);
    }

    /**
     * Log when tutorial step completes
     * UPDATED: Now properly sets tracking ID for anonymous users
     */
    public static void triggerTutorialStepCompleted(String trackingId, String stepName, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(trackingId));
        params.putString("step_name", stepName);
        long timestamp = System.currentTimeMillis();
        params.putLong("timestamp", timestamp);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_step_completed");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   step_name: " + stepName);
            Log.d(TAG, "   timestamp: " + timestamp);
        }

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("tutorial_step_completed", params);
    }

    /**
     * Log when tutorial completes
     * UPDATED: Now properly sets tracking ID for anonymous users
     */
    public static void triggerTutorialCompleted(String trackingId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(trackingId));
        long timestamp = System.currentTimeMillis();
        params.putLong("timestamp", timestamp);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_completed");
            Log.d(TAG, "   tracking_id_present: " + hasValue(trackingId));
            Log.d(TAG, "   timestamp: " + timestamp);
        }

        setTrackingUserId(trackingId, analytics);
        analytics.logEvent("tutorial_completed", params);
    }

    /**
     * Log when AI Chat button clicked in tutorial
     */
    public static void triggerAiChatClicked(String userId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(userId));
        params.putString("context", "tutorial");

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_ai_chat_clicked");
            Log.d(TAG, "   user_present: " + hasValue(userId));
            Log.d(TAG, "   context: tutorial");
        }

        analytics.logEvent("tutorial_ai_chat_clicked", params);
    }

    /**
     * Log when Read Screen button clicked in tutorial
     */
    public static void triggerReadScreenClicked(String userId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(userId));
        params.putString("context", "tutorial");

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_read_screen_clicked");
            Log.d(TAG, "   user_present: " + hasValue(userId));
            Log.d(TAG, "   context: tutorial");
        }

        analytics.logEvent("tutorial_read_screen_clicked", params);
    }

    /**
     * Log when Tonality button clicked in tutorial
     */
    public static void triggerTonalityClicked(String userId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(userId));
        params.putString("context", "tutorial");

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_tonality_clicked");
            Log.d(TAG, "   user_present: " + hasValue(userId));
            Log.d(TAG, "   context: tutorial");
        }

        analytics.logEvent("tutorial_tonality_clicked", params);
    }

    /**
     * Log when Grammar button clicked in tutorial
     */
    public static void triggerGrammarClicked(String userId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(userId));
        params.putString("context", "tutorial");

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_grammar_clicked");
            Log.d(TAG, "   user_present: " + hasValue(userId));
            Log.d(TAG, "   context: tutorial");
        }

        analytics.logEvent("tutorial_grammar_clicked", params);
    }

    /**
     * Log when Translate button clicked in tutorial
     */
    public static void triggerTranslateClicked(String userId, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(userId));
        params.putString("context", "tutorial");

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_translate_clicked");
            Log.d(TAG, "   user_present: " + hasValue(userId));
            Log.d(TAG, "   context: tutorial");
        }

        analytics.logEvent("tutorial_translate_clicked", params);
    }

    /**
     * Log when AI text is applied in tutorial
     */
    public static void triggerAiTextApplied(String userId, int textLength, FirebaseAnalytics analytics) {
        if (analytics == null) return;
        Bundle params = new Bundle();
        params.putString("analytics_user_id", safeAnalyticsId(userId));
        params.putInt("text_length", textLength);
        params.putString("context", "tutorial");

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 EVENT: tutorial_ai_text_applied");
            Log.d(TAG, "   user_present: " + hasValue(userId));
            Log.d(TAG, "   text_length: " + textLength);
            Log.d(TAG, "   context: tutorial");
        }

        analytics.logEvent("tutorial_ai_text_applied", params);
    }

    // ----------------------------------------------------------------------------
    // ADD THIS SECTION AFTER THE CLASS DECLARATION
    // ----------------------------------------------------------------------------

    /**
     * Get a tracking ID that works for both logged-in and anonymous users.
     * Uses device ID as fallback when user is not logged in.
     * This is CRITICAL for tracking events during tutorial (before login).
     *
     * @param context Android context
     * @param userId  User ID if available, can be null
     * @return Tracking ID (either userId or "anon_" + deviceId)
     */
    public static String getTrackingId(Context context, String userId) {
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        // Fallback to device ID for anonymous users
        String deviceId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
        return "anon_" + hashIdentifier(deviceId != null ? deviceId : "unknown");
    }

    /**
     * Safely set user ID on FirebaseAnalytics.
     * For anonymous users (anon_*), sets as user property instead of userId
     * so we can link events after user logs in.
     */
    public static void setTrackingUserId(String trackingId, FirebaseAnalytics analytics) {
        if (analytics == null || trackingId == null) return;

        String safeId = safeAnalyticsId(trackingId);
        if (safeId == null) return;

        analytics.setUserProperty("analytics_user_id", safeId);
        analytics.setUserId(safeId);
    }

}
