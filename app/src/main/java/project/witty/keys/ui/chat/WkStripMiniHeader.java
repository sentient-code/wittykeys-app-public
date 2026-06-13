package project.witty.keys.ui.chat;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import project.witty.keys.R;

public class WkStripMiniHeader extends FrameLayout {

    public interface OnSessionsClickListener { void onClick(); }
    public interface OnCloseClickListener { void onClick(); }

    private TextView title;
    private View dot;
    private View quoteBar;
    private TextView quote;
    private ImageView sessions;
    private ImageView close;
    private OnSessionsClickListener sessionsListener;
    private OnCloseClickListener closeListener;

    public WkStripMiniHeader(Context context) {
        super(context);
        init(context);
    }

    public WkStripMiniHeader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_strip_mini_header, this, true);
        title = findViewById(R.id.wkStripTitle);
        dot = findViewById(R.id.wkStripDot);
        quoteBar = findViewById(R.id.wkStripQuoteBar);
        quote = findViewById(R.id.wkStripQuote);
        sessions = findViewById(R.id.wkStripSessions);
        close = findViewById(R.id.wkStripClose);
        sessions.setOnClickListener(v -> { if (sessionsListener != null) sessionsListener.onClick(); });
        close.setOnClickListener(v -> { if (closeListener != null) closeListener.onClick(); });
    }

    public void setTitle(CharSequence t) { title.setText(t); }
    public void setOnSessionsClickListener(OnSessionsClickListener l) { this.sessionsListener = l; }
    public void setOnCloseClickListener(OnCloseClickListener l) { this.closeListener = l; }

    public void setDotColor(int color) {
        if (dot != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            dot.setBackground(bg);
        }
    }

    public void setReplyQuote(String text) {
        if (quote != null) {
            if (text != null && !text.isEmpty()) {
                quote.setText(text);
                quote.setVisibility(VISIBLE);
                if (title != null) title.setVisibility(GONE);
                if (quoteBar != null) quoteBar.setVisibility(VISIBLE);
            } else {
                quote.setVisibility(GONE);
                if (title != null) title.setVisibility(VISIBLE);
                if (quoteBar != null) quoteBar.setVisibility(GONE);
            }
        }
    }

    @VisibleForTesting public String getTitleForTest() { return title.getText().toString(); }
    @VisibleForTesting public int getQuoteBarVisibilityForTest() { return quoteBar != null ? quoteBar.getVisibility() : GONE; }
    @VisibleForTesting public void clickSessionsForTest() { sessions.performClick(); }
    @VisibleForTesting public void clickCloseForTest() { close.performClick(); }
}
