package project.witty.keys.ui.chat;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import project.witty.keys.R;

public class WkSourceTag extends FrameLayout {

    private TextView label;

    public WkSourceTag(Context context) {
        super(context);
        init(context);
    }

    public WkSourceTag(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_source_tag, this, true);
        label = findViewById(R.id.wkSourceTagLabel);
        setSurface(Surface.KEYBOARD);
    }

    public void setSurface(Surface surface) {
        switch (surface) {
            case KEYBOARD:
                label.setText(R.string.wk_ds_source_kbd);
                setBackgroundResource(R.drawable.bg_wk_source_tag_kbd);
                label.setTextColor(ContextCompat.getColor(getContext(), R.color.wk_accent));
                break;
            case OVERLAY:
                label.setText(R.string.wk_ds_source_ovr);
                setBackgroundResource(R.drawable.bg_wk_source_tag_ovr);
                label.setTextColor(ContextCompat.getColor(getContext(), R.color.wk_purple));
                break;
            case FULLSCREEN:
                label.setText(R.string.wk_ds_source_fs);
                setBackgroundResource(R.drawable.bg_wk_source_tag_kbd);
                label.setTextColor(ContextCompat.getColor(getContext(), R.color.wk_accent));
                break;
        }
    }

    @VisibleForTesting public String getTextForTest() { return label.getText().toString(); }
}
