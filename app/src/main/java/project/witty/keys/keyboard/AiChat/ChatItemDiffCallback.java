package project.witty.keys.keyboard.AiChat;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

/**
 * DiffUtil.Callback for ChatAdapter — enables efficient partial updates
 * instead of full notifyDataSetChanged() rebinds.
 */
public class ChatItemDiffCallback extends DiffUtil.Callback {

    private final List<ChatItem> oldList;
    private final List<ChatItem> newList;

    public ChatItemDiffCallback(List<ChatItem> oldList, List<ChatItem> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        ChatItem oldItem = oldList.get(oldItemPosition);
        ChatItem newItem = newList.get(newItemPosition);
        // Same item if IDs match (UUID-based for messages, singleton for Loading)
        return oldItem.getId().equals(newItem.getId());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        ChatItem oldItem = oldList.get(oldItemPosition);
        ChatItem newItem = newList.get(newItemPosition);
        // Same view type is a prerequisite
        if (oldItem.getViewType() != newItem.getViewType()) return false;
        // Deep equals check
        return oldItem.equals(newItem);
    }
}
