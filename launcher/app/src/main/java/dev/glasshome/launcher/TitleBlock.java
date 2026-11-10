package dev.glasshome.launcher;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Caption, title and "Press OK" hint for the featured title, shared by the hero styles. */
final class TitleBlock extends LinearLayout {
    private final TextView caption;
    private final TextView title;
    private final TextView hint;
    private FeaturedItem shown;

    TitleBlock(Context context, float titleSp, int gravity, int maxWidthPx) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(gravity);
        float density = getResources().getDisplayMetrics().density;
        caption = text(context, 14, 400, R.color.text_secondary, density);
        title = text(context, titleSp, 300, R.color.text_primary, density);
        title.setMaxWidth(maxWidthPx);
        title.setEllipsize(TextUtils.TruncateAt.END);
        hint = text(context, 14, 400, R.color.text_secondary, density);
        hint.setAlpha(0f);
        addView(caption);
        addView(title);
        addView(hint);
        ((LayoutParams) title.getLayoutParams()).topMargin = Math.round(2 * density);
        ((LayoutParams) hint.getLayoutParams()).topMargin = Math.round(6 * density);
    }

    void set(FeaturedItem item, boolean animate) {
        if (item == shown) return;
        shown = item;
        if (!animate) {
            apply(item);
            return;
        }
        animate().cancel();
        animate().alpha(0f).setDuration(140).withEndAction(() -> {
            apply(item);
            animate().alpha(1f).setDuration(320).start();
        }).start();
    }

    void setFocused(boolean focused) {
        hint.animate().alpha(focused ? 1f : 0f).setDuration(200).start();
    }

    private void apply(FeaturedItem item) {
        Context c = getContext();
        if (item == null) {
            caption.setText("");
            title.setText("");
            hint.setText("");
            return;
        }
        caption.setText(item.provider == null
                ? c.getString(R.string.featured_trending)
                : c.getString(R.string.featured_caption, item.provider));
        title.setText(item.title);
        hint.setText(item.provider == null
                ? c.getString(R.string.featured_hint_generic)
                : c.getString(R.string.featured_hint, item.provider));
    }

    private static TextView text(Context context, float sp, int weight, int color, float density) {
        TextView view = new TextView(context);
        view.setTextSize(sp);
        view.setTypeface(Fonts.outfit(context, weight));
        view.setTextColor(context.getColor(color));
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setShadowLayer(12 * density, 0, density, 0xB3000000);
        view.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return view;
    }
}
