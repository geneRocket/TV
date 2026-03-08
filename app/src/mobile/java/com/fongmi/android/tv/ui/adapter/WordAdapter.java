package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterCollectWordBinding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WordAdapter extends RecyclerView.Adapter<WordAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<String> mItems;

    public WordAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.mListener = listener;
    }

    public interface OnClickListener {

        void onItemClick(String text);
    }

    public void addAll(List<String> items) {
        mItems.clear();
        appendUnique(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void appendAll(List<String> items) {
        appendUnique(items);
        notifyDataSetChanged();
    }

    private void appendUnique(List<String> items) {
        if (items == null || items.isEmpty()) return;
        Set<String> values = new LinkedHashSet<>(mItems);
        for (int i = 0; i < items.size() && values.size() < 20; i++) {
            String item = items.get(i);
            if (item == null || item.isEmpty()) continue;
            values.add(item);
        }
        mItems.clear();
        mItems.addAll(values);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterCollectWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = mItems.get(position);
        holder.binding.text.setText(item);
        holder.binding.text.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onItemClick(mItems.get(index));
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterCollectWordBinding binding;

        ViewHolder(@NonNull AdapterCollectWordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
