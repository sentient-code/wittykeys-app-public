package project.witty.keys.app.context;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import project.witty.keys.BuildConfig;
import project.witty.keys.app.helpers.DebugConfig;

/**
 * ContextEngine - Intelligent screen context extraction for WittyKeys.
 *
 * Replaces the "dumb" screen reading (grabs ALL text) with smart extraction that:
 * 1. Detects app type (messaging, email, dating, social, other)
 * 2. Extracts only relevant messages (filters UI labels, buttons, timestamps)
 * 3. Identifies the last message user needs to reply to
 * 4. Extracts sender name when available
 * 5. Implements 2-second cache
 * 6. Detects conversation phase (Phase 1)
 */
public class ContextEngine {

    private static final String TAG = "ContextEngine";

    // Cache settings
    private static final long CACHE_TTL_MS = 2000; // 2 second cache
    private ScreenContext cachedContext;
    private long cacheTimestamp;
    private String cachedPackage;

    private final ConversationPhaseDetector phaseDetector = new ConversationPhaseDetector();
    private ConversationPhaseDetector.PhaseResult cachedPhase;

    // Timestamp patterns
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "^\\d{1,2}:\\d{2}(:\\d{2})?\\s*(am|pm|AM|PM)?$"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "^(today|yesterday|\\d{1,2}/\\d{1,2}(/\\d{2,4})?|\\w{3}\\s+\\d{1,2})$",
            Pattern.CASE_INSENSITIVE
    );

    // UI labels to filter out (lowercase)
    private static final Set<String> UI_LABELS = new HashSet<>(Arrays.asList(
            "send", "back", "cancel", "ok", "yes", "no", "done", "next", "close",
            "type a message", "write a message", "message", "search", "settings",
            "more", "menu", "options", "reply", "forward", "delete", "archive",
            "compose", "new message", "new chat", "attach", "attachment", "photo",
            "camera", "gallery", "video", "document", "location", "contact",
            "voice message", "record", "mic", "emoji", "sticker", "gif",
            "online", "offline", "typing...", "typing", "last seen",
            "delivered", "read", "sent", "sending", "failed",
            "mute", "unmute", "block", "unblock", "report", "clear chat",
            "end-to-end encrypted", "encrypted", "tap for more info",
            "view contact", "media", "links", "docs", "starred", "search in chat",
            // WhatsApp-specific UI labels
            "calls", "chats", "updates", "communities", "status",
            "ask meta ai or search", "ask meta ai", "meta ai",
            "new group", "new broadcast", "linked devices", "payments",
            "starred messages", "select language", "help", "invite friends",
            // WhatsApp onboarding/help text
            "tap on the chats tab to create a new community",
            "tap to create a new community", "create a new community",
            "messages and calls are end-to-end encrypted",
            "no one outside of this chat",
            // Timestamps and status
            "last seen", "online", "typing"
    ));

    // Minimum message length
    private static final int MIN_MESSAGE_LENGTH = 2;
    // Maximum message length (longer texts are likely not messages)
    private static final int MAX_MESSAGE_LENGTH = 500;

    /**
     * Extract screen context from accessibility tree
     *
     * @param root The root AccessibilityNodeInfo
     * @return ScreenContext or null if extraction failed
     */
    public ScreenContext extractContext(AccessibilityNodeInfo root) {
        if (root == null) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[CE] Cannot extract context: root is null");
            }
            return null;
        }

        CharSequence pkgName = root.getPackageName();
        String packageName = pkgName != null ? pkgName.toString() : "";

        // Check cache
        if (isCacheValid(packageName)) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[CE] Using cached context for: " + packageName);
            }
            return cachedContext;
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Extracting fresh context for: " + packageName);
        }

        if (BuildConfig.DEBUG) {
            Log.i("WK_E2E", "[APP] ContextEngine.extractContext() — package: " + packageName);
        }

        // Detect app category
        AppDetector.AppCategory category = AppDetector.categorize(packageName);
        String appName = AppDetector.getAppName(packageName);

        if (BuildConfig.DEBUG) {
            Log.i("WK_E2E", "[APP] ContextEngine — category: " + category.name());
        }

        // Extract based on category
        ScreenContext context;
        switch (category) {
            case MESSAGING:
                context = extractMessagingContext(root, appName);
                break;
            case EMAIL:
                context = extractEmailContext(root, appName);
                break;
            case DATING:
                context = extractDatingContext(root, appName);
                break;
            case SOCIAL:
                context = extractSocialContext(root, appName);
                break;
            case OTHER:
            default:
                context = extractGenericContext(root, appName);
                break;
        }

        // Cache result
        cacheContext(context, packageName);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Context extracted: " + (context != null ? context.getViewType() : "null"));
        }

        return context;
    }

    /**
     * Extract context for messaging apps (WhatsApp, Telegram, etc.)
     */
    private ScreenContext extractMessagingContext(AccessibilityNodeInfo root, String appName) {
        List<AccessibilityNodeInfo> textNodes = new ArrayList<>();
        findTextNodes(root, textNodes);

        List<ChatMessage> messages = new ArrayList<>();
        List<String> participants = new ArrayList<>();
        String lastSender = null;

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Messaging: found " + textNodes.size() + " text nodes");
        }

        for (AccessibilityNodeInfo node : textNodes) {
            CharSequence text = node.getText();
            if (text == null) continue;

            String textStr = text.toString().trim();

            // Check if this looks like a message
            if (isLikelyMessage(textStr, node)) {
                // Try to determine if from user or other
                boolean isFromUser = isMessageFromUser(node);
                String sender = extractSenderFromNode(node);

                if (sender != null && !sender.isEmpty() && !participants.contains(sender)) {
                    participants.add(sender);
                    lastSender = sender;
                }

                ChatMessage message = new ChatMessage(
                        sender != null ? sender : (isFromUser ? "Me" : "Other"),
                        textStr,
                        null, // timestamp extracted separately if needed
                        isFromUser
                );
                messages.add(message);

                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[CE] Message found: \"" + truncate(textStr, 50) + "\" from=" + (isFromUser ? "user" : "other"));
                }
            }
        }

        // Recycle nodes
        for (AccessibilityNodeInfo node : textNodes) {
            node.recycle();
        }

        if (messages.isEmpty()) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[CE] No messages found, falling back to generic");
            }
            return extractGenericContext(root, appName);
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Messaging context: " + messages.size() + " messages, " + participants.size() + " participants");
        }

        if (BuildConfig.DEBUG) {
            Log.i("WK_E2E", "[APP] ContextEngine.extractMessagingContext() — extracted " + messages.size() + " messages");
        }

        return new Chat(appName, participants, messages);
    }

    /**
     * Extract context for email apps (Gmail, Outlook, etc.)
     */
    private ScreenContext extractEmailContext(AccessibilityNodeInfo root, String appName) {
        List<AccessibilityNodeInfo> textNodes = new ArrayList<>();
        findTextNodes(root, textNodes);

        String from = null;
        List<String> to = new ArrayList<>();
        String subject = null;
        StringBuilder bodyBuilder = new StringBuilder();

        for (AccessibilityNodeInfo node : textNodes) {
            CharSequence text = node.getText();
            if (text == null) continue;

            String textStr = text.toString().trim();

            // Skip UI labels
            if (isUILabel(textStr)) continue;

            // Try to identify email components based on context
            CharSequence contentDesc = node.getContentDescription();
            String desc = contentDesc != null ? contentDesc.toString().toLowerCase() : "";

            if (desc.contains("from") || desc.contains("sender")) {
                from = textStr;
            } else if (desc.contains("to") || desc.contains("recipient")) {
                to.add(textStr);
            } else if (desc.contains("subject")) {
                subject = textStr;
            } else if (textStr.length() > MIN_MESSAGE_LENGTH && textStr.length() <= MAX_MESSAGE_LENGTH) {
                // Likely body content
                if (bodyBuilder.length() > 0) bodyBuilder.append("\n");
                bodyBuilder.append(textStr);
            }
        }

        // Recycle nodes
        for (AccessibilityNodeInfo node : textNodes) {
            node.recycle();
        }

        String body = bodyBuilder.toString();
        if (body.isEmpty() && from == null && subject == null) {
            return extractGenericContext(root, appName);
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Email context: from=" + from + ", subject=" + truncate(subject, 30));
        }

        return new Email(appName, from, to, subject, body);
    }

    /**
     * Extract context for dating apps (Tinder, Bumble, Hinge, etc.)
     */
    private ScreenContext extractDatingContext(AccessibilityNodeInfo root, String appName) {
        List<AccessibilityNodeInfo> textNodes = new ArrayList<>();
        findTextNodes(root, textNodes);

        String name = null;
        Integer age = null;
        StringBuilder bioBuilder = new StringBuilder();
        List<String> prompts = new ArrayList<>();
        List<String> interests = new ArrayList<>();

        for (AccessibilityNodeInfo node : textNodes) {
            CharSequence text = node.getText();
            if (text == null) continue;

            String textStr = text.toString().trim();

            // Skip UI labels
            if (isUILabel(textStr)) continue;

            // Try to identify profile components
            // Name and age often appear together like "Sarah, 25"
            if (textStr.matches("^[A-Z][a-z]+,?\\s*\\d{2}$")) {
                String[] parts = textStr.split("[,\\s]+");
                if (parts.length >= 1) name = parts[0];
                if (parts.length >= 2) {
                    try {
                        age = Integer.parseInt(parts[parts.length - 1]);
                    } catch (NumberFormatException ignored) {}
                }
            } else if (textStr.length() > 30 && textStr.length() <= 300) {
                // Likely bio or prompt
                if (textStr.contains("?")) {
                    prompts.add(textStr);
                } else {
                    if (bioBuilder.length() > 0) bioBuilder.append("\n");
                    bioBuilder.append(textStr);
                }
            } else if (textStr.length() <= 30 && !isTimestamp(textStr)) {
                // Could be an interest
                interests.add(textStr);
            }
        }

        // Recycle nodes
        for (AccessibilityNodeInfo node : textNodes) {
            node.recycle();
        }

        String bio = bioBuilder.toString();
        if (bio.isEmpty() && name == null) {
            return extractGenericContext(root, appName);
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Dating context: name=" + name + ", age=" + age);
        }

        return new DatingProfile(appName, name, age, bio, prompts, interests);
    }

    /**
     * Extract context for social apps (LinkedIn, Twitter, etc.)
     * Falls back to messaging-style extraction for DMs or generic for feeds
     */
    private ScreenContext extractSocialContext(AccessibilityNodeInfo root, String appName) {
        // Social apps often have chat/DM features, try messaging extraction first
        ScreenContext context = extractMessagingContext(root, appName);

        if (context instanceof Chat) {
            Chat chat = (Chat) context;
            if (chat.getMessages() != null && !chat.getMessages().isEmpty()) {
                return context;
            }
        }

        // Fall back to generic
        return extractGenericContext(root, appName);
    }

    /**
     * Extract generic context (for unknown apps)
     */
    private ScreenContext extractGenericContext(AccessibilityNodeInfo root, String appName) {
        StringBuilder screenText = new StringBuilder();
        collectFilteredText(root, screenText, 0);

        String text = screenText.toString().trim();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Generic context: " + text.length() + " chars");
        }

        return new Generic(appName, text);
    }

    /**
     * Find all text-containing nodes in the accessibility tree
     */
    private void findTextNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            results.add(AccessibilityNodeInfo.obtain(node));
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findTextNodes(child, results);
                child.recycle();
            }
        }
    }

    /**
     * Collect filtered text from the accessibility tree (for generic extraction)
     */
    private void collectFilteredText(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 20) return; // Limit depth to prevent stack overflow

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            String textStr = text.toString().trim();
            if (!isUILabel(textStr) && !isTimestamp(textStr) && textStr.length() >= MIN_MESSAGE_LENGTH) {
                out.append(textStr).append("\n");
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectFilteredText(child, out, depth + 1);
                child.recycle();
            }
        }
    }

    /**
     * Check if text is likely a message (not UI element)
     */
    private boolean isLikelyMessage(String text, AccessibilityNodeInfo node) {
        if (text == null || text.isEmpty()) return false;

        // Length check
        if (text.length() < MIN_MESSAGE_LENGTH || text.length() > MAX_MESSAGE_LENGTH) {
            return false;
        }

        // UI label check
        if (isUILabel(text)) return false;

        // Timestamp check
        if (isTimestamp(text)) return false;

        // Check node class for buttons/headers
        CharSequence className = node.getClassName();
        if (className != null) {
            String cls = className.toString().toLowerCase();
            if (cls.contains("button") || cls.contains("imageview") ||
                cls.contains("checkbox") || cls.contains("switch") ||
                cls.contains("seekbar") || cls.contains("progressbar")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if text is a UI label
     */
    private boolean isUILabel(String text) {
        if (text == null) return true;
        return UI_LABELS.contains(text.toLowerCase().trim());
    }

    /**
     * Check if text is a timestamp
     */
    private boolean isTimestamp(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        return TIMESTAMP_PATTERN.matcher(trimmed).matches() ||
               DATE_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Try to determine if a message is from the current user
     * Based on position/alignment heuristics
     */
    private boolean isMessageFromUser(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // Check content description for hints
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null) {
            String desc = contentDesc.toString().toLowerCase();
            if (desc.contains("you") || desc.contains("sent") || desc.contains("me")) {
                return true;
            }
        }

        // Could add more heuristics based on screen position
        // Messages from user are typically on the right side
        // But this requires getBoundsInScreen which is more complex

        return false;
    }

    /**
     * Try to extract sender name from node context
     */
    private String extractSenderFromNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        // Check content description
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null) {
            String desc = contentDesc.toString();
            // Common patterns: "Message from John", "John:", etc.
            if (desc.contains("from ")) {
                int idx = desc.indexOf("from ");
                String afterFrom = desc.substring(idx + 5);
                int endIdx = afterFrom.indexOf(":");
                if (endIdx == -1) endIdx = afterFrom.indexOf(",");
                if (endIdx == -1) endIdx = Math.min(afterFrom.length(), 30);
                return afterFrom.substring(0, endIdx).trim();
            }
        }

        // Check parent nodes for sender info
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                AccessibilityNodeInfo sibling = parent.getChild(i);
                if (sibling != null && sibling != node) {
                    CharSequence sibText = sibling.getText();
                    if (sibText != null) {
                        String sibStr = sibText.toString().trim();
                        // Sender names are typically short and before the message
                        if (sibStr.length() > 0 && sibStr.length() <= 30 &&
                            !isUILabel(sibStr) && !isTimestamp(sibStr)) {
                            sibling.recycle();
                            parent.recycle();
                            return sibStr;
                        }
                    }
                    sibling.recycle();
                }
            }
            parent.recycle();
        }

        return null;
    }

    /**
     * Check if cache is valid for the given package
     */
    private boolean isCacheValid(String packageName) {
        if (cachedContext == null) return false;
        if (cachedPackage == null || !cachedPackage.equals(packageName)) return false;

        long elapsed = System.currentTimeMillis() - cacheTimestamp;
        return elapsed < CACHE_TTL_MS;
    }

    /**
     * Check if cache is valid (public method)
     */
    public boolean isCacheValid() {
        if (cachedContext == null) return false;
        long elapsed = System.currentTimeMillis() - cacheTimestamp;
        return elapsed < CACHE_TTL_MS;
    }

    /**
     * Cache the extracted context
     */
    private void cacheContext(ScreenContext context, String packageName) {
        this.cachedContext = context;
        this.cachedPackage = packageName;
        this.cacheTimestamp = System.currentTimeMillis();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Context cached for: " + packageName);
        }
    }

    /**
     * Invalidate the cache
     */
    public void invalidateCache() {
        this.cachedContext = null;
        this.cachedPackage = null;
        this.cacheTimestamp = 0;
        this.cachedPhase = null;

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Cache invalidated");
        }
    }

    /**
     * Get the last message from the cached context (if available)
     *
     * @return The last message text or null
     */
    public String getLastMessage() {
        if (cachedContext == null) return null;

        if (cachedContext instanceof Chat) {
            Chat chat = (Chat) cachedContext;
            List<ChatMessage> messages = chat.getMessages();
            if (messages != null && !messages.isEmpty()) {
                // Find the last message NOT from user
                for (int i = messages.size() - 1; i >= 0; i--) {
                    ChatMessage msg = messages.get(i);
                    if (!msg.isFromCurrentUser()) {
                        return msg.getText();
                    }
                }
                // If all from user, return the last one
                return messages.get(messages.size() - 1).getText();
            }
        } else if (cachedContext instanceof Email) {
            Email email = (Email) cachedContext;
            return email.getBody();
        } else if (cachedContext instanceof Generic) {
            Generic generic = (Generic) cachedContext;
            return generic.getScreenText();
        }

        return null;
    }

    /**
     * Get the sender name from the cached context (if available)
     *
     * @return The sender name or null
     */
    public String getSenderName() {
        if (cachedContext == null) return null;

        if (cachedContext instanceof Chat) {
            Chat chat = (Chat) cachedContext;
            List<String> participants = chat.getParticipants();
            if (participants != null && !participants.isEmpty()) {
                return participants.get(0);
            }
        } else if (cachedContext instanceof Email) {
            Email email = (Email) cachedContext;
            return email.getFrom();
        } else if (cachedContext instanceof DatingProfile) {
            DatingProfile profile = (DatingProfile) cachedContext;
            return profile.getName();
        }

        return null;
    }

    /**
     * Get the cached context
     */
    public ScreenContext getCachedContext() {
        return cachedContext;
    }

    // ========== PHASE 1: EMOTION & PHASE DETECTION ==========

    /**
     * Detect conversation phase from cached context.
     *
     * @return PhaseResult or null if no context available
     */
    public ConversationPhaseDetector.PhaseResult detectPhase() {
        if (cachedContext == null) {
            return null;
        }

        String lastMessage = getLastMessage();
        int messageCount = 0;

        if (cachedContext instanceof Chat) {
            Chat chat = (Chat) cachedContext;
            List<ChatMessage> messages = chat.getMessages();
            if (messages != null) {
                messageCount = messages.size();
                cachedPhase = phaseDetector.detectPhase(messages, messages.size() - 1);
            } else {
                cachedPhase = phaseDetector.detectPhase(lastMessage, 0);
            }
        } else {
            cachedPhase = phaseDetector.detectPhase(lastMessage, messageCount);
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[CE] Phase detected: " + cachedPhase);
        }

        return cachedPhase;
    }

    /**
     * Get the cached phase result (doesn't re-detect).
     *
     * @return Cached PhaseResult or null
     */
    public ConversationPhaseDetector.PhaseResult getCachedPhase() {
        return cachedPhase;
    }

    /**
     * Get relationship context hint for prompts based on app category.
     *
     * @return Relationship hint string for prompt injection
     */
    public String getRelationshipContextHint() {
        if (cachedContext == null) {
            return "";
        }

        String viewType = cachedContext.getViewType();

        if ("dating".equalsIgnoreCase(viewType) || cachedContext instanceof DatingProfile) {
            return "RELATIONSHIP CONTEXT: Dating app - be flirty, confident, playful. Show genuine interest.";
        }

        if ("email".equalsIgnoreCase(viewType) || cachedContext instanceof Email) {
            return "RELATIONSHIP CONTEXT: Email - be professional, concise, clear. Match formality level.";
        }

        if ("chat".equalsIgnoreCase(viewType) || cachedContext instanceof Chat) {
            return "RELATIONSHIP CONTEXT: Messaging - be casual, match their energy, keep it natural.";
        }

        return "RELATIONSHIP CONTEXT: General - be friendly and natural.";
    }

    /**
     * Helper to truncate string for logging
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }
}
