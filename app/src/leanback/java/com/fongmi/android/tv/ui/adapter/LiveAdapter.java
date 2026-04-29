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
        setHasStableIds(true);
    }

    public void setAction(boolean action) {
        if (this.action == action) return;
        this.action = action;
        notifyItemRangeChanged(0, getItemCount());
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

    @Override
    public long getItemId(int position) {
        return mItems.get(position).getName().hashCode();
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
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterLiveBinding binding;

        public ViewHolder(@NonNull AdapterLiveBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.text.setOnClickListener(v -> onTextClick());
            binding.boot.setOnClickListener(v -> onBootClick());
            binding.pass.setOnClickListener(v -> onPassClick());
            binding.boot.setOnLongClickListener(v -> onBootLongClick());
            binding.pass.setOnLongClickListener(v -> onPassLongClick());
        }

        private int getAdapterIndex() {
            return getBindingAdapterPosition();
        }

        private void onTextClick() {
            int index = getAdapterIndex();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onItemClick(mItems.get(index));
        }

        private void onBootClick() {
            int index = getAdapterIndex();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onBootClick(index, mItems.get(index));
        }

        private void onPassClick() {
            int index = getAdapterIndex();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onPassClick(index, mItems.get(index));
        }

        private boolean onBootLongClick() {
            int index = getAdapterIndex();
            return index != RecyclerView.NO_POSITION && mListener.onBootLongClick(mItems.get(index));
        }

        private boolean onPassLongClick() {
            int index = getAdapterIndex();
            return index != RecyclerView.NO_POSITION && mListener.onPassLongClick(mItems.get(index));
        }
    }
}
