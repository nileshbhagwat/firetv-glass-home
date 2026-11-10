package dev.glasshome.launcher;

import android.graphics.RectF;

/**
 * One featured title prepared on the laptop by the artwork script (docker/featured).
 * Rectangles are fractions of their frame: the card frame for {@link #cardCharRect} (a negative
 * top means the character rises above the card) and the full screen for {@link #fullCharRect}.
 */
final class FeaturedItem {
    final String id;
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

    FeaturedItem(String id, String title, String provider, String packageName, int accent,
                 String card, String cardChar, RectF cardCharRect,
                 String full, String fullChar, RectF fullCharRect) {
        this.id = id;
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

    boolean hasCardCharacter() {
        return cardChar != null && cardCharRect != null;
    }

    boolean hasFullCharacter() {
        return fullChar != null && fullCharRect != null;
    }
}
