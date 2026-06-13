package project.witty.keys.keyboard.internal;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import project.witty.keys.R;
import project.witty.keys.latin.common.Constants;

/**
 * A styled TextView that looks like an EditText but receives characters
 * programmatically via the InternalInputTarget interface.
 *
 * Used inside UnifiedAiView's input area to capture chat messages
 * without modifying the host app's editor field.
 *
 * Key difference from InternalInputView: Enter key triggers a send callback
 * instead of being ignored.
 */
public class InternalChatInput extends FrameLayout implements InternalInputTarget {

    private static final String TAG = "WK_CHAT_INPUT";
    private static final int MAX_LENGTH = 200;
    private static final long CURSOR_BLINK_INTERVAL = 500;

    private final StringBuilder buffer = new StringBuilder();
    private final TextView textView;
    private final TextView clearButton;

    private boolean active = false;
    private boolean cursorVisible = true;
    private String placeholderText = "Ask anything...";
    private int placeholderColor;
    private int textColor;
    private OnTextChangedListener textChangedListener;

    // Send callback — triggered on Enter key press
    private Runnable onSendCallback;

    private final Runnable cursorBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            cursorVisible = !cursorVisible;
            updateDisplay();
            postDelayed(this, CURSOR_BLINK_INTERVAL);
        }
    };

    public InternalChatInput(Context context) {
        this(context, null);
    }

    public InternalChatInput(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InternalChatInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        placeholderColor = context.getResources().getColor(R.color.wk_text3);
        textColor = context.getResources().getColor(R.color.wk_text);

        // Main text display — multiline for 2-3 lines of text
        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        textView.setTextColor(placeholderColor);
        textView.setTypeface(null, Typeface.ITALIC);
        textView.setMaxLines(3);
        textView.setText(placeholderText);
        textView.setPadding(dp(12), dp(4), dp(36), dp(4));
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        textLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        textView.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        addView(textView, textLp);

        // Clear (X) button — right side, hidden until text > 0
        clearButton = new TextView(context);
        clearButton.setText("\u2715"); // ✕
        clearButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        clearButton.setTextColor(placeholderColor);
        clearButton.setGravity(android.view.Gravity.CENTER);
        clearButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(dp(28), dp(28));
        clearLp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END;
        clearLp.setMarginEnd(dp(4));
        clearButton.setOnClickListener(v -> {
            buffer.setLength(0);
            updateDisplay();
            notifyTextChanged();
        });
        addView(clearButton, clearLp);

        setOnClickListener(v -> {
            if (!active) {
                activate();
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
            // Enter key triggers SEND (not ignored like InternalInputView)
            if (buffer.length() > 0 && onSendCallback != null) {
                onSendCallback.run();
            }
            return;
        }

        // Printable characters (including space)
        if (codePoint >= 32 && buffer.length() < MAX_LENGTH) {
            buffer.appendCodePoint(codePoint);
            updateDisplay();
            notifyTextChanged();
        }
    }

    @Override
    public void onDeleteInput() {
        if (!active || buffer.length() == 0) return;
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
        setBackground(getContext().getResources().getDrawable(R.drawable.bg_input_pill_focused, null));
        updateDisplay();
        startCursorBlink();
        Log.d(TAG, "InternalChatInput activated");
    }

    @Override
    public void deactivate() {
        if (!active) return;
        active = false;
        buffer.setLength(0);
        stopCursorBlink();
        setBackground(getContext().getResources().getDrawable(R.drawable.bg_input_pill, null));
        updateDisplay();
        Log.d(TAG, "InternalChatInput deactivated");
    }

    @Override
    public void setOnTextChangedListener(OnTextChangedListener listener) {
        this.textChangedListener = listener;
    }

    // ===== Public API =====

    /** Set the send callback — triggered when user presses Enter */
    public void setOnSendCallback(Runnable callback) {
        this.onSendCallback = callback;
    }

    /** Set text programmatically (for debug/test broadcasts) */
    public void setText(String text) {
        buffer.setLength(0);
        if (text != null) {
            buffer.append(text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text);
        }
        updateDisplay();
        notifyTextChanged();
    }

    /** Set placeholder text shown when buffer is empty */
    public void setPlaceholderText(String text) {
        this.placeholderText = text;
        if (buffer.length() == 0) {
            updateDisplay();
        }
    }

    // ===== Internal =====

    private void updateDisplay() {
        if (buffer.length() == 0) {
            // Show placeholder
            textView.setTextColor(placeholderColor);
            textView.setTypeface(null, Typeface.ITALIC);
            if (active) {
                textView.setText(cursorVisible ? "\u2758 " + placeholderText : "  " + placeholderText);
            } else {
                textView.setText(placeholderText);
            }
            clearButton.setVisibility(View.GONE);
        } else {
            // Show typed text with cursor
            textView.setTextColor(textColor);
            textView.setTypeface(null, Typeface.NORMAL);
            String displayText = buffer.toString();
            if (active && cursorVisible) {
                displayText = displayText + "\u2758";
            }
            textView.setText(displayText);
            clearButton.setVisibility(View.GONE);
        }
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

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getContext().getResources().getDisplayMetrics());
    }

    // ===== Lifecycle-aware cursor management =====

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
