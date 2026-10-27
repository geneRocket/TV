package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Subtitle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SearchSubtitleAdapter extends RecyclerView.Adapter<SearchSubtitleAdapter.ViewHolder> {

    private final List<Subtitle> items;
    private final OnClickListener listener;

    public SearchSubtitleAdapter(OnClickListener listener) {
        this.items = new ArrayList<>();
        this.listener = listener;
    }

    public void addAll(List<Subtitle> items) {
        if (items == null || items.isEmpty()) return;
        int start = this.items.size();
        this.items.addAll(items);
        notifyItemRangeInserted(start, items.size());
    }

    public void clear() {
        int count = this.items.size();
        this.items.clear();
        if (count > 0) notifyItemRangeRemoved(0, count);
    }

    public void replace(List<Subtitle> values) {
        List<Subtitle> previous = new ArrayList<>(items);
        List<Subtitle> next = new ArrayList<>(values);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return previous.size(); }
            public int getNewListSize() { return next.size(); }
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                Subtitle old = previous.get(oldPosition), value = next.get(newPosition);
                return Objects.equals(old.getUrl(), value.getUrl()) && Objects.equals(old.getSource(), value.getSource());
            }
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Subtitle old = previous.get(oldPosition), value = next.get(newPosition);
                return Objects.equals(old.getName(), value.getName()) && Objects.equals(old.getLanguage(), value.getLanguage())
                        && Objects.equals(old.getIsZip(), value.getIsZip()) && old.isExternal() == value.isExternal();
            }
        });
        items.clear(); items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_subtitle, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Subtitle item = items.get(position);
        holder.name.setText(item.getName());
        List<String> metadata = new ArrayList<>();
        if (item.getSource() != null && !item.getSource().isEmpty()) metadata.add(item.getSource());
        if (!item.getLanguage().isEmpty()) metadata.add(item.getLanguage());
        if (item.isExternal()) metadata.add(holder.itemView.getContext().getString(R.string.subtitle_website_result));
        holder.meta.setText(android.text.TextUtils.join(" · ",metadata));
        holder.meta.setVisibility(metadata.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView name;
        public TextView meta;

        public ViewHolder(android.view.View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            meta = itemView.findViewById(R.id.meta);
            itemView.setOnClickListener(v -> {
                RecyclerView.Adapter<?> adapter = getBindingAdapter();
                if (!(adapter instanceof SearchSubtitleAdapter)) return;
                SearchSubtitleAdapter self = (SearchSubtitleAdapter) adapter;
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                Subtitle item = self.items.get(position);
                self.listener.onItemClick(item, position);
            });
        }
    }

    public interface OnClickListener {
        void onItemClick(Subtitle item, int position);
    }
}
