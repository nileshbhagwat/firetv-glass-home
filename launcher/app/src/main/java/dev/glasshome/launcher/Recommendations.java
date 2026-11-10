package dev.glasshome.launcher;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory store of content recommendations that apps post (for example Netflix on Fire OS),
 * filled by {@link RecommendationListener}. Nothing is written to disk.
 */
final class Recommendations {
    /** Maximum items kept per app; artwork is stored at card size to keep memory low. */
    static final int MAX_PER_APP = 4;

    interface Listener {
        void onRecommendationsChanged();
    }

    static final class Item {
        final String key;
        final String title;
        final Bitmap image;
        final int rank;

        Item(String key, String title, Bitmap image, int rank) {
            this.key = key;
            this.title = title;
            this.image = image;
            this.rank = rank;
        }
    }

    private static final Map<String, List<Item>> BY_PACKAGE = new HashMap<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Recommendations() {
    }

    static synchronized List<Item> forPackage(String packageName) {
        List<Item> items = BY_PACKAGE.get(packageName);
        return items == null ? Collections.emptyList() : new ArrayList<>(items);
    }

    /** Replaces everything known for the given packages (called with a full snapshot). */
    static void replaceAll(Map<String, List<Item>> snapshot) {
        synchronized (Recommendations.class) {
            BY_PACKAGE.clear();
            for (Map.Entry<String, List<Item>> entry : snapshot.entrySet()) {
                List<Item> items = new ArrayList<>(entry.getValue());
                items.sort((a, b) -> Integer.compare(a.rank, b.rank));
                if (items.size() > MAX_PER_APP) {
                    items = new ArrayList<>(items.subList(0, MAX_PER_APP));
                }
                BY_PACKAGE.put(entry.getKey(), items);
            }
        }
        MAIN.post(Recommendations::notifyListeners);
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners() {
        for (Listener listener : new ArrayList<>(LISTENERS)) {
            listener.onRecommendationsChanged();
        }
    }
}
