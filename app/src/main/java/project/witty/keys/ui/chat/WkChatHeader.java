package project.witty.keys.ui.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import project.witty.keys.R;

public class WkChatHeader extends FrameLayout {

    public interface OnBackClickListener { void onClick(); }
    public interface OnSessionsClickListener { void onClick(); }
    public interface OnCloseClickListener { void onClick(); }

    private ImageView back;
    private View dot;
    private TextView title;
    private TextView meta;
    private ImageView sessions;
    private ImageView close;
    private OnBackClickListener backListener;
    private OnSessionsClickListener sessionsListener;
    private OnCloseClickListener closeListener;

    public WkChatHeader(Context context) {
        super(context);
        init(context);
    }

    public WkChatHeader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_chat_header, this, true);
        back = findViewById(R.id.wkHeaderBack);
        dot = findViewById(R.id.wkHeaderDot);
        title = findViewById(R.id.wkHeaderTitle);
        meta = findViewById(R.id.wkHeaderMeta);
        sessions = findViewById(R.id.wkHeaderSessions);
        close = findViewById(R.id.wkHeaderClose);
        back.setOnClickListener(v -> { if (backListener != null) backListener.onClick(); });
        sessions.setOnClickListener(v -> { if (sessionsListener != null) sessionsListener.onClick(); });
        close.setOnClickListener(v -> { if (closeListener != null) closeListener.onClick(); });
    }

    public void setTitle(CharSequence t) { title.setText(t); }
    public void setBackVisible(boolean v) { back.setVisibility(v ? VISIBLE : GONE); }
    public void setOnBackClickListener(OnBackClickListener l) { this.backListener = l; }
    public void setOnSessionsClickListener(OnSessionsClickListener l) { this.sessionsListener = l; }
    public void setOnCloseClickListener(OnCloseClickListener l) { this.closeListener = l; }

    public void setDotColor(int color) {
        if (dot != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            dot.setBackground(bg);
            dot.setVisibility(VISIBLE);
        }
    }

    public void setDotVisible(boolean visible) {
        if (dot != null) dot.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setSessionsVisible(boolean v) { if (sessions != null) sessions.setVisibility(v ? VISIBLE : GONE); }

    public void setMeta(String text) {
        if (meta != null) {
            if (text != null && !text.isEmpty()) {
                meta.setText(text);
                meta.setVisibility(VISIBLE);
            } else {
                meta.setVisibility(GONE);
            }
        }
    }

    @VisibleForTesting public String getTitleForTest() { return title.getText().toString(); }
    @VisibleForTesting public int getBackVisibilityForTest() { return back.getVisibility(); }
    @VisibleForTesting public void clickBackForTest() { back.performClick(); }
    @VisibleForTesting public void clickSessionsForTest() { sessions.performClick(); }
    @VisibleForTesting public void clickCloseForTest() { close.performClick(); }
}
