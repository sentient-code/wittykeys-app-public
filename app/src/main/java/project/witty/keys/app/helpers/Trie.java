package project.witty.keys.app.helpers;

import java.util.ArrayList;
import java.util.List;

// Trie.java
public class Trie {
    private final TrieNode root = new TrieNode();

    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node.children.putIfAbsent(c, new TrieNode());
            node = node.children.get(c);
        }
        node.isEndOfWord = true;
    }

    public List<String> searchPrefix(String prefix) {
        List<String> suggestions = new ArrayList<>();
        TrieNode node = root;
        // Traverse to the end of the prefix
        for (char c : prefix.toCharArray()) {
            if (!node.children.containsKey(c)) return suggestions;
            node = node.children.get(c);
        }
        // Collect all words under this prefix
        dfs(node, prefix, suggestions);
        return suggestions;
    }

    private void dfs(TrieNode node, String currentWord, List<String> suggestions) {
        if (node.isEndOfWord) {
            suggestions.add(currentWord);
        }
        for (char c : node.children.keySet()) {
            dfs(node.children.get(c), currentWord + c, suggestions);
        }
    }
}