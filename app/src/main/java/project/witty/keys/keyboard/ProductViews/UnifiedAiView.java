// In: project/witty/keys/keyboard/ProductViews/UnifiedAiView.java
package project.witty.keys.keyboard.ProductViews;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import project.witty.keys.R;
import project.witty.keys.app.helpers.JourneyTracer;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.keyboard.AiChat.AIFeatureType;
import project.witty.keys.keyboard.AiChat.ChatAdapter;
import project.witty.keys.keyboard.AiChat.ChatItem;
import project.witty.keys.keyboard.AiChat.AiMessage;
import project.witty.keys.keyboard.AiChat.ChatPersistence;
import project.witty.keys.keyboard.AiChat.CtaType;
import project.witty.keys.keyboard.AiChat.ErrorMessage;
import project.witty.keys.keyboard.AiChat.Loading;
import project.witty.keys.keyboard.AiChat.NlsBannerMessage;
import project.witty.keys.keyboard.AiChat.ScreenshotMessage;
import project.witty.keys.keyboard.AiChat.AnalyzingMessage;
import project.witty.keys.keyboard.AiChat.SessionBannerMessage;
import project.witty.keys.keyboard.AiChat.UserMessage;

import project.witty.keys.app.context.AiChatStateManager;
import project.witty.keys.app.context.UnifiedChatSessionManager;
import project.witty.keys.app.context.ContextWindowManager;
import project.witty.keys.app.context.ConversationMatcher;
import project.witty.keys.app.context.NlsMessageBuffer;
import project.witty.keys.app.context.SessionRepository;
import project.witty.keys.app.database.ChatMessage;
import project.witty.keys.app.database.ChatSession;
import project.witty.keys.app.database.ChatSessionDao;
import project.witty.keys.app.database.SessionScreenshot;
import project.witty.keys.app.database.WittyKeysDatabase;
import project.witty.keys.app.helpers.NavigationBarHelper;
import project.witty.keys.keyboard.KeyboardActionListener;
import project.witty.keys.keyboard.KeyboardSwitcher;
import project.witty.keys.keyboard.Themeable;
import project.witty.keys.keyboard.internal.InternalChatInput;
import project.witty.keys.latin.LatinIME;
import project.witty.keys.ui.chat.WkStripMiniHeader;
import project.witty.keys.ui.chat.WkChatHeader;
import project.witty.keys.ui.chat.WkEmptyState;
import project.witty.keys.ui.chat.WkSessionCard;
import project.witty.keys.ui.chat.WkDualCtaRow;
import project.witty.keys.ui.chat.Surface;
import project.witty.keys.ui.chat.WkInputBar;
import project.witty.keys.ui.chat.WkSessionDisplay;

public class UnifiedAiView extends ProductContainerView implements Themeable {
    private static final String TAG = "UnifiedAiView";
    private static final int MAX_CHAT_ITEMS = 100;

    // UI States
    public static final int STATE_REPLY_MODE = 0;  // Split: header + input + keyboard
    public static final int STATE_AI_VIEW = 1;      // Full: header + chat + reply bar
    public static final int STATE_NEW_CHAT = 2;      // ac-new-chat: input bar + QWERTY visible
    public static final int STATE_SESSIONS_LIST = 3;  // ac-sessions-list / ac-sessions-empty

    // Modes
    public static final int MODE_GENERAL = 0;
    public static final int MODE_PERSONAL = 1;

    // State tracking
    private int mCurrentState = STATE_SESSIONS_LIST;
    private int mCurrentMode = MODE_GENERAL;

    // Core views
    private RecyclerView mChatRecyclerView;
    private ProgressBar mInitialProgressBar;
    private ChatAdapter mChatAdapter;
    private List<ChatItem> mChatItems;
    private Context mThemedContext;
    private KeyboardActionListener mKeyboardActionListener;
    private LatinIME mLatinIme;

    // New header/reply bar views
    private View mHeaderView;
    private WkStripMiniHeader mDsHeader;
    private WkChatHeader mDsChatHeader;
    private View mCaptureBtn; // Build 7.0 P3
    private View mReplyBarView;
    private TextView mReplyTriggerBtn;
    private View mBodyContainer;

    // Input area views (Phase C)
    private WkInputBar mInputBar;
    private InternalChatInput mChatInput;
    private View mSendBtn;
    private ChatPersistence mChatPersistence;

    // P4: Session persistence + context managers
    private AiChatStateManager stateManager;
    private UnifiedChatSessionManager unifiedSessionManager;
    private SessionRepository sessionRepo;
    private ContextWindowManager contextManager;
    private boolean sessionOpened = false; // P4: one-time session init flag

    // T5/T6: Session list views
    private RecyclerView mSessionListRecyclerView;
    private View mSessionListContainer;
    private View mSessionsEmptyView;
    private String currentSessionTitle = "";
    private long currentSessionId = -1;
    private String currentSource = "keyboard";

    // Navigation bar padding — same pattern as EmojiKeyboard
    // Default 0: system/InputView manages keyboard height including nav bar.
    private int mNavigationBarHeight = 0;

    // Undo bar for session delete
    private final android.os.Handler mUndoHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable mUndoRunnable;
    private android.view.View mUndoBar;

    // Session list pagination
    private static final int SESSIONS_PAGE_SIZE = 20;
    private int mSessionsOffset = 0;
    private SessionCardAdapter mSessionsAdapter;

    public UnifiedAiView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mThemedContext = context;
        mChatPersistence = new ChatPersistence(context);

        stateManager = AiChatStateManager.getInstance();
        stateManager.init(context);
        unifiedSessionManager = UnifiedChatSessionManager.getInstance(context);
        sessionRepo = SessionRepository.getInstance(context);
        contextManager = ContextWindowManager.getInstance();

        if (titleTextView != null && titleTextView.getParent() != null) {
            ((View) titleTextView.getParent()).setVisibility(View.GONE);
        }
        if (backButton != null) {
            backButton.setVisibility(View.GONE);
        }

        mNavigationBarHeight = NavigationBarHelper.getSafeBottomPadding(context);

        LayoutInflater.from(context).inflate(R.layout.wk_ds_unified_ai_view, this, true);

        mDsHeader = findViewById(R.id.wkDsHeader);
        mDsChatHeader = findViewById(R.id.wkDsChatHeader);
        mBodyContainer = findViewById(R.id.wkBodyContainer);
        mChatRecyclerView = findViewById(R.id.wkChatRecyclerView);
        mSessionListContainer = findViewById(R.id.wkSessionListContainer);
        mSessionListRecyclerView = findViewById(R.id.wkSessionListRecyclerView);
        mSessionsEmptyView = findViewById(R.id.wkSessionsEmptyView);
        if (mSessionsEmptyView instanceof WkEmptyState) {
            WkEmptyState emptyState = (WkEmptyState) mSessionsEmptyView;
            emptyState.setIcon(R.drawable.ic_wk_empty_chat);
            emptyState.bind("No conversations yet", "Start a chat or share a screenshot to begin.");
        }
        mInitialProgressBar = findViewById(R.id.wkInitialProgressBar);
        mInputBar = findViewById(R.id.wkInputBar);
        mReplyBarView = findViewById(R.id.wkReplyBarView);
        mReplyTriggerBtn = mReplyBarView.findViewById(R.id.replyTriggerBtn);
        
        mChatInput = (InternalChatInput) mInputBar.getEditText();
        mSendBtn = mInputBar.findViewById(R.id.wkInputSend);
        mCaptureBtn = mInputBar.findViewById(R.id.wkInputCapture);

        mHeaderView = mDsHeader;

        mDsHeader.setOnCloseClickListener(() -> {
            KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
            if (switcher != null) switcher.showKeyboardView();
            clearSession();
        });
        mDsHeader.setOnSessionsClickListener(() -> showSessionsList());
        
        mDsChatHeader.setOnBackClickListener(() -> setUIState(STATE_SESSIONS_LIST));
        mDsChatHeader.setOnSessionsClickListener(() -> showSessionsList());
        mDsChatHeader.setOnCloseClickListener(() -> {
            KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
            if (switcher != null) switcher.showKeyboardView();
            clearSession();
        });

        mChatItems = new ArrayList<>();
        mChatRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        
        mSessionListRecyclerView.setLayoutManager(new LinearLayoutManager(context));

        WkDualCtaRow ctaRow = findViewById(R.id.wkDualCtaRow);
        if (ctaRow != null) {
            ctaRow.setPrimary("Screenshot", () -> triggerScreenCapture());
            ctaRow.setPrimaryIcon(R.drawable.ic_wk_capture);
            ctaRow.setGhost("New chat", () -> setUIState(STATE_NEW_CHAT));
            ctaRow.setGhostIcon(R.drawable.ic_wk_plus);
        }

        setupHeaderButtons();

        mReplyTriggerBtn.setOnClickListener(v -> {
            KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
            if (switcher != null) switcher.showReplyModeView();
            setUIState(STATE_REPLY_MODE);
        });

        mInputBar.setOnSendListener(text -> sendChatMessage(text));
        mInputBar.setOnCaptureListener(() -> triggerScreenCapture());

        mChatInput.setOnSendCallback(() -> sendChatMessage(mChatInput.getText()));

        mChatInput.setOnTextChangedListener(text -> {
            if (mSendBtn != null) {
                mSendBtn.setAlpha(text.isEmpty() ? 0.4f : 1.0f);
            }
        });

        setUIState(mCurrentState);
        updateUIForMode();
        onThemeChanged(context);
    }
    // ============================================================================================
    // STATE & MODE MANAGEMENT
    // ============================================================================================

    public void setUIState(int state) {
        Log.d(TAG, "setUIState: " + stateToString(state) + " (was " + stateToString(mCurrentState) + ")");
        mCurrentState = state;

        // Hide ALL state-specific containers first
        if (mChatRecyclerView != null) mChatRecyclerView.setVisibility(View.GONE);
        if (mInputBar != null) {
            // Reset input bar to wrap_content before hiding (strip states set weight=1)
            LinearLayout.LayoutParams stripLp = (LinearLayout.LayoutParams) mInputBar.getLayoutParams();
            if (stripLp != null && stripLp.weight != 0f) {
                stripLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                stripLp.weight = 0f;
                mInputBar.setLayoutParams(stripLp);
            }
            mInputBar.setVisibility(View.GONE);
        }
        if (mReplyBarView != null) mReplyBarView.setVisibility(View.GONE);
        if (mReplyTriggerBtn != null) mReplyTriggerBtn.setVisibility(View.GONE);
        if (mSessionListContainer != null) mSessionListContainer.setVisibility(View.GONE);
        if (mInitialProgressBar != null) mInitialProgressBar.setVisibility(View.GONE);
        if (mDsHeader != null) mDsHeader.setVisibility(View.GONE);
        if (mDsChatHeader != null) mDsChatHeader.setVisibility(View.GONE);
        if (mBodyContainer != null) mBodyContainer.setVisibility(View.GONE);
        hideNewChatPrompt();

        switch (state) {
            case STATE_SESSIONS_LIST:
                // K3/K4: Full takeover — chat header (44px) + dual CTA + sessions/empty
                if (mBodyContainer != null) mBodyContainer.setVisibility(View.VISIBLE);
                showSessionsListState();
                break;
            case STATE_NEW_CHAT:
                // K1: Compact strip header (34px) + input bar + QWERTY below
                showNewChatState();
                break;
            case STATE_AI_VIEW:
                // K5-K8: Full takeover — chat header (44px) + chat + input
                if (mBodyContainer != null) mBodyContainer.setVisibility(View.VISIBLE);
                KeyboardSwitcher aiViewSwitcher = KeyboardSwitcher.getInstance();
                if (aiViewSwitcher != null) {
                    aiViewSwitcher.showAiChatView();
                }
                // Show chat header with back button for AI_VIEW
                if (mDsChatHeader != null) {
                    mDsChatHeader.setVisibility(View.VISIBLE);
                    mDsChatHeader.setBackVisible(true);
                }
                if (mChatRecyclerView != null) mChatRecyclerView.setVisibility(View.VISIBLE);
                if (mInputBar != null) mInputBar.setVisibility(View.VISIBLE);
                updateHeaderForActiveChat();
                setupInputAsTapTarget();
                if (mChatInput != null) {
                    mChatInput.setPlaceholderText("Follow up\u2026");
                }
                if (mChatInput != null && mChatInput.isActive()) mChatInput.deactivate();
                break;
            case STATE_REPLY_MODE:
                // K2: Compact strip header (34px) + input bar + QWERTY below
                KeyboardSwitcher replyModeSwitcher = KeyboardSwitcher.getInstance();
                if (replyModeSwitcher != null) {
                    replyModeSwitcher.showReplyModeView();
                }
                // Show strip header with reply context
                if (mDsHeader != null) {
                    mDsHeader.setVisibility(View.VISIBLE);
                    mDsHeader.setDotColor(getResources().getColor(R.color.wk_purple));
                    String replyContext = (currentSessionTitle != null && !currentSessionTitle.isEmpty())
                        ? currentSessionTitle : null;
                    mDsHeader.setReplyQuote(replyContext);
                }
                if (mInputBar != null) {
                    mInputBar.setVisibility(View.VISIBLE);
                    LinearLayout.LayoutParams stripLp = (LinearLayout.LayoutParams) mInputBar.getLayoutParams();
                    if (stripLp != null) { stripLp.height = 0; stripLp.weight = 1f; mInputBar.setLayoutParams(stripLp); }
                }
                activateInputForTyping();
                break;
        }
    }

    private String stateToString(int state) {
        switch (state) {
            case STATE_REPLY_MODE: return "REPLY_MODE";
            case STATE_AI_VIEW: return "AI_VIEW";
            case STATE_NEW_CHAT: return "NEW_CHAT";
            case STATE_SESSIONS_LIST: return "SESSIONS_LIST";
            default: return "UNKNOWN(" + state + ")";
        }
    }

    // ============================================================================================
    // T5/T6: HELPERS
    // ============================================================================================

    private void updateSessionsEmptyState(SessionCardAdapter adapter) {
        boolean empty = !adapter.hasAnySessions();
        if (mSessionListRecyclerView != null)
            mSessionListRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (mSessionsEmptyView != null)
            mSessionsEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty && mDsChatHeader != null)
            mDsChatHeader.setMeta(null);
    }

    private void showDeleteUndoBar(ChatSession session, int removedPos, SessionCardAdapter adapter) {
        // Dismiss any existing undo bar first
        if (mUndoRunnable != null) mUndoHandler.removeCallbacks(mUndoRunnable);
        if (mUndoBar != null) removeView(mUndoBar);

        android.widget.LinearLayout bar = new android.widget.LinearLayout(getContext());
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF323232);
        int pad = dpToPx(14);
        bar.setPadding(pad, dpToPx(10), pad, dpToPx(10));

        android.widget.TextView msgView = new android.widget.TextView(getContext());
        msgView.setText("Session deleted");
        msgView.setTextColor(0xFFFFFFFF);
        msgView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        msgView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.TextView undoView = new android.widget.TextView(getContext());
        undoView.setText("UNDO");
        undoView.setTextColor(0xFF64B5F6);
        undoView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        undoView.setTypeface(null, android.graphics.Typeface.BOLD);
        undoView.setPadding(dpToPx(8), 0, 0, 0);

        bar.addView(msgView);
        bar.addView(undoView);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.BOTTOM;
        addView(bar, lp);
        mUndoBar = bar;

        mUndoRunnable = () -> {
            removeView(bar);
            mUndoBar = null;
            if (unifiedSessionManager != null) {
                unifiedSessionManager.deleteSession(session.id, () -> {});
            }
        };
        mUndoHandler.postDelayed(mUndoRunnable, 4000);

        undoView.setOnClickListener(v -> {
            mUndoHandler.removeCallbacks(mUndoRunnable);
            mUndoRunnable = null;
            removeView(bar);
            mUndoBar = null;
            // Reload from DB — session still there, headers rebuilt correctly
            loadSessionsList();
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /** Apply accent→purple gradient shader to header title text (matches mockup) */
    private void applyGradientText(final TextView tv) {
        tv.post(() -> {
            int w = tv.getWidth();
            if (w <= 0) w = tv.getMeasuredWidth();
            if (w <= 0) w = 200; // fallback
            android.graphics.LinearGradient shader = new android.graphics.LinearGradient(
                0, 0, w, 0,
                getResources().getColor(R.color.wk_accent),
                getResources().getColor(R.color.wk_purple),
                android.graphics.Shader.TileMode.CLAMP);
            tv.getPaint().setShader(shader);
            tv.invalidate();
        });
    }

    private void scrollToBottom() {
        if (mChatRecyclerView != null && mChatAdapter != null && mChatAdapter.getItemCount() > 0) {
            mChatRecyclerView.scrollToPosition(mChatAdapter.getItemCount() - 1);
        }
    }

    // ============================================================================================
    // T5/T6: HEADER BUTTONS, INPUT TAP TARGET, SESSION LIST
    // ============================================================================================

    private void setupHeaderButtons() {
        // Update the appropriate header for current state
        switch (mCurrentState) {
            case STATE_SESSIONS_LIST:
                updateHeaderForSessionsList();
                break;
            case STATE_NEW_CHAT:
                updateHeaderForNewChat();
                break;
            default:
                updateHeaderForActiveChat();
                break;
        }
    }

    private void launchFullscreen() {
        Context ctx = getContext();
        if (ctx != null) {
            Intent intent = new Intent(ctx, project.witty.keys.keyboard.AiChat.AiChatActivity.class);
            if (currentSessionId > 0) {
                intent.putExtra("session_id", currentSessionId);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        }
    }

    /** K1: Strip header for New Chat state. */
    private void updateHeaderForNewChat() {
        if (mDsHeader != null) {
            mDsHeader.setTitle("New chat");
            mDsHeader.setDotColor(getResources().getColor(R.color.wk_green));
            mDsHeader.setReplyQuote(null);
        }
    }

    public void setReplyText(String text) {
        currentSessionTitle = (text != null) ? text : "";
    }

    /** K5-K8: Chat header for Active Chat state. */
    private void updateHeaderForActiveChat() {
        if (mDsChatHeader != null) {
            String title = (currentSessionTitle != null && !currentSessionTitle.isEmpty())
                ? WkSessionDisplay.displayTitle(currentSessionTitle) : "AI Chat";
            mDsChatHeader.setTitle(title);
            mDsChatHeader.setBackVisible(true);
            mDsChatHeader.setDotColor(getResources().getColor(R.color.wk_green));
            mDsChatHeader.setSessionsVisible(true);
            mDsChatHeader.setMeta(null);
        }
    }

    /** K3/K4: Chat header for Sessions List state. */
    private void updateHeaderForSessionsList() {
        if (mDsChatHeader != null) {
            mDsChatHeader.setTitle("All sessions");
            mDsChatHeader.setBackVisible(false);
            mDsChatHeader.setDotVisible(false);
            mDsChatHeader.setMeta(null);
            mDsChatHeader.setSessionsVisible(false);
        }
    }

    private void showDeleteAllConfirmation() {
        new android.app.AlertDialog.Builder(mThemedContext)
            .setTitle("Delete all sessions?")
            .setMessage("This will permanently remove all chat sessions and messages.")
            .setPositiveButton("Delete All", (dialog, which) -> {
                if (unifiedSessionManager != null) {
                    unifiedSessionManager.deleteAllSessions();
                    // Refresh the session list after a short delay for DB to complete
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        loadSessionsList();
                    }, 300);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setupInputAsTapTarget() {
        if (mChatInput != null) {
            mChatInput.setFocusable(false);
            mChatInput.setFocusableInTouchMode(false);
            mChatInput.setOnClickListener(v -> {
                Log.d(TAG, "Input tapped — switching to REPLY_MODE");
                setUIState(STATE_REPLY_MODE);
            });
        }
        if (mSendBtn != null) {
            mSendBtn.setAlpha(0.4f);
            mSendBtn.setEnabled(false);
        }
    }

    private void activateInputForTyping() {
        if (mChatInput != null) {
            mChatInput.setOnClickListener(null);
            mChatInput.setFocusable(true);
            mChatInput.setFocusableInTouchMode(true);
            mChatInput.requestFocus();
            if (!mChatInput.isActive()) mChatInput.activate();
            Log.w("WK_DIAG", "[INPUT] activateInputForTyping: active=" + mChatInput.isActive());
        }
        if (mSendBtn != null) {
            mSendBtn.setAlpha(1.0f);
            mSendBtn.setEnabled(true);
        }
    }

    private void showSessionsList() {
        setUIState(STATE_SESSIONS_LIST);
    }

    private void showSessionsListState() {
        // Reset session so screenshot/new-chat don't accidentally append to last-viewed session
        currentSessionId = -1;
        sessionOpened = false;

        KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        if (switcher != null) {
            switcher.showAiChatView();
        }
        if (mDsChatHeader != null) mDsChatHeader.setVisibility(View.VISIBLE);
        updateHeaderForSessionsList();

        if (mSessionListContainer != null) mSessionListContainer.setVisibility(View.VISIBLE);
        loadSessionsList();
    }

    private View createDualCtaRow() {
        WkDualCtaRow cta = new WkDualCtaRow(mThemedContext);
        cta.setPrimary("Screenshot", () -> triggerScreenCapture());
        cta.setPrimaryIcon(R.drawable.ic_wk_capture);
        cta.setGhost("New chat", () -> setUIState(STATE_NEW_CHAT));
        cta.setGhostIcon(R.drawable.ic_wk_plus);
        return cta;
    }

    private void showNewChatState() {
        // K1: Compact strip header (34px) + input bar + QWERTY below
        KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        if (switcher != null) {
            switcher.showReplyModeView();
        }

        // Show strip header with "New chat" title
        if (mDsHeader != null) {
            mDsHeader.setVisibility(View.VISIBLE);
            updateHeaderForNewChat();
        }

        // Show input area (compact strip: header + input, QWERTY below)
        // weight=1 expands input bar to fill remaining 101dp-32dp=69dp, eliminating the black gap
        if (mInputBar != null) {
            mInputBar.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams stripLp = (LinearLayout.LayoutParams) mInputBar.getLayoutParams();
            if (stripLp != null) { stripLp.height = 0; stripLp.weight = 1f; mInputBar.setLayoutParams(stripLp); }
        }
        if (mReplyBarView != null) mReplyBarView.setVisibility(View.GONE);

        // Clear any existing chat
        if (mChatItems != null) mChatItems.clear();
        if (mChatAdapter != null) mChatAdapter.notifyDataSetChanged();

        // Reset session state so next send creates a fresh Room session
        currentSessionId = -1;
        currentSessionTitle = "";
        currentSource = "keyboard";
        sessionOpened = false;

        // Activate input for immediate typing
        activateInputForTyping();
        if (mChatInput != null) {
            mChatInput.setPlaceholderText("Ask anything...");
        }

        Log.d(TAG, "Showing New Chat state (compact strip + QWERTY)");
    }

    private View mNewChatPromptView;

    private void showNewChatPrompt() {
        if (mNewChatPromptView == null) {
            mNewChatPromptView = createNewChatPromptView();
            ViewGroup parent = (ViewGroup) mChatRecyclerView.getParent();
            if (parent != null) {
                // Insert before input area (index parent.childCount - 1 would be input)
                parent.addView(mNewChatPromptView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
            }
        }
        mNewChatPromptView.setVisibility(View.VISIBLE);
    }

    private void hideNewChatPrompt() {
        if (mNewChatPromptView != null) mNewChatPromptView.setVisibility(View.GONE);
    }

    private View createNewChatPromptView() {
        LinearLayout container = new LinearLayout(mThemedContext);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(10));
        container.setBackgroundColor(getResources().getColor(R.color.wk_bg));

        // ✨ icon
        TextView icon = new TextView(mThemedContext);
        icon.setText("✨");
        icon.setTextSize(28);
        icon.setGravity(Gravity.CENTER);
        icon.setAlpha(0.5f);
        container.addView(icon);

        // Title
        TextView title = new TextView(mThemedContext);
        title.setText("How can I help?");
        title.setTextSize(14);
        title.setTextColor(getResources().getColor(R.color.wk_text));
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dpToPx(6);
        container.addView(title, titleLp);

        // Subtitle
        TextView sub = new TextView(mThemedContext);
        sub.setText("Type a message or try a suggestion");
        sub.setTextSize(11);
        sub.setTextColor(getResources().getColor(R.color.wk_text3));
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dpToPx(2);
        container.addView(sub, subLp);

        // Suggestion chips (2 rows of 2)
        LinearLayout chipContainer = createSuggestionChips();
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = dpToPx(10);
        container.addView(chipContainer, chipLp);

        return container;
    }

    private LinearLayout createSuggestionChips() {
        LinearLayout rows = new LinearLayout(mThemedContext);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setGravity(Gravity.CENTER_HORIZONTAL);

        String[][] suggestions = {
            {"Draft an email", "Translate to Hindi"},
            {"Fix grammar", "Explain this"}
        };

        for (String[] row : suggestions) {
            LinearLayout rowLayout = new LinearLayout(mThemedContext);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);

            for (String text : row) {
                TextView chip = new TextView(mThemedContext);
                chip.setText(text);
                chip.setTextSize(10);
                chip.setTextColor(getResources().getColor(R.color.wk_text2));
                chip.setPadding(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5));

                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(dpToPx(16));
                bg.setColor(getResources().getColor(R.color.wk_surface));
                bg.setStroke(1, 0x14000000);
                chip.setBackground(bg);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
                chip.setLayoutParams(lp);

                chip.setOnClickListener(v -> {
                    if (mChatInput != null) {
                        mChatInput.setText(text);
                        activateInputForTyping();
                    }
                });
                rowLayout.addView(chip);
            }
            rows.addView(rowLayout);
        }
        return rows;
    }

    private void loadSessionsList() {
        mSessionsOffset = 0;
        mSessionsAdapter = null;
        loadSessionsPage(0);
    }

    private void loadSessionsPage(int offset) {
        if (unifiedSessionManager == null) return;
        new Thread(() -> {
            List<ChatSession> page = unifiedSessionManager.getSessionsPageSync(SESSIONS_PAGE_SIZE, offset);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (offset == 0) {
                    // First page — build fresh adapter
                    if (page.isEmpty()) {
                        if (mSessionListRecyclerView != null) mSessionListRecyclerView.setVisibility(View.GONE);
                        if (mSessionsEmptyView != null) mSessionsEmptyView.setVisibility(View.VISIBLE);
                        if (mDsChatHeader != null) mDsChatHeader.setMeta(null);
                        return;
                    }
                    if (mSessionListRecyclerView != null) mSessionListRecyclerView.setVisibility(View.VISIBLE);
                    if (mSessionsEmptyView != null) mSessionsEmptyView.setVisibility(View.GONE);

                    mSessionsAdapter = new SessionCardAdapter(page, session -> {
                        currentSessionId = session.id;
                        currentSource = session.source;
                        loadSessionMessagesFromRoom(session);
                    });
                    final SessionCardAdapter adapter = mSessionsAdapter;
                    mSessionsAdapter.setOnSessionDeleteListener(session -> {
                        int removedPos = adapter.removeSession(session);
                        updateSessionsEmptyState(adapter);
                        showDeleteUndoBar(session, removedPos, adapter);
                    });
                    mSessionsAdapter.setOnLoadMoreListener(() -> {
                        mSessionsAdapter.setLoadMoreVisible(false);
                        loadSessionsPage(mSessionsOffset);
                    });
                    mSessionsOffset = page.size();
                    if (page.size() == SESSIONS_PAGE_SIZE) mSessionsAdapter.setLoadMoreVisible(true);
                    mSessionListRecyclerView.setAdapter(mSessionsAdapter);
                } else {
                    // Subsequent page — append to existing adapter
                    if (mSessionsAdapter == null) return;
                    mSessionsAdapter.appendSessions(page);
                    mSessionsOffset += page.size();
                    if (page.size() == SESSIONS_PAGE_SIZE) mSessionsAdapter.setLoadMoreVisible(true);
                }
                Log.d(TAG, "Sessions page loaded: " + page.size() + " (offset=" + offset + ")");
            });
        }).start();
    }

    /** Test support: inject 3 demo sessions for K04 golden state */
    public void injectDemoSessions() {
        new Thread(() -> {
            try {
                WittyKeysDatabase db = WittyKeysDatabase.getInstance(getContext());
                ChatSessionDao dao = db.chatSessionDao();

                // Clear existing sessions first so we get a clean K04 state
                dao.deleteAll();

                long now = System.currentTimeMillis();

                ChatSession s1 = new ChatSession();
                s1.title = "Chat with Aanya";
                s1.source = "overlay";
                s1.sourceIcon = "\uD83D\uDD2E"; // crystal ball
                s1.summary = "Slides land by 6pm \u2014 on it.";
                s1.updatedAt = now - 120_000; // 2m ago
                s1.createdAt = now - 120_000;
                s1.messageCount = 3;

                ChatSession s2 = new ChatSession();
                s2.title = "Hindi \u2014 landlord email";
                s2.source = "fullscreen";
                s2.sourceIcon = "\u2197\uFE0F"; // arrow
                s2.summary = "Polite request for plumbing fix\u2026";
                s2.updatedAt = now - 3_600_000; // 1h ago
                s2.createdAt = now - 3_600_000;
                s2.messageCount = 5;

                ChatSession s3 = new ChatSession();
                s3.title = "Rahul \u2014 party plan";
                s3.source = "keyboard";
                s3.sourceIcon = "\u2328\uFE0F"; // keyboard
                s3.summary = "Flirty short replies with emoji\u2026";
                s3.updatedAt = now - 86_400_000; // yesterday
                s3.createdAt = now - 86_400_000;
                s3.messageCount = 8;

                dao.insert(s1);
                dao.insert(s2);
                dao.insert(s3);

                new Handler(Looper.getMainLooper()).post(() -> loadSessionsList());
                Log.d(TAG, "injectDemoSessions: 3 demo sessions inserted");
            } catch (Exception e) {
                Log.e(TAG, "injectDemoSessions failed", e);
            }
        }).start();
    }

    /** For golden capture K03: wipes Room sessions and shows empty sessions state. */
    public void clearAllSessionsAndShowEmpty() {
        if (unifiedSessionManager == null) {
            if (mSessionsEmptyView != null) mSessionsEmptyView.setVisibility(View.VISIBLE);
            if (mSessionListRecyclerView != null) mSessionListRecyclerView.setVisibility(View.GONE);
            return;
        }
        new Thread(() -> {
            try {
                WittyKeysDatabase db = WittyKeysDatabase.getInstance(getContext());
                db.chatSessionDao().deleteAll();
                new Handler(Looper.getMainLooper()).post(() -> loadSessionsList());
                Log.d(TAG, "clearAllSessionsAndShowEmpty: all sessions deleted");
            } catch (Exception e) {
                Log.e(TAG, "clearAllSessionsAndShowEmpty failed", e);
            }
        }).start();
    }

    private void loadSessionMessagesFromRoom(ChatSession session) {
        if (unifiedSessionManager == null) return;
        new Thread(() -> {
            List<ChatMessage> messages = unifiedSessionManager.getMessagesSync(session.id);
            // Resolve screenshot records on background thread before posting to main
            List<ChatItem> restoredItems = new ArrayList<>();
            for (project.witty.keys.app.database.ChatMessage msg : messages) {
                if (msg.content == null || msg.content.isEmpty()) continue;
                if ("nls_context".equals(msg.type)) continue;
                if ("user".equals(msg.role)) {
                    restoredItems.add(new UserMessage(msg.content, msg.timestamp));
                } else if ("assistant".equals(msg.role)) {
                    restoredItems.add(new AiMessage(msg.content, CtaType.REPLY_COPY, msg.timestamp));
                } else if ("screenshot_analysis".equals(msg.type)) {
                    if (msg.screenshotId != null) {
                        WittyKeysDatabase db = WittyKeysDatabase.getInstance(getContext());
                        SessionScreenshot ss = db.screenshotDao().getById(msg.screenshotId);
                        if (ss != null && ss.filePath != null) {
                            restoredItems.add(new ScreenshotMessage(ss.filePath, null, ss.width, ss.height));
                        }
                    }
                    restoredItems.add(new AiMessage(msg.content, CtaType.REPLY_COPY, msg.timestamp));
                }
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                mChatItems.clear();
                if (!restoredItems.isEmpty()) {
                    SessionBannerMessage banner = new SessionBannerMessage(restoredItems.size());
                    mChatItems.add(banner);
                    mChatItems.addAll(restoredItems);
                    if (mChatAdapter != null) mChatAdapter.setRestoredItemCount(1 + restoredItems.size());
                }
                if (mChatAdapter != null) mChatAdapter.notifyDataSetChanged();
                currentSessionTitle = WkSessionDisplay.displayTitle(session.title);
                currentSource = session.source != null ? session.source : "keyboard";
                setUIState(STATE_AI_VIEW);
                scrollToBottom();
                Log.d(TAG, "Loaded session " + session.id + ": " + restoredItems.size() + " messages");
            });
        }).start();
    }

    /** Empty state view matching mockup ac-sessions-empty */
    private View createEmptyStateView() {
        WkEmptyState empty = new WkEmptyState(mThemedContext);
        empty.setIcon(R.drawable.ic_wk_empty_chat);
        empty.bind("No conversations yet", "Start a chat or share a screenshot to begin.");
        return empty;
    }

    // SessionCardAdapter — light theme, source-coded avatars per mockup
    private static class SessionCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_SESSION = 1;
        private static final int TYPE_LOAD_MORE = 2;

        private static final class LoadMoreFooter {}
        private static final LoadMoreFooter LOAD_MORE = new LoadMoreFooter();

        // Flat list: String = date header, ChatSession = session card, LoadMoreFooter = footer
        private final List<Object> items;
        private final OnSessionClickListener listener;
        private OnSessionDeleteListener deleteListener;
        private Runnable loadMoreListener;

        interface OnSessionClickListener {
            void onSessionClick(ChatSession session);
        }

        interface OnSessionDeleteListener {
            void onSessionDelete(ChatSession session);
        }

        void setOnSessionDeleteListener(OnSessionDeleteListener dl) {
            this.deleteListener = dl;
        }

        void setOnLoadMoreListener(Runnable r) {
            this.loadMoreListener = r;
        }

        void setLoadMoreVisible(boolean show) {
            boolean currently = !items.isEmpty() && items.get(items.size() - 1) instanceof LoadMoreFooter;
            if (show && !currently) {
                items.add(LOAD_MORE);
                notifyItemInserted(items.size() - 1);
            } else if (!show && currently) {
                items.remove(items.size() - 1);
                notifyItemRemoved(items.size());
            }
        }

        void appendSessions(List<ChatSession> newSessions) {
            // Remove footer before appending, rebuild grouped entries, re-add footer handled by caller
            setLoadMoreVisible(false);
            String lastBucket = null;
            // Find last bucket in existing items
            for (int i = items.size() - 1; i >= 0; i--) {
                if (items.get(i) instanceof String) { lastBucket = (String) items.get(i); break; }
            }
            for (ChatSession s : newSessions) {
                long ts = s.updatedAt > 0 ? s.updatedAt : s.createdAt;
                String bucket = dateBucket(ts);
                if (!bucket.equals(lastBucket)) {
                    items.add(bucket);
                    notifyItemInserted(items.size() - 1);
                    lastBucket = bucket;
                }
                items.add(s);
                notifyItemInserted(items.size() - 1);
            }
        }

        SessionCardAdapter(List<ChatSession> sessions, OnSessionClickListener listener) {
            this.items = buildGroupedList(sessions);
            this.listener = listener;
        }

        private static List<Object> buildGroupedList(List<ChatSession> sessions) {
            List<Object> result = new ArrayList<>();
            String lastBucket = null;
            for (ChatSession s : sessions) {
                long ts = s.updatedAt > 0 ? s.updatedAt : s.createdAt;
                String bucket = dateBucket(ts);
                if (!bucket.equals(lastBucket)) {
                    result.add(bucket);
                    lastBucket = bucket;
                }
                result.add(s);
            }
            return result;
        }

        private static String dateBucket(long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;
            if (diff < 86_400_000L) return "Today";
            if (diff < 172_800_000L) return "Yesterday";
            return "Older";
        }

        @Override
        public int getItemViewType(int position) {
            Object item = items.get(position);
            if (item instanceof String) return TYPE_HEADER;
            if (item instanceof LoadMoreFooter) return TYPE_LOAD_MORE;
            return TYPE_SESSION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                android.widget.TextView tv = (android.widget.TextView) android.view.LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.wk_ds_session_date_header, parent, false);
                return new DateHeaderVH(tv);
            }
            if (viewType == TYPE_LOAD_MORE) {
                android.widget.TextView btn = new android.widget.TextView(parent.getContext());
                btn.setText("Load more");
                btn.setTextColor(0xFF64B5F6);
                btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                btn.setGravity(android.view.Gravity.CENTER);
                btn.setPadding(0, (int)(14 * parent.getContext().getResources().getDisplayMetrics().density),
                    0, (int)(14 * parent.getContext().getResources().getDisplayMetrics().density));
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btn.setLayoutParams(lp);
                return new LoadMoreVH(btn);
            }
            WkSessionCard card = new WkSessionCard(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * parent.getContext().getResources().getDisplayMetrics().density);
            card.setLayoutParams(lp);
            View deleteBtn = card.findViewById(R.id.wkCardDelete);
            return new VH(card, deleteBtn);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof DateHeaderVH) {
                ((DateHeaderVH) holder).label.setText((String) items.get(position));
                return;
            }
            if (holder instanceof LoadMoreVH) {
                holder.itemView.setOnClickListener(v -> {
                    if (loadMoreListener != null) loadMoreListener.run();
                });
                return;
            }
            ChatSession session = (ChatSession) items.get(position);
            WkSessionCard card = (WkSessionCard) holder.itemView;

            String title = WkSessionDisplay.displayTitle(session.title);
            String preview = WkSessionDisplay.preview(session.title, session.summary);
            long ts = session.updatedAt > 0 ? session.updatedAt : session.createdAt;
            String time = formatRelativeTime(ts);
            Surface source = WkSessionDisplay.surfaceFromSource(session.source);

            card.bind(title, preview, time, source, false);
            card.setOnClickListener(v -> {
                if (listener != null) listener.onSessionClick(session);
            });
            card.setOnLongClickListener(null);
            VH vh = (VH) holder;
            vh.deleteBtn.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onSessionDelete(session);
            });
        }

        private String formatRelativeTime(long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;
            if (diff < 60_000) return "now";
            if (diff < 3600_000) return (diff / 60_000) + "m";
            if (diff < 86400_000) return (diff / 3600_000) + "h";
            return (diff / 86400_000) + "d";
        }

        int removeSession(ChatSession session) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof ChatSession
                        && ((ChatSession) items.get(i)).id == session.id) {
                    items.remove(i);
                    notifyItemRemoved(i);
                    // If the preceding item is a date header and now has no session
                    // immediately after it, it's orphaned — remove it too.
                    if (i > 0 && items.get(i - 1) instanceof String) {
                        boolean orphaned = (i >= items.size() || items.get(i) instanceof String);
                        if (orphaned) {
                            items.remove(i - 1);
                            notifyItemRemoved(i - 1);
                        }
                    }
                    return i;
                }
            }
            return -1;
        }

        boolean hasAnySessions() {
            for (Object item : items) {
                if (item instanceof ChatSession) return true;
            }
            return false;
        }

        void restoreSession(ChatSession session, int position) {
            int insertAt = Math.min(position, items.size());
            items.add(insertAt, session);
            notifyItemInserted(insertAt);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final View deleteBtn;
            VH(View v, View deleteBtn) {
                super(v);
                this.deleteBtn = deleteBtn;
            }
        }

        static class DateHeaderVH extends RecyclerView.ViewHolder {
            final android.widget.TextView label;
            DateHeaderVH(android.widget.TextView v) {
                super(v);
                label = v;
            }
        }

        static class LoadMoreVH extends RecyclerView.ViewHolder {
            LoadMoreVH(android.view.View v) { super(v); }
        }
    }

    public void setMode(int mode) {
        mCurrentMode = mode;
        updateUIForMode();
    }

    private void updateUIForMode() {
        // Mode tabs removed in DS header — just set adapter avatar emoji
        if (mCurrentMode == MODE_GENERAL) {
            if (mChatAdapter != null) mChatAdapter.setAvatarEmoji("\uD83E\uDD16"); // 🤖
        } else {
            if (mChatAdapter != null) mChatAdapter.setAvatarEmoji("\uD83D\uDCAC"); // 💬
        }
    }

    private int getAccentColor() {
        TypedArray ta = getContext().obtainStyledAttributes(new int[]{R.attr.colorAccent});
        int color = ta.getColor(0, 0xFF6CB4EE);
        ta.recycle();
        return color;
    }

    private int getTitleColor() {
        TypedArray ta = getContext().obtainStyledAttributes(new int[]{R.attr.productViewTitleColor});
        int color = ta.getColor(0, 0xFFB0B0B0);
        ta.recycle();
        return color;
    }

    private int getPersonalAccentColor() {
        return 0xFFA855F7; // Purple accent for Personal mode
    }

    /**
     * Update the context pill with app/contact information.
     * Context pill removed in DS header — no-op.
     */
    public void setContextInfo(String appName, String contactName) {
        // Context pill removed in DS header
    }

    /**
     * Update the context pill with app/contact/emotion information.
     * Context pill removed in DS header — no-op.
     */
    public void setContextInfo(String appName, String contactName, String emotion) {
        // Context pill removed in DS header
    }

    private void updatePersonalContextPill() {
        // Context pill removed in DS header — no-op
    }

    public int getCurrentMode() { return mCurrentMode; }
    public int getCurrentState() { return mCurrentState; }

    /** Scroll chat RecyclerView to position 0 (top). Used by golden tests. */
    public void scrollToTop() {
        mainThread(() -> {
            if (mChatRecyclerView != null) {
                mChatRecyclerView.scrollToPosition(0);
            }
        });
    }

    public void setDebugChatItems(List<ChatItem> items, String headerMeta,
                                  boolean inputDisabled, boolean captureHighlighted) {
        mainThread(() -> {
            mChatItems.clear();
            mChatItems.addAll(items);
            mChatAdapter.resetAnimationState();
            mChatAdapter.notifyDataSetChanged();
            updateHeaderForActiveChat();
            if (mDsChatHeader != null) {
                mDsChatHeader.setMeta(headerMeta);
            }
            setInputDisabled(inputDisabled);
            if (mChatInput != null) {
                mChatInput.setPlaceholderText("Follow up…");
            }
            setCaptureButtonHighlighted(captureHighlighted);
            if (mChatRecyclerView != null) {
                mChatRecyclerView.post(() -> mChatRecyclerView.scrollToPosition(0));
            }
        });
    }

    /** Show or hide the reply bar programmatically. Used by golden tests. */
    public void setReplyBarVisible(boolean visible) {
        mainThread(() -> {
            if (mReplyBarView != null) {
                mReplyBarView.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }

    // ============================================================================================
    // VIEW LIFECYCLE & THEME
    // ============================================================================================

    public List<ChatItem> getChatItems() {
        return mChatItems;
    }

    @Override
    public void onBackPress() {
        if (mChatInput != null && mChatInput.isActive()) {
            mChatInput.deactivate();
        }
        clearSession();
        super.onBackPress();
    }

    public void setViewTitle(String title, AIFeatureType ctaType) {
        setTitleTextView(title, ctaType);
    }

    public void setup(KeyboardActionListener listener, LatinIME ime) {
        this.mKeyboardActionListener = listener;
        this.mLatinIme = ime;
        mChatAdapter = new ChatAdapter(mChatItems, listener, ime, mThemedContext);
        mChatAdapter.setCompactMode(true);
        mChatRecyclerView.setAdapter(mChatAdapter);
    }

    @Override
    public void onThemeChanged(Context themedContext) {
        super.onThemeChanged(themedContext);
        this.mThemedContext = themedContext;
        setBackgroundColor(ThemeUtils.getThemeColor(themedContext, R.attr.productViewBackgroundColor));

        // Progress bar tint
        if (mInitialProgressBar != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            mInitialProgressBar.setIndeterminateTintList(
                    android.content.res.ColorStateList.valueOf(ThemeUtils.getThemeColor(themedContext, R.attr.productViewTitleColor))
            );
        }

        // Chat adapter
        if (mChatAdapter != null) {
            mChatAdapter.setThemedContext(themedContext);
            mChatAdapter.notifyDataSetChanged();
        }

        // Theme DS header (WkStripMiniHeader handles its own theming via DS tokens)
        updateUIForMode();
        // Reply bar is floating with no background — no theming needed
        // Theme input area
        if (mInputBar != null) {
            
        }
    }

    // ============================================================================================
    // INPUT & SEND
    // ============================================================================================

    /**
     * Reads text from InternalChatInput, sends it as a user message via KeyboardSwitcher.
     * Called by both the send button and Enter key.
     */
    private void sendChatMessage(CharSequence submittedText) {
        if (mChatInput == null) return;
        String text = submittedText != null ? submittedText.toString().trim() : "";
        if (text.isEmpty()) return;

        // JourneyTracer: AI chat message sent
        String chatTraceId = JourneyTracer.start(JourneyTracer.Journey.AI_CHAT);
        JourneyTracer.setCurrentSmartReplyTrace(chatTraceId);
        try {
            org.json.JSONObject dataIn = new org.json.JSONObject();
            dataIn.put("message_length", text.length());
            dataIn.put("session_id", currentSessionId);
            JourneyTracer.step(chatTraceId, "CHAT_MESSAGE_SENT", dataIn, null, "user sent message");
        } catch (Exception ignored) {}

        // Create session in Room DB if first message, then save the message in callback
        if (currentSessionId == -1 && unifiedSessionManager != null) {
            String title = UnifiedChatSessionManager.buildTitle("keyboard", text);
            final String msgText = text;
            unifiedSessionManager.createSession(
                UnifiedChatSessionManager.SOURCE_KEYBOARD,
                UnifiedChatSessionManager.ICON_KEYBOARD,
                title,
                sessionId -> {
                    currentSessionId = sessionId;
                    Log.d(TAG, "Created keyboard session: " + sessionId);
                    // Save first message inside callback (avoids race condition)
                    unifiedSessionManager.addMessage(sessionId, "user", msgText, "text");
                }
            );
        } else if (currentSessionId > 0 && unifiedSessionManager != null) {
            // Subsequent messages: session already exists
            unifiedSessionManager.addMessage(currentSessionId, "user", text, "text");
        }

        // Update header to show context
        currentSessionTitle = text.length() > 35 ? text.substring(0, 32) + "..." : text;
        currentSource = "keyboard";
        updateHeaderForActiveChat();

        // Brief rotation animation on send
        if (mSendBtn != null) {
            mSendBtn.animate()
                .rotation(360f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .withEndAction(() -> mSendBtn.setRotation(0f))
                .start();
        }

        // Clear input immediately for responsiveness
        mChatInput.clear();
        if (mSendBtn != null) mSendBtn.setAlpha(0.4f);

        // Full takeover: AI chat replaces QWERTY completely
        setUIState(STATE_AI_VIEW);

        // Route through follow-up path (skips view transition since setUIState already handled it)
        KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        if (switcher != null) {
            try {
                switcher.performAiActionFollowUp(KeyboardSwitcher.AiAction.AI_CHAT, text);
            } catch (NullPointerException e) {
                Log.w(TAG, "KeyboardSwitcher not fully initialized; skipping AI follow-up dispatch", e);
            }
        }
    }

    /** Get the InternalChatInput for key routing registration */
    public InternalChatInput getChatInput() {
        return mChatInput;
    }

    // ============================================================================================
    // PUBLIC API FOR MANAGING THE CHAT
    // ============================================================================================

    /**
     * Ensure a Room session is open and messages are loaded.
     * If a session already exists for the active contact, restores it.
     * Otherwise starts a fresh empty session.
     * Used when AI Chat needs to be shown (e.g., after screenshot analysis).
     */
    public void ensureSessionOpen() {
        if (!sessionOpened) {
            sessionOpened = true;
            openChatSession();
        }
        // If chat items are empty, the session will be populated by loadSessionMessages()
        // via the openChatSession() callback
        if (mChatItems.isEmpty()) {
            mainThread(() -> {
                mChatAdapter.resetAnimationState();
                mChatAdapter.notifyDataSetChanged();
            });
        }
    }

    public void startNewSession(List<ChatItem> initialItems) {
        // P4: Open/resume Room session on first call
        if (!sessionOpened) {
            sessionOpened = true;
            openChatSession();
        }
        mainThread(() -> {
            Log.d(TAG, "startNewSession CALLED. Clearing " + mChatItems.size() + " items.");
            mChatItems.clear();
            mChatItems.addAll(initialItems);
            for (ChatItem item : initialItems) {
                Log.d(TAG, "startNewSession: Adding item of type -> " + item.getClass().getSimpleName());
            }
            mChatAdapter.resetAnimationState();
            mChatAdapter.notifyDataSetChanged();
            mChatRecyclerView.scrollToPosition(mChatItems.size() - 1);
            Log.d(TAG, "New session started with " + initialItems.size() + " items.");
            // Auto-save conversation
            if (mChatPersistence != null) {
                mChatPersistence.saveConversation(mChatItems);
            }
        });
    }

    public void addItem(ChatItem item) {
        mainThread(() -> {
            Log.d(TAG, "addItem CALLED. Adding item of type -> " + item.getClass().getSimpleName());
            mChatItems.add(item);
            trimChatHistory();
            mChatAdapter.notifyItemInserted(mChatItems.size() - 1);
            mChatRecyclerView.scrollToPosition(mChatItems.size() - 1);
            Log.d(TAG, "Added item: " + item.getClass().getSimpleName());
            if (mCurrentState == STATE_AI_VIEW) updateHeaderForActiveChat();
            if (item instanceof AiMessage) {
                setInputDisabled(false);
            }
            // Auto-save conversation
            if (mChatPersistence != null) {
                mChatPersistence.saveConversation(mChatItems);
            }
            // P4: Persist AI responses to Room DB for session restore
            saveItemToRoom(item);
        });
    }

    public void replaceLastItem(ChatItem newItem) {
        mainThread(() -> {
            if (!mChatItems.isEmpty()) {
                int lastIndex = mChatItems.size() - 1;
                Log.d(TAG, "replaceLastItem CALLED. Replacing item at " + lastIndex + " with -> " + newItem.getClass().getSimpleName());
                mChatItems.set(lastIndex, newItem);
                mChatAdapter.notifyItemChanged(lastIndex);
                Log.d(TAG, "Replaced last item with type: " + newItem.getClass().getSimpleName());
                // Auto-save conversation
                if (mChatPersistence != null) {
                    mChatPersistence.saveConversation(mChatItems);
                }
                // P4: Persist AI responses to Room DB for session restore
                saveItemToRoom(newItem);
            } else {
                Log.w(TAG, "replaceLastItem CALLED, but list is empty.");
            }
        });
    }

    public void removeLastItem() {
        mainThread(() -> {
            if (!mChatItems.isEmpty()) {
                int lastIndex = mChatItems.size() - 1;
                Log.d(TAG, "removeLastItem CALLED. Removing item at " + lastIndex + " of type " + mChatItems.get(lastIndex).getClass().getSimpleName());
                mChatItems.remove(lastIndex);
                mChatAdapter.notifyItemRemoved(lastIndex);
                Log.d(TAG, "Removed last item.");
            } else {
                Log.w(TAG, "removeLastItem CALLED, but list is empty.");
            }
        });
    }

    public void showLoadingAndClear() {
        mainThread(() -> {
            Log.d(TAG, "showLoadingAndClear CALLED. Clearing list and adding Loading item.");
            mChatItems.clear();
            mChatItems.add(Loading.INSTANCE);
            mChatAdapter.resetAnimationState();
            mChatAdapter.notifyDataSetChanged();
            setInputDisabled(true);
        });
    }

    /** Disable input bar during loading/analyzing states (K7/K8). */
    public void setInputDisabled(boolean disabled) {
        if (mInputBar != null) {
            if (mInputBar != null) mInputBar.setDisabled(disabled);
        }
        if (mChatInput != null) {
            mChatInput.setEnabled(!disabled);
            if (disabled) {
                mChatInput.setPlaceholderText("Waiting for AI\u2026");
            } else {
                mChatInput.setPlaceholderText("Follow up\u2026");
            }
        }
        if (mSendBtn != null) {
            mSendBtn.setEnabled(!disabled);
            mSendBtn.setAlpha(disabled ? 0.3f : 1.0f);
        }
    }

    public void clearSession() {
        mainThread(() -> {
            Log.d(TAG, "clearSession CALLED. Clearing " + mChatItems.size() + " items.");
            mChatItems.clear();
            mChatAdapter.resetAnimationState();
            mChatAdapter.notifyDataSetChanged();
            // Deactivate and clear input
            if (mChatInput != null) {
                mChatInput.deactivate();
            }
            // Clear persisted conversation
            if (mChatPersistence != null) {
                mChatPersistence.clearConversation();
            }
            // Reset capture button to unhighlighted state
            setCaptureButtonHighlighted(false);
            // SILENT state reset — do NOT call setUIState() here.
            // When clearSession() is called from the close button, showKeyboardView()
            // has already switched KeyboardSwitcher back to QWERTY mode.
            // Calling setUIState(STATE_SESSIONS_LIST) triggers showSessionsListState()
            // which calls showAiChatView() and UNDOES the keyboard restoration.
            mCurrentState = STATE_SESSIONS_LIST;
            // Silently hide all child views (same as setUIState hide-all block, but
            // WITHOUT triggering any state-specific method like showSessionsListState)
            if (mChatRecyclerView != null) mChatRecyclerView.setVisibility(View.GONE);
            if (mInputBar != null) mInputBar.setVisibility(View.GONE);
            if (mReplyBarView != null) mReplyBarView.setVisibility(View.GONE);
            if (mReplyTriggerBtn != null) mReplyTriggerBtn.setVisibility(View.GONE);
            if (mSessionListContainer != null) mSessionListContainer.setVisibility(View.GONE);
            if (mInitialProgressBar != null) mInitialProgressBar.setVisibility(View.GONE);
            // Reset Room DB session tracking
            currentSessionId = -1;
            currentSessionTitle = "";
            // Reset session flag so next open triggers openChatSession() + NLS injection
            sessionOpened = false;
            Log.d(TAG, "Session cleared (silent reset, no setUIState).");
        });
    }

    /**
     * Try to restore the last persisted conversation.
     * Returns true if a conversation was restored, false if empty.
     */
    public boolean tryRestoreConversation() {
        if (mChatPersistence == null || !mChatPersistence.hasPersistedConversation()) {
            return false;
        }
        List<ChatItem> restored = mChatPersistence.loadConversation();
        if (restored.isEmpty()) return false;

        mainThread(() -> {
            mChatItems.clear();
            mChatItems.addAll(restored);
            mChatAdapter.resetAnimationState();
            mChatAdapter.notifyDataSetChanged();
            mChatRecyclerView.scrollToPosition(mChatItems.size() - 1);
            Log.d(TAG, "Restored " + restored.size() + " messages from persistence");
        });
        return true;
    }

    /**
     * Trims chat history to MAX_CHAT_ITEMS, removing oldest items first.
     * Must be called on the UI thread while mChatItems is being mutated.
     */
    private void trimChatHistory() {
        while (mChatItems.size() > MAX_CHAT_ITEMS) {
            mChatItems.remove(0);
        }
    }

    private void mainThread(Runnable runnable) {
        Handler h = getHandler();
        if (h != null) {
            h.post(runnable);
        } else {
            new Handler(Looper.getMainLooper()).post(runnable);
        }
    }

    public void notifyItemChanged(int position) {
        mainThread(() -> {
            if (mChatAdapter != null && position >= 0 && position < mChatItems.size()) {
                mChatAdapter.notifyItemChanged(position);
                Log.d(TAG, "Notified item changed at position: " + position);
            } else {
                Log.w(TAG, "notifyItemChanged called with invalid position: " + position);
            }
        });
    }

    public void hideInitialLoading() {
        mainThread(() -> {
            if (mInitialProgressBar != null) {
                mInitialProgressBar.setVisibility(View.GONE);
            }
        });
    }

    // ============================================================================================
    // BUILD 7.0: SCREEN CAPTURE
    // ============================================================================================

    /**
     * Build 7.0 P3: Add capture button to header bar, before the close button.
     * Styled as a text button consistent with the mode tabs.
     */
    private void setupCaptureButton() { }

    public void setCaptureButtonEnabled(boolean enabled) {
        if (mCaptureBtn != null) {
            mCaptureBtn.setEnabled(enabled);
            mCaptureBtn.setAlpha(enabled ? 1.0f : 0.4f);
            Log.d(TAG, "[AI_CHAT] Capture button enabled=" + enabled);
        }
    }

    // ============================================================================================
    // P4: SESSION PERSISTENCE + NLS CONTEXT
    // ============================================================================================

    /**
     * P4: Open/resume a chat session for the active conversation.
     * Called when AI Chat becomes visible.
     */
    private void openChatSession() {
        ConversationMatcher.ContactMatch contact =
                ConversationMatcher.getInstance().getActiveContact();

        if (contact != null) {
            // Read-only fetch: only resume if a session already exists for this contact.
            // Never creates a blank row — session creation is lazy, deferred to
            // sendChatMessage() and onScreenshotAnalysisReceived().
            sessionRepo.getSessionByConversationKey(
                contact.conversationKey,
                new SessionRepository.SessionCallback() {
                    @Override
                    public void onSession(ChatSession session) {
                        stateManager.setActiveSession(session);
                        loadSessionMessages(session.id);
                        injectNlsContext(session.id, contact);
                        stateManager.setChatOpen(true);
                        Log.d(TAG, "[AI_CHAT] Session resumed: #" + session.id);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "[AI_CHAT] Error resuming session: " + error);
                        stateManager.setChatOpen(true);
                    }
                });
        } else {
            stateManager.setChatOpen(true);
            Log.d(TAG, "[AI_CHAT] Opened without contact context");
        }
    }

    private void loadSessionMessages(long sessionId) {
        sessionRepo.getMessages(sessionId, messages -> {
            stateManager.setMessages(messages);
            Log.d(TAG, "[AI_CHAT] Loaded " + messages.size() + " messages for session #" + sessionId);

            // Convert Room ChatMessages → ChatItems for display
            List<ChatItem> restoredItems = new ArrayList<>();
            for (project.witty.keys.app.database.ChatMessage msg : messages) {
                if (msg.content == null || msg.content.isEmpty()) continue;
                // Skip nls_context — fresh NLS will be injected by injectNlsContext()
                if ("nls_context".equals(msg.type)) continue;

                switch (msg.role) {
                    case "user":
                        restoredItems.add(new UserMessage(msg.content, msg.timestamp));
                        break;
                    case "assistant":
                        restoredItems.add(new AiMessage(msg.content, CtaType.REPLY_COPY, msg.timestamp));
                        break;
                    case "context":
                    case "system":
                        if ("screenshot_analysis".equals(msg.type)) {
                            if (msg.screenshotId != null) {
                                WittyKeysDatabase db = WittyKeysDatabase.getInstance(getContext());
                                SessionScreenshot ss = db.screenshotDao().getById(msg.screenshotId);
                                if (ss != null && ss.filePath != null) {
                                    restoredItems.add(new ScreenshotMessage(
                                            ss.filePath, null, ss.width, ss.height));
                                }
                            }
                            restoredItems.add(new AiMessage(msg.content, CtaType.REPLY_COPY, msg.timestamp));
                        }
                        break;
                    default:
                        break;
                }
            }

            if (!restoredItems.isEmpty()) {
                mainThread(() -> {
                    // Add session banner first
                    SessionBannerMessage banner = new SessionBannerMessage(restoredItems.size());
                    mChatItems.add(0, banner);
                    // Add restored items after banner
                    mChatItems.addAll(1, restoredItems);
                    // Tell adapter how many items are restored (banner + messages)
                    mChatAdapter.setRestoredItemCount(1 + restoredItems.size());
                    mChatAdapter.notifyDataSetChanged();
                    mChatRecyclerView.scrollToPosition(mChatItems.size() - 1);
                    Log.d(TAG, "[AI_CHAT] Restored " + restoredItems.size()
                            + " messages from session #" + sessionId);
                });
            }
        });
    }

    private void saveUserMessage(String content) {
        if (currentSessionId <= 0 || unifiedSessionManager == null) return;
        unifiedSessionManager.addMessage(currentSessionId, "user", content, "text");
    }

    private void saveAssistantMessage(String content) {
        if (currentSessionId <= 0 || unifiedSessionManager == null) return;
        unifiedSessionManager.addMessage(currentSessionId, "assistant", content, "text");
    }

    /**
     * P4: Save a ChatItem to Room DB for session persistence.
     * Handles AiMessage, ErrorMessage — UserMessage is saved separately by saveUserMessage().
     */
    private void saveItemToRoom(ChatItem item) {
        if (currentSessionId <= 0) return;
        long sessionId = currentSessionId;

        if (item instanceof AiMessage) {
            String content = ((AiMessage) item).getMarkdownText();
            if (content != null && !content.isEmpty()) {
                saveAssistantMessage(content);
            }
        }
        // Other types (Loading, Error, NlsBanner) are transient — don't persist
    }

    /**
     * P4: Inject last 10 NLS messages as context when AI Chat opens.
     */
    private void injectNlsContext(long sessionId, ConversationMatcher.ContactMatch contact) {
        try {
            NlsMessageBuffer buffer = NlsMessageBuffer.getInstance();
            List<String> recentMessages = buffer.getRecentMessages(contact.conversationKey, 10);

            if (recentMessages != null && !recentMessages.isEmpty()) {
                // Build NLS entries for the banner UI
                List<NlsBannerMessage.NlsEntry> entries = new ArrayList<>();
                StringBuilder nlsText = new StringBuilder();
                nlsText.append("Recent messages from ").append(contact.contactName).append(":\n");
                for (String msg : recentMessages) {
                    nlsText.append("- ").append(msg).append("\n");
                    // msg is "sender: text" from NlsMessageBuffer — split to avoid duplicating sender
                    int colonIdx = msg.indexOf(": ");
                    String sender = colonIdx > 0 ? msg.substring(0, colonIdx) : contact.contactName;
                    String text = colonIdx > 0 ? msg.substring(colonIdx + 2) : msg;
                    entries.add(new NlsBannerMessage.NlsEntry(sender, text));
                }

                // Add NLS banner to RecyclerView
                NlsBannerMessage banner = new NlsBannerMessage(contact.contactName, entries);
                addItem(banner);

                String content = nlsText.toString().trim();
                int tokens = contextManager.estimateTokens(content);

                sessionRepo.addMessage(sessionId, "context", content, "nls_context", tokens, null,
                    new SessionRepository.MessageCallback() {
                        @Override
                        public void onMessage(ChatMessage msg) {
                            stateManager.addMessage(msg);
                            Log.d(TAG, "[AI_CHAT] NLS context injected: "
                                    + recentMessages.size() + " messages");
                        }

                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "[AI_CHAT] NLS injection error (non-fatal): " + error);
                        }
                    });
            } else {
                Log.d(TAG, "[AI_CHAT] No NLS messages to inject for contact");
            }
        } catch (Exception e) {
            Log.w(TAG, "[AI_CHAT] NLS context injection failed (non-fatal): " + e.getMessage());
        }
    }

    // ============================================================================================
    // P4: SCREENSHOT ANALYSIS (replaces P3 stubs)
    // ============================================================================================

    /**
     * Called immediately after screenshot is captured (before analysis starts).
     * Shows AI view + analyzing indicator without waiting for Claude Vision.
     */
    public void onScreenshotCaptured(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            Log.w(TAG, "[AI_CHAT] Screenshot capture returned no path — permission denied or failed, ignoring");
            return;
        }
        Log.d(TAG, "[AI_CHAT] Screenshot captured — showing analyzing state");
        currentSessionId = -1;
        sessionOpened = false;
        mainThread(() -> {
            mChatItems.clear();
            if (mChatAdapter != null) mChatAdapter.notifyDataSetChanged();
            // Add screenshot thumbnail (user side) + analyzing indicator (AI side) together
            // in one block so the adapter sees both before any scroll happens.
            mChatItems.add(new ScreenshotMessage(imagePath, null, 0, 0));
            mChatItems.add(new project.witty.keys.keyboard.AiChat.AnalyzingMessage());
            if (mChatAdapter != null) mChatAdapter.notifyDataSetChanged();
            setUIState(STATE_AI_VIEW);
            // Scroll to top so the screenshot thumbnail is visible, not the analyzing bubble.
            if (mChatRecyclerView != null) mChatRecyclerView.scrollToPosition(0);
            setInputDisabled(true);
        });
    }

    /**
     * Handle screenshot analysis result from ScreenCaptureService.
     * Replaces the AnalyzingMessage with the screenshot + analysis.
     */
    public void onScreenshotAnalysisReceived(String imagePath, String analysis) {
        Log.d(TAG, "[AI_CHAT] Screenshot analysis received");
        Log.d(TAG, "[AI_CHAT] Screenshot analysis received, sessionId=" + currentSessionId);
        if (analysis == null || analysis.isEmpty()) {
            Log.w(TAG, "[AI_CHAT] Empty screenshot analysis, ignoring");
            return;
        }

        final String finalImagePath = imagePath;
        final String finalAnalysis = analysis;
        restoreAiFollowUpInput();

        Runnable showInUi = () -> mainThread(() -> {
            // Replace AnalyzingMessage with AI analysis bubble (left side)
            AiMessage analysisMsg = new AiMessage(finalAnalysis, CtaType.REGENERATE_COPY_REPLY);
            boolean replaced = false;
            for (int i = mChatItems.size() - 1; i >= 0; i--) {
                if (mChatItems.get(i) instanceof AnalyzingMessage) {
                    mChatItems.set(i, analysisMsg);
                    if (mChatAdapter != null) mChatAdapter.notifyItemChanged(i);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                mChatItems.add(analysisMsg);
                if (mChatAdapter != null) mChatAdapter.notifyItemInserted(mChatItems.size() - 1);
            }
            if (mChatRecyclerView != null) mChatRecyclerView.scrollToPosition(mChatItems.size() - 1);
            if (mCurrentState != STATE_AI_VIEW) setUIState(STATE_AI_VIEW);
            restoreAiFollowUpInput();
        });

        if (currentSessionId <= 0 && unifiedSessionManager != null) {
            // No session open — create one for this screenshot
            String title = UnifiedChatSessionManager.buildTitle(
                UnifiedChatSessionManager.SOURCE_KEYBOARD, "Screenshot analysis");
            unifiedSessionManager.createSession(
                UnifiedChatSessionManager.SOURCE_KEYBOARD,
                UnifiedChatSessionManager.ICON_KEYBOARD,
                title,
                sessionId -> {
                    currentSessionId = sessionId;
                    sessionOpened = true;
                    unifiedSessionManager.addMessageWithScreenshot(sessionId, "system",
                        finalAnalysis, "screenshot_analysis", finalImagePath);
                    showInUi.run();
                });
        } else if (currentSessionId > 0 && unifiedSessionManager != null) {
            unifiedSessionManager.addMessageWithScreenshot(currentSessionId, "system",
                finalAnalysis, "screenshot_analysis", finalImagePath);
            showInUi.run();
        } else {
            // Fallback: show in UI only
            showInUi.run();
        }
    }

    public void onScreenshotAnalysisError(String error) {
        Log.e(TAG, "[AI_CHAT] Screenshot analysis error: " + error);
        mainThread(() -> {
            removeLastAnalyzingMessage();
            mChatItems.add(new ErrorMessage(
                    error != null && !error.isEmpty()
                            ? error
                            : "Screenshot analysis failed. Try again.",
                    null));
            if (mChatAdapter != null) mChatAdapter.notifyDataSetChanged();
            if (mCurrentState != STATE_AI_VIEW) setUIState(STATE_AI_VIEW);
            restoreAiFollowUpInput();
        });
    }

    private void restoreAiFollowUpInput() {
        mainThread(() -> {
            setInputDisabled(false);
            setupInputAsTapTarget();
            if (mChatInput != null) {
                mChatInput.setPlaceholderText("Follow up\u2026");
            }
        });
    }

    private void removeLastAnalyzingMessage() {
        for (int i = mChatItems.size() - 1; i >= 0; i--) {
            if (mChatItems.get(i) instanceof AnalyzingMessage) {
                mChatItems.remove(i);
                if (mChatAdapter != null) mChatAdapter.notifyItemRemoved(i);
                return;
            }
        }
    }

    // ============================================================================================
    // P4 UI: Debug broadcast helpers for golden pipeline (AC11–AC13)
    // ============================================================================================

    /**
     * Add a screenshot message to the chat (for AC11 golden state).
     * In production, this is called by onScreenshotAnalysisReceived().
     * For debug/golden pipeline, called via broadcast.
     */
    public void addScreenshotMessage(String imagePath, String analysis, int width, int height) {
        ScreenshotMessage msg = new ScreenshotMessage(imagePath, analysis, width, height);
        mainThread(() -> {
            mChatItems.add(msg);
            mChatAdapter.submitList(new java.util.ArrayList<>(mChatItems));
            mChatRecyclerView.scrollToPosition(mChatItems.size() - 1);
        });
    }

    /**
     * Add an NLS context banner to the chat (for AC12 golden state).
     * In production, this is called by injectNlsContext().
     * For debug/golden pipeline, called via broadcast.
     */
    public void addNlsBanner(String contactName, String messagesJson) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(messagesJson);
            java.util.List<NlsBannerMessage.NlsEntry> entries = new java.util.ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                entries.add(new NlsBannerMessage.NlsEntry(
                    obj.optString("sender", ""),
                    obj.optString("text", "")));
            }
            NlsBannerMessage msg = new NlsBannerMessage(contactName, entries);
            mainThread(() -> {
                mChatItems.add(msg);
                mChatAdapter.submitList(new java.util.ArrayList<>(mChatItems));
                mChatRecyclerView.scrollToPosition(mChatItems.size() - 1);
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse NLS banner JSON", e);
        }
    }

    /**
     * Add a session resumed banner to the chat (for AC13 golden state).
     * In production, this is called by loadSessionMessages().
     * For debug/golden pipeline, called via broadcast.
     */
    public void addSessionBanner(int messageCount) {
        SessionBannerMessage msg = new SessionBannerMessage(messageCount);
        mainThread(() -> {
            mChatItems.add(msg);
            mChatAdapter.submitList(new java.util.ArrayList<>(mChatItems));
            mChatRecyclerView.scrollToPosition(mChatItems.size() - 1);
        });
    }

    // ============================================================================================
    // NAVIGATION BAR PADDING (same pattern as EmojiKeyboard)
    // ============================================================================================

    /**
     * Update navigation bar padding dynamically.
     * Called by KeyboardSwitcher when the view is attached to the window.
     */
    public void updateNavigationBarPadding() {
        int newNavBarHeight = NavigationBarHelper.getSafeBottomPadding(getContext());
        if (newNavBarHeight != mNavigationBarHeight) {
            mNavigationBarHeight = newNavBarHeight;
            // Re-apply state to pick up the new padding
            if (mCurrentState == STATE_AI_VIEW) {
                int bottomPad = mNavigationBarHeight > 0 ? mNavigationBarHeight : 90;
                setPadding(0, 0, 0, bottomPad);
            }
        }
    }

    public void setCaptureButtonHighlighted(boolean highlighted) {
        if (mCaptureBtn != null) {
            mCaptureBtn.setAlpha(highlighted ? 1.0f : 0.5f);
        }
    }

    public void showAnalyzingMessage() {
        mainThread(() -> {
            AnalyzingMessage msg = new AnalyzingMessage();
            mChatItems.add(msg);
            mChatAdapter.notifyItemInserted(mChatItems.size() - 1);
            mChatRecyclerView.scrollToPosition(mChatItems.size() - 1);
            setInputDisabled(true);
        });
    }


    private void triggerScreenCapture() {
        try {
            KeyboardSwitcher.getInstance().startScreenCapture();
        } catch (Exception e) {
            Log.e(TAG, "[AI_CHAT] Error triggering screen capture", e);
        }
    }

}
