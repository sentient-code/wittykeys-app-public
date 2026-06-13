package project.witty.keys.app.helpers;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;

public final class ThemeUtils {
    /**
     * Resolves a theme attribute to a color integer.
     * @param context The themed context.
     * @param attrId The ID of the attribute to resolve (e.g., R.attr.utilityRowBackground).
     * @return The resolved color integer.
     */
    public static int getThemeColor(final Context context, final int attrId) {
        final TypedArray a = context.obtainStyledAttributes(new int[] { attrId });
        try {
            return a.getColor(0, Color.BLACK); // Return black as a fallback
        } finally {
            a.recycle();
        }
    }

    /**
     * Creates a theme-aware StateListDrawable for button backgrounds.
     *
     * @param themedContext The themed context to resolve attributes from.
     * @param normalColorAttr The attribute for the normal state background color.
     * @param pressedColorAttr The attribute for the pressed state background color.
     * @param cornerRadius The corner radius in pixels.
     * @return A theme-aware Drawable.
     */
    public static Drawable createButtonBackground(Context themedContext, int normalColorAttr, int pressedColorAttr, float cornerRadius) {
        // Get colors from the current theme
        int normalColor = getThemeColor(themedContext, normalColorAttr);
        int pressedColor = getThemeColor(themedContext, pressedColorAttr);

        // Create drawable for the normal state
        GradientDrawable normalDrawable = new GradientDrawable();
        normalDrawable.setColor(normalColor);
        normalDrawable.setCornerRadius(cornerRadius);

        // Create drawable for the pressed state
        GradientDrawable pressedDrawable = new GradientDrawable();
        pressedDrawable.setColor(pressedColor);
        pressedDrawable.setCornerRadius(cornerRadius);

        // Create the StateListDrawable (selector)
        StateListDrawable stateListDrawable = new StateListDrawable();
        // Order matters: pressed state must be added first.
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        stateListDrawable.addState(StateSet.WILD_CARD, normalDrawable); // Fallback/normal state

        return stateListDrawable;
    }
}