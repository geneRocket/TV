package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterEpisodeGridBinding;
import com.fongmi.android.tv.databinding.AdapterEpisodeHoriBinding;
import com.fongmi.android.tv.databinding.AdapterEpisodeVertBinding;
import com.fongmi.android.tv.player.EpisodeNavigator;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.holder.EpisodeGridHolder;
import com.fongmi.android.tv.ui.holder.EpisodeHoriHolder;
import com.fongmi.android.tv.ui.holder.EpisodeVertHolder;

import java.util.ArrayList;
import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<BaseEpisodeHolder> {

    private final OnClickListener mListener;
    private final List<Episode> mItems;
    private final int viewType;

    public EpisodeAdapter(OnClickListener listener, int viewType) {
        this(listener, viewType, new ArrayList<>());
    }

    public EpisodeAdapter(OnClickListener listener, int viewType, ArrayList<Episode> items) {
        this.mListener = listener;
        this.viewType = viewType;
        this.mItems = items;
    }

    public interface OnClickListener {

        void onItemClick(Episode item);
    }

    public void addAll(List<Episode> items) {
        mItems.clear();
        if (items != null) for (Episode item : items) if (item != null) mItems.add(item);
        notifyDataSetChanged();
    }

    public int getPosition() {
        return Math.max(findActivatedPosition(), 0);
    }

    public int getPosition(Episode item) {
        return mItems.indexOf(item);
    }

    public Episode getActivated() {
        int position = findActivatedPosition();
        return position < 0 ? null : mItems.get(position);
    }

    public Episode getNext() {
        if (mItems.isEmpty()) return null;
        return mItems.get(EpisodeNavigator.next(findActivatedPosition(), getItemCount()));
    }

    public Episode getPrev() {
        if (mItems.isEmpty()) return null;
        return mItems.get(EpisodeNavigator.previous(findActivatedPosition(), getItemCount()));
    }

    private int findActivatedPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isActivated()) return i;
        return -1;
    }

    public List<Episode> getItems() {
        return mItems;
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
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
    public void onBindViewHolder(@NonNull BaseEpisodeHolder holder, int position) {
        holder.initView(mItems.get(position));
    }

    @NonNull
    @Override
    public BaseEpisodeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case ViewType.HORI:
                return new EpisodeHoriHolder(AdapterEpisodeHoriBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener);
            case ViewType.VERT:
                return new EpisodeVertHolder(AdapterEpisodeVertBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener);
            default:
                return new EpisodeGridHolder(AdapterEpisodeGridBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener);
        }
    }
}
