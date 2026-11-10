package dev.glasshome.launcher;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/**
 * Spotlight: a dark stage. A soft pool of light roams slowly across the featured scene while
 * the character always stays lit, with a faint backlight in the title's own colour. Changing
 * title works like a theatre: the lights go down and come up on the next scene.
 */
final class SpotlightHero extends HeroView {
    private static final long ROAM_MS = 18000;

    private final int width;
    private final int height;
    private SceneLayer scene;
    private View darkness;
    private View halo;
    private View curtain;
    private TitleBlock block;
    private ValueAnimator roam;
    private ObjectAnimator breathe;
    private float lightX;
    private float lightY;
    private int transitions;

    SpotlightHero(Context context) {
        super(context);
        width = dp(960);
        height = dp(540);
    }

    @Override
    protected void onBind() {
        Context c = getContext();
        scene = new SceneLayer(this, width, height);
        addView(scene, at(0, 0, width, height));
        // Twice the screen in each direction, so it covers everything wherever the light goes.
        darkness = new View(c);
        darkness.setBackground(lightMask());
        scene.addBehindCharacter(darkness, new FrameLayout.LayoutParams(width * 2, height * 2));
        halo = new View(c);
        scene.addBehindCharacter(halo, new FrameLayout.LayoutParams(1, 1));

        curtain = new View(c);
        curtain.setBackgroundColor(Color.BLACK);
        curtain.setAlpha(0f);
        addView(curtain, at(0, 0, width, height));
        block = new TitleBlock(c, 34, Gravity.START, dp(560));
        addView(block, at(dp(70), dp(262), LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        block.set(current(), false);
        scene.show(this, current(), null);
        light(current());
    }

    /** A clear pool of light fading into darkness, centred in the view. */
    private GradientDrawable lightMask() {
        GradientDrawable mask = new GradientDrawable();
        mask.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        mask.setGradientCenter(0.5f, 0.5f);
        mask.setGradientRadius(dp(500));
        mask.setColors(new int[]{0x00000000, 0x00000000, 0xDC000000, 0xF7000000},
                new float[]{0f, 0.24f, 0.62f, 1f});
        return mask;
    }

    /** Aims the light and the backlight at the title's character. */
    private void light(FeaturedItem item) {
        float[] focus = scene.focusPoint();
        lightX = focus[0];
        lightY = focus[1];
        placeLight(0f, 0f);
        boolean character = item != null && item.hasFullCharacter();
        halo.setVisibility(character ? VISIBLE : GONE);
        if (!character) return;
        int size = Math.max(dp(260), Math.round(item.fullCharRect.height() * height * 1.1f));
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(size / 2f);
        glow.setColors(new int[]{withAlpha(item.accent, 0x8C), withAlpha(item.accent, 0x30), 0x00000000},
                new float[]{0f, 0.5f, 1f});
        halo.setBackground(glow);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) halo.getLayoutParams();
        lp.width = size;
        lp.height = size;
        lp.leftMargin = Math.round(focus[0] - size / 2f);
        lp.topMargin = Math.round(focus[1] - size / 2f);
        halo.setLayoutParams(lp);
    }

    private void placeLight(float dx, float dy) {
        darkness.setTranslationX(lightX + dx - width);
        darkness.setTranslationY(lightY + dy - height);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    @Override
    protected void onStep(int delta) {
        final int transition = ++transitions;
        block.set(current(), true);
        curtain.animate().cancel();
        curtain.animate().alpha(1f).setDuration(380).setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (transition != transitions) return;
                    scene.show(this, current(), () -> {
                        if (transition != transitions) return;
                        light(current());
                        scene.character.setTranslationY(dp(14));
                        scene.character.animate().translationY(0f).setDuration(1000)
                                .setInterpolator(new DecelerateInterpolator(2f)).start();
                        curtain.animate().alpha(0f).setDuration(950)
                                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
                    });
                }).start();
    }

    @Override
    boolean coversScreen() {
        return true;
    }

    @Override
    protected void onFocusChanged() {
        block.setFocused(focused);
    }

    @Override
    protected void onStart() {
        stopAmbient();
        // The light wanders in a slow figure of eight around the character.
        roam = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        roam.setDuration(ROAM_MS);
        roam.setRepeatCount(ValueAnimator.INFINITE);
        roam.setInterpolator(new LinearInterpolator());
        float ax = dp(150);
        float ay = dp(45);
        roam.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            placeLight(ax * (float) Math.sin(t), ay * (float) Math.sin(2 * t + 0.7f));
        });
        roam.start();
        breathe = ObjectAnimator.ofPropertyValuesHolder(halo,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.94f, 1.06f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.94f, 1.06f));
        breathe.setDuration(5000);
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.setInterpolator(new AccelerateDecelerateInterpolator());
        breathe.start();
    }

    @Override
    protected void onStop() {
        stopAmbient();
    }

    private void stopAmbient() {
        if (roam != null) roam.cancel();
        if (breathe != null) breathe.cancel();
        roam = null;
        breathe = null;
    }
}
