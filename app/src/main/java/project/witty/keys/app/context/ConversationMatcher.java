package project.witty.keys.app.context;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import project.witty.keys.app.helpers.JourneyTracer;

/**
 * ConversationMatcher — NEW file (Sprint 1, Build 7.0)
 *
 * Multi-signal confidence scoring singleton.
 * Correlates NLS data with A11y contact identification.
 * All matching is LOCAL — no network calls.
 *
 * CONTRACT FOR DOWNSTREAM PHASES:
 * - P2 (Smart Replies): Calls getActiveContact() to get conversationKey for ReplyCache lookup
 * - P4 (AI Chat): Calls getActiveContact() for session context
 * - P6 (Onboarding): Checks isReady() for permission status
 *
 * Method signatures that downstream phases depend on:
 * - getInstance() → ConversationMatcher
 * - getActiveContact() → ContactMatch (nullable)
 * - registerNlsContact(String packageName, String contactName) → void
 * - setActiveContact(String packageName, String contactName, MatchSource source) → void
 * - setActiveContactFromNotificationTap(String packageName, String contactName, String nlsConversationKey) → void
 * - ContactMatch.conversationKey → String (format: "packageName|contactName")
 * - ContactMatch.confidence → float (0.0-1.0)
 */
public class ConversationMatcher {
    private static ConversationMatcher instance;

    // Current active contact (volatile for cross-thread visibility)
    private volatile ContactMatch activeContact;

    // Package name of the app where the keyboard is currently active
    private volatile String currentEditorPackage;

    // NLS-captured contacts (populated by WittyKeysNotificationListenerService)
    private final Map<String, Set<String>> nlsContacts = new ConcurrentHashMap<>();
    // Key: packageName, Value: set of contact names seen via NLS

    public static synchronized ConversationMatcher getInstance() {
        if (instance == null) instance = new ConversationMatcher();
        return instance;
    }

    // Private constructor — singleton
    private ConversationMatcher() {}

    /**
     * Called by LatinIME.onStartInputViewInternal() to track which app the keyboard serves.
     * NLS uses this to filter — only notifications from this package update activeContact.
     */
    public void setCurrentEditorPackage(String packageName) {
        this.currentEditorPackage = packageName;
    }

    public String getCurrentEditorPackage() {
        return currentEditorPackage;
    }

    /**
     * Called by AccessibilityService when a chat header contact name is detected.
     * Performs LOCAL-ONLY matching against NLS contact database.
     */
    public void setActiveContact(String packageName, String contactName, MatchSource source) {
        float confidence = 0.0f;
        String conversationKey = null;

        // Check if NLS has data for this contact
        Set<String> knownContacts = nlsContacts.get(packageName);
        if (knownContacts != null) {
            // Exact match
            if (knownContacts.contains(contactName)) {
                confidence = 0.99f;
                conversationKey = packageName + "|" + contactName;
            } else {
                // Fuzzy match (handles nickname differences: "Mom ❤️" vs "Mom")
                for (String known : knownContacts) {
                    if (fuzzyMatch(contactName, known)) {
                        confidence = 0.90f;
                        conversationKey = packageName + "|" + known;
                        break;
                    }
                }
            }
        }

        if (conversationKey == null && source == MatchSource.ACCESSIBILITY) {
            // A11y identified contact but no NLS data yet — still useful
            confidence = 0.80f;
            conversationKey = packageName + "|" + contactName;
        }

        activeContact = new ContactMatch(contactName, packageName, conversationKey,
            confidence, source, System.currentTimeMillis());

        // JourneyTracer: contact matched
        String traceId = JourneyTracer.getCurrentSmartReplyTrace();
        if (traceId != null) {
            try {
                JSONObject dataIn = new JSONObject();
                dataIn.put("nls_sender", contactName);
                JSONObject dataOut = new JSONObject();
                dataOut.put("confidence", confidence);
                dataOut.put("method", source.name());
                JourneyTracer.step(traceId, "CONTACT_MATCHED", dataIn, dataOut,
                    confidence > 0.8f ? "high confidence → use contact" : "low confidence → fallback");
            } catch (Exception ignored) {}
        }
    }

    /**
     * Called by NLS when notification is tapped (REASON_CLICK).
     * Highest confidence — we KNOW which conversation was opened.
     */
    public void setActiveContactFromNotificationTap(String packageName,
            String contactName, String nlsConversationKey) {
        activeContact = new ContactMatch(contactName, packageName, nlsConversationKey,
            0.95f, MatchSource.NLS_TAP, System.currentTimeMillis());
    }

    /**
     * Called by NLS on each captured message — builds the contact database.
     */
    public void registerNlsContact(String packageName, String contactName) {
        nlsContacts.computeIfAbsent(packageName, k -> ConcurrentHashMap.newKeySet())
            .add(contactName);
    }

    /**
     * Called by SmartAssistantBar when keyboard opens.
     * Returns the best match with confidence score.
     */
    public ContactMatch getActiveContact() {
        ContactMatch match = activeContact;
        if (match == null) return null;
        // Stale check — contact identification older than 5 minutes is unreliable
        if (System.currentTimeMillis() - match.timestamp > 5 * 60 * 1000) return null;
        return match;
    }

    /**
     * Called when user manually selects a contact from the picker strip.
     */
    public void setUserSelectedContact(String packageName, String contactName) {
        String conversationKey = packageName + "|" + contactName;
        activeContact = new ContactMatch(contactName, packageName, conversationKey,
            1.0f, MatchSource.USER_SELECTED, System.currentTimeMillis());
    }

    /**
     * Called when user explicitly selects a contact from the picker strip.
     * Sets confidence to 1.0 (100%) — user explicitly confirmed the contact.
     * P2 API — used by SmartAssistantBar.onContactPickerSelected().
     */
    public void setActiveContactFromUserSelection(String packageName,
            String contactName, String conversationKey) {
        activeContact = new ContactMatch(contactName, packageName, conversationKey,
            1.0f, MatchSource.USER_SELECTED, System.currentTimeMillis());
    }

    /**
     * Returns true if NLS has captured any contacts (i.e., NLS permission is granted and working).
     */
    public boolean hasNlsData() {
        return !nlsContacts.isEmpty();
    }

    /**
     * Returns the set of known contacts for a given app package.
     * Used by SmartAssistantBar to show contact picker strip.
     */
    public Set<String> getKnownContacts(String packageName) {
        return nlsContacts.getOrDefault(packageName, Set.of());
    }

    /**
     * Clears the active contact (e.g., when user leaves messaging app).
     */
    public void clearActiveContact() {
        activeContact = null;
    }

    /** Public static accessor for NLS service to check contact match without instance method. */
    public static boolean fuzzyMatchStatic(String a, String b) {
        if (a == null || b == null) return false;
        return getInstance().fuzzyMatch(a, b);
    }

    private boolean fuzzyMatch(String a, String b) {
        // Strip emoji and whitespace for comparison
        String cleanA = a.replaceAll("[^\\p{L}\\p{N}\\s]", "").trim().toLowerCase();
        String cleanB = b.replaceAll("[^\\p{L}\\p{N}\\s]", "").trim().toLowerCase();
        if (cleanA.equals(cleanB)) return true;
        if (cleanA.contains(cleanB) || cleanB.contains(cleanA)) return true;
        // Levenshtein distance <= 2 for short names
        if (cleanA.length() < 20 && cleanB.length() < 20) {
            return levenshteinDistance(cleanA, cleanB) <= 2;
        }
        return false;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                dp[i][j] = Math.min(dp[i-1][j] + 1,
                    Math.min(dp[i][j-1] + 1,
                        dp[i-1][j-1] + (a.charAt(i-1) == b.charAt(j-1) ? 0 : 1)));
            }
        }
        return dp[a.length()][b.length()];
    }

    // --- Inner classes ---

    public static class ContactMatch {
        public final String contactName;
        public final String packageName;
        public final String conversationKey; // format: "packageName|contactName"
        public final float confidence;       // 0.0 - 1.0
        public final MatchSource source;
        public final long timestamp;

        public ContactMatch(String contactName, String packageName,
                String conversationKey, float confidence,
                MatchSource source, long timestamp) {
            this.contactName = contactName;
            this.packageName = packageName;
            this.conversationKey = conversationKey;
            this.confidence = confidence;
            this.source = source;
            this.timestamp = timestamp;
        }
    }

    public enum MatchSource {
        ACCESSIBILITY,     // Contact name from screen header
        NLS_TAP,           // Notification tap (REASON_CLICK)
        NLS_DISMISS,       // Notification dismissed by app (REASON_APP_CANCEL)
        NLS_RECENCY,       // Most recent NLS message heuristic
        USER_SELECTED      // User tapped contact in picker strip
    }
}
