package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterSearchWordBinding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class WordAdapter extends RecyclerView.Adapter<WordAdapter.ViewHolder> {

    private static final int MAX_SIZE = 20;
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
        replaceItems(normalize(items, new LinkedHashSet<>()));
    }

    public void clear() {
        if (mItems.isEmpty()) return;
        mItems.clear();
        notifyDataSetChanged();
    }

    public void appendAll(List<String> items) {
        replaceItems(normalize(items, new LinkedHashSet<>(mItems)));
    }

    private void replaceItems(List<String> items) {
        if (mItems.equals(items)) return;
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    private List<String> normalize(List<String> items, LinkedHashSet<String> set) {
        if (items != null) {
            for (String item : items) {
                if (item == null) continue;
                String text = item.trim();
                if (text.isEmpty()) continue;
                set.add(text);
                if (set.size() >= MAX_SIZE) break;
            }
        }
        return new ArrayList<>(set);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.word.setText(mItems.get(position));
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterSearchWordBinding binding;

        public ViewHolder(@NonNull AdapterSearchWordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            mListener.onItemClick(mItems.get(getLayoutPosition()));
        }
    }
}
