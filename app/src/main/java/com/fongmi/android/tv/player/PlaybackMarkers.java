package com.fongmi.android.tv.player;

/** Pure rules for deriving intro/outro skip durations from the current timeline. */
public final class PlaybackMarkers {

    public static final long INVALID = -1;

    private PlaybackMarkers() {}

    public static long openingAt(long position, long duration) {
        if (!hasTimeline(position, duration) || position > duration / 2) return INVALID;
        return position;
    }

    public static long endingAt(long position, long duration) {
        if (!hasTimeline(position, duration) || position < duration / 2) return INVALID;
        return Math.max(duration - position, 0);
    }

    private static boolean hasTimeline(long position, long duration) {
        return position >= 0 && duration > 0 && position <= duration;
    }
}
