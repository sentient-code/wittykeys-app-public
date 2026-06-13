/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package project.witty.keys.keyboard;

import static android.view.View.VISIBLE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import project.witty.keys.app.helpers.NavigationBarHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;
import project.witty.keys.R;
import project.witty.keys.api.ChatGPTApi;
import project.witty.keys.app.AccessibilityConsentActivity;
import project.witty.keys.app.PermissionDisclosureDialog;

import project.witty.keys.app.context.ScreenContext;
import project.witty.keys.app.entities.AppContext;
import project.witty.keys.app.entities.Subscription;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.helpers.JourneyTracer;
import project.witty.keys.app.helpers.ScreenReaderAccessibility;
import project.witty.keys.app.helpers.ScreenshotPermissionActivity;
import project.witty.keys.app.utils.PromptGenerator;
import project.witty.keys.app.utils.ToneData;
import project.witty.keys.event.Event;
import project.witty.keys.keyboard.AiChat.AIFeatureType;
import project.witty.keys.keyboard.AiChat.AiMessage;
import project.witty.keys.keyboard.AiChat.CategoryOption;
import project.witty.keys.keyboard.AiChat.ChatItem;
import project.witty.keys.keyboard.AiChat.CtaType;
import project.witty.keys.keyboard.AiChat.ErrorMessage;
import project.witty.keys.keyboard.AiChat.GridOptions;
import project.witty.keys.keyboard.AiChat.HorizontalOptions;
import project.witty.keys.keyboard.AiChat.Loading;
import project.witty.keys.keyboard.AiChat.MetadataCard;
import project.witty.keys.keyboard.AiChat.OptionsType;
import project.witty.keys.keyboard.AiChat.UserMessage;
import project.witty.keys.keyboard.AssistantViews.AiViewAssistant;
import project.witty.keys.keyboard.AssistantViews.SmartAssistantBar;
import project.witty.keys.keyboard.AssistantViews.SuggestionRow;
import project.witty.keys.keyboard.AssistantViews.UtilityRow;
import project.witty.keys.keyboard.EmojiKeyboard.EmojiKeyboard;
import project.witty.keys.keyboard.EmojiKeyboard.InternalSearchView;
import project.witty.keys.keyboard.KeyboardLayoutSet.KeyboardLayoutSetException;
import project.witty.keys.keyboard.ProductViews.UnifiedAiView;
import project.witty.keys.keyboard.internal.InternalInputTarget;
import project.witty.keys.keyboard.internal.InternalInputView;
import project.witty.keys.keyboard.internal.KeyboardState;
import project.witty.keys.keyboard.internal.KeyboardTextsSet;
import project.witty.keys.keyboard.shared.LanguageFlags;
import project.witty.keys.latin.InputView;
import project.witty.keys.latin.LatinIME;
import project.witty.keys.latin.RichInputConnection;
import project.witty.keys.latin.RichInputMethodManager;
import project.witty.keys.latin.settings.Settings;
import project.witty.keys.latin.settings.SettingsValues;
import project.witty.keys.latin.utils.CapsModeUtils;
import project.witty.keys.latin.utils.LanguageOnSpacebarUtils;
import project.witty.keys.latin.utils.RecapitalizeStatus;

import project.witty.keys.latin.utils.ResourceUtils;

// Imports for speech recognition
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.Manifest;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
// Import Uri for constructing a settings Intent when requesting microphone permission.
import android.net.Uri;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.FirebaseFirestore;

import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.utils.DailyUsageTracker;
import project.witty.keys.app.tutorial.TutorialManager;


public final class KeyboardSwitcher implements KeyboardState.SwitchActions {
    private static final String TAG = KeyboardSwitcher.class.getSimpleName();

    private InputView mCurrentInputView;
    private int mCurrentUiMode;
    private int mCurrentTextColor = 0x0;
    private View mMainKeyboardFrame;
    private MainKeyboardView mKeyboardView;

    private LatinIME mLatinIME;
    private RichInputMethodManager mRichImm;

    private KeyboardState mState;

    private KeyboardLayoutSet mKeyboardLayoutSet;
    // TODO: The following {@link KeyboardTextsSet} should be in {@link KeyboardLayoutSet}.
    private final KeyboardTextsSet mKeyboardTextsSet = new KeyboardTextsSet();

    private KeyboardTheme mKeyboardTheme;
    private Context mThemeContext;
    private Context mViewThemeContext; // Night-mode-aware context for non-keyboard views

    private static final KeyboardSwitcher sInstance = new KeyboardSwitcher();

    public static KeyboardSwitcher getInstance() {
        return sInstance;
    }

    private boolean lastApiCallFailed = false;
    private EmojiKeyboard emojiKeyboard;
    //Assistant Views
    // SmartAssistantBar - Unified replacement for UtilityRow + SuggestionRow (Build 6.3)
    private SmartAssistantBar smartAssistantBar;
    // Legacy references kept for backward compatibility during transition
    @Deprecated private UtilityRow utilityRow;
    @Deprecated private SuggestionRow suggestionRow;
    private AiViewAssistant aiViewAssistant;
    public List<String> receivedTexts = new ArrayList<>(); // Use a List for dynamic size
    public String capturedText = null;
    private Handler mainHandler; // Handler to post delayed actions
    public String currentSelectedLevel1Intent = null;
    public String currentSelectedLevel2Intent = null;
    PromptGenerator promptGenerator = new PromptGenerator();
    private UnifiedAiView mUnifiedAiView;

    public enum AiAction {
        CORRECT_GRAMMAR,
        CHANGE_TONE,
        SHOW_SCAN_OPTIONS, // Action to just show the grid
        SCAN_AND_EXECUTE,   // Action after user clicks an option from the grid
        AI_CHAT,          // <-- Add this
        SHOW_TRANSLATE_OPTIONS // <-- Add this
    }
    private String mPendingHeaderText = null;


    // Flag indicating whether the current unified AI session is an AI chat conversation.
    // This is used to decide whether to include conversation history in API requests.
    private boolean mInAiChatConversation = false;

    /**
     * Stores the last AI-generated result so that follow-up actions (e.g. suggestions) can
     * operate on the most recent output. This is populated whenever an AI message or scan
     * result is added to the chat. It should not include user prompts.
     */
    private String mLastAiResult = "";

    /**
     * Stores the full AI response that is currently displayed in the AiAssistantView.
     * This is used when the user clicks on the layout to return to the full chat view.
     */
    private String mLastAiResponseForAssistant = "";

    /**
     * Tracks the last prompt sent to the AI so that a user can regenerate a new response
     * for the same input. Whenever executeGptApiWithChatUi is called, these values are updated.
     */
    private String mLastPrompt = "";
    private project.witty.keys.keyboard.AiChat.CtaType mLastCtaType = project.witty.keys.keyboard.AiChat.CtaType.REPLY_COPY;

    /**
     * Indicates whether the current voice recognition session should invoke the
     * ChatGPT API after transcribing speech. If false, only the speech is
     * committed into the input field and no API calls are made. This flag is
     * cleared after processing each voice command.
     */
    private boolean mVoiceApiCallFlag = false;

    /**
     * Stores the most recent task used in a screenâ€based scan action. When the user selects
     * a LevelÂ 2 option from the scan grid (e.g., "Generate a 'Agree' reply"), this value is
     * updated to reflect the chosen task. If the user taps â€œRegenerateâ€ on a scan result,
     * we use this task to trigger another scan, ensuring the same action is repeated with
     * fresh AI output. Without this, regenerate in scan flows would incorrectly fall back
     * to the last chat prompt or do nothing.
     */
    private String mLastScanTask = "";

    /**
     * Speech recognizer and intent used for capturing voice commands. The recognizer
     * instance is lazily initialised when the user first taps the voice CTA. Once
     * initialised, it will be reused for subsequent voice inputs. The boolean
     * flag guards against overlapping recognition sessions.
     */
    private SpeechRecognizer mSpeechRecognizer;
    private Intent mSpeechRecognizerIntent;
    private boolean mIsListening = false;
    private boolean mMicToggleActive = false;
    private int mPartialVoiceLength = 0;



    // Make sure you have an instance of your API class
    private final ChatGPTApi chatGPTApi = new ChatGPTApi();
    private AppContext mCurrentScreenContext;
    private String mCurrentUserAction;
    private KeyboardSwitcher() {
        // Intentional empty constructor for singleton.
    }
    private int mCalculatedKeyboardHeight = 0; // Add this line
    // --- NEW: Add a reference to the container ---
    private View mKeyboardBlockContainer;
    private TutorialManager tutorialManager;
    private BroadcastReceiver tutorialHighlightReceiver;


    public static void init(final LatinIME latinIme) {
        // DEBUG: Log keyboard switcher initialization
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "=== KEYBOARD_ENABLE_DEBUG: KeyboardSwitcher.init() ===");
            Log.d(TAG, "   Timestamp: " + System.currentTimeMillis());
            Log.d(TAG, "   LatinIME instance: " + (latinIme != null));
        }
        sInstance.initInternal(latinIme);
    }


    private void initInternal(final LatinIME latinIme) {
        mLatinIME = latinIme;
        mRichImm = RichInputMethodManager.getInstance();
        mState = new KeyboardState(this);
        tutorialManager = TutorialManager.getInstance(mLatinIME);
        registerTutorialHighlightReceiver();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "   KeyboardSwitcher initialized successfully");
            Log.d(TAG, "   RichInputMethodManager: " + (mRichImm != null));
            Log.d(TAG, "   KeyboardState: " + (mState != null));
        }
    }

    public void updateKeyboardTheme(final int uiMode) {
        final boolean themeUpdated = updateKeyboardThemeAndContextThemeWrapper(
                mLatinIME, KeyboardTheme.getKeyboardTheme(mLatinIME), uiMode);
        if (themeUpdated && mKeyboardView != null) {
            mLatinIME.setInputView(onCreateInputView(uiMode));
            // Update navbar color after keyboard view is recreated
            // Post with delay to ensure keyboard's mCustomColor is set
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                mLatinIME.setNavigationBarColor();
            }, 100);
        }
    }

    private boolean updateKeyboardThemeAndContextThemeWrapper(final Context context,
                                                              final KeyboardTheme keyboardTheme, final int uiMode) {
        int newTextColor = 0x0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            newTextColor = context.getResources().getColor(R.color.key_text_color_lxx_system);
        }

        if (mThemeContext == null
                || !keyboardTheme.equals(mKeyboardTheme)
                || mCurrentUiMode != uiMode
                || newTextColor != mCurrentTextColor) {
            mKeyboardTheme = keyboardTheme;
            mCurrentUiMode = uiMode;
            mCurrentTextColor = newTextColor;
            mThemeContext = new ContextThemeWrapper(context, keyboardTheme.mStyleId);
            // Views (UnifiedAIView, EmojiKeyboard, etc.) need a context with the
            // correct night-mode flag so wk_* resources from values-night/ resolve
            // correctly. InputMethodService may not track night mode reliably, so
            // we always create an explicit night-aware context for ALL theme types.
            boolean isDark;
            int viewStyleId;
            if (keyboardTheme.mThemeId == KeyboardTheme.THEME_ID_SYSTEM) {
                isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
                viewStyleId = isDark
                        ? R.style.KeyboardTheme_LXX_Dark
                        : R.style.KeyboardTheme_LXX_Light;
            } else if (keyboardTheme.mThemeId == KeyboardTheme.THEME_ID_DARK) {
                isDark = true;
                viewStyleId = R.style.KeyboardTheme_LXX_Dark;
            } else {
                // THEME_ID_LIGHT or any unknown — force light
                isDark = false;
                viewStyleId = R.style.KeyboardTheme_LXX_Light;
            }
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (isDark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            Context nightAwareContext = context.createConfigurationContext(config);
            mViewThemeContext = new ContextThemeWrapper(nightAwareContext, viewStyleId);
            if (smartAssistantBar != null) smartAssistantBar.onThemeChanged(mViewThemeContext);
            if (mUnifiedAiView != null) mUnifiedAiView.onThemeChanged(mViewThemeContext);
            if (emojiKeyboard != null) emojiKeyboard.onThemeChanged(mViewThemeContext);
            if (aiViewAssistant != null) aiViewAssistant.onThemeChanged(mViewThemeContext);
            KeyboardLayoutSet.onKeyboardThemeChanged();
            return true;
        }
        return false;
    }

    public void loadKeyboard(final EditorInfo editorInfo, final SettingsValues settingsValues,
                             final int currentAutoCapsState, final int currentRecapitalizeState) {
        // DEBUG: Log keyboard loading
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "=== KEYBOARD_ENABLE_DEBUG: KeyboardSwitcher.loadKeyboard() ===");
            Log.d(TAG, "   Timestamp: " + System.currentTimeMillis());
            Log.d(TAG, "   Editor Package: " + (editorInfo != null ? editorInfo.packageName : "null"));
            Log.d(TAG, "   AutoCaps State: " + currentAutoCapsState);
        }

        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                mThemeContext, editorInfo);
        final Resources res = mThemeContext.getResources();
        final int keyboardWidth = mLatinIME.getMaxWidth();
        final int keyboardHeight = ResourceUtils.getKeyboardHeight(res, settingsValues);
        final int keyboardBottomOffset = ResourceUtils.getKeyboardBottomOffset(res, settingsValues);
        setCalculatedKeyboardHeight(keyboardHeight);
        builder.setKeyboardTheme(mKeyboardTheme.mThemeId);
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight, keyboardBottomOffset);
        builder.setSubtype(mRichImm.getCurrentSubtype());
        builder.setLanguageSwitchKeyEnabled(mLatinIME.shouldShowLanguageSwitchKey());
        builder.setShowSpecialChars(!settingsValues.mHideSpecialChars);
        builder.setShowNumberRow(settingsValues.mShowNumberRow);
        mKeyboardLayoutSet = builder.build();
        try {
            mState.onLoadKeyboard(currentAutoCapsState, currentRecapitalizeState);
            mKeyboardTextsSet.setLocale(mRichImm.getCurrentSubtype().getLocaleObject(),
                    mThemeContext);
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "   Keyboard loaded successfully");
            }
        } catch (KeyboardLayoutSetException e) {
            Log.w(TAG, "loading keyboard failed: " + e.mKeyboardId, e.getCause());
        }
    }

    public void saveKeyboardState() {
        if (getKeyboard() != null) {
            mState.onSaveKeyboardState();
        }
    }

    public void onHideWindow() {
        if (mKeyboardView != null) {
            if (!"SCAN_IN_PROGRESS".equals(mCurrentUserAction)) {
                receivedTexts = new ArrayList<>();
                capturedText = null;
            }
            // Do NOT call clearSession() here — hiding the keyboard window is temporary
            // (e.g. ScreenCaptureActivity starting). Session state must survive hide/show cycles.
            hideProductViews();
            if (getCandidateView() == null) {
                showUtilityRow();
            }
            mKeyboardView.onHideWindow();
        }
    }

    private void setKeyboard(
            final int keyboardId,
            final KeyboardSwitchState toggleState) {
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        setMainKeyboardFrame(currentSettingsValues, toggleState);
        // TODO: pass this object to setKeyboard instead of getting the current values.
        final MainKeyboardView keyboardView = mKeyboardView;
        final Keyboard oldKeyboard = keyboardView.getKeyboard();
        final Keyboard newKeyboard = mKeyboardLayoutSet.getKeyboard(keyboardId);
        keyboardView.setKeyboard(newKeyboard);
        // Update navbar to match keyboard color after keyboard is loaded
        mLatinIME.setNavigationBarColor();
        keyboardView.setKeyPreviewPopupEnabled(
                currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay);
        final boolean subtypeChanged = (oldKeyboard == null)
                || !newKeyboard.mId.mSubtype.equals(oldKeyboard.mId.mSubtype);
        final int languageOnSpacebarFormatType = LanguageOnSpacebarUtils
                .getLanguageOnSpacebarFormatType(newKeyboard.mId.mSubtype);
        keyboardView.startDisplayLanguageOnSpacebar(subtypeChanged, languageOnSpacebarFormatType);
    }

    public Keyboard getKeyboard() {
        if (mKeyboardView != null) {
            return mKeyboardView.getKeyboard();
        }
        return null;
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    public void resetKeyboardStateToAlphabet(final int currentAutoCapsState,
                                             final int currentRecapitalizeState) {
        mState.onResetKeyboardStateToAlphabet(currentAutoCapsState, currentRecapitalizeState);
    }

    public void onPressKey(final int code, final boolean isSinglePointer,
                           final int currentAutoCapsState, final int currentRecapitalizeState) {
        mState.onPressKey(code, isSinglePointer, currentAutoCapsState, currentRecapitalizeState);
    }

    public void onReleaseKey(final int code, final boolean withSliding,
                             final int currentAutoCapsState, final int currentRecapitalizeState) {
        smartAssistantBar.onUserInput(getCommitedText());
        mState.onReleaseKey(code, withSliding, currentAutoCapsState, currentRecapitalizeState);
    }

    public void onFinishSlidingInput(final int currentAutoCapsState,
                                     final int currentRecapitalizeState) {
        mState.onFinishSlidingInput(currentAutoCapsState, currentRecapitalizeState);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetManualShiftedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetManualShiftedKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetAutomaticShiftedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetAutomaticShiftedKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED, KeyboardSwitchState.OTHER);
    }

    public LatinIME getmLatinIME() {
        return mLatinIME;
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetShiftLockedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetShiftLockedKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsShiftedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsShiftedKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS_SHIFTED, KeyboardSwitchState.SYMBOLS_SHIFTED);
    }

    public boolean isImeSuppressedByHardwareKeyboard(
            final SettingsValues settingsValues,
            final KeyboardSwitchState toggleState) {
        return settingsValues.mHasHardwareKeyboard && toggleState == KeyboardSwitchState.HIDDEN;
    }

    private void setMainKeyboardFrame(
            final SettingsValues settingsValues,
            final KeyboardSwitchState toggleState) {
        final int visibility = isImeSuppressedByHardwareKeyboard(settingsValues, toggleState)
                ? View.GONE : VISIBLE;
        mKeyboardView.setVisibility(visibility);
        // The visibility of {@link #mKeyboardView} must be aligned with {@link #MainKeyboardFrame}.
        // @see #getVisibleKeyboardView() and
        // @see LatinIME#onComputeInset(android.inputmethodservice.InputMethodService.Insets)
        mMainKeyboardFrame.setVisibility(visibility);
    }

    public enum KeyboardSwitchState {
        HIDDEN(-1),
        SYMBOLS_SHIFTED(KeyboardId.ELEMENT_SYMBOLS_SHIFTED),
        OTHER(-1);

        final int mKeyboardId;

        KeyboardSwitchState(int keyboardId) {
            mKeyboardId = keyboardId;
        }
    }

    public KeyboardSwitchState getKeyboardSwitchState() {
        boolean hidden = mKeyboardLayoutSet == null
                || mKeyboardView == null
                || !mKeyboardView.isShown();
        if (hidden) {
            return KeyboardSwitchState.HIDDEN;
        } else if (isShowingKeyboardId(KeyboardId.ELEMENT_SYMBOLS_SHIFTED)) {
            return KeyboardSwitchState.SYMBOLS_SHIFTED;
        }
        return KeyboardSwitchState.OTHER;
    }

    // Future method for requesting an updating to the shift state.
    @Override
    public void requestUpdatingShiftState(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_ACTION) {
            Log.d(TAG, "requestUpdatingShiftState: "
                    + " autoCapsFlags=" + CapsModeUtils.flagsToString(autoCapsFlags)
                    + " recapitalizeMode=" + RecapitalizeStatus.modeToString(recapitalizeMode));
        }
        mState.onUpdateShiftState(autoCapsFlags, recapitalizeMode, isKeyboardShowing(), isProductViewVisible());
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void startDoubleTapShiftKeyTimer() {
        if (DEBUG_TIMER_ACTION) {
            Log.d(TAG, "startDoubleTapShiftKeyTimer");
        }
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            keyboardView.startDoubleTapShiftKeyTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void cancelDoubleTapShiftKeyTimer() {
        if (DEBUG_TIMER_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard");
        }
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            keyboardView.cancelDoubleTapShiftKeyTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public boolean isInDoubleTapShiftKeyTimeout() {
        if (DEBUG_TIMER_ACTION) {
            Log.d(TAG, "isInDoubleTapShiftKeyTimeout");
        }
        final MainKeyboardView keyboardView = getMainKeyboardView();
        return keyboardView != null && keyboardView.isInDoubleTapShiftKeyTimeout();
    }

    /**
     * Updates state machine to figure out when to automatically switch back to the previous mode.
     */
    public void onEvent(final Event event, final int currentAutoCapsState,
                        final int currentRecapitalizeState) {
        mState.onEvent(event, currentAutoCapsState, currentRecapitalizeState, isKeyboardShowing(), isProductViewVisible());
    }

    public boolean isShowingKeyboardId(int... keyboardIds) {
        if (mKeyboardView == null || !mKeyboardView.isShown()) {
            return false;
        }
        int activeKeyboardId = mKeyboardView.getKeyboard().mId.mElementId;
        for (int keyboardId : keyboardIds) {
            if (activeKeyboardId == keyboardId) {
                return true;
            }
        }
        return false;
    }
    public View getVisibleKeyboardView() {
        // Product panels first
        if (mUnifiedAiView != null && mUnifiedAiView.isShown()) return mUnifiedAiView;
        if (emojiKeyboard != null && emojiKeyboard.isShown())   return emojiKeyboard;

        // When showing the keyboard, return the *entire* frame that includes
        // UtilityRow + SuggestionRow + key view so those areas are touchable.
        if (mMainKeyboardFrame != null && mMainKeyboardFrame.isShown()) return mMainKeyboardFrame;

        return mKeyboardView; // fallback
    }

    public View getCandidateView() {
        // handle CandidateView here
        if (aiViewAssistant != null && aiViewAssistant.isShown()) {
            return aiViewAssistant;
        }
        return null;
    }

    public MainKeyboardView getMainKeyboardView() {
        return mKeyboardView;
    }

    public void deallocateMemory() {
        if (mKeyboardView != null) {
            mKeyboardView.cancelAllOngoingEvents();
            mKeyboardView.deallocateMemory();
        }
    }

    public View onCreateInputView(final int uiMode) {
        if (mKeyboardView != null) {
            mKeyboardView.closing();
        }
        mainHandler = new Handler(Looper.getMainLooper());
        updateKeyboardThemeAndContextThemeWrapper(
                mLatinIME, KeyboardTheme.getKeyboardTheme(mLatinIME /* context */), uiMode);
        mCurrentInputView = (InputView) LayoutInflater.from(mThemeContext).inflate(
                R.layout.input_view, null);
        mMainKeyboardFrame = mCurrentInputView.findViewById(R.id.main_keyboard_frame);
        mMainKeyboardFrame.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    private boolean wasVisible = false;  // Track visibility state

                    @Override public void onGlobalLayout() {
                        if (mMainKeyboardFrame == null) return;
                        int h = mMainKeyboardFrame.getHeight();

                        // Original height tracking logic
                        if (h > 0 && h != mCalculatedKeyboardHeight) {
                            mCalculatedKeyboardHeight = h;
                            resizeProductPanelsTo(h); // keep panels in lockstep
                        }

                        // âœ… NEW: Detect when keyboard becomes visible (height > 0 AND is shown)
                        boolean isNowVisible = h > 0 && mMainKeyboardFrame.isShown();

                        if (isNowVisible && !wasVisible) {
                            // Keyboard just became visible!
                            if (DebugConfig.isDebugMode) {
                                Log.d(TAG, "ðŸŽ¹ OnGlobalLayoutListener: Keyboard became VISIBLE (h=" + h + ")");
                            }

                            // Check and apply tutorial highlight
                            if (tutorialManager != null && tutorialManager.isTutorialMode()) {
                                // Use small delay to ensure UI is fully rendered
                                if (mainHandler != null) {
                                    mainHandler.postDelayed(() -> {
                                        checkAndApplyTutorialHighlight();
                                    }, 100);
                                } else {
                                    checkAndApplyTutorialHighlight();
                                }
                            }
                        }

                        wasVisible = isNowVisible;
                    }
                }
        );
        mKeyboardBlockContainer = mCurrentInputView.findViewById(R.id.keyboard_block_container);
        mKeyboardView = (MainKeyboardView) mCurrentInputView.findViewById(R.id.keyboard_view);
        emojiKeyboard = (EmojiKeyboard) mCurrentInputView.findViewById(R.id.emoji_keyboard);
        // SmartAssistantBar - Unified component (Build 6.3)
        smartAssistantBar = (SmartAssistantBar) mCurrentInputView.findViewById(R.id.smart_assistant_bar);
        aiViewAssistant = (AiViewAssistant) mCurrentInputView.findViewById(R.id.ai_view_assistant);
        mUnifiedAiView = (UnifiedAiView) mCurrentInputView.findViewById(R.id.unified_ai_view);
        mKeyboardView.setKeyboardActionListener(mLatinIME);
        emojiKeyboard.setKeyboardActionListener(mLatinIME);
        mUnifiedAiView.setup(mLatinIME, mLatinIME);
        if (smartAssistantBar != null) {
            smartAssistantBar.setLatinIme(mLatinIME);
            smartAssistantBar.setTutorialManager(tutorialManager);
        }
        setAllUtilityListeners();
        setAiViewListener();

        // Theme all views with the night-mode-aware context (mViewThemeContext)
        // Views were just inflated from mThemeContext which may have wrong night mode
        // for System theme, so explicitly apply the correct theme now.
        if (smartAssistantBar != null) smartAssistantBar.onThemeChanged(mViewThemeContext);
        if (mUnifiedAiView != null) mUnifiedAiView.onThemeChanged(mViewThemeContext);
        if (emojiKeyboard != null) emojiKeyboard.onThemeChanged(mViewThemeContext);
        if (aiViewAssistant != null) aiViewAssistant.onThemeChanged(mViewThemeContext);

        updateKeyboardTheme(uiMode);
        return mCurrentInputView;
    }



    public void hideProductViews() {
        // This is now effectively a shortcut to show the main keyboard view.
        if (emojiSearchMode) {
            exitEmojiSearchMode();
        }
        if (isProductViewVisible() || (emojiKeyboard != null && emojiKeyboard.isShown())) {
            showKeyboardView();
        }
    }


    public boolean isProductViewVisible() {
        return (mUnifiedAiView != null && mUnifiedAiView.isShown()) || (emojiKeyboard != null && emojiKeyboard.isShown());
    }


    public void showKeyboardView() {
        if (mKeyboardBlockContainer != null) {
            hideAiAssistantView();
            showUtilityRow();
            showMainViewComponent(mKeyboardBlockContainer);
        }
    }


    public void setLastApiCallFailed(boolean failed) {
        lastApiCallFailed = failed;
    }

    public boolean didLastApiCallFail() {
        return lastApiCallFailed;
    }

    public int getUtilityRowHeight() {
        if (smartAssistantBar != null) {
            return smartAssistantBar.getRow1Height();
        }
        return 0;
    }

    public int getSuggestionRowHeight() {
        if (smartAssistantBar != null) {
            return smartAssistantBar.getRow2Height();
        }
        return 0;
    }

    public int getSmartAssistantBarHeight() {
        if (smartAssistantBar != null) {
            return smartAssistantBar.getTotalHeight();
        }
        return 0;
    }

    private boolean isAiViewVisible() {
        return aiViewAssistant != null && aiViewAssistant.getVisibility() == VISIBLE;
    }

    public void setAllUtilityListeners() {
        if (smartAssistantBar != null) {
            smartAssistantBar.setAiChatClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (smartAssistantBar != null) smartAssistantBar.stopHighlight();
                    if (isAiViewVisible()) {
                        Toast.makeText(mCurrentInputView.getContext(), "Please clear current chat to start new!!", Toast.LENGTH_LONG).show();
                        return;
                    }
                    smartAssistantBar.requireSubscriptionThenRun(() -> {
                        // AI Chat has its own input field — no dependency on app's EditText
                        performAiAction(AiAction.AI_CHAT, "");
                    });

                    if (tutorialManager != null) {
                        tutorialManager.notifyButtonClicked("AI_CHAT");
                    }

                }
            });

            // Phase 2B: Tone button → show inline tone picker (Row 2)
            smartAssistantBar.setToneClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (smartAssistantBar != null) smartAssistantBar.stopHighlight();
                    RichInputConnection mConnection = mLatinIME.getInputLogicInstance().mConnection;
                    String commitedText = mConnection.getCommitedText();
                    if (commitedText.isEmpty()) {
                        smartAssistantBar.showEmptyTextError();
                        return;
                    }
                    smartAssistantBar.requireSubscriptionThenRun(() -> {
                        smartAssistantBar.showRow2State(SmartAssistantBar.Row2State.TONE_PICKER);
                    });

                    if (tutorialManager != null) {
                        tutorialManager.notifyButtonClicked("TONALITY");
                    }
                }
            });

            // Phase 2B: Grammar button → inline grammar fix (no UnifiedAiView)
            smartAssistantBar.setGrammarClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (smartAssistantBar != null) smartAssistantBar.stopHighlight();
                    smartAssistantBar.requireSubscriptionThenRun(() -> {
                        smartAssistantBar.performGrammarFix();
                    });
                }
            });

            smartAssistantBar.setScreenReadClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (smartAssistantBar != null) smartAssistantBar.stopHighlight();
                    // Check if AI view is already visible
                    if (isAiViewVisible()) {
                        Toast.makeText(mCurrentInputView.getContext(), "Please clear current chat to start new!!", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Check if accessibility is enabled using PermissionDisclosureDialog
                    if (PermissionDisclosureDialog.isAccessibilityEnabled(mLatinIME)) {
                        // Accessibility enabled - use real Read Screen feature
                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "âœ… Accessibility enabled - starting real Read Screen flow");
                        }
                        startScanScreenFlow();
                    } else {
                        // Accessibility NOT enabled - show permission disclosure dialog
                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "Accessibility not enabled - showing permission disclosure");
                            if (tutorialManager != null) {
                                Log.d(TAG, "Tutorial mode: " + tutorialManager.isTutorialMode());
                            }
                        }

                        // Use PermissionDisclosureDialog for Play Store compliant disclosure
                        PermissionDisclosureDialog.showFromKeyboard(mThemeContext, () -> {
                            // This callback is triggered when permission is granted
                            if (DebugConfig.isDebugMode) {
                                Log.d(TAG, "Permission granted - starting screen read flow");
                            }
                            startScanScreenFlow();
                        });
                    }

                    // Notify tutorial manager
                    if (tutorialManager != null) {
                        tutorialManager.notifyButtonClicked("READ_SCREEN");
                    }
                }
            });



            // Phase 2B: Translate button → show inline language picker (Row 2)
            smartAssistantBar.setTranslateClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    RichInputConnection mConnection = mLatinIME.getInputLogicInstance().mConnection;
                    String commitedText = mConnection.getCommitedText();
                    if (commitedText.isEmpty()) {
                        smartAssistantBar.showEmptyTextError();
                        return;
                    }
                    smartAssistantBar.requireSubscriptionThenRun(() -> {
                        smartAssistantBar.showRow2State(SmartAssistantBar.Row2State.LANG_PICKER);
                    });

                    if (tutorialManager != null) {
                        tutorialManager.notifyButtonClicked("TRANSLATE");
                    }
                }
            });

            //for read content
            // Voice/speech input listener (no API call). When the user taps the first
            // microphone icon, we reset the API flag and start recognition. The
            // recognised speech will be committed into the input field without
            // contacting the AI.
            smartAssistantBar.setVoiceClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mVoiceApiCallFlag = false;
                    startVoiceRecognition();
                }
            });

            // Voice prompt listener (API call). When the user taps the second
            // microphone icon, we set the API flag to true so that after speech
            // recognition, the recognised text is both committed into the input field
            // and sent to ChatGPT. The AI response will be shown in the suggestion row.
            smartAssistantBar.setDictationClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mMicToggleActive) {
                        mMicToggleActive = false;
                        cancelVoiceRecognition();
                    } else {
                        mMicToggleActive = true;
                        mVoiceApiCallFlag = false;
                        startVoiceRecognition();
                    }
                }
            });

            // ========== PHASE 2B: More CTA Listener ==========
            // More CTA shows inline tone picker (same as Tone button)
            smartAssistantBar.setMoreCtaClickListener(new SmartAssistantBar.OnMoreCtaClickListener() {
                @Override
                public void onMoreCtaClick() {
                    RichInputConnection mConnection = mLatinIME.getInputLogicInstance().mConnection;
                    String commitedText = mConnection.getCommitedText();
                    if (commitedText.isEmpty()) {
                        smartAssistantBar.showEmptyTextError();
                        return;
                    }
                    smartAssistantBar.requireSubscriptionThenRun(() -> {
                        smartAssistantBar.showRow2State(SmartAssistantBar.Row2State.TONE_PICKER);
                    });
                }
            });

            // ========== PHASE 3: Custom Generate Listener ==========
            // When user taps Generate in custom mode, we get the prompt from host app's input
            // and call the API to generate custom replies.
            smartAssistantBar.setCustomGenerateListener(new SmartAssistantBar.OnCustomGenerateListener() {
                @Override
                public void onGenerateCustomReplies(String prompt) {
                    if (isAiViewVisible()) {
                        Toast.makeText(mCurrentInputView.getContext(), "Please clear current chat to start new!", Toast.LENGTH_LONG).show();
                        smartAssistantBar.showCustomModeError("AI view busy");
                        return;
                    }
                    // Generate custom replies based on the prompt
                    smartAssistantBar.requireSubscriptionThenRun(() -> {
                        generateCustomRepliesFromPrompt(prompt);
                    });
                }
            });

            // ========== BUILD 7.0: Screen Capture Listener ==========
            smartAssistantBar.setScreenCaptureListener(() -> startScreenCapture());

        }
    }

    public void startScanScreenFlow() {

        // 1. Ensure the main AI view is visible. This is correct.
        showMainViewComponent(mUnifiedAiView);
        mUnifiedAiView.setViewTitle("Read Screen",AIFeatureType.GENERATE_READ_REPLY);
        mUnifiedAiView.hideInitialLoading();
        List<CategoryOption> level1Options = new ArrayList<>();
        level1Options.add(new CategoryOption(R.drawable.translate_v2_icon, "Translate", "task_Translate the content on the screen."));

        for (String intent : ToneData.getLevel1Intents()) {
            level1Options.add(new CategoryOption(R.drawable.continue_v2_icon, intent, "show_level2_" + intent));
        }

        Log.d("ScanFlow", "Step 1.1: Building and displaying Level 1 options.");

        // 3. Call startNewSession. This method already clears the list before adding
        // the new items, so it will correctly remove any old "Loading" item.
        mUnifiedAiView.startNewSession(Collections.singletonList(
                new GridOptions("What would you like to do with this screen?", level1Options)
        ));
    }


    public List<String> getRecievedTextFromAccessibility() {
        return receivedTexts;
    }


    public void setAiViewListener() {
        if (aiViewAssistant != null) {
            // The info button has been removed in the new design, so no listener is attached.

            aiViewAssistant.setBackButtonListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideAiAssistantView();
                    showUtilityRow();
                    showKeyboardView();
                    // Don't clear session - user might want to come back to it
                }
            });

            // Layout click - Show existing chat without adding new message or API call
            aiViewAssistant.setLayoutClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (DebugConfig.isDebugMode) {
                        Log.d(TAG, "📱 AiAssistantView layout clicked - showing existing chat");
                    }

                    // Hide assistant view
                    hideAiAssistantView();

                    // Show UnifiedAiView with existing chat (no new message, no API call)
                    if (mUnifiedAiView != null && mUnifiedAiView.getChatItems() != null
                            && !mUnifiedAiView.getChatItems().isEmpty()) {
                        hideUtilityRow();
                        showMainViewComponent(mUnifiedAiView);

                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "✅ Showing UnifiedAiView with " + mUnifiedAiView.getChatItems().size() + " existing items");
                        }
                    } else {
                        // If no existing chat, just go back to keyboard
                        showUtilityRow();
                        showKeyboardView();

                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "⚠️ No existing chat items, showing keyboard");
                        }
                    }
                }
            });

            aiViewAssistant.setClearConversationListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideAiAssistantView();
                    showUtilityRow();
                    if (mUnifiedAiView != null) {
                        mUnifiedAiView.clearSession();
                    }
                    // Clear stored AI response
                    mLastAiResponseForAssistant = "";
                }
            });

            // Reply button - Send user's follow-up question to ChatGPT
            aiViewAssistant.setReplyButtonListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    RichInputConnection mConnection = mLatinIME.getInputLogicInstance().mConnection;
                    String commitedText = mConnection.getCommitedText();

                    if (commitedText.isEmpty()) {
                        Toast.makeText(mCurrentInputView.getContext(), "Please enter a follow-up question", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (DebugConfig.isDebugMode) {
                        Log.d(TAG, "💬 Reply button clicked with follow-up: " + commitedText.substring(0, Math.min(50, commitedText.length())));
                    }

                    // Hide assistant view first
                    hideAiAssistantView();

                    // Perform AI chat action with the follow-up question
                    // This will show UnifiedAiView, add the user's message to existing chat, and call API
                    performAiAction(AiAction.AI_CHAT, commitedText);
                }
            });
        }
    }

    public void showAiAssistantView(String fullResponse) {
        if (aiViewAssistant == null) return;

        // Store the full response for potential follow-up actions
        mLastAiResponseForAssistant = fullResponse;

        // Clear the editText so user can type follow-up question
        clearEditText();

        // Keep the keyboard block + utility + suggestions visible
        showKeyboardView();

        // Update preview text + show
        aiViewAssistant.setLastResponseTextView(getPreview(fullResponse));
        aiViewAssistant.setVisibility(VISIBLE);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📝 AiAssistantView shown. EditText cleared for follow-up input.");
        }
    }

    /**
     * Clears all text from the host application's input field.
     * This is used when showing the AiAssistantView so the user can type a follow-up question.
     */
    private void clearEditText() {
        if (mLatinIME == null) return;
        RichInputConnection connection = mLatinIME.getInputLogicInstance().mConnection;
        if (connection != null && connection.isConnected()) {
            connection.beginBatchEdit();
            CharSequence before = connection.getTextBeforeCursor(10000, 0);
            CharSequence after = connection.getTextAfterCursor(10000, 0);
            int totalLength = (before != null ? before.length() : 0) + (after != null ? after.length() : 0);
            if (totalLength > 0) {
                connection.setSelection(0, totalLength);
                connection.commitText("", 1);
            }
            connection.endBatchEdit();

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "🧹 EditText cleared. Previous length: " + totalLength);
            }
        }
    }

    public int getAiViewAssistantHeight() {
        if (aiViewAssistant != null) {
            return aiViewAssistant.getHeight();
        }
        return 0;

    }

    /**
     * Builds a shortened preview of an AI response for display in the assistant
     * overlay. If the input contains 20 words or fewer, it is returned
     * unchanged. Otherwise, the preview consists of the first 10 words, an
     * ellipsis, and the last 10 words.
     *
     * @param fullResponse the complete AI response to shorten
     * @return a preview string suitable for display
     */
    private String getPreview(String fullResponse) {
        if (fullResponse == null) {
            return "";
        }
        String trimmed = fullResponse.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] words = trimmed.split("\\s+");
        if (words.length <= 20) {
            return trimmed;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(words[i]);
            sb.append(i < 9 ? " " : "");
        }
        sb.append(" ... ");
        int start = Math.max(words.length - 10, 10);
        for (int i = start; i < words.length; i++) {
            sb.append(words[i]);
            if (i < words.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    /**
     * Construct a conversation history for use with the ChatGPT API. This history includes
     * up to the last five user or AI messages from the unified chat view, plus the current
     * prompt as the most recent user message. It ignores other item types (such as options
     * or metadata). If an error occurs, this method returns {@code null} so callers can
     * fallback to a minimal history. The returned list is ordered chronologically from
     * oldest to newest.
     *
     * @param currentPrompt The prompt text to be used as the most recent user message.
     * @return A list of {@link JSONObject} instances representing the conversation history,
     *         or {@code null} on failure.
     */
    private List<JSONObject> buildConversationHistory(String currentPrompt) {
        List<JSONObject> history = new ArrayList<>();
        try {
            // If the unified AI view or its items are unavailable, return a minimal history
            if (mUnifiedAiView == null || mUnifiedAiView.getChatItems() == null) {
                history.add(new JSONObject().put("role", "user").put("content", currentPrompt));
                return history;
            }
            List<ChatItem> items = mUnifiedAiView.getChatItems();
            // Gather up to the last five relevant messages (user or AI) in reverse order
            List<ChatItem> relevant = new ArrayList<>();
            for (int i = items.size() - 1; i >= 0 && relevant.size() < 5; i--) {
                ChatItem item = items.get(i);
                if (item instanceof AiMessage || item instanceof UserMessage) {
                    // Prepend to maintain chronological order when later iterating
                    relevant.add(0, item);
                }
            }
            // Convert each relevant item to a JSON object with the appropriate role and content
            for (ChatItem item : relevant) {
                if (item instanceof AiMessage) {
                    String content = ((AiMessage) item).getMarkdownText();
                    history.add(new JSONObject().put("role", "assistant").put("content", content));
                } else if (item instanceof UserMessage) {
                    String content = ((UserMessage) item).getText();
                    history.add(new JSONObject().put("role", "user").put("content", content));
                }
            }
            // Append the current prompt as the last user message
            history.add(new JSONObject().put("role", "user").put("content", currentPrompt));
            return history;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void hideAiAssistantView() {
        if (aiViewAssistant != null) {
            aiViewAssistant.setVisibility(View.GONE);
            aiViewAssistant.setLastResponseTextView("");
        }
    }

    public void showUtilityRow() {
        // SmartAssistantBar is shown as part of the keyboard block.
        // Ensure it's visible and in the correct state.
        if (smartAssistantBar != null) {
            smartAssistantBar.setVisibility(VISIBLE);
            // Set to EXPANDED state when showing
            smartAssistantBar.setState(SmartAssistantBar.BarState.EXPANDED);
            // Populate suggestions based on the current committed text
            try {
                smartAssistantBar.onUserInput(getCommitedText());
            } catch (Exception e) {
                // If input retrieval fails, fall back to showing default suggestions.
                smartAssistantBar.onUserInput("");
            }
        }
    }

    public void hideUtilityRow() {
        // Hide the SmartAssistantBar when needed
        if (smartAssistantBar != null) {
            smartAssistantBar.setVisibility(View.GONE);
        }
    }

// ============================================================================================
// TUTORIAL HIGHLIGHT - Add/Update these methods in KeyboardSwitcher.java
// ============================================================================================

    /**
     * Checks if tutorial mode is active and highlights the appropriate CTA button.
     * Should be called when the keyboard becomes visible.
     */
    public void checkAndApplyTutorialHighlight() {

        Log.d(TAG, "ðŸ” DEBUG checkAndApplyTutorialHighlight:");
        Log.d(TAG, "   - tutorialManager: " + (tutorialManager != null));
        Log.d(TAG, "   - isTutorialMode: " + (tutorialManager != null ? tutorialManager.isTutorialMode() : "N/A"));
        Log.d(TAG, "   - currentTask: " + (tutorialManager != null ? tutorialManager.getCurrentTask() : "N/A"));
        Log.d(TAG, "   - buttonToHighlight: " + (tutorialManager != null ? tutorialManager.getButtonToHighlight() : "N/A"));
        Log.d(TAG, "   - currentHighlighted: " + (smartAssistantBar != null ? smartAssistantBar.getCurrentHighlightedButtonType() : "N/A"));

        if (smartAssistantBar == null) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "ðŸ”´ checkAndApplyTutorialHighlight: utilityRow is null");
            }
            return;
        }

        // Always stop current highlight first
        smartAssistantBar.stopHighlight();

        if (tutorialManager == null) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "ðŸ”´ checkAndApplyTutorialHighlight: tutorialManager is null");
            }
            return;
        }

        // Check if tutorial mode is active
        if (!tutorialManager.isTutorialMode()) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "ðŸ”´ checkAndApplyTutorialHighlight: not in tutorial mode");
            }
            return;
        }

        // Get fresh button to highlight based on CURRENT task
        String buttonToHighlight = tutorialManager.getButtonToHighlight();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "ðŸŽ¯ checkAndApplyTutorialHighlight: buttonToHighlight = " + buttonToHighlight);
        }

        if (buttonToHighlight != null) {
            // Small delay to ensure keyboard is fully visible
            if (mainHandler != null) {
                mainHandler.postDelayed(() -> {
                    if (smartAssistantBar != null && tutorialManager != null && tutorialManager.isTutorialMode()) {
                        // Double-check we should still highlight this button
                        String currentButton = tutorialManager.getButtonToHighlight();
                        if (buttonToHighlight.equals(currentButton)) {
                            smartAssistantBar.highlightButton(buttonToHighlight);
                            if (DebugConfig.isDebugMode) {
                                Log.d(TAG, "âœ… Tutorial highlight applied: " + buttonToHighlight);
                            }
                        } else {
                            if (DebugConfig.isDebugMode) {
                                Log.d(TAG, "âš ï¸ Button changed during delay. Expected: " + buttonToHighlight + ", Current: " + currentButton);
                            }
                        }
                    }
                }, 200); // Reduced delay for faster response
            }
        }
    }

    private void registerTutorialHighlightReceiver() {
        tutorialHighlightReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.wittykeys.tutorial.HIGHLIGHT_BUTTON".equals(action)) {
                    String buttonType = intent.getStringExtra("button_type");
                    if (buttonType != null && smartAssistantBar != null) {
                        mainHandler.post(() -> {
                            smartAssistantBar.highlightButton(buttonType);
                            if (DebugConfig.isDebugMode) {
                                Log.d(TAG, "ðŸŽ¯ Received highlight broadcast for: " + buttonType);
                            }
                        });
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter("com.wittykeys.tutorial.HIGHLIGHT_BUTTON");
        LocalBroadcastManager.getInstance(mLatinIME).registerReceiver(tutorialHighlightReceiver, filter);
    }

    // ===== Emoji Search Mode =====
    // When emoji search is active, we show BOTH the emoji keyboard (search bar + results grid)
    // and the main keyboard (QWERTY) so the user can type their search query.
    // Layout: emoji keyboard (compact, weight fills top) + keyboard keys (wrap_content, bottom)
    private boolean emojiSearchMode = false;

    /**
     * Enter emoji search mode: show main keyboard below the emoji search bar + results.
     * Called by EmojiKeyboard.activateSearch().
     */
    public void enterEmojiSearchMode() {
        if (emojiKeyboard == null || mKeyboardBlockContainer == null) return;
        emojiSearchMode = true;

        // Fix the parent frame height so both children share the space
        if (mMainKeyboardFrame != null) {
            int totalHeight = mMainKeyboardFrame.getHeight();
            if (totalHeight <= 0) totalHeight = mCalculatedKeyboardHeight;
            ViewGroup.LayoutParams frameLp = mMainKeyboardFrame.getLayoutParams();
            frameLp.height = totalHeight;
            mMainKeyboardFrame.setLayoutParams(frameLp);
        }

        // Show main keyboard (QWERTY) — wrap_content at bottom
        mKeyboardBlockContainer.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams kbLp = mKeyboardBlockContainer.getLayoutParams();
        kbLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mKeyboardBlockContainer.setLayoutParams(kbLp);

        // Hide SAB during search (not needed, and saves space)
        if (smartAssistantBar != null) smartAssistantBar.setVisibility(View.GONE);

        // Emoji keyboard: weight=1 to fill remaining space above the keyboard
        emojiKeyboard.setVisibility(View.VISIBLE);
        android.widget.LinearLayout.LayoutParams emojiLp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        emojiKeyboard.setLayoutParams(emojiLp);

        // Hide other product views
        if (mUnifiedAiView != null) mUnifiedAiView.setVisibility(View.GONE);

        // Ensure correct order: emoji search on TOP, QWERTY keyboard on BOTTOM.
        // bringToFront() in a LinearLayout moves the view to the last child position (bottom).
        mKeyboardBlockContainer.bringToFront();

        if (mCurrentInputView != null) mCurrentInputView.requestLayout();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "Entered emoji search mode — keyboard + search visible");
        }
    }

    /**
     * Exit emoji search mode: restore full emoji keyboard, hide main keyboard.
     * Called by EmojiKeyboard.deactivateSearch().
     */
    public void exitEmojiSearchMode() {
        if (emojiKeyboard == null || mKeyboardBlockContainer == null) return;
        emojiSearchMode = false;

        // Restore parent frame to wrap_content
        if (mMainKeyboardFrame != null) {
            ViewGroup.LayoutParams frameLp = mMainKeyboardFrame.getLayoutParams();
            frameLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mMainKeyboardFrame.setLayoutParams(frameLp);
        }

        // Restore SAB visibility
        if (smartAssistantBar != null) smartAssistantBar.setVisibility(View.VISIBLE);

        // Switch back to full emoji keyboard
        showMainViewComponent(emojiKeyboard);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "Exited emoji search mode — full emoji keyboard restored");
        }
    }

    public boolean isEmojiSearchMode() {
        return emojiSearchMode;
    }

    // ToggleEmojiKeyboard is updated to use the new system.
    public void toggleEmojiKeyboardView() {
        if (emojiKeyboard != null && mKeyboardBlockContainer != null) {
            if (emojiKeyboard.isShown()) {
                showKeyboardView(); // Switch back to the full keyboard block
            } else {
                emojiKeyboard.onShow(); // Reset stale search state before showing
                showMainViewComponent(emojiKeyboard); // Switch to just the emoji keyboard
            }
        }
    }


    public boolean isKeyboardShowing() {
        return mKeyboardView != null && mKeyboardView.isShown();
    }

    public String getCommitedText() {
        RichInputConnection mConnection = mLatinIME.getInputLogicInstance().mConnection;
        String commitedText = mConnection.getCommitedText();
        return commitedText;
    }

    public void toggleVisibility(boolean isVisible) {
        if (isVisible) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mLatinIME.requestShowSelf(InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            mLatinIME.requestHideSelf(0);
        }
    }

    public void showAIReplyView() {
        hideUtilityRow();
    }

    public void toggleAccessibilityService(boolean enable) {
        if (mLatinIME != null) {
            mLatinIME.toggleAccessibilityService(enable);
        }
    }


    public void resetKeyboardDefaultState() {
        if (smartAssistantBar != null && mUnifiedAiView != null) {
            toggleVisibility(true);
            toggleAccessibilityService(false);
            // Do NOT call clearSession() here — it hides all UnifiedAiView children
            // without switching back to QWERTY, leaving a blank screen. The keyboard
            // was on a valid view before the screenshot attempt; leave it there.
        }
    }

    // This is the core of the on-device analysis.
    private AppContext determineContext() {
        String packageName = mLatinIME.getCurrentInputEditorPackageName();

        AccessibilityNodeInfo rootNode = null;
        ScreenReaderAccessibility accessibilityService = ScreenReaderAccessibility.getInstance();
        if (accessibilityService != null) {
            rootNode = accessibilityService.getLatestRootNode();
        }
        Log.d(TAG, "Current input editor package name: " + packageName + ", RootNode: " + rootNode);

        if (packageName == null || rootNode == null) {
            Log.e(TAG, "Could not determine context: PackageName or RootNode is null.");
            return new AppContext("Other", "Other"); // Return a fallback context
        }

        // --- You must customize this logic with real View IDs from the Layout Inspector ---
        if (packageName.contains("tinder") || packageName.contains("hinge") || packageName.contains("bumble")) {
            // Example for Hinge profile view
            if (!rootNode.findAccessibilityNodeInfosByText("Send a Compliment").isEmpty()) {
                return new AppContext("Dating", "Profile");
            }
            return new AppContext("Dating", "Chat"); // Default for dating apps if not a profile
        }

        if (packageName.contains("whatsapp") || packageName.contains("telegram")) {
            return new AppContext("Chat", "Chat");
        }

        if (packageName.contains("instagram")) {
            // You can add logic to differentiate between Story, Post, Comments etc.
            return new AppContext("Social", "Post");
        }

        if (packageName.contains("gm") || packageName.contains("mail")) {
            return new AppContext("Mail", "Mail_Thread");
        }

        return new AppContext("Other", "Other");
    }

    public AppContext getStoredAppContext() {
        return mCurrentScreenContext;
    }

    // New entry point for starting the screen capture process AFTER user selects an option
    public void startScreenCapture() {
        List<String> debugTexts = getRecievedTextFromAccessibility();
        Log.w(TAG, "startScreenCapture: receivedTexts size=" + (debugTexts == null ? 0 : debugTexts.size()));
        // No handler, no delay. Just start the activity immediately.
        String currentPackageName = mLatinIME.getCurrentInputEditorPackageName();
        Intent intent = new Intent(mThemeContext, ScreenshotPermissionActivity.class);
        intent.putExtra("SCREEN_TARGET_PACKAGE_NAME", currentPackageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        mThemeContext.startActivity(intent);
    }

    /**
     * Called immediately after screenshot is captured (before analysis).
     * Navigates to AI Chat and shows analyzing state right away.
     */
    public void onScreenshotCaptured(String imagePath) {
        Log.d(TAG, "[Build7.0] Screenshot captured, navigating to AI Chat");
        if (mUnifiedAiView == null) return;
        if (!isAiViewVisible()) {
            hideUtilityRow();
            showMainViewComponent(mUnifiedAiView);
            mInAiChatConversation = true;
            mUnifiedAiView.setViewTitle("AI Chat", AIFeatureType.AI_CHAT_WRITTEN);
        }
        mUnifiedAiView.onScreenshotCaptured(imagePath);
    }

    /**
     * Build 7.0: Receive screenshot analysis result from ScreenCaptureService.
     * Called on main thread. Routes to SAB and/or UnifiedAiView.
     */
    public void onScreenshotAnalysisReceived(String jsonResult) {
        Log.d(TAG, "[Build7.0] Screenshot analysis received");
        try {
            org.json.JSONObject result = new org.json.JSONObject(jsonResult);
            String type = result.optString("type", "");

            if ("screenshot_analysis".equals(type)) {
                String analysis = result.optString("analysis", "");
                String imagePath = result.optString("image_path", "");

                if (mUnifiedAiView != null) {
                    // AI view should already be visible from onScreenshotCaptured, but guard anyway
                    if (!isAiViewVisible()) {
                        hideUtilityRow();
                        showMainViewComponent(mUnifiedAiView);
                        mInAiChatConversation = true;
                        mUnifiedAiView.setViewTitle("AI Chat", AIFeatureType.AI_CHAT_WRITTEN);
                    }
                    // Do NOT call ensureSessionOpen() — it loads old session messages and races
                    mUnifiedAiView.onScreenshotAnalysisReceived(imagePath, analysis);
                }

                if (smartAssistantBar != null) {
                    smartAssistantBar.onScreenshotAnalysisReceived(analysis);
                }
            } else if ("screenshot_analysis_error".equals(type)) {
                String error = result.optString("error", "");
                Log.e(TAG, "[Build7.0] Screenshot analysis error: " + error);
                if (mUnifiedAiView != null) {
                    mUnifiedAiView.onScreenshotAnalysisError(error);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[Build7.0] Error processing screenshot analysis", e);
        }
    }

    public void onRepliesReceived(final String jsonResponse) {
        mainHandler.post(() -> {
            Log.d("ScanFlow", "Step 5: Received JSON response in KeyboardSwitcher. Parsing now.");

            // First, remove the loading indicator that was shown after the metadata card.
            mUnifiedAiView.removeLastItem();

            Log.d(TAG, "onRepliesReceived called with json=" + jsonResponse);
            try {
                JSONObject response = new JSONObject(jsonResponse);
                // 1. Extract the task result and display it as an AiMessage
                String taskResult = response.optString("task_result", "");
                // Update the last AI result so suggestions and regenerate can operate on it.
                mLastAiResult = taskResult;
                String metadata = response.optString("metadata", "");
                Log.d(TAG,"metaData: "+ metadata);

                // Always remove the loading indicator (already removed in caller)
                if (!taskResult.isEmpty()) {
                    // Use the regenerate CTA so the user can fetch additional variations of the same prompt
                    mUnifiedAiView.addItem(new AiMessage(taskResult, project.witty.keys.keyboard.AiChat.CtaType.REGENERATE_COPY_REPLY));
                    recordAiUsage();
                }

                // If metadata is provided, update the metadata card. Otherwise leave existing detail.
                if (!metadata.isEmpty()) {
                    updateMetadataCardDetails(metadata);
                }

                // 3. Extract suggestions from the AI response and display them as clickable options (if any)
                JSONArray suggestions = response.optJSONArray("suggestions");
                if (suggestions != null && suggestions.length() > 0) {
                    List<String> suggestionList = new ArrayList<>();
                    for (int i = 0; i < suggestions.length(); i++) {
                        String suggestion = suggestions.optString(i);
                        if (suggestion != null && !suggestion.isEmpty()) {
                            suggestionList.add(suggestion);
                        }
                    }
                    if (!suggestionList.isEmpty()) {
                        mUnifiedAiView.addItem(new HorizontalOptions("Suggestions:", suggestionList, OptionsType.SUGGESTION));
                    }
                }

                // If both taskResult and metadata were empty, interpret as error
                if (taskResult.isEmpty() && metadata.isEmpty()) {
                    // Provide a generic error message
                    mUnifiedAiView.addItem(new AiMessage("AI service did not return any valid data.", CtaType.REPLY_COPY));
                }

            } catch (JSONException e) {
                Log.e("ScanFlow", "Step 5.1: Failed to parse JSON response.", e);
                // Show a user-friendly error message
                mUnifiedAiView.addItem(new AiMessage("Error parsing AI response.", CtaType.REPLY_COPY));
            }
        });
    }

    private void updateMetadataCardDetails(String newDetails) {
        // This is a bit tricky, we need to find the card in our chat list
        List<ChatItem> chatItems = mUnifiedAiView.getChatItems(); // Need a getter in UnifiedAiView
        if (chatItems != null) {
            for (int i = 0; i < chatItems.size(); i++) {
                ChatItem item = chatItems.get(i);
                if (item instanceof MetadataCard) {
                    // Found it! We need a way to update its content.
                    // Let's add a `details` field to the MetadataCard model.
                    ((MetadataCard) item).setGeneratedDetails(newDetails);
                    Log.d(TAG, "MetadataCard updated with details: " + newDetails);
                    mUnifiedAiView.notifyItemChanged(i); // Tell the adapter to re-bind this specific item
                    return;
                }
            }
        }
    }

    // NEW method to handle the context received from the service
    public void onScreenContextReceived(ScreenContext context) {
        if (mainHandler == null) {
            Log.w(TAG, "onScreenContextReceived: mainHandler is null, skipping update");
            return;
        }
        mainHandler.post(() -> {
            Log.d(TAG, "onScreenContextReceived: Updating UI with metadata card. Context type=" + context.getClass().getSimpleName());
            if (mUnifiedAiView != null) {
                mUnifiedAiView.startNewSession(Arrays.asList(
                        new MetadataCard(context),
                        Loading.INSTANCE
                ));
            }
        });
    }


    public String getCurrentUserAction() {
        return mCurrentUserAction;
    }

    public void setCurrentUserAction(String action) {
        this.mCurrentUserAction = action;
    }


// ========================================================================================
    // === NEW CENTRALIZED VIEW SWITCHING LOGIC ===
    // ========================================================================================

    // KeyboardSwitcher.java
    private void showMainViewComponent(View viewToShow) {
        if (viewToShow == null) return;

        final View[] productViews = { mUnifiedAiView, emojiKeyboard };
        final int targetHeight = (mMainKeyboardFrame != null && mMainKeyboardFrame.getHeight() > 0)
                ? mMainKeyboardFrame.getHeight()
                : mCalculatedKeyboardHeight;

        if (viewToShow == mKeyboardBlockContainer) {
            // Show the whole keyboard block as-is (wrap content)
            if (mKeyboardBlockContainer != null) {
                ViewGroup.LayoutParams lp = mKeyboardBlockContainer.getLayoutParams();
                if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    mKeyboardBlockContainer.setLayoutParams(lp);
                }
                mKeyboardBlockContainer.setVisibility(View.VISIBLE);
                mKeyboardBlockContainer.bringToFront();
            }
            if (smartAssistantBar != null) smartAssistantBar.setVisibility(View.VISIBLE);
            if (smartAssistantBar != null) smartAssistantBar.setVisibility(View.VISIBLE);

            // Panels must be GONE so they donâ€™t steal touches
            for (View v : productViews) if (v != null) v.setVisibility(View.GONE);
        } else {
            // Hide keyboard block entirely so it doesn't overlap
            if (mKeyboardBlockContainer != null) mKeyboardBlockContainer.setVisibility(View.GONE);

            // Size and show only the requested product view
            for (View v : productViews) {
                if (v == null) continue;
                if (v == viewToShow) {
                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                    if (lp != null && targetHeight > 0 && lp.height != targetHeight) {
                        lp.height = targetHeight;
                        v.setLayoutParams(lp);
                    }
                    v.setVisibility(View.VISIBLE);
                    v.bringToFront();
                    // Propagate theme when a product view becomes visible
                    if (v instanceof Themeable && mCurrentInputView != null) {
                        ((Themeable) v).onThemeChanged(mCurrentInputView.getContext());
                    }
                    // Apply nav bar padding so content isn't hidden behind gesture bar.
                    // Use mCurrentInputView as the inset source — it's always attached/visible
                    // so getRootWindowInsets() reliably returns gesture nav bar height on API 30+.
                    View insetSource = mCurrentInputView != null ? mCurrentInputView : v;
                    int navBarHeight = NavigationBarHelper.getSafeBottomPadding(insetSource);
                    if (v == mUnifiedAiView) {
                        v.setPadding(0, 0, 0, navBarHeight);
                    } else if (v == emojiKeyboard
                            && emojiKeyboard instanceof project.witty.keys.keyboard.EmojiKeyboard.EmojiKeyboard) {
                        ((project.witty.keys.keyboard.EmojiKeyboard.EmojiKeyboard) emojiKeyboard)
                            .updateNavigationBarPadding();
                    }
                } else {
                    v.setVisibility(View.GONE);
                }
            }
        }

        // Refresh layout and insets so touch region matches the new visible view
        if (mCurrentInputView != null) mCurrentInputView.requestLayout();
        View vis = getVisibleKeyboardView();
        if (vis != null) vis.requestLayout();
    }

    public void setCalculatedKeyboardHeight(int height) {
        if (height > 0) {
            mCalculatedKeyboardHeight = height;
            // If a panel is currently visible, re-apply its size now.
            if (mUnifiedAiView != null && mUnifiedAiView.isShown()) {
                resizeProductPanelsTo(height);
            } else if (emojiKeyboard != null && emojiKeyboard.isShown()) {
                resizeProductPanelsTo(height);
            }
        }
    }


    public int getCalculatedKeyboardHeight() {
        return mCalculatedKeyboardHeight;
    }

    @Deprecated
    public SuggestionRow getSuggestionRow() {
        return null; // Legacy method - use getSmartAssistantBar() instead
    }

    public SmartAssistantBar getSmartAssistantBar() {
        return smartAssistantBar;
    }

    /**
     * Return the currently active InternalInputTarget, checking all possible sources:
     * 1. SAB's InternalInputView (custom tone/translate prompt) — already registered on LatinIME
     * 2. EmojiKeyboard's InternalSearchView (emoji search)
     * Returns null if no target is active.
     */
    public InternalInputTarget getActiveInternalInputTarget() {
        // 1. Check SAB's registered target (SAB sets this via LatinIME.setInternalInputTarget)
        if (mLatinIME != null) {
            InternalInputTarget sabTarget = mLatinIME.getInternalInputTarget();
            if (sabTarget != null && sabTarget.isActive()) {
                return sabTarget;
            }
        }
        // 2. Check emoji keyboard search bar (visible during emoji search mode)
        if (emojiKeyboard != null && (emojiKeyboard.isShown() || emojiSearchMode)) {
            InternalSearchView search = emojiKeyboard.getSearchView();
            if (search != null && search.isActive()) {
                return search;
            }
        }
        // 3. Check AI chat input (visible when UnifiedAiView is shown)
        if (mUnifiedAiView != null && mUnifiedAiView.isShown()) {
            InternalInputTarget chatInput = mUnifiedAiView.getChatInput();
            if (chatInput != null && chatInput.isActive()) {
                return chatInput;
            }
        }
        return null;
    }

    /**
     * Generate custom replies from user's prompt (typed in host app's input field).
     * Called when user taps Generate in custom mode.
     */
    private void generateCustomRepliesFromPrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            if (smartAssistantBar != null) {
                smartAssistantBar.showCustomModeError("Empty prompt");
            }
            return;
        }

        Log.d(TAG, "[CustomMode] Generating custom replies for prompt: " + prompt.substring(0, Math.min(30, prompt.length())) + "...");

        // Phase 2B: Use ReplyGenerator's custom prompt builder
        String systemPrompt = project.witty.keys.app.context.ReplyGenerator.buildCustomPrompt(prompt);

        String userPrompt = prompt;

        // Conversation context was previously sourced from MemoryViewData (removed in Build 7.1)
        // TODO: Source context from ConversationMatcher or NLS pipeline if needed

        // Call Claude API with callback
        project.witty.keys.api.ClaudeApi claudeApi = new project.witty.keys.api.ClaudeApi();
        claudeApi.generateReplies(systemPrompt, userPrompt, new project.witty.keys.api.ClaudeApi.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                // Update UI on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (smartAssistantBar != null) {
                        // Take first 3 replies
                        List<String> limitedReplies = replies.size() > 3
                            ? replies.subList(0, 3)
                            : replies;
                        smartAssistantBar.showCustomReplies(limitedReplies);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "[CustomMode] Error generating custom replies: " + error);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (smartAssistantBar != null) {
                        smartAssistantBar.showCustomModeError(error);
                    }
                });
            }
        });
    }

    public void showAiChatView() {
        hideUtilityRow();
        showMainViewComponent(mUnifiedAiView);
        // Ensure nav bar padding is applied for full takeover mode
    }

    /**
     * Show Reply Mode: keyboard visible with AI header+input strip above it.
     * First shows the keyboard normally, then overlays a compact AI header strip
     * by setting the SmartAssistantBar visibility to GONE and making the
     * UnifiedAiView visible with a fixed small height for header+input only.
     */
    public void showReplyModeView() {
        // 1. First ensure keyboard is showing properly
        if (mKeyboardBlockContainer != null) {
            ViewGroup.LayoutParams kbLp = mKeyboardBlockContainer.getLayoutParams();
            if (kbLp != null && kbLp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                kbLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mKeyboardBlockContainer.setLayoutParams(kbLp);
            }
            mKeyboardBlockContainer.setVisibility(View.VISIBLE);
            mKeyboardBlockContainer.bringToFront();
        }

        // 2. Hide SAB — replaced by AI header strip
        if (smartAssistantBar != null) smartAssistantBar.setVisibility(View.GONE);

        // 3. Hide other product views
        if (emojiKeyboard != null) emojiKeyboard.setVisibility(View.GONE);

        // 4. Show UnifiedAiView with fixed height matching SAB height exactly
        //    SAB = 101dp (wk_bar_height). Strip must match so composite screenshot
        //    has zero gap between header strip and QWERTY keys.
        //    Content: 40dp header + ~61dp input (input expands via weight to fill)
        if (mUnifiedAiView != null) {
            float density = mUnifiedAiView.getResources().getDisplayMetrics().density;
            int stripHeight = (int) (101 * density); // 101dp: match SAB height exactly
            ViewGroup.LayoutParams lp = mUnifiedAiView.getLayoutParams();
            if (lp != null) {
                lp.height = stripHeight;
                mUnifiedAiView.setLayoutParams(lp);
            }
            mUnifiedAiView.setVisibility(View.VISIBLE);
            // Clear nav-bar padding set by showAiChatView() — strip mode needs no bottom padding
            mUnifiedAiView.setPadding(0, 0, 0, 0);
            if (mCurrentInputView != null) {
                ((Themeable) mUnifiedAiView).onThemeChanged(mCurrentInputView.getContext());
            }
        }

        // 5. Refresh layout
        if (mCurrentInputView != null) mCurrentInputView.requestLayout();
        View vis = getVisibleKeyboardView();
        if (vis != null) vis.requestLayout();
    }

    public void performAiAction(AiAction action, @Nullable String data) {
        performAiAction(action, data, null); // Call the main method with null subData
    }

    /**
     * Follow-up AI action: skips view transition (keyboard stays in reply mode).
     * Used when UnifiedAiView sends follow-up messages in an active chat.
     * The view is already visible — we only need to fire the AI request.
     */
    public void performAiActionFollowUp(AiAction action, @Nullable String data) {
        // DO NOT call hideUtilityRow() or showMainViewComponent()
        Log.d(TAG, "performAiActionFollowUp: action=" + action + " data.length=" + (data != null ? data.length() : 0));

        switch (action) {
            case AI_CHAT:
                FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(mLatinIME);
                User user = EncryptedPreferences.getUserLoggedInInfo();
                String userId = (user != null ? user.getId() : null);
                EventHelpers.triggerAiChatInitiatedEvent(userId, data != null ? data.length() : 0, analytics);
                mInAiChatConversation = true;

                if (data != null && !data.isEmpty()) {
                    // Add user message + loading indicator to existing conversation
                    mUnifiedAiView.addItem(new project.witty.keys.keyboard.AiChat.UserMessage(data));
                    maybeAddPendingHeader();
                    mUnifiedAiView.addItem(Loading.INSTANCE);

                    // Fire AI request — same as performAiAction AI_CHAT case
                    executeGptApiWithChatUi(data, CtaType.REPLY_COPY);
                }
                break;

            default:
                // Non-chat actions: fall through to full performAiAction
                performAiAction(action, data);
                break;
        }
    }

    // --- NEW: Central Dispatcher Method ---
    public void performAiAction(AiAction action, @Nullable String data, @Nullable String subData) {
        FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(mLatinIME);
        User user = EncryptedPreferences.getUserLoggedInInfo();
        String userId = (user != null ? user.getId() : null);
        // 1. Show the UnifiedAiView and hide the main keyboard
        hideUtilityRow();
        if (action != AiAction.SCAN_AND_EXECUTE) {
            showMainViewComponent(mUnifiedAiView);
        }

        // Update context pill with current app info
        if (mCurrentScreenContext != null && mUnifiedAiView != null) {
            mUnifiedAiView.setContextInfo(
                mCurrentScreenContext.appType,
                mCurrentScreenContext.extractedText != null ? mCurrentScreenContext.extractedText.get("contactName") : null
            );
        }

        // 2. Prepare the initial UI state in the chat view
        switch (action) {
            case CORRECT_GRAMMAR:
                // Grammar corrections should start a new conversation context
                EventHelpers.triggerGrammarCorrectionEvent(userId, data != null ? data.length() : 0, analytics);

                mInAiChatConversation = false;
                if (data == null || data.isEmpty()) {
                    Toast.makeText(mThemeContext, "Please enter some text first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                mUnifiedAiView.setViewTitle("Grammar", AIFeatureType.GRAMMAR);
                // Start a new session in the chat view with the user's text and a loading indicator
                mUnifiedAiView.startNewSession(Arrays.asList(
                        new UserMessage(data),
                        Loading.INSTANCE
                ));

                // 3. Generate the prompt and execute the API call
                String promptText = promptGenerator.getCustomPromptForKeyboardInput(AIFeatureType.GRAMMAR, data, null);
                executeGptApiWithChatUi(promptText, CtaType.APPLY_COPY);
                break;
            case CHANGE_TONE:
                // Tone change is a separate context
                mInAiChatConversation = false;
                if (subData == null) { // This is the INITIAL call to show the options
                    EventHelpers.triggerToneChangeInitiatedEvent(userId, data != null ? data.length() : 0, analytics);

                    // Get the list of tones from ToneData
                    if (data == null || data.isEmpty()) {
                        Toast.makeText(mThemeContext, "Please enter some text first.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    mUnifiedAiView.setViewTitle("Change Tone",AIFeatureType.REPHRASE_WRITTEN);

                    List<String> toneOptions = new ArrayList<>(ToneData.getToneEmojiMap().keySet());
                    mUnifiedAiView.startNewSession(Arrays.asList(
                            new UserMessage(data),
                            new HorizontalOptions("Choose a tone:", toneOptions, OptionsType.TONE)
                    ));
                } else { // This is the SECOND call, after a tone has been selected
                    // Add a loading indicator while we call the API
                    mUnifiedAiView.setViewTitle("Change Tone",AIFeatureType.REPHRASE_WRITTEN);
                    mUnifiedAiView.addItem(new project.witty.keys.keyboard.AiChat.SystemMessage("Tone: " + subData));
                    mUnifiedAiView.addItem(Loading.INSTANCE);
                    // Generate prompt with the selected tone (subData)
                    String promptTextForTone = promptGenerator.getCustomPromptForGenerativeActions(
                            AIFeatureType.REPHRASE_WRITTEN, // You might need to adapt this enum or prompt generator
                            null,
                            subData,// The selected tone
                            data // The original text
                    );
                    // For tones, we expect multiple suggestions, so the CTA type is different
                    executeGptApiWithChatUi(promptTextForTone, CtaType.SUGGESTIONS);
                }
                break;
            case AI_CHAT: // <-- Add title case
                EventHelpers.triggerAiChatInitiatedEvent(userId, data != null ? data.length() : 0, analytics);

                mInAiChatConversation = true;
                mUnifiedAiView.setViewTitle("AI Chat", AIFeatureType.AI_CHAT_WRITTEN);

                // No prompt text → show session list (user browses existing sessions or starts new)
                if (data == null || data.isEmpty()) {
                    mUnifiedAiView.setUIState(UnifiedAiView.STATE_SESSIONS_LIST);
                    break;
                }

                java.util.List<project.witty.keys.keyboard.AiChat.ChatItem> currentItems = mUnifiedAiView.getChatItems();
                if (currentItems == null || currentItems.isEmpty()) {
                    java.util.List<project.witty.keys.keyboard.AiChat.ChatItem> items = new java.util.ArrayList<>();
                    items.add(new project.witty.keys.keyboard.AiChat.UserMessage(data));

                    // NEW: pending header support
                    if (mPendingHeaderText != null) {
                        items.add(new project.witty.keys.keyboard.AiChat.SystemMessage(mPendingHeaderText));
                        mPendingHeaderText = null;
                    }

                    items.add(Loading.INSTANCE);
                    mUnifiedAiView.startNewSession(items);
                } else {
                    mUnifiedAiView.addItem(new project.witty.keys.keyboard.AiChat.UserMessage(data));
                    // NEW: pending header support
                    maybeAddPendingHeader();
                    mUnifiedAiView.addItem(Loading.INSTANCE);
                }

                executeGptApiWithChatUi(data, CtaType.REPLY_COPY);
                break;
            case SHOW_TRANSLATE_OPTIONS: // <-- Add title case
                // Translation is a separate context
                EventHelpers.triggerTranslationInitiatedEvent(userId, data != null ? data.length() : 0, analytics);
                mInAiChatConversation = false;
                if (data == null || data.isEmpty()) {
                    Toast.makeText(mThemeContext, "Please enter text to translate.", Toast.LENGTH_SHORT).show();
                    mUnifiedAiView.clearSession();
                    return;
                }
                mUnifiedAiView.setViewTitle("Translate",AIFeatureType.TRANSLATE_WRITTEN);

                Set<String> languageSet = new LinkedHashSet<>(LanguageFlags.getLanguageFlags().keySet());
                List<String> languageOptions = new ArrayList<>(languageSet);

                mUnifiedAiView.startNewSession(Arrays.asList(
                        new UserMessage(data),
                        new HorizontalOptions("Translate to:", languageOptions, OptionsType.LANGUAGE)
                ));

                // The second part of the translation flow will be triggered from the ViewHolder
                break;
            case SHOW_SCAN_OPTIONS:
                // Scanning is a separate context

                mInAiChatConversation = false;
                mUnifiedAiView.setViewTitle("Generate Reply", AIFeatureType.GENERATE_READ_REPLY);
                // Prepare the grid options using your ToneData class or a new one
                List<CategoryOption> options = new ArrayList<>();
                int optionCount = options.size(); // use the actual list you build
                EventHelpers.triggerScanOptionsShownEvent(userId, optionCount, analytics);
                // TODO: Use real icons
                options.add(new CategoryOption(R.drawable.gen_ai_icon, "Summarise", "summarise"));
                options.add(new CategoryOption(R.drawable.gen_ai_icon, "Simplify", "simplify"));
                options.add(new CategoryOption(R.drawable.translate_v2_icon, "Translate", "translate"));
                options.add(new CategoryOption(R.drawable.translate_v2_icon, "Verify Facts", "Verify Facts"));

                // Add reply categories from ToneData
                for (String intent : ToneData.getLevel1Intents()) {
                    options.add(new CategoryOption(R.drawable.continue_v2_icon, intent, "reply_" + intent));
                }

                // Begin a new session with an instruction message and the category row.
                List<project.witty.keys.keyboard.AiChat.ChatItem> scanSession = new ArrayList<>();
                Context ctx = mThemeContext;
                scanSession.add(new project.witty.keys.keyboard.AiChat.SystemMessage(ctx.getString(R.string.choose_a_category))); // Show a prompt to choose
                // Provide a simple title for the category row.  We reuse the 'choose_a_category' string for consistency.
                scanSession.add(new GridOptions(ctx.getString(R.string.choose_a_category), options));
                mUnifiedAiView.startNewSession(scanSession);
                break;

            case SCAN_AND_EXECUTE:
                // Reset context for scan and execute
                mInAiChatConversation = false;
                Log.d(TAG, "SCAN_AND_EXECUTE invoked with task: " + subData);
                mUnifiedAiView.showLoadingAndClear();
                // Before capturing the screen, ensure accessibility service is enabled.
                boolean isEnabledScan = AccessibilityUtils.isAccessibilityServiceEnabled(mLatinIME, ScreenReaderAccessibility.class);
                if (!isEnabledScan) {
                    Log.d(TAG, "Accessibility service not enabled. Launching consent activity.");
                    Intent intent = new Intent(mThemeContext, AccessibilityConsentActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mThemeContext.startActivity(intent);
                    // Don't proceed with capture until the service is enabled.
                    return;
                }
                String finalTask = subData; // e.g. "Generate a 'Agree' replyâ€¦"
                EventHelpers.triggerScanIntentSelectedEvent(userId, finalTask, analytics);
                // Record the final task for regeneration. We also update the current user
                // action so the service can access it later. Storing both ensures that
                // regenerateLastPrompt() can restart this scan without losing context.
                setCurrentUserAction(subData);
                mLastScanTask = subData;
                Log.d(TAG, "Current user action set: " + getCurrentUserAction());
                // Enable the accessibility service to start capturing text while we capture the screen
                if (mLatinIME != null) {
                    mLatinIME.toggleAccessibilityService(true);
                }
                startScreenCapture();
                break;
        }
    }

    private void executeGptApiWithChatUi(String prompt, final CtaType ctaTypeForResponse) {
        // Record the last used prompt and CTA so we can regenerate on demand
        mLastPrompt = prompt;
        mLastCtaType = ctaTypeForResponse;

        // Build the conversation history. For AI chat replies (identified via the flag and CTA), include previous messages.
        List<JSONObject> conversationHistory;
        if (mInAiChatConversation && ctaTypeForResponse == CtaType.REPLY_COPY) {
            conversationHistory = buildConversationHistory(prompt);
            // If building failed for some reason, fall back to a minimal conversation
            if (conversationHistory == null || conversationHistory.isEmpty()) {
                conversationHistory = new ArrayList<>();
                try {
                    conversationHistory.add(new JSONObject().put("role", "user").put("content", prompt));
                } catch (JSONException e) {
                    // If JSON fails here, we can't proceed
                    e.printStackTrace();
                    ErrorMessage errorItem = new ErrorMessage("Failed to create request", () -> executeGptApiWithChatUi(prompt, ctaTypeForResponse));
                    mUnifiedAiView.replaceLastItem(errorItem);
                    return;
                }
            }
        } else {
            // For non-chat contexts, send only the user's prompt
            conversationHistory = new ArrayList<>();
            try {
                conversationHistory.add(new JSONObject().put("role", "user").put("content", prompt));
            } catch (JSONException e) {
                e.printStackTrace();
                // Create an error item and use it to replace the loading indicator
                ErrorMessage errorItem = new ErrorMessage("Failed to create request", () -> executeGptApiWithChatUi(prompt, ctaTypeForResponse));
                mUnifiedAiView.replaceLastItem(errorItem);
                return;
            }
        }

        String chatSystemPrompt = (mInAiChatConversation && ctaTypeForResponse == CtaType.REPLY_COPY)
            ? "You are a helpful AI assistant embedded in a mobile keyboard. "
                + "Keep your response concise and focused. Limit to 60 words maximum. "
                + "Do not use bullet points, headers, or markdown formatting unless essential. "
                + "Write in plain conversational text."
            : null;

        chatGPTApi.getChatGPTResponseForConversation(conversationHistory, chatSystemPrompt, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Network error. Please check your connection.";
                Log.d(TAG,e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> {
                    // On failure, replace the loading item with an error item that has a retry action.
                    // If there are no existing chat items (e.g. API call failed before loading indicator)
                    // then start a new session containing just the error message.  This prevents the UI
                    // from hanging with a blank screen when the network is unavailable.
                    ErrorMessage errorItem = new ErrorMessage(errorMessage, () -> executeGptApiWithChatUi(prompt, ctaTypeForResponse));
                    if (mUnifiedAiView.getChatItems().isEmpty()) {
                        java.util.List<project.witty.keys.keyboard.AiChat.ChatItem> list = new java.util.ArrayList<>();
                        list.add(errorItem);
                        mUnifiedAiView.startNewSession(list);
                    } else {
                        mUnifiedAiView.replaceLastItem(errorItem);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                // Use a try-with-resources block to ensure the response body is always closed
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        final String errorText = "API Error: " + response.code();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            ErrorMessage errorItem = new ErrorMessage(errorText, () -> executeGptApiWithChatUi(prompt, ctaTypeForResponse));
                            mUnifiedAiView.replaceLastItem(errorItem);
                        });
                        return;
                    }

                    String responseString = responseBody.string();
                    JSONObject jsonObject = new JSONObject(responseString);
                    JSONArray choices = jsonObject.getJSONArray("choices");
                    if (choices.length() == 0) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            ErrorMessage errorItem = new ErrorMessage("Empty response from AI.", () -> executeGptApiWithChatUi(prompt, ctaTypeForResponse));
                            mUnifiedAiView.replaceLastItem(errorItem);
                        });
                        return;
                    }

                    String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                    // TODO: Add your token counting logic here if needed

                    // --- THIS IS THE KEY LOGIC CHANGE ---
                    // Post the entire response handling logic to the main thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // Step 1: Always remove the loading indicator first.
                        // We remove it instead of replacing it because for suggestions,
                        // we will be adding multiple new items.
                        mUnifiedAiView.removeLastItem();

                        // Step 2: Decide how to display the content based on the CtaType.
                        switch (ctaTypeForResponse) {
                            case SUGGESTIONS:
                                // This is for "Change Tone" or other features that give multiple options.
                                // The AI might return a numbered list, bullet points, or just new lines.
                                // This parsing logic makes it robust.
                                String[] suggestions = content.split("\\n");
                                int suggestionsAdded = 0;
                                for (String suggestion : suggestions) {
                                    // Clean up the suggestion text
                                    String trimmed = suggestion.trim();
                                    // Remove list markers like "1. ", "- ", "* " etc.
                                    trimmed = trimmed.replaceAll("^\\d+\\.\\s*|^[*-]\\s*", "");
                                    // Remove quotes if the AI wraps the response in them
                                    trimmed = trimmed.replaceAll("^\"|\"$", "");

                                    if (!trimmed.isEmpty()) {
                                        // Add each valid suggestion as a separate AiMessage item
                                        mUnifiedAiView.addItem(new AiMessage(trimmed, CtaType.SUGGESTIONS));
                                        suggestionsAdded++;
                                    }
                                }
                                // If parsing fails and we add no suggestions, show an error.
                                if (suggestionsAdded == 0) {
                                    mUnifiedAiView.addItem(new AiMessage("Could not parse suggestions. Raw response: " + content, CtaType.APPLY_COPY));
                                } else {
                                    // Offer a regenerate option so the user can get more variations for the same prompt
                                    java.util.List<String> regenerateOptions = new java.util.ArrayList<>();
                                    regenerateOptions.add("Regenerate");
                                    mUnifiedAiView.addItem(new HorizontalOptions("Actions:", regenerateOptions, OptionsType.SUGGESTION));
                                }
                                recordAiUsage();
                                break;

                            case APPLY_COPY:
                            case REPLY_COPY:
                            default:
                                // This is the "normal" case for a single response (e.g., Grammar, AI Chat).
                                // We just add one AiMessage item and store the result for future suggestions.
                                mUnifiedAiView.addItem(new AiMessage(content, ctaTypeForResponse));
                                // Save the generated content as the last AI result for follow-up actions
                                mLastAiResult = content;
                                recordAiUsage();

                                // JourneyTracer: AI response rendered
                                String chatTraceId = JourneyTracer.getCurrentSmartReplyTrace();
                                if (chatTraceId != null) {
                                    try {
                                        org.json.JSONObject dataOut = new org.json.JSONObject();
                                        dataOut.put("response_length", content.length());
                                        JourneyTracer.step(chatTraceId, "RESPONSE_RENDERED", null, dataOut, "AI response displayed");
                                        JourneyTracer.complete(chatTraceId, true);
                                        JourneyTracer.setCurrentSmartReplyTrace(null);
                                    } catch (Exception ignored) {}
                                }
                                break;
                        }
                    });

                } catch (IOException | JSONException e) {
                    // This catches errors from responseBody.string() or JSON parsing
                    new Handler(Looper.getMainLooper()).post(() -> {
                        ErrorMessage errorItem = new ErrorMessage("Failed to parse response.", () -> executeGptApiWithChatUi(prompt, ctaTypeForResponse));
                        mUnifiedAiView.replaceLastItem(errorItem);
                    });
                }
            }
        });
    }
    public UnifiedAiView getUnifiedAiView() { return mUnifiedAiView; }

    /**
     * Invoked when a suggestion pill is clicked. Depending on the suggestion text, this method
     * delegates to the appropriate AI action using the most recently generated AI result as the
     * input. If no last result is available, the method will simply ignore the suggestion.
     *
     * Supported suggestions:
     *  - "Change Tone": shows the tone selector for the last AI output.
     *  - "Savage Reply": rephrases the last AI output using the "Savage" tone.
     *
     * If other suggestion text is supplied, it defaults to opening a new AI chat using the
     * suggestion as the prompt and the last AI output as context.
     *
     * @param suggestion the label of the clicked suggestion
     */
    public void handleSuggestion(String suggestion) {
        FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(mLatinIME);
        User user = EncryptedPreferences.getUserLoggedInInfo();
        String userId = (user != null ? user.getId() : null);
        EventHelpers.triggerSuggestionSelectedEvent(userId, suggestion, analytics);
        if (suggestion == null || suggestion.trim().isEmpty()) return;
        String normalized = suggestion.toLowerCase().trim();

        // Regenerate is handled separately
        if (normalized.equals("regenerate")) {
            setNextResponseHeader("Action: Regenerate");
            regenerateLastPrompt();
            return;
        }

        // Use the last AI result as the base text for any transformation actions. If it's empty,
        // fall back to the currently committed text from the editor so the user can still
        // transform their own input.
        String baseText = mLastAiResult;
        if (baseText == null || baseText.trim().isEmpty()) {
            baseText = getCommitedText();
        }

        // Suggestion-based dispatch. We look for keywords in the suggestion to map to our
        // supported keyboard actions. The order matters: more specific phrases like
        // "savage reply" should be checked before generic ones like "reply" or "tone".
        if (normalized.contains("savage")) {
            // Savage replies are a specific tone. Trigger CHANGE_TONE with the Savage tone.
            performAiAction(AiAction.CHANGE_TONE, baseText, "Savage");

        } else if (normalized.contains("tone")) {
            // For any suggestion containing "tone", open the tone picker on the last result.
            performAiAction(AiAction.CHANGE_TONE, baseText, null);
        } else if (normalized.contains("translate")) {
            // Initiate translation workflow
            performAiAction(AiAction.SHOW_TRANSLATE_OPTIONS, baseText);
        } else if (normalized.contains("grammar") || normalized.contains("correct")) {
            // Initiate grammar correction
            performAiAction(AiAction.CORRECT_GRAMMAR, baseText);
        } else if (normalized.contains("summarise") || normalized.contains("summarize") || normalized.contains("summary")) {
            // Summarise the last AI result. We'll craft a summarisation prompt and send
            // through the AI chat. It will respond with a summary.
            String summarisePrompt = "Summarise the following text:\n" + baseText;
            performAiAction(AiAction.AI_CHAT, summarisePrompt);
        } else if (normalized.contains("simplify") || normalized.contains("simple")) {
            // Simplify the last AI result. Craft a prompt accordingly.
            String simplifyPrompt = "Simplify the following text:\n" + baseText;
            performAiAction(AiAction.AI_CHAT, simplifyPrompt);
        } else if (normalized.contains("verify")) {
            // Cross verify facts. Ask the AI to fact-check the last result.
            String verifyPrompt = "Cross verify the facts in the following text and provide corrections if necessary:\n" + baseText;
            performAiAction(AiAction.AI_CHAT, verifyPrompt);
        } else if (normalized.contains("reply")) {
            // Generate a reply suggestion. We'll treat the suggestion as the style for reply.
            // For example, "witty reply" would produce a witty reply. Use our generative action
            // through Chat to handle this free-form suggestion.
            String replyPrompt = suggestion;
            performAiAction(AiAction.AI_CHAT, replyPrompt);
        } else {
            // Default: treat the suggestion as a prompt for a new AI chat conversation.
            performAiAction(AiAction.AI_CHAT, suggestion);
        }
    }

    /**
     * Re-executes the last AI prompt and displays the result. This method will also show a
     * loading indicator in the chat view. If no last prompt is recorded, it silently does
     * nothing.
     */
    public void regenerateLastPrompt() {
        // For chat and tone flows, mLastPrompt will be populated. For scan flows, mLastPrompt
        // remains empty but mLastScanTask is set. Use whichever is available.
        boolean hasChatPrompt = mLastPrompt != null && !mLastPrompt.trim().isEmpty();
        boolean hasScanTask = mLastScanTask != null && !mLastScanTask.trim().isEmpty();

        if (!hasChatPrompt && !hasScanTask) {
            // Nothing to regenerate
            return;
        }

        if (hasChatPrompt) {
            // Show a loading indicator and request a fresh variation. We append a note to the
            // prompt asking for a different alternative so the AI generates a new result.
            mUnifiedAiView.addItem(new project.witty.keys.keyboard.AiChat.SystemMessage("\uD83D\uDD04 Regenerating..."));
            mUnifiedAiView.addItem(Loading.INSTANCE);
            String augmentedPrompt = mLastPrompt + "\n\nPlease provide a different alternative to your previous response.";
            executeGptApiWithChatUi(augmentedPrompt, mLastCtaType);
        } else if (hasScanTask) {
            // Trigger another screen capture with the same final task. The performAiAction
            // method will handle showing loading states and launching the capture flow. We pass
            // null for data (original text) and the stored task as subData.
            performAiAction(AiAction.SCAN_AND_EXECUTE, null, mLastScanTask);
        }
    }

    /**
     * Begins a voice recognition session intended for AI prompt delivery. Before
     * invoking the recognizer, this method sets a flag so that when the
     * recognition result is processed, the onVoiceCommand handler will
     * directly call executeVoicePrompt() instead of parsing the command for
     * keywords or committing the text into the input field. This allows the
     * user to ask free-form questions and receive a single AI response in
     * the suggestion row without affecting the text currently being edited.
     */
    public void startVoiceRecognitionForAi() {
        // Enable the API call flag so that the recognised text will be sent
        // to ChatGPT after it is committed into the input field.
        mVoiceApiCallFlag = true;
        startVoiceRecognition();
    }

    /**
     * Begins listening for a single voice command from the user. If the speech recognizer has
     * not yet been created, it will be instantiated along with its intent and listener. Once
     * initialised, subsequent calls will reuse the same recognizer. The listener handles
     * recognition callbacks and dispatches the recognised text to {@link #onVoiceCommand}.
     */
    public void startVoiceRecognition() {
        // Determine an appropriate context for the speech recognizer. Prefer the
        // current input view's context if available, otherwise fall back to
        // the IME service itself. Using mThemeContext here can cause issues
        // because it is a themed wrapper rather than an actual application context.
        Context speechContext = (mCurrentInputView != null) ? mCurrentInputView.getContext() : mLatinIME;

        // Check microphone permission before starting recognition. If not granted,
        // instruct the user to enable it and return early.
        if (speechContext != null) {
            int permissionCheck = ContextCompat.checkSelfPermission(speechContext, Manifest.permission.RECORD_AUDIO);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                if (smartAssistantBar != null) {
                    smartAssistantBar.updateSuggestions(java.util.Collections.singletonList("Enable microphone permission"));
                    // Do not remain in voice mode when we cannot start recognition
                    smartAssistantBar.setVoiceMode(false);
                }
                // Launch the application details settings page so the user can grant the
                // microphone permission. This is one of the few ways to request a
                // runtime permission from within an input method service context.
                try {
                    Intent settingsIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    settingsIntent.setData(Uri.fromParts("package", speechContext.getPackageName(), null));
                    settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    speechContext.startActivity(settingsIntent);
                } catch (Exception e) {
                    // If we cannot launch settings, there's nothing else we can do. The
                    // suggestion row already indicates to the user that permission is needed.
                }
                return;
            }
        }

        // Provide immediate feedback to the user that listening has started. Reset the
        // suggestion row to text-suggestion mode to ensure it is visible, then enable
        // voice mode so that dictionary suggestions do not overwrite the "Listening..."
        // message.
        if (smartAssistantBar != null) {
            // Ensure we are in text suggestions mode (not clipboard or other states)
            smartAssistantBar.resetToTextSuggestions();
            smartAssistantBar.updateSuggestions(java.util.Collections.singletonList("Listening..."));
            smartAssistantBar.setVoiceMode(true);
            // Assign a cancel listener to stop voice recognition.  This must be set
            // every time we start listening so that the UI can cancel multiple
            // successive recognitions.
//            suggestionRow.setOnCancelVoiceListener(v -> cancelVoiceRecognition());
        }

        // Start the pulsing animation on the microphone icons in the utility row.  This
        // provides a visual indication that the system is actively listening for
        // speech.  The animation will be cleared when recognition finishes or is
        // cancelled.  We guard against a null utilityRow in case the view has not
        // been initialised yet.
        if (smartAssistantBar != null) {
            if (mVoiceApiCallFlag) {
                smartAssistantBar.startVoicePromptAnimation();
            } else {
                smartAssistantBar.startVoiceInputAnimation();
            }
        }

        // Initialise the recognizer and intent lazily
        if (mSpeechRecognizer == null) {
            try {
                // Use the resolved speechContext instead of the themed context when creating
                // the recognizer. This avoids issues with theme wrappers and ensures the
                // recognizer is bound to a real application context.
                mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(speechContext);
            } catch (Exception e) {
                // Speech recognition may not be available on all devices. Simply return.
                return;
            }
            // Build the recognition intent
            mSpeechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                    speechContext != null ? speechContext.getPackageName() : "");
            mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            // Attach a listener to receive callbacks
            mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) { }

                @Override
                public void onBeginningOfSpeech() { }

                @Override
                public void onRmsChanged(float rmsdB) { }

                @Override
                public void onBufferReceived(byte[] buffer) { }

                @Override
                public void onEndOfSpeech() { }

                @Override
                public void onError(int error) {
                    mIsListening = false;
                    // Clean up any partial text left in the editor
                    if (mPartialVoiceLength > 0) {
                        android.view.inputmethod.InputConnection ic = mLatinIME.getCurrentInputConnection();
                        if (ic != null) ic.deleteSurroundingText(mPartialVoiceLength, 0);
                        mPartialVoiceLength = 0;
                    }
                    if (mMicToggleActive) {
                        boolean fatal = (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                                || error == SpeechRecognizer.ERROR_CLIENT);
                        if (!fatal) {
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (mMicToggleActive) startVoiceRecognition();
                            }, 300);
                            return;
                        }
                        mMicToggleActive = false;
                    }
                    if (smartAssistantBar != null) {
                        smartAssistantBar.stopVoiceAnimation();
                        String message;
                        switch (error) {
                            case SpeechRecognizer.ERROR_AUDIO:
                            case SpeechRecognizer.ERROR_CLIENT:
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            case SpeechRecognizer.ERROR_NETWORK:
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                                message = "Voice input unavailable";
                                break;
                            case SpeechRecognizer.ERROR_NO_MATCH:
                                message = "Didn't catch that";
                                break;
                            default:
                                message = "Recognition error";
                        }
                        smartAssistantBar.updateSuggestions(java.util.Collections.singletonList(message));
                        smartAssistantBar.setVoiceMode(false);
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    mIsListening = false;
                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String recognised = matches.get(0).trim();
                            if (!recognised.isEmpty()) {
                                // Remove partial text already typed, then commit final
                                if (mPartialVoiceLength > 0) {
                                    android.view.inputmethod.InputConnection ic = mLatinIME.getCurrentInputConnection();
                                    if (ic != null) ic.deleteSurroundingText(mPartialVoiceLength, 0);
                                    mPartialVoiceLength = 0;
                                }
                                if (mMicToggleActive) {
                                    commitVoiceInputToEditor(recognised);
                                    startVoiceRecognition();
                                    return;
                                }
                                onVoiceCommand(recognised);
                                return;
                            }
                        }
                    }
                    mPartialVoiceLength = 0;
                    if (mMicToggleActive) {
                        startVoiceRecognition();
                        return;
                    }
                    if (smartAssistantBar != null) {
                        smartAssistantBar.stopVoiceAnimation();
                        smartAssistantBar.updateSuggestions(java.util.Collections.singletonList("No speech detected"));
                        smartAssistantBar.setVoiceMode(false);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    if (!mMicToggleActive) return;
                    ArrayList<String> partials = partialResults != null
                            ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            : null;
                    if (partials == null || partials.isEmpty()) return;
                    String partial = partials.get(0);
                    if (partial == null || partial.isEmpty()) return;
                    android.view.inputmethod.InputConnection ic = mLatinIME.getCurrentInputConnection();
                    if (ic == null) return;
                    if (mPartialVoiceLength > 0) {
                        ic.deleteSurroundingText(mPartialVoiceLength, 0);
                    }
                    ic.commitText(partial, 1);
                    mPartialVoiceLength = partial.length();
                }

                @Override
                public void onEvent(int eventType, Bundle params) { }
            });
        }
        // Start listening if we are not already
        if (!mIsListening && mSpeechRecognizer != null) {
            mIsListening = true;
            mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
        }
    }

    /**
     * Stops the current voice recognition session if one is active.  This will
     * instruct the speech recogniser to stop listening and revert the
     * suggestion row out of voice mode.  It also clears any inâ€‘progress status
     * messages such as "Listening..." so that normal suggestions resume.
     */
    public void cancelVoiceRecognition() {
        mMicToggleActive = false;
        mPartialVoiceLength = 0;
        // Stop the recogniser gracefully
        if (mSpeechRecognizer != null) {
            try {
                mSpeechRecognizer.stopListening();
            } catch (Exception e) {
                // Ignore if recogniser is already inactive
            }
        }
        mIsListening = false;
        // Stop any pulsing animation on the microphone icons now that we are no longer
        // listening.  This ensures the icons return to their default size when
        // recognition is cancelled via the UI or programmatically.
        if (smartAssistantBar != null) {
            smartAssistantBar.stopVoiceAnimation();
        }
        if (smartAssistantBar != null) {
            smartAssistantBar.setVoiceMode(false);
            // Return to normal suggestions state
            smartAssistantBar.resetToTextSuggestions();
        }
    }

    /**
     * Processes the recognised voice command and dispatches it to the appropriate AI
     * action. Translation and grammar commands are treated as generic prompts and
     * submitted directly to ChatGPT. Other keywords such as tone, savage reply,
     * summarise, simplify and verify trigger the corresponding actions on the
     * recognised text. All other input, including phrases like "generate reply",
     * will be treated as a free-form prompt to the AI chat.
     *
     * @param command the raw recognised voice command
     */
    private void onVoiceCommand(String command) {
        if (command == null) return;
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return;
        // Always commit the transcribed speech into the current app's input view so the
        // user can see the recognised text. This is done before deciding whether to
        // call the AI. Both voice CTAs update the input view.
        commitVoiceInputToEditor(trimmed);
        // Determine whether this voice command should trigger an API call. If the
        // API flag is false, we return immediately after inserting the text so
        // that no AI request is made. The default dictionary suggestions will
        // handle next word predictions.
        if (!mVoiceApiCallFlag) {
            // If this voice session is not intended to call the AI, exit voice mode and
            // update the suggestion row to reflect the new input. Without this, the
            // suggestion row will remain stuck in "Listening..." mode and the user will not
            // see dictionary or clipboard suggestions until typing something manually.
            mVoiceApiCallFlag = false;
            if (smartAssistantBar != null) {
                smartAssistantBar.setVoiceMode(false);
                // Refresh suggestions for the committed text (including clipboard if empty).
                smartAssistantBar.resetToTextSuggestions();
                smartAssistantBar.onUserInput(getCommitedText());
            }
            return;
        }
        // Reset the flag so it does not affect subsequent calls
        mVoiceApiCallFlag = false;

        // Normalised version of the recognised command
        String normalized = trimmed.toLowerCase();

        // Translation and grammar-related phrases are handled generically
        if (normalized.contains("translate") || normalized.contains("grammar") || normalized.contains("correct")) {
            executeVoicePrompt(trimmed);
            return;
        }
        // Tone modifications: savage or generic tone
        if (normalized.contains("savage")) {
            performAiAction(AiAction.CHANGE_TONE, trimmed, "Savage");
            return;
        }
        if (normalized.contains("tone")) {
            performAiAction(AiAction.CHANGE_TONE, trimmed, null);
            return;
        }
        // Summary
        if (normalized.contains("summarise") || normalized.contains("summarize") || normalized.contains("summary")) {
            String summarisePrompt = "Summarise the following text:\n" + trimmed;
            performAiAction(AiAction.AI_CHAT, summarisePrompt);
            return;
        }
        // Simplify
        if (normalized.contains("simplify") || normalized.contains("simple")) {
            String simplifyPrompt = "Simplify the following text:\n" + trimmed;
            performAiAction(AiAction.AI_CHAT, simplifyPrompt);
            return;
        }
        // Verify facts
        if (normalized.contains("verify")) {
            String verifyPrompt = "Cross verify the facts in the following text and provide corrections if necessary:\n" + trimmed;
            performAiAction(AiAction.AI_CHAT, verifyPrompt);
            return;
        }
        // Compose an email or mail. If the user says "compose email" or "compose mail",
        // convert the voice command into a structured instruction asking ChatGPT to
        // draft a professional email based on the provided content. The original
        // command is passed along so any additional context or body text is included.
        if ((normalized.contains("compose") && normalized.contains("email")) ||
                (normalized.contains("compose") && normalized.contains("mail"))) {
            String emailPrompt = "Compose a professional email based on the following instruction:\n" + trimmed;
            performAiAction(AiAction.AI_CHAT, emailPrompt);
            return;
        }
        // For all other phrases (including "generate reply"), treat as free-form prompt
        executeVoicePrompt(trimmed);
    }

    /**
     * Writes the recognised voice text into the active input connection. This is used
     * to mirror spoken commands into the host application's text field. A space is
     * appended to separate the inserted text from subsequent user typing.
     *
     * @param text non-empty voice transcription to commit
     */
    private void commitVoiceInputToEditor(String text) {
        if (mLatinIME == null || text == null || text.isEmpty()) return;
        RichInputConnection connection = mLatinIME.getInputLogicInstance().mConnection;
        if (connection != null) {
            connection.commitText(text + " ", 1);
        }
    }

    /**
     * Sends a one-off prompt to ChatGPT and updates the suggestion row with the
     * returned response. The prompt is treated as the sole message in a new
     * conversation. Upon completion, the first choice from the API is displayed
     * as a single clickable suggestion. The last AI result and prompt are
     * recorded so that follow-up actions can operate on the returned content.
     *
     * @param prompt the free-form prompt to send to the AI
     */
    private void executeVoicePrompt(String prompt) {
        if (prompt == null) return;
        String trimmed = prompt.trim();
        if (trimmed.isEmpty()) return;
        // Build a minimal conversation history with one user message
        final List<JSONObject> conversationHistory = new ArrayList<>();
        try {
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", trimmed);
            conversationHistory.add(userMessage);
        } catch (JSONException e) {
            // If we can't build the JSON, abort the call
            return;
        }
        // Show a placeholder suggestion while loading. Enter voice mode so that this
        // placeholder is not overwritten by dictionary suggestions.
        if (smartAssistantBar != null) {
            smartAssistantBar.updateSuggestions(Collections.singletonList("..."));
            smartAssistantBar.setVoiceMode(true);
        }
        chatGPTApi.getChatGPTResponseForConversation(conversationHistory, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(mThemeContext, "Failed to get response", Toast.LENGTH_SHORT).show();
                    if (smartAssistantBar != null) {
                        smartAssistantBar.updateSuggestions(Collections.emptyList());
                        smartAssistantBar.setVoiceMode(false);
                    }
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(mThemeContext, "API call failed", Toast.LENGTH_SHORT).show();
                        if (smartAssistantBar != null) {
                            smartAssistantBar.updateSuggestions(Collections.emptyList());
                            smartAssistantBar.setVoiceMode(false);
                        }
                    });
                    return;
                }
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(mThemeContext, "Empty response", Toast.LENGTH_SHORT).show();
                        if (smartAssistantBar != null) {
                            smartAssistantBar.updateSuggestions(Collections.emptyList());
                            smartAssistantBar.setVoiceMode(false);
                        }
                    });
                    return;
                }
                String body = responseBody.string();
                String content = "";
                try {
                    JSONObject jsonObject = new JSONObject(body);
                    content = jsonObject.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim();
                } catch (JSONException e) {
                    // If parsing fails, fall back to raw response
                    content = body;
                }
                final String finalContent = content;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (smartAssistantBar != null) {
                        smartAssistantBar.showAiInlineResponse(trimmed, finalContent);
                        // Exit voice mode after displaying the result so regular suggestions can resume
                        smartAssistantBar.setVoiceMode(false);
                    }
                    // Persist the last AI result and prompt for follow-up actions
                    mLastAiResult = finalContent;
                    mLastPrompt = trimmed;
                    mLastCtaType = CtaType.REPLY_COPY;
                    recordAiUsage();
                });
            }
        });
    }

    public void performTranslateApiCall(String textToTranslate, String targetLanguage) {
        // Generate the specific prompt for translation
        String prompt = "Translate the following text to " + targetLanguage + ". " +
                "Respond with only the translated text, nothing else.\n\n" +
                "Text: \"" + textToTranslate + "\"";

        // Execute the API call. The response should have "Apply" and "Copy" buttons.
        executeGptApiWithChatUi(prompt, CtaType.APPLY_COPY);
    }

    public void onLanguageSelectedForTranslation(String textToTranslate, String targetLanguage) {
        FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(mLatinIME);
        User user = EncryptedPreferences.getUserLoggedInInfo();
        String userId = (user != null ? user.getId() : null);
        EventHelpers.triggerLanguageSelectedEvent(userId, targetLanguage, analytics);
        // This method will now orchestrate the UI changes.
        mUnifiedAiView.addItem(new project.witty.keys.keyboard.AiChat.SystemMessage("Translated to: " + targetLanguage));

        // 2. Add the Loading item in its place.
        mUnifiedAiView.addItem(Loading.INSTANCE);

        // 3. Now, call the existing method to perform the API call.
        performTranslateApiCall(textToTranslate, targetLanguage);
    }

    /**
     * This is the central handler for any click within a GridOptions view.
     * It's called from the GridOptionsViewHolder's callback.
     */
    public void onGridOptionSelected(CategoryOption option) {
        String action = option.getAction();
        Log.d("ScanFlow", "Step 2: User selected option with action: " + action);

        if (action.startsWith("show_level2_")) {
            // --- User chose a reply category, so we show the Level 2 options ---
            String level1Intent = action.substring("show_level2_".length());

            Map<String, String> level2Actions = ToneData.getLevel2Actions(level1Intent);
            if (level2Actions.isEmpty()) {
                Log.e("ScanFlow", "No Level 2 actions found for intent: " + level1Intent);
                return;
            }

            List<CategoryOption> level2Options = new ArrayList<>();
            for (Map.Entry<String, String> entry : level2Actions.entrySet()) {
                String title = entry.getValue() + " " + entry.getKey(); // e.g., "ðŸ‘ Agree"
                // The final action is now a clear instruction for the AI prompt
                String finalTask = "Generate a '" + entry.getKey() + "' reply based on the screen's context.";
                level2Options.add(new CategoryOption(R.drawable.continue_v2_icon, title, "task_" + finalTask));
            }

            Log.d("ScanFlow", "Step 2.1: Showing Level 2 options.");
            // Start a new session: remove the previous category UI and prompt the user to choose an option.
            Context ctx = mThemeContext;
            List<project.witty.keys.keyboard.AiChat.ChatItem> session = new ArrayList<>();
            session.add(new GridOptions(ctx.getString(R.string.choose_an_intent), level2Options));
            mUnifiedAiView.startNewSession(session);

        } else if (action.startsWith("task_")) {
            // --- User chose a final task. Time to capture the screen. ---
            String finalTask = action.substring("task_".length());
            Log.d("ScanFlow", "Step 2.2: Final task chosen: '" + finalTask + "'. Starting capture process.");

            // 1. Show a loading screen in the UI
            mUnifiedAiView.showLoadingAndClear();

            // 2. Set the user's intended action so the service can access it
            setCurrentUserAction(finalTask);

            // 3. Start the capture process (which now just launches the permission activity)
            startScreenCapture();
        }
    }

    private void setNextResponseHeader(String title) {
        mPendingHeaderText = title;
    }
    private void maybeAddPendingHeader() {
        if (mPendingHeaderText != null) {
            if (mUnifiedAiView != null) {
                mUnifiedAiView.addItem(new project.witty.keys.keyboard.AiChat.SystemMessage(mPendingHeaderText));
            }
            mPendingHeaderText = null;
        }
    }
    // KeyboardSwitcher.java
    private void resizeProductPanelsTo(int targetHeight) {
        final View[] productViews = { mUnifiedAiView, emojiKeyboard };
        for (View v : productViews) {
            if (v == null) continue;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.height != targetHeight) {
                lp.height = targetHeight;
                v.setLayoutParams(lp);
            }
        }
    }


    public void openChatWithSeedConversation(String userPrompt, String assistantMessage) {
        if (mUnifiedAiView == null) return;
        showMainViewComponent(mUnifiedAiView);
        mInAiChatConversation = true;
        mUnifiedAiView.setViewTitle("AI Chat", AIFeatureType.AI_CHAT_WRITTEN);

        java.util.List<project.witty.keys.keyboard.AiChat.ChatItem> items = new java.util.ArrayList<>();
        if (userPrompt != null && !userPrompt.trim().isEmpty()) {
            items.add(new project.witty.keys.keyboard.AiChat.UserMessage(userPrompt));
        }
        items.add(new project.witty.keys.keyboard.AiChat.AiMessage(
                assistantMessage, project.witty.keys.keyboard.AiChat.CtaType.REPLY_COPY));

        mUnifiedAiView.startNewSession(items);

        // seed last-result state so suggestions/regenerate work
        mLastAiResult = assistantMessage;
        mLastPrompt = (userPrompt == null) ? "" : userPrompt;
        mLastCtaType = project.witty.keys.keyboard.AiChat.CtaType.REPLY_COPY;
    }

    public void openChatWithSeedAssistantMessage(String assistantMessage) {
        openChatWithSeedConversation("", assistantMessage);
    }


    /** Record one AI action usage via DailyUsageTracker. */
    private void recordAiUsage() {
        DailyUsageTracker.getInstance(mLatinIME).recordUsage();
    }



}