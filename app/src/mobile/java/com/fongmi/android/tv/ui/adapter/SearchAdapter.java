package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodOneBinding;
import com.fongmi.android.tv.databinding.AdapterVodRectBinding;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.holder.VodOneHolder;
import com.fongmi.android.tv.ui.holder.VodRectHolder;
import com.fongmi.android.tv.utils.SearchSorter;

import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<BaseVodHolder> {

    private final VodAdapter.OnClickListener mListener;
    private final List<Vod> mItems;
    private String keyword;
    private int viewType;
    private int[] size;

    public SearchAdapter(VodAdapter.OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setViewType(int viewType, int count) {
        if (this.viewType > 0 && this.viewType != viewType && count == 1) notifyDataSetChanged();
        Setting.putViewType(viewType);
        this.viewType = viewType;
    }

    public void setSize(int[] size) {
        this.size = size;
    }

    public int getWidth() {
        return size[0];
    }

    public boolean isList() {
        return viewType == ViewType.LIST;
    }

    public boolean isGrid() {
        return viewType == ViewType.GRID;
    }

    public void setAll(List<Vod> items) {
        clear().addAll(items);
    }

    public void addAll(List<Vod> items) {
        if (items == null || items.isEmpty()) return;
        List<Vod> newItems = SearchSorter.merge(mItems, items, keyword);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return mItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Vod oldItem = mItems.get(oldItemPosition);
                Vod newItem = newItems.get(newItemPosition);
                return oldItem.getSiteKey().equals(newItem.getSiteKey()) && oldItem.getVodId().equals(newItem.getVodId()) && oldItem.getVodName().equals(newItem.getVodName());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return mItems.get(oldItemPosition).equals(newItems.get(newItemPosition));
            }
        });
        mItems.clear();
        mItems.addAll(newItems);
        result.dispatchUpdatesTo(this);
    }

    public SearchAdapter clear() {
        mItems.clear();
        notifyDataSetChanged();
        return this;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @Override
    public void onBindViewHolder(@NonNull BaseVodHolder holder, int position) {
        holder.initView(mItems.get(position));
    }

    @NonNull
    @Override
    public BaseVodHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ViewType.LIST) return new VodOneHolder(AdapterVodOneBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener);
        else return new VodRectHolder(AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener).size(size);
    }
}
