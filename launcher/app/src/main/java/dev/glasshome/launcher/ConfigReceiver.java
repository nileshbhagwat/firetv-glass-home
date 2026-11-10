package dev.glasshome.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Configuration over ADB (protected by android.permission.DUMP, which only the shell holds).
 *
 * adb shell am broadcast -n dev.glasshome.launcher/.ConfigReceiver \
 *     -a dev.glasshome.launcher.SET_DOCK --es packages "com.a,com.b,com.c,com.d"
 * adb shell am broadcast -n dev.glasshome.launcher/.ConfigReceiver -a dev.glasshome.launcher.RELOAD
 */
public final class ConfigReceiver extends BroadcastReceiver {
    static final String ACTION_SET_DOCK = "dev.glasshome.launcher.SET_DOCK";
    static final String ACTION_SET_NAME = "dev.glasshome.launcher.SET_NAME";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_PACKAGES = "packages";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SET_DOCK.equals(intent.getAction())) {
            Prefs.setDock(context, intent.getStringExtra(EXTRA_PACKAGES));
        } else if (ACTION_SET_NAME.equals(intent.getAction())) {
            Prefs.setOwnerName(context, intent.getStringExtra(EXTRA_NAME));
        }
        context.sendBroadcast(new Intent(MainActivity.ACTION_REFRESH).setPackage(context.getPackageName()));
    }
}
