package dev.glasshome.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Compact glass dropdown under the Inputs pill: HDMI ports, AV and Live TV. */
final class InputsMenu {
    private static final String LIVE_TV_PACKAGE = "com.amazon.tv.livetv";

    private final MainActivity activity;
    private PopupWindow popup;

    InputsMenu(MainActivity activity) {
        this.activity = activity;
    }

    boolean isShowing() {
        return popup != null && popup.isShowing();
    }

    void dismiss() {
        if (isShowing()) {
            popup.dismiss();
        }
    }

    void show(View anchor) {
        dismiss();
        LayoutInflater inflater = LayoutInflater.from(activity);
        LinearLayout list = (LinearLayout) inflater.inflate(R.layout.menu_inputs, null, false);
        List<Item> items = loadItems();
        if (items.isEmpty()) {
            TextView empty = (TextView) inflater.inflate(R.layout.item_input, list, false);
            empty.setText(R.string.no_inputs);
            empty.setAlpha(0.6f);
            list.addView(empty);
        }
        for (Item item : items) {
            TextView row = (TextView) inflater.inflate(R.layout.item_input, list, false);
            row.setText(item.label);
            row.setTypeface(Fonts.outfit(activity, 400));
            row.setOnClickListener(v -> {
                dismiss();
                activity.launch(item.intent, item.label);
            });
            list.addView(row);
        }
        int width = activity.getResources().getDimensionPixelSize(R.dimen.menu_width);
        popup = new PopupWindow(list, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(android.R.style.Animation_Dialog);

        int[] location = new int[2];
        anchor.getLocationInWindow(location);
        int gap = Math.round(10 * activity.getResources().getDisplayMetrics().density);
        int x = location[0] + anchor.getWidth() - width;
        int y = location[1] + anchor.getHeight() + gap;
        popup.showAtLocation(anchor.getRootView(), Gravity.TOP | Gravity.START, x, y);
        if (list.getChildCount() > 0) {
            list.getChildAt(0).requestFocus();
        }
    }

    private List<Item> loadItems() {
        List<Item> items = new ArrayList<>();
        TvInputManager manager = (TvInputManager) activity.getSystemService(Context.TV_INPUT_SERVICE);
        if (manager != null) {
            List<TvInputInfo> inputs = new ArrayList<>(manager.getTvInputList());
            inputs.sort((a, b) -> Integer.compare(rank(a.getType()), rank(b.getType())) != 0
                    ? Integer.compare(rank(a.getType()), rank(b.getType()))
                    : a.getId().compareTo(b.getId()));
            for (TvInputInfo info : inputs) {
                if (info.getParentId() != null || rank(info.getType()) == Integer.MAX_VALUE) {
                    continue;
                }
                CharSequence label = info.loadCustomLabel(activity);
                if (TextUtils.isEmpty(label)) {
                    label = info.loadLabel(activity);
                }
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(info.getId()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                items.add(new Item(label, intent));
            }
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null) {
            items.add(new Item(activity.getString(R.string.live_tv), liveTv));
        }
        return items;
    }

    /** HDMI first, then analogue/legacy ports. Tuners and app inputs are excluded. */
    private static int rank(int type) {
        switch (type) {
            case TvInputInfo.TYPE_HDMI:
                return 0;
            case TvInputInfo.TYPE_DISPLAY_PORT:
            case TvInputInfo.TYPE_DVI:
                return 1;
            case TvInputInfo.TYPE_COMPOSITE:
            case TvInputInfo.TYPE_COMPONENT:
            case TvInputInfo.TYPE_SVIDEO:
            case TvInputInfo.TYPE_SCART:
            case TvInputInfo.TYPE_VGA:
                return 2;
            default:
                return Integer.MAX_VALUE;
        }
    }

    private Intent liveTvIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN).setPackage(LIVE_TV_PACKAGE);
        ResolveInfo info = activity.getPackageManager().resolveActivity(intent, 0);
        if (info == null || info.activityInfo == null) {
            return null;
        }
        return new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static final class Item {
        final CharSequence label;
        final Intent intent;

        Item(CharSequence label, Intent intent) {
            this.label = label;
            this.intent = intent;
        }
    }
}
