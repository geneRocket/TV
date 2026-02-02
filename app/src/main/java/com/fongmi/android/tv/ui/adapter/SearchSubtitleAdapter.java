package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Subtitle;

import java.util.ArrayList;
import java.util.List;

public class SearchSubtitleAdapter extends RecyclerView.Adapter<SearchSubtitleAdapter.ViewHolder> {

    private final List<Subtitle> items;
    private final OnClickListener listener;

    public SearchSubtitleAdapter(OnClickListener listener) {
        this.items = new ArrayList<>();
        this.listener = listener;
    }

    public void addAll(List<Subtitle> items) {
        if (items != null) {
            this.items.addAll(items);
            notifyDataSetChanged();
        }
    }

    public void clear() {
        this.items.clear();
        notifyDataSetChanged();
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
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item, position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView name;

        public ViewHolder(android.view.View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
        }
    }

    public interface OnClickListener {
        void onItemClick(Subtitle item, int position);
    }
}
