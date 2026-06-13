package project.witty.keys.app.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ChatMessageDao {

    @Insert
    long insert(ChatMessage message);

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    List<ChatMessage> getBySession(long sessionId);

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    LiveData<List<ChatMessage>> getMessagesForSession(long sessionId);

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    List<ChatMessage> getMessagesForSessionSync(long sessionId);

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    List<ChatMessage> getRecentBySession(long sessionId, int limit);

    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId")
    int getMessageCount(long sessionId);

    @Query("SELECT SUM(token_count) FROM chat_messages WHERE session_id = :sessionId")
    int getTotalTokens(long sessionId);

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    void deleteBySession(long sessionId);

    @Query("DELETE FROM chat_messages")
    void deleteAll();
}
