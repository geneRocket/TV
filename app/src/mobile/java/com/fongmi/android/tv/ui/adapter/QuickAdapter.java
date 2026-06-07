package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterQuickBinding;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuickAdapter extends RecyclerView.Adapter<QuickAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Vod> mItems;

    public QuickAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Vod item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Vod> items) {
        if (items == null || items.isEmpty()) return;
        int position = mItems.size();
        List<Vod> added = filterNew(items);
        if (added.isEmpty()) return;
        mItems.addAll(added);
        notifyItemRangeInserted(position, added.size());
    }

    public void sort(Comparator<Vod> comparator) {
        Collections.sort(mItems, comparator);
        notifyDataSetChanged();
    }

    public void sort(Comparator<Vod> comparator) {
        Collections.sort(mItems, comparator);
        notifyDataSetChanged();
    }

    public Vod get(int position) {
        return mItems.get(position);
    }

    public Vod poll(Set<String> broken, String currentKey, String currentId) {
        while (!mItems.isEmpty()) {
            Vod item = mItems.remove(0);
            notifyItemRemoved(0);
            if (item == null) continue;
            if (item.getSiteKey().equals(currentKey) && item.getVodId().equals(currentId)) continue;
            if (broken != null && broken.contains(key(item))) continue;
            return item;
        }
        return null;
    }

    public void remove(int position) {
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    private List<Vod> filterNew(List<Vod> items) {
        List<Vod> added = new ArrayList<>();
        Set<String> loaded = new HashSet<>();
        for (Vod item : mItems) loaded.add(key(item));
        for (Vod item : items) {
            String key = key(item);
            if (key.isEmpty() || !loaded.add(key)) continue;
            added.add(item);
        }
        return added;
    }

    private String key(Vod item) {
        if (item == null) return "";
        String site = item.getSiteKey();
        String id = item.getVodId();
        String name = item.getVodName();
        return site + "@" + (id.isEmpty() ? Util.normalize(name) : id);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterQuickBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vod item = mItems.get(position);
        holder.binding.name.setText(item.getVodName());
        holder.binding.site.setText(item.getSiteName());
        holder.binding.remark.setText(item.getVodRemarks());
        holder.binding.getRoot().setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onItemClick(mItems.get(index));
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterQuickBinding binding;

        ViewHolder(@NonNull AdapterQuickBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
