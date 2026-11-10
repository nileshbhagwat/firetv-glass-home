package dev.glasshome.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Gallery ring: the featured titles stand on a slowly turning 3D ring of glass cards. Each
 * card's character rises above its frame. Left/Right (or the automatic advance) turns the ring.
 */
final class RingHero extends HeroView {
    private static final float STEP_DEGREES = 40f;
    private static final float MAX_VISIBLE_DEGREES = 100f;

    private final List<View> cards = new ArrayList<>();
    private final List<ImageView> arts = new ArrayList<>();
    private final int cardWidth;
    private final int cardHeight;
    private final int centerX;
    private final int centerY;
    private final int radius;
    private final int perspective;
    private TitleBlock block;
    private float position;
    private float target;
    private ValueAnimator spin;

    RingHero(Context context) {
        super(context);
        cardWidth = dp(212);
        cardHeight = dp(119);
        // Right of centre and high enough that the clock, the title and the dock all stay clear.
        centerX = dp(548);
        centerY = dp(206);
        radius = dp(300);
        perspective = dp(1100);
    }

    @Override
    protected void onBind() {
        cards.clear();
        arts.clear();
        FrameLayout stage = new FrameLayout(getContext());
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        addView(stage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        for (FeaturedItem item : items) {
            FrameLayout card = new FrameLayout(getContext());
            card.setClipChildren(false);
            card.setClipToPadding(false);
            card.setOutlineProvider(null); // translationZ is used only for drawing order
            card.setCameraDistance(12000 * density);
            ImageView art = artView();
            art.setContentDescription(item.title);
            card.addView(art, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            loadInto(art, item.card, cardWidth, cardHeight, false, null);
            if (item.hasCardCharacter()) {
                ImageView character = characterView();
                place(character, item.cardCharRect, cardWidth, cardHeight);
                card.addView(character);
                loadInto(character, item.cardChar, character.getLayoutParams().width,
                        character.getLayoutParams().height, true, null);
            }
            stage.addView(card, at(centerX - cardWidth / 2, centerY - cardHeight / 2, cardWidth, cardHeight));
            cards.add(card);
            arts.add(art);
        }
        block = new TitleBlock(getContext(), 26, Gravity.CENTER_HORIZONTAL, dp(560));
        FrameLayout centred = new FrameLayout(getContext());
        centred.addView(block, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        addView(centred, at(centerX - dp(300), dp(280), dp(600), LayoutParams.WRAP_CONTENT));
        block.set(current(), false);
        position = index;
        target = index;
        layoutCards();
        updateSelection();
    }

    @Override
    protected void onStep(int delta) {
        target += delta;
        if (spin != null) spin.cancel();
        spin = ValueAnimator.ofFloat(position, target);
        spin.setDuration(760);
        spin.setInterpolator(new DecelerateInterpolator(2.2f));
        spin.addUpdateListener(a -> {
            position = (float) a.getAnimatedValue();
            layoutCards();
        });
        spin.start();
        block.set(current(), true);
        updateSelection();
    }

    @Override
    protected void onFocusChanged() {
        layoutCards();
        updateSelection();
        block.setFocused(focused);
    }

    @Override
    protected void onStop() {
        if (spin != null) spin.end();
    }

    private void updateSelection() {
        for (int i = 0; i < arts.size(); i++) {
            arts.get(i).setSelected(focused && i == index);
        }
    }

    /** Places every card on the ring for the current (fractional) position. */
    private void layoutCards() {
        int n = cards.size();
        for (int i = 0; i < n; i++) {
            float d = i - position;
            d -= n * Math.round(d / n); // shortest way round the ring
            float theta = d * STEP_DEGREES;
            View card = cards.get(i);
            if (Math.abs(theta) > MAX_VISIBLE_DEGREES) {
                card.setVisibility(INVISIBLE);
                continue;
            }
            card.setVisibility(VISIBLE);
            double rad = Math.toRadians(theta);
            float x = (float) (radius * Math.sin(rad));
            float z = (float) (radius * (Math.cos(rad) - 1)); // 0 at the front, negative behind
            float scale = perspective / (perspective - z);
            float front = Math.max(0f, 1f - Math.abs(d));
            float lift = 1f + (focused ? 0.07f : 0.03f) * front;
            card.setTranslationX(x * scale);
            card.setTranslationY(z * 0.1f);
            card.setScaleX(scale * lift);
            card.setScaleY(scale * lift);
            card.setRotationY(-theta * 0.85f);
            card.setAlpha(Math.max(0f, Math.min(1f, 1f - Math.abs(d) * 0.3f)));
            card.setTranslationZ(z + front);
        }
    }
}
