package project.witty.keys.app.context;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import project.witty.keys.app.database.WittyKeysDatabase;
import project.witty.keys.app.database.ChatMessage;
import project.witty.keys.app.database.ChatSession;
import project.witty.keys.app.database.ChatSessionDao;
import project.witty.keys.app.database.ChatMessageDao;
import project.witty.keys.app.database.SessionScreenshot;
import project.witty.keys.app.database.SessionScreenshotDao;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton that manages ALL chat sessions across keyboard, overlay, and fullscreen.
 * Replaces AiChatStateManager (SharedPrefs) and AiChatSession (in-memory LinkedList).
 * All state persisted in Room DB via ChatSessionDao/ChatMessageDao.
 */
public class UnifiedChatSessionManager {
    private static final String TAG = "UnifiedChatSessionMgr";
    private static volatile UnifiedChatSessionManager instance;

    private final ChatSessionDao sessionDao;
    private final ChatMessageDao messageDao;
    private final SessionScreenshotDao screenshotDao;
    private final ExecutorService executor;
    private long activeSessionId = -1;

    // Source constants — used in ChatSession.source field
    public static final String SOURCE_KEYBOARD = "keyboard";
    public static final String SOURCE_OVERLAY = "overlay";
    public static final String SOURCE_FULLSCREEN = "fullscreen";

    // Source icon constants — used in ChatSession.sourceIcon field
    public static final String ICON_KEYBOARD = "⌨️";
    public static final String ICON_OVERLAY = "🔮";
    public static final String ICON_FULLSCREEN = "↗️";

    private UnifiedChatSessionManager(Context context) {
        WittyKeysDatabase db = WittyKeysDatabase.getInstance(context.getApplicationContext());
        this.sessionDao = db.chatSessionDao();
        this.messageDao = db.chatMessageDao();
        this.screenshotDao = db.screenshotDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static UnifiedChatSessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (UnifiedChatSessionManager.class) {
                if (instance == null) {
                    instance = new UnifiedChatSessionManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Create a new chat session. Returns session ID via callback (runs on executor).
     * Source lives in ChatSession.source; title is content-only for display.
     */
    public void createSession(String source, String sourceIcon, String title,
                              OnSessionCreatedCallback callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            ChatSession session = new ChatSession();
            session.title = title;
            session.createdAt = now;
            session.updatedAt = now;
            session.isArchived = false;
            session.source = source;
            session.sourceIcon = sourceIcon;
            session.messageCount = 0;
            session.totalTokens = 0;
            long id = sessionDao.insert(session);
            activeSessionId = id;
            Log.d(TAG, "Created session " + id + " source=" + source);
            if (callback != null) {
                callback.onCreated(id);
            }
        });
    }

    /**
     * Add a message to a session. Updates session's updatedAt timestamp.
     */
    public void addMessage(long sessionId, String role, String content, String type) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            ChatMessage msg = new ChatMessage();
            msg.sessionId = sessionId;
            msg.role = role;
            msg.content = content;
            msg.type = type;
            msg.timestamp = now;
            messageDao.insert(msg);
            sessionDao.incrementMessageCount(sessionId, now);
            maybeUpdateSummary(sessionId, role, content);
        });
    }

    /** Inserts a screenshot record and a linked chat message atomically on the DB executor. */
    public void addMessageWithScreenshot(long sessionId, String role, String content,
                                         String type, String imagePath) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            SessionScreenshot ss = new SessionScreenshot();
            ss.sessionId = sessionId;
            ss.filePath = imagePath;
            ss.capturedAt = now;
            long ssId = screenshotDao.insert(ss);

            ChatMessage msg = new ChatMessage();
            msg.sessionId = sessionId;
            msg.role = role;
            msg.content = content;
            msg.type = type;
            msg.timestamp = now;
            msg.screenshotId = ssId;
            messageDao.insert(msg);
            sessionDao.incrementMessageCount(sessionId, now);
            maybeUpdateSummary(sessionId, role, content);
        });
    }

    private void maybeUpdateSummary(long sessionId, String role, String content) {
        if (!"user".equals(role) || content == null || content.isEmpty()) return;
        String snippet = content.length() > 80 ? content.substring(0, 80) + "…" : content;
        sessionDao.updateSummaryIfEmpty(sessionId, snippet);
    }

    /**
     * Get all active (non-archived) sessions as LiveData for UI observation.
     */
    public LiveData<List<ChatSession>> getAllSessions() {
        return sessionDao.getAllActiveSessions();
    }

    /**
     * Get all active sessions synchronously (for non-UI contexts).
     */
    public List<ChatSession> getAllSessionsSync() {
        return sessionDao.getAllActive();
    }

    public List<ChatSession> getSessionsPageSync(int limit, int offset) {
        return sessionDao.getActiveSessionsPage(limit, offset);
    }

    /**
     * Get a specific session by ID.
     */
    public LiveData<ChatSession> getSession(long sessionId) {
        return sessionDao.getSessionById(sessionId);
    }

    /**
     * Get a specific session by ID synchronously.
     */
    public ChatSession getSessionSync(long sessionId) {
        return sessionDao.getById(sessionId);
    }

    /**
     * Get all messages for a session as LiveData.
     */
    public LiveData<List<ChatMessage>> getMessages(long sessionId) {
        return messageDao.getMessagesForSession(sessionId);
    }

    /**
     * Get messages synchronously (for non-UI contexts like overlay).
     */
    public List<ChatMessage> getMessagesSync(long sessionId) {
        return messageDao.getMessagesForSessionSync(sessionId);
    }

    /**
     * Set the currently active session (shared across surfaces).
     */
    public void setActiveSessionId(long sessionId) {
        this.activeSessionId = sessionId;
    }

    public long getActiveSessionId() {
        return activeSessionId;
    }

    /**
     * Build a content-only session title from initial message context.
     * The surface is rendered separately with WkSourceTag.
     */
    public static String buildTitle(String source, String context) {
        if (context == null || context.trim().isEmpty()) {
            return "New Chat";
        }
        String cleanContext = context.trim();
        String truncated = cleanContext.length() > 50
            ? cleanContext.substring(0, 47) + "..."
            : cleanContext;
        return truncated;
    }

    /**
     * Delete a single session by ID. Room cascades to its messages via FK.
     */
    public void deleteSession(long sessionId, Runnable onComplete) {
        executor.execute(() -> {
            sessionDao.deleteById(sessionId);
            if (activeSessionId == sessionId) activeSessionId = -1;
            Log.d(TAG, "Deleted session " + sessionId);
            if (onComplete != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
            }
        });
    }

    /**
     * Delete all sessions and messages. Used for data cleanup.
     */
    public void deleteAllSessions() {
        executor.execute(() -> {
            sessionDao.deleteAll();
            messageDao.deleteAll();
            activeSessionId = -1;
            Log.d(TAG, "All sessions and messages deleted");
        });
    }

    public interface OnSessionCreatedCallback {
        void onCreated(long sessionId);
    }
}
