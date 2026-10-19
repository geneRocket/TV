package com.fongmi.android.tv.player;

/** A request for one playback page, rather than an application-wide refresh. */
public final class PlaybackCommand {
    public enum Type { DETAIL, RELOAD, SUBTITLE, DANMAKU }

    private final Type type;
    private final String path;

    private PlaybackCommand(Type type, String path) {
        this.type = type;
        this.path = path;
    }

    public static PlaybackCommand fromRefresh(String type, String path) {
        if ("detail".equals(type)) return new PlaybackCommand(Type.DETAIL, path);
        if ("player".equals(type)) return new PlaybackCommand(Type.RELOAD, path);
        if ("subtitle".equals(type)) return new PlaybackCommand(Type.SUBTITLE, path);
        if ("danmaku".equals(type)) return new PlaybackCommand(Type.DANMAKU, path);
        return null;
    }

    public Type getType() { return type; }
    public String getPath() { return path; }
}
