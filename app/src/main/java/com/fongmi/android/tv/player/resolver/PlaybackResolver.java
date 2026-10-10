package com.fongmi.android.tv.player.resolver;

public interface PlaybackResolver {

    ResolveHandle resolve(ResolveRequest request, Callback callback);

    interface Callback {
        void onResolved(ResolveResult result);
        void onFailure(ResolveFailure failure);
    }
}
