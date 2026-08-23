package com.fongmi.android.tv.player.vod;

/** Owns one-shot playback intent that cannot be derived from episode history. */
public final class VodPlaybackIntent {

    private boolean keepCurrentPositionOnNextPlay;

    public void keepCurrentPositionOnNextPlay() {
        keepCurrentPositionOnNextPlay = true;
    }

    public boolean hasKeepCurrentPositionOnNextPlay() {
        return keepCurrentPositionOnNextPlay;
    }

    public StartPolicy consumeStartPolicy(boolean replay, boolean keepCurrentPositionForActiveFlow) {
        boolean keepCurrentPosition = keepCurrentPositionOnNextPlay || keepCurrentPositionForActiveFlow;
        keepCurrentPositionOnNextPlay = false;
        if (replay) return StartPolicy.FRESH;
        return keepCurrentPosition ? StartPolicy.KEEP_CURRENT_POSITION : StartPolicy.RESUME_SAVED;
    }

    public void clear() {
        keepCurrentPositionOnNextPlay = false;
    }
}
