package com.fongmi.android.tv.player;

/** Accumulates preview offsets; reversing direction walks back from the preview. */
public final class RemoteSeekAccumulator {
    private long offset;
    public int step(boolean forward, int repeatCount, int stepMillis) {
        int multiplier = repeatCount < 10 ? 1 : repeatCount < 30 ? 2 : 4;
        offset += (forward ? 1L : -1L) * multiplier * stepMillis;
        offset = Math.max(-Integer.MAX_VALUE, Math.min(Integer.MAX_VALUE, offset));
        return value();
    }
    public int value() { return (int) offset; }
    public void reset() { offset = 0; }
}
