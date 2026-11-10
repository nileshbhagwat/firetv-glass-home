package dev.glasshome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Fire OS always routes the Home button to Amazon's launcher, which cannot be disabled.
 * When Amazon's home screen stays in front, this service opens Glass Home instead.
 *
 * Amazon's home screen also flashes up briefly when Fire TV opens its Settings screens,
 * so the redirect waits a moment and is cancelled if another activity comes to the front.
 */
public final class HomeRedirectService extends AccessibilityService {
    static final String ACTION_HOME_BUTTON = "dev.glasshome.launcher.action.HOME_BUTTON";
    static final String EXTRA_RESET_FOCUS = "reset_focus";

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME_CLASS = "HomeActivity";
    private static final String SETTINGS_PACKAGE = "com.amazon.tv.settings.v2";
    private static final long SETTLE_MS = 300;
    // Opening Settings passes through Amazon's home screen; its window follows ~400 ms later.
    private static final long SETTINGS_SETTLE_MS = 1200;
    private static final long SETTINGS_WINDOW_MS = 3000;
    private static final String TAG = "GlassHome";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> activityCache = new HashMap<>();
    private String lastPackage;
    private long lastSettingsEvent = -SETTINGS_WINDOW_MS;
    private boolean resetFocus;

    private final Runnable redirect = () -> {
        Intent home = new Intent(this, MainActivity.class)
                .setAction(ACTION_HOME_BUTTON)
                .putExtra(EXTRA_RESET_FOCUS, resetFocus)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkgText = event.getPackageName();
        CharSequence clsText = event.getClassName();
        if (pkgText == null || clsText == null) {
            return;
        }
        String pkg = pkgText.toString();
        String cls = clsText.toString();
        long now = SystemClock.uptimeMillis();
        if (SETTINGS_PACKAGE.equals(pkg)) {
            lastSettingsEvent = now;
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "window " + pkg + "/" + cls + " activity=" + isActivity(pkg, cls));
        }
        if (!isActivity(pkg, cls)) {
            return; // Dialogs, popups and toasts do not change the screen underneath.
        }
        if (AMAZON_LAUNCHER.equals(pkg) && cls.contains(AMAZON_HOME_CLASS)) {
            // Home pressed while Glass Home was already in front: go back to the dock.
            resetFocus = getPackageName().equals(lastPackage);
            boolean settingsLaunch = now - lastSettingsEvent < SETTINGS_WINDOW_MS;
            handler.removeCallbacks(redirect);
            handler.postDelayed(redirect, settingsLaunch ? SETTINGS_SETTLE_MS : SETTLE_MS);
        } else {
            handler.removeCallbacks(redirect);
            lastPackage = pkg;
        }
    }

    private boolean isActivity(String pkg, String cls) {
        String key = pkg + "/" + cls;
        Boolean cached = activityCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean result;
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
            result = true;
        } catch (PackageManager.NameNotFoundException e) {
            result = false;
        }
        activityCache.put(key, result);
        return result;
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(redirect);
    }
}
