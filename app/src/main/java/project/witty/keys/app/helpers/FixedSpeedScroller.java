package project.witty.keys.app.helpers;

import android.content.Context;
import android.view.animation.Interpolator;
import android.widget.Scroller;

/**
 * Custom Scroller to control ViewPager scroll duration when smoothScroll is used.
 */
public class FixedSpeedScroller extends Scroller {

    private int scrollDuration = 1000; // Default duration in milliseconds (ADJUST AS NEEDED)

    // Constructors matching the Scroller class
    public FixedSpeedScroller(Context context, int duration) {
        super(context);
        this.scrollDuration = duration;
    }

    public FixedSpeedScroller(Context context, Interpolator interpolator, int duration) {
        super(context, interpolator);
        this.scrollDuration = duration;
    }

    // This constructor is needed for newer Android versions
    public FixedSpeedScroller(Context context, Interpolator interpolator, boolean flywheel, int duration) {
        super(context, interpolator, flywheel);
        this.scrollDuration = duration;
    }

    @Override
    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        // Ignore received duration, use the custom fixed duration instead
        super.startScroll(startX, startY, dx, dy, this.scrollDuration);
    }

    @Override
    public void startScroll(int startX, int startY, int dx, int dy) {
        // Called internally on some versions, ensure it uses the custom duration too
        super.startScroll(startX, startY, dx, dy, this.scrollDuration);
    }

    /**
     * Optional: Allows changing the duration after creation.
     * @param duration New scroll duration in milliseconds.
     */
    public void setScrollDuration(int duration) {
        this.scrollDuration = duration;
    }
}