package project.witty.keys.ui.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import project.witty.keys.R;

public class WkEmptyState extends FrameLayout {

    private ImageView icon;
    private TextView title;
    private TextView sub;
    private WkDualCtaRow cta;

    public WkEmptyState(Context context) {
        super(context);
        init(context);
    }

    public WkEmptyState(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_empty_state, this, true);
        icon = findViewById(R.id.wkEmptyIcon);
        title = findViewById(R.id.wkEmptyTitle);
        sub = findViewById(R.id.wkEmptySub);
        cta = findViewById(R.id.wkEmptyCta);
    }

    public void setIcon(@DrawableRes int iconRes) {
        if (icon != null) icon.setImageResource(iconRes);
    }

    public void bind(CharSequence titleText, CharSequence subText) {
        title.setText(titleText);
        sub.setText(subText);
        cta.setVisibility(GONE);
    }

    public void showCta(CharSequence primaryLabel, WkDualCtaRow.OnCtaClickListener primaryListener,
                        CharSequence ghostLabel, WkDualCtaRow.OnCtaClickListener ghostListener) {
        cta.setPrimary(primaryLabel, primaryListener);
        cta.setGhost(ghostLabel, ghostListener);
        cta.setVisibility(VISIBLE);
    }

    @VisibleForTesting public String getTitleForTest() { return title.getText().toString(); }
    @VisibleForTesting public String getSubForTest() { return sub.getText().toString(); }
    @VisibleForTesting public int getCtaVisibilityForTest() { return cta.getVisibility(); }
}
