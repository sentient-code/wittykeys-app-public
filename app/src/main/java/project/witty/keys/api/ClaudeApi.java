package project.witty.keys.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import project.witty.keys.BuildConfig;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.DemoLogger;
import project.witty.keys.app.helpers.JourneyTracer;

/**
 * ClaudeApi - HTTP client for Claude API via Firebase proxy.
 *
 * Uses Firebase Functions proxy to securely call Claude API.
 * API key is stored on Firebase server, NOT in the app.
 *
 * Error Types (Phase 7):
 * - NETWORK: No internet connection or network failure
 * - TIMEOUT: Request timed out
 * - RATE_LIMIT: Too many requests (HTTP 429)
 * - API_ERROR: Claude API returned an error
 * - PARSE_ERROR: Failed to parse response
 */
public class ClaudeApi {

    private static final String TAG = "ClaudeApi";
    private static final String PROXY_URL = BuildConfig.API_BASE_URL + "/proxyClaudeHttp";

    // Error type constants for J8 logging
    public static final String ERROR_NETWORK = "NETWORK";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
    public static final String ERROR_RATE_LIMIT = "RATE_LIMIT";
    public static final String ERROR_API = "API_ERROR";
    public static final String ERROR_PARSE = "PARSE_ERROR";

    // Phase 1: Retry configuration
    private static final int MAX_RETRIES = 1;
    private static final long RETRY_TIMEOUT_MS = 2000; // 2 seconds before retry
    private static final int MAX_REPLY_OPTIONS = 8;

    private final OkHttpClient client;
    private final OkHttpClient retryClient; // Shorter timeout for initial attempt

    public ClaudeApi() {
        // Standard client for normal/retry requests
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // Phase 1: Shorter timeout client for first attempt (2s timeout triggers retry)
        retryClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .build();

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[Claude] ClaudeApi initialized with retry support");
        }
    }

    /**
     * Generate replies using Claude API via Firebase proxy.
     * Phase 1: Includes retry logic - 1 retry after 2s timeout.
     *
     * @param systemPrompt The system prompt for Claude
     * @param userMessage The user message/context
     * @param callback Callback for reply results
     */
    public void generateReplies(String systemPrompt, String userMessage, ReplyCallback callback) {
        generateRepliesWithRetry(systemPrompt, userMessage, callback, 0);
    }

    /**
     * Internal method with retry support.
     * Phase 1: Retry logic implementation.
     */
    private void generateRepliesWithRetry(String systemPrompt, String userMessage,
                                          ReplyCallback callback, int attemptNumber) {
        final long requestStartTime = System.currentTimeMillis();
        final boolean isRetry = attemptNumber > 0;

        // Performance trace for API call timing
        final Trace apiTrace;
        if (!isRetry) {
            apiTrace = FirebasePerformance.getInstance().newTrace("claude_api_call");
            apiTrace.start();
        } else {
            apiTrace = null;
        }

        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "[Claude] generateReplies called" + (isRetry ? " (RETRY #" + attemptNumber + ")" : ""));
            Log.d(TAG, "[Claude] User message length: " + (userMessage != null ? userMessage.length() : 0));
        }

        // Demo logging: API request
        if (!isRetry) {
            DemoLogger.logApiRequestMetadata(
                    DemoLogger.FLOW_AI_FEATURES,
                    systemPrompt != null ? systemPrompt.length() : 0,
                    userMessage != null ? userMessage.length() : 0);
        }

        try {
            JSONObject requestJson = new JSONObject();

            // Build messages array with user message
            JSONArray messagesArray = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messagesArray.put(userMsg);

            requestJson.put("messages", messagesArray);
            requestJson.put("system", systemPrompt);
            requestJson.put("max_tokens", 512); // Increased for 8 replies

            RequestBody body = RequestBody.create(
                    requestJson.toString(),
                    MediaType.parse("application/json")
            );

            // JourneyTracer: add trace ID header for cross-domain linking
            String traceId = JourneyTracer.getCurrentSmartReplyTrace();
            Request.Builder requestBuilder = new Request.Builder()
                    .url(PROXY_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json");
            if (traceId != null) {
                requestBuilder.addHeader("X-Trace-Id", traceId);
                try {
                    JSONObject dataIn = new JSONObject();
                    dataIn.put("model", "haiku");
                    dataIn.put("max_tokens", 512);
                    JourneyTracer.step(traceId, "CLAUDE_API_CALLED", dataIn, null,
                        "sending to proxyClaudeHttp");
                } catch (Exception ignored) {}
            }
            Request request = requestBuilder.build();

            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "[Claude] Sending request to proxy" + (isRetry ? " (retry with longer timeout)" : ""));
            }

            // Phase 1: Use retryClient (2s timeout) for first attempt, standard client for retry
            OkHttpClient activeClient = isRetry ? client : retryClient;

            activeClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    long latency = System.currentTimeMillis() - requestStartTime;
                    Log.e(TAG, "[Claude] Request failed" + (isRetry ? " (retry)" : ""), e);

                    // Determine error type for J8 logging
                    String errorType;
                    String errorMessage;
                    boolean isTimeout = e instanceof java.net.SocketTimeoutException;

                    if (isTimeout) {
                        errorType = ERROR_TIMEOUT;
                        errorMessage = "Request timed out after " + latency + "ms";
                    } else if (e instanceof java.net.UnknownHostException) {
                        errorType = ERROR_NETWORK;
                        errorMessage = "No internet connection";
                    } else if (e instanceof java.net.ConnectException) {
                        errorType = ERROR_NETWORK;
                        errorMessage = "Unable to connect to server";
                    } else {
                        errorType = ERROR_NETWORK;
                        errorMessage = e.getMessage() != null ? e.getMessage() : "Network error";
                    }

                    // Phase 1: Retry logic - retry once on timeout
                    if (isTimeout && attemptNumber < MAX_RETRIES) {
                        Log.d(TAG, "[Claude] Timeout on attempt " + attemptNumber + ", retrying with longer timeout...");
                        generateRepliesWithRetry(systemPrompt, userMessage, callback, attemptNumber + 1);
                        return; // Don't call callback yet, retry in progress
                    }

                    // Demo logging: API failure (final failure)
                    DemoLogger.logApiResponse(DemoLogger.FLOW_AI_FEATURES, null, latency, false);
                    DemoLogger.logError(DemoLogger.FLOW_AI_FEATURES, errorType.toLowerCase(), errorMessage);
                    FirebaseCrashlytics.getInstance().recordException(
                        new RuntimeException("ClaudeApi error: " + errorType + " - " + errorMessage));
                    if (apiTrace != null) {
                        apiTrace.putAttribute("error_type", errorType);
                        apiTrace.stop();
                    }

                    // JourneyTracer: API error
                    if (traceId != null) {
                        JourneyTracer.error(traceId, "CLAUDE_API_ERROR", errorType, errorMessage);
                    }

                    // Callback with error type prefix for proper J8 logging
                    callback.onError(errorType + ": " + errorMessage);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (DebugConfig.isDebugMode) {
                        Log.d(TAG, "[Claude] Response received: " + response.code());
                    }

                    if (!response.isSuccessful()) {
                        long latency = System.currentTimeMillis() - requestStartTime;
                        String errorBody = response.body() != null ? response.body().string() : "";
                        int statusCode = response.code();
                        Log.e(TAG, "[Claude] HTTP error: " + statusCode + " (body_length=" + errorBody.length() + ")");

                        // Determine error type based on HTTP status code
                        String errorType;
                        String errorMessage;
                        if (statusCode == 429) {
                            errorType = ERROR_RATE_LIMIT;
                            errorMessage = "Too many requests. Please wait a moment.";
                        } else if (statusCode == 408 || statusCode == 504) {
                            errorType = ERROR_TIMEOUT;
                            errorMessage = "Server timeout. Please try again.";
                        } else if (statusCode >= 500) {
                            errorType = ERROR_API;
                            errorMessage = "Server error. Please try again later.";
                        } else {
                            errorType = ERROR_API;
                            errorMessage = "Request failed (HTTP " + statusCode + ")";
                        }

                        // Demo logging: API HTTP error
                        DemoLogger.logApiResponse(DemoLogger.FLOW_AI_FEATURES, null, latency, false);
                        DemoLogger.logError(DemoLogger.FLOW_AI_FEATURES, errorType.toLowerCase(), errorMessage);
                        FirebaseCrashlytics.getInstance().recordException(
                            new RuntimeException("ClaudeApi error: " + errorType + " - " + errorMessage));
                        if (apiTrace != null) {
                            apiTrace.putAttribute("error_type", errorType);
                            apiTrace.stop();
                        }

                        // JourneyTracer: HTTP error
                        if (traceId != null) {
                            JourneyTracer.error(traceId, "CLAUDE_API_ERROR", errorType, errorMessage);
                        }

                        // Callback with error type prefix for proper J8 logging
                        callback.onError(errorType + ": " + errorMessage);
                        return;
                    }

                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (DebugConfig.isDebugMode) {
                            Log.d(TAG, "[Claude] Response body length: " + responseBody.length());
                        }

                        // Parse OpenAI-compatible response format from proxy
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        JSONArray choices = jsonResponse.optJSONArray("choices");

                        if (choices != null && choices.length() > 0) {
                            JSONObject firstChoice = choices.getJSONObject(0);
                            JSONObject message = firstChoice.optJSONObject("message");
                            String content = message != null ? message.optString("content", "") : "";

                            // Parse replies from content (one per line)
                            List<String> replies = parseReplies(content);

                            long latency = System.currentTimeMillis() - requestStartTime;
                            if (DebugConfig.isDebugMode) {
                                Log.d(TAG, "[Claude] Parsed " + replies.size() + " replies");
                            }

                            // JourneyTracer: replies parsed
                            if (traceId != null) {
                                try {
                                    JSONObject traceDataIn = new JSONObject();
                                    traceDataIn.put("status", response.code());
                                    JSONObject traceDataOut = new JSONObject();
                                    traceDataOut.put("reply_count", replies.size());
                                    traceDataOut.put("latency_ms", latency);
                                    JourneyTracer.step(traceId, "REPLIES_PARSED", traceDataIn, traceDataOut,
                                        replies.size() + " replies parsed");
                                } catch (Exception ignored) {}
                            }

                            // Demo logging: API success
                            DemoLogger.logApiResponse(DemoLogger.FLOW_AI_FEATURES, replies, latency, true);
                            if (apiTrace != null) {
                                apiTrace.putMetric("response_tokens", replies.size());
                                apiTrace.stop();
                            }

                            callback.onRepliesGenerated(replies);
                        } else {
                            long latency = System.currentTimeMillis() - requestStartTime;
                            Log.e(TAG, "[Claude] No choices in response");
                            // Demo logging: API format error
                            DemoLogger.logApiResponse(DemoLogger.FLOW_AI_FEATURES, null, latency, false);
                            DemoLogger.logError(DemoLogger.FLOW_AI_FEATURES, ERROR_PARSE.toLowerCase(), "No choices in response");
                            FirebaseCrashlytics.getInstance().recordException(
                                new RuntimeException("ClaudeApi error: " + ERROR_PARSE + " - No choices in response"));
                            if (apiTrace != null) {
                                apiTrace.putAttribute("error_type", ERROR_PARSE);
                                apiTrace.stop();
                            }
                            callback.onError(ERROR_PARSE + ": Invalid response format");
                        }

                    } catch (JSONException e) {
                        long latency = System.currentTimeMillis() - requestStartTime;
                        Log.e(TAG, "[Claude] JSON parsing error", e);
                        // Demo logging: Parse error
                        DemoLogger.logApiResponse(DemoLogger.FLOW_AI_FEATURES, null, latency, false);
                        DemoLogger.logError(DemoLogger.FLOW_AI_FEATURES, ERROR_PARSE.toLowerCase(), e.getMessage());
                        FirebaseCrashlytics.getInstance().recordException(e);
                        if (apiTrace != null) {
                            apiTrace.putAttribute("error_type", ERROR_PARSE);
                            apiTrace.stop();
                        }
                        callback.onError(ERROR_PARSE + ": " + e.getMessage());
                    }
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "[Claude] JSON build error", e);
            callback.onError("JSON error: " + e.getMessage());
        }
    }

    /**
     * Parse Claude's response into individual replies.
     * Handles two formats:
     * 1. JSON format (from bilingual prompt): {"quickReplies": [...], "quickRepliesTranslated": [...]}
     * 2. Plain text format: one reply per line
     */
    private List<String> parseReplies(String content) {
        List<String> replies = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return replies;
        }

        String trimmedContent = content.trim();

        String parseTarget = extractReplyPayload(trimmedContent);

        List<String> jsonReplies = parseJsonReplies(parseTarget);
        if (!jsonReplies.isEmpty()) {
            return jsonReplies;
        }

        // Plain text format: one reply per line
        String[] lines = parseTarget.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            // Skip empty lines, JSON artifacts, and numbered prefixes
            if (!trimmed.isEmpty() && !isJsonArtifact(trimmed)) {
                // Remove common numbering prefixes
                trimmed = trimmed.replaceFirst("^\\d+[.):]\\s*", "");
                addReplyIfValid(replies, trimmed);
            }
        }

        return replies;
    }

    /**
     * Extract the most reply-like payload when Claude wraps JSON in fences or prose.
     */
    private String extractReplyPayload(String content) {
        String fenced = extractFencedContent(content);
        String candidate = fenced != null ? fenced : content;

        if (candidate.contains("quickReplies")) {
            int objectStart = candidate.indexOf('{');
            int objectEnd = candidate.lastIndexOf('}');
            if (objectStart >= 0 && objectEnd > objectStart) {
                return candidate.substring(objectStart, objectEnd + 1).trim();
            }
        }

        return candidate.trim();
    }

    private String extractFencedContent(String content) {
        int fenceStart = content.indexOf("```");
        if (fenceStart < 0) {
            return null;
        }

        int contentStart = content.indexOf('\n', fenceStart + 3);
        if (contentStart < 0) {
            return null;
        }

        int fenceEnd = content.indexOf("```", contentStart + 1);
        String fenced = fenceEnd >= 0
                ? content.substring(contentStart + 1, fenceEnd)
                : content.substring(contentStart + 1);
        fenced = fenced.trim();
        return fenced.isEmpty() ? null : fenced;
    }

    private List<String> parseJsonReplies(String content) {
        List<String> replies = new ArrayList<>();
        String trimmed = content.trim();

        try {
            if (trimmed.startsWith("{") && trimmed.contains("quickReplies")) {
                JSONObject json = new JSONObject(trimmed);
                addRepliesFromJsonArray(replies, json.optJSONArray("quickReplies"));

                if (replies.isEmpty()) {
                    addRepliesFromJsonArray(replies, json.optJSONArray("quickRepliesTranslated"));
                }

                if (DebugConfig.isDebugMode) {
                    Log.d(TAG, "[Claude] Parsed JSON response - " + replies.size() + " replies");
                }
            } else if (trimmed.startsWith("[")) {
                addRepliesFromJsonArray(replies, new JSONArray(trimmed));
            }
        } catch (JSONException e) {
            Log.w(TAG, "[Claude] Failed to parse as JSON, falling back to line parsing: " + e.getMessage());
        }

        return replies;
    }

    private void addRepliesFromJsonArray(List<String> replies, JSONArray array) {
        if (array == null) return;

        for (int i = 0; i < array.length() && replies.size() < MAX_REPLY_OPTIONS; i++) {
            addReplyIfValid(replies, array.optString(i, ""));
        }
    }

    private void addReplyIfValid(List<String> replies, String text) {
        if (replies.size() >= MAX_REPLY_OPTIONS) return;

        String cleaned = cleanReplyText(text);
        if (!cleaned.isEmpty() && !isJsonArtifact(cleaned)) {
            replies.add(cleaned);
        }
    }

    /**
     * Clean up reply text by removing surrounding quotes and trailing commas.
     */
    private String cleanReplyText(String text) {
        if (text == null) return "";
        String cleaned = text.trim();

        while (cleaned.endsWith(",")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }

        // Remove surrounding quotes after JSON-line commas have been stripped.
        while ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
               (cleaned.startsWith("'") && cleaned.endsWith("'")) ||
               (cleaned.startsWith("“") && cleaned.endsWith("”"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        while (cleaned.endsWith(",")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }

        return cleaned.trim();
    }

    /**
     * Check if a line is a JSON artifact that should be skipped.
     */
    private boolean isJsonArtifact(String line) {
        String trimmed = line.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        return trimmed.equals("{") ||
               trimmed.equals("}") ||
               trimmed.equals("[") ||
               trimmed.equals("]") ||
               trimmed.equals("],") ||
               trimmed.equals("},") ||
               trimmed.startsWith("```") ||
               trimmed.startsWith("**") ||
               lower.startsWith("breakdown:") ||
               lower.startsWith("explanation:") ||
               lower.startsWith("notes:") ||
               lower.startsWith("note:") ||
               lower.contains("why these replies") ||
               (trimmed.startsWith("- ") && lower.contains("reply")) ||
               (trimmed.startsWith("* ") && lower.contains("reply")) ||
               (trimmed.startsWith("- ") && lower.contains("tone")) ||
               (trimmed.startsWith("* ") && lower.contains("tone")) ||
               trimmed.startsWith("\"detectedLanguage\"") ||
               trimmed.startsWith("\"needsTranslation\"") ||
               trimmed.startsWith("\"quickReplies\"") ||
               trimmed.startsWith("\"quickRepliesTranslated\"");
    }

    /**
     * Callback interface for reply generation results.
     */
    public interface ReplyCallback {
        /**
         * Called when replies are successfully generated.
         * @param replies List of reply suggestions (typically 4)
         */
        void onRepliesGenerated(List<String> replies);

        /**
         * Called when an error occurs.
         * @param error Error message
         */
        void onError(String error);
    }

    /**
     * Get the proxy URL (useful for debugging).
     */
    public static String getProxyUrl() {
        return PROXY_URL;
    }
}
