package project.witty.keys.ui.chat;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import project.witty.keys.R;

public class WkTypingBubble extends FrameLayout {

    private final View[] dots = new View[3];
    private final ObjectAnimator[] anims = new ObjectAnimator[3];
    private boolean animating = false;

    public WkTypingBubble(Context context) {
        super(context);
        init(context);
    }

    public WkTypingBubble(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_bubble_typing, this, true);
        dots[0] = findViewById(R.id.wkTypingDot1);
        dots[1] = findViewById(R.id.wkTypingDot2);
        dots[2] = findViewById(R.id.wkTypingDot3);
        for (int i = 0; i < 3; i++) {
            anims[i] = ObjectAnimator.ofFloat(dots[i], "alpha", 0.3f, 1.0f, 0.3f);
            anims[i].setDuration(900);
            anims[i].setStartDelay(i * 150L);
            anims[i].setRepeatCount(ValueAnimator.INFINITE);
        }
    }

    public void start() {
        if (animating) return;
        for (ObjectAnimator a : anims) a.start();
        animating = true;
    }

    public void stop() {
        if (!animating) return;
        for (ObjectAnimator a : anims) a.cancel();
        for (View d : dots) d.setAlpha(1f);
        animating = false;
    }

    public void setBubbleColor(int color) {
        if (getChildCount() > 0) {
            android.graphics.drawable.Drawable bg = getChildAt(0).getBackground();
            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) bg).setColor(color);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @VisibleForTesting
    public int getDotCountForTest() {
        ViewGroup row = (ViewGroup) getChildAt(0);
        return row.getChildCount();
    }

    @VisibleForTesting
    public boolean isAnimatingForTest() {
        return animating;
    }
}
