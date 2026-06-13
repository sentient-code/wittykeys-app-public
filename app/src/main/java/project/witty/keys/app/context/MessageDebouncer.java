package project.witty.keys.app.context;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import project.witty.keys.app.helpers.JourneyTracer;

/**
 * MessageDebouncer — NEW file (Sprint 1, Build 7.0)
 *
 * Batches incoming NLS messages to avoid triggering reply generation on every
 * individual notification. Uses 2s quiet window / 8s max wait.
 *
 * CONTRACT FOR DOWNSTREAM PHASES:
 * - P2 (Smart Replies): ReplyPrecomputeManager registers as the BatchListener
 */
public class MessageDebouncer {

    public interface BatchListener {
        /**
         * Called when a batch of messages is ready for processing.
         * @param packageName The app these messages came from
         * @param contactName The contact who sent these messages
         * @param messages The batched messages (in order)
         */
        void onMessageBatchReady(String packageName, String contactName,
                                  List<NlsMessage> messages);
    }

    private static final long QUIET_WINDOW_MS = 2000;  // 2 seconds
    private static final long MAX_WAIT_MS = 8000;       // 8 seconds

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<NlsMessage> pendingMessages = new ArrayList<>();
    private BatchListener listener;
    private String currentPackage;
    private String currentContact;
    private long batchStartTime;
    private Runnable quietWindowRunnable;
    private Runnable maxWaitRunnable;

    public void setListener(BatchListener listener) {
        this.listener = listener;
    }

    /**
     * Add a message to the current batch. Resets the quiet window timer.
     */
    public synchronized void addMessage(String packageName, String contactName,
                                         NlsMessage message) {
        // If different conversation, flush current batch first
        if (currentPackage != null && (!currentPackage.equals(packageName)
                || !currentContact.equals(contactName))) {
            flushBatch();
        }

        if (pendingMessages.isEmpty()) {
            // Start of new batch
            currentPackage = packageName;
            currentContact = contactName;
            batchStartTime = System.currentTimeMillis();

            // Set max wait timer
            maxWaitRunnable = this::flushBatch;
            handler.postDelayed(maxWaitRunnable, MAX_WAIT_MS);
        }

        pendingMessages.add(message);

        // Reset quiet window
        if (quietWindowRunnable != null) {
            handler.removeCallbacks(quietWindowRunnable);
        }
        quietWindowRunnable = this::flushBatch;
        handler.postDelayed(quietWindowRunnable, QUIET_WINDOW_MS);
    }

    /**
     * Flush the current batch and deliver to listener.
     */
    private synchronized void flushBatch() {
        if (pendingMessages.isEmpty()) return;

        // Cancel pending timers
        if (quietWindowRunnable != null) handler.removeCallbacks(quietWindowRunnable);
        if (maxWaitRunnable != null) handler.removeCallbacks(maxWaitRunnable);

        // JourneyTracer: debounce triggered
        String traceId = JourneyTracer.getCurrentSmartReplyTrace();
        if (traceId != null) {
            try {
                JSONObject dataIn = new JSONObject();
                dataIn.put("buffered_messages", pendingMessages.size());
                dataIn.put("debounce_ms", System.currentTimeMillis() - batchStartTime);
                JourneyTracer.step(traceId, "DEBOUNCE_TRIGGERED", dataIn, null,
                    "debounce window elapsed → proceed to generation");
            } catch (Exception ignored) {}
        }

        // Deliver batch
        if (listener != null) {
            List<NlsMessage> batch = new ArrayList<>(pendingMessages);
            String pkg = currentPackage;
            String contact = currentContact;
            listener.onMessageBatchReady(pkg, contact, batch);
        }

        // Reset state
        pendingMessages.clear();
        currentPackage = null;
        currentContact = null;
        quietWindowRunnable = null;
        maxWaitRunnable = null;
    }

    /**
     * Data class for NLS-captured messages.
     */
    public static class NlsMessage {
        public final String sender;
        public final String text;
        public final long timestamp;
        public final boolean isGroup;

        public NlsMessage(String sender, String text, long timestamp, boolean isGroup) {
            this.sender = sender;
            this.text = text;
            this.timestamp = timestamp;
            this.isGroup = isGroup;
        }
    }
}
