package project.witty.keys.keyboard.EmojiKeyboard;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import project.witty.keys.R;

/**
 * A horizontal PopupWindow showing skin tone variants (default + 5 modifiers)
 * for a long-pressed emoji. Anchored above the pressed view.
 *
 * D2: Basic version — shows 6 variants, commit on tap.
 * Follow-up: "remember preference" per emoji (not in this phase).
 */
public class SkinTonePopup {

    // Unicode skin tone modifiers (Fitzpatrick scale)
    private static final String[] SKIN_TONE_MODIFIERS = {
            "",                // default (no modifier)
            "\uD83C\uDFFB",   // light (U+1F3FB)
            "\uD83C\uDFFC",   // medium-light (U+1F3FC)
            "\uD83C\uDFFD",   // medium (U+1F3FD)
            "\uD83C\uDFFE",   // medium-dark (U+1F3FE)
            "\uD83C\uDFFF"    // dark (U+1F3FF)
    };

    private static final Paint sPaint = new Paint();

    public interface OnSkinToneSelectedListener {
        void onSelected(String emojiVariant);
    }

    /**
     * Check if an emoji supports skin tone modifiers.
     * Uses {@link Paint#hasGlyph(String)} (API 23+) to verify the emoji + modifier
     * renders as a valid single glyph on this device.
     */
    public static boolean supportsSkinTones(String emoji) {
        String base = stripSkinTone(emoji);
        String test = base + SKIN_TONE_MODIFIERS[1]; // try light skin tone
        return sPaint.hasGlyph(test);
    }

    /**
     * Strip any existing skin tone modifier from an emoji string.
     * Removes code points in the range U+1F3FB..U+1F3FF.
     */
    public static String stripSkinTone(String emoji) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < emoji.length()) {
            int cp = emoji.codePointAt(i);
            if (cp >= 0x1F3FB && cp <= 0x1F3FF) {
                i += Character.charCount(cp);
                continue;
            }
            sb.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /**
     * Show the skin tone popup anchored above the given view.
     *
     * @param anchor   The long-pressed emoji view
     * @param emoji    The emoji string (may already have a skin tone)
     * @param listener Called when a variant is tapped
     */
    public static void show(View anchor, String emoji, OnSkinToneSelectedListener listener) {
        Context context = anchor.getContext();
        String base = stripSkinTone(emoji);

        // Build 6 variants: base + each skin tone modifier
        String[] variants = new String[SKIN_TONE_MODIFIERS.length];
        for (int i = 0; i < SKIN_TONE_MODIFIERS.length; i++) {
            variants[i] = base + SKIN_TONE_MODIFIERS[i];
        }

        // Horizontal container
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        int padding = context.getResources().getDimensionPixelSize(
                R.dimen.wk_emoji_skin_popup_padding);
        container.setPadding(padding, padding, padding, padding);

        // Rounded-rect background: wk_surface2 fill, wk_chip_border stroke, 12dp corners
        GradientDrawable bg = new GradientDrawable();
        int cornerRadius = context.getResources().getDimensionPixelSize(
                R.dimen.wk_emoji_skin_popup_corner_radius);
        bg.setCornerRadius(cornerRadius);
        bg.setColor(context.getResources().getColor(R.color.wk_surface2));
        bg.setStroke(
                context.getResources().getDimensionPixelSize(
                        R.dimen.wk_emoji_skin_popup_border_width),
                context.getResources().getColor(R.color.wk_chip_border));
        container.setBackground(bg);
        container.setElevation(4 * context.getResources().getDisplayMetrics().density);

        // Add emoji variant buttons
        float emojiTextSize = context.getResources().getDimension(R.dimen.wk_emoji_text_size);
        int cellSize = context.getResources().getDimensionPixelSize(
                R.dimen.wk_emoji_skin_popup_cell_size);

        PopupWindow popup = new PopupWindow(
                container,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true // focusable → dismisses on tap outside
        );

        for (String variant : variants) {
            TextView tv = new TextView(context);
            tv.setText(variant);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, emojiTextSize);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellSize, cellSize);
            container.addView(tv, lp);

            tv.setOnClickListener(v -> {
                listener.onSelected(variant);
                popup.dismiss();
            });
        }

        // Configure popup
        popup.setElevation(4 * context.getResources().getDisplayMetrics().density);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

        // Position: above anchor, horizontally centered
        container.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth = container.getMeasuredWidth();
        int popupHeight = container.getMeasuredHeight();
        int xOff = (anchor.getWidth() - popupWidth) / 2;
        int yOff = -(anchor.getHeight() + popupHeight + padding);

        popup.showAsDropDown(anchor, xOff, yOff);
    }

    /**
     * Show a non-focusable skin tone popup for testing/screenshots.
     * Non-focusable popup doesn't steal IME focus, so the emoji keyboard
     * stays visible behind it.
     */
    public static PopupWindow showNonFocusable(View anchor, String emoji) {
        Context context = anchor.getContext();
        String base = stripSkinTone(emoji);

        String[] variants = new String[SKIN_TONE_MODIFIERS.length];
        for (int i = 0; i < SKIN_TONE_MODIFIERS.length; i++) {
            variants[i] = base + SKIN_TONE_MODIFIERS[i];
        }

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        int padding = context.getResources().getDimensionPixelSize(
                R.dimen.wk_emoji_skin_popup_padding);
        container.setPadding(padding, padding, padding, padding);

        GradientDrawable bg = new GradientDrawable();
        int cornerRadius = context.getResources().getDimensionPixelSize(
                R.dimen.wk_emoji_skin_popup_corner_radius);
        bg.setCornerRadius(cornerRadius);
        bg.setColor(context.getResources().getColor(R.color.wk_surface2));
        bg.setStroke(
                context.getResources().getDimensionPixelSize(
                        R.dimen.wk_emoji_skin_popup_border_width),
                context.getResources().getColor(R.color.wk_chip_border));
        container.setBackground(bg);
        container.setElevation(4 * context.getResources().getDisplayMetrics().density);

        float emojiTextSize = context.getResources().getDimension(R.dimen.wk_emoji_text_size);
        int cellSize = context.getResources().getDimensionPixelSize(
                R.dimen.wk_emoji_skin_popup_cell_size);

        // Non-focusable: won't steal IME focus
        PopupWindow popup = new PopupWindow(
                container,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false
        );

        for (String variant : variants) {
            TextView tv = new TextView(context);
            tv.setText(variant);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, emojiTextSize);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellSize, cellSize);
            container.addView(tv, lp);
        }

        popup.setElevation(4 * context.getResources().getDisplayMetrics().density);
        popup.setOutsideTouchable(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

        container.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth = container.getMeasuredWidth();
        int popupHeight = container.getMeasuredHeight();
        int xOff = (anchor.getWidth() - popupWidth) / 2;
        int yOff = -(anchor.getHeight() + popupHeight + padding);

        popup.showAsDropDown(anchor, xOff, yOff);
        return popup;
    }
}
