package project.witty.keys.app.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import project.witty.keys.BuildConfig;
import project.witty.keys.app.database.SessionScreenshot;
import project.witty.keys.app.database.WittyKeysDatabase;

/**
 * ScreenshotAnalyzer — Build 7.0 Phase 3
 *
 * Analyzes captured screenshots using Claude Haiku Vision API.
 * Extracted from Build 6.3's ScreenCaptureService for clean separation.
 *
 * Flow: JPEG file -> resize (1568px max) -> compress (q80) -> base64 -> Claude Vision -> analysis text
 *
 * CONTRACT (P3 -> P4):
 * - analyze(context, imagePath, conversationContext, callback) — async, calls back on main thread
 * - AnalysisCallback { onSuccess(String analysis), onError(String error) }
 * - Saves screenshot to Room database (SessionScreenshot) before analysis
 */
public class ScreenshotAnalyzer {

    private static final String TAG = "ScreenshotAnalyzer";
    private static final int MAX_DIMENSION = 1568; // Max edge for Claude Vision
    private static final int JPEG_QUALITY = 80;
    private static final String PROXY_URL = BuildConfig.API_BASE_URL + "/proxyClaudeHttp";
    private static final int MAX_RETRIES = 1;
    private static final long RETRY_DELAY_MS = 2000;

    private static ScreenshotAnalyzer instance;
    private final ExecutorService executor;
    private final OkHttpClient httpClient;

    public interface AnalysisCallback {
        void onSuccess(String analysis);
        void onError(String error);
    }

    private ScreenshotAnalyzer() {
        executor = Executors.newSingleThreadExecutor();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS) // Vision calls can be slow
                .writeTimeout(60, TimeUnit.SECONDS)  // Large base64 payloads
                .build();
    }

    public static synchronized ScreenshotAnalyzer getInstance() {
        if (instance == null) {
            instance = new ScreenshotAnalyzer();
        }
        return instance;
    }

    /**
     * Analyze a screenshot using Claude Haiku Vision.
     *
     * @param context Android context for database access
     * @param imagePath Absolute path to the captured JPEG file
     * @param conversationContext NLS conversation context (recent messages, contact name)
     * @param sessionId Room ChatSession ID to associate screenshot with (0 if no session)
     * @param callback Called on main thread with analysis result or error
     */
    public void analyze(Context context, String imagePath, String conversationContext,
                        long sessionId, AnalysisCallback callback) {
        // JourneyTracer: screenshot analysis started
        final String traceId = JourneyTracer.start(JourneyTracer.Journey.SCREENSHOT_AI);
        try {
            JSONObject dataIn = new JSONObject();
            dataIn.put("image_present", imagePath != null && !imagePath.isEmpty());
            dataIn.put("session_id", sessionId);
            JourneyTracer.step(traceId, "SCREENSHOT_CAPTURED", dataIn, null, "screenshot ready for analysis");
        } catch (Exception ignored) {}

        executor.submit(() -> {
            try {
                Log.d(TAG, "[ANALYZE] Starting analysis: image_present="
                        + (imagePath != null && !imagePath.isEmpty()));

                // 1. Load and resize bitmap
                Bitmap original = BitmapFactory.decodeFile(imagePath);
                if (original == null) {
                    postError(callback, "Failed to decode image.");
                    return;
                }

                Bitmap resized = resizeBitmap(original, MAX_DIMENSION);
                int finalWidth = resized.getWidth();
                int finalHeight = resized.getHeight();
                if (resized != original) {
                    original.recycle();
                }
                Log.d(TAG, "[ANALYZE] Resized to: " + finalWidth + "x" + finalHeight);

                // 2. Compress to JPEG and encode as base64
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
                String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                int imageSizeKb = baos.size() / 1024;
                Log.d(TAG, "[ANALYZE] Compressed to " + imageSizeKb + "KB base64");
                resized.recycle();
                baos.close();

                // 3. Save screenshot record to Room database
                if (sessionId > 0) {
                    try {
                        SessionScreenshot screenshot = new SessionScreenshot();
                        screenshot.sessionId = sessionId;
                        screenshot.filePath = imagePath;
                        screenshot.capturedAt = System.currentTimeMillis();
                        screenshot.width = finalWidth;
                        screenshot.height = finalHeight;
                        WittyKeysDatabase.getInstance(context).screenshotDao().insert(screenshot);
                        Log.d(TAG, "[ANALYZE] Screenshot saved to Room DB for session " + sessionId);
                    } catch (Exception dbErr) {
                        Log.w(TAG, "[ANALYZE] Failed to save screenshot to DB (non-fatal): " + dbErr.getMessage());
                    }
                }

                // 4. Build Claude Vision API request with retry on 5xx/network errors
                String analysis = null;
                Exception lastError = null;
                // JourneyTracer: vision API call
                JourneyTracer.step(traceId, "VISION_API_CALLED", null, null, "sending to Claude Vision");

                for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                    try {
                        if (attempt > 0) {
                            Log.d(TAG, "[ANALYZE] Retry attempt " + (attempt + 1) + " after " + RETRY_DELAY_MS + "ms");
                            Thread.sleep(RETRY_DELAY_MS);
                        }
                        long startMs = System.currentTimeMillis();
                        analysis = callClaudeVision(base64Image, conversationContext);
                        long latencyMs = System.currentTimeMillis() - startMs;
                        Log.d(TAG, "[ANALYZE] Claude Vision returned in " + latencyMs + "ms");
                        break; // Success — exit retry loop
                    } catch (Exception e) {
                        lastError = e;
                        String msg = e.getMessage() != null ? e.getMessage() : "";
                        // Only retry on network/5xx errors, not 4xx (our fault)
                        if (msg.contains("HTTP 4")) {
                            Log.e(TAG, "[ANALYZE] 4xx error - not retrying");
                            break;
                        }
                        Log.w(TAG, "[ANALYZE] Attempt " + (attempt + 1)
                                + " failed: error_present=" + !msg.isEmpty());
                    }
                }

                // 5. Return result or structured error
                if (analysis != null && !analysis.isEmpty()) {
                    // JourneyTracer: vision response success
                    try {
                        JSONObject dataOut = new JSONObject();
                        dataOut.put("analysis_length", analysis.length());
                        JourneyTracer.step(traceId, "VISION_RESPONSE", null, dataOut, "analysis complete");
                        JourneyTracer.complete(traceId, true);
                    } catch (Exception ignored) {}
                    postSuccess(callback, analysis);
                } else {
                    String errorMsg = lastError != null ? lastError.getMessage() : "empty analysis";
                    Log.e(TAG, "[ANALYZE] All retries exhausted: error_present="
                            + (errorMsg != null && !errorMsg.isEmpty()));
                    JourneyTracer.error(traceId, "VISION_EXHAUSTED", "retries_failed", "analysis_unavailable");
                    JourneyTracer.complete(traceId, false);
                    try {
                        JSONObject errorResult = new JSONObject();
                        errorResult.put("error", true);
                        errorResult.put("message", "Screenshot analysis unavailable. Try again.");
                        postError(callback, errorResult.toString());
                    } catch (JSONException je) {
                        postError(callback, "Analysis failed: " + errorMsg);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "[ANALYZE] Error during screenshot analysis", e);
                JourneyTracer.error(traceId, "VISION_ERROR", "exception", "analysis_exception");
                JourneyTracer.complete(traceId, false);
                postError(callback, "Analysis failed.");
            }
        });
    }

    /**
     * Call Claude Haiku Vision API via Firebase proxy.
     * Uses proxyClaudeHttp with image content blocks (Anthropic Messages API format).
     */
    private String callClaudeVision(String base64Image, String conversationContext) throws Exception {
        // Build system prompt — concise mobile-first analysis
        String systemPrompt = "You are WittyKeys AI, a mobile keyboard assistant. "
                + "The user captured a screenshot. Analyze it concisely in 2-3 sentences MAX. "
                + "Focus ONLY on: (1) What app/screen is shown, (2) The key actionable content. "
                + "If it's a conversation: who is talking and what's the latest topic. "
                + "If it's a notification: what action is needed (ignore boilerplate/legal text). "
                + "If it's content: summarize the main point only. "
                + "NEVER reproduce long text verbatim. NEVER list every detail you see. "
                + "Format for mobile: short sentences, no headers, no bullet lists, no markdown.";

        // Build user message with image + text content blocks
        JSONArray userContent = new JSONArray();

        // Image content block (Anthropic format)
        JSONObject imageBlock = new JSONObject();
        imageBlock.put("type", "image");
        JSONObject imageSource = new JSONObject();
        imageSource.put("type", "base64");
        imageSource.put("media_type", "image/jpeg");
        imageSource.put("data", base64Image);
        imageBlock.put("source", imageSource);
        userContent.put(imageBlock);

        // Text content block with conversation context
        JSONObject textBlock = new JSONObject();
        textBlock.put("type", "text");
        String userText = "Analyze this screenshot.";
        if (conversationContext != null && !conversationContext.isEmpty()) {
            userText += "\n\nConversation context from recent messages:\n" + conversationContext;
        }
        textBlock.put("text", userText);
        userContent.put(textBlock);

        // Build messages array
        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);
        messages.put(userMessage);

        // Build request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "claude-haiku-4-5-20251001");
        requestBody.put("max_tokens", 200);
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", messages);

        // Send HTTP request
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(PROXY_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        Log.d(TAG, "[VISION] Sending request to " + PROXY_URL);
        Response response = httpClient.newCall(request).execute();

        if (response.isSuccessful() && response.body() != null) {
            String responseStr = response.body().string();
            Log.d(TAG, "[VISION] Response received, parsing...");

            // Parse Anthropic Messages API response format
            JSONObject root = new JSONObject(responseStr);
            JSONArray content = root.optJSONArray("content");
            if (content != null && content.length() > 0) {
                JSONObject firstBlock = content.getJSONObject(0);
                return firstBlock.optString("text", "");
            }

            // Fallback: try OpenAI-style response (if proxy normalizes)
            JSONArray choices = root.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject msgObj = choices.getJSONObject(0).optJSONObject("message");
                if (msgObj != null) {
                    return msgObj.optString("content", "");
                }
            }

            Log.w(TAG, "[VISION] Could not parse response: body_length=" + responseStr.length());
            return null;
        } else {
            String errorBody = response.body() != null ? response.body().string() : "unknown";
            Log.e(TAG, "[VISION] HTTP Error " + response.code()
                    + ": body_length=" + errorBody.length());
            throw new Exception("Claude Vision API error: HTTP " + response.code());
        }
    }

    /**
     * Resize bitmap so the longest edge is at most maxDimension pixels.
     * Maintains aspect ratio.
     */
    private Bitmap resizeBitmap(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int maxEdge = Math.max(width, height);

        if (maxEdge <= maxDimension) {
            return bitmap; // No resize needed
        }

        float scale = (float) maxDimension / maxEdge;
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private void postSuccess(AnalysisCallback callback, String result) {
        if (callback != null) {
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> callback.onSuccess(result));
        }
    }

    private void postError(AnalysisCallback callback, String error) {
        if (callback != null) {
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> callback.onError(error));
        }
    }
}
