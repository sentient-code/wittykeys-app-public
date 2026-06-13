package project.witty.keys.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ActionFrequencyDao {

    @Query("SELECT * FROM action_frequency ORDER BY count DESC, lastUsed DESC LIMIT :limit")
    List<ActionFrequency> getTopByFrequency(int limit);

    @Query("SELECT * FROM action_frequency ORDER BY lastUsed DESC LIMIT :limit")
    List<ActionFrequency> getRecent(int limit);

    @Query("SELECT * FROM action_frequency WHERE actionType = :type AND parameter = :param LIMIT 1")
    ActionFrequency findByTypeAndParam(String type, String param);

    @Query("UPDATE action_frequency SET count = count + 1, lastUsed = :timestamp WHERE actionType = :type AND parameter = :param")
    int incrementAction(String type, String param, long timestamp);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ActionFrequency action);

    @Query("SELECT COUNT(*) FROM action_frequency")
    int getTotalActions();

    @Query("DELETE FROM action_frequency")
    void deleteAll();
}
