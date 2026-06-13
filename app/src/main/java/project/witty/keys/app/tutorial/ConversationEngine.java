package project.witty.keys.app.tutorial;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared conversation state machine for InteractiveTutorialActivity.
 * Used by all 3 modes: onboarding, e2e_test, quality_score.
 *
 * Loads scenarios from OnboardingConversations and tracks progress
 * through conversations message-by-message.
 */
public class ConversationEngine {

    private static final String TAG = "ConversationEngine";

    // --- Listener interface ---
    public interface ConversationListener {
        void onScenarioStarted(OnboardingConversations.Conversation scenario, int scenarioIndex);
        void onMessageReady(OnboardingConversations.Message message, int messageIndex);
        void onScenarioCompleted(OnboardingConversations.Conversation scenario, int scenarioIndex);
        void onAllScenariosCompleted();
    }

    // --- Feature hint metadata ---
    public enum FeatureHint {
        SMART_REPLIES,      // Scenario 1: highlight reply chips
        READ_SCREEN,        // Scenario 1: callout after first reply
        CUSTOM_GENERATE,    // Scenario 2: highlight Custom/Generate CTA
        AI_CHAT,            // Scenario 3: highlight AI Chat CTA
        NONE                // No hint (optional scenarios or test mode)
    }

    // --- State ---
    private List<OnboardingConversations.Conversation> scenarios;
    private int currentScenarioIndex = 0;
    private int currentMessageIndex = 0;
    private boolean showFeatureNudges = false;
    private ConversationListener listener;

    // Feature hints per scenario (indexed by position in mandatory list)
    private static final FeatureHint[] MANDATORY_HINTS = {
        FeatureHint.SMART_REPLIES,   // Scenario 1: Friend Chat
        FeatureHint.CUSTOM_GENERATE, // Scenario 2: Boss
        FeatureHint.AI_CHAT          // Scenario 3: Global Chat
    };

    public ConversationEngine() {
        this.scenarios = new ArrayList<>();
        Log.d(TAG, "ConversationEngine created");
    }

    // --- Scenario Loading ---

    /**
     * Load 3 mandatory onboarding scenarios.
     * Used by MODE_ONBOARDING.
     */
    public void loadForOnboarding() {
        Log.d(TAG, "loadForOnboarding: loading 3 mandatory scenarios");
        this.scenarios = new ArrayList<>(OnboardingConversations.getConversations());
        this.showFeatureNudges = true;
        resetProgress();
        Log.d(TAG, "loadForOnboarding: loaded " + scenarios.size() + " scenarios");
    }

    /**
     * Load optional scenarios (4-7) to append after mandatory.
     * Called when user taps "Show me more!" after scenario 3.
     */
    public void loadOptionalScenarios() {
        Log.d(TAG, "loadOptionalScenarios: appending optional scenarios");
        List<OnboardingConversations.Conversation> all = OnboardingConversations.getAllScenarios();
        // Add only the ones not already loaded (index 3+)
        for (int i = 3; i < all.size(); i++) {
            this.scenarios.add(all.get(i));
        }
        Log.d(TAG, "loadOptionalScenarios: total scenarios now " + scenarios.size());
    }

    /**
     * Load ALL 7 scenarios for automated testing.
     * Used by MODE_E2E_TEST.
     */
    public void loadAllScenarios() {
        Log.d(TAG, "loadAllScenarios: loading all 7 scenarios");
        this.scenarios = new ArrayList<>(OnboardingConversations.getAllScenarios());
        this.showFeatureNudges = false;
        resetProgress();
        Log.d(TAG, "loadAllScenarios: loaded " + scenarios.size() + " scenarios");
    }

    /**
     * Load a single scenario by name.
     * Used by MODE_E2E_TEST (targeted) and MODE_QUALITY_SCORE.
     */
    public void loadScenario(String scenarioName) {
        Log.d(TAG, "loadScenario: loading scenario '" + scenarioName + "'");
        OnboardingConversations.Conversation scenario =
            OnboardingConversations.getScenarioByName(scenarioName);
        if (scenario != null) {
            this.scenarios = new ArrayList<>(Collections.singletonList(scenario));
            this.showFeatureNudges = false;
            resetProgress();
            Log.d(TAG, "loadScenario: loaded '" + scenarioName + "' successfully");
        } else {
            Log.e(TAG, "loadScenario: scenario '" + scenarioName + "' NOT FOUND");
            this.scenarios = new ArrayList<>();
        }
    }

    // --- Progress Control ---

    private void resetProgress() {
        currentScenarioIndex = 0;
        currentMessageIndex = 0;
        Log.d(TAG, "resetProgress: index=0, message=0");
    }

    /**
     * Get the current scenario.
     * @return current Conversation or null if exhausted
     */
    public OnboardingConversations.Conversation getCurrentScenario() {
        if (currentScenarioIndex < scenarios.size()) {
            return scenarios.get(currentScenarioIndex);
        }
        return null;
    }

    /**
     * Get the current scenario index (0-based).
     */
    public int getCurrentScenarioIndex() {
        return currentScenarioIndex;
    }

    /**
     * Get total number of loaded scenarios.
     */
    public int getScenarioCount() {
        return scenarios.size();
    }

    /**
     * Start the current scenario — notifies listener.
     */
    public void startCurrentScenario() {
        OnboardingConversations.Conversation current = getCurrentScenario();
        if (current != null) {
            currentMessageIndex = 0;
            Log.d(TAG, "startCurrentScenario: " + current.scenarioType
                + " (index=" + currentScenarioIndex + ")");
            if (listener != null) {
                listener.onScenarioStarted(current, currentScenarioIndex);
            }
        } else {
            Log.w(TAG, "startCurrentScenario: no scenario available");
        }
    }

    /**
     * Get the next message in the current scenario.
     * @return next Message or null if scenario is complete
     */
    public OnboardingConversations.Message getNextMessage() {
        OnboardingConversations.Conversation current = getCurrentScenario();
        if (current == null) {
            Log.w(TAG, "getNextMessage: no current scenario");
            return null;
        }

        if (currentMessageIndex < current.messages.size()) {
            OnboardingConversations.Message msg = current.messages.get(currentMessageIndex);
            Log.d(TAG, "getNextMessage: scenario=" + current.scenarioType
                + " msgIndex=" + currentMessageIndex
                + " isReceived=" + msg.isReceived
                + " text='" + truncate(msg.text, 40) + "'");
            if (listener != null) {
                listener.onMessageReady(msg, currentMessageIndex);
            }
            currentMessageIndex++;
            return msg;
        }

        Log.d(TAG, "getNextMessage: scenario " + current.scenarioType + " complete");
        return null;
    }

    /**
     * Check if the current scenario has more messages.
     */
    public boolean isScenarioComplete() {
        OnboardingConversations.Conversation current = getCurrentScenario();
        if (current == null) return true;
        return currentMessageIndex >= current.messages.size();
    }

    /**
     * Mark current scenario as completed and try to advance to next.
     * @return true if there is a next scenario, false if all done
     */
    public boolean advanceToNextScenario() {
        OnboardingConversations.Conversation completed = getCurrentScenario();
        if (completed != null && listener != null) {
            listener.onScenarioCompleted(completed, currentScenarioIndex);
        }

        currentScenarioIndex++;
        currentMessageIndex = 0;

        Log.d(TAG, "advanceToNextScenario: now at index=" + currentScenarioIndex
            + " of " + scenarios.size());

        if (currentScenarioIndex >= scenarios.size()) {
            Log.d(TAG, "advanceToNextScenario: ALL SCENARIOS COMPLETED");
            if (listener != null) {
                listener.onAllScenariosCompleted();
            }
            return false;
        }
        return true;
    }

    /**
     * Check if mandatory scenarios (first 3) are all done.
     */
    public boolean areMandatoryScenariosComplete() {
        return currentScenarioIndex >= 3;
    }

    // --- Feature Hints ---

    /**
     * Get the feature hint for the current scenario.
     * Returns NONE in test modes or for optional scenarios.
     */
    public FeatureHint getCurrentFeatureHint() {
        if (!showFeatureNudges) return FeatureHint.NONE;
        if (currentScenarioIndex < MANDATORY_HINTS.length) {
            return MANDATORY_HINTS[currentScenarioIndex];
        }
        return FeatureHint.NONE;
    }

    /**
     * Whether to show feature nudge bot messages.
     */
    public boolean shouldShowFeatureNudges() {
        return showFeatureNudges;
    }

    // --- Context for AI/Scoring ---

    /**
     * Get conversation context for the current state.
     * Used by quality scoring to understand expected reply characteristics.
     */
    public ConversationContext getCurrentContext() {
        OnboardingConversations.Conversation current = getCurrentScenario();
        if (current == null) return null;

        return new ConversationContext(
            current.contactName,
            current.scenarioType,
            currentScenarioIndex,
            currentMessageIndex,
            getCurrentFeatureHint()
        );
    }

    // --- Listener ---

    public void setListener(ConversationListener listener) {
        this.listener = listener;
    }

    // --- Utility ---

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // --- Context data class ---

    public static class ConversationContext {
        public final String contactName;
        public final OnboardingConversations.ScenarioType scenarioType;
        public final int scenarioIndex;
        public final int messageIndex;
        public final FeatureHint featureHint;

        public ConversationContext(String contactName,
                                   OnboardingConversations.ScenarioType scenarioType,
                                   int scenarioIndex, int messageIndex,
                                   FeatureHint featureHint) {
            this.contactName = contactName;
            this.scenarioType = scenarioType;
            this.scenarioIndex = scenarioIndex;
            this.messageIndex = messageIndex;
            this.featureHint = featureHint;
        }

        @Override
        public String toString() {
            return "ConversationContext{contact='" + contactName
                + "', scenario=" + scenarioType
                + ", idx=" + scenarioIndex
                + ", msg=" + messageIndex
                + ", hint=" + featureHint + "}";
        }
    }
}
