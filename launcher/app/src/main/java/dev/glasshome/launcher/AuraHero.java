package dev.glasshome.launcher;

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Aura: the featured scene is washed in a single tone of the title's own colour while the
 * character stays in full colour inside a slowly breathing glow, and small motes of light
 * drift upwards. Changing title dissolves into the next scene and its colour.
 */
final class AuraHero extends HeroView {
    private static final int MOTES = 10;

    private final int width;
    private final int height;
    private final SceneLayer[] layers = new SceneLayer[2];
    private final View[] halos = new View[2];
    private final List<View> motes = new ArrayList<>();
    private final List<ValueAnimator> drifting = new ArrayList<>();
    private FrameLayout stage;
    private TitleBlock block;
    private ObjectAnimator breathe;
    private int front;
    private int transitions;

    AuraHero(Context context) {
        super(context);
        width = dp(960);
        height = dp(540);
    }

    @Override
    protected void onBind() {
        Context c = getContext();
        motes.clear();
        stage = new FrameLayout(c);
        stage.setClipChildren(false);
        addView(stage, at(0, 0, width, height));
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new SceneLayer(this, width, height);
            halos[i] = new View(c);
            layers[i].addBehindCharacter(halos[i], new FrameLayout.LayoutParams(1, 1));
            stage.addView(layers[i], at(0, 0, width, height));
        }
        front = 0;
        layers[1].setAlpha(0f);
        dress(0, current());
        layers[0].show(this, current(), null);

        // Motes of light in front of the scene, at fixed spots so the screen never looks busy.
        Random random = new Random(7);
        for (int i = 0; i < MOTES; i++) {
            int size = dp(5 + random.nextInt(9));
            View mote = new View(c);
            GradientDrawable dot = new GradientDrawable();
            dot.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            dot.setGradientRadius(size / 2f);
            dot.setColors(new int[]{0xE6FFFFFF, 0x00FFFFFF});
            mote.setBackground(dot);
            mote.setAlpha(0f);
            addView(mote, at(dp(380 + random.nextInt(540)), dp(120 + random.nextInt(280)), size, size));
            motes.add(mote);
        }

        Shading.addFloor(this, width, height);
        block = new TitleBlock(c, 34, Gravity.START, dp(560));
        addView(block, at(dp(70), dp(262), LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        block.set(current(), false);
    }

    /** One tone of the title's colour on the scene, and its glow behind the character. */
    private void dress(int index, FeaturedItem item) {
        int tone = tone(item.accent);
        layers[index].backdrop.setColorFilter(duotone(tone));
        View halo = halos[index];
        boolean character = item.hasFullCharacter();
        halo.setVisibility(character ? VISIBLE : GONE);
        if (!character) return;
        int size = Math.max(dp(280), Math.round(item.fullCharRect.height() * height * 1.15f));
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(size / 2f);
        glow.setColors(new int[]{withAlpha(tone, 0xB3), withAlpha(tone, 0x40), 0x00000000},
                new float[]{0f, 0.45f, 1f});
        halo.setBackground(glow);
        float cx = item.fullCharRect.centerX() * width;
        float cy = (item.fullCharRect.top + item.fullCharRect.height() * 0.4f) * height;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) halo.getLayoutParams();
        lp.width = size;
        lp.height = size;
        lp.leftMargin = Math.round(cx - size / 2f);
        lp.topMargin = Math.round(cy - size / 2f);
        halo.setLayoutParams(lp);
        halo.setScaleX(1f);
        halo.setScaleY(1f);
        halo.setAlpha(1f);
    }

    /** The title's colour, made vivid and bright enough to carry a whole scene. */
    private static int tone(int accent) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.max(0.35f, Math.min(0.75f, hsv[1]));
        hsv[2] = 0.92f;
        return Color.HSVToColor(hsv);
    }

    /**
     * Duotone: each pixel's brightness picks a colour between a deep shadow of the tone and the
     * tone itself, so the picture keeps its detail but takes on a single colour.
     */
    private static ColorMatrixColorFilter duotone(int tone) {
        float[] high = {Color.red(tone), Color.green(tone), Color.blue(tone)};
        float[] luma = {0.299f, 0.587f, 0.114f};
        float[] m = new float[20];
        for (int c = 0; c < 3; c++) {
            float shadow = high[c] * 0.08f;
            for (int k = 0; k < 3; k++) m[c * 5 + k] = luma[k] * (high[c] - shadow) / 255f;
            m[c * 5 + 4] = shadow;
        }
        m[18] = 1f;
        return new ColorMatrixColorFilter(m);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    @Override
    protected void onStep(int delta) {
        final int transition = ++transitions;
        SceneLayer out = layers[front];
        front = 1 - front;
        final int next = front;
        SceneLayer in = layers[next];
        FeaturedItem item = current();
        in.reset();
        in.setAlpha(0f);
        dress(next, item);
        in.show(this, item, () -> {
            if (transition != transitions) return;
            block.set(item, true); // the name changes with the picture
            in.bringToFront();
            in.animate().alpha(1f).setDuration(1100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            in.character.setAlpha(0f);
            in.character.setTranslationY(dp(16));
            in.character.animate().alpha(1f).translationY(0f).setStartDelay(200).setDuration(950)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            View halo = halos[next];
            halo.setScaleX(0.6f);
            halo.setScaleY(0.6f);
            halo.animate().scaleX(1f).scaleY(1f).setDuration(1400)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .withEndAction(() -> {
                        if (next == front && running) startBreathing();
                    }).start();
            out.animate().alpha(0f).setDuration(1100).start();
        });
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
        startBreathing();
        stopMotes();
        Random random = new Random(11);
        for (View mote : motes) {
            float peak = 0.25f + random.nextFloat() * 0.45f;
            ObjectAnimator drift = ObjectAnimator.ofPropertyValuesHolder(mote,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -dp(60 + random.nextInt(90))),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, dp(random.nextInt(41) - 20)),
                    PropertyValuesHolder.ofKeyframe(View.ALPHA, Keyframe.ofFloat(0f, 0f),
                            Keyframe.ofFloat(0.5f, peak), Keyframe.ofFloat(1f, 0f)));
            drift.setDuration(7000 + random.nextInt(6000));
            drift.setRepeatCount(ValueAnimator.INFINITE);
            drift.setInterpolator(new LinearInterpolator());
            drift.start();
            drift.setCurrentFraction(random.nextFloat()); // not all in step
            drifting.add(drift);
        }
    }

    @Override
    protected void onStop() {
        if (breathe != null) breathe.cancel();
        breathe = null;
        stopMotes();
    }

    private void startBreathing() {
        if (breathe != null) breathe.cancel();
        View halo = halos[front];
        breathe = ObjectAnimator.ofPropertyValuesHolder(halo,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1.05f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.95f, 1.05f));
        breathe.setDuration(4800);
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.setInterpolator(new AccelerateDecelerateInterpolator());
        breathe.start();
    }

    private void stopMotes() {
        for (ValueAnimator drift : drifting) drift.cancel();
        drifting.clear();
        for (View mote : motes) mote.setAlpha(0f);
    }
}
