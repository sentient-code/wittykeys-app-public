package project.witty.keys.keyboard.AiChat;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import project.witty.keys.R;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.app.utils.ToneData;
import project.witty.keys.keyboard.KeyboardSwitcher;
import project.witty.keys.keyboard.shared.LanguageFlags;
import android.graphics.drawable.Drawable;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * ViewHolder that renders a titled horizontal list of tiny "pill" buttons.
 *
 * Expected parent layout (item_chat_horizontal_options.xml):
 *  - Title TextView:       @id/horizontal_options_title
 *  - Inner RecyclerView:   @id/horizontal_recycler_view
 *
 * Each pill uses layout item_chat_single_option_button.xml:
 *  - Root: TextView (acts as a button)
 */
public class HorizontalOptionsViewHolder extends RecyclerView.ViewHolder {

    private final TextView titleTextView;
    private final RecyclerView horizontalRecyclerView;
    private final SingleOptionAdapter singleOptionAdapter;

    public HorizontalOptionsViewHolder(@NonNull View itemView) {
        super(itemView);
        titleTextView = itemView.findViewById(R.id.horizontal_options_title);
        horizontalRecyclerView = itemView.findViewById(R.id.horizontal_recycler_view);

        // Adapter uses the singleton KeyboardSwitcher to delegate action handling
        singleOptionAdapter = new SingleOptionAdapter(KeyboardSwitcher.getInstance());
        horizontalRecyclerView.setAdapter(singleOptionAdapter);
        horizontalRecyclerView.setItemAnimator(null); // prevent flicker on quick updates
//        horizontalRecyclerView.setHasFixedSize(true);
    }

    public void bind(HorizontalOptions item, Context themedContext) {
        if (item == null) return;

        // Set the title text and style only when the options are not suggestions.
        // For suggestion rows (e.g. actions like Regenerate), we hide the title completely
        // so that only the icon appears without the "Actions:" label.  Otherwise, we
        // display the provided title in bold with the proper theme colour.

        titleTextView.setVisibility(View.VISIBLE);
        titleTextView.setText(item.getTitle());
        titleTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTextView.setTextColor(
                ThemeUtils.getThemeColor(themedContext, R.attr.productViewTitleColor)
        );

        singleOptionAdapter.updateOptions(item, themedContext);
    }

    // =========================================================================================
    // Inner adapter for the "pills"
    // =========================================================================================
    private static class SingleOptionAdapter extends RecyclerView.Adapter<SingleOptionAdapter.OptionViewHolder> {
        private final KeyboardSwitcher keyboardSwitcher;
        private final List<String> options = new ArrayList<>();
        private HorizontalOptions parentItem;
        private Context themedContext;

        // Only used for OptionsType.TONE to show a selected state
        private int selectedPosition = RecyclerView.NO_POSITION;

        SingleOptionAdapter(KeyboardSwitcher switcher) {
            this.keyboardSwitcher = switcher;
            setHasStableIds(true);
        }

        void updateOptions(HorizontalOptions item, Context context) {
            this.parentItem = item;
            this.themedContext = context;
            options.clear();
            if (item.getOptions() != null) options.addAll(item.getOptions());
            selectedPosition = RecyclerView.NO_POSITION; // reset selection on new data
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            // Stable id based on option text + type
            String key = (parentItem == null ? "null" : parentItem.getType().name())
                    + "|" + options.get(position);
            return key.hashCode();
        }

        @NonNull
        @Override
        public OptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = themedContext != null ? themedContext : parent.getContext();
            View view = LayoutInflater.from(ctx)
                    .inflate(R.layout.item_chat_single_option_button, parent, false);
            return new OptionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OptionViewHolder holder, int position) {
            String optionText = options.get(position);
            boolean isSelected = parentItem != null
                    && parentItem.getType() == OptionsType.TONE
                    && position == selectedPosition;
            holder.bind(optionText, isSelected, parentItem, themedContext, this::onOptionClicked);
        }

        private void onOptionClicked(int position) {
            if (position == RecyclerView.NO_POSITION || parentItem == null) return;

            String selected = options.get(position);
            String originalText = keyboardSwitcher.getCommitedText();
            FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(KeyboardSwitcher.getInstance().getmLatinIME());
            User user = EncryptedPreferences.getUserLoggedInInfo();
            String userId = (user != null ? user.getId() : null);

            switch (parentItem.getType()) {
                case TONE:
                    // update UI selection
                    EventHelpers.triggerToneSelectedEvent(userId, selected, analytics);

                    int prev = selectedPosition;
                    selectedPosition = position;
                    if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev);
                    notifyItemChanged(selectedPosition);

                    // trigger AI call with tone
                    keyboardSwitcher.performAiAction(
                            KeyboardSwitcher.AiAction.CHANGE_TONE,
                            originalText,              // data (original text)
                            selected                    // subData (tone)
                    );
                    break;

                case LANGUAGE:
                    // For translation, immediately trigger the translator path
                    keyboardSwitcher.onLanguageSelectedForTranslation(originalText, selected);
                    break;

                case SUGGESTION:
                    // For suggestion pills, delegate handling back to the KeyboardSwitcher
                    keyboardSwitcher.handleSuggestion(selected);
                    break;

                case NEXT_ACTION:
                default:
                    // If you later add "click to apply" behavior for generic horizontal lists,
                    // handle here. For now we do nothing.
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return options.size();
        }

        // -------------------------------------------------------------------------------------
        // Inner ViewHolder
        // -------------------------------------------------------------------------------------
        static class OptionViewHolder extends RecyclerView.ViewHolder {
            private final TextView pill;

            OptionViewHolder(@NonNull View itemView) {
                super(itemView);
                // item_chat_single_option_button.xml root is a TextView
                pill = (TextView) itemView;
            }

            void bind(String optionText,
                      boolean isSelected,
                      HorizontalOptions parentItem,
                      Context themedContext,
                      OnPillClick click) {
                // Build visible label with emoji depending on the option type
                String label = optionText;
                if (parentItem != null) {
                    if (parentItem.getType() == OptionsType.TONE) {
                        String emoji = ToneData.getEmojiForTone(optionText);
                        if (emoji != null) label = emoji + " " + optionText;
                    } else if (parentItem.getType() == OptionsType.LANGUAGE) {
                        Map<String, String> flags = LanguageFlags.getLanguageFlags();
                        String flag = flags.get(optionText);
                        if (flag != null) label = flag + " " + optionText;
                    }
                }
                // For suggestion actions (e.g. Regenerate), we display an icon instead of text

                if (parentItem != null && parentItem.getType() == OptionsType.SUGGESTION) {
                    boolean isSingleRegenerate = false;
                    List<String> opts = parentItem.getOptions();
                    if (opts != null && opts.size() == 1) {
                        String only = opts.get(0);
                        isSingleRegenerate = only != null && only.equalsIgnoreCase("Regenerate");
                    }
                    if (isSingleRegenerate) {
                        // Show refresh icon only
                        pill.setText("");
                        Drawable icon = ContextCompat.getDrawable(themedContext, R.drawable.ic_icon_regenerate);
                        if (icon != null) {
                            icon.setTint(ThemeUtils.getThemeColor(themedContext, R.attr.themedButtonTextColor));
                            pill.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                            int pad = (int) (6 * pill.getResources().getDisplayMetrics().density);
                            pill.setPadding(pad, pad, pad, pad);
                        }
                    } else {
                        pill.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                        int pad = (int) (6 * pill.getResources().getDisplayMetrics().density);
                        pill.setPadding(pad, pad, pad, pad);
                        pill.setText(label);
                    }
                } else {
                    // Normal label with emoji/flag
                    pill.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                    pill.setText(label);
                }

                // Theming (selected vs normal)
                int bgColor;
                int textColor;
                if (isSelected) {
                    bgColor = ThemeUtils.getThemeColor(themedContext, R.attr.themedButtonPressedBackgroundColor);
                    textColor = ThemeUtils.getThemeColor(themedContext, R.attr.themedButtonBackgroundColor);
                } else {
                    bgColor = ThemeUtils.getThemeColor(themedContext, R.attr.themedButtonBackgroundColor);
                    textColor = ThemeUtils.getThemeColor(themedContext, R.attr.themedButtonTextColor);
                }

                GradientDrawable bg = (GradientDrawable)
                        ContextCompat.getDrawable(themedContext, R.drawable.background_option_button_normal)
                                .mutate();
                bg.setColor(bgColor);
                pill.setBackground(bg);
                pill.setTextColor(textColor);

                // Click
                itemView.setOnClickListener(v -> click.onClick(getBindingAdapterPosition()));
            }
        }

        interface OnPillClick {
            void onClick(int adapterPosition);
        }
    }
}