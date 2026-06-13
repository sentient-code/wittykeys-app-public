// project/witty/keys/keyboard/shared/ShimmerLoaderView.java
package project.witty.keys.keyboard.shared;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import project.witty.keys.R;

public class ShimmerLoaderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    // Colors (light grey)
    private int baseColor = 0xFFE0E0E0;
    private int highlightColor = 0xFFF5F5F5;

    // Shape
    private float cornerRadiusPx = 12f * getResources().getDisplayMetrics().density;

    // Multi-line layout
    private boolean multiLine = true; // <- now default to multi-line
    private float lineHeightPx = 14f * getResources().getDisplayMetrics().density;
    private float lineSpacingPx = 8f * getResources().getDisplayMetrics().density;
    private float[] lineFractions = new float[]{0.5f, 0.75f, 1f}; // 50%, 75%, 100%

    // Shimmer
    private LinearGradient gradient;
    private ValueAnimator animator;
    private boolean autoStart = true;

    public ShimmerLoaderView(Context c) { super(c); init(null); }
    public ShimmerLoaderView(Context c, @Nullable AttributeSet a) { super(c, a); init(a); }
    public ShimmerLoaderView(Context c, @Nullable AttributeSet a, int s) { super(c, a, s); init(a); }

    private void init(@Nullable AttributeSet attrs) {
        // Safer across devices for shader-matrix animation
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (autoStart) post(this::startShimmer);
    }

    @Override protected void onDetachedFromWindow() {
        stopShimmer();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildGradient(w, h);
    }

    private void buildGradient(int w, int h) {
        if (w <= 0 || h <= 0) {
            gradient = null;
            paint.setShader(null);
            return;
        }
        gradient = new LinearGradient(
                -w, 0, 0, 0,
                new int[]{baseColor, highlightColor, baseColor},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        paint.setShader(gradient);
        matrix.reset();
        if (animator != null && animator.isRunning()) {
            gradient.setLocalMatrix(matrix);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float w = getWidth();
        final float h = getHeight();

        // If gradient not ready yet, fill with base
        if (paint.getShader() == null) {
            paint.setColor(baseColor);
        }

        if (!multiLine) {
            // Single bar fallback
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint);
            return;
        }

        // Compute heights to fit exactly: 3*lineHeight + 2*spacing = total block
        float desired = 3 * lineHeightPx + 2 * lineSpacingPx;
        if (h > 0 && Math.abs(h - desired) > getResources().getDisplayMetrics().density) {
            // Scale heights proportionally to fill given view height
            float scale = h / desired;
            float scaledLineHeight = lineHeightPx * scale;
            float scaledSpacing = lineSpacingPx * scale;
            drawLines(canvas, w, h, scaledLineHeight, scaledSpacing);
        } else {
            drawLines(canvas, w, h, lineHeightPx, lineSpacingPx);
        }
    }

    private void drawLines(Canvas canvas, float w, float h, float lineH, float spacing) {
        // Center the 3 lines block vertically
        float blockH = 3 * lineH + 2 * spacing;
        float topStart = (h - blockH) / 2f;

        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;

        for (int i = 0; i < 3; i++) {
            float widthFrac = lineFractions[i];
            float lineW = Math.max(0f, Math.min(1f, widthFrac)) * w;

            float top = topStart + i * (lineH + spacing);
            float bottom = top + lineH;

            float left = rtl ? (w - lineW) : 0f;
            float right = rtl ? w : lineW;

            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint);
        }
    }

    public void startShimmer() {
        if (getWidth() == 0 || getHeight() == 0) { post(this::startShimmer); return; }
        if (animator != null && animator.isRunning()) return;

        final float w = getWidth();
        animator = ValueAnimator.ofFloat(-w, w);
        animator.setDuration(1200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.addUpdateListener(a -> {
            float x = (float) a.getAnimatedValue();
            matrix.setTranslate(x, 0f);
            if (gradient != null) gradient.setLocalMatrix(matrix);
            invalidate();
        });
        animator.start();
    }

    public void stopShimmer() {
        if (animator != null) {
            animator.cancel();
            animator.removeAllUpdateListeners();
            animator = null;
        }
        invalidate();
    }

    // ---- Public setters (optional) ----
    public void setBaseColor(int color) { baseColor = color; buildGradient(getWidth(), getHeight()); }
    public void setHighlightColor(int color) { highlightColor = color; buildGradient(getWidth(), getHeight()); }
    public void setCornerRadiusPx(float r) { cornerRadiusPx = r; invalidate(); }
    public void setMultiLine(boolean enabled) { multiLine = enabled; invalidate(); }
    public void setLineFractions(float f1, float f2, float f3) {
        lineFractions = new float[]{f1, f2, f3}; invalidate();
    }
    public void setLineHeights(float lineHeightPx, float lineSpacingPx) {
        this.lineHeightPx = lineHeightPx; this.lineSpacingPx = lineSpacingPx; invalidate();
    }
}
