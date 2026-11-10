package dev.glasshome.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * The single translucent "glass" plate that springs from the focused element to the next.
 * Position, size and corner radius follow a damped spring; nothing is blurred.
 */
public final class HighlightView extends View {
    private static final float STIFFNESS = 420f;
    private static final float DAMPING_RATIO = 0.74f;
    private static final float DAMPING = 2f * DAMPING_RATIO * (float) Math.sqrt(STIFFNESS);

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    // left, top, right, bottom, radius
    private final float[] current = new float[5];
    private final float[] velocity = new float[5];
    private final float[] target = new float[5];
    private boolean placed;
    private boolean animating;
    private boolean shown = true;
    private long lastFrame;

    public HighlightView(Context context) {
        this(context, null);
    }

    public HighlightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        fill.setColor(context.getColor(R.color.glass_fill));
        stroke.setColor(context.getColor(R.color.glass_stroke));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * density);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void moveTo(RectF to, float radius, boolean animate) {
        target[0] = to.left;
        target[1] = to.top;
        target[2] = to.right;
        target[3] = to.bottom;
        target[4] = radius;
        shown = true;
        if (!placed || !animate) {
            System.arraycopy(target, 0, current, 0, 5);
            java.util.Arrays.fill(velocity, 0f);
            placed = true;
            animating = false;
            invalidate();
            return;
        }
        if (!animating) {
            animating = true;
            lastFrame = 0;
            postInvalidateOnAnimation();
        }
    }

    void hide() {
        shown = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!placed || !shown) {
            return;
        }
        if (animating) {
            step();
        }
        rect.set(current[0], current[1], current[2], current[3]);
        canvas.drawRoundRect(rect, current[4], current[4], fill);
        canvas.drawRoundRect(rect, current[4], current[4], stroke);
        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    private void step() {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 1f / 60f : Math.min(0.034f, (now - lastFrame) / 1000f);
        lastFrame = now;
        final int substeps = 4;
        float h = dt / substeps;
        for (int s = 0; s < substeps; s++) {
            for (int i = 0; i < 5; i++) {
                float accel = -STIFFNESS * (current[i] - target[i]) - DAMPING * velocity[i];
                velocity[i] += accel * h;
                current[i] += velocity[i] * h;
            }
        }
        boolean settled = true;
        for (int i = 0; i < 5; i++) {
            if (Math.abs(current[i] - target[i]) > 0.5f || Math.abs(velocity[i]) > 8f) {
                settled = false;
                break;
            }
        }
        if (settled) {
            System.arraycopy(target, 0, current, 0, 5);
            java.util.Arrays.fill(velocity, 0f);
            animating = false;
        }
    }
}
