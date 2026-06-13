package project.witty.keys.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import project.witty.keys.R;

public class HomeAdapter extends RecyclerView.Adapter<HomeAdapter.HomeViewHolder> {

    private List<HomeItem> homeList;


    public interface OnItemClickListener {
        void onItemClicked(int position);
    }

    private OnItemClickListener listener;


    public HomeAdapter(List<HomeItem> homeList,OnItemClickListener listener) {
        this.homeList = homeList;
        this.listener = listener;
    }

    @Override
    public HomeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.list_item_home, parent, false);
        return new HomeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(HomeViewHolder holder, int position) {
        HomeItem item = homeList.get(position);
        holder.icon.setImageResource(item.iconResId);
        holder.title.setText(item.title);
        holder.subtitle.setText(item.subtitle);
        holder.itemView.setOnClickListener(v -> listener.onItemClicked(position));
    }

    @Override
    public int getItemCount() {
        return homeList.size();
    }

    public static class HomeViewHolder extends RecyclerView.ViewHolder {
        private ImageView icon;
        private TextView title;
        private TextView subtitle;

        public HomeViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
        }
    }
}