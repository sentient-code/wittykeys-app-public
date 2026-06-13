package project.witty.keys.keyboard.ProductViews;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.drawable.GradientDrawable;

import androidx.core.content.ContextCompat;

import project.witty.keys.R;
import project.witty.keys.app.entities.Subscription;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.keyboard.AiChat.AIFeatureType;
import project.witty.keys.keyboard.KeyboardSwitcher;
import project.witty.keys.keyboard.Themeable;
import project.witty.keys.keyboard.shared.ErrorInfo;
import project.witty.keys.keyboard.shared.NetworkUtils;
import project.witty.keys.keyboard.shared.NoSubscriptionOrNotLoggedInView;

public class ProductContainerView extends FrameLayout implements Themeable {
    private static final String TAG = "ProductContainerView";
    protected TextView titleTextView;
    protected ImageView iconImageView;
    protected ImageView backButton;
    private LinearLayout noInternetLayout;
    private TextView noInternetMessage;
    private Handler handler;
    private Runnable networkCheckRunnable;
    private KeyboardSwitcher mKeyboardSwitcher = KeyboardSwitcher.getInstance();
    private boolean wasDisconnected = false;
    private NoSubscriptionOrNotLoggedInView noSubscriptionOrNotLoggedInView;
    protected Context mThemedContext;

    public ProductContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setTag(R.id.theme_background_attr, R.attr.productViewBackgroundColor);
        setPadding(0, 0, 0, 90); // Increase bottom padding
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        EncryptedPreferences.initialize(context);

        // Initialize iconImageView.  The icon sits within a pill‑shaped background so
        // that it appears distinct against the title bar.  We assign a theme tag
        // for both the icon colour and the background drawable so that
        // applyThemeRecursively() will automatically apply a themed background and
        // colour using the same button attributes as other CTAs.
        iconImageView = new ImageView(context);
        LayoutParams iconLayoutParams = new LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, getResources().getDisplayMetrics())
        );
        iconImageView.setLayoutParams(iconLayoutParams);
        int iconPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        iconImageView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        // Tint the icon colour to match the title text.  Previously this used
        // utilityRowIconColor which caused a mismatch between the icon and the
        // title colour.  Using productViewTitleColor ensures both icon and
        // title are tinted consistently on both light and dark backgrounds.
        iconImageView.setTag(R.id.theme_icon_color_attr, R.attr.productViewTitleColor);

        // Apply a pill‑shaped background via the drawable tag.  The resource string "button"
        // triggers applyThemeRecursively to create a ThemeUtils button background using
        // the themedButtonBackgroundColor and themedButtonPressedBackgroundColor attributes.

       // Initialize titleTextView
        titleTextView = new TextView(context);
        LayoutParams titleLayoutParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleTextView.setLayoutParams(titleLayoutParams);
        titleTextView.setTextSize(22);
        titleTextView.setGravity(Gravity.START);
        titleTextView.setTypeface(null, Typeface.BOLD);
        // === THEME TAG ===
        titleTextView.setTag(R.id.theme_text_color_attr, R.attr.productViewTitleColor);


        // Create a horizontal LinearLayout to hold the icon and title
        LinearLayout titleLayout = new LinearLayout(context);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        // Background for the title layout will be applied via theming.  Previously this
        // hard‑coded the LXX light background colour which prevented proper theme
        // propagation (dark/light or custom palettes).  The titleLayout now relies
        // solely on the theme attribute set via the tag (productViewTitleBackgroundColor)
        // and will be tinted in onThemeChanged via applyThemeRecursively.
        titleLayout.setPadding(16, 12, 16, 12);
        titleLayout.setGravity(Gravity.CENTER_VERTICAL);
        // === THEME TAG ===
        titleLayout.setTag(R.id.theme_background_attr, R.attr.productViewTitleBackgroundColor);
        titleLayout.addView(iconImageView);
        titleLayout.addView(titleTextView);

        addView(titleLayout);

        // Initialize back button
        backButton = new ImageView(context);
        LayoutParams backButtonLayoutParams = new LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics())
        );
        backButtonLayoutParams.gravity = Gravity.BOTTOM | Gravity.END;
        backButtonLayoutParams.setMargins(16, 55, 16, 0);
        backButton.setLayoutParams(backButtonLayoutParams);
        backButton.setElevation(5);
        Drawable icon = getResources().getDrawable(R.drawable.sym_keyboard_back_lxx_light);
        backButton.setImageDrawable(icon);
        // === THEME TAG ===
        backButton.setTag(R.id.theme_icon_color_attr, R.attr.productViewTitleColor);

        backButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPress();
            }
        });
        // Set ripple effect for touch animation
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        backButton.setBackgroundResource(outValue.resourceId);
        addView(backButton);

        // Initialize noSubscriptionOrNotLoggedInView
        noSubscriptionOrNotLoggedInView = new NoSubscriptionOrNotLoggedInView(context, attrs);
        noSubscriptionOrNotLoggedInView.setVisibility(View.GONE);
        addView(noSubscriptionOrNotLoggedInView);

        // Initialize handler and runnable for periodic network checks
        handler = new Handler();
        networkCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkInternetConnection();
                handler.postDelayed(this, 10000); // Check every 5 seconds
            }
        };
        handler.post(networkCheckRunnable);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ProductContainerView,
                0, 0);

        try {
            String title = a.getString(R.styleable.ProductContainerView_titleText);
            titleTextView.setText(title);
        } finally {
            a.recycle();
        }

        // Apply initial theme
        onThemeChanged(getContext());
    }

    // ============================================================================================
    // THEME HANDLING
    // ============================================================================================
    @Override
    public void onThemeChanged(Context themedContext) {
        this.mThemedContext = themedContext;
        Log.d(TAG, "onThemeChanged called. Applying theme to " + getClass().getSimpleName());

        // Theme the shared components in this container
        applyThemeRecursively(this, themedContext);

        // Also theme the subscription view
        if (noSubscriptionOrNotLoggedInView != null) {
            noSubscriptionOrNotLoggedInView.onThemeChanged(themedContext);
        }
    }

    protected void applyThemeRecursively(View view, Context themedContext) {
        if (view == null) return;

        Object bgAttrTag = view.getTag(R.id.theme_background_attr);
        if (bgAttrTag instanceof Integer) {
            view.setBackgroundColor(ThemeUtils.getThemeColor(themedContext, (Integer) bgAttrTag));
        }

        Object backgroundDrawableTag = view.getTag(R.id.theme_background_drawable_attr);
        if ("button".equals(backgroundDrawableTag)) {
            float cornerRadius = getResources().getDimension(R.dimen.button_corner_radius_lxx);
            Drawable buttonBackground = ThemeUtils.createButtonBackground(
                    themedContext, R.attr.themedButtonBackgroundColor,
                    R.attr.themedButtonPressedBackgroundColor, cornerRadius);
            view.setBackground(buttonBackground);
        }

        // 3. Apply text color from attribute
        if (view instanceof TextView) {
            Object textColorAttrTag = view.getTag(R.id.theme_text_color_attr);
            if (textColorAttrTag instanceof Integer) {
                int attrId = (Integer) textColorAttrTag;
                ((TextView) view).setTextColor(ThemeUtils.getThemeColor(themedContext, attrId));
            }
        }

        // 4. Apply icon/image color filter from attribute
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

    private void checkInternetConnection() {
        if (NetworkUtils.isNetworkConnected(getContext())) {
            if (wasDisconnected && mKeyboardSwitcher.didLastApiCallFail()) {
                wasDisconnected = false; // Reset the flag after retrying
            }
        } else {
            handleFailure(getContext().getString(R.string.error_device_network));
            wasDisconnected = true; // Set the flag when disconnected
        }
    }

    public void handleFailure(String errorMessage) {
        if (noInternetLayout != null) {
            noInternetMessage.setText(errorMessage);
            fadeIn(noInternetLayout);
        }
    }

    public boolean checkUserAndSubscription() {
        User user = EncryptedPreferences.getUserLoggedInInfo();
        Log.d(TAG, "User info: user_present=" + (user != null));
        if (user == null) {
            noSubscriptionOrNotLoggedInView.setErrorInfo(ErrorInfo.ErrorType.NOT_LOGGED_IN);
            showSubscriptionView();
            return false;
        } else {
            EncryptedPreferences.FreeTrialInfo trialInfo = EncryptedPreferences.getFreeTrialInfo();
            EncryptedPreferences.SubscriptionInfo subscriptionInfo = EncryptedPreferences.getSubscriptionInfo();
            Log.d(TAG, "Subscription info: " + subscriptionInfo + " Trial info: " + trialInfo);
            if ((trialInfo != null && trialInfo.isFreeTrialEnded())) {
                noSubscriptionOrNotLoggedInView.setErrorInfo(ErrorInfo.ErrorType.SUBSCRIPTION_EXPIRED);
                showSubscriptionView();
                return false;
            } else if (subscriptionInfo != null && !subscriptionInfo.getStatus().equals(Subscription.SubscriptionStatus.ACTIVE.toString())) {
                noSubscriptionOrNotLoggedInView.setErrorInfo(ErrorInfo.ErrorType.NO_ACTIVE_SUBSCRIPTION);
                showSubscriptionView();
                return false;
            }
        }
        Log.d(TAG, "User and subscription check passed.");
        return true;
    }

    public void showSubscriptionView() {
        noSubscriptionOrNotLoggedInView.setVisibility(View.VISIBLE);

    }

    public void hideSubscriptionView() {
        noSubscriptionOrNotLoggedInView.setVisibility(View.GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(networkCheckRunnable);
    }

    public void onBackPress() {
        // Feedback animation
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(200);
        fadeOut.setRepeatCount(1);
        fadeOut.setRepeatMode(Animation.REVERSE);

        backButton.startAnimation(fadeOut);
        // Handle back button click
        mKeyboardSwitcher.hideProductViews();
        mKeyboardSwitcher.hideAiAssistantView();
        mKeyboardSwitcher.showUtilityRow();
        mKeyboardSwitcher.showKeyboardView();
    }

    public void setTitleTextView(String title, AIFeatureType ctaType) {
        titleTextView.setText(title);
        int iconResId;
        switch (ctaType) {
            case AI_CHAT_WRITTEN:
                iconResId = R.drawable.gen_ai_icon;
                break;
            case GRAMMAR:
                iconResId = R.drawable.grammar_v2_icon;
                break;
            case TRANSLATE_WRITTEN:
                iconResId = R.drawable.translate_v2_icon;
                break;
            case TONALITY:
                iconResId = R.drawable.tone_v2_icon;
                break;
            case GENERATE_READ_REPLY:
                iconResId = R.drawable.continue_v2_icon;
                break;
            default:
                iconResId = R.drawable.screen_reader_v2_icon;
                break;
        }
        iconImageView.setImageResource(iconResId);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    /** Try theme color first; fallback to R.color.warning_color */
    private int getWarningColor(Context c) {
        try {
            // If you later add a theme attr (e.g., R.attr.warningColor), use ThemeUtils here.
            // return ThemeUtils.getThemeColor(c, R.attr.warningColor);
            return ContextCompat.getColor(c, R.color.warning_color);
        } catch (Throwable t) {
            return ContextCompat.getColor(c, R.color.warning_color);
        }
    }

    /** Build a rounded pill with a subtle warning tint (12% alpha of warning color) */
    private Drawable buildWarningPillBackground(Context c) {
        int warn = getWarningColor(c);
        int bg = (warn & 0x00FFFFFF) | (0x1F << 24); // ~12% alpha
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(14));
        return d;
    }

    private void fadeIn(View v) {
        if (v.getVisibility() == View.VISIBLE && v.getAlpha() == 1f) return;
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(180).setListener(null).start();
    }

    private void fadeOut(final View v) {
        if (v.getVisibility() != View.VISIBLE) return;
        v.animate().alpha(0f).setDuration(150).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                v.setVisibility(View.GONE);
                v.setAlpha(1f);
            }
        }).start();
    }

}
