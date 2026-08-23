package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterQuickBinding;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchSorter;
import com.fongmi.android.tv.utils.SortedLists;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuickAdapter extends RecyclerView.Adapter<QuickAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Vod> mItems;
    private final Set<String> mKeys;

    public QuickAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
        this.mKeys = new HashSet<>();
    }

    public interface OnClickListener {

        void onItemClick(Vod item);
    }

    public void clear() {
        mItems.clear();
        mKeys.clear();
        notifyDataSetChanged();
    }

    public void addAllSorted(List<Vod> items, Comparator<Vod> comparator) {
        if (items == null || items.isEmpty()) return;
        List<Vod> added = filterNew(items);
        if (added.isEmpty()) return;
        added.sort(comparator);
        List<Vod> merged = SortedLists.merge(mItems, added, comparator);
        mItems.clear();
        mItems.addAll(merged);
        notifyDataSetChanged();
    }

    public Vod get(int position) {
        return mItems.get(position);
    }

    public Vod poll(Set<String> broken, String currentSourceKey) {
        while (!mItems.isEmpty()) {
            Vod item = mItems.remove(0);
            String key = key(item);
            mKeys.remove(key);
            notifyItemRemoved(0);
            if (item == null) continue;
            if (key.equals(currentSourceKey)) continue;
            if (broken != null && broken.contains(key)) continue;
            return item;
        }
        return null;
    }

    public void remove(int position) {
        mKeys.remove(key(mItems.remove(position)));
        notifyItemRemoved(position);
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    private List<Vod> filterNew(List<Vod> items) {
        List<Vod> added = new ArrayList<>();
        for (Vod item : items) {
            String key = key(item);
            if (key.isEmpty() || !mKeys.add(key)) continue;
            added.add(item);
        }
        return added;
    }

    private String key(Vod item) {
        return SearchSorter.key(item);
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
