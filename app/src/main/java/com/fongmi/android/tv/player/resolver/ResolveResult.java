package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;

public final class ResolveResult {

    private final long requestId;
    private final Result result;

    public ResolveResult(long requestId, Result result) {
        if (requestId <= 0) throw new IllegalArgumentException("requestId must be positive");
        if (result == null) throw new IllegalArgumentException("result must not be null");
        this.requestId = requestId;
        this.result = result;
    }

    public long getRequestId() { return requestId; }

    public Result getResult() { return result; }
}
