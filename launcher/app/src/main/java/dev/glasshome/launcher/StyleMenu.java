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
 * Home style picker, opened with the remote's Menu button. A glass panel over the dock has two
 * rows: the home style, and whose titles the featured styles show (the focused app's or all
 * apps'). Up/Down moves between the rows, Left/Right previews each choice live, OK keeps both,
 * Back (or Menu again) restores what was there before. Like the Inputs menu it never takes key
 * focus: MainActivity forwards keys.
 */
final class StyleMenu {
    interface Host {
        String committedStyle();

        boolean committedTitlesFollowApp();

        /** Shows a choice live without saving it. */
        void preview(String style, boolean titlesFollowApp);

        void commit(String style, boolean titlesFollowApp);

        boolean hasFeatured();
    }

    private static final int ROW_STYLE = 0;
    private static final int ROW_TITLES = 1;
    /** Choices in the titles row, in order: the focused app's titles, then all apps' titles. */
    private static final int[] TITLE_LABELS = {R.string.titles_focused_app, R.string.titles_all_apps};

    private final Context context;
    private final Host host;
    private final List<TextView> styleChips = new ArrayList<>();
    private final List<TextView> titleChips = new ArrayList<>();
    private PopupWindow popup;
    private int row;
    private int styleIndex;
    private int titlesIndex;
    private String originalStyle;
    private boolean originalTitlesFollowApp;

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
        originalStyle = host.committedStyle();
        originalTitlesFollowApp = host.committedTitlesFollowApp();
        styleChips.clear();
        titleChips.clear();

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.menu_bg);
        int pad = Math.round(8 * density);
        panel.setPadding(pad, pad, pad, pad);

        List<String> styleLabels = new ArrayList<>();
        for (String style : HomeStyle.ALL) styleLabels.add(context.getString(HomeStyle.label(style)));
        List<String> titleLabels = new ArrayList<>();
        for (int label : TITLE_LABELS) titleLabels.add(context.getString(label));
        TextView styleHeading = heading(R.string.style_menu_title, density);
        TextView titlesHeading = heading(R.string.titles_menu_title, density);
        // Equal heading widths line the two rows of choices up.
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        styleHeading.measure(unspecified, unspecified);
        titlesHeading.measure(unspecified, unspecified);
        int headingWidth = Math.max(styleHeading.getMeasuredWidth(), titlesHeading.getMeasuredWidth());
        styleHeading.setMinWidth(headingWidth);
        titlesHeading.setMinWidth(headingWidth);

        panel.addView(row(styleHeading, styleLabels, styleChips, density), rowParams(0));
        panel.addView(row(titlesHeading, titleLabels, titleChips, density), rowParams(Math.round(4 * density)));

        TextView footer = text(context.getString(host.hasFeatured() ? R.string.featured_credit : R.string.featured_none),
                12, R.color.text_secondary);
        LinearLayout.LayoutParams flp = rowParams(Math.round(6 * density));
        flp.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(footer, flp);

        row = ROW_STYLE;
        styleIndex = 0;
        for (int i = 0; i < HomeStyle.ALL.length; i++) {
            if (HomeStyle.ALL[i].equals(originalStyle)) styleIndex = i;
        }
        titlesIndex = originalTitlesFollowApp ? 0 : 1;
        updateSelection();

        panel.measure(unspecified, unspecified);
        popup = new PopupWindow(panel, panel.getMeasuredWidth(), panel.getMeasuredHeight(), false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setAnimationStyle(android.R.style.Animation_Dialog);
        popup.showAtLocation(anchor.getRootView(), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0,
                Math.round(364 * density));
    }

    /** Closes the picker. With {@code revert}, the choices in use before it opened come back. */
    void dismiss(boolean revert) {
        if (!isShowing()) return;
        popup.dismiss();
        if (revert && originalStyle != null) host.preview(originalStyle, originalTitlesFollowApp);
    }

    boolean handleKey(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    int delta = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                    List<TextView> chips = row == ROW_STYLE ? styleChips : titleChips;
                    int next = (row == ROW_STYLE ? styleIndex : titlesIndex) + delta;
                    if (next >= 0 && next < chips.size()) {
                        if (row == ROW_STYLE) {
                            styleIndex = next;
                        } else {
                            titlesIndex = next;
                        }
                        updateSelection();
                        host.preview(chosenStyle(), chosenTitlesFollowApp());
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) {
                    row = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN ? ROW_TITLES : ROW_STYLE;
                    updateSelection();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (down && event.getRepeatCount() == 0) {
                    popup.dismiss();
                    host.commit(chosenStyle(), chosenTitlesFollowApp());
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

    private String chosenStyle() {
        return HomeStyle.ALL[styleIndex];
    }

    private boolean chosenTitlesFollowApp() {
        return titlesIndex == 0;
    }

    /**
     * The chosen chip of each row is outlined; in the row being changed it is filled instead,
     * so it is always clear which row Left/Right acts on.
     */
    private void updateSelection() {
        for (int i = 0; i < styleChips.size(); i++) {
            styleChips.get(i).setActivated(i == styleIndex);
            styleChips.get(i).setSelected(row == ROW_STYLE && i == styleIndex);
        }
        for (int i = 0; i < titleChips.size(); i++) {
            titleChips.get(i).setActivated(i == titlesIndex);
            titleChips.get(i).setSelected(row == ROW_TITLES && i == titlesIndex);
        }
    }

    private LinearLayout row(TextView heading, List<String> labels, List<TextView> chips, float density) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(heading);
        for (String label : labels) {
            TextView chip = text(label, 15, R.color.text_primary);
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
        return row;
    }

    /** Explicit wrap-content: a default (match-parent) row would let the widest line set its width. */
    private static LinearLayout.LayoutParams rowParams(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private TextView heading(int label, float density) {
        TextView heading = text(context.getString(label), 15, R.color.text_secondary);
        heading.setPadding(Math.round(10 * density), 0, Math.round(10 * density), 0);
        return heading;
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
