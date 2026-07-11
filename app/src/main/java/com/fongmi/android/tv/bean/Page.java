package com.fongmi.android.tv.bean;

import androidx.annotation.Nullable;

public class Page<A, E> {

    private final String vodId;
    private final Style style;
    private final int position;
    private final int childPosition;
    private final A adapter;
    private final E extra;
    private final boolean enable;
    private final int page;

    public static <A, E> Page<A, E> get(Vod vod, int position, A adapter, E extra, int page, boolean enable) {
        return new Page<>(vod, position, -1, adapter, extra, page, enable);
    }

    public static <A, E> Page<A, E> get(Vod vod, int position, int childPosition, A adapter, E extra, int page, boolean enable) {
        return new Page<>(vod, position, childPosition, adapter, extra, page, enable);
    }

    private Page(Vod vod, int position, int childPosition, A adapter, E extra, int page, boolean enable) {
        this.vodId = vod.getVodId();
        this.style = vod.getCate() != null ? vod.getCate().getStyle() : null;
        this.position = position;
        this.childPosition = childPosition;
        this.adapter = adapter;
        this.extra = extra;
        this.page = page;
        this.enable = enable;
    }

    public String getVodId() {
        return vodId;
    }

    public Style getStyle() {
        return style;
    }

    public int getPosition() {
        return position;
    }

    public int getChildPosition() {
        return childPosition;
    }

    public A getAdapter() {
        return adapter;
    }

    public E getExtra() {
        return extra;
    }

    public boolean isEnable() {
        return enable;
    }

    public int getPage() {
        return page;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Page<?, ?>)) return false;
        Page<?, ?> it = (Page<?, ?>) obj;
        return getVodId().equals(it.getVodId()) && getPosition() == it.getPosition() && getChildPosition() == it.getChildPosition();
    }
}
