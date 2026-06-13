package project.witty.keys.keyboard.AiChat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;
import java.util.List;
import project.witty.keys.R;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.keyboard.KeyboardSwitcher;

public class GridOptionsViewHolder extends RecyclerView.ViewHolder {
    private final TextView titleTextView;
    private final RecyclerView gridRecyclerView;
    private final GridOptionAdapter gridAdapter;

    public GridOptionsViewHolder(@NonNull View itemView) {
        super(itemView);
        titleTextView = itemView.findViewById(R.id.grid_options_title);
        gridRecyclerView = itemView.findViewById(R.id.grid_recycler_view);
        gridAdapter = new GridOptionAdapter(option -> {
            KeyboardSwitcher.getInstance().onGridOptionSelected(option);
        });
        gridRecyclerView.setAdapter(gridAdapter);
    }


    public void bind(GridOptions item, Context themedContext) {
        titleTextView.setText(item.getTitle());
        titleTextView.setTextColor(ThemeUtils.getThemeColor(themedContext, R.attr.productViewTitleColor));
        // Set up a horizontal layout manager so all options appear in a single row.
        // This replaces the default 4-column grid layout defined in XML.  A horizontal
        // LinearLayoutManager allows horizontal scrolling when there are many options,
        // matching the design of translation and tone selection rows.
        RecyclerView.LayoutManager lm = gridRecyclerView.getLayoutManager();
        if (!(lm instanceof androidx.recyclerview.widget.LinearLayoutManager) ||
                ((androidx.recyclerview.widget.LinearLayoutManager) lm).getOrientation() != androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL) {
            androidx.recyclerview.widget.LinearLayoutManager manager = new androidx.recyclerview.widget.LinearLayoutManager(gridRecyclerView.getContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false);
            gridRecyclerView.setLayoutManager(manager);
        }
        gridAdapter.updateOptions(item.getOptions(), themedContext);
    }

    public interface OnOptionClickListener {
        void onOptionClick(CategoryOption option);
    }

    private static class GridOptionAdapter extends RecyclerView.Adapter<GridOptionAdapter.OptionViewHolder> {
        private List<CategoryOption> options = new ArrayList<>();
        private Context themedContext;
        private final KeyboardSwitcher keyboardSwitcher = KeyboardSwitcher.getInstance();
        private final OnOptionClickListener clickListener;

        // Keep track of the currently selected item to provide visual feedback
        private int selectedPosition = RecyclerView.NO_POSITION;

        void updateOptions(List<CategoryOption> newOptions, Context context) {
            this.themedContext = context;
            this.options.clear();
            this.options.addAll(newOptions);
            // Reset selection each time options change
            selectedPosition = RecyclerView.NO_POSITION;
            notifyDataSetChanged();
        }

        GridOptionAdapter(OnOptionClickListener listener) {
            this.clickListener = listener;
        }

        @NonNull @Override
        public OptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_single_grid_option, parent, false);
            return new OptionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OptionViewHolder holder, int position) {
            boolean isSelected = position == selectedPosition;
            holder.bind(options.get(position), isSelected);
        }

        @Override public int getItemCount() { return options.size(); }

        class OptionViewHolder extends RecyclerView.ViewHolder {
            private final TextView label;

            OptionViewHolder(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.grid_option_label);
            }

            void bind(CategoryOption option, boolean isSelected) {
                // Populate icon and label with the option's data
                label.setText(option.getTitle());

                // Apply dynamic theming based on selection
                int bgColor = isSelected
                        ? ThemeUtils.getThemeColor(themedContext, R.attr.themedButtonPressedBackgroundColor)
                        : ThemeUtils.getThemeColor(themedContext, R.attr.themedButtonBackgroundColor);
                int textColor = isSelected
                        ? ThemeUtils.getThemeColor(themedContext, R.attr.themedButtonBackgroundColor)
                        : ThemeUtils.getThemeColor(themedContext, R.attr.themedButtonTextColor);

                // Tint the icon to match button text colour for readability
                label.setTextColor(textColor);

                // Create a pill‑shaped background similar to other CTA buttons
                float cornerRadius;
                try {
                    cornerRadius = itemView.getResources().getDimension(R.dimen.button_corner_radius_lxx);
                } catch (Exception e) {
                    cornerRadius = 8f * itemView.getResources().getDisplayMetrics().density;
                }
                itemView.setBackground(
                        ThemeUtils.createButtonBackground(
                                themedContext,
                                isSelected ? R.attr.themedButtonPressedBackgroundColor : R.attr.themedButtonBackgroundColor,
                                R.attr.themedButtonPressedBackgroundColor,
                                cornerRadius
                        )
                );
                int paddingH = (int) (12 * itemView.getResources().getDisplayMetrics().density);
                int paddingV = (int) (10 * itemView.getResources().getDisplayMetrics().density);
                itemView.setPadding(paddingH, paddingV, paddingH, paddingV);

                // Click behaviour: update selection state then delegate action
                itemView.setOnClickListener(v -> {
                    String optionTitle = option.getTitle(); // e.g. “Translate”, “Summarise”
                    FirebaseAnalytics analytics = FirebaseAnalytics.getInstance( KeyboardSwitcher.getInstance().getmLatinIME());
                    User user = EncryptedPreferences.getUserLoggedInInfo();
                    String userId = (user != null ? user.getId() : null);
                    EventHelpers.triggerScanOptionSelectedEvent(userId, optionTitle, analytics);
                    int previous = selectedPosition;
                    selectedPosition = getBindingAdapterPosition();
                    if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous);
                    notifyItemChanged(selectedPosition);


                    String action = option.getAction();
                    if (action != null && action.startsWith("task_")) {
                        // Remove the "task_" prefix when passing to SCAN_AND_EXECUTE so the userTask is correct.
                        String finalTask = action.substring("task_".length());
                        keyboardSwitcher.performAiAction(KeyboardSwitcher.AiAction.SCAN_AND_EXECUTE, null, finalTask);
                    } else {
                        clickListener.onOptionClick(option);
                    }
                });
            }
        }
    }
}