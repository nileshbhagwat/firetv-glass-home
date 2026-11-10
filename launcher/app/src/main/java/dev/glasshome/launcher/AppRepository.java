package dev.glasshome.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        collect(pm, byPackage, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(pm, byPackage, Intent.CATEGORY_LAUNCHER);
        List<AppEntry> apps = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        return apps;
    }

    private static void collect(PackageManager pm, Map<String, AppEntry> out, String category) {
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
            Drawable icon = activity.loadIcon(pm);
            CharSequence label = info.loadLabel(pm);
            out.put(activity.packageName, new AppEntry(activity.packageName, launch, label, banner, icon));
        }
    }
}
