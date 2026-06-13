package project.witty.keys.app;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import project.witty.keys.R;

public class ImageCarouselBottomSheetFragment extends BottomSheetDialogFragment {

    private static final int[] IMAGES = {R.drawable.feature_image_1, R.drawable.feature_image_2, R.drawable.feature_image_3, R.drawable.feature_image_4, R.drawable.feature_image_5, R.drawable.feature_image_6,R.drawable.feature_image_7};
    private Handler handler = new Handler();
    private Runnable runnable;
    private ViewPager2 viewPager;
    private View backButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_image_carousel_bottom_sheet, container, false);

        viewPager = view.findViewById(R.id.view_pager_how_to_use);
        ImageCarouselAdapter adapter = new ImageCarouselAdapter(IMAGES, R.layout.carousel_features_item);
        backButton = view.findViewById(R.id.how_to_use_back);
        backButton.setOnClickListener(v -> dismiss());
        viewPager.setAdapter(adapter);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        View view = getView();
        if (view != null) {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.9);
            view.setLayoutParams(layoutParams);
        }

        runnable = new Runnable() {
            @Override
            public void run() {
                int currentItem = viewPager.getCurrentItem();
                int nextItem = (currentItem + 1) % IMAGES.length;
                viewPager.setCurrentItem(nextItem, true);
                handler.postDelayed(this, 3000); // Change image every 5 seconds
            }
        };
        handler.postDelayed(runnable, 3000);
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(runnable);
    }
}