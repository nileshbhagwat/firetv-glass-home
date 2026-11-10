package dev.glasshome.launcher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** A launchable app as shown on a tile. */
final class AppEntry {
    final String packageName;
    final Intent launchIntent;
    final CharSequence label;
    final Drawable banner;
    final Drawable icon;
    int accent;

    AppEntry(String packageName, Intent launchIntent, CharSequence label, Drawable banner, Drawable icon) {
        this.packageName = packageName;
        this.launchIntent = launchIntent;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
    }
}
