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

public class WkSessionCard extends FrameLayout {

    private TextView title;
    private TextView preview;
    private TextView time;
    private View unreadDot;
    private WkSourceTag sourceTag;
    private View deleteBtn;

    public WkSessionCard(Context context) {
        super(context);
        init(context);
    }

    public WkSessionCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_session_card, this, true);
        title = findViewById(R.id.wkCardTitle);
        preview = findViewById(R.id.wkCardPreview);
        time = findViewById(R.id.wkCardTime);
        unreadDot = findViewById(R.id.wkCardUnreadDot);
        sourceTag = findViewById(R.id.wkCardSource);
        deleteBtn = findViewById(R.id.wkCardDelete);
    }

    public void bind(CharSequence titleText, CharSequence previewText, CharSequence timeText,
                     Surface source, boolean unread) {
        title.setText(titleText);
        boolean hasPreview = previewText != null && previewText.length() > 0;
        preview.setText(hasPreview ? previewText : "");
        preview.setVisibility(hasPreview ? VISIBLE : GONE);
        time.setText(timeText);
        if (source == Surface.FULLSCREEN) {
            sourceTag.setVisibility(GONE);
        } else {
            sourceTag.setVisibility(VISIBLE);
            sourceTag.setSurface(source);
        }
        unreadDot.setVisibility(unread ? VISIBLE : GONE);
    }

    public void setOnDeleteClickListener(@Nullable View.OnClickListener listener) {
        deleteBtn.setOnClickListener(listener);
    }

    @VisibleForTesting public String getTitleForTest() { return title.getText().toString(); }
    @VisibleForTesting public String getPreviewForTest() { return preview.getText().toString(); }
    @VisibleForTesting public String getTimeForTest() { return time.getText().toString(); }
    @VisibleForTesting public int getUnreadDotVisibilityForTest() { return unreadDot.getVisibility(); }
    @VisibleForTesting public int getSourceTagVisibilityForTest() { return sourceTag.getVisibility(); }
    @VisibleForTesting public String getSourceTagTextForTest() { return sourceTag.getTextForTest(); }
    @VisibleForTesting public void clickDeleteForTest() { deleteBtn.performClick(); }
}
