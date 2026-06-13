package project.witty.keys.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SessionScreenshotDao {

    @Insert
    long insert(SessionScreenshot screenshot);

    @Query("SELECT * FROM session_screenshots WHERE session_id = :sessionId ORDER BY captured_at DESC")
    List<SessionScreenshot> getBySession(long sessionId);

    @Query("SELECT * FROM session_screenshots WHERE id = :screenshotId")
    SessionScreenshot getById(long screenshotId);

    @Query("DELETE FROM session_screenshots WHERE session_id = :sessionId")
    void deleteBySession(long sessionId);
}
