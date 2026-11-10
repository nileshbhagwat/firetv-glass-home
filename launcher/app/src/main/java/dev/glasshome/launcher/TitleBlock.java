package dev.glasshome.launcher;

import android.content.Context;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Caption and title for the featured title, shared by the hero styles. When the title has
 * focus the caption turns into the "Press OK" hint, so the block never grows towards the dock.
 */
final class TitleBlock extends LinearLayout {
    private final TextView caption;
    private final TextView title;
    private FeaturedItem shown;
    private boolean focused;

    TitleBlock(Context context, float titleSp, int gravity, int maxWidthPx) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(gravity);
        float density = getResources().getDisplayMetrics().density;
        caption = text(context, 14, 400, density);
        title = text(context, titleSp, 300, density);
        title.setTextColor(context.getColor(R.color.text_primary));
        title.setMaxWidth(maxWidthPx);
        title.setEllipsize(TextUtils.TruncateAt.END);
        addView(caption);
        addView(title);
        ((LayoutParams) title.getLayoutParams()).topMargin = Math.round(4 * density);
        updateCaption();
    }

    void set(FeaturedItem item, boolean animate) {
        if (item == shown) return;
        shown = item;
        if (!animate) {
            apply();
            return;
        }
        animate().cancel();
        animate().alpha(0f).setDuration(140).withEndAction(() -> {
            apply();
            animate().alpha(1f).setDuration(320).start();
        }).start();
    }

    void setFocused(boolean value) {
        if (focused == value) return;
        focused = value;
        caption.animate().cancel();
        caption.animate().alpha(0f).setDuration(110).withEndAction(() -> {
            updateCaption();
            caption.animate().alpha(1f).setDuration(220).start();
        }).start();
    }

    private void apply() {
        title.setText(shown == null ? "" : shown.title);
        updateCaption();
    }

    private void updateCaption() {
        Context c = getContext();
        caption.setTextColor(c.getColor(focused ? R.color.text_primary : R.color.text_secondary));
        if (shown == null) {
            caption.setText("");
        } else if (focused) {
            caption.setText(shown.provider == null
                    ? c.getString(R.string.featured_hint_generic)
                    : c.getString(R.string.featured_hint, shown.provider));
        } else {
            caption.setText(shown.provider == null
                    ? c.getString(R.string.featured_trending)
                    : c.getString(R.string.featured_caption, shown.provider));
        }
    }

    private static TextView text(Context context, float sp, int weight, float density) {
        TextView view = new TextView(context);
        view.setTextSize(sp);
        view.setTypeface(Fonts.outfit(context, weight));
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setShadowLayer(12 * density, 0, density, 0xB3000000);
        view.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return view;
    }
}
