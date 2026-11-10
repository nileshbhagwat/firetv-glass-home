package dev.glasshome.launcher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Grid of app tiles. Selection is driven by MainActivity, not by Android focus. */
final class TileAdapter extends RecyclerView.Adapter<TileAdapter.Holder> {
    private final List<AppEntry> apps;
    private int selected = -1;

    TileAdapter(List<AppEntry> apps) {
        this.apps = apps;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View tile = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tile, parent, false);
        return new Holder(tile);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TileViews.bind(holder.itemView, apps.get(position));
        TileViews.setSelected(holder.itemView, position == selected, false);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    void setSelected(int index, RecyclerView grid) {
        if (index == selected) {
            return;
        }
        int previous = selected;
        selected = index;
        update(grid, previous, false);
        update(grid, index, true);
    }

    void resetSelection() {
        selected = -1;
    }

    private static void update(RecyclerView grid, int position, boolean isSelected) {
        if (position < 0) {
            return;
        }
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            TileViews.setSelected(holder.itemView, isSelected, true);
        }
    }
}
