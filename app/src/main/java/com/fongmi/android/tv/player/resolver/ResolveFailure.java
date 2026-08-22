package com.fongmi.android.tv.player.resolver;

public final class ResolveFailure {

    public enum Kind {
        CANCELLED,
        TIMEOUT,
        EXTRACT,
        UNKNOWN
    }

    private final long requestId;
    private final Kind kind;
    private final Throwable cause;

    public ResolveFailure(long requestId, Kind kind, Throwable cause) {
        this.requestId = requestId;
        this.kind = kind;
        this.cause = cause;
    }

    public long getRequestId() { return requestId; }

    public Kind getKind() { return kind; }

    public Throwable getCause() { return cause; }
}
