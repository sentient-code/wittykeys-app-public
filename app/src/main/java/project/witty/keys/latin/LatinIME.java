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

package project.witty.keys.latin;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.giphy.sdk.ui.Giphy;
import com.google.firebase.Firebase;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.giphy.sdk.core.models.Media;
import java.io.FileOutputStream;
import java.io.InputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import project.witty.keys.BuildConfig;
import project.witty.keys.R;
import project.witty.keys.app.entities.Subscription;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.CommunicationService;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.BatteryOptimizationHelper;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.ActionTracker;
import project.witty.keys.app.helpers.ActivationManager;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.utils.DailyUsageTracker;
import project.witty.keys.app.helpers.ScreenReaderAccessibility;
import project.witty.keys.app.context.Chat;
import project.witty.keys.app.context.ChatMessage;
import project.witty.keys.app.context.ContextEngine;
import project.witty.keys.app.context.AppDetector;
import project.witty.keys.app.context.ReplyGenerator;
import project.witty.keys.app.context.ScreenContext;
import project.witty.keys.compat.EditorInfoCompatUtils;
import project.witty.keys.compat.PreferenceManagerCompat;
import project.witty.keys.compat.ViewOutlineProviderCompatUtils;
import project.witty.keys.compat.ViewOutlineProviderCompatUtils.InsetsUpdater;
import project.witty.keys.event.Event;
import project.witty.keys.event.InputTransaction;
import project.witty.keys.keyboard.AssistantViews.SmartAssistantBar;
import project.witty.keys.keyboard.AssistantViews.SmartAssistantLogger;
import project.witty.keys.app.context.ConversationMatcher;
import project.witty.keys.keyboard.AssistantViews.SuggestionRow;
import project.witty.keys.keyboard.Keyboard;
import project.witty.keys.keyboard.KeyboardActionListener;
import project.witty.keys.keyboard.KeyboardId;
import project.witty.keys.keyboard.KeyboardSwitcher;
import project.witty.keys.keyboard.MainKeyboardView;
import project.witty.keys.keyboard.internal.InternalInputTarget;
import project.witty.keys.latin.common.Constants;
import project.witty.keys.latin.define.DebugFlags;
import project.witty.keys.latin.inputlogic.InputLogic;
import project.witty.keys.latin.settings.Settings;
import project.witty.keys.latin.settings.SettingsActivity;
import project.witty.keys.latin.settings.SettingsValues;
import project.witty.keys.latin.utils.ApplicationUtils;
import project.witty.keys.latin.utils.LeakGuardHandlerWrapper;
import project.witty.keys.latin.utils.ResourceUtils;
import project.witty.keys.latin.utils.ViewLayoutUtils;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService implements KeyboardActionListener,
        RichInputMethodManager.SubtypeChangedListener {
    static final String TAG = LatinIME.class.getSimpleName();
    private static final boolean TRACE = false;
    private static final int EXTENDED_TOUCHABLE_REGION_HEIGHT = 100;
    private static final int PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT = 2;
    private static final int PENDING_IMS_CALLBACK_DURATION_MILLIS = 800;
    static final long DELAY_DEALLOCATE_MEMORY_MILLIS = TimeUnit.SECONDS.toMillis(10);
    final Settings mSettings;
    private Locale mLocale;
    private int mOriginalNavBarColor = 0;
    private int mOriginalNavBarFlags = 0;
    final InputLogic mInputLogic = new InputLogic(this /* LatinIME */);
    // TODO: Move these {@link View}s to {@link KeyboardSwitcher}.
    private View mInputView;
    private InsetsUpdater mInsetsUpdater;
    private RichInputMethodManager mRichImm;
    final KeyboardSwitcher mKeyboardSwitcher;
    private AlertDialog mOptionsDialog;
    public final UIHandler mHandler = new UIHandler(this);
    private Handler mMainThreadHandler;
    private ActivationManager activationManager;
    private static final String KEY_FIRST_MESSAGE_ACTIVATION_TRACKED = "first_message_activation_tracked";

    // Debug-only SAB controller for visual testing (registered via reflection)
    private BroadcastReceiver mDebugSABController;

    // Internal input target — when active, key events route here instead of host app
    private InternalInputTarget mInternalInputTarget;

    // Keyboard session tracking for true DAU measurement
    private String mCurrentSessionId = null;
    private long mSessionStartTime = 0;
    private int mCharactersTypedInSession = 0;

    public static final class UIHandler extends LeakGuardHandlerWrapper<LatinIME> {
        private static final int MSG_UPDATE_SHIFT_STATE = 0;
        private static final int MSG_PENDING_IMS_CALLBACK = 1;
        private static final int MSG_RESET_CACHES = 7;
        private static final int MSG_WAIT_FOR_DICTIONARY_LOAD = 8;
        private static final int MSG_DEALLOCATE_MEMORY = 9;

        private static final int ARG1_TRUE = 1;

        private int mDelayInMillisecondsToUpdateShiftState;

        public UIHandler(final LatinIME ownerInstance) {
            super(ownerInstance);
        }

        public void onCreate() {
            final LatinIME latinIme = getOwnerInstance();
            if (latinIme == null) {
                return;
            }
            final Resources res = latinIme.getResources();
            mDelayInMillisecondsToUpdateShiftState = res.getInteger(
                    R.integer.config_delay_in_milliseconds_to_update_shift_state);
        }

        @Override
        public void handleMessage(final Message msg) {
            final LatinIME latinIme = getOwnerInstance();
            if (latinIme == null) {
                return;
            }
            final KeyboardSwitcher switcher = latinIme.mKeyboardSwitcher;
            switch (msg.what) {
                case MSG_UPDATE_SHIFT_STATE:
                    switcher.requestUpdatingShiftState(latinIme.getCurrentAutoCapsState(),
                            latinIme.getCurrentRecapitalizeState());
                    break;
                case MSG_RESET_CACHES:
                    final SettingsValues settingsValues = latinIme.mSettings.getCurrent();
                    if (latinIme.mInputLogic.retryResetCachesAndReturnSuccess(
                            msg.arg1 == ARG1_TRUE /* tryResumeSuggestions */,
                            msg.arg2 /* remainingTries */, this /* handler */)) {
                        // If we were able to reset the caches, then we can reload the keyboard.
                        // Otherwise, we'll do it when we can.
                        latinIme.mKeyboardSwitcher.loadKeyboard(latinIme.getCurrentInputEditorInfo(),
                                settingsValues, latinIme.getCurrentAutoCapsState(),
                                latinIme.getCurrentRecapitalizeState());
                    }
                    break;
                case MSG_WAIT_FOR_DICTIONARY_LOAD:
                    Log.i(TAG, "Timeout waiting for dictionary load");
                    break;
                case MSG_DEALLOCATE_MEMORY:
                    latinIme.deallocateMemory();
                    break;
            }
        }

        public void postResetCaches(final boolean tryResumeSuggestions, final int remainingTries) {
            removeMessages(MSG_RESET_CACHES);
            sendMessage(obtainMessage(MSG_RESET_CACHES, tryResumeSuggestions ? 1 : 0,
                    remainingTries, null));
        }

        public void postUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SHIFT_STATE),
                    mDelayInMillisecondsToUpdateShiftState);
        }

        public void postDeallocateMemory() {
            sendMessageDelayed(obtainMessage(MSG_DEALLOCATE_MEMORY),
                    DELAY_DEALLOCATE_MEMORY_MILLIS);
        }

        public void cancelDeallocateMemory() {
            removeMessages(MSG_DEALLOCATE_MEMORY);
        }

        public boolean hasPendingDeallocateMemory() {
            return hasMessages(MSG_DEALLOCATE_MEMORY);
        }

        // Working variables for the following methods.
        private boolean mIsOrientationChanging;
        private boolean mPendingSuccessiveImsCallback;
        private boolean mHasPendingStartInput;
        private boolean mHasPendingFinishInputView;
        private boolean mHasPendingFinishInput;
        private EditorInfo mAppliedEditorInfo;

        private void resetPendingImsCallback() {
            mHasPendingFinishInputView = false;
            mHasPendingFinishInput = false;
            mHasPendingStartInput = false;
        }

        private void executePendingImsCallback(final LatinIME latinIme, final EditorInfo editorInfo,
                                               boolean restarting) {
            if (mHasPendingFinishInputView) {
                latinIme.onFinishInputViewInternal(mHasPendingFinishInput);
            }
            if (mHasPendingFinishInput) {
                latinIme.onFinishInputInternal();
            }
            if (mHasPendingStartInput) {
                latinIme.onStartInputInternal(editorInfo, restarting);
            }
            resetPendingImsCallback();
        }

        public void onStartInput(final EditorInfo editorInfo, final boolean restarting) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the second onStartInput after orientation changed.
                mHasPendingStartInput = true;
            } else {
                if (mIsOrientationChanging && restarting) {
                    // This is the first onStartInput after orientation changed.
                    mIsOrientationChanging = false;
                    mPendingSuccessiveImsCallback = true;
                }
                final LatinIME latinIme = getOwnerInstance();
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting);
                    latinIme.onStartInputInternal(editorInfo, restarting);
                }
            }
        }

        public void onStartInputView(final EditorInfo editorInfo, final boolean restarting) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)
                    && KeyboardId.equivalentEditorInfoForKeyboard(editorInfo, mAppliedEditorInfo)) {
                // Typically this is the second onStartInputView after orientation changed.
                resetPendingImsCallback();
            } else {
                if (mPendingSuccessiveImsCallback) {
                    // This is the first onStartInputView after orientation changed.
                    mPendingSuccessiveImsCallback = false;
                    resetPendingImsCallback();
                    sendMessageDelayed(obtainMessage(MSG_PENDING_IMS_CALLBACK),
                            PENDING_IMS_CALLBACK_DURATION_MILLIS);
                }
                final LatinIME latinIme = getOwnerInstance();
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting);
                    latinIme.onStartInputViewInternal(editorInfo, restarting);
                    mAppliedEditorInfo = editorInfo;
                }
                cancelDeallocateMemory();
            }
        }


        public void onFinishInputView(final boolean finishingInput) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInputView after orientation changed.
                mHasPendingFinishInputView = true;
            } else {
                final LatinIME latinIme = getOwnerInstance();
                if (latinIme != null) {
                    latinIme.onFinishInputViewInternal(finishingInput);
                    mAppliedEditorInfo = null;
                }
                if (!hasPendingDeallocateMemory()) {
                    postDeallocateMemory();
                }
            }
        }

        public void onFinishInput() {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInput after orientation changed.
                mHasPendingFinishInput = true;
            } else {
                final LatinIME latinIme = getOwnerInstance();
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, null, false);
                    latinIme.onFinishInputInternal();
                }
            }
        }
    }
    private CommunicationService communicationService;
    private boolean communicationServiceBound = false;
    private ServiceConnection communicationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CommunicationService.LocalBinder binder = (CommunicationService.LocalBinder) service;
            communicationService = binder.getService();
            communicationService.setLatinIME(LatinIME.this);
            communicationServiceBound = true;
            Log.d("WK_AI_DEBUG", "[SERVICE] CommunicationService CONNECTED - bound=true");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            communicationServiceBound = false;
            communicationService = null;
            Log.w("WK_AI_DEBUG", "[SERVICE] CommunicationService DISCONNECTED - bound=false");
        }
    };
    private Boolean notHideWindow = false;
    private String mCurrentInputEditorPackageName;
    private ClipboardManager mClipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener mClipChangedListener;
    private long mProtectionStartTime = 0;
    private static final long MAX_PROTECTION_DURATION_MS = 15000;

    // Build 6.3: Proactive context reading
    private ReplyGenerator mReplyGenerator;

    // Build 7.0 P6: Battery optimization health check
    private int keyboardOpenCount = 0;

    // 2. Declare a BroadcastReceiver as a member variable
    public LatinIME() {
        super();
        mSettings = Settings.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
    }

    @Override
    public void onCreate() {
        Settings.init(this);
        DebugFlags.init(PreferenceManagerCompat.getDeviceSharedPreferences(this));
        RichInputMethodManager.init(this);
        mRichImm = RichInputMethodManager.getInstance();
        mRichImm.setSubtypeChangeHandler(this);
        KeyboardSwitcher.init(this);
        AudioAndHapticFeedbackManager.init(this);
        super.onCreate();
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        mHandler.onCreate();
        Giphy.INSTANCE.configure(this, BuildConfig.GIPHY_API_KEY, true);
        // TODO: Resolve mutual dependencies of {@link #loadSettings()} and
        // {@link #resetDictionaryFacilitatorIfNecessary()}.
        loadSettings();

        // Register to receive ringer mode change.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mRingerModeChangeReceiver, filter);
        Intent intent = new Intent(this, CommunicationService.class);
        bindService(intent, communicationServiceConnection, Context.BIND_AUTO_CREATE);

        // Build 6.3: Initialize ReplyGenerator for proactive context
        mReplyGenerator = new ReplyGenerator();

        // Get the ClipboardManager service once and store it.
        mClipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        // Define the listener's behavior. We'll reuse this definition.
        mClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                // This method is called automatically by the system when the clipboard changes.
                // We simply call our existing checkClipboard logic.
                checkClipboard();
            }
        };
        FirebaseApp.initializeApp(this);
        EncryptedPreferences.initialize(this);
        DebugConfig.init(this);
        DebugConfig.enableDebugMode(this);

        // Initialize ActivationManager for tracking user milestones
        activationManager = new ActivationManager(this);

        // DEBUG: Log IME service creation and keyboard enabled state
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "=== KEYBOARD_ENABLE_DEBUG: LatinIME.onCreate() ===");
            Log.d(TAG, "   Timestamp: " + System.currentTimeMillis());
            Log.d(TAG, "   ActivationManager initialized: " + (activationManager != null));

            // Log current user and tracking state
            project.witty.keys.app.entities.User user = EncryptedPreferences.getUserLoggedInInfo();
            Log.d(TAG, "   User Logged In: " + (user != null));
            Log.d(TAG, "   user_present: " + (user != null && user.getId() != null && !user.getId().isEmpty()));

            // Log milestone state
            boolean keyboardMilestoneTracked = EncryptedPreferences.getBoolean("milestone_keyboard_enabled", false);
            Log.d(TAG, "   Keyboard Enabled Milestone Already Tracked: " + keyboardMilestoneTracked);

            // Log tracking ID
            String trackingId = activationManager.getTrackingId();
            Log.d(TAG, "   tracking_id_present: " + (trackingId != null && !trackingId.isEmpty()));

            Log.d(TAG, "   LatinIME onCreate COMPLETE - Keyboard service is running");
        }

        // Register DebugSABController for visual testing (debug builds only)
        if (BuildConfig.DEBUG) {
            try {
                Class<?> controllerClass = Class.forName("project.witty.keys.debug.DebugSABController");
                java.lang.reflect.Constructor<?> constructor = controllerClass.getConstructor(KeyboardSwitcher.class);
                mDebugSABController = (BroadcastReceiver) constructor.newInstance(mKeyboardSwitcher);

                IntentFilter debugFilter = new IntentFilter();
                debugFilter.addAction("project.witty.keys.debug.SHOW_LOADING_SHIMMER");
                debugFilter.addAction("project.witty.keys.debug.INJECT_SCENARIO");
                debugFilter.addAction("project.witty.keys.debug.SHOW_ERROR");
                // OriginalView states (G05-G11)
                debugFilter.addAction("project.witty.keys.debug.SHOW_SMART_REPLIES");
                debugFilter.addAction("project.witty.keys.debug.COLLAPSE_VIEW");
                debugFilter.addAction("project.witty.keys.debug.ENTER_CUSTOM_MODE");
                debugFilter.addAction("project.witty.keys.debug.SHOW_NO_CONTEXT");
                debugFilter.addAction("project.witty.keys.debug.SHOW_ACCESSIBILITY_PROMPT");
                debugFilter.addAction("project.witty.keys.debug.SHOW_ROW2_SHIMMER");
                debugFilter.addAction("project.witty.keys.debug.SHOW_OV_ERROR");
                debugFilter.addAction("project.witty.keys.debug.SHOW_ORIGINAL_VIEW");
                // Contact Picker (G31)
                debugFilter.addAction("project.witty.keys.debug.SHOW_CONTACT_PICKER");
                // Screen Capture CTA (G32)
                debugFilter.addAction("project.witty.keys.debug.SHOW_CAPTURE_CTA");
                // CTA interactions (G12-G20)
                debugFilter.addAction("project.witty.keys.debug.SETUP_TONE_PICKER");
                debugFilter.addAction("project.witty.keys.debug.SHOW_TONE_LOADING");
                debugFilter.addAction("project.witty.keys.debug.ACTIVATE_TONE");
                debugFilter.addAction("project.witty.keys.debug.SETUP_LANG_PICKER");
                debugFilter.addAction("project.witty.keys.debug.SHOW_TRANSLATE_LOADING");
                debugFilter.addAction("project.witty.keys.debug.SHOW_TRANSLATE_ACTIVE");
                debugFilter.addAction("project.witty.keys.debug.SETUP_GRAMMAR_CTA");
                // Special states (G26-G28)
                debugFilter.addAction("project.witty.keys.debug.SHOW_MILESTONE_TOAST");
                debugFilter.addAction("project.witty.keys.debug.START_BRAIN_BLINK");
                // Bottom sheets (G29-G30)
                debugFilter.addAction("project.witty.keys.debug.SHOW_BOTTOM_SHEET");
                debugFilter.addAction("project.witty.keys.debug.SHOW_CONSENT_SHEET");
                // Utility
                debugFilter.addAction("project.witty.keys.debug.CANCEL_ANIMATIONS");
                // AI Quality Scoring (Sprint 4)
                debugFilter.addAction("project.witty.keys.debug.TRIGGER_AI_REPLIES");
                // Emoji Keyboard Golden States (EK01-EK13)
                debugFilter.addAction("project.witty.keys.debug.SHOW_EMOJI_KEYBOARD");
                debugFilter.addAction("project.witty.keys.debug.EMOJI_SELECT_CATEGORY");
                debugFilter.addAction("project.witty.keys.debug.EMOJI_CLEAR_RECENTS");
                debugFilter.addAction("project.witty.keys.debug.EMOJI_ACTIVATE_SEARCH");
                debugFilter.addAction("project.witty.keys.debug.EMOJI_SEARCH_TEXT");
                debugFilter.addAction("project.witty.keys.debug.EMOJI_DEACTIVATE_SEARCH");
                debugFilter.addAction("project.witty.keys.debug.EMOJI_SWITCH_GIF");
                debugFilter.addAction("project.witty.keys.debug.EMOJI_SWITCH_EMOJI");
                debugFilter.addAction("project.witty.keys.debug.EMOJI_SHOW_SKIN_TONE");
                debugFilter.addAction("project.witty.keys.debug.CLOSE_EMOJI_KEYBOARD");
                debugFilter.addAction("project.witty.keys.debug.EMOJI_SET_RECENTS");
                debugFilter.addAction("project.witty.keys.debug.RESET_EMOJI_STATE");
                debugFilter.addAction("project.witty.keys.debug.SET_DARK_THEME");
                debugFilter.addAction("project.witty.keys.debug.SET_LIGHT_THEME");
                // AI Chat Golden States (AC01-AC09)
                debugFilter.addAction("project.witty.keys.debug.OPEN_AI_CHAT");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_ADD_USER_MSG");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_ADD_AI_MSG");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SHOW_LOADING");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SHOW_ERROR");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_ADD_SYSTEM_MSG");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_ADD_METADATA_CARD");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SET_CONTEXT");
                debugFilter.addAction("project.witty.keys.debug.SET_AI_CHAT_UI_STATE");
                debugFilter.addAction("project.witty.keys.debug.SET_AI_CHAT_INPUT_TEXT");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SCROLL_TO_TOP");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_CLEAR");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SET_REPLY_BAR");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SHOW_CAPTURE");
                debugFilter.addAction("project.witty.keys.debug.SHOW_REPLY_MODE");
                // E2E Lifecycle Test Support
                debugFilter.addAction("project.witty.keys.debug.QUERY_STATE");
                debugFilter.addAction("project.witty.keys.debug.RESET_STATE_HISTORY");
                debugFilter.addAction("project.witty.keys.debug.TAP_REPLY");
                debugFilter.addAction("project.witty.keys.debug.TRIGGER_PIPELINE");
                debugFilter.addAction("project.witty.keys.debug.SEND_AI_CHAT_MESSAGE");
                // AI Chat P4 States (AC11-AC13)
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SHOW_SCREENSHOT");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SHOW_NLS_CONTEXT");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SHOW_SESSION_RESUMED");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_ADD_SCREENSHOT_MSG");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_ADD_NLS_BANNER");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_ADD_SESSION_BANNER");

                // P5: AC14 + FS01-FS10 golden states
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SHOW_CAPTURE_ANALYZING");
                debugFilter.addAction("project.witty.keys.debug.AI_CHAT_SHOW_ANALYZING");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_EMPTY");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_CHAT");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_LOADING");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_ERROR");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_SCREENSHOT");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_NLS_CONTEXT");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_SESSION_LIST");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_SESSION_RESUMED");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_LONG_CHAT");
                debugFilter.addAction("project.witty.keys.debug.FS_SHOW_CAPTURE_ANALYZING");

                // K_ keyboard v2 golden states (K01-K08)
                debugFilter.addAction("project.witty.keys.debug.K_NEW_CHAT");
                debugFilter.addAction("project.witty.keys.debug.K_REPLY_MODE");
                debugFilter.addAction("project.witty.keys.debug.K_SESSIONS_EMPTY");
                debugFilter.addAction("project.witty.keys.debug.K_SESSIONS_POPULATED");
                debugFilter.addAction("project.witty.keys.debug.K_AI_VIEW_EMPTY");
                debugFilter.addAction("project.witty.keys.debug.K_AI_VIEW_POPULATED");
                debugFilter.addAction("project.witty.keys.debug.K_AI_VIEW_LOADING");
                debugFilter.addAction("project.witty.keys.debug.K_AI_VIEW_SCREENSHOT");

                // O_ overlay v2 golden states (O01-O07)
                debugFilter.addAction("project.witty.keys.debug.O_BUBBLE_IDLE");
                debugFilter.addAction("project.witty.keys.debug.O_POPUP_EMPTY");
                debugFilter.addAction("project.witty.keys.debug.O_POPUP_POPULATED");
                debugFilter.addAction("project.witty.keys.debug.O_POPUP_LOADING");
                debugFilter.addAction("project.witty.keys.debug.O_POPUP_CHAT_EMPTY");
                debugFilter.addAction("project.witty.keys.debug.O_POPUP_CHAT_LOADING");
                debugFilter.addAction("project.witty.keys.debug.O_POPUP_CHAT_POPULATED");

                // FV2_ fullscreen v2 golden states (F01-F06)
                debugFilter.addAction("project.witty.keys.debug.FV2_INITIAL_LOAD");
                debugFilter.addAction("project.witty.keys.debug.FV2_SESSIONS_EMPTY");
                debugFilter.addAction("project.witty.keys.debug.FV2_SESSIONS_POPULATED");
                debugFilter.addAction("project.witty.keys.debug.FV2_CHAT_EMPTY");
                debugFilter.addAction("project.witty.keys.debug.FV2_CHAT_POPULATED");
                debugFilter.addAction("project.witty.keys.debug.FV2_CHAT_ERROR");

                registerReceiver(mDebugSABController, debugFilter, Context.RECEIVER_EXPORTED);
                Log.d(TAG, "[DEBUG] DebugSABController registered for visual test control");
            } catch (Exception e) {
                Log.e(TAG, "[DEBUG] Failed to register DebugSABController: " + e.getMessage());
            }
        }
    }

    private void loadSettings() {
        mLocale = mRichImm.getCurrentSubtype().getLocaleObject();
        final EditorInfo editorInfo = getCurrentInputEditorInfo();
        final InputAttributes inputAttributes = new InputAttributes(editorInfo, isFullscreenMode());
        mSettings.loadSettings(inputAttributes);
        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        AudioAndHapticFeedbackManager.getInstance().onSettingsChanged(currentSettingsValues);
    }

    @Override
    public void onDestroy() {
        mSettings.onDestroy();
        unregisterReceiver(mRingerModeChangeReceiver);

        // Unregister debug SAB controller if registered
        if (mDebugSABController != null) {
            try {
                unregisterReceiver(mDebugSABController);
                Log.d(TAG, "[DEBUG] DebugSABController unregistered");
            } catch (Exception e) {
                Log.e(TAG, "[DEBUG] Failed to unregister DebugSABController: " + e.getMessage());
            }
            mDebugSABController = null;
        }

        super.onDestroy();
        if (communicationServiceBound) {
            unbindService(communicationServiceConnection);
            communicationServiceBound = false;
            communicationService = null;
        }
        Log.d("LatinIME", "onDestroy");
    }

    private boolean isImeSuppressedByHardwareKeyboard() {
        final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        return !onEvaluateInputViewShown() && switcher.isImeSuppressedByHardwareKeyboard(
                mSettings.getCurrent(), switcher.getKeyboardSwitchState());
    }

    @Override
    public void onConfigurationChanged(final Configuration conf) {
        SettingsValues settingsValues = mSettings.getCurrent();
        if (settingsValues.mHasHardwareKeyboard != Settings.readHasHardwareKeyboard(conf)) {
            // If the state of having a hardware keyboard changed, then we want to reload the
            // settings to adjust for that.
            // TODO: we should probably do this unconditionally here, rather than only when we
            // have a change in hardware keyboard configuration.
            loadSettings();
        }
        mKeyboardSwitcher.updateKeyboardTheme(conf.uiMode);
        super.onConfigurationChanged(conf);
        // Refresh navigation bar color when night mode changes
        if (isInputViewShown()) {
            setNavigationBarColor();
        }
    }

    @Override
    public View onCreateInputView() {
        return mKeyboardSwitcher.onCreateInputView(getResources().getConfiguration().uiMode);
    }

    @Override
    public void setInputView(final View view) {
        super.setInputView(view);
        mInputView = view;
        mInsetsUpdater = ViewOutlineProviderCompatUtils.setInsetsOutlineProvider(view);
        updateSoftInputWindowLayoutParameters();
    }

    @Override
    public void setCandidatesView(final View view) {
        // To ensure that CandidatesView will never be set.
    }

    @Override
    public void onStartInput(final EditorInfo editorInfo, final boolean restarting) {
        mHandler.onStartInput(editorInfo, restarting);
    }

    @Override
    public void onStartInputView(final EditorInfo editorInfo, final boolean restarting) {
        // DEBUG: Log keyboard visibility - confirms keyboard is being used
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "=== KEYBOARD_ENABLE_DEBUG: LatinIME.onStartInputView() ===");
            Log.d(TAG, "   Timestamp: " + System.currentTimeMillis());
            Log.d(TAG, "   Restarting: " + restarting);
            Log.d(TAG, "   Editor Package: " + (editorInfo != null ? editorInfo.packageName : "null"));
            Log.d(TAG, "   Editor Class: " + (editorInfo != null ? editorInfo.fieldName : "null"));

            // Log user state to detect if events might be missed
            project.witty.keys.app.entities.User user = EncryptedPreferences.getUserLoggedInInfo();
            Log.d(TAG, "   User Logged In: " + (user != null));
            if (user == null) {
                Log.w(TAG, "   NOTE: User is null - some keyboard events may not be tracked");
            }
        }

        checkClipboard();
        // 2. START LISTENING: Register the listener to detect future copies.
        if (mClipboardManager != null) {
            mClipboardManager.addPrimaryClipChangedListener(mClipChangedListener);
        }
        mHandler.onStartInputView(editorInfo, restarting);
        // ✅ NEW: Track keyboard session start for true DAU metrics
        trackKeyboardSessionStart();

        // Battery optimization check (every 50th keyboard open to avoid spam)
        if (++keyboardOpenCount % 50 == 0) {
            if (BatteryOptimizationHelper.isLikelyBeingKilled(this)) {
                showBatteryOptimizationNotification();
            }
        }
    }

    private void showBatteryOptimizationNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String channelId = "wk_service_health";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                "Service Health", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }

        Intent intent = BatteryOptimizationHelper.getBatterySettingsIntent(this);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification notification = new NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.settings_icon)
            .setContentTitle("WittyKeys needs your help")
            .setContentText("Smart replies stopped working. Tap to fix battery settings.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();

        nm.notify(200, notification);
    }

    @Override
    public void onFinishInputView(final boolean finishingInput) {
        // Deactivate internal input target on keyboard hide
        if (mInternalInputTarget != null && mInternalInputTarget.isActive()) {
            mInternalInputTarget.deactivate();
        }
        // Also deactivate emoji search if active
        final InternalInputTarget emojiTarget =
                mKeyboardSwitcher.getActiveInternalInputTarget();
        if (emojiTarget != null) {
            emojiTarget.deactivate();
        }
        mRichImm.resetSubtypeCycleOrder();
        mHandler.onFinishInputView(finishingInput);
        if (mClipboardManager != null) {
            mClipboardManager.removePrimaryClipChangedListener(mClipChangedListener);
        }
        // ✅ NEW: Track keyboard session end with duration and usage metrics
        trackKeyboardSessionEnd();
    }

    @Override
    public void onFinishInput() {
        mHandler.onFinishInput();
    }

    @Override
    public void onCurrentSubtypeChanged() {
        mInputLogic.onSubtypeChanged();
        loadKeyboard();
    }

    void onStartInputInternal(final EditorInfo editorInfo, final boolean restarting) {
        super.onStartInput(editorInfo, restarting);
        // If the primary hint language does not match the current subtype language, then try
        // to switch to the primary hint language.
        // TODO: Support all the locales in EditorInfo#hintLocales.
        final Locale primaryHintLocale = EditorInfoCompatUtils.getPrimaryHintLocale(editorInfo);
        if (primaryHintLocale == null) {
            return;
        }
        mRichImm.setCurrentSubtype(primaryHintLocale);
    }

    void onStartInputViewInternal(final EditorInfo editorInfo, final boolean restarting) {
        Log.e("WK_PHASE2_DEBUG", ">>> onStartInputViewInternal CALLED <<< restarting=" + restarting);
        super.onStartInputView(editorInfo, restarting);

        // Switch to the null consumer to handle cases leading to early exit below, for which we
        // also wouldn't be consuming gesture data.
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        switcher.updateKeyboardTheme(getResources().getConfiguration().uiMode);
        final MainKeyboardView mainKeyboardView = switcher.getMainKeyboardView();
        // If we are starting input in a different text field from before, we'll have to reload
        // settings, so currentSettingsValues can't be final.
        SettingsValues currentSettingsValues = mSettings.getCurrent();

        if (editorInfo == null) {
            Log.e(TAG, "Null EditorInfo in onStartInputView()");
            if (DebugFlags.DEBUG_ENABLED) {
                throw new NullPointerException("Null EditorInfo in onStartInputView()");
            }
            return;
        }
        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "onStartInputView: editorInfo:"
                    + String.format("inputType=0x%08x imeOptions=0x%08x",
                    editorInfo.inputType, editorInfo.imeOptions));
            Log.d(TAG, "All caps = "
                    + ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0)
                    + ", sentence caps = "
                    + ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0)
                    + ", word caps = "
                    + ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0));
        }
        Log.i(TAG, "Starting input. Cursor position = "
                + editorInfo.initialSelStart + "," + editorInfo.initialSelEnd);

        // In landscape mode, this method gets called without the input view being created.
        if (mainKeyboardView == null) {
            return;
        }

        final boolean inputTypeChanged = !currentSettingsValues.isSameInputType(editorInfo);
        final boolean isDifferentTextField = !restarting || inputTypeChanged;

        // The EditorInfo might have a flag that affects fullscreen mode.
        // Note: This call should be done by InputMethodService?
        updateFullscreenMode();

        // ALERT: settings have not been reloaded and there is a chance they may be stale.
        // In the practice, if it is, we should have gotten onConfigurationChanged so it should
        // be fine, but this is horribly confusing and must be fixed AS SOON AS POSSIBLE.

        // In some cases the input connection has not been reset yet and we can't access it. In
        // this case we will need to call loadKeyboard() later, when it's accessible, so that we
        // can go into the correct mode, so we need to do some housekeeping here.
        final boolean needToCallLoadKeyboardLater;
        if (!isImeSuppressedByHardwareKeyboard()) {
            // The app calling setText() has the effect of clearing the composing
            // span, so we should reset our state unconditionally, even if restarting is true.
            // We also tell the input logic about the combining rules for the current subtype, so
            // it can adjust its combiners if needed.
            mInputLogic.startInput();

            // TODO[IL]: Can the following be moved to InputLogic#startInput?
            if (!mInputLogic.mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                    editorInfo.initialSelStart, editorInfo.initialSelEnd)) {
                // Sometimes, while rotating, for some reason the framework tells the app we are not
                // connected to it and that means we can't refresh the cache. In this case, schedule
                // a refresh later.
                // We try resetting the caches up to 5 times before giving up.
                mHandler.postResetCaches(isDifferentTextField, 5 /* remainingTries */);
                // mLastSelection{Start,End} are reset later in this method, no need to do it here
                needToCallLoadKeyboardLater = true;
            } else {
                needToCallLoadKeyboardLater = false;
            }
        } else {
            // If we have a hardware keyboard we don't need to call loadKeyboard later anyway.
            needToCallLoadKeyboardLater = false;
        }

        if (isDifferentTextField ||
                !currentSettingsValues.hasSameOrientation(getResources().getConfiguration())) {
            loadSettings();
        }
        if (isDifferentTextField) {
            mainKeyboardView.closing();
            currentSettingsValues = mSettings.getCurrent();

            switcher.loadKeyboard(editorInfo, currentSettingsValues, getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
            if (needToCallLoadKeyboardLater) {
                // If we need to call loadKeyboard again later, we need to save its state now. The
                // later call will be done in #retryResetCaches.
                switcher.saveKeyboardState();
            }
        } else if (restarting) {
            // TODO: Come up with a more comprehensive way to reset the keyboard layout when
            // a keyboard layout set doesn't get reloaded in this method.
            switcher.resetKeyboardStateToAlphabet(getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
            // In apps like Talk, we come here when the text is sent and the field gets emptied and
            // we need to re-evaluate the shift state, but not the whole layout which would be
            // disruptive.
            // Space state must be updated before calling updateShiftState
            switcher.requestUpdatingShiftState(getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
        }

        mCurrentInputEditorPackageName = editorInfo.packageName;
        fetchUserAndSubscriptionInfo();

        // Build 6.3: Proactive context reading for smart replies
        // ALWAYS log to diagnose AI feature issues (use Log.e for GUARANTEED visibility)
        Log.e("WK_PHASE2_DEBUG", "=== onStartInputViewInternal REACHED === pkg=" + editorInfo.packageName);
        Log.e("WK_PHASE2_DEBUG", "USE_PROACTIVE_CONTEXT=" + DebugConfig.USE_PROACTIVE_CONTEXT +
              " communicationServiceBound=" + communicationServiceBound);
        // Build 7.0: Track editor package so NLS only processes matching notifications
        ConversationMatcher.getInstance().setCurrentEditorPackage(editorInfo.packageName);
        // Build 7.0: Check for pre-computed replies from NLS pipeline
        SmartAssistantBar bar70 = mKeyboardSwitcher.getSmartAssistantBar();
        boolean precomputedHandled = false;
        if (bar70 != null) {
            precomputedHandled = bar70.checkPrecomputedReplies();
        }

        // Sync overlay icon alpha state on keyboard reopen
        if (bar70 != null) {
            bar70.refreshOverlayIconState();
        }

        if (precomputedHandled) {
            Log.d("LatinIME", "[Build7.0] Pre-computed replies handled, skipping proactive context");
        } else if (DebugConfig.USE_PROACTIVE_CONTEXT && communicationServiceBound) {
            Log.e("WK_PHASE2_DEBUG", "CALLING triggerProactiveContextReading NOW");
            triggerProactiveContextReading();
        } else {
            Log.e("WK_PHASE2_DEBUG", "SKIPPED triggerProactiveContextReading - conditions not met");
        }

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        if (isInputViewShown())
            setNavigationBarColor();

        // Phase 9: Check if accessibility was just enabled and hide prompt if so
        checkAndClearAccessibilityPromptIfEnabled();
    }

    /**
     * Phase 9: Check if accessibility is now enabled and clear the prompt.
     * Called when keyboard window is shown (e.g., after returning from Settings).
     */
    private void checkAndClearAccessibilityPromptIfEnabled() {
        SmartAssistantBar bar = mKeyboardSwitcher.getSmartAssistantBar();
        if (bar == null || !bar.isAccessibilityPromptVisible()) {
            return; // No prompt showing, nothing to do
        }

        // Check if accessibility is now enabled
        if (communicationService != null && communicationServiceBound) {
            ContextEngine contextEngine = communicationService.getContextEngine();
            if (contextEngine != null) {
                AccessibilityNodeInfo root = communicationService.getRootNodeFromAccessibility();
                if (root != null) {
                    // Accessibility is now enabled! Hide prompt and trigger context reading
                    Log.d(TAG, "[SAB] Accessibility now enabled - hiding prompt and triggering context reading");
                    bar.hideAccessibilityPrompt();
                    triggerProactiveContextReading();
                }
            }
        }
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
        clearNavigationBarColor();
    }

    void onFinishInputInternal() {
        super.onFinishInput();

        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
    }

    void onFinishInputViewInternal(final boolean finishingInput) {
        super.onFinishInputView(finishingInput);
    }

    protected void deallocateMemory() {
        mKeyboardSwitcher.deallocateMemory();
    }

    @Override
    public void onUpdateSelection(final int oldSelStart, final int oldSelEnd,
                                  final int newSelStart, final int newSelEnd,
                                  final int composingSpanStart, final int composingSpanEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                composingSpanStart, composingSpanEnd);

        if (DebugFlags.DEBUG_ENABLED) {
            Log.i(TAG, "onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
                    + ", nss=" + newSelStart + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart + ", ce=" + composingSpanEnd);
        }

        // This call happens whether our view is displayed or not, but if it's not then we should
        // not attempt recorrection. This is true even with a hardware keyboard connected: if the
        // view is not displayed we have no means of showing suggestions anyway, and if it is then
        // we want to show suggestions anyway.
        if (isInputViewShown()
                && mInputLogic.onUpdateSelection(newSelStart, newSelEnd)) {
            mKeyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
        }
    }

    @Override
    public void hideWindow() {
        // Check protection with timeout safety
        if (notHideWindow && !isProtectionExpired()) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "🛡️ KEYBOARD_PROTECT: hideWindow() BLOCKED - protection active");
            }
            return;
        }

        // If protection expired, release it automatically (safety mechanism)
        if (isProtectionExpired()) {
            if (DebugConfig.isDebugMode) {
                Log.w(TAG, "⚠️ KEYBOARD_PROTECT: Protection EXPIRED after 15s, auto-releasing");
            }
            releaseKeyboardProtection();
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🪟 hideWindow() executing normally");
        }

        mKeyboardSwitcher.onHideWindow();

        if (TRACE) Debug.stopMethodTracing();
        if (isShowingOptionDialog()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
        super.hideWindow();
    }

    /**
     * Protects keyboard from hiding during Read Screen capture.
     */
    public void protectKeyboardVisibility() {
        notHideWindow = true;
        mProtectionStartTime = System.currentTimeMillis();
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🛡️ KEYBOARD_PROTECT: Protection ENABLED - keyboard will not hide");
        }
    }

    /**
     * Releases keyboard protection after Read Screen completes.
     */
    public void releaseKeyboardProtection() {
        notHideWindow = false;
        mProtectionStartTime = 0;
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🛡️ KEYBOARD_PROTECT: Protection RELEASED - normal hide behavior restored");
        }
    }

    /**
     * Checks if protection has timed out (safety mechanism).
     */
    private boolean isProtectionExpired() {
        if (!notHideWindow || mProtectionStartTime == 0) return false;
        long elapsed = System.currentTimeMillis() - mProtectionStartTime;
        return elapsed > MAX_PROTECTION_DURATION_MS;
    }
    // In LatinIME.java
    @Override
    public void onComputeInsets(final InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        // This method may be called before {@link #setInputView(View)}.
        if (mInputView == null) {
            return;
        }
        final View visibleKeyboardView = mKeyboardSwitcher.getVisibleKeyboardView();
        // This is the new flag for clarity
        final boolean isStandardKeyboardViewShown = (visibleKeyboardView == mKeyboardSwitcher.getMainKeyboardView());

        if (visibleKeyboardView == null) {
            return;
        }
        final int inputHeight = mInputView.getHeight();
        if (isImeSuppressedByHardwareKeyboard() && !visibleKeyboardView.isShown()) {
            // ... (this part is fine)
            return;
        }

        // --- START: MODIFIED LOGIC ---

        final int mainKeyboardAreaHeight;
        final int visibleTopY;

        if (isStandardKeyboardViewShown) {
            // If the standard keyboard is shown, calculate its full area height
            final int utilityRowHeight = mKeyboardSwitcher.getUtilityRowHeight();
            final int suggestionRowHeight = mKeyboardSwitcher.getSuggestionRowHeight();
            final int mainKeyboardViewHeight = mKeyboardSwitcher.getMainKeyboardView().getHeight();
            final int assistantViewHeight = mKeyboardSwitcher.getAiViewAssistantHeight();

            mainKeyboardAreaHeight = utilityRowHeight + suggestionRowHeight + assistantViewHeight + mainKeyboardViewHeight;
            visibleTopY = inputHeight - mainKeyboardAreaHeight;

            // Store this calculated height in the switcher for other views to use
            mKeyboardSwitcher.setCalculatedKeyboardHeight(mainKeyboardAreaHeight);
        } else {
            // For custom views (AiView, ToneView, etc.), we RE-USE the stored height
            mainKeyboardAreaHeight = mKeyboardSwitcher.getCalculatedKeyboardHeight();
            visibleTopY = inputHeight - mainKeyboardAreaHeight;
        }

        // Safety check: if height is 0, it means we haven't calculated it yet.
        // Fall back to the visible view's height to avoid a blank screen.
        if (mainKeyboardAreaHeight <= 0) {
            outInsets.contentTopInsets = inputHeight - visibleKeyboardView.getHeight();
            outInsets.visibleTopInsets = inputHeight - visibleKeyboardView.getHeight();
            mInsetsUpdater.setInsets(outInsets);
            return; // Early exit to prevent issues
        }

        // --- END: MODIFIED LOGIC ---

        // The rest of the method can stay the same, but it should use visibleTopY
        // Need to set expanded touchable region only if a keyboard view is being shown.
        if (visibleKeyboardView.isShown()) {
            // ... (touchableRegion logic is fine)
        }
        outInsets.contentTopInsets = visibleTopY;
        outInsets.visibleTopInsets = visibleTopY;
        mInsetsUpdater.setInsets(outInsets);
    }

    @Override
    public boolean onShowInputRequested(final int flags, final boolean configChange) {
        if (isImeSuppressedByHardwareKeyboard()) {
            return true;
        }
        return super.onShowInputRequested(flags, configChange);
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        if (isImeSuppressedByHardwareKeyboard()) {
            // If there is a hardware keyboard, disable full screen mode.
            return false;
        }
        // Reread resource value here, because this method is called by the framework as needed.
        final boolean isFullscreenModeAllowed = Settings.readUseFullscreenMode(getResources());
        if (super.onEvaluateFullscreenMode() && isFullscreenModeAllowed) {
            // TODO: Remove this hack. Actually we should not really assume NO_EXTRACT_UI
            // implies NO_FULLSCREEN. However, the framework mistakenly does.  i.e. NO_EXTRACT_UI
            // without NO_FULLSCREEN doesn't work as expected. Because of this we need this
            // hack for now.  Let's get rid of this once the framework gets fixed.
            final EditorInfo ei = getCurrentInputEditorInfo();
            return !(ei != null && ((ei.imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0));
        }
        return false;
    }

    @Override
    public void updateFullscreenMode() {
        super.updateFullscreenMode();
        updateSoftInputWindowLayoutParameters();
    }

    private void updateSoftInputWindowLayoutParameters() {
        // Override layout parameters to expand {@link SoftInputWindow} to the entire screen.
        // See {@link InputMethodService#setinputView(View)} and
        // {@link SoftInputWindow#updateWidthHeight(WindowManager.LayoutParams)}.
        final Window window = getWindow().getWindow();
        ViewLayoutUtils.updateLayoutHeightOf(window, LayoutParams.MATCH_PARENT);
        // This method may be called before {@link #setInputView(View)}.
        if (mInputView != null) {
            // In non-fullscreen mode, {@link InputView} and its parent inputArea should expand to
            // the entire screen and be placed at the bottom of {@link SoftInputWindow}.
            // In fullscreen mode, these shouldn't expand to the entire screen and should be
            // coexistent with {@link #mExtractedArea} above.
            // See {@link InputMethodService#setInputView(View) and
            // com.android.internal.R.layout.input_method.xml.
            final int layoutHeight = isFullscreenMode()
                    ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;
            final View inputArea = window.findViewById(android.R.id.inputArea);
            ViewLayoutUtils.updateLayoutHeightOf(inputArea, layoutHeight);
            ViewLayoutUtils.updateLayoutGravityOf(inputArea, Gravity.BOTTOM);
            ViewLayoutUtils.updateLayoutHeightOf(mInputView, layoutHeight);
        }
    }

    int getCurrentAutoCapsState() {
        return mInputLogic.getCurrentAutoCapsState(mSettings.getCurrent(),
                mRichImm.getCurrentSubtype().getKeyboardLayoutSet());
    }

    int getCurrentRecapitalizeState() {
        return mInputLogic.getCurrentRecapitalizeState();
    }

    @Override
    public boolean onCustomRequest(final int requestCode) {
        switch (requestCode) {
            case Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER:
                return showInputMethodPicker();
            case Constants.CODE_DELETE:
                mInputLogic.sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL);
                return true;
            case Constants.CODE_SWITCH_EMOJI:
                mKeyboardSwitcher.toggleEmojiKeyboardView();
                return true;
            default:
                Log.e(TAG, "Unable to match the desired swap behavior for request code: " + requestCode);
                return false;

        }
    }

    public boolean showInputMethodPicker() {
        if (isShowingOptionDialog()) {
            return false;
        }
        mOptionsDialog = mRichImm.showSubtypePicker(this,
                mKeyboardSwitcher.getMainKeyboardView().getWindowToken(), this);
        return mOptionsDialog != null;
    }

    public Locale getCurrentLayoutLocale() {
        return mLocale;
    }

    @Override
    public void onMovePointer(int steps) {
        Log.d(TAG, "onMovePointer: " + steps);
        if (mInputLogic.mConnection.hasCursorPosition()) {
            if (TextUtils.getLayoutDirectionFromLocale(getCurrentLayoutLocale()) == View.LAYOUT_DIRECTION_RTL)
                steps = -steps;

            steps = mInputLogic.mConnection.getUnicodeSteps(steps, true);
            final int end = mInputLogic.mConnection.getExpectedSelectionEnd() + steps;
            final int start = mInputLogic.mConnection.hasSelection() ? mInputLogic.mConnection.getExpectedSelectionStart() : end;
            mInputLogic.mConnection.setSelection(start, end);
        } else {
            for (; steps < 0; steps++)
                mInputLogic.sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT);
            for (; steps > 0; steps--)
                mInputLogic.sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT);
        }
    }

    @Override
    public void onMoveDeletePointer(int steps) {
        if (mInputLogic.mConnection.hasCursorPosition()) {
            steps = mInputLogic.mConnection.getUnicodeSteps(steps, false);
            final int end = mInputLogic.mConnection.getExpectedSelectionEnd();
            final int start = mInputLogic.mConnection.getExpectedSelectionStart() + steps;
            if (start > end)
                return;
            mInputLogic.mConnection.setSelection(start, end);
        } else {
            for (; steps < 0; steps++)
                mInputLogic.sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL);
        }
    }

    @Override
    public void onUpWithDeletePointerActive() {
        if (mInputLogic.mConnection.hasSelection())
            mInputLogic.sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL);
    }

    private boolean isShowingOptionDialog() {
        return mOptionsDialog != null && mOptionsDialog.isShowing();
    }

    public void switchToNextSubtype() {
        final IBinder token = getWindow().getWindow().getAttributes().token;
        mRichImm.switchToNextInputMethod(token, !shouldSwitchToOtherInputMethods(token));
    }

    // TODO: Instead of checking for alphabetic keyboard here, separate keycodes for
    // alphabetic shift and shift while in symbol layout and get rid of this method.
    private int getCodePointForKeyboard(final int codePoint) {
        if (Constants.CODE_SHIFT == codePoint) {
            final Keyboard currentKeyboard = mKeyboardSwitcher.getKeyboard();
            if (null != currentKeyboard && currentKeyboard.mId.isAlphabetKeyboard()) {
                return codePoint;
            }
            return Constants.CODE_SYMBOL_SHIFT;
        }
        return codePoint;
    }

    // Implementation of {@link KeyboardActionListener}.
    @Override
    public void onCodeInput(final int codePoint, final int x, final int y,
                            final boolean isKeyRepeat) {
        // === Internal input target interception ===
        // When an InternalInputTarget is active (SAB custom prompt or emoji search),
        // route key events to it instead of the host app's editor.
        final InternalInputTarget activeTarget =
                mKeyboardSwitcher.getActiveInternalInputTarget();
        if (activeTarget != null && handleInternalInputCodePoint(
                activeTarget, codePoint, x, y, isKeyRepeat)) {
            return;
        }

        // Debug logging for key press
        android.util.Log.d("WK_KEYBOARD", "[KEY_PRESS] codePoint=" + codePoint +
            " char='" + (codePoint > 31 ? (char)codePoint : "?") + "'" +
            " x=" + x + " y=" + y + " repeat=" + isKeyRepeat);

        // TODO: this processing does not belong inside LatinIME, the caller should be doing this.
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        // x and y include some padding, but everything down the line (especially native
        // code) needs the coordinates in the keyboard frame.
        // TODO: We should reconsider which coordinate system should be used to represent
        // keyboard event. Also we should pull this up -- LatinIME has no business doing
        // this transformation, it should be done already before calling onEvent.
        final int keyX = mainKeyboardView.getKeyX(x);
        final int keyY = mainKeyboardView.getKeyY(y);
        final Event event = createSoftwareKeypressEvent(getCodePointForKeyboard(codePoint),
                keyX, keyY, isKeyRepeat);
        onEvent(event);

        if (codePoint > 0 && !isKeyRepeat) {
            mCharactersTypedInSession++;  // Count for session tracking
            trackFirstMessageIfNeeded();   // Existing activation tracking

            // Phase 4: Notify accessibility that user is typing (for EVERY key press)
            // This prevents new message detection from firing while user types
            ScreenReaderAccessibility accessibility = ScreenReaderAccessibility.getInstance();
            if (accessibility != null) {
                accessibility.notifyUserTyping();
            }
        }

    }

    private boolean handleInternalInputCodePoint(final InternalInputTarget activeTarget,
                                                 final int codePoint, final int x, final int y,
                                                 final boolean isKeyRepeat) {
        if (codePoint == Constants.CODE_SHIFT
                || codePoint == Constants.CODE_CAPSLOCK
                || codePoint == Constants.CODE_SWITCH_ALPHA_SYMBOL
                || codePoint == Constants.CODE_SYMBOL_SHIFT) {
            return false;
        }

        if (codePoint == Constants.CODE_DELETE) {
            activeTarget.onDeleteInput();
            dispatchConsumedInternalInputEvent(codePoint, x, y, isKeyRepeat);
            refreshInternalInputShiftState(activeTarget);
            return true;
        }

        activeTarget.onCodeInput(codePoint);
        if (codePoint > 0 && !isKeyRepeat) {
            dispatchConsumedInternalInputEvent(codePoint, x, y, false);
        }
        refreshInternalInputShiftState(activeTarget);
        return true;
    }

    private void dispatchConsumedInternalInputEvent(final int codePoint, final int x, final int y,
                                                   final boolean isKeyRepeat) {
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        final int keyX = mainKeyboardView != null ? mainKeyboardView.getKeyX(x) : x;
        final int keyY = mainKeyboardView != null ? mainKeyboardView.getKeyY(y) : y;
        final Event event = createSoftwareKeypressEvent(getCodePointForKeyboard(codePoint),
                keyX, keyY, isKeyRepeat);
        mKeyboardSwitcher.onEvent(event, getCurrentAutoCapsState(), getCurrentRecapitalizeState());
    }

    private void refreshInternalInputShiftState(final InternalInputTarget activeTarget) {
        if (activeTarget == null) return;
        mKeyboardSwitcher.requestUpdatingShiftState(getInternalInputAutoCapsState(activeTarget),
                getCurrentRecapitalizeState());
    }

    private int getInternalInputAutoCapsState(final InternalInputTarget activeTarget) {
        final String text = activeTarget.getText();
        if (TextUtils.isEmpty(text)) {
            return TextUtils.CAP_MODE_SENTENCES;
        }

        int lastNonSpace = text.length() - 1;
        while (lastNonSpace >= 0 && Character.isWhitespace(text.charAt(lastNonSpace))) {
            lastNonSpace--;
        }
        if (lastNonSpace < 0) {
            return TextUtils.CAP_MODE_SENTENCES;
        }

        final boolean hasTrailingSpace = lastNonSpace < text.length() - 1;
        final char last = text.charAt(lastNonSpace);
        if (hasTrailingSpace && (last == '.' || last == '!' || last == '?')) {
            return TextUtils.CAP_MODE_SENTENCES;
        }
        return Constants.TextUtils.CAP_MODE_OFF;
    }

    // This method is public for testability of LatinIME, but also in the future it should
    // completely replace #onCodeInput.
    public void onEvent(final Event event) {
        final InputTransaction completeInputTransaction =
                mInputLogic.onCodeInput(mSettings.getCurrent(), event);
        updateStateAfterInputTransaction(completeInputTransaction);
        mKeyboardSwitcher.onEvent(event, getCurrentAutoCapsState(), getCurrentRecapitalizeState());
    }

    // A helper method to split the code point and the key code. Ultimately, they should not be
    // squashed into the same variable, and this method should be removed.
    // public for testing, as we don't want to copy the same logic into test code
    public static Event createSoftwareKeypressEvent(final int keyCodeOrCodePoint, final int keyX,
                                                    final int keyY, final boolean isKeyRepeat) {
        final int keyCode;
        final int codePoint;
        if (keyCodeOrCodePoint <= 0) {
            keyCode = keyCodeOrCodePoint;
            codePoint = Event.NOT_A_CODE_POINT;
        } else {
            keyCode = Event.NOT_A_KEY_CODE;
            codePoint = keyCodeOrCodePoint;
        }
        return Event.createSoftwareKeypressEvent(codePoint, keyCode, keyX, keyY, isKeyRepeat);
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onTextInput(final String rawText) {
        // Internal input target interception for multi-char input (e.g., ".com" key)
        final InternalInputTarget activeTextTarget =
                mKeyboardSwitcher.getActiveInternalInputTarget();
        if (activeTextTarget != null) {
            if (rawText != null) {
                for (int i = 0; i < rawText.length(); ) {
                    int cp = rawText.codePointAt(i);
                    activeTextTarget.onCodeInput(cp);
                    i += Character.charCount(cp);
                }
            }
            refreshInternalInputShiftState(activeTextTarget);
            return;
        }

        // TODO: have the keyboard pass the correct key code when we need it.
        final Event event = Event.createSoftwareTextEvent(rawText, Constants.CODE_OUTPUT_TEXT);
        final InputTransaction completeInputTransaction =
                mInputLogic.onTextInput(mSettings.getCurrent(), event);
        updateStateAfterInputTransaction(completeInputTransaction);
        mKeyboardSwitcher.onEvent(event, getCurrentAutoCapsState(), getCurrentRecapitalizeState());
        // Track first message for activation scoring
        if (rawText != null && !rawText.trim().isEmpty()) {
            trackFirstMessageIfNeeded();

            // Phase 4: Notify accessibility that user is typing
            // This prevents user's own sent messages from triggering "new message" detection
            ScreenReaderAccessibility accessibility = ScreenReaderAccessibility.getInstance();
            if (accessibility != null) {
                accessibility.notifyUserTyping();
            }
        }
    }


    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onFinishSlidingInput() {
        // User finished sliding input.
        mKeyboardSwitcher.onFinishSlidingInput(getCurrentAutoCapsState(),
                getCurrentRecapitalizeState());
    }

    public void loadKeyboard() {
        // Since we are switching languages, the most urgent thing is to let the keyboard graphics
        // update. LoadKeyboard does that, but we need to wait for buffer flip for it to be on
        // the screen. Anything we do right now will delay this, so wait until the next frame
        // before we do the rest, like reopening dictionaries and updating suggestions. So we
        // post a message.
        loadSettings();
        if (mKeyboardSwitcher.getMainKeyboardView() != null) {
            // Reload keyboard because the current language has been changed.
            mKeyboardSwitcher.loadKeyboard(getCurrentInputEditorInfo(), mSettings.getCurrent(),
                    getCurrentAutoCapsState(), getCurrentRecapitalizeState());
        }
    }

    /**
     * After an input transaction has been executed, some state must be updated. This includes
     * the shift state of the keyboard and suggestions. This method looks at the finished
     * inputTransaction to find out what is necessary and updates the state accordingly.
     *
     * @param inputTransaction The transaction that has been executed.
     */
    private void updateStateAfterInputTransaction(final InputTransaction inputTransaction) {
        switch (inputTransaction.getRequiredShiftUpdate()) {
            case InputTransaction.SHIFT_UPDATE_LATER:
                mHandler.postUpdateShiftState();
                break;
            case InputTransaction.SHIFT_UPDATE_NOW:
                mKeyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(),
                        getCurrentRecapitalizeState());
                break;
            default: // SHIFT_NO_UPDATE
        }
    }

    private void hapticAndAudioFeedback(final int code, final int repeatCount) {
        final MainKeyboardView keyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (keyboardView != null && keyboardView.isInDraggingFinger()) {
            // No need to feedback while finger is dragging.
            return;
        }
        if (repeatCount > 0) {
            if (code == Constants.CODE_DELETE && !mInputLogic.mConnection.canDeleteCharacters()) {
                // No need to feedback when repeat delete key will have no effect.
                return;
            }
            // TODO: Use event time that the last feedback has been generated instead of relying on
            // a repeat count to thin out feedback.
            if (repeatCount % PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT == 0) {
                return;
            }
        }
        final AudioAndHapticFeedbackManager feedbackManager =
                AudioAndHapticFeedbackManager.getInstance();
        if (repeatCount == 0) {
            // TODO: Reconsider how to perform haptic feedback when repeating key.
            feedbackManager.performHapticFeedback(keyboardView);
        }
        feedbackManager.performAudioFeedback(code);
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is depressed;
    // release matching call is {@link #onReleaseKey(int,boolean)} below.
    @Override
    public void onPressKey(final int primaryCode, final int repeatCount,
                           final boolean isSinglePointer) {
        mKeyboardSwitcher.onPressKey(primaryCode, isSinglePointer, getCurrentAutoCapsState(),
                getCurrentRecapitalizeState());
        hapticAndAudioFeedback(primaryCode, repeatCount);
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is released;
    // press matching call is {@link #onPressKey(int,int,boolean)} above.
    @Override
    public void onReleaseKey(final int primaryCode, final boolean withSliding) {
        mKeyboardSwitcher.onReleaseKey(primaryCode, withSliding, getCurrentAutoCapsState(),
                getCurrentRecapitalizeState());
    }

    // receive ringer mode change.
    private final BroadcastReceiver mRingerModeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                AudioAndHapticFeedbackManager.getInstance().onRingerModeChanged();
            }
        }
    };

    public void launchSettings() {
        requestHideSelf(0);
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
        final Intent intent = new Intent();
        intent.setClass(LatinIME.this, SettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    public void debugDumpStateAndCrashWithException(final String context) {
        final SettingsValues settingsValues = mSettings.getCurrent();
        final StringBuilder s = new StringBuilder(settingsValues.toString());
        s.append("\nAttributes : ").append(settingsValues.mInputAttributes)
                .append("\nContext : ").append(context);
        throw new RuntimeException(s.toString());
    }

    @Override
    protected void dump(final FileDescriptor fd, final PrintWriter fout, final String[] args) {
        super.dump(fd, fout, args);

        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        p.println("  VersionCode = " + ApplicationUtils.getVersionCode(this));
        p.println("  VersionName = " + ApplicationUtils.getVersionName(this));
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        final int keyboardMode = keyboard != null ? keyboard.mId.mMode : -1;
        p.println("  Keyboard mode = " + keyboardMode);
    }

    public boolean shouldSwitchToOtherInputMethods(final IBinder token) {
        // TODO: Revisit here to reorganize the settings. Probably we can/should use different
        // strategy once the implementation of
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} is defined well.
        if (!mSettings.getCurrent().mImeSwitchEnabled) {
            return false;
        }
        return mRichImm.shouldOfferSwitchingToOtherInputMethods(token);
    }

    public boolean shouldShowLanguageSwitchKey() {
        if (mSettings.getCurrent().isLanguageSwitchKeyDisabled()) {
            return false;
        }
        if (mRichImm.hasMultipleEnabledSubtypes()) {
            return true;
        }

        final IBinder token = getWindow().getWindow().getAttributes().token;
        if (token == null) {
            return false;
        }
        return shouldSwitchToOtherInputMethods(token);
    }

    // Round 3 Fix #7: Made public so DebugSABController can refresh nav bar after theme switch
    public void setNavigationBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mSettings.getCurrent().mUseMatchingNavbarColor) {
            // Get the exact color from the keyboard view to ensure perfect match
            int keyboardColor = 0;
            final MainKeyboardView keyboardView = mKeyboardSwitcher.getMainKeyboardView();
            if (keyboardView != null && Color.alpha(keyboardView.mCustomColor) > 0) {
                keyboardColor = keyboardView.mCustomColor;
            } else {
                // Fallback if keyboard view not ready
                final SharedPreferences prefs = PreferenceManagerCompat.getDeviceSharedPreferences(this);
                keyboardColor = Settings.readKeyboardColor(prefs, this);
            }

            final Window window = getWindow().getWindow();
            if (window == null) {
                return;
            }
            mOriginalNavBarColor = window.getNavigationBarColor();
            window.setNavigationBarColor(keyboardColor);

            final View view = window.getDecorView();
            mOriginalNavBarFlags = view.getSystemUiVisibility();
            if (ResourceUtils.isBrightColor(keyboardColor)) {
                view.setSystemUiVisibility(mOriginalNavBarFlags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            } else {
                view.setSystemUiVisibility(mOriginalNavBarFlags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
        }
    }

    private void clearNavigationBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mSettings.getCurrent().mUseMatchingNavbarColor) {
            final Window window = getWindow().getWindow();
            if (window == null) {
                return;
            }
            window.setNavigationBarColor(mOriginalNavBarColor);
            final View view = window.getDecorView();
            view.setSystemUiVisibility(mOriginalNavBarFlags);
        }
    }

    public InputLogic getInputLogicInstance() {
        return mInputLogic;
    }

    /** Set the active internal input target. Pass null to clear. */
    public void setInternalInputTarget(InternalInputTarget target) {
        mInternalInputTarget = target;
        Log.d(TAG, "InternalInputTarget set: " + (target != null ? target.getClass().getSimpleName() : "null"));
    }

    /** Get the active internal input target, or null if none. */
    public InternalInputTarget getInternalInputTarget() {
        return mInternalInputTarget;
    }

    public void toggleAccessibilityService(boolean enable) {
        if (communicationServiceBound) {
            if (enable) {
                communicationService.enableAccessibility();
                Log.d("LatinIME", "Enable accessibility called via Communication Service");
            } else {
                communicationService.disableAccessibility();
                Log.d("LatinIME", "Disable accessibility called via Communication Service");
            }
        } else {
            Log.e("LatinIME", "Communication Service not bound.");
        }
    }

    public void receiveTextFromAccessibility(String receivedText) {
        Log.w("LatinIME", "receiveTextFromAccessibility len=" + (receivedText == null ? 0 : receivedText.length()));
        if (receivedText != null && receivedText.length() > 0) {
            KeyboardSwitcher.getInstance().receivedTexts.add(receivedText);
            Log.w("LatinIME", "stored in receivedTexts; total=" + KeyboardSwitcher.getInstance().receivedTexts.size());
        }
    }
    public void receiveTextFromScreenshotAnalysis(String receivedText) {
        if (receivedText != null) {
            mKeyboardSwitcher.capturedText = receivedText;
            Log.d("KeyboardSwitcher", "screenshotReceiver: " + mKeyboardSwitcher.capturedText);
        }
    }

    private void fetchUserAndSubscriptionInfo() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        User user = EncryptedPreferences.getUserLoggedInInfo();
        if (user == null) {
            Log.w(TAG, "User not logged in. Skipping subscription sync.");
            return;
        }

        String userId = user.getId();

        // Sync subscription status (for unlimited flag)
        Subscription.getLatestSubscription(userId, db, task -> {
            if (task.isSuccessful()) {
                QuerySnapshot querySnapshot = task.getResult();
                if (querySnapshot != null && !querySnapshot.isEmpty()) {
                    DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                    String subscriptionPackageId = document.getString(Subscription.PACKAGE_ID);
                    String statusString = document.getString(Subscription.STATUS);

                    Subscription.SubscriptionStatus firestoreStatus = Subscription.SubscriptionStatus.ACTIVE;
                    try {
                        if (statusString != null && !statusString.isEmpty()) {
                            firestoreStatus = Subscription.SubscriptionStatus.valueOf(statusString);
                        }
                    } catch (IllegalArgumentException e) {
                        Log.w(TAG, "Invalid status from Firestore: " + statusString);
                    }

                    // If user has an active paid subscription, set unlimited
                    boolean isPaidSub = subscriptionPackageId != null
                            && !Subscription.FT_PACKAGE_ID_STRING.equals(subscriptionPackageId)
                            && firestoreStatus == Subscription.SubscriptionStatus.ACTIVE;

                    DailyUsageTracker.getInstance(LatinIME.this).setUnlimited(isPaidSub);

                    // Keep local subscription info updated for other code that reads it
                    if (Subscription.FT_PACKAGE_ID_STRING.equals(subscriptionPackageId)) {
                        // Free trial user — daily usage gating applies
                        Log.d(TAG, "Free trial user — daily usage gating via DailyUsageTracker");
                    } else {
                        // Paid subscriber
                        Date endDate = document.getDate(Subscription.SUBSCRIPTION_END_DATE);
                        long expiry = (endDate != null) ? endDate.getTime() : 0;
                        int consumed = 0;
                        if (document.getLong(Subscription.CONSUMED_TOKENS) != null) {
                            consumed = document.getLong(Subscription.CONSUMED_TOKENS).intValue();
                        }
                        EncryptedPreferences.saveSubscriptionInfo(
                                subscriptionPackageId, subscriptionPackageId,
                                expiry, consumed, firestoreStatus);
                        Log.d(TAG, "Paid subscriber synced. Status: " + firestoreStatus + " | plus allowance active");
                    }
                } else {
                    Log.w(TAG, "No subscription found for user: user_present=" + (userId != null && !userId.isEmpty()));
                }
            } else {
                Log.w(TAG, "Subscription fetch failed", task.getException());
            }
        });
    }

    /**
     * Toggles the keyboard visibility or forces it to a specific state.
     *
     * @param forceShow If true, attempts to show the keyboard.
     *                  If false, attempts to hide the keyboard.
     *                  If null, toggles the current visibility.
     * @return The new visibility state (true if shown, false if hidden) after the action is requested.
     *         Note that visibility changes are asynchronous.
     */
    public boolean setKeyboardVisibility(@Nullable Boolean forceShow) {
        boolean currentlyShown = isInputViewShown();
        boolean makeVisible;

        if (forceShow != null) {
            makeVisible = forceShow;
        } else {
            makeVisible = !currentlyShown; // Toggle
        }

        if (makeVisible) {
            if (!currentlyShown) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Log.d(TAG, "Requesting to show keyboard.");
                    requestShowSelf(InputMethodManager.SHOW_IMPLICIT); // Or 0
                }
            } else {
                Log.d(TAG, "Keyboard already shown, no action taken for show request.");
            }
            return true; // Intended state is shown
        } else { // makeHidden
            if (currentlyShown) {
                Log.d(TAG, "Requesting to hide keyboard.");
                requestHideSelf(0);
            } else {
                Log.d(TAG, "Keyboard already hidden, no action taken for hide request.");
            }
            return false; // Intended state is hidden
        }
    }

    public String getCurrentInputEditorPackageName() {
        return mCurrentInputEditorPackageName;
    }

    public void onRepliesReceived(final String replies) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                // This code will now run on the UI thread.
                Log.d(TAG, "onRepliesReceived: Executing on UI thread." + replies.toString());
                KeyboardSwitcher.getInstance().onRepliesReceived(replies);
            }
        });
    }

    /**
     * Phase 4 (J13): Called when a new message arrives while user is typing.
     * Triggers brain blink animation in SmartAssistantBar.
     *
     * @param senderName The sender of the new message
     * @param messageText The new message content
     */
    public void onNewMessageWhileTyping(final String senderName, final String messageText) {
        mHandler.post(() -> {
            Log.d(TAG, "[J13] onNewMessageWhileTyping from: " + senderName);
            SmartAssistantBar bar = mKeyboardSwitcher.getSmartAssistantBar();
            if (bar != null) {
                // Trigger brain blink animation to indicate new message
                // Quick replies will be generated asynchronously by ReplyGenerator
                bar.onNewMessageReceived(senderName, java.util.Collections.emptyList());
            } else {
                Log.w(TAG, "[J13] SmartAssistantBar not available for new message notification");
            }
        });
    }

    private void checkClipboard() {
        if (mClipboardManager == null) return;

        ActionTracker tracker = ActionTracker.getInstance(this);

        if (mClipboardManager.hasPrimaryClip()) {
            ClipData clipData = mClipboardManager.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() > 0) {
                ClipData.Item item = clipData.getItemAt(0);
                CharSequence text = item.getText();

                if (text != null && text.length() > 0 && text.length() < 500) {
                    // Store in ActionTracker — Row 2 will pick it up dynamically
                    tracker.setClipboardText(text.toString());

                    // Trigger Row 2 refresh if keyboard is visible
                    refreshRow2IfVisible();
                    return;
                }
            }
        }

        // No valid clipboard text — clear tracker
        tracker.clearClipboardText();
    }

    /**
     * Refresh dynamic Row 2 chips if the keyboard is currently showing.
     */
    private void refreshRow2IfVisible() {
        SmartAssistantBar smartAssistantBar = mKeyboardSwitcher.getSmartAssistantBar();
        if (smartAssistantBar != null && smartAssistantBar.isShown()) {
            smartAssistantBar.refreshDynamicRow2();
        }
    }

    public void onGiphyMediaSelected(final Media media) {
        final String gifUrl = media.getImages().getOriginal().getGifUrl();
        if (gifUrl == null) {
            Log.e(TAG, "Selected GIPHY media has no URL.");
            return;
        }

        // IMPORTANT: We must download the GIF first, then share it via FileProvider.
        // This needs to happen on a background thread.
        new Thread(() -> {
            final File gifFile = downloadGiphyToCache(gifUrl);
            if (gifFile == null) {
                Log.e(TAG, "Failed to download GIPHY file.");
                mMainThreadHandler.post(() -> Toast.makeText(this, "Failed to send GIF.", Toast.LENGTH_SHORT).show());
                return;
            }

            final Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", gifFile);

            // The actual commit must happen back on the main thread.
            mMainThreadHandler.post(() -> {
                sendRichContentToApp(contentUri, "image/gif", media.getTitle());
                // Hide the Giphy view after selection
                mKeyboardSwitcher.showKeyboardView();
            });
        }).start();
    }

    private File downloadGiphyToCache(String url) {
        try {
            // Setup the cache directory, same as for stickers
            File cacheDir = new File(getCacheDir(), "stickers"); // Re-using the 'stickers' cache dir is fine
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File file = new File(cacheDir, System.currentTimeMillis() + ".gif");

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) return null;

            ResponseBody body = response.body();
            if (body == null) return null;

            try (InputStream in = body.byteStream(); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[2048];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            return file;

        } catch (IOException e) {
            Log.e(TAG, "Error downloading GIPHY file to cache", e);
            return null;
        }
    }

    private void sendRichContentToApp(Uri contentUri, String mimeType, String description) {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        final int flag = InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        final InputContentInfo inputContentInfo = new InputContentInfo(
                contentUri,
                new ClipDescription(description, new String[]{mimeType}),
                null
        );
        ic.commitContent(inputContentInfo, flag, null);
    }

    /**
     * Track first message milestone for activation scoring.
     * Should be called when user commits text to an input field.
     * This is only tracked once per app installation.
     */
    public void trackFirstMessageIfNeeded() {
        if (activationManager == null) {
            if (DebugConfig.isDebugMode) {
                Log.w(TAG, "⚠️ ActivationManager is null, cannot track first message");
            }
            return;
        }

        // Check if already tracked (only track once per install)
        boolean alreadyTracked = EncryptedPreferences.getBoolean(KEY_FIRST_MESSAGE_ACTIVATION_TRACKED, false);
        if (alreadyTracked) {
            return; // Already tracked, skip
        }

        // Simply pass null - ActivationManager will use device ID if user not logged in
        activationManager.trackFirstMessage(null);
        EncryptedPreferences.saveBoolean(KEY_FIRST_MESSAGE_ACTIVATION_TRACKED, true);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "✏️ Activation: First message milestone tracked");
            Log.d(TAG, "📊 " + activationManager.getActivationStatusDebug());
        }
    }

    /**
     * Track keyboard session start for true keyboard DAU metrics.
     * This fires when the keyboard becomes visible in ANY app.
     *
     * This is CRITICAL for keyboard retention - users may use keyboard
     * every day without ever opening the WittyKeys app!
     */
    private void trackKeyboardSessionStart() {
        if (mCurrentSessionId != null) {
            // Session already active, skip duplicate tracking
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "🎹 Session already active, skipping start tracking");
            }
            return;
        }

        // Generate unique session ID
        mCurrentSessionId = java.util.UUID.randomUUID().toString();
        mSessionStartTime = System.currentTimeMillis();
        mCharactersTypedInSession = 0;

        // Get tracking ID (works for logged in AND anonymous users)
        String userId = null;
        User user = EncryptedPreferences.getUserLoggedInInfo();
        if (user != null) {
            userId = user.getId();
        }
        String trackingId = EventHelpers.getTrackingId(this, userId);

        // Fire analytics event
        FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(this);
        EventHelpers.triggerKeyboardSessionStarted(trackingId, mCurrentSessionId, analytics);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎹 KEYBOARD SESSION STARTED");
            Log.d(TAG, "   Session ID: " + mCurrentSessionId);
            Log.d(TAG, "   tracking_id_present: " + (trackingId != null && !trackingId.isEmpty()));
            Log.d(TAG, "   Package: " + mCurrentInputEditorPackageName);
        }
    }

    /**
     * Track keyboard session end with duration and usage metrics.
     * This fires when the keyboard is hidden.
     *
     * Provides valuable metrics:
     * - Session duration (how long keyboard was open)
     * - Characters typed (engagement depth)
     * - Can calculate true keyboard DAU from unique users per day
     */
    private void trackKeyboardSessionEnd() {
        if (mCurrentSessionId == null) {
            // No active session, nothing to track
            return;
        }

        // Calculate session metrics
        long sessionDuration = System.currentTimeMillis() - mSessionStartTime;

        // Get tracking ID
        String userId = null;
        User user = EncryptedPreferences.getUserLoggedInInfo();
        if (user != null) {
            userId = user.getId();
        }
        String trackingId = EventHelpers.getTrackingId(this, userId);

        // Fire analytics event
        FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(this);
        EventHelpers.triggerKeyboardSessionEnded(
                trackingId,
                mCurrentSessionId,
                sessionDuration,
                mCharactersTypedInSession,
                analytics
        );

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎹 KEYBOARD SESSION ENDED");
            Log.d(TAG, "   Session ID: " + mCurrentSessionId);
            Log.d(TAG, "   Duration: " + sessionDuration + "ms (" + (sessionDuration/1000) + "s)");
            Log.d(TAG, "   Characters typed: " + mCharactersTypedInSession);
            Log.d(TAG, "   tracking_id_present: " + (trackingId != null && !trackingId.isEmpty()));
        }

        // Reset session state
        mCurrentSessionId = null;
        mSessionStartTime = 0;
        mCharactersTypedInSession = 0;
    }

    // ========== BUILD 6.3: PROACTIVE CONTEXT READING ==========

    /**
     * Trigger proactive context reading for smart replies.
     * Called when keyboard opens in messaging apps.
     *
     * Build 7.1: Simplified — no MemoryView, just pushes reply chips to Row 1.
     */
    private void triggerProactiveContextReading() {
        // J1.S1: Log keyboard open with package name
        SmartAssistantLogger.j1_onStartInputView(mCurrentInputEditorPackageName);
        Log.w("WK_AI_DEBUG", "[PROACTIVE] Starting triggerProactiveContextReading for pkg=" + mCurrentInputEditorPackageName);

        // Condition checks
        boolean accessibilityEnabled = false;
        boolean isContextualApp = false;
        boolean hasMessages = false;

        if (!DebugConfig.USE_PROACTIVE_CONTEXT) {
            Log.w("WK_AI_DEBUG", "[PROACTIVE] ABORT: USE_PROACTIVE_CONTEXT is false");
            SmartAssistantLogger.j1_conditionsCheck(false, false, false);
            return;
        }
        if (communicationService == null || !communicationServiceBound) {
            Log.w("WK_AI_DEBUG", "[PROACTIVE] ABORT: communicationService=" + communicationService +
                  " bound=" + communicationServiceBound);
            SmartAssistantLogger.j1_conditionsCheck(false, false, false);
            return;
        }

        ContextEngine contextEngine = communicationService.getContextEngine();
        if (contextEngine == null) {
            Log.w("WK_AI_DEBUG", "[PROACTIVE] ABORT: ContextEngine is null - accessibility service may not be running");
            SmartAssistantLogger.j7_accessibilityNotEnabled();
            SmartAssistantLogger.j1_conditionsCheck(false, false, false);
            // Build 7.0: Skip accessibility prompt if NLS pipeline is active
            // NLS replaces accessibility for context reading
            if (ConversationMatcher.getInstance().getActiveContact() == null) {
                showAccessibilityPromptInBar();
            }
            return;
        }
        accessibilityEnabled = true;
        Log.d("WK_AI_DEBUG", "[PROACTIVE] ContextEngine obtained successfully");

        AccessibilityNodeInfo root = communicationService.getRootNodeFromAccessibility();
        if (root == null) {
            Log.w("WK_AI_DEBUG", "[PROACTIVE] ABORT: Root node is null - accessibility may not be enabled or granted");
            SmartAssistantLogger.j7_accessibilityNotEnabled();
            SmartAssistantLogger.j1_conditionsCheck(accessibilityEnabled, false, false);
            // Build 7.0: Skip accessibility prompt if NLS pipeline is active
            if (ConversationMatcher.getInstance().getActiveContact() == null) {
                showAccessibilityPromptInBar();
            }
            return;
        }
        Log.d("WK_AI_DEBUG", "[PROACTIVE] Root node obtained: " + root.getPackageName());

        Log.d("WK_AI_DEBUG", "[PROACTIVE] Extracting context for package: " + mCurrentInputEditorPackageName);

        ScreenContext context = contextEngine.extractContext(root);
        if (context == null) {
            Log.w("WK_AI_DEBUG", "[PROACTIVE] Non-contextual: No context extracted from screen");
            // J9: Non-contextual app handling
            SmartAssistantLogger.j9_nonContextualAppDetected(mCurrentInputEditorPackageName);
            SmartAssistantLogger.j1_conditionsCheck(accessibilityEnabled, false, false);
            // Show OriginalView EXPANDED for non-contextual apps
            showOriginalViewForNonContextualApp();
            return;
        }
        Log.d("WK_AI_DEBUG", "[PROACTIVE] Context extracted: " + context.getClass().getSimpleName());

        if (!(context instanceof Chat)) {
            Log.w("WK_AI_DEBUG", "[PROACTIVE] Non-contextual: Context is not Chat type: " + context.getClass().getSimpleName());
            // J9: Non-contextual app handling
            SmartAssistantLogger.j9_nonContextualAppDetected(mCurrentInputEditorPackageName);
            SmartAssistantLogger.j1_conditionsCheck(accessibilityEnabled, false, false);
            // Show OriginalView EXPANDED for non-contextual apps
            showOriginalViewForNonContextualApp();
            return;
        }
        isContextualApp = true;

        Chat chatContext = (Chat) context;
        int messageCount = chatContext.getMessages() != null ? chatContext.getMessages().size() : 0;
        hasMessages = messageCount > 0;
        Log.d("WK_AI_DEBUG", "[PROACTIVE] Chat context confirmed, messages=" + messageCount);

        // J1.S2: Log condition check results
        SmartAssistantLogger.j1_conditionsCheck(accessibilityEnabled, isContextualApp, hasMessages);

        SmartAssistantBar bar = mKeyboardSwitcher.getSmartAssistantBar();
        if (bar == null) {
            Log.w("WK_AI_DEBUG", "[PROACTIVE] ABORT: SmartAssistantBar not available");
            return;
        }
        Log.d("WK_AI_DEBUG", "[PROACTIVE] SmartAssistantBar obtained");

        // Show loading shimmer in Row 2 while generating replies
        mHandler.post(() -> {
            bar.showOriginalViewWithContext();
        });
        Log.d("WK_AI_DEBUG", "[PROACTIVE] OriginalView shown, generating replies...");

        // J1.S4: Send API request
        SmartAssistantLogger.j1_apiRequestSent("generateReplies", messageCount);

        mReplyGenerator.generateReplies(chatContext, new ReplyGenerator.ReplyCallback() {
            @Override
            public void onRepliesGenerated(List<String> replies) {
                Log.d("WK_AI_DEBUG", "[PROACTIVE] SUCCESS: Received " + replies.size() + " replies: " + replies);

                // J1.S5: API response received
                SmartAssistantLogger.j1_apiResponseReceived(200, "NEUTRAL", replies.size());

                // Display reply chips in Row 1
                mHandler.post(() -> {
                    bar.showSmartReplies(replies);
                });
            }

            @Override
            public void onError(String error) {
                Log.e("WK_AI_DEBUG", "[PROACTIVE] API ERROR: " + error);

                // J8: API error handling — silent failure (hide suggestion bar)
                SmartAssistantLogger.j8_apiCallFailed("API_ERROR", error);
                Log.d("WK_AI_DEBUG", "[PROACTIVE] API error handled silently");
            }
        });
    }

    /**
     * Public method to trigger manual context reading (when user taps screen button).
     */
    public void triggerManualContextReading() {
        triggerProactiveContextReading();
    }

    /**
     * Get the ReplyGenerator instance for E2E testing.
     * Debug builds only.
     */
    public ReplyGenerator getReplyGenerator() {
        return mReplyGenerator;
    }

    /**
     * Phase 4: Show OriginalView EXPANDED for non-contextual apps.
     * Called when keyboard opens in a non-contextual app (Google Keep, Notes, etc.)
     * where we can't extract conversation context.
     */
    private void showOriginalViewForNonContextualApp() {
        SmartAssistantBar bar = mKeyboardSwitcher.getSmartAssistantBar();
        if (bar == null) {
            Log.w("WK_AI_DEBUG", "[J9] SmartAssistantBar not available for non-contextual app handling");
            return;
        }

        // J9.S2: Show OriginalView EXPANDED (no quick replies)
        mHandler.post(() -> {
            // Phase 4 Fix: Stop brain blink animation when switching to non-contextual app
            bar.stopBrainBlinkAnimation();

            // Ensure we're in EXPANDED state with no context for THIS non-contextual app
            bar.showContextHint();  // Shows hint text instead of quick replies
            SmartAssistantLogger.j9_originalViewExpandedShown();
            Log.d("WK_AI_DEBUG", "[J9] OriginalView EXPANDED shown for non-contextual app");
        });
    }

    /**
     * Phase 9: Show accessibility prompt in Row 2 when accessibility is not enabled.
     * Called when keyboard opens but accessibility service is not running.
     */
    private void showAccessibilityPromptInBar() {
        SmartAssistantBar bar = mKeyboardSwitcher.getSmartAssistantBar();
        if (bar == null) {
            Log.w("WK_AI_DEBUG", "[J7] SmartAssistantBar not available for accessibility prompt");
            return;
        }

        // Show OriginalView with accessibility prompt in Row 2
        mHandler.post(() -> {
            // Show accessibility prompt in Row 2
            bar.showAccessibilityPrompt();
            Log.d("WK_AI_DEBUG", "[J7] Accessibility prompt shown in Row 2");
        });
    }

    private static String getSenderName(Chat chat, String defaultName) {
        if (chat.getParticipants() != null && !chat.getParticipants().isEmpty()) {
            return chat.getParticipants().get(0);
        }
        return defaultName;
    }


}
