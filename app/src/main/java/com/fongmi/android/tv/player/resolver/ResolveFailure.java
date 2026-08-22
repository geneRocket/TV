package com.fongmi.android.tv.player.resolver;

public final class ResolveFailure {

    private final long requestId;
    private final Throwable cause;

    public ResolveFailure(long requestId, Throwable cause) {
        if (requestId <= 0) throw new IllegalArgumentException("requestId must be positive");
        this.requestId = requestId;
        this.cause = cause == null ? new IllegalStateException("playback resolve failed") : cause;
    }

    public long getRequestId() { return requestId; }

    public Throwable getCause() { return cause; }
}
