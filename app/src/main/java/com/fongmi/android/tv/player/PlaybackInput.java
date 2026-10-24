package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Sub;

/** A semantic user input shared by page controls, time bars and media sessions. */
public final class PlaybackInput {
    public enum Type { PLAY, PAUSE, SEEK, NEXT, PREVIOUS, STOP, SUBTITLE, PLAYER, DECODE }

    private final Type type;
    private final long position;
    private final Sub subtitle;
    private final int player;
    private final boolean persist;

    private PlaybackInput(Type type, long position, Sub subtitle) {
        this(type, position, subtitle, -1, false);
    }

    private PlaybackInput(Type type, long position, Sub subtitle, int player, boolean persist) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        this.type = type;
        this.position = Math.max(position, 0);
        this.subtitle = subtitle;
        this.player = player;
        this.persist = persist;
    }

    public static PlaybackInput action(Type type) {
        if (type == Type.SEEK || type == Type.SUBTITLE || type == Type.PLAYER || type == Type.DECODE)
            throw new IllegalArgumentException("input requires a value");
        return new PlaybackInput(type, 0, null);
    }

    public static PlaybackInput seek(long position) { return new PlaybackInput(Type.SEEK, position, null); }
    public static PlaybackInput subtitle(Sub subtitle) { return new PlaybackInput(Type.SUBTITLE, 0, subtitle); }
    public static PlaybackInput player(int player) { return new PlaybackInput(Type.PLAYER, 0, null, player, true); }
    public static PlaybackInput decode(boolean persist) { return new PlaybackInput(Type.DECODE, 0, null, -1, persist); }
    public Type getType() { return type; }
    public long getPosition() { return position; }
    public Sub getSubtitle() { return subtitle; }
    public int getPlayer() { return player; }
    public boolean isPersistent() { return persist; }

    public interface Handler {
        void dispatch(PlaybackInput input);
        default boolean playWhenReady(boolean requested) { return requested; }
        default void onOwnershipLost() {}
    }
}
