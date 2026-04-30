package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.AdapterCacheDirBinding;

import java.util.ArrayList;
import java.util.List;

public class CacheDirAdapter extends RecyclerView.Adapter<CacheDirAdapter.ViewHolder> {

    private final CacheDirAdapter.OnClickListener mListener;
    private List<String> mItems;

    public CacheDirAdapter(OnClickListener listener, List<String> items) {
        this.mListener = listener;
        this.mItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
        setHasStableIds(true);
    }

    public interface OnClickListener {

        void onItemClick(String path);

    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public long getItemId(int position) {
        return mItems.get(position).hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterCacheDirBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = mItems.get(position);
        boolean selected = Setting.getThunderCacheDir().equals(item);
        holder.binding.text.setText(item);
        holder.binding.text.setSelected(selected);
        holder.binding.text.setActivated(selected);
        holder.binding.text.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onItemClick(mItems.get(index));
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterCacheDirBinding binding;

        public ViewHolder(@NonNull AdapterCacheDirBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
