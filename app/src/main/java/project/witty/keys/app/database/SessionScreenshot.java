package project.witty.keys.app.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "session_screenshots",
    foreignKeys = @ForeignKey(
        entity = ChatSession.class,
        parentColumns = "id",
        childColumns = "session_id",
        onDelete = ForeignKey.CASCADE),
    indices = {
        @Index("session_id")
    })
public class SessionScreenshot {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "session_id")
    public long sessionId;

    @ColumnInfo(name = "file_path")
    public String filePath; // Internal storage JPEG

    @ColumnInfo(name = "thumbnail_path")
    public String thumbnailPath;

    @ColumnInfo(name = "captured_at")
    public long capturedAt;

    @ColumnInfo(name = "extracted_text")
    public String extractedText; // Cached Claude Vision analysis

    @ColumnInfo(name = "width")
    public int width;

    @ColumnInfo(name = "height")
    public int height;
}
