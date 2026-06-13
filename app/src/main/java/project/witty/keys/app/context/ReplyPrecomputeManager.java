package project.witty.keys.app.context;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import project.witty.keys.app.entitlements.AiActionType;
import project.witty.keys.app.entitlements.AiEntitlementManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Pre-computes smart replies in background when NLS captures new messages.
 * Uses ReplyGenerator → Claude Haiku to generate 3 reply suggestions,
 * stored in ReplyCache for instant display when keyboard opens.
 *
 * Thread safety: ExecutorService (2 threads) + Semaphore (3 concurrent max).
 * This prevents API flood while allowing overlap between batches.
 */
public class ReplyPrecomputeManager implements MessageDebouncer.BatchListener {

    private static final String TAG = "ReplyPrecompute";
    private static final int THREAD_POOL_SIZE = 2;
    private static final int MAX_CONCURRENT_REQUESTS = 3;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 2000; // 2 seconds, then 4 seconds

    private static ReplyPrecomputeManager instance;

    private final Context appContext;
    private final ReplyGenerator replyGenerator;
    private final ReplyCache replyCache;
    private final ExecutorService executorService;
    private final Semaphore semaphore;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());

    private ReplyPrecomputeManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.replyGenerator = new ReplyGenerator();
        this.replyCache = ReplyCache.getInstance();
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.semaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);
    }

    public static synchronized ReplyPrecomputeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ReplyPrecomputeManager(context);
        }
        return instance;
    }

    /**
     * Called by MessageDebouncer when a batch of messages is ready.
     * Runs reply generation on background thread.
     */
    @Override
    public void onMessageBatchReady(String packageName, String contactName,
                                     List<MessageDebouncer.NlsMessage> messages) {
        if (messages == null || messages.isEmpty()) return;
        if (contactName == null || packageName == null) {
            Log.w(TAG, "Missing sender or package in message batch, skipping precompute");
            return;
        }
        if (!AiEntitlementManager.getInstance(appContext).shouldRunBackgroundPrecompute()) {
            Log.d(TAG, "[PRECOMPUTE] Background precompute skipped by AI entitlement policy");
            return;
        }

        String conversationKey = packageName + "|" + contactName;
        NlsMessageBuffer.ConversationSnapshot snapshot =
                NlsMessageBuffer.getInstance().openConversation(conversationKey);

        if (snapshot == null || snapshot.latestMessageSentByUser || snapshot.latestIncomingId == null) {
            Log.d(TAG, "[PRECOMPUTE] No replyable incoming conversation state");
            return;
        }

        if (replyCache.hasFreshReplies(conversationKey, snapshot.latestIncomingId)) {
            Log.d(TAG, "[PRECOMPUTE] Fresh cache exists, skipping: package=" + packageName);
            return;
        }

        if (cacheSafeMediaRepliesIfNeeded(conversationKey, snapshot.latestIncomingId, snapshot.messages)) {
            return;
        }

        Chat chat = buildChatFromBufferedMessages(snapshot.messages, packageName, contactName);
        attemptGeneration(conversationKey, snapshot.latestIncomingId, chat, 0);
    }

    private void attemptGeneration(String conversationKey, String latestIncomingId, Chat chat, int attempt) {
        executorService.submit(() -> {
            boolean acquired = false;
            try {
                acquired = semaphore.tryAcquire();
                if (!acquired) {
                    Log.w(TAG, "All precompute slots busy, dropping request: conversation_key_present="
                            + (conversationKey != null && !conversationKey.isEmpty()));
                    return;
                }

                Log.d(TAG, "[PRECOMPUTE] Attempt " + (attempt + 1)
                        + ": conversation_key_present=" + (conversationKey != null && !conversationKey.isEmpty()));

                replyGenerator.generateReplies(chat, new ReplyGenerator.ReplyCallback() {
                    @Override
                    public void onRepliesGenerated(List<String> replies) {
                        if (replies != null && !replies.isEmpty()) {
                            List<String> topReplies = replies.subList(0, Math.min(3, replies.size()));
                            replyCache.put(conversationKey, latestIncomingId, topReplies);
                            AiEntitlementManager.getInstance(appContext)
                                    .record(AiActionType.BACKGROUND_PRECOMPUTE);
                            Log.d(TAG, "[PRECOMPUTE] Cached " + topReplies.size()
                                    + " replies: conversation_key_present="
                                    + (conversationKey != null && !conversationKey.isEmpty()));
                            // Trigger overlay badge refresh
                            project.witty.keys.app.overlay.WittyKeysOverlayService.triggerBadgeRefresh();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Precompute failed: attempt=" + (attempt + 1)
                                + ", conversation_key_present=" + (conversationKey != null && !conversationKey.isEmpty())
                                + ", error_present=" + (error != null && !error.isEmpty()));
                        if (attempt < MAX_RETRIES) {
                            long delay = RETRY_DELAY_MS * (long) Math.pow(2, attempt);
                            retryHandler.postDelayed(() ->
                                attemptGeneration(conversationKey, latestIncomingId, chat, attempt + 1), delay);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Precompute exception: conversation_key_present="
                        + (conversationKey != null && !conversationKey.isEmpty()), e);
            } finally {
                if (acquired) {
                    semaphore.release();
                }
            }
        });
    }

    /**
     * Builds a Chat object from NLS-captured messages.
     * Maps NLS data into the existing ReplyGenerator input format.
     */
    private Chat buildChatFromNlsMessages(List<MessageDebouncer.NlsMessage> messages,
                                           String packageName, String contactName) {
        // Convert NlsMessage list to ChatMessage list
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (MessageDebouncer.NlsMessage nlsMsg : messages) {
            ChatMessage chatMsg = new ChatMessage(
                nlsMsg.sender,
                nlsMsg.text,
                String.valueOf(nlsMsg.timestamp),
                false // NLS messages are incoming (not from current user)
            );
            chatMessages.add(chatMsg);
        }

        List<String> participants = new ArrayList<>();
        participants.add(contactName);

        // Chat constructor: (String appName, List<String> participants, List<ChatMessage> messages)
        return new Chat(packageName, participants, chatMessages);
    }

    private Chat buildChatFromBufferedMessages(List<NlsMessageBuffer.BufferedMessage> messages,
                                               String packageName, String contactName) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        if (messages != null) {
            for (NlsMessageBuffer.BufferedMessage message : messages) {
                chatMessages.add(new ChatMessage(
                        message.sender,
                        message.text,
                        String.valueOf(message.timestamp),
                        message.isSent));
            }
        }

        List<String> participants = new ArrayList<>();
        participants.add(contactName);
        return new Chat(packageName, participants, chatMessages);
    }

    /**
     * Invalidates cached replies for a conversation (e.g., when user sends a reply).
     * Called by SmartAssistantBar after tap-to-insert.
     */
    public void invalidateCache(String conversationKey) {
        replyCache.invalidate(conversationKey);
        Log.d(TAG, "[PRECOMPUTE] Cache invalidated: conversation_key_present="
                + (conversationKey != null && !conversationKey.isEmpty()));
    }

    /**
     * On-demand reply generation for a conversation.
     * Called by SmartAssistantBar when high confidence but no cache.
     * Uses buffered NLS messages if available.
     *
     * @param conversationKey e.g. "com.whatsapp|Gopu Abhishek"
     * @param packageName     e.g. "com.whatsapp"
     * @param contactName     e.g. "Gopu Abhishek"
     * @param callback        called on main thread with replies, or null on failure
     */
    public void generateOnDemand(String conversationKey, String packageName,
                                  String contactName, OnDemandCallback callback) {
        NlsMessageBuffer.ConversationSnapshot snapshot =
                NlsMessageBuffer.getInstance().openConversation(conversationKey);
        if (snapshot == null || snapshot.latestMessageSentByUser || snapshot.latestIncomingId == null) {
            Log.w(TAG, "[PRECOMPUTE] On-demand: no replyable incoming context");
            new Handler(Looper.getMainLooper()).post(() -> callback.onRepliesReady(null));
            return;
        }

        // Check cache first
        List<String> cached = replyCache.get(conversationKey, snapshot.latestIncomingId);
        if (cached != null && !cached.isEmpty()) {
            Log.d(TAG, "[PRECOMPUTE] On-demand cache hit: reply_count=" + cached.size());
            new Handler(Looper.getMainLooper()).post(() -> callback.onRepliesReady(cached));
            return;
        }

        if (cacheSafeMediaRepliesIfNeeded(conversationKey, snapshot.latestIncomingId, snapshot.messages)) {
            List<String> safeReplies = replyCache.get(conversationKey, snapshot.latestIncomingId);
            new Handler(Looper.getMainLooper()).post(() -> callback.onRepliesReady(safeReplies));
            return;
        }

        Chat chat = buildChatFromBufferedMessages(snapshot.messages, packageName, contactName);

        Log.d(TAG, "[PRECOMPUTE] On-demand: generating"
            + " (" + snapshot.messages.size() + " messages)");

        executorService.submit(() -> {
            try {
                replyGenerator.generateReplies(chat, new ReplyGenerator.ReplyCallback() {
                    @Override
                    public void onRepliesGenerated(List<String> replies) {
                        if (replies != null && !replies.isEmpty()) {
                            List<String> topReplies = replies.subList(0, Math.min(3, replies.size()));
                            replyCache.put(conversationKey, snapshot.latestIncomingId, topReplies);
                            Log.d(TAG, "[PRECOMPUTE] On-demand: cached " + topReplies.size()
                                + " replies");
                            // Trigger overlay badge refresh
                            project.witty.keys.app.overlay.WittyKeysOverlayService.triggerBadgeRefresh();
                            new Handler(Looper.getMainLooper()).post(
                                () -> callback.onRepliesReady(topReplies));
                        } else {
                            new Handler(Looper.getMainLooper()).post(
                                () -> callback.onRepliesReady(null));
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "[PRECOMPUTE] On-demand failed: error_present="
                                + (error != null && !error.isEmpty()));
                        new Handler(Looper.getMainLooper()).post(
                            () -> callback.onRepliesReady(null));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "[PRECOMPUTE] On-demand exception", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onRepliesReady(null));
            }
        });
    }

    public interface OnDemandCallback {
        void onRepliesReady(List<String> replies);
    }

    private boolean cacheSafeMediaRepliesIfNeeded(String conversationKey, String latestIncomingId,
                                                  List<NlsMessageBuffer.BufferedMessage> messages) {
        NlsMessageBuffer.BufferedMessage latestIncoming = latestIncomingMessage(messages);
        if (latestIncoming == null
                || !MediaMessageNormalizer.isMediaPlaceholderText(latestIncoming.text)) {
            return false;
        }

        List<String> safeReplies = MediaMessageNormalizer.safeRepliesForMediaPlaceholder(latestIncoming.text);
        replyCache.put(conversationKey, latestIncomingId, safeReplies);
        project.witty.keys.app.overlay.WittyKeysOverlayService.triggerBadgeRefresh();
        Log.d(TAG, "[PRECOMPUTE] Cached safe media placeholder replies");
        return true;
    }

    private NlsMessageBuffer.BufferedMessage latestIncomingMessage(
            List<NlsMessageBuffer.BufferedMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int index = messages.size() - 1; index >= 0; index--) {
            NlsMessageBuffer.BufferedMessage message = messages.get(index);
            if (message != null && !message.isSent) {
                return message;
            }
        }
        return null;
    }

    /**
     * Cleanup when app is shutting down.
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
