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
 * Rules, in order:
 * - Leaving a Settings screen (Back or Home) is redirected quickly.
 * - Entering Settings passes through Amazon's home screen first; that is not redirected.
 * - For two minutes after start-up, Fire OS may bring its home screen back several times
 *   while it settles, so the redirect waits for the home screen to stay put.
 * - If Amazon's home screen keeps bouncing back right after each redirect, redirects pause
 *   for 30 seconds instead of fighting Fire OS.
 */
public final class HomeRedirectService extends AccessibilityService {
    static final String ACTION_HOME_BUTTON = "dev.glasshome.launcher.action.HOME_BUTTON";
    static final String EXTRA_RESET_FOCUS = "reset_focus";

    private static final String TAG = "GlassHome";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME_CLASS = "HomeActivity";
    private static final String SETTINGS_PACKAGE = "com.amazon.tv.settings.v2";

    private static final long SETTLE_MS = 300;
    private static final long SETTINGS_ENTRY_SETTLE_MS = 1200;
    private static final long SETTINGS_ENTRY_WINDOW_MS = 3000;
    private static final long BOOT_PERIOD_MS = 120_000;
    private static final long BOOT_SETTLE_MS = 1500;
    private static final long BOUNCE_WINDOW_MS = 3000;
    private static final int BOUNCE_LIMIT = 4;
    private static final long PAUSE_MS = 30_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> activityCache = new HashMap<>();
    private String lastPackage;
    private String lastClass;
    private long lastSettingsEvent = -SETTINGS_ENTRY_WINDOW_MS;
    private long lastRedirect = -BOUNCE_WINDOW_MS;
    private int bounces;
    private long pausedUntil;
    private boolean resetFocus;

    private final Runnable redirect = () -> {
        lastRedirect = SystemClock.uptimeMillis();
        Log.i(TAG, "Opening Glass Home (reset focus: " + resetFocus + ")");
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
        boolean activity = isActivity(pkg, cls);
        if (SETTINGS_PACKAGE.equals(pkg) && !activity) {
            lastSettingsEvent = now; // Settings trampoline window: Settings is opening.
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "window " + pkg + "/" + cls + " activity=" + activity);
        }
        if (!activity) {
            return; // Dialogs, popups and toasts do not change the screen underneath.
        }

        if (!AMAZON_LAUNCHER.equals(pkg) || !cls.contains(AMAZON_HOME_CLASS)) {
            handler.removeCallbacks(redirect);
            lastPackage = pkg;
            lastClass = cls;
            return;
        }

        // Amazon's home screen is in front.
        if (now < pausedUntil) {
            return;
        }
        bounces = now - lastRedirect < BOUNCE_WINDOW_MS ? bounces + 1 : 0;
        if (bounces >= BOUNCE_LIMIT) {
            Log.w(TAG, "Amazon's home screen keeps returning; pausing redirects for 30 s");
            pausedUntil = now + PAUSE_MS;
            bounces = 0;
            handler.removeCallbacks(redirect);
            return;
        }

        boolean leavingSettings = SETTINGS_PACKAGE.equals(lastPackage)
                || (lastClass != null && lastClass.endsWith("SettingsActivity"));
        boolean enteringSettings = !leavingSettings && now - lastSettingsEvent < SETTINGS_ENTRY_WINDOW_MS;
        long delay;
        if (enteringSettings) {
            delay = SETTINGS_ENTRY_SETTLE_MS;
        } else if (SystemClock.elapsedRealtime() < BOOT_PERIOD_MS) {
            delay = BOOT_SETTLE_MS;
        } else {
            delay = SETTLE_MS;
        }
        // Home pressed while Glass Home was already in front: go back to the dock.
        resetFocus = getPackageName().equals(lastPackage);
        handler.removeCallbacks(redirect);
        handler.postDelayed(redirect, delay);
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
