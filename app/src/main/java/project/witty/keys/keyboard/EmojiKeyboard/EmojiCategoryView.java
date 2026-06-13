package project.witty.keys.keyboard.EmojiKeyboard;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import project.witty.keys.keyboard.EmojiKeyboard.data.EmojiDataProvider;

public class EmojiCategoryView extends HorizontalScrollView {
    private LinearLayout container;

    private View lastSelectedButton = null;
    private Context mThemedContext;

    /** Category icons in the same order as EmojiDataProvider.CATEGORY_ORDER */
    private static final String[] CATEGORY_ICONS = {
            "\u23F1",  // Recents (stopwatch)
            "\uD83D\uDE0A",  // Smileys & People
            "\uD83D\uDC95",  // Dating & Romance
            "\uD83E\uDD1A",  // Gestures & Body
            "\uD83D\uDC3E",  // Animals & Nature
            "\uD83C\uDF54",  // Food & Drink (hamburger)
            "\u26BD",  // Activities & Sports
            "\u2708\uFE0F",  // Travel & Places
            "\uD83D\uDCA1",  // Objects
            "\uD83D\uDD23",  // Symbols & Flags
    };

    /** Short display labels (without full EmojiDataProvider names for compactness). */
    private static final String[] CATEGORY_SHORT_LABELS = {
            "Recents",
            "Smileys",
            "Dating",
            "Gestures",
            "Animals",
            "Food",
            "Activities",
            "Travel",
            "Objects",
            "Symbols",
    };

    public EmojiCategoryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        this.mThemedContext = context;
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        int marginV = getResources().getDimensionPixelSize(R.dimen.wk_emoji_category_margin_v);
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
            // Use themed context to inflate drawable so it gets correct night-mode colors
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

    /**
     * Add a category chip button with an emoji icon prefix.
     *
     * @param categoryName full category name from EmojiDataProvider (used for matching)
     * @param listener     click handler to load emojis for this category
     */
    public void addCategoryButton(String categoryName, OnClickListener listener) {
        TextView chip = new TextView(getContext());

        // Margins between chips
        int marginH = getResources().getDimensionPixelSize(R.dimen.wk_emoji_category_margin_h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(marginH, 0, marginH, 0);
        chip.setLayoutParams(lp);

        // Label with emoji icon prefix
        String icon = getIconForCategory(categoryName);
        String shortLabel = getShortLabel(categoryName);
        chip.setText(icon + " " + shortLabel);
        chip.setTag(categoryName); // store full name for selectCategory()

        chip.setClickable(true);
        chip.setFocusable(true);
        float textSize = getResources().getDimension(R.dimen.wk_emoji_category_text_size);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);

        // Asymmetric padding (more horizontal than vertical for pill shape)
        int paddingH = getResources().getDimensionPixelSize(R.dimen.wk_emoji_category_padding_h);
        int paddingV = getResources().getDimensionPixelSize(R.dimen.wk_emoji_category_padding_v);
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
    }

    public void selectCategory(String categoryName) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                TextView button = (TextView) child;
                // Match by stored tag (full category name)
                Object tag = button.getTag();
                String matchName = tag != null ? tag.toString() : button.getText().toString();
                if (matchName.equalsIgnoreCase(categoryName)) {
                    if (lastSelectedButton instanceof TextView) {
                        updateButtonAppearance((TextView) lastSelectedButton, false);
                    }
                    updateButtonAppearance(button, true);
                    lastSelectedButton = button;
                    // Scroll to make the selected chip visible
                    scrollToChild(button);
                    return;
                }
            }
        }
    }

    private void animateSelection(View chip) {
        int durationMs = getResources().getInteger(R.integer.wk_emoji_category_select_anim_ms);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(chip, "scaleX", 0.95f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(chip, "scaleY", 0.95f, 1.0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(durationMs);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    private void scrollToChild(View child) {
        int scrollX = child.getLeft() - (getWidth() / 2) + (child.getWidth() / 2);
        smoothScrollTo(Math.max(0, scrollX), 0);
    }

    private String getIconForCategory(String categoryName) {
        String[] names = EmojiDataProvider.getCategoryNamesStatic();
        for (int i = 0; i < names.length && i < CATEGORY_ICONS.length; i++) {
            if (names[i].equalsIgnoreCase(categoryName)) {
                return CATEGORY_ICONS[i];
            }
        }
        return "\u2B50"; // fallback star
    }

    private String getShortLabel(String categoryName) {
        String[] names = EmojiDataProvider.getCategoryNamesStatic();
        for (int i = 0; i < names.length && i < CATEGORY_SHORT_LABELS.length; i++) {
            if (names[i].equalsIgnoreCase(categoryName)) {
                return CATEGORY_SHORT_LABELS[i];
            }
        }
        return categoryName;
    }
}
