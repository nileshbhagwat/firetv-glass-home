package dev.glasshome.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Home style picker, opened with the remote's Menu button. A glass bar over the dock lists the
 * styles; Left/Right previews each one live, OK keeps it, Back (or Menu again) restores the
 * previous style. Like the Inputs menu it never takes key focus: MainActivity forwards keys.
 */
final class StyleMenu {
    interface Host {
        String committedStyle();

        void previewStyle(String style);

        void commitStyle(String style);

        boolean hasFeatured();
    }

    /** Styles per row; the picker wraps onto a second row so it fits the screen. */
    private static final int PER_ROW = 4;

    private final Context context;
    private final Host host;
    private final List<TextView> chips = new ArrayList<>();
    private PopupWindow popup;
    private int selected;
    private String original;

    StyleMenu(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    boolean isShowing() {
        return popup != null && popup.isShowing();
    }

    void show(View anchor) {
        if (isShowing()) return;
        float density = context.getResources().getDisplayMetrics().density;
        original = host.committedStyle();
        chips.clear();

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackgroundResource(R.drawable.menu_bg);
        int pad = Math.round(8 * density);
        panel.setPadding(pad, pad, pad, pad);

        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        TextView heading = text(context.getString(R.string.style_menu_title), 15, R.color.text_secondary);
        heading.setPadding(Math.round(10 * density), 0, Math.round(10 * density), 0);
        heading.measure(unspecified, unspecified);
        LinearLayout row = null;
        for (int i = 0; i < HomeStyle.ALL.length; i++) {
            if (i % PER_ROW == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                // The heading starts the first row; later rows are indented to line up under it.
                if (i == 0) {
                    row.addView(heading);
                } else {
                    row.addView(new View(context), new LinearLayout.LayoutParams(heading.getMeasuredWidth(), 1));
                }
                // Explicit wrap-content: a default (match-parent) row would let the footer set the width.
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.gravity = Gravity.START;
                if (i > 0) rlp.topMargin = Math.round(4 * density);
                panel.addView(row, rlp);
            }
            TextView chip = text(context.getString(HomeStyle.label(HomeStyle.ALL[i])), 15, R.color.text_primary);
            chip.setBackgroundResource(R.drawable.menu_item_bg);
            chip.setGravity(Gravity.CENTER);
            int h = Math.round(16 * density);
            chip.setPadding(h, 0, h, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(40 * density));
            lp.leftMargin = Math.round(4 * density);
            row.addView(chip, lp);
            chips.add(chip);
        }

        TextView footer = text(context.getString(host.hasFeatured() ? R.string.featured_credit : R.string.featured_none),
                12, R.color.text_secondary);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Math.round(6 * density);
        panel.addView(footer, flp);

        selected = 0;
        for (int i = 0; i < HomeStyle.ALL.length; i++) {
            if (HomeStyle.ALL[i].equals(original)) selected = i;
        }
        updateSelection();

        panel.measure(unspecified, unspecified);
        popup = new PopupWindow(panel, panel.getMeasuredWidth(), panel.getMeasuredHeight(), false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setAnimationStyle(android.R.style.Animation_Dialog);
        popup.showAtLocation(anchor.getRootView(), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0,
                Math.round(372 * density));
    }

    /** Closes the picker. With {@code revert}, the style in use before it opened comes back. */
    void dismiss(boolean revert) {
        if (!isShowing()) return;
        popup.dismiss();
        if (revert && original != null) host.previewStyle(original);
    }

    boolean handleKey(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) select(selected + (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1));
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) select(selected + (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN ? PER_ROW : -PER_ROW));
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (down && event.getRepeatCount() == 0) {
                    popup.dismiss();
                    host.commitStyle(HomeStyle.ALL[selected]);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
                if (down && event.getRepeatCount() == 0) dismiss(true);
                return true;
            default:
                return false;
        }
    }

    /** Moves to a style (ignored past either end) and previews it. */
    private void select(int next) {
        if (next < 0 || next >= chips.size() || next == selected) return;
        selected = next;
        updateSelection();
        host.previewStyle(HomeStyle.ALL[selected]);
    }

    private void updateSelection() {
        for (int i = 0; i < chips.size(); i++) {
            chips.get(i).setSelected(i == selected);
        }
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(context.getColor(color));
        view.setTypeface(Fonts.outfit(context, 400));
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
}
