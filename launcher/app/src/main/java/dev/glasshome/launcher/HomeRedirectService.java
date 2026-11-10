package dev.glasshome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS always routes the Home button to Amazon's launcher, which cannot be disabled.
 * When Amazon's home screen comes to the front, this service opens Glass Home instead.
 */
public final class HomeRedirectService extends AccessibilityService {
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final long MIN_INTERVAL_MS = 700;

    private long lastRedirect;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null || !AMAZON_LAUNCHER.contentEquals(pkg)
                || !cls.toString().contains("HomeActivity")) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirect < MIN_INTERVAL_MS) {
            return;
        }
        lastRedirect = now;
        Intent home = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
    }

    @Override
    public void onInterrupt() {
        // No feedback to interrupt.
    }
}
