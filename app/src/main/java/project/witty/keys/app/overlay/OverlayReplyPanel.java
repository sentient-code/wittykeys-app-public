package project.witty.keys.app.overlay;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import project.witty.keys.R;
import project.witty.keys.app.context.ConversationReplyState;
import project.witty.keys.app.context.ConversationReplyStateBuilder;
import project.witty.keys.app.context.NlsMessageBuffer;
import project.witty.keys.app.context.ReplyCache;
import project.witty.keys.app.context.ConversationMatcher;
import project.witty.keys.app.entitlements.AiActionType;
import project.witty.keys.app.entitlements.AiEntitlementDecision;
import project.witty.keys.app.entitlements.AiEntitlementManager;
import project.witty.keys.app.helpers.WittyKeysNotificationListenerService;

/**
 * OverlayReplyPanel — Build 7.1 MVP (Redesigned to match HTML mockup)
 *
 * Tabbed reply popup with:
 * - Bottom-border tabs per contact
 * - Chat-bubble message display (received left, sent right)
 * - AI suggestion chips (accent-glow, right-aligned)
 * - Direct reply via RemoteInput (fallback: clipboard)
 */
public class OverlayReplyPanel {

    private static final String TAG = "WK_OVERLAY_REPLY";
    private static final String SENT_WAITING_MESSAGE = "Sent. Waiting for their reply.";

    // Message types for adapter
    private static final int MSG_TYPE_RECEIVED = 0;
    private static final int MSG_TYPE_SENT = 1;

    private final Context context;
    private final WittyKeysOverlayService overlayService;

    // Data
    private List<ContactTab> contactTabs = new ArrayList<>();
    private int selectedTabIndex = 0;

    // Views
    private View popupView;
    private LinearLayout tabContainer;
    private RecyclerView messagesRecycler;
    private LinearLayout suggestionsContainer;
    private EditText replyInput;
    private ImageButton sendButton;
    private TextView contactNameView;
    private TextView subtitleView;
    private FrameLayout iconFrame;
    private ImageView appIconView;
    private TextView openAppView;

    private boolean isShowing = false;

    /**
     * Data model for a contact tab.
     */
    static class ContactTab {
        final String contactName;
        final String packageName;
        final String appDisplayName;
        final String conversationKey;
        final List<ChatMessage> messages;
        final ConversationReplyState replyState;

        ContactTab(String contactName, String packageName, String appDisplayName,
                   String conversationKey, List<ChatMessage> messages, ConversationReplyState replyState) {
            this.contactName = contactName;
            this.packageName = packageName;
            this.appDisplayName = appDisplayName;
            this.conversationKey = conversationKey;
            this.messages = messages;
            this.replyState = replyState;
        }
    }

    /**
     * Chat message with type (received or sent).
     */
    static class ChatMessage {
        final String sender;
        final String text;
        final boolean isSent;

        ChatMessage(String sender, String text, boolean isSent) {
            this.sender = sender != null && !sender.trim().isEmpty()
                    ? sender.trim()
                    : (isSent ? "You" : "Unknown");
            this.text = text;
            this.isSent = isSent;
        }
    }

    public OverlayReplyPanel(Context context, WittyKeysOverlayService overlayService) {
        this.context = context;
        this.overlayService = overlayService;
    }

    // ─── Show/Hide ───

    public void show() {
        buildTabs();

        if (isShowing && popupView == null) {
            isShowing = false;
        }

        if (isShowing) {
            if (contactTabs.isEmpty()) {
                showEmptyState();
            } else {
                selectTab(0);
            }
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        popupView = inflater.inflate(R.layout.overlay_reply_popup, null);
        initViews();

        if (contactTabs.isEmpty()) {
            showEmptyState();
        } else {
            selectTab(0);
        }

        overlayService.showPopup(popupView, "reply", 340, 460);
        isShowing = true;
        Log.d(TAG, "Reply popup shown with " + contactTabs.size() + " tabs");
    }

    private void showEmptyState() {
        if (contactNameView != null) contactNameView.setText("Quick Replies");
        if (subtitleView != null) {
            subtitleView.setText("No pending messages");
            subtitleView.setVisibility(View.VISIBLE);
        }
        if (tabContainer != null) tabContainer.removeAllViews();
        if (suggestionsContainer != null) {
            suggestionsContainer.removeAllViews();

            TextView emptyText = new TextView(context);
            emptyText.setText("No pending replies right now.\nNew messages will appear here automatically.");
            emptyText.setTextSize(12);
            emptyText.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_text3));
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24));
            suggestionsContainer.addView(emptyText);
        }
        if (messagesRecycler != null) {
            messagesRecycler.setAdapter(new MessageAdapter(new ArrayList<>()));
        }
    }

    public void refreshIfShowing() {
        if (!isShowing || popupView == null) return;

        int previousCount = contactTabs.size();
        buildTabs();

        if (contactTabs.isEmpty()) {
            showEmptyState();
        } else {
            if (selectedTabIndex >= contactTabs.size()) {
                selectedTabIndex = 0;
            }
            selectTab(selectedTabIndex);
        }

        Log.d(TAG, "Reply panel refreshed: " + previousCount + " → " + contactTabs.size() + " tabs");
    }

    public void hide() {
        if (!isShowing) return;
        overlayService.hidePopupFromPanel();
        popupView = null;
        isShowing = false;
        Log.d(TAG, "Reply popup hidden");
    }

    void onPopupDismissedByService() {
        popupView = null;
        isShowing = false;
        selectedTabIndex = 0;
        Log.d(TAG, "Reply popup externally dismissed");
    }

    public boolean isShowing() {
        return isShowing;
    }

    /** Debug-only: inject deterministic reply popup states for golden screenshot testing. */
    public void setDebugState(String state) {
        if (isShowing) {
            hide();
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        popupView = inflater.inflate(R.layout.overlay_reply_popup, null);
        initViews();
        contactTabs.clear();
        selectedTabIndex = 0;

        if ("reply_populated".equals(state)) {
            seedDebugTabs();
            selectTab(0);
        } else if ("reply_loading".equals(state)) {
            seedDebugLoadingTab();
            selectTab(0);
        } else {
            showEmptyState();
        }

        overlayService.showPopup(popupView, "reply", 340, 460);
        isShowing = true;
        Log.d(TAG, "Reply debug popup shown: " + state);
    }

    // ─── View Setup ───

    private void initViews() {
        contactNameView = popupView.findViewById(R.id.overlay_reply_contact_name);
        subtitleView = popupView.findViewById(R.id.overlay_reply_subtitle);
        iconFrame = popupView.findViewById(R.id.overlay_reply_icon_frame);
        appIconView = popupView.findViewById(R.id.overlay_reply_app_icon);
        openAppView = popupView.findViewById(R.id.overlay_reply_open_app);
        tabContainer = popupView.findViewById(R.id.overlay_reply_tabs);
        messagesRecycler = popupView.findViewById(R.id.overlay_reply_messages);
        suggestionsContainer = popupView.findViewById(R.id.overlay_reply_suggestions);
        replyInput = popupView.findViewById(R.id.overlay_reply_input);
        sendButton = popupView.findViewById(R.id.overlay_reply_send);

        if (messagesRecycler != null) {
            LinearLayoutManager lm = new LinearLayoutManager(context);
            lm.setStackFromEnd(true);
            messagesRecycler.setLayoutManager(lm);
        }

        if (sendButton != null) {
            sendButton.setOnClickListener(v -> sendReply());
        }

        if (replyInput != null) {
            replyInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    sendReply();
                    return true;
                }
                return false;
            });
        }

        if (openAppView != null) {
            openAppView.setOnClickListener(v -> openChatApp());
        }
    }

    private void seedDebugTabs() {
        List<ChatMessage> whatsappMessages = new ArrayList<>();
        whatsappMessages.add(new ChatMessage("Ananya", "Can you send the final deck today?", false));
        whatsappMessages.add(new ChatMessage("Ananya", "Client review moved to 6.", false));
        List<String> whatsappReplies = new ArrayList<>();
        whatsappReplies.add("Yes, I will send it before 5:30.");
        whatsappReplies.add("I am polishing the final slides now.");
        whatsappReplies.add("Can we keep 10 minutes for a quick check?");
        contactTabs.add(new ContactTab(
            "Ananya", "com.whatsapp", "WhatsApp", "com.whatsapp|Ananya",
            whatsappMessages, debugReplyState("com.whatsapp|Ananya", "com.whatsapp", "Ananya", whatsappMessages, whatsappReplies)));

        List<ChatMessage> telegramMessages = new ArrayList<>();
        telegramMessages.add(new ChatMessage("Rahul", "Are we still on for dinner?", false));
        telegramMessages.add(new ChatMessage("You", "I may be 10 minutes late.", true));
        telegramMessages.add(new ChatMessage("Rahul", "No problem, ping when you leave.", false));
        List<String> telegramReplies = new ArrayList<>();
        telegramReplies.add("Sure, I will message when I start.");
        telegramReplies.add("Thanks, see you soon.");
        contactTabs.add(new ContactTab(
            "Rahul", "org.telegram.messenger", "Telegram", "org.telegram.messenger|Rahul",
            telegramMessages, debugReplyState("org.telegram.messenger|Rahul", "org.telegram.messenger", "Rahul", telegramMessages, telegramReplies)));
    }

    private ConversationReplyState debugReplyState(
            String conversationKey,
            String packageName,
            String contactName,
            List<ChatMessage> messages,
            List<String> suggestions) {
        List<ConversationReplyState.Message> stateMessages = new ArrayList<>();
        String latestIncomingId = null;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            String id = "debug-" + i;
            stateMessages.add(new ConversationReplyState.Message(
                    message.sender,
                    message.text,
                    message.isSent,
                    id));
            if (!message.isSent) latestIncomingId = id;
        }
        return ConversationReplyState.fromMessages(
                conversationKey,
                packageName,
                contactName,
                stateMessages,
                suggestions,
                latestIncomingId);
    }

    private void seedDebugLoadingTab() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("Ananya", "Client review moved to 6.", false));
        List<ConversationReplyState.Message> stateMessages = new ArrayList<>();
        stateMessages.add(new ConversationReplyState.Message("Ananya", "Client review moved to 6.", false, "debug-loading-in-1"));
        ConversationReplyState state = ConversationReplyState.fromMessages(
                "com.whatsapp|Ananya",
                "com.whatsapp",
                "Ananya",
                stateMessages,
                new ArrayList<>(),
                "different-id");
        contactTabs.add(new ContactTab(
                "Ananya", "com.whatsapp", "WhatsApp", "com.whatsapp|Ananya", messages, state));
    }

    // ─── Tab Building ───

    private void buildTabs() {
        contactTabs.clear();

        ReplyCache cache = ReplyCache.getInstance();
        ConversationMatcher matcher = ConversationMatcher.getInstance();
        NlsMessageBuffer msgBuffer = NlsMessageBuffer.getInstance();

        if (cache == null || matcher == null || msgBuffer == null) {
            Log.w(TAG, "ReplyCache or ConversationMatcher not available");
            return;
        }

        List<NlsMessageBuffer.ConversationSnapshot> conversations = msgBuffer.getOpenConversations();
        for (NlsMessageBuffer.ConversationSnapshot conversation : conversations) {
            matcher.registerNlsContact(conversation.packageName, conversation.contactName);
            ConversationReplyState state =
                    ConversationReplyStateBuilder.fromSnapshot(conversation, cache);

            List<ChatMessage> chatMessages = new ArrayList<>();
            for (NlsMessageBuffer.BufferedMessage msg : conversation.messages) {
                chatMessages.add(new ChatMessage(msg.sender, msg.text, msg.isSent));
            }

            String appName = getAppDisplayName(conversation.packageName);
            contactTabs.add(new ContactTab(
                conversation.contactName,
                conversation.packageName,
                appName,
                conversation.conversationKey,
                chatMessages,
                state
            ));
        }

        Log.d(TAG, "Built " + contactTabs.size() + " contact tabs");
    }

    /**
     * Render tabs with bottom-border style (matching mockup).
     * Active tab: accent color text + accent bottom border.
     * Inactive tab: text2 color, no border.
     */
    private void renderTabs() {
        if (tabContainer == null) return;
        tabContainer.removeAllViews();

        for (int i = 0; i < contactTabs.size(); i++) {
            ContactTab tab = contactTabs.get(i);
            final int index = i;

            LinearLayout tabItem = new LinearLayout(context);
            tabItem.setOrientation(LinearLayout.HORIZONTAL);
            tabItem.setGravity(Gravity.CENTER_VERTICAL);
            tabItem.setPadding(dpToPx(12), dpToPx(7), dpToPx(12), dpToPx(7));

            // Tab text
            TextView tabText = new TextView(context);
            tabText.setText(tab.contactName);
            tabText.setTextSize(11);
            tabText.setSingleLine(true);

            if (i == selectedTabIndex) {
                tabText.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_accent));
                tabText.setTypeface(null, Typeface.BOLD);
                // Bottom border via background drawable
                GradientDrawable bottomBorder = new GradientDrawable();
                bottomBorder.setColor(Color.TRANSPARENT);
                tabItem.setBackground(null);
                // We'll use a view below for the border
            } else {
                tabText.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_text2));
                tabText.setTypeface(null, Typeface.NORMAL);
            }

            tabItem.addView(tabText);

            // Tab badge (message count)
            if (tab.messages.size() > 0) {
                TextView badge = new TextView(context);
                badge.setText(String.valueOf(tab.messages.size()));
                badge.setTextSize(9);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);

                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setShape(GradientDrawable.OVAL);
                badgeBg.setColor(ContextCompat.getColor(context, R.color.wk_overlay_accent_glow));
                badge.setBackground(badgeBg);
                badge.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_accent));

                int badgeSize = dpToPx(16);
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
                badgeLp.setMarginStart(dpToPx(5));
                tabItem.addView(badge, badgeLp);
            }

            // Wrap in container with bottom border for active tab
            LinearLayout tabWrapper = new LinearLayout(context);
            tabWrapper.setOrientation(LinearLayout.VERTICAL);
            tabWrapper.addView(tabItem);

            if (i == selectedTabIndex) {
                View borderLine = new View(context);
                borderLine.setBackgroundColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_accent));
                LinearLayout.LayoutParams borderLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(2));
                tabWrapper.addView(borderLine, borderLp);
            } else {
                View borderLine = new View(context);
                borderLine.setBackgroundColor(Color.TRANSPARENT);
                LinearLayout.LayoutParams borderLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(2));
                tabWrapper.addView(borderLine, borderLp);
            }

            tabWrapper.setOnClickListener(v -> selectTab(index));
            tabContainer.addView(tabWrapper);
        }
    }

    // ─── Tab Selection ───

    private void selectTab(int index) {
        if (index < 0 || index >= contactTabs.size()) return;

        selectedTabIndex = index;
        ContactTab tab = contactTabs.get(index);

        // Update header
        if (contactNameView != null) {
            contactNameView.setText(tab.contactName);
        }
        if (subtitleView != null) {
            subtitleView.setText(tab.appDisplayName);
            subtitleView.setVisibility(View.VISIBLE);
        }

        // Update icon frame background based on app
        if (iconFrame != null) {
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(dpToPx(7));
            iconBg.setColor(getAppIconBgColor(tab.packageName));
            iconFrame.setBackground(iconBg);
        }

        if (appIconView != null) {
            try {
                appIconView.setImageDrawable(
                    context.getPackageManager().getApplicationIcon(tab.packageName));
            } catch (PackageManager.NameNotFoundException e) {
                appIconView.setImageResource(R.drawable.settings_icon);
            }
        }

        renderTabs();
        updateMessages(tab);
        updateSuggestions(tab);
    }

    /**
     * Get app-specific icon background color.
     */
    private int getAppIconBgColor(String packageName) {
        switch (packageName) {
            case "com.whatsapp":
            case "com.whatsapp.w4b":
                return ContextCompat.getColor(context, R.color.wk_overlay_whatsapp_bg);
            case "org.telegram.messenger":
                return ContextCompat.getColor(context, R.color.wk_overlay_telegram_bg);
            case "com.instagram.android":
                return ContextCompat.getColor(context, R.color.wk_overlay_instagram_bg);
            case "com.facebook.orca":
                return ContextCompat.getColor(context, R.color.wk_overlay_messenger_bg);
            default:
                return ContextCompat.getColor(context, R.color.wk_overlay_accent_glow);
        }
    }

    private void updateMessages(ContactTab tab) {
        if (messagesRecycler == null) return;

        messagesRecycler.setAdapter(new MessageAdapter(tab.messages));
        if (!tab.messages.isEmpty()) {
            messagesRecycler.scrollToPosition(tab.messages.size() - 1);
        }
    }

    private void updateSuggestions(ContactTab tab) {
        if (suggestionsContainer == null) return;
        suggestionsContainer.removeAllViews();

        AiEntitlementDecision decision = AiEntitlementManager.getInstance(context)
                .canRun(AiActionType.SHORT_TEXT);
        if (!decision.allowed) {
            showQuotaUpgradeMessage("Daily limit exhausted. It will reset soon.");
            return;
        }

        ConversationReplyState state = tab.replyState;
        if (state != null && state.blockedReason == ConversationReplyState.BlockedReason.IN_FLIGHT) {
            showSuggestionsLoading();
            return;
        }
        if (state == null || !state.canShowSuggestions) {
            showWaitingForSuggestions(state);
            return;
        }
        List<String> suggestions = state.suggestions;

        // "AI SUGGESTIONS" label
        TextView label = new TextView(context);
        label.setText("AI SUGGESTIONS");
        label.setTextSize(8);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_text3));
        label.setLetterSpacing(0.1f);
        label.setPadding(0, dpToPx(6), 0, dpToPx(3));
        suggestionsContainer.addView(label);

        // Suggestion chips — right-aligned, vertical stack
        int count = Math.min(suggestions.size(), 4);
        for (int i = 0; i < count; i++) {
            String suggestion = suggestions.get(i);
            TextView chip = createSuggestionChip(suggestion, tab);

            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.gravity = Gravity.END;
            chipLp.bottomMargin = dpToPx(4);
            suggestionsContainer.addView(chip, chipLp);
        }
    }

    private TextView createSuggestionChip(String text, ContactTab tab) {
        TextView chip = new TextView(context);
        chip.setText(truncate(text, 40));
        chip.setTextSize(12);
        chip.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_accent));
        chip.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        chip.setSingleLine(false);
        chip.setMaxLines(2);
        chip.setBackground(ContextCompat.getDrawable(context, R.drawable.overlay_suggestion_chip_bg));

        chip.setOnClickListener(v -> {
            if (replyInput != null) {
                replyInput.setText(text);
                replyInput.setSelection(text.length());
            }
        });

        return chip;
    }

    private void showQuotaUpgradeMessage(String message) {
        TextView chip = new TextView(context);
        chip.setText(message != null && !message.isEmpty()
            ? message
            : "AI credits used. Upgrade for more quick replies.");
        chip.setTextSize(12);
        chip.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_accent));
        chip.setPadding(dpToPx(12), dpToPx(9), dpToPx(12), dpToPx(9));
        chip.setBackground(ContextCompat.getDrawable(context, R.drawable.overlay_suggestion_chip_bg));
        suggestionsContainer.addView(chip);
    }

    private void showWaitingForSuggestions(ConversationReplyState state) {
        showStatusMessage(state != null && state.statusMessage != null && !state.statusMessage.isEmpty()
                ? state.statusMessage
                : "Reply manually while AI suggestions update.");
    }

    private void showSuggestionsLoading() {
        TextView label = new TextView(context);
        label.setText("AI SUGGESTIONS");
        label.setTextSize(8);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_text3));
        label.setLetterSpacing(0.1f);
        label.setPadding(0, dpToPx(6), 0, dpToPx(4));
        suggestionsContainer.addView(label);

        LinearLayout loadingRow = new LinearLayout(context);
        loadingRow.setOrientation(LinearLayout.HORIZONTAL);
        loadingRow.setGravity(Gravity.CENTER_VERTICAL);
        loadingRow.setPadding(dpToPx(10), dpToPx(8), dpToPx(12), dpToPx(8));
        loadingRow.setBackground(ContextCompat.getDrawable(context, R.drawable.overlay_suggestion_chip_bg));

        ProgressBar spinner = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminate(true);
        spinner.getIndeterminateDrawable().setTint(ContextCompat.getColor(context, R.color.wk_overlay_dark_accent));
        loadingRow.addView(spinner, new LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)));

        TextView text = new TextView(context);
        text.setText("AI suggestions are loading");
        text.setTextSize(11);
        text.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_text2));
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.setMarginStart(dpToPx(8));
        loadingRow.addView(text, textLp);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.gravity = Gravity.END;
        suggestionsContainer.addView(loadingRow, rowLp);
    }

    private void showStatusMessage(String message) {
        TextView waiting = new TextView(context);
        waiting.setText(message != null && !message.isEmpty()
                ? message
                : "Reply manually while AI suggestions update.");
        waiting.setTextSize(11);
        waiting.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_text3));
        waiting.setPadding(0, dpToPx(6), 0, dpToPx(8));
        suggestionsContainer.addView(waiting);
    }

    // ─── Reply Sending ───

    private void sendReply() {
        if (replyInput == null || selectedTabIndex >= contactTabs.size()) return;

        String text = replyInput.getText().toString().trim();
        if (text.isEmpty()) return;

        ContactTab tab = contactTabs.get(selectedTabIndex);

        // Try RemoteInput first
        WittyKeysNotificationListenerService.RemoteInputSendResult sendResult =
            WittyKeysNotificationListenerService.sendDirectReply(
            tab.conversationKey, text);

        if (sendResult.sent) {
            WittyKeysNotificationListenerService.trackSentReply(tab.conversationKey, text);
            NlsMessageBuffer.getInstance().addSentMessage(tab.conversationKey, text);
            Toast.makeText(context, "Reply sent to " + tab.contactName, Toast.LENGTH_SHORT).show();
            replyInput.setText("");

            ReplyCache cache = ReplyCache.getInstance();
            if (cache != null) {
                cache.invalidateConversation(tab.conversationKey);
            }
            String conversationKey = tab.conversationKey;
            buildTabs();
            selectedTabIndex = findTabIndex(conversationKey);
            selectTab(selectedTabIndex);
            overlayService.updateBadge(getPendingReplyCount());
        } else {
            // Fallback: copy to clipboard + open app
            ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("WittyKeys Reply", text));
            }
            Toast.makeText(context, "Copied! Opening " + tab.appDisplayName + "...",
                Toast.LENGTH_SHORT).show();
            if (subtitleView != null) {
                subtitleView.setText("Copied. Open app to send.");
                subtitleView.setVisibility(View.VISIBLE);
            }
            openChatApp();
        }
    }

    private void openChatApp() {
        if (selectedTabIndex >= contactTabs.size()) return;
        ContactTab tab = contactTabs.get(selectedTabIndex);

        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(tab.packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open app: " + e.getMessage());
        }
    }

    // ─── Badge Count ───

    public int getPendingReplyCount() {
        int count = 0;
        ReplyCache cache = ReplyCache.getInstance();
        for (NlsMessageBuffer.ConversationSnapshot conversation :
                NlsMessageBuffer.getInstance().getOpenConversations()) {
            ConversationReplyState state =
                    ConversationReplyStateBuilder.fromSnapshot(conversation, cache);
            if (state.badgeEligible) count++;
        }
        return count;
    }

    private int findTabIndex(String conversationKey) {
        for (int i = 0; i < contactTabs.size(); i++) {
            if (contactTabs.get(i).conversationKey.equals(conversationKey)) return i;
        }
        return 0;
    }

    // ─── Chat-Bubble Message Adapter ───

    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
        private final List<ChatMessage> messages;

        MessageAdapter(List<ChatMessage> messages) {
            this.messages = messages != null ? messages : new ArrayList<>();
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).isSent ? MSG_TYPE_SENT : MSG_TYPE_RECEIVED;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout wrapper = new LinearLayout(parent.getContext());
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(12);
            tv.setLineSpacing(0, 1.4f);
            tv.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dpToPx(6);

            if (viewType == MSG_TYPE_SENT) {
                lp.gravity = Gravity.END;
                tv.setBackground(ContextCompat.getDrawable(parent.getContext(),
                    R.drawable.overlay_msg_sent_bg));
                tv.setTextColor(ContextCompat.getColor(parent.getContext(),
                    R.color.wk_overlay_dark_text));
            } else {
                lp.gravity = Gravity.START;
                tv.setBackground(ContextCompat.getDrawable(parent.getContext(),
                    R.drawable.overlay_msg_received_bg));
                tv.setTextColor(ContextCompat.getColor(parent.getContext(),
                    R.color.wk_overlay_dark_text));
            }

            // Max width 88%
            int maxWidth = (int) (parent.getWidth() * 0.88f);
            if (maxWidth > 0) tv.setMaxWidth(maxWidth);

            tv.setLayoutParams(lp);
            wrapper.addView(tv);
            return new VH(wrapper, tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ChatMessage msg = messages.get(position);
            holder.textView.setText(formatMessage(msg));
        }

        @Override public int getItemCount() { return messages.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView textView;
            VH(View wrapper, TextView tv) {
                super(wrapper);
                textView = tv;
            }
        }
    }

    private SpannableString formatMessage(ChatMessage msg) {
        String sender = msg.isSent ? "You" : msg.sender;
        String body = msg.text != null ? msg.text : "";
        String value = sender + "\n" + body;
        SpannableString span = new SpannableString(value);
        int labelEnd = sender.length();
        int labelColor = msg.isSent
                ? ContextCompat.getColor(context, R.color.wk_overlay_dark_accent)
                : ContextCompat.getColor(context, R.color.wk_overlay_dark_text2);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new RelativeSizeSpan(0.82f), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(labelColor), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    // ─── Utility ───

    private String getAppDisplayName(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            switch (packageName) {
                case "com.whatsapp": return "WhatsApp";
                case "com.whatsapp.w4b": return "WhatsApp Business";
                case "com.instagram.android": return "Instagram";
                case "com.google.android.apps.messaging": return "Messages";
                case "com.google.android.apps.dynamite": return "Google Chat";
                case "org.telegram.messenger": return "Telegram";
                case "com.facebook.orca": return "Messenger";
                case "com.Slack": return "Slack";
                default: return packageName;
            }
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
