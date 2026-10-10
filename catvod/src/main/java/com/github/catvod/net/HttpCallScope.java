package com.github.catvod.net;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import okhttp3.Call;

/** Owns HTTP calls created by one synchronous request, including spider calls. */
public final class HttpCallScope implements AutoCloseable {
    private static final ThreadLocal<HttpCallScope> CURRENT = new ThreadLocal<>();
    private final Set<Call> calls = new LinkedHashSet<>();
    private boolean closed;

    public static void throwIfCancelled() throws java.io.InterruptedIOException {
        HttpCallScope owner = CURRENT.get();
        if (Thread.currentThread().isInterrupted() || (owner != null && owner.isClosed())) {
            throw new java.io.InterruptedIOException("Request cancelled");
        }
    }

    private synchronized boolean isClosed() { return closed; }

    public static Call track(Call call) {
        HttpCallScope owner = CURRENT.get();
        if (owner != null) owner.add(call);
        return call;
    }

    private synchronized void add(Call call) {
        if (closed) call.cancel();
        else calls.add(call);
    }

    public <T> T call(Callable<T> request) throws Exception {
        synchronized (this) {
            if (closed) throw new InterruptedException("Request cancelled");
        }
        HttpCallScope previous = CURRENT.get();
        CURRENT.set(this);
        try { return request.call(); }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            close();
        }
    }

    @Override public void close() {
        Set<Call> pending;
        synchronized (this) {
            closed = true;
            pending = new LinkedHashSet<>(calls);
            calls.clear();
        }
        for (Call call : pending) call.cancel();
    }
}
