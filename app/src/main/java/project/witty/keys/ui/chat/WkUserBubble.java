package project.witty.keys.ui.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import project.witty.keys.R;

public class WkUserBubble extends FrameLayout {

    private TextView text;

    public WkUserBubble(Context context) {
        super(context);
        init(context);
    }

    public WkUserBubble(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_bubble_user, this, true);
        text = findViewById(R.id.wkBubbleUserText);
    }

    public void bind(String message) {
        text.setText(message == null ? "" : message);
    }

    @VisibleForTesting
    public String getTextForTest() {
        return text.getText().toString();
    }
}
