package project.witty.keys.keyboard.EmojiKeyboard;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import project.witty.keys.R;
import project.witty.keys.keyboard.internal.InternalInputTarget;
import project.witty.keys.latin.common.Constants;

/**
 * A pill-shaped search bar for the emoji keyboard that receives key events
 * via the {@link InternalInputTarget} interface.
 * <p>
 * Follows the same pattern as {@link project.witty.keys.keyboard.internal.InternalInputView}:
 * when active, LatinIME routes key events here instead of the host editor.
 * <p>
 * Visual layout: [search icon] [Search emoji... / typed text + cursor] [clear]
 */
public class InternalSearchView extends FrameLayout implements InternalInputTarget {

    private static final String TAG = "WK_EMOJI_SEARCH_VIEW";
    private static final int MAX_LENGTH = 40;
    private static final long CURSOR_BLINK_INTERVAL = 500; // ms

    private final StringBuilder buffer = new StringBuilder();
    private final ImageView searchIcon;
    private final TextView textView;
    private final TextView clearButton;

    private boolean active = false;
    private boolean cursorVisible = true;
    private String placeholderText = "Search emoji...";
    private int placeholderColor;
    private int textColor;
    private int accentColor;
    private Context mThemedContext; // Night-mode-aware context for drawable loading

    private OnTextChangedListener textChangedListener;
    private OnClickListener activateListener;
    private OnDeactivateListener deactivateListener;
    private OnEnterListener enterListener;

    /** Callback when the search bar is deactivated (clear pressed or empty backspace). */
    public interface OnDeactivateListener {
        void onDeactivate();
    }

    /** Callback when Enter is pressed while search is active. */
    public interface OnEnterListener {
        void onEnterPressed();
    }

    private final Runnable cursorBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            cursorVisible = !cursorVisible;
            updateDisplay();
            postDelayed(this, CURSOR_BLINK_INTERVAL);
        }
    };

    public InternalSearchView(Context context) {
        this(context, null);
    }

    public InternalSearchView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InternalSearchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        placeholderColor = context.getResources().getColor(R.color.wk_text3);
        textColor = context.getResources().getColor(R.color.wk_text);
        accentColor = context.getResources().getColor(R.color.wk_accent);

        int height = context.getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_height);
        int iconSize = context.getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_icon_size);
        int clearSize = context.getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_clear_size);
        int iconMarginStart = context.getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_icon_margin_start);
        int textMarginStart = context.getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_text_margin_start);
        int textMarginEnd = context.getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_text_margin_end);
        int clearMarginEnd = context.getResources().getDimensionPixelSize(R.dimen.wk_emoji_search_clear_margin_end);
        float searchTextSize = context.getResources().getDimension(R.dimen.wk_emoji_search_text_size);

        // Search icon (left side) — custom outlined icon matching mockup
        searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.wk_ic_search);
        searchIcon.setColorFilter(placeholderColor);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        iconLp.setMarginStart(iconMarginStart);
        addView(searchIcon, iconLp);

        // Text display (center)
        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, searchTextSize);
        textView.setTextColor(placeholderColor);
        textView.setTypeface(null, Typeface.ITALIC);
        textView.setSingleLine(true);
        textView.setText(placeholderText);
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        textLp.setMarginStart(textMarginStart);
        textLp.setMarginEnd(textMarginEnd);
        addView(textView, textLp);

        // Clear button (right side)
        clearButton = new TextView(context);
        clearButton.setText("\u2715"); // X
        clearButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, searchTextSize);
        clearButton.setTextColor(placeholderColor);
        clearButton.setGravity(Gravity.CENTER);
        clearButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(clearSize, clearSize);
        clearLp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        clearLp.setMarginEnd(clearMarginEnd);
        clearButton.setOnClickListener(v -> {
            deactivate();
            if (deactivateListener != null) deactivateListener.onDeactivate();
        });
        addView(clearButton, clearLp);

        // Background and sizing — use fixed height so children center correctly
        setBackground(context.getResources().getDrawable(R.drawable.wk_emoji_search_bg, null));
        // Force exact height via layout params (setMinimumHeight alone doesn't
        // guarantee the FrameLayout is tall enough for CENTER_VERTICAL to work)
        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, height));

        // Tap the search bar to activate
        setOnClickListener(v -> {
            if (!active) {
                activate();
                if (activateListener != null) activateListener.onClick(v);
            }
        });
    }

    // ===== InternalInputTarget implementation =====

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void onCodeInput(int codePoint) {
        if (!active) return;

        if (codePoint == Constants.CODE_ENTER) {
            if (enterListener != null) enterListener.onEnterPressed();
            return;
        }

        if (codePoint >= 32 && buffer.length() < MAX_LENGTH) {
            buffer.appendCodePoint(codePoint);
            updateDisplay();
            notifyTextChanged();
        }
    }

    @Override
    public void onDeleteInput() {
        if (!active) return;
        if (buffer.length() == 0) {
            // Empty backspace → deactivate
            deactivate();
            if (deactivateListener != null) deactivateListener.onDeactivate();
            return;
        }
        buffer.deleteCharAt(buffer.length() - 1);
        updateDisplay();
        notifyTextChanged();
    }

    @Override
    public String getText() {
        return buffer.toString();
    }

    @Override
    public void clear() {
        buffer.setLength(0);
        updateDisplay();
    }

    @Override
    public void activate() {
        if (active) return;
        active = true;
        buffer.setLength(0);
        cursorVisible = true;
        Context ctx = mThemedContext != null ? mThemedContext : getContext();
        setBackground(ctx.getDrawable(R.drawable.wk_emoji_search_bg_focused));
        updateDisplay();
        startCursorBlink();

        // D7: Animate search icon color wk_text3 → wk_accent (200ms)
        animateIconColor(placeholderColor, accentColor);

        Log.d(TAG, "InternalSearchView activated");
    }

    @Override
    public void deactivate() {
        if (!active) return;
        active = false;
        buffer.setLength(0);
        stopCursorBlink();
        Context ctx = mThemedContext != null ? mThemedContext : getContext();
        setBackground(ctx.getDrawable(R.drawable.wk_emoji_search_bg));
        updateDisplay();

        // D7: Animate search icon color wk_accent → wk_text3 (200ms)
        animateIconColor(accentColor, placeholderColor);

        Log.d(TAG, "InternalSearchView deactivated");
    }

    @Override
    public void setOnTextChangedListener(OnTextChangedListener listener) {
        this.textChangedListener = listener;
    }

    // ===== Public API =====

    /** Listener called when the user taps the search bar to activate it. */
    public void setOnActivateListener(OnClickListener listener) {
        this.activateListener = listener;
    }

    /** Listener called when search is deactivated (clear or empty backspace). */
    public void setOnDeactivateListener(OnDeactivateListener listener) {
        this.deactivateListener = listener;
    }

    /** Listener called when Enter key is pressed in search. */
    public void setOnEnterListener(OnEnterListener listener) {
        this.enterListener = listener;
    }

    /**
     * Programmatically set search text (for debug/test automation).
     * Activates the search bar if not already active, sets text, and triggers the text changed listener.
     */
    public void setSearchText(String text) {
        if (!active) {
            activate();
        }
        buffer.setLength(0);
        buffer.append(text);
        updateDisplay();
        notifyTextChanged();
    }

    // ===== Internal =====

    private void updateDisplay() {
        if (buffer.length() == 0) {
            textView.setTextColor(placeholderColor);
            textView.setTypeface(null, Typeface.ITALIC);
            if (active) {
                textView.setText(cursorVisible ? "\u2758 " + placeholderText : "  " + placeholderText);
            } else {
                textView.setText(placeholderText);
            }
            clearButton.setVisibility(View.GONE);
        } else {
            textView.setTextColor(textColor);
            textView.setTypeface(null, Typeface.NORMAL);
            String displayText = buffer.toString();
            if (active && cursorVisible) {
                displayText = displayText + "\u2758";
            }
            textView.setText(displayText);
            clearButton.setVisibility(active ? View.VISIBLE : View.GONE);
        }
    }

    // D7: Smooth color transition for search icon
    private void animateIconColor(int fromColor, int toColor) {
        ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        colorAnim.setDuration(200);
        colorAnim.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            searchIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        });
        colorAnim.start();
    }

    private void startCursorBlink() {
        removeCallbacks(cursorBlinkRunnable);
        cursorVisible = true;
        postDelayed(cursorBlinkRunnable, CURSOR_BLINK_INTERVAL);
    }

    private void stopCursorBlink() {
        removeCallbacks(cursorBlinkRunnable);
        cursorVisible = false;
    }

    private void notifyTextChanged() {
        if (textChangedListener != null) {
            textChangedListener.onTextChanged(buffer.toString());
        }
    }

    // ===== Theme =====

    /**
     * Update colors and drawables from the themed context so the search bar
     * follows the keyboard theme (Light/Dark/System) instead of always using
     * the View's own context.
     */
    public void onThemeChanged(Context themedContext) {
        mThemedContext = themedContext;
        placeholderColor = themedContext.getResources().getColor(R.color.wk_text3);
        textColor = themedContext.getResources().getColor(R.color.wk_text);
        accentColor = themedContext.getResources().getColor(R.color.wk_accent);

        // Re-apply drawable from themed context so @color references resolve correctly
        if (active) {
            setBackground(themedContext.getDrawable(R.drawable.wk_emoji_search_bg_focused));
            searchIcon.setColorFilter(accentColor);
        } else {
            setBackground(themedContext.getDrawable(R.drawable.wk_emoji_search_bg));
            searchIcon.setColorFilter(placeholderColor);
        }

        clearButton.setTextColor(placeholderColor);
        updateDisplay();
    }

    // ===== Lifecycle =====

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopCursorBlink();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE && active) {
            startCursorBlink();
        } else {
            stopCursorBlink();
        }
    }
}
