package dev.glasshome.launcher;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;

/** Binds an app to a tile and animates its focused state (slight scale-up, name underneath). */
final class TileViews {
    static final float FOCUS_SCALE = 1.08f;
    private static final Interpolator EASE = new DecelerateInterpolator(2f);

    private TileViews() {
    }

    static View card(View tile) {
        return tile.findViewById(R.id.card);
    }

    static void bind(View tile, AppEntry app) {
        View card = card(tile);
        card.setClipToOutline(true);
        ImageView banner = tile.findViewById(R.id.banner);
        ImageView icon = tile.findViewById(R.id.icon);
        if (app.banner != null) {
            banner.setImageDrawable(app.banner);
            banner.setVisibility(View.VISIBLE);
            icon.setImageDrawable(null);
            icon.setVisibility(View.GONE);
        } else {
            banner.setImageDrawable(null);
            banner.setVisibility(View.GONE);
            icon.setImageDrawable(app.icon);
            icon.setVisibility(View.VISIBLE);
        }
        TextView label = tile.findViewById(R.id.label);
        label.setText(app.label);
        label.setTypeface(Fonts.outfit(tile.getContext(), 400));
        tile.setTag(R.id.tile_selected, null);
    }

    static void setSelected(View tile, boolean selected, boolean animate) {
        Object previous = tile.getTag(R.id.tile_selected);
        if (animate && previous instanceof Boolean && (Boolean) previous == selected) {
            return;
        }
        tile.setTag(R.id.tile_selected, selected);
        View card = card(tile);
        View label = tile.findViewById(R.id.label);
        float scale = selected ? FOCUS_SCALE : 1f;
        float alpha = selected ? 1f : 0f;
        float shift = selected ? 0f : -4f * tile.getResources().getDisplayMetrics().density;
        card.animate().cancel();
        label.animate().cancel();
        if (animate) {
            card.animate().scaleX(scale).scaleY(scale).setDuration(240).setInterpolator(EASE).start();
            label.animate().alpha(alpha).translationY(shift).setDuration(200).setInterpolator(EASE).start();
        } else {
            card.setScaleX(scale);
            card.setScaleY(scale);
            label.setAlpha(alpha);
            label.setTranslationY(shift);
        }
    }
}
