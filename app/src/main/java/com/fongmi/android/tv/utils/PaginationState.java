package com.fongmi.android.tv.utils;

/** Pagination after a completed category or search request. */
public final class PaginationState {

    private final int page;
    private final boolean more;

    private PaginationState(int page, boolean more) {
        this.page = page;
        this.more = more;
    }

    public static PaginationState complete(int requestedPage, int pageCount, int itemCount, boolean failed) {
        int page = Math.max(1, requestedPage);
        if (failed) return new PaginationState(Math.max(1, page - 1), true);
        if (itemCount == 0) return new PaginationState(Math.max(1, page - 1), false);
        return new PaginationState(page, pageCount <= 0 || page < pageCount);
    }

    public int getPage() {
        return page;
    }

    public boolean canLoadMore() {
        return more;
    }
}
