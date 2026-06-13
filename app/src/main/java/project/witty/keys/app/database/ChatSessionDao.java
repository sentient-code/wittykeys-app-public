package project.witty.keys.app.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ChatSessionDao {

    @Insert
    long insert(ChatSession session);

    @Update
    void update(ChatSession session);

    @Delete
    void delete(ChatSession session);

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    ChatSession getById(long sessionId);

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    LiveData<ChatSession> getSessionById(long sessionId);

    @Query("SELECT * FROM chat_sessions WHERE conversation_key = :conversationKey AND is_archived = 0 ORDER BY updated_at DESC LIMIT 1")
    ChatSession getActiveByConversationKey(String conversationKey);

    @Query("SELECT * FROM chat_sessions WHERE is_archived = 0 ORDER BY updated_at DESC")
    List<ChatSession> getAllActive();

    @Query("SELECT * FROM chat_sessions WHERE is_archived = 0 ORDER BY updated_at DESC")
    LiveData<List<ChatSession>> getAllActiveSessions();

    @Query("SELECT * FROM chat_sessions WHERE is_archived = 0 ORDER BY updated_at DESC LIMIT :limit")
    LiveData<List<ChatSession>> getRecentSessions(int limit);

    @Query("SELECT * FROM chat_sessions WHERE is_archived = 0 ORDER BY updated_at DESC LIMIT :limit OFFSET :offset")
    List<ChatSession> getActiveSessionsPage(int limit, int offset);

    @Query("SELECT * FROM chat_sessions WHERE contact_name = :contactName ORDER BY updated_at DESC")
    List<ChatSession> getByContact(String contactName);

    @Query("UPDATE chat_sessions SET message_count = message_count + 1, updated_at = :timestamp WHERE id = :sessionId")
    void incrementMessageCount(long sessionId, long timestamp);

    @Query("UPDATE chat_sessions SET summary = :summary WHERE id = :sessionId AND summary IS NULL")
    void updateSummaryIfEmpty(long sessionId, String summary);

    @Query("UPDATE chat_sessions SET total_tokens = :totalTokens WHERE id = :sessionId")
    void updateTokenCount(long sessionId, int totalTokens);

    @Query("UPDATE chat_sessions SET is_archived = 1 WHERE id = :sessionId")
    void archive(long sessionId);

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    void deleteById(long sessionId);

    @Query("DELETE FROM chat_sessions")
    void deleteAll();
}
