package project.witty.keys.app; // Replace with your package

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout; // Changed from LinearLayout
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import project.witty.keys.R;

public class IntroFragment extends Fragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_SUBTITLE = "arg_subtitle";
    private static final String ARG_IMAGE = "arg_image";
    private static final String ARG_BG_COLOR = "arg_bg_color"; // New argument for background
    private TextView titleTextView;
    private TextView subtitleTextView;

    // Modified newInstance to accept background color
    public static IntroFragment newInstance(String title, String subtitle,
                                            @DrawableRes int imageResId,
                                            @ColorRes int bgColorResId) {
        IntroFragment fragment = new IntroFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        args.putInt(ARG_IMAGE, imageResId);
        args.putInt(ARG_BG_COLOR, bgColorResId); // Pass background color resource ID
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_intro, container, false);

        RelativeLayout rootLayout = view.findViewById(R.id.fragment_intro_root);
        titleTextView = view.findViewById(R.id.intro_title);
        subtitleTextView = view.findViewById(R.id.intro_subtitle);
        ImageView imageView = view.findViewById(R.id.intro_image);

        // Set text, image, background color - happens only once usually
        if (getArguments() != null) {
            String title = getArguments().getString(ARG_TITLE);
            String subtitle = getArguments().getString(ARG_SUBTITLE);
            int imageResId = getArguments().getInt(ARG_IMAGE);
            int bgColorResId = getArguments().getInt(ARG_BG_COLOR);

            if (titleTextView != null) titleTextView.setText(title);
            if (subtitleTextView != null) subtitleTextView.setText(subtitle);
            if (imageView != null) imageView.setImageResource(imageResId);

            if (getContext() != null && rootLayout != null) {
                rootLayout.setBackgroundColor(ContextCompat.getColor(getContext(), bgColorResId));
            } else {
                Log.e("IntroFragment", "Root layout or context is null in onCreateView.");
            }
        }

        // Ensure initial state from XML (alpha 0.0) is respected
        if (titleTextView != null) titleTextView.setAlpha(0.0f);
        if (subtitleTextView != null) subtitleTextView.setAlpha(0.0f);

        return view;
    }


    @Override
    public void onResume() {
        super.onResume();
        // Trigger animation when this fragment becomes the current, visible page
        startSlideFadeInAnimation(); // Call the updated animation method
    }

    @Override
    public void onPause() {
        super.onPause();
        // Reset views when fragment is no longer the primary one
        resetViewsForAnimation();
    }

    private void startSlideFadeInAnimation() {
        // Check if views are bound and fragment is attached
        if (titleTextView == null || subtitleTextView == null || !isAdded() || getContext() == null) {
            Log.w("IntroFragment", "Views not ready or fragment detached in startSlideFadeInAnimation");
            // Fallback: Make views instantly visible at final position
            if(titleTextView != null) { titleTextView.setTranslationX(0f); titleTextView.setAlpha(1.0f); }
            if(subtitleTextView != null) { subtitleTextView.setTranslationX(0f); subtitleTextView.setAlpha(1.0f); }
            return;
        }

        // Calculate distance to slide in from (e.g., half screen width)
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        // Start further off-screen for a more noticeable slide, adjust multiplier as needed
        float translationDistance = displayMetrics.widthPixels / 1.5f;

        long animationDuration = 700; // Slightly longer duration might look better
        long titleDelay = 100;       // Delay for title
        long subtitleDelay = 150;   // Slightly later delay for subtitle (stagger effect)

        // --- Animate Title ---
        // 1. Set initial state: invisible and off-screen to the right
        titleTextView.setAlpha(0.0f);
        titleTextView.setTranslationX(translationDistance);
        // 2. Animate to final state
        titleTextView.animate()
                .alpha(1.0f)            // Fade in
                .translationX(0f)       // Slide to original X position
                .setDuration(animationDuration)
                .setStartDelay(titleDelay)
                .setInterpolator(new DecelerateInterpolator(1.5f)) // Smoother easing
                .setListener(null);     // Clear listener

        // --- Animate Subtitle ---
        // 1. Set initial state: invisible and off-screen to the right
        subtitleTextView.setAlpha(0.0f);
        subtitleTextView.setTranslationX(translationDistance);
        // 2. Animate to final state
        subtitleTextView.animate()
                .alpha(1.0f)            // Fade in
                .translationX(0f)       // Slide to original X position
                .setDuration(animationDuration)
                .setStartDelay(subtitleDelay) // Start slightly after title
                .setInterpolator(new DecelerateInterpolator(1.5f)) // Smoother easing
                .setListener(null);     // Clear listener
    }

    private void resetViewsForAnimation() {
        // Stop any ongoing animations on these views and reset alpha to 0.
        // No need to reset translationX here, as startSlideFadeInAnimation sets the
        // initial translation explicitly each time.
        if (titleTextView != null) {
            titleTextView.animate().cancel(); // Stop ongoing animation
            titleTextView.setAlpha(0.0f);     // Reset alpha
        }
        if (subtitleTextView != null) {
            subtitleTextView.animate().cancel(); // Stop ongoing animation
            subtitleTextView.setAlpha(0.0f);     // Reset alpha
        }
    }
}