package dev.glasshome.launcher;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * One featured title as a full-screen scene for the borderless styles: the softened artwork,
 * with the styles' shading drawn in ({@link Shading}), fills the screen and the cut-out
 * character stands on top of it, exactly where it is in the picture. Styles may put their own
 * layers between the two ({@link #addBehindCharacter}).
 */
final class SceneLayer extends FrameLayout {
    final ImageView backdrop;
    final ImageView character;
    private final int width;
    private final int height;
    FeaturedItem item;

    SceneLayer(HeroView hero, int width, int height) {
        super(hero.getContext());
        this.width = width;
        this.height = height;
        setClipChildren(false);
        backdrop = new ImageView(getContext());
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(backdrop, new LayoutParams(width, height));
        character = hero.characterView();
        addView(character, new LayoutParams(1, 1));
        setPivotX(width / 2f);
        setPivotY(height / 2f);
    }

    /** Adds a layer above the artwork and below the character. */
    void addBehindCharacter(View view, LayoutParams params) {
        addView(view, indexOfChild(character), params);
    }

    /** True when the title has a cut-out character. */
    boolean hasCharacter() {
        return item != null && item.hasFullCharacter();
    }

    /** The character's upper body (or the middle of the picture) in layer pixels: {x, y}. */
    float[] focusPoint() {
        if (!hasCharacter()) return new float[]{width / 2f, height * 0.4f};
        return new float[]{item.fullCharRect.centerX() * width,
                (item.fullCharRect.top + item.fullCharRect.height() * 0.35f) * height};
    }

    /** Loads a title; {@code done} runs once its pictures are on screen. */
    void show(HeroView hero, FeaturedItem item, Runnable done) {
        this.item = item;
        String full = item.full != null ? item.full : item.card;
        final int[] pending = {item.hasFullCharacter() ? 2 : 1};
        Runnable loaded = () -> {
            if (--pending[0] == 0 && done != null) done.run();
        };
        hero.loadSceneInto(backdrop, full, width, height, loaded);
        if (item.hasFullCharacter()) {
            character.setVisibility(VISIBLE);
            HeroView.place(character, item.fullCharRect, width, height);
            hero.loadInto(character, item.fullChar, character.getLayoutParams().width,
                    character.getLayoutParams().height, true, loaded);
        } else {
            hero.loadInto(character, null, 0, 0, true, null);
            character.setVisibility(GONE);
        }
    }

    /** Back to rest: no zoom, offset or fade left over from a previous title. */
    void reset() {
        animate().cancel();
        character.animate().cancel();
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
        character.setScaleX(1f);
        character.setScaleY(1f);
        character.setTranslationX(0f);
        character.setTranslationY(0f);
        character.setAlpha(1f);
    }
}
