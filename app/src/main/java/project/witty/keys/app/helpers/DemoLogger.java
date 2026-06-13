package project.witty.keys.app.helpers;

import android.util.Log;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import project.witty.keys.BuildConfig;

/**
 * DemoLogger - Structured logging for E2E demo testing.
 *
 * Outputs JSON-formatted logs filtered by tag "WK_DEMO".
 * Log format: [DEMO:{flow}:{step}] {json_data}
 *
 * Filter in logcat: adb logcat -s "WK_DEMO"
 */
public class DemoLogger {

    private static final String TAG = "WK_DEMO";
    private static final SimpleDateFormat ISO_FORMAT =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    // Demo flow identifiers
    public static final String FLOW_ONBOARDING = "ONBOARDING";
    public static final String FLOW_KEYBOARD_BASIC = "KEYBOARD_BASIC";
    public static final String FLOW_SMART_ASSISTANT = "SMART_ASSISTANT";
    public static final String FLOW_AI_FEATURES = "AI_FEATURES";
    public static final String FLOW_SCREEN_READ = "SCREEN_READ";
    public static final String FLOW_HINGLISH = "HINGLISH";
    public static final String FLOW_TONE_CHANGE = "TONE_CHANGE";
    public static final String FLOW_GRAMMAR = "GRAMMAR";
    public static final String FLOW_PERFORMANCE = "PERFORMANCE";

    // Step types
    public static final String STEP_START = "START";
    public static final String STEP_INPUT = "INPUT";
    public static final String STEP_CONTEXT = "CONTEXT";
    public static final String STEP_API_REQUEST = "API_REQUEST";
    public static final String STEP_API_RESPONSE = "API_RESPONSE";
    public static final String STEP_UI_UPDATE = "UI_UPDATE";
    public static final String STEP_USER_ACTION = "USER_ACTION";
    public static final String STEP_COMPLETE = "COMPLETE";
    public static final String STEP_ERROR = "ERROR";

    private static boolean enabled = BuildConfig.DEBUG;

    /**
     * Enable or disable demo logging.
     */
    public static void setEnabled(boolean enable) {
        enabled = enable;
    }

    /**
     * Check if demo logging is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Log demo event with structured data.
     *
     * @param flow The demo flow identifier (e.g., FLOW_AI_FEATURES)
     * @param step The step type (e.g., STEP_API_REQUEST)
     * @param data JSON object containing event data
     */
    public static void log(String flow, String step, JSONObject data) {
        if (!enabled) return;

        try {
            JSONObject logEntry = new JSONObject();
            logEntry.put("timestamp", ISO_FORMAT.format(new Date()));
            logEntry.put("flow", flow);
            logEntry.put("step", step);
            logEntry.put("data", data != null ? data : new JSONObject());

            String logTag = String.format("[DEMO:%s:%s]", flow, step);
            Log.d(TAG, logTag + " " + logEntry.toString());

        } catch (Exception e) {
            Log.e(TAG, "DemoLogger error: " + e.getMessage());
        }
    }

    /**
     * Log user input event.
     */
    public static void logInput(String flow, String inputText, String inputField) {
        try {
            JSONObject data = new JSONObject();
            data.put("input_length", inputText != null ? inputText.length() : 0);
            data.put("input_field", inputField);
            data.put("contains_hinglish", containsHinglish(inputText));
            log(flow, STEP_INPUT, data);
        } catch (Exception e) {
            Log.e(TAG, "logInput error: " + e.getMessage());
        }
    }

    /**
     * Log screen context extraction.
     */
    public static void logContext(String flow, String appName, String appCategory,
                                   int messageCount, String lastMessage, String sender) {
        try {
            JSONObject data = new JSONObject();
            data.put("app_name", appName);
            data.put("app_category", appCategory);
            data.put("message_count", messageCount);
            data.put("last_message_length", lastMessage != null ? lastMessage.length() : 0);
            data.put("sender_present", sender != null && !sender.isEmpty());
            log(flow, STEP_CONTEXT, data);
        } catch (Exception e) {
            Log.e(TAG, "logContext error: " + e.getMessage());
        }
    }

    /**
     * Log API request to Claude.
     */
    public static void logApiRequest(String flow, String systemPrompt, String userPrompt) {
        logApiRequestMetadata(
                flow,
                systemPrompt != null ? systemPrompt.length() : 0,
                userPrompt != null ? userPrompt.length() : 0);
    }

    /**
     * Log API request metadata without prompt text.
     */
    public static void logApiRequestMetadata(String flow, int systemPromptLength, int userPromptLength) {
        try {
            JSONObject data = new JSONObject();
            data.put("system_prompt_length", Math.max(0, systemPromptLength));
            data.put("user_prompt_length", Math.max(0, userPromptLength));
            data.put("endpoint", "proxyClaudeHttp");
            log(flow, STEP_API_REQUEST, data);
        } catch (Exception e) {
            Log.e(TAG, "logApiRequest error: " + e.getMessage());
        }
    }

    /**
     * Log API response from Claude.
     */
    public static void logApiResponse(String flow, List<String> replies, long latencyMs, boolean success) {
        logApiResponseMetadata(
                flow,
                replies != null ? replies.size() : 0,
                calculateAvgLength(replies),
                latencyMs,
                success);
    }

    /**
     * Log API response metadata without reply text.
     */
    public static void logApiResponseMetadata(String flow, int replyCount, int avgReplyLength, long latencyMs, boolean success) {
        try {
            JSONObject data = new JSONObject();
            data.put("success", success);
            data.put("reply_count", Math.max(0, replyCount));
            data.put("latency_ms", latencyMs);
            data.put("avg_reply_length", Math.max(0, avgReplyLength));

            log(flow, STEP_API_RESPONSE, data);
        } catch (Exception e) {
            Log.e(TAG, "logApiResponse error: " + e.getMessage());
        }
    }

    /**
     * Log UI update (predictions, smart replies shown).
     */
    public static void logUiUpdate(String flow, String component, List<String> items) {
        try {
            JSONObject data = new JSONObject();
            data.put("component", component);
            data.put("item_count", items != null ? items.size() : 0);
            data.put("avg_item_length", calculateAvgLength(items));

            log(flow, STEP_UI_UPDATE, data);
        } catch (Exception e) {
            Log.e(TAG, "logUiUpdate error: " + e.getMessage());
        }
    }

    /**
     * Log UI update with string value.
     */
    public static void logUiUpdate(String flow, String component, String value) {
        try {
            JSONObject data = new JSONObject();
            data.put("component", component);
            data.put("value_length", value != null ? value.length() : 0);
            log(flow, STEP_UI_UPDATE, data);
        } catch (Exception e) {
            Log.e(TAG, "logUiUpdate error: " + e.getMessage());
        }
    }

    /**
     * Log user action (button tap, chip selection).
     */
    public static void logUserAction(String flow, String action, String target, String value) {
        try {
            JSONObject data = new JSONObject();
            data.put("action", action);
            data.put("target", target);
            if (value != null) {
                data.put("value_length", value.length());
            }
            log(flow, STEP_USER_ACTION, data);
        } catch (Exception e) {
            Log.e(TAG, "logUserAction error: " + e.getMessage());
        }
    }

    /**
     * Log demo flow start.
     */
    public static void logStart(String flow) {
        try {
            JSONObject data = new JSONObject();
            data.put("debug_mode", DebugConfig.isDebugMode);
            data.put("build_type", BuildConfig.BUILD_TYPE);
            data.put("version_name", BuildConfig.VERSION_NAME);
            log(flow, STEP_START, data);
        } catch (Exception e) {
            Log.e(TAG, "logStart error: " + e.getMessage());
        }
    }

    /**
     * Log demo flow completion.
     */
    public static void logComplete(String flow, boolean success, long totalTimeMs) {
        try {
            JSONObject data = new JSONObject();
            data.put("success", success);
            data.put("total_time_ms", totalTimeMs);
            log(flow, STEP_COMPLETE, data);
        } catch (Exception e) {
            Log.e(TAG, "logComplete error: " + e.getMessage());
        }
    }

    /**
     * Log error.
     */
    public static void logError(String flow, String errorType, String errorMessage) {
        try {
            JSONObject data = new JSONObject();
            data.put("error_type", errorType);
            data.put("error_message", errorMessage);
            log(flow, STEP_ERROR, data);
        } catch (Exception e) {
            Log.e(TAG, "logError error: " + e.getMessage());
        }
    }

    /**
     * Log tone change action.
     */
    public static void logToneChange(String fromTone, String toTone, String originalText, String modifiedText) {
        try {
            JSONObject data = new JSONObject();
            data.put("from_tone", fromTone);
            data.put("to_tone", toTone);
            data.put("original_length", originalText != null ? originalText.length() : 0);
            data.put("modified_length", modifiedText != null ? modifiedText.length() : 0);
            log(FLOW_TONE_CHANGE, STEP_UI_UPDATE, data);
        } catch (Exception e) {
            Log.e(TAG, "logToneChange error: " + e.getMessage());
        }
    }

    /**
     * Log grammar correction.
     */
    public static void logGrammarCorrection(String originalText, String correctedText, int errorCount) {
        try {
            JSONObject data = new JSONObject();
            data.put("original_length", originalText != null ? originalText.length() : 0);
            data.put("corrected_length", correctedText != null ? correctedText.length() : 0);
            data.put("error_count", errorCount);
            data.put("changes_made", originalText == null ? correctedText != null : !originalText.equals(correctedText));
            log(FLOW_GRAMMAR, STEP_UI_UPDATE, data);
        } catch (Exception e) {
            Log.e(TAG, "logGrammarCorrection error: " + e.getMessage());
        }
    }

    /**
     * Log performance metric.
     */
    public static void logPerformance(String metricName, long valueMs, String details) {
        try {
            JSONObject data = new JSONObject();
            data.put("metric", metricName);
            data.put("value_ms", valueMs);
            data.put("details", details);

            // Add threshold checks
            if (metricName.equals("keyboard_open_time")) {
                data.put("threshold_ms", 300);
                data.put("passed", valueMs <= 300);
            } else if (metricName.equals("ai_response_time")) {
                data.put("threshold_ms", 3000);
                data.put("passed", valueMs <= 3000);
            }

            log(FLOW_PERFORMANCE, STEP_UI_UPDATE, data);
        } catch (Exception e) {
            Log.e(TAG, "logPerformance error: " + e.getMessage());
        }
    }

    // Helper methods

    private static String truncate(String str, int maxLen) {
        if (str == null) return null;
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    private static boolean containsHinglish(String text) {
        if (text == null) return false;
        // Simple heuristic: contains common Hinglish words
        String lower = text.toLowerCase();
        return lower.contains("kya") || lower.contains("hai") || lower.contains("bhai") ||
               lower.contains("yaar") || lower.contains("accha") || lower.contains("theek") ||
               lower.contains("nahi") || lower.contains("haan") || lower.contains("aur") ||
               lower.contains("bro") || lower.contains("mein") || lower.contains("tum");
    }

    private static int calculateAvgLength(List<String> items) {
        if (items == null || items.isEmpty()) return 0;
        int total = 0;
        for (String item : items) {
            total += item != null ? item.length() : 0;
        }
        return total / items.size();
    }
}
