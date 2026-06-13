package project.witty.keys.app.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import project.witty.keys.R;
import project.witty.keys.ui.chat.WkSessionCard;
import project.witty.keys.ui.chat.WkEmptyState;
import project.witty.keys.ui.chat.WkInputBar;
import project.witty.keys.ui.chat.WkDualCtaRow;
import project.witty.keys.ui.chat.Surface;
import project.witty.keys.ui.chat.WkSessionDisplay;
import project.witty.keys.ui.chat.WkSessionListFormat;
import project.witty.keys.app.context.UnifiedChatSessionManager;
import project.witty.keys.app.database.ChatSession;
import project.witty.keys.app.database.SessionScreenshot;
import project.witty.keys.app.database.WittyKeysDatabase;
import project.witty.keys.keyboard.AiChat.AiChatActivity;
import project.witty.keys.app.database.ChatMessage;
import project.witty.keys.keyboard.AiChat.AiMessage;
import project.witty.keys.keyboard.AiChat.ChatAdapter;
import project.witty.keys.keyboard.AiChat.ChatItem;
import project.witty.keys.keyboard.AiChat.CtaType;
import project.witty.keys.keyboard.AiChat.ErrorMessage;
import project.witty.keys.keyboard.AiChat.Loading;
import project.witty.keys.keyboard.AiChat.ScreenshotMessage;
import project.witty.keys.keyboard.AiChat.UserMessage;

/**
 * OverlayChatPanel — Build 7.1 MVP (Redesigned to match HTML mockup)
 *
 * 3-mode popup for screenshot AI:
 * - HISTORY: "New Screenshot" button + list of previous sessions (thumbnails, titles, preview)
 * - LOADING: Screenshot as user message bubble (right) + analyzing spinner + shimmer bars
 * - CHAT: Full persistent chat with follow-ups, additional screenshots, expand to fullscreen
 */
public class OverlayChatPanel {

    private static final String TAG = "WK_OVERLAY_CHAT";
    private static final int SESSION_PAGE_SIZE = 20;

    private enum Mode { HISTORY, LOADING, CHAT }

    private final Context context;
    private final Context themedContext;
    private final WittyKeysOverlayService overlayService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Session management
    private final LinkedList<AiChatSession> sessions = new LinkedList<>();
    private AiChatSession currentSession;
    private UnifiedChatSessionManager unifiedSessionManager;
    private long currentUnifiedSessionId = -1;
    private int sessionsOffset = 0;
    private boolean sessionsHasMore = false;
    private boolean debugUseSeededSessions = false;

    // Views
    private View popupView;
    private FrameLayout contentContainer;
    private WkInputBar wkInputBar;
    private TextView titleView;
    private TextView subtitleView;
    private TextView expandView;

    // Chat mode
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;

    private OverlayAiEngine aiEngine;
    private boolean isShowing = false;
    private Mode currentMode = Mode.HISTORY;
    private boolean roomSessionCreationPending = false;
    private final List<RoomSessionCallback> pendingRoomSessionCallbacks = new ArrayList<>();

    private interface RoomSessionCallback {
        void onReady(long sessionId);
    }

    public OverlayChatPanel(Context context, WittyKeysOverlayService overlayService) {
        this.context = context.getApplicationContext();
        this.themedContext = new ContextThemeWrapper(context, R.style.KeyboardTheme_LXX_Dark);
        this.overlayService = overlayService;
        this.aiEngine = new OverlayAiEngine(context.getApplicationContext());
        this.unifiedSessionManager = UnifiedChatSessionManager.getInstance(context);
    }

    // ─── Show/Hide ───

    public void show(String imagePath, String analysis) {
        Log.d(TAG, "Overlay chat requested; showing=" + isShowing);
        // Safety: if a previous popup is still attached, force-dismiss it first
        if (isShowing) {
            hide();
        }

        // Reset stale state from previous session
        currentSession = null;
        currentUnifiedSessionId = -1;
        popupView = null;  // Force fresh popup inflation

        currentSession = createSession();
        currentSession.addScreenshot(imagePath);
        currentSession.title = "AI Chat";

        if (analysis != null && !analysis.isEmpty()) {
            currentSession.messages.add(new UserMessage("📸 Screenshot captured"));
            AiMessage aiMsg = new AiMessage(analysis, CtaType.REGENERATE_COPY_REPLY);
            currentSession.messages.add(aiMsg);
            currentSession.messageCount = 2;
            currentSession.updatePreview(analysis);
            persistScreenshotAnalysis(analysis);

            showPopupInMode(Mode.CHAT, imagePath);
        } else {
            showPopupInMode(Mode.LOADING, imagePath);
        }
    }

    public void showAnalyzing(String imagePath) {
        // Safety: dismiss stale popup first
        if (isShowing) {
            hide();
        }
        if (currentSession == null) {
            currentSession = createSession();
        }
        currentSession.addScreenshot(imagePath);
        currentSession.title = "AI Chat";
        currentSession.messages.add(new UserMessage("📸 Screenshot captured"));

        showPopupInMode(Mode.LOADING, imagePath);
    }

    public void showHistory() {
        currentSession = null;
        sessionsOffset = 0;
        sessionsHasMore = false;
        showPopupInMode(Mode.HISTORY, null);
    }

    public void hide() {
        Log.d(TAG, "Overlay chat hide requested; showing=" + isShowing);
        if (!isShowing) return;
        mainHandler.removeCallbacksAndMessages(null);
        overlayService.hidePopupFromPanel();
        popupView = null;
        isShowing = false;
        currentMode = Mode.HISTORY;
        // Clean up session state to prevent stale references on reopen
        currentSession = null;
        currentUnifiedSessionId = -1;
        Log.d(TAG, "Chat panel hidden — state cleaned up");
    }

    public boolean isShowing() {
        return isShowing;
    }

    void onPopupDismissedByService(String type) {
        Log.d(TAG, "Overlay chat externally dismissed. type=" + type + " showing=" + isShowing);
        mainHandler.removeCallbacksAndMessages(null);
        popupView = null;
        isShowing = false;
        currentMode = Mode.HISTORY;
        currentSession = null;
        currentUnifiedSessionId = -1;
    }

    // ─── Mode Switching ───

    private void showPopupInMode(Mode mode, String imagePath) {
        Log.d(TAG, "Rendering popup mode=" + mode + " showing=" + isShowing);
        this.currentMode = mode;

        LayoutInflater inflater = LayoutInflater.from(themedContext);
        popupView = inflater.inflate(R.layout.overlay_screenshot_popup, null);
        initBaseViews();

        switch (mode) {
            case HISTORY:
                showHistoryContent();
                break;
            case LOADING:
                showLoadingContent(imagePath);
                break;
            case CHAT:
                showChatContent();
                break;
        }

        if (isShowing) {
            overlayService.hidePopupFromPanel();
            isShowing = false;
        }
        try {
            overlayService.showPopup(popupView, "screenshot-" + mode.name().toLowerCase(), 340, 480);
            isShowing = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to show popup: " + e.getMessage(), e);
            isShowing = false;
            popupView = null;
        }
    }

    private void initBaseViews() {
        contentContainer = popupView.findViewById(R.id.overlay_screenshot_content);
        wkInputBar = popupView.findViewById(R.id.overlay_input_bar);
        titleView = popupView.findViewById(R.id.overlay_screenshot_title);
        subtitleView = popupView.findViewById(R.id.overlay_screenshot_subtitle);
        expandView = popupView.findViewById(R.id.overlay_screenshot_expand);

        if (expandView != null) {
            expandView.setVisibility(View.GONE);
            expandView.setOnClickListener(null);
        }

        if (wkInputBar != null) {
            wkInputBar.setUseSystemIme(true);
            wkInputBar.setOverlayDarkStyle();
            wkInputBar.setHint("Ask anything...");
            wkInputBar.setCaptureEnabled(true);
            wkInputBar.setOnSendListener(text -> {
                if (text != null && !text.trim().isEmpty()) {
                    sendMessageWithText(text.trim());
                    wkInputBar.clearText();
                }
            });
            wkInputBar.setOnCaptureListener(() -> captureNewScreenshot());
        }
    }

    // ─── HISTORY Mode ───

    /**
     * Async version: runs Room query on background thread, posts callback to main thread.
     */
    private void syncSessionsFromRoomAsync(Runnable onComplete) {
        syncSessionsFromRoomAsync(false, onComplete);
    }

    private void syncSessionsFromRoomAsync(boolean append, Runnable onComplete) {
        if (debugUseSeededSessions) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (unifiedSessionManager == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        final int offset = append ? sessionsOffset : 0;
        new Thread(() -> {
            List<ChatSession> roomSessions = unifiedSessionManager.getSessionsPageSync(
                SESSION_PAGE_SIZE, offset);
            List<AiChatSession> synced = new java.util.ArrayList<>();
            for (ChatSession rs : roomSessions) {
                AiChatSession session = new AiChatSession();
                session.title = rs.title;
                session.roomSessionId = rs.id;
                session.lastUpdated = rs.updatedAt > 0 ? rs.updatedAt : rs.createdAt;
                session.messageCount = rs.messageCount;
                session.lastPreview = rs.summary;
                session.source = rs.source != null
                    ? rs.source : UnifiedChatSessionManager.SOURCE_KEYBOARD;
                synced.add(session);
            }
            mainHandler.post(() -> {
                if (!append) sessions.clear();
                sessions.addAll(synced);
                sessionsOffset = append ? sessionsOffset + synced.size() : synced.size();
                sessionsHasMore = WkSessionListFormat.hasMorePage(synced.size(), SESSION_PAGE_SIZE);
                Log.d(TAG, "Synced " + synced.size() + " sessions from Room DB (offset="
                    + offset + ", total=" + sessions.size() + ", hasMore=" + sessionsHasMore + ")");
                if (onComplete != null) onComplete.run();
            });
        }).start();
    }

    private void showHistoryContent() {
        if (contentContainer == null) return;
        syncSessionsFromRoomAsync(() -> {
            showHistoryContentUI();
        });
    }

    /** UI portion of showHistoryContent — called on main thread after async sync. */
    private void showHistoryContentUI() {
        if (contentContainer == null) return;
        contentContainer.removeAllViews();

        // Set header text
        if (titleView != null) titleView.setText("WittyKeys AI");
        if (subtitleView != null) subtitleView.setText("Your conversations");

        // Hide input bar in history mode
        if (wkInputBar != null) wkInputBar.setVisibility(View.GONE);
        if (expandView != null) expandView.setVisibility(View.GONE);

        // Build scrollable list
        android.widget.ScrollView scroll = new android.widget.ScrollView(themedContext);
        LinearLayout list = new LinearLayout(themedContext);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, 0);

        View ctaRow = createCtaRow();
        LinearLayout.LayoutParams ctaLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctaLp.setMargins(dpToPx(8), 0, dpToPx(8), dpToPx(10));
        list.addView(ctaRow, ctaLp);

        if (sessions.isEmpty()) {
            // Empty state
            list.addView(createOverlayEmptyState());
        } else {
            // Group sessions by time
            String currentGroup = null;
            for (int i = 0; i < sessions.size(); i++) {
                AiChatSession session = sessions.get(i);
                String group = getSessionTimeGroup(session.lastUpdated);
                if (!group.equals(currentGroup)) {
                    currentGroup = group;
                    list.addView(createSectionLabel(group));
                }
                list.addView(createOverlaySessionCard(session, i == 0));
            }
            if (sessionsHasMore) {
                list.addView(createLoadMoreFooter());
            }
        }

        scroll.addView(list);
        contentContainer.addView(scroll, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        Log.d(TAG, "History content: " + sessions.size() + " sessions");
    }

    private String getSessionTimeGroup(long timestamp) {
        return WkSessionListFormat.dateBucket(timestamp, System.currentTimeMillis());
    }

    private View createSectionLabel(String text) {
        TextView label = new TextView(themedContext);
        label.setText(text);
        label.setTextSize(9);
        label.setTypeface(null, Typeface.BOLD);
        label.setAllCaps(true);
        label.setLetterSpacing(0.1f);
        label.setTextColor(Color.parseColor("#606068"));
        int pad = dpToPx(4);
        label.setPadding(dpToPx(8), dpToPx(8), pad, dpToPx(6));
        return label;
    }

    private View createLoadMoreFooter() {
        TextView btn = new TextView(themedContext);
        btn.setText("Load more");
        btn.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_accent));
        btn.setTextSize(13);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dpToPx(14), 0, dpToPx(14));
        btn.setOnClickListener(v -> syncSessionsFromRoomAsync(true, this::showHistoryContentUI));
        return btn;
    }

    /** CTA row — aligned with the keyboard AI sessions list. */
    private View createCtaRow() {
        WkDualCtaRow cta = new WkDualCtaRow(themedContext);
        cta.setPrimary("Screenshot", () -> captureNewScreenshot());
        cta.setPrimaryIcon(R.drawable.ic_wk_capture);
        cta.setGhost("New chat", () -> {
            startNewChat();
        });
        cta.setGhostIcon(R.drawable.ic_wk_plus);
        return cta;
    }

    private void startNewChat() {
        currentSession = new AiChatSession();
        currentSession.title = "New Chat";
        currentSession.source = UnifiedChatSessionManager.SOURCE_OVERLAY;
        currentSession.messages = new LinkedList<>();
        currentUnifiedSessionId = -1;
        showPopupInMode(Mode.CHAT, null);
    }

    /** Session card — DS WkSessionCard wrapper */
    private View createOverlaySessionCard(AiChatSession session, boolean isActive) {
        WkSessionCard card = new WkSessionCard(themedContext);
        String title = WkSessionDisplay.displayTitle(session.title);
        String preview = WkSessionDisplay.preview(session.title, session.lastPreview);
        String time = formatOverlayRelativeTime(session.lastUpdated);
        Surface source = WkSessionDisplay.surfaceFromSource(session.source);

        card.bind(title, preview, time, source, false);
        card.setOnDeleteClickListener(v -> deleteOverlaySession(session));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dpToPx(8), 0, dpToPx(8), dpToPx(8));
        card.setLayoutParams(cardLp);

        // Preserve existing click handler — loads Room messages and opens chat
        card.setOnClickListener(v -> {
            Log.d(TAG, "Session card clicked. roomSessionId=" + session.roomSessionId
                + " showing=" + isShowing);
            // Load messages from Room DB for cross-surface sharing
            if (unifiedSessionManager != null && session.roomSessionId > 0) {
                new Thread(() -> {
                    List<ChatMessage> messages = unifiedSessionManager.getMessagesSync(session.roomSessionId);
                    List<ChatItem> restoredItems = restoreChatItemsFromRoom(messages);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        // Guard: if panel was hidden while loading, abort
                        if (!isShowing) {
                            Log.w(TAG, "Panel hidden during Room message load — aborting session open");
                            return;
                        }
                        currentSession = session;
                        session.messages.clear();
                        session.messages.addAll(restoredItems);
                        session.messageCount = session.messages.size();
                        currentUnifiedSessionId = session.roomSessionId;
                        showPopupInMode(Mode.CHAT, null);
                    });
                }).start();
            } else {
                currentSession = session;
                showPopupInMode(Mode.CHAT, null);
            }
        });

        return card;
    }

    private void deleteOverlaySession(AiChatSession session) {
        if (session == null) return;
        long roomSessionId = session.roomSessionId;
        sessions.remove(session);
        if (currentSession == session || currentUnifiedSessionId == roomSessionId) {
            currentSession = null;
            currentUnifiedSessionId = -1;
        }

        Runnable refresh = () -> syncSessionsFromRoomAsync(false, () -> {
            if (currentMode == Mode.HISTORY && popupView != null) {
                showHistoryContentUI();
            }
        });

        if (roomSessionId > 0 && unifiedSessionManager != null) {
            unifiedSessionManager.deleteSession(roomSessionId, refresh);
        } else {
            refresh.run();
        }
    }

    /** Empty state — DS WkEmptyState component */
    private View createOverlayEmptyState() {
        WkEmptyState empty = new WkEmptyState(themedContext);
        empty.bind("No sessions yet", "Start a chat or share a screenshot.");
        return empty;
    }

    private String formatOverlayRelativeTime(long timestamp) {
        if (timestamp <= 0) return "";
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / 60000;
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days == 1) return "Yesterday";
        return days + "d ago";
    }

    // ─── LOADING Mode ───

    private void showLoadingContent(String imagePath) {
        if (contentContainer == null) return;
        contentContainer.removeAllViews();

        if (titleView != null) titleView.setText("AI Chat");
        if (subtitleView != null) subtitleView.setText("Analyzing...");
        if (wkInputBar != null) wkInputBar.setVisibility(View.GONE);
        if (expandView != null) expandView.setVisibility(View.GONE);

        LinearLayout loadingLayout = new LinearLayout(themedContext);
        loadingLayout.setOrientation(LinearLayout.VERTICAL);
        loadingLayout.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        // Screenshot thumbnail (right-aligned like user message)
        if (imagePath != null) {
            FrameLayout thumbWrapper = new FrameLayout(themedContext);

            ImageView thumb = new ImageView(themedContext);
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                Bitmap bmp = BitmapFactory.decodeFile(imagePath, opts);
                if (bmp != null) {
                    thumb.setImageBitmap(bmp);
                    thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load thumbnail: " + e.getMessage());
            }

            // Rounded corners with sent-message style radius
            GradientDrawable thumbBg = new GradientDrawable();
            thumbBg.setCornerRadii(new float[]{
                dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14),
                dpToPx(14), dpToPx(14), dpToPx(4), dpToPx(4)
            });
            thumbBg.setColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_surface3));
            thumb.setBackground(thumbBg);
            thumb.setClipToOutline(true);

            // Border
            GradientDrawable borderBg = new GradientDrawable();
            borderBg.setCornerRadii(new float[]{
                dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14),
                dpToPx(14), dpToPx(14), dpToPx(4), dpToPx(4)
            });
            borderBg.setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.wk_overlay_glass));
            borderBg.setColor(Color.TRANSPARENT);

            FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(dpToPx(180), dpToPx(100));
            thumbLp.gravity = Gravity.END;
            thumbWrapper.addView(thumb, thumbLp);

            LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            loadingLayout.addView(thumbWrapper, wrapperLp);
        }

        // Analyzing indicator (centered)
        LinearLayout analyzeContainer = new LinearLayout(themedContext);
        analyzeContainer.setOrientation(LinearLayout.VERTICAL);
        analyzeContainer.setGravity(Gravity.CENTER);
        analyzeContainer.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(16));

        // Spinner emoji
        TextView spinnerEmoji = new TextView(themedContext);
        spinnerEmoji.setText("🔍");
        spinnerEmoji.setTextSize(24);
        spinnerEmoji.setGravity(Gravity.CENTER);
        analyzeContainer.addView(spinnerEmoji);

        // "AI is analyzing..." text
        TextView analyzingText = new TextView(themedContext);
        analyzingText.setText("AI is analyzing...");
        analyzingText.setTextSize(12);
        analyzingText.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_text2));
        analyzingText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.topMargin = dpToPx(8);
        analyzeContainer.addView(analyzingText, textLp);

        // Sub text
        TextView subText = new TextView(themedContext);
        subText.setText("This may take a few seconds");
        subText.setTextSize(10);
        subText.setTextColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_text3));
        subText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dpToPx(4);
        analyzeContainer.addView(subText, subLp);

        // Shimmer progress bars
        LinearLayout shimmerRow = new LinearLayout(themedContext);
        shimmerRow.setOrientation(LinearLayout.HORIZONTAL);
        shimmerRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams shimmerRowLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shimmerRowLp.topMargin = dpToPx(12);

        for (int i = 0; i < 3; i++) {
            View bar = new View(themedContext);
            GradientDrawable barBg = new GradientDrawable();
            barBg.setCornerRadius(dpToPx(2));
            if (i == 0) {
                barBg.setColors(new int[]{
                    ContextCompat.getColor(context, R.color.wk_overlay_dark_accent),
                    ContextCompat.getColor(context, R.color.wk_overlay_dark_purple)
                });
                barBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                barBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            } else {
                barBg.setColor(ContextCompat.getColor(context, R.color.wk_overlay_dark_surface3));
            }
            bar.setBackground(barBg);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(4));
            if (i > 0) barLp.setMarginStart(dpToPx(4));
            shimmerRow.addView(bar, barLp);
        }

        analyzeContainer.addView(shimmerRow, shimmerRowLp);

        loadingLayout.addView(analyzeContainer);

        contentContainer.addView(loadingLayout, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ─── CHAT Mode ───

    private void showChatContent() {
        if (contentContainer == null || currentSession == null) return;
        contentContainer.removeAllViews();

        if (titleView != null) titleView.setText(WkSessionDisplay.displayTitle(currentSession.title));
        if (subtitleView != null) subtitleView.setText(currentSession.messageCount + " messages");
        if (wkInputBar != null) wkInputBar.setVisibility(View.VISIBLE);
        if (expandView != null) expandView.setVisibility(View.GONE);

        chatRecyclerView = new RecyclerView(themedContext);
        LinearLayoutManager lm = new LinearLayoutManager(themedContext);
        lm.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(lm);
        chatRecyclerView.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        chatRecyclerView.setClipToPadding(false);

        chatAdapter = new ChatAdapter(currentSession.messages, null, null, themedContext);
        chatAdapter.setCompactMode(true);
        chatRecyclerView.setAdapter(chatAdapter);

        contentContainer.addView(chatRecyclerView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (!currentSession.messages.isEmpty()) {
            chatRecyclerView.scrollToPosition(currentSession.messages.size() - 1);
        }
    }

    // ─── Analysis Callbacks ───

    public void onAnalysisComplete(String analysis) {
        mainHandler.post(() -> {
            if (currentSession == null) return;

            AiMessage aiMsg = new AiMessage(analysis, CtaType.REGENERATE_COPY_REPLY);
            currentSession.messages.add(aiMsg);
            currentSession.messageCount = currentSession.messages.size();
            currentSession.updatePreview(analysis);

            if (currentSession.title == null || "AI Chat".equals(currentSession.title)) {
                // Strip markdown heading prefixes from auto-title
                String cleanAnalysis = analysis.replaceFirst("^#+\\s*", "").trim();
                String autoTitle = cleanAnalysis.length() > 40
                    ? cleanAnalysis.substring(0, 37) + "..." : cleanAnalysis;
                currentSession.title = autoTitle;
            }
            persistScreenshotAnalysis(analysis);

            showPopupInMode(Mode.CHAT, null);
        });
    }

    public void onAnalysisError(String error) {
        mainHandler.post(() -> {
            if (currentSession == null) return;

            ErrorMessage errMsg = new ErrorMessage("Analysis failed: " + error, () -> {});
            currentSession.messages.add(errMsg);
            currentSession.messageCount = currentSession.messages.size();

            showPopupInMode(Mode.CHAT, null);
        });
    }

    // ─── Chat Input ───

    private void sendMessage() {
        if (wkInputBar == null) return;
        String text = wkInputBar.getText();
        if (text == null || text.trim().isEmpty()) return;
        sendMessageWithText(text.trim());
        wkInputBar.clearText();
    }

    private void sendMessageWithText(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (currentSession == null) return;

        UserMessage userMsg = new UserMessage(text);
        currentSession.messages.add(userMsg);
        currentSession.messages.add(Loading.INSTANCE);
        currentSession.messageCount = currentSession.messages.size();

        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
            chatRecyclerView.scrollToPosition(currentSession.messages.size() - 1);
        }

        String imagePath = currentSession.screenshotPaths.isEmpty()
            ? null : currentSession.screenshotPaths.get(0);
        String initialAnalysis = null;

        for (ChatItem item : currentSession.messages) {
            if (item instanceof AiMessage) {
                initialAnalysis = ((AiMessage) item).getMarkdownText();
                break;
            }
        }

        List<ChatItem> historySnapshot = new ArrayList<>(currentSession.messages);
        final String finalImagePath = imagePath;
        final String finalInitialAnalysis = initialAnalysis;

        ensureRoomSession(text, sessionId -> {
            if (sessionId > 0 && unifiedSessionManager != null) {
                unifiedSessionManager.addMessage(sessionId, "user", text, "text");
            }
            mainHandler.post(() -> sendFollowUpToAi(text, finalImagePath, finalInitialAnalysis, historySnapshot));
        });
    }

    private void sendFollowUpToAi(String text, String imagePath, String initialAnalysis,
                                  List<ChatItem> historySnapshot) {
        if (currentSession == null) return;
        aiEngine.sendFollowUp(text, imagePath, initialAnalysis, historySnapshot,
            new OverlayAiEngine.ResponseCallback() {
                @Override
                public void onResponse(String response) {
                    mainHandler.post(() -> {
                        if (currentSession == null) return;
                        removeLastLoading();
                        AiMessage aiMsg = new AiMessage(response, CtaType.REGENERATE_COPY_REPLY);
                        currentSession.messages.add(aiMsg);
                        currentSession.messageCount = currentSession.messages.size();
                        currentSession.updatePreview(response);

                        // Persist AI response to unified Room DB
                        if (currentUnifiedSessionId > 0) {
                            unifiedSessionManager.addMessage(currentUnifiedSessionId, "assistant", response, "text");
                        }

                        if (chatAdapter != null) {
                            chatAdapter.notifyDataSetChanged();
                            chatRecyclerView.scrollToPosition(currentSession.messages.size() - 1);
                        }
                        if (subtitleView != null) {
                            subtitleView.setText(currentSession.messageCount + " messages");
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        if (currentSession == null) return;
                        removeLastLoading();
                        ErrorMessage errMsg = new ErrorMessage("Error: " + error, () -> {});
                        currentSession.messages.add(errMsg);
                        currentSession.messageCount = currentSession.messages.size();

                        if (chatAdapter != null) {
                            chatAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        );
    }

    private void removeLastLoading() {
        if (currentSession == null || currentSession.messages.isEmpty()) return;
        int last = currentSession.messages.size() - 1;
        if (currentSession.messages.get(last) instanceof Loading) {
            currentSession.messages.remove(last);
        }
    }

    // ─── Session Management ───

    private AiChatSession createSession() {
        AiChatSession session = new AiChatSession();
        sessions.addFirst(session);
        while (sessions.size() > SESSION_PAGE_SIZE) {
            sessions.removeLast();
        }
        return session;
    }

    private void ensureRoomSession(String titleContext, RoomSessionCallback callback) {
        if (unifiedSessionManager == null) {
            if (callback != null) callback.onReady(-1);
            return;
        }
        synchronized (this) {
            if (currentUnifiedSessionId > 0) {
                if (callback != null) callback.onReady(currentUnifiedSessionId);
                return;
            }
            if (currentSession != null && currentSession.roomSessionId > 0) {
                currentUnifiedSessionId = currentSession.roomSessionId;
                if (callback != null) callback.onReady(currentUnifiedSessionId);
                return;
            }
            if (roomSessionCreationPending) {
                if (callback != null) pendingRoomSessionCallbacks.add(callback);
                return;
            }
            roomSessionCreationPending = true;
            if (callback != null) pendingRoomSessionCallbacks.add(callback);
        }

        String title = UnifiedChatSessionManager.buildTitle(
            UnifiedChatSessionManager.SOURCE_OVERLAY, titleContext);
        unifiedSessionManager.createSession(
            UnifiedChatSessionManager.SOURCE_OVERLAY,
            UnifiedChatSessionManager.ICON_OVERLAY,
            title,
            id -> {
                currentUnifiedSessionId = id;
                if (currentSession != null) {
                    currentSession.roomSessionId = id;
                    currentSession.source = UnifiedChatSessionManager.SOURCE_OVERLAY;
                    if (currentSession.title == null
                        || "AI Chat".equals(currentSession.title)
                        || "New Chat".equals(currentSession.title)) {
                        currentSession.title = title;
                    }
                }
                Log.d(TAG, "Created overlay session " + id);
                List<RoomSessionCallback> callbacks;
                synchronized (OverlayChatPanel.this) {
                    roomSessionCreationPending = false;
                    callbacks = new ArrayList<>(pendingRoomSessionCallbacks);
                    pendingRoomSessionCallbacks.clear();
                }
                for (RoomSessionCallback pendingCallback : callbacks) {
                    pendingCallback.onReady(id);
                }
            });
    }

    private void persistScreenshotAnalysis(String analysis) {
        if (analysis == null || analysis.trim().isEmpty() || currentSession == null) return;
        String imagePath = currentSession.screenshotPaths.isEmpty()
            ? null : currentSession.screenshotPaths.get(0);
        ensureRoomSession("Screenshot captured", sessionId -> {
            if (sessionId <= 0 || unifiedSessionManager == null) return;
            if (imagePath != null && !imagePath.isEmpty()) {
                unifiedSessionManager.addMessageWithScreenshot(
                    sessionId, "system", analysis, "screenshot_analysis", imagePath);
            } else {
                unifiedSessionManager.addMessage(sessionId, "assistant", analysis, "text");
            }
        });
    }

    private List<ChatItem> restoreChatItemsFromRoom(List<ChatMessage> messages) {
        List<ChatItem> restoredItems = new ArrayList<>();
        if (messages == null) return restoredItems;
        WittyKeysDatabase db = WittyKeysDatabase.getInstance(context);
        for (ChatMessage msg : messages) {
            if (msg == null || msg.content == null || msg.content.isEmpty()) continue;
            if ("nls_context".equals(msg.type)) continue;
            if ("user".equals(msg.role)) {
                restoredItems.add(new UserMessage(msg.content, msg.timestamp));
            } else if ("assistant".equals(msg.role)) {
                restoredItems.add(new AiMessage(msg.content, CtaType.REPLY_COPY, msg.timestamp));
            } else if ("screenshot_analysis".equals(msg.type)) {
                if (msg.screenshotId != null && db != null) {
                    SessionScreenshot ss = db.screenshotDao().getById(msg.screenshotId);
                    if (ss != null && ss.filePath != null) {
                        restoredItems.add(new ScreenshotMessage(ss.filePath, null, ss.width, ss.height));
                    }
                }
                restoredItems.add(new AiMessage(msg.content, CtaType.REPLY_COPY, msg.timestamp));
            }
        }
        return restoredItems;
    }

    private void resumeSession(AiChatSession session) {
        currentSession = session;
        showPopupInMode(Mode.CHAT, null);
    }

    // ─── Actions ───

    private void expandToFullscreen() {
        try {
            Intent intent = new Intent(context, AiChatActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Pass unified session ID for Room DB lookup
            if (currentUnifiedSessionId > 0) {
                intent.putExtra("session_id", currentUnifiedSessionId);
                intent.putExtra("source", "overlay");
            }

            if (currentSession != null) {
                if (!currentSession.screenshotPaths.isEmpty()) {
                    intent.putExtra("overlay_screenshot_path",
                        currentSession.screenshotPaths.get(0));
                }

                ArrayList<String> msgTexts = new ArrayList<>();
                ArrayList<String> msgTypes = new ArrayList<>();
                for (ChatItem item : currentSession.messages) {
                    if (item instanceof UserMessage) {
                        msgTexts.add(((UserMessage) item).getText());
                        msgTypes.add("user");
                    } else if (item instanceof AiMessage) {
                        msgTexts.add(((AiMessage) item).getMarkdownText());
                        msgTypes.add("ai");
                    }
                }
                intent.putStringArrayListExtra("overlay_msg_texts", msgTexts);
                intent.putStringArrayListExtra("overlay_msg_types", msgTypes);
            }

            context.startActivity(intent);
            hide();
        } catch (Exception e) {
            Log.e(TAG, "Failed to expand to fullscreen: " + e.getMessage());
        }
    }

    public boolean hasHistory() {
        return !sessions.isEmpty();
    }

    /** Debug-only: inject a named overlay UI state for golden screenshot testing. */
    public void setDebugState(String state) {
        if (state == null) return;
        debugUseSeededSessions = false;
        switch (state) {
            case "popup_empty":
            case "popup_history_empty":
                sessions.clear();
                sessionsOffset = 0;
                sessionsHasMore = false;
                debugUseSeededSessions = true;
                currentSession = null;
                currentUnifiedSessionId = -1;
                showPopupInMode(Mode.HISTORY, null);
                break;
            case "popup_populated":
            case "popup_history_populated":
                seedDebugSessions();
                debugUseSeededSessions = true;
                currentSession = null;
                currentUnifiedSessionId = -1;
                showPopupInMode(Mode.HISTORY, null);
                break;
            case "popup_loading":
                currentSession = createDebugSession("Screenshot analysis", 0);
                showPopupInMode(Mode.LOADING, null);
                break;
            case "popup_chat_empty":
                currentSession = createDebugSession("New chat", 0);
                showPopupInMode(Mode.CHAT, null);
                break;
            case "popup_chat_loading":
                currentSession = createDebugSession("Reply follow-up", 0);
                currentSession.messages.add(new UserMessage("Can you help me reply to this?"));
                currentSession.messages.add(Loading.INSTANCE);
                currentSession.messageCount = currentSession.messages.size();
                showPopupInMode(Mode.CHAT, null);
                break;
            case "popup_chat_populated":
                currentSession = createDebugSession("Delivery update", 0);
                currentSession.messages.add(new UserMessage("What should I say here?"));
                currentSession.messages.add(new AiMessage(
                    "You can acknowledge the delay, give a clear next step, and keep the tone calm.",
                    CtaType.REPLY_COPY));
                currentSession.messageCount = currentSession.messages.size();
                currentSession.updatePreview("Calm reply plan for the delivery update");
                showPopupInMode(Mode.CHAT, null);
                break;
            default:
                Log.w(TAG, "Unknown debug state: " + state);
        }
    }

    private void seedDebugSessions() {
        sessions.clear();
        long now = System.currentTimeMillis();
        AiChatSession recent = createDebugSession("Delivery update", now - 5 * 60 * 1000);
        recent.lastPreview = "Calm reply plan for the delayed order";
        recent.messageCount = 4;

        AiChatSession yesterday = createDebugSession("Invoice screenshot", now - 26 * 60 * 60 * 1000);
        yesterday.lastPreview = "Summary and next action from the screen";
        yesterday.messageCount = 3;

        AiChatSession older = createDebugSession("Telegram plan", now - 3 * 24 * 60 * 60 * 1000);
        older.lastPreview = "Drafted a short response for the group";
        older.messageCount = 2;

        sessions.add(recent);
        sessions.add(yesterday);
        sessions.add(older);
        sessionsOffset = sessions.size();
        sessionsHasMore = false;
    }

    private AiChatSession createDebugSession(String title, long timestamp) {
        AiChatSession session = new AiChatSession();
        session.title = title;
        session.source = UnifiedChatSessionManager.SOURCE_OVERLAY;
        session.lastUpdated = timestamp > 0 ? timestamp : System.currentTimeMillis();
        session.messageCount = session.messages.size();
        return session;
    }

    private void captureNewScreenshot() {
        hide();
        WittyKeysOverlayService service = WittyKeysOverlayService.getInstance();
        if (service != null) {
            service.triggerScreenshot();
        }
    }

    // ─── Shutdown ───

    public void shutdown() {
        mainHandler.removeCallbacksAndMessages(null);
        if (aiEngine != null) {
            aiEngine.shutdown();
        }
    }

    // ─── Util ───

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
