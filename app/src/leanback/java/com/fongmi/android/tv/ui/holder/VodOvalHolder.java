package com.fongmi.android.tv.ui.holder;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodOvalBinding;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ImgUtil;

public class VodOvalHolder extends BaseVodHolder {

    private final VodPresenter.OnClickListener listener;
    private final AdapterVodOvalBinding binding;

    public VodOvalHolder(@NonNull AdapterVodOvalBinding binding, VodPresenter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        binding.getRoot().setOnClickListener(v -> {
            Vod item = getItem();
            if (item != null) listener.onItemClick(item);
        });
        binding.getRoot().setOnLongClickListener(v -> {
            Vod item = getItem();
            return item != null && listener.onLongClick(item);
        });
    }

    public VodOvalHolder size(int[] size) {
        binding.image.getLayoutParams().width = size[0];
        binding.image.getLayoutParams().height = size[1];
        return this;
    }

    @Override
    public void initView(Vod item) {
        setItem(item);
        binding.name.setText(item.getVodName());
        binding.name.setVisibility(item.getNameVisible());
        ImgUtil.oval(item.getVodName(), item.getVodPic(), item.getSite(), binding.image);
    }

    @Override
    public void onUnbind() {
        super.onUnbind();
        ImgUtil.clear(binding.image);
    }
}
