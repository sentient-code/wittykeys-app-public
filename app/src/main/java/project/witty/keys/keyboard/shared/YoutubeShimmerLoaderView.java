// YoutubeShimmerLoaderView.java
package project.witty.keys.keyboard.shared;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import com.facebook.shimmer.ShimmerFrameLayout;

import project.witty.keys.R;

public class YoutubeShimmerLoaderView extends FrameLayout {
    private ShimmerFrameLayout shimmerFrameLayout;

    public YoutubeShimmerLoaderView(Context context) {
        super(context);
        init(context);
    }

    public YoutubeShimmerLoaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public YoutubeShimmerLoaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.youtube_shimmer_loader, this, true);
        shimmerFrameLayout = findViewById(R.id.shimmer_view_container);
    }

    public void startShimmer() {
        shimmerFrameLayout.startShimmer();
    }

    public void stopShimmer() {
        shimmerFrameLayout.stopShimmer();
    }
}