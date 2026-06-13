package project.witty.keys.ui.chat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import project.witty.keys.R;

public class WkDualCtaRow extends FrameLayout {

    public interface OnCtaClickListener { void onClick(); }

    private TextView primary;
    private TextView ghost;

    public WkDualCtaRow(Context context) {
        super(context);
        init(context);
    }

    public WkDualCtaRow(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.wk_ds_dual_cta_row, this, true);
        primary = findViewById(R.id.wkCtaPrimary);
        ghost = findViewById(R.id.wkCtaGhost);
    }

    public void setPrimary(CharSequence label, OnCtaClickListener l) {
        primary.setText(label);
        primary.setOnClickListener(v -> { if (l != null) l.onClick(); });
    }

    public void setGhost(CharSequence label, OnCtaClickListener l) {
        ghost.setText(label);
        ghost.setOnClickListener(v -> { if (l != null) l.onClick(); });
    }

    public void setPrimaryIcon(@DrawableRes int iconRes) {
        if (iconRes == 0) { primary.setCompoundDrawablesRelative(null, null, null, null); return; }
        Drawable icon = ContextCompat.getDrawable(getContext(), iconRes);
        if (icon == null) return;
        icon = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTint(icon, ContextCompat.getColor(getContext(), R.color.wk_green));
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        primary.setCompoundDrawablesRelative(icon, null, null, null);
    }

    public void setGhostIcon(@DrawableRes int iconRes) {
        if (iconRes == 0) { ghost.setCompoundDrawablesRelative(null, null, null, null); return; }
        Drawable icon = ContextCompat.getDrawable(getContext(), iconRes);
        if (icon == null) return;
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        ghost.setCompoundDrawablesRelative(icon, null, null, null);
    }

    @VisibleForTesting public String getPrimaryLabelForTest() { return primary.getText().toString(); }
    @VisibleForTesting public String getGhostLabelForTest() { return ghost.getText().toString(); }
    @VisibleForTesting public void clickPrimaryForTest() { primary.performClick(); }
    @VisibleForTesting public void clickGhostForTest() { ghost.performClick(); }
}
