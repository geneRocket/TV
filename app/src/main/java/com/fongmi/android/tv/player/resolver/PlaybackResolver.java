package com.fongmi.android.tv.player.resolver;

public interface PlaybackResolver {

    ResolveHandle resolve(ResolveRequest request, Callback callback);

    interface Callback {
        default void onParsing() {}
        void onResolved(ResolveResult result);
        void onFailure(ResolveFailure failure);
    }
}
