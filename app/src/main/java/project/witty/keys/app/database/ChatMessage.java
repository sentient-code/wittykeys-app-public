package project.witty.keys.app.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages",
    foreignKeys = @ForeignKey(
        entity = ChatSession.class,
        parentColumns = "id",
        childColumns = "session_id",
        onDelete = ForeignKey.CASCADE),
    indices = {
        @Index("session_id"),
        @Index("timestamp")
    })
public class ChatMessage {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "session_id")
    public long sessionId;

    @ColumnInfo(name = "role")
    public String role; // "user", "assistant", "system", "context"

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "type")
    public String type; // "text", "screenshot_analysis", "nls_context", "reply_suggestion"

    @ColumnInfo(name = "token_count")
    public int tokenCount;

    @ColumnInfo(name = "screenshot_id")
    public Long screenshotId; // Nullable, links to screenshot
}
