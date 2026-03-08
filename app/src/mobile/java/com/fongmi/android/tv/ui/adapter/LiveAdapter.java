package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.databinding.AdapterLiveBinding;

import java.util.List;

public class LiveAdapter extends RecyclerView.Adapter<LiveAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Live> mItems;
    private boolean action;

    public LiveAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = LiveConfig.get().getLives();
    }

    public void setAction(boolean action) {
        this.action = action;
    }

    public interface OnClickListener {

        void onItemClick(Live item);

        void onBootClick(int position, Live item);

        void onPassClick(int position, Live item);

        boolean onBootLongClick(Live item);

        boolean onPassLongClick(Live item);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterLiveBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Live item = mItems.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.text.setSelected(item.isActivated());
        holder.binding.text.setActivated(item.isActivated());
        holder.binding.boot.setImageResource(item.getBootIcon());
        holder.binding.pass.setImageResource(item.getPassIcon());
        holder.binding.boot.setVisibility(action ? View.VISIBLE : View.GONE);
        holder.binding.pass.setVisibility(action ? View.VISIBLE : View.GONE);
        holder.binding.text.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onItemClick(mItems.get(index));
        });
        holder.binding.boot.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onBootClick(index, mItems.get(index));
        });
        holder.binding.pass.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onPassClick(index, mItems.get(index));
        });
        holder.binding.boot.setOnLongClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            return index != RecyclerView.NO_POSITION && mListener.onBootLongClick(mItems.get(index));
        });
        holder.binding.pass.setOnLongClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            return index != RecyclerView.NO_POSITION && mListener.onPassLongClick(mItems.get(index));
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterLiveBinding binding;

        ViewHolder(@NonNull AdapterLiveBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
