package dev.glasshome.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

/**
 * The shading of the borderless styles, drawn into the artwork once when a title is loaded:
 * darker at the top (clock, greeting and pills), on the left (the title's name) and towards the
 * dock, where the picture fades to black. Drawn into the picture rather than as layers on top,
 * because the TV's graphics chip spends about 9 ms on every full-screen layer it paints, and a
 * smooth frame has only 16 ms.
 */
final class Shading {
    private Shading() {
    }

    /** The source picture centre-cropped to width x height with the shading drawn in. */
    static Bitmap bake(Bitmap source, int width, int height) {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        float scale = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((width - source.getWidth() * scale) / 2f, (height - source.getHeight() * scale) / 2f);
        canvas.drawBitmap(source, matrix, paint);

        float dp = width / 960f; // the page is laid out for 960 x 540 dp
        // Top: clock, greeting and pills.
        vertical(canvas, width, 0, 230 * dp, new int[]{0xB3000000, 0x4D000000, 0x00000000}, new float[]{0f, 0.45f, 1f});
        // Left: the featured title's name.
        Paint side = new Paint(Paint.DITHER_FLAG);
        side.setShader(new LinearGradient(0, 0, 620 * dp, 0, new int[]{0xA6000000, 0x40000000, 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 620 * dp, height, side);
        // Bottom: into the black behind the dock.
        vertical(canvas, width, 0, 450 * dp, new int[]{0x00000000, 0x26000000, 0xFF000000}, new float[]{0f, 0.55f, 1f});
        Paint floor = new Paint();
        floor.setColor(0xFF000000);
        canvas.drawRect(0, 450 * dp, width, height, floor);
        return out;
    }

    /**
     * Fades the bottom of the scene, characters included, into the black behind the dock. Only
     * a strip at the bottom, so it costs little to paint.
     */
    static void addFloor(HeroView hero, int width, int height) {
        android.content.Context c = hero.getContext();
        android.view.View fade = new android.view.View(c);
        fade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xFF000000}));
        hero.addView(fade, HeroView.at(0, hero.dp(390), width, hero.dp(60)));
        android.view.View floor = new android.view.View(c);
        floor.setBackgroundColor(0xFF000000);
        hero.addView(floor, HeroView.at(0, hero.dp(450), width, height - hero.dp(450)));
    }

    private static void vertical(Canvas canvas, int width, float top, float bottom, int[] colors, float[] stops) {
        Paint paint = new Paint(Paint.DITHER_FLAG);
        paint.setShader(new LinearGradient(0, top, 0, bottom, colors, stops, Shader.TileMode.CLAMP));
        canvas.drawRect(0, top, width, bottom, paint);
    }
}
