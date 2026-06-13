package project.witty.keys.app.parsing;

import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import project.witty.keys.app.context.*; // Import your context models

public class ScreenContextParser {
    private static final String TAG = "ScreenContextParser";

    // --- 1) Package → App Name map (exact matches) ---
    private static final Map<String, String> PACKAGE_NAME_MAP = new HashMap<>();
    static {
        // WhatsApp (consumer + business)
        PACKAGE_NAME_MAP.put("com.whatsapp", "WhatsApp");
        PACKAGE_NAME_MAP.put("com.whatsapp.w4b", "WhatsApp Business");

        // Telegram / Signal / Messenger
        PACKAGE_NAME_MAP.put("org.telegram.messenger", "Telegram");
        PACKAGE_NAME_MAP.put("org.thoughtcrime.securesms", "Signal");
        PACKAGE_NAME_MAP.put("com.facebook.orca", "Messenger");

        // SMS / Messages
        PACKAGE_NAME_MAP.put("com.google.android.apps.messaging", "Messages");
        PACKAGE_NAME_MAP.put("com.samsung.android.messaging", "Samsung Messages");
        PACKAGE_NAME_MAP.put("com.android.messaging", "Messages");

        // Email
        PACKAGE_NAME_MAP.put("com.google.android.gm", "Gmail");
        PACKAGE_NAME_MAP.put("com.microsoft.office.outlook", "Outlook");
        PACKAGE_NAME_MAP.put("ch.protonmail.android", "Proton Mail");

        // Social
        PACKAGE_NAME_MAP.put("com.instagram.android", "Instagram");
        PACKAGE_NAME_MAP.put("com.facebook.katana", "Facebook");
        PACKAGE_NAME_MAP.put("com.twitter.android", "X (Twitter)");
        PACKAGE_NAME_MAP.put("com.reddit.frontpage", "Reddit");
        PACKAGE_NAME_MAP.put("com.snapchat.android", "Snapchat");
        PACKAGE_NAME_MAP.put("com.linkedin.android", "LinkedIn");
        PACKAGE_NAME_MAP.put("com.zhiliaoapp.musically", "TikTok");
        PACKAGE_NAME_MAP.put("com.ss.android.ugc.trill", "TikTok");

        // Work / Chat
        PACKAGE_NAME_MAP.put("com.Slack", "Slack");
        PACKAGE_NAME_MAP.put("com.slack", "Slack"); // some forks use this
        PACKAGE_NAME_MAP.put("com.microsoft.teams", "Microsoft Teams");
        PACKAGE_NAME_MAP.put("com.discord", "Discord");

        // Video / Media
        PACKAGE_NAME_MAP.put("com.google.android.youtube", "YouTube");
        PACKAGE_NAME_MAP.put("com.netflix.mediaclient", "Netflix");
        PACKAGE_NAME_MAP.put("com.spotify.music", "Spotify");

        // Browsers
        PACKAGE_NAME_MAP.put("com.android.chrome", "Chrome");
        PACKAGE_NAME_MAP.put("org.mozilla.firefox", "Firefox");
        PACKAGE_NAME_MAP.put("com.opera.browser", "Opera");
        PACKAGE_NAME_MAP.put("com.brave.browser", "Brave");

        // Dating (handy for your earlier logic)
        PACKAGE_NAME_MAP.put("com.tinder", "Tinder");
        PACKAGE_NAME_MAP.put("co.hinge.app", "Hinge");
        PACKAGE_NAME_MAP.put("com.bumble.app", "Bumble");
    }

    // --- 2) Helpers to classify quickly by package name ---
    private static boolean isWhatsapp(String pkg) {
        return pkg.contains("whatsapp");
    }

    private static boolean isTelegram(String pkg) {
        return "org.telegram.messenger".equals(pkg);
    }

    private static boolean isSignal(String pkg) {
        return "org.thoughtcrime.securesms".equals(pkg);
    }

    private static boolean isMessenger(String pkg) {
        return "com.facebook.orca".equals(pkg);
    }

    private static boolean isSmsMessages(String pkg) {
        return "com.google.android.apps.messaging".equals(pkg)
                || "com.android.messaging".equals(pkg)
                || "com.samsung.android.messaging".equals(pkg);
    }

    private static boolean isGmail(String pkg) {
        return "com.google.android.gm".equals(pkg);
    }

    // --- 3) Resolve a friendly app name for ANY package ---
    private static String resolveAppName(String packageName) {
        if (packageName == null) return "Unknown";
        // Exact match first
        String label = PACKAGE_NAME_MAP.get(packageName);
        if (label != null) return label;

        // Heuristic contains() fallback for families
        String p = packageName.toLowerCase();
        if (p.contains("whatsapp")) return "WhatsApp";
        if (p.contains("telegram")) return "Telegram";
        if (p.contains("signal")) return "Signal";
        if (p.contains("facebook") && p.contains("orca")) return "Messenger";
        if (p.contains("messaging")) return "Messages";
        if (p.contains("gmail") || p.contains("android.gm")) return "Gmail";
        if (p.contains("instagram")) return "Instagram";
        if (p.contains("twitter")) return "X (Twitter)";
        if (p.contains("reddit")) return "Reddit";
        if (p.contains("slack")) return "Slack";
        if (p.contains("discord")) return "Discord";
        if (p.contains("teams")) return "Microsoft Teams";
        if (p.contains("youtube")) return "YouTube";
        if (p.contains("chrome")) return "Chrome";
        if (p.contains("firefox")) return "Firefox";
        if (p.contains("tiktok") || p.contains("musically") || p.contains("ss.android.ugc.trill")) return "TikTok";
        if (p.contains("tinder")) return "Tinder";
        if (p.contains("hinge")) return "Hinge";
        if (p.contains("bumble")) return "Bumble";

        // Default to the raw package name
        return packageName;
    }

    // --- 4) Public API: parse root node and attach friendly app name everywhere ---
    public ScreenContext parse(AccessibilityNodeInfo rootNode, String packageName) {
        if (rootNode == null || packageName == null) {
            return new Generic("Unknown", "No data available.");
        }

        final String appName = resolveAppName(packageName);

        // App-specific parsers (add more as you implement them)
        if (isWhatsapp(packageName)) {
            return parseWhatsAppChat(rootNode); // Already returns "WhatsApp"
        }

        // Example: messaging-style fallback. If you later add specific parsers, branch above.
        if (isTelegram(packageName) || isSignal(packageName) || isMessenger(packageName) || isSmsMessages(packageName)) {
            return parseGenericChat(rootNode, appName);
        }

        // Email quick branch (optional specialized parser later)
        if (isGmail(packageName)) {
            String allText = extractAllTextRecursive(rootNode, new StringBuilder());
            return new Generic(appName, allText); // if you have a Mail model; else Generic
        }

        // Fallback for unhandled apps: just extract all text, but use FRIENDLY NAME
        String allText = extractAllTextRecursive(rootNode, new StringBuilder());
        return new Generic(appName, allText);
    }

    // --- 5) WhatsApp example you already had (kept intact) ---
    private ScreenContext parseWhatsAppChat(AccessibilityNodeInfo rootNode) {
        List<ChatMessage> messages = new ArrayList<>();
        List<String> participants = new ArrayList<>();

        List<AccessibilityNodeInfo> messageListNodes =
                rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/messages_list");
        if (messageListNodes != null && !messageListNodes.isEmpty()) {
            traverseChildrenForMessages(messageListNodes.get(0), messages);
        }

        String[] nameIds = new String[] {
                "com.whatsapp:id/conversation_contact_name",
                "com.whatsapp.w4b:id/conversation_contact_name",
                "com.whatsapp:id/contact_name",
                "com.whatsapp.w4b:id/contact_name"
        };
        for (String id : nameIds) {
            List<AccessibilityNodeInfo> contactNameNodes = rootNode.findAccessibilityNodeInfosByViewId(id);
            if (contactNameNodes != null && !contactNameNodes.isEmpty()) {
                CharSequence name = contactNameNodes.get(0).getText();
                if (name != null) {
                    participants.add(name.toString());
                    break;
                }
            }
        }
        return new Chat("WhatsApp", participants, messages);
    }

    // --- 6) Simple generic chat parser for other messengers ---
    // Tries to extract bubble texts and a title as participant name(s).
    private ScreenContext parseGenericChat(AccessibilityNodeInfo rootNode, String appName) {
        List<ChatMessage> messages = new ArrayList<>();
        List<String> participants = new ArrayList<>();

        // Try some common message text view IDs used by popular messengers
        String[] commonMessageTextIds = new String[] {
                // Telegram
                "org.telegram.messenger:id/message_text",
                // Signal
                "org.thoughtcrime.securesms:id/conversation_item_body",
                // Messenger
                "com.facebook.orca:id/text",
                // Google Messages / AOSP
                "com.google.android.apps.messaging:id/message_text",
                "com.android.messaging:id/message_text",
                // Samsung Messages (varies by version)
                "com.samsung.android.messaging:id/message_text"
        };

        boolean anyFound = false;
        for (String id : commonMessageTextIds) {
            List<AccessibilityNodeInfo> textNodes = safeFindByViewId(rootNode, id);
            if (textNodes != null && !textNodes.isEmpty()) {
                for (AccessibilityNodeInfo n : textNodes) {
                    CharSequence t = n.getText();
                    if (!TextUtils.isEmpty(t)) {
                        messages.add(new ChatMessage("Unknown Sender", t.toString(), null, false));
                        anyFound = true;
                    }
                }
            }
        }
        if (!anyFound) {
            // Fall back to a light recursive scan for text nodes (keeps things resilient)
            collectLikelyMessageTexts(rootNode, messages, 0, 3);
        }

        // Try to grab chat title from a few common toolbar/title IDs
        String[] commonTitleIds = new String[] {
                // Telegram
                "org.telegram.messenger:id/action_bar_title",
                // Signal
                "org.thoughtcrime.securesms:id/conversation_title",
                // Messenger
                "com.facebook.orca:id/titlebar_title_text",
                // Messages
                "com.google.android.apps.messaging:id/conversation_title",
                "com.android.messaging:id/conversation_title"
        };
        for (String id : commonTitleIds) {
            List<AccessibilityNodeInfo> titleNodes = safeFindByViewId(rootNode, id);
            if (titleNodes != null && !titleNodes.isEmpty()) {
                CharSequence name = titleNodes.get(0).getText();
                if (!TextUtils.isEmpty(name)) {
                    participants.add(name.toString());
                    break;
                }
            }
        }

        return new Chat(appName, participants, messages);
    }

    private List<AccessibilityNodeInfo> safeFindByViewId(AccessibilityNodeInfo node, String id) {
        try {
            return node.findAccessibilityNodeInfosByViewId(id);
        } catch (Throwable t) {
            return null; // some OEMs throw on unknown IDs; fail safely
        }
    }

    private void collectLikelyMessageTexts(AccessibilityNodeInfo node, List<ChatMessage> out, int depth, int maxDepth) {
        if (node == null || depth > maxDepth) return;
        CharSequence t = node.getText();
        if (!TextUtils.isEmpty(t)) {
            String s = t.toString().trim();
            // Heuristic: filter out very short strings and timestamps
            if (s.length() > 1 && !s.matches("^\\d{1,2}:\\d{2}([AP]M)?$")) {
                out.add(new ChatMessage("Unknown Sender", s, null, false));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectLikelyMessageTexts(node.getChild(i), out, depth + 1, maxDepth);
        }
    }

    private void traverseChildrenForMessages(AccessibilityNodeInfo node, List<ChatMessage> messages) {
        if (node == null) return;
        String[] messageIds = new String[] {
                "com.whatsapp:id/message_text",
                "com.whatsapp.w4b:id/message_text"
        };
        for (String id : messageIds) {
            List<AccessibilityNodeInfo> textNodes = safeFindByViewId(node, id);
            if (textNodes != null && !textNodes.isEmpty()) {
                CharSequence text = textNodes.get(0).getText();
                if (text != null) {
                    messages.add(new ChatMessage("Unknown Sender", text.toString(), null, false));
                    break;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            traverseChildrenForMessages(node.getChild(i), messages);
        }
    }

    private String extractAllTextRecursive(AccessibilityNodeInfo node, StringBuilder builder) {
        if (node == null) return "";
        if (node.getText() != null && !TextUtils.isEmpty(node.getText())) {
            builder.append(node.getText().toString()).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            extractAllTextRecursive(node.getChild(i), builder);
        }
        return builder.toString();
    }
}
