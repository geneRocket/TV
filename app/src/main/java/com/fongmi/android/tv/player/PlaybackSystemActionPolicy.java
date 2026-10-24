package com.fongmi.android.tv.player;

/** Notification dismissal manages background playback, never closes a foreground player page. */
public final class PlaybackSystemActionPolicy {
    private PlaybackSystemActionPolicy() {}
    public static boolean accepts(boolean dismissal, boolean foreground) { return !dismissal || !foreground; }
}
