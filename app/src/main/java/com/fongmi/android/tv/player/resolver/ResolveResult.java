package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;

public final class ResolveResult {

    private final long requestId;
    private final Result result;

    public ResolveResult(long requestId, Result result) {
        this.requestId = requestId;
        this.result = result == null ? Result.empty() : result;
    }

    public long getRequestId() { return requestId; }

    public Result getResult() { return result; }
}
