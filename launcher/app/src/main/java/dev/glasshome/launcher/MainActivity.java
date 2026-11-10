package dev.glasshome.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen. Focus is modelled explicitly as zone + index so that D-pad movement is
 * deterministic: Up/Down move exactly one row, the ends of a row never fall through.
 */
public final class MainActivity extends Activity {
    static final String ACTION_REFRESH = "dev.glasshome.launcher.action.REFRESH";

    private static final int ZONE_TOP = 0;
    private static final int ZONE_DOCK = 1;
    private static final int ZONE_GRID = 2;
    private static final int COLUMNS = 4;
    private static final int PILL_INPUTS = 0;
    private static final int PILL_WIFI = 1;

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<AppEntry> dockApps = new ArrayList<>();
    private final List<AppEntry> gridApps = new ArrayList<>();
    private final List<View> dockTiles = new ArrayList<>();
    private final Rect tmpRect = new Rect();
    private final RectF tmpRectF = new RectF();

    private ViewGroup page;
    private HighlightView highlight;
    private GlowView glow;
    private TextView title;
    private TextView greeting;
    private TextView[] pills;
    private ViewGroup dockRow;
    private RecyclerView grid;
    private TileAdapter adapter;
    private ImageView wallpaper;
    private TextView shelfCaption;
    private LinearLayout shelf;
    private String shelfPackage;
    private List<Recommendations.Item> shelfItems = new ArrayList<>();
    private final Recommendations.Listener shelfListener = () -> updateShelf(true);
    private View scrim;
    private InputsMenu inputsMenu;
    private NetworkStatus networkStatus;

    private int zone = ZONE_DOCK;
    private int topIndex;
    private int dockIndex;
    private int gridIndex;
    private int gridTopRow;
    private int gridScrollY;
    private boolean appsShown;
    private int appsOffset;
    private int rowHeight;
    private int loadGeneration;
    private boolean receiversRegistered;

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_REFRESH.equals(action)) {
                loadWallpaper();
                reloadApps();
            } else {
                updateGreeting();
            }
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        page = findViewById(R.id.page);
        highlight = findViewById(R.id.highlight);
        glow = findViewById(R.id.glow);
        title = findViewById(R.id.title);
        greeting = findViewById(R.id.greeting);
        dockRow = findViewById(R.id.dock_row);
        grid = findViewById(R.id.grid);
        wallpaper = findViewById(R.id.wallpaper);
        scrim = findViewById(R.id.scrim);
        shelfCaption = findViewById(R.id.shelf_caption);
        shelf = findViewById(R.id.shelf);
        Recommendations.addListener(shelfListener);
        pills = new TextView[]{findViewById(R.id.pill_inputs), findViewById(R.id.pill_wifi), findViewById(R.id.pill_settings)};
        appsOffset = getResources().getDimensionPixelSize(R.dimen.apps_offset);
        rowHeight = getResources().getDimensionPixelSize(R.dimen.tile_slot_height);
        applyTypefaces();

        adapter = new TileAdapter(gridApps);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        grid.setAdapter(adapter);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                gridScrollY += dy;
                if (zone == ZONE_GRID) {
                    placeHighlight(true, 0);
                }
            }
        });

        int[] saved = Prefs.focus(this);
        zone = saved[0];
        topIndex = saved[1];
        dockIndex = saved[2];
        gridIndex = saved[3];

        inputsMenu = new InputsMenu(this);
        networkStatus = new NetworkStatus(this, label -> pills[PILL_WIFI].setText(label));
        registerReceivers();
        networkStatus.start();
        updateGreeting();
        loadWallpaper();
        reloadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
        networkStatus.refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        inputsMenu.dismiss();
        Prefs.saveFocus(this, zone, topIndex, dockIndex, gridIndex);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        inputsMenu.dismiss();
        if (intent.getBooleanExtra(HomeRedirectService.EXTRA_RESET_FOCUS, false)) {
            // Home pressed while Glass Home was already in front: return to the dock.
            if (!dockApps.isEmpty()) zone = ZONE_DOCK;
        } else {
            // Returning from an app: focus that app's tile, like Apple TV. Otherwise the last
            // focused tile is kept.
            focusPackage(intent.getStringExtra(HomeRedirectService.EXTRA_FROM_PACKAGE));
        }
        applyFocus(true);
    }

    private void focusPackage(String packageName) {
        if (packageName == null) return;
        for (int i = 0; i < dockApps.size(); i++) {
            if (packageName.equals(dockApps.get(i).packageName)) {
                zone = ZONE_DOCK;
                dockIndex = i;
                return;
            }
        }
        for (int i = 0; i < gridApps.size(); i++) {
            if (packageName.equals(gridApps.get(i).packageName)) {
                zone = ZONE_GRID;
                gridIndex = i;
                return;
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (receiversRegistered) {
            unregisterReceiver(refreshReceiver);
            unregisterReceiver(packageReceiver);
        }
        networkStatus.stop();
        Recommendations.removeListener(shelfListener);
        loader.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (android.util.Log.isLoggable("GlassHome", android.util.Log.DEBUG)) {
            android.util.Log.d("GlassHome", "key " + KeyEvent.keyCodeToString(event.getKeyCode())
                    + (event.getAction() == KeyEvent.ACTION_DOWN ? " down" : " up")
                    + " zone=" + zone + " menu=" + inputsMenu.isShowing());
        }
        // While the Inputs menu is open it receives the remote's keys from here (it never
        // takes window focus itself), so no press is lost while the popup is appearing.
        if (inputsMenu.isShowing() && inputsMenu.handleKey(event)) {
            return true;
        }
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) move(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) move(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) move(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) move(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (down && event.getRepeatCount() == 0) activate();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down && event.getRepeatCount() == 0 && zone != ZONE_DOCK && !dockApps.isEmpty()) {
                    zone = ZONE_DOCK;
                    applyFocus(true);
                }
                return true; // A launcher never closes itself on Back.
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void move(int dx, int dy) {
        switch (zone) {
            case ZONE_TOP:
                if (dx != 0) {
                    topIndex = clamp(topIndex + dx, 0, pills.length - 1);
                } else if (dy > 0 && !dockApps.isEmpty()) {
                    zone = ZONE_DOCK;
                }
                break;
            case ZONE_DOCK:
                if (dx != 0) {
                    dockIndex = clamp(dockIndex + dx, 0, dockApps.size() - 1);
                } else if (dy < 0) {
                    zone = ZONE_TOP;
                } else if (dy > 0 && !gridApps.isEmpty()) {
                    zone = ZONE_GRID;
                    gridIndex = Math.min(dockIndex, gridApps.size() - 1);
                }
                break;
            default: {
                int row = gridIndex / COLUMNS;
                int col = gridIndex % COLUMNS;
                if (dx != 0) {
                    int c = col + dx;
                    int index = row * COLUMNS + c;
                    if (c >= 0 && c < COLUMNS && index < gridApps.size()) {
                        gridIndex = index;
                    }
                } else if (dy < 0) {
                    if (row == 0) {
                        zone = dockApps.isEmpty() ? ZONE_TOP : ZONE_DOCK;
                        if (!dockApps.isEmpty()) dockIndex = Math.min(col, dockApps.size() - 1);
                    } else {
                        gridIndex -= COLUMNS;
                    }
                } else if (dy > 0) {
                    int lastRow = (gridApps.size() - 1) / COLUMNS;
                    if (row < lastRow) gridIndex = Math.min(gridIndex + COLUMNS, gridApps.size() - 1);
                }
                break;
            }
        }
        applyFocus(true);
    }

    private void applyFocus(boolean animate) {
        boolean wantApps = zone == ZONE_GRID;
        if (wantApps != appsShown || !animate) {
            setAppsShown(wantApps, animate);
        }
        for (int i = 0; i < pills.length; i++) {
            float alpha = zone == ZONE_TOP && i == topIndex ? 1f : 0.8f;
            pills[i].animate().alpha(alpha).setDuration(160).start();
        }
        for (int i = 0; i < dockTiles.size(); i++) {
            TileViews.setSelected(dockTiles.get(i), zone == ZONE_DOCK && i == dockIndex, animate);
        }
        adapter.setSelected(zone == ZONE_GRID ? gridIndex : -1, grid);
        if (!dockApps.isEmpty()) {
            AppEntry app = dockApps.get(dockIndex);
            title.setText(app.label);
            glow.setColor(app.accent, animate);
        } else {
            title.setText("");
        }
        title.animate().alpha(zone == ZONE_DOCK ? 1f : 0.45f).setDuration(220).start();
        updateShelf(animate);
        if (zone == ZONE_GRID) {
            scrollGridToSelection(animate);
        }
        placeHighlight(animate, 0);
    }

    private void setAppsShown(boolean show, boolean animate) {
        appsShown = show;
        float y = show ? -appsOffset : 0f;
        page.animate().cancel();
        if (animate) {
            page.animate().translationY(y).setDuration(460).setInterpolator(new DecelerateInterpolator(2.4f)).start();
        } else {
            page.setTranslationY(y);
        }
    }

    private void scrollGridToSelection(boolean animate) {
        int row = gridIndex / COLUMNS;
        int visibleRows = Math.max(1, (grid.getHeight() - grid.getPaddingBottom()) / rowHeight);
        if (row < gridTopRow) {
            gridTopRow = row;
        } else if (row >= gridTopRow + visibleRows) {
            gridTopRow = row - visibleRows + 1;
        }
        int dy = gridTopRow * rowHeight - gridScrollY;
        if (dy == 0) {
            return;
        }
        if (animate) {
            grid.smoothScrollBy(0, dy, new DecelerateInterpolator(2f));
        } else {
            grid.scrollBy(0, dy);
        }
    }

    /** Moves the glass plate behind the focused element (in page coordinates). */
    private void placeHighlight(boolean animate, int attempt) {
        View target;
        boolean pill = false;
        if (zone == ZONE_TOP) {
            target = pills[topIndex];
            pill = true;
        } else if (zone == ZONE_DOCK) {
            if (dockTiles.isEmpty()) {
                highlight.hide();
                return;
            }
            target = TileViews.card(dockTiles.get(dockIndex));
        } else {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(gridIndex);
            target = holder == null ? null : TileViews.card(holder.itemView);
        }
        if (target == null || target.getWidth() == 0) {
            if (attempt < 10) {
                main.postDelayed(() -> placeHighlight(animate, attempt + 1), 16);
            }
            return;
        }
        tmpRect.set(0, 0, target.getWidth(), target.getHeight());
        page.offsetDescendantRectToMyCoords(target, tmpRect);
        tmpRectF.set(tmpRect);
        float density = getResources().getDisplayMetrics().density;
        float radius;
        if (pill) {
            float pad = 3 * density;
            tmpRectF.inset(-pad, -pad);
            radius = tmpRectF.height() / 2f;
        } else {
            float grow = (TileViews.FOCUS_SCALE - 1f) / 2f;
            float pad = 6 * density;
            tmpRectF.inset(-tmpRectF.width() * grow - pad, -tmpRectF.height() * grow - pad);
            radius = 12 * density * TileViews.FOCUS_SCALE + pad;
        }
        highlight.moveTo(tmpRectF, radius, animate);
    }

    private void activate() {
        if (zone == ZONE_TOP) {
            if (topIndex == PILL_INPUTS) {
                inputsMenu.show(pills[PILL_INPUTS]);
            } else if (topIndex == PILL_WIFI) {
                openSettings(Settings.ACTION_WIFI_SETTINGS);
            } else {
                openFireTvSettings();
            }
            return;
        }
        List<AppEntry> list = zone == ZONE_DOCK ? dockApps : gridApps;
        int index = zone == ZONE_DOCK ? dockIndex : gridIndex;
        if (index >= 0 && index < list.size()) {
            AppEntry app = list.get(index);
            launch(app.launchIntent, app.label);
        }
    }

    void launch(Intent intent, CharSequence label) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.cannot_open, label), Toast.LENGTH_SHORT).show();
        }
    }

    /** Opens Fire TV's settings screen directly, avoiding a detour through Amazon's home screen. */
    private void openFireTvSettings() {
        try {
            // Opened directly and in its own task: no detour through Amazon's home screen on the
            // way in, and Back from Settings returns straight to Glass Home.
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName("com.amazon.tv.launcher",
                            "com.amazon.tv.launcher.ui.MainSettingsActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            // Fall through to the generic settings request below.
        }
        try {
            startActivity(new Intent("amazon.intent.action.MAINSETTINGS")
                    .setPackage("com.amazon.tv.launcher")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            openSettings(Settings.ACTION_SETTINGS);
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            launch(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), getString(R.string.settings));
        }
    }

    private void reloadApps() {
        final int generation = ++loadGeneration;
        final Context app = getApplicationContext();
        final int fallback = getColor(R.color.accent_default);
        loader.execute(() -> {
            List<AppEntry> all = AppRepository.loadAll(app);
            List<AppEntry> dock = new ArrayList<>();
            for (String pkg : Prefs.dock(app)) {
                for (AppEntry entry : all) {
                    if (entry.packageName.equals(pkg)) {
                        dock.add(entry);
                        break;
                    }
                }
            }
            List<AppEntry> rest = new ArrayList<>();
            for (AppEntry entry : all) {
                if (!dock.contains(entry)) rest.add(entry);
            }
            for (AppEntry entry : dock) {
                entry.accent = AccentSampler.sample(entry.banner != null ? entry.banner : entry.icon, fallback);
            }
            main.post(() -> {
                if (generation == loadGeneration && !isDestroyed()) applyApps(dock, rest);
            });
        });
    }

    private void applyApps(List<AppEntry> dock, List<AppEntry> rest) {
        dockApps.clear();
        dockApps.addAll(dock);
        gridApps.clear();
        gridApps.addAll(rest);

        dockRow.removeAllViews();
        dockTiles.clear();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (AppEntry entry : dockApps) {
            View tile = inflater.inflate(R.layout.item_tile, dockRow, false);
            TileViews.bind(tile, entry);
            sizeDockTile(tile, dockApps.size());
            dockRow.addView(tile);
            dockTiles.add(tile);
        }
        adapter.resetSelection();
        adapter.notifyDataSetChanged();

        topIndex = clamp(topIndex, 0, pills.length - 1);
        dockIndex = clamp(dockIndex, 0, Math.max(0, dockApps.size() - 1));
        gridIndex = clamp(gridIndex, 0, Math.max(0, gridApps.size() - 1));
        if (zone == ZONE_GRID && gridApps.isEmpty()) zone = ZONE_DOCK;
        if (zone == ZONE_DOCK && dockApps.isEmpty()) zone = ZONE_TOP;
        if (zone < ZONE_TOP || zone > ZONE_GRID) zone = ZONE_DOCK;
        page.post(() -> applyFocus(false));
    }

    /** Four or fewer dock apps use full-size tiles; more shrink proportionally to fit the band. */
    private void sizeDockTile(View tile, int count) {
        Resources res = getResources();
        int fullSlot = res.getDimensionPixelSize(R.dimen.tile_slot_width);
        int fullWidth = res.getDimensionPixelSize(R.dimen.tile_width);
        int fullHeight = res.getDimensionPixelSize(R.dimen.tile_height);
        int slot = count > 4 ? res.getDimensionPixelSize(R.dimen.dock_row_width) / count : fullSlot;
        float scale = slot / (float) fullSlot;
        int width = Math.round(fullWidth * scale);
        int height = Math.round(fullHeight * scale);

        tile.getLayoutParams().width = slot;
        FrameLayout.LayoutParams card = (FrameLayout.LayoutParams) TileViews.card(tile).getLayoutParams();
        card.width = width;
        card.height = height;
        card.topMargin = res.getDimensionPixelSize(R.dimen.tile_top) + (fullHeight - height) / 2;
        FrameLayout.LayoutParams label = (FrameLayout.LayoutParams) tile.findViewById(R.id.label).getLayoutParams();
        label.topMargin = card.topMargin + height + Math.round(12 * res.getDisplayMetrics().density);
    }

    private void loadWallpaper() {
        final File dir = getFilesDir(); // written by scripts/set-wallpaper.ps1
        loader.execute(() -> {
            Bitmap bitmap = null;
            if (dir != null) {
                for (String name : new String[]{"wallpaper.jpg", "wallpaper.png", "wallpaper.webp"}) {
                    File file = new File(dir, name);
                    if (file.isFile()) {
                        bitmap = decodeScaled(file, 1920, 1080);
                        if (bitmap != null) break;
                    }
                }
            }
            final Bitmap result = bitmap;
            main.post(() -> {
                if (isDestroyed()) return;
                wallpaper.setImageBitmap(result);
                int visibility = result == null ? View.GONE : View.VISIBLE;
                wallpaper.setVisibility(visibility);
                scrim.setVisibility(visibility);
            });
        });
    }

    private static Bitmap decodeScaled(File file, int maxWidth, int maxHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= maxHeight) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    /**
     * The owner's first name: an override set over ADB, otherwise taken from the TV's name
     * ("SAM's Fire TV" gives "Sam"). Returns null when the TV name has no owner.
     */
    private String ownerName() {
        String override = Prefs.ownerName(this);
        if (override != null) return override;
        String device = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        if (device == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\S+)['\u2019]s\\b").matcher(device);
        if (!m.find()) return null;
        String raw = m.group(1);
        return raw.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + raw.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Content shelf: when the focused dock app shares recommendations (Netflix does on Fire OS),
     * up to four artwork cards replace the large app name, fanned slightly in 3D.
     */
    private void updateShelf(boolean animate) {
        AppEntry app = (zone != ZONE_GRID && !dockApps.isEmpty()) ? dockApps.get(dockIndex) : null;
        List<Recommendations.Item> items = app == null ? new ArrayList<>() : Recommendations.forPackage(app.packageName);
        if (items.isEmpty()) {
            shelf.setVisibility(View.GONE);
            shelfCaption.setVisibility(View.GONE);
            title.setVisibility(View.VISIBLE);
            shelfPackage = null;
            shelfItems = items;
            return;
        }
        title.setVisibility(View.GONE);
        shelfCaption.setText(getString(R.string.shelf_caption, app.label));
        shelfCaption.setTypeface(Fonts.outfit(this, 400));
        shelfCaption.setVisibility(View.VISIBLE);
        shelf.setVisibility(View.VISIBLE);
        boolean changed = !app.packageName.equals(shelfPackage) || !sameItems(items, shelfItems);
        shelfPackage = app.packageName;
        shelfItems = items;
        shelf.setAlpha(zone == ZONE_DOCK ? 1f : 0.45f);
        if (!changed) return;

        shelf.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int width = getResources().getDimensionPixelSize(R.dimen.shelf_card_width);
        int height = getResources().getDimensionPixelSize(R.dimen.shelf_card_height);
        float middle = (items.size() - 1) / 2f;
        for (int i = 0; i < items.size(); i++) {
            ImageView card = new ImageView(this);
            card.setImageBitmap(items.get(i).image);
            card.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.setBackgroundResource(R.drawable.tile_bg);
            card.setClipToOutline(true);
            card.setContentDescription(items.get(i).title);
            card.setCameraDistance(8000 * density);
            card.setRotationY((i - middle) * -6f); // fanned towards the centre
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
            lp.setMarginEnd(Math.round(16 * density));
            shelf.addView(card, lp);
            if (animate) {
                // Pop out of the screen: each card tips up and springs forward in turn.
                card.setAlpha(0f);
                card.setScaleX(0.86f);
                card.setScaleY(0.86f);
                card.setRotationX(24f);
                card.setTranslationY(18 * density);
                card.animate().alpha(1f).scaleX(1f).scaleY(1f).rotationX(0f).translationY(0f)
                        .setStartDelay(i * 70L).setDuration(460)
                        .setInterpolator(new OvershootInterpolator(1.3f)).start();
            }
        }
    }

    private static boolean sameItems(List<Recommendations.Item> a, List<Recommendations.Item> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).key.equals(b.get(i).key)) return false;
        }
        return true;
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int text;
        if (hour >= 5 && hour < 12) {
            text = R.string.greeting_morning;
        } else if (hour >= 12 && hour < 17) {
            text = R.string.greeting_afternoon;
        } else if (hour >= 17 && hour < 22) {
            text = R.string.greeting_evening;
        } else {
            text = R.string.greeting_night;
        }
        String name = ownerName();
        greeting.setText(name == null ? getString(text) : getString(R.string.greeting_with_name, getString(text), name));
    }

    private void applyTypefaces() {
        ((TextView) findViewById(R.id.clock)).setTypeface(Fonts.outfit(this, 200));
        ((TextView) findViewById(R.id.date)).setTypeface(Fonts.outfit(this, 300));
        greeting.setTypeface(Fonts.outfit(this, 400));
        title.setTypeface(Fonts.outfit(this, 300));
        ((TextView) findViewById(R.id.apps_title)).setTypeface(Fonts.outfit(this, 400));
        ((TextView) findViewById(R.id.apps_clock)).setTypeface(Fonts.outfit(this, 300));
        for (TextView pill : pills) {
            pill.setTypeface(Fonts.outfit(this, 400));
        }
    }

    private void registerReceivers() {
        IntentFilter refresh = new IntentFilter();
        refresh.addAction(ACTION_REFRESH);
        refresh.addAction(Intent.ACTION_TIME_TICK);
        refresh.addAction(Intent.ACTION_TIME_CHANGED);
        refresh.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(refreshReceiver, refresh);

        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packages.addDataScheme("package");
        registerReceiver(packageReceiver, packages);
        receiversRegistered = true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
