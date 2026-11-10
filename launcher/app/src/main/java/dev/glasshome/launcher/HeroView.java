package dev.glasshome.launcher;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Base for the featured-title styles. A hero fills the home screen behind the dock (the page
 * is laid out for 960 x 540 dp) and shows one featured title at a time; MainActivity moves
 * between titles with {@link #step(int)} and pauses all motion with {@link #stop()} whenever
 * the home screen is not in front.
 */
abstract class HeroView extends FrameLayout {
    interface Listener {
        void onFeaturedChanged(FeaturedItem item);
    }

    protected final float density;
    protected final List<FeaturedItem> items = new ArrayList<>();
    protected FeaturedStore store;
    protected int index;
    protected boolean focused;
    protected boolean running;
    private Listener listener;

    HeroView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Shows the given titles, starting at the one with {@code preferredId} when present. */
    final void bind(FeaturedStore store, List<FeaturedItem> list, String preferredId) {
        this.store = store;
        items.clear();
        items.addAll(list);
        index = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(preferredId)) index = i;
        }
        removeAllViews();
        if (!items.isEmpty()) {
            onBind();
            if (focused) onFocusChanged();
            if (running) onStart();
        }
        notifyChanged();
    }

    final boolean isEmpty() {
        return items.isEmpty();
    }

    final FeaturedItem current() {
        return items.isEmpty() ? null : items.get(index);
    }

    /** Moves to the next (+1) or previous (-1) title. */
    final void step(int delta) {
        if (items.size() < 2) return;
        index = Math.floorMod(index + delta, items.size());
        onStep(delta);
        notifyChanged();
    }

    final void setFocused(boolean value) {
        if (focused == value) return;
        focused = value;
        if (!items.isEmpty()) onFocusChanged();
    }

    final void start() {
        if (running) return;
        running = true;
        if (!items.isEmpty()) onStart();
    }

    final void stop() {
        if (!running) return;
        running = false;
        onStop();
    }

    /** True when the style draws its own clock, so the standard one is hidden. */
    boolean replacesClock() {
        return false;
    }

    /**
     * True when the style's artwork covers the whole screen. The home screen then stops
     * painting the soft background glow behind it, which nobody would see.
     */
    boolean coversScreen() {
        return false;
    }

    /** Greeting text for styles that draw their own clock. */
    void setGreeting(CharSequence text) {
    }

    protected abstract void onBind();

    protected abstract void onStep(int delta);

    protected void onFocusChanged() {
    }

    protected void onStart() {
    }

    protected void onStop() {
    }

    protected final int dp(float value) {
        return Math.round(value * density);
    }

    /** Loads an image into {@code view}; late results for an image no longer wanted are ignored. */
    protected final void loadInto(ImageView view, String file, int maxWidth, int maxHeight, boolean alpha, Runnable done) {
        view.setTag(R.id.featured_file, file);
        if (file == null) {
            view.setImageDrawable(null);
            if (done != null) done.run();
            return;
        }
        store.bitmap(file, maxWidth, maxHeight, alpha, bitmap -> {
            if (!file.equals(view.getTag(R.id.featured_file))) return;
            view.setImageBitmap(bitmap);
            if (done != null) done.run();
        });
    }

    /** Like {@link #loadInto}, for a full-screen picture with the shading drawn in. */
    protected final void loadSceneInto(ImageView view, String file, int width, int height, Runnable done) {
        view.setTag(R.id.featured_file, file);
        if (file == null) {
            view.setImageDrawable(null);
            if (done != null) done.run();
            return;
        }
        store.scene(file, width, height, bitmap -> {
            if (!file.equals(view.getTag(R.id.featured_file))) return;
            view.setImageBitmap(bitmap);
            if (done != null) done.run();
        });
    }

    /** A rounded artwork view with the glass edge and light sheen used by every style. */
    protected final ImageView artView() {
        ImageView view = new ImageView(getContext());
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setBackgroundResource(R.drawable.tile_bg);
        view.setClipToOutline(true);
        view.setForeground(getContext().getDrawable(R.drawable.glass_card_fg));
        return view;
    }

    protected final ImageView characterView() {
        ImageView view = new ImageView(getContext());
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    /** Positions {@code view} in a frame of the given size from a rectangle in frame fractions. */
    protected static void place(View view, RectF rect, int frameWidth, int frameHeight) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.max(1, Math.round(rect.width() * frameWidth)),
                Math.max(1, Math.round(rect.height() * frameHeight)));
        lp.leftMargin = Math.round(rect.left * frameWidth);
        lp.topMargin = Math.round(rect.top * frameHeight);
        view.setLayoutParams(lp);
    }

    protected static FrameLayout.LayoutParams at(int left, int top, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
        lp.leftMargin = left;
        lp.topMargin = top;
        return lp;
    }

    private void notifyChanged() {
        if (listener != null) listener.onFeaturedChanged(current());
    }
}
