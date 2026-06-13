package project.witty.keys.app;

import android.content.Intent;
import android.os.Bundle;

import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.launch.LaunchStateActivity;
import project.witty.keys.app.state.AccountEntitlementSnapshotProvider;

public class UserProfileActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EncryptedPreferences.initialize(this);
        AccountEntitlementSnapshotProvider.current(this);

        User user = EncryptedPreferences.getUserLoggedInInfo();
        Intent intent = new Intent(this, LaunchStateActivity.class);
        intent.putExtra(
                LaunchStateActivity.EXTRA_STATE,
                user == null
                        ? LaunchStateActivity.STATE_ACCOUNT_SIGNIN_REASON
                        : LaunchStateActivity.STATE_ACCOUNT_PROFILE_SIGNED_IN);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
