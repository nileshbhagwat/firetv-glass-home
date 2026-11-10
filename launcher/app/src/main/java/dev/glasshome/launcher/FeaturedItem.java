package dev.glasshome.launcher;

import android.graphics.RectF;

/**
 * One featured title prepared on the laptop by the artwork script (docker/featured).
 * Rectangles are fractions of their frame: the card frame for {@link #cardCharRect} (a negative
 * top means the character rises above the card) and the full screen for {@link #fullCharRect}.
 */
final class FeaturedItem {
    static final String KIND_FILM = "movie";
    static final String KIND_SERIES = "tv";

    final String id;
    /** {@link #KIND_FILM} or {@link #KIND_SERIES}. */
    final String kind;
    final String title;
    final String provider;
    final String packageName;
    final int accent;
    final String card;
    final String cardChar;
    final RectF cardCharRect;
    final String full;
    final String fullChar;
    final RectF fullCharRect;

    FeaturedItem(String id, String kind, String title, String provider, String packageName, int accent,
                 String card, String cardChar, RectF cardCharRect,
                 String full, String fullChar, RectF fullCharRect) {
        this.id = id;
        this.kind = KIND_SERIES.equals(kind) ? KIND_SERIES : KIND_FILM;
        this.title = title;
        this.provider = provider;
        this.packageName = packageName;
        this.accent = accent;
        this.card = card;
        this.cardChar = cardChar;
        this.cardCharRect = cardCharRect;
        this.full = full;
        this.fullChar = fullChar;
        this.fullCharRect = fullCharRect;
    }

    boolean isSeries() {
        return KIND_SERIES.equals(kind);
    }

    boolean hasCardCharacter() {
        return cardChar != null && cardCharRect != null;
    }

    boolean hasFullCharacter() {
        return fullChar != null && fullCharRect != null;
    }
}
