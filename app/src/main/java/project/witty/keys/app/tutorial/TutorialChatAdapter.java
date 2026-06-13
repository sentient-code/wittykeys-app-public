package project.witty.keys.app.tutorial;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import project.witty.keys.R;
import java.util.List;

/**
 * Adapter for tutorial chat messages
 * Handles bot messages, user messages, and action buttons
 *
 * FIXED: Bot message bubbles now use 70% of screen width max
 */
public class TutorialChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_BOT = 0;
    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_CELEBRATION = 2;
    private static final int VIEW_TYPE_ACTION = 3;

    private Context context;
    private List<TutorialChatMessage> messages;
    private OnActionClickListener actionClickListener;

    // Screen width for calculating 70% max width
    private int screenWidth;
    private int maxBubbleWidth;

    public interface OnActionClickListener {
        void onActionClick(String actionId);
    }

    public TutorialChatAdapter(Context context, List<TutorialChatMessage> messages) {
        this.context = context;
        this.messages = messages;

        // Calculate screen width and 70% max bubble width
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            screenWidth = displayMetrics.widthPixels;
        } else {
            screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        }
        maxBubbleWidth = (int) (screenWidth * 0.70f); // 70% of screen width
    }

    public void setOnActionClickListener(OnActionClickListener listener) {
        this.actionClickListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        TutorialChatMessage message = messages.get(position);
        switch (message.getType()) {
            case USER_MESSAGE:
                return VIEW_TYPE_USER;
            case BOT_CELEBRATION:
                return VIEW_TYPE_CELEBRATION;
            case ACTION_BUTTON:
                return VIEW_TYPE_ACTION;
            case BOT_MESSAGE:
            default:
                return VIEW_TYPE_BOT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        float density = context.getResources().getDisplayMetrics().density;

        switch (viewType) {
            case VIEW_TYPE_USER:
                return new UserMessageViewHolder(createUserMessageView(density));
            case VIEW_TYPE_CELEBRATION:
                return new BotMessageViewHolder(createBotMessageView(density, true));
            case VIEW_TYPE_ACTION:
                return new ActionButtonViewHolder(createActionButtonView(density));
            case VIEW_TYPE_BOT:
            default:
                return new BotMessageViewHolder(createBotMessageView(density, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TutorialChatMessage message = messages.get(position);

        switch (holder.getItemViewType()) {
            case VIEW_TYPE_USER:
                ((UserMessageViewHolder) holder).bind(message);
                break;
            case VIEW_TYPE_CELEBRATION:
            case VIEW_TYPE_BOT:
                ((BotMessageViewHolder) holder).bind(message);
                break;
            case VIEW_TYPE_ACTION:
                ((ActionButtonViewHolder) holder).bind(message);
                break;
        }

        // Animate if not already animated
        if (!message.isAnimated()) {
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(30f);
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setStartDelay(50L)
                    .start();
            message.setAnimated(true);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ========== View Creation Methods ==========

    /**
     * Creates bot message view with 70% max width
     * FIXED: Bubble now properly expands to fit text up to 70% of screen width
     */
    private View createBotMessageView(float density, boolean isCelebration) {
        // Container - LEFT aligned, MATCH_PARENT width
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.START);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int marginH = (int) (12 * density);
        int marginV = (int) (4 * density);
        containerParams.setMargins(marginH, marginV, marginH, marginV);
        container.setLayoutParams(containerParams);

        // Message bubble with rounded corners
        // KEY FIX: Use maxBubbleWidth (70% of screen) as max width
        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);

        // Create rounded background
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.RECTANGLE);
        bubbleBg.setColor(ContextCompat.getColor(context, R.color.secondary_app_color));
        bubbleBg.setCornerRadii(new float[] {
                4 * density, 4 * density,     // top-left (small for bot - chat tail effect)
                20 * density, 20 * density,   // top-right
                20 * density, 20 * density,   // bottom-right
                20 * density, 20 * density    // bottom-left
        });
        bubble.setBackground(bubbleBg);

        int paddingH = (int) (14 * density);
        int paddingV = (int) (10 * density);
        bubble.setPadding(paddingH, paddingV, paddingH, paddingV);

        // KEY FIX: Set bubble to WRAP_CONTENT but with a maximum width
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubble.setLayoutParams(bubbleParams);

        // Message text
        // KEY FIX: Set maxWidth on the TextView to 70% of screen width minus padding
        TextView messageText = new TextView(context);
        messageText.setId(View.generateViewId());
        messageText.setTag("message_text");
        messageText.setTextColor(ContextCompat.getColor(context, R.color.intro_title_text));
        messageText.setTextSize(15);
        messageText.setLineSpacing(4f, 1f);

        // Calculate max text width (70% screen - bubble padding - container margins)
        int maxTextWidth = maxBubbleWidth - (paddingH * 2) - (marginH * 2);
        messageText.setMaxWidth(maxTextWidth);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageText.setLayoutParams(textParams);
        bubble.addView(messageText);

        // Timestamp
        TextView timestamp = new TextView(context);
        timestamp.setId(View.generateViewId());
        timestamp.setTag("timestamp");
        timestamp.setTextColor(ContextCompat.getColor(context, R.color.fifth_app_color));
        timestamp.setTextSize(10);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timeParams.setMargins(0, (int) (4 * density), 0, 0);
        timeParams.gravity = Gravity.END;
        timestamp.setLayoutParams(timeParams);
        bubble.addView(timestamp);

        container.addView(bubble);
        return container;
    }

    /**
     * Creates user message view - right aligned with green bubble
     */
    private View createUserMessageView(float density) {
        // Container - RIGHT aligned
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.END);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int marginH = (int) (12 * density);
        int marginV = (int) (4 * density);
        // More margin on left to push bubble right
        containerParams.setMargins((int) (screenWidth * 0.30f), marginV, marginH, marginV);
        container.setLayoutParams(containerParams);

        // Message bubble - green like WhatsApp
        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);

        // Create rounded background
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.RECTANGLE);
        bubbleBg.setColor(ContextCompat.getColor(context, R.color.fourth_app_color));
        bubbleBg.setCornerRadii(new float[] {
                20 * density, 20 * density,   // top-left
                4 * density, 4 * density,     // top-right (small for user - chat tail effect)
                20 * density, 20 * density,   // bottom-right
                20 * density, 20 * density    // bottom-left
        });
        bubble.setBackground(bubbleBg);

        int paddingH = (int) (14 * density);
        int paddingV = (int) (10 * density);
        bubble.setPadding(paddingH, paddingV, paddingH, paddingV);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubble.setLayoutParams(bubbleParams);

        // Message text - max 70% of screen
        TextView messageText = new TextView(context);
        messageText.setId(View.generateViewId());
        messageText.setTag("message_text");
        messageText.setTextColor(ContextCompat.getColor(context, R.color.primary_app_color));
        messageText.setTextSize(15);
        messageText.setLineSpacing(4f, 1f);

        int maxTextWidth = maxBubbleWidth - (paddingH * 2) - (marginH * 2);
        messageText.setMaxWidth(maxTextWidth);
        bubble.addView(messageText);

        // Timestamp row with checkmarks
        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timeRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timeRowParams.setMargins(0, (int) (4 * density), 0, 0);
        timeRow.setLayoutParams(timeRowParams);

        TextView timestamp = new TextView(context);
        timestamp.setId(View.generateViewId());
        timestamp.setTag("timestamp");
        timestamp.setTextColor(ContextCompat.getColor(context, R.color.fifth_app_color));
        timestamp.setTextSize(10);
        timeRow.addView(timestamp);

        // Double checkmark
        TextView checkmarks = new TextView(context);
        checkmarks.setText(" ✓✓");
        checkmarks.setTextColor(ContextCompat.getColor(context, R.color.fourth_app_color));
        checkmarks.setTextSize(10);
        timeRow.addView(checkmarks);

        bubble.addView(timeRow);
        container.addView(bubble);
        return container;
    }

    /**
     * Creates action button (Enable Keyboard, Finish Tutorial)
     */
    private View createActionButtonView(float density) {
        // Container centered
        FrameLayout container = new FrameLayout(context);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int marginH = (int) (24 * density);
        int marginV = (int) (20 * density);
        containerParams.setMargins(marginH, marginV, marginH, marginV);
        container.setLayoutParams(containerParams);

        MaterialButton button = new MaterialButton(context);
        button.setId(View.generateViewId());
        button.setTag("action_button");
        button.setTextColor(ContextCompat.getColor(context, R.color.third_app_color));
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.intro_button_background)
                )
        );
        button.setCornerRadius((int) (28 * density));
        button.setInsetTop(0);
        button.setInsetBottom(0);

        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (int) (52 * density)
        );
        button.setLayoutParams(buttonParams);

        container.addView(button);
        return container;
    }

    // ========== View Holders ==========

    class BotMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timestamp;

        BotMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewWithTag("message_text");
            timestamp = itemView.findViewWithTag("timestamp");
        }

        void bind(TutorialChatMessage message) {
            messageText.setText(message.getText());
            timestamp.setText(message.getFormattedTime());

            // Set contentDescription for accessibility — ContextEngine looks for "from X:" pattern
            String sender = message.getSenderName();
            if (sender != null && !sender.isEmpty()) {
                messageText.setContentDescription("from " + sender + ": " + message.getText());
            }
        }
    }

    class UserMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timestamp;

        UserMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewWithTag("message_text");
            timestamp = itemView.findViewWithTag("timestamp");
        }

        void bind(TutorialChatMessage message) {
            messageText.setText(message.getText());
            timestamp.setText(message.getFormattedTime());

            // User messages — mark as "from You:" for accessibility
            messageText.setContentDescription("from You: " + message.getText());
        }
    }

    class ActionButtonViewHolder extends RecyclerView.ViewHolder {
        MaterialButton button;

        ActionButtonViewHolder(View itemView) {
            super(itemView);
            button = itemView.findViewWithTag("action_button");
        }

        void bind(TutorialChatMessage message) {
            button.setText(message.getActionButtonText());
            button.setOnClickListener(v -> {
                if (actionClickListener != null) {
                    actionClickListener.onActionClick(message.getActionId());
                }
            });
        }
    }
}