// YoutubeSearchApi.java
package project.witty.keys.api;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import java.io.IOException;

public class YoutubeSearchApi {
    // Public showcase note: direct client-side API keys are intentionally not
    // included. Production API calls should go through a backend proxy.
    private static final String API_KEY = "";
    private static final String PART = "snippet";
    private static final String MAX_RESULTS = "10";
    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3/search?part=";
    private static final String type = "video";
    private OkHttpClient client = new OkHttpClient();

    public void getSearchResults(String query, Callback callback) {
        if (API_KEY.isEmpty()) {
            callback.onFailure(null, new IOException("YouTube search is disabled in the public showcase build."));
            return;
        }
        String url = BASE_URL + PART + "&maxResults=" + MAX_RESULTS + "&q=" + query + "&type="+ type + "&key=" + API_KEY;
        Request request = new Request.Builder().url(url).build();
        Call call = client.newCall(request);
        call.enqueue(callback);
    }
}
