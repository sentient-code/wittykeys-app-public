package project.witty.keys.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import project.witty.keys.R;

public class BaseActivity extends AppCompatActivity {

    protected Toolbar toolbar;
    protected LinearLayout toolbarContentStart;
    protected LinearLayout toolbarContentEnd;
    protected ImageView toolbarLogo;
    protected ImageView toolbarBack;
    protected TextView toolbarTitle;
    protected ImageView toolbarUserIcon, toolbarUserStartIcon, toolbarSettingsIcon;
    protected TextView toolbarUserStartId;
    protected TextView toolbarEndText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        toolbarContentStart = findViewById(R.id.toolbar_content_start);
        toolbarContentEnd = findViewById(R.id.toolbar_content_end);
        toolbarLogo = findViewById(R.id.toolbar_logo);
        toolbarBack = findViewById(R.id.toolbar_back);
        toolbarTitle = findViewById(R.id.toolbar_title);
        toolbarUserIcon = findViewById(R.id.toolbar_user_icon);
        toolbarEndText = findViewById(R.id.toolbar_end_text);
        toolbarUserStartId = findViewById(R.id.toolbar_user_id_start);
        toolbarUserStartIcon = findViewById(R.id.toolbar_user_icon_start);
        toolbarSettingsIcon = findViewById(R.id.toolbar_settings_icon);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    protected void hideToolBar() {
        toolbar.setVisibility(View.GONE);
    }

    protected void showBackButton(boolean show) {
        toolbarLogo.setVisibility(show ? View.GONE : View.VISIBLE);
        toolbarBack.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            toolbarBack.setOnClickListener(v -> onBackPressed());
        }
    }

    protected void showLogo(boolean show) {
        toolbarLogo.setVisibility(show ? View.VISIBLE : View.GONE);
        toolbarBack.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    protected void setToolbarTitle(String title) {
        toolbarTitle.setVisibility(View.VISIBLE);
        toolbarTitle.setText(title);
    }

    protected void showUserIcon(boolean show, String userId, boolean start) {
        if (start) {
            toolbarUserStartIcon.setVisibility(show ? View.VISIBLE : View.GONE);
            toolbarUserStartId.setVisibility(show ? View.VISIBLE : View.GONE);
            toolbarUserStartId.setText(userId);
        } else {
            toolbarUserIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        }

    }

    protected void showUserIconOnly(boolean show, boolean start, ImageView.OnClickListener listener) {
        if (start) {
            toolbarUserStartIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        } else {
            toolbarUserIcon.setVisibility(show ? View.VISIBLE : View.GONE);
            toolbarUserIcon.setOnClickListener(listener);
        }

    }

    protected void showSettingIcon(boolean show, ImageView.OnClickListener listener) {
        toolbarSettingsIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        toolbarSettingsIcon.setOnClickListener(listener);

    }

    protected void showEndText(boolean show, TextView.OnClickListener listener) {
        toolbarEndText.setVisibility(show ? View.VISIBLE : View.GONE);
        SpannableString content = new SpannableString("Skip");
        content.setSpan(new UnderlineSpan(), 0, "Skip".length(), 0);
        toolbarEndText.setText(content);
        toolbarEndText.setOnClickListener(listener);
    }
}