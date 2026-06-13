package project.witty.keys.ui.chat;

import android.content.Context;
import android.graphics.Color;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import project.witty.keys.keyboard.internal.InternalChatInput;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import project.witty.keys.R;

public class WkInputBar extends FrameLayout {

    public interface OnSendListener { void onSend(String text); }
    public interface OnCaptureListener { void onCapture(); }

    private ImageView captureBtn;
    private InternalChatInput pill;
    private EditText systemEditText;
    private TextView sendBtn;
    private android.view.View container;
    private boolean sendActive = false;
    private boolean systemImeMode = false;
    private CharSequence hintText = "";
    private int systemImeTextColor = 0;
    private int systemImeHintColor = 0;
    private boolean hasSystemImeColors = false;
    private OnSendListener sendListener;
    private OnCaptureListener captureListener;

    public WkInputBar(Context context) {
        super(context);
        init(context);
    }

    public WkInputBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_input_bar, this, true);
        container = findViewById(R.id.wkInputBarContainer);
        captureBtn = findViewById(R.id.wkInputCapture);
        pill = findViewById(R.id.wkInputPill);
        sendBtn = findViewById(R.id.wkInputSend);

        hintText = pill.getContext().getString(R.string.wk_ds_input_hint_default);
        pill.setOnTextChangedListener(text -> {
            if (!systemImeMode) updateSendActive(text != null && text.trim().length() > 0);
        });
        /*
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateSendActive(s != null && s.toString().trim().length() > 0);
            }
*/

        sendBtn.setOnClickListener(v -> {
            String text = getText();
            boolean hasText = text != null && text.trim().length() > 0;
            if (!hasText || sendListener == null) return;
            sendListener.onSend(text);
        });

        captureBtn.setOnClickListener(v -> {
            if (captureListener != null) captureListener.onCapture();
        });
    }

    public void setCaptureEnabled(boolean enabled) {
        captureBtn.setVisibility(enabled ? VISIBLE : GONE);
    }

    public void setHint(CharSequence hint) {
        hintText = hint != null ? hint : "";
        pill.setPlaceholderText(systemImeMode ? "" : hintText.toString());
        if (systemEditText != null) {
            systemEditText.setHint(hintText);
        }
    }

    public void setText(CharSequence text) {
        if (systemImeMode) {
            ensureSystemEditText();
            systemEditText.setText(text != null ? text : "");
            systemEditText.setSelection(systemEditText.getText().length());
            updateSendActive(systemEditText.getText().toString().trim().length() > 0);
        } else {
            pill.setText(text != null ? text.toString() : "");
        }
    }

    public String getText() {
        if (systemImeMode && systemEditText != null) {
            return systemEditText.getText().toString();
        }
        return pill.getText();
    }

    public void clearText() {
        if (systemImeMode && systemEditText != null) {
            systemEditText.setText("");
            updateSendActive(false);
        } else {
            pill.setText("");
        }
    }

    public InternalChatInput getEditText() {
        return pill;
    }

    public void setDisabled(boolean disabled) {
        setAlpha(disabled ? 0.5f : 1.0f);
        pill.setEnabled(!disabled);
        if (systemEditText != null) systemEditText.setEnabled(!disabled);
        sendBtn.setEnabled(!disabled);
        captureBtn.setEnabled(!disabled);
    }

    public void setUseSystemIme(boolean enabled) {
        if (systemImeMode == enabled) return;
        systemImeMode = enabled;
        if (enabled) {
            ensureSystemEditText();
            pill.setPlaceholderText("");
            pill.setText("");
            pill.setOnClickListener(v -> focusSystemIme());
            systemEditText.setVisibility(VISIBLE);
            updateSendActive(systemEditText.getText().toString().trim().length() > 0);
        } else {
            String text = systemEditText != null ? systemEditText.getText().toString() : "";
            if (systemEditText != null) systemEditText.setVisibility(GONE);
            pill.setPlaceholderText(hintText.toString());
            pill.setOnClickListener(v -> {
                if (!pill.isActive()) pill.activate();
            });
            pill.setText(text);
        }
    }

    public void focusSystemIme() {
        if (!systemImeMode) return;
        ensureSystemEditText();
        systemEditText.requestFocus();
        systemEditText.post(() -> {
            InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(systemEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    public void setOverlayDarkStyle() {
        setBackgroundColor(Color.TRANSPARENT);
        if (container != null) {
            container.setBackgroundResource(R.drawable.overlay_input_bar_bg);
        }
        pill.setBackgroundResource(R.drawable.overlay_input_bg);
        setSystemImeTextColors(
            ContextCompat.getColor(getContext(), R.color.wk_overlay_dark_text),
            ContextCompat.getColor(getContext(), R.color.wk_overlay_dark_text2));
    }

    public void setSystemImeTextColors(int textColor, int hintColor) {
        systemImeTextColor = textColor;
        systemImeHintColor = hintColor;
        hasSystemImeColors = true;
        if (systemEditText != null) {
            systemEditText.setTextColor(textColor);
            systemEditText.setHintTextColor(hintColor);
        }
    }

    public void setOnSendListener(OnSendListener l) { this.sendListener = l; }
    public void setOnCaptureListener(OnCaptureListener l) { this.captureListener = l; }

    private void updateSendActive(boolean active) {
        sendActive = active;
        sendBtn.setAlpha(active ? 1.0f : 0.4f);
    }

    private void ensureSystemEditText() {
        if (systemEditText != null) return;

        systemEditText = new EditText(getContext());
        systemEditText.setBackgroundColor(Color.TRANSPARENT);
        systemEditText.setTextColor(hasSystemImeColors
            ? systemImeTextColor : ContextCompat.getColor(getContext(), R.color.wk_text));
        systemEditText.setHintTextColor(hasSystemImeColors
            ? systemImeHintColor : ContextCompat.getColor(getContext(), R.color.wk_text3));
        systemEditText.setTextSize(14);
        systemEditText.setSingleLine(false);
        systemEditText.setMinLines(1);
        systemEditText.setMaxLines(3);
        systemEditText.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        systemEditText.setPadding(dp(12), dp(4), dp(36), dp(4));
        systemEditText.setHint(hintText);
        systemEditText.setVisibility(GONE);
        systemEditText.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        systemEditText.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        systemEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendBtn.performClick();
                return true;
            }
            return false;
        });
        systemEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendActive(s != null && s.toString().trim().length() > 0);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        systemEditText.setOnClickListener(v -> focusSystemIme());

        pill.addView(systemEditText, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private int dp(int value) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics());
    }

    @VisibleForTesting public int getCaptureVisibilityForTest() { return captureBtn.getVisibility(); }
    @VisibleForTesting public boolean isSendActiveForTest() { return sendActive; }
    @VisibleForTesting public void clickSendForTest() { sendBtn.performClick(); }
    @VisibleForTesting public void clickCaptureForTest() { captureBtn.performClick(); }
    @VisibleForTesting public boolean isSystemImeModeForTest() { return systemImeMode; }
    @VisibleForTesting public EditText getSystemEditTextForTest() { return systemEditText; }
}
