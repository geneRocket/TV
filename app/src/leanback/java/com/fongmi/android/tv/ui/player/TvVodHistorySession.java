package com.fongmi.android.tv.ui.player;

import androidx.media3.common.C;

/** Owns transient history state for one TV VOD screen session. */
public final class TvVodHistorySession {

    private long preservedFlagPosition = C.TIME_UNSET;

    public void preserveFlagPosition(long position) { preservedFlagPosition = Math.max(position, 0); }
    public boolean hasPreservedFlagPosition() { return preservedFlagPosition != C.TIME_UNSET; }
    public long consumeFlagPosition() {
        long value = preservedFlagPosition;
        preservedFlagPosition = C.TIME_UNSET;
        return value;
    }

    public void clear() {
        preservedFlagPosition = C.TIME_UNSET;
    }
}
