package project.witty.keys.app.tutorial;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import project.witty.keys.R;

/**
 * Custom step indicator showing cumulative progress
 * Steps 1 to currentStep are active, rest are inactive
 * Has semi-transparent rounded background
 */
public class TutorialStepIndicator extends View {

    private static final int TOTAL_STEPS = 6;

    private int currentStep = 1;
    private int completedSteps = 0;

    private Paint activePaint;
    private Paint inactivePaint;
    private Paint backgroundPaint;

    private float indicatorWidth;
    private float indicatorHeight;
    private float indicatorSpacing;
    private float cornerRadius;
    private float backgroundCornerRadius;
    private float backgroundPaddingH;
    private float backgroundPaddingV;

    public TutorialStepIndicator(Context context) {
        super(context);
        init();
    }

    public TutorialStepIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TutorialStepIndicator(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        indicatorWidth = 40 * density;
        indicatorHeight = 4 * density;
        indicatorSpacing = 8 * density;
        cornerRadius = 2 * density;
        backgroundCornerRadius = 20 * density;
        backgroundPaddingH = 16 * density;
        backgroundPaddingV = 10 * density;

        // Active indicator paint (bright cyan color)
        activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setColor(ContextCompat.getColor(getContext(), R.color.fourth_app_color));
        activePaint.setStyle(Paint.Style.FILL);

        // Inactive indicator paint (dim color)
        inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setColor(ContextCompat.getColor(getContext(), R.color.fifth_app_color));
        inactivePaint.setAlpha(80);
        inactivePaint.setStyle(Paint.Style.FILL);

        // Semi-transparent background paint (60% opacity)
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.secondary_app_color));
        backgroundPaint.setAlpha(153); // 60% of 255
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Set current step (1-indexed)
     * All steps up to and including completedSteps will be active
     */
    public void setProgress(int currentStep, int completedSteps) {
        this.currentStep = Math.max(1, Math.min(currentStep, TOTAL_STEPS));
        this.completedSteps = Math.max(0, Math.min(completedSteps, TOTAL_STEPS));
        invalidate();
    }

    /**
     * Set completed steps with animation
     */
    public void animateToStep(int completedSteps) {
        int oldCompleted = this.completedSteps;
        this.completedSteps = Math.max(0, Math.min(completedSteps, TOTAL_STEPS));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float totalWidth = (indicatorWidth * TOTAL_STEPS) + (indicatorSpacing * (TOTAL_STEPS - 1)) + (backgroundPaddingH * 2);
        float totalHeight = indicatorHeight + (backgroundPaddingV * 2);

        setMeasuredDimension(
                (int) totalWidth,
                (int) totalHeight
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        // Draw semi-transparent rounded background
        RectF bgRect = new RectF(0, 0, width, height);
        canvas.drawRoundRect(bgRect, backgroundCornerRadius, backgroundCornerRadius, backgroundPaint);

        // Calculate starting X to center indicators
        float totalIndicatorsWidth = (indicatorWidth * TOTAL_STEPS) + (indicatorSpacing * (TOTAL_STEPS - 1));
        float startX = (width - totalIndicatorsWidth) / 2;
        float centerY = height / 2;

        // Draw each indicator
        for (int i = 0; i < TOTAL_STEPS; i++) {
            float left = startX + (i * (indicatorWidth + indicatorSpacing));
            float top = centerY - (indicatorHeight / 2);
            float right = left + indicatorWidth;
            float bottom = top + indicatorHeight;

            RectF rect = new RectF(left, top, right, bottom);

            // Use active paint for completed steps and current step
            boolean isActive = (i < completedSteps) || (i < currentStep);
            Paint paint = isActive ? activePaint : inactivePaint;
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        }
    }
}