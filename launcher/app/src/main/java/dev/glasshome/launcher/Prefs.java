package dev.glasshome.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Persistent settings: dock order and the last focused position. */
final class Prefs {
    static final List<String> DEFAULT_DOCK = Arrays.asList(
            "com.netflix.ninja",          // Netflix
            "com.amazon.firebat",         // Prime Video
            "com.amazon.firetv.youtube",  // YouTube
            "in.startv.hotstar",          // JioHotstar
            "com.onemainstream.sonyliv.android", // SonyLIV
            "com.zee5.amazon");           // Zee5

    private static final String FILE = "glasshome";
    private static final String KEY_DOCK = "dock_packages";
    private static final String KEY_ZONE = "focus_zone";
    private static final String KEY_TOP = "focus_top";
    private static final String KEY_DOCK_INDEX = "focus_dock";
    private static final String KEY_GRID = "focus_grid";
    private static final String KEY_OWNER = "owner_name";
    private static final String KEY_STYLE = "home_style";

    private Prefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static List<String> dock(Context context) {
        String stored = prefs(context).getString(KEY_DOCK, null);
        if (stored == null) {
            return DEFAULT_DOCK;
        }
        List<String> result = new ArrayList<>();
        for (String part : stored.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** Saves the dock order. An empty value restores the default dock. */
    static void setDock(Context context, String csv) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (csv == null || csv.trim().isEmpty()) {
            editor.remove(KEY_DOCK);
        } else {
            editor.putString(KEY_DOCK, csv);
        }
        editor.commit();
    }

    /** Returns {zone, topIndex, dockIndex, gridIndex}. */
    static int[] focus(Context context) {
        SharedPreferences p = prefs(context);
        return new int[]{p.getInt(KEY_ZONE, 1), p.getInt(KEY_TOP, 0), p.getInt(KEY_DOCK_INDEX, 0), p.getInt(KEY_GRID, 0)};
    }

    /** Name shown in the greeting; null means "use the TV's name". */
    static String ownerName(Context context) {
        String name = prefs(context).getString(KEY_OWNER, null);
        return name == null || name.trim().isEmpty() ? null : name.trim();
    }

    static void setOwnerName(Context context, String name) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (name == null || name.trim().isEmpty()) {
            editor.remove(KEY_OWNER);
        } else {
            editor.putString(KEY_OWNER, name.trim());
        }
        editor.commit();
    }

    /** The chosen home style (see {@link HomeStyle}); classic when none was chosen. */
    static String style(Context context) {
        return HomeStyle.normalize(prefs(context).getString(KEY_STYLE, HomeStyle.CLASSIC));
    }

    static void setStyle(Context context, String style) {
        prefs(context).edit().putString(KEY_STYLE, HomeStyle.normalize(style)).commit();
    }

    static void saveFocus(Context context, int zone, int top, int dock, int grid) {
        prefs(context).edit()
                .putInt(KEY_ZONE, zone)
                .putInt(KEY_TOP, top)
                .putInt(KEY_DOCK_INDEX, dock)
                .putInt(KEY_GRID, grid)
                .apply();
    }
}
