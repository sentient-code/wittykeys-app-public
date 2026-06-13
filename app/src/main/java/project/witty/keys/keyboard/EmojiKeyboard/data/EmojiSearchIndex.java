package project.witty.keys.keyboard.EmojiKeyboard.data;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fast prefix-search index over the emoji dataset.
 * <p>
 * Built once from {@link EmojiDataProvider}, maps lowercased keywords
 * (aliases, tags, description words) to sets of {@link EmojiEntry}.
 * <p>
 * Search uses AND logic across query words and returns results sorted
 * by relevance (exact-match first, then prefix-match, then by index
 * order in the original dataset).
 */
public final class EmojiSearchIndex {

    private static final String TAG = "WK_EMOJI_SEARCH";
    private static final int MAX_RESULTS = 200;
    private static final int MIN_QUERY_LENGTH = 2;

    /** keyword → set of EmojiEntry */
    private final HashMap<String, Set<EmojiEntry>> mIndex = new HashMap<>();

    /** All known keywords for prefix iteration */
    private final List<String> mKeywords = new ArrayList<>();

    public EmojiSearchIndex(EmojiDataProvider provider) {
        buildIndex(provider);
    }

    // ──────────────────────── Public API ────────────────────────

    /**
     * Search for emojis matching the query.
     *
     * @param query at least {@link #MIN_QUERY_LENGTH} chars
     * @return matching entries sorted by relevance, capped at {@link #MAX_RESULTS}
     */
    public List<EmojiEntry> search(String query) {
        if (query == null || query.length() < MIN_QUERY_LENGTH) {
            return Collections.emptyList();
        }

        String[] words = query.toLowerCase(Locale.US).trim().split("\\s+");
        if (words.length == 0) return Collections.emptyList();

        // For each query word, find all matching emojis (prefix match across keywords)
        Set<EmojiEntry> resultSet = null;
        for (String word : words) {
            Set<EmojiEntry> wordMatches = findMatchesForWord(word);
            if (wordMatches.isEmpty()) {
                return Collections.emptyList(); // AND logic: no match for one word = no results
            }
            if (resultSet == null) {
                resultSet = new LinkedHashSet<>(wordMatches);
            } else {
                resultSet.retainAll(wordMatches); // AND: intersect
            }
            if (resultSet.isEmpty()) {
                return Collections.emptyList();
            }
        }

        if (resultSet == null) return Collections.emptyList();

        // Sort by relevance
        List<EmojiEntry> results = new ArrayList<>(resultSet);
        final String firstWord = words[0];
        Collections.sort(results, new EmojiRelevanceComparator(firstWord));

        // Cap
        if (results.size() > MAX_RESULTS) {
            results = results.subList(0, MAX_RESULTS);
        }
        return results;
    }

    public int getIndexSize() {
        return mIndex.size();
    }

    // ──────────────────────── Index building ────────────────────────

    private void buildIndex(EmojiDataProvider provider) {
        long start = System.currentTimeMillis();
        List<EmojiEntry> all = provider.getAllEmojis();

        for (EmojiEntry entry : all) {
            // Index aliases
            for (String alias : entry.aliases) {
                addToIndex(alias.toLowerCase(Locale.US), entry);
            }
            // Index tags
            for (String tag : entry.tags) {
                addToIndex(tag.toLowerCase(Locale.US), entry);
            }
            // Index description words
            String[] descWords = entry.description.toLowerCase(Locale.US).split("\\s+");
            for (String word : descWords) {
                // Skip very short words and noise
                if (word.length() >= 2 && !isNoiseWord(word)) {
                    addToIndex(word, entry);
                }
            }
        }

        // Build sorted keyword list for prefix iteration
        mKeywords.addAll(mIndex.keySet());
        Collections.sort(mKeywords);

        long elapsed = System.currentTimeMillis() - start;
        Log.d(TAG, "Built search index: " + mIndex.size() + " keywords, "
                + all.size() + " emojis in " + elapsed + "ms");
    }

    private void addToIndex(String keyword, EmojiEntry entry) {
        Set<EmojiEntry> set = mIndex.get(keyword);
        if (set == null) {
            set = new LinkedHashSet<>();
            mIndex.put(keyword, set);
        }
        set.add(entry);
    }

    // ──────────────────────── Prefix search ────────────────────────

    private Set<EmojiEntry> findMatchesForWord(String word) {
        Set<EmojiEntry> matches = new LinkedHashSet<>();

        // Exact match first
        Set<EmojiEntry> exact = mIndex.get(word);
        if (exact != null) {
            matches.addAll(exact);
        }

        // Prefix matches — iterate sorted keywords using binary search for start
        int startIdx = Collections.binarySearch(mKeywords, word);
        if (startIdx < 0) startIdx = -(startIdx + 1);

        for (int i = startIdx; i < mKeywords.size(); i++) {
            String kw = mKeywords.get(i);
            if (!kw.startsWith(word)) break; // past prefix range
            if (kw.equals(word)) continue; // already added
            Set<EmojiEntry> prefixSet = mIndex.get(kw);
            if (prefixSet != null) {
                matches.addAll(prefixSet);
            }
        }

        return matches;
    }

    // ──────────────────────── Relevance sorting ────────────────────────

    private static class EmojiRelevanceComparator implements Comparator<EmojiEntry> {
        private final String mQuery;

        EmojiRelevanceComparator(String query) {
            mQuery = query;
        }

        @Override
        public int compare(EmojiEntry a, EmojiEntry b) {
            int scoreA = relevanceScore(a);
            int scoreB = relevanceScore(b);
            return scoreB - scoreA; // higher score first
        }

        private int relevanceScore(EmojiEntry entry) {
            int score = 0;
            // Exact alias match
            for (String alias : entry.aliases) {
                if (alias.equalsIgnoreCase(mQuery)) { score += 100; break; }
                if (alias.toLowerCase(Locale.US).startsWith(mQuery)) { score += 50; break; }
            }
            // Exact tag match
            for (String tag : entry.tags) {
                if (tag.equalsIgnoreCase(mQuery)) { score += 80; break; }
                if (tag.toLowerCase(Locale.US).startsWith(mQuery)) { score += 40; break; }
            }
            // Description contains
            if (entry.description.contains(mQuery)) score += 30;
            // Shorter descriptions rank higher (more specific)
            score -= entry.description.length() / 10;
            return score;
        }
    }

    // ──────────────────────── Noise filter ────────────────────────

    private static boolean isNoiseWord(String word) {
        switch (word) {
            case "of": case "the": case "a": case "an": case "in":
            case "on": case "to": case "for": case "and": case "or":
            case "is": case "it": case "at": case "by": case "with":
                return true;
            default:
                return false;
        }
    }
}
