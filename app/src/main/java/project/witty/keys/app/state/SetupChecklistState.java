package project.witty.keys.app.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SetupChecklistState {
    public enum ItemId {
        KEYBOARD_ENABLED,
        KEYBOARD_DEFAULT,
        OVERLAY_BUBBLE,
        APP_NOTIFICATIONS,
        NOTIFICATION_ACCESS,
        ACCESSIBILITY_HELPER,
        SCREEN_CAPTURE
    }

    public enum Status {
        DONE,
        REQUIRED_MISSING,
        OPTIONAL_MISSING,
        ASK_WHEN_USED,
        NEEDS_REPAIR,
        CHECKING,
        NOT_AVAILABLE
    }

    public static final class Item {
        public final ItemId id;
        public final String title;
        public final String benefit;
        public final Status status;
        public final String label;
        public final boolean required;

        Item(ItemId id, String title, String benefit, Status status, String label, boolean required) {
            this.id = id;
            this.title = title;
            this.benefit = benefit;
            this.status = status;
            this.label = label;
            this.required = required;
        }

        public boolean isDone() {
            return status == Status.DONE || status == Status.ASK_WHEN_USED;
        }
    }

    public final List<Item> items;
    public final Item screenCaptureStatus;
    public final int readyCount;
    public final int totalCount;

    private SetupChecklistState(List<Item> items) {
        this.items = Collections.unmodifiableList(items);
        Item screen = null;
        int ready = 0;
        int total = 0;
        for (Item item : items) {
            if (item.id == ItemId.SCREEN_CAPTURE) {
                screen = item;
            } else {
                total++;
                if (item.isDone()) {
                    ready++;
                }
            }
        }
        this.screenCaptureStatus = screen;
        this.readyCount = ready;
        this.totalCount = total;
    }

    public static SetupChecklistState fromFacts(
            boolean keyboardEnabled,
            boolean keyboardDefault,
            boolean overlayAllowed,
            boolean appNotifications,
            boolean notificationAccess,
            boolean accessibilityEnabled) {
        List<Item> items = new ArrayList<>();
        items.add(new Item(
                ItemId.KEYBOARD_ENABLED,
                "Keyboard enabled",
                "Use WittyKeys where you type.",
                keyboardEnabled ? Status.DONE : Status.REQUIRED_MISSING,
                keyboardEnabled ? "Done" : "Enable",
                true));
        items.add(new Item(
                ItemId.KEYBOARD_DEFAULT,
                "Keyboard configuration",
                "Show the Smart Assistant Bar while typing.",
                keyboardDefault ? Status.DONE : Status.REQUIRED_MISSING,
                keyboardDefault ? "Done" : "Set default",
                true));
        items.add(new Item(
                ItemId.OVERLAY_BUBBLE,
                "Floating overlay",
                "Show WittyKeys above other apps for Ask AI and Quick Reply.",
                overlayAllowed ? Status.DONE : Status.REQUIRED_MISSING,
                overlayAllowed ? "Done" : "Enable",
                true));
        items.add(new Item(
                ItemId.APP_NOTIFICATIONS,
                "App notifications",
                "Receive push updates for account, subscription, usage, and important WittyKeys alerts.",
                appNotifications ? Status.DONE : Status.REQUIRED_MISSING,
                appNotifications ? "Done" : "Enable",
                true));
        items.add(new Item(
                ItemId.NOTIFICATION_ACCESS,
                "Notification access",
                "Catch message notifications so Quick Reply can track recent conversations.",
                notificationAccess ? Status.DONE : Status.OPTIONAL_MISSING,
                notificationAccess ? "Done" : "Optional",
                false));
        items.add(new Item(
                ItemId.ACCESSIBILITY_HELPER,
                "Accessibility helper",
                "Improve active chat detection and context matching with prominent disclosure.",
                accessibilityEnabled ? Status.DONE : Status.OPTIONAL_MISSING,
                accessibilityEnabled ? "Done" : "Optional",
                false));
        items.add(new Item(
                ItemId.SCREEN_CAPTURE,
                "Screen capture",
                "Ask AI about the screen you choose.",
                Status.ASK_WHEN_USED,
                "Ask when used",
                false));
        return new SetupChecklistState(items);
    }

    public Item item(ItemId id) {
        for (Item item : items) {
            if (item.id == id) return item;
        }
        throw new IllegalArgumentException("Missing setup item: " + id);
    }
}
