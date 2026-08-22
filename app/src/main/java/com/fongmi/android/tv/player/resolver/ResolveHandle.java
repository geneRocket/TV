package com.fongmi.android.tv.player.resolver;

public interface ResolveHandle {
    long getRequestId();
    void cancel();
}
