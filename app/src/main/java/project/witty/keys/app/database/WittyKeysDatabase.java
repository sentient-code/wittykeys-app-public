package project.witty.keys.app.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {
    ChatSession.class,
    ChatMessage.class,
    SessionScreenshot.class,
    ActionFrequency.class
}, version = 5, exportSchema = true)
public abstract class WittyKeysDatabase extends RoomDatabase {
    private static volatile WittyKeysDatabase INSTANCE;

    public abstract ChatSessionDao chatSessionDao();
    public abstract ChatMessageDao chatMessageDao();
    public abstract SessionScreenshotDao screenshotDao();
    public abstract ActionFrequencyDao actionFrequencyDao();

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE chat_sessions ADD COLUMN source TEXT DEFAULT 'keyboard'"
            );
            database.execSQL(
                "ALTER TABLE chat_sessions ADD COLUMN source_icon TEXT DEFAULT '⌨️'"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_chat_sessions_source ON chat_sessions(source)"
            );
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("UPDATE chat_sessions SET summary = NULL");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "UPDATE chat_sessions SET summary = (" +
                "  SELECT SUBSTR(content, 1, 80)" +
                "  FROM chat_messages" +
                "  WHERE session_id = chat_sessions.id" +
                "    AND role = 'user'" +
                "    AND (type IS NULL OR type != 'nls_context')" +
                "  ORDER BY timestamp ASC LIMIT 1" +
                ") WHERE summary IS NULL"
            );
        }
    };

    public static WittyKeysDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (WittyKeysDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        WittyKeysDatabase.class,
                        "wittykeys_chat.db"
                    ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
