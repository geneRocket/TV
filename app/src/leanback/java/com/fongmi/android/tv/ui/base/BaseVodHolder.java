package com.fongmi.android.tv.ui.base;

import android.view.View;

import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.Vod;

public abstract class BaseVodHolder extends Presenter.ViewHolder {

    private Vod item;

    public BaseVodHolder(View view) {
        super(view);
    }

    public abstract void initView(Vod item);

    protected final void setItem(Vod item) {
        this.item = item;
    }

    protected final Vod getItem() {
        return item;
    }

    public void onUnbind() {
        item = null;
    }
}
