package project.witty.keys.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Tracks how often the user performs each AI action with specific parameters.
 * Used by ActionTracker to personalize the Row 2 chip bar.
 */
@Entity(tableName = "action_frequency",
        indices = {@Index(value = {"actionType", "parameter"}, unique = true)})
public class ActionFrequency {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Action category: "tone", "translate", "grammar", "custom" */
    @NonNull
    public String actionType;

    /** Specific parameter: "casual", "professional", "hi", "es", "make it shorter", etc. */
    @NonNull
    public String parameter;

    /** How many times this action+parameter combo was used */
    public int count;

    /** Timestamp of last use (System.currentTimeMillis()) */
    public long lastUsed;

    /** Display emoji for the chip (e.g., "😎", "🌐", "✓", "✏️") */
    public String emoji;

    /** Display label for the chip (e.g., "Casual", "→ Hindi", "Grammar", "make shorter") */
    public String displayLabel;
}
