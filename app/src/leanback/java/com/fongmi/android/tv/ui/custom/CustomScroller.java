package com.fongmi.android.tv.ui.custom;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Result;

public class CustomScroller extends RecyclerView.OnScrollListener {

    private static final int PREFETCH_DISTANCE = 2;

    private final Callback callback;
    private boolean loading;
    private boolean enable;
    private int page;

    public CustomScroller(Callback callback) {
        this.callback = callback;
        this.enable = true;
        this.page = 1;
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView view, int newState) {
        if (isDisable() || isLoading() || newState != RecyclerView.SCROLL_STATE_IDLE) return;
        if (isNearEnd(view)) callback.onLoadMore(String.valueOf(++page));
    }

    private boolean isNearEnd(RecyclerView view) {
        if (view == null || view.getLayoutManager() == null || view.getLayoutManager().getItemCount() == 0 || view.getLayoutManager().getChildCount() == 0) return false;
        int lastPosition = RecyclerView.NO_POSITION;
        for (int i = 0; i < view.getLayoutManager().getChildCount(); i++) {
            View child = view.getLayoutManager().getChildAt(i);
            if (child != null) lastPosition = Math.max(lastPosition, view.getLayoutManager().getPosition(child));
        }
        return lastPosition >= view.getLayoutManager().getItemCount() - PREFETCH_DISTANCE;
    }

    public void reset() {
        enable = true;
        loading = false;
        page = 1;
    }

    public int addPage() {
        loading = true;
        return ++page;
    }

    public int getPage() {
        return page;
    }

    public void restore(int page, boolean enable) {
        this.loading = false;
        this.enable = enable;
        this.page = page;
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean isDisable() {
        return !enable;
    }

    public void setEnable(int pageCount) {
        this.enable = page < pageCount || pageCount == 0;
    }

    public void endLoading(Result result) {
        if (result.getList().isEmpty()) page = Math.max(1, page - 1);
        setEnable(result.getPageCount());
        setLoading(false);
    }

    public interface Callback {
        void onLoadMore(String page);
    }
}
