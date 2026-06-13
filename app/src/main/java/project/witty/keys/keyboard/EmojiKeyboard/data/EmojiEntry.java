package project.witty.keys.keyboard.EmojiKeyboard.data;

import java.util.Collections;
import java.util.List;

/**
 * Data class representing a single emoji parsed from emojis.json.
 */
public final class EmojiEntry {
    public final String emojiChar;
    public final String description;
    public final List<String> aliases;
    public final List<String> tags;
    public final String category;

    public EmojiEntry(String emojiChar, String description,
                      List<String> aliases, List<String> tags, String category) {
        this.emojiChar = emojiChar;
        this.description = description;
        this.aliases = aliases != null ? Collections.unmodifiableList(aliases) : Collections.emptyList();
        this.tags = tags != null ? Collections.unmodifiableList(tags) : Collections.emptyList();
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmojiEntry)) return false;
        return emojiChar.equals(((EmojiEntry) o).emojiChar);
    }

    @Override
    public int hashCode() {
        return emojiChar.hashCode();
    }
}
