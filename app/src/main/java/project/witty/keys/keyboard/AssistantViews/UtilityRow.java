package project.witty.keys.keyboard.AssistantViews;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.util.HashMap;
import java.util.Map;

import project.witty.keys.R;
import project.witty.keys.app.HomeActivity;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.keyboard.Themeable;
import project.witty.keys.keyboard.shared.LanguageFlags;

public class UtilityRow extends FrameLayout implements Themeable {

    private final Context context;

    // --- VIEWS NEEDED FOR LOGIC (Click Listeners, State Switches, etc.) ---
    private LinearLayout rootLayout;
    private LinearLayout initialState;

    // Add a voice icon for speech input. This sits at the far right of the initial
    // action bar and allows the user to dictate a command or prompt. The actual
    // click listener is provided externally via the public API.
    private ImageView voiceIcon;

    // Additional voice prompt icon for triggering AI suggestions directly. This icon
    // will invoke a voice recognition flow that does not commit the spoken text
    // into the input field but instead sends it as a prompt to the AI and displays
    // the result in the suggestion row.
    private ImageView voicePromptIcon;



    public UtilityRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init(context);
    }

    private void init(Context context) {
        // Root layout
        rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.HORIZONTAL);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.setBackgroundResource(R.drawable.border_rounded_corners_utility);
        addView(rootLayout);

        // --- States setup ---
        int paddingHorizontal = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        int paddingVertical = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());

        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        // --- Initial State ---
        initialState = new LinearLayout(context);
        initialState.setLayoutParams(stateParams);
        initialState.setOrientation(LinearLayout.HORIZONTAL);
        initialState.setGravity(Gravity.CENTER);
        initialState.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

        LayoutParams logoParams = new LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, getResources().getDisplayMetrics())
        );
        logoParams.setMargins((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()), 8, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()), 8);

        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                0,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, getResources().getDisplayMetrics()),
                1.0f
        );
        imageParams.setMargins((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()), 0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()), 0);


        LinearLayout.LayoutParams roundImageParams = new LinearLayout.LayoutParams(
                80,80
        );
        imageParams.setMargins((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics()), 0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()), 0);


        voicePromptIcon = createRoundIconImageView(R.drawable.ic_microphone_ai, roundImageParams);
        initialState.addView(voicePromptIcon);
        initialState.addView(createImageView(R.drawable.gen_ai_icon, imageParams));
        initialState.addView(createImageView(R.drawable.translate_v2_icon, imageParams));
        initialState.addView(createImageView(R.drawable.grammar_v2_icon, imageParams));
        initialState.addView(createImageView(R.drawable.tone_v2_icon, imageParams));
        initialState.addView(createImageView(R.drawable.continue_v2_icon, imageParams));
        voiceIcon = createRoundIconImageView(R.drawable.ic_microphone, roundImageParams);
        initialState.addView(voiceIcon);
        // --- Voice Icon ---
        // We reuse the screen_reader icon as the microphone/voice trigger. A dedicated
        // microphone asset is not available in the project resources, so this will
        // act as a placeholder. If a proper microphone drawable is added later,
        // simply replace the drawable resource here.

        rootLayout.addView(initialState);
        onThemeChanged(this.context);
    }

    // ============================================================================================
    // THEME HANDLING (New Approach)
    // ============================================================================================
    @Override
    public void onThemeChanged(Context themedContext) {
        Log.d("UtilityRow", "onThemeChanged called. Applying new theme via traversal.");

        // Apply theme to the root layout itself
        int utilityRowBackgroundColor = ThemeUtils.getThemeColor(themedContext, R.attr.utilityRowBackground);
        if (rootLayout != null) {
            rootLayout.setBackgroundColor(utilityRowBackgroundColor);
        }

        // Start the recursive theme application on the entire view hierarchy
        applyThemeRecursively(this, themedContext);
    }

    private void applyThemeRecursively(View view, Context themedContext) {
        if (view == null) {
            return;
        }

        // 1. Apply background color from attribute
        Object backgroundAttrTag = view.getTag(R.id.theme_background_attr);
        if (backgroundAttrTag instanceof Integer) {
            int attrId = (Integer) backgroundAttrTag;
            view.setBackgroundColor(ThemeUtils.getThemeColor(themedContext, attrId));
        }


        // 2. Apply drawable background from attribute (for buttons & round icons)
        Object backgroundDrawableTag = view.getTag(R.id.theme_background_drawable_attr);
        if (backgroundDrawableTag instanceof String) {
            String what = (String) backgroundDrawableTag;

            if ("button".equals(what)) {
                float cornerRadius = getResources().getDimension(R.dimen.button_corner_radius_lxx);
                Drawable bg = ThemeUtils.createButtonBackground(
                        themedContext,
                        R.attr.themedButtonBackgroundColor,
                        R.attr.themedButtonPressedBackgroundColor,
                        cornerRadius
                );
                view.setBackground(bg);
            } else if ("round_icon".equals(what)) {
                // Make it a pill; large radius works regardless of the final height
                float cornerRadiusPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 999f, getResources().getDisplayMetrics());

                Drawable bg = ThemeUtils.createButtonBackground(
                        themedContext,
                        // Fill color compatible with your themes
                        R.attr.themedButtonBackgroundColor,
                        // Pressed state color
                        R.attr.themedButtonPressedBackgroundColor,
                        cornerRadiusPx
                );
                view.setBackground(bg);

                // Optional: ensure some min height so the pill looks nice even when weighted
                int minH = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());
                view.setMinimumHeight(minH);
            }
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

        // 5. Apply progress bar tint from attribute
        if (view instanceof ProgressBar) {
            Object tintColorAttrTag = view.getTag(R.id.theme_icon_color_attr); // Re-using icon color for tint
            if (tintColorAttrTag instanceof Integer) {
                int attrId = (Integer) tintColorAttrTag;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ((ProgressBar) view).setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ThemeUtils.getThemeColor(themedContext, attrId)));
                }
            }
        }

        // --- RECURSE FOR ALL CHILDREN ---
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applyThemeRecursively(viewGroup.getChildAt(i), themedContext);
            }
        }
    }

    private ImageView createRoundIconImageView(int drawableId, LinearLayout.LayoutParams lp) {
        LinearLayout.LayoutParams lpnew = lp;
        ImageView iv = new ImageView(context);
        iv.setLayoutParams(lpnew);
        iv.setClickable(true);
        iv.setFocusable(true);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setImageResource(drawableId);


        // LESS padding than before (was dp(8))
        int pad = dp(6);
        iv.setPadding(pad, pad, pad, pad);

        int bgColor   = safeThemeColor(context, R.attr.themedButtonBackgroundColor, android.R.color.darker_gray);
        int pressed   = safeThemeColor(context, R.attr.themedButtonPressedBackgroundColor, android.R.color.black);
        int iconTint  = safeThemeColor(context, R.attr.themedButtonTextColor, android.R.color.white);

        // MORE rounded: make it a full pill by using half the height as radius
        float radius = (lpnew != null && lp.height > 0) ? (lpnew.height / 2f) : dp(999);

        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(radius);
        pill.setColor(bgColor);

        if (Build.VERSION.SDK_INT >= 21) {
            // Keep ripple inside the rounded shape
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(radius);
            mask.setColor(0xFFFFFFFF); // alpha-only mask
            RippleDrawable ripple = new RippleDrawable(
                    android.content.res.ColorStateList.valueOf(pressed),
                    pill,
                    mask
            );
            iv.setBackground(ripple);
            iv.setClipToOutline(true);
        } else {
            iv.setBackground(pill);
        }

        iv.setColorFilter(iconTint);
        iv.setTag(R.id.theme_icon_color_attr, R.attr.themedButtonTextColor);
        return iv;
    }

    // Resolve a theme color safely; fall back to a normal color resource if the attr isn't defined.
    private int safeThemeColor(Context ctx, int attrId, int fallbackColorRes) {
        TypedValue tv = new TypedValue();
        boolean found = ctx.getTheme() != null && ctx.getTheme().resolveAttribute(attrId, tv, true);
        if (found) {
            // If it's a reference (e.g., @color/foo), load it; otherwise tv.data is the ARGB color.
            if (tv.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(ctx, tv.resourceId);
            } else {
                return tv.data;
            }
        }
        return androidx.core.content.ContextCompat.getColor(ctx, fallbackColorRes);
    }

    // dp -> px
    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // ============================================================================================
    // VIEW CREATION METHODS (Updated with Theming Tags)
    // ============================================================================================

    private ImageView createImageView(int drawableId, ViewGroup.LayoutParams layoutParams) {
        ImageView imageView = new ImageView(context);
        imageView.setLayoutParams(layoutParams);
        imageView.setClickable(true);
        imageView.setFocusable(true);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        imageView.setPadding(padding, padding, padding, padding);
        imageView.setImageResource(drawableId);

        // === THEME TAGS ===
        imageView.setTag(R.id.theme_icon_color_attr, R.attr.utilityRowIconColor);
        // The background is not set here; if needed, assign a button drawable via theme in applyThemeRecursively
        return imageView;
    }

    private TextView createButton(String text, ViewGroup.LayoutParams layoutParams) {
        TextView textView = new TextView(context);
        textView.setLayoutParams(layoutParams);
        textView.setText(text);
        textView.setClickable(true);
        textView.setFocusable(true);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        textView.setPadding(padding, padding, padding, padding);

        // === THEME TAGS ===
        textView.setTag(R.id.theme_text_color_attr, R.attr.themedButtonTextColor);
        textView.setTag(R.id.theme_background_drawable_attr, "button");
        return textView;
    }


    private TextView createLanguageButton(String flagEmoji, String language, ViewGroup.LayoutParams layoutParams) {
        TextView textView = new TextView(context);
        textView.setLayoutParams(layoutParams);
        textView.setText(flagEmoji + " " + language);
        textView.setClickable(true);
        textView.setFocusable(true);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        textView.setPadding(padding, padding, padding, padding);

        // === THEME TAGS ===
        textView.setTag(R.id.theme_text_color_attr, R.attr.themedButtonTextColor);
        textView.setTag(R.id.theme_background_drawable_attr, "button");
        return textView;
    }

    private ImageView createBackIcon(LinearLayout.LayoutParams layoutParams) {
        ImageView imageView = new ImageView(context);
        imageView.setLayoutParams(layoutParams);
        imageView.setClickable(true);
        imageView.setFocusable(true);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        imageView.setPadding(padding, padding, padding, padding);
        imageView.setImageResource(R.drawable.back_v2_icon);
        imageView.setTag(R.id.theme_icon_color_attr, R.attr.utilityRowIconColor);
        return imageView;
    }

    // ============================================================================================
    // PUBLIC API (Listeners and State Switching) - No changes needed here
    // ============================================================================================

    /**
     * Assign a click listener to the voice prompt CTA. This image view is used to
     * trigger a voice recognition flow that routes the spoken prompt directly to the
     * AI and shows the result in the suggestion row without committing the speech
     * into the host input field.
     *
     * @param listener the handler invoked when the user taps the voice prompt icon
     */
    public void setVoicePromptCtaClickListener(OnClickListener listener) {
        if (voicePromptIcon != null) {
            voicePromptIcon.setOnClickListener(listener);
        } else {
            // Fallback: attach the listener to the last child if the prompt icon
            // is not yet initialised. The index will be one after the voice icon.
            if (initialState != null && initialState.getChildCount() > 0) {
                initialState.getChildAt(0).setOnClickListener(listener);
            }
        }
    }
    public void setChatgptCtaClickListener(OnClickListener listener) {
        // Since we don't have a global var, we find the view.
        // This is slightly less efficient but demonstrates how to do it.
        // For frequently accessed views, keeping a global var is fine.
        // Assuming order is fixed:
        if (initialState.getChildCount() > 1) initialState.getChildAt(1).setOnClickListener(listener);
    }
    public void setTranslationCtaClickListener(OnClickListener listener) {
        if (initialState.getChildCount() > 2) initialState.getChildAt(2).setOnClickListener(listener);
    }
    public void setGrammarCtaClickListener(OnClickListener listener) {
        if (initialState.getChildCount() > 3) initialState.getChildAt(3).setOnClickListener(listener);
    }
    public void setTonalCtaClickListener(OnClickListener listener) {
        if (initialState.getChildCount() > 4) initialState.getChildAt(4).setOnClickListener(listener);
    }
    public void setContinueMessageClickListener(OnClickListener listener) {
        if (initialState.getChildCount() > 5) initialState.getChildAt(5).setOnClickListener(listener);
    }

    /**
     * Assign a click listener to the voice (speech) CTA. This image view is the
     * final child of the initial state and enables voice-based interactions.
     *
     * @param listener the handler invoked when the user taps the voice icon
     */
    public void setVoiceCtaClickListener(OnClickListener listener) {
        if (voiceIcon != null) {
            voiceIcon.setOnClickListener(listener);
        } else {
            // Fallback: if voiceIcon is not yet initialised (unlikely), attach
            // the listener to the last child of the initial state as a best-effort.
            if (initialState != null && initialState.getChildCount() > 6) {
                initialState.getChildAt(6).setOnClickListener(listener);
            }
        }
    }



    public void startVoiceInputAnimation() {
        stopVoiceAnimation();
        if (voiceIcon == null) return;
        android.view.animation.Animation pulse = new android.view.animation.ScaleAnimation(
                1f, 1.05f, 1f, 1.05f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        );
        pulse.setDuration(400);
        pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
        pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
        voiceIcon.startAnimation(pulse);
    }

    public void startVoicePromptAnimation() {
        stopVoiceAnimation();
        if (voicePromptIcon == null) return;
        android.view.animation.Animation pulse = new android.view.animation.ScaleAnimation(
                1f, 1.05f, 1f, 1.05f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        );
        pulse.setDuration(400);
        pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
        pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
        voicePromptIcon.startAnimation(pulse);
    }

    /**
     * Starts a pulsing animation on the voice and voice prompt icons to indicate
     * that the microphone is actively listening.  The animation scales the
     * icons up and down repeatedly.  If the animation is already running,
     * calling this again will restart it.
     */

    /**
     * Stops the pulsing animation on the voice and voice prompt icons.  After
     * calling this, the icons will revert to their original size.
     */
    public void stopVoiceAnimation() {
        if (voiceIcon != null) {
            voiceIcon.clearAnimation();
        }
        if (voicePromptIcon != null) {
            voicePromptIcon.clearAnimation();
        }
    }

// ============================================================================================
// TUTORIAL HIGHLIGHT FEATURE - Add this to end of UtilityRow.java (before closing brace)
// ============================================================================================

    private View currentHighlightedView = null;
    private android.animation.AnimatorSet currentHighlightAnimator = null;
    private String currentHighlightedButtonType = null;
    private Integer originalIconTint = null;

    /**
     * Highlights a specific CTA button with a pulsing scale + color animation.
     * Used during tutorial to guide users to the correct button.
     *
     * @param buttonType One of: "AI_CHAT", "READ_SCREEN", "TONALITY", "GRAMMAR", "TRANSLATE"
     */
    public void highlightButton(String buttonType) {
        if (buttonType == null) {
            Log.w("UtilityRow", "highlightButton called with null buttonType");
            return;
        }

        // Don't re-highlight if same button is already highlighted
        if (buttonType.equals(currentHighlightedButtonType) && isHighlighting()) {
            Log.d("UtilityRow", "Button already highlighted: " + buttonType);
            return;
        }

        // First, stop any existing highlight completely
        stopHighlight();

        View targetView = getButtonByType(buttonType);
        if (targetView == null) {
            Log.w("UtilityRow", "Cannot highlight unknown button type: " + buttonType);
            return;
        }

        if (!(targetView instanceof ImageView)) {
            Log.w("UtilityRow", "Target view is not an ImageView: " + buttonType);
            return;
        }

        ImageView targetIcon = (ImageView) targetView;
        currentHighlightedView = targetIcon;
        currentHighlightedButtonType = buttonType;

        // Store original tint color
        if (targetIcon.getColorFilter() != null) {
            // Try to get color from theme
            originalIconTint = safeThemeColor(context, R.attr.utilityRowIconColor, android.R.color.white);
        }

        // Highlight color - bright gold/yellow
        final int highlightColor = 0xFFFFD700; // Gold
        final int originalColor = originalIconTint != null ? originalIconTint : 0xFFFFFFFF;

        // Create pulsing scale animation
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(
                targetIcon, "scaleX", 1f, 1.25f, 1f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(
                targetIcon, "scaleY", 1f, 1.25f, 1f);

        // Create color tint animation using ArgbEvaluator
        android.animation.ValueAnimator colorAnim = android.animation.ValueAnimator.ofObject(
                new android.animation.ArgbEvaluator(), originalColor, highlightColor, originalColor);
        colorAnim.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            targetIcon.setColorFilter(color);
        });

        currentHighlightAnimator = new android.animation.AnimatorSet();
        currentHighlightAnimator.playTogether(scaleX, scaleY, colorAnim);
        currentHighlightAnimator.setDuration(800);
        currentHighlightAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        // Loop indefinitely
        currentHighlightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!cancelled && currentHighlightAnimator != null && currentHighlightedView == targetIcon) {
                    // Restart animation
                    currentHighlightAnimator.start();
                }
            }
        });

        currentHighlightAnimator.start();

        Log.d("UtilityRow", "🎯 Highlighting button: " + buttonType);
    }

    /**
     * Stops the current highlight animation and resets the view.
     */
    public void stopHighlight() {
        Log.d("UtilityRow", "🔴 stopHighlight() called. Current: " + currentHighlightedButtonType);

        if (currentHighlightAnimator != null) {
            currentHighlightAnimator.removeAllListeners();
            currentHighlightAnimator.cancel();
            currentHighlightAnimator.end();
            currentHighlightAnimator = null;
        }

        if (currentHighlightedView != null) {
            // Reset scale
            currentHighlightedView.setScaleX(1f);
            currentHighlightedView.setScaleY(1f);
            currentHighlightedView.setAlpha(1f);
            currentHighlightedView.clearAnimation();

            // Restore original icon tint
            if (currentHighlightedView instanceof ImageView) {
                ImageView iv = (ImageView) currentHighlightedView;
                if (originalIconTint != null) {
                    iv.setColorFilter(originalIconTint);
                } else {
                    // Restore from theme
                    int themeColor = safeThemeColor(context, R.attr.utilityRowIconColor, android.R.color.white);
                    iv.setColorFilter(themeColor);
                }
            }

            currentHighlightedView = null;
        }

        currentHighlightedButtonType = null;
        originalIconTint = null;
    }

    /**
     * Gets the View for a given button type.
     * Button layout in initialState:
     * - Index 0: voicePromptIcon
     * - Index 1: AI Chat (gen_ai_icon)
     * - Index 2: Translate (translate_v2_icon)
     * - Index 3: Grammar (grammar_v2_icon)
     * - Index 4: Tonality (tone_v2_icon)
     * - Index 5: Read Screen (continue_v2_icon)
     * - Index 6: voiceIcon
     */
    private View getButtonByType(String buttonType) {
        if (initialState == null) {
            Log.w("UtilityRow", "initialState is null");
            return null;
        }

        int index = -1;
        switch (buttonType) {
            case "AI_CHAT":
                index = 1;
                break;
            case "TRANSLATE":
                index = 2;
                break;
            case "GRAMMAR":
                index = 3;
                break;
            case "TONALITY":
                index = 4;
                break;
            case "READ_SCREEN":
                index = 5;
                break;
            default:
                Log.w("UtilityRow", "Unknown button type: " + buttonType);
                return null;
        }

        if (index >= 0 && index < initialState.getChildCount()) {
            return initialState.getChildAt(index);
        }

        Log.w("UtilityRow", "Button index out of bounds: " + index + " for " + buttonType);
        return null;
    }

    /**
     * Check if any button is currently highlighted.
     */
    public boolean isHighlighting() {
        return currentHighlightedView != null && currentHighlightAnimator != null
                && currentHighlightAnimator.isRunning();
    }

    /**
     * Get currently highlighted button type.
     */
    public String getCurrentHighlightedButtonType() {
        return currentHighlightedButtonType;
    }

}