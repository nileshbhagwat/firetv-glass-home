package dev.glasshome.launcher;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Depth clock: the featured artwork fills the top of the screen and its character stands in
 * front of the large clock (and behind the dock), like a depth-effect lock screen. The three
 * layers drift by different amounts for a gentle parallax.
 */
final class DepthHero extends HeroView {
    private final int width;
    private final int height;
    private final List<Animator> ambient = new ArrayList<>();
    private FrameLayout scene;
    private ImageView backdrop;
    private ImageView character;
    private LinearLayout clockGroup;
    private TextView greeting;
    private TitleBlock block;
    private CharSequence greetingText = "";

    DepthHero(Context context) {
        super(context);
        width = dp(960);
        height = dp(540);
    }

    @Override
    boolean replacesClock() {
        return true;
    }

    @Override
    void setGreeting(CharSequence text) {
        greetingText = text;
        if (greeting != null) greeting.setText(text);
    }

    @Override
    protected void onBind() {
        Context c = getContext();
        scene = new FrameLayout(c);
        scene.setClipChildren(false);
        scene.setScaleX(1.06f);
        scene.setScaleY(1.06f);
        addView(scene, at(0, 0, width, height));

        backdrop = new ImageView(c);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        scene.addView(backdrop, at(0, 0, width, height));

        clockGroup = new LinearLayout(c);
        clockGroup.setOrientation(LinearLayout.VERTICAL);
        greeting = label(c, 16, 400, c.getColor(R.color.text_secondary));
        greeting.setText(greetingText);
        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        styleText(clock, 132, 200, 0xF2FFFFFF);
        // No date line here: it would sit half behind the character.
        clockGroup.addView(greeting);
        clockGroup.addView(clock);
        scene.addView(clockGroup, at(dp(70), dp(86), LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        character = characterView();
        scene.addView(character, at(0, 0, 1, 1));

        View fade = new View(c);
        fade.setBackgroundResource(R.drawable.hero_fade);
        addView(fade, at(0, 0, width, dp(450)));
        View floor = new View(c);
        floor.setBackgroundColor(Color.BLACK);
        addView(floor, at(0, dp(450), width, height - dp(450)));

        block = new TitleBlock(c, 34, Gravity.START, dp(520));
        addView(block, at(dp(70), dp(262), LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        show(current(), false);
    }

    @Override
    protected void onStep(int delta) {
        character.animate().alpha(0f).translationY(dp(14)).setDuration(180).start();
        backdrop.animate().alpha(0.15f).setDuration(220).withEndAction(() -> show(current(), true)).start();
    }

    @Override
    protected void onFocusChanged() {
        block.setFocused(focused);
        float scale = focused ? 1.08f : 1.06f;
        scene.animate().scaleX(scale).scaleY(scale).setDuration(420).start();
    }

    @Override
    protected void onStart() {
        stopAmbient();
        // Background, clock and character drift together but by different amounts: parallax.
        ambient.add(drift(scene, dp(8), 18000));
        ambient.add(drift(clockGroup, dp(2), 18000));
        ambient.add(drift(character, dp(4), 18000));
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

    private static ObjectAnimator drift(View view, float distance, long duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -distance, distance);
        animator.setDuration(duration);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        return animator;
    }

    private void show(FeaturedItem item, boolean animate) {
        if (item == null) return;
        block.set(item, animate);
        String full = item.full != null ? item.full : item.card;
        loadInto(backdrop, full, width, height, false, () -> {
            if (animate) {
                backdrop.setAlpha(0f);
                backdrop.animate().alpha(1f).setDuration(520).start();
            } else {
                backdrop.setAlpha(1f);
            }
        });
        if (!item.hasFullCharacter()) {
            loadInto(character, null, 0, 0, true, null);
            character.setVisibility(GONE);
            placeClock(item, null, animate);
            return;
        }
        character.setVisibility(VISIBLE);
        place(character, item.fullCharRect, width, height);
        loadInto(character, item.fullChar, character.getLayoutParams().width,
                character.getLayoutParams().height, true, () -> {
                    Bitmap cutout = character.getDrawable() instanceof BitmapDrawable
                            ? ((BitmapDrawable) character.getDrawable()).getBitmap() : null;
                    placeClock(item, cutout, animate);
                    if (animate) {
                        character.setAlpha(0f);
                        character.setTranslationY(dp(26));
                        character.animate().alpha(1f).translationY(0f).setStartDelay(160).setDuration(640)
                                .setInterpolator(new OvershootInterpolator(1.1f)).start();
                    } else {
                        character.setAlpha(1f);
                        character.setTranslationY(0f);
                    }
                });
    }

    /**
     * Puts the clock beside the character at the clock's height, overlapping its outline by
     * about a fifth of the clock's width: the character covers one edge of the time, never the
     * middle of it.
     */
    private void placeClock(FeaturedItem item, Bitmap cutout, boolean animate) {
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        clockGroup.measure(unspecified, unspecified);
        int w = clockGroup.getMeasuredWidth();
        float target = dp(70);
        if (cutout != null && item.hasFullCharacter()) {
            RectF r = item.fullCharRect;
            float charLeft = r.left * width, charTop = r.top * height;
            float charWidth = r.width() * width, charHeight = r.height() * height;
            float minX = Float.MAX_VALUE, maxX = -1f;
            for (int y = dp(110); y <= dp(210); y += dp(10)) {
                float fy = (y - charTop) / charHeight;
                if (fy < 0f || fy >= 1f) continue;
                int by = Math.min(cutout.getHeight() - 1, (int) (fy * cutout.getHeight()));
                for (int bx = 0; bx < cutout.getWidth(); bx += 2) {
                    if (Color.alpha(cutout.getPixel(bx, by)) > 160) {
                        float x = charLeft + bx * charWidth / cutout.getWidth();
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                    }
                }
            }
            if (maxX >= 0f) {
                float overlap = w * 0.22f;
                float spaceLeft = minX - dp(70);
                float spaceRight = width - dp(70) - maxX;
                target = spaceRight >= spaceLeft ? maxX - overlap : minX - w + overlap;
            } else {
                target = r.centerX() * width - w / 2f;
            }
        }
        int left = Math.round(Math.max(dp(70), Math.min(width - dp(70) - w, target)));
        Runnable apply = () -> {
            LayoutParams lp = (LayoutParams) clockGroup.getLayoutParams();
            lp.leftMargin = left;
            clockGroup.setLayoutParams(lp);
        };
        if (!animate) {
            apply.run();
            return;
        }
        clockGroup.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            apply.run();
            clockGroup.animate().alpha(1f).setDuration(420).start();
        }).start();
    }

    private TextView label(Context c, float sp, int weight, int color) {
        TextView view = new TextView(c);
        styleText(view, sp, weight, color);
        return view;
    }

    private void styleText(TextView view, float sp, int weight, int color) {
        view.setTextSize(sp);
        view.setTypeface(Fonts.outfit(getContext(), weight));
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setShadowLayer(14 * density, 0, density, 0x99000000);
    }
}
