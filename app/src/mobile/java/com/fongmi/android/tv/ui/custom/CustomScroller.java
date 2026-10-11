package com.fongmi.android.tv.ui.custom;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.utils.PaginationState;

public class CustomScroller extends RecyclerView.OnScrollListener {

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
    public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
        if (isDisable() || isLoading() || view.getScrollState() == RecyclerView.SCROLL_STATE_IDLE || callback == null) return;
        if (!view.canScrollVertically(1) && dy > 0) callback.onLoadMore(String.valueOf(addPage()));
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

    public boolean first() {
        return page == 1;
    }

    public void setPage(int page) {
        this.page = page;
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
        PaginationState state = PaginationState.complete(page, result.getPageCount(), result.getList().size(), result.isRequestFailed());
        page = state.getPage();
        enable = state.canLoadMore();
        loading = false;
    }

    public interface Callback {
        void onLoadMore(String page);
    }
}
