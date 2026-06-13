package project.witty.keys.keyboard.EmojiKeyboard;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import project.witty.keys.R;
import project.witty.keys.app.helpers.ThemeUtils;

public class EmojiFunctionalView extends LinearLayout {
    private View topDivider;
    private TextView switchKeyboardButton;
    private ImageButton deleteButton;

    private TextView emojiButton;
    private TextView gifButton;
    private View emojiUnderline;
    private View gifUnderline;

    private OnClickListener emojiButtonListener = v -> {};
    private OnClickListener gifButtonListener = v -> {};

    public enum Mode { EMOJI, GIF }
    private Mode currentMode = Mode.EMOJI;
    private Context mThemedContext;

    public EmojiFunctionalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);

        // Top divider line
        topDivider = new View(context);
        int dividerHeight = getResources().getDimensionPixelSize(R.dimen.wk_emoji_func_divider_height);
        topDivider.setBackgroundColor(getResources().getColor(R.color.wk_divider));
        addView(topDivider, new LayoutParams(LayoutParams.MATCH_PARENT, dividerHeight));

        // Main bar container (horizontal)
        LinearLayout barContainer = new LinearLayout(context);
        barContainer.setOrientation(HORIZONTAL);
        barContainer.setGravity(Gravity.CENTER_VERTICAL);
        int funcPad = getResources().getDimensionPixelSize(R.dimen.wk_emoji_func_padding);
        barContainer.setPadding(funcPad, 0, funcPad, 0);

        int barHeight = getResources().getDimensionPixelSize(R.dimen.wk_emoji_func_height);
        addView(barContainer, new LayoutParams(LayoutParams.MATCH_PARENT, barHeight));

        // "ABC" Button
        switchKeyboardButton = createStyledButton("ABC");
        barContainer.addView(switchKeyboardButton, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

        // Segmented control container for Emoji/GIF
        LinearLayout switcherContainer = new LinearLayout(context);
        switcherContainer.setOrientation(HORIZONTAL);
        switcherContainer.setGravity(Gravity.CENTER);
        int switcherMargin = getResources().getDimensionPixelSize(R.dimen.wk_emoji_func_switcher_margin);
        LayoutParams containerParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 3.0f);
        containerParams.setMarginStart(switcherMargin);
        containerParams.setMarginEnd(switcherMargin);
        barContainer.addView(switcherContainer, containerParams);

        // Each segment: vertical layout with label + underline
        switcherContainer.addView(createSegment(true), new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f));
        switcherContainer.addView(createSegment(false), new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f));

        // Delete Button — use system vector so it adapts to theme
        deleteButton = new ImageButton(context);
        deleteButton.setImageResource(R.drawable.sym_keyboard_delete_lxx_system);
        int iconPad = getResources().getDimensionPixelSize(R.dimen.wk_emoji_func_btn_icon_padding);
        deleteButton.setPadding(iconPad, iconPad, iconPad, iconPad);
        deleteButton.setBackground(null); // flat, no background
        deleteButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        barContainer.addView(deleteButton, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

        // Set background
        setBackgroundColor(getResources().getColor(R.color.wk_surface));

        updateSelection();
    }

    /**
     * Create a segment (Emoji or GIF) with label + underline indicator.
     */
    private LinearLayout createSegment(boolean isEmoji) {
        Context context = getContext();
        LinearLayout segment = new LinearLayout(context);
        segment.setOrientation(VERTICAL);
        segment.setGravity(Gravity.CENTER);

        TextView label = createStyledButton(isEmoji ? "\uD83D\uDE0A Emoji" : "\uD83C\uDFAC GIF");
        label.setBackground(null); // no chip background in segmented control
        segment.addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));

        // Underline indicator
        View underline = new View(context);
        int underlineHeight = getResources().getDimensionPixelSize(R.dimen.wk_emoji_func_underline_height);
        segment.addView(underline, new LayoutParams(LayoutParams.MATCH_PARENT, underlineHeight));

        if (isEmoji) {
            emojiButton = label;
            emojiUnderline = underline;
            label.setOnClickListener(v -> emojiButtonListener.onClick(v));
        } else {
            gifButton = label;
            gifUnderline = underline;
            label.setOnClickListener(v -> gifButtonListener.onClick(v));
        }

        return segment;
    }

    private TextView createStyledButton(String text) {
        TextView button = new TextView(getContext());
        button.setText(text);
        float textSize = getResources().getDimension(R.dimen.wk_emoji_func_text_size);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, Typeface.BOLD);
        int paddingV = getResources().getDimensionPixelSize(R.dimen.wk_emoji_func_btn_padding_v);
        int paddingH = getResources().getDimensionPixelSize(R.dimen.wk_emoji_func_btn_padding_h);
        button.setPadding(paddingH, paddingV, paddingH, paddingV);
        return button;
    }

    public void setMode(Mode mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            updateSelection();
        }
    }

    private void updateSelection() {
        if (mThemedContext == null) mThemedContext = getContext();

        boolean isEmojiSelected = currentMode == Mode.EMOJI;
        int accentColor = mThemedContext.getResources().getColor(R.color.wk_accent);
        int textColor2 = mThemedContext.getResources().getColor(R.color.wk_text2);

        // Emoji segment
        if (emojiButton != null) {
            emojiButton.setTextColor(isEmojiSelected ? accentColor : textColor2);
        }
        if (emojiUnderline != null) {
            emojiUnderline.setBackgroundColor(isEmojiSelected ? accentColor : Color.TRANSPARENT);
        }

        // GIF segment
        if (gifButton != null) {
            gifButton.setTextColor(!isEmojiSelected ? accentColor : textColor2);
        }
        if (gifUnderline != null) {
            gifUnderline.setBackgroundColor(!isEmojiSelected ? accentColor : Color.TRANSPARENT);
        }
    }

    public void onThemeChanged(Context themedContext) {
        this.mThemedContext = themedContext;

        // Use themed context resources so colors follow keyboard theme (Light/Dark/System)
        android.content.res.Resources res = themedContext.getResources();

        // Background — matches keyboard bg via wk_surface
        setBackgroundColor(res.getColor(R.color.wk_surface));

        // Divider
        if (topDivider != null) {
            topDivider.setBackgroundColor(res.getColor(R.color.wk_divider));
        }

        // ABC button — simple text, no pill background
        if (switchKeyboardButton != null) {
            switchKeyboardButton.setTextColor(res.getColor(R.color.wk_text2));
            switchKeyboardButton.setBackground(null);
        }

        // Delete button — themed icon tint, no background
        if (deleteButton != null) {
            deleteButton.setColorFilter(res.getColor(R.color.wk_text2), PorterDuff.Mode.SRC_IN);
            deleteButton.setBackground(null);
        }

        updateSelection();
    }

    public void setSwitchKeyboardListener(OnClickListener listener) {
        switchKeyboardButton.setOnClickListener(listener);
    }

    public void setDeleteListener(OnClickListener listener) {
        // D6: Delete button press feedback — scale(0.9) + haptic, then release
        deleteButton.setOnClickListener(v -> {
            // Scale down
            AnimatorSet press = new AnimatorSet();
            press.playTogether(
                    ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.9f),
                    ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.9f)
            );
            press.setDuration(50);
            press.setInterpolator(new AccelerateInterpolator());

            // Scale back
            AnimatorSet release = new AnimatorSet();
            release.playTogether(
                    ObjectAnimator.ofFloat(v, "scaleX", 0.9f, 1f),
                    ObjectAnimator.ofFloat(v, "scaleY", 0.9f, 1f)
            );
            release.setDuration(100);
            release.setInterpolator(new DecelerateInterpolator());

            AnimatorSet combined = new AnimatorSet();
            combined.playSequentially(press, release);
            combined.start();

            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listener != null) listener.onClick(v);
        });
    }

    public void setEmojiButtonListener(OnClickListener listener) {
        this.emojiButtonListener = listener;
    }

    public void setGifButtonListener(OnClickListener listener) {
        this.gifButtonListener = listener;
    }
}
