package project.witty.keys.app.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;

import project.witty.keys.BuildConfig;
import project.witty.keys.R;
import project.witty.keys.app.BaseActivity;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.NotchHandler;
import project.witty.keys.app.launch.LaunchStateActivity;
import project.witty.keys.app.state.AccountEntitlementSnapshot;
import project.witty.keys.app.state.AccountEntitlementSnapshotProvider;

public class SettingsHubActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotchHandler.configureEdgeToEdge(this);
        setContentView(R.layout.activity_settings_hub);
        NotchHandler.handleSystemBars(this);
        EncryptedPreferences.initialize(this);
        TextView versionText = findViewById(R.id.settings_version_text);
        if (versionText != null) {
            versionText.setText("Version " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        }

        bindRow(R.id.settings_profile_button, this::openAccountLaunchDetail);
        bindRow(R.id.settings_account_row, this::openAccountLaunchDetail);
        bindRow(R.id.settings_subscription_row, () -> openLaunchDetail(LaunchStateActivity.STATE_SUBSCRIPTION_PLUS_OFFER));
        bindRow(R.id.settings_ai_usage_row, () -> openLaunchDetail(LaunchStateActivity.STATE_AI_USAGE));
        bindRow(R.id.settings_app_setup_row, () -> openLaunchDetail(LaunchStateActivity.STATE_APP_SETUP));
        bindRow(R.id.settings_help_privacy_row, () -> openLaunchDetail(LaunchStateActivity.STATE_SUPPORT));
        bindBottomNavigation();
    }

    private void bindRow(int id, Runnable action) {
        View row = findViewById(id);
        if (row != null) {
            row.setOnClickListener(v -> action.run());
        }
    }

    private void openLaunchDetail(String state) {
        AccountEntitlementSnapshot snapshot = AccountEntitlementSnapshotProvider.current(this);
        Intent intent = new Intent(this, LaunchStateActivity.class);
        intent.putExtra(LaunchStateActivity.EXTRA_STATE, state);
        intent.putExtra("allowanceDisplay", snapshot.allowanceDisplay);
        startActivity(intent);
    }

    private void openKeyboardSettings() {
        Intent intent = new Intent(this, project.witty.keys.latin.settings.SettingsActivity.class);
        startActivity(intent);
    }

    private void bindBottomNavigation() {
        bindRow(R.id.settings_nav_home, this::openHomeTab);
        bindRow(R.id.settings_nav_usage, this::openUsageTab);
        bindRow(R.id.settings_nav_settings, this::openSettingsTab);
    }

    private void openHomeTab() {
        Intent intent = new Intent(this, project.witty.keys.app.HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startBottomTabActivity(intent);
    }

    private void openUsageTab() {
        Intent intent = new Intent(this, LaunchStateActivity.class);
        intent.putExtra(LaunchStateActivity.EXTRA_STATE, LaunchStateActivity.STATE_AI_USAGE);
        startBottomTabActivity(intent);
    }

    private void openSettingsTab() {
        View content = findViewById(android.R.id.content);
        if (content != null) {
            content.post(() -> content.scrollTo(0, 0));
        }
    }

    private void startBottomTabActivity(Intent intent) {
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openAccountLaunchDetail() {
        User user = getValidatedAccountUser();
        openLaunchDetail(user == null
                ? LaunchStateActivity.STATE_ACCOUNT_SIGNIN_REASON
                : LaunchStateActivity.STATE_ACCOUNT_PROFILE_SIGNED_IN);
    }

    private User getValidatedAccountUser() {
        User user = EncryptedPreferences.getUserLoggedInInfo();
        if (user == null) {
            return null;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            EncryptedPreferences.clearUserInfo();
            EncryptedPreferences.clearSubscriptionInfo();
            return null;
        }
        return user;
    }

}
