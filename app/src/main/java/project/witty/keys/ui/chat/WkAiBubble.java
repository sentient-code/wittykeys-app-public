package project.witty.keys.ui.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import project.witty.keys.R;

public class WkAiBubble extends FrameLayout {

    public interface OnRetryClickListener { void onRetry(); }

    private TextView badge;
    private TextView text;
    private TextView retry;

    public WkAiBubble(Context context) {
        super(context);
        init(context);
    }

    public WkAiBubble(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_bubble_ai, this, true);
        badge = findViewById(R.id.wkBubbleAiBadge);
        text = findViewById(R.id.wkBubbleAiText);
        retry = findViewById(R.id.wkBubbleAiRetry);
    }

    public void bindNormal(CharSequence message) {
        badge.setVisibility(GONE);
        retry.setVisibility(GONE);
        text.setVisibility(VISIBLE);
        text.setText(message == null ? "" : message);
    }

    public void setBubbleColor(int color) {
        if (getChildCount() > 0) {
            android.graphics.drawable.Drawable bg = getChildAt(0).getBackground();
            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) bg).setColor(color);
            }
        }
    }

    public void bindWithReplyBadge(String message) {
        badge.setVisibility(VISIBLE);
        retry.setVisibility(GONE);
        text.setVisibility(VISIBLE);
        text.setText(message == null ? "" : message);
    }

    public void bindError(OnRetryClickListener listener) {
        badge.setVisibility(GONE);
        text.setVisibility(GONE);
        retry.setVisibility(VISIBLE);
        retry.setOnClickListener(v -> { if (listener != null) listener.onRetry(); });
    }

    @VisibleForTesting public String getTextForTest() { return text.getText().toString(); }
    @VisibleForTesting public int getBadgeVisibilityForTest() { return badge.getVisibility(); }
    @VisibleForTesting public int getRetryVisibilityForTest() { return retry.getVisibility(); }
    @VisibleForTesting public void clickRetryForTest() { retry.performClick(); }
}
