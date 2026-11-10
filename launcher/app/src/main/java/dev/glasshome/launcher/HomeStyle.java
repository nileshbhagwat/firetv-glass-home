package dev.glasshome.launcher;

import android.content.Context;

/** The home screen styles that can be chosen with the remote's Menu button. */
final class HomeStyle {
    static final String CLASSIC = "classic";
    static final String RING = "ring";
    static final String DEPTH = "depth";
    static final String BREAKOUT = "breakout";
    static final String MOSAIC = "mosaic";
    static final String CINEMA = "cinema";
    static final String SPOTLIGHT = "spotlight";
    static final String AURA = "aura";

    static final String[] ALL = {CLASSIC, RING, DEPTH, BREAKOUT, MOSAIC, CINEMA, SPOTLIGHT, AURA};

    private HomeStyle() {
    }

    static String normalize(String style) {
        for (String known : ALL) {
            if (known.equals(style)) return known;
        }
        return CLASSIC;
    }

    /**
     * True for gallery styles, which show several titles at once and mix films with series.
     * The other styles show one title at a time and use films.
     */
    static boolean showsSeries(String style) {
        return RING.equals(style);
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
            case CINEMA:
                return R.string.style_cinema;
            case SPOTLIGHT:
                return R.string.style_spotlight;
            case AURA:
                return R.string.style_aura;
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
            case CINEMA:
                return new CinemaHero(context);
            case SPOTLIGHT:
                return new SpotlightHero(context);
            case AURA:
                return new AuraHero(context);
            default:
                return null;
        }
    }
}
