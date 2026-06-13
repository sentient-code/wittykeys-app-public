package project.witty.keys.app.helpers;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import project.witty.keys.BuildConfig;

/**
 * JourneyTracer — Structured journey tracing for SFOS Product Domain observability.
 *
 * Traces every user journey through the app with:
 * - Unique trace_id per journey
 * - Sequential steps with data_in, data_out, and decision fields
 * - Timing at every step
 * - Logcat output (dev) + batched HTTP POST to /ingestTraceHttp (prod)
 *
 * Usage:
 *   String traceId = JourneyTracer.start(Journey.SMART_REPLY);
 *   JourneyTracer.step(traceId, "NLS_RECEIVED", dataIn, null, "buffering message");
 *   JourneyTracer.step(traceId, "DEBOUNCE_FIRED", dataIn, dataOut, "window elapsed");
 *   JourneyTracer.complete(traceId, true);
 *
 * Filter in logcat: adb logcat -s "WK_TRACE"
 */
public class JourneyTracer {

    private static final String TAG = "WK_TRACE";
    private static final String DOMAIN = "product";
    private static final int MAX_BATCH_SIZE = 50;
    private static final long FLUSH_INTERVAL_MS = 30_000; // 30 seconds

    // Ingestion endpoint (set via init)
    private static String ingestUrl = null;
    private static boolean productionMode = false;

    // Active traces
    private static final ConcurrentHashMap<String, TraceContext> activeTraces = new ConcurrentHashMap<>();

    // Batch buffer for production
    private static final List<JSONObject> eventBuffer = new ArrayList<>();
    private static final Object bufferLock = new Object();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Handler flushHandler;

    // Static holder for passing trace IDs through the smart reply pipeline
    private static volatile String currentSmartReplyTraceId;

    // Journey types
    public enum Journey {
        SMART_REPLY("smart_reply", "smart-assistant"),
        TONE_CHANGE("tone_change", "smart-assistant"),
        GRAMMAR_FIX("grammar_fix", "smart-assistant"),
        TRANSLATION("translation", "smart-assistant"),
        SCREENSHOT_AI("screenshot_ai", "overlay"),
        AI_CHAT("ai_chat", "ai-chat"),
        OVERLAY_REPLY("overlay_reply", "overlay"),
        CLIPBOARD_ACTION("clipboard_action", "smart-assistant"),
        CUSTOM_PROMPT("custom_prompt", "smart-assistant"),
        ROW2_DYNAMIC("row2_dynamic", "smart-assistant");

        public final String name;
        public final String service;

        Journey(String name, String service) {
            this.name = name;
            this.service = service;
        }
    }

    // Internal trace context
    private static class TraceContext {
        final String traceId;
        final Journey journey;
        final long startedAt;
        final String parentId;
        int stepCount;

        TraceContext(String traceId, Journey journey, String parentId) {
            this.traceId = traceId;
            this.journey = journey;
            this.startedAt = System.currentTimeMillis();
            this.parentId = parentId;
            this.stepCount = 0;
        }
    }

    private static final SimpleDateFormat ISO_FORMAT =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    // ========================================================================
    // Initialization
    // ========================================================================

    /**
     * Initialize JourneyTracer for production mode.
     * Call once from Application.onCreate().
     *
     * @param baseUrl Backend base URL (e.g., "https://us-central1-tapai-e33d2.cloudfunctions.net")
     * @param enableProduction Whether to batch+send traces to backend
     */
    public static void init(String baseUrl, boolean enableProduction) {
        ingestUrl = baseUrl + "/ingestTraceHttp";
        productionMode = enableProduction;

        if (productionMode) {
            flushHandler = new Handler(Looper.getMainLooper());
            scheduleFlush();
        }

        Log.d(TAG, "[INIT] JourneyTracer initialized. production=" + enableProduction);
    }

    // ========================================================================
    // Core API
    // ========================================================================

    /**
     * Start a new journey trace.
     *
     * @param journey The journey type
     * @return traceId to pass to subsequent step() and complete() calls
     */
    public static String start(Journey journey) {
        return start(journey, null);
    }

    /**
     * Start a journey trace with a parent trace (for cross-domain linking).
     *
     * @param journey The journey type
     * @param parentId Parent trace_id (e.g., from a campaign that triggered this flow)
     * @return traceId
     */
    public static String start(Journey journey, String parentId) {
        String traceId = generateTraceId(journey);
        TraceContext ctx = new TraceContext(traceId, journey, parentId);
        activeTraces.put(traceId, ctx);

        JSONObject data = new JSONObject();
        try {
            data.put("journey", journey.name);
            data.put("service", journey.service);
            data.put("build_type", BuildConfig.BUILD_TYPE);
        } catch (JSONException ignored) {}

        emitEvent(ctx, "trace_start", "info", data);

        return traceId;
    }

    /**
     * Record a step in an active journey.
     *
     * @param traceId From start()
     * @param stepName Short identifier (e.g., "NLS_RECEIVED", "CLAUDE_API_CALLED")
     * @param dataIn  Input data for this step (nullable)
     * @param dataOut Output data from this step (nullable)
     * @param decision Why this path was taken (nullable)
     */
    public static void step(String traceId, String stepName,
                            JSONObject dataIn, JSONObject dataOut, String decision) {
        TraceContext ctx = activeTraces.get(traceId);
        if (ctx == null) {
            Log.w(TAG, "[STEP] Unknown trace: " + traceId);
            return;
        }

        ctx.stepCount++;

        JSONObject data = new JSONObject();
        try {
            if (dataIn != null) data.put("data_in", dataIn);
            if (dataOut != null) data.put("data_out", dataOut);
            if (decision != null) data.put("decision", decision);
        } catch (JSONException ignored) {}

        emitEvent(ctx, stepName, "info", data);
    }

    /**
     * Record a step with simple key-value data (convenience method).
     */
    public static void step(String traceId, String stepName, String key, Object value) {
        JSONObject dataIn = new JSONObject();
        try {
            dataIn.put(key, value);
        } catch (JSONException ignored) {}
        step(traceId, stepName, dataIn, null, null);
    }

    /**
     * Record an error step.
     */
    public static void error(String traceId, String stepName, String errorType, String errorMessage) {
        TraceContext ctx = activeTraces.get(traceId);
        if (ctx == null) {
            Log.w(TAG, "[ERROR] Unknown trace: " + traceId);
            return;
        }

        ctx.stepCount++;

        JSONObject data = new JSONObject();
        try {
            data.put("error_type", errorType);
            data.put("error_message", errorMessage);
        } catch (JSONException ignored) {}

        emitEvent(ctx, stepName, "error", data);
    }

    /**
     * Complete a journey trace.
     *
     * @param traceId From start()
     * @param success Whether the journey completed successfully
     */
    public static void complete(String traceId, boolean success) {
        TraceContext ctx = activeTraces.remove(traceId);
        if (ctx == null) {
            Log.w(TAG, "[COMPLETE] Unknown trace: " + traceId);
            return;
        }

        long durationMs = System.currentTimeMillis() - ctx.startedAt;

        JSONObject data = new JSONObject();
        try {
            data.put("success", success);
            data.put("total_steps", ctx.stepCount);
            data.put("duration_ms", durationMs);
            data.put("journey", ctx.journey.name);
        } catch (JSONException ignored) {}

        emitEvent(ctx, "trace_end", success ? "info" : "error", data);
    }

    /**
     * Get the trace_id for passing to API calls (X-Trace-Id header).
     */
    public static String getTraceId(String traceId) {
        return traceId; // pass-through, but validates it exists
    }

    // ========================================================================
    // Static holder for passing trace IDs through pipelines
    // ========================================================================

    public static void setCurrentSmartReplyTrace(String traceId) {
        currentSmartReplyTraceId = traceId;
    }

    public static String getCurrentSmartReplyTrace() {
        return currentSmartReplyTraceId;
    }

    // ========================================================================
    // Internal: Event emission
    // ========================================================================

    private static void emitEvent(TraceContext ctx, String step, String level, JSONObject data) {
        try {
            JSONObject safeData = sanitizeForPrivacy(data);
            JSONObject event = new JSONObject();
            event.put("timestamp", ISO_FORMAT.format(new Date()));
            event.put("level", level);
            event.put("domain", DOMAIN);
            event.put("service", ctx.journey.service);
            event.put("operation", ctx.journey.name);
            event.put("trace_id", ctx.traceId);
            if (ctx.parentId != null) event.put("parent_id", ctx.parentId);
            event.put("step", step);
            event.put("step_number", ctx.stepCount);
            event.put("duration_ms", System.currentTimeMillis() - ctx.startedAt);
            event.put("data", safeData);

            if (BuildConfig.DEBUG || DebugConfig.isDebugMode) {
                String logLine = String.format("[%s:%s:%s] %s",
                    ctx.journey.name, step, ctx.traceId.substring(ctx.traceId.length() - 8),
                    safeData.toString());

                if ("error".equals(level)) {
                    Log.e(TAG, logLine);
                } else {
                    Log.d(TAG, formatTraceLog(logLine));
                }
            }

            // Buffer for production send
            if (productionMode) {
                synchronized (bufferLock) {
                    eventBuffer.add(event);
                    if (eventBuffer.size() >= MAX_BATCH_SIZE) {
                        flushAsync();
                    }
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, "Failed to build trace event: " + e.getMessage());
        }
    }

    private static String formatTraceLog(String logLine) {
        return logLine;
    }

    private static JSONObject sanitizeForPrivacy(JSONObject source) {
        JSONObject sanitized = new JSONObject();
        if (source == null) {
            return sanitized;
        }

        JSONArray names = source.names();
        if (names == null) {
            return sanitized;
        }

        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i, "");
            Object value = source.opt(key);
            try {
                sanitized.put(key, sanitizeValue(key, value));
            } catch (JSONException ignored) {}
        }
        return sanitized;
    }

    private static JSONArray sanitizeForPrivacy(JSONArray source) {
        JSONArray sanitized = new JSONArray();
        if (source == null) {
            return sanitized;
        }
        for (int i = 0; i < source.length(); i++) {
            sanitized.put(sanitizeValue("", source.opt(i)));
        }
        return sanitized;
    }

    private static Object sanitizeValue(String key, Object value) {
        if (value == null || value == JSONObject.NULL) {
            return JSONObject.NULL;
        }
        if (value instanceof JSONObject) {
            return sanitizeForPrivacy((JSONObject) value);
        }
        if (value instanceof JSONArray) {
            return sanitizeForPrivacy((JSONArray) value);
        }
        if (value instanceof String && isSensitiveKey(key)) {
            return redactedString((String) value);
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase(Locale.US);
        if (lower.endsWith("_length") || lower.endsWith("_count") || lower.endsWith("_present")) {
            return false;
        }
        return lower.contains("text")
                || lower.contains("message")
                || lower.contains("prompt")
                || lower.contains("reply")
                || lower.contains("content")
                || lower.contains("screenshot")
                || lower.contains("image")
                || lower.contains("sender")
                || lower.contains("contact")
                || lower.contains("phone")
                || lower.contains("email")
                || lower.contains("token")
                || lower.contains("account");
    }

    private static String redactedString(String value) {
        return "[redacted length=" + (value != null ? value.length() : 0) + "]";
    }

    // ========================================================================
    // Internal: Production batching
    // ========================================================================

    private static void scheduleFlush() {
        if (flushHandler != null) {
            flushHandler.postDelayed(() -> {
                flushAsync();
                scheduleFlush();
            }, FLUSH_INTERVAL_MS);
        }
    }

    private static void flushAsync() {
        final List<JSONObject> batch;
        synchronized (bufferLock) {
            if (eventBuffer.isEmpty()) return;
            batch = new ArrayList<>(eventBuffer);
            eventBuffer.clear();
        }

        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                JSONArray events = new JSONArray();
                for (JSONObject event : batch) {
                    events.put(event);
                }
                payload.put("events", events);

                URL url = new URL(ingestUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    Log.d(TAG, "[FLUSH] Sent " + batch.size() + " events to backend");
                } else {
                    Log.w(TAG, "[FLUSH] Backend returned " + code + " — events lost");
                }
                conn.disconnect();

            } catch (Exception e) {
                Log.w(TAG, "[FLUSH] Failed to send batch: " + e.getMessage());
                // Events are lost — acceptable for observability data
            }
        });
    }

    // ========================================================================
    // Internal: Trace ID generation
    // ========================================================================

    private static String generateTraceId(Journey journey) {
        String timestamp = Long.toString(System.currentTimeMillis(), 36);
        String random = UUID.randomUUID().toString().substring(0, 8);
        return "prod_" + journey.name + "_" + timestamp + "_" + random;
    }
}
