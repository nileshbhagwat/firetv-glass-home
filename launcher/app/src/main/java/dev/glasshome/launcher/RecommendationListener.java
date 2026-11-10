package dev.glasshome.launcher;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Collects content recommendations that apps post as notifications (category
 * "recommendation"), such as Netflix's "new and popular" titles on Fire OS. Only the title and
 * the artwork are kept, in memory. Requires notification access, granted over ADB by the
 * install scripts.
 */
public final class RecommendationListener extends NotificationListenerService {
    private static final String TAG = "GlassHome";
    private static final String EXTRA_RANK = "com.amazon.extra.RANK";
    private static final long DEBOUNCE_MS = 800;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable rebuild = this::rebuildNow;

    @Override
    public void onListenerConnected() {
        scheduleRebuild();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (isRecommendation(sbn)) scheduleRebuild();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (isRecommendation(sbn)) scheduleRebuild();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(rebuild);
        worker.shutdownNow();
        super.onDestroy();
    }

    private static boolean isRecommendation(StatusBarNotification sbn) {
        return sbn != null && Notification.CATEGORY_RECOMMENDATION.equals(sbn.getNotification().category);
    }

    private void scheduleRebuild() {
        handler.removeCallbacks(rebuild);
        handler.postDelayed(rebuild, DEBOUNCE_MS);
    }

    private void rebuildNow() {
        final StatusBarNotification[] active;
        try {
            active = getActiveNotifications();
        } catch (RuntimeException e) {
            Log.w(TAG, "Recommendations unavailable", e);
            return;
        }
        if (active == null) return;
        worker.execute(() -> {
            Map<String, List<StatusBarNotification>> candidates = new HashMap<>();
            for (StatusBarNotification sbn : active) {
                if (!isRecommendation(sbn)) continue;
                List<StatusBarNotification> list = candidates.get(sbn.getPackageName());
                if (list == null) {
                    list = new ArrayList<>();
                    candidates.put(sbn.getPackageName(), list);
                }
                list.add(sbn);
            }
            int width = getResources().getDimensionPixelSize(R.dimen.shelf_card_width);
            int height = getResources().getDimensionPixelSize(R.dimen.shelf_card_height);
            Map<String, List<Recommendations.Item>> snapshot = new HashMap<>();
            for (Map.Entry<String, List<StatusBarNotification>> entry : candidates.entrySet()) {
                List<StatusBarNotification> list = entry.getValue();
                list.sort((a, b) -> Integer.compare(rank(a), rank(b)));
                List<Recommendations.Item> items = new ArrayList<>();
                for (StatusBarNotification sbn : list) {
                    if (items.size() >= Recommendations.MAX_PER_APP) break;
                    Bitmap image = artwork(sbn.getNotification(), width, height);
                    if (image == null) continue;
                    CharSequence title = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
                    items.add(new Recommendations.Item(sbn.getKey(), title == null ? "" : title.toString(), image, rank(sbn)));
                }
                if (!items.isEmpty()) snapshot.put(entry.getKey(), items);
            }
            Recommendations.replaceAll(snapshot);
        });
    }

    private static int rank(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;
        Object value = extras.get(EXTRA_RANK);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                // Fall through.
            }
        }
        return Integer.MAX_VALUE;
    }

    private Bitmap artwork(Notification notification, int width, int height) {
        Icon icon = notification.getLargeIcon();
        if (icon == null) return null;
        try {
            Drawable drawable = icon.loadDrawable(this);
            if (drawable == null) return null;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(new Canvas(bitmap));
            return bitmap;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
