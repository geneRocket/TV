package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.AdapterConfigBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private List<Config> mItems;
    private final Set<Config> mSelected;
    private boolean mMultiSelect;
    private boolean mIncludeCurrent;

    public ConfigAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mSelected = new LinkedHashSet<>();
        setHasStableIds(true);
    }

    public interface OnClickListener {

        void onTextClick(Config item);

        void onDeleteClick(Config item);
    }

    public ConfigAdapter addAll(int type) {
        mItems = Config.getAll(type);
        if (!mIncludeCurrent) mItems.remove(type == 0 ? VodConfig.get().getConfig() : LiveConfig.get().getConfig());
        mSelected.clear();
        return this;
    }

    public void setItems(List<Config> items) {
        List<Config> newItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
        if (newItems.equals(mItems) && mSelected.isEmpty()) return;
        mItems = newItems;
        mSelected.clear();
        notifyDataSetChanged();
    }

    public ConfigAdapter multi(boolean multi) {
        mMultiSelect = multi;
        mSelected.clear();
        return this;
    }

    public ConfigAdapter includeCurrent(boolean includeCurrent) {
        mIncludeCurrent = includeCurrent;
        return this;
    }

    public void toggle(Config item) {
        int index = mItems.indexOf(item);
        if (index == -1) return;
        if (mSelected.contains(item)) mSelected.remove(item);
        else mSelected.add(item);
        notifyItemChanged(index);
    }

    public List<Config> getSelected() {
        return new ArrayList<>(mSelected);
    }

    public void select(List<String> urls) {
        Set<Config> oldSelected = new LinkedHashSet<>(mSelected);
        mSelected.clear();
        if (mItems == null || urls == null || urls.isEmpty()) {
            notifySelectionChanged(oldSelected);
            return;
        }
        Set<String> values = new HashSet<>();
        for (String url : urls) if (url != null && !url.trim().isEmpty()) values.add(url.trim());
        for (Config item : mItems) if (values.contains(item.getUrl())) mSelected.add(item);
        notifySelectionChanged(oldSelected);
    }

    private void notifySelectionChanged(Set<Config> oldSelected) {
        if (mItems == null) return;
        for (int i = 0; i < mItems.size(); i++) {
            Config item = mItems.get(i);
            if (oldSelected.contains(item) != mSelected.contains(item)) notifyItemChanged(i);
        }
    }

    public int remove(Config item) {
        int index = mItems.indexOf(item);
        if (index == -1) return index;
        item.delete();
        mSelected.remove(item);
        mItems.remove(index);
        notifyItemRemoved(index);
        return index;
    }

    @Override
    public int getItemCount() {
        return mItems == null ? 0 : mItems.size();
    }

    @Override
    public long getItemId(int position) {
        Config item = mItems.get(position);
        return item.getId();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterConfigBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Config item = mItems.get(position);
        boolean selected = mSelected.contains(item);
        String text = mMultiSelect ? (selected ? "\u2713 " : "") + item.getDesc() : item.getDesc();
        holder.binding.text.setText(text);
        holder.binding.text.setActivated(selected);
        holder.binding.text.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onTextClick(mItems.get(index));
        });
        holder.binding.delete.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onDeleteClick(mItems.get(index));
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterConfigBinding binding;

        public ViewHolder(@NonNull AdapterConfigBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
