package dev.glasshome.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Picks a representative, saturated colour from an app banner for the ambient glow. */
final class AccentSampler {
    private static final int W = 32;
    private static final int H = 18;

    private AccentSampler() {
    }

    static int sample(Drawable drawable, int fallback) {
        if (drawable == null) {
            return fallback;
        }
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Rect previous = new Rect(drawable.getBounds());
        try {
            drawable.setBounds(0, 0, W, H);
            drawable.draw(new Canvas(bitmap));
        } catch (RuntimeException e) {
            bitmap.recycle();
            return fallback;
        } finally {
            drawable.setBounds(previous);
        }

        float[] hsv = new float[3];
        double x = 0;
        double y = 0;
        double weightSum = 0;
        double saturationSum = 0;
        int[] pixels = new int[W * H];
        bitmap.getPixels(pixels, 0, W, 0, 0, W, H);
        bitmap.recycle();
        for (int pixel : pixels) {
            if (Color.alpha(pixel) < 128) {
                continue;
            }
            Color.colorToHSV(pixel, hsv);
            double weight = hsv[1] * hsv[2];
            if (weight < 0.08) {
                continue;
            }
            double radians = Math.toRadians(hsv[0]);
            x += Math.cos(radians) * weight;
            y += Math.sin(radians) * weight;
            weightSum += weight;
            saturationSum += hsv[1] * weight;
        }
        if (weightSum < 1.0) {
            return fallback;
        }
        float hue = (float) ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0);
        float saturation = (float) Math.max(0.45, Math.min(0.85, saturationSum / weightSum));
        return Color.HSVToColor(new float[]{hue, saturation, 1f});
    }
}
