package project.witty.keys.app.context;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import project.witty.keys.app.database.ChatMessage;
import project.witty.keys.app.database.ChatSession;

/**
 * AiChatStateManager — Singleton state holder for AI Chat.
 * Build 7.0 Phase 4.
 *
 * Shared between UnifiedAiView (compact mode) and AiChatActivity (full-screen, P5).
 * Uses LiveData for reactive updates and SharedPreferences for crash recovery.
 * @deprecated Use UnifiedChatSessionManager instead. Will be removed in Build 7.2.
 */
@Deprecated
public class AiChatStateManager {

    private static final String TAG = "AiChatStateManager";
    private static final String PREFS_NAME = "ai_chat_state";
    private static final String KEY_ACTIVE_SESSION_ID = "active_session_id";
    private static final String KEY_CONVERSATION_KEY = "conversation_key";
    private static final String KEY_CONTACT_NAME = "contact_name";

    private static AiChatStateManager instance;

    private SharedPreferences prefs;
    private boolean initialized = false;

    // Active session
    private ChatSession activeSession;
    private final MutableLiveData<ChatSession> activeSessionLive = new MutableLiveData<>();

    // Messages for current session (in-memory cache)
    private final List<ChatMessage> messages = new ArrayList<>();
    private final MutableLiveData<List<ChatMessage>> messagesLive = new MutableLiveData<>(new ArrayList<>());

    // Chat open/close state
    private final MutableLiveData<Boolean> isChatOpen = new MutableLiveData<>(false);

    // Loading state
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    private AiChatStateManager() {}

    public static synchronized AiChatStateManager getInstance() {
        if (instance == null) {
            instance = new AiChatStateManager();
        }
        return instance;
    }

    /**
     * Initialize with application context. Call once from Application or first access.
     */
    public void init(Context context) {
        if (initialized) return;
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initialized = true;
        Log.d(TAG, "Initialized");
    }

    // --- Session Management ---

    /**
     * Set the active session. Called when AI Chat opens or switches sessions.
     */
    public void setActiveSession(ChatSession session) {
        this.activeSession = session;
        activeSessionLive.postValue(session);
        if (session != null && prefs != null) {
            prefs.edit()
                .putLong(KEY_ACTIVE_SESSION_ID, session.id)
                .putString(KEY_CONVERSATION_KEY, session.conversationKey)
                .putString(KEY_CONTACT_NAME, session.contactName)
                .apply();
        }
        Log.d(TAG, "Active session set: " + (session != null ? "#" + session.id : "null"));
    }

    public ChatSession getActiveSession() {
        return activeSession;
    }

    public long getActiveSessionId() {
        return activeSession != null ? activeSession.id : -1;
    }

    public LiveData<ChatSession> getActiveSessionLive() {
        return activeSessionLive;
    }

    /**
     * Restore session from SharedPreferences after service destruction.
     * Returns the saved session ID, or -1 if none.
     */
    public long getSavedSessionId() {
        if (prefs == null) return -1;
        return prefs.getLong(KEY_ACTIVE_SESSION_ID, -1);
    }

    public String getSavedConversationKey() {
        if (prefs == null) return null;
        return prefs.getString(KEY_CONVERSATION_KEY, null);
    }

    // --- Message Management ---

    /**
     * Set the full message list (used when loading from DB).
     */
    public void setMessages(List<ChatMessage> msgs) {
        messages.clear();
        messages.addAll(msgs);
        messagesLive.postValue(new ArrayList<>(messages));
    }

    /**
     * Add a single message to the in-memory list.
     */
    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        messagesLive.postValue(new ArrayList<>(messages));
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public LiveData<List<ChatMessage>> getMessagesLive() {
        return messagesLive;
    }

    // --- Chat State ---

    public void setChatOpen(boolean open) {
        isChatOpen.postValue(open);
        Log.d(TAG, "Chat open: " + open);
    }

    public LiveData<Boolean> getIsChatOpen() {
        return isChatOpen;
    }

    public void setLoading(boolean loading) {
        isLoading.postValue(loading);
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    // --- Context for Claude API ---

    /**
     * Build a context string from recent NLS messages for the active conversation.
     * Used by ContextWindowManager when constructing Claude API calls.
     */
    public String getNlsContextSummary() {
        // NLS context is injected as "context" role messages with type "nls_context"
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            if ("nls_context".equals(msg.type)) {
                sb.append(msg.content).append("\n");
            }
        }
        return sb.toString().trim();
    }

    // --- Cleanup ---

    /**
     * Clear all in-memory state. Called when keyboard is destroyed or user closes chat.
     */
    public void clearState() {
        activeSession = null;
        activeSessionLive.postValue(null);
        messages.clear();
        messagesLive.postValue(new ArrayList<>());
        isChatOpen.postValue(false);
        isLoading.postValue(false);
        // Do NOT clear SharedPreferences — they're the crash recovery backup
        Log.d(TAG, "State cleared");
    }
}
