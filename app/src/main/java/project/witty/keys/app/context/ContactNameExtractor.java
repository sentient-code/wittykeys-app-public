package project.witty.keys.app.context;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import java.util.Map;

/**
 * ContactNameExtractor — NEW file (Sprint 1, Build 7.0)
 *
 * Pure local utility. No network. No storage. No AI.
 * Extracts the contact/group name from the chat header of messaging apps.
 * Used ONLY for local string matching against NLS-captured contact names.
 * The extracted text NEVER leaves the device, NEVER touches any API.
 */
public class ContactNameExtractor {

    // Known resource IDs for chat header contact names per app
    // WhatsApp: contact name is in the ActionBar area
    // Telegram: contact name in toolbar
    // Instagram: contact name in header
    private static final Map<String, String> CHAT_HEADER_IDS = Map.of(
        "com.whatsapp", "com.whatsapp:id/conversation_contact_name",
        "org.telegram.messenger", "org.telegram.messenger:id/action_bar_title",
        "com.instagram.android", "com.instagram.android:id/thread_title"
    );

    /**
     * Extracts the contact/group name from the chat header.
     * Returns null if not in a chat screen or extraction fails.
     * This is a LOCAL operation — no data leaves the device.
     */
    public static String extractFromTree(AccessibilityNodeInfo root, String packageName) {
        if (root == null) return null;

        // Strategy 1: Known resource ID for this app
        String resourceId = CHAT_HEADER_IDS.get(packageName);
        if (resourceId != null) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(resourceId);
            if (nodes != null && !nodes.isEmpty()) {
                CharSequence text = nodes.get(0).getText();
                for (AccessibilityNodeInfo node : nodes) node.recycle();
                if (text != null && text.length() > 0 && text.length() < 100) {
                    return text.toString().trim();
                }
            }
        }

        // Strategy 2: Content description on ActionBar/Toolbar
        // Many apps set contentDescription = "Chat with Mom" or just "Mom"
        return extractFromContentDescription(root, packageName);
    }

    private static String extractFromContentDescription(
            AccessibilityNodeInfo root, String packageName) {
        // Walk first 3 levels looking for Toolbar/ActionBar with contentDescription
        // This is a shallow, fast traversal — not a full tree walk
        for (int i = 0; i < root.getChildCount() && i < 10; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) continue;
            CharSequence desc = child.getContentDescription();
            if (desc != null && desc.length() > 0 && desc.length() < 100) {
                String text = desc.toString();
                // Filter out navigation descriptions
                if (!text.contains("Navigate") && !text.contains("Back")
                        && !text.contains("Menu") && !text.contains("Search")) {
                    child.recycle();
                    return text.trim();
                }
            }
            // One more level deep
            for (int j = 0; j < child.getChildCount() && j < 10; j++) {
                AccessibilityNodeInfo grandchild = child.getChild(j);
                if (grandchild == null) continue;
                CharSequence gDesc = grandchild.getContentDescription();
                if (gDesc != null && gDesc.length() > 0 && gDesc.length() < 100) {
                    String gText = gDesc.toString();
                    if (!gText.contains("Navigate") && !gText.contains("Back")) {
                        grandchild.recycle();
                        child.recycle();
                        return gText.trim();
                    }
                }
                grandchild.recycle();
            }
            child.recycle();
        }
        return null;
    }

    /**
     * Returns true if we have a known chat header resource ID for this package.
     * Used to determine extraction confidence.
     */
    public static boolean hasKnownHeaderId(String packageName) {
        return CHAT_HEADER_IDS.containsKey(packageName);
    }
}
