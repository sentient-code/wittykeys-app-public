package project.witty.keys.keyboard.AiChat;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import project.witty.keys.R;
import project.witty.keys.app.context.Chat;
import project.witty.keys.app.context.ChatMessage;
import project.witty.keys.app.context.ScreenContext;
import project.witty.keys.app.helpers.ThemeUtils;

public class MetadataCardViewHolder extends RecyclerView.ViewHolder {
    private final TextView title, details;
    private final ImageView expandIcon;
    private final LinearLayout rootLayout;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public MetadataCardViewHolder(@NonNull View itemView) {
        super(itemView);
        rootLayout = itemView.findViewById(R.id.metadata_root_layout);
        title = itemView.findViewById(R.id.metadata_title);
        details = itemView.findViewById(R.id.metadata_details);
        expandIcon = itemView.findViewById(R.id.metadata_expand_icon);
    }

    public void bind(MetadataCard item, Context themedContext) {
        ScreenContext context = item.getContext();

        // Rich icon format: 📱 WhatsApp · 👤 Mom · 😊 Happy · 🕐 Active 5min ago
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCF1 ").append(context.getAppName());

        if (context instanceof Chat) {
            Chat chat = (Chat) context;
            if (chat.getParticipants() != null && !chat.getParticipants().isEmpty()) {
                sb.append(" \u00B7 \uD83D\uDC64 ").append(chat.getParticipants().get(0));
            }
        }

        String emotion = item.getEmotion();
        if (emotion != null && !emotion.isEmpty()) {
            sb.append(" \u00B7 ").append(emotion);
        }

        String timeActive = item.getTimeActive();
        if (timeActive != null && !timeActive.isEmpty()) {
            sb.append(" \u00B7 \uD83D\uDD50 ").append(timeActive);
        }

        title.setText(sb.toString());

        // Details section: message count or expanded details
        int msgCount = item.getMessageCount();
        String detailsText;
        if (!TextUtils.isEmpty(item.getGeneratedDetails())) {
            detailsText = item.getGeneratedDetails();
        } else if (msgCount > 0) {
            detailsText = msgCount + " messages \u25BE";
        } else {
            detailsText = formatContext(context);
        }
        details.setText(detailsText);

        // Handle expand/collapse state
        if (item.isExpanded()) {
            details.setVisibility(View.VISIBLE);
        } else {
            details.setVisibility(View.GONE);
        }

        rootLayout.setOnClickListener(v -> {
            item.setExpanded(!item.isExpanded());
            details.setVisibility(item.isExpanded() ? View.VISIBLE : View.GONE);
        });

        // Theming
        int textColor = ThemeUtils.getThemeColor(themedContext, R.attr.productViewTitleColor);
        title.setTextColor(textColor);
        details.setTextColor(0xFF4ECDC4); // Teal for message count link
        expandIcon.setColorFilter(textColor);
    }

    private String formatContext(ScreenContext context) {
        if (context instanceof Chat) {
            Chat chat = (Chat) context;
            StringBuilder sb = new StringBuilder();
            sb.append("Participants: ").append(TextUtils.join(", ", chat.getParticipants())).append("\n\n");
            for (ChatMessage msg : chat.getMessages()) {
                sb.append(msg.getSender()).append(": ").append(msg.getText()).append("\n");
            }
            return sb.toString();
        }
        return gson.toJson(context);
    }
}
