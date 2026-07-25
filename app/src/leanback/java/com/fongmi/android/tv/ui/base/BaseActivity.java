package com.fongmi.android.tv.ui.base;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.recyclerview.widget.RecyclerView;

public abstract class BaseActivity extends AbsActivity {

    @Override
    public boolean isLeanback() {
        return true;
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
