package dev.glasshome.launcher;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Break-out card: the featured artwork sits in a tilting glass card, its character climbs out
 * over the top edge, and small glass shards drift around it, some behind and some in front.
 */
final class BreakoutHero extends HeroView {
    /** Shards relative to the card: x, y, width, height (dp), rotation, in front of the card (1) or behind (0). */
    private static final float[][] SHARDS = {
            {-46, -24, 64, 44, -9, 0},
            {268, 128, 82, 52, 8, 0},
            {96, 162, 40, 28, -5, 0},
            {-26, 140, 44, 32, 12, 1},
            {323, 28, 32, 44, -10, 1},
            {206, -50, 26, 26, 15, 1},
    };

    private final int cardWidth;
    private final int cardHeight;
    private final int left;
    private final int top;
    private final List<View> shards = new ArrayList<>();
    private final List<Float> shardRotations = new ArrayList<>();
    private final List<Animator> ambient = new ArrayList<>();
    private FrameLayout tilt;
    private FrameLayout flip;
    private ImageView art;
    private ImageView character;
    private TitleBlock block;

    BreakoutHero(Context context) {
        super(context);
        cardWidth = dp(313);
        cardHeight = dp(176);
        left = dp(578);
        top = dp(164);
    }

    @Override
    protected void onBind() {
        Context c = getContext();
        shards.clear();
        shardRotations.clear();
        addShards(c, false);

        tilt = new FrameLayout(c);
        tilt.setClipChildren(false);
        tilt.setCameraDistance(9000 * density);
        flip = new FrameLayout(c);
        flip.setClipChildren(false);
        flip.setCameraDistance(9000 * density);
        art = artView();
        flip.addView(art, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        character = characterView();
        flip.addView(character, at(0, 0, 1, 1));
        tilt.addView(flip, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(tilt, at(left, top, cardWidth, cardHeight));

        addShards(c, true);
        block = new TitleBlock(c, 34, Gravity.START, dp(460));
        addView(block, at(dp(70), dp(262), LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        block.set(current(), false);
        show(current(), false);
    }

    private void addShards(Context c, boolean front) {
        for (float[] s : SHARDS) {
            if ((s[5] == 1) != front) continue;
            View shard = new View(c);
            shard.setBackgroundResource(R.drawable.glass_shard);
            shard.setRotation(s[4]);
            addView(shard, at(left + dp(s[0]), top + dp(s[1]), dp(s[2]), dp(s[3])));
            shards.add(shard);
            shardRotations.add(s[4]);
        }
    }

    private void show(FeaturedItem item, boolean animate) {
        if (item == null) return;
        art.setContentDescription(item.title);
        loadInto(art, item.card, cardWidth, cardHeight, false, null);
        if (!item.hasCardCharacter()) {
            loadInto(character, null, 0, 0, true, null);
            character.setVisibility(GONE);
            return;
        }
        character.setVisibility(VISIBLE);
        place(character, item.cardCharRect, cardWidth, cardHeight);
        loadInto(character, item.cardChar, character.getLayoutParams().width,
                character.getLayoutParams().height, true, () -> {
                    if (animate) {
                        character.setAlpha(0f);
                        character.setTranslationY(dp(20));
                        character.animate().alpha(1f).translationY(0f).setStartDelay(220).setDuration(560)
                                .setInterpolator(new OvershootInterpolator(1.3f)).start();
                    } else {
                        character.setAlpha(1f);
                        character.setTranslationY(0f);
                    }
                });
    }

    @Override
    protected void onStep(int delta) {
        block.set(current(), true);
        float out = delta > 0 ? -90f : 90f;
        flip.animate().cancel();
        flip.animate().rotationY(out).setDuration(200).setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    show(current(), true);
                    flip.setRotationY(-out);
                    flip.animate().rotationY(0f).setDuration(480)
                            .setInterpolator(new OvershootInterpolator(1.2f)).start();
                }).start();
    }

    @Override
    protected void onFocusChanged() {
        art.setSelected(focused);
        block.setFocused(focused);
        float scale = focused ? 1.05f : 1f;
        tilt.animate().scaleX(scale).scaleY(scale).setDuration(320).start();
    }

    @Override
    protected void onStart() {
        stopAmbient();
        ValueAnimator wobble = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        wobble.setDuration(12000);
        wobble.setRepeatCount(ValueAnimator.INFINITE);
        wobble.setInterpolator(new LinearInterpolator());
        wobble.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            tilt.setRotationY(6f * (float) Math.sin(t));
            tilt.setRotationX(2.5f * (float) Math.cos(t));
        });
        ambient.add(wobble);
        for (int i = 0; i < shards.size(); i++) {
            View shard = shards.get(i);
            float r = shardRotations.get(i);
            ObjectAnimator f = ObjectAnimator.ofPropertyValuesHolder(shard,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -dp(5), dp(5)),
                    PropertyValuesHolder.ofFloat(View.ROTATION, r - 4f, r + 4f));
            f.setDuration(4200 + i * 650L);
            f.setRepeatMode(ValueAnimator.REVERSE);
            f.setRepeatCount(ValueAnimator.INFINITE);
            f.setInterpolator(new AccelerateDecelerateInterpolator());
            ambient.add(f);
        }
        for (Animator animator : ambient) animator.start();
    }

    @Override
    protected void onStop() {
        stopAmbient();
    }

    private void stopAmbient() {
        for (Animator animator : ambient) animator.cancel();
        ambient.clear();
    }
}
