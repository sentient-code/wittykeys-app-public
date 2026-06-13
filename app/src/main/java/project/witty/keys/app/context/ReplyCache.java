package project.witty.keys.app.context;

import android.util.Log;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import project.witty.keys.app.helpers.JourneyTracer;

/**
 * LRU cache for pre-computed smart replies.
 *
 * - Capacity: 10 conversations
 * - TTL: 10 minutes per entry
 * - Keyed by conversationKey (format: "package|contactName")
 * - Thread-safe via synchronized methods
 *
 * Invalidation triggers:
 * - User sends a reply (conversation context changed)
 * - New messages arrive for the same conversation (re-precompute)
 * - TTL expiration (stale replies)
 */
public class ReplyCache {

    private static final String TAG = "ReplyCache";
    private static final int MAX_ENTRIES = 10;
    private static final long TTL_MS = 10 * 60 * 1000; // 10 minutes

    private static ReplyCache instance;

    private final LinkedHashMap<String, CacheEntry> cache;

    private ReplyCache() {
        // LRU eviction: removeEldestEntry when size exceeds MAX_ENTRIES
        this.cache = new LinkedHashMap<String, CacheEntry>(MAX_ENTRIES + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > MAX_ENTRIES;
            }
        };
    }

    public static synchronized ReplyCache getInstance() {
        if (instance == null) {
            instance = new ReplyCache();
        }
        return instance;
    }

    /**
     * Store pre-computed replies for a conversation.
     */
    public synchronized void put(String conversationKey, List<String> replies) {
        if (conversationKey == null || replies == null || replies.isEmpty()) return;
        cache.put(conversationKey, new CacheEntry(replies, System.currentTimeMillis()));
        Log.d(TAG, "Cached " + replies.size() + " replies for " + conversationKey
                + " (total entries: " + cache.size() + ")");

        // JourneyTracer: reply cached
        String traceId = JourneyTracer.getCurrentSmartReplyTrace();
        if (traceId != null) {
            try {
                JSONObject dataIn = new JSONObject();
                dataIn.put("key", conversationKey);
                dataIn.put("reply_count", replies.size());
                JSONObject dataOut = new JSONObject();
                dataOut.put("cache_size", cache.size());
                JourneyTracer.step(traceId, "REPLY_CACHE_STORED", dataIn, dataOut, null);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void put(String conversationKey, String latestIncomingId, List<String> replies) {
        if (conversationKey == null || latestIncomingId == null || replies == null || replies.isEmpty()) return;
        cache.put(scopedKey(conversationKey, latestIncomingId), new CacheEntry(replies, System.currentTimeMillis()));
        Log.d(TAG, "Cached scoped replies for conversation_key_present="
                + (conversationKey != null && !conversationKey.isEmpty())
                + ", incoming_id_present=true");
    }

    /**
     * Retrieve cached replies for a conversation.
     * Returns null if not cached or expired.
     */
    public synchronized List<String> get(String conversationKey) {
        if (conversationKey == null) return null;

        CacheEntry entry = cache.get(conversationKey);
        if (entry == null) return null;

        // TTL check
        if (System.currentTimeMillis() - entry.timestamp > TTL_MS) {
            cache.remove(conversationKey);
            Log.d(TAG, "Cache expired for " + conversationKey);
            return null;
        }

        return entry.replies;
    }

    public synchronized List<String> get(String conversationKey, String latestIncomingId) {
        if (conversationKey == null || latestIncomingId == null) return null;
        return get(scopedKey(conversationKey, latestIncomingId));
    }

    /**
     * Check if fresh (non-expired) replies exist for a conversation.
     */
    public synchronized boolean hasFreshReplies(String conversationKey) {
        return get(conversationKey) != null;
    }

    public synchronized boolean hasFreshReplies(String conversationKey, String latestIncomingId) {
        return get(conversationKey, latestIncomingId) != null;
    }

    /**
     * Invalidate cache for a specific conversation.
     * Called when user sends a reply or new messages arrive.
     */
    public synchronized void invalidate(String conversationKey) {
        if (conversationKey == null) return;
        cache.remove(conversationKey);
    }

    public synchronized void invalidateConversation(String conversationKey) {
        if (conversationKey == null) return;
        cache.remove(conversationKey);
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationKey + "#"));
    }

    /**
     * Invalidate all entries for a specific package (app).
     * Useful when app context changes significantly.
     */
    public synchronized void invalidateByPackage(String packageName) {
        if (packageName == null) return;
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(packageName + "|"));
    }

    /**
     * Clear entire cache.
     */
    public synchronized void clear() {
        cache.clear();
    }

    /**
     * Get current cache size (for debugging/analytics).
     */
    public synchronized int size() {
        return cache.size();
    }

    private String scopedKey(String conversationKey, String latestIncomingId) {
        return conversationKey + "#" + latestIncomingId;
    }

    private static class CacheEntry {
        final List<String> replies;
        final long timestamp;

        CacheEntry(List<String> replies, long timestamp) {
            this.replies = replies;
            this.timestamp = timestamp;
        }
    }
}
