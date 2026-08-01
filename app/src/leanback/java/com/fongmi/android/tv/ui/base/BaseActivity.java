package com.fongmi.android.tv.ui.base;

import android.content.res.Resources;
import android.os.Looper;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.recyclerview.widget.RecyclerView;

import me.jessyan.autosize.AutoSizeCompat;

public abstract class BaseActivity extends AbsActivity {

    @Override
    public boolean isLeanback() {
        return true;
    }

    @Override
    public Resources getResources() {
        Resources resources = super.getResources();
        if (Looper.myLooper() == Looper.getMainLooper()) AutoSizeCompat.autoConvertDensityOfGlobal(resources);
        return resources;
    }

    public void notifyItemChanged(RecyclerView view, ArrayObjectAdapter adapter) {
        if (view.isComputingLayout()) view.post(() -> notifyItemChanged(view, adapter));
        else adapter.notifyArrayItemRangeChanged(0, adapter.size());
    }

    public void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        if (view.isComputingLayout()) view.post(() -> notifyItemChanged(view, adapter));
        else adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }
}
