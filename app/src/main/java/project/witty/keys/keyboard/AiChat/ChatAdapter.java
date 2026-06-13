package project.witty.keys.keyboard.AiChat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import project.witty.keys.ui.chat.WkAiBubble;
import project.witty.keys.ui.chat.WkUserBubble;
import project.witty.keys.ui.chat.WkTypingBubble;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.BuildConfig;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.DiffUtil;

import io.noties.markwon.Markwon;
import project.witty.keys.R;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.DebugConfig;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.keyboard.AiChat.AiMessage;
import project.witty.keys.keyboard.AiChat.ChatItem;
import project.witty.keys.keyboard.AiChat.SystemMessage;
import project.witty.keys.keyboard.AiChat.UserMessage;
import project.witty.keys.keyboard.KeyboardActionListener;
import project.witty.keys.keyboard.KeyboardSwitcher;
import project.witty.keys.keyboard.shared.ShimmerLoaderView;
import project.witty.keys.latin.LatinIME;
import project.witty.keys.latin.RichInputConnection;
import project.witty.keys.app.tutorial.TutorialManager;


public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatItem> chatItems;
    private final KeyboardActionListener keyboardActionListener;
    private final LatinIME latinIme;
    private Context themedContext;
    private final Markwon markwon; // Markdown renderer
    private TutorialManager tutorialManager;

    // Avatar emoji — set by UnifiedAiView based on mode (General=🤖, Personal=💬)
    private String mAvatarEmoji = "\uD83E\uDD16"; // default: 🤖

    // Number of restored (session persistence) items — these get 70% opacity
    private int restoredItemCount = 0;

    // Support email for content reports - UPDATE THIS WITH YOUR EMAIL
    private static final String REPORT_EMAIL = "support@wittykeys.app";

    // Compact mode: truncate long AI messages (keyboard/overlay surfaces)
    private boolean compactMode = false;
    private static final int COMPACT_MAX_CHARS = 500;

    public ChatAdapter(List<ChatItem> chatItems, KeyboardActionListener listener, LatinIME ime, Context themedContext) {
        this.chatItems = chatItems;
        this.keyboardActionListener = listener;
        this.latinIme = ime;
        this.themedContext = themedContext;
        // Initialize Markwon instance — remove heading underline + clamp heading sizes
        this.markwon = Markwon.builder(themedContext)
                .usePlugin(new io.noties.markwon.AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull io.noties.markwon.core.MarkwonTheme.Builder builder) {
                        builder.headingBreakHeight(0); // Remove H1/H2 bottom line
                        // Clamp heading sizes for compact mobile surfaces
                        // Base text is 12sp — headings should NOT be dramatically larger
                        builder.headingTextSizeMultipliers(new float[]{
                            1.15f,  // H1: ~14sp (was 24sp default)
                            1.1f,   // H2: ~13sp (was 18sp default)
                            1.05f,  // H3: ~12.5sp
                            1.0f,   // H4: same as body
                            1.0f,   // H5: same as body
                            1.0f    // H6: same as body
                        });
                    }
                })
                .build();
        this.tutorialManager = TutorialManager.getInstance(themedContext);

    }

    public void setThemedContext(Context themedContext) {
        this.themedContext = themedContext;
    }

    /** Enable compact mode to truncate long AI messages (for keyboard/overlay surfaces). */
    public void setCompactMode(boolean compact) {
        this.compactMode = compact;
    }

    /** Set avatar emoji based on mode: General=🤖, Personal=💬 */
    public void setAvatarEmoji(String emoji) {
        this.mAvatarEmoji = emoji;
        notifyDataSetChanged();
    }

    /**
     * Submit a new list using DiffUtil for efficient partial updates.
     * Falls back to notifyDataSetChanged() if DiffUtil throws.
     */
    public void submitList(List<ChatItem> newItems) {
        try {
            List<ChatItem> oldItems = new ArrayList<>(chatItems);
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(
                    new ChatItemDiffCallback(oldItems, newItems));
            chatItems.clear();
            chatItems.addAll(newItems);
            resetAnimationState();
            result.dispatchUpdatesTo(this);
        } catch (Exception e) {
            chatItems.clear();
            chatItems.addAll(newItems);
            resetAnimationState();
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case ChatItem.VIEW_TYPE_USER_MESSAGE: {
                // Wrap WkUserBubble in a right-aligned container with timestamp
                LinearLayout wrapper = new LinearLayout(parent.getContext());
                wrapper.setOrientation(LinearLayout.VERTICAL);
                wrapper.setGravity(android.view.Gravity.END);
                wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                int pad = (int) (8 * parent.getResources().getDisplayMetrics().density);
                wrapper.setPadding(pad * 8, pad / 2, pad, pad / 2); // 64dp left, 8dp right

                WkUserBubble bubble = new WkUserBubble(parent.getContext());
                wrapper.addView(bubble, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                TextView time = new TextView(parent.getContext());
                time.setTextSize(9);
                time.setTextColor(0x80F0F0F2);
                LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                timeLp.topMargin = (int) (2 * parent.getResources().getDisplayMetrics().density);
                timeLp.setMarginEnd(pad);
                time.setLayoutParams(timeLp);
                wrapper.addView(time);

                return new UserMessageViewHolder(wrapper);
            }
            case ChatItem.VIEW_TYPE_AI_MESSAGE:
                View viewAi = inflater.inflate(R.layout.item_chat_ai_message, parent, false);
                return new AiMessageViewHolder(viewAi);
            case ChatItem.VIEW_TYPE_LOADING: {
                float density = parent.getResources().getDisplayMetrics().density;
                int pad10 = (int) (10 * density);
                int pad3 = (int) (3 * density);
                android.widget.LinearLayout loadingRow = new android.widget.LinearLayout(parent.getContext());
                loadingRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                loadingRow.setPadding(pad10, pad3, pad10, pad3);
                loadingRow.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                WkTypingBubble typingBubble = new WkTypingBubble(parent.getContext());
                typingBubble.setBubbleColor(ThemeUtils.getThemeColor(themedContext, R.attr.chatAiBubbleColor));
                loadingRow.addView(typingBubble, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new LoadingViewHolder(loadingRow, typingBubble);
            }
            // We will add other cases (Grid, Horizontal, Metadata) later
            case ChatItem.VIEW_TYPE_HORIZONTAL_OPTIONS:
                View viewOptions = inflater.inflate(R.layout.item_chat_horizontal_options, parent, false);
                return new HorizontalOptionsViewHolder(viewOptions);
            case ChatItem.VIEW_TYPE_GRID_OPTIONS:
                View viewGrid = inflater.inflate(R.layout.item_chat_grid_options, parent, false);
                return new GridOptionsViewHolder(viewGrid);
            case ChatItem.VIEW_TYPE_METADATA_CARD:
                View viewMeta = inflater.inflate(R.layout.item_chat_metadata_card, parent, false);
                return new MetadataCardViewHolder(viewMeta);
            case ChatItem.VIEW_TYPE_ERROR_MESSAGE:
                View viewError = inflater.inflate(R.layout.item_chat_error_message, parent, false);
                return new ErrorMessageViewHolder(viewError);
            case ChatItem.VIEW_TYPE_SYSTEM_MESSAGE:
                View viewSystem = inflater.inflate(R.layout.item_chat_system_message, parent, false);
                return new SystemMessageViewHolder(viewSystem);
            case ChatItem.VIEW_TYPE_SCREENSHOT_MESSAGE:
                View viewScreenshot = inflater.inflate(R.layout.item_chat_screenshot_message, parent, false);
                return new ScreenshotViewHolder(viewScreenshot);
            case ChatItem.VIEW_TYPE_NLS_BANNER:
                View viewNls = inflater.inflate(R.layout.item_chat_nls_banner, parent, false);
                return new NlsBannerViewHolder(viewNls);
            case ChatItem.VIEW_TYPE_SESSION_BANNER:
                View viewSession = inflater.inflate(R.layout.item_chat_session_banner, parent, false);
                return new SessionBannerViewHolder(viewSession);
            case ChatItem.VIEW_TYPE_ANALYZING_MESSAGE:
                View viewAnalyzing = inflater.inflate(R.layout.item_chat_analyzing, parent, false);
                return new AnalyzingViewHolder(viewAnalyzing);
            default:
                View emptyView = new View(parent.getContext());
                return new RecyclerView.ViewHolder(emptyView) {};
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = chatItems.get(position);
        switch (holder.getItemViewType()) {
            case ChatItem.VIEW_TYPE_USER_MESSAGE:
                ((UserMessageViewHolder) holder).bind((UserMessage) item);
                break;
            case ChatItem.VIEW_TYPE_AI_MESSAGE:
                ((AiMessageViewHolder) holder).bind((AiMessage) item);
                break;
            case ChatItem.VIEW_TYPE_LOADING:
                ((LoadingViewHolder) holder).bind();
                break;
            case ChatItem.VIEW_TYPE_HORIZONTAL_OPTIONS:
                ((HorizontalOptionsViewHolder) holder).bind((HorizontalOptions) item, themedContext);
                break;
            case ChatItem.VIEW_TYPE_GRID_OPTIONS:
                ((GridOptionsViewHolder) holder).bind((GridOptions) item, themedContext);
                break;
            case ChatItem.VIEW_TYPE_METADATA_CARD:
                ((MetadataCardViewHolder) holder).bind((MetadataCard) item, themedContext);
                break;
            case ChatItem.VIEW_TYPE_ERROR_MESSAGE:
                ((ErrorMessageViewHolder) holder).bind((ErrorMessage) item, themedContext);
                break;
            case ChatItem.VIEW_TYPE_SYSTEM_MESSAGE:
                ((SystemMessageViewHolder) holder).bind((SystemMessage) item, themedContext);
                break;
            case ChatItem.VIEW_TYPE_SCREENSHOT_MESSAGE:
                ((ScreenshotViewHolder) holder).bind((ScreenshotMessage) item, themedContext);
                break;
            case ChatItem.VIEW_TYPE_NLS_BANNER:
                ((NlsBannerViewHolder) holder).bind((NlsBannerMessage) item, themedContext);
                break;
            case ChatItem.VIEW_TYPE_SESSION_BANNER:
                ((SessionBannerViewHolder) holder).bind((SessionBannerMessage) item, themedContext);
                break;
            case ChatItem.VIEW_TYPE_ANALYZING_MESSAGE:
                ((AnalyzingViewHolder) holder).bind((AnalyzingMessage) item, themedContext);
                break;
        }
        // Restored items (from session persistence) get 70% opacity
        holder.itemView.setAlpha(position < restoredItemCount ? 0.7f : 1.0f);
        animateItemAppear(holder.itemView, position);
    }

    /** Set the count of restored items (these will render at 70% opacity) */
    public void setRestoredItemCount(int count) {
        this.restoredItemCount = count;
    }

    @Override
    public int getItemCount() {
        return chatItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return chatItems.get(position).getViewType();
    }

    // =========================================================================================
    // INNER CLASSES: VIEW HOLDERS
    // =========================================================================================

    // --- 1. ViewHolder for User's Messages ---
    public class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private final WkUserBubble bubble;
        private final TextView timeView;

        public UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            bubble = (WkUserBubble) ((ViewGroup) itemView).getChildAt(0);
            timeView = (TextView) ((ViewGroup) itemView).getChildAt(1);
        }

        void bind(UserMessage message) {
            bubble.bind(message.getText());
            if (timeView != null) {
                timeView.setText(formatTimestamp(message.getTimestamp()));
            }
        }
    }

    // --- 2. ViewHolder for AI's Messages ---
    public class AiMessageViewHolder extends RecyclerView.ViewHolder {
        private final WkAiBubble cardView;
        private final TextView textView;
        private final TextView avatar;
        private final TextView timeView;
        private final LinearLayout ctaContainer;
        private final TextView ctaButton1;
        private final TextView ctaButton2;
        private final TextView ctaButton3;
        private final TextView ctaButtonReport;

        public AiMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.ai_message_card);
            textView = itemView.findViewById(R.id.wkBubbleAiText);
            avatar = itemView.findViewById(R.id.ai_avatar);
            timeView = itemView.findViewById(R.id.ai_message_time);
            ctaContainer = itemView.findViewById(R.id.cta_button_container);
            ctaButton1 = itemView.findViewById(R.id.cta_button_1);
            ctaButton2 = itemView.findViewById(R.id.cta_button_2);
            ctaButton3 = itemView.findViewById(R.id.cta_button_3);
            ctaButtonReport = itemView.findViewById(R.id.cta_button_report);
        }

        void bind(AiMessage message) {
            // Initialize DS bubble visibility state (badge gone, retry gone, text visible)
            cardView.bindNormal("");

            // Use Markwon to render Markdown text — truncate in compact mode
            String content = message.getMarkdownText();
            if (compactMode && content != null && content.length() > COMPACT_MAX_CHARS) {
                content = content.substring(0, COMPACT_MAX_CHARS) + "\u2026 [tap \u2197 to read more]";
            }
            markwon.setMarkdown(textView, content);

            // Handle CTA button visibility and actions based on the CtaType
            setupCtaButtons(message);

            // Theme the DS bubble
            int aiColor = ThemeUtils.getThemeColor(themedContext, R.attr.chatAiBubbleColor);
            cardView.setBubbleColor(aiColor);
            textView.setTextColor(
                    ThemeUtils.getThemeColor(themedContext, R.attr.chatAiBubbleTextColor)
            );

            // Theme CTA chips
            int chipColor = ThemeUtils.getThemeColor(themedContext, R.attr.chatCtaChipColor);
            themeCtaChip(ctaButton1, chipColor);
            themeCtaChip(ctaButton2, chipColor);
            themeCtaChip(ctaButton3, chipColor);
            themeCtaChip(ctaButtonReport, chipColor);

            // Set CTA text color
            int ctaTextColor = ThemeUtils.getThemeColor(themedContext, R.attr.productViewTitleColor);
            ctaButton1.setTextColor(ctaTextColor);
            ctaButton2.setTextColor(ctaTextColor);
            if (ctaButton3 != null) {
                ctaButton3.setTextColor(ctaTextColor);
            }
            if (ctaButtonReport != null) {
                ctaButtonReport.setTextColor(ctaTextColor);
            }

            // Set mode-specific avatar emoji (General=🤖, Personal=💬)
            if (avatar != null) {
                avatar.setText(mAvatarEmoji);
            }

            // Timestamp
            if (timeView != null) {
                timeView.setText(formatTimestamp(message.getTimestamp()));
            }

            // NEW: make the whole card apply the text when tapped
            cardView.setOnClickListener(v -> applyTextToEditor(message.getMarkdownText()));
            textView.setOnClickListener(v -> applyTextToEditor(message.getMarkdownText()));

            // (Optional) ripple feedback on tap, guarded for API level
            try {
                android.util.TypedValue tv = new android.util.TypedValue();
                if (itemView.getContext().getTheme()
                        .resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                        && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    cardView.setForeground(
                            androidx.core.content.ContextCompat.getDrawable(itemView.getContext(), tv.resourceId));
                    textView.setForeground( androidx.core.content.ContextCompat.getDrawable(itemView.getContext(), tv.resourceId));
                }
            } catch (Throwable ignored) { /* best-effort only */ }
        }

        private void setupCtaButtons(AiMessage message) {
            LatinIME mLatinIme = KeyboardSwitcher.getInstance().getmLatinIME();
            Context analyticsContext = mLatinIme != null ? mLatinIme : themedContext;
            FirebaseAnalytics analytics = analyticsContext != null
                ? FirebaseAnalytics.getInstance(analyticsContext) : null;
            User user = EncryptedPreferences.getUserLoggedInInfo();
            String userId = (user != null ? user.getId() : null);

            // Default state: hide all buttons and container
            ctaButton1.setVisibility(View.GONE);
            ctaButton2.setVisibility(View.GONE);
            if (ctaButton3 != null) {
                ctaButton3.setVisibility(View.GONE);
            }
            if (ctaButtonReport != null) {
                ctaButtonReport.setVisibility(View.GONE);
            }
            ctaContainer.setVisibility(View.GONE);

            switch (message.getCtaType()) {
                case NONE:
                    break;
                case REPLY_COPY:
                    ctaContainer.setVisibility(View.VISIBLE);
                    ctaButton1.setVisibility(View.VISIBLE);
                    ctaButton1.setText("\uD83D\uDCCB Copy");
                    ctaButton1.setOnClickListener(v -> {
                        EventHelpers.triggerCtaClickedEvent(userId, "copy", analytics);
                        copyToClipboard(message.getMarkdownText());
                    });

                    // Reply — REMOVED (user feedback: Copy + Report only)
                    ctaButton2.setVisibility(View.GONE);
                    if (ctaButton3 != null) {
                        ctaButton3.setVisibility(View.GONE);
                    }
                    break;
                case APPLY_COPY:
                    ctaContainer.setVisibility(View.VISIBLE);
                    ctaButton1.setVisibility(View.VISIBLE);
                    ctaButton1.setText("\uD83D\uDCCB Copy");
                    ctaButton1.setOnClickListener(v -> {
                        EventHelpers.triggerCtaClickedEvent(userId, "copy", analytics);
                        copyToClipboard(message.getMarkdownText());
                    });

                    // Apply + Reply — REMOVED (Copy + Report only)
                    ctaButton2.setVisibility(View.GONE);
                    if (ctaButton3 != null) {
                        ctaButton3.setVisibility(View.GONE);
                    }
                    break;
                case SUGGESTIONS:
                    // For this type, the message itself is the button. The container is hidden.
                    // We'll add click listener to the card view instead.
                    cardView.setOnClickListener(v -> applyTextToEditor(message.getMarkdownText()));
                    break;

                case REGENERATE_COPY_REPLY:
                    ctaContainer.setVisibility(View.VISIBLE);
                    // Button 1: Copy — KEEP
                    ctaButton1.setVisibility(View.VISIBLE);
                    ctaButton1.setText("\uD83D\uDCCB Copy");
                    ctaButton1.setOnClickListener(v -> {
                        EventHelpers.triggerCtaClickedEvent(userId, "copy", analytics);
                        copyToClipboard(message.getMarkdownText());
                    });

                    // Button 2: Reply — REMOVED (user feedback: doesn't make sense in chat)
                    ctaButton2.setVisibility(View.GONE);

                    // Button 3: Regenerate — REMOVED (user feedback: doesn't make sense)
                    if (ctaButton3 != null) {
                        ctaButton3.setVisibility(View.GONE);
                    }

                    // Report button — SHOW (Copy + Report only)
                    if (ctaButtonReport != null) {
                        ctaButtonReport.setVisibility(View.VISIBLE);
                        ctaButtonReport.setText("\u26A0 Report");
                        ctaButtonReport.setOnClickListener(v -> {
                            EventHelpers.triggerCtaClickedEvent(userId, "report_ai_content", analytics);
                            reportAiContent(message.getMarkdownText());
                        });
                    }
                    break;
            }

            // =====================================================================
            // NEW: Setup Report/Flag button - ALWAYS VISIBLE on AI messages
            // Required by Google Play AI-Generated Content Policy
            // =====================================================================
            setupReportButton(message, userId, analytics);

            // After configuring text and click listeners, apply a themed style to all CTA buttons.
            // This ensures that each button consistently follows the current keyboard theme for
            // background and text colours as well as corner radii.  Padding is also applied
            // programmatically here because the underlying XML defines borderless buttons with
            // minimal styling.  Without this, the CTAs appear unstyled and out of place.
            styleCtaButton(ctaButton1);
            styleCtaButton(ctaButton2);
            if (ctaButton3 != null) {
                styleCtaButton(ctaButton3);
            }
            if (ctaButtonReport != null) {
                styleReportButton(ctaButtonReport);
            }
        }

        /**
         * NEW: Setup the Report/Flag button for AI content reporting.
         * Required by Google Play AI-Generated Content Policy.
         * Users can report offensive AI-generated content without leaving the app.
         */
        private void setupReportButton(AiMessage message, String userId, FirebaseAnalytics analytics) {
            if (ctaButtonReport == null) return;

            if (message.getCtaType() == CtaType.NONE) {
                ctaButtonReport.setVisibility(View.GONE);
                return;
            }

            // Show report button on AI messages (required by Google Play policy)
            ctaContainer.setVisibility(View.VISIBLE);
            ctaButtonReport.setVisibility(View.VISIBLE);
            ctaButtonReport.setText("\uD83D\uDEA9 Report");

            ctaButtonReport.setOnClickListener(v -> {
                // Track analytics event
                EventHelpers.triggerCtaClickedEvent(userId, "report_ai_content", analytics);

                // Log for debugging
                if (DebugConfig.isDebugMode) {
                    android.util.Log.d("ChatAdapter", "🚩 Report button clicked for AI content");
                }

                // Open email intent to report content
                reportAiContent(message.getMarkdownText());
            });
        }

        /**
         * NEW: Open email intent to report AI-generated content.
         * This is the quick implementation (Option B) for Google Play policy compliance.
         */
        private void reportAiContent(String aiContent) {
            try {
                // Truncate content if too long
                String truncatedContent = aiContent;
                if (aiContent.length() > 500) {
                    truncatedContent = aiContent.substring(0, 500) + "...[truncated]";
                }

                // Feature type
                String featureType = "AI Feature";

                // Build email subject
                String subject = "WittyKeys AI Content Report";

                // Build email body
                String emailBody = "I would like to report the following AI-generated content:\n\n" +
                        "---\n" +
                        "Feature: " + featureType + "\n" +
                        "Content:\n" + truncatedContent + "\n" +
                        "---\n\n" +
                        "Reason for report:\n[Please describe why you're reporting this content]\n\n" +
                        "---\n" +
                        "App Version: " + BuildConfig.VERSION_NAME + "\n" +
                        "Timestamp: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.getDefault()).format(new java.util.Date());

                // Get email from resources
                String reportEmail = themedContext.getString(R.string.contact_mail);

                // URL encode subject and body for mailto URI
                String encodedSubject = Uri.encode(subject);
                String encodedBody = Uri.encode(emailBody);

                // Build mailto URI with subject and body included
                String mailtoUri = "mailto:" + reportEmail +
                        "?subject=" + encodedSubject +
                        "&body=" + encodedBody;

                // Create email intent
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse(mailtoUri));

                // Add FLAG_ACTIVITY_NEW_TASK since we're launching from a service context
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Launch email chooser
                Intent chooser = Intent.createChooser(intent, "Report AI Content");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                themedContext.startActivity(chooser);

                // Track the report event in Firebase
                trackReportEvent(featureType);

                if (DebugConfig.isDebugMode) {
                    android.util.Log.d("ChatAdapter", "🚩 Report email intent launched");
                }

            } catch (Exception e) {
                if (DebugConfig.isDebugMode) {
                    android.util.Log.e("ChatAdapter", "❌ Failed to launch report email", e);
                }
                Toast.makeText(themedContext, "Unable to open email app. Please email " +
                        themedContext.getString(R.string.contact_mail), Toast.LENGTH_LONG).show();
            }
        }
        /**
         * NEW: Track AI content report event in Firebase Analytics.
         */
        private void trackReportEvent(String featureType) {
            try {
                FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(themedContext);
                android.os.Bundle params = new android.os.Bundle();
                params.putString("feature_type", featureType);
                params.putLong("timestamp", System.currentTimeMillis());
                analytics.logEvent("ai_content_reported", params);
            } catch (Exception ignored) {}
        }

        /**
         * Style the report button as a chip (emoji + text label).
         */
        private void styleReportButton(TextView button) {
            if (button == null) return;
            button.setAllCaps(false);
        }

        /**
         * Style a CTA button as a chip (emoji + text label).
         */
        private void styleCtaButton(TextView button) {
            if (button == null) return;
            button.setAllCaps(false);
        }

        private void themeCtaChip(TextView chip, int chipColor) {
            if (chip != null && chip.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) chip.getBackground()).setColor(chipColor);
            }
        }


        private void copyToClipboard(String text) {
            ClipboardManager clipboard = (ClipboardManager) themedContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("WittyKeys AI Response", text);
            clipboard.setPrimaryClip(clip);

            // Visual feedback: change copy button text briefly
            if (ctaButton1 != null) {
                String originalText = ctaButton1.getText().toString();
                ctaButton1.setText("\u2714 Copied");
                ctaButton1.setAlpha(0.7f);
                ctaButton1.postDelayed(() -> {
                    ctaButton1.setText(originalText);
                    ctaButton1.setAlpha(1.0f);
                }, 1500);
            }
        }

        private void applyTextToEditor(String newText) {
            if (latinIme == null) return;
            RichInputConnection ric = latinIme.getInputLogicInstance().mConnection;
            if (ric != null && ric.isConnected()) {
                ric.beginBatchEdit();
                // Replace all existing text in the editor
                CharSequence currentText = ric.getTextBeforeCursor(1000, 0) + "" + ric.getTextAfterCursor(1000, 0);
                ric.setSelection(0, currentText.length());
                ric.commitText(newText, 1);
                ric.endBatchEdit();
                // ADD THESE LINES - Notify tutorial that text was applied
                if (tutorialManager != null) {
                    tutorialManager.notifyTextApplied(newText);
                }
                // ADD DEBUG LOG
                if (DebugConfig.isDebugMode) {
                    android.util.Log.d("ChatAdapter", "✅ AI text applied: " +
                            newText.substring(0, Math.min(50, newText.length())));
                }
            } else {
                Toast.makeText(themedContext, "Editor not available.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- Shared utilities ---
    private static String formatTimestamp(long millis) {
        return new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            .format(new java.util.Date(millis));
    }

    // --- Message appear animation ---
    private int lastAnimatedPosition = -1;

    private void animateItemAppear(View view, int position) {
        // Only animate newly appearing items (not already-seen items during scroll-back)
        if (position <= lastAnimatedPosition) return;
        lastAnimatedPosition = position;

        // Start state: slightly below and transparent
        view.setTranslationY(40f);
        view.setAlpha(0f);

        // Animate to final position
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    public void resetAnimationState() {
        lastAnimatedPosition = -1;
        restoredItemCount = 0;
    }

    // --- 3. ViewHolder for the Loading Animation (3-dot bounce) ---
    public class LoadingViewHolder extends RecyclerView.ViewHolder {
        private final WkTypingBubble typingBubble;

        public LoadingViewHolder(@NonNull View itemView, WkTypingBubble bubble) {
            super(itemView);
            typingBubble = bubble;
        }

        void bind() {
            typingBubble.start();
        }

        void stop() {
            typingBubble.stop();
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof LoadingViewHolder) {
            ((LoadingViewHolder) holder).stop();   // prevent anim leaks
        }
    }

    // --- 4. ViewHolder for Error Messages ---
    public class ErrorMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView errorText;
        private final TextView retryBtn;
        private final TextView errorAvatar;

        public ErrorMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            errorText = itemView.findViewById(R.id.error_text);
            retryBtn = itemView.findViewById(R.id.error_retry_btn);
            errorAvatar = itemView.findViewById(R.id.error_avatar);
        }

        void bind(ErrorMessage message, Context context) {
            // Set mode-specific avatar emoji
            if (errorAvatar != null) {
                errorAvatar.setText(mAvatarEmoji);
            }
            errorText.setText(message.getText());
            int textColor = ThemeUtils.getThemeColor(context, R.attr.productViewTitleColor);
            errorText.setTextColor(textColor);
            // Retry button — always use bright accent blue, ensure visible text
            int accentColor = 0xFF6CB4EE; // Hardcoded bright blue for visibility
            if (retryBtn.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) retryBtn.getBackground()).setColor(accentColor);
            }
            retryBtn.setTextColor(0xFF000000); // Black text on blue bg for contrast
            retryBtn.setOnClickListener(v -> {
                if (message.getRetryAction() != null) {
                    message.getRetryAction().run();
                }
            });
            // Theme error card bg
            View card = itemView.findViewById(R.id.error_card);
            if (card != null) {
                int aiColor = ThemeUtils.getThemeColor(context, R.attr.chatAiBubbleColor);
                android.graphics.drawable.GradientDrawable bg =
                    (android.graphics.drawable.GradientDrawable) card.getBackground();
                if (bg != null) bg.setColor(aiColor);
            }
        }
    }

    // --- 5. ViewHolder for System Messages ---
    /**
     * Displays a simple system message (e.g. instructions like "Please select a category").
     * The system message appears centred within the AI view and adopts the product view
     * title colour to stand out without competing with user or AI messages.
     */
    public class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView systemMessageText;

        public SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            systemMessageText = itemView.findViewById(R.id.system_message_text);
        }

        void bind(SystemMessage message, Context context) {
            systemMessageText.setText(message.getText());
            int colour = ThemeUtils.getThemeColor(context, R.attr.productViewTitleColor);
            // Apply ~50% alpha for subtle pill style
            int alphaColour = (colour & 0x00FFFFFF) | 0x80000000;
            systemMessageText.setTextColor(alphaColour);
        }
    }

    // --- 6. ViewHolder for Screenshot Messages (AC11) ---
    public class ScreenshotViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbImage;
        private final LinearLayout thumbPlaceholder;
        private final View cardView;

        public ScreenshotViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbImage = itemView.findViewById(R.id.screenshot_thumb_image);
            thumbPlaceholder = itemView.findViewById(R.id.screenshot_thumb_placeholder);
            cardView = itemView.findViewById(R.id.screenshot_card);
        }

        void bind(ScreenshotMessage message, Context context) {
            if (message.getImagePath() != null && !message.getImagePath().isEmpty()) {
                try {
                    android.graphics.Bitmap bitmap = BitmapFactory.decodeFile(message.getImagePath());
                    if (bitmap != null) {
                        thumbImage.setImageBitmap(bitmap);
                        thumbImage.setVisibility(View.VISIBLE);
                        thumbPlaceholder.setVisibility(View.GONE);
                    } else {
                        thumbImage.setVisibility(View.GONE);
                        thumbPlaceholder.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    thumbImage.setVisibility(View.GONE);
                    thumbPlaceholder.setVisibility(View.VISIBLE);
                }
            } else {
                thumbImage.setVisibility(View.GONE);
                thumbPlaceholder.setVisibility(View.VISIBLE);
            }

            int surfaceColor = ThemeUtils.getThemeColor(context, R.attr.chatAiBubbleColor);
            if (cardView != null && cardView.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) cardView.getBackground()).setColor(surfaceColor);
            }
        }
    }

    // --- 7. ViewHolder for NLS Context Banner (AC12) ---
    public class NlsBannerViewHolder extends RecyclerView.ViewHolder {
        private final TextView headerText;
        private final LinearLayout messagesContainer;

        public NlsBannerViewHolder(@NonNull View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.nls_banner_header);
            messagesContainer = itemView.findViewById(R.id.nls_messages_container);
        }

        void bind(NlsBannerMessage message, Context context) {
            // Set header
            headerText.setText("\uD83D\uDCF2 Recent messages from " + message.getContactName());

            // Clear and populate message list
            messagesContainer.removeAllViews();
            int textColor = ThemeUtils.getThemeColor(context, R.attr.productViewTitleColor);

            for (NlsBannerMessage.NlsEntry entry : message.getMessages()) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dpToPx(4, context), 0, dpToPx(4, context));

                // Sender name (bold)
                TextView sender = new TextView(context);
                sender.setText(entry.getSender() + ": ");
                sender.setTextSize(14);
                sender.setTypeface(null, android.graphics.Typeface.BOLD);
                sender.setTextColor(textColor);
                row.addView(sender);

                // Message text
                TextView text = new TextView(context);
                text.setText(entry.getText());
                text.setTextSize(14);
                text.setTextColor(textColor);
                text.setAlpha(0.85f);
                // Let text wrap instead of being cut off
                text.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                row.addView(text);

                messagesContainer.addView(row);
            }
        }

        private int dpToPx(int dp, Context context) {
            return (int) (dp * context.getResources().getDisplayMetrics().density);
        }
    }

    // --- 8. ViewHolder for Session Resumed Banner (AC13) ---
    public class SessionBannerViewHolder extends RecyclerView.ViewHolder {
        private final TextView bannerText;

        public SessionBannerViewHolder(@NonNull View itemView) {
            super(itemView);
            bannerText = itemView.findViewById(R.id.session_banner_text);
        }

        void bind(SessionBannerMessage message, Context context) {
            String text = "Resumed \u00B7 " + message.getMessageCount() +
                    " message" + (message.getMessageCount() != 1 ? "s" : "");
            bannerText.setText(text);
            int textColor = ThemeUtils.getThemeColor(context, R.attr.productViewTitleColor);
            bannerText.setTextColor((textColor & 0x00FFFFFF) | 0x80000000); // 50% alpha
        }
    }

    // --- 9. ViewHolder for Screenshot Analyzing (AC14 + FS10) ---
    public class AnalyzingViewHolder extends RecyclerView.ViewHolder {
        private final View dot1, dot2, dot3;
        private final android.view.View bubbleView;

        public AnalyzingViewHolder(@NonNull View itemView) {
            super(itemView);
            dot1 = itemView.findViewById(R.id.analyzing_dot_1);
            dot2 = itemView.findViewById(R.id.analyzing_dot_2);
            dot3 = itemView.findViewById(R.id.analyzing_dot_3);
            bubbleView = itemView.findViewById(R.id.analyzing_bubble);
        }

        void bind(AnalyzingMessage message, Context context) {
            // Animate dots — same bounce pattern as typing indicator but smaller (4dp dots)
            animateDot(dot1, 0);
            animateDot(dot2, 200);
            animateDot(dot3, 400);
        }

        private void animateDot(View dot, long delay) {
            dot.setAlpha(0.3f);
            android.animation.ObjectAnimator animator =
                android.animation.ObjectAnimator.ofFloat(dot, "translationY", 0f, -6f, 0f);
            animator.setDuration(1400);
            animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            animator.setStartDelay(delay);
            animator.start();

            android.animation.ObjectAnimator alphaAnim =
                android.animation.ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1f, 0.3f);
            alphaAnim.setDuration(1400);
            alphaAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            alphaAnim.setStartDelay(delay);
            alphaAnim.start();
        }
    }
}
