package com.fongmi.android.tv.player;

/** Pure timeline rules for converting a relative seek gesture into an absolute position. */
public final class PlaybackSeek {

    private PlaybackSeek() {}

    public static long target(long position, long duration, long offset) {
        long current = Math.max(position, 0);
        long target;
        try {
            target = Math.addExact(current, offset);
        } catch (ArithmeticException ignored) {
            target = offset > 0 ? Long.MAX_VALUE : 0;
        }
        target = Math.max(target, 0);
        return duration > 0 ? Math.min(target, duration) : target;
    }
}
