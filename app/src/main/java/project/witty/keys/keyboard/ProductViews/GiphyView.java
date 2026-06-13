package project.witty.keys.keyboard.ProductViews;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.giphy.sdk.core.models.Media;
import com.giphy.sdk.core.models.enums.MediaType;
import com.giphy.sdk.core.models.enums.RatingType;
import com.giphy.sdk.ui.Giphy;
import com.giphy.sdk.ui.pagination.GPHContent;
import com.giphy.sdk.ui.themes.GPHTheme;
import com.giphy.sdk.ui.views.GiphyGridView;
import com.giphy.sdk.ui.views.GPHGridCallback;

import project.witty.keys.R;
import project.witty.keys.latin.LatinIME;

public class GiphyView extends FrameLayout {

    private LatinIME mLatinIme;
    private GiphyGridView gridView;

    // Add a flag to track initialization
    private boolean isInitialized = false;

    public GiphyView(Context context) {
        super(context);
        init(context);
    }

    public GiphyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // The init method will now be very lightweight
        init(context);
    }

    private void init(Context context) {
        // Just inflate the layout. Do NOT configure the grid here.
        inflate(context, R.layout.giphy_view, this);
        gridView = findViewById(R.id.giphy_grid_view);
    }

    // This is our new public setup method
    public void initializeAndLoadContent() {
        // If we've already run this, do nothing.
        if (isInitialized) {
            return;
        }

        Log.d("WittyKeys_GiphyView", "Initializing GIPHY grid for the first time.");
        // --- All the setup logic is moved here ---
        gridView.setTheme(GPHTheme.Light);
        gridView.setCallback(new GPHGridCallback() {
            @Override
            public void contentDidUpdate(int resultCount) {
                Log.d("WittyKeys_GiphyView", "GIPHY content updated. Result count: " + resultCount);

            }

            @Override
            public void didSelectMedia(@NonNull Media media) {
                if (mLatinIme != null) {
                    mLatinIme.onGiphyMediaSelected(media);
                }
            }
        });

        // Load trending GIFs by default when first initialized
        performSearch("");

        // Mark as initialized so this code doesn't run again.
        isInitialized = true;
    }

    public void performSearch(String query) {
        // Safety check in case this is called before initialization
        if (!isInitialized) {
            Log.w("WittyKeys_GiphyView", "Search called before view was initialized. Ignoring.");
            return;
        }
        Log.d("WittyKeys_GiphyView", "Performing GIPHY search for query: '" + query + "'");

        GPHContent contentRequest;
        if (query == null || query.trim().isEmpty()) {
            contentRequest = GPHContent.Companion.trending(MediaType.gif, RatingType.pg13);
        } else {
            contentRequest = GPHContent.Companion.searchQuery(query, MediaType.gif, RatingType.pg13);
        }
        gridView.setContent(contentRequest);
    }

    public void setLatinIme(LatinIME ime) {
        mLatinIme = ime;
    }
}