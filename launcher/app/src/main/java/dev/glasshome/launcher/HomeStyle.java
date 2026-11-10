package dev.glasshome.launcher;

import android.content.Context;

/** The home screen styles that can be chosen with the remote's Menu button. */
final class HomeStyle {
    static final String CLASSIC = "classic";
    static final String RING = "ring";
    static final String DEPTH = "depth";
    static final String BREAKOUT = "breakout";
    static final String MOSAIC = "mosaic";

    static final String[] ALL = {CLASSIC, RING, DEPTH, BREAKOUT, MOSAIC};

    private HomeStyle() {
    }

    static String normalize(String style) {
        for (String known : ALL) {
            if (known.equals(style)) return known;
        }
        return CLASSIC;
    }

    static int label(String style) {
        switch (style) {
            case RING:
                return R.string.style_ring;
            case DEPTH:
                return R.string.style_depth;
            case BREAKOUT:
                return R.string.style_breakout;
            case MOSAIC:
                return R.string.style_mosaic;
            default:
                return R.string.style_classic;
        }
    }

    /** Creates the featured-title view for a style, or null for the classic home. */
    static HeroView create(Context context, String style) {
        switch (style) {
            case RING:
                return new RingHero(context);
            case DEPTH:
                return new DepthHero(context);
            case BREAKOUT:
                return new BreakoutHero(context);
            case MOSAIC:
                return new MosaicHero(context);
            default:
                return null;
        }
    }
}
