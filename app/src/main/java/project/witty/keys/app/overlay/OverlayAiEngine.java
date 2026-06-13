package project.witty.keys.app.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import project.witty.keys.BuildConfig;
import project.witty.keys.keyboard.AiChat.AiMessage;
import project.witty.keys.keyboard.AiChat.ChatItem;
import project.witty.keys.keyboard.AiChat.UserMessage;
import project.witty.keys.app.entitlements.AiActionType;
import project.witty.keys.app.entitlements.AiEntitlementDecision;
import project.witty.keys.app.entitlements.AiEntitlementManager;

/**
 * OverlayAiEngine — Build 7.1 MVP
 *
 * Handles AI message exchange for the overlay chat panel.
 * Sends follow-up questions about a captured screenshot to Claude.
 */
public class OverlayAiEngine {

    private static final String TAG = "WK_OVERLAY_AI";
    private static final String API_URL = BuildConfig.API_BASE_URL + "/proxyClaudeHttp";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final int MAX_TOKENS = 300;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient;

    private String cachedBase64Image;
    private String cachedImagePath;

    public interface ResponseCallback {
        void onResponse(String response);
        void onError(String error);
    }

    public OverlayAiEngine(Context context) {
        // Fix Task 8.2: Use ApplicationContext to prevent context leaks
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    public void sendFollowUp(String userMessage, String imagePath, String initialAnalysis,
                              List<ChatItem> chatHistory, ResponseCallback callback) {

        AiEntitlementManager entitlements = AiEntitlementManager.getInstance(context);
        AiEntitlementDecision decision = entitlements.canRun(AiActionType.SCREEN_AI);
        if (!decision.allowed) {
            callback.onError(decision.userMessage);
            return;
        }

        executor.execute(() -> {
            try {
                JSONArray messages = buildMessages(userMessage, imagePath, initialAnalysis, chatHistory);

                JSONObject requestBody = new JSONObject();
                requestBody.put("model", MODEL);
                requestBody.put("max_tokens", MAX_TOKENS);
                requestBody.put("system", "You are WittyKeys AI, a mobile keyboard assistant. "
                    + "Be concise: 2-3 sentences for answers. "
                    + "No markdown headers, no bullet lists, no numbered lists. "
                    + "Format for mobile: short paragraphs, easy to scan on a small screen. "
                    + "If drafting a reply, write exactly as user would send it — nothing else. "
                    + "If analyzing a screenshot, describe ONLY what's actionable. "
                    + "NEVER reproduce text verbatim from screenshots.");
                requestBody.put("messages", messages);

                Request request = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestBody.toString()))
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError("API error: " + response.code());
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    String aiText = parseResponse(responseBody);

                    if (aiText != null && !aiText.isEmpty()) {
                        entitlements.record(AiActionType.SCREEN_AI);
                        callback.onResponse(aiText);
                    } else {
                        callback.onError("Empty response from AI");
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "AI request failed: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        });
    }

    private JSONArray buildMessages(String userMessage, String imagePath,
                                     String initialAnalysis, List<ChatItem> chatHistory) throws Exception {

        JSONArray messages = new JSONArray();

        // Message 1: Screenshot + context
        JSONObject firstMessage = new JSONObject();
        firstMessage.put("role", "user");

        JSONArray firstContent = new JSONArray();

        String base64 = getBase64Image(imagePath);
        if (base64 != null) {
            JSONObject imageBlock = new JSONObject();
            imageBlock.put("type", "image");
            JSONObject source = new JSONObject();
            source.put("type", "base64");
            source.put("media_type", "image/jpeg");
            source.put("data", base64);
            imageBlock.put("source", source);
            firstContent.put(imageBlock);
        }

        JSONObject textBlock = new JSONObject();
        textBlock.put("type", "text");
        textBlock.put("text", "I captured this screenshot. Here's what I see.");
        firstContent.put(textBlock);

        firstMessage.put("content", firstContent);
        messages.put(firstMessage);

        // Message 2: Assistant's initial analysis
        if (initialAnalysis != null && !initialAnalysis.isEmpty()) {
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", initialAnalysis);
            messages.put(assistantMsg);
        }

        // Messages 3+: Conversation history
        if (chatHistory != null) {
            for (ChatItem item : chatHistory) {
                if (item instanceof UserMessage) {
                    JSONObject msg = new JSONObject();
                    msg.put("role", "user");
                    msg.put("content", ((UserMessage) item).getText());
                    messages.put(msg);
                } else if (item instanceof AiMessage) {
                    JSONObject msg = new JSONObject();
                    msg.put("role", "assistant");
                    msg.put("content", ((AiMessage) item).getMarkdownText());
                    messages.put(msg);
                }
            }
        }

        // Final: current user question
        JSONObject currentMsg = new JSONObject();
        currentMsg.put("role", "user");
        currentMsg.put("content", userMessage);
        messages.put(currentMsg);

        return messages;
    }

    private String getBase64Image(String imagePath) {
        if (imagePath == null) return null;

        if (imagePath.equals(cachedImagePath) && cachedBase64Image != null) {
            return cachedBase64Image;
        }

        try {
            File file = new File(imagePath);
            if (!file.exists()) return null;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            int maxDim = Math.max(options.outWidth, options.outHeight);
            int sampleSize = 1;
            while (maxDim / sampleSize > 1568) {
                sampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
            if (bitmap == null) return null;

            // Fix Task 8.5: Wrap bitmap compression in try-finally to ensure recycling
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                cachedBase64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                cachedImagePath = imagePath;
                return cachedBase64Image;
            } finally {
                bitmap.recycle();
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to encode image: " + e.getMessage());
            return null;
        }
    }

    private String parseResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            // Anthropic format
            if (json.has("content")) {
                JSONArray content = json.getJSONArray("content");
                if (content.length() > 0) {
                    JSONObject first = content.getJSONObject(0);
                    if ("text".equals(first.optString("type"))) {
                        return first.getString("text");
                    }
                }
            }

            // OpenAI-compatible format
            if (json.has("choices")) {
                JSONArray choices = json.getJSONArray("choices");
                if (choices.length() > 0) {
                    return choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse response: " + e.getMessage());
        }
        return null;
    }

    /**
     * Shutdown the executor service (Task 8.3 — prevent ExecutorService leak).
     * Call from OverlayChatPanel.hide() and service onDestroy().
     */
    public void shutdown() {
        try {
            executor.shutdownNow();
        } catch (Exception ignored) {
            Log.w(TAG, "Error shutting down executor: " + ignored.getMessage());
        }
    }
}
