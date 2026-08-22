package com.fongmi.android.tv.ui.player;

/** Owns TV list selections; adapters render these values but do not own playback state. */
public final class TvVodSelectionState {

    private int flag;
    private int episode;
    private int parse;

    public int flag(int size) { return clamp(flag, size); }
    public int episode(int size) { return clamp(episode, size); }
    public int parse(int size) { return clamp(parse, size); }

    public void selectFlag(int index, int size) { flag = clamp(index, size); }
    public void selectEpisode(int index, int size) { episode = clamp(index, size); }
    public void selectParse(int index, int size) { parse = clamp(index, size); }

    public void resetEpisode() { episode = 0; }

    private int clamp(int value, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(value, size - 1));
    }
}
