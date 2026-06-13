package project.witty.keys.keyboard.AiChat;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import project.witty.keys.ui.chat.WkChatHeader;
import project.witty.keys.ui.chat.WkInputBar;
import project.witty.keys.ui.chat.WkEmptyState;
import project.witty.keys.ui.chat.WkSessionCard;
import project.witty.keys.ui.chat.WkDualCtaRow;
import project.witty.keys.ui.chat.Surface;
import project.witty.keys.ui.chat.WkSessionDisplay;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import project.witty.keys.R;
import project.witty.keys.app.context.AiChatStateManager;
import project.witty.keys.app.context.UnifiedChatSessionManager;
import project.witty.keys.app.database.ChatMessage;
import project.witty.keys.app.database.ChatSession;
import project.witty.keys.app.database.SessionScreenshot;
import project.witty.keys.app.database.WittyKeysDatabase;
import project.witty.keys.app.helpers.ScreenshotPermissionActivity;
import project.witty.keys.app.overlay.OverlayAiEngine;

/**
 * Full-screen AI Chat Activity — launched from compact UnifiedAiView
 * via "↗ Open Full Screen" button.
 *
 * NOT onEvaluateFullscreenMode() — that breaks WhatsApp.
 *
 * Uses AiChatStateManager singleton for state handoff between
 * compact (UnifiedAiView) and full-screen (this Activity).
 *
 * Reuses ChatAdapter with all ViewHolder types from compact mode.
 * No capture button — capture only happens in compact where the
 * other app is visible.
 */
public class AiChatActivity extends AppCompatActivity {

    private static final String TAG = "AiChatActivity";

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private LinearLayoutManager chatLayoutManager;
    private WkChatHeader chatHeader;
    private WkInputBar wkInputBar;
    private WkDualCtaRow sessionCtaRow;
    private View sessionListContainer;
    private View chatContainer;
    private RecyclerView sessionRecyclerView;

    private WkEmptyState emptyStateView;
    private SessionListAdapter sessionListAdapter;

    private AiChatStateManager stateManager;
    private UnifiedChatSessionManager unifiedSessionManager;
    private OverlayAiEngine fsAiEngine;
    private List<ChatItem> chatItems = new ArrayList<>();
    private long currentSessionId = -1;
    private WkEmptyState emptySessionsStateView;
    private View newChatBtn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        // Blend status bar with toolbar background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.wk_surface));
        }

        stateManager = AiChatStateManager.getInstance();
        stateManager.init(this);
        unifiedSessionManager = UnifiedChatSessionManager.getInstance(this);
        fsAiEngine = new OverlayAiEngine(this);

        initViews();
        setupToolbar();
        setupChatRecyclerView();
        setupInputField();

        // Debug state handling (golden pipeline)
        String debugState = getIntent().getStringExtra("debug_state");
        // Overlay session handoff (expand from screenshot AI overlay)
        ArrayList<String> overlayMsgTexts = getIntent().getStringArrayListExtra("overlay_msg_texts");
        ArrayList<String> overlayMsgTypes = getIntent().getStringArrayListExtra("overlay_msg_types");

        // Check for unified session_id from intent
        long intentSessionId = getIntent().getLongExtra("session_id", -1);
        if (intentSessionId > 0) {
            currentSessionId = intentSessionId;
            unifiedSessionManager.setActiveSessionId(intentSessionId);
        }

        if (debugState != null) {
            handleDebugState(debugState);
        } else if (overlayMsgTexts != null && overlayMsgTypes != null && !overlayMsgTexts.isEmpty()) {
            loadOverlaySession(overlayMsgTexts, overlayMsgTypes);
        } else if (intentSessionId > 0) {
            // Launched for a specific session — open it
            openSession(intentSessionId);
        } else {
            // Default: show session list
            toggleSessionList();
            loadSessionList();
            // Ensure input bar hidden for session list
            if (wkInputBar != null) wkInputBar.setVisibility(View.GONE);
        }

        Log.d(TAG, "AiChatActivity created. Session: " +
            stateManager.getActiveSessionId());
    }

    private void initViews() {
        chatRecyclerView = findViewById(R.id.fs_chat_recycler);
        chatHeader = findViewById(R.id.fs_chat_header);
        wkInputBar = findViewById(R.id.fs_input_bar);
        sessionCtaRow = findViewById(R.id.fs_session_cta_row);
        sessionListContainer = findViewById(R.id.fs_session_list_container);
        chatContainer = findViewById(R.id.fs_chat_container);
        sessionRecyclerView = findViewById(R.id.fs_session_recycler);
        emptyStateView = findViewById(R.id.fs_empty_state);
        emptySessionsStateView = findViewById(R.id.fs_sessions_empty_state);
        newChatBtn = findViewById(R.id.fs_new_chat_btn);
    }

    private void setupToolbar() {
        chatHeader.setBackVisible(true);
        chatHeader.setOnBackClickListener(() -> onBackPressed());
        chatHeader.setOnSessionsClickListener(() -> toggleSessionList());
        chatHeader.setOnCloseClickListener(() -> finish());
        if (sessionCtaRow != null) {
            sessionCtaRow.setPrimary("Screenshot", () -> {
                Intent captureIntent = new Intent(AiChatActivity.this, ScreenshotPermissionActivity.class);
                startActivity(captureIntent);
                Log.d(TAG, "[FV2] Screenshot CTA: launching ScreenshotPermissionActivity");
            });
            sessionCtaRow.setPrimaryIcon(R.drawable.ic_wk_capture);
            sessionCtaRow.setGhost("New chat", () -> {
                chatItems.clear();
                chatAdapter.notifyDataSetChanged();
                chatHeader.setTitle("AI Chat");
                chatHeader.setMeta(null);
                if (sessionListContainer != null) sessionListContainer.setVisibility(View.GONE);
                if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
                if (wkInputBar != null) wkInputBar.setVisibility(View.VISIBLE);
            });
            sessionCtaRow.setGhostIcon(R.drawable.ic_wk_plus);
        }
    }

    private void setupChatRecyclerView() {
        // Replace XML RecyclerView with one created using keyboard-themed context.
        // This makes parent.getContext() in ChatAdapter.onCreateViewHolder return a
        // context that can resolve productViewTitleColor, chatAiBubbleColor, etc.
        // Without this, layout inflation crashes on ?attr/productViewTitleColor.
        android.widget.FrameLayout container = (android.widget.FrameLayout) chatContainer;
        container.removeView(chatRecyclerView);

        Context chatContext = new ContextThemeWrapper(this, R.style.KeyboardTheme_LXX_Dark);
        chatRecyclerView = new RecyclerView(chatContext);
        chatRecyclerView.setId(R.id.fs_chat_recycler);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        chatRecyclerView.setLayoutParams(lp);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        chatRecyclerView.setPadding(pad, pad, pad, pad);
        chatRecyclerView.setClipToPadding(false);
        container.addView(chatRecyclerView, 0);

        chatLayoutManager = new LinearLayoutManager(this);
        chatLayoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(chatLayoutManager);

        chatAdapter = new ChatAdapter(chatItems, null, null, chatContext);
        chatRecyclerView.setAdapter(chatAdapter);
    }

    private void setupInputField() {
        wkInputBar.setCaptureEnabled(false); // No capture in fullscreen
        wkInputBar.setOnSendListener(text -> {
            if (text != null && !text.trim().isEmpty()) {
                sendMessage(text.trim());
                wkInputBar.clearText();
            }
        });
    }

    private void sendMessage(String text) {
        if (fsAiEngine == null) {
            fsAiEngine = new OverlayAiEngine(this);
        }

        List<ChatItem> historySnapshot = new ArrayList<>(chatItems);

        if (currentSessionId <= 0 && unifiedSessionManager != null) {
            String title = UnifiedChatSessionManager.buildTitle("fullscreen", text);
            unifiedSessionManager.createSession(
                UnifiedChatSessionManager.SOURCE_FULLSCREEN,
                UnifiedChatSessionManager.ICON_FULLSCREEN,
                title,
                sessionId -> {
                    currentSessionId = sessionId;
                    unifiedSessionManager.addMessage(sessionId, "user", text, "text");
                    Log.d(TAG, "Created fullscreen session: " + sessionId);
                }
            );
        } else if (currentSessionId > 0 && unifiedSessionManager != null) {
            unifiedSessionManager.addMessage(currentSessionId, "user", text, "text");
        }

        chatItems.add(new UserMessage(text));
        chatItems.add(Loading.INSTANCE);
        chatAdapter.notifyDataSetChanged();
        scrollToBottom();
        if (wkInputBar != null) wkInputBar.setEnabled(false);

        fsAiEngine.sendFollowUp(text, null, null, historySnapshot,
            new OverlayAiEngine.ResponseCallback() {
                @Override
                public void onResponse(String response) {
                    runOnUiThread(() -> {
                        removeTrailingLoading();
                        chatItems.add(new AiMessage(response, CtaType.REGENERATE_COPY_REPLY));
                        chatAdapter.notifyDataSetChanged();
                        scrollToBottom();
                        if (wkInputBar != null) wkInputBar.setEnabled(true);
                        if (currentSessionId > 0 && unifiedSessionManager != null) {
                            unifiedSessionManager.addMessage(currentSessionId, "assistant", response, "text");
                        }
                        Log.d(TAG, "AI response received: " + response.length() + " chars");
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        removeTrailingLoading();
                        chatItems.add(new ErrorMessage("Error: " + error, () -> sendMessage(text)));
                        chatAdapter.notifyDataSetChanged();
                        scrollToBottom();
                        if (wkInputBar != null) wkInputBar.setEnabled(true);
                        Log.e(TAG, "AI response error: " + error);
                    });
                }
            }
        );
        Log.d(TAG, "Sent message to API. length=" + text.length());
    }

    private void removeTrailingLoading() {
        int lastIdx = chatItems.size() - 1;
        if (lastIdx >= 0 && chatItems.get(lastIdx) instanceof Loading) {
            chatItems.remove(lastIdx);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fsAiEngine != null) {
            fsAiEngine.shutdown();
            fsAiEngine = null;
        }
    }

    private void loadActiveSession() {
        // Use current state from AiChatStateManager
        updateToolbar(null, null);
        scrollToBottom();
        Log.d(TAG, "Loaded active session: " + stateManager.getActiveSessionId());
    }

    /**
     * Load an overlay screenshot AI session passed via intent extras.
     * Called when expanding from the overlay chat panel to fullscreen.
     */
    private void loadOverlaySession(ArrayList<String> texts, ArrayList<String> types) {
        chatItems.clear();
        int count = Math.min(texts.size(), types.size());
        for (int i = 0; i < count; i++) {
            if ("user".equals(types.get(i))) {
                chatItems.add(new UserMessage(texts.get(i)));
            } else if ("ai".equals(types.get(i))) {
                chatItems.add(new AiMessage(texts.get(i), CtaType.REGENERATE_COPY_REPLY));
            }
        }
        chatAdapter.notifyDataSetChanged();
        chatHeader.setTitle("AI Chat");
        scrollToBottom();
        Log.d(TAG, "Loaded overlay session with " + chatItems.size() + " messages");
    }

    private void updateToolbar(String contactName, String appName) {
        if (contactName != null && !contactName.isEmpty()) {
            chatHeader.setTitle("Reply to " + contactName);
        } else {
            chatHeader.setTitle("AI Chat");
        }
    }

    private void toggleSessionList() {
        boolean showingList = sessionListContainer.getVisibility() == View.VISIBLE;
        if (showingList) {
            // Switching TO chat view
            sessionListContainer.setVisibility(View.GONE);
            chatContainer.setVisibility(View.VISIBLE);
            if (wkInputBar != null) wkInputBar.setVisibility(View.VISIBLE);
        } else {
            // Switching TO session list
            sessionListContainer.setVisibility(View.VISIBLE);
            chatContainer.setVisibility(View.GONE);
            if (wkInputBar != null) wkInputBar.setVisibility(View.GONE);
            loadSessionList();
        }
    }

    private void loadSessionList() {
        if (unifiedSessionManager == null) return;
        new Thread(() -> {
            List<ChatSession> sessions = unifiedSessionManager.getAllSessionsSync();
            List<SessionItem> items = new ArrayList<>();

            // Group by date
            String lastDateGroup = "";
            java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault());
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
            java.util.Calendar today = java.util.Calendar.getInstance();
            java.util.Calendar yesterday = java.util.Calendar.getInstance();
            yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1);

            for (ChatSession s : sessions) {
                long ts = s.updatedAt > 0 ? s.updatedAt : s.createdAt;
                java.util.Calendar sessionCal = java.util.Calendar.getInstance();
                sessionCal.setTimeInMillis(ts);

                String dateGroup;
                if (isSameDay(sessionCal, today)) dateGroup = "Today";
                else if (isSameDay(sessionCal, yesterday)) dateGroup = "Yesterday";
                else dateGroup = dayFormat.format(new java.util.Date(ts));

                if (!dateGroup.equals(lastDateGroup)) {
                    items.add(SessionItem.header(dateGroup));
                    lastDateGroup = dateGroup;
                }

                String sourceValue = s.source != null
                    ? s.source : UnifiedChatSessionManager.SOURCE_KEYBOARD;

                String time = timeFormat.format(new java.util.Date(ts));
                items.add(SessionItem.session("💬", sourceValue,
                    WkSessionDisplay.displayTitle(s.title),
                    WkSessionDisplay.preview(s.title, s.summary),
                    time, s.messageCount + " msgs", s.id == currentSessionId, s.id));
            }
            runOnUiThread(() -> {
                if (items.isEmpty()) {
                    showEmptySessionsState();
                } else {
                    hideEmptySessionsState();
                    if (sessionListAdapter == null) {
                        sessionListAdapter = new SessionListAdapter(items, sessionItem -> {
                            openSession(sessionItem.sessionId);
                        });
                        sessionRecyclerView.setLayoutManager(new LinearLayoutManager(AiChatActivity.this));
                        sessionRecyclerView.setAdapter(sessionListAdapter);
                    } else {
                        sessionListAdapter.updateItems(items);
                    }
                }
                updateToolbarForSessionList(sessions.size());
                Log.d(TAG, "Loaded " + sessions.size() + " sessions (+" + (items.size() - sessions.size()) + " headers)");
            });
        }).start();
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private void updateEmptyState() {
        if (chatItems.isEmpty()) {
            emptyStateView.bind("No chats yet", "Start a chat or share a screenshot.");
            emptyStateView.setVisibility(View.VISIBLE);
        } else {
            emptyStateView.setVisibility(View.GONE);
        }
    }

    private boolean isSameDay(java.util.Calendar a, java.util.Calendar b) {
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
            && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private void updateToolbarForSessionList(int count) {
        chatHeader.setTitle(count > 0 ? count + " conversations" : "AI Chat");
    }

    private void updateToolbarForSession(ChatSession session) {
        if (session == null) return;
        chatHeader.setTitle(WkSessionDisplay.displayTitle(session.title));
        Log.d(TAG, "Toolbar updated for session " + session.id);
    }

    private void showEmptySessionsState() {
        if (sessionRecyclerView != null) sessionRecyclerView.setVisibility(View.GONE);
        if (emptySessionsStateView != null) {
            emptySessionsStateView.bind("No sessions yet", "Start a conversation from keyboard or overlay");
            emptySessionsStateView.setVisibility(View.VISIBLE);
        }
    }

    private void hideEmptySessionsState() {
        if (sessionRecyclerView != null) sessionRecyclerView.setVisibility(View.VISIBLE);
        if (emptySessionsStateView != null) emptySessionsStateView.setVisibility(View.GONE);
    }

    private void openSession(long sessionId) {
        if (unifiedSessionManager == null) return;
        new Thread(() -> {
            ChatSession session = unifiedSessionManager.getSessionSync(sessionId);
            if (session == null) return;
            List<ChatMessage> messages = unifiedSessionManager.getMessagesSync(sessionId);
            List<ChatItem> restoredItems = restoreChatItemsFromRoom(messages);
            runOnUiThread(() -> {
                currentSessionId = sessionId;
                chatItems.clear();
                chatItems.addAll(restoredItems);
                if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
                updateToolbarForSession(session);
                // Ensure we're showing chat, not list
                if (sessionListContainer != null) sessionListContainer.setVisibility(View.GONE);
                if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
                if (wkInputBar != null) wkInputBar.setVisibility(View.VISIBLE);
                scrollToBottom();
                Log.d(TAG, "Opened session " + sessionId + ": " + messages.size() + " messages");
            });
        }).start();
    }

    private List<ChatItem> restoreChatItemsFromRoom(List<ChatMessage> messages) {
        List<ChatItem> restoredItems = new ArrayList<>();
        if (messages == null) return restoredItems;
        WittyKeysDatabase db = WittyKeysDatabase.getInstance(this);
        for (ChatMessage msg : messages) {
            if (msg == null || msg.content == null || msg.content.isEmpty()) continue;
            if ("nls_context".equals(msg.type)) continue;
            if ("user".equals(msg.role)) {
                restoredItems.add(new UserMessage(msg.content, msg.timestamp));
            } else if ("assistant".equals(msg.role)) {
                restoredItems.add(new AiMessage(msg.content, CtaType.REGENERATE_COPY_REPLY, msg.timestamp));
            } else if ("screenshot_analysis".equals(msg.type)) {
                if (msg.screenshotId != null && db != null) {
                    SessionScreenshot ss = db.screenshotDao().getById(msg.screenshotId);
                    if (ss != null && ss.filePath != null) {
                        restoredItems.add(new ScreenshotMessage(ss.filePath, null, ss.width, ss.height));
                    }
                }
                restoredItems.add(new AiMessage(msg.content, CtaType.REGENERATE_COPY_REPLY, msg.timestamp));
            }
        }
        return restoredItems;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.d(TAG, "Back pressed — returning to messaging app");
    }

    // ============================================================================================
    // DEBUG STATE HANDLING (Golden Pipeline)
    // ============================================================================================

    /**
     * Handle debug states for golden pipeline screenshots.
     * Populates chat with mock data matching the approved mockup states.
     */
    private void handleDebugState(String state) {
        Log.d(TAG, "Debug state: " + state);
        List<ChatItem> items = new ArrayList<>();
        boolean scrollToTop = state != null && state.startsWith("fv2_");

        String contactName = getIntent().getStringExtra("contact_name");
        String appName = getIntent().getStringExtra("app_name");

        switch (state) {
            case "fs_empty":
                // FS01: Empty session — show placeholder
                chatHeader.setTitle("AI Chat");
                updateEmptyState(); // Shows placeholder since no items
                return; // Skip setting items

            case "fs_chat":
                // FS02: Active conversation with Mom
                chatHeader.setTitle("Chat with " + contactName);
                items.add(new UserMessage("How should I reply to Mom's dinner invite?"));
                items.add(new AiMessage("**Mom is being warm and inviting** — she mentioned your favorite paneer. Best response: Match her warmth.\n\n*\"Haan mummy pakka aa raha hoon! Paneer ke liye toh kuch bhi \uD83D\uDE0B❤\uFE0F\"*", CtaType.REGENERATE_COPY_REPLY));
                items.add(new UserMessage("But what if she gets upset that I'm late?"));
                items.add(new AiMessage("Based on the context, Mom seems **caring, not strict**. The paneer mention is affection. If late:\n\n*\"Mummy thoda late ho jaega, but pakka aaunga. Save some paneer for me! \uD83D\uDE0A\"*", CtaType.REGENERATE_COPY_REPLY));
                break;

            case "fs_loading":
                // FS03: Typing indicator
                chatHeader.setTitle("Chat with " + contactName);
                items.add(new UserMessage("How should I respond to Mom's dinner invite?"));
                items.add(Loading.INSTANCE);
                break;

            case "fs_error":
                // FS04: Error card
                chatHeader.setTitle("Chat with " + contactName);
                items.add(new UserMessage("How should I respond to Mom's dinner invite?"));
                items.add(new ErrorMessage(
                    "Network timeout — Claude API did not respond within 30 seconds. Check your connection.",
                    () -> Log.d(TAG, "Retry tapped")));
                break;

            case "fs_screenshot":
                // FS05: Screenshot inline (280px thumbnail)
                chatHeader.setTitle("Chat with " + contactName);
                items.add(new UserMessage("Can you see what's on my screen?"));
                items.add(new ScreenshotMessage("", "WhatsApp chat showing a NullPointerException dialog in the background.", 1080, 2400));
                items.add(new AiMessage("**Screen Analysis:** I can see a NullPointerException at **MainActivity.java line 42**.\n\nThe issue is accessing a view before `setContentView()`. Move `findViewById()` after `setContentView()` to fix this.", CtaType.REGENERATE_COPY_REPLY));
                break;

            case "fs_nls_context":
                // FS06: NLS context banner
                chatHeader.setTitle("Chat with " + contactName);
                List<NlsBannerMessage.NlsEntry> nlsEntries = new ArrayList<>();
                nlsEntries.add(new NlsBannerMessage.NlsEntry("Mom", "Beta dinner pe aao kal, paneer tikka bana rahi hun"));
                nlsEntries.add(new NlsBannerMessage.NlsEntry("Mom", "Dad bhi aa rahe hain"));
                nlsEntries.add(new NlsBannerMessage.NlsEntry("You", "Ok mummy, aa raha hun"));
                items.add(new NlsBannerMessage("Mom", nlsEntries));
                items.add(new UserMessage("What did Mom say about dinner?"));
                items.add(new AiMessage("**Based on Mom's messages:** She invited you for dinner and is making paneer tikka. Dad is coming too. You already confirmed.\n\n**Follow up:** *\"Kya laun mummy? Dessert le aata hun \uD83C\uDF70\"*", CtaType.REGENERATE_COPY_REPLY));
                break;

            case "fs_session_list":
                // FS07: Session list view
                chatHeader.setTitle("Chat History");
                chatContainer.setVisibility(View.GONE);
                sessionListContainer.setVisibility(View.VISIBLE);
                populateDebugSessionList();
                return; // Skip setting chat items

            case "fs_session_resumed":
                // FS08: Session resumed with old messages at 70% opacity
                chatHeader.setTitle("Chat with " + contactName);
                items.add(new SessionBannerMessage(3));
                items.add(new UserMessage("He keeps asking about the party, what should I say?"));
                items.add(new AiMessage("Try: *\"Saturday 8pm pakka! Tu venue dekh le...\"*", CtaType.REPLY_COPY));
                // Fresh NLS context
                List<NlsBannerMessage.NlsEntry> resumeEntries = new ArrayList<>();
                resumeEntries.add(new NlsBannerMessage.NlsEntry("Arjun", "Bhai party kab hai? Sab log puch rahe hain \uD83C\uDF89"));
                items.add(new NlsBannerMessage("Arjun", resumeEntries));
                items.add(new UserMessage("He's asking again, should I change the plan?"));
                items.add(new AiMessage("**Arjun seems excited**, not pressuring. His friends are interested too.\n\n*\"Haan bhai Saturday 8pm confirm! Sab ko bata de \uD83C\uDF89\"*", CtaType.REGENERATE_COPY_REPLY));
                // Set restored item count for 70% opacity on first 3 items
                chatAdapter.setRestoredItemCount(3);
                break;

            case "fs_long_chat":
                // FS09: 12-message deep conversation
                chatHeader.setTitle("Chat with " + contactName);
                items.add(new UserMessage("I'm refactoring my Android app. Where should I start?"));
                items.add(new AiMessage("Start with the **data layer**. Separate your Room database access into a clean Repository pattern.", CtaType.REPLY_COPY));
                items.add(new UserMessage("What about the UI layer?"));
                items.add(new AiMessage("Use **MVVM** — create ViewModels for each screen. Move all business logic out of Activities/Fragments.", CtaType.REPLY_COPY));
                items.add(new UserMessage("Should I use LiveData or Flow?"));
                items.add(new AiMessage("**Flow** is the modern choice. Use `StateFlow` for UI state and `SharedFlow` for events.", CtaType.REPLY_COPY));
                items.add(new UserMessage("What about dependency injection?"));
                items.add(new AiMessage("**Hilt** is the standard. Annotate your Application class with `@HiltAndroidApp` and inject with `@Inject`.", CtaType.REPLY_COPY));
                items.add(new UserMessage("How do I handle navigation?"));
                items.add(new AiMessage("Use **Navigation Component** with a single Activity and Fragment destinations. Define your nav graph in XML.", CtaType.REPLY_COPY));
                items.add(new UserMessage("What about testing?"));
                items.add(new AiMessage("Layer your tests:\n- **Unit tests**: JUnit + Mockito for ViewModels\n- **Integration**: Room in-memory DB tests\n- **UI**: Espresso for critical flows", CtaType.REPLY_COPY));
                break;

            case "fs_capture_analyzing":
                // FS10: Screenshot analyzing handoff from compact
                chatHeader.setTitle("Chat with " + contactName);
                items.add(new UserMessage("Can you help me debug what's on screen?"));
                items.add(new AiMessage("Sure! Go back to the keyboard and tap the \uD83D\uDCF7 capture button — I'll analyze whatever is on your screen.", CtaType.REPLY_COPY));
                items.add(new AnalyzingMessage());
                break;

            case "fv2_initial_load":
                configureDebugChat("WittyKeys AI", null, false);
                setDebugChatTopPadding(320);
                items.add(Loading.INSTANCE);
                break;

            case "fv2_sessions_empty":
                chatHeader.setTitle("All sessions");
                chatHeader.setMeta(null);
                chatContainer.setVisibility(View.GONE);
                if (wkInputBar != null) wkInputBar.setVisibility(View.GONE);
                sessionListContainer.setVisibility(View.VISIBLE);
                if (emptySessionsStateView != null) {
                    emptySessionsStateView.bind("No conversations yet", "Create a new chat to get started.");
                    emptySessionsStateView.setVisibility(View.VISIBLE);
                }
                return;

            case "fv2_sessions_populated":
                chatHeader.setTitle("All sessions");
                chatHeader.setMeta(null);
                chatContainer.setVisibility(View.GONE);
                if (wkInputBar != null) wkInputBar.setVisibility(View.GONE);
                sessionListContainer.setVisibility(View.VISIBLE);
                if (emptySessionsStateView != null) emptySessionsStateView.setVisibility(View.GONE);
                populateDebugSessionList();
                return;

            case "fv2_chat_empty":
                configureDebugChat("Chat with Aanya", "1 msg", true);
                items.add(new UserMessage("Draft a calm reply"));
                break;

            case "fv2_chat_populated":
                configureDebugChat("Chat with Aanya", "6 msgs", true);
                items.add(new SessionBannerMessage(6));
                items.add(new AiMessage("· overlay\nDraft: \"Slides by 6pm — on it.\"", CtaType.NONE));
                items.add(new UserMessage("More urgent"));
                items.add(new AiMessage("✨ Smart reply\n\"Landing in your inbox by 5:30.\"", CtaType.NONE));
                items.add(new UserMessage("· keyboard\nTranslate to Hinglish"));
                items.add(new AiMessage("\"Inbox tak 5:30 ke andar pahunch jayega!\"", CtaType.NONE));
                break;

            case "fv2_chat_error":
                configureDebugChat("Chat with Aanya", null, true);
                items.add(new UserMessage("Rewrite once more"));
                items.add(new ErrorMessage(
                    "Could not reach AI.",
                    () -> Log.d(TAG, "[FV2] Retry tapped")));
                break;
        }

        chatItems.clear();
        chatItems.addAll(items);
        chatAdapter.notifyDataSetChanged();
        if (!items.isEmpty()) {
            chatRecyclerView.post(() -> chatRecyclerView.scrollToPosition(scrollToTop ? 0 : items.size() - 1));
        }
    }

    private void configureDebugChat(String title, String meta, boolean showInput) {
        if (chatLayoutManager != null) {
            chatLayoutManager.setStackFromEnd(false);
        }
        setDebugChatTopPadding(8);
        if (sessionListContainer != null) sessionListContainer.setVisibility(View.GONE);
        if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
        if (wkInputBar != null) {
            wkInputBar.setVisibility(showInput ? View.VISIBLE : View.GONE);
            wkInputBar.setHint("Follow up…");
        }
        chatHeader.setTitle(title);
        chatHeader.setMeta(meta);
    }

    private void setDebugChatTopPadding(int topDp) {
        if (chatRecyclerView == null) return;
        int side = dpToPx(8);
        chatRecyclerView.setPadding(side, dpToPx(topDp), side, side);
    }

    /**
     * Populate the session list with mock data for FS07 golden.
     */
    private void populateDebugSessionList() {
        List<SessionItem> sessions = new ArrayList<>();

        // Today group
        sessions.add(SessionItem.header("Today"));
        sessions.add(SessionItem.session("🔮", "overlay", "Chat with Aanya",
            "Slides by 6pm — on it.",
            "2m", "overlay", true));
        sessions.add(SessionItem.session("📸", "fullscreen", "Hindi — landlord email",
            "Polite plumbing request…",
            "1h", "fullscreen", false));

        // Yesterday group
        sessions.add(SessionItem.header("Yesterday"));
        sessions.add(SessionItem.session("⌨️", "keyboard", "Rahul — party plan",
            "Flirty short replies…",
            "yday", "keyboard", false));

        if (sessionRecyclerView != null) {
            LinearLayoutManager lm = new LinearLayoutManager(this);
            sessionRecyclerView.setLayoutManager(lm);
            sessionListAdapter = new SessionListAdapter(sessions);
            sessionRecyclerView.setAdapter(sessionListAdapter);
        }
        Log.d(TAG, "Debug session list populated with " + sessions.size() + " items");
    }

    // ============================================================================================
    // SESSION LIST MODEL + ADAPTER
    // ============================================================================================

    /** Simple model for session list items (headers + session cards). */
    static class SessionItem {
        enum Type { HEADER, SESSION }
        Type type;
        String headerText;
        String avatar;
        String sourceIcon;
        String contactName;
        String lastMessage;
        String time;
        String messageCount;
        boolean isActive;
        long sessionId;

        static SessionItem header(String text) {
            SessionItem item = new SessionItem();
            item.type = Type.HEADER;
            item.headerText = text;
            return item;
        }

        static SessionItem session(String avatar, String name, String preview,
                                   String time, String count, boolean active) {
            SessionItem item = new SessionItem();
            item.type = Type.SESSION;
            item.avatar = avatar;
            item.contactName = name;
            item.lastMessage = preview;
            item.time = time;
            item.messageCount = count;
            item.isActive = active;
            return item;
        }

        static SessionItem session(String avatar, String sourceIcon, String name,
                                   String preview, String time, String count, boolean active) {
            SessionItem item = session(avatar, name, preview, time, count, active);
            item.sourceIcon = sourceIcon;
            return item;
        }

        static SessionItem session(String avatar, String sourceIcon, String name,
                                   String preview, String time, String count, boolean active, long sessionId) {
            SessionItem item = session(avatar, sourceIcon, name, preview, time, count, active);
            item.sessionId = sessionId;
            return item;
        }
    }

    /** RecyclerView adapter for the session list with card layout. */
    interface OnSessionClickListener {
        void onSessionClick(SessionItem item);
    }

    static class SessionListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_SESSION = 1;
        private final List<SessionItem> items;
        private OnSessionClickListener clickListener;

        SessionListAdapter(List<SessionItem> items) {
            this.items = items;
        }

        SessionListAdapter(List<SessionItem> items, OnSessionClickListener listener) {
            this.items = items;
            this.clickListener = listener;
        }

        void setOnSessionClickListener(OnSessionClickListener listener) {
            this.clickListener = listener;
        }

        void updateItems(List<SessionItem> newItems) {
            this.items.clear();
            this.items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type == SessionItem.Type.HEADER ? TYPE_HEADER : TYPE_SESSION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                View v = inflater.inflate(R.layout.item_session_group_header, parent, false);
                return new HeaderVH(v);
            } else {
                WkSessionCard card = new WkSessionCard(parent.getContext());
                card.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new SessionVH(card);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SessionItem item = items.get(position);
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).bind(item);
            } else if (holder instanceof SessionVH) {
                ((SessionVH) holder).bind(item);
                holder.itemView.setOnClickListener(v -> {
                    if (clickListener != null && item.sessionId > 0) {
                        clickListener.onSessionClick(item);
                    }
                });
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class HeaderVH extends RecyclerView.ViewHolder {
            TextView text;
            HeaderVH(View v) {
                super(v);
                text = v.findViewById(R.id.session_group_header);
            }
            void bind(SessionItem item) {
                text.setText(item.headerText);
            }
        }

        static class SessionVH extends RecyclerView.ViewHolder {
            SessionVH(View v) { super(v); }
            void bind(SessionItem item) {
                WkSessionCard card = (WkSessionCard) itemView;
                Surface source = Surface.KEYBOARD;
                if (item.sourceIcon != null) {
                    String icon = item.sourceIcon.toLowerCase();
                    if (icon.contains("overlay") || "🔮".equals(item.sourceIcon)) source = Surface.OVERLAY;
                    else if (icon.contains("fullscreen") || "📸".equals(item.sourceIcon)) source = Surface.FULLSCREEN;
                }
                card.bind(item.contactName, item.lastMessage, item.time, source, item.isActive);
            }
        }
    }
}
