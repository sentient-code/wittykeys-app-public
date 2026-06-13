package project.witty.keys.app.context;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import project.witty.keys.app.database.ChatMessage;
import project.witty.keys.app.database.ChatMessageDao;
import project.witty.keys.app.database.ChatSession;
import project.witty.keys.app.database.ChatSessionDao;
import project.witty.keys.app.database.SessionScreenshot;
import project.witty.keys.app.database.SessionScreenshotDao;
import project.witty.keys.app.database.WittyKeysDatabase;

/**
 * SessionRepository — Room DAO wrapper for AI Chat session management.
 * Build 7.0 Phase 4.
 *
 * Provides thread-safe CRUD for sessions, messages, and screenshots.
 * All database operations run on a background ExecutorService.
 */
public class SessionRepository {

    private static final String TAG = "SessionRepository";

    private final ChatSessionDao sessionDao;
    private final ChatMessageDao messageDao;
    private final SessionScreenshotDao screenshotDao;
    private final ExecutorService executor;
    private final Context appContext;

    private static SessionRepository instance;

    private SessionRepository(Context context) {
        this.appContext = context.getApplicationContext();
        WittyKeysDatabase db = WittyKeysDatabase.getInstance(appContext);
        this.sessionDao = db.chatSessionDao();
        this.messageDao = db.chatMessageDao();
        this.screenshotDao = db.screenshotDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static synchronized SessionRepository getInstance(Context context) {
        if (instance == null) {
            instance = new SessionRepository(context);
        }
        return instance;
    }

    // --- Session Operations ---

    /**
     * Get or create a session for the current conversation.
     * If an active (non-archived) session exists for this conversationKey, return it.
     * Otherwise, create a new one.
     */
    public void getOrCreateSession(String conversationKey, String contactName,
            String packageName, SessionCallback callback) {
        executor.execute(() -> {
            try {
                ChatSession session = sessionDao.getActiveByConversationKey(conversationKey);
                if (session == null) {
                    session = new ChatSession();
                    session.conversationKey = conversationKey;
                    session.contactName = contactName;
                    session.packageName = packageName;
                    session.createdAt = System.currentTimeMillis();
                    session.updatedAt = System.currentTimeMillis();
                    session.messageCount = 0;
                    session.totalTokens = 0;
                    session.isArchived = false;
                    session.id = sessionDao.insert(session);
                    Log.d(TAG, "Created new session #" + session.id + " for " + contactName);
                } else {
                    Log.d(TAG, "Resuming session #" + session.id + " for " + contactName);
                }
                final ChatSession result = session;
                postToMain(() -> callback.onSession(result));
            } catch (Exception e) {
                Log.e(TAG, "Error getting/creating session", e);
                postToMain(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Fetch an existing session by conversationKey — never creates one.
     * Callback fires only if a session already exists; silent no-op otherwise.
     */
    public void getSessionByConversationKey(String conversationKey, SessionCallback callback) {
        executor.execute(() -> {
            try {
                ChatSession session = sessionDao.getActiveByConversationKey(conversationKey);
                if (session != null) {
                    final ChatSession result = session;
                    postToMain(() -> callback.onSession(result));
                }
                // If null: no session yet — do nothing, don't create a blank row
            } catch (Exception e) {
                Log.e(TAG, "Error fetching session by key", e);
            }
        });
    }

    /**
     * Add a message to a session. Updates message count and timestamp.
     */
    public void addMessage(long sessionId, String role, String content,
            String type, int tokenCount, Long screenshotId, MessageCallback callback) {
        executor.execute(() -> {
            try {
                ChatMessage msg = new ChatMessage();
                msg.sessionId = sessionId;
                msg.role = role;
                msg.content = content;
                msg.timestamp = System.currentTimeMillis();
                msg.type = type != null ? type : "text";
                msg.tokenCount = tokenCount;
                msg.screenshotId = screenshotId;
                msg.id = messageDao.insert(msg);

                sessionDao.incrementMessageCount(sessionId, msg.timestamp);
                Log.d(TAG, "Added " + role + " message to session #" + sessionId);

                final ChatMessage result = msg;
                if (callback != null) {
                    postToMain(() -> callback.onMessage(result));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error adding message", e);
                if (callback != null) {
                    postToMain(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }

    /**
     * Get all messages for a session, ordered by timestamp ASC.
     */
    public void getMessages(long sessionId, MessagesCallback callback) {
        executor.execute(() -> {
            try {
                List<ChatMessage> messages = messageDao.getBySession(sessionId);
                postToMain(() -> callback.onMessages(messages));
            } catch (Exception e) {
                Log.e(TAG, "Error loading messages", e);
                postToMain(() -> callback.onMessages(Collections.emptyList()));
            }
        });
    }

    /**
     * Get recent messages (for context window building).
     */
    public void getRecentMessages(long sessionId, int limit, MessagesCallback callback) {
        executor.execute(() -> {
            try {
                List<ChatMessage> messages = messageDao.getRecentBySession(sessionId, limit);
                // Reverse to get chronological order (DAO returns DESC)
                Collections.reverse(messages);
                postToMain(() -> callback.onMessages(messages));
            } catch (Exception e) {
                Log.e(TAG, "Error loading recent messages", e);
                postToMain(() -> callback.onMessages(Collections.emptyList()));
            }
        });
    }

    /**
     * Get all active (non-archived) sessions, most recent first.
     */
    public void getAllActiveSessions(SessionsCallback callback) {
        executor.execute(() -> {
            try {
                List<ChatSession> sessions = sessionDao.getAllActive();
                postToMain(() -> callback.onSessions(sessions));
            } catch (Exception e) {
                Log.e(TAG, "Error loading sessions", e);
                postToMain(() -> callback.onSessions(Collections.emptyList()));
            }
        });
    }

    // --- Screenshot Operations ---

    /**
     * Save a screenshot record and return the ID.
     */
    public void addScreenshot(long sessionId, String filePath, String analysis,
            int width, int height, ScreenshotCallback callback) {
        executor.execute(() -> {
            try {
                SessionScreenshot ss = new SessionScreenshot();
                ss.sessionId = sessionId;
                ss.filePath = filePath;
                ss.capturedAt = System.currentTimeMillis();
                ss.extractedText = analysis;
                ss.width = width;
                ss.height = height;

                // Generate thumbnail path (same dir, _thumb suffix)
                String thumbPath = filePath.replace(".jpg", "_thumb.jpg");
                ss.thumbnailPath = thumbPath;

                ss.id = screenshotDao.insert(ss);
                Log.d(TAG, "Saved screenshot #" + ss.id + " for session #" + sessionId);

                final SessionScreenshot result = ss;
                if (callback != null) {
                    postToMain(() -> callback.onScreenshot(result));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving screenshot", e);
                if (callback != null) {
                    postToMain(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }

    /**
     * Get all screenshots for a session.
     */
    public void getScreenshots(long sessionId, ScreenshotsCallback callback) {
        executor.execute(() -> {
            try {
                List<SessionScreenshot> screenshots = screenshotDao.getBySession(sessionId);
                postToMain(() -> callback.onScreenshots(screenshots));
            } catch (Exception e) {
                Log.e(TAG, "Error loading screenshots", e);
                postToMain(() -> callback.onScreenshots(Collections.emptyList()));
            }
        });
    }

    // --- Token Management ---

    /**
     * Update total token count for a session.
     */
    public void updateTokenCount(long sessionId, int totalTokens) {
        executor.execute(() -> {
            try {
                sessionDao.updateTokenCount(sessionId, totalTokens);
            } catch (Exception e) {
                Log.e(TAG, "Error updating token count", e);
            }
        });
    }

    // --- Helpers ---

    private void postToMain(Runnable action) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(action);
    }

    // --- Callbacks ---

    public interface SessionCallback {
        void onSession(ChatSession session);
        void onError(String error);
    }

    public interface MessageCallback {
        void onMessage(ChatMessage message);
        void onError(String error);
    }

    public interface MessagesCallback {
        void onMessages(List<ChatMessage> messages);
    }

    public interface SessionsCallback {
        void onSessions(List<ChatSession> sessions);
    }

    public interface ScreenshotCallback {
        void onScreenshot(SessionScreenshot screenshot);
        void onError(String error);
    }

    public interface ScreenshotsCallback {
        void onScreenshots(List<SessionScreenshot> screenshots);
    }
}
