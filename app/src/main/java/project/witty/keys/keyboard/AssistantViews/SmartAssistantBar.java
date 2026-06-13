package project.witty.keys.keyboard.AssistantViews;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.MotionEvent;
import android.widget.PopupWindow;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import project.witty.keys.keyboard.KeyboardTheme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import project.witty.keys.R;
import project.witty.keys.app.helpers.ActionTracker;
import project.witty.keys.app.helpers.JourneyTracer;
import project.witty.keys.app.utils.DailyUsageTracker;
import project.witty.keys.app.utils.ToneData;
import project.witty.keys.app.AuthenticationActivity;
import project.witty.keys.app.SubscriptionListingActivity;
import project.witty.keys.app.entities.Subscription;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.DemoLogger;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.app.helpers.Trie;
import project.witty.keys.app.tutorial.TutorialManager;
import project.witty.keys.api.ClaudeApi;
import project.witty.keys.app.context.AccessibilityPromptTracker;
import project.witty.keys.app.context.ConversationMatcher;
import project.witty.keys.app.context.NlsMessageBuffer;
import project.witty.keys.app.context.ReplyCache;
import project.witty.keys.app.context.ReplyGenerator;
import project.witty.keys.app.context.ReplyPrecomputeManager;
import project.witty.keys.app.context.ReplyValidator;
import project.witty.keys.app.overlay.OverlayServiceManager;
import project.witty.keys.keyboard.Themeable;
import project.witty.keys.debug.TestModeController;
import project.witty.keys.keyboard.internal.InternalInputView;
import project.witty.keys.latin.LatinIME;
import project.witty.keys.latin.RichInputConnection;

/**
 * SmartAssistantBar - Unified assistant UI for WittyKeys keyboard (Build 6.3)
 *
 * Replaces both UtilityRow and SuggestionRow with a single component.
 * Always displays 2 rows to prevent UI jumping.
 *
 * EXPANDED state: AI buttons (Row 1) + Smart replies (Row 2)
 * COLLAPSED state: Predictions + Expand CTA (Row 1) + Tone chips + Context actions (Row 2)
 */
public class SmartAssistantBar extends FrameLayout implements Themeable, SmartAssistantBarManager.SabActionCallback {

    private static final String TAG = "SmartAssistantBar";
    private static final int HIGHLIGHT_COLOR_GOLD = 0xFFFFD700;

    // Master switch: test mode controlled by TestModeController (defaults to OFF = production).
    // Only activated by DebugSABController during golden screenshot automation.
    private SmartAssistantBarManager sabManager;
    private int debugStateIndex = 0; // Phase C: Debug state cycling
    // MV tracking removed (Build 7.1)

    // ========== STATES ==========
    public enum BarState {
        EXPANDED,   // AI buttons visible, smart replies or hint in row 2
        COLLAPSED   // Predictions visible, tone chips + context actions in row 2
    }

    // Phase 2: Row 2 State (which Row 2 content is visible)
    public enum Row2State {
        SMART_REPLIES,      // Default - reply chips
        TONE_PICKER,        // 21 tone chips + Custom
        TONE_ACTIVE,        // Pinned tone + suggestions + regen
        LANG_PICKER,        // 9 language flags + Custom
        CUSTOM_MODE,        // Prompt input + Generate + Cancel
        SHIMMER_LOADING,    // 3 shimmer chips (API loading)
        ACC_PROMPT,         // Accessibility enable prompt
        COLLAPSED,          // Context chips (typing state)
        CONTACT_PICKER      // Contact chips when confidence < 80%
    }

    private BarState currentState = BarState.EXPANDED;
    private Row2State currentRow2State = Row2State.SMART_REPLIES;

    // ========== CONTEXT ==========
    private final Context context;
    private LatinIME mLatinIme;
    private TutorialManager tutorialManager;

    public void setTutorialManager(TutorialManager tm) {
        this.tutorialManager = tm;
    }

    // ========== ROW 1 VIEWS ==========
    private LinearLayout row1Container;

    // Always visible anchors (emoji TextViews in XML)
    private TextView dictationButton;
    private TextView voiceButton;

    // EXPANDED content (emoji TextViews in XML)
    private LinearLayout aiButtonsContainer;
    private TextView aiChatButton;
    private TextView translateButton;
    private TextView grammarButton;
    private TextView toneButton;
    private TextView screenReadButton;

    // COLLAPSED content
    private LinearLayout predictionsContainer;
    private TextView prediction1, prediction2, prediction3;
    private View predictionSeparator1, predictionSeparator2;

    // Phase 3: Brain switch + collapse/expand buttons (emoji TextViews in XML)
    private View brainSwitchButton;
    private TextView collapseButton;
    private TextView expandButton;

    // ========== ROW 2 VIEWS ==========
    private FrameLayout row2Container;

    // EXPANDED content (HorizontalScrollView)
    private View smartRepliesScroll;
    private LinearLayout smartRepliesContainer;
    private TextView smartReply1, smartReply2, smartReply3;
    private TextView customCta;
    private TextView moreCta;
    private TextView hintText;

    // COLLAPSED content (HorizontalScrollView)
    private View collapsedRow2Scroll;
    private LinearLayout collapsedRow2Container;
    private TextView smartToneChip;
    private TextView grammarFixChip, translateChip;
    private TextView moreCtaCollapsed;

    // CUSTOM MODE content (transforms Row 2)
    private View customModeContainer;
    private TextView customPromptInput;  // Legacy — kept for old layout compat
    private TextView generateButton;  // XML uses TextView, not Button
    private TextView cancelCustomButton;  // XML uses TextView, not ImageButton
    private boolean isCustomModeActive = false;

    // NEW: Internal input for custom mode — types inside keyboard, not host app
    private String pendingTransformationText;  // Original text saved when entering custom mode

    // Phase 9: ACCESSIBILITY PROMPT content (transforms Row 2)
    private View accessibilityPromptContainer;
    private TextView accessibilityPromptText;
    private TextView accessibilityEnableButton;  // XML uses TextView, not Button
    private boolean isAccessibilityPromptActive = false;
    private PopupWindow accessibilityConsentPopup;

    // Phase 2: New Row 2 layouts (from includes)
    private View tonePicker;
    private View toneActive;
    private View langPicker;
    private View shimmerRow2;
    private View clipButton;  // Row 1 clipboard button

    // Phase 2: Tone picker elements
    private ViewGroup toneChipsContainer;
    private View pinnedToneChip;
    private TextView pinnedToneText;
    private View closeToneActive;
    private ViewGroup toneSuggestionsContainer;
    private View regenButton;

    // Phase 2: Language picker elements
    private ViewGroup langChipsContainer;

    // Task 2.27: Milestone toast elements
    private View milestoneToast;
    private TextView milestoneEmoji;
    private TextView milestoneTitle;
    private TextView milestoneSubtitle;
    private View milestoneClose;
    private Runnable milestoneAutoHideRunnable;

    // Phase 2B: Track current tone for regen
    private String currentActiveTone;

    // Phase 2: Handler for delayed actions
    private final Handler animationHandler = new Handler(Looper.getMainLooper());

    // Phase 3: Brain blink animation
    private android.animation.ObjectAnimator brainBlinkAnimator;
    private boolean hasPendingNewMessage = false;
    // Brain icon → overlay toggle state
    private boolean isOverlayVisible = false;
    // Task 4: Recent AI actions tracker for dynamic Row 2
    private RecentAiActionsTracker recentActionsTracker;

    // ========== DATA ==========
    private Trie wordTrie;
    private Map<String, String> emojiMap;
    private static final Map<String, List<String>> NEXT_WORDS = new HashMap<>();
    private static final List<String> FALLBACK_DEFAULTS = Arrays.asList("the", "to", "and");

    private List<String> currentSmartReplies = new ArrayList<>();
    private boolean hasContext = false;
    private boolean voiceMode = false;

    // Phase 1: Reply quality validation
    private final ReplyValidator replyValidator = new ReplyValidator();
    private String lastIncomingMessage = null; // For validation context

    // ========== HIGHLIGHT (Tutorial) ==========
    private View currentHighlightedView = null;
    private android.animation.AnimatorSet currentHighlightAnimator = null;
    private String currentHighlightedButtonType = null;
    private Integer originalIconTint = null;

    // ========== CONSTRUCTORS ==========

    public SmartAssistantBar(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public SmartAssistantBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public SmartAssistantBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }

    // ========== PHASE C: DEBUG STATE CYCLING ==========

    public void debugCycleNextState() {
        if (!TestModeController.isTestMode() || sabManager == null) return;

        SabState[] allStates = SabState.values();
        debugStateIndex = (debugStateIndex + 1) % allStates.length;
        SabState nextState = allStates[debugStateIndex];

        Log.d(TAG, "[DEBUG] Switching to state: " + nextState.name() + " (" + (debugStateIndex + 1) + "/" + allStates.length + ")");
        sabManager.switchToState(nextState);

        // Show a toast with state name for easy identification
        android.widget.Toast.makeText(context, nextState.name() + " (" + (debugStateIndex + 1) + "/" + allStates.length + ")", android.widget.Toast.LENGTH_SHORT).show();
    }

    // ========== SAB ACTION CALLBACK IMPLEMENTATION ==========
    // These methods are called by SmartAssistantBarManager when buttons/chips are tapped.

    @Override
    public void onBrainTapped() {
        Log.d(TAG, "[SAB] onBrainTapped → toggleOverlay");
        toggleOverlay();
    }

    @Override
    public void onToneTapped() {
        Log.d(TAG, "[SAB] onToneTapped");
        if (sabManager != null) sabManager.switchToState(SabState.CTA_TONE_SELECT);
    }

    @Override
    public void onGrammarTapped() {
        Log.d(TAG, "[SAB] onGrammarTapped");
        performGrammarFixNew();
    }

    @Override
    public void onTranslateTapped() {
        Log.d(TAG, "[SAB] onTranslateTapped");
        if (sabManager != null) sabManager.switchToState(SabState.CTA_TRANSLATE);
    }

    @Override
    public void onCollapseTapped() {
        Log.d(TAG, "[SAB] onCollapseTapped");
        if (sabManager != null) sabManager.switchToState(SabState.OV_COLLAPSED);
    }

    @Override
    public void onExpandTapped() {
        Log.d(TAG, "[SAB] onExpandTapped");
        if (sabManager != null) sabManager.switchToState(SabState.OV_EXPANDED);
    }

    @Override
    public void onReplyChipTapped(String replyText) {
        Log.d(TAG, "[SAB] onReplyChipTapped: " + replyText.substring(0, Math.min(40, replyText.length())));

        // JourneyTracer: reply chip tapped
        String tapTraceId = JourneyTracer.start(JourneyTracer.Journey.SMART_REPLY);
        try {
            org.json.JSONObject dataIn = new org.json.JSONObject();
            dataIn.put("reply_text_preview", replyText.substring(0, Math.min(40, replyText.length())));
            dataIn.put("action", "chip_tapped");
            JourneyTracer.step(tapTraceId, "REPLY_CHIP_TAPPED", dataIn, null, "user selected reply");
            JourneyTracer.complete(tapTraceId, true);
        } catch (Exception ignored) {}

        commitSmartReplyNew(replyText);
    }

    @Override
    public void onCustomTapped() {
        Log.d(TAG, "[SAB] onCustomTapped");
        if (sabManager != null) sabManager.switchToState(SabState.OV_CUSTOM);
    }

    @Override
    public void onCustomModeEntered(SabState customState) {
        Log.d(TAG, "[SAB] onCustomModeEntered: " + customState);
        String placeholder;
        switch (customState) {
            case CTA_TONE_CUSTOM:
                placeholder = "\uD83C\uDFAD Describe your tone...";
                break;
            case CTA_TRANSLATE_CUSTOM:
                placeholder = "\uD83C\uDF10 Type target language...";
                break;
            default:
                placeholder = "Type custom instruction...";
                break;
        }
        activateInternalInput(placeholder);
    }

    @Override
    public void onMoreTapped() {
        Log.d(TAG, "[SAB] onMoreTapped");
        // More button — currently no-op (MV Modal removed in Build 7.1)
    }

    @Override
    public void onToneChipSelected(String toneName) {
        Log.d(TAG, "[SAB] onToneChipSelected: " + toneName);
        applyToneNew(toneName);
    }

    @Override
    public void onLanguageChipSelected(String language) {
        Log.d(TAG, "[SAB] onLanguageChipSelected: " + language);
        translateTextNew(language);
    }

    @Override
    public void onToneActiveCloseTapped() {
        Log.d(TAG, "[SAB] onToneActiveCloseTapped");
        if (sabManager != null) sabManager.switchToState(SabState.OV_EXPANDED);
    }

    @Override
    public void onRegenTapped() {
        Log.d(TAG, "[SAB] onRegenTapped: re-applying current tone");
        if (currentActiveTone != null) {
            applyToneNew(currentActiveTone);
        }
    }

    @Override
    public void onAiChatTapped() {
        Log.d(TAG, "[SAB] onAiChatTapped");
        if (storedAiChatClickListener != null) {
            storedAiChatClickListener.onClick(this);
        }
    }

    @Override
    public void onDictationTapped() {
        Log.d(TAG, "[SAB] onDictationTapped");
        if (storedDictationClickListener != null) {
            storedDictationClickListener.onClick(this);
        }
    }

    @Override
    public void onAccPromptEnableTapped() {
        Log.d(TAG, "[SAB] onAccPromptEnableTapped: showing consent bottom sheet");
        if (sabManager != null) {
            sabManager.switchToState(SabState.BS_ACC_CONSENT);
        }
    }

    @Override
    public void onDirectToneTapped(String toneName) {
        Log.d(TAG, "[SAB] onDirectToneTapped: " + toneName);
        applyToneNew(toneName);
    }

    @Override
    public void onDirectTranslateTapped(String languageCode) {
        Log.d(TAG, "[SAB] onDirectTranslateTapped: " + languageCode);
        translateTextNew(languageCode);
    }

    @Override
    public void onCustomPromptRerun(String prompt) {
        Log.d(TAG, "[SAB] onCustomPromptRerun: " + prompt.substring(0, Math.min(30, prompt.length())));
        if (sabManager == null) return;
        sabManager.switchToState(SabState.OV_ROW2_LOADING);
        String systemPrompt = "You are a helpful assistant. Generate 4 short reply suggestions based on the user's request. Return ONLY the replies, one per line, no numbering or prefixes.";
        new ClaudeApi().generateReplies(systemPrompt, prompt, new ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!replies.isEmpty() && sabManager != null) {
                        List<SmartAssistantBarManager.ReplyChip> chips = new ArrayList<>();
                        for (String reply : replies) {
                            chips.add(new SmartAssistantBarManager.ReplyChip(reply, null));
                        }
                        sabManager.setReplyChips(chips);
                        sabManager.switchToState(SabState.OV_EXPANDED);
                        // Record the rerun
                        DailyUsageTracker.getInstance(context).recordUsage();
                        ActionTracker.getInstance(context).recordAction(
                            ActionTracker.TYPE_CUSTOM, prompt, "\u270F\uFE0F", prompt);
                        if (sabManager != null) sabManager.refreshDynamicRow2();
                    }
                });
            }

            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.e(TAG, "[SAB] Custom prompt rerun error: " + error);
                    if (sabManager != null) sabManager.switchToState(SabState.OV_EXPANDED);
                });
            }
        });
    }

    @Override
    public void onCustomModeTapped() {
        Log.d(TAG, "[SAB] onCustomModeTapped");
        if (sabManager != null) sabManager.switchToState(SabState.OV_CUSTOM);
    }

    @Override
    public void onCustomGenerateTapped() {
        Log.d(TAG, "[SAB] onCustomGenerateTapped");
        if (sabManager == null) return;

        // Read instruction from InternalInputView (typed inside keyboard)
        InternalInputView inputView = sabManager.getInternalInputView();
        String instruction = (inputView != null) ? inputView.getText() : null;
        if (instruction == null || instruction.trim().isEmpty()) {
            android.widget.Toast.makeText(context, "Type your custom instruction first", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Deactivate internal input now that we have the instruction
        deactivateInternalInput();

        SabState origin = sabManager.getCurrentCustomOrigin();

        if (origin == SabState.CTA_TONE_CUSTOM) {
            // Custom tone: instruction = tone description, pendingText = text to transform
            sabManager.handleCustomGenerate(instruction);
            applyToneWithText(pendingTransformationText, instruction);
        } else if (origin == SabState.CTA_TRANSLATE_CUSTOM) {
            // Custom translate: instruction = target language, pendingText = text to translate
            sabManager.handleCustomGenerate(instruction);
            translateTextWithSource(pendingTransformationText, instruction);
        } else {
            // OV_CUSTOM (general): generate AI replies using instruction as prompt
            // JourneyTracer: custom prompt started
            final String customTraceId = JourneyTracer.start(JourneyTracer.Journey.CUSTOM_PROMPT);
            JourneyTracer.setCurrentSmartReplyTrace(customTraceId);
            try {
                org.json.JSONObject dataIn = new org.json.JSONObject();
                dataIn.put("prompt_length", instruction.length());
                dataIn.put("prompt_preview", instruction.substring(0, Math.min(30, instruction.length())));
                JourneyTracer.step(customTraceId, "CUSTOM_PROMPT_SUBMITTED", dataIn, null,
                    "user submitted custom prompt");
            } catch (Exception ignored) {}

            sabManager.switchToState(SabState.OV_ROW2_LOADING);
            String systemPrompt = "You are a helpful assistant. Generate 4 short reply suggestions based on the user's request. Return ONLY the replies, one per line, no numbering or prefixes.";
            new ClaudeApi().generateReplies(systemPrompt, instruction, new ClaudeApi.ReplyCallback() {
                @Override
                public void onRepliesGenerated(List<String> replies) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!replies.isEmpty() && sabManager != null) {
                            List<SmartAssistantBarManager.ReplyChip> chips = new ArrayList<>();
                            for (String reply : replies) {
                                chips.add(new SmartAssistantBarManager.ReplyChip(reply, null));
                            }
                            sabManager.setReplyChips(chips);
                            sabManager.switchToState(SabState.OV_EXPANDED);
                            Log.d(TAG, "[SAB] Custom generate: " + replies.size() + " replies");

                            // JourneyTracer: custom replies generated
                            try {
                                org.json.JSONObject dataOut = new org.json.JSONObject();
                                dataOut.put("reply_count", replies.size());
                                JourneyTracer.step(customTraceId, "CUSTOM_REPLIES_GENERATED", null, dataOut, "replies received");
                                JourneyTracer.complete(customTraceId, true);
                            } catch (Exception ignored) {}

                            // Record custom action for dynamic Row 2
                            DailyUsageTracker.getInstance(context).recordUsage();
                            ActionTracker.getInstance(context).recordAction(
                                ActionTracker.TYPE_CUSTOM, instruction, "\u270F\uFE0F", instruction);
                            sabManager.refreshDynamicRow2();
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Log.e(TAG, "[SAB] Custom generate error: " + error);
                        if (sabManager != null) sabManager.switchToState(SabState.OV_EXPANDED);
                    });
                }
            });
        }
        pendingTransformationText = null;
    }

    @Override
    public void onCustomCancelTapped() {
        Log.d(TAG, "[SAB] onCustomCancelTapped → deactivate internal input → OV_EXPANDED");
        deactivateInternalInput();
        pendingTransformationText = null;
        if (sabManager != null) {
            sabManager.switchToState(SabState.OV_EXPANDED);
        }
    }

    @Override
    public void onMvReplyFlagged(String replyText) {
        Log.d(TAG, "[SAB] onMvReplyFlagged: " + replyText.substring(0, Math.min(40, replyText.length())));
        ReportHelper.reportContent(context, replyText, ReportHelper.FEATURE_SMART_REPLY);
    }

    @Override
    public void onPredictionTapped(String text) {
        Log.d(TAG, "[SAB] onPredictionTapped: " + text);
        commitSuggestionSmart(text);
    }

    // ========== NEW MANAGER HELPER METHODS ==========

    /**
     * Commit a smart reply to the host app's input field (new manager path).
     * Same logic as old commitSmartReply() but bypasses TestModeController guard.
     */
    private void commitSmartReplyNew(String text) {
        if (text == null || mLatinIme == null) {
            Log.w(TAG, "[SAB] commitSmartReplyNew: null text or mLatinIme");
            return;
        }
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) {
            Log.w(TAG, "[SAB] commitSmartReplyNew: ric null or not connected");
            return;
        }
        Log.d(TAG, "[SAB] commitSmartReplyNew: replacing editor with '" + text.substring(0, Math.min(40, text.length())) + "'");
        DemoLogger.logUserAction(DemoLogger.FLOW_AI_FEATURES, "select_reply", "smart_reply", text);
        // Replace editor text (not append) — Bug #3 fix
        String currentText = ric.getCommitedText();
        int totalLength = currentText != null ? currentText.length() : 0;
        ric.replaceText(0, totalLength, text);
        ric.setSelection(text.length(), text.length());

        // Build 7.0: Invalidate cache since user replied — context has changed
        ConversationMatcher.ContactMatch currentMatch = ConversationMatcher.getInstance().getActiveContact();
        if (currentMatch != null && currentMatch.conversationKey != null) {
            ReplyPrecomputeManager.getInstance(getContext()).invalidateCache(currentMatch.conversationKey);
        }

        AccessibilityPromptTracker tracker = AccessibilityPromptTracker.getInstance(getContext());
        tracker.recordReplyTap();

        // After committing a reply, clear reply chips so Row 2 shows updated LRU actions
        if (sabManager != null) {
            sabManager.setReplyChips(new java.util.ArrayList<>());
            sabManager.refreshDynamicRow2();
            Log.d(TAG, "[SAB] Reply committed, cleared chips, refreshing Row 2 LRU");
        }
    }

    /**
     * Perform grammar fix with Row 2 result preview.
     * Shows grammar active row with shimmer → API call → shows corrected text
     * with word-level diff highlighting → user taps Apply/Copy.
     */
    private void performGrammarFixNew() {
        if (mLatinIme == null) {
            Log.w(TAG, "[SAB] performGrammarFixNew: mLatinIme is null");
            return;
        }
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) return;
        String originalText = ric.getCommitedText();
        if (originalText == null || originalText.trim().isEmpty()) {
            android.widget.Toast.makeText(context, "Please write something first", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // JourneyTracer: grammar fix started
        final String grammarTraceId = JourneyTracer.start(JourneyTracer.Journey.GRAMMAR_FIX);
        JourneyTracer.setCurrentSmartReplyTrace(grammarTraceId);
        try {
            org.json.JSONObject dataIn = new org.json.JSONObject();
            dataIn.put("text_length", originalText.length());
            JourneyTracer.step(grammarTraceId, "GRAMMAR_REQUESTED", dataIn, null, "user tapped grammar");
        } catch (Exception ignored) {}

        // Show grammar active row with shimmer
        if (sabManager != null) {
            sabManager.switchToState(SabState.CTA_GRAMMAR);
            sabManager.configureGrammarActiveRow();
        }

        String systemPrompt = ReplyGenerator.buildGrammarPrompt();
        new ClaudeApi().generateReplies(systemPrompt, originalText, new ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!replies.isEmpty() && sabManager != null) {
                        String corrected = replies.get(0);
                        // Build highlighted text with word-level diff
                        CharSequence highlighted = buildDiffHighlightedText(originalText, corrected);
                        sabManager.showGrammarResult(highlighted);

                        // Record grammar action for dynamic Row 2
                        DailyUsageTracker.getInstance(context).recordUsage();
                        ActionTracker.getInstance(context).recordAction(
                            ActionTracker.TYPE_GRAMMAR, "", "\u2713", "Grammar");
                        if (recentActionsTracker != null) {
                            recentActionsTracker.recordAction("grammar_check", "Grammar", "✅");
                        }
                        sabManager.refreshDynamicRow2();

                        // Wire Apply button
                        View applyBtn = sabManager.getGrammarApplyButton();
                        if (applyBtn != null) {
                            applyBtn.setOnClickListener(v -> {
                                replaceEditorTextNew(corrected);
                                Log.d(TAG, "[SAB] Grammar fix applied via Row 2");
                                sabManager.switchToState(SabState.OV_EXPANDED);
                            });
                        }

                        // Wire Copy button
                        View copyBtn = sabManager.getGrammarCopyButton();
                        if (copyBtn != null) {
                            copyBtn.setOnClickListener(v -> {
                                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Grammar Fix", corrected));
                                    android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show();
                                }
                                Log.d(TAG, "[SAB] Grammar fix copied to clipboard");
                            });
                        }

                        // JourneyTracer: grammar applied
                        try {
                            org.json.JSONObject dataOut = new org.json.JSONObject();
                            dataOut.put("corrections_applied", !corrected.equals(originalText));
                            JourneyTracer.step(grammarTraceId, "GRAMMAR_APPLIED", null, dataOut, "corrections applied");
                            JourneyTracer.complete(grammarTraceId, true);
                        } catch (Exception ignored) {}

                        Log.d(TAG, "[SAB] Grammar fix result shown in Row 2");
                    }
                });
            }

            @Override
            public void onError(String error) {
                // JourneyTracer: grammar error
                JourneyTracer.error(grammarTraceId, "GRAMMAR_ERROR", "api_error", error);
                JourneyTracer.complete(grammarTraceId, false);

                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.e(TAG, "[SAB] Grammar fix error: " + error);
                    if (sabManager != null) sabManager.switchToState(SabState.OV_EXPANDED);
                });
            }
        });
    }

    /**
     * Build a SpannableString of the corrected text with changed words highlighted
     * in accent color. Uses simple word-level diff: split both strings by spaces,
     * compare word by word, highlight mismatches and insertions.
     */
    private CharSequence buildDiffHighlightedText(String original, String corrected) {
        if (original == null || corrected == null) return corrected != null ? corrected : "";
        if (original.equals(corrected)) return corrected;

        String[] origWords = original.split("\\s+");
        String[] corrWords = corrected.split("\\s+");
        int accentColor = ContextCompat.getColor(context, R.color.wk_accent);

        SpannableString spannable = new SpannableString(corrected);

        // Walk through corrected text, tracking character positions
        int pos = 0;
        for (int i = 0; i < corrWords.length; i++) {
            String corrWord = corrWords[i];
            // Find position of this word in corrected string (skip whitespace)
            int wordStart = corrected.indexOf(corrWord, pos);
            if (wordStart < 0) wordStart = pos;
            int wordEnd = wordStart + corrWord.length();

            // Determine if this word differs from original
            boolean isDifferent;
            if (i < origWords.length) {
                isDifferent = !origWords[i].equals(corrWord);
            } else {
                // Extra word in corrected text — it's new
                isDifferent = true;
            }

            if (isDifferent) {
                spannable.setSpan(
                    new ForegroundColorSpan(accentColor),
                    wordStart, wordEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }

            pos = wordEnd;
        }

        return spannable;
    }

    /**
     * Apply tone transformation (new manager path).
     * Shows shimmer → API call → shows tone active state with suggestions.
     */
    private void applyToneNew(String toneName) {
        currentActiveTone = toneName;
        if (mLatinIme == null) return;
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) return;
        String text = ric.getCommitedText();
        if (text == null || text.trim().isEmpty()) {
            android.widget.Toast.makeText(context, "Please write something first", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // JourneyTracer: tone change started
        final String toneTraceId = JourneyTracer.start(JourneyTracer.Journey.TONE_CHANGE);
        JourneyTracer.setCurrentSmartReplyTrace(toneTraceId);
        try {
            org.json.JSONObject dataIn = new org.json.JSONObject();
            dataIn.put("tone", toneName);
            dataIn.put("original_text_length", text.length());
            JourneyTracer.step(toneTraceId, "TONE_SELECTED", dataIn, null,
                "user selected tone: " + toneName);
        } catch (Exception ignored) {}

        // SET active tone name/emoji in manager BEFORE state switch (supports all 21 tones)
        if (sabManager != null) {
            String emoji = ToneData.getEmojiForTone(toneName);
            sabManager.setActiveTone(toneName, emoji != null ? emoji : "😎");
            // CLEAR stale suggestions before starting new API call
            sabManager.setToneSuggestions(new java.util.ArrayList<>());
            Log.d(TAG, "[SAB] Set active tone: " + toneName + ", cleared stale suggestions");
        }

        // Show loading
        if (sabManager != null) sabManager.switchToState(SabState.OV_ROW2_LOADING);

        String systemPrompt = ReplyGenerator.buildTonePrompt(toneName);
        new ClaudeApi().generateReplies(systemPrompt, text, new ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    // Guard: discard stale callback if user selected a different tone
                    if (!toneName.equals(currentActiveTone)) {
                        Log.w(TAG, "[SAB] Discarding stale tone callback: " + toneName + " (current: " + currentActiveTone + ")");
                        return;
                    }
                    Log.d(TAG, "[SAB] Tone applied: " + toneName + " -> " + replies.size() + " suggestions");

                    // JourneyTracer: tone applied
                    try {
                        org.json.JSONObject dataOut = new org.json.JSONObject();
                        dataOut.put("suggestion_count", replies.size());
                        JourneyTracer.step(toneTraceId, "TONE_APPLIED", null, dataOut, "text transformed");
                        JourneyTracer.complete(toneTraceId, true);
                    } catch (Exception ignored) {}

                    if (sabManager != null) {
                        // Push API replies as tone suggestions BEFORE state switch
                        List<SmartAssistantBarManager.ReplyChip> chips = new ArrayList<>();
                        for (String reply : replies) {
                            chips.add(new SmartAssistantBarManager.ReplyChip(reply, null));
                        }
                        sabManager.setToneSuggestions(chips);
                        SabState toneState = mapToneNameToState(toneName);
                        sabManager.switchToState(toneState);
                        // Record tone action for dynamic Row 2
                        DailyUsageTracker.getInstance(context).recordUsage();
                        String emoji = ToneData.getEmojiForTone(toneName);
                        ActionTracker.getInstance(context).recordAction(
                            ActionTracker.TYPE_TONE, toneName,
                            emoji != null ? emoji : "\uD83D\uDCDD", toneName);
                        if (recentActionsTracker != null) {
                            recentActionsTracker.recordAction("tone_" + toneName, toneName, emoji != null ? emoji : "💭");
                        }
                        sabManager.refreshDynamicRow2();
                    }
                });
            }

            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.e(TAG, "[SAB] Tone error: " + error);
                    if (sabManager != null) sabManager.switchToState(SabState.OV_EXPANDED);
                });
            }
        });
    }

    /**
     * Translate text (new manager path).
     * Shows shimmer → API call → replaces editor text → back to expanded.
     */
    private void translateTextNew(String targetLanguage) {
        if (mLatinIme == null) return;
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) return;
        String text = ric.getCommitedText();
        if (text == null || text.trim().isEmpty()) {
            android.widget.Toast.makeText(context, "Please write something first", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // JourneyTracer: translation started
        final String translateTraceId = JourneyTracer.start(JourneyTracer.Journey.TRANSLATION);
        JourneyTracer.setCurrentSmartReplyTrace(translateTraceId);
        try {
            org.json.JSONObject dataIn = new org.json.JSONObject();
            dataIn.put("target_language", targetLanguage);
            dataIn.put("text_length", text.length());
            JourneyTracer.step(translateTraceId, "TRANSLATION_REQUESTED", dataIn, null,
                "translate to " + targetLanguage);
        } catch (Exception ignored) {}

        // Show translate active UI with shimmer (approved design)
        if (sabManager != null) {
            sabManager.switchToState(SabState.CTA_TRANSLATE_ACTIVE);
        }

        String systemPrompt = ReplyGenerator.buildTranslatePrompt(targetLanguage);
        new ClaudeApi().generateReplies(systemPrompt, text, new ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!replies.isEmpty() && sabManager != null) {
                        String translated = replies.get(0);
                        // Show translated text in the translate active row
                        sabManager.showTranslateResult(translated);
                        // Record translate action for dynamic Row 2
                        DailyUsageTracker.getInstance(context).recordUsage();
                        ActionTracker.getInstance(context).recordAction(
                            ActionTracker.TYPE_TRANSLATE, targetLanguage,
                            "\uD83C\uDF10", targetLanguage);
                        if (recentActionsTracker != null) {
                            recentActionsTracker.recordAction("translate_" + targetLanguage, targetLanguage, "🌐");
                        }
                        sabManager.refreshDynamicRow2();
                        // Wire "Apply" button to replace editor text
                        View applyBtn = sabManager.getTranslateApplyButton();
                        if (applyBtn != null) {
                            applyBtn.setOnClickListener(v -> {
                                replaceEditorTextNew(translated);
                                Log.d(TAG, "[SAB] Translate applied: " + targetLanguage);
                                sabManager.switchToState(SabState.OV_EXPANDED);
                            });
                        }
                        // Wire "Copy" button to clipboard
                        View copyBtn = sabManager.getTranslateCopyButton();
                        if (copyBtn != null) {
                            copyBtn.setOnClickListener(v -> {
                                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Translation", translated));
                                    android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show();
                                }
                                Log.d(TAG, "[SAB] Translation copied to clipboard");
                            });
                        }
                        // JourneyTracer: translation applied
                        try {
                            org.json.JSONObject dataOut = new org.json.JSONObject();
                            dataOut.put("translated_length", translated.length());
                            JourneyTracer.step(translateTraceId, "TRANSLATION_APPLIED", null, dataOut, "translated to " + targetLanguage);
                            JourneyTracer.complete(translateTraceId, true);
                        } catch (Exception ignored) {}

                        Log.d(TAG, "[SAB] Translated to " + targetLanguage + " — showing result UI");
                    }
                });
            }

            @Override
            public void onError(String error) {
                // JourneyTracer: translation error
                JourneyTracer.error(translateTraceId, "TRANSLATION_ERROR", "api_error", error);
                JourneyTracer.complete(translateTraceId, false);

                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.e(TAG, "[SAB] Translate error: " + error);
                    if (sabManager != null) sabManager.switchToState(SabState.OV_EXPANDED);
                });
            }
        });
    }

    /**
     * Apply tone with explicit source text and instruction.
     * Used by custom tone mode — source text was saved before entering custom input.
     */
    private void applyToneWithText(String sourceText, String toneInstruction) {
        currentActiveTone = toneInstruction;
        if (sourceText == null || sourceText.trim().isEmpty()) {
            // Fallback: read from editor if no pending text was saved
            if (mLatinIme != null) {
                RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
                if (ric != null && ric.isConnected()) {
                    CharSequence text = ric.getTextBeforeCursor(4096, 0);
                    sourceText = (text != null) ? text.toString() : null;
                }
            }
            if (sourceText == null || sourceText.trim().isEmpty()) {
                android.widget.Toast.makeText(context, "No text to transform", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (sabManager != null) sabManager.switchToState(SabState.OV_ROW2_LOADING);

        String systemPrompt = ReplyGenerator.buildTonePrompt(toneInstruction);
        final String textToTransform = sourceText;
        new ClaudeApi().generateReplies(systemPrompt, textToTransform, new ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.d(TAG, "[SAB] Custom tone applied: " + toneInstruction + " -> " + replies.size() + " suggestions");
                    if (sabManager != null) {
                        List<SmartAssistantBarManager.ReplyChip> chips = new ArrayList<>();
                        for (String reply : replies) {
                            chips.add(new SmartAssistantBarManager.ReplyChip(reply, null));
                        }
                        sabManager.setToneSuggestions(chips);
                        SabState toneState = mapToneNameToState(toneInstruction);
                        sabManager.switchToState(toneState);
                    }
                });
            }

            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.e(TAG, "[SAB] Custom tone error: " + error);
                    if (sabManager != null) sabManager.switchToState(SabState.OV_EXPANDED);
                });
            }
        });
    }

    /**
     * Translate with explicit source text and target language instruction.
     * Used by custom translate mode — source text was saved before entering custom input.
     */
    private void translateTextWithSource(String sourceText, String targetLanguage) {
        if (sourceText == null || sourceText.trim().isEmpty()) {
            // Fallback: read from editor if no pending text was saved
            if (mLatinIme != null) {
                RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
                if (ric != null && ric.isConnected()) {
                    CharSequence text = ric.getTextBeforeCursor(4096, 0);
                    sourceText = (text != null) ? text.toString() : null;
                }
            }
            if (sourceText == null || sourceText.trim().isEmpty()) {
                android.widget.Toast.makeText(context, "No text to translate", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (sabManager != null) {
            sabManager.switchToState(SabState.CTA_TRANSLATE_ACTIVE);
        }

        String systemPrompt = ReplyGenerator.buildTranslatePrompt(targetLanguage);
        final String textToTranslate = sourceText;
        new ClaudeApi().generateReplies(systemPrompt, textToTranslate, new ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!replies.isEmpty() && sabManager != null) {
                        String translated = replies.get(0);
                        sabManager.showTranslateResult(translated);
                        View applyBtn = sabManager.getTranslateApplyButton();
                        if (applyBtn != null) {
                            applyBtn.setOnClickListener(v -> {
                                replaceEditorTextNew(translated);
                                Log.d(TAG, "[SAB] Custom translate applied: " + targetLanguage);
                                sabManager.switchToState(SabState.OV_EXPANDED);
                            });
                        }
                        View copyBtn = sabManager.getTranslateCopyButton();
                        if (copyBtn != null) {
                            copyBtn.setOnClickListener(v -> {
                                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Translation", translated));
                                    android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        Log.d(TAG, "[SAB] Custom translated to " + targetLanguage);
                    }
                });
            }

            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.e(TAG, "[SAB] Custom translate error: " + error);
                    if (sabManager != null) sabManager.switchToState(SabState.OV_EXPANDED);
                });
            }
        });
    }

    // ===== Internal Input Activation/Deactivation =====

    /**
     * Activate internal input mode: save editor text, register with LatinIME,
     * activate InternalInputView in Row 2.
     */
    public void activateInternalInput(String placeholder) {
        if (mLatinIme == null || sabManager == null) return;

        // 1. Capture pending transformation text from host app editor
        // Use getTextBeforeCursor() which reads ACTUAL editor content,
        // not getCommitedText() which only tracks keyboard-committed buffer
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric != null && ric.isConnected()) {
            CharSequence editorText = ric.getTextBeforeCursor(4096, 0);
            pendingTransformationText = (editorText != null) ? editorText.toString() : null;
        }

        // 2. Validate: must have text to transform (for tone/translate modes)
        SabState origin = sabManager.getCurrentCustomOrigin();
        if ((origin == SabState.CTA_TONE_CUSTOM || origin == SabState.CTA_TRANSLATE_CUSTOM)
                && (pendingTransformationText == null || pendingTransformationText.trim().isEmpty())) {
            android.widget.Toast.makeText(context, "Type something first", android.widget.Toast.LENGTH_SHORT).show();
            pendingTransformationText = null;
            // Still activate internal input view (just won't have text to transform)
            // so the UI doesn't end up in a broken state
        }

        // 3. Get InternalInputView and activate it
        InternalInputView inputView = sabManager.getInternalInputView();
        if (inputView != null) {
            if (placeholder != null) {
                inputView.setPlaceholderText(placeholder);
            }
            inputView.activate();

            // 4. Register with LatinIME for key routing
            mLatinIme.setInternalInputTarget(inputView);

            // 5. Set flag to prevent onUserInput() from collapsing OV
            isCustomModeActive = true;

            Log.d(TAG, "[SAB] Internal input activated. Pending text: " +
                    (pendingTransformationText != null ? pendingTransformationText.length() + " chars" : "null"));
        }
    }

    /**
     * Deactivate internal input mode: clear buffer, unregister from LatinIME.
     */
    public void deactivateInternalInput() {
        isCustomModeActive = false;
        if (sabManager != null) {
            InternalInputView inputView = sabManager.getInternalInputView();
            if (inputView != null) {
                inputView.deactivate();
            }
        }
        if (mLatinIme != null) {
            mLatinIme.setInternalInputTarget(null);
        }
        Log.d(TAG, "[SAB] Internal input deactivated");
    }

    /** Get the saved pending transformation text (for external use). */
    public String getPendingTransformationText() {
        return pendingTransformationText;
    }

    /** Clear pending transformation text (for cleanup). */
    public void clearPendingTransformationText() {
        pendingTransformationText = null;
    }

    /**
     * Replace all text in host app's input field (new manager path).
     */
    private void replaceEditorTextNew(String newText) {
        if (newText == null || mLatinIme == null) return;
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) return;
        String currentText = ric.getCommitedText();
        int totalLength = currentText != null ? currentText.length() : 0;
        ric.replaceText(0, totalLength, newText);
        ric.setSelection(newText.length(), newText.length());
    }

    /**
     * Debug-only: Push reply chips to the new SAB manager and switch to OV_EXPANDED.
     * Called by DebugSABController when TestModeController.isTestMode() is true.
     */
    public void debugShowSmartReplies(List<String> replies) {
        if (sabManager == null || replies == null) return;
        pushReplyChipsToManager(replies);
        sabManager.switchToState(SabState.OV_EXPANDED);
        Log.d(TAG, "[SAB] debugShowSmartReplies: pushed " + replies.size() + " chips to manager");
    }

    /**
     * Debug-only: Switch SAB manager to any SabState by name.
     * Called by DebugSABController when TestModeController.isTestMode() is true.
     */
    public void debugSwitchState(String stateName) {
        if (sabManager == null) return;

        // Handle special debug-only pseudo-states for screenshot capture
        if ("CTA_TONE_ACTIVE_LOADING".equals(stateName)) {
            sabManager.switchToState(SabState.TONE_CASUAL);
            sabManager.showToneSuggestionsShimmer();
            Log.d(TAG, "[SAB] debugSwitchState: tone active + shimmer loading");
            return;
        }
        if ("CTA_TRANSLATE_ACTIVE_RESULT".equals(stateName)) {
            sabManager.switchToState(SabState.CTA_TRANSLATE_ACTIVE);
            sabManager.configureTranslateActiveRow("Hindi", "\uD83C\uDDEE\uD83C\uDDF3");
            sabManager.showTranslateResult("मैं कल शाम को आऊंगा");
            Log.d(TAG, "[SAB] debugSwitchState: translate active + result shown");
            return;
        }
        if ("CTA_TRANSLATE_ACTIVE_LOADING".equals(stateName)) {
            sabManager.switchToState(SabState.CTA_TRANSLATE_ACTIVE);
            sabManager.configureTranslateActiveRow("Hindi", "\uD83C\uDDEE\uD83C\uDDF3");
            // Don't call showTranslateResult — leave shimmer visible
            Log.d(TAG, "[SAB] debugSwitchState: translate active + loading shimmer");
            return;
        }

        try {
            SabState state = SabState.valueOf(stateName);
            sabManager.switchToState(state);
            // Configure translate active row with mock data for debug/screenshot
            if (state == SabState.CTA_TRANSLATE_ACTIVE) {
                sabManager.configureTranslateActiveRow("Hindi", "\uD83C\uDDEE\uD83C\uDDF3");
            }
            Log.d(TAG, "[SAB] debugSwitchState: " + stateName);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "[SAB] debugSwitchState: unknown state " + stateName);
        }
    }

    /**
     * Called when keyboard opens. Checks for pre-computed replies from NLS pipeline.
     * This is the primary entry point for Build 7.0 smart replies.
     *
     * Priority:
     * 1. High confidence (>=0.80) + matching app/contact + latest incoming cached replies → show instantly
     * 2. Uncertain contact/app state → suppress suggestions for this sprint
     * 3. High confidence without cache → generate on demand from scoped NLS context
     */
    public boolean checkPrecomputedReplies() {
        ConversationMatcher.ContactMatch match = ConversationMatcher.getInstance().getActiveContact();

        if (match == null) {
            return suppressKeyboardSuggestions("no_active_contact");
        }

        Log.d(TAG, "[SAB] Active contact: " + match.contactName
                + " (confidence=" + match.confidence + ", key=" + match.conversationKey + ")");

        String editorPackage = ConversationMatcher.getInstance().getCurrentEditorPackage();
        if (editorPackage == null || !editorPackage.equals(match.packageName)) {
            return suppressKeyboardSuggestions("wrong_active_app");
        }
        if (match.confidence < 0.80f || match.conversationKey == null) {
            return suppressKeyboardSuggestions("uncertain_contact");
        }

        NlsMessageBuffer.ConversationSnapshot snapshot =
                NlsMessageBuffer.getInstance().openConversation(match.conversationKey);
        if (snapshot == null || snapshot.latestMessageSentByUser || snapshot.latestIncomingId == null) {
            return suppressKeyboardSuggestions("latest_message_not_replyable");
        }

        List<String> cachedReplies = ReplyCache.getInstance().get(match.conversationKey, snapshot.latestIncomingId);
        if (cachedReplies != null && !cachedReplies.isEmpty()) {
            Log.d(TAG, "[SAB] Showing scoped cached replies for " + match.contactName);
            pushReplyChipsToManager(cachedReplies);
            if (sabManager != null) {
                sabManager.switchToState(SabState.OV_EXPANDED);
            }
            return true;
        }

        Log.d(TAG, "[SAB] High confidence but no scoped cache, generating on-demand");
        if (sabManager != null) {
            sabManager.switchToState(SabState.OV_ROW2_LOADING);
        }
        ReplyPrecomputeManager.getInstance(getContext()).generateOnDemand(
            match.conversationKey, match.packageName, match.contactName,
            replies -> {
                if (replies != null && !replies.isEmpty()) {
                    pushReplyChipsToManager(replies);
                    if (sabManager != null) {
                        sabManager.switchToState(SabState.OV_EXPANDED);
                    }
                } else {
                    suppressKeyboardSuggestions("on_demand_empty");
                }
            });
        return true;
    }

    private boolean suppressKeyboardSuggestions(String reason) {
        if (sabManager != null) {
            sabManager.setReplies(new java.util.ArrayList<>());
            sabManager.setReplyChips(new java.util.ArrayList<>());
            sabManager.switchToState(SabState.OV_NO_CONTEXT);
        }
        Log.d(TAG, "[SAB] Suggestions suppressed: " + reason);
        return true;
    }

    /**
     * Shows contact picker strip when ConversationMatcher confidence < 80%.
     * Displays known NLS contacts as tappable chips.
     */
    private void showContactPickerStrip(String packageName) {
        java.util.Set<String> knownContacts = ConversationMatcher.getInstance().getKnownContacts(packageName);
        if (knownContacts == null || knownContacts.isEmpty()) {
            Log.d(TAG, "[SAB] No known contacts for picker strip in " + packageName);
            return;
        }

        Log.d(TAG, "[SAB] Showing contact picker with " + knownContacts.size() + " contacts");

        // Build contact chip list for the manager
        java.util.List<String> contactList = new java.util.ArrayList<>(knownContacts);
        // Limit to 5 most recent contacts to avoid overflow
        if (contactList.size() > 5) {
            contactList = contactList.subList(0, 5);
        }

        if (sabManager != null) {
            sabManager.showContactPicker(contactList);
        }
    }

    /**
     * Called when user taps a contact in the picker strip.
     * Sets USER_SELECTED confidence (100%) and loads cached replies.
     */
    public void onContactPickerSelected(String contactName, String packageName) {
        Log.d(TAG, "[SAB] User selected contact: " + contactName);

        // Set as active contact with 100% confidence
        String conversationKey = packageName + "|" + contactName;
        ConversationMatcher.getInstance().setActiveContactFromUserSelection(
                packageName, contactName, conversationKey);

        // Try to load cached replies for this contact
        List<String> cachedReplies = ReplyCache.getInstance().get(conversationKey);
        if (cachedReplies != null && !cachedReplies.isEmpty()) {
            pushReplyChipsToManager(cachedReplies);
            if (sabManager != null) {
                sabManager.switchToState(SabState.OV_EXPANDED);
            }
        } else {
            // No cache — show shimmer while generating on-demand
            if (sabManager != null) {
                sabManager.switchToState(SabState.OV_ROW2_LOADING);
            }
            // The existing onContextChanged flow handles on-demand generation
            Log.d(TAG, "[SAB] On-demand generation triggered for " + conversationKey);
        }
    }

    /**
     * Debug method for golden testing — shows contact picker with mock contacts.
     * Called by DebugSABController when state is CONTACT_PICKER.
     */
    public void debugShowContactPicker(List<String> mockContacts) {
        if (mockContacts == null || mockContacts.isEmpty()) {
            mockContacts = java.util.Arrays.asList("Mom", "Rahul", "Priya");
        }
        if (sabManager != null) {
            sabManager.showContactPicker(mockContacts);
        }
    }

    /**
     * Push quick reply strings to the manager as ReplyChips for OriginalView display.
     */
    public void pushReplyChipsToManager(List<String> replies) {
        if (sabManager == null || replies == null) return;
        List<SmartAssistantBarManager.ReplyChip> chips = new ArrayList<>();
        for (String reply : replies) {
            chips.add(new SmartAssistantBarManager.ReplyChip(reply, null));
        }
        sabManager.setReplyChips(chips);
        // Count reply generation as an AI action
        DailyUsageTracker.getInstance(context).recordUsage();

        // JourneyTracer: UI chips displayed — journey complete
        String traceId = JourneyTracer.getCurrentSmartReplyTrace();
        if (traceId != null) {
            try {
                org.json.JSONObject dataIn = new org.json.JSONObject();
                dataIn.put("chip_count", chips.size());
                dataIn.put("row", "Row1");
                JourneyTracer.step(traceId, "UI_CHIPS_DISPLAYED", dataIn, null,
                    chips.size() + " chips rendered in Row 1");
                JourneyTracer.complete(traceId, true);
                JourneyTracer.setCurrentSmartReplyTrace(null);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Map a tone name to the corresponding SabState for the active tone display.
     */
    private SabState mapToneNameToState(String toneName) {
        // All 21 tones use CTA_TONE_ACTIVE — the active tone name/emoji
        // is stored in SmartAssistantBarManager.activeToneName and used
        // directly by configureToneActiveRow() for label + color.
        return SabState.CTA_TONE_ACTIVE;
    }

    // ========== INITIALIZATION ==========

    private void init() {
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[SAB] Initializing SmartAssistantBar");
        }

        // ALWAYS inflate new layout and create manager (production + test)
        LayoutInflater.from(context).inflate(R.layout.wk_smart_assistant_bar, this, true);
        sabManager = new SmartAssistantBarManager(this);
        sabManager.setActionCallback(this);
        sabManager.switchToState(SabState.OV_EXPANDED);

        // Build 7.0: Bind CTA container + add scan button (after layout inflated)
        aiButtonsContainer = findViewById(R.id.wk_ov_row1_cta_buttons);
        setupScreenCaptureButton();

        // Load local dictionaries (needed for next-word predictions)
        loadDictionary();
        loadEmojiMap();
        seedNextWordTable();

        // Task 4: Initialize recent AI actions tracker
        SharedPreferences prefs = context.getSharedPreferences("WittyKeysPrefs", Context.MODE_PRIVATE);
        this.recentActionsTracker = new RecentAiActionsTracker(prefs);

        // Debug state cycling — test mode only
        if (TestModeController.isTestMode()) {
            this.setOnLongClickListener(v -> {
                debugCycleNextState();
                return true;
            });
        }

        Log.d(TAG, "[SAB] SmartAssistantBarManager initialized");
    }

    private void bindViews() {
        // Row 1
        row1Container = findViewById(R.id.row1_container);

        // Phase 3: Brain switch button (always visible)
        brainSwitchButton = findViewById(R.id.brain_switch_button);
        dictationButton = findViewById(R.id.dictation_button);
        collapseButton = findViewById(R.id.collapse_button);
        expandButton = findViewById(R.id.expand_button);

        // Row 1 - EXPANDED (real layout: wk_original_view.xml via wk_smart_assistant_bar.xml)
        aiButtonsContainer = findViewById(R.id.wk_ov_row1_cta_buttons);
        aiChatButton = findViewById(R.id.wk_ov_ai_btn);
        translateButton = findViewById(R.id.wk_ov_translate_btn);
        grammarButton = findViewById(R.id.wk_ov_grammar_btn);
        toneButton = findViewById(R.id.wk_ov_tone_btn);
        // Build 7.0: Screen capture button — re-enabled programmatically
        setupScreenCaptureButton();

        // Row 1 - COLLAPSED
        predictionsContainer = findViewById(R.id.predictions_container);
        prediction1 = findViewById(R.id.prediction_1);
        prediction2 = findViewById(R.id.prediction_2);
        prediction3 = findViewById(R.id.prediction_3);
        predictionSeparator1 = findViewById(R.id.prediction_separator_1);
        predictionSeparator2 = findViewById(R.id.prediction_separator_2);

        // Row 2
        row2Container = findViewById(R.id.row2_container);

        // Row 2 - EXPANDED (HorizontalScrollView)
        smartRepliesScroll = findViewById(R.id.smart_replies_scroll);
        smartRepliesContainer = findViewById(R.id.smart_replies_container);
        smartReply1 = findViewById(R.id.smart_reply_1);
        smartReply2 = findViewById(R.id.smart_reply_2);
        smartReply3 = findViewById(R.id.smart_reply_3);
        customCta = findViewById(R.id.custom_cta);
        moreCta = findViewById(R.id.more_cta);
        hintText = findViewById(R.id.hint_text);

        // Row 2 - COLLAPSED (HorizontalScrollView)
        collapsedRow2Scroll = findViewById(R.id.collapsed_row2_scroll);
        collapsedRow2Container = findViewById(R.id.collapsed_row2_container);
        smartToneChip = findViewById(R.id.smart_tone_chip);
        grammarFixChip = findViewById(R.id.grammar_fix_chip);
        translateChip = findViewById(R.id.translate_chip);
        moreCtaCollapsed = findViewById(R.id.more_cta_collapsed);

        // Row 2 - CUSTOM MODE
        customModeContainer = findViewById(R.id.custom_mode_container);
        customPromptInput = findViewById(R.id.custom_prompt_input);
        generateButton = findViewById(R.id.generate_button);
        cancelCustomButton = findViewById(R.id.cancel_custom_button);

        // Row 2 - ACCESSIBILITY PROMPT (Phase 9)
        accessibilityPromptContainer = findViewById(R.id.accessibility_prompt_container);
        accessibilityPromptText = findViewById(R.id.accessibility_prompt_text);
        accessibilityEnableButton = findViewById(R.id.accessibility_enable_button);

        // Row 2 - Phase 2 layouts (from includes)
        tonePicker = findViewById(R.id.tone_picker);
        toneActive = findViewById(R.id.tone_active);
        langPicker = findViewById(R.id.lang_picker);
        shimmerRow2 = findViewById(R.id.shimmer_row2);

        // clipButton REMOVED from layout per design spec
        clipButton = null;

        // Tone picker elements
        if (tonePicker != null) {
            toneChipsContainer = tonePicker.findViewById(R.id.tone_chips_container);
        }

        // Tone active elements
        if (toneActive != null) {
            pinnedToneChip = toneActive.findViewById(R.id.pinned_tone_container);
            pinnedToneText = toneActive.findViewById(R.id.pinned_tone_chip);
            closeToneActive = toneActive.findViewById(R.id.close_tone_active);
            toneSuggestionsContainer = toneActive.findViewById(R.id.tone_suggestions_container);
            regenButton = toneActive.findViewById(R.id.regen_button);
        }

        // Language picker elements
        if (langPicker != null) {
            langChipsContainer = langPicker.findViewById(R.id.lang_chips_container);
        }

        // Setup prediction click listeners
        setupPredictionClickListeners();

        // Setup brain switch button (Phase 3)
        setupBrainSwitchButton();

        // Setup collapse/expand buttons (Phase 3)
        setupCollapseExpandButtons();

        // Setup smart reply click listeners
        setupSmartReplyClickListeners();

        // Setup custom mode (Phase 3)
        setupCustomMode();

        // Setup More CTA (Phase 3)
        setupMoreCta();

        // Setup tone chip click listeners (collapsed state)
        setupCollapsedToneChips();

        // Setup accessibility prompt (Phase 9)
        setupAccessibilityPrompt();

        // Phase 2: Setup tone picker chips
        setupTonePickerChips();

        // Phase 2: Setup language picker chips
        setupLangPickerChips();

        // Phase 2: Setup tone active close and regen buttons
        setupToneActiveButtons();

        // Phase 2: Setup clipboard button
        setupClipButton();

        // Task 2.27: Setup milestone toast
        setupMilestoneToast();

        // Phase 3: Apply progressive feature disclosure on init
        updateFeatureVisibility();
    }

    /**
     * Task 2.27: Bind and setup milestone toast views
     */
    private void setupMilestoneToast() {
        milestoneToast = findViewById(R.id.milestone_toast);
        if (milestoneToast != null) {
            milestoneEmoji = milestoneToast.findViewById(R.id.milestone_emoji);
            milestoneTitle = milestoneToast.findViewById(R.id.milestone_title);
            milestoneSubtitle = milestoneToast.findViewById(R.id.milestone_subtitle);
            milestoneClose = milestoneToast.findViewById(R.id.milestone_close);

            // Close button dismisses the toast
            if (milestoneClose != null) {
                milestoneClose.setOnClickListener(v -> {
                    playCtaTapAnimation(v);
                    hideMilestoneToast();
                });
            }
        }
    }

    private void setupPredictionClickListeners() {
        View.OnClickListener predictionClickListener = v -> {
            if (v instanceof TextView) {
                String text = ((TextView) v).getText().toString();
                commitSuggestionSmart(text);
            }
        };

        if (prediction1 != null) prediction1.setOnClickListener(predictionClickListener);
        if (prediction2 != null) prediction2.setOnClickListener(predictionClickListener);
        if (prediction3 != null) prediction3.setOnClickListener(predictionClickListener);
    }

    private void setupSmartReplyClickListeners() {
        // Phase 8: Add touch listener to detect flag icon taps vs text taps
        // Flag icon is at drawableEnd - if user taps that area, report content
        // Otherwise, commit the smart reply text
        setupSmartReplyWithFlag(smartReply1);
        setupSmartReplyWithFlag(smartReply2);
        setupSmartReplyWithFlag(smartReply3);
    }

    /**
     * Setup a smart reply TextView with both text tap and flag tap handling.
     * Phase 8: Report Feature (mailto:)
     *
     * - Tap on text area → commit smart reply
     * - Tap on flag icon (drawableEnd) → report via ReportHelper
     */
    @SuppressWarnings("ClickableViewAccessibility")
    private void setupSmartReplyWithFlag(TextView tv) {
        if (tv == null) return;

        tv.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                TextView textView = (TextView) v;

                // Check if touch is on the drawableEnd (flag icon)
                Drawable[] drawables = textView.getCompoundDrawablesRelative();
                Drawable drawableEnd = drawables[2]; // index 2 = drawableEnd

                if (drawableEnd != null) {
                    int drawableWidth = drawableEnd.getIntrinsicWidth();
                    int viewWidth = textView.getWidth();
                    int paddingEnd = textView.getPaddingEnd();
                    int drawablePadding = textView.getCompoundDrawablePadding();

                    // Calculate the flag tap area (from right edge to start of flag)
                    int flagStartX = viewWidth - paddingEnd - drawableWidth - drawablePadding;
                    float touchX = event.getX();

                    if (touchX >= flagStartX) {
                        // Flag tapped - report the content
                        playCtaTapAnimation(v);
                        String content = textView.getText().toString();
                        ReportHelper.reportContent(context, content, ReportHelper.FEATURE_SMART_REPLY);
                        return true; // Consume the event
                    }
                }

                // Text tapped - commit smart reply with tap + checkmark animation
                playCtaTapAnimation(v);
                playCheckmarkAnimation(v);
                String text = textView.getText().toString();
                commitSmartReply(text);
                return true; // Consume the event
            }
            return false; // Don't consume other events (like ACTION_DOWN for visual feedback)
        });

        // Keep click listener for accessibility
        tv.setOnClickListener(v -> {
            if (v instanceof TextView) {
                playCtaTapAnimation(v);
                playCheckmarkAnimation(v);
                String text = ((TextView) v).getText().toString();
                commitSmartReply(text);
            }
        });
    }

    // ========== PHASE 3: BRAIN SWITCH BUTTON ==========

    private void setupBrainSwitchButton() {
        if (brainSwitchButton != null) {
            brainSwitchButton.setOnClickListener(v -> {
                SmartAssistantLogger.j3_brainIconTapped();
                playCtaTapAnimation(v);
                stopBrainBlinkAnimation();
                toggleOverlay();
            });
            updateBrainIconState();
        }
    }

    private void toggleOverlay() {
        Context ctx = getContext();
        if (ctx == null) return;

        boolean isRunning = OverlayServiceManager.isOverlayRunning(ctx);
        if (isRunning) {
            OverlayServiceManager.hideOverlay(ctx);
            isOverlayVisible = false;
        } else {
            OverlayServiceManager.showOverlay(ctx);
            isOverlayVisible = true;
        }
        updateBrainIconState();
    }

    private void updateBrainIconState() {
        if (brainSwitchButton != null) {
            Context ctx = getContext();
            if (ctx != null) {
                isOverlayVisible = OverlayServiceManager.isOverlayRunning(ctx);
            }
            brainSwitchButton.setAlpha(isOverlayVisible ? 1.0f : 0.4f);
        }
    }

    /**
     * Called from LatinIME.onStartInputView() to sync overlay icon state.
     * Must be called on main thread.
     */
    public void refreshOverlayIconState() {
        updateBrainIconState();
    }

    // ========== PHASE 3: COLLAPSE/EXPAND BUTTONS ==========

    private void setupCollapseExpandButtons() {
        if (collapseButton != null) {
            collapseButton.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[SAB] Collapse button clicked, switching to COLLAPSED");
                }
                DemoLogger.logUserAction(DemoLogger.FLOW_SMART_ASSISTANT, "tap_collapse", "collapse_button", null);
                setState(BarState.COLLAPSED);
            });
        }

        if (expandButton != null) {
            expandButton.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[SAB] Expand button clicked, switching to EXPANDED");
                }
                DemoLogger.logUserAction(DemoLogger.FLOW_SMART_ASSISTANT, "tap_expand", "expand_button", null);
                setState(BarState.EXPANDED);
            });
        }
    }

    // ========== PHASE 3: CUSTOM MODE ==========
    // User types prompt in the ACTIVE APP's input field (WhatsApp, etc.)
    // Custom mode shows: "Type prompt..." + [Generate] + [Cancel]
    // Generate reads text from host app's input, generates custom replies

    private OnCustomGenerateListener customGenerateListener;

    /**
     * Interface for Custom Generate events.
     * KeyboardSwitcher implements this to call the API with the prompt.
     */
    public interface OnCustomGenerateListener {
        void onGenerateCustomReplies(String prompt);
    }

    /**
     * Set the callback for Custom Generate.
     */
    public void setCustomGenerateListener(OnCustomGenerateListener listener) {
        this.customGenerateListener = listener;
    }

    private void setupCustomMode() {
        // Custom CTA enters custom mode
        if (customCta != null) {
            customCta.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                enterCustomMode();
            });
        }

        // Generate button reads text from host app and generates replies
        if (generateButton != null) {
            generateButton.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                onGenerateCustomClick();
            });
        }

        // Cancel button exits custom mode
        if (cancelCustomButton != null) {
            cancelCustomButton.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                exitCustomMode();
            });
        }
    }

    /**
     * Enter custom mode - show prompt message + Generate + Cancel in Row 2
     */
    public void enterCustomMode() {
        return; // Old layout removed — Manager handles this
    }

    /**
     * Exit custom mode - return to normal Row 2 content
     */
    public void exitCustomMode() {
        return; // Old layout removed — Manager handles this
    }

    /**
     * Generate button clicked — reads instruction from InternalInputView
     * and original text from pendingTransformationText.
     */
    private void onGenerateCustomClick() {
        // Delegates to onCustomGenerateTapped() which handles the new flow
        onCustomGenerateTapped();
    }

    /**
     * Show custom replies in Row 2 and exit custom mode.
     */
    public void showCustomReplies(List<String> replies) {
        if (sabManager != null && replies != null && !replies.isEmpty()) {
            List<SmartAssistantBarManager.ReplyChip> chips = new ArrayList<>();
            for (String reply : replies) {
                chips.add(new SmartAssistantBarManager.ReplyChip(reply, null));
            }
            sabManager.setReplyChips(chips);
            isCustomModeActive = false;
            sabManager.switchToState(SabState.OV_EXPANDED);
            Log.d(TAG, "[SAB] showCustomReplies: " + replies.size() + " replies → OV_EXPANDED");
        }
    }

    /**
     * Show error in custom mode (API failed)
     */
    public void showCustomModeError(String error) {
        android.widget.Toast.makeText(context, error != null ? error : "Something went wrong", android.widget.Toast.LENGTH_SHORT).show();
        if (sabManager != null) {
            isCustomModeActive = false;
            sabManager.switchToState(SabState.OV_EXPANDED);
        }
        Log.d(TAG, "[SAB] showCustomModeError: " + error);
    }

    // ========== PHASE 3: MORE CTA (TONALITY OPTIONS) ==========
    // More CTA should show the SAME tone options UI as the Tone icon in Row 1.
    // This is done via a callback to KeyboardSwitcher which controls UnifiedAiView.

    private OnMoreCtaClickListener moreCtaClickListener;

    /**
     * Interface for More CTA click events.
     * KeyboardSwitcher should implement this to show UnifiedAiView with tone options.
     */
    public interface OnMoreCtaClickListener {
        void onMoreCtaClick();
    }

    /**
     * Set the callback for More CTA clicks.
     * KeyboardSwitcher sets this to trigger the same tone selection UI as the Tone icon.
     */
    public void setMoreCtaClickListener(OnMoreCtaClickListener listener) {
        this.moreCtaClickListener = listener;
    }

    private void setupMoreCta() {
        View.OnClickListener moreClickListener = v -> {
            playCtaTapAnimation(v);
            SmartAssistantLogger.j4_moreCtaTapped();
            // Delegate to KeyboardSwitcher to show UnifiedAiView with tone options
            // (same as toneButton click)
            if (moreCtaClickListener != null) {
                moreCtaClickListener.onMoreCtaClick();
            } else {
                Log.w(TAG, "[SAB] More CTA clicked but no listener set - KeyboardSwitcher should set this");
            }
        };

        if (moreCta != null) {
            moreCta.setOnClickListener(moreClickListener);
        }

        if (moreCtaCollapsed != null) {
            moreCtaCollapsed.setOnClickListener(moreClickListener);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.getResources().getDisplayMetrics()
        );
    }

    // ========== PHASE 3: COLLAPSED TONE CHIPS ==========

    private void setupCollapsedToneChips() {
        if (smartToneChip != null) {
            smartToneChip.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                String currentTone = smartToneChip.getText().toString();
                SmartAssistantLogger.j4_contextActionTapped(currentTone);
                // Phase 2B: Switch to expanded + run tone flow
                setState(BarState.EXPANDED);
                applyToneAndShowActive(currentTone.toLowerCase());
            });
        }

        // Task 2.34: Wire grammar fix chip to actual API
        if (grammarFixChip != null) {
            grammarFixChip.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                SmartAssistantLogger.j4_contextActionTapped("GRAMMAR_FIX");
                performGrammarFix();
            });
        }

        // Task 2.34: Wire translate chip — expand + show language picker
        if (translateChip != null) {
            translateChip.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                SmartAssistantLogger.j4_contextActionTapped("TRANSLATE_CHIP");
                setState(BarState.EXPANDED);
                showRow2State(Row2State.LANG_PICKER);
            });
        }
    }

    // ========== PHASE 3: BRAIN BLINK ANIMATION ==========

    public void startBrainBlinkAnimation() {
        return; // Old layout removed — Manager handles this
    }

    public void stopBrainBlinkAnimation() {
        if (sabManager != null) sabManager.stopBrainBlinkAnimation();
    }

    // ========== PHASE 2: ROW 2 STATE MANAGEMENT ==========

    /**
     * Show a specific Row 2 state. Only ONE child of Row 2 FrameLayout is visible at a time.
     * Task 2.29-2.35: Row 2 state switching
     */
    public void showRow2State(Row2State state) {
        return; // Old layout removed — Manager handles this
    }

    /**
     * Get current Row 2 state
     */
    public Row2State getCurrentRow2State() {
        return currentRow2State;
    }

    // ========== PHASE 2: CTA TAP ANIMATIONS ==========

    /**
     * Task 2.20: OV tap animation - scale 0.96x, 150ms total (75ms down + 75ms up)
     */
    public void playCtaTapAnimation(View view) {
        if (view == null) return;

        view.animate()
            .scaleX(0.96f).scaleY(0.96f)
            .setDuration(75)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .withEndAction(() -> {
                view.animate()
                    .scaleX(1.0f).scaleY(1.0f)
                    .setDuration(75)
                    .start();
            })
            .start();

        // Haptic feedback
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    }

    /**
     * Task 2.20/2.21: Green border flash + checkmark animation for reply commit
     */
    public void playCheckmarkAnimation(View view) {
        if (view == null) return;

        // Get the background and apply green border flash
        Drawable bg = view.getBackground();
        if (bg instanceof GradientDrawable) {
            GradientDrawable gd = (GradientDrawable) bg;
            int greenGlow = androidx.core.content.ContextCompat.getColor(context, R.color.wk_green_glow);
            gd.setStroke(dpToPx(2), greenGlow);

            // Reset border after 600ms
            animationHandler.postDelayed(() -> {
                int chipBorder = androidx.core.content.ContextCompat.getColor(context, R.color.wk_chip_border);
                gd.setStroke(dpToPx(1), chipBorder);
            }, 600);
        }

        // Haptic feedback
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    }

    /**
     * Task 2.25: Grammar flash animation - green glow to surface2, 800ms
     */
    public void playGrammarFlashAnimation(View view) {
        if (view == null) return;

        int greenGlow = androidx.core.content.ContextCompat.getColor(context, R.color.wk_green_glow);
        int surface2 = androidx.core.content.ContextCompat.getColor(context, R.color.wk_surface2);

        android.animation.ValueAnimator colorAnim = android.animation.ValueAnimator.ofArgb(greenGlow, surface2);
        colorAnim.setDuration(800);
        colorAnim.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            view.setBackgroundColor(color);
        });
        colorAnim.start();

        // Haptic feedback
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    }

    /**
     * Error flash animation - red flash, 500ms
     */
    public void playErrorFlash(View view) {
        if (view == null) return;

        int errorColor = androidx.core.content.ContextCompat.getColor(context, R.color.wk_error_red);
        int normalColor = androidx.core.content.ContextCompat.getColor(context, R.color.wk_surface2);

        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofArgb(errorColor, normalColor);
        anim.setDuration(500);
        anim.addUpdateListener(a -> view.setBackgroundColor((int) a.getAnimatedValue()));
        anim.start();

        // Error haptic
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    /**
     * Shake animation for input field validation errors
     */
    public void playShakeAnimation(View view) {
        if (view == null) return;

        android.animation.ObjectAnimator shake = android.animation.ObjectAnimator.ofFloat(
            view, "translationX", 0, 10, -10, 10, -10, 5, -5, 0
        );
        shake.setDuration(300);
        shake.start();
    }

    // ========== TASK 2.24: SHIMMER ANIMATION ==========

    private android.animation.AnimatorSet shimmerAnimatorSet;

    /**
     * Task 2.24: Shimmer animation - alpha pulse on 3 chips with stagger
     * Each chip: alpha 0.3 → 1.0 → 0.3, 1200ms, staggered by 200ms
     */
    private void startShimmerAnimation() {
        stopShimmerAnimation();

        if (shimmerRow2 == null) return;

        View chip1 = shimmerRow2.findViewById(R.id.shimmer_chip_1);
        View chip2 = shimmerRow2.findViewById(R.id.shimmer_chip_2);
        View chip3 = shimmerRow2.findViewById(R.id.shimmer_chip_3);

        if (chip1 == null || chip2 == null || chip3 == null) return;

        // Create alpha animators for each chip
        ObjectAnimator anim1 = ObjectAnimator.ofFloat(chip1, "alpha", 0.3f, 1.0f, 0.3f);
        anim1.setDuration(1200);
        anim1.setRepeatCount(android.animation.ValueAnimator.INFINITE);

        ObjectAnimator anim2 = ObjectAnimator.ofFloat(chip2, "alpha", 0.3f, 1.0f, 0.3f);
        anim2.setDuration(1200);
        anim2.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        anim2.setStartDelay(200);

        ObjectAnimator anim3 = ObjectAnimator.ofFloat(chip3, "alpha", 0.3f, 1.0f, 0.3f);
        anim3.setDuration(1200);
        anim3.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        anim3.setStartDelay(400);

        shimmerAnimatorSet = new android.animation.AnimatorSet();
        shimmerAnimatorSet.playTogether(anim1, anim2, anim3);
        shimmerAnimatorSet.start();
    }

    private void stopShimmerAnimation() {
        if (shimmerAnimatorSet != null) {
            shimmerAnimatorSet.cancel();
            shimmerAnimatorSet = null;
        }
    }

    // ========== TASK 2.27: MILESTONE TOAST ==========

    /**
     * Show milestone toast with slide up animation.
     * Auto-hides after 2500ms.
     *
     * @param emoji   Celebration emoji (e.g., "🎉", "🏆", "⭐")
     * @param title   Main message (e.g., "First AI Reply Sent!")
     * @param subtitle Optional subtitle (can be null)
     */
    public void showMilestoneToast(String emoji, String title, String subtitle) {
        return; // Old layout removed — Manager handles this
    }

    /**
     * Hide milestone toast with fade out animation
     */
    public void hideMilestoneToast() {
        return; // Old layout removed — Manager handles this
    }

    /**
     * Convenience method to show common milestones
     */
    public void showMilestone(MilestoneType type) {
        return; // Old layout removed — Manager handles this
    }

    /**
     * Milestone types for easy triggering
     */
    public enum MilestoneType {
        FIRST_AI_REPLY,
        TEN_REPLIES,
        FIFTY_REPLIES,
        FIRST_TONE,
        FIRST_GRAMMAR,
        FIRST_TRANSLATE
    }

    /**
     * Task 2.27: Confetti animation — 5 emoji particles with staggered fall
     */
    private void playConfettiAnimation() {
        if (milestoneToast == null || !(milestoneToast instanceof FrameLayout)) return;

        FrameLayout toastContainer = (FrameLayout) milestoneToast;
        String[] confettiEmojis = {"🎉", "✨", "🎊", "⭐", "🥳"};
        int containerWidth = toastContainer.getWidth();
        if (containerWidth <= 0) containerWidth = 600; // fallback

        for (int i = 0; i < confettiEmojis.length; i++) {
            TextView particle = new TextView(context);
            particle.setText(confettiEmojis[i]);
            particle.setTextSize(14);
            particle.setAlpha(0f);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            );
            // Spread particles across the width
            params.leftMargin = (int)(containerWidth * (0.1f + 0.2f * i));
            params.topMargin = 0;
            toastContainer.addView(particle, params);

            // Animate: fade in + fall down + fade out
            final int delay = i * 120; // 120ms stagger
            final View p = particle;
            animationHandler.postDelayed(() -> {
                ObjectAnimator fallAnim = ObjectAnimator.ofFloat(p, "translationY", -20f, 60f);
                fallAnim.setDuration(600);

                ObjectAnimator alphaIn = ObjectAnimator.ofFloat(p, "alpha", 0f, 1f, 0f);
                alphaIn.setDuration(600);

                android.animation.AnimatorSet set = new android.animation.AnimatorSet();
                set.playTogether(fallAnim, alphaIn);
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        toastContainer.removeView(p);
                    }
                });
                set.start();
            }, delay);
        }
    }

    /**
     * Task 2.35: Show stat cards on brain long-press, auto-hide after 5s
     */
    public void showStatCards() {
        return; // Old layout removed — Manager handles this
    }

    /**
     * Helper: Convert dp to px
     */
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp,
            context.getResources().getDisplayMetrics()
        );
    }

    // ========== PHASE 9: ACCESSIBILITY PERMISSION PROMPT ==========

    /**
     * Setup accessibility prompt click listener.
     * When user taps [Enable], show the consent modal FIRST (not direct Settings redirect).
     */
    private void setupAccessibilityPrompt() {
        if (accessibilityEnableButton != null) {
            accessibilityEnableButton.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                SmartAssistantLogger.j7_enableButtonTapped();
                Log.d(TAG, "[SAB] Enable button tapped - showing accessibility consent modal");
                showAccessibilityConsentDialog();
            });
        }
    }

    /**
     * Show accessibility prompt in Row 2.
     * Called when keyboard opens and accessibility permission is not enabled.
     * Hides normal Row 2 content and shows the prompt.
     */
    public void showAccessibilityPrompt() {
        if (sabManager != null) {
            sabManager.switchToState(SabState.OV_ACCESSIBILITY);
        }
        Log.d(TAG, "[SAB] showAccessibilityPrompt → OV_ACCESSIBILITY");
    }

    /**
     * Hide accessibility prompt and restore normal Row 2 content.
     */
    public void hideAccessibilityPrompt() {
        if (sabManager != null) {
            sabManager.switchToState(SabState.OV_EXPANDED);
        }
        Log.d(TAG, "[SAB] hideAccessibilityPrompt → OV_EXPANDED");
    }

    /**
     * Show accessibility consent dialog (MODAL).
     * CRITICAL: Must show modal BEFORE redirecting to Settings (Play Store requirement).
     * Uses PopupWindow because Dialog doesn't work properly in IME context.
     */
    private void showAccessibilityConsentDialog() {
        // Dismiss any existing popup
        if (accessibilityConsentPopup != null && accessibilityConsentPopup.isShowing()) {
            accessibilityConsentPopup.dismiss();
        }

        // Inflate the consent dialog layout
        View contentView = LayoutInflater.from(context).inflate(
            R.layout.accessibility_consent_popup, null);

        // Create PopupWindow
        accessibilityConsentPopup = new PopupWindow(
            contentView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true); // focusable

        // Find views in the popup
        TextView titleView = contentView.findViewById(R.id.consent_title);
        TextView messageView = contentView.findViewById(R.id.consent_message);
        android.widget.Button grantButton = contentView.findViewById(R.id.grant_permission_button);
        android.widget.Button cancelButton = contentView.findViewById(R.id.cancel_button);

        // Set text
        if (titleView != null) {
            titleView.setText(R.string.accessibility_consent_title);
        }
        if (messageView != null) {
            messageView.setText(R.string.accessibility_consent_message);
        }

        // Grant Permission button - opens Settings
        if (grantButton != null) {
            grantButton.setOnClickListener(v -> {
                SmartAssistantLogger.j7_permissionGranted();
                Log.d(TAG, "[SAB] User granted permission - opening Accessibility Settings");

                // Open Accessibility Settings
                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);

                // Dismiss popup
                if (accessibilityConsentPopup != null) {
                    accessibilityConsentPopup.dismiss();
                }
            });
        }

        // Cancel button - just dismisses the popup
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> {
                SmartAssistantLogger.j7_permissionCancelled();
                Log.d(TAG, "[SAB] User cancelled permission modal");

                if (accessibilityConsentPopup != null) {
                    accessibilityConsentPopup.dismiss();
                }
            });
        }

        // Set background for proper popup appearance
        accessibilityConsentPopup.setBackgroundDrawable(
            new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        accessibilityConsentPopup.setOutsideTouchable(true);

        // Show popup — wrapped in try-catch for IME window token issues
        try {
            if (getWindowToken() != null) {
                accessibilityConsentPopup.showAtLocation(this, android.view.Gravity.BOTTOM, 0, getHeight());
                Log.d(TAG, "[SAB] Accessibility consent popup displayed");
            } else {
                Log.w(TAG, "[SAB] No window token — falling back to AlertDialog");
                showAccessibilityConsentAsDialog(contentView, grantButton, cancelButton);
            }
        } catch (Exception e) {
            Log.e(TAG, "[SAB] PopupWindow failed: " + e.getMessage() + " — falling back to AlertDialog");
            showAccessibilityConsentAsDialog(contentView, grantButton, cancelButton);
        }
    }

    /** Fallback: show consent as AlertDialog (works from IME via window token) */
    private void showAccessibilityConsentAsDialog(View contentView, android.widget.Button grantBtn, android.widget.Button cancelBtn) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        // Remove contentView from any parent (may have been added to popup)
        if (contentView.getParent() != null) {
            ((ViewGroup) contentView.getParent()).removeView(contentView);
        }
        builder.setView(contentView);
        builder.setCancelable(true);
        android.app.AlertDialog dialog = builder.create();

        // Use the IME's window token so dialog shows above keyboard
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
            IBinder token = getWindowToken();
            if (token != null) {
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.token = token;
                dialog.getWindow().setAttributes(lp);
            }
        }

        // Re-wire buttons to also dismiss this dialog
        if (grantBtn != null) {
            grantBtn.setOnClickListener(v -> {
                SmartAssistantLogger.j7_permissionGranted();
                Log.d(TAG, "[SAB] User granted permission - opening Accessibility Settings");
                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                dialog.dismiss();
            });
        }
        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> {
                SmartAssistantLogger.j7_permissionCancelled();
                Log.d(TAG, "[SAB] User cancelled permission dialog");
                dialog.dismiss();
            });
        }

        dialog.show();
        Log.d(TAG, "[SAB] Accessibility consent dialog displayed (AlertDialog fallback)");
    }

    /**
     * Check if accessibility prompt is currently visible.
     */
    public boolean isAccessibilityPromptVisible() {
        return isAccessibilityPromptActive;
    }

    // ========== PHASE 3: DEFERRED ACCESSIBILITY PROMPT ==========

    /**
     * Task 2.8: Show deferred accessibility prompt after 3 reply taps.
     * Uses the existing ACC_PROMPT Row2State and accessibilityPromptContainer.
     */
    private void showDeferredAccessibilityPrompt() {
        AccessibilityPromptTracker tracker = AccessibilityPromptTracker.getInstance(getContext());
        tracker.markPromptShown();

        // Use existing Row 2 state machine to show ACC_PROMPT
        showRow2State(Row2State.ACC_PROMPT);

        // Wire [Later] button if it exists in the accessibility prompt container
        if (accessibilityPromptContainer != null) {
            View laterButton = accessibilityPromptContainer.findViewWithTag("later_button");
            if (laterButton != null) {
                laterButton.setOnClickListener(v -> {
                    tracker.markPromptDismissed();
                    showRow2State(Row2State.SMART_REPLIES);
                    // Restore replies if available
                    if (currentSmartReplies != null && !currentSmartReplies.isEmpty()) {
                        showSmartReplies(currentSmartReplies);
                    }
                });
            }
        }

        Log.d(TAG, "[SAB] Phase 3: Deferred accessibility prompt shown after " + tracker.getTapCount() + " taps");
    }

    // ========== PHASE 3: SWIPE HINT ==========

    /**
     * Task 2.12: Show one-time swipe hint after first reply tap.
     * "Swipe for more reply styles →" — shown once, never again.
     */
    private void showSwipeHintIfNeeded() {
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("wk_onboarding", Context.MODE_PRIVATE);
        if (prefs.getBoolean("wk_onboarding_swipe_hint_shown", false)) return;

        prefs.edit().putBoolean("wk_onboarding_swipe_hint_shown", true).apply();

        // Create hint view above SmartAssistantBar
        TextView hint = new TextView(getContext());
        hint.setText("Swipe for more reply styles \u2192");
        hint.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.wk_text2));
        hint.setTextSize(12f);
        hint.setBackgroundColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.wk_surface));
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        hint.setPadding(pad * 2, pad / 2, pad * 2, pad / 2);
        hint.setAlpha(0f);

        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            int myIndex = parent.indexOfChild(this);
            if (myIndex >= 0) {
                parent.addView(hint, myIndex);
            } else {
                parent.addView(hint);
            }

            hint.animate().alpha(1f).setDuration(300).start();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                hint.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    parent.removeView(hint);
                }).start();
            }, 3000);
        }

        Log.d(TAG, "[HINT] Swipe hint shown (one-time)");
    }

    // ========== PHASE 3: PROGRESSIVE FEATURE DISCLOSURE ==========

    /**
     * Task 2.11: Hide tone selector and advanced features until 5+ reply taps.
     * Reduces initial cognitive load for new users.
     */
    private void updateFeatureVisibility() {
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("wk_onboarding", Context.MODE_PRIVATE);
        boolean unlocked = prefs.getBoolean("wk_onboarding_features_unlocked", false);

        if (!unlocked) {
            int tapCount = AccessibilityPromptTracker.getInstance(getContext()).getTapCount();
            if (tapCount >= 5) {
                // Unlock! Show with animation
                unlocked = true;
                prefs.edit().putBoolean("wk_onboarding_features_unlocked", true).apply();
                Log.d(TAG, "[DISCLOSURE] Features unlocked after " + tapCount + " taps");

                // Fade in tone button
                if (toneButton != null) {
                    toneButton.setVisibility(View.VISIBLE);
                    toneButton.setAlpha(0f);
                    toneButton.animate().alpha(1f).setDuration(300).start();
                }
                // Fade in translate button
                if (translateButton != null) {
                    translateButton.setVisibility(View.VISIBLE);
                    translateButton.setAlpha(0f);
                    translateButton.animate().alpha(1f).setDuration(300).start();
                }
            }
        }

        if (!unlocked) {
            // Hide advanced features
            if (toneButton != null) toneButton.setVisibility(View.GONE);
            if (translateButton != null) translateButton.setVisibility(View.GONE);
        }
    }

    // ========== PHASE 2: TONE PICKER SETUP ==========

    /**
     * Task 2.30: Setup tone picker chip click listeners
     */
    private void setupTonePickerChips() {
        if (toneChipsContainer == null) return;

        for (int i = 0; i < toneChipsContainer.getChildCount(); i++) {
            View chip = toneChipsContainer.getChildAt(i);
            Object tag = chip.getTag();
            String toneName = tag != null ? tag.toString() : null;

            if (toneName == null) continue;

            if ("Custom".equals(toneName)) {
                chip.setOnClickListener(v -> {
                    playCtaTapAnimation(v);
                    SmartAssistantLogger.j4_contextActionTapped("TONE_CUSTOM");
                    showRow2State(Row2State.CUSTOM_MODE);
                });
            } else {
                final String selectedTone = toneName;
                chip.setOnClickListener(v -> {
                    playCtaTapAnimation(v);
                    SmartAssistantLogger.j4_contextActionTapped("TONE_" + selectedTone);
                    applyToneAndShowActive(selectedTone);
                });
            }
        }
    }

    // ========== PHASE 2: LANGUAGE PICKER SETUP ==========

    /**
     * Task 2.31: Setup language picker chip click listeners
     */
    private void setupLangPickerChips() {
        if (langChipsContainer == null) return;

        for (int i = 0; i < langChipsContainer.getChildCount(); i++) {
            View chip = langChipsContainer.getChildAt(i);
            Object tag = chip.getTag();
            String langName = tag != null ? tag.toString() : null;

            if (langName == null) continue;

            if ("Custom".equals(langName)) {
                chip.setOnClickListener(v -> {
                    playCtaTapAnimation(v);
                    SmartAssistantLogger.j4_contextActionTapped("TRANSLATE_CUSTOM");
                    showRow2State(Row2State.CUSTOM_MODE);
                });
            } else {
                final String selectedLang = langName;
                chip.setOnClickListener(v -> {
                    playCtaTapAnimation(v);
                    SmartAssistantLogger.j4_contextActionTapped("TRANSLATE_" + selectedLang);
                    translateText(selectedLang);
                });
            }
        }
    }

    // ========== PHASE 2: TONE ACTIVE BUTTONS ==========

    /**
     * Task 2.30: Setup tone active close and regen buttons
     */
    private void setupToneActiveButtons() {
        // Close button - return to smart replies
        if (closeToneActive != null) {
            closeToneActive.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                showRow2State(Row2State.SMART_REPLIES);
            });
        }

        // Regen button - regenerate tone suggestions with current tone
        if (regenButton != null) {
            regenButton.setOnClickListener(v -> {
                playCtaTapAnimation(v);
                SmartAssistantLogger.j4_contextActionTapped("TONE_REGEN");
                if (currentActiveTone != null) {
                    applyToneAndShowActive(currentActiveTone);
                }
            });
        }
    }

    // ========== PHASE 2B: TRANSLATION FLOW ==========

    /**
     * Task 2.31: Translate text via API and replace editor content.
     * Flow: LANG_PICKER → SHIMMER_LOADING → API → replace text → SMART_REPLIES.
     */
    private void translateText(String targetLanguage) {
        String text = getCurrentTextFromEditor();
        if (text == null || text.trim().isEmpty()) {
            showEmptyTextError();
            return;
        }

        showRow2State(Row2State.SHIMMER_LOADING);
        SmartAssistantLogger.j4_loadingShown("TRANSLATE_" + targetLanguage);

        String systemPrompt = ReplyGenerator.buildTranslatePrompt(targetLanguage);
        new ClaudeApi().generateReplies(systemPrompt, text, new ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                animationHandler.post(() -> {
                    if (!replies.isEmpty()) {
                        replaceEditorText(replies.get(0));
                        SmartAssistantLogger.j4_transformComplete(replies.size());
                    }
                    showRow2State(Row2State.SMART_REPLIES);
                });
            }

            @Override
            public void onError(String error) {
                animationHandler.post(() -> {
                    showRow2State(Row2State.SMART_REPLIES);
                    SmartAssistantLogger.logError("TRANSLATE", error);
                });
            }
        });
    }

    // ========== PHASE 2: CLIPBOARD BUTTON ==========

    /**
     * Task 2.29: Setup clipboard button
     */
    private void setupClipButton() {
        if (clipButton == null) return;

        clipButton.setOnClickListener(v -> {
            playCtaTapAnimation(v);
            SmartAssistantLogger.j4_contextActionTapped("CLIP");

            // Get text to copy (last selected reply or current EditText content)
            String textToClip = null;

            // Try to get from last smart reply
            if (!currentSmartReplies.isEmpty()) {
                textToClip = currentSmartReplies.get(0);
            }

            // Fallback to EditText content
            if ((textToClip == null || textToClip.isEmpty()) && mLatinIme != null) {
                RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
                if (ric != null) {
                    textToClip = ric.getCommitedText();
                }
            }

            if (textToClip != null && !textToClip.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("WittyKeys Reply", textToClip);
                clipboard.setPrimaryClip(clip);

                // Show checkmark animation
                playCheckmarkAnimation(v);

                Log.d(TAG, "[SAB] Copied to clipboard: " + textToClip.length() + " chars");
            } else {
                // Nothing to copy
                playErrorFlash(v);
            }
        });
    }

    // ========== BUILD 7.0: SCREEN CAPTURE ==========

    /**
     * Build 7.0 P3: Programmatically create and add the screen capture button
     * to the ai_buttons_container (Row 1 EXPANDED).
     */
    private void setupScreenCaptureButton() {
        if (aiButtonsContainer == null) {
            screenReadButton = null;
            return;
        }

        screenReadButton = new TextView(getContext());
        screenReadButton.setText("\uD83D\uDCF1 Scan"); // 📱 Scan
        screenReadButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.wk_text_cta));
        screenReadButton.setTextColor(getResources().getColor(R.color.wk_text2));
        screenReadButton.setBackground(getResources().getDrawable(R.drawable.wk_cta_button_bg));
        screenReadButton.setGravity(android.view.Gravity.CENTER);
        screenReadButton.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

        int paddingH = getResources().getDimensionPixelSize(R.dimen.wk_cta_button_padding_h);
        int paddingV = getResources().getDimensionPixelSize(R.dimen.wk_cta_button_padding_v);
        screenReadButton.setPadding(paddingH, paddingV, paddingH, paddingV);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                getResources().getDimensionPixelSize(R.dimen.wk_cta_button_height));
        int gap = getResources().getDimensionPixelSize(R.dimen.wk_row1_gap);
        lp.setMarginEnd(gap);
        screenReadButton.setLayoutParams(lp);
        screenReadButton.setClickable(true);
        screenReadButton.setFocusable(true);
        screenReadButton.setContentDescription("Capture Screen");

        screenReadButton.setOnClickListener(v -> {
            Log.d(TAG, "[SAB] Capture screen button tapped");
            playCtaTapAnimation(v);
            triggerScreenCapture();
        });

        // Insert at position 1 (after AI Chat button, before Tone button)
        aiButtonsContainer.addView(screenReadButton, 1);
    }

    private OnScreenCaptureListener screenCaptureListener;

    public interface OnScreenCaptureListener {
        void onScreenCaptureRequested();
    }

    public void setScreenCaptureListener(OnScreenCaptureListener listener) {
        this.screenCaptureListener = listener;
    }

    private void triggerScreenCapture() {
        if (screenCaptureListener != null) {
            screenCaptureListener.onScreenCaptureRequested();
        } else {
            Log.w(TAG, "[SAB] No screen capture listener set");
        }
    }

    /**
     * Build 7.0: Handle screenshot analysis result from ScreenshotAnalyzer.
     * Called by KeyboardSwitcher when analysis completes.
     */
    public void onScreenshotAnalysisReceived(String analysis) {
        Log.d(TAG, "[SAB] Screenshot analysis received");
    }

    /**
     * Highlight the screen capture button with blue accent (for active scan state).
     * Normal state: standard CTA styling (wk_cta_button_bg).
     * Active state: blue accent background, blue border, blue text.
     */
    public void setScreenCaptureHighlighted(boolean highlighted) {
        if (screenReadButton == null) {
            Log.w(TAG, "[SAB] setScreenCaptureHighlighted: screenReadButton is NULL");
            return;
        }
        Log.d(TAG, "[SAB] setScreenCaptureHighlighted(" + highlighted + ")");
        if (highlighted) {
            float density = getResources().getDisplayMetrics().density;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(14 * density);
            bg.setColor(android.graphics.Color.argb(51, 96, 165, 250)); // ~20% blue tint
            bg.setStroke((int)(1.5f * density), android.graphics.Color.parseColor("#60A5FA"));
            screenReadButton.setBackground(bg);
            screenReadButton.setTextColor(android.graphics.Color.parseColor("#3B82F6")); // stronger blue
        } else {
            screenReadButton.setBackground(getResources().getDrawable(R.drawable.wk_cta_button_bg));
            screenReadButton.setTextColor(getResources().getColor(R.color.wk_text2));
        }
    }

    // ========== PHASE 3: NEW MESSAGE HANDLING ==========

    /**
     * Called when a new message is detected while user is in the keyboard.
     * Phase 4 Fix: This should ONLY trigger brain blink in OriginalView, NOT change views.
     * It should NOT overwrite existing sharedContextData with incomplete data.
     */
    /**
     * Called when a new message is detected while user is in the keyboard.
     * Triggers brain blink animation and optionally updates reply chips.
     *
     * @param senderName The sender of the new message
     * @param replies Optional new reply suggestions (can be empty)
     */
    public void onNewMessageReceived(String senderName, List<String> replies) {
        SmartAssistantLogger.j13_newMessageReceived(
            senderName != null ? senderName : "unknown"
        );
        if (sabManager != null) {
            // Update reply chips if provided
            if (replies != null && !replies.isEmpty()) {
                pushReplyChipsToManager(replies);
            }
            sabManager.switchToState(SabState.OV_BRAIN_BLINK);
        }
        Log.d(TAG, "[SAB] onNewMessageReceived processed for sender=" + senderName);
    }

    // ========== STATE MANAGEMENT ==========

    public void setState(BarState newState) {
        return; // Old layout removed — Manager handles this
    }

    public BarState getState() {
        return currentState;
    }

    private void updateVisibility() {
        // Brain switch button always visible in OriginalView (both states)
        if (brainSwitchButton != null) brainSwitchButton.setVisibility(View.VISIBLE);

        // Dictation button always visible in OriginalView (both states)
        if (dictationButton != null) dictationButton.setVisibility(View.VISIBLE);

        // Custom mode is now handled by UnifiedAiView via callback, not in-line EditText
        // So we just reset the flag here
        isCustomModeActive = false;

        // Phase 9: If accessibility prompt is active, keep it visible and skip normal Row 2 updates
        boolean skipRow2Update = isAccessibilityPromptActive;

        switch (currentState) {
            case EXPANDED:
                // Row 1: Show AI buttons, hide predictions
                if (aiButtonsContainer != null) aiButtonsContainer.setVisibility(View.VISIBLE);
                if (predictionsContainer != null) predictionsContainer.setVisibility(View.GONE);

                // Collapse/Expand buttons
                if (collapseButton != null) collapseButton.setVisibility(View.VISIBLE);
                if (expandButton != null) expandButton.setVisibility(View.GONE);

                // Row 2: Show smart replies scroll, hide collapsed content
                // Phase 9: Skip if accessibility prompt is active
                if (!skipRow2Update) {
                    if (hasContext && !currentSmartReplies.isEmpty()) {
                        if (smartRepliesScroll != null) smartRepliesScroll.setVisibility(View.VISIBLE);
                        if (smartRepliesContainer != null) smartRepliesContainer.setVisibility(View.VISIBLE);
                    } else {
                        if (smartRepliesScroll != null) smartRepliesScroll.setVisibility(View.GONE);
                        if (smartRepliesContainer != null) smartRepliesContainer.setVisibility(View.GONE);
                    }
                    // hintText removed - not needed
                    if (hintText != null) hintText.setVisibility(View.GONE);
                    if (collapsedRow2Scroll != null) collapsedRow2Scroll.setVisibility(View.GONE);
                    if (customModeContainer != null) customModeContainer.setVisibility(View.GONE);
                    if (accessibilityPromptContainer != null) accessibilityPromptContainer.setVisibility(View.GONE);
                }
                break;

            case COLLAPSED:
                // Row 1: Hide AI buttons, show predictions
                if (aiButtonsContainer != null) aiButtonsContainer.setVisibility(View.GONE);
                if (predictionsContainer != null) predictionsContainer.setVisibility(View.VISIBLE);

                // Collapse/Expand buttons
                if (collapseButton != null) collapseButton.setVisibility(View.GONE);
                if (expandButton != null) expandButton.setVisibility(View.VISIBLE);

                // Row 2: Hide smart replies/hint, show collapsed content
                // Phase 9: Skip if accessibility prompt is active
                if (!skipRow2Update) {
                    if (smartRepliesScroll != null) smartRepliesScroll.setVisibility(View.GONE);
                    if (hintText != null) hintText.setVisibility(View.GONE);
                    if (collapsedRow2Scroll != null) collapsedRow2Scroll.setVisibility(View.VISIBLE);
                    if (customModeContainer != null) customModeContainer.setVisibility(View.GONE);
                    if (accessibilityPromptContainer != null) accessibilityPromptContainer.setVisibility(View.GONE);
                }
                break;
        }
    }

    // ========== USER INPUT HANDLING ==========

    /**
     * Called when user types text. Transitions to COLLAPSED state and updates predictions.
     */
    public void onUserInput(String input) {
        if (voiceMode) return;
        if (isCustomModeActive) return;

        if (input != null && !input.trim().isEmpty()) {
            // User typing → collapse
            if (sabManager != null) {
                sabManager.switchToState(SabState.OV_COLLAPSED);

                // Push real predictions from local Trie dictionary
                String lastWord = getLastWord(input);
                List<String> suggestions = generateSuggestions(lastWord);
                if (suggestions.isEmpty()) {
                    // Fallback defaults
                    sabManager.setPredictions(new String[]{"the", "to", "and"});
                } else {
                    String[] preds = new String[Math.min(3, suggestions.size())];
                    for (int i = 0; i < preds.length; i++) {
                        preds[i] = suggestions.get(i);
                    }
                    sabManager.setPredictions(preds);
                }
            }
            SmartAssistantLogger.j4_userStartedTyping(input.charAt(0));
        } else {
            // Text cleared → expand
            if (sabManager != null) {
                sabManager.setPredictions(null);
                sabManager.switchToState(SabState.OV_EXPANDED);
            }
        }
    }

    private void updatePredictions(String lastWord, String fullInput) {
        List<String> suggestions = generateSuggestions(lastWord);

        if (suggestions.isEmpty()) {
            showDefaultPredictions();
            return;
        }

        // Apply context-aware casing
        String lastWordRaw = getLastWordRaw(fullInput);
        boolean startOfSentence = isStartOfSentence(fullInput);
        List<String> caseAdjusted = new ArrayList<>();
        for (String s : suggestions) {
            caseAdjusted.add(adjustCaseForContext(s, lastWordRaw, startOfSentence));
        }

        // Update UI
        if (prediction1 != null && caseAdjusted.size() > 0) {
            prediction1.setText(caseAdjusted.get(0));
            prediction1.setVisibility(View.VISIBLE);
            applyChipStyle(prediction1);
        }
        if (prediction2 != null && caseAdjusted.size() > 1) {
            prediction2.setText(caseAdjusted.get(1));
            prediction2.setVisibility(View.VISIBLE);
            if (predictionSeparator1 != null) predictionSeparator1.setVisibility(View.VISIBLE);
            applyChipStyle(prediction2);
        } else {
            if (prediction2 != null) prediction2.setVisibility(View.GONE);
            if (predictionSeparator1 != null) predictionSeparator1.setVisibility(View.GONE);
        }
        if (prediction3 != null && caseAdjusted.size() > 2) {
            prediction3.setText(caseAdjusted.get(2));
            prediction3.setVisibility(View.VISIBLE);
            if (predictionSeparator2 != null) predictionSeparator2.setVisibility(View.VISIBLE);
            applyChipStyle(prediction3);
        } else {
            if (prediction3 != null) prediction3.setVisibility(View.GONE);
            if (predictionSeparator2 != null) predictionSeparator2.setVisibility(View.GONE);
        }
    }

    private void showDefaultPredictions() {
        if (prediction1 != null) {
            prediction1.setText("Hi");
            prediction1.setVisibility(View.VISIBLE);
            applyChipStyle(prediction1);
        }
        if (prediction2 != null) {
            prediction2.setText("Hey");
            prediction2.setVisibility(View.VISIBLE);
            applyChipStyle(prediction2);
        }
        if (prediction3 != null) {
            prediction3.setText("Hello");
            prediction3.setVisibility(View.VISIBLE);
            applyChipStyle(prediction3);
        }
        if (predictionSeparator1 != null) predictionSeparator1.setVisibility(View.VISIBLE);
        if (predictionSeparator2 != null) predictionSeparator2.setVisibility(View.VISIBLE);
    }

    /**
     * Update Row 2 context actions based on input text and conversation context.
     * Phase 3 Fix - Step 2: Make context actions DYNAMIC based on conversation context.
     */
    private void updateRow2CollapsedContent(String inputText) {
        if (inputText == null || inputText.isEmpty()) {
            showDefaultContextActions();
            return;
        }

        // Determine smart tone based on conversation context
        String suggestedTone = getSuggestedToneFromContext();
        if (smartToneChip != null) {
            smartToneChip.setText("📝 " + suggestedTone);
            smartToneChip.setVisibility(View.VISIBLE);
        }

        // Grammar chip - show with character count if text is long enough
        if (grammarFixChip != null) {
            int wordCount = inputText.trim().split("\\s+").length;
            if (wordCount > 3) {
                grammarFixChip.setText("✓ Grammar");
            } else {
                grammarFixChip.setText("✓ Grammar");
            }
            grammarFixChip.setVisibility(View.VISIBLE);
        }

        // Translate chip - detect if translation might be useful
        if (translateChip != null) {
            String targetLang = getTranslationTargetLanguage();
            translateChip.setText("🌐 " + targetLang);
            translateChip.setVisibility(View.VISIBLE);
        }

        // More CTA always visible
        if (moreCtaCollapsed != null) {
            moreCtaCollapsed.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Get suggested tone based on conversation context.
     * Returns "Professional" for work contexts, "Casual" for friends, etc.
     * Build 7.1: Simplified — MemoryViewData/sharedContextData removed.
     * TODO: Source tone suggestion from ActionTracker or ConversationMatcher.
     */
    private String getSuggestedToneFromContext() {
        // Build 7.1: Default to "Casual" — ActionTracker will personalize this later
        return "Casual";
    }

    /**
     * Get translation target language based on detected language.
     * Build 7.1: Simplified — MemoryViewData/sharedContextData removed.
     * TODO: Source language detection from ConversationMatcher or NLS pipeline.
     */
    private String getTranslationTargetLanguage() {
        // Build 7.1: Default to "Translate" — language detection will be
        // sourced from ConversationMatcher in a future update
        return "Translate";
    }

    private void showDefaultContextActions() {
        // Show default context actions in collapsed state
        if (grammarFixChip != null) {
            grammarFixChip.setText("✓ Grammar");
            grammarFixChip.setVisibility(View.VISIBLE);
        }
        if (translateChip != null) {
            translateChip.setText("🌐 Translate");
            translateChip.setVisibility(View.VISIBLE);
        }
        if (smartToneChip != null) {
            smartToneChip.setText("📝 Professional");
            smartToneChip.setVisibility(View.VISIBLE);
        }
        if (moreCtaCollapsed != null) {
            moreCtaCollapsed.setVisibility(View.VISIBLE);
        }
    }

    private boolean showClipboardIfAvailable() {
        // Skip if accessibility prompt is active - don't override it
        if (isAccessibilityPromptActive) {
            return false;
        }
        try {
            ClipboardManager cb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip() != null) {
                CharSequence clipText = cb.getPrimaryClip().getItemAt(0).coerceToText(context);
                if (clipText != null && clipText.length() > 0) {
                    // Show clipboard in prediction area
                    String truncated = clipText.length() > 20
                        ? clipText.subSequence(0, 20) + "..."
                        : clipText.toString();

                    if (prediction1 != null) {
                        prediction1.setText("Paste: " + truncated);
                        prediction1.setOnClickListener(v -> commitSuggestion(clipText.toString()));
                        applyChipStyle(prediction1);
                    }
                    if (prediction2 != null) prediction2.setVisibility(View.GONE);
                    if (prediction3 != null) prediction3.setVisibility(View.GONE);
                    if (predictionSeparator1 != null) predictionSeparator1.setVisibility(View.GONE);
                    if (predictionSeparator2 != null) predictionSeparator2.setVisibility(View.GONE);

                    setState(BarState.COLLAPSED);
                    return true;
                }
            }
        } catch (Exception e) {
            if (DebugConfig.isDebugMode) {
                Log.e(TAG, "[SAB] Error checking clipboard", e);
            }
        }
        return false;
    }

    // ========== SMART REPLIES ==========

    /**
     * Display AI-generated smart replies in Row 2 (EXPANDED state)
     */
    public void showSmartReplies(List<String> replies) {
        return; // Old layout removed — Manager handles this
    }

    /**
     * Phase 1: Validate replies using ReplyValidator.
     * Filters out banned phrases, enforces variety, and checks quality.
     */
    private List<String> validateReplies(List<String> replies) {
        if (replies == null || replies.isEmpty()) {
            return replies;
        }

        // Determine context for validation
        boolean isCasualContext = true; // Most contexts are casual for a keyboard

        // Validate with context
        ReplyValidator.ValidationResult result = replyValidator.validateReplies(
                replies,
                lastIncomingMessage,
                isCasualContext
        );

        if (DebugConfig.isDebugMode && result.getFilteredCount() > 0) {
            Log.d(TAG, "[SAB] Validation: " + result.getValidCount() + " valid, " +
                    result.getFilteredCount() + " filtered");
        }

        return result.getValidReplies();
    }

    /**
     * Set the last incoming message for validation context.
     * Call this before showing replies.
     */
    public void setLastIncomingMessage(String message) {
        this.lastIncomingMessage = message;
    }

    /**
     * Show hint text when no context is available
     */
    public void showContextHint() {
        if (sabManager != null) {
            sabManager.switchToState(SabState.OV_NO_CONTEXT);
        }
        Log.d(TAG, "[SAB] showContextHint → OV_NO_CONTEXT");
    }

    private void commitSmartReply(String text) {
        if (text == null || mLatinIme == null) return;

        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) return;

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[SAB] Committing smart reply: " + text);
        }

        // Demo logging: Smart reply selected
        DemoLogger.logUserAction(DemoLogger.FLOW_AI_FEATURES, "select_reply", "smart_reply", text);

        // Replace editor text (not append) — Bug #3 fix
        String currentText = ric.getCommitedText();
        int totalLength = currentText != null ? currentText.length() : 0;
        ric.replaceText(0, totalLength, text);
        ric.setSelection(text.length(), text.length());

        // Phase 3: Track reply tap for onboarding progressive features
        AccessibilityPromptTracker tracker = AccessibilityPromptTracker.getInstance(getContext());
        tracker.recordReplyTap();

        // Phase 3: Check if accessibility prompt should appear (deferred permission)
        if (tracker.shouldShowPrompt()) {
            showDeferredAccessibilityPrompt();
        }

        // Phase 3: Show swipe hint on first tap
        showSwipeHintIfNeeded();

        // Phase 3: Update progressive feature disclosure
        updateFeatureVisibility();
    }

    // ========== PHASE 2B: TEXT ACCESS HELPER ==========

    /**
     * Task 2.29: Get current text from the host app's input field via RichInputConnection.
     * Returns null if LatinIME is not set or connection is unavailable.
     */
    private String getCurrentTextFromEditor() {
        if (mLatinIme == null) return null;
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) return null;
        return ric.getCommitedText();
    }

    /**
     * Task 2.29: Replace all text in host app's input field.
     * Used by grammar fix and translation to replace editor content.
     */
    private void replaceEditorText(String newText) {
        if (newText == null || mLatinIme == null) return;
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) return;
        String currentText = ric.getCommitedText();
        int totalLength = currentText != null ? currentText.length() : 0;
        ric.replaceText(0, totalLength, newText);
        ric.setSelection(newText.length(), newText.length());
    }

    // ========== PHASE 2B: GRAMMAR FIX (INLINE) ==========

    /**
     * Task 2.29: Perform inline grammar fix - flash animation → API call → replace text.
     * Called from KeyboardSwitcher's grammar button listener.
     */
    public void performGrammarFix() {
        return; // Old layout removed — Manager handles this
    }

    /**
     * Task 2.29: Show error when user taps CTA with empty input field.
     * Public so KeyboardSwitcher can call it.
     */
    public void showEmptyTextError() {
        android.widget.Toast.makeText(context, "Please type something first", android.widget.Toast.LENGTH_SHORT).show();
        Log.d(TAG, "[SAB] showEmptyTextError: toast shown");
    }

    // ========== PHASE 2B: TONE FLOW ==========

    /**
     * Task 2.30: Apply tone transformation via API and show results in TONE_ACTIVE state.
     * Flow: TONE_PICKER → SHIMMER_LOADING → API → TONE_ACTIVE with suggestions.
     */
    private void applyToneAndShowActive(String toneName) {
        currentActiveTone = toneName;
        String text = getCurrentTextFromEditor();
        if (text == null || text.trim().isEmpty()) {
            showEmptyTextError();
            showRow2State(Row2State.SMART_REPLIES);
            return;
        }

        showRow2State(Row2State.SHIMMER_LOADING);
        SmartAssistantLogger.j4_loadingShown("TONE_" + toneName);

        String systemPrompt = ReplyGenerator.buildTonePrompt(toneName);
        new ClaudeApi().generateReplies(systemPrompt, text, new ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                animationHandler.post(() -> {
                    populateToneActiveRow(toneName, replies);
                    showRow2State(Row2State.TONE_ACTIVE);
                    SmartAssistantLogger.j4_transformComplete(replies.size());
                });
            }

            @Override
            public void onError(String error) {
                animationHandler.post(() -> {
                    showRow2State(Row2State.SMART_REPLIES);
                    SmartAssistantLogger.logError("TONE", error);
                });
            }
        });
    }

    /**
     * Task 2.30: Populate the tone active row with pinned tone chip and suggestion chips.
     * Each suggestion chip taps → replaceEditorText() + return to SMART_REPLIES.
     */
    private void populateToneActiveRow(String toneName, List<String> suggestions) {
        // Set pinned tone chip text
        if (pinnedToneText != null) {
            String emoji = project.witty.keys.app.utils.ToneData.getEmojiForTone(toneName);
            pinnedToneText.setText(emoji + " " + toneName);
        }

        // Clear and populate suggestions container
        if (toneSuggestionsContainer != null) {
            toneSuggestionsContainer.removeAllViews();

            for (String suggestion : suggestions) {
                TextView chip = new TextView(context);
                chip.setText(suggestion.length() > 30 ? suggestion.substring(0, 27) + "..." : suggestion);
                chip.setMaxLines(1);

                // Style: match existing chip pattern
                int paddingH = dpToPx(12);
                int paddingV = dpToPx(8);
                chip.setPadding(paddingH, paddingV, paddingH, paddingV);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

                int surface2 = androidx.core.content.ContextCompat.getColor(context, R.color.wk_surface2);
                int chipBorder = androidx.core.content.ContextCompat.getColor(context, R.color.wk_chip_border);
                int textPrimary = androidx.core.content.ContextCompat.getColor(context, R.color.wk_text);

                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setColor(surface2);
                chipBg.setStroke(dpToPx(1), chipBorder);
                chipBg.setCornerRadius(dpToPx(16));
                chip.setBackground(chipBg);
                chip.setTextColor(textPrimary);

                // Set minimum touch target
                chip.setMinHeight(dpToPx(36));

                // Margin between chips
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.setMarginEnd(dpToPx(8));
                chip.setLayoutParams(lp);

                // Click: replace editor text and return to smart replies
                final String fullSuggestion = suggestion;
                chip.setOnClickListener(v -> {
                    playCtaTapAnimation(v);
                    playCheckmarkAnimation(v);
                    replaceEditorText(fullSuggestion);
                    SmartAssistantLogger.j4_contextActionTapped("TONE_SUGGESTION_SELECTED");

                    // Return to smart replies after a brief delay for visual feedback
                    animationHandler.postDelayed(() -> {
                        showRow2State(Row2State.SMART_REPLIES);
                    }, 400);
                });

                toneSuggestionsContainer.addView(chip);
            }
        }
    }

    // ========== CLICK LISTENER SETUPS (Public API) ==========

    private OnClickListener storedDictationClickListener;
    private OnClickListener storedAiChatClickListener;

    public void setDictationClickListener(OnClickListener listener) {
        this.storedDictationClickListener = listener;
    }

    public void setVoiceClickListener(OnClickListener listener) {
        // Voice uses same listener as dictation for now
        this.storedDictationClickListener = listener;
    }

    public void setAiChatClickListener(OnClickListener listener) {
        this.storedAiChatClickListener = listener;
    }

    public void setTranslateClickListener(OnClickListener listener) {
        return; // Old layout removed — Manager handles this
    }

    public void setGrammarClickListener(OnClickListener listener) {
        return; // Old layout removed — Manager handles this
    }

    public void setToneClickListener(OnClickListener listener) {
        return; // Old layout removed — Manager handles this
    }

    public void setScreenReadClickListener(OnClickListener listener) {
        return; // Old layout removed — Manager handles this
    }


    // ========== VOICE ANIMATION ==========

    public void startVoiceInputAnimation() {
        if (sabManager != null) sabManager.setMicListeningActive(true);
    }

    public void startVoicePromptAnimation() {
        if (sabManager != null) sabManager.setMicListeningActive(true);
    }

    public void stopVoiceAnimation() {
        if (sabManager != null) sabManager.setMicListeningActive(false);
    }

    // ========== TUTORIAL HIGHLIGHT ==========

    public void highlightButton(String buttonType) {
        // TODO: Wire tutorial highlight to Manager views when tutorial is reimplemented
        Log.d(TAG, "[SAB] highlightButton: " + buttonType + " — not yet wired to Manager");
    }

    public void stopHighlight() {
        // TODO: Wire tutorial highlight stop to Manager when tutorial is reimplemented
        Log.d(TAG, "[SAB] stopHighlight: not yet wired to Manager");
    }

    private View getButtonByType(String buttonType) {
        switch (buttonType) {
            case "AI_CHAT":
                return aiChatButton;
            case "TRANSLATE":
                return translateButton;
            case "GRAMMAR":
                return grammarButton;
            case "TONALITY":
                return toneButton;
            case "READ_SCREEN":
                return screenReadButton;
            default:
                return null;
        }
    }

    public boolean isHighlighting() {
        return currentHighlightedView != null && currentHighlightAnimator != null
                && currentHighlightAnimator.isRunning();
    }

    public String getCurrentHighlightedButtonType() {
        return currentHighlightedButtonType;
    }

    // ========== THEME SUPPORT ==========

    @Override
    public void onThemeChanged(Context themedContext) {
        if (sabManager != null && themedContext != null) {
            // Determine isDark from the user's selected keyboard theme
            KeyboardTheme theme = KeyboardTheme.getKeyboardTheme(themedContext);
            boolean isDark;
            if (theme.mThemeId == KeyboardTheme.THEME_ID_LIGHT) {
                isDark = false;
            } else if (theme.mThemeId == KeyboardTheme.THEME_ID_DARK) {
                isDark = true;
            } else {
                // SYSTEM theme: follow device night mode
                int nightFlags = themedContext.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                isDark = (nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES);
            }
            Log.d(TAG, "[SAB] onThemeChanged: themeId=" + theme.mThemeId + " isDark=" + isDark);
            sabManager.onThemeChanged(themedContext, isDark);
        }
    }

    /**
     * Apply theme colors to CustomModeView components.
     * Phase 6 - Theme Integration
     */
    private void applyCustomModeTheme(Context themedContext) {
        int hintTextColor = ThemeUtils.getThemeColor(themedContext, R.attr.utilityRowIconColor);
        int iconColor = ThemeUtils.getThemeColor(themedContext, R.attr.utilityRowIconColor);
        float cornerRadius = getResources().getDimension(R.dimen.button_corner_radius_lxx);

        // Custom prompt input text
        if (customPromptInput != null) {
            customPromptInput.setTextColor(hintTextColor);
            // Create themed background programmatically for CustomModeView input
            Drawable customInputBg = ThemeUtils.createButtonBackground(
                    themedContext,
                    R.attr.themedButtonBackgroundColor,
                    R.attr.themedButtonBackgroundColor,
                    cornerRadius);
            customPromptInput.setBackground(customInputBg);
        }

        // Generate button
        if (generateButton != null) {
            applyChipStyle(generateButton);
        }

        // Cancel button (TextView, use setTextColor instead of setColorFilter)
        if (cancelCustomButton != null) {
            cancelCustomButton.setTextColor(iconColor);
        }
    }

    private void applyThemeRecursively(View view, Context themedContext) {
        if (view == null) return;

        Object backgroundAttrTag = view.getTag(R.id.theme_background_attr);
        if (backgroundAttrTag instanceof Integer) {
            int attrId = (Integer) backgroundAttrTag;
            view.setBackgroundColor(ThemeUtils.getThemeColor(themedContext, attrId));
        }

        if (view instanceof TextView) {
            Object textColorAttrTag = view.getTag(R.id.theme_text_color_attr);
            if (textColorAttrTag instanceof Integer) {
                int attrId = (Integer) textColorAttrTag;
                ((TextView) view).setTextColor(ThemeUtils.getThemeColor(themedContext, attrId));
            }
        }

        if (view instanceof ImageView) {
            Object iconColorAttrTag = view.getTag(R.id.theme_icon_color_attr);
            if (iconColorAttrTag instanceof Integer) {
                int attrId = (Integer) iconColorAttrTag;
                ((ImageView) view).setColorFilter(ThemeUtils.getThemeColor(themedContext, attrId));
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyThemeRecursively(vg.getChildAt(i), themedContext);
            }
        }
    }

    private void applyChipStyle(TextView tv) {
        if (tv == null) return;
        // Use design-spec chip styling (wk_chip_default drawable) instead of theme button colors
        tv.setBackgroundResource(R.drawable.wk_chip_default);
        tv.setTextColor(getResources().getColor(R.color.wk_text, null));

        // Phase 6 fix: Apply icon tint to compound drawables (flag icons on smart replies)
        // Apply flag icon tint to match theme colors
        int iconColor = ThemeUtils.getThemeColor(context, R.attr.utilityRowIconColor);
        Drawable[] drawables = tv.getCompoundDrawables();
        for (Drawable drawable : drawables) {
            if (drawable != null) {
                drawable.mutate().setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN);
                drawable.setAlpha(128); // 50% alpha for subtle flag icons
            }
        }
        // Also handle relative drawables (drawableStart, drawableEnd)
        Drawable[] relativeDrawables = tv.getCompoundDrawablesRelative();
        for (Drawable drawable : relativeDrawables) {
            if (drawable != null) {
                drawable.mutate().setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN);
                drawable.setAlpha(128); // 50% alpha for subtle flag icons
            }
        }
    }

    private void applyChipStyleToAll(TextView... chips) {
        for (TextView chip : chips) {
            applyChipStyle(chip);
        }
    }

    private static void applyIconTint(int color, ImageView... icons) {
        for (ImageView icon : icons) {
            if (icon != null) icon.setColorFilter(color);
        }
    }

    private static void applyTextViewTint(int color, TextView... textViews) {
        for (TextView tv : textViews) {
            if (tv != null) tv.setTextColor(color);
        }
    }

    private int safeThemeColor(Context ctx, int attrId, int fallbackColorRes) {
        TypedValue tv = new TypedValue();
        boolean found = ctx.getTheme() != null && ctx.getTheme().resolveAttribute(attrId, tv, true);
        if (found) {
            if (tv.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(ctx, tv.resourceId);
            } else {
                return tv.data;
            }
        }
        return androidx.core.content.ContextCompat.getColor(ctx, fallbackColorRes);
    }

    // ========== COMMIT LOGIC ==========

    public void setLatinIme(LatinIME ime) {
        mLatinIme = ime;
        Log.d(TAG, "[SAB] setLatinIme: mLatinIme set");
    }

    private void commitSuggestion(String suggestion) {
        if (suggestion == null || mLatinIme == null) return;
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null) return;

        String currentText = ric.getCommitedText();
        int totalLength = currentText != null ? currentText.length() : 0;
        ric.replaceText(0, totalLength, suggestion);
        ric.setSelection(suggestion.length(), suggestion.length());
    }

    private void commitSuggestionSmart(String suggestion) {
        if (suggestion == null || mLatinIme == null) return;
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) return;

        CharSequence before1 = ric.getTextBeforeCursor(1, 0);
        boolean atWordBoundary = (before1 == null || before1.length() == 0
                || Character.isWhitespace(before1.charAt(before1.length() - 1)));

        if (atWordBoundary) {
            CharSequence before = ric.getTextBeforeCursor(256, 0);
            boolean sentenceStart = isSentenceBoundaryBefore(before);
            String toCommit = sentenceStart ? capitalizeFirstWord(suggestion) : suggestion;

            ric.commitText(toCommit + " ", 1);
            showNextWordPredictions(toCommit);
            return;
        }

        // Replace current token
        final int MAX_LOOKBACK = 64;
        CharSequence before = ric.getTextBeforeCursor(MAX_LOOKBACK, 0);
        if (before == null || before.length() == 0) {
            ric.commitText(suggestion + " ", 1);
            showNextWordPredictions(suggestion);
            return;
        }

        int i = before.length() - 1;
        while (i >= 0) {
            char c = before.charAt(i);
            boolean isWordChar = Character.isLetterOrDigit(c) || c == '_' || c == '\'';
            if (!isWordChar) break;
            i--;
        }
        int tokenStartInBefore = i + 1;
        int tokenLen = before.length() - tokenStartInBefore;
        String originalToken = tokenLen > 0 ? before.subSequence(tokenStartInBefore, before.length()).toString() : "";

        String adjusted = matchCase(suggestion, originalToken);

        int selStart = ric.getExpectedSelectionStart();
        int selEnd = ric.getExpectedSelectionEnd();
        if (selStart < 0 || selEnd < 0) {
            ric.commitText(adjusted + " ", 1);
            showNextWordPredictions(adjusted);
            return;
        }

        int absStart = Math.max(0, selStart - tokenLen);
        int absEnd = selEnd;

        ric.beginBatchEdit();
        ric.setSelection(absStart, absEnd);
        ric.commitText(adjusted + " ", 1);
        ric.endBatchEdit();

        showNextWordPredictions(adjusted);
    }

    private void showNextWordPredictions(String prevWord) {
        String key = (prevWord == null ? "" : prevWord).toLowerCase();
        List<String> picks = NEXT_WORDS.get(key);
        if (picks == null || picks.isEmpty()) picks = NEXT_WORDS.get("__default__");
        if (picks == null || picks.isEmpty()) picks = FALLBACK_DEFAULTS;

        List<String> out = new ArrayList<>(3);
        for (String p : picks) {
            if (p == null || p.isEmpty()) continue;
            out.add("i".equals(p) ? "I" : p);
            if (out.size() == 3) break;
        }

        if (out.isEmpty()) return;

        if (prediction1 != null && out.size() > 0) {
            prediction1.setText(out.get(0));
            prediction1.setVisibility(View.VISIBLE);
            applyChipStyle(prediction1);
        }
        if (prediction2 != null && out.size() > 1) {
            prediction2.setText(out.get(1));
            prediction2.setVisibility(View.VISIBLE);
            if (predictionSeparator1 != null) predictionSeparator1.setVisibility(View.VISIBLE);
            applyChipStyle(prediction2);
        }
        if (prediction3 != null && out.size() > 2) {
            prediction3.setText(out.get(2));
            prediction3.setVisibility(View.VISIBLE);
            if (predictionSeparator2 != null) predictionSeparator2.setVisibility(View.VISIBLE);
            applyChipStyle(prediction3);
        }
    }

    // ========== DICTIONARY & SUGGESTIONS ==========

    private void loadDictionary() {
        wordTrie = new Trie();
        try (InputStream is = context.getResources().openRawResource(R.raw.dictionary_google_10000);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                wordTrie.insert(line.trim().toLowerCase());
            }
        } catch (IOException e) {
            if (DebugConfig.isDebugMode) {
                Log.e(TAG, "[SAB] Error loading dictionary", e);
            }
        }
    }

    private void loadEmojiMap() {
        emojiMap = new HashMap<>();
        try (InputStream is = context.getResources().openRawResource(R.raw.emojis)) {
            android.util.JsonReader reader = new android.util.JsonReader(new InputStreamReader(is));
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                String emoji = null;
                List<String> aliases = new ArrayList<>();
                List<String> tags = new ArrayList<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    switch (name) {
                        case "emoji":
                            emoji = reader.nextString();
                            break;
                        case "aliases":
                            reader.beginArray();
                            while (reader.hasNext()) aliases.add(reader.nextString());
                            reader.endArray();
                            break;
                        case "tags":
                            reader.beginArray();
                            while (reader.hasNext()) tags.add(reader.nextString());
                            reader.endArray();
                            break;
                        default:
                            reader.skipValue();
                            break;
                    }
                }
                reader.endObject();
                if (emoji != null) {
                    for (String alias : aliases) emojiMap.put(alias, emoji);
                    for (String tag : tags) emojiMap.put(tag, emoji);
                }
            }
            reader.endArray();
        } catch (IOException e) {
            if (DebugConfig.isDebugMode) {
                Log.e(TAG, "[SAB] Error loading emoji map", e);
            }
        }
    }

    private void seedNextWordTable() {
        NEXT_WORDS.clear();
        NEXT_WORDS.put("__default__", FALLBACK_DEFAULTS);

        // Greetings
        NEXT_WORDS.put("hi", Arrays.asList("there", "!", "how"));
        NEXT_WORDS.put("hey", Arrays.asList("there", "!", "what's"));
        NEXT_WORDS.put("hello", Arrays.asList("there", "!", "again"));

        // Gratitude
        NEXT_WORDS.put("thanks", Arrays.asList("!", "so", "for"));
        NEXT_WORDS.put("thank", Arrays.asList("you", "you!", "you so much"));

        // Pronouns
        NEXT_WORDS.put("i", Arrays.asList("am", "will", "have"));
        NEXT_WORDS.put("you", Arrays.asList("are", "can", "have"));
        NEXT_WORDS.put("he", Arrays.asList("is", "was", "has"));
        NEXT_WORDS.put("she", Arrays.asList("is", "was", "has"));
        NEXT_WORDS.put("it", Arrays.asList("is", "was", "seems"));
        NEXT_WORDS.put("we", Arrays.asList("are", "should", "will"));
        NEXT_WORDS.put("they", Arrays.asList("are", "were", "have"));

        // Common verbs
        NEXT_WORDS.put("can", Arrays.asList("you", "we", "i"));
        NEXT_WORDS.put("will", Arrays.asList("you", "we", "be"));
        NEXT_WORDS.put("do", Arrays.asList("you", "we", "it"));
        NEXT_WORDS.put("have", Arrays.asList("you", "we", "been"));
        NEXT_WORDS.put("is", Arrays.asList("it", "this", "that"));

        // Question words
        NEXT_WORDS.put("what", Arrays.asList("is", "are", "do"));
        NEXT_WORDS.put("when", Arrays.asList("is", "will", "are"));
        NEXT_WORDS.put("where", Arrays.asList("is", "are", "can"));
        NEXT_WORDS.put("why", Arrays.asList("is", "are", "did"));
        NEXT_WORDS.put("how", Arrays.asList("are", "is", "do"));

        // Conjunctions
        NEXT_WORDS.put("and", Arrays.asList("then", "also", "the"));
        NEXT_WORDS.put("but", Arrays.asList("i", "we", "it's"));
        NEXT_WORDS.put("so", Arrays.asList("i", "we", "that"));

        // Prepositions
        NEXT_WORDS.put("to", Arrays.asList("the", "be", "do"));
        NEXT_WORDS.put("for", Arrays.asList("the", "you", "now"));
        NEXT_WORDS.put("with", Arrays.asList("you", "the", "me"));

        // Affirmations
        NEXT_WORDS.put("yes", Arrays.asList("please", "this", "definitely"));
        NEXT_WORDS.put("no", Arrays.asList("problem", "worries", "thanks"));
        NEXT_WORDS.put("ok", Arrays.asList("thanks", "then", "cool"));
        NEXT_WORDS.put("sure", Arrays.asList("thing", "i'll", "can"));

        // Common phrases
        NEXT_WORDS.put("good", Arrays.asList("morning", "afternoon", "evening"));
        NEXT_WORDS.put("see", Arrays.asList("you", "you soon", "you later"));
        NEXT_WORDS.put("take", Arrays.asList("care", "it", "a"));
    }

    private List<String> generateSuggestions(String lastWord) {
        List<String> suggestions = new ArrayList<>();
        if (lastWord == null || lastWord.isEmpty()) return suggestions;

        List<String> dictionaryMatches = wordTrie.searchPrefix(lastWord.toLowerCase());
        int count = 0;
        for (String match : dictionaryMatches) {
            suggestions.add(match);
            if (++count >= 3) break;
        }

        List<String> emojiMatches = generateEmojiSuggestions(lastWord.toLowerCase());
        suggestions.addAll(emojiMatches);
        return suggestions;
    }

    private List<String> generateEmojiSuggestions(String query) {
        List<String> emojiSuggestions = new ArrayList<>();
        if (emojiMap != null && emojiMap.containsKey(query)) {
            emojiSuggestions.add(emojiMap.get(query));
        }
        return emojiSuggestions;
    }

    // ========== STRING UTILITIES ==========

    private String getLastWord(String input) {
        if (input == null || input.isEmpty()) return "";
        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) return "";
        String[] words = trimmedInput.split("\\s+");
        return words[words.length - 1];
    }

    private String getLastWordRaw(String input) {
        if (input == null) return "";
        int i = input.length() - 1;
        while (i >= 0 && Character.isWhitespace(input.charAt(i))) i--;
        if (i < 0) return "";
        int end = i;
        while (i >= 0) {
            char c = input.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '\'' || c == '_') i--;
            else break;
        }
        return input.substring(i + 1, end + 1);
    }

    private boolean isStartOfSentence(CharSequence beforeCursor) {
        if (beforeCursor == null || beforeCursor.length() == 0) return true;
        int i = beforeCursor.length() - 1;
        while (i >= 0 && Character.isWhitespace(beforeCursor.charAt(i))) i--;
        if (i < 0) return true;
        char c = beforeCursor.charAt(i);
        return c == '.' || c == '!' || c == '?' || c == '\n';
    }

    private boolean isSentenceBoundaryBefore(CharSequence before) {
        if (before == null || before.length() == 0) return true;
        int i = before.length() - 1;
        while (i >= 0 && Character.isWhitespace(before.charAt(i))) i--;
        if (i < 0) return true;
        char c = before.charAt(i);
        return c == '.' || c == '!' || c == '?' || c == '\n';
    }

    private String adjustCaseForContext(String suggestion, String typedTokenRaw, boolean startOfSentence) {
        if (suggestion == null || suggestion.isEmpty()) return suggestion;
        if (!Character.isLetter(suggestion.codePointAt(0))) return suggestion;

        boolean userForcedCap = typedTokenRaw != null && typedTokenRaw.length() > 0
                && Character.isUpperCase(typedTokenRaw.charAt(0));
        boolean cap = startOfSentence || userForcedCap;

        if (!cap) return suggestion;
        int cp = suggestion.codePointAt(0);
        int upper = Character.toUpperCase(cp);
        return new StringBuilder()
                .appendCodePoint(upper)
                .append(suggestion.substring(Character.charCount(cp)))
                .toString();
    }

    private static String capitalizeFirstWord(String s) {
        if (s == null || s.isEmpty()) return s;
        char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) {
            if (Character.isLetter(a[i])) {
                a[i] = Character.toUpperCase(a[i]);
                break;
            }
        }
        return new String(a);
    }

    private static String matchCase(String suggestion, String originalToken) {
        if (suggestion == null || suggestion.isEmpty() || originalToken == null || originalToken.isEmpty())
            return suggestion;
        if (hasOnlyCase(originalToken, true)) return suggestion.toUpperCase();
        if (hasOnlyCase(originalToken, false)) return suggestion.toLowerCase();
        if (isTitleCaseWord(originalToken)) return capitalizeFirstWord(suggestion);
        return suggestion;
    }

    private static boolean hasOnlyCase(String s, boolean upper) {
        if (s == null || s.isEmpty()) return false;
        boolean hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (upper ? !Character.isUpperCase(c) : !Character.isLowerCase(c)) return false;
            }
        }
        return hasLetter;
    }

    private static boolean isTitleCaseWord(String s) {
        if (s == null || s.isEmpty()) return false;
        int i = 0;
        while (i < s.length() && !Character.isLetter(s.charAt(i))) i++;
        if (i >= s.length()) return false;
        if (!Character.isUpperCase(s.charAt(i))) return false;
        for (int j = i + 1; j < s.length(); j++) {
            char c = s.charAt(j);
            if (Character.isLetter(c) && !Character.isLowerCase(c)) return false;
        }
        return true;
    }

    // ========== VOICE MODE ==========

    public void setVoiceMode(boolean enabled) {
        this.voiceMode = enabled;
    }

    public boolean isVoiceMode() {
        return this.voiceMode;
    }

    // ========== UTILITY METHODS ==========

    // Note: dp(int value) is defined above at line ~587 for tonality popup

    public int getRow1Height() {
        if (row1Container != null) {
            return row1Container.getHeight();
        }
        return 0;
    }

    public int getRow2Height() {
        if (row2Container != null) {
            return row2Container.getHeight();
        }
        return 0;
    }

    public int getTotalHeight() {
        return getRow1Height() + getRow2Height();
    }

    // ========== SUBSCRIPTION CHECK (Legacy from SuggestionRow) ==========

    public boolean checkUserAndSubscription() {
        // DEBUG: bypass subscription for emulator UI testing
        if (project.witty.keys.BuildConfig.DEBUG) return true;

        if (tutorialManager != null && tutorialManager.isTutorialMode()) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[SAB] Tutorial mode active - Bypassing subscription check");
            }
            return true;
        }

        try {
            EncryptedPreferences.initialize(context);
        } catch (Throwable ignored) {
        }

        User user = EncryptedPreferences.getUserLoggedInInfo();
        if (user == null) {
            return false;
        }

        EncryptedPreferences.FreeTrialInfo trialInfo = EncryptedPreferences.getFreeTrialInfo();
        EncryptedPreferences.SubscriptionInfo subscriptionInfo = EncryptedPreferences.getSubscriptionInfo();

        if (trialInfo != null && trialInfo.isFreeTrialEnded()) {
            return false;
        }

        if (subscriptionInfo != null
                && !Subscription.SubscriptionStatus.ACTIVE.toString().equals(subscriptionInfo.getStatus())) {
            return false;
        }

        return true;
    }

    public boolean requireSubscriptionThenRun(Runnable action) {
        if (checkUserAndSubscription()) {
            try {
                action.run();
            } catch (Throwable t) {
                if (DebugConfig.isDebugMode) {
                    Log.e(TAG, "[SAB] AI action failed", t);
                }
            }
            return true;
        }
        return false;
    }

    // ========== LEGACY COMPATIBILITY METHODS (from SuggestionRow) ==========

    /**
     * Update prediction suggestions - legacy compatibility method.
     * Used for displaying status messages or suggestions.
     */
    public void updateSuggestions(List<String> suggestions) {
        // Used by voice mode to show status messages ("Listening...", etc.)
        if (sabManager != null && suggestions != null && !suggestions.isEmpty()) {
            String[] preds = suggestions.toArray(new String[0]);
            sabManager.setPredictions(preds);
        }
    }

    /**
     * Reset to default text suggestions state - legacy compatibility method.
     */
    public void resetToTextSuggestions() {
        if (sabManager != null) {
            sabManager.switchToState(SabState.OV_EXPANDED);
        }
    }

    /**
     * Show AI inline response in the suggestion area - legacy compatibility method.
     */
    public void showAiInlineResponse(String prompt, String response) {
        if (sabManager != null && response != null && !response.isEmpty()) {
            List<SmartAssistantBarManager.ReplyChip> chips = new ArrayList<>();
            chips.add(new SmartAssistantBarManager.ReplyChip(response, null));
            sabManager.setReplyChips(chips);
            sabManager.switchToState(SabState.OV_EXPANDED);
            Log.d(TAG, "[SAB] showAiInlineResponse: pushed response to OV_EXPANDED");
        }
    }

    /**
     * Show AI inline response (single parameter version).
     */
    public void showAiInlineResponse(String response) {
        showAiInlineResponse(null, response);
    }

    /**
     * Refresh dynamic Row 2 chips (called after AI actions, clipboard changes, or keyboard show).
     */
    public void refreshDynamicRow2() {
        if (sabManager != null) {
            sabManager.refreshDynamicRow2();
        }
    }

    // ========== API ERROR HANDLING ==========

    /**
     * Handle API error - shows error state in OriginalView Row 2.
     */
    public void onApiError(String errorMessage) {
        String errorType = "API_ERROR";
        String displayMessage = errorMessage;
        if (errorMessage != null && errorMessage.contains(": ")) {
            String[] parts = errorMessage.split(": ", 2);
            errorType = parts[0];
            displayMessage = parts.length > 1 ? parts[1] : errorMessage;
        }
        SmartAssistantLogger.j8_apiCallFailed(errorType, displayMessage);
        Log.e(TAG, "[SAB] API Error: " + errorType + " - " + displayMessage);

        if (sabManager != null) {
            sabManager.switchToState(SabState.OV_ERROR);
        }
    }

    /** [E2E TEST SUPPORT] Get SAB manager for state query/history */
    public SmartAssistantBarManager getSabManager() {
        return sabManager;
    }

    /**
     * [E2E TEST SUPPORT] Get current reply chips as a list of strings.
     * Replaces getSharedContextData().getQuickReplies() which was removed with MemoryViewData.
     */
    public List<String> getCurrentReplies() {
        if (sabManager != null) {
            return sabManager.getCurrentReplyTexts();
        }
        return null;
    }

    /**
     * Ensure OriginalView is shown with OV_EXPANDED.
     * Called when keyboard reopens.
     */
    public void showOriginalViewWithContext() {
        if (sabManager != null) {
            // Guard: skip if already in OV_EXPANDED to prevent Row 2 flicker on keystroke
            SabState current = sabManager.getCurrentState();
            if (current == SabState.OV_EXPANDED || current == SabState.OV_MILESTONE || current == SabState.OV_BRAIN_BLINK) {
                Log.d(TAG, "[SAB] showOriginalViewWithContext — already " + current.name() + ", skipping");
                return;
            }
            // Also skip if in a tone-active state (user is viewing tone suggestions)
            if (current == SabState.CTA_TONE_ACTIVE) {
                Log.d(TAG, "[SAB] showOriginalViewWithContext — in tone active, skipping");
                return;
            }
            sabManager.switchToState(SabState.OV_EXPANDED);
            Log.d(TAG, "[SAB] showOriginalViewWithContext → OV_EXPANDED");
        }
    }

    /**
     * Get the recent actions tracker for external callers.
     */
    public RecentAiActionsTracker getRecentActionsTracker() {
        return recentActionsTracker;
    }

    // ========== TASK 4: RECENT AI ACTIONS TRACKER ==========

    /**
     * Tracks recently used AI actions for display in Row 2.
     * Uses LRU (Least Recently Used) eviction with max 8 entries.
     */
    static class RecentAiActionsTracker {
        private static final String PREFS_KEY = "wk_recent_ai_actions";
        private static final int MAX_ENTRIES = 8;

        private LinkedHashMap<String, AiAction> actions;
        private SharedPreferences prefs;

        static class AiAction {
            String id;
            String label;
            String emoji;
            long timestamp;

            AiAction(String id, String label, String emoji) {
                this.id = id;
                this.label = label;
                this.emoji = emoji;
                this.timestamp = System.currentTimeMillis();
            }
        }

        RecentAiActionsTracker(SharedPreferences prefs) {
            this.prefs = prefs;
            this.actions = new LinkedHashMap<String, AiAction>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, AiAction> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };
            loadFromPrefs();
        }

        void recordAction(String id, String label, String emoji) {
            actions.put(id, new AiAction(id, label, emoji));
            saveToPrefs();
        }

        List<AiAction> getRecentActions() {
            List<AiAction> list = new ArrayList<>(actions.values());
            Collections.reverse(list);
            return list;
        }

        private void saveToPrefs() {
            try {
                JSONArray array = new JSONArray();
                for (AiAction action : actions.values()) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", action.id);
                    obj.put("label", action.label);
                    obj.put("emoji", action.emoji);
                    obj.put("timestamp", action.timestamp);
                    array.put(obj);
                }
                prefs.edit().putString(PREFS_KEY, array.toString()).apply();
            } catch (JSONException e) {
                Log.e("RecentAiActionsTracker", "Save error", e);
            }
        }

        private void loadFromPrefs() {
            try {
                String json = prefs.getString(PREFS_KEY, "[]");
                JSONArray array = new JSONArray(json);
                actions.clear();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    AiAction action = new AiAction(
                        obj.getString("id"),
                        obj.getString("label"),
                        obj.getString("emoji")
                    );
                    action.timestamp = obj.getLong("timestamp");
                    actions.put(action.id, action);
                }
            } catch (JSONException e) {
                Log.e("RecentAiActionsTracker", "Load error", e);
            }
        }
    }
}
