package project.witty.keys.keyboard.EmojiKeyboard;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static project.witty.keys.app.helpers.NavigationBarHelper.applyNavigationBarPadding;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.giphy.sdk.core.models.Media;
import com.giphy.sdk.core.models.enums.MediaType;
import com.giphy.sdk.core.models.enums.RatingType;
import com.giphy.sdk.ui.Giphy;

import com.giphy.sdk.ui.pagination.GPHContent;
import com.giphy.sdk.ui.themes.GPHTheme;
import com.giphy.sdk.ui.views.GPHGridCallback;
import com.giphy.sdk.ui.views.GiphyGridView;

import java.util.List;

import project.witty.keys.R;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.NavigationBarHelper;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.keyboard.AssistantViews.ThemeColorHelper;
import project.witty.keys.keyboard.EmojiKeyboard.data.EmojiDataProvider;
import project.witty.keys.keyboard.EmojiKeyboard.data.EmojiEntry;
import project.witty.keys.keyboard.EmojiKeyboard.data.EmojiSearchIndex;
import project.witty.keys.keyboard.KeyboardActionListener;
import project.witty.keys.keyboard.KeyboardSwitcher;
import project.witty.keys.keyboard.Themeable;
import project.witty.keys.keyboard.internal.KeyboardEmojisTable;
import project.witty.keys.latin.LatinIME;
import project.witty.keys.latin.common.Constants;

public class EmojiKeyboard extends LinearLayout implements Themeable {
    private static final String TAG = "EmojiKeyboard";
    private static final long SEARCH_DEBOUNCE_MS = 150;
    private static final int CATEGORY_ANIM_MS = 200;

    public enum DisplayMode { EMOJI, GIPHY }
    private DisplayMode currentMode = DisplayMode.EMOJI;

    private FrameLayout contentContainer;
    private EmojiFunctionalView emojiFunctionalView;
    private InternalSearchView searchView;

    // View containers
    private LinearLayout emojiContentLayout;
    private EmojiCategoryView emojiCategoryView;
    private EmojisView emojisView;
    private GifCategoryView gifCategoryView;
    private TextView gifRecentsButton;

    private GiphyGridView mGiphyGridView;
    private EmojiRecentsManager emojiRecentsManager;
    private EmojiDataProvider emojiDataProvider;
    private EmojiSearchIndex emojiSearchIndex;

    // D8: Recents empty state overlay
    private LinearLayout emptyStateView;

    // Search results hint (e.g. "12 results", "Type to search...")
    private TextView searchHintView;

    // Search state
    private boolean searchActive = false;
    private String lastSearchCategory = EmojiDataProvider.CAT_RECENTS;
    private List<EmojiEntry> lastSearchResults;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    private KeyboardActionListener mKeyboardActionListener;
    private final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
    private PopupWindow mSkinTonePopup;  // Stored reference for test cleanup
    private Context mThemedContext;  // Night-mode-aware context for theme-correct colors
    private int mNavigationBarHeight = 0;
    private final String[] GIF_CATEGORIES = {"Recents", "LOL", "Happy", "Thumbs Up", "Sad", "Working"};
    private static final String[] GIF_CATEGORY_ICONS = {"\uD83D\uDD70", "\uD83D\uDE02", "\uD83D\uDE0A", "\uD83D\uDC4D", "\uD83D\uDE22", "\uD83D\uDCBC"};

    public EmojiKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
        mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        emojiRecentsManager = new EmojiRecentsManager(context);
        emojiDataProvider = EmojiDataProvider.getInstance(context);
        emojiSearchIndex = new EmojiSearchIndex(emojiDataProvider);

        // Note: Navigation bar padding is NOT applied here.
        // The system/InputView framework manages overall keyboard height including nav bar.
        // Adding extra padding here caused a visible gap at the bottom (fixed Feb 24).
        mNavigationBarHeight = 0;

        // 1. Search Bar (top, visible in EMOJI mode only)
        searchView = new InternalSearchView(context);
        int searchMarginH = getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_margin_h);
        int searchMarginV = getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_margin_v);
        int searchHeight = getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_height);
        LayoutParams searchLp = new LayoutParams(MATCH_PARENT, searchHeight);
        searchLp.setMargins(searchMarginH, searchMarginV, searchMarginH, searchMarginV);
        addView(searchView, searchLp);

        // 1b. Search Results Hint (below search bar, hidden by default)
        searchHintView = new TextView(context);
        float hintTextSize = getResources().getDimension(R.dimen.wk_emoji_search_hint_text_size);
        int hintPadH = getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_hint_padding_h);
        int hintPadTop = getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_hint_padding_top);
        searchHintView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, hintTextSize);
        searchHintView.setTextColor(getResources().getColor(R.color.wk_text3));
        searchHintView.setTypeface(null, android.graphics.Typeface.BOLD);
        searchHintView.setPadding(hintPadH, hintPadTop, hintPadH, 0);
        searchHintView.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        searchHintView.setVisibility(GONE);
        addView(searchHintView, new LayoutParams(MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // 2. Category Views (below search bar)
        emojiCategoryView = new EmojiCategoryView(context, null);
        gifCategoryView = new GifCategoryView(context, null);
        addView(emojiCategoryView);
        addView(gifCategoryView);

        // 3. Content Container
        contentContainer = new FrameLayout(context);
        addView(contentContainer, new LayoutParams(MATCH_PARENT, 0, 1.0f));

        // 4. Functional View
        emojiFunctionalView = new EmojiFunctionalView(context, null);
        addView(emojiFunctionalView, new LayoutParams(MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // --- Setup Content Views ---
        setupEmojiLayout();
        setupCategoryButtons();
        applyNavigationBarPadding();

        setupListeners();
        setupSearchListeners();
        switchToMode(DisplayMode.EMOJI);
        // Note: don't call onThemeChanged(getContext()) here — the inflation context
        // may not have the correct night-mode flag. KeyboardSwitcher.onCreateInputView()
        // calls onThemeChanged(mViewThemeContext) with the proper night-aware context.
    }

    // ===== Navigation bar padding =====

    private void applyNavigationBarPadding() {
        if (emojiFunctionalView != null && mNavigationBarHeight > 0) {
            int existingPaddingLeft = emojiFunctionalView.getPaddingLeft();
            int existingPaddingTop = emojiFunctionalView.getPaddingTop();
            int existingPaddingRight = emojiFunctionalView.getPaddingRight();
            int existingPaddingBottom = emojiFunctionalView.getPaddingBottom();

            emojiFunctionalView.setPadding(
                    existingPaddingLeft,
                    existingPaddingTop,
                    existingPaddingRight,
                    existingPaddingBottom + mNavigationBarHeight
            );
        }
    }

    public void updateNavigationBarPadding() {
        int newNavBarHeight = NavigationBarHelper.getSafeBottomPadding(this);
        if (newNavBarHeight != mNavigationBarHeight) {
            mNavigationBarHeight = newNavBarHeight;
            applyNavigationBarPadding();
        }
    }

    // ===== Setup =====

    private void setupEmojiLayout() {
        emojiContentLayout = new LinearLayout(getContext());
        emojiContentLayout.setOrientation(VERTICAL);
        emojisView = new EmojisView(getContext(), null);
        emojiContentLayout.addView(emojisView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        // D2: Skin tone long-press — show popup if emoji supports modifiers
        emojisView.setOnEmojiLongPressListener((anchorView, emoji) -> {
            if (SkinTonePopup.supportsSkinTones(emoji)) {
                SkinTonePopup.show(anchorView, emoji, variant -> onEmojiSelected(variant));
            }
        });
    }

    private void setupCategoryButtons() {
        // Emoji Categories from EmojiDataProvider (10 categories including Recents)
        String[] allCategories = emojiDataProvider.getCategoryNames();
        for (String category : allCategories) {
            emojiCategoryView.addCategoryButton(category, v -> {
                if (searchActive) deactivateSearch();
                hideRecentsEmptyState();
                showEmojisForCategory(category);
                lastSearchCategory = category;
            });
        }

        // GIF Categories (with emoji icon prefixes)
        for (int i = 0; i < GIF_CATEGORIES.length; i++) {
            final String category = GIF_CATEGORIES[i];
            String label = GIF_CATEGORY_ICONS[i] + " " + category;
            TextView button = gifCategoryView.addCategoryButton(label, v -> showGifsForCategory(category));
            button.setTag(category); // Override tag for selectCategory matching
            if (category.equals("Recents")) {
                gifRecentsButton = button;
            }
        }
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        this.mKeyboardActionListener = listener;
    }

    /** Get the search view for InternalInputTarget routing. */
    public InternalSearchView getSearchView() {
        return searchView;
    }

    /** Is the emoji search bar currently active? */
    public boolean isSearchActive() {
        return searchView != null && searchView.isActive();
    }

    private void setupListeners() {
        emojiFunctionalView.setSwitchKeyboardListener(v -> switchToAlphabetKeyboard());
        emojiFunctionalView.setDeleteListener(v -> deleteCharacter());
        emojiFunctionalView.setEmojiButtonListener(v -> switchToMode(DisplayMode.EMOJI));
        emojiFunctionalView.setGifButtonListener(v -> switchToMode(DisplayMode.GIPHY));
    }

    // ===== C2: Search activation / deactivation =====

    private void setupSearchListeners() {
        // C2: Activation — tap on search bar
        searchView.setOnActivateListener(v -> activateSearch());

        // C2: Deactivation — clear button or empty backspace
        searchView.setOnDeactivateListener(this::deactivateSearch);

        // C3: Real-time search filtering with debounce
        searchView.setOnTextChangedListener(text -> {
            if (text.length() < 2) {
                showSearchHint("Type 2+ characters to search");
                lastSearchResults = null;
                return;
            }
            searchHandler.removeCallbacksAndMessages(null);
            searchHandler.postDelayed(() -> {
                List<EmojiEntry> results = emojiSearchIndex.search(text);
                lastSearchResults = results;
                showSearchResults(results, text);
            }, SEARCH_DEBOUNCE_MS);
        });

        // C6: Enter key commits first result
        searchView.setOnEnterListener(() -> {
            if (lastSearchResults != null && !lastSearchResults.isEmpty()) {
                String firstEmoji = lastSearchResults.get(0).emojiChar;
                onEmojiSelected(firstEmoji);
                deactivateSearch();
            }
        });
    }

    private void activateSearch() {
        if (searchActive) return;
        searchActive = true;

        // Hide empty state overlay if showing
        hideRecentsEmptyState();

        // Hide category bar and functional bar — only search bar + grid visible
        emojiCategoryView.setVisibility(GONE);
        emojiFunctionalView.setVisibility(GONE);

        // Switch grid to horizontal single-row mode for search results
        emojisView.setHorizontalMode(true);

        // Tell KeyboardSwitcher to show main keyboard below us for typing
        switcher.enterEmojiSearchMode();

        // Show search hint
        showSearchHint("Type to search...");

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "Search activated — main keyboard shown for typing");
        }
    }

    private void deactivateSearch() {
        if (!searchActive) return;
        searchActive = false;
        searchHandler.removeCallbacksAndMessages(null);
        lastSearchResults = null;

        // Ensure InternalSearchView is deactivated
        if (searchView.isActive()) {
            searchView.deactivate();
        }

        // Hide search hint and restore grid mode
        searchHintView.setVisibility(GONE);
        emojisView.setHorizontalMode(false);

        // Restore category bar and functional bar
        emojiCategoryView.setVisibility(VISIBLE);
        emojiCategoryView.setTranslationY(0f);
        emojiCategoryView.setAlpha(1f);
        emojiFunctionalView.setVisibility(VISIBLE);

        // Tell KeyboardSwitcher to exit search mode — restore full emoji keyboard
        switcher.exitEmojiSearchMode();

        // Return to last active category (or Recents)
        showEmojisForCategory(lastSearchCategory);
        emojiCategoryView.selectCategory(lastSearchCategory);

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "Search deactivated, returned to: " + lastSearchCategory);
        }
    }

    /**
     * Called when the emoji keyboard becomes visible again (e.g. toggled back on).
     * Resets any stale search state so the user sees the normal emoji grid.
     */
    public void onShow() {
        if (searchActive) {
            deactivateSearch();
        }
    }

    // ===== C3-C4: Search results display =====

    private void showSearchResults(List<EmojiEntry> results, String query) {
        if (results.isEmpty()) {
            showSearchHint("No emoji found for '" + query + "'");
            return;
        }

        // Update results hint
        searchHintView.setText(results.size() + " results");
        searchHintView.setVisibility(VISIBLE);

        // Convert to String[] for EmojisView
        String[] emojiChars = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            emojiChars[i] = results.get(i).emojiChar;
        }

        // D4: Quick fade transition for search results (50ms out, 100ms in)
        emojisView.animate()
                .alpha(0f)
                .setDuration(50)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        emojisView.setEmojis(emojiChars, v -> {
                            String emoji = ((android.widget.TextView) v).getText().toString();
                            // Deactivate search FIRST so InternalSearchView is no longer
                            // the active input target — otherwise onTextInput routes the
                            // emoji into the search bar instead of the real InputConnection
                            lastSearchCategory = EmojiDataProvider.CAT_RECENTS;
                            switchToAlphabetKeyboard();
                            // Now commit the emoji to the actual editor
                            onEmojiSelected(emoji);
                        });
                        emojisView.animate()
                                .alpha(1f)
                                .setDuration(100)
                                .setListener(null)
                                .start();
                    }
                })
                .start();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "Search results: " + results.size() + " for '" + query + "'");
        }
    }

    private void showSearchHint(String hint) {
        // Show hint text and clear the grid
        searchHintView.setText(hint);
        searchHintView.setVisibility(VISIBLE);
        emojisView.setEmojis(new String[0], null);
    }

    // ===== Mode switching =====

    private void switchToMode(DisplayMode mode) {
        if (currentMode == mode && contentContainer.getChildCount() > 0) return;

        // C7: Deactivate search when switching modes
        if (searchActive) deactivateSearch();

        currentMode = mode;
        contentContainer.removeAllViews();
        if (mode == DisplayMode.EMOJI) {
            emojiCategoryView.setVisibility(VISIBLE);
            emojiCategoryView.setAlpha(1f);
            emojiCategoryView.setTranslationY(0f);
            gifCategoryView.setVisibility(GONE);
            if (searchView != null) searchView.setVisibility(VISIBLE);
            contentContainer.addView(emojiContentLayout);
            showEmojisForCategory(EmojiDataProvider.CAT_RECENTS);
            emojiCategoryView.selectCategory(EmojiDataProvider.CAT_RECENTS);
            emojiFunctionalView.setMode(EmojiFunctionalView.Mode.EMOJI);
        } else {
            emojiCategoryView.setVisibility(GONE);
            gifCategoryView.setVisibility(VISIBLE);
            if (searchView != null) searchView.setVisibility(GONE);
            gifRecentsButton.setVisibility(VISIBLE);
            if (Giphy.INSTANCE.getRecents().getCount() > 0) {
                showGifsForCategory("Recents");
                gifCategoryView.selectCategory("Recents");
            } else {
                showGifsForCategory(GIF_CATEGORIES[1]);
                gifCategoryView.selectCategory(GIF_CATEGORIES[1]);
            }
            if (mGiphyGridView == null) {
                mGiphyGridView = new GiphyGridView(getContext());
                int gifColumns = getResources().getInteger(R.integer.wk_gif_grid_columns);
                mGiphyGridView.setSpanCount(gifColumns);
                mGiphyGridView.setTheme(resolveGiphyTheme());
                mGiphyGridView.setCallback(new GPHGridCallback() {
                    @Override public void contentDidUpdate(int resultCount) { }
                    @Override public void didSelectMedia(@NonNull Media media) {
                        if (mKeyboardActionListener instanceof LatinIME) {
                            ((LatinIME) mKeyboardActionListener).onGiphyMediaSelected(media);
                        }
                    }
                });
            }
            contentContainer.addView(mGiphyGridView, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            emojiFunctionalView.setMode(EmojiFunctionalView.Mode.GIF);
        }
    }

    // ===== Emoji/GIF display =====

    private void showEmojisForCategory(String category) {
        // Always hide empty state overlay — showRecentsEmptyState() will re-show if needed
        hideRecentsEmptyState();

        String[] emojis;
        if (category.equals(EmojiDataProvider.CAT_RECENTS)) {
            List<String> recentList = emojiRecentsManager.getRecents();
            emojis = recentList.toArray(new String[0]);
            // D8: Recents empty state
            if (emojis.length == 0) {
                showRecentsEmptyState();
                return;
            }
        } else {
            emojis = emojiDataProvider.getEmojisForCategory(category);
            if (emojis.length == 0) {
                emojis = KeyboardEmojisTable.getEmojisForCategory(category);
            }
        }

        // D3: Cross-fade transition — fade out (100ms), swap data, fade in (150ms)
        final String[] finalEmojis = emojis;
        emojisView.animate()
                .alpha(0f)
                .setDuration(100)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        emojisView.setEmojis(finalEmojis,
                                v -> onEmojiSelected(((android.widget.TextView) v).getText().toString()));
                        emojisView.scrollToPosition(0);
                        emojisView.animate()
                                .alpha(1f)
                                .setDuration(150)
                                .setListener(null)
                                .start();
                    }
                })
                .start();
    }

    // D8: Show centered empty state when Recents has no emojis
    private void showRecentsEmptyState() {
        // Clear grid and show hint via a single invisible-emoji trick:
        // We'll overlay a message. Use emojisView's parent.
        emojisView.setEmojis(new String[0], null);

        // Remove any existing empty state view
        if (emptyStateView != null && emptyStateView.getParent() != null) {
            ((android.view.ViewGroup) emptyStateView.getParent()).removeView(emptyStateView);
        }

        // Create centered empty state — use themed context for correct theme colors
        Context colorCtx = mThemedContext != null ? mThemedContext : getContext();
        if (emptyStateView == null) {
            emptyStateView = new LinearLayout(getContext());
            emptyStateView.setOrientation(LinearLayout.VERTICAL);
            emptyStateView.setGravity(android.view.Gravity.CENTER);

            TextView line1 = new TextView(getContext());
            line1.setText("Your recent emojis will appear here");
            line1.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            line1.setTextColor(colorCtx.getResources().getColor(R.color.wk_text3));
            line1.setGravity(android.view.Gravity.CENTER);
            emptyStateView.addView(line1);

            TextView line2 = new TextView(getContext());
            line2.setText("Try searching for an emoji above");
            line2.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            line2.setTextColor(colorCtx.getResources().getColor(R.color.wk_text3));
            line2.setGravity(android.view.Gravity.CENTER);
            int topMargin = (int) (8 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = topMargin;
            emptyStateView.addView(line2, lp);
        }

        // Add empty state to contentContainer (FrameLayout overlay, not LinearLayout)
        if (emptyStateView.getParent() == null) {
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            contentContainer.addView(emptyStateView, flp);
        }
        emptyStateView.setVisibility(VISIBLE);
    }

    private void hideRecentsEmptyState() {
        if (emptyStateView != null) {
            emptyStateView.setVisibility(GONE);
        }
    }

    private void showGifsForCategory(String category) {
        if (mGiphyGridView == null) return;

        GPHContent contentRequest;
        if (category.equals("Recents")) {
            contentRequest = GPHContent.Companion.getRecents();
        } else {
            contentRequest = GPHContent.Companion.searchQuery(category, MediaType.gif, RatingType.pg13);
        }
        mGiphyGridView.setContent(contentRequest);
    }

    private void onEmojiSelected(String emoji) {
        Log.d(TAG, "onEmojiSelected: '" + emoji + "' listener=" + mKeyboardActionListener.getClass().getSimpleName());
        mKeyboardActionListener.onTextInput(emoji);
        emojiRecentsManager.addRecent(emoji);
    }

    /**
     * Programmatically show skin tone popup for testing (debug broadcast).
     * Finds the first visible skin-tone-capable emoji and shows the popup.
     */
    public void showSkinTonePopupForTest() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            for (int i = 0; i < emojisView.getChildCount(); i++) {
                View child = emojisView.getChildAt(i);
                if (child instanceof TextView) {
                    String emoji = ((TextView) child).getText().toString();
                    if (SkinTonePopup.supportsSkinTones(emoji)) {
                        // Use non-focusable popup to avoid stealing IME focus
                        // (focusable popup causes IME to close emoji keyboard)
                        mSkinTonePopup = SkinTonePopup.showNonFocusable(child, emoji);
                        return;
                    }
                }
            }
            Log.w(TAG, "showSkinTonePopupForTest: no skin-tone-capable emoji found in grid");
        }, 300);
    }

    /** Dismiss the skin tone popup if showing. Called during test state reset. */
    public void dismissSkinTonePopup() {
        if (mSkinTonePopup != null && mSkinTonePopup.isShowing()) {
            mSkinTonePopup.dismiss();
        }
        mSkinTonePopup = null;
    }

    private void switchToAlphabetKeyboard() {
        // C7: Clean up search on keyboard hide
        if (searchActive) deactivateSearch();
        switcher.hideProductViews();
    }

    private void deleteCharacter() {
        mKeyboardActionListener.onCustomRequest(Constants.CODE_DELETE);
    }

    // ===== Theme =====

    @Override
    public void onThemeChanged(Context themedContext) {
        mThemedContext = themedContext;

        // Background — use themed context so it follows keyboard theme (Light/Dark/System)
        this.setBackgroundColor(themedContext.getResources().getColor(R.color.wk_bg));

        // Update search hint color from themed context
        if (searchHintView != null) {
            searchHintView.setTextColor(themedContext.getResources().getColor(R.color.wk_text3));
        }

        // Update empty state text colors if already created
        if (emptyStateView != null) {
            for (int i = 0; i < emptyStateView.getChildCount(); i++) {
                View child = emptyStateView.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(
                            themedContext.getResources().getColor(R.color.wk_text3));
                }
            }
        }

        // Propagate theme to all children (C7: search view updates colors, stays active)
        if (emojiCategoryView != null) emojiCategoryView.onThemeChanged(themedContext);
        if (gifCategoryView != null) gifCategoryView.onThemeChanged(themedContext);
        if (emojisView != null) emojisView.onThemeChanged(themedContext);
        if (emojiFunctionalView != null) emojiFunctionalView.onThemeChanged(themedContext);
        if (searchView != null) searchView.onThemeChanged(themedContext);
        if (mGiphyGridView != null) {
            mGiphyGridView.setTheme(resolveGiphyTheme());
        }
    }

    /** Map app theme to Giphy SDK theme — uses themed context for keyboard theme awareness. */
    private GPHTheme resolveGiphyTheme() {
        try {
            Context ctx = mThemedContext != null ? mThemedContext : getContext();
            boolean isDark = (ctx.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            return isDark ? GPHTheme.Dark : GPHTheme.Light;
        } catch (Exception e) {
            return GPHTheme.Dark;
        }
    }

    // ===== Lifecycle =====

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateNavigationBarPadding();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "EmojiKeyboard attached to window");
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // C7: Clean up search on detach
        if (searchActive) deactivateSearch();
        searchHandler.removeCallbacksAndMessages(null);
    }
}
