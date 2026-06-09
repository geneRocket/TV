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
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.Collections;
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
        for (Vod item : items) item.setScore(Util.similarity(item.getVodName(), keyword));
        mItems.addAll(items);
        sort();
    }

    private void sort() {
        if (keyword == null || keyword.isEmpty()) return;
        List<Vod> oldItems = new ArrayList<>(mItems);
        Collections.sort(mItems, (o1, o2) -> Double.compare(o2.getScore(), o1.getScore()));
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return mItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).getVodId().equals(mItems.get(newItemPosition).getVodId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).equals(mItems.get(newItemPosition));
            }
        }).dispatchUpdatesTo(this);
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
