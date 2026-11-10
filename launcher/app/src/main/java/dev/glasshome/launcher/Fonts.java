package dev.glasshome.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

/** Outfit variable font at specific weights, cached per weight. */
final class Fonts {
    private static final SparseArray<Typeface> CACHE = new SparseArray<>();

    private Fonts() {
    }

    static Typeface outfit(Context context, int weight) {
        Typeface cached = CACHE.get(weight);
        if (cached != null) {
            return cached;
        }
        Typeface typeface = null;
        try {
            typeface = new Typeface.Builder(context.getAssets(), "fonts/Outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .setWeight(weight)
                    .build();
        } catch (RuntimeException ignored) {
            // Fall back to the system font below.
        }
        if (typeface == null) {
            typeface = Typeface.create(Typeface.SANS_SERIF, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        }
        CACHE.put(weight, typeface);
        return typeface;
    }
}
