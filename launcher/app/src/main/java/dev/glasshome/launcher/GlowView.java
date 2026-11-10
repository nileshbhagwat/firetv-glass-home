package dev.glasshome.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Soft elliptical glow tinted with the focused dock app's banner colour. */
public final class GlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private int color;
    private ValueAnimator animator;

    public GlowView(Context context) {
        this(context, null);
    }

    public GlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        color = context.getColor(R.color.accent_default);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setColor(int newColor, boolean animate) {
        if (animator != null) {
            animator.cancel();
        }
        if (!animate || getWidth() == 0) {
            color = newColor;
            updateShader();
            return;
        }
        animator = ValueAnimator.ofArgb(color, newColor);
        animator.setDuration(450);
        animator.addUpdateListener(a -> {
            color = (int) a.getAnimatedValue();
            updateShader();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader();
    }

    private void updateShader() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        int rgb = color & 0x00FFFFFF;
        RadialGradient gradient = new RadialGradient(0f, 0f, 1f,
                new int[]{0x52000000 | rgb, 0x1C000000 | rgb, rgb},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        matrix.reset();
        matrix.postScale(w / 2f, h / 2f);
        matrix.postTranslate(w / 2f, h / 2f);
        gradient.setLocalMatrix(matrix);
        paint.setShader(gradient);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
