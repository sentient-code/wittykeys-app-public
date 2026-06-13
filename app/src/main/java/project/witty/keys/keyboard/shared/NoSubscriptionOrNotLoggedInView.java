// NoSubscriptionOrNotLoggedInView.java
package project.witty.keys.keyboard.shared;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import project.witty.keys.R;
import project.witty.keys.app.AuthenticationActivity;
import project.witty.keys.app.SubscriptionListingActivity;
import project.witty.keys.app.helpers.ThemeUtils;

public class NoSubscriptionOrNotLoggedInView extends LinearLayout {

    private TextView error_message;
    private Button cta;
    private View rootView;

    public NoSubscriptionOrNotLoggedInView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        rootView = LayoutInflater.from(context).inflate(R.layout.product_error_view, this, true);
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int deviceHeight = displayMetrics.heightPixels;
        int height = (int) (deviceHeight * 0.4);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));

        error_message = findViewById(R.id.error_message);
        cta = findViewById(R.id.err_cta);

        // === THEME TAGS ===
        rootView.setTag(R.id.theme_background_attr, R.attr.productViewBackgroundColor);
        error_message.setTag(R.id.theme_text_color_attr, R.attr.productViewTitleColor);
        cta.setTag(R.id.theme_text_color_attr, R.attr.themedButtonTextColor);
        cta.setTag(R.id.theme_background_drawable_attr, "button");

        // Apply initial theme
        onThemeChanged(getContext());
    }

    // This method is called by the parent ProductContainerView
    public void onThemeChanged(Context themedContext) {
        applyThemeRecursively(this, themedContext);
    }

    private void applyThemeRecursively(View view, Context themedContext) {
        if (view == null) return;

        // Apply background color from attribute
        Object backgroundAttrTag = view.getTag(R.id.theme_background_attr);
        if (backgroundAttrTag instanceof Integer) {
            view.setBackgroundColor(ThemeUtils.getThemeColor(themedContext, (Integer) backgroundAttrTag));
        }

        // Apply background drawable for the button
        Object backgroundDrawableTag = view.getTag(R.id.theme_background_drawable_attr);
        if ("button".equals(backgroundDrawableTag)) {
            Drawable buttonBackground = ThemeUtils.createButtonBackground(
                    themedContext, R.attr.themedButtonBackgroundColor, R.attr.themedButtonPressedBackgroundColor,
                    getResources().getDimension(R.dimen.button_corner_radius_lxx)
            );
            view.setBackground(buttonBackground);
        }

        // Apply text color from attribute
        if (view instanceof TextView) {
            Object textColorAttrTag = view.getTag(R.id.theme_text_color_attr);
            if (textColorAttrTag instanceof Integer) {
                ((TextView) view).setTextColor(ThemeUtils.getThemeColor(themedContext, (Integer) textColorAttrTag));
            }
        }

        // Recurse for all children
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applyThemeRecursively(viewGroup.getChildAt(i), themedContext);
            }
        }
    }
    public void setErrorInfo(ErrorInfo.ErrorType errorType) {
        Log.d("NoSubscriptionOrNotLoggedInView", "reached :" + errorType);

        switch (errorType) {
            case NO_ACTIVE_SUBSCRIPTION:
            case SUBSCRIPTION_EXPIRED:
            case MANDATE_CANCELLED:
            case PAYMENT_FAILED:
                setSubscriptionMessage("You don't have an active subscription. Please renew your subscription to continue using the app.");
                setSubscribeButtonText("Renew");
                setCtaListener(v -> {
                    Intent intent = new Intent(v.getContext(), SubscriptionListingActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    v.getContext().startActivity(intent);
                });
                break;
            case NOT_LOGGED_IN:
                setSubscriptionMessage("You are not logged in. Please log in to continue using the app.");
                setSubscribeButtonText("Log In");
                setCtaListener(v -> {
                    Log.d("NoSubscriptionOrNotLoggedInView", "Log In clicked");
                    Intent intent = new Intent(v.getContext(), AuthenticationActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    v.getContext().startActivity(intent);
                });
                break;
        }
    }


    public void setSubscriptionMessage(String message) {
        error_message.setText(message);
    }

    public void setSubscribeButtonText(String buttonText) {
        cta.setText(buttonText);
    }
    private void setCtaListener (OnClickListener listener) {
        cta.setOnClickListener(listener);
    }

}