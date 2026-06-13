package project.witty.keys.app;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

import project.witty.keys.R;

public class SubscriptionAdapter extends RecyclerView.Adapter<SubscriptionAdapter.SubscriptionViewHolder> {

    private List<SubscriptionItem> subscriptionItems;
    private int selectedPosition = RecyclerView.NO_POSITION; // To track selected item
    private SubscriptionFragment.OnSubscriptionSelectedListener listener;

    public SubscriptionAdapter(List<SubscriptionItem> subscriptionItems, SubscriptionFragment.OnSubscriptionSelectedListener listener) {
        this.subscriptionItems = subscriptionItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SubscriptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_subscription_card, parent, false);
        return new SubscriptionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubscriptionViewHolder holder, int position) {
        SubscriptionItem item = subscriptionItems.get(position);
        holder.name.setText(displayTitleFor(item));
        holder.description.setText(displayDescriptionFor(item));
        holder.originalPrice.setText(item.originalPrice);
        holder.finalPrice.setText(item.finalPrice);
        holder.originalPriceTitle.setText("Google Play plan");
        holder.finalPriceTitle.setText("Google Play price");
        // Clear previous features
        holder.featuresContainer.removeAllViews();

        // Add features dynamically
        for (String feature : displayBenefitsFor(item)) {
            TextView featureView = new TextView(holder.featuresContainer.getContext());
            featureView.setText(feature);
            featureView.setTextColor(color(holder, R.color.wk_overlay_dark_text2));
            featureView.setTextSize(11);
            featureView.setTypeface(null, Typeface.NORMAL);
            holder.featuresContainer.addView(featureView);
        }

        // Highlight selected item
        if (selectedPosition == position) {
            holder.cardContent.setBackgroundResource(R.drawable.wk_app_card_hero_bg);
            holder.name.setTextColor(color(holder, R.color.wk_overlay_dark_text));
            holder.description.setTextColor(color(holder, R.color.wk_overlay_dark_text));
            holder.originalPrice.setTextColor(color(holder, R.color.wk_overlay_dark_text));
            holder.finalPrice.setTextColor(color(holder, R.color.wk_overlay_dark_accent));
            holder.roundedView.setColorFilter(color(holder, R.color.wk_overlay_dark_text));
            holder.finalPriceTitle.setTextColor(color(holder, R.color.wk_overlay_dark_text2));
            holder.originalPriceTitle.setTextColor(color(holder, R.color.wk_overlay_dark_text2));
        } else {
            holder.cardContent.setBackgroundResource(R.drawable.wk_app_card_bg);
            holder.name.setTextColor(color(holder, R.color.wk_overlay_dark_text));
            holder.description.setTextColor(color(holder, R.color.wk_overlay_dark_text2));
            holder.originalPrice.setTextColor(color(holder, R.color.wk_overlay_dark_text2));
            holder.finalPrice.setTextColor(color(holder, R.color.wk_overlay_dark_accent));
            holder.roundedView.setColorFilter(color(holder, R.color.wk_overlay_dark_accent));
            holder.finalPriceTitle.setTextColor(color(holder, R.color.wk_overlay_dark_text2));
            holder.originalPriceTitle.setTextColor(color(holder, R.color.wk_overlay_dark_text2));
        }
        holder.cardView.setCardElevation(0);

        holder.cardView.setOnClickListener(v -> {
            if (selectedPosition != holder.getAdapterPosition()) {
                int previousSelectedPosition = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(previousSelectedPosition);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onSubscriptionSelected(subscriptionItems.get(position));
                }
            } else {
                int previousSelectedPosition = selectedPosition;
                selectedPosition = RecyclerView.NO_POSITION;
                notifyItemChanged(previousSelectedPosition);
                if (listener != null) {
                    listener.onSubscriptionSelected(null);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return subscriptionItems.size();
    }

    public static class SubscriptionViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView description;
        TextView originalPrice;
        TextView originalPriceTitle;
        TextView finalPrice;
        TextView finalPriceTitle;
        CardView cardView; // Add CardView reference
        LinearLayout cardContent;
        ImageView roundedView;
        LinearLayout featuresContainer;
        public SubscriptionViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.subscription_card_view);
            cardContent = itemView.findViewById(R.id.subscription_card_content);
            name = itemView.findViewById(R.id.package_features);
            description = itemView.findViewById(R.id.subscription_card_description);
            originalPrice = itemView.findViewById(R.id.original_price);
            finalPrice = itemView.findViewById(R.id.final_price);
            roundedView = itemView.findViewById(R.id.purchase_icon);
            originalPriceTitle = itemView.findViewById(R.id.original_price_title);
            finalPriceTitle = itemView.findViewById(R.id.final_price_title);
            featuresContainer = itemView.findViewById(R.id.features_container);
        }
    }

    public void resetSelectedPosition() {
        int previousSelectedPosition = selectedPosition;
        selectedPosition = RecyclerView.NO_POSITION;
        if (previousSelectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousSelectedPosition);
        }
    }

    private String displayTitleFor(SubscriptionItem item) {
        if (item.productId != null && item.productId.toLowerCase().contains("year")) {
            return "Annual Plus";
        }
        return "Larger allowance";
    }

    private String displayDescriptionFor(SubscriptionItem item) {
        if (item.productId != null && item.productId.toLowerCase().contains("year")) {
            return "Best value for heavier screen AI and reply usage.";
        }
        return "Higher credit pool for chat, quick reply, and screen AI.";
    }

    private List<String> displayBenefitsFor(SubscriptionItem item) {
        return Arrays.asList(
                "Works across Overlay, Keyboard, and AI Chat.",
                "Credit limits keep free use sustainable."
        );
    }

    private int color(SubscriptionViewHolder holder, int colorRes) {
        return ContextCompat.getColor(holder.itemView.getContext(), colorRes);
    }
}
