package com.fongmi.android.tv.player.resolver;

public interface PlaybackResolver {

    ResolveHandle resolve(ResolveRequest request, Callback callback);

    default void prefetch(ResolveRequest request) {}

    default void cancelPrefetch() {}

    interface Callback {
        void onResolved(ResolveResult result);
        void onFailure(ResolveFailure failure);
    }
}
