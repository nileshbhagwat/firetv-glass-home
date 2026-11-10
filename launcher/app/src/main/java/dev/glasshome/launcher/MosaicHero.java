package dev.glasshome.launcher;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
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
 * Glass mosaic: the artwork is split into glass tiles that sit at slightly different angles and
 * slowly breathe; the character stands whole in front of them. Changing title flips the tiles
 * over in a wave.
 */
final class MosaicHero extends HeroView {
    private static final int COLUMNS = 5;
    private static final int ROWS = 3;

    private final int frameWidth;
    private final int frameHeight;
    private final int left;
    private final int top;
    private final int gap;
    private final List<View> tiles = new ArrayList<>();
    private final List<ImageView> pieces = new ArrayList<>();
    private final List<Animator> ambient = new ArrayList<>();
    private FrameLayout frame;
    private ImageView character;
    private TitleBlock block;
    private Runnable pendingFlip;

    MosaicHero(Context context) {
        super(context);
        // Low and small enough that a character rising out of it stays below the top pills.
        frameWidth = dp(302);
        frameHeight = dp(170);
        left = dp(588);
        top = dp(166);
        gap = dp(6);
    }

    @Override
    protected void onBind() {
        Context c = getContext();
        tiles.clear();
        pieces.clear();
        frame = new FrameLayout(c);
        frame.setClipChildren(false);
        addView(frame, at(left, top, frameWidth, frameHeight));
        int tileWidth = (frameWidth - gap * (COLUMNS - 1)) / COLUMNS;
        int tileHeight = (frameHeight - gap * (ROWS - 1)) / ROWS;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int x = col * (tileWidth + gap);
                int y = row * (tileHeight + gap);
                FrameLayout tile = new FrameLayout(c);
                tile.setBackgroundResource(R.drawable.glass_tile_bg);
                tile.setClipToOutline(true);
                tile.setForeground(c.getDrawable(R.drawable.glass_tile_fg));
                tile.setCameraDistance(9000 * density);
                tile.setRotationX(baseRotationX(row));
                tile.setRotationY(baseRotationY(col));
                ImageView piece = new ImageView(c);
                piece.setScaleType(ImageView.ScaleType.CENTER_CROP);
                piece.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(piece, at(-x, -y, frameWidth, frameHeight));
                frame.addView(tile, at(x, y, tileWidth, tileHeight));
                tiles.add(tile);
                pieces.add(piece);
            }
        }
        character = characterView();
        frame.addView(character, at(0, 0, 1, 1));
        block = new TitleBlock(c, 34, Gravity.START, dp(440));
        addView(block, at(dp(70), dp(262), LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        block.set(current(), false);
        show(current(), false);
    }

    private static float baseRotationX(int row) {
        return (row - 1) * -5f;
    }

    private static float baseRotationY(int col) {
        return (col - 2) * 5f;
    }

    /** Loads the artwork once and shows it in every tile, then the character. */
    private void show(FeaturedItem item, boolean animate) {
        if (item == null) return;
        frame.setContentDescription(item.title);
        store.bitmap(item.card, frameWidth, frameHeight, false, bitmap -> {
            if (item != current()) return;
            setPieces(bitmap);
        });
        if (!item.hasCardCharacter()) {
            loadInto(character, null, 0, 0, true, null);
            character.setVisibility(GONE);
            return;
        }
        character.setVisibility(VISIBLE);
        place(character, item.cardCharRect, frameWidth, frameHeight);
        loadInto(character, item.cardChar, character.getLayoutParams().width,
                character.getLayoutParams().height, true, () -> {
                    if (animate) {
                        character.setAlpha(0f);
                        character.setTranslationY(dp(22));
                        character.animate().alpha(1f).translationY(0f).setStartDelay(260).setDuration(600)
                                .setInterpolator(new OvershootInterpolator(1.2f)).start();
                    } else {
                        character.setAlpha(1f);
                        character.setTranslationY(0f);
                    }
                });
    }

    private void setPieces(Bitmap bitmap) {
        for (ImageView piece : pieces) piece.setImageBitmap(bitmap);
    }

    @Override
    protected void onStep(int delta) {
        block.set(current(), true);
        if (pendingFlip != null) removeCallbacks(pendingFlip);
        character.animate().alpha(0f).translationY(dp(16)).setDuration(160).start();
        long last = 0;
        for (int i = 0; i < tiles.size(); i++) {
            int row = i / COLUMNS, col = i % COLUMNS;
            long delay = (col + row) * 50L;
            last = Math.max(last, delay);
            View tile = tiles.get(i);
            tile.animate().cancel();
            tile.animate().rotationX(baseRotationX(row) + 90f).setStartDelay(delay).setDuration(170)
                    .setInterpolator(new AccelerateInterpolator()).start();
        }
        pendingFlip = () -> {
            pendingFlip = null;
            show(current(), true);
            for (int i = 0; i < tiles.size(); i++) {
                int row = i / COLUMNS, col = i % COLUMNS;
                View tile = tiles.get(i);
                tile.setRotationX(baseRotationX(row) - 90f);
                tile.animate().rotationX(baseRotationX(row)).setStartDelay((col + row) * 50L).setDuration(420)
                        .setInterpolator(new OvershootInterpolator(1.4f)).start();
            }
        };
        postDelayed(pendingFlip, last + 190);
    }

    @Override
    protected void onFocusChanged() {
        block.setFocused(focused);
        float scale = focused ? 1.04f : 1f;
        frame.animate().scaleX(scale).scaleY(scale).setDuration(320).start();
    }

    @Override
    protected void onStart() {
        stopAmbient();
        ValueAnimator breathe = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        breathe.setDuration(14000);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.setInterpolator(new LinearInterpolator());
        breathe.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            for (int i = 0; i < tiles.size(); i++) {
                int row = i / COLUMNS, col = i % COLUMNS;
                float phase = col * 0.6f + row * 0.9f;
                tiles.get(i).setRotationY(baseRotationY(col) + 7f * (float) Math.sin(t + phase));
            }
        });
        ambient.add(breathe);
        ObjectAnimator sway = ObjectAnimator.ofFloat(character, View.TRANSLATION_X, -dp(2), dp(2));
        sway.setDuration(6500);
        sway.setRepeatMode(ValueAnimator.REVERSE);
        sway.setRepeatCount(ValueAnimator.INFINITE);
        sway.setInterpolator(new AccelerateDecelerateInterpolator());
        ambient.add(sway);
        for (Animator animator : ambient) animator.start();
    }

    @Override
    protected void onStop() {
        stopAmbient();
        if (pendingFlip != null) {
            removeCallbacks(pendingFlip);
            pendingFlip.run();
        }
    }

    private void stopAmbient() {
        for (Animator animator : ambient) animator.cancel();
        ambient.clear();
    }
}
