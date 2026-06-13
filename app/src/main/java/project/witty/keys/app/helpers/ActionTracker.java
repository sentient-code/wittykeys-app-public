package project.witty.keys.app.helpers;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import project.witty.keys.app.database.ActionFrequency;
import project.witty.keys.app.database.ActionFrequencyDao;
import project.witty.keys.app.database.WittyKeysDatabase;
import project.witty.keys.app.helpers.EncryptedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tracks user AI action preferences and builds personalized Row 2 chips.
 *
 * Ranking: score = (frequency x 0.6) + (recency x 0.3) + (context x 0.1)
 */
public class ActionTracker {

    private static final String TAG = "WK_ACTION_TRACKER";
    private static ActionTracker instance;
    private final ActionFrequencyDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Action types
    public static final String TYPE_TONE = "tone";
    public static final String TYPE_TRANSLATE = "translate";
    public static final String TYPE_GRAMMAR = "grammar";
    public static final String TYPE_CUSTOM = "custom";

    // Clipboard tracking
    private static final String KEY_CLIPBOARD_TEXT = "action_tracker_clip_text";
    private static final String KEY_CLIPBOARD_TIME = "action_tracker_clip_time";
    private static final long CLIPBOARD_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes

    private ActionTracker(Context context) {
        WittyKeysDatabase db = WittyKeysDatabase.getInstance(context.getApplicationContext());
        this.dao = db.actionFrequencyDao();
    }

    public static synchronized ActionTracker getInstance(Context context) {
        if (instance == null) {
            instance = new ActionTracker(context);
        }
        return instance;
    }

    // ─── Recording ───

    /**
     * Record an AI action. Call after successful API response.
     * Runs on background thread — safe to call from UI thread.
     *
     * @param type      One of TYPE_TONE, TYPE_TRANSLATE, TYPE_GRAMMAR, TYPE_CUSTOM
     * @param parameter The specific choice (e.g., "casual", "hi", "make shorter")
     * @param emoji     Display emoji for the chip
     * @param label     Display label for the chip
     */
    public void recordAction(String type, String parameter, String emoji, String label) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                int updated = dao.incrementAction(type, parameter, now);
                if (updated == 0) {
                    // First time using this action+parameter combo
                    ActionFrequency af = new ActionFrequency();
                    af.actionType = type;
                    af.parameter = parameter;
                    af.count = 1;
                    af.lastUsed = now;
                    af.emoji = emoji;
                    af.displayLabel = label;
                    dao.insert(af);
                }
                Log.d(TAG, "Recorded: " + type + "/" + parameter);
            } catch (Exception e) {
                Log.e(TAG, "Failed to record action: " + e.getMessage());
            }
        });
    }

    // ─── Clipboard tracking ───

    /**
     * Store clipboard text for Row 2 chip display.
     * Called from LatinIME.checkClipboard() when text is detected.
     */
    public void setClipboardText(String text) {
        if (text == null || text.trim().isEmpty()) {
            clearClipboardText();
            return;
        }
        EncryptedPreferences.saveString(KEY_CLIPBOARD_TEXT, text);
        EncryptedPreferences.saveLong(KEY_CLIPBOARD_TIME, System.currentTimeMillis());
        Log.d(TAG, "Clipboard stored: " + text.length() + " chars");
    }

    /**
     * Get stored clipboard text, or null if expired (>5 min) or empty.
     */
    public String getClipboardText() {
        long storedTime = EncryptedPreferences.getLong(KEY_CLIPBOARD_TIME, 0);
        if (System.currentTimeMillis() - storedTime > CLIPBOARD_EXPIRY_MS) {
            return null; // Expired
        }
        String text = EncryptedPreferences.getString(KEY_CLIPBOARD_TEXT, "");
        return text.isEmpty() ? null : text;
    }

    /**
     * Clear stored clipboard text (after paste or expiry).
     */
    public void clearClipboardText() {
        EncryptedPreferences.saveString(KEY_CLIPBOARD_TEXT, "");
        EncryptedPreferences.saveLong(KEY_CLIPBOARD_TIME, 0L);
    }

    // ─── Dynamic Row 2 ───

    /**
     * Build the dynamic Row 2 chip list.
     * Returns ranked ChipData list based on user's action history.
     *
     * MUST be called on background thread (Room query).
     * Use buildDynamicRow2Async() for UI thread calls.
     */
    public List<ChipData> buildDynamicRow2() {
        List<ChipData> chips = new ArrayList<>();

        int totalActions = dao.getTotalActions();
        if (totalActions == 0) {
            // First-time user — show defaults + clipboard + Custom + More
            chips.addAll(getDefaultChips());

            // Clipboard chip if fresh text exists
            String clipTextDefault = getClipboardText();
            if (clipTextDefault != null) {
                String trunc = clipTextDefault.length() > 18
                    ? clipTextDefault.substring(0, 18) + "\u2026" : clipTextDefault;
                chips.add(new ChipData("clipboard", clipTextDefault,
                    "\uD83D\uDCCB", "\"" + trunc + "\"",
                    ChipData.TapAction.CLIPBOARD_ACTION));
            }

            chips.add(new ChipData("custom", "", "\u270F\uFE0F", "Custom", ChipData.TapAction.OPEN_CUSTOM_MODE));
            chips.add(new ChipData("more", "", "", "+ More", ChipData.TapAction.EXPAND_FULL_PANEL));
            return chips;
        }

        // Show the 3 most recently used actions across all types (tone, grammar, translate, custom)
        List<ActionFrequency> recentActions = dao.getRecent(3);

        for (ActionFrequency af : recentActions) {
            ChipData chip = actionToChip(af);
            if (chip != null) chips.add(chip);
        }

        // Pad with defaults if fewer than 3 personalized chips
        if (chips.size() < 3) {
            List<ChipData> defaults = getDefaultChips();
            for (ChipData d : defaults) {
                if (chips.size() >= 3) break;
                boolean duplicate = false;
                for (ChipData existing : chips) {
                    if (existing.actionType.equals(d.actionType)
                        && existing.parameter.equals(d.parameter)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) chips.add(d);
            }
        }

        // Clipboard chip (Slot 4 if fresh text exists)
        String clipText = getClipboardText();
        if (clipText != null && chips.size() < 5) {
            String truncated = clipText.length() > 18
                ? clipText.substring(0, 18) + "\u2026"
                : clipText;
            chips.add(new ChipData(
                "clipboard", clipText,
                "\uD83D\uDCCB", "\"" + truncated + "\"",
                ChipData.TapAction.CLIPBOARD_ACTION));
        }

        // Always add Custom as second-to-last
        chips.add(new ChipData("custom", "", "\u270F\uFE0F", "Custom", ChipData.TapAction.OPEN_CUSTOM_MODE));

        // Always add More as last
        chips.add(new ChipData("more", "", "", "+ More", ChipData.TapAction.EXPAND_FULL_PANEL));

        // JourneyTracer: Row 2 dynamic actions ranked
        String row2TraceId = JourneyTracer.start(JourneyTracer.Journey.ROW2_DYNAMIC);
        try {
            JSONObject dataOut = new JSONObject();
            dataOut.put("actions_ranked", chips.size());
            dataOut.put("top_action", chips.isEmpty() ? "none" : chips.get(0).label);
            JourneyTracer.step(row2TraceId, "ACTIONS_RANKED", null, dataOut,
                "ranked " + chips.size() + " actions for Row 2");
            JourneyTracer.complete(row2TraceId, true);
        } catch (Exception ignored) {}

        return chips;
    }

    /**
     * Async version — calls callback on UI thread with chip list.
     */
    public void buildDynamicRow2Async(Context context, DynamicChipsCallback callback) {
        executor.execute(() -> {
            try {
                List<ChipData> chips = buildDynamicRow2();
                new android.os.Handler(context.getMainLooper()).post(() -> callback.onChipsReady(chips));
            } catch (Exception e) {
                Log.e(TAG, "buildDynamicRow2 failed, returning defaults: " + e.getMessage());
                // Fallback: return default chips so Row 2 is never empty
                List<ChipData> fallback = new ArrayList<>();
                fallback.addAll(getDefaultChips());
                fallback.add(new ChipData("custom", "", "\u270F\uFE0F", "Custom", ChipData.TapAction.OPEN_CUSTOM_MODE));
                fallback.add(new ChipData("more", "", "", "+ More", ChipData.TapAction.EXPAND_FULL_PANEL));
                new android.os.Handler(context.getMainLooper()).post(() -> callback.onChipsReady(fallback));
            }
        });
    }

    public interface DynamicChipsCallback {
        void onChipsReady(List<ChipData> chips);
    }

    // ─── Chip Conversion ───

    private ChipData actionToChip(ActionFrequency af) {
        switch (af.actionType) {
            case TYPE_TONE:
                return new ChipData(TYPE_TONE, af.parameter,
                    af.emoji != null ? af.emoji : "\uD83D\uDCDD",
                    af.displayLabel != null ? af.displayLabel : af.parameter,
                    ChipData.TapAction.APPLY_TONE_DIRECT);

            case TYPE_TRANSLATE:
                return new ChipData(TYPE_TRANSLATE, af.parameter,
                    "\uD83C\uDF10",
                    "\u2192 " + (af.displayLabel != null ? af.displayLabel : af.parameter),
                    ChipData.TapAction.TRANSLATE_DIRECT);

            case TYPE_GRAMMAR:
                return new ChipData(TYPE_GRAMMAR, "",
                    "\u2713", "Grammar",
                    ChipData.TapAction.GRAMMAR_DIRECT);

            case TYPE_CUSTOM:
                String truncated = af.parameter.length() > 15
                    ? af.parameter.substring(0, 15) + "\u2026"
                    : af.parameter;
                return new ChipData(TYPE_CUSTOM, af.parameter,
                    "\u270F\uFE0F", "\"" + truncated + "\"",
                    ChipData.TapAction.RERUN_CUSTOM);

            default:
                return null;
        }
    }

    private List<ChipData> getDefaultChips() {
        List<ChipData> defaults = new ArrayList<>();
        defaults.add(new ChipData(TYPE_TONE, "tone_picker", "\uD83D\uDCDD", "Tone", ChipData.TapAction.OPEN_TONE_PICKER));
        defaults.add(new ChipData(TYPE_GRAMMAR, "", "\u2713", "Grammar", ChipData.TapAction.GRAMMAR_DIRECT));
        defaults.add(new ChipData(TYPE_TRANSLATE, "lang_picker", "\uD83C\uDF10", "Translate", ChipData.TapAction.OPEN_LANG_PICKER));
        return defaults;
    }

}
