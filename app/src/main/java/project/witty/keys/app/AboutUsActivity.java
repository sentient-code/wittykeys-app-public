package project.witty.keys.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;

import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

import project.witty.keys.R;

public class AboutUsActivity extends BaseActivity {

    private ImageView facebookIcon, twitterIcon, instagramIcon;
    private MaterialButton rateAppButton;
    private ReviewManager reviewManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);
        setupToolbar();
        showUserIcon(true, "abhishek8938@gmail.com", true);
        showBackButton(true);
        facebookIcon = findViewById(R.id.facebook_icon);
        twitterIcon = findViewById(R.id.youtube_icon);
        instagramIcon = findViewById(R.id.instagram_icon);
        rateAppButton = findViewById(R.id.rate_app_button);

        reviewManager = ReviewManagerFactory.create(this);

        facebookIcon.setOnClickListener(v -> openSocialMedia("https://www.facebook.com/[YourFacebookPage]"));
        twitterIcon.setOnClickListener(v -> openSocialMedia("https://twitter.com/[YourTwitterHandle]"));
        instagramIcon.setOnClickListener(v -> openSocialMedia("https://www.instagram.com/[YourInstagramHandle]"));

        rateAppButton.setOnClickListener(v -> launchReviewFlow());
    }

    private void openSocialMedia(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void launchReviewFlow() {
        Task<ReviewInfo> request = reviewManager.requestReviewFlow();
        request.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // We got the ReviewInfo object
                ReviewInfo reviewInfo = task.getResult();
                Task<Void> flow = reviewManager.launchReviewFlow(this, reviewInfo);
                flow.addOnCompleteListener(flowTask -> {
                    // The flow has finished. The user has either reviewed the app or not.
                    // You don't need to do anything here.
                });
            } else {
                // There was some problem, continue regardless of the result.
                // Log error or show a message to the user.
            }
        });
    }
}