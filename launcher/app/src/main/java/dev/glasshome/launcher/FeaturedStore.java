package dev.glasshome.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Featured titles pushed from the laptop into files/featured (index.json plus images).
 * Nothing is downloaded on the TV. Images are decoded off the main thread at the size they are
 * shown and kept in a small cache, so only the titles on screen use memory.
 */
final class FeaturedStore {
    interface BitmapCallback {
        void onBitmap(Bitmap bitmap);
    }

    private static final String TAG = "GlassHome";
    private static final int CACHE_BYTES = 40 * 1024 * 1024;

    private final File dir;
    private final int fallbackAccent;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private List<FeaturedItem> items = Collections.emptyList();
    private int generation;

    FeaturedStore(Context context) {
        dir = new File(context.getFilesDir(), "featured");
        fallbackAccent = context.getColor(R.color.accent_default);
    }

    List<FeaturedItem> items() {
        return items;
    }

    /**
     * The titles one app shows in a style: only that app's own titles, films and series for a
     * gallery style, films only for styles that show one title at a time (series are used there
     * only when the app has no films).
     */
    List<FeaturedItem> itemsFor(String packageName, String style) {
        List<FeaturedItem> mine = new ArrayList<>();
        List<FeaturedItem> films = new ArrayList<>();
        if (packageName == null) return mine;
        for (FeaturedItem item : items) {
            if (!packageName.equals(item.packageName)) continue;
            mine.add(item);
            if (!item.isSeries()) films.add(item);
        }
        return HomeStyle.showsSeries(style) || films.isEmpty() ? mine : films;
    }

    /** Re-reads index.json and calls {@code done} on the main thread. */
    void reload(Runnable done) {
        final int request = ++generation;
        io.execute(() -> {
            List<FeaturedItem> loaded = parse();
            main.post(() -> {
                if (request != generation) return;
                items = loaded;
                cache.evictAll();
                done.run();
            });
        });
    }

    /** Decodes an image from the featured folder, scaled down to at most maxWidth x maxHeight. */
    void bitmap(String name, int maxWidth, int maxHeight, boolean alpha, BitmapCallback callback) {
        if (name == null) {
            callback.onBitmap(null);
            return;
        }
        final String key = name + '@' + maxWidth + 'x' + maxHeight;
        Bitmap cached = cache.get(key);
        if (cached != null) {
            callback.onBitmap(cached);
            return;
        }
        final int request = generation;
        io.execute(() -> {
            Bitmap bitmap = decode(new File(dir, name), maxWidth, maxHeight, alpha);
            main.post(() -> {
                if (bitmap != null && request == generation) cache.put(key, bitmap);
                callback.onBitmap(bitmap);
            });
        });
    }

    void shutdown() {
        io.shutdownNow();
        cache.evictAll();
    }

    private List<FeaturedItem> parse() {
        File index = new File(dir, "index.json");
        if (!index.isFile()) return Collections.emptyList();
        List<FeaturedItem> result = new ArrayList<>();
        try {
            JSONArray array = new JSONObject(read(index)).optJSONArray("items");
            if (array == null) return result;
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String card = existing(o.optString("card", null));
                if (card == null) continue;
                result.add(new FeaturedItem(
                        o.optString("id", String.valueOf(i)),
                        o.optString("kind", FeaturedItem.KIND_FILM),
                        o.optString("title", ""),
                        o.optString("provider", null),
                        o.optString("package", null),
                        color(o.optString("accent", null)),
                        card,
                        existing(o.optString("cardChar", null)),
                        rect(o.optJSONArray("cardCharRect")),
                        existing(o.optString("full", null)),
                        existing(o.optString("fullChar", null)),
                        rect(o.optJSONArray("fullCharRect"))));
            }
        } catch (Exception e) {
            Log.w(TAG, "Featured titles could not be read", e);
        }
        return result;
    }

    private String existing(String name) {
        return name != null && !name.isEmpty() && new File(dir, name).isFile() ? name : null;
    }

    private int color(String value) {
        try {
            return value == null ? fallbackAccent : Color.parseColor(value);
        } catch (IllegalArgumentException e) {
            return fallbackAccent;
        }
    }

    private static RectF rect(JSONArray a) {
        if (a == null || a.length() != 4) return null;
        float x = (float) a.optDouble(0), y = (float) a.optDouble(1);
        float w = (float) a.optDouble(2), h = (float) a.optDouble(3);
        if (!(w > 0) || !(h > 0)) return null;
        return new RectF(x, y, x + w, y + h);
    }

    private static String read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int n = in.read(data, offset, data.length - offset);
                if (n < 0) break;
                offset += n;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static Bitmap decode(File file, int maxWidth, int maxHeight, boolean alpha) {
        if (!file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= maxHeight) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "Not enough memory for " + file.getName());
            return null;
        }
    }
}
