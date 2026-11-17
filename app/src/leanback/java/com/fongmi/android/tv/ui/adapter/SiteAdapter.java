package com.fongmi.android.tv.ui.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteBinding;

import java.util.List;
import java.util.ArrayList;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Site> mItems;
    private final com.fongmi.android.tv.utils.SerialExecutor persistence =
            new com.fongmi.android.tv.utils.SerialExecutor(App::execute,
                    error -> com.fongmi.android.tv.utils.ThreadPools.log(error, "Site settings save failed."));
    private int type;

    public SiteAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = VodConfig.get().getSites();
    }

    public interface OnClickListener {

        void onItemClick(Site item);
    }

    public void setType(int type) {
        if (this.type == type) return;
        this.type = type;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void selectAll() {
        setEnable(type != 3);
    }

    public void cancelAll() {
        setEnable(type == 3);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSiteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = mItems.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.check.setChecked(getChecked(item));
        holder.binding.text.setSelected(item.isActivated());
        holder.binding.text.setActivated(item.isActivated());
        holder.binding.check.setVisibility(type == 0 ? View.GONE : View.VISIBLE);
        holder.binding.text.setGravity(Setting.getSiteMode() == 0 ? Gravity.CENTER : Gravity.START);
    }

    private boolean getChecked(Site item) {
        if (type == 1) return item.isSearchable();
        if (type == 2) return item.isChangeable();
        return false;
    }

    private void setListener(@NonNull ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        Site item = mItems.get(position);
        if (type == 0) mListener.onItemClick(item);
        if (type == 1) saveSearchable(item);
        if (type == 2) saveChangeable(item);
        if (type != 0) notifyItemChanged(position);
    }

    private void saveSearchable(Site item) {
        item.setSearchable(!item.isSearchable());
        persistence.execute(item::save);
    }

    private void saveChangeable(Site item) {
        item.setChangeable(!item.isChangeable());
        persistence.execute(item::save);
    }

    private boolean setLongListener(@NonNull ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return false;
        Site item = mItems.get(position);
        if (type == 1) setEnable(!item.isSearchable());
        if (type == 2) setEnable(!item.isChangeable());
        return true;
    }

    private void setEnable(boolean enable) {
        List<Site> selected = new ArrayList<>(mItems);
        for (Site site : selected) {
            if (type == 1) site.setSearchable(enable);
            if (type == 2) site.setChangeable(enable);
        }
        persistence.execute(() -> selected.forEach(Site::save));
        notifyItemRangeChanged(0, getItemCount());
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private final AdapterSiteBinding binding;

        ViewHolder(@NonNull AdapterSiteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            setListener(this);
        }

        @Override
        public boolean onLongClick(View view) {
            return setLongListener(this);
        }
    }
}
