package project.witty.keys.keyboard.AssistantViews;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;
import android.util.TypedValue;


import java.util.Map;

import project.witty.keys.R;

/**
 * ThemeColorHelper - Utility class for resolving theme colors programmatically
 *
 * Build 6.3 - SmartAssistantBar Revamp - Phase 6
 *
 * CRITICAL: This class is needed because PopupWindow
 * cannot resolve theme attributes (?attr/...) from XML in IME context.
 *
 * Usage:
 * - Call methods from onThemeChanged(Context themedContext)
 * - Pass the themedContext to get colors for the current theme
 */
public final class ThemeColorHelper {

    private ThemeColorHelper() {
        // Utility class, no instantiation
    }

    // ========== CORE COLOR RESOLUTION ==========

    /**
     * Resolve a theme attribute to a color integer.
     *
     * @param themedContext The themed context (from onThemeChanged)
     * @param attrId        The attribute ID (e.g., R.attr.keyTextColor)
     * @param defaultColor  Fallback color if resolution fails
     * @return The resolved color
     */
    public static int getColor(Context themedContext, int attrId, int defaultColor) {
        TypedValue typedValue = new TypedValue();
        if (themedContext.getTheme().resolveAttribute(attrId, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return typedValue.data;
            } else if (typedValue.type == TypedValue.TYPE_REFERENCE) {
                // It's a reference to a color resource
                try {
                    return themedContext.getResources().getColor(typedValue.resourceId, themedContext.getTheme());
                } catch (Exception e) {
                    return defaultColor;
                }
            }
        }
        return defaultColor;
    }

    /**
     * Resolve a theme attribute to a color integer (using black as fallback).
     */
    public static int getColor(Context themedContext, int attrId) {
        return getColor(themedContext, attrId, Color.BLACK);
    }

    // ========== CONVENIENCE METHODS FOR COMMON COLORS ==========

    /**
     * Get the primary text color from the current theme.
     * Maps to: themedButtonTextColor (theme-level attribute)
     */
    public static int getPrimaryTextColor(Context themedContext) {
        return getColor(themedContext, R.attr.themedButtonTextColor, Color.BLACK);
    }

    /**
     * Get the secondary/hint text color from the current theme.
     * Maps to: utilityRowIconColor (theme-level attribute)
     */
    public static int getSecondaryTextColor(Context themedContext) {
        return getColor(themedContext, R.attr.utilityRowIconColor, Color.GRAY);
    }

    /**
     * Get the background color from the current theme.
     * Maps to: utilityRowBackground
     */
    public static int getBackgroundColor(Context themedContext) {
        return getColor(themedContext, R.attr.utilityRowBackground, Color.WHITE);
    }

    /**
     * Get the icon tint color from the current theme.
     * Maps to: utilityRowIconColor
     */
    public static int getIconColor(Context themedContext) {
        return getColor(themedContext, R.attr.utilityRowIconColor, Color.GRAY);
    }

    /**
     * Get the accent/functional color from the current theme.
     * Maps to: themedButtonTextColor (theme-level attribute)
     */
    public static int getAccentColor(Context themedContext) {
        return getColor(themedContext, R.attr.themedButtonTextColor, Color.BLUE);
    }

    /**
     * Get the card/chip background color from the current theme.
     * Maps to: themedButtonBackgroundColor
     */
    public static int getCardBackgroundColor(Context themedContext) {
        return getColor(themedContext, R.attr.themedButtonBackgroundColor, Color.LTGRAY);
    }

    /**
     * Get the button text color from the current theme.
     * Maps to: themedButtonTextColor
     */
    public static int getButtonTextColor(Context themedContext) {
        return getColor(themedContext, R.attr.themedButtonTextColor, Color.BLACK);
    }

    /**
     * Get the divider/separator color from the current theme.
     * Maps to: utilityRowSeparatorColor
     */
    public static int getDividerColor(Context themedContext) {
        return getColor(themedContext, R.attr.utilityRowSeparatorColor, Color.LTGRAY);
    }

    // ========== DRAWABLE CREATION ==========

    /**
     * Create a rounded rectangle background drawable with the given color.
     *
     * @param color        The background color
     * @param cornerRadius The corner radius in pixels
     * @return A GradientDrawable with rounded corners
     */
    public static GradientDrawable createRoundedBackground(int color, float cornerRadius) {
        return createRoundedRect(color, cornerRadius);
    }

    public static GradientDrawable createTopRoundedBackground(int color, float cornerRadius) {
        return createRoundedRect(color,
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                0, 0,
                0, 0);
    }

    private static GradientDrawable createRoundedRect(int color, float... radii) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        if (radii.length == 1) {
            drawable.setCornerRadius(radii[0]);
        } else if (radii.length == 8) {
            drawable.setCornerRadii(radii);
        }
        return drawable;
    }

    /**
     * Create a StateListDrawable for buttons with press states.
     *
     * @param themedContext The themed context
     * @param cornerRadius  The corner radius in pixels
     * @return A StateListDrawable with normal and pressed states
     */
    public static Drawable createButtonBackground(Context themedContext, float cornerRadius) {
        int normalColor = getColor(themedContext, R.attr.themedButtonBackgroundColor, Color.LTGRAY);
        int pressedColor = getColor(themedContext, R.attr.themedButtonPressedBackgroundColor, Color.GRAY);

        GradientDrawable normalDrawable = createRoundedBackground(normalColor, cornerRadius);
        GradientDrawable pressedDrawable = createRoundedBackground(pressedColor, cornerRadius);

        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        stateListDrawable.addState(StateSet.WILD_CARD, normalDrawable);

        return stateListDrawable;
    }

    /**
     * Create a card background with border.
     *
     * @param themedContext The themed context
     * @param cornerRadius  The corner radius in pixels
     * @return A GradientDrawable with border
     */
    public static GradientDrawable createCardBackground(Context themedContext, float cornerRadius) {
        GradientDrawable drawable = createRoundedRect(getCardBackgroundColor(themedContext), cornerRadius);
        drawable.setStroke(1, getDividerColor(themedContext));
        return drawable;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Calculate the luminance of a color (0.0 = black, 1.0 = white).
     */
    private static double calculateLuminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;

        // sRGB to linear RGB
        r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /**
     * Determine if the current theme is dark mode based on background luminance.
     */
    public static boolean isDarkMode(Context themedContext) {
        int bgColor = getBackgroundColor(themedContext);
        return calculateLuminance(bgColor) < 0.5;
    }
}
