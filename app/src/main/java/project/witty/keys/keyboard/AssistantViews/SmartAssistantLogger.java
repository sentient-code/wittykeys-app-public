package project.witty.keys.keyboard.AssistantViews;

import android.util.Log;

/**
 * SmartAssistantLogger - Centralized logging for SmartAssistantBar journeys
 *
 * Logging Format: J{journey}.S{step}: {action}
 *
 * JOURNEYS:
 * J1: Keyboard Opens in Messaging App
 * J2: Auto-Hide After 15 Seconds
 * J3: User Switches to OriginalView Manually
 * J4: User Starts Typing (COLLAPSED State)
 * J5: Custom Prompt Flow
 * J6: (Removed — was MemoryView modal)
 * J7: Accessibility Not Enabled
 * J8: API Error Handling
 * J9: Non-Contextual App
 * J10: Non-English Message
 * J11: Report AI Response
 * J12: Regenerate Flow
 * J13: New Message While Typing
 *
 * Build 6.3 - SmartAssistantBar Revamp
 */
public class SmartAssistantLogger {

    private static final String TAG = "SmartAssistantBar";

    private static boolean enabled = true;

    // ========== CONFIGURATION ==========

    public static void setEnabled(boolean isEnabled) {
        enabled = isEnabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // ========== CORE LOGGING METHODS ==========

    /**
     * Log a journey step
     * Format: J{journey}.S{step}: {action}
     */
    public static void logJourney(int journey, int step, String action) {
        if (!enabled) return;
        Log.d(TAG, "J" + journey + ".S" + step + ": " + action);
    }

    /**
     * Log a state change
     * Format: STATE: {previousState} -> {newState} (reason={reason})
     */
    public static void logStateChange(String previousState, String newState, String reason) {
        if (!enabled) return;
        Log.d(TAG, "STATE: " + previousState + " -> " + newState + " (reason=" + reason + ")");
    }

    /**
     * Log a view change
     * Format: STATE: {previousView} -> {newView} (reason={reason})
     */
    public static void logViewChange(String previousView, String newView, String reason) {
        if (!enabled) return;
        Log.d(TAG, "STATE: " + previousView + " -> " + newView + " (reason=" + reason + ")");
    }

    /**
     * Log a timer event
     * Format: TIMER: {event}
     */
    public static void logTimer(String event) {
        if (!enabled) return;
        Log.d(TAG, "TIMER: " + event);
    }

    /**
     * Log a data event
     * Format: DATA: {event}
     */
    public static void logData(String event) {
        if (!enabled) return;
        Log.d(TAG, "DATA: " + event);
    }

    /**
     * Log an error
     * Format: ERROR: {errorType} - {errorMessage}
     */
    public static void logError(String errorType, String errorMessage) {
        if (!enabled) return;
        Log.e(TAG, "ERROR: " + errorType + " - " + errorMessage);
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    // ========== JOURNEY 1: Keyboard Opens in Messaging App ==========

    public static void j1_onStartInputView(String packageName) {
        logJourney(1, 1, "onStartInputViewInternal triggered - package=" + packageName);
    }

    public static void j1_conditionsCheck(boolean accessibilityEnabled, boolean isContextual, boolean hasMessages) {
        logJourney(1, 2, "Conditions check - accessibility=" + accessibilityEnabled +
                ", contextual=" + isContextual + ", hasMessages=" + hasMessages);
    }

    public static void j1_apiRequestSent(String endpoint, int messagesCount) {
        logJourney(1, 4, "API Request sent - endpoint=" + endpoint + ", messagesCount=" + messagesCount);
    }

    public static void j1_apiResponseReceived(int status, String emotion, int repliesCount) {
        logJourney(1, 5, "API Response received - status=" + status +
                ", emotion=" + emotion + ", repliesCount=" + repliesCount);
    }

    public static void j1_quickReplyTapped(int index, String text) {
        logJourney(1, 8, "Quick reply tapped - index=" + index + ", text=" + truncate(text, 30));
    }

    // ========== JOURNEY 2: Auto-Hide After 15 Seconds ==========

    public static void j2_timerExpired(boolean userInteracted) {
        logJourney(2, 1, "Auto-hide timer expired (15000ms) - userInteracted=" + userInteracted);
    }

    public static void j2_switchToOriginalView(int repliesCount) {
        logJourney(2, 2, "Switching to OriginalView EXPANDED - preserving " + repliesCount + " quick replies");
    }

    // ========== JOURNEY 3: User Switches to OriginalView Manually ==========

    public static void j3_brainIconTapped() {
        logJourney(3, 1, "Brain icon tapped - switching to OriginalView");
    }

    public static void j3_originalViewShown() {
        logJourney(3, 2, "OriginalView EXPANDED shown - quick replies preserved");
    }

    // ========== JOURNEY 4: User Starts Typing (COLLAPSED State) ==========

    public static void j4_userStartedTyping(char firstChar) {
        logJourney(4, 1, "User started typing - char='" + firstChar + "'");
    }

    public static void j4_switchedToCollapsed() {
        logJourney(4, 2, "Switched to COLLAPSED state");
    }

    public static void j4_switchedToExpanded() {
        logJourney(4, 2, "Switched back to EXPANDED state (text cleared)");
    }

    public static void j4_moreCtaTapped() {
        logJourney(4, 3, "More CTA tapped - showing tonality options");
    }

    public static void j4_contextActionTapped(String actionType) {
        logJourney(4, 4, "Context action tapped - action=" + actionType);
    }

    public static void j4_loadingShown(String actionType) {
        logJourney(4, 5, "Loading state shown for " + actionType);
    }

    public static void j4_transformComplete(int variationCount) {
        logJourney(4, 6, "Transform complete - variations=" + variationCount);
    }

    // ========== JOURNEY 5: Custom Prompt Flow ==========

    public static void j5_customModeEntry(String currentState) {
        logJourney(5, 1, "Current state before Custom - " + currentState);
    }

    public static void j5_customCtaTapped() {
        logJourney(5, 2, "Custom CTA tapped - Row 2 transforming to CustomModeView");
    }

    public static void j5_userTypingPrompt(int promptLength) {
        logJourney(5, 3, "User typing custom prompt - length=" + promptLength);
    }

    public static void j5_generateTapped() {
        logJourney(5, 4, "Generate tapped - sending custom prompt");
    }

    public static void j5_customRepliesReceived(int repliesCount) {
        logJourney(5, 5, "Custom replies received - count=" + repliesCount);
    }

    public static void j5_customModeError(String errorMessage) {
        logJourney(5, 6, "Custom mode error - " + errorMessage);
    }

    // ========== JOURNEY 7: Accessibility Not Enabled ==========

    public static void j7_accessibilityNotEnabled() {
        logJourney(7, 1, "Accessibility not enabled - showing prompt in Row 2");
    }

    public static void j7_enableButtonTapped() {
        logJourney(7, 2, "Enable button tapped - showing MODAL");
    }

    public static void j7_permissionGranted() {
        logJourney(7, 3, "User granted permission - opening Settings");
    }

    public static void j7_permissionCancelled() {
        logJourney(7, 4, "User cancelled permission modal");
    }

    // ========== JOURNEY 8: API Error Handling ==========

    public static void j8_apiCallFailed(String errorType, String errorMessage) {
        logJourney(8, 1, "API call failed - error=" + errorType + ", message=" + errorMessage);
    }

    public static void j8_originalViewErrorShown() {
        logJourney(8, 3, "OriginalView Row 2 error state shown");
    }

    public static void j8_retryTapped() {
        logJourney(8, 4, "Retry tapped - retrying API call");
    }

    // ========== JOURNEY 9: Non-Contextual App ==========

    public static void j9_nonContextualAppDetected(String packageName) {
        logJourney(9, 1, "Non-contextual app detected - package=" + packageName);
    }

    public static void j9_originalViewExpandedShown() {
        logJourney(9, 2, "Showing OriginalView EXPANDED (no quick replies)");
    }

    public static void j9_userStartedTyping() {
        logJourney(9, 3, "User started typing - showing COLLAPSED with generic actions");
    }

    // ========== JOURNEY 10: Non-English Message ==========

    public static void j10_nonEnglishDetected(String language) {
        logJourney(10, 1, "Non-English detected - language=" + language);
    }

    public static void j10_bilingualRepliesShown() {
        logJourney(10, 2, "Showing translation + bilingual replies");
    }

    // ========== JOURNEY 11: Report AI Response ==========

    public static void j11_reportFlagTapped(String content) {
        logJourney(11, 1, "Report flag tapped - content=" + truncate(content, 30));
    }

    public static void j11_openingMailIntent() {
        logJourney(11, 2, "Opening mailto: intent");
    }

    // ========== JOURNEY 12: Regenerate Flow ==========

    public static void j12_firstTap(String actionType) {
        logJourney(12, 1, "First tap on " + actionType + " - generating 6 variations");
    }

    public static void j12_cyclingVariation(int currentIndex, int totalVariations) {
        logJourney(12, 2, "Second tap - cycling to variation " + (currentIndex + 1) + "/" + totalVariations);
    }

    public static void j12_allVariationsExhausted() {
        logJourney(12, 3, "All variations exhausted - showing refresh option");
    }

    // ========== JOURNEY 13: New Message While Typing ==========

    public static void j13_userTypingInCollapsed() {
        logJourney(13, 1, "User typing in COLLAPSED state");
    }

    public static void j13_newMessageReceived(String sender) {
        logJourney(13, 2, "New message received from " + sender);
    }

    public static void j13_brainBlinkStarted() {
        logJourney(13, 3, "Brain icon blinking started");
    }

    public static void j13_blinkingBrainTapped() {
        logJourney(13, 4, "User tapped blinking brain - showing smart replies");
    }

    // ========== TIMER EVENTS ==========

    public static void timerAutoHideStarted() {
        logTimer("Auto-hide started (15000ms)");
    }

    public static void timerAutoHideCancelled(String reason) {
        logTimer("Auto-hide cancelled (reason=" + reason + ")");
    }

    public static void timerTeleprompterStarted() {
        logTimer("Teleprompter scroll started (delay=2000ms)");
    }

    public static void timerBrainBlinkStarted() {
        logTimer("Brain blink started (duration=5000ms)");
    }

    // ========== DATA EVENTS ==========

    public static void dataRepliesUpdated(int repliesCount) {
        logData("Replies updated - repliesCount=" + repliesCount);
    }

    public static void dataQuickRepliesShared() {
        logData("Quick replies shared with OriginalView");
    }

    // ========== ERROR EVENTS ==========

    public static void errorNetworkTimeout(long timeoutMs) {
        logError("NETWORK_TIMEOUT", "API timeout after " + timeoutMs + "ms");
    }

    public static void errorNoInternet() {
        logError("NO_INTERNET", "No internet connection");
    }

    public static void errorApiError(int statusCode, String message) {
        logError("API_ERROR", "Status " + statusCode + ": " + message);
    }

    public static void errorRateLimit() {
        logError("RATE_LIMIT", "Rate limit exceeded (429)");
    }

    public static void errorParsingFailed(String reason) {
        logError("PARSING_FAILED", reason);
    }
}
