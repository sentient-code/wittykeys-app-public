package project.witty.keys.app.tutorial;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;
import java.util.List;

import project.witty.keys.BuildConfig;
import project.witty.keys.R;
import project.witty.keys.app.BaseActivity;
import project.witty.keys.app.HomeActivity;
import project.witty.keys.app.helpers.ActivationManager;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.EventHelpers;

/**
 * Interactive Tutorial Activity - Chat-based Design
 *
 * IMPROVEMENTS IMPLEMENTED:
 * 1. Pre-fill sample messages in EditText for AI tasks (AI Chat, Tonality, Grammar)
 * 2. Wait for user to click Send after AI response is applied before completing task
 * 3. Show mock conversation for Read Screen task
 * 4. Skip button as "Skip" text
 * 5. Background image with 20% opacity
 * 6. Bot message bubbles full width
 * 7. Enable Keyboard button visible above navigation bar
 * 8. Header icon in circular background
 */
public class InteractiveTutorialActivity extends BaseActivity {

    private static final String TAG = "TutorialActivity";
    private static final int TOTAL_STEPS = 6;

    // ========== UI Components ==========

    // Header
    private LinearLayout headerContainer;
    private FrameLayout headerIconContainer;
    private ImageView headerIcon;
    private TextView headerStepText;
    private TextView headerTaskName;
    private TextView skipButton;

    // Step Indicator
    private TutorialStepIndicator stepIndicator;

    // Chat Area
    private FrameLayout chatAreaContainer;
    private ImageView chatBackground;
    private RecyclerView chatRecyclerView;
    private TutorialChatAdapter chatAdapter;
    private List<TutorialChatMessage> chatMessages;

    // Input Area
    private LinearLayout inputContainer;
    private EditText messageInput;
    private ImageButton sendButton;

    // Root view for insets
    private View rootView;

    // ========== State ==========
    private TutorialManager tutorialManager;
    private ActivationManager activationManager;
    private TutorialTask currentTask;
    private int completedSteps = 0;
    private boolean isWaitingForKeyboard = false;
    private boolean taskInProgress = false;
    private boolean isWaitingForUserToSendAiResponse = false;  // IMPROVEMENT #2: Wait for user to click send
    private boolean isReturningUserSetup = false;  // True if returning user is just setting up keyboard
    private Handler mainHandler;

    // ========== Broadcast Receivers ==========
    private BroadcastReceiver tutorialReceiver;
    private BroadcastReceiver inputMethodReceiver;

    // ========== Onboarding Timing Tracking ==========
    private long mOnboardingStartTime = 0;      // When onboarding started
    private long mCurrentStepStartTime = 0;      // When current step started
    private int mStepsCompletedBeforeSkip = 0;   // For skip tracking

    private com.google.firebase.analytics.FirebaseAnalytics mFirebaseAnalytics;
    private int mNavigationBarHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎓 Tutorial Activity Created - Chat Mode");
        }
        mFirebaseAnalytics = com.google.firebase.analytics.FirebaseAnalytics.getInstance(this);

        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize managers
        tutorialManager = TutorialManager.getInstance(this);
        activationManager = new ActivationManager(this);

        // Check if this is a returning user just setting up keyboard
        isReturningUserSetup = getIntent().getBooleanExtra("returning_user_setup", false);
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "isReturningUserSetup: " + isReturningUserSetup);
        }

        // Start tutorial mode
        tutorialManager.startTutorialMode();


        // Get tracking ID (works for anonymous users during tutorial)
        String trackingId = EventHelpers.getTrackingId(this, getUserId());
        EventHelpers.triggerOnboardingStarted(trackingId, mFirebaseAnalytics);
        EventHelpers.triggerTutorialStarted(
                trackingId,
               mFirebaseAnalytics
        );

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 ANALYTICS: onboarding_started & tutorial_started");
            Log.d(TAG, "   TrackingID: " + trackingId);
            Log.d(TAG, "   StartTime: " + mOnboardingStartTime);
        }

        // Create UI
        createUI();
        configureSystemBars();
        setupWindowInsets();

        // Register receivers
        registerTutorialReceiver();
        registerInputMethodReceiver();

        // Initialize chat
        chatMessages = new ArrayList<>();
        chatAdapter = new TutorialChatAdapter(this, chatMessages);
        chatAdapter.setOnActionClickListener(this::handleActionClick);
        chatRecyclerView.setAdapter(chatAdapter);

        // Load first task
        loadTask(tutorialManager.getCurrentTask());
    }

    // ========== UI Creation ==========

    private void createUI() {
        float density = getResources().getDisplayMetrics().density;

        // Root FrameLayout
        FrameLayout root = new FrameLayout(this);
        root.setId(View.generateViewId());
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_app_color));

        // Main content layout
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // 1. Header
        headerContainer = createHeader(density);
        mainLayout.addView(headerContainer);

        // 2. Step Indicator
        LinearLayout indicatorWrapper = createStepIndicatorWrapper(density);
        mainLayout.addView(indicatorWrapper);

        // 3. Chat Area
        chatAreaContainer = createChatArea(density);
        mainLayout.addView(chatAreaContainer);

        // 4. Input Area
        inputContainer = createInputArea(density);
        mainLayout.addView(inputContainer);

        root.addView(mainLayout);
        setContentView(root);
        rootView = root;
    }

    private LinearLayout createHeader(float density) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(ContextCompat.getColor(this, R.color.secondary_app_color));
        int paddingH = (int) (16 * density);
        int paddingV = (int) (12 * density);
        header.setPadding(paddingH, paddingV, paddingH, paddingV);

        // Circular container for icon
        headerIconContainer = new FrameLayout(this);
        int iconContainerSize = (int) (48 * density);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(iconContainerSize, iconContainerSize);
        iconContainerParams.setMargins(0, 0, (int) (12 * density), 0);
        headerIconContainer.setLayoutParams(iconContainerParams);

        // Create circular background
        GradientDrawable circleBackground = new GradientDrawable();
        circleBackground.setShape(GradientDrawable.OVAL);
        circleBackground.setColor(ContextCompat.getColor(this, R.color.primary_app_color));
        headerIconContainer.setBackground(circleBackground);

        // Icon inside circular container
        headerIcon = new ImageView(this);
        int iconSize = (int) (28 * density);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        headerIcon.setLayoutParams(iconParams);
        headerIcon.setColorFilter(ContextCompat.getColor(this, R.color.fourth_app_color));
        headerIconContainer.addView(headerIcon);

        header.addView(headerIconContainer);

        // Text container (Step + Task Name)
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.weight = 1;
        textContainer.setLayoutParams(textParams);

        // Step text
        headerStepText = new TextView(this);
        headerStepText.setTextColor(ContextCompat.getColor(this, R.color.fifth_app_color));
        headerStepText.setTextSize(12);
        try {
            headerStepText.setTypeface(Typeface.create("google-sans", Typeface.NORMAL));
        } catch (Exception e) {
            // Fallback
        }
        textContainer.addView(headerStepText);

        // Task name
        headerTaskName = new TextView(this);
        headerTaskName.setTextColor(ContextCompat.getColor(this, R.color.intro_title_text));
        headerTaskName.setTextSize(18);
        try {
            headerTaskName.setTypeface(Typeface.create("google-sans-medium", Typeface.BOLD));
        } catch (Exception e) {
            headerTaskName.setTypeface(null, Typeface.BOLD);
        }
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMargins(0, (int) (2 * density), 0, 0);
        headerTaskName.setLayoutParams(nameParams);
        textContainer.addView(headerTaskName);

        header.addView(textContainer);

        // Skip button as "Skip" text
        skipButton = new TextView(this);
        skipButton.setText("Skip");
        skipButton.setTextColor(ContextCompat.getColor(this, R.color.intro_button_text));
        skipButton.setTextSize(16);
        skipButton.setPadding(
                (int) (12 * density),
                (int) (8 * density),
                (int) (12 * density),
                (int) (8 * density)
        );
        skipButton.setVisibility(View.GONE);
        skipButton.setOnClickListener(v -> showSkipConfirmationDialog());
        header.addView(skipButton);

        return header;
    }

    private LinearLayout createStepIndicatorWrapper(float density) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.CENTER);
        int paddingV = (int) (12 * density);
        wrapper.setPadding(0, paddingV, 0, paddingV);
        wrapper.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_app_color));

        stepIndicator = new TutorialStepIndicator(this);
        wrapper.addView(stepIndicator);

        return wrapper;
    }

    private FrameLayout createChatArea(float density) {
        FrameLayout chatArea = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
        );
        params.weight = 1;
        chatArea.setLayoutParams(params);
        chatArea.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_app_color));

        // Background image with 20% opacity
        chatBackground = new ImageView(this);
        chatBackground.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        chatBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            chatBackground.setImageResource(R.drawable.tutorial_chat_background);
        } catch (Exception e) {
            if (DebugConfig.isDebugMode) {
                Log.w(TAG, "tutorial_chat_background not found, using solid background");
            }
        }
        chatBackground.setAlpha(0.2f);
        chatArea.addView(chatBackground);

        // RecyclerView for chat messages
        chatRecyclerView = new RecyclerView(this);
        chatRecyclerView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setClipToPadding(false);
        int chatPadding = (int) (8 * density);
        chatRecyclerView.setPadding(0, chatPadding, 0, chatPadding + 16);

        chatArea.addView(chatRecyclerView);

        return chatArea;
    }

    private LinearLayout createInputArea(float density) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setBackgroundColor(ContextCompat.getColor(this, R.color.secondary_app_color));
        int paddingH = (int) (12 * density);
        int paddingV = (int) (12 * density);
        container.setPadding(paddingH, paddingV, paddingH, paddingV);

        // EditText with rounded background
        messageInput = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.weight = 1;
        inputParams.setMargins(0, 0, (int) (12 * density), 0);
        messageInput.setLayoutParams(inputParams);
        messageInput.setHint("Type a message...");
        messageInput.setHintTextColor(ContextCompat.getColor(this, R.color.fifth_app_color));
        messageInput.setTextColor(ContextCompat.getColor(this, R.color.intro_title_text));
        messageInput.setTextSize(16);

        // Rounded background for input
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setColor(ContextCompat.getColor(this, R.color.primary_app_color));
        inputBg.setCornerRadius(24 * density);
        messageInput.setBackground(inputBg);

        int inputPaddingH = (int) (20 * density);
        int inputPaddingV = (int) (14 * density);
        messageInput.setPadding(inputPaddingH, inputPaddingV, inputPaddingH, inputPaddingV);
        messageInput.setMaxLines(4);
        messageInput.setMinHeight((int) (48 * density));

        // Enable/disable send button based on text
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                sendButton.setEnabled(s.length() > 0);
                sendButton.setAlpha(s.length() > 0 ? 1f : 0.5f);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        container.addView(messageInput);

        // Send button
        sendButton = new ImageButton(this);
        int buttonSize = (int) (48 * density);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        sendButton.setLayoutParams(buttonParams);
        sendButton.setImageResource(android.R.drawable.ic_menu_send);
        sendButton.setColorFilter(ContextCompat.getColor(this, R.color.intro_button_text));

        // Circular green background
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setShape(GradientDrawable.OVAL);
        sendBg.setColor(ContextCompat.getColor(this, R.color.intro_button_background));
        sendButton.setBackground(sendBg);

        sendButton.setEnabled(false);
        sendButton.setAlpha(0.5f);
        sendButton.setOnClickListener(v -> sendMessage());
        container.addView(sendButton);

        return container;
    }

    // ========== System Configuration ==========

    private void configureSystemBars() {
        Window window = getWindow();
        if (window == null) return;

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());

        window.setStatusBarColor(ContextCompat.getColor(this, R.color.secondary_app_color));
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.secondary_app_color));
        if (insetsController != null) {
            insetsController.setAppearanceLightNavigationBars(false);
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());


            // Store navigation bar height for use when input is hidden
            mNavigationBarHeight = systemBars.bottom;

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "📐 Window Insets - NavBar: " + systemBars.bottom + "px, IME: " + ime.bottom + "px");
            }

            // Apply top padding to header for status bar
            headerContainer.setPadding(
                    headerContainer.getPaddingLeft(),
                    (int) (12 * getResources().getDisplayMetrics().density) + systemBars.top,
                    headerContainer.getPaddingRight(),
                    headerContainer.getPaddingBottom()
            );

            // Apply bottom padding to input area for navigation bar AND keyboard
            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
            int basePadding = (int) (12 * getResources().getDisplayMetrics().density);
            inputContainer.setPadding(
                    inputContainer.getPaddingLeft(),
                    basePadding,
                    inputContainer.getPaddingRight(),
                    basePadding + bottomPadding
            );

            // Update chat RecyclerView padding when input is hidden
            updateChatRecyclerViewPadding();

            // Scroll to bottom when keyboard appears
            if (ime.bottom > 0) {
                chatRecyclerView.post(() -> {
                    if (chatMessages.size() > 0) {
                        chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
                    }
                });
            }

            return windowInsets;
        });
    }

    /**
     * Update chat RecyclerView bottom padding based on input container visibility.
     * When input is hidden, add navigation bar padding to prevent action buttons from being cropped.
     */
    private void updateChatRecyclerViewPadding() {
        if (chatRecyclerView == null) return;

        float density = getResources().getDisplayMetrics().density;
        int basePadding = (int) (8 * density);

        // When input is hidden, add navigation bar height to bottom padding
        boolean inputHidden = (inputContainer.getVisibility() != View.VISIBLE);
        int bottomPadding = basePadding + 16;  // Base padding

        if (inputHidden && mNavigationBarHeight > 0) {
            bottomPadding = basePadding + mNavigationBarHeight + 16;

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "📐 Chat padding (input HIDDEN): " + bottomPadding + "px");
            }
        }

        chatRecyclerView.setPadding(0, basePadding, 0, bottomPadding);
    }

    // ========== Test Accessors ==========

    /**
     * Test accessor — returns the message input EditText for E2E verification.
     * Only used by instrumentation tests to verify text commitment.
     */
    public EditText getMessageInput() {
        return messageInput;
    }

    /**
     * Test accessor — returns current text in the message input.
     */
    public String getMessageText() {
        return messageInput != null ? messageInput.getText().toString() : "";
    }

    /**
     * E2E test support: expose and focus the tutorial input so the selected IME
     * has a real editor to attach to before keyboard/SAB capture tests run.
     */
    public void prepareKeyboardHostForTest() {
        if (!BuildConfig.DEBUG || inputContainer == null || messageInput == null) return;
        inputContainer.setVisibility(View.VISIBLE);
        updateChatRecyclerViewPadding();
        messageInput.setText("");
        messageInput.requestFocus();
        messageInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);
    }

    /**
     * [E2E TEST SUPPORT] Adds a mock incoming message to the chat RecyclerView.
     * This updates the UI, which fires real accessibility events that
     * ScreenReaderAccessibility picks up — triggering the real AI pipeline.
     *
     * Only the conversation is mocked. Everything downstream is real.
     */
    public void addTestIncomingMessage(String sender, String messageText) {
        TutorialChatMessage msg = new TutorialChatMessage(
            messageText,
            TutorialChatMessage.MessageType.BOT_MESSAGE
        );
        msg.setSenderName(sender);
        chatMessages.add(msg);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);

        // Ensure accessibility sees the new content
        chatRecyclerView.announceForAccessibility("New message from " + sender);

        if (BuildConfig.DEBUG) {
            Log.i("WK_E2E", "[APP] addTestIncomingMessage: sender=" + sender
                + " text=" + messageText.substring(0, Math.min(50, messageText.length()))
                + " chatSize=" + chatMessages.size());
        }
    }

    // ========== Task Management ==========

    private void loadTask(TutorialTask task) {
        currentTask = task;
        taskInProgress = true;
        isWaitingForUserToSendAiResponse = false;  // Reset this flag
        // ✅ NEW: Track step start time for duration calculation
        mCurrentStepStartTime = System.currentTimeMillis();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📋 Loading task: " + task.name() + " (Step " + task.getStepNumber() + ")");
        }

        // ========== NEW: Smart keyboard status check for ENABLE_KEYBOARD step ==========
        if (task == TutorialTask.ENABLE_KEYBOARD) {
            boolean isEnabled = isWittyKeysEnabled();
            boolean isDefault = isWittyKeysDefaultInputMethod();

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "🔍 Keyboard status check - Enabled: " + isEnabled + ", Default: " + isDefault);
            }

            if (isEnabled && isDefault) {
                // Keyboard is already enabled AND selected - skip directly to step 2
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "✅ Keyboard already set up - skipping to AI_CHAT_TASK");
                }
                skipEnableKeyboardStep();
                return;
            } else if (isEnabled && !isDefault) {
                // Keyboard is enabled but not selected - show IME picker flow
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "🔄 Keyboard enabled but not default - showing IME picker flow");
                }
                isWaitingForKeyboard = true;
                loadTaskWithImePicker(task);
                return;
            }
            // If not enabled, continue with normal flow (show Enable button)
        }
        // ========== END: Smart keyboard status check ==========

        // Update header with animation
        updateHeader(task);

        // Update step indicator
        stepIndicator.setProgress(task.getStepNumber(), completedSteps);

        // Show skip button only after step 1 is complete
        skipButton.setVisibility(completedSteps >= 1 && completedSteps < 6 ? View.VISIBLE : View.GONE);

        // Clear previous messages and show new task messages
        clearChatAndShowTask(task);

        // Show/hide input area based on task
        updateInputVisibility(task);

        String trackingId = EventHelpers.getTrackingId(this, getUserId());
        EventHelpers.triggerTutorialStepStarted(
                trackingId,
                task.name(),
                mFirebaseAnalytics
        );

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 ANALYTICS: tutorial_step_started");
            Log.d(TAG, "   Step: " + task.name());
            Log.d(TAG, "   TrackingID: " + trackingId);
        }
    }

    /**
     * Skip the ENABLE_KEYBOARD step when keyboard is already enabled and selected.
     * Marks the task as complete and proceeds directly to AI_CHAT_TASK.
     */
    private void skipEnableKeyboardStep() {
        // Mark step 1 as completed
        completedSteps = 1;
        tutorialManager.markTaskCompleted(TutorialTask.ENABLE_KEYBOARD);

        // Track activation milestone
        if (activationManager != null) {
            activationManager.trackKeyboardEnabled(getUserId());
        }

        // Fire analytics
        String trackingId = EventHelpers.getTrackingId(this, getUserId());
        EventHelpers.triggerTutorialStepCompleted(trackingId, TutorialTask.ENABLE_KEYBOARD.name(), mFirebaseAnalytics);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 ANALYTICS: tutorial_step_completed (auto-skipped)");
            Log.d(TAG, "   Step: ENABLE_KEYBOARD");
        }

        // Load the next task (AI_CHAT_TASK)
        TutorialTask nextTask = TutorialTask.ENABLE_KEYBOARD.getNextTask();
        if (nextTask != null) {
            tutorialManager.setCurrentTask(nextTask);
            loadTask(nextTask);
        }
    }

    /**
     * Load ENABLE_KEYBOARD task with IME picker flow.
     * Shows different messages and the IME picker instead of Enable button.
     */
    private void loadTaskWithImePicker(TutorialTask task) {
        // Update header
        updateHeader(task);

        // Update step indicator
        stepIndicator.setProgress(task.getStepNumber(), completedSteps);

        // Hide skip button for step 1
        skipButton.setVisibility(View.GONE);

        // Hide input area
        inputContainer.setVisibility(View.GONE);

        // Clear and show custom messages for IME picker flow
        chatRecyclerView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    chatMessages.clear();
                    chatAdapter.notifyDataSetChanged();
                    chatRecyclerView.setAlpha(1f);

                    // Add messages for IME picker flow
                    String[] messages = new String[]{
                            "Hey! 👋 Welcome to WittyKeys!",
                            "Great news! WittyKeys is already enabled on your device! ✅",
                            "Now just select it as your keyboard to get started."
                    };

                    for (int i = 0; i < messages.length; i++) {
                        final int index = i;
                        mainHandler.postDelayed(() -> {
                            TutorialChatMessage msg = new TutorialChatMessage(
                                    messages[index],
                                    TutorialChatMessage.MessageType.BOT_MESSAGE
                            );
                            chatMessages.add(msg);
                            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                            chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
                        }, i * 600L);
                    }

                    // Add "Choose WittyKeys Keyboard" button
                    mainHandler.postDelayed(() -> {
                        TutorialChatMessage btnMsg = new TutorialChatMessage(
                                "Choose WittyKeys Keyboard",
                                "choose_keyboard"
                        );
                        chatMessages.add(btnMsg);
                        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                        chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
                    }, messages.length * 600L + 400L);
                })
                .start();

        // Fire analytics
        String trackingId = EventHelpers.getTrackingId(this, getUserId());
        EventHelpers.triggerTutorialStepStarted(trackingId, task.name(), mFirebaseAnalytics);
    }

    private void updateHeader(TutorialTask task) {
        // Fade out current content
        AnimatorSet fadeOut = new AnimatorSet();
        fadeOut.playTogether(
                ObjectAnimator.ofFloat(headerIconContainer, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(headerStepText, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(headerTaskName, "alpha", 1f, 0f)
        );
        fadeOut.setDuration(150);

        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Update content
                headerIcon.setImageResource(getIconForTask(task));
                headerStepText.setText("Step " + task.getStepNumber() + " of " + TOTAL_STEPS);
                headerTaskName.setText(task.getTitle());

                // Fade in
                AnimatorSet fadeIn = new AnimatorSet();
                fadeIn.playTogether(
                        ObjectAnimator.ofFloat(headerIconContainer, "alpha", 0f, 1f),
                        ObjectAnimator.ofFloat(headerStepText, "alpha", 0f, 1f),
                        ObjectAnimator.ofFloat(headerTaskName, "alpha", 0f, 1f)
                );
                fadeIn.setDuration(200);
                fadeIn.start();
            }
        });

        fadeOut.start();
    }

    private void clearChatAndShowTask(TutorialTask task) {
        // Fade out existing messages
        chatRecyclerView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    // Clear messages
                    chatMessages.clear();
                    chatAdapter.notifyDataSetChanged();

                    // Fade in
                    chatRecyclerView.setAlpha(1f);

                    // Add bot messages for this task with staggered timing
                    addBotMessagesForTask(task);
                })
                .start();
    }

    private void addBotMessagesForTask(TutorialTask task) {
        String[] messages = getBotMessagesForTask(task);

        for (int i = 0; i < messages.length; i++) {
            final int index = i;
            mainHandler.postDelayed(() -> {
                TutorialChatMessage msg = new TutorialChatMessage(
                        messages[index],
                        TutorialChatMessage.MessageType.BOT_MESSAGE
                );
                chatMessages.add(msg);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }, i * 600L);
        }

        // Add action button if needed (ENABLE_KEYBOARD and TOKEN_EXPLANATION)
        String actionButton = getActionButtonForTask(task);
        String actionId = getActionIdForTask(task);

        if (actionButton != null && actionId != null) {
            mainHandler.postDelayed(() -> {
                TutorialChatMessage btnMsg = new TutorialChatMessage(actionButton, actionId);
                chatMessages.add(btnMsg);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }, messages.length * 600L + 400L);
        }

        // IMPROVEMENT #3: Add mock conversation for READ_SCREEN_TASK
        if (task == TutorialTask.READ_SCREEN_TASK) {
            addMockConversationForReadScreen(messages.length);
        }

        String sampleMessage = getSampleMessageForTask(task);
        if (sampleMessage != null) {
            long delayAfterMessages = messages.length * 600L + 800L;
            mainHandler.postDelayed(() -> {
                if (messageInput != null && inputContainer.getVisibility() == View.VISIBLE) {
                    messageInput.setText(sampleMessage);
                    messageInput.setSelection(sampleMessage.length());

                    // Show keyboard automatically
                    messageInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT);
                    }

                    // Trigger highlight after keyboard is shown (with delay for keyboard to appear)
                    mainHandler.postDelayed(() -> {
                        notifyKeyboardShownForTutorial();
                    }, 500);

                    if (DebugConfig.isDebugMode) {
                        Log.d(TAG, "📝 Pre-filled sample message and showing keyboard: " + sampleMessage);
                    }
                }
            }, delayAfterMessages);
        }
    }

    /**
     * Notify that keyboard is shown during tutorial - triggers CTA highlight via broadcast
     */
    private void notifyKeyboardShownForTutorial() {
        if (tutorialManager == null || !taskInProgress) return;

        String buttonToHighlight = null;
        switch (currentTask) {
            case AI_CHAT_TASK:
                buttonToHighlight = "AI_CHAT";
                break;
            case READ_SCREEN_TASK:
                buttonToHighlight = "READ_SCREEN";
                break;
            case TONALITY_TASK:
                buttonToHighlight = "TONALITY";
                break;
            case GRAMMAR_TASK:
                buttonToHighlight = "GRAMMAR";
                break;
        }

        if (buttonToHighlight != null) {
            // Send broadcast to keyboard to highlight the button
            Intent intent = new Intent("com.wittykeys.tutorial.HIGHLIGHT_BUTTON");
            intent.putExtra("button_type", buttonToHighlight);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "📡 Sent highlight request for: " + buttonToHighlight);
            }
        }
    }

    /**
     * IMPROVEMENT #3: Add mock conversation for Read Screen task
     * Shows a fake conversation that the "Read Screen" feature would analyze
     */
    private void addMockConversationForReadScreen(int botMessageCount) {
        String[] mockConversation = getMockConversationForReadScreen();
        long baseDelay = botMessageCount * 600L + 600L;

        for (int i = 0; i < mockConversation.length; i++) {
            final int index = i;
            // Alternate between "other person" (bot style) and "context user" messages
            // For mock: even = other person asking, odd = context showing what user might say
            final boolean isOtherPerson = (i % 2 == 0);

            mainHandler.postDelayed(() -> {
                // Use BOT_MESSAGE for the "other person" in the mock conversation
                // Use USER_MESSAGE style for context of what "you" said before
                TutorialChatMessage.MessageType type = isOtherPerson
                        ? TutorialChatMessage.MessageType.BOT_MESSAGE
                        : TutorialChatMessage.MessageType.USER_MESSAGE;

                TutorialChatMessage msg = new TutorialChatMessage(mockConversation[index], type);
                chatMessages.add(msg);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }, baseDelay + (i * 500L));
        }

        // Add instruction after mock conversation
        long instructionDelay = baseDelay + (mockConversation.length * 500L) + 400L;
        mainHandler.postDelayed(() -> {
            TutorialChatMessage instruction = new TutorialChatMessage(
                    "👆 Now try it! Tap Read Screen → Respond → pick any reply style!",
                    TutorialChatMessage.MessageType.BOT_MESSAGE
            );
            chatMessages.add(instruction);
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
            chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
        }, instructionDelay);
    }

    /**
     * Get mock conversation messages for Read Screen demo
     */
    private String[] getMockConversationForReadScreen() {
        return new String[]{
                "Hey! Want to grab dinner tonight? 🍕",
                "Sure! What do you have in mind?",
                "I'm craving Italian food. Maybe somewhere downtown?",
                "Sounds perfect! I love Italian!",
                "Can you suggest a few good Italian restaurants in downtown?"
        };
    }

    /**
     * IMPROVEMENT #1: Get sample message to pre-fill in EditText for each task
     */
    private String getSampleMessageForTask(TutorialTask task) {
        switch (task) {
            case AI_CHAT_TASK:
                return "Write a birthday wish for my best friend";
            case TONALITY_TASK:
                return "hey can u send me the report asap its urgent";
            case GRAMMAR_TASK:
                return "i dont no how too fix this their are to many erors";
            case READ_SCREEN_TASK:
                // No pre-fill for Read Screen - user taps Read Screen button
                return null;
            case ENABLE_KEYBOARD:
            case TOKEN_EXPLANATION:
            default:
                return null;
        }
    }

    private void updateInputVisibility(TutorialTask task) {
        // Show input only for tasks that require typing/sending
        boolean showInput = task == TutorialTask.AI_CHAT_TASK ||
                task == TutorialTask.TONALITY_TASK ||
                task == TutorialTask.GRAMMAR_TASK ||
                task == TutorialTask.READ_SCREEN_TASK;

        inputContainer.setVisibility(showInput ? View.VISIBLE : View.GONE);

        // Update chat RecyclerView padding based on input visibility
        updateChatRecyclerViewPadding();

        // Set hint based on task and clear previous text
        if (showInput) {
            messageInput.setHint(getInputHintForTask(task));
            messageInput.setText(""); // Clear - will be pre-filled by addBotMessagesForTask
        }
    }

    // ========== Bot Messages ==========

    private String[] getBotMessagesForTask(TutorialTask task) {
        switch (task) {
            case ENABLE_KEYBOARD:
                return new String[]{
                        "Hey! 👋 Welcome to WittyKeys!",
                        "I'm here to help you set up your new AI keyboard.",
                        "First, let's enable WittyKeys in your device settings."
                };
            case AI_CHAT_TASK:
                return new String[]{
                        "Awesome! 🎉 WittyKeys is ready!",
                        "Now let's try the AI Chat feature.",
                        "I've added a sample message for you. Tap the AI Chat button in your keyboard to transform it! ✨"
                };
            case READ_SCREEN_TASK:
                return new String[]{
                        "Great job! 💪",
                        "Now let's try Read Screen - it reads conversations and suggests smart replies!",
                        "📱 First time: Grant permission when asked",
                        "✨ Steps:",
                        "① Tap Read Screen button",
                        "② Always choose 'Entire Screen' if asked while capturing screenshot",
                        "③ Tap Respond → Pick a reply style",
                        "Practice with this conversation:"
                };

            case TONALITY_TASK:
                return new String[]{
                        "You're doing amazing! 🌟",
                        "Let's try changing the tone of messages.",
                        "I've added a casual message. Tap the Tonality button to make it more professional!"
                };
            case GRAMMAR_TASK:
                return new String[]{
                        "Almost there! 📝",
                        "Now let's fix some grammar mistakes.",
                        "I've added a message with errors. Tap Grammar Correction to fix it instantly!"
                };
            case TOKEN_EXPLANATION:
                return new String[]{
                        "You're a pro now! 🏆",
                        "One last thing - you have 2,000 FREE tokens to use!",
                        "Each AI action uses some tokens. Simple tasks use ~50, complex ones ~100.",
                        "That's enough for 20-40 AI actions! Ready to start?"
                };
            default:
                return new String[]{"Let's continue..."};
        }
    }

    private String getActionButtonForTask(TutorialTask task) {
        switch (task) {
            case ENABLE_KEYBOARD:
                return "Enable WittyKeys Keyboard";
            case TOKEN_EXPLANATION:
                return "Start Using WittyKeys! 🚀";
            default:
                return null;
        }
    }

    private String getActionIdForTask(TutorialTask task) {
        switch (task) {
            case ENABLE_KEYBOARD:
                return "enable_keyboard";
            case TOKEN_EXPLANATION:
                return "finish_tutorial";
            default:
                return null;
        }
    }

    private String getInputHintForTask(TutorialTask task) {
        switch (task) {
            case AI_CHAT_TASK:
                return "Type a message to transform...";
            case READ_SCREEN_TASK:
                return "AI response will appear here...";
            case TONALITY_TASK:
                return "Type a message to change tone...";
            case GRAMMAR_TASK:
                return "Type a message with mistakes...";
            default:
                return "Type a message...";
        }
    }

    private int getIconForTask(TutorialTask task) {
        switch (task) {
            case ENABLE_KEYBOARD:
                return R.drawable.gen_ai_icon;
            case AI_CHAT_TASK:
                return R.drawable.gen_ai_icon;
            case READ_SCREEN_TASK:
                return R.drawable.continue_v2_icon;
            case TONALITY_TASK:
                return R.drawable.tone_v2_icon;
            case GRAMMAR_TASK:
                return R.drawable.grammar_v2_icon;
            case TOKEN_EXPLANATION:
                return R.drawable.gen_ai_icon;
            default:
                return R.drawable.gen_ai_icon;
        }
    }

    // ========== User Actions ==========

    /**
     * IMPROVEMENT #2: Modified sendMessage to handle the proper flow
     * When user sends the AI-generated response, complete the task
     */
    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(messageInput.getWindowToken(), 0);
        }

        // ✅ NEW: Track first message milestone for activation scoring
        // This handles the case where user sends a pre-filled message in the tutorial
        if (activationManager != null) {
            activationManager.trackFirstMessage(null);  // null = use device ID
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "✏️ Activation: First message tracked from tutorial sendMessage()");
            }
        }

        // Add user message to chat
        TutorialChatMessage userMsg = new TutorialChatMessage(
                text,
                TutorialChatMessage.MessageType.USER_MESSAGE
        );
        chatMessages.add(userMsg);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);

        // Clear input
        messageInput.setText("");

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "💬 User sent: " + text);
            Log.d(TAG, "📌 isWaitingForUserToSendAiResponse: " + isWaitingForUserToSendAiResponse);
        }

        // IMPROVEMENT #2: If we were waiting for user to send AI response, complete the task
        if (isWaitingForUserToSendAiResponse) {
            isWaitingForUserToSendAiResponse = false;
            taskInProgress = false;
            tutorialManager.markTaskCompleted(currentTask);

            // Show congratulation and proceed to next task
            mainHandler.postDelayed(this::showTaskCompletionCelebration, 500);

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "✅ Task completed after user sent AI response: " + currentTask.name());
            }
        } else {
            // Normal flow - show bot response prompting to use AI feature
            mainHandler.postDelayed(() -> {
                String botResponse = getBotResponseForTask(currentTask);
                TutorialChatMessage botMsg = new TutorialChatMessage(
                        botResponse,
                        TutorialChatMessage.MessageType.BOT_MESSAGE
                );
                chatMessages.add(botMsg);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }, 800);
        }
    }

    private String getBotResponseForTask(TutorialTask task) {
        switch (task) {
            case AI_CHAT_TASK:
                return "Perfect! Now tap the AI Chat button (🤖) in your keyboard to transform your message!";
            case READ_SCREEN_TASK:
                return "Great! Now tap Read Screen (📖) to get smart suggestions!";
            case TONALITY_TASK:
                return "Nice! Tap the Tonality button (🎭) to change the tone!";
            case GRAMMAR_TASK:
                return "Good! Now tap Grammar Correction (✏️) to fix any errors!";
            default:
                return "Great! Keep going!";
        }
    }

    private void handleActionClick(String actionId) {
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎯 Action clicked: " + actionId);
        }

        switch (actionId) {
            case "enable_keyboard":
                isWaitingForKeyboard = true;
                openKeyboardSettings();
                break;
            case "choose_keyboard":
                // Keyboard is already enabled, just show the IME picker
                isWaitingForKeyboard = true;
                showInputMethodPicker();
                break;
            case "finish_tutorial":
                completeTutorial();
                break;
        }
    }

    // ========== Task Completion ==========

    private void showTaskCompletionCelebration() {
        // Add celebration message
        String celebration = getCelebrationMessage();
        TutorialChatMessage celebMsg = new TutorialChatMessage(
                celebration,
                TutorialChatMessage.MessageType.BOT_CELEBRATION
        );
        chatMessages.add(celebMsg);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);

        // ✅ NEW: Calculate time spent on this step
        long timeSpentMs = System.currentTimeMillis() - mCurrentStepStartTime;

        // ✅ NEW: Track step completion with timing
        String trackingId = EventHelpers.getTrackingId(this, getUserId());
        int stepNumber = currentTask.getStepNumber();
        String stepName = currentTask.name();

        // Fire onboarding step completed event (with timing)
        EventHelpers.triggerOnboardingStepCompleted(
                trackingId,
                stepNumber,
                stepName,
                timeSpentMs,
                mFirebaseAnalytics
        );

        // Fire tutorial step completed event
        EventHelpers.triggerTutorialStepCompleted(
                trackingId,
                stepName,
                mFirebaseAnalytics
        );

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "📊 ANALYTICS: onboarding_step_completed & tutorial_step_completed");
            Log.d(TAG, "   Step: " + stepName + " (#" + stepNumber + ")");
            Log.d(TAG, "   TimeSpent: " + timeSpentMs + "ms (" + (timeSpentMs/1000) + "s)");
            Log.d(TAG, "   TrackingID: " + trackingId);
        }
        // ✅ Track keyboard enabled milestone for activation scoring
        if (currentTask == TutorialTask.ENABLE_KEYBOARD) {
            if (activationManager != null) {
                activationManager.trackKeyboardEnabled(null);
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "🔑 Activation: Keyboard enabled milestone tracked");
                    Log.d(TAG, "📊 " + activationManager.getActivationStatusDebug());
                }
            }

            // ✅ NEW: Also track keyboard_set event
            EventHelpers.triggerKeyboardSetEvent(trackingId, mFirebaseAnalytics);

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "📊 ANALYTICS: keyboard_set event fired");
            }
        }

        // Update completed steps
        completedSteps = currentTask.getStepNumber();
        stepIndicator.animateToStep(completedSteps);

        // Proceed to next task after delay
        mainHandler.postDelayed(() -> {
            // Check if returning user completed ENABLE_KEYBOARD - go directly to Home
            if (isReturningUserSetup && currentTask == TutorialTask.ENABLE_KEYBOARD) {
                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "Returning user completed keyboard setup - navigating to HomeActivity");
                }
                Intent intent = new Intent(InteractiveTutorialActivity.this, HomeActivity.class);
                startActivity(intent);
                finish();
                return;
            }

            // Normal tutorial flow - continue to next task
            TutorialTask nextTask = currentTask.getNextTask();
            if (nextTask != null) {
                tutorialManager.setCurrentTask(nextTask);
                loadTask(nextTask);
            } else {
                completeTutorial();
            }
        }, 1200);
    }

    private String getCelebrationMessage() {
        String[] celebrations = {
                "Awesome! 🎉 You've enabled WittyKeys!",
                "Great job! 💪 AI Chat mastered!",
                "You're amazing! 🌟 Read Screen complete!",
                "Perfect! ✨ Tonality understood!",
                "Excellent! 🏆 Grammar correction done!",
                "Well done! 🎊 You're all set!"
        };
        int index = Math.min(currentTask.getStepNumber() - 1, celebrations.length - 1);
        return celebrations[index];
    }

    // ========== Skip & Complete ==========

    private void showSkipConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Skip Tutorial?")
                .setMessage("You'll miss learning about AI-powered writing, grammar correction, translation.")
                .setPositiveButton("Skip Anyway", (dialog, which) -> {
                    if (DebugConfig.isDebugMode) {
                        Log.d(TAG, "⏭️ User skipped tutorial");
                    }

                    // ✅ NEW: Track onboarding skipped event
                    String trackingId = EventHelpers.getTrackingId(this,getUserId());
                    EventHelpers.triggerOnboardingSkipped(
                            trackingId,
                            mStepsCompletedBeforeSkip,
                            mFirebaseAnalytics
                    );

                    if (DebugConfig.isDebugMode) {
                        Log.d(TAG, "📊 ANALYTICS: onboarding_skipped");
                        Log.d(TAG, "   StepsCompleted: " + mStepsCompletedBeforeSkip);
                        Log.d(TAG, "   TrackingID: " + trackingId);
                    }

                    completeTutorial();
                })
                .setNegativeButton("Continue", null)
                .show();
    }

    private void completeTutorial() {
        tutorialManager.endTutorialMode();

        // ✅ FIXED: Track with device ID fallback + onboarding timing
        String trackingId = EventHelpers.getTrackingId(this,getUserId());
        long totalTimeMs = System.currentTimeMillis() - mOnboardingStartTime;
        int totalSteps = completedSteps;  // May be less than TOTAL_STEPS if skipped

        // Fire onboarding completed event (with total time and steps)
        EventHelpers.triggerOnboardingCompleted(
                trackingId,
                totalSteps,
                totalTimeMs,
                mFirebaseAnalytics
        );

        // Fire tutorial completed event
        EventHelpers.triggerTutorialCompleted(
                trackingId,
                mFirebaseAnalytics
        );

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎉 Tutorial completed!");
            Log.d(TAG, "📊 ANALYTICS: onboarding_completed & tutorial_completed");
            Log.d(TAG, "   TotalSteps: " + totalSteps);
            Log.d(TAG, "   TotalTime: " + totalTimeMs + "ms (" + (totalTimeMs/1000) + "s)");
            Log.d(TAG, "   TrackingID: " + trackingId);
        }

        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ========== Keyboard Setup Helpers ==========

    private void openKeyboardSettings() {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        startActivity(intent);
    }

    private boolean isWittyKeysEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;

        List<InputMethodInfo> enabledMethods = imm.getEnabledInputMethodList();
        for (InputMethodInfo method : enabledMethods) {
            if (getPackageName().equals(method.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isWittyKeysDefaultInputMethod() {
        String currentId = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        );
        return currentId != null && currentId.contains(getPackageName());
    }

    // ========== Broadcast Receivers ==========

    private void registerTutorialReceiver() {
        tutorialReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "📡 Received: " + action);
                }

                switch (action) {
                    case TutorialManager.ACTION_AI_CHAT_CLICKED:
                    case TutorialManager.ACTION_READ_SCREEN_CLICKED:
                    case TutorialManager.ACTION_TONALITY_CLICKED:
                    case TutorialManager.ACTION_GRAMMAR_CLICKED:
                        addBotPromptForAction(action);
                        break;
                    case TutorialManager.ACTION_AI_RESPONSE_APPLIED:
                        handleAIResponseApplied();
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(TutorialManager.ACTION_AI_CHAT_CLICKED);
        filter.addAction(TutorialManager.ACTION_READ_SCREEN_CLICKED);
        filter.addAction(TutorialManager.ACTION_TONALITY_CLICKED);
        filter.addAction(TutorialManager.ACTION_GRAMMAR_CLICKED);
        filter.addAction(TutorialManager.ACTION_AI_RESPONSE_APPLIED);

        LocalBroadcastManager.getInstance(this).registerReceiver(tutorialReceiver, filter);
    }

    private void addBotPromptForAction(String action) {
        String message = "Perfect! Now select an option and tap Apply! ✨";
        TutorialChatMessage msg = new TutorialChatMessage(
                message,
                TutorialChatMessage.MessageType.BOT_MESSAGE
        );
        chatMessages.add(msg);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
    }

    /**
     * Handle AI response applied - called when user clicks Apply button.
     * Instead of immediately completing, wait for user to click send.
     * FIXED: Added guard to prevent duplicate messages.
     */
    private void handleAIResponseApplied() {
        // Guard: Don't process if not in active task
        if (!taskInProgress) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "⚠️ handleAIResponseApplied ignored - taskInProgress is false");
            }
            return;
        }

        // Guard: Prevent duplicate messages if already waiting for user to send
        if (isWaitingForUserToSendAiResponse) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "⚠️ handleAIResponseApplied ignored - already waiting for user to send");
            }
            return;
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🤖 AI response applied - waiting for user to click Send");
        }

        // Set flag FIRST to prevent duplicate calls
        isWaitingForUserToSendAiResponse = true;

        // Add bot message prompting user to send
        mainHandler.postDelayed(() -> {
            // Double-check we're still in the right state before adding message
            if (isWaitingForUserToSendAiResponse && taskInProgress) {
                TutorialChatMessage promptMsg = new TutorialChatMessage(
                        "Great! The AI response is in your text field. Now tap Send to add it to the conversation! 📤",
                        TutorialChatMessage.MessageType.BOT_MESSAGE
                );
                chatMessages.add(promptMsg);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
        }, 300);
    }

    private void registerInputMethodReceiver() {
        inputMethodReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isWaitingForKeyboard && currentTask == TutorialTask.ENABLE_KEYBOARD) {
                    if (isWittyKeysEnabled() && isWittyKeysDefaultInputMethod()) {
                        isWaitingForKeyboard = false;
                        taskInProgress = false;
                        tutorialManager.markTaskCompleted(currentTask);
                        showTaskCompletionCelebration();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(inputMethodReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(inputMethodReceiver, filter);
        }
    }

    // ========== Lifecycle ==========

    @Override
    protected void onResume() {
        super.onResume();

        if (isWaitingForKeyboard && currentTask == TutorialTask.ENABLE_KEYBOARD) {
            boolean isEnabled = isWittyKeysEnabled();
            boolean isDefault = isWittyKeysDefaultInputMethod();

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "🔍 Keyboard status - Enabled: " + isEnabled + ", Default: " + isDefault);
            }

            if (isEnabled && isDefault) {
                isWaitingForKeyboard = false;
                taskInProgress = false;
                tutorialManager.markTaskCompleted(currentTask);
                showTaskCompletionCelebration();
            } else if (isEnabled && !isDefault) {
                showInputMethodPicker();
            }
        }
    }

    private void showInputMethodPicker() {
        mainHandler.postDelayed(() -> {
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to show IME picker", e);
            }
        }, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (inputMethodReceiver != null) {
            try {
                unregisterReceiver(inputMethodReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Receiver already unregistered");
            }
        }

        if (tutorialReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(tutorialReceiver);
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎓 Tutorial Activity Destroyed");
        }
    }

    private String getUserId() {
        project.witty.keys.app.entities.User user =
                project.witty.keys.app.helpers.EncryptedPreferences.getUserLoggedInInfo();
        return user != null ? user.getId() : null;
    }
}
