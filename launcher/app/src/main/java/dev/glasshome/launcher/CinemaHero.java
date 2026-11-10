package dev.glasshome.launcher;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Cinema: the featured title fills the screen with no frame, and the camera slowly pushes in
 * towards the character and back (a Ken Burns move). The character grows a little faster than
 * the scene behind it, so it stands apart in depth. Changing title dollies through: the old
 * scene glides towards the viewer and dissolves while the next settles in from slightly closer.
 */
final class CinemaHero extends HeroView {
    private static final long PUSH_MS = 14000;
    // A gentle push: the shading drawn into the picture moves with it and must stay behind the clock.
    private static final float REST_SCALE = 1.03f;
    private static final float PUSH_SCALE = 1.10f;

    private final int width;
    private final int height;
    private final SceneLayer[] layers = new SceneLayer[2];
    private FrameLayout stage;
    private int front;
    private int transitions;
    private AnimatorSet push;
    private TitleBlock block;

    CinemaHero(Context context) {
        super(context);
        width = dp(960);
        height = dp(540);
    }

    @Override
    protected void onBind() {
        Context c = getContext();
        stage = new FrameLayout(c);
        stage.setClipChildren(false);
        addView(stage, at(0, 0, width, height));
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new SceneLayer(this, width, height);
            stage.addView(layers[i], at(0, 0, width, height));
        }
        front = 0;
        layers[1].setAlpha(0f);
        SceneLayer first = layers[front];
        first.show(this, current(), null);
        settle(first);
        first.setScaleX(REST_SCALE);
        first.setScaleY(REST_SCALE);

        block = new TitleBlock(c, 34, Gravity.START, dp(560));
        addView(block, at(dp(70), dp(262), LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        block.set(current(), false);
    }

    @Override
    protected void onStep(int delta) {
        final int transition = ++transitions;
        stopPush();
        SceneLayer out = layers[front];
        front = 1 - front;
        SceneLayer in = layers[front];
        block.set(current(), true);
        in.reset();
        in.setAlpha(0f);
        in.show(this, current(), () -> {
            if (transition == transitions) dollyThrough(out, in, delta);
        });
    }

    /** The old scene glides towards the viewer and dissolves; the new one settles in. */
    private void dollyThrough(SceneLayer out, SceneLayer in, int delta) {
        in.bringToFront();
        settle(in);
        in.setScaleX(PUSH_SCALE);
        in.setScaleY(PUSH_SCALE);
        in.character.setTranslationY(dp(18));
        in.character.setAlpha(0f);
        in.animate().alpha(1f).scaleX(REST_SCALE).scaleY(REST_SCALE).setDuration(1150)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    if (in == layers[front] && running) startPush(in);
                }).start();
        in.character.animate().alpha(1f).translationY(0f).setStartDelay(240).setDuration(900)
                .setInterpolator(new DecelerateInterpolator(2f)).start();

        float grow = out.getScaleX() * 1.12f;
        out.animate().alpha(0f).scaleX(grow).scaleY(grow).translationX(-delta * dp(36)).setDuration(900)
                .setInterpolator(new AccelerateInterpolator(1.2f)).start();
        out.character.animate().alpha(0f).scaleX(1.1f).scaleY(1.1f).setDuration(700)
                .setInterpolator(new AccelerateInterpolator()).start();
    }

    /** Pivots for the push: the scene zooms on the character's upper body, the character from its feet. */
    private void settle(SceneLayer layer) {
        float[] focus = layer.focusPoint();
        layer.setPivotX(focus[0]);
        layer.setPivotY(focus[1]);
        View character = layer.character;
        if (character.getLayoutParams() != null) {
            character.setPivotX(character.getLayoutParams().width / 2f);
            character.setPivotY(character.getLayoutParams().height);
        }
    }

    private void startPush(SceneLayer layer) {
        stopPush();
        ObjectAnimator scene = ObjectAnimator.ofPropertyValuesHolder(layer,
                PropertyValuesHolder.ofFloat(View.SCALE_X, layer.getScaleX(), PUSH_SCALE),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, layer.getScaleY(), PUSH_SCALE));
        // The character grows a little faster than the scene: it reads as nearer.
        ObjectAnimator figure = ObjectAnimator.ofPropertyValuesHolder(layer.character,
                PropertyValuesHolder.ofFloat(View.SCALE_X, layer.character.getScaleX(), 1.035f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, layer.character.getScaleY(), 1.035f));
        for (ObjectAnimator animator : new ObjectAnimator[]{scene, figure}) {
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setRepeatCount(ValueAnimator.INFINITE);
        }
        push = new AnimatorSet();
        push.playTogether(scene, figure);
        push.setDuration(PUSH_MS);
        push.setInterpolator(new AccelerateDecelerateInterpolator());
        push.start();
    }

    private void stopPush() {
        if (push != null) push.cancel();
        push = null;
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
        if (layers[front] != null) startPush(layers[front]);
    }

    @Override
    protected void onStop() {
        stopPush();
    }
}
