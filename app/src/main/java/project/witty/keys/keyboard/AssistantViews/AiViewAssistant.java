package project.witty.keys.keyboard.AssistantViews;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import project.witty.keys.R;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.keyboard.Themeable;
import project.witty.keys.latin.utils.DialogUtils;

public class AiViewAssistant extends AssistantContainer implements Themeable {
    private Context mThemedContext;
    private TextView lastResponseTextView;
    private ImageView backButton;
    private TextView clearConversationButton;
    // We only need a preview text, back button, clear conversation button and a reply button.
    // Removed the unused EditText input and info icon. A single reply button will be shown
    // instead of an icon-only send button so users know how to respond to the AI.
    private Button sendButton;
    private LinearLayout horizontalLayout;
    private LinearLayout initialLayout;
    public AiViewAssistant(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // Root layout
        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        rootLayout.setTag(R.id.theme_background_attr, R.attr.productViewBackgroundColor);

        addView(rootLayout);
        // Create a new LinearLayout for the message preview area
        initialLayout = new LinearLayout(context);
        initialLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams initialLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        initialLayoutParams.setMargins(16, 16, 16, 16);
        initialLayout.setLayoutParams(initialLayoutParams);
        initialLayout.setPadding(8, 16, 8, 16);
        initialLayout.setGravity(Gravity.CENTER_VERTICAL);
        initialLayout.setFocusable(true);
        initialLayout.setClickable(true);
        initialLayout.setTag(R.id.theme_background_drawable_attr, "button");

        // Initialize message box
        View separator = new View(context);
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics()),
                LinearLayout.LayoutParams.MATCH_PARENT// Correct height
        );
        separatorParams.setMargins(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics())
        );
        separatorParams.gravity = Gravity.CENTER_VERTICAL;
        separator.setTag(R.id.theme_background_attr, R.attr.themedButtonPressedBackgroundColor);
        separator.setLayoutParams(separatorParams);
        initialLayout.addView(separator);

        LinearLayout textGroupLayout = new LinearLayout(context);
        LinearLayout.LayoutParams messageBoxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                2
        );
        textGroupLayout.setLayoutParams(messageBoxParams);
        textGroupLayout.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);

        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(null, Typeface.BOLD);
        title.setText("ChatGPT");
        title.setTag(R.id.theme_text_color_attr, R.attr.productViewTitleColor);
        textGroupLayout.addView(title);

        lastResponseTextView = new TextView(context);
        lastResponseTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lastResponseTextView.setTag(R.id.theme_text_color_attr, R.attr.themedButtonTextColor);
        textGroupLayout.addView(lastResponseTextView);

        initialLayout.addView(textGroupLayout);

        // Note: the original design included an info icon; per the updated requirements
        // we no longer include it. Only the ChatGPT title and the preview text remain.
        rootLayout.addView(initialLayout);
        // Create a new LinearLayout for the icon and contentTextView
        horizontalLayout = new LinearLayout(context);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
        horizontalLayout.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        horizontalLayout.setPadding(16, 16, 16, 16);
        horizontalLayout.setGravity(Gravity.CENTER_VERTICAL);
        horizontalLayout.setTag(R.id.theme_background_attr, R.attr.productViewBackgroundColor);

        // Initialize back button
        backButton = new ImageView(context);
        LinearLayout.LayoutParams backButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        backButton.setLayoutParams(backButtonParams);
        backButton.setImageResource(R.drawable.back_v2_icon);
        backButton.setTag(R.id.theme_icon_color_attr, R.attr.productViewTitleColor);
        horizontalLayout.addView(backButton);


        clearConversationButton = new TextView(context);
        LinearLayout.LayoutParams clearButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        clearButtonParams.setMargins(54, 0, 54, 0);
        clearConversationButton.setLayoutParams(clearButtonParams);
        clearConversationButton.setText("Clear Chat");
        clearConversationButton.setTextSize(16);
        clearConversationButton.setTypeface(null, Typeface.BOLD);
        clearConversationButton.setGravity(Gravity.CENTER);
        clearConversationButton.setTag(R.id.theme_text_color_attr, R.attr.themedButtonTextColor);
        clearConversationButton.setTag(R.id.theme_background_drawable_attr, "button");

        clearConversationButton.setPadding(24, 14, 24,14 );
        clearConversationButton.setClickable(true);
        clearConversationButton.setFocusable(true);
        horizontalLayout.addView(clearConversationButton);

        // Initialize reply button
        sendButton = new Button(context);
        // Use wrap_content for width so the word "Reply" fits naturally
        LayoutParams buttonParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics())
        );
        sendButton.setLayoutParams(buttonParams);
        sendButton.setText("Reply");
        sendButton.setTextSize(14);
        sendButton.setTag(R.id.theme_text_color_attr, R.attr.themedButtonTextColor);
        sendButton.setTag(R.id.theme_background_drawable_attr, "button");
        sendButton.setPadding(24, 14, 24, 14);
        horizontalLayout.addView(sendButton);
        rootLayout.addView(horizontalLayout);
        mThemedContext = context;       // store themed context
        onThemeChanged(context);
    }

    @Override
    public void onThemeChanged(Context themedContext) {
        mThemedContext = themedContext;
        applyThemeRecursively(this, themedContext);
    }

    private void applyThemeRecursively(View view, Context themedContext) {
        if (view == null) return;

        Object bgAttrTag = view.getTag(R.id.theme_background_attr);
        if (bgAttrTag instanceof Integer) {
            view.setBackgroundColor(ThemeUtils.getThemeColor(themedContext, (Integer) bgAttrTag));
        }

        Object backgroundDrawableTag = view.getTag(R.id.theme_background_drawable_attr);
        if ("button".equals(backgroundDrawableTag)) {
            float cornerRadius = getResources().getDimension(R.dimen.button_corner_radius_lxx);
            Drawable bg = ThemeUtils.createButtonBackground(
                    themedContext,
                    R.attr.themedButtonBackgroundColor,
                    R.attr.themedButtonPressedBackgroundColor,
                    cornerRadius
            );
            view.setBackground(bg);
        }

        if (view instanceof TextView) {
            Object textColorAttrTag = view.getTag(R.id.theme_text_color_attr);
            if (textColorAttrTag instanceof Integer) {
                int attrId = (Integer) textColorAttrTag;
                ((TextView) view).setTextColor(ThemeUtils.getThemeColor(themedContext, attrId));
            }
        }

        if (view instanceof ImageView) {
            Object iconColorAttrTag = view.getTag(R.id.theme_icon_color_attr);
            if (iconColorAttrTag instanceof Integer) {
                int attrId = (Integer) iconColorAttrTag;
                ((ImageView) view).setColorFilter(ThemeUtils.getThemeColor(themedContext, attrId));
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyThemeRecursively(group.getChildAt(i), themedContext);
            }
        }
    }


    public void setLastResponseTextView(String text) {
        lastResponseTextView.setText(text);
    }

    /**
     * The info button has been removed as part of the simplified design.
     * This method is intentionally left empty for backward compatibility.
     * @param listener ignored listener
     */
    public void setInfoButtonListener(OnClickListener listener) {
        // No-op
    }
    public void setLayoutClickListener(OnClickListener listener) {
        initialLayout.setOnClickListener(listener);
    }

    public void setClearConversationListener(OnClickListener listener) {
        clearConversationButton.setOnClickListener(listener);
    }

    public void setBackButtonListener(OnClickListener listener) {
        backButton.setOnClickListener(listener);
    }

    public void setReplyButtonListener(OnClickListener listener) {
        sendButton.setOnClickListener(listener);
    }


    public AlertDialog showInfoDialog(final Context context, final IBinder windowToken, final String infoMessage) {
        if (windowToken == null) {
            return null;
        }

        final CharSequence title = "ALERT!!";

        final AlertDialog.Builder builder = new AlertDialog.Builder(
                DialogUtils.getPlatformDialogThemeContext(context));
        builder.setMessage(infoMessage)
                .setTitle(title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        final AlertDialog dialog = builder.create();
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        final Window window = dialog.getWindow();
        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = windowToken;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        dialog.show();
        return dialog;
    }

}