package com.fongmi.android.tv.player;

public final class EpisodeNavigator {

    private EpisodeNavigator() {
    }

    public static int next(int current, int size) {
        return move(current, size, 1);
    }

    public static int previous(int current, int size) {
        return move(current, size, -1);
    }

    private static int move(int current, int size, int delta) {
        if (size <= 0) return -1;
        int safeCurrent = Math.max(0, Math.min(current, size - 1));
        return Math.max(0, Math.min(safeCurrent + delta, size - 1));
    }
}
