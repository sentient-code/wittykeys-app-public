package project.witty.keys.app; // Replace with your actual package name

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color; // Import Color for potential testing
import android.os.Build; // Import Build for API checks
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import project.witty.keys.R; // Replace with your actual R file import
import project.witty.keys.app.helpers.FixedSpeedScroller; // Replace with your actual path
import project.witty.keys.app.helpers.NotchHandler;

public class IntroActivity extends BaseActivity { // Ensure BaseActivity exists

    private ViewPager viewPager;
    private IntroPagerAdapter adapter;
    private Button startButton;
    private static final String TAG = IntroActivity.class.getSimpleName();
    private boolean enabled = false; // Flag for IME status

    // --- Variables for ViewPager Indicators ---
    private List<View> indicators;

    // --- Variables for Auto Scroll ---
    private Handler autoScrollHandler;
    private Runnable autoScrollRunnable;
    private static final long AUTO_SCROLL_DELAY = 5000; // 5 seconds
    private static final int SMOOTH_SCROLL_DURATION = 1000; // 1 second
    private boolean isUserScrolling = false;

    // --- ViewPager Config ---
    private static final int NUM_PAGES = 5;

    // Store original padding values to handle insets correctly
    private final AtomicInteger originalTopPadding = new AtomicInteger(-1);
    private final AtomicInteger originalBottomPadding = new AtomicInteger(-1);


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // *** 1. Enable Edge-to-Edge Display ***
        // Must be called BEFORE setContentView
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro); // Ensure this layout exists
        // --- Find Views ---
        View rootContainer = findViewById(R.id.intro_root_container); // MUST exist in XML
        if (rootContainer == null) {
            Log.e(TAG, "FATAL ERROR: R.id.intro_root_container not found in layout!");
            finish(); // Exit if root view is missing
            return;
        }

        viewPager = findViewById(R.id.intro_pager);
        startButton = findViewById(R.id.start_button);
        indicators = new ArrayList<>();
        indicators.add(findViewById(R.id.indicator_0));
        indicators.add(findViewById(R.id.indicator_1));
        indicators.add(findViewById(R.id.indicator_2));
        indicators.add(findViewById(R.id.indicator_3));
        indicators.add(findViewById(R.id.indicator_4));

        // --- Set up Adapter ---
        adapter = new IntroPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);

        // *** 2. Configure System Bar Appearance (Status + Navigation) ***
        // Handles colors and icon styles after enabling edge-to-edge
        configureSystemBars();

        // --- Inject Custom Scroller ---
        setupCustomScroller();

//        // *** 3. Apply Window Insets Listener (Padding for Content) ***
//        // Calculates padding needed to avoid overlap with styled system bars/notches
        applyWindowInsets(rootContainer);

        // --- Setup Remaining Components ---
        setupViewPagerListener();
        setupAutoScroll();
        setupStartButtonListener();
        updateIndicators(0); // Set initial indicator state
    }

    // --- Method to Configure System Bar Appearance (Combined) ---
    private void configureSystemBars() {
        Window window = getWindow();
        if (window == null) return;

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());

        // --- Status Bar Configuration ---
        // Set desired status bar color (or Color.TRANSPARENT for full blend)
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.third_app_color));
        if (insetsController != null) {
            // Set status bar icon color (false = light icons for dark background)
            insetsController.setAppearanceLightStatusBars(false);
        }

        // --- Navigation Bar Configuration ---
        int navBarColor = ContextCompat.getColor(this, R.color.intro_bg_1); // Your desired color
        boolean isNavBarLight = false; // Set true if navBarColor is light, false if dark

        // Optional: Disable contrast enforcement on API 29+ if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        // Set the navigation bar color
        window.setNavigationBarColor(navBarColor);

        // Set navigation bar icon color (requires API 27+)
        if (insetsController != null) {
            // true = dark icons (for light background), false = light icons (for dark background)
            insetsController.setAppearanceLightNavigationBars(isNavBarLight);
        }
    }


    // --- Method to Apply Window Insets for Padding ---
    private void applyWindowInsets(View viewToPad) {
        // Store original padding the first time
        if (originalTopPadding.get() == -1) {
            originalTopPadding.set(viewToPad.getPaddingTop());
            originalBottomPadding.set(viewToPad.getPaddingBottom());
        }

        ViewCompat.setOnApplyWindowInsetsListener(viewToPad, (v, windowInsets) -> {
            // Get insets for system bars (status, nav) and display cutouts (notch)
            Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            // Determine the maximum inset needed at the top
            int topInset = Math.max(systemBarInsets.top, cutoutInsets.top);
            // Determine the maximum inset needed at the bottom
            int bottomInset = Math.max(systemBarInsets.bottom, cutoutInsets.bottom);

            Log.d(TAG, "Applying Insets - Top: " + topInset + ", Bottom: " + bottomInset);

            // Apply padding to the view: Add calculated insets to the original padding
            v.setPadding(
                    v.getPaddingLeft(),
                    originalTopPadding.get() + topInset, // Add top inset
                    v.getPaddingRight(),
                    originalBottomPadding.get() + bottomInset // Add bottom inset
            );

            // Return the original insets so other views can potentially use them too
            return windowInsets;
        });

        // Request the initial application of insets
        ViewCompat.requestApplyInsets(viewToPad);
    }


    // --- Method to Inject Custom Scroller ---
    private void setupCustomScroller() {
        try {
            Field scrollerField = ViewPager.class.getDeclaredField("mScroller");
            scrollerField.setAccessible(true);
            FixedSpeedScroller scroller = new FixedSpeedScroller(this, SMOOTH_SCROLL_DURATION);
            scrollerField.set(viewPager, scroller);
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
            Log.e(TAG, "Reflection Error setting ViewPager scroller.", e);
        }
    }

    // --- Method to Set Up ViewPager Listener ---
    private void setupViewPagerListener() {
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                updateIndicators(position);
                if (!isUserScrolling && autoScrollHandler != null) {
                    stopAutoScroll();
                    startAutoScroll();
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                    isUserScrolling = true;
                    Log.d(TAG, "User dragging - stopping auto-scroll");
                    stopAutoScroll();
                } else if (state == ViewPager.SCROLL_STATE_IDLE && isUserScrolling) {
                    // Restart only if user *was* dragging and has now stopped
                    Log.d(TAG, "User stopped dragging - restarting auto-scroll");
                    isUserScrolling = false; // Reset flag first
                    startAutoScroll();
                }
            }
        });
    }

    // --- Method to Set Up Auto Scroll ---
    private void setupAutoScroll() {
        autoScrollHandler = new Handler(Looper.getMainLooper());
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (adapter == null || adapter.getCount() == 0 || isUserScrolling) return;
                int currentItem = viewPager.getCurrentItem();
                int nextItem = (currentItem + 1) % adapter.getCount();
                Log.d(TAG, "Auto-scrolling to page: " + nextItem);
                viewPager.setCurrentItem(nextItem, true);
            }
        };
    }

    // --- Method to Set Up Start Button Listener ---
    private void setupStartButtonListener() {
        startButton.setOnClickListener(v -> {
            Log.d(TAG, "Start Button clicked");
            stopAutoScroll();
            Intent intent;
            if (!enabled) { // Use the 'enabled' flag checked in onResume/onStart
                intent = new Intent(this, SetUpKeyboardActivity.class); // Ensure exists
            } else {
                intent = new Intent(this, HomeActivity.class); // Ensure exists
            }
            startActivity(intent);
            this.finish(); // Finish IntroActivity
        });
    }

    // --- Method to update indicator appearance ---
    private void updateIndicators(int position) {
        if (indicators == null || indicators.isEmpty()) return;
        int safePosition = position % indicators.size();
        Log.d(TAG, "Updating indicators for position: " + safePosition);

        for (int i = 0; i < indicators.size(); i++) {
            View indicator = indicators.get(i);
            if (indicator != null) {
                indicator.setBackgroundResource(i == safePosition ?
                        R.drawable.indicator_active_shape : // Ensure exists
                        R.drawable.indicator_inactive_shape); // Ensure exists
            }
        }
    }


    // --- Auto Scroll Control Methods ---
    private void startAutoScroll() {
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            stopAutoScroll(); // Prevent multiple timers
            autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
            Log.d(TAG, "Auto-scroll timer started");
        }
    }

    private void stopAutoScroll() {
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
            Log.d(TAG, "Auto-scroll timer stopped");
        }
    }


    // --- Lifecycle Management ---
    @Override
    protected void onResume() {
        super.onResume();
        checkImeEnabledStatus();
        if (!isUserScrolling) {
            startAutoScroll();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoScroll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoScroll(); // Clean up handler
        autoScrollHandler = null;
        autoScrollRunnable = null;
    }

    // --- IME Check Trigger ---
    @Override
    protected void onStart() {
        super.onStart();
        checkImeEnabledStatus(); // Initial check
    }

    // --- Extracted method to check IME status ---
    private void checkImeEnabledStatus() {
        try {
            enabled = isInputMethodOfThisImeEnabled();
            Log.d(TAG, "IME Enabled Check: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Exception during IME check", e);
            enabled = false; // Default to false on error
        }
    }

    // --- Method to check if this app's IME is enabled ---
    private boolean isInputMethodOfThisImeEnabled() {
        final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        final String imePackageName = getPackageName();
        if (imePackageName == null) return false;

        try {
            List<InputMethodInfo> enabledImes = imm.getEnabledInputMethodList();
            if (enabledImes == null) return false;
            for (final InputMethodInfo imi : enabledImes) {
                if (imi != null && imePackageName.equals(imi.getPackageName())) {
                    return true; // Found our IME enabled
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking enabled input methods", e);
        }
        return false; // Our IME not found in enabled list
    }
    // --- End IME Check Methods ---


    // --- Inner Static Class for Pager Adapter ---
    private static class IntroPagerAdapter extends FragmentPagerAdapter {
        public IntroPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            int actualPosition = position % NUM_PAGES;
            Log.d(TAG, "PagerAdapter creating Fragment for position: " + actualPosition);
            switch (actualPosition) {
                case 0:
                    return IntroFragment.newInstance( // Ensure IntroFragment.newInstance exists
                            "\uD83D\uDCF1 Opening ChatGPT or note apps again and again?",
                            "Ask questions, generate content, or summarize right from your keyboard—no app-switching needed.",
                            R.drawable.intro_image_1, R.color.intro_bg_1); // Ensure resources exist
                case 1:
                    return IntroFragment.newInstance(
                            "\uD83D\uDE30 Stuck with replies, awkward messages, or overthinking every word? ",
                            "WittyKeys suggests casual, flirty, funny, or professional replies in a tap.",
                            R.drawable.intro_image_3, R.color.intro_bg_2);
                case 2:
                    return IntroFragment.newInstance(
                            "\uD83D\uDCC9 Grammar mistakes or lack of fluency hurting your confidence? ",
                            "WittyKeys fixes your grammar, tone, and spelling instantly—type like a pro in any language.",
                            R.drawable.intro_image_2, R.color.intro_bg_3);
                case 3: // Fourth page
                    return IntroFragment.newInstance(
                            "\uD83E\uDDE0 Writing long messages, emails, and captions takes forever?",
                            "Let WittyKeys write smarter and faster for you—powered by AI, built right into your keyboard.",
                            R.drawable.intro_image_4, R.color.intro_bg_1);
                case 4: // Fifth page
                    return IntroFragment.newInstance(
                            "\uD83D\uDE80 You’re busy, but your words matter—every chat, caption, and email counts.",
                            "WittyKeys gives you the power of AI in every keystroke—fast, smart, and effortless.",
                            R.drawable.intro_image_5, R.color.intro_bg_2);
                default:
                    Log.e(TAG, "PagerAdapter getItem requested invalid position: " + actualPosition + ", falling back to 0");
                    return IntroFragment.newInstance(
                            "\uD83D\uDCF1 Opening ChatGPT or note apps again and again?",
                            "Ask questions, generate content, or summarize right from your keyboard—no app-switching needed.",
                            R.drawable.intro_image_1, R.color.intro_bg_1);
            }
        }

        @Override
        public int getCount() {
            return NUM_PAGES;
        }
    }
    // --- End Pager Adapter ---
}