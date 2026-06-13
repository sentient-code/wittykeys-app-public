package project.witty.keys.keyboard.AssistantViews;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import project.witty.keys.BuildConfig;
import project.witty.keys.R;
import project.witty.keys.app.context.ConversationMatcher;
import project.witty.keys.app.helpers.ActionTracker;
import project.witty.keys.app.helpers.ChipData;
import project.witty.keys.debug.TestModeController;
import project.witty.keys.keyboard.internal.InternalInputView;

import java.util.ArrayList;
import java.util.List;

import static project.witty.keys.keyboard.AssistantViews.SabState.*;

/**
 * Manages all UI state switching for the SmartAssistantBar.
 * Inflates wk_smart_assistant_bar.xml and handles 28 states.
 */
public class SmartAssistantBarManager {

    // ══════════════════════════════════════════════════
    // Callback interface for button/chip actions
    // ══════════════════════════════════════════════════

    public interface SabActionCallback {
        void onBrainTapped();
        void onToneTapped();
        void onGrammarTapped();
        void onTranslateTapped();
        void onCollapseTapped();
        void onExpandTapped();
        void onReplyChipTapped(String replyText);
        void onCustomTapped();
        void onCustomModeEntered(SabState customState);
        void onMoreTapped();
        void onToneChipSelected(String toneName);
        void onLanguageChipSelected(String language);
        void onToneActiveCloseTapped();
        void onRegenTapped();
        void onAiChatTapped();
        void onDictationTapped();
        void onAccPromptEnableTapped();
        void onCustomGenerateTapped();
        void onCustomCancelTapped();
        void onMvReplyFlagged(String replyText);
        void onPredictionTapped(String text);
        void onDirectToneTapped(String toneName);
        void onDirectTranslateTapped(String languageCode);
        void onCustomPromptRerun(String prompt);
        void onCustomModeTapped();
    }

    private static final String TAG = "SAB";
    private static final String[] STARTER_PREDICTIONS = new String[]{"Hi", "Hey", "Hello"};

    // Canonical tone emoji — must match populateToneChips() hardcoded array exactly
    private static final java.util.Map<String, String> TONE_EMOJI;
    static {
        TONE_EMOJI = new java.util.HashMap<>();
        TONE_EMOJI.put("Casual", "😎"); TONE_EMOJI.put("Professional", "💼");
        TONE_EMOJI.put("Savage", "🔥"); TONE_EMOJI.put("Sarcastic", "🙄");
        TONE_EMOJI.put("Calm", "🧘"); TONE_EMOJI.put("Flirty", "💕");
        TONE_EMOJI.put("Formal", "🎭"); TONE_EMOJI.put("Playful", "🎮");
        TONE_EMOJI.put("Sassy", "💅"); TONE_EMOJI.put("Teasing", "😜");
        TONE_EMOJI.put("Quirky", "🤪"); TONE_EMOJI.put("Concise", "📝");
        TONE_EMOJI.put("Romantic", "💝"); TONE_EMOJI.put("Descriptive", "📖");
        TONE_EMOJI.put("Persuasive", "🎯"); TONE_EMOJI.put("Witty", "🧠");
        TONE_EMOJI.put("Inspirational", "✨"); TONE_EMOJI.put("Technical", "💻");
        TONE_EMOJI.put("Urgent", "🚨"); TONE_EMOJI.put("Curious", "🧐");
        TONE_EMOJI.put("Polite", "🎩");
    }

    private Context context;
    private final View rootView;
    private SabActionCallback callback;

    // Current state
    private SabState currentState = OV_EXPANDED;
    private SabState currentCustomOrigin = null;

    /** [E2E TEST SUPPORT] Tracks state transitions for lifecycle test assertions */
    private final List<String> stateHistory = new ArrayList<>();

    // Data
    private String contactName = "";
    private String summaryText = "";
    private String emotionEmoji = "";
    private String emotionLabel = "";
    private int emotionColorResId = R.color.wk_text2;
    private List<ReplyChip> replies = new ArrayList<>();
    private List<ReplyChip> replyChips = new ArrayList<>();
    private List<ReplyChip> toneSuggestions = new ArrayList<>();
    // Cached dynamic Row 2 chips — refreshed after every AI action, used synchronously on keystrokes
    private List<ChipData> dynamicChipsCache = null;
    private String activeToneName = null;  // Tracks which tone is active (all 21 tones)
    private String activeToneEmoji = null;
    private String[] currentPredictions = STARTER_PREDICTIONS.clone();

    // ══════ Top-level views ══════
    private final View originalView;
    private final TextView milestoneToast;

    // ══════ OriginalView — Row 1 views ══════
    private final LinearLayout ovRow1;
    private final LinearLayout ovRow1CtaButtons;
    private final View ovSpacerLeft;
    private final LinearLayout ovPreds;
    private final View ovSpacerRight;
    private final TextView ovCollapseBtn;
    private final TextView ovExpandBtn;
    private final ImageView ovBrain;
    private final TextView ovToneBtn;
    private final TextView ovTranslateBtn;
    private final TextView ovGrammarBtn;
    private final TextView ovAiBtn;
    private final View ovDivider;
    private final ImageView ovMicCollapsed;
    private final ImageView ovMicBtn;

    // ══════ OriginalView — Row 2 views ══════
    private final HorizontalScrollView ovRow2Chips;
    private final LinearLayout ovRow2ChipsContainer;
    private final HorizontalScrollView ovRow2TonePicker;
    private final LinearLayout ovToneChipsContainer;
    private final HorizontalScrollView ovRow2LangPicker;
    private final LinearLayout ovLangChipsContainer;
    private final LinearLayout ovRow2Shimmer;
    private final LinearLayout ovRow2Error;
    private final LinearLayout ovRow2AccPrompt;
    private final LinearLayout ovRow2Custom;
    private final InternalInputView ovCustomInput;
    private final View ovCustomGenerate;
    private final View ovCustomCancel;
    private final LinearLayout ovRow2ToneActive;
    private final LinearLayout ovRow2NoContext;

    // ══════ Tone Active Row views ══════
    private final TextView ovRegenBtn;
    private final LinearLayout ovTonePinned;
    private final TextView ovTonePinnedLabel;
    private final TextView ovTonePinnedClose;
    private final LinearLayout ovToneSuggestionsShimmer;
    private final HorizontalScrollView ovToneSuggestionsScroll;
    private final LinearLayout ovToneSuggestionsContainer;

    // ══════ Translate Active Row views ══════
    private final LinearLayout ovRow2TranslateActive;
    private final LinearLayout ovTranslatePinned;
    private final TextView ovTranslatePinnedLabel;
    private final TextView ovTranslatePinnedClose;
    private final LinearLayout ovTranslateShimmer;
    private final LinearLayout ovTranslateResult;
    private final TextView ovTranslateText;
    private final TextView ovTranslateApply;
    private final TextView ovTranslateCopy;

    // ══════ Grammar Active Row views ══════
    private final LinearLayout ovRow2GrammarActive;
    private final LinearLayout ovGrammarPinned;
    private final TextView ovGrammarPinnedLabel;
    private final TextView ovGrammarPinnedClose;
    private final LinearLayout ovGrammarShimmer;
    private final LinearLayout ovGrammarResult;
    private final TextView ovGrammarText;
    private final TextView ovGrammarApply;
    private final TextView ovGrammarCopy;

    // Auto-hide handler (kept for future use)
    private final android.os.Handler statsAutoHideHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    // Shimmer views for animation
    private View shimmer1, shimmer2, shimmer3;

    // Brain blink animator reference (for cleanup)
    private AnimatorSet brainBlinkAnimator;

    // ══════ Bottom Sheet Popup ══════
    private PopupWindow bottomSheetPopup;
    private View bottomSheetOverlay;
    private View bottomSheetView;
    private boolean isBottomSheetDismissing = false;
    private SabState bottomSheetState = null;

    // Bottom sheet dismiss callback
    public interface BottomSheetDismissCallback {
        void onDismissed();
    }
    private BottomSheetDismissCallback bottomSheetDismissCallback;

    private String appName = "";

    /**
     * Constructor: takes an already-inflated wk_smart_assistant_bar.xml root view.
     */
    public SmartAssistantBarManager(View sabView) {
        this.rootView = sabView;
        this.context = sabView.getContext();

        // Top-level
        originalView = sabView.findViewById(R.id.wk_original_view);
        milestoneToast = sabView.findViewById(R.id.wk_milestone_toast);

        // OriginalView Row 1
        ovRow1 = sabView.findViewById(R.id.wk_ov_row1);
        ovRow1CtaButtons = sabView.findViewById(R.id.wk_ov_row1_cta_buttons);
        ovSpacerLeft = sabView.findViewById(R.id.wk_ov_spacer_left);
        ovPreds = sabView.findViewById(R.id.wk_ov_preds);
        ovSpacerRight = sabView.findViewById(R.id.wk_ov_spacer_right);
        ovCollapseBtn = sabView.findViewById(R.id.wk_ov_collapse_btn);
        ovExpandBtn = sabView.findViewById(R.id.wk_ov_expand_btn);
        ovBrain = sabView.findViewById(R.id.wk_ov_brain);
        ovToneBtn = sabView.findViewById(R.id.wk_ov_tone_btn);
        ovTranslateBtn = sabView.findViewById(R.id.wk_ov_translate_btn);
        ovGrammarBtn = sabView.findViewById(R.id.wk_ov_grammar_btn);
        ovAiBtn = sabView.findViewById(R.id.wk_ov_ai_btn);
        ovDivider = sabView.findViewById(R.id.wk_ov_divider);
        ovMicCollapsed = sabView.findViewById(R.id.wk_ov_mic_collapsed);
        ovMicBtn = sabView.findViewById(R.id.wk_ov_mic_btn);

        // OriginalView Row 2
        ovRow2Chips = sabView.findViewById(R.id.wk_ov_row2_chips);
        ovRow2ChipsContainer = sabView.findViewById(R.id.wk_ov_row2_chips_container);
        ovRow2TonePicker = sabView.findViewById(R.id.wk_ov_row2_tone_picker);
        ovToneChipsContainer = sabView.findViewById(R.id.wk_ov_tone_chips_container);
        ovRow2LangPicker = sabView.findViewById(R.id.wk_ov_row2_lang_picker);
        ovLangChipsContainer = sabView.findViewById(R.id.wk_ov_lang_chips_container);
        ovRow2Shimmer = sabView.findViewById(R.id.wk_ov_row2_shimmer);
        ovRow2Error = sabView.findViewById(R.id.wk_ov_row2_error);
        ovRow2AccPrompt = sabView.findViewById(R.id.wk_ov_row2_acc_prompt);
        ovRow2Custom = sabView.findViewById(R.id.wk_ov_row2_custom);
        ovCustomInput = sabView.findViewById(R.id.wk_ov_custom_input);
        ovCustomGenerate = sabView.findViewById(R.id.wk_ov_custom_generate);
        ovCustomCancel = sabView.findViewById(R.id.wk_ov_custom_cancel);
        ovRow2ToneActive = sabView.findViewById(R.id.wk_ov_row2_tone_active);
        ovRow2NoContext = sabView.findViewById(R.id.wk_ov_row2_no_context);

        // Tone Active Row
        ovRegenBtn = sabView.findViewById(R.id.wk_ov_regen_btn);
        ovTonePinned = sabView.findViewById(R.id.wk_ov_tone_pinned);
        ovTonePinnedLabel = sabView.findViewById(R.id.wk_ov_tone_pinned_label);
        ovTonePinnedClose = sabView.findViewById(R.id.wk_ov_tone_pinned_close);
        ovToneSuggestionsShimmer = sabView.findViewById(R.id.wk_ov_tone_suggestions_shimmer);
        ovToneSuggestionsScroll = sabView.findViewById(R.id.wk_ov_tone_suggestions_scroll);
        ovToneSuggestionsContainer = sabView.findViewById(R.id.wk_ov_tone_suggestions_container);

        // Translate Active Row
        ovRow2TranslateActive = sabView.findViewById(R.id.wk_ov_row2_translate_active);
        ovTranslatePinned = sabView.findViewById(R.id.wk_ov_translate_pinned);
        ovTranslatePinnedLabel = sabView.findViewById(R.id.wk_ov_translate_pinned_label);
        ovTranslatePinnedClose = sabView.findViewById(R.id.wk_ov_translate_pinned_close);
        ovTranslateShimmer = sabView.findViewById(R.id.wk_ov_translate_shimmer);
        ovTranslateResult = sabView.findViewById(R.id.wk_ov_translate_result);
        ovTranslateText = sabView.findViewById(R.id.wk_ov_translate_text);
        ovTranslateApply = sabView.findViewById(R.id.wk_ov_translate_apply);
        ovTranslateCopy = sabView.findViewById(R.id.wk_ov_translate_copy);

        // Grammar Active Row
        ovRow2GrammarActive = sabView.findViewById(R.id.wk_ov_row2_grammar_active);
        ovGrammarPinned = sabView.findViewById(R.id.wk_ov_grammar_pinned);
        ovGrammarPinnedLabel = sabView.findViewById(R.id.wk_ov_grammar_pinned_label);
        ovGrammarPinnedClose = sabView.findViewById(R.id.wk_ov_grammar_pinned_close);
        ovGrammarShimmer = sabView.findViewById(R.id.wk_ov_grammar_shimmer);
        ovGrammarResult = sabView.findViewById(R.id.wk_ov_grammar_result);
        ovGrammarText = sabView.findViewById(R.id.wk_ov_grammar_text);
        ovGrammarApply = sabView.findViewById(R.id.wk_ov_grammar_apply);
        ovGrammarCopy = sabView.findViewById(R.id.wk_ov_grammar_copy);

        // Shimmer views (children of ovRow2Shimmer)
        if (ovRow2Shimmer.getChildCount() >= 3) {
            shimmer1 = ovRow2Shimmer.getChildAt(0);
            shimmer2 = ovRow2Shimmer.getChildAt(1);
            shimmer3 = ovRow2Shimmer.getChildAt(2);
        }
    }

    // ══════════════════════════════════════════════════
    // Callback setter + Click listener wiring
    // ══════════════════════════════════════════════════

    public void setActionCallback(SabActionCallback cb) {
        this.callback = cb;
        setupClickListeners();
    }

    private void setupClickListeners() {
        if (callback == null) return;
        Log.d(TAG, "setupClickListeners: wiring all button click handlers");

        // Row 1 CTA buttons
        if (ovBrain != null) ovBrain.setOnClickListener(v -> callback.onBrainTapped());
        if (ovToneBtn != null) ovToneBtn.setOnClickListener(v -> callback.onToneTapped());
        if (ovGrammarBtn != null) ovGrammarBtn.setOnClickListener(v -> callback.onGrammarTapped());
        if (ovTranslateBtn != null) ovTranslateBtn.setOnClickListener(v -> callback.onTranslateTapped());

        // Collapse / Expand
        if (ovCollapseBtn != null) ovCollapseBtn.setOnClickListener(v -> callback.onCollapseTapped());
        if (ovExpandBtn != null) ovExpandBtn.setOnClickListener(v -> callback.onExpandTapped());

        // AI Chat button
        if (ovAiBtn != null) ovAiBtn.setOnClickListener(v -> callback.onAiChatTapped());

        // Dictation / Mic button (collapsed state)
        if (ovMicCollapsed != null) {
            ovMicCollapsed.setOnClickListener(v -> callback.onDictationTapped());
        }
        if (ovMicBtn != null) {
            ovMicBtn.setOnClickListener(v -> callback.onDictationTapped());
        }

        // Tone Active row controls
        if (ovRegenBtn != null) ovRegenBtn.setOnClickListener(v -> callback.onRegenTapped());
        if (ovTonePinnedClose != null) ovTonePinnedClose.setOnClickListener(v -> callback.onToneActiveCloseTapped());

        // Custom mode: Generate and Cancel buttons
        if (ovCustomGenerate != null) ovCustomGenerate.setOnClickListener(v -> callback.onCustomGenerateTapped());
        if (ovCustomCancel != null) ovCustomCancel.setOnClickListener(v -> callback.onCustomCancelTapped());
    }

    // ══════════════════════════════════════════════════
    // §5.2 — Master Switch Method
    // ══════════════════════════════════════════════════

    /**
     * Switch to any SAB state. This is the ONLY method that should change view visibility.
     * All other methods call this.
     */
    public void switchToState(SabState state) {
        Log.d(TAG, "switchToState: " + state.name());

        // [E2E] Track state transition and log for lifecycle tests
        stateHistory.add(state.name());
        if (BuildConfig.DEBUG) {
            Log.i("WK_E2E", "[APP] State transition: "
                + (currentState != null ? currentState.name() : "null") + " → " + state.name());
        }

        performStateSwitch(state);

        currentState = state;
    }

    /** [E2E TEST SUPPORT] Get state transition history */
    public List<String> getStateHistory() {
        return new ArrayList<>(stateHistory);
    }

    /** [E2E TEST SUPPORT] Clear state history between tests */
    public void clearStateHistory() {
        stateHistory.clear();
        Log.i("WK_E2E", "[APP] State history cleared");
    }

    /** [E2E TEST SUPPORT] Get current reply chip texts as strings */
    public List<String> getCurrentReplyTexts() {
        List<String> texts = new ArrayList<>();
        if (replyChips == null || replyChips.isEmpty()) {
            return texts;
        }
        for (ReplyChip chip : replyChips) {
            texts.add(chip.text);
        }
        return texts;
    }

    /**
     * Actually perform the view visibility swap and configuration.
     * Extracted from the original switchToState() body.
     */
    private void performStateSwitch(SabState state) {
        // Cancel brain blink animation if switching away from OV_BRAIN_BLINK
        if (currentState == OV_BRAIN_BLINK && state != OV_BRAIN_BLINK) {
            stopBrainBlinkAnimation();
        }

        // Dismiss bottom sheet PopupWindow if switching away from a bottom sheet state
        if (isBottomSheetShowing() && state != BS_ACC_CONSENT) {
            dismissBottomSheet();
        }

        // Step 1: Hide ALL top-level views then show correct one
        originalView.setVisibility(View.GONE);
        milestoneToast.setVisibility(View.GONE);

        // Step 2: Show the correct top-level view
        switch (state) {
            case BS_ACC_CONSENT:
                originalView.setVisibility(View.VISIBLE);
                configureOriginalView(state);
                // Delay bottom sheet creation to allow IME window to fully settle
                // IME PopupWindows get auto-dismissed if shown during layout transitions
                rootView.postDelayed(() -> showBottomSheet(SabState.BS_ACC_CONSENT), 500);
                break;

            default:
                // All OV and CTA states
                originalView.setVisibility(View.VISIBLE);
                configureOriginalView(state);
                break;
        }

        if (state == OV_MILESTONE) {
            showMilestoneToast("🎉 50 replies sent!");
        }
    }

    /**
     * Animate brain icon transition when crossing MV↔OV boundary.
     * Outgoing brain shrinks + fades, then views swap, then incoming brain expands + fades in.
     */
    // ══════════════════════════════════════════════════
    // §5.4 — OriginalView Configuration
    // ══════════════════════════════════════════════════

    private void configureOriginalView(SabState state) {
        // Step 1: Configure Row 1
        configureRow1(state);

        // Show row divider between Row 1 and Row 2
        if (ovDivider != null) {
            ovDivider.setVisibility(View.VISIBLE);
        }

        // Step 2: Hide ALL Row 2 states
        ovRow2Chips.setVisibility(View.GONE);
        ovRow2TonePicker.setVisibility(View.GONE);
        ovRow2LangPicker.setVisibility(View.GONE);
        ovRow2Shimmer.setVisibility(View.GONE);
        ovRow2Error.setVisibility(View.GONE);
        ovRow2AccPrompt.setVisibility(View.GONE);
        ovRow2Custom.setVisibility(View.GONE);
        if (ovRow2GrammarActive != null) ovRow2GrammarActive.setVisibility(View.GONE);
        ovRow2ToneActive.setVisibility(View.GONE);
        if (ovRow2TranslateActive != null) ovRow2TranslateActive.setVisibility(View.GONE);
        ovRow2NoContext.setVisibility(View.GONE);

        // Step 3: Show the correct Row 2 state
        switch (state) {
            case OV_EXPANDED:
            case OV_MILESTONE:
            case OV_BRAIN_BLINK:
                ovRow2Chips.setVisibility(View.VISIBLE);
                // Auto-populate from MockData only in test mode
                if (replyChips.isEmpty() && TestModeController.shouldUseMockData()) {
                    replyChips = MockData.getReplies(state);
                }
                populateOvChips(replyChips);
                Log.d(TAG, "Row2: reply chips");
                break;

            case OV_COLLAPSED:
                ovRow2Chips.setVisibility(View.VISIBLE);
                populateOvContextChips();
                Log.d(TAG, "Row2: context action chips (collapsed)");
                break;

            case CTA_TONE_SELECT:
                ovRow2TonePicker.setVisibility(View.VISIBLE);
                populateToneChips();
                Log.d(TAG, "Row2: tone picker with 21 tones");
                break;

            case CTA_TONE_ACTIVE:
            case TONE_PROFESSIONAL:
            case TONE_CASUAL:
            case TONE_SAVAGE:
            case TONE_SARCASTIC:
            case TONE_CALM:
            case OV_DATING:
                ovRow2ToneActive.setVisibility(View.VISIBLE);
                configureToneActiveRow(state);
                Log.d(TAG, "Row2: tone active with pinned " + state.name());
                break;

            case CTA_TRANSLATE:
                ovRow2LangPicker.setVisibility(View.VISIBLE);
                populateLanguageChips();
                Log.d(TAG, "Row2: language picker");
                break;

            case CTA_TRANSLATE_ACTIVE:
                if (ovRow2TranslateActive != null) {
                    ovRow2TranslateActive.setVisibility(View.VISIBLE);
                    // Shimmer starts visible from XML, result hidden
                    if (ovTranslateShimmer != null) ovTranslateShimmer.setVisibility(View.VISIBLE);
                    if (ovTranslateResult != null) ovTranslateResult.setVisibility(View.GONE);
                    Log.d(TAG, "Row2: translate active — pinned lang + shimmer");
                }
                break;

            case CTA_TONE_CUSTOM:
                currentCustomOrigin = CTA_TONE_CUSTOM;
                ovRow2Custom.setVisibility(View.VISIBLE);
                configureCustomHint(state);
                if (callback != null) callback.onCustomModeEntered(state);
                Log.d(TAG, "Row2: tone custom mode — InternalInputView activated");
                break;

            case CTA_TRANSLATE_CUSTOM:
                currentCustomOrigin = CTA_TRANSLATE_CUSTOM;
                ovRow2Custom.setVisibility(View.VISIBLE);
                configureCustomHint(state);
                if (callback != null) callback.onCustomModeEntered(state);
                Log.d(TAG, "Row2: translate custom mode — InternalInputView activated");
                break;

            case OV_CUSTOM:
                ovRow2Custom.setVisibility(View.VISIBLE);
                configureCustomHint(state);
                if (callback != null) callback.onCustomModeEntered(state);
                Log.d(TAG, "Row2: general custom mode — InternalInputView activated");
                break;

            case CTA_GRAMMAR:
                if (ovRow2GrammarActive != null) {
                    ovRow2GrammarActive.setVisibility(View.VISIBLE);
                    // Shimmer starts visible, result hidden (same pattern as translate)
                    if (ovGrammarShimmer != null) ovGrammarShimmer.setVisibility(View.VISIBLE);
                    if (ovGrammarResult != null) ovGrammarResult.setVisibility(View.GONE);
                    Log.d(TAG, "Row2: grammar active — pinned chip + shimmer");
                }
                break;

            case OV_NO_CONTEXT:
                ovRow2NoContext.setVisibility(View.VISIBLE);
                Log.d(TAG, "Row2: no context hint");
                break;

            case OV_ACCESSIBILITY:
            case BS_ACC_CONSENT:
                ovRow2AccPrompt.setVisibility(View.VISIBLE);
                configureAccPrompt();
                Log.d(TAG, "Row2: accessibility prompt");
                break;

            case OV_ROW2_LOADING:
                ovRow2Shimmer.setVisibility(View.VISIBLE);
                startShimmerAnimation();
                Log.d(TAG, "Row2: shimmer loading");
                break;

            case OV_ERROR:
                ovRow2Error.setVisibility(View.VISIBLE);
                Log.d(TAG, "Row2: error with retry");
                break;

            case OV_CONTACT_PICKER:
                // Contact picker reuses the reply chips container with contact chips
                ovRow2Chips.setVisibility(View.VISIBLE);
                Log.d(TAG, "Row2: contact picker");
                break;
        }
    }

    // ══════════════════════════════════════════════════
    // §5.5 — Row 1 Configuration
    // ══════════════════════════════════════════════════

    private void configureRow1(SabState state) {
        boolean isCollapsed = (state == OV_COLLAPSED);

        // Dim Row 1 when accessibility prompt is shown (features unavailable)
        boolean isAccessibility = (state == OV_ACCESSIBILITY);
        if (ovRow1 != null) {
            ovRow1.setAlpha(isAccessibility ? 0.5f : 1.0f);
        }

        // CTA buttons visible in expanded, gone in collapsed
        ovRow1CtaButtons.setVisibility(isCollapsed ? View.GONE : View.VISIBLE);

        // Spacers + predictions visible in collapsed only (not in expanded)
        ovSpacerLeft.setVisibility(isCollapsed ? View.VISIBLE : View.GONE);
        ovPreds.setVisibility(isCollapsed ? View.VISIBLE : View.GONE);
        ovSpacerRight.setVisibility(isCollapsed ? View.VISIBLE : View.GONE);

        // Expand button visible in collapsed only (collapse is inside CTA container)
        ovExpandBtn.setVisibility(isCollapsed ? View.VISIBLE : View.GONE);

        // Collapsed mic visible in collapsed only (expanded mic is inside CTA container)
        if (ovMicCollapsed != null) {
            ovMicCollapsed.setVisibility(isCollapsed ? View.VISIBLE : View.GONE);
        }

        // Reset all CTA button backgrounds, text, and colors to defaults
        ovBrain.setBackground(null);
        setBgRes(ovToneBtn, R.drawable.wk_ai_btn_default);
        setBgRes(ovTranslateBtn, R.drawable.wk_icon_circle_bg);
        setBgRes(ovGrammarBtn, R.drawable.wk_icon_circle_bg);
        if (ovAiBtn != null) setBgRes(ovAiBtn, R.drawable.wk_ai_btn_default);
        ovToneBtn.setText("🎭 Tone");
        ovTranslateBtn.setText("🌐");
        ovGrammarBtn.setText("✍️");
        ovToneBtn.setTextColor(getColor(R.color.wk_text2));
        ovTranslateBtn.setTextColor(getColor(R.color.wk_text2));
        ovGrammarBtn.setTextColor(getColor(R.color.wk_text2));

        // Highlight active CTA button + append dropdown arrow
        switch (state) {
            case CTA_TONE_SELECT:
            case CTA_TONE_ACTIVE:
            case CTA_TONE_CUSTOM:
            case TONE_PROFESSIONAL:
            case TONE_CASUAL:
            case TONE_SAVAGE:
            case TONE_SARCASTIC:
            case TONE_CALM:
            case OV_DATING:
                setBgRes(ovToneBtn, R.drawable.wk_ai_btn_active);
                ovToneBtn.setText("🎭 Tone ▾");
                ovToneBtn.setTextColor(getColor(R.color.wk_accent));
                Log.d(TAG, "Row1: 🎭 Tone ▾ highlighted");
                break;

            case CTA_TRANSLATE:
            case CTA_TRANSLATE_ACTIVE:
            case CTA_TRANSLATE_CUSTOM:
                setBgRes(ovTranslateBtn, R.drawable.wk_ai_btn_active);
                ovTranslateBtn.setText("🌐");
                ovTranslateBtn.setTextColor(getColor(R.color.wk_accent));
                Log.d(TAG, "Row1: 🌐 highlighted — translate active");
                break;

            case CTA_GRAMMAR:
                setBgRes(ovGrammarBtn, R.drawable.wk_ai_btn_active_green);
                ovGrammarBtn.setTextColor(getColor(R.color.wk_green));
                Log.d(TAG, "Row1: ✓ Gram green — grammar active row in Row 2");
                break;

            case OV_BRAIN_BLINK:
                startBrainBlinkAnimation();
                Log.d(TAG, "Row1: 🧠 blinking");
                break;
        }

        // Populate predictions when collapsed.
        if (isCollapsed && TestModeController.shouldUseMockData()) {
            populatePredictions(MockData.getPredictions(state));
        } else if (isCollapsed) {
            populatePredictions(resolvePredictionsForCollapsed());
        }
    }

    // ══════════════════════════════════════════════════
    // §5.6 — Tone Active Row Configuration
    // ══════════════════════════════════════════════════

    private void configureToneActiveRow(SabState state) {
        // Use stored active tone name/emoji (supports all 21 tones)
        String toneEmoji, toneName;
        if (activeToneName != null) {
            toneName = activeToneName;
            toneEmoji = activeToneEmoji != null ? activeToneEmoji : "😎";
        } else {
            // Legacy fallback: derive from state enum (only 5 tones)
            switch (state) {
                case TONE_PROFESSIONAL: toneEmoji = "💼"; toneName = "Professional"; break;
                case TONE_CASUAL:       toneEmoji = "😎"; toneName = "Casual"; break;
                case TONE_SAVAGE:       toneEmoji = "🔥"; toneName = "Savage"; break;
                case TONE_SARCASTIC:    toneEmoji = "🙄"; toneName = "Sarcastic"; break;
                case TONE_CALM:         toneEmoji = "🧘"; toneName = "Calm"; break;
                case OV_DATING:         toneEmoji = "💘"; toneName = "Flirty"; break;
                default:                toneEmoji = "😎"; toneName = "Casual"; break;
            }
        }
        Log.d(TAG, "configureToneActiveRow: " + toneEmoji + " " + toneName + " (state=" + state.name() + ")");

        int toneColor = getToneColorRes(toneName);
        int toneGlowColor = getToneGlowColorRes(toneName);

        // Set pinned chip
        ovTonePinnedLabel.setText(toneEmoji + " " + toneName);
        ovTonePinnedLabel.setTextColor(getColor(toneColor));

        // Set pinned chip background (tone glow + border)
        GradientDrawable pinnedBg = new GradientDrawable();
        pinnedBg.setColor(getColor(toneGlowColor));
        pinnedBg.setStroke(dpToPx(1), ColorUtils.setAlphaComponent(getColor(toneColor), 102)); // 40% alpha
        pinnedBg.setCornerRadius(dpToPx(16));
        ovTonePinned.setBackground(pinnedBg);

        // For mock/debug display: hide shimmer, show suggestions directly
        if (ovToneSuggestionsShimmer != null) ovToneSuggestionsShimmer.setVisibility(View.GONE);
        if (ovToneSuggestionsScroll != null) ovToneSuggestionsScroll.setVisibility(View.VISIBLE);

        // Populate tone-filtered suggestions
        populateToneSuggestions(state, toneName, toneColor);

        Log.d(TAG, "Tone active: " + toneName + " pinned, suggestions loaded");
    }

    /**
     * Show shimmer in the tone suggestions area (hide actual suggestions).
     * Called when API is loading for tone suggestions.
     */
    public void showToneSuggestionsShimmer() {
        if (ovToneSuggestionsShimmer != null) ovToneSuggestionsShimmer.setVisibility(View.VISIBLE);
        if (ovToneSuggestionsScroll != null) ovToneSuggestionsScroll.setVisibility(View.GONE);
        Log.d(TAG, "Tone suggestions: shimmer shown");
    }

    /**
     * Hide shimmer and show tone suggestions scroll.
     * Called after API returns with tone-adjusted replies.
     */
    public void hideToneSuggestionsShimmer() {
        if (ovToneSuggestionsShimmer != null) ovToneSuggestionsShimmer.setVisibility(View.GONE);
        if (ovToneSuggestionsScroll != null) ovToneSuggestionsScroll.setVisibility(View.VISIBLE);
        Log.d(TAG, "Tone suggestions: shimmer hidden, scroll visible");
    }

    // ══════════════════════════════════════════════════
    // §5.7a — Build 7.0: Contact Picker Strip
    // ══════════════════════════════════════════════════

    /**
     * Renders contact picker strip with tappable contact chips.
     * Each chip shows: "[ContactName 💬]"
     * Tapping a chip calls SmartAssistantBar.onContactPickerSelected().
     */
    public void showContactPicker(List<String> contacts) {
        switchToState(SabState.OV_CONTACT_PICKER);

        // Reuse ovRow2ChipsContainer for contact chips
        ovRow2ChipsContainer.removeAllViews();

        // Add a label
        TextView label = new TextView(context);
        label.setText("Replying to:");
        label.setTextColor(0xFFB0B0B0); // Light gray
        label.setTextSize(12f);
        int dp4 = dpToPx(4);
        int dp8 = dpToPx(8);
        label.setPadding(dp8, dp4, dp8, dp4 / 2);
        ovRow2ChipsContainer.addView(label);

        for (String contactName : contacts) {
            TextView chip = createContactChip(contactName);
            ovRow2ChipsContainer.addView(chip);
        }
    }

    /**
     * Creates a single contact chip button.
     * Styled to match existing SAB dark theme chip style.
     */
    private TextView createContactChip(String contactName) {
        TextView chip = new TextView(context);
        chip.setText(contactName + " \uD83D\uDCAC");
        chip.setTextColor(0xFFE0E0E0); // Light text
        chip.setTextSize(14f);
        int dp12 = dpToPx(12);
        int dp8 = dpToPx(8);
        chip.setPadding(dp12, dp8, dp12, dp8);

        // Dark theme chip background
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2D2D2D); // Dark chip background
        bg.setCornerRadius(dpToPx(16));
        bg.setStroke(1, 0xFF404040); // Subtle border
        chip.setBackground(bg);

        // Margins between chips
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int cpDp4 = dpToPx(4);
        params.setMargins(cpDp4, 0, cpDp4, 0);
        chip.setLayoutParams(params);

        // Click handler — delegates to SAB via callback
        chip.setOnClickListener(v -> {
            ConversationMatcher.ContactMatch activeMatch =
                    ConversationMatcher.getInstance().getActiveContact();
            String packageName = activeMatch != null ? activeMatch.packageName : "";
            if (callback instanceof SmartAssistantBar) {
                ((SmartAssistantBar) callback).onContactPickerSelected(contactName, packageName);
            }
        });

        return chip;
    }

    // §5.7 — Helper: Populate OV Reply Chips
    // ══════════════════════════════════════════════════

    private void populateOvChips(List<ReplyChip> chips) {
        ovRow2ChipsContainer.removeAllViews();

        if (chips != null && !chips.isEmpty()) {
            // Path A: Show AI-generated reply chips
            for (int i = 0; i < chips.size(); i++) {
                ReplyChip chip = chips.get(i);
                View chipView = LayoutInflater.from(context).inflate(
                    R.layout.wk_ov_reply_chip_item, ovRow2ChipsContainer, false);

                View innerChip = chipView.findViewById(R.id.wk_ov_chip);
                TextView text = chipView.findViewById(R.id.wk_ov_chip_text);
                TextView hinglish = chipView.findViewById(R.id.wk_ov_chip_hinglish);
                TextView flagIcon = chipView.findViewById(R.id.wk_ov_chip_flag);

                text.setText(chip.text);

                if (chip.languageTag != null) {
                    hinglish.setText(chip.languageTag);
                    hinglish.setVisibility(View.VISIBLE);
                }

                View clickTarget = (innerChip != null) ? innerChip : chipView;
                clickTarget.setOnClickListener(v -> onChipTapped(chipView, chip));

                if (flagIcon != null) {
                    flagIcon.setClickable(true);
                    flagIcon.setFocusable(true);
                    flagIcon.setText("🚩");
                    flagIcon.setOnClickListener(v -> {
                        Log.d(TAG, "OV chip flagged: " + chip.text);
                        if (callback != null) callback.onMvReplyFlagged(chip.text);
                    });
                }

                ovRow2ChipsContainer.addView(chipView);
                Log.d(TAG, "Added OV chip[" + i + "]: " + chip.text);
            }
            // After reply chips, add Custom + More ONCE
            addCustomAndMoreChips();
        } else {
            // Path B: No reply chips — show LRU action chips + Custom + More
            Log.d(TAG, "No reply chips, showing recent AI action chips");
            // Path B: use cache if primed, otherwise fetch once and prime it
            if (dynamicChipsCache != null) {
                applyExpandedDynamicChips(dynamicChipsCache);
                Log.d(TAG, "Row2 OV_EXPANDED: applied cached action chips + Custom + More");
            } else {
                ActionTracker.getInstance(context).buildDynamicRow2Async(context, dynamicChips -> {
                    if (currentState != SabState.OV_EXPANDED && currentState != SabState.OV_MILESTONE && currentState != SabState.OV_BRAIN_BLINK) {
                        return;
                    }
                    dynamicChipsCache = dynamicChips;
                    applyExpandedDynamicChips(dynamicChips);
                    Log.d(TAG, "Row2 OV_EXPANDED: fetched + cached action chips + Custom + More");
                });
            }
        }
    }

    /** Helper: Adds exactly one Custom chip and one More chip to ovRow2ChipsContainer. */
    private void addCustomAndMoreChips() {
        View customChip = createCustomChip();
        ovRow2ChipsContainer.addView(customChip);
        View moreChip = createMoreChip();
        ovRow2ChipsContainer.addView(moreChip);
    }

    // ══════════════════════════════════════════════════
    // §5.7b — Helper: Populate OV Context Action Chips (COLLAPSED)
    // ══════════════════════════════════════════════════

    private void populateOvContextChips() {
        if (dynamicChipsCache != null) {
            applyContextChips(dynamicChipsCache);
        } else {
            // First load — fetch from DB and prime the cache
            ActionTracker.getInstance(context).buildDynamicRow2Async(context, chips -> {
                dynamicChipsCache = chips;
                applyContextChips(chips);
            });
        }
    }

    private void applyContextChips(List<ChipData> chips) {
        ovRow2ChipsContainer.removeAllViews();
        for (ChipData chipData : chips) {
            ovRow2ChipsContainer.addView(createDynamicChip(chipData));
        }
    }

    private void applyExpandedDynamicChips(List<ChipData> chips) {
        ovRow2ChipsContainer.removeAllViews();
        for (ChipData chipData : chips) {
            if (chipData.tapAction == ChipData.TapAction.OPEN_CUSTOM_MODE
                    || chipData.tapAction == ChipData.TapAction.EXPAND_FULL_PANEL) {
                continue;
            }
            ovRow2ChipsContainer.addView(createDynamicChip(chipData));
        }
        addCustomAndMoreChips();
    }

    private TextView createDynamicChip(ChipData chipData) {
        TextView chip = new TextView(context);
        // Use TONE_EMOJI so Row 2 chips match the tone picker icons exactly
        String displayText = chipData.getDisplayText();
        if (chipData.tapAction == ChipData.TapAction.APPLY_TONE_DIRECT) {
            String canonical = TONE_EMOJI.get(chipData.label);
            if (canonical != null) displayText = canonical + " " + chipData.label;
        }
        chip.setText(displayText);
        chip.setTextSize(11);
        chip.setMaxLines(1);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setClickable(true);
        chip.setFocusable(true);

        // Styling
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(16));

        if (chipData.tapAction == ChipData.TapAction.EXPAND_FULL_PANEL) {
            // "+ More" chip — accent border styling
            chip.setTextColor(getColor(R.color.wk_accent));
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
            bg.setColor(getColor(R.color.wk_surface2));
            bg.setStroke(dpToPx(1), getColor(R.color.wk_accent_glow));
        } else {
            // Standard chip styling
            chip.setTextColor(getColor(R.color.wk_text));
            bg.setColor(getColor(R.color.wk_surface2));
        }

        chip.setBackground(bg);
        chip.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(28));
        lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        chip.setLayoutParams(lp);

        // Tap handler
        chip.setOnClickListener(v -> handleChipTap(chipData));

        return chip;
    }

    private void handleChipTap(ChipData chipData) {
        if (callback == null) return;

        switch (chipData.tapAction) {
            case APPLY_TONE_DIRECT:
                callback.onDirectToneTapped(chipData.parameter);
                break;
            case TRANSLATE_DIRECT:
                callback.onDirectTranslateTapped(chipData.parameter);
                break;
            case GRAMMAR_DIRECT:
                callback.onGrammarTapped();
                break;
            case RERUN_CUSTOM:
                callback.onCustomPromptRerun(chipData.parameter);
                break;
            case OPEN_TONE_PICKER:
                callback.onToneTapped();
                break;
            case OPEN_LANG_PICKER:
                callback.onTranslateTapped();
                break;
            case OPEN_CUSTOM_MODE:
                callback.onCustomModeTapped();
                break;
            case EXPAND_FULL_PANEL:
                callback.onToneTapped(); // For now, expand to tone picker as "more" action
                break;
            case CLIPBOARD_ACTION:
                onClipboardChipTapped(chipData);
                break;
        }
    }

    /**
     * Refreshes the dynamic Row 2 chip cache after any AI action completes.
     * Always fetches fresh data from ActionTracker, then applies to UI if currently visible.
     * Using a cache avoids queuing a DB read on every keystroke.
     */
    public void refreshDynamicRow2() {
        ActionTracker.getInstance(context).buildDynamicRow2Async(context, chips -> {
            dynamicChipsCache = chips;
            if (currentState == SabState.OV_COLLAPSED) {
                applyContextChips(chips);
            } else if (currentState == SabState.OV_EXPANDED || currentState == SabState.OV_MILESTONE || currentState == SabState.OV_BRAIN_BLINK) {
                if (replyChips.isEmpty()) {
                    applyExpandedDynamicChips(chips);
                }
            }
        });
    }

    /**
     * Clipboard chip tapped: paste text into active field, then refresh Row 2.
     */
    private void onClipboardChipTapped(ChipData chip) {
        String clipText = chip.parameter;
        if (clipText == null || clipText.isEmpty()) return;

        // 1. Paste clipboard text via reply chip mechanism
        if (callback != null) {
            callback.onReplyChipTapped(clipText);
        }

        // 2. Clear clipboard from ActionTracker (used once)
        ActionTracker.getInstance(context).clearClipboardText();

        // 3. Refresh Row 2 — clipboard chip disappears, normal actions show
        refreshDynamicRow2();

        Log.d(TAG, "Clipboard chip tapped, pasted " + clipText.length() + " chars");
    }

    // ══════════════════════════════════════════════════
    // §5.9 — Animation Methods
    // ══════════════════════════════════════════════════

    public void stopBrainBlinkAnimation() {
        if (brainBlinkAnimator != null) {
            brainBlinkAnimator.cancel();
            brainBlinkAnimator = null;
        }
        if (ovBrain != null) {
            ovBrain.setScaleX(1f);
            ovBrain.setScaleY(1f);
            ovBrain.setBackground(null);
        }
    }

    private void startBrainBlinkAnimation() {
        stopBrainBlinkAnimation(); // Cancel any existing animation first
        if (ovBrain != null) {
            ovBrain.setAlpha(1f);
            ovBrain.setScaleX(1f);
            ovBrain.setScaleY(1f);
            ovBrain.setBackground(null);
        }
        Log.d(TAG, "Logo set to active state");
    }

    // ========== MIC LISTENING INDICATOR ==========

    public void setMicListeningActive(boolean active) {
        android.graphics.drawable.Drawable bg = null;
        if (active) {
            android.graphics.drawable.GradientDrawable oval = new android.graphics.drawable.GradientDrawable();
            oval.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            oval.setColor(android.graphics.Color.argb(102, 255, 255, 255)); // white 40%
            int inset = dpToPx(4); // shrink circle to ~28dp on 36dp button
            bg = new android.graphics.drawable.InsetDrawable(oval, inset);
        }
        if (ovMicBtn != null) ovMicBtn.setBackground(bg);
        if (ovMicCollapsed != null) ovMicCollapsed.setBackground(bg);
    }

    private ArrayList<ObjectAnimator> shimmerAnimators;

    private void startShimmerAnimation() {
        stopShimmerAnimation();

        if (shimmerAnimators == null) {
            shimmerAnimators = new ArrayList<>();
        }

        View[] shimmerViews = new View[]{shimmer1, shimmer2, shimmer3};
        for (int i = 0; i < shimmerViews.length; i++) {
            View shimmerView = shimmerViews[i];
            if (shimmerView == null) continue;

            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(
                shimmerView,
                "alpha",
                0.3f, 0.7f, 0.3f
            );
            alphaAnimator.setDuration(1500);
            alphaAnimator.setRepeatCount(ValueAnimator.INFINITE);
            alphaAnimator.setRepeatMode(ValueAnimator.RESTART);
            alphaAnimator.setStartDelay(i * 200L);
            alphaAnimator.setInterpolator(new LinearInterpolator());
            alphaAnimator.start();

            shimmerAnimators.add(alphaAnimator);
        }
        Log.d(TAG, "Started shimmer animation (alpha pulse)");
    }

    private void stopShimmerAnimation() {
        if (shimmerAnimators != null) {
            for (ObjectAnimator animator : shimmerAnimators) {
                if (animator != null) {
                    animator.cancel();
                }
            }
            shimmerAnimators.clear();
        }
    }

    private void showMilestoneToast(String text) {
        milestoneToast.setText(text);
        milestoneToast.setVisibility(View.VISIBLE);

        // Animate: slideUp + fadeIn → hold → fadeOut
        // Hold for 8s so debug screenshots can capture it
        ObjectAnimator slideUp = ObjectAnimator.ofFloat(milestoneToast, "translationY", 10f, 0f);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(milestoneToast, "alpha", 0f, 1f);
        AnimatorSet showSet = new AnimatorSet();
        showSet.playTogether(slideUp, fadeIn);
        showSet.setDuration(375);

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(milestoneToast, "alpha", 1f, 0f);
        fadeOut.setStartDelay(8000); // Long hold for debug capture
        fadeOut.setDuration(500);

        AnimatorSet fullSet = new AnimatorSet();
        fullSet.playSequentially(showSet, fadeOut);
        fullSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                milestoneToast.setVisibility(View.GONE);
            }
        });
        fullSet.start();

        Log.d(TAG, "Milestone toast: " + text);
    }

    // ══════════════════════════════════════════════════
    // §5.6b — Translate Active Row Configuration
    // ══════════════════════════════════════════════════

    /**
     * Configure the translate-active row: pinned language chip + shimmer → result + Apply.
     * Called after switchToState(CTA_TRANSLATE_ACTIVE).
     */
    public void configureTranslateActiveRow(String langName, String langFlag) {
        if (ovTranslatePinnedLabel == null) return;

        // Set pinned chip label
        String label = langFlag + " " + langName;
        ovTranslatePinnedLabel.setText(label);

        // Show shimmer, hide result
        if (ovTranslateShimmer != null) ovTranslateShimmer.setVisibility(View.VISIBLE);
        if (ovTranslateResult != null) ovTranslateResult.setVisibility(View.GONE);

        // Close button returns to OV_EXPANDED
        if (ovTranslatePinnedClose != null) {
            ovTranslatePinnedClose.setOnClickListener(v -> {
                switchToState(OV_EXPANDED);
                Log.d(TAG, "Translate active: cancelled by user");
            });
        }

        Log.d(TAG, "Translate active configured: " + label);
    }

    /**
     * Show the translate result (hides shimmer, shows text + Apply button).
     * Called when API returns translated text.
     */
    public void showTranslateResult(String translatedText) {
        if (ovTranslateShimmer != null) ovTranslateShimmer.setVisibility(View.GONE);
        if (ovTranslateResult != null) ovTranslateResult.setVisibility(View.VISIBLE);
        if (ovTranslateText != null) ovTranslateText.setText(translatedText);

        // Apply button click is wired by caller
        Log.d(TAG, "Translate result shown: " + translatedText);
    }

    /**
     * Get the Apply button for translate active to wire click listener externally.
     */
    public View getTranslateApplyButton() {
        return ovTranslateApply;
    }

    /**
     * Get the Copy button for translate active to wire click listener externally.
     */
    public View getTranslateCopyButton() {
        return ovTranslateCopy;
    }

    // ══════════════════════════════════════════════════
    // §5.6c — Grammar Active Row Configuration
    // ══════════════════════════════════════════════════

    /**
     * Configure the grammar active row: show pinned "✓ Grammar" chip + shimmer.
     * Called when grammar API request starts.
     */
    public void configureGrammarActiveRow() {
        // Show shimmer, hide result
        if (ovGrammarShimmer != null) ovGrammarShimmer.setVisibility(View.VISIBLE);
        if (ovGrammarResult != null) ovGrammarResult.setVisibility(View.GONE);

        // Close button returns to OV_EXPANDED
        if (ovGrammarPinnedClose != null) {
            ovGrammarPinnedClose.setOnClickListener(v -> {
                switchToState(OV_EXPANDED);
                Log.d(TAG, "Grammar active: cancelled by user");
            });
        }

        Log.d(TAG, "Grammar active configured: pinned chip + shimmer");
    }

    /**
     * Show the grammar correction result in the grammar active row.
     * Hides shimmer, shows corrected text + Apply + Copy buttons.
     */
    public void showGrammarResult(CharSequence correctedText) {
        if (ovGrammarShimmer != null) ovGrammarShimmer.setVisibility(View.GONE);
        if (ovGrammarResult != null) ovGrammarResult.setVisibility(View.VISIBLE);
        if (ovGrammarText != null) ovGrammarText.setText(correctedText);

        Log.d(TAG, "Grammar result shown: " + correctedText);
    }

    /**
     * Get the Apply button for grammar active to wire click listener externally.
     */
    public View getGrammarApplyButton() {
        return ovGrammarApply;
    }

    /**
     * Get the Copy button for grammar active to wire click listener externally.
     */
    public View getGrammarCopyButton() {
        return ovGrammarCopy;
    }

    // ══════════════════════════════════════════════════
    // §5.6d — Tone Color Lookup (all 21 tones)
    // ══════════════════════════════════════════════════

    /**
     * Get the primary color resource ID for any tone name.
     * Covers all 21 tones in the WittyKeys tone system.
     */
    public int getToneColorRes(String toneName) {
        switch (toneName) {
            case "Casual":        return R.color.wk_tone_casual;
            case "Professional":  return R.color.wk_tone_professional;
            case "Savage":        return R.color.wk_tone_savage;
            case "Sarcastic":     return R.color.wk_tone_sarcastic;
            case "Calm":          return R.color.wk_tone_calm;
            case "Flirty":        return R.color.wk_tone_flirty;
            case "Formal":        return R.color.wk_tone_formal;
            case "Playful":       return R.color.wk_tone_playful;
            case "Sassy":         return R.color.wk_tone_sassy;
            case "Teasing":       return R.color.wk_tone_teasing;
            case "Quirky":        return R.color.wk_tone_quirky;
            case "Concise":       return R.color.wk_tone_concise;
            case "Romantic":      return R.color.wk_tone_romantic;
            case "Descriptive":   return R.color.wk_tone_descriptive;
            case "Persuasive":    return R.color.wk_tone_persuasive;
            case "Witty":         return R.color.wk_tone_witty;
            case "Inspirational": return R.color.wk_tone_inspirational;
            case "Technical":     return R.color.wk_tone_technical;
            case "Urgent":        return R.color.wk_tone_urgent;
            case "Curious":       return R.color.wk_tone_curious;
            case "Polite":        return R.color.wk_tone_polite;
            default:              return R.color.wk_accent;
        }
    }

    /**
     * Get the glow color resource ID for any tone name.
     * Covers all 21 tones in the WittyKeys tone system.
     */
    public int getToneGlowColorRes(String toneName) {
        switch (toneName) {
            case "Casual":        return R.color.wk_tone_casual_glow;
            case "Professional":  return R.color.wk_tone_professional_glow;
            case "Savage":        return R.color.wk_tone_savage_glow;
            case "Sarcastic":     return R.color.wk_tone_sarcastic_glow;
            case "Calm":          return R.color.wk_tone_calm_glow;
            case "Flirty":        return R.color.wk_tone_flirty_glow;
            case "Formal":        return R.color.wk_tone_formal_glow;
            case "Playful":       return R.color.wk_tone_playful_glow;
            case "Sassy":         return R.color.wk_tone_sassy_glow;
            case "Teasing":       return R.color.wk_tone_teasing_glow;
            case "Quirky":        return R.color.wk_tone_quirky_glow;
            case "Concise":       return R.color.wk_tone_concise_glow;
            case "Romantic":      return R.color.wk_tone_romantic_glow;
            case "Descriptive":   return R.color.wk_tone_descriptive_glow;
            case "Persuasive":    return R.color.wk_tone_persuasive_glow;
            case "Witty":         return R.color.wk_tone_witty_glow;
            case "Inspirational": return R.color.wk_tone_inspirational_glow;
            case "Technical":     return R.color.wk_tone_technical_glow;
            case "Urgent":        return R.color.wk_tone_urgent_glow;
            case "Curious":       return R.color.wk_tone_curious_glow;
            case "Polite":        return R.color.wk_tone_polite_glow;
            default:              return R.color.wk_accent_glow;
        }
    }

    // ══════════════════════════════════════════════════
    // §5.6c — Custom Generate → Active State Transition
    // ══════════════════════════════════════════════════

    /**
     * Handle Generate button in custom mode: transition to the appropriate active state
     * with a truncated pinned chip label (max 18 chars + "...").
     */
    public void handleCustomGenerate(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) return;
        prompt = prompt.trim();

        // Truncate for pinned chip label (max 18 chars + ellipsis)
        String pinnedLabel = "\u270F\uFE0F " + (prompt.length() > 18
            ? prompt.substring(0, 18) + "..."
            : prompt);

        if (currentCustomOrigin == CTA_TONE_CUSTOM) {
            // Transition to tone active with custom tone (purple color)
            switchToState(CTA_TONE_ACTIVE);
            int toneColor = getColor(R.color.wk_purple);
            int toneGlow = getColor(R.color.wk_purple_glow);

            ovTonePinnedLabel.setText(pinnedLabel);
            ovTonePinnedLabel.setTextColor(toneColor);

            GradientDrawable pinnedBg = new GradientDrawable();
            pinnedBg.setColor(toneGlow);
            pinnedBg.setStroke(dpToPx(1), ColorUtils.setAlphaComponent(toneColor, 102));
            pinnedBg.setCornerRadius(dpToPx(16));
            ovTonePinned.setBackground(pinnedBg);

            // Show shimmer initially
            showToneSuggestionsShimmer();
            Log.d(TAG, "Custom tone → active: " + pinnedLabel);

        } else if (currentCustomOrigin == CTA_TRANSLATE_CUSTOM) {
            // Transition to translate active with custom language
            switchToState(CTA_TRANSLATE_ACTIVE);
            configureTranslateActiveRow(prompt, "\u270F\uFE0F");
            // Override the label with truncated version
            if (ovTranslatePinnedLabel != null) ovTranslatePinnedLabel.setText(pinnedLabel);
            Log.d(TAG, "Custom translate → active: " + pinnedLabel);
        }
    }

    /**
     * Get the current custom origin state (CTA_TONE_CUSTOM or CTA_TRANSLATE_CUSTOM).
     */
    public SabState getCurrentCustomOrigin() {
        return currentCustomOrigin;
    }

    /** Get the InternalInputView from the custom row layout. */
    public InternalInputView getInternalInputView() {
        return ovCustomInput;
    }

    // ══════════════════════════════════════════════════
    // Helper methods
    // ══════════════════════════════════════════════════

    private int getColor(int colorResId) {
        return ContextCompat.getColor(context, colorResId);
    }

    /**
     * Called when keyboard theme changes. Creates a configuration context
     * with the correct night mode flag so all wk_* color resources resolve
     * to values/ (light) or values-night/ (dark) automatically.
     */
    public void onThemeChanged(Context themedContext, boolean isDark) {
        android.content.res.Configuration config = new android.content.res.Configuration(
            themedContext.getResources().getConfiguration());
        int nightFlag = isDark
            ? android.content.res.Configuration.UI_MODE_NIGHT_YES
            : android.content.res.Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode
            & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | nightFlag;
        this.context = themedContext.createConfigurationContext(config);

        // Diagnostic: verify color resolution
        int bgColor = getColor(R.color.wk_bg);
        int textColor = getColor(R.color.wk_text);
        Log.d(TAG, "onThemeChanged: isDark=" + isDark
            + " wk_bg=#" + Integer.toHexString(bgColor)
            + " wk_text=#" + Integer.toHexString(textColor)
            + " nightMode=" + ((config.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES ? "YES" : "NO"));

        refreshAllColors();
    }

    /**
     * Re-applies theme colors to all persistent views and rebuilds current state.
     * Persistent views have backgrounds/colors baked from inflation time — we must
     * re-set them using the updated context. Dynamic views (chips, cards) are rebuilt
     * by switchToState().
     */
    private void refreshAllColors() {
        // ── Root background (gradient: wk_surface → wk_bg) ──
        rootView.setBackground(ContextCompat.getDrawable(context, R.drawable.wk_smart_bar_bg));
        // Also update the inflated wk_smart_bar child (its background was baked at inflation time)
        View smartBarChild = rootView.findViewById(R.id.wk_smart_bar);
        if (smartBarChild != null && smartBarChild != rootView) {
            smartBarChild.setBackground(ContextCompat.getDrawable(context, R.drawable.wk_smart_bar_bg));
        }

        // ── Row 1 CTA button backgrounds (wk_ai_btn_default: wk_surface2 + wk_btn_border) ──
        refreshBg(ovToneBtn, R.drawable.wk_ai_btn_default);
        refreshBg(ovTranslateBtn, R.drawable.wk_icon_circle_bg);
        refreshBg(ovGrammarBtn, R.drawable.wk_icon_circle_bg);
        refreshBg(ovAiBtn, R.drawable.wk_ai_btn_default);

        // ── Collapse/Expand buttons (wk_collapse_btn_bg: wk_surface2/3 gradient) ──
        refreshBg(ovCollapseBtn, R.drawable.wk_collapse_btn_bg);
        refreshBg(ovExpandBtn, R.drawable.wk_collapse_btn_bg);

        // ── Dividers (direct @color/wk_divider reference) ──
        if (ovDivider != null) ovDivider.setBackgroundColor(getColor(R.color.wk_divider));

        // ── Shimmer bars (wk_shimmer_bar: wk_surface2/3 gradient) ──
        if (shimmer1 != null) refreshBg(shimmer1, R.drawable.wk_shimmer_bar);
        if (shimmer2 != null) refreshBg(shimmer2, R.drawable.wk_shimmer_bar);
        if (shimmer3 != null) refreshBg(shimmer3, R.drawable.wk_shimmer_bar);

        // ── Regen button background ──
        refreshBg(ovRegenBtn, R.drawable.wk_regen_btn_bg);

        // ── Tone active row: pinned chip + suggestions shimmer ──
        // G13 fix: Don't set background on shimmer container — let individual bars render on SAB surface
        ovToneSuggestionsShimmer.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        // ── Translate active row: pinned chip + result chip ──
        refreshBg(ovTranslatePinned, R.drawable.wk_translate_pinned_bg);

        // ── Persistent text colors (set in XML at inflation, not reprogrammed by state) ──
        if (ovMicCollapsed != null) ovMicCollapsed.setColorFilter(getColor(R.color.wk_text));
        if (ovMicBtn != null) ovMicBtn.setColorFilter(getColor(R.color.wk_text));
        if (ovGrammarText != null) ovGrammarText.setTextColor(getColor(R.color.wk_text));
        if (ovGrammarPinnedClose != null) ovGrammarPinnedClose.setTextColor(getColor(R.color.wk_text3));
        if (ovTonePinnedClose != null) ovTonePinnedClose.setTextColor(getColor(R.color.wk_text3));
        if (ovTranslatePinnedClose != null) ovTranslatePinnedClose.setTextColor(getColor(R.color.wk_text3));
        if (ovTranslateText != null) ovTranslateText.setTextColor(getColor(R.color.wk_text));

        // ── Re-render current state (rebuilds dynamic views: chips, cards, CTA colors) ──
        switchToState(currentState);
    }

    private void refreshBg(View view, int drawableResId) {
        if (view != null) {
            view.setBackground(ContextCompat.getDrawable(context, drawableResId));
        }
    }

    /** Like setBackgroundResource but resolves drawable via our theme-aware context. */
    private void setBgRes(View view, int drawableResId) {
        view.setBackground(ContextCompat.getDrawable(context, drawableResId));
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    private void applyToneSelectedStyle(TextView chip) {
        int accentColor = getColor(R.color.wk_accent);
        GradientDrawable selectedBg = new GradientDrawable();
        selectedBg.setColor(getColor(R.color.wk_accent_glow));
        selectedBg.setStroke(dpToPx(1), ColorUtils.setAlphaComponent(accentColor, 102));
        selectedBg.setCornerRadius(dpToPx(14));
        chip.setBackground(selectedBg);
        chip.setTextColor(accentColor);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);

        // Accent underline indicator
        GradientDrawable underline = new GradientDrawable();
        underline.setShape(GradientDrawable.RECTANGLE);
        underline.setColor(accentColor);
        underline.setCornerRadius(dpToPx(1.5f));
        underline.setSize(dpToPx(20), dpToPx(3));
        chip.setCompoundDrawablesWithIntrinsicBounds(null, null, null, underline);
        chip.setCompoundDrawablePadding(dpToPx(2));
    }

    private void clearToneSelectedStyle(View child) {
        if (child instanceof TextView) {
            TextView chip = (TextView) child;
            chip.setTextColor(getColor(R.color.wk_text));
            chip.setTypeface(android.graphics.Typeface.DEFAULT);
            setBgRes(chip, R.drawable.wk_tone_chip_bg);
            chip.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }
    }

    private void populatePredictions(String[] predictions) {
        ovPreds.removeAllViews();
        for (int i = 0; i < predictions.length; i++) {
            if (i > 0) {
                // Add divider
                View divider = new View(context);
                divider.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(1), dpToPx(18)));
                setBgRes(divider, R.drawable.wk_pred_divider);
                ovPreds.addView(divider);
            }
            TextView pred = new TextView(context);
            pred.setText(predictions[i]);
            pred.setTextSize(13);
            pred.setTextColor(getColor(R.color.wk_text));
            pred.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
            pred.setClickable(true);
            pred.setFocusable(true);
            final String predText = predictions[i];
            pred.setOnClickListener(v -> {
                if (callback != null) callback.onPredictionTapped(predText);
            });
            ovPreds.addView(pred);
        }
    }

    private String[] resolvePredictionsForCollapsed() {
        return currentPredictions != null && currentPredictions.length > 0
            ? currentPredictions
            : STARTER_PREDICTIONS;
    }

    private void populateToneChips() {
        ovToneChipsContainer.removeAllViews();
        String[][] tones = {
            {"😎", "Casual"}, {"💼", "Professional"}, {"🔥", "Savage"},
            {"🙄", "Sarcastic"}, {"🧘", "Calm"}, {"💕", "Flirty"},
            {"🎭", "Formal"}, {"🎮", "Playful"}, {"💅", "Sassy"},
            {"😜", "Teasing"}, {"🤪", "Quirky"}, {"📝", "Concise"},
            {"💝", "Romantic"}, {"📖", "Descriptive"}, {"🎯", "Persuasive"},
            {"🧠", "Witty"}, {"✨", "Inspirational"}, {"💻", "Technical"},
            {"🚨", "Urgent"}, {"🧐", "Curious"}, {"🎩", "Polite"}
        };
        for (int i = 0; i < tones.length; i++) {
            final String toneName = tones[i][1];
            TextView chip = new TextView(context);
            chip.setText(tones[i][0] + " " + toneName);
            chip.setTextSize(11);
            chip.setTextColor(getColor(R.color.wk_text));
            setBgRes(chip, R.drawable.wk_tone_chip_bg);
            chip.setPadding(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dpToPx(6));
            chip.setLayoutParams(lp);
            chip.setClickable(true);
            chip.setFocusable(true);

            chip.setOnClickListener(v -> {
                // Clear all selections then apply to tapped chip
                for (int j = 0; j < ovToneChipsContainer.getChildCount(); j++) {
                    clearToneSelectedStyle(ovToneChipsContainer.getChildAt(j));
                }
                applyToneSelectedStyle(chip);
                if (callback != null) callback.onToneChipSelected(toneName);
            });
            ovToneChipsContainer.addView(chip);
        }

        // Add ✏️ Custom chip at end of tone picker
        TextView customChipTone = new TextView(context);
        customChipTone.setText("✏️ Custom");
        customChipTone.setTextSize(11);
        customChipTone.setTextColor(getColor(R.color.wk_purple));
        setBgRes(customChipTone, R.drawable.wk_tone_custom_chip_bg);
        customChipTone.setPadding(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5));
        LinearLayout.LayoutParams customToneLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        customToneLp.setMarginEnd(dpToPx(6));
        customChipTone.setLayoutParams(customToneLp);
        customChipTone.setClickable(true);
        customChipTone.setFocusable(true);
        customChipTone.setOnClickListener(v -> switchToState(CTA_TONE_CUSTOM));
        ovToneChipsContainer.addView(customChipTone);
        Log.d(TAG, "Tone picker: added ✏️ Custom chip at end");
    }

    private void populateLanguageChips() {
        ovLangChipsContainer.removeAllViews();
        String[][] langs = {
            {"🇮🇳", "Hinglish"}, {"🇬🇧", "English"}, {"🇮🇳", "Hindi"},
            {"🇪🇸", "Spanish"}, {"🇫🇷", "French"}, {"🇩🇪", "German"}
        };
        for (int i = 0; i < langs.length; i++) {
            final String langName = langs[i][1];
            TextView chip = new TextView(context);
            chip.setText(langs[i][0] + " " + langName);
            chip.setTextSize(12);
            chip.setTextColor(getColor(R.color.wk_text));
            setBgRes(chip, R.drawable.wk_lang_chip_bg);
            chip.setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dpToPx(6));
            chip.setLayoutParams(lp);
            chip.setClickable(true);
            chip.setFocusable(true);

            final String langFlag = langs[i][0];
            chip.setOnClickListener(v -> {
                // Transition to translate-active with shimmer
                switchToState(CTA_TRANSLATE_ACTIVE);
                configureTranslateActiveRow(langName, langFlag);
                if (callback != null) callback.onLanguageChipSelected(langName);
                Log.d(TAG, "Language selected: " + langName + " — showing translate active");
            });
            ovLangChipsContainer.addView(chip);
        }

        // Add ✏️ Custom chip at end of language picker
        TextView customChipLang = new TextView(context);
        customChipLang.setText("✏️ Custom");
        customChipLang.setTextSize(12);
        customChipLang.setTextColor(getColor(R.color.wk_accent));
        setBgRes(customChipLang, R.drawable.wk_lang_custom_chip_bg);
        customChipLang.setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6));
        LinearLayout.LayoutParams customLangLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        customLangLp.setMarginEnd(dpToPx(6));
        customChipLang.setLayoutParams(customLangLp);
        customChipLang.setClickable(true);
        customChipLang.setFocusable(true);
        customChipLang.setOnClickListener(v -> switchToState(CTA_TRANSLATE_CUSTOM));
        ovLangChipsContainer.addView(customChipLang);
        Log.d(TAG, "Language picker: added ✏️ Custom chip at end");
    }

    private TextView createReplyChip(String text, int toneColor) {
        float cornerRadiusPx = dpToPx(20);
        float accentWidthPx = dpToPx(2);
        float borderWidthPx = dpToPx(1);
        int bgColor = ContextCompat.getColor(context, R.color.wk_surface2);
        int borderColor = ContextCompat.getColor(context, R.color.wk_chip_border);

        Drawable chipDrawable = new Drawable() {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF chipRect = new RectF();
            private final RectF borderRect = new RectF();
            private final Path chipPath = new Path();
            private final Path leftEdgePath = new Path();

            {
                bgPaint.setColor(bgColor);
                bgPaint.setStyle(Paint.Style.FILL);

                borderPaint.setColor(borderColor);
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(borderWidthPx);

                // Accent uses STROKE, not FILL
                accentPaint.setColor(toneColor);
                accentPaint.setStyle(Paint.Style.STROKE);
                accentPaint.setStrokeWidth(accentWidthPx * 2); // 4dp total, half inside chip
                accentPaint.setStrokeCap(Paint.Cap.ROUND);
            }

            @Override
            public void draw(Canvas canvas) {
                Rect b = getBounds();
                float w = b.width();
                float h = b.height();

                // Clamp corner radius if chip is too small
                float r = Math.min(cornerRadiusPx, Math.min(w, h) / 2f);

                // 1) Draw chip background
                chipRect.set(b);
                canvas.drawRoundRect(chipRect, r, r, bgPaint);

                // 2) Draw border
                float half = borderWidthPx / 2f;
                borderRect.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
                canvas.drawRoundRect(borderRect, r, r, borderPaint);

                // 3) Draw accent — stroke the left edge path, clipped to chip
                canvas.save();
                chipPath.reset();
                chipPath.addRoundRect(chipRect, r, r, Path.Direction.CW);
                canvas.clipPath(chipPath);
                canvas.clipRect(b.left, b.top + h / 8f, b.right, b.bottom - h / 8f);

                // Build path tracing the left side of the rounded rect:
                // Start at top-center of chip, arc down-left to left-center, arc down-right to bottom-center
                leftEdgePath.reset();

                // Top-left arc: from (left + r, top) curving to (left, top + r)
                // We use arcTo with the oval that defines the top-left corner
                RectF topLeftArc = new RectF(b.left, b.top, b.left + 2 * r, b.top + 2 * r);
                leftEdgePath.moveTo(b.left + r, b.top); // start at top of left curve
                leftEdgePath.arcTo(topLeftArc, 270, -90); // sweep from 270° to 180° (counter-clockwise)

                // Straight left edge (if height > 2*radius)
                if (h > 2 * r) {
                    leftEdgePath.lineTo(b.left, b.bottom - r);
                }

                // Bottom-left arc: from (left, bottom - r) curving to (left + r, bottom)
                RectF bottomLeftArc = new RectF(b.left, b.bottom - 2 * r, b.left + 2 * r, b.bottom);
                leftEdgePath.arcTo(bottomLeftArc, 180, -90); // sweep from 180° to 90° (counter-clockwise)

                canvas.drawPath(leftEdgePath, accentPaint);
                canvas.restore();
            }

            @Override public void setAlpha(int alpha) { bgPaint.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter cf) { bgPaint.setColorFilter(cf); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };

        // ── Build the chip TextView ──
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(12.5f);
        chip.setTextColor(ContextCompat.getColor(context, R.color.wk_text));
        chip.setMaxLines(1);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setIncludeFontPadding(false);
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        chip.setBackground(chipDrawable);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(34));
        lp.setMarginEnd(dpToPx(6));
        lp.gravity = android.view.Gravity.CENTER_VERTICAL;
        chip.setLayoutParams(lp);

        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            if (callback != null) callback.onReplyChipTapped(text);
        });

        return chip;
    }

    private void populateToneSuggestions(SabState state, String toneName, int toneColorRes) {
        ovToneSuggestionsContainer.removeAllViews();

        // Hide the XML-defined regen button (left-side position)
        if (ovRegenBtn != null) ovRegenBtn.setVisibility(View.GONE);

        // Use real tone suggestions if available, else MockData in test mode
        List<ReplyChip> suggestions;
        if (!toneSuggestions.isEmpty()) {
            suggestions = new ArrayList<>(toneSuggestions); // copy — don't consume, fresh API call handles refresh
        } else if (TestModeController.shouldUseMockData()) {
            suggestions = MockData.getReplies(state);
        } else {
            return; // No data available
        }
        int toneColor = getColor(toneColorRes);

        for (ReplyChip suggestion : suggestions) {
            TextView chip = createReplyChip(suggestion.text, toneColor);
            ovToneSuggestionsContainer.addView(chip);
        }

        // Add ✨ regen button at END of suggestions
        TextView regenEnd = new TextView(context);
        regenEnd.setText("✨");
        regenEnd.setTextSize(16);
        regenEnd.setGravity(android.view.Gravity.CENTER);
        setBgRes(regenEnd, R.drawable.wk_regen_btn_bg);
        LinearLayout.LayoutParams regenLp = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        regenLp.setMarginStart(dpToPx(4));
        regenLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        regenEnd.setLayoutParams(regenLp);
        regenEnd.setClickable(true);
        regenEnd.setFocusable(true);
        regenEnd.setOnClickListener(v -> {
            if (callback != null) callback.onRegenTapped();
        });
        ovToneSuggestionsContainer.addView(regenEnd);

        Log.d(TAG, "Tone suggestions: " + suggestions.size() + " chips + ✨ regen at end");
    }

    private void configureAccPrompt() {
        TextView accText = rootView.findViewById(R.id.wk_ov_acc_text);
        if (accText != null) {
            String fullText = "Enable AI features for smart replies";
            android.text.SpannableString spannable = new android.text.SpannableString(fullText);
            int start = fullText.indexOf("AI features");
            int end = start + "AI features".length();
            spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.wk_accent)),
                start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            accText.setText(spannable);
        }
        // Wire click on both the container AND the Enable button
        // (Button intercepts touch events, so parent alone won't work)
        View.OnClickListener accClickListener = v -> {
            if (callback != null) callback.onAccPromptEnableTapped();
        };
        if (ovRow2AccPrompt != null) {
            ovRow2AccPrompt.setOnClickListener(accClickListener);
            // Also wire the actual "Enable" button which steals clicks from parent
            android.widget.Button accBtn = ovRow2AccPrompt.findViewById(R.id.wk_ov_acc_btn);
            if (accBtn != null) {
                accBtn.setOnClickListener(accClickListener);
            }
        }
    }

    private void configureCustomHint(SabState state) {
        if (ovCustomInput == null) return;
        switch (state) {
            case CTA_TONE_CUSTOM:
                ovCustomInput.setPlaceholderText("\uD83C\uDFAD Describe your tone...");
                break;
            case CTA_TRANSLATE_CUSTOM:
                ovCustomInput.setPlaceholderText("\uD83C\uDF10 Type target language...");
                break;
            default: // OV_CUSTOM
                ovCustomInput.setPlaceholderText("Type custom instruction...");
                break;
        }
    }

    // ══════════════════════════════════════════════════
    // Bottom Sheet Implementation (BS_ACC_CONSENT)
    // ══════════════════════════════════════════════════

    private void showBottomSheet(SabState state) {
        Log.d(TAG, "showBottomSheet: " + state.name());

        // Dismiss any existing bottom sheet first
        dismissBottomSheet();

        bottomSheetState = state;

        // Create overlay container (semi-transparent dark bg)
        FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(getColor(R.color.wk_overlay_bg));
        bottomSheetOverlay = overlay;

        // Inflate consent sheet layout
        View sheetView;
        sheetView = LayoutInflater.from(context).inflate(
            R.layout.wk_acc_consent, overlay, false);
        wireConsentButtons(sheetView);
        bottomSheetView = sheetView;

        // Apply navigation bar inset as bottom padding so content isn't overlapped
        int navBarHeight = getNavigationBarHeight();
        if (navBarHeight > 0) {
            // Find the scrollable body container inside the sheet
            // Consent sheet: root LinearLayout > [0]=handle, [1]=ScrollView > [0]=body LinearLayout
            View body = null;
            ViewGroup root = (ViewGroup) sheetView;
            for (int i = 0; i < root.getChildCount(); i++) {
                if (root.getChildAt(i) instanceof android.widget.ScrollView) {
                    android.widget.ScrollView sv = (android.widget.ScrollView) root.getChildAt(i);
                    if (sv.getChildCount() > 0) body = sv.getChildAt(0);
                    break;
                }
            }
            if (body != null) {
                body.setPadding(
                    body.getPaddingLeft(),
                    body.getPaddingTop(),
                    body.getPaddingRight(),
                    body.getPaddingBottom() + navBarHeight);
            }
        }

        // Add sheet to overlay at bottom
        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        overlay.addView(sheetView, sheetParams);

        // Create PopupWindow (covers entire screen area)
        // Non-focusable to prevent stealing focus from IME window (which causes auto-dismiss)
        bottomSheetPopup = new PopupWindow(
            overlay,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            false); // non-focusable: prevents IME focus loss → auto-dismiss
        bottomSheetPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        bottomSheetPopup.setOutsideTouchable(false);
        bottomSheetPopup.setClippingEnabled(false);

        // Dismiss listener
        bottomSheetPopup.setOnDismissListener(() -> {
            bottomSheetState = null;
            isBottomSheetDismissing = false;
            Log.d(TAG, "Bottom sheet popup dismissed");
        });

        // Show covering the keyboard area
        bottomSheetPopup.showAtLocation(rootView, Gravity.BOTTOM, 0, 0);

        // Animate: overlay fade-in + sheet slide-up
        overlay.setAlpha(0f);
        overlay.animate()
            .alpha(1f)
            .setDuration(300)
            .start();

        // Sheet starts offscreen (below), slides up
        sheetView.setTranslationY(800); // large initial offset
        sheetView.post(() -> {
            // Re-measure to get actual height
            float targetTranslation = sheetView.getHeight() > 0 ? sheetView.getHeight() : 800;
            sheetView.setTranslationY(targetTranslation);
            sheetView.animate()
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
        });

        // Tap overlay (outside sheet) to dismiss
        overlay.setOnClickListener(v -> dismissBottomSheetAnimated());
        // Consume clicks on sheet so overlay tap doesn't fire
        sheetView.setOnClickListener(v -> { /* consume */ });
        sheetView.setClickable(true);

        Log.d(TAG, "showBottomSheet: PopupWindow shown for " + state.name());
    }

    /**
     * Dismiss bottom sheet immediately (no animation).
     */
    public void dismissBottomSheet() {
        if (bottomSheetPopup != null && bottomSheetPopup.isShowing()) {
            bottomSheetPopup.dismiss();
            bottomSheetPopup = null;
        }
        bottomSheetOverlay = null;
        bottomSheetView = null;
        bottomSheetState = null;
        isBottomSheetDismissing = false;
    }

    /**
     * Dismiss bottom sheet with slide-down + fade-out animation.
     */
    private void dismissBottomSheetAnimated() {
        if (isBottomSheetDismissing) return;
        if (bottomSheetPopup == null || !bottomSheetPopup.isShowing()) return;
        isBottomSheetDismissing = true;

        Log.d(TAG, "dismissBottomSheetAnimated: starting dismiss");

        // Slide sheet down
        if (bottomSheetView != null) {
            bottomSheetView.animate()
                .translationY(bottomSheetView.getHeight())
                .setDuration(300)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (bottomSheetPopup != null && bottomSheetPopup.isShowing()) {
                        bottomSheetPopup.dismiss();
                    }
                    bottomSheetPopup = null;
                    bottomSheetOverlay = null;
                    bottomSheetView = null;
                    isBottomSheetDismissing = false;

                    // Return to OV_EXPANDED after dismiss
                    switchToState(OV_EXPANDED);
                    Log.d(TAG, "Bottom sheet dismissed, returned to OV_EXPANDED");
                })
                .start();
        } else {
            dismissBottomSheet();
            switchToState(OV_EXPANDED);
        }

        // Fade out overlay
        if (bottomSheetOverlay != null) {
            bottomSheetOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .start();
        }
    }

    /**
     * Check if a bottom sheet is currently showing.
     */
    public boolean isBottomSheetShowing() {
        return bottomSheetPopup != null && bottomSheetPopup.isShowing();
    }

    /**
     * Get the navigation bar height. Returns 0 for gesture navigation (no bar).
     */
    private int getNavigationBarHeight() {
        android.content.res.Resources res = context.getResources();
        int resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            // Check if gesture navigation is active (nav bar height <= 20dp means gestures)
            int navHeight = res.getDimensionPixelSize(resourceId);
            // Gesture nav typically reports a small value (16-20dp) for the gesture pill area
            // 3-button nav typically reports 48dp. We add it either way so content clears.
            return navHeight;
        }
        return 0;
    }

    // ══════ Consent Sheet Button Wiring ══════

    private void wireConsentButtons(View sheetView) {
        View notNowBtn = sheetView.findViewById(R.id.btn_not_now);
        View enableBtn = sheetView.findViewById(R.id.btn_enable);

        if (notNowBtn != null) {
            notNowBtn.setOnClickListener(v -> {
                Log.d(TAG, "Consent: Not Now tapped");
                dismissBottomSheetAnimated();
            });
        }
        if (enableBtn != null) {
            enableBtn.setOnClickListener(v -> {
                Log.d(TAG, "Consent: Grant Permission tapped → opening Accessibility Settings");
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open Accessibility Settings: " + e.getMessage());
                }
                dismissBottomSheetAnimated();
            });
        }
    }

    public void setAppName(String name) {
        this.appName = name;
    }

    private TextView createCustomChip() {
        return createDynamicChip(new ChipData("custom", "", "\u270F\uFE0F", "Custom", ChipData.TapAction.OPEN_CUSTOM_MODE));
    }

    private TextView createMoreChip() {
        return createDynamicChip(new ChipData("more", "", "", "+ More", ChipData.TapAction.EXPAND_FULL_PANEL));
    }

    private void onChipTapped(View chipView, ReplyChip chip) {
        Log.d(TAG, "Chip tapped: " + chip.text);
        if (callback != null) callback.onReplyChipTapped(chip.text);
    }

    // ══════════════════════════════════════════════════
    // Public setters for data
    // ══════════════════════════════════════════════════

    public void setContactName(String name) {
        this.contactName = name;
    }

    public void setSummaryText(String summary) {
        this.summaryText = summary;
    }

    public void setEmotionData(String emoji, String label, int colorResId) {
        this.emotionEmoji = emoji;
        this.emotionLabel = label;
        this.emotionColorResId = colorResId;
    }

    public void setReplies(List<ReplyChip> replies) {
        this.replies = replies;
    }

    public void setReplyChips(List<ReplyChip> chips) {
        this.replyChips = chips;
    }

    /**
     * Set predictions for OV_COLLAPSED Row 1 from real prediction engine.
     * Called by SmartAssistantBar.onUserInput() with real next-word predictions.
     */
    public void setPredictions(String[] predictions) {
        if (predictions == null || predictions.length == 0) {
            currentPredictions = STARTER_PREDICTIONS.clone();
        } else {
            currentPredictions = predictions.clone();
        }
        populatePredictions(resolvePredictionsForCollapsed());
    }

    public void setToneSuggestions(List<ReplyChip> suggestions) {
        this.toneSuggestions = suggestions != null ? suggestions : new ArrayList<>();
    }

    public void setActiveTone(String name, String emoji) {
        this.activeToneName = name;
        this.activeToneEmoji = emoji;
        Log.d(TAG, "Active tone set: " + emoji + " " + name);
    }

    public SabState getCurrentState() {
        Log.d(TAG, "getCurrentState: " + currentState);
        return currentState;
    }

    public View getRootView() {
        return rootView;
    }

    // ========== TEST HELPERS ==========

    /**
     * Get texts of currently displayed reply chips.
     * Used by E2E tests to assert reply content.
     */
    public List<String> getReplyTexts() {
        List<String> texts = new ArrayList<>();
        if (ovRow2ChipsContainer != null) {
            for (int i = 0; i < ovRow2ChipsContainer.getChildCount(); i++) {
                View child = ovRow2ChipsContainer.getChildAt(i);
                if (child instanceof android.view.ViewGroup) {
                    // Each chip is a layout, find the text inside
                    android.view.ViewGroup chipLayout = (android.view.ViewGroup) child;
                    for (int j = 0; j < chipLayout.getChildCount(); j++) {
                        View inner = chipLayout.getChildAt(j);
                        if (inner instanceof TextView) {
                            String chipText = ((TextView) inner).getText().toString();
                            if (!chipText.isEmpty()) {
                                texts.add(chipText);
                                break;
                            }
                        }
                    }
                } else if (child instanceof TextView) {
                    texts.add(((TextView) child).getText().toString());
                }
            }
        }
        Log.d(TAG, "getReplyTexts: found " + texts.size() + " replies");
        return texts;
    }

    /**
     * Check if reply chips are currently visible.
     */
    public boolean areChipsVisible() {
        boolean visible = ovRow2Chips != null
            && ovRow2Chips.getVisibility() == View.VISIBLE
            && ovRow2ChipsContainer != null
            && ovRow2ChipsContainer.getChildCount() > 0;
        Log.d(TAG, "areChipsVisible: " + visible);
        return visible;
    }

    // ══════════════════════════════════════════════════
    // ReplyChip data class
    // ══════════════════════════════════════════════════

    public static class ReplyChip {
        public final String text;
        public final String languageTag; // null or "en" = no badge; "HI","ES","FR" etc = show badge

        public ReplyChip(String text, String languageTag) {
            this.text = text;
            this.languageTag = languageTag;
        }
    }
}
