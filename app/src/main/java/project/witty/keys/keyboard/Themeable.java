package project.witty.keys.keyboard;

import android.content.Context;

/**
 * An interface for custom views that can be updated when the keyboard theme changes.
 */
public interface Themeable {
    void onThemeChanged(Context themedContext);
}