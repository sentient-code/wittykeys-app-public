package project.witty.keys.api;

import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import project.witty.keys.BuildConfig;
import project.witty.keys.app.helpers.DebugConfig;

/**
 * ChatGPTApi - SECURE VERSION using HTTP Firebase Functions
 *
 * NO AUTHENTICATION REQUIRED - works during tutorial too!
 * API key is stored on Firebase server, NOT in the app.
 */
public class ChatGPTApi {

    private static final String TAG = "ChatGPTApi";

    // API_BASE_URL is configured in build.gradle:
    // - debug: http://10.0.2.2:5001/tapai-e33d2/us-central1 (emulator localhost)
    // - release: https://us-central1-tapai-e33d2.cloudfunctions.net (production)
    // Routed through Claude proxy (OpenAI-compatible response format)
    private static final String PROXY_URL = BuildConfig.API_BASE_URL + "/proxyClaudeHttp";
    private static final String PROXY_VISION_URL = BuildConfig.API_BASE_URL + "/proxyOpenAIVisionHttp";

    private final OkHttpClient client;

    public ChatGPTApi() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🔐 ChatGPTApi initialized (SECURE HTTP - no auth required)");
        }
    }

    /**
     * Get ChatGPT response via Firebase HTTP Function proxy
     * Same signature as original - no changes needed in calling code!
     */
    /**
     * Get ChatGPT response via Firebase HTTP Function proxy.
     * Includes 1 automatic retry after 3 seconds on network failure.
     */
    public void getChatGPTResponseForConversation(List<JSONObject> conversationHistory, Callback callback) {
        getChatGPTResponseForConversation(conversationHistory, null, callback);
    }

    public void getChatGPTResponseForConversation(List<JSONObject> conversationHistory,
            @Nullable String systemPrompt, Callback callback) {
        getChatGPTResponseWithRetry(conversationHistory, systemPrompt, callback, 0);
    }

    private static final int MAX_RETRIES = 1;
    private static final long RETRY_DELAY_MS = 3000;

    private void getChatGPTResponseWithRetry(List<JSONObject> conversationHistory,
            @Nullable String systemPrompt, Callback callback, int retryCount) {
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🚀 getChatGPTResponse - messages: " + conversationHistory.size() + (retryCount > 0 ? " (retry #" + retryCount + ")" : ""));
        }

        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("messages", new JSONArray(conversationHistory.toString()));
            requestJson.put("model", "claude-haiku-4-5-20251001");
            requestJson.put("maxTokens", 1024);
            requestJson.put("temperature", 0.5);
            requestJson.put("topP", 1.0);
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                requestJson.put("system", systemPrompt);
            }

            RequestBody body = RequestBody.create(
                    requestJson.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(PROXY_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "📤 Sending request to: " + PROXY_URL);
            }

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (retryCount < MAX_RETRIES) {
                        Log.w(TAG, "⚠️ Request failed, retrying in " + RETRY_DELAY_MS + "ms (attempt " + (retryCount + 1) + ")");
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            getChatGPTResponseWithRetry(conversationHistory, systemPrompt, callback, retryCount + 1);
                        }, RETRY_DELAY_MS);
                    } else {
                        Log.e(TAG, "❌ HTTP request failed after " + (retryCount + 1) + " attempts", e);
                        callback.onFailure(call, e);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (DebugConfig.isDebugMode) {
                        Log.d(TAG, "✅ Response received: " + response.code());
                    }

                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        Log.e(TAG, "❌ HTTP error: " + response.code()
                                + " body_length=" + errorBody.length());

                        // Retry on 5xx server errors
                        if (response.code() >= 500 && retryCount < MAX_RETRIES) {
                            Log.w(TAG, "⚠️ Server error " + response.code() + ", retrying in " + RETRY_DELAY_MS + "ms");
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                getChatGPTResponseWithRetry(conversationHistory, systemPrompt, callback, retryCount + 1);
                            }, RETRY_DELAY_MS);
                            return;
                        }

                        callback.onFailure(call, new IOException("HTTP " + response.code()));
                        return;
                    }

                    callback.onResponse(call, response);
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "❌ JSON error", e);
            callback.onFailure(null, new IOException("JSON error: " + e.getMessage()));
        }
    }

    /**
     * DeepSeek API - placeholder
     */
    public void getDeepSeekResponseForConversation(List<JSONObject> conversationHistory, Callback callback) {
        Log.w(TAG, "DeepSeek API not available via secure proxy");
        callback.onFailure(null, new IOException("DeepSeek not available"));
    }

    // =========================================================================
    // HELPER: Get the proxy URL (useful for ScreenCaptureService)
    // =========================================================================

    public static String getProxyUrl() {
        return PROXY_URL;
    }

    public static String getProxyVisionUrl() {
        return PROXY_VISION_URL;
    }
}
