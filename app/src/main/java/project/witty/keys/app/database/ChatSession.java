package project.witty.keys.app.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_sessions",
    indices = {
        @Index("contact_name"),
        @Index("updated_at"),
        @Index("package_name"),
        @Index("source")
    })
public class ChatSession {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "contact_name")
    public String contactName;

    @ColumnInfo(name = "package_name")
    public String packageName;

    @ColumnInfo(name = "conversation_key")
    public String conversationKey;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    @ColumnInfo(name = "title")
    public String title; // Auto-generated from first message

    @ColumnInfo(name = "summary")
    public String summary; // For token management

    @ColumnInfo(name = "message_count")
    public int messageCount;

    @ColumnInfo(name = "total_tokens")
    public int totalTokens;

    @ColumnInfo(name = "is_archived")
    public boolean isArchived;

    @ColumnInfo(name = "source", defaultValue = "keyboard")
    public String source; // "keyboard", "overlay", "fullscreen"

    @ColumnInfo(name = "source_icon", defaultValue = "⌨️")
    public String sourceIcon; // "⌨️", "🔮", "↗️"
}
