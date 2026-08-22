package com.fongmi.android.tv.player.resolver;

public final class ResolveFailure {

    private final long requestId;
    private final Throwable cause;

    public ResolveFailure(long requestId, Throwable cause) {
        this.requestId = requestId;
        this.cause = cause;
    }

    public long getRequestId() { return requestId; }

    public Throwable getCause() { return cause; }
}
