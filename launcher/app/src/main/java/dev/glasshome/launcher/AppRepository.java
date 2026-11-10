package dev.glasshome.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds launchable apps, preferring TV (leanback) entry points. */
final class AppRepository {
    /** System entry points that are reachable elsewhere or not useful as tiles. */
    private static final Set<String> HIDDEN_PACKAGES = new HashSet<>(Arrays.asList(
            "dev.glasshome.launcher",
            "com.amazon.tv.launcher",
            "com.amazon.firehomestarter",
            "com.amazon.tv.settings.v2",
            "com.amazon.android.settings",
            "com.amazon.tv.quicksettings.ui",
            "com.amazon.ftv.profilepicker",
            "com.amazon.ftv.screensaver",
            "com.amazon.ssm",
            "com.amazon.hedwig",
            "com.amazon.tv.earlyaccess",
            "com.android.documentsui",
            "com.mediatek.opapp",
            "com.vewd.core.fvpplayer",
            "com.vewd.core.opapp",
            "com.vewd.core.tvapp"));

    private static final Set<String> HIDDEN_ACTIVITIES = new HashSet<>(Arrays.asList(
            "com.apple.airplay.amazon.airplay.PowerModeDialogActivity",
            "com.apple.airplay.amazon.airplay.awareness.AwarenessActivity"));

    private AppRepository() {
    }

    static List<AppEntry> loadAll(Context context) {
        PackageManager pm = context.getPackageManager();
        Resources res = context.getResources();
        // Render banners and icons once at their on-screen size (including the focus scale-up).
        // Apps often ship banners many times larger than a tile; keeping those would cost
        // tens of megabytes of memory on a TV with little to spare.
        Sizes sizes = new Sizes(
                Math.round(res.getDimension(R.dimen.tile_width) * TileViews.FOCUS_SCALE),
                Math.round(res.getDimension(R.dimen.tile_height) * TileViews.FOCUS_SCALE),
                Math.round(56 * res.getDisplayMetrics().density * TileViews.FOCUS_SCALE));
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        collect(pm, res, sizes, byPackage, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(pm, res, sizes, byPackage, Intent.CATEGORY_LAUNCHER);
        List<AppEntry> apps = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        return apps;
    }

    private static void collect(PackageManager pm, Resources res, Sizes sizes, Map<String, AppEntry> out, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null
                    || out.containsKey(activity.packageName)
                    || HIDDEN_PACKAGES.contains(activity.packageName)
                    || HIDDEN_ACTIVITIES.contains(activity.name)) {
                continue;
            }
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(new ComponentName(activity.packageName, activity.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Drawable banner = activity.loadBanner(pm);
            if (banner == null) {
                banner = activity.applicationInfo.loadBanner(pm);
            }
            Drawable icon = banner == null ? render(res, activity.loadIcon(pm), sizes.icon, sizes.icon) : null;
            banner = render(res, banner, sizes.bannerWidth, sizes.bannerHeight);
            CharSequence label = info.loadLabel(pm);
            out.put(activity.packageName, new AppEntry(activity.packageName, launch, label, banner, icon));
        }
    }

    private static Drawable render(Resources res, Drawable source, int width, int height) {
        if (source == null || width <= 0 || height <= 0) {
            return source;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            source.setBounds(0, 0, width, height);
            source.draw(new Canvas(bitmap));
            return new BitmapDrawable(res, bitmap);
        } catch (RuntimeException e) {
            return source;
        }
    }

    private static final class Sizes {
        final int bannerWidth;
        final int bannerHeight;
        final int icon;

        Sizes(int bannerWidth, int bannerHeight, int icon) {
            this.bannerWidth = bannerWidth;
            this.bannerHeight = bannerHeight;
            this.icon = icon;
        }
    }
}
