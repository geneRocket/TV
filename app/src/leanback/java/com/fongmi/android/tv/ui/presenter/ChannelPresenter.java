package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.databinding.AdapterChannelBinding;
import com.fongmi.android.tv.utils.ImgUtil;

public class ChannelPresenter extends Presenter {

    private final OnClickListener mListener;

    public ChannelPresenter(OnClickListener listener) {
        this.mListener = listener;
    }

    public interface OnClickListener {

        void showEpg(Channel item);

        void onItemClick(Channel item);

        boolean onLongClick(Channel item);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(AdapterChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object object) {
        Channel item = (Channel) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        item.loadLogo(holder.binding.logo);
        holder.binding.name.setText(item.getName());
        holder.binding.number.setText(item.getNumber());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.item = item;
        holder.binding.epg.setVisibility(item.getData().getList().isEmpty() || !item.isSelected() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.item = null;
        ImgUtil.clear(holder.binding.logo, ImageView.ScaleType.FIT_CENTER);
        holder.binding.epg.setVisibility(View.GONE);
    }

    public class ViewHolder extends Presenter.ViewHolder {

        private final AdapterChannelBinding binding;
        private Channel item;

        public ViewHolder(@NonNull AdapterChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.binding.getRoot().setOnClickListener(view -> {
                if (item != null) mListener.onItemClick(item);
            });
            this.binding.getRoot().setOnLongClickListener(view -> item != null && mListener.onLongClick(item));
            this.binding.getRoot().setRightListener(() -> {
                if (item != null) mListener.showEpg(item);
            });
        }
    }
}
