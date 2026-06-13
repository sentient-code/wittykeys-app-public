package project.witty.keys.keyboard.EmojiKeyboard;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import project.witty.keys.R;

public class GifCategoryView extends HorizontalScrollView {
    private LinearLayout container;

    private View lastSelectedButton = null;
    private Context mThemedContext;

    public GifCategoryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        this.mThemedContext = context;
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        int marginV = getResources().getDimensionPixelSize(R.dimen.wk_gif_category_margin_v);
        container.setPadding(0, marginV, 0, marginV);
        container.setGravity(Gravity.CENTER_VERTICAL);
        addView(container);

        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setHorizontalFadingEdgeEnabled(false);
    }

    private void updateButtonAppearance(TextView button, boolean isSelected) {
        if (button == null) return;

        // Use themed context for correct Light/Dark colors
        android.content.res.Resources res = mThemedContext != null
                ? mThemedContext.getResources() : getResources();

        if (isSelected) {
            button.setBackground(mThemedContext != null
                    ? mThemedContext.getDrawable(R.drawable.wk_emoji_chip_bg_selected)
                    : getContext().getDrawable(R.drawable.wk_emoji_chip_bg_selected));
            button.setTextColor(res.getColor(R.color.wk_accent));
        } else {
            button.setBackground(mThemedContext != null
                    ? mThemedContext.getDrawable(R.drawable.wk_emoji_chip_bg)
                    : getContext().getDrawable(R.drawable.wk_emoji_chip_bg));
            button.setTextColor(res.getColor(R.color.wk_text2));
        }
    }

    public void onThemeChanged(Context themedContext) {
        this.mThemedContext = themedContext;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                updateButtonAppearance((TextView) child, child == lastSelectedButton);
            }
        }
    }

    public TextView addCategoryButton(String categoryName, OnClickListener listener) {
        TextView chip = new TextView(getContext());

        int marginH = getResources().getDimensionPixelSize(R.dimen.wk_gif_category_margin_h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(marginH, 0, marginH, 0);
        chip.setLayoutParams(lp);

        chip.setText(categoryName);
        chip.setTag(categoryName);
        chip.setClickable(true);
        chip.setFocusable(true);
        float textSize = getResources().getDimension(R.dimen.wk_gif_category_text_size);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);

        int paddingH = getResources().getDimensionPixelSize(R.dimen.wk_gif_category_padding_h);
        int paddingV = getResources().getDimensionPixelSize(R.dimen.wk_gif_category_padding_v);
        chip.setPadding(paddingH, paddingV, paddingH, paddingV);
        chip.setGravity(Gravity.CENTER);

        chip.setOnClickListener(v -> {
            if (lastSelectedButton instanceof TextView) {
                updateButtonAppearance((TextView) lastSelectedButton, false);
            }
            updateButtonAppearance(chip, true);
            animateSelection(chip);
            lastSelectedButton = chip;
            listener.onClick(v);
        });

        updateButtonAppearance(chip, false);
        container.addView(chip);
        return chip;
    }

    public void selectCategory(String categoryName) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                TextView button = (TextView) child;
                Object tag = button.getTag();
                String matchName = tag != null ? tag.toString() : button.getText().toString();
                if (matchName.equalsIgnoreCase(categoryName)) {
                    if (lastSelectedButton instanceof TextView) {
                        updateButtonAppearance((TextView) lastSelectedButton, false);
                    }
                    updateButtonAppearance(button, true);
                    lastSelectedButton = button;
                    scrollToChild(button);
                    return;
                }
            }
        }
    }

    private void animateSelection(View chip) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(chip, "scaleX", 0.95f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(chip, "scaleY", 0.95f, 1.0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(150);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    private void scrollToChild(View child) {
        int scrollX = child.getLeft() - (getWidth() / 2) + (child.getWidth() / 2);
        smoothScrollTo(Math.max(0, scrollX), 0);
    }
}
