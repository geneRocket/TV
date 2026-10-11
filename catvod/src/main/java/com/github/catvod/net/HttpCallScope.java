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
        try { return attach(request); }
        finally { close(); }
    }

    /** Carry request cancellation into runtime worker threads without closing its owner. */
    public static <T> Callable<T> bind(Callable<T> request) {
        HttpCallScope owner = CURRENT.get();
        return () -> owner == null ? request.call() : owner.attach(request);
    }

    public static <T> java.util.function.Consumer<T> bindConsumer(java.util.function.Consumer<T> callback) {
        HttpCallScope owner = CURRENT.get();
        return value -> {
            if (owner == null) { callback.accept(value); return; }
            try { owner.attach(() -> { callback.accept(value); return null; }); }
            catch (InterruptedException ignored) { /* Owner cancellation discards late callbacks. */ }
            catch (RuntimeException error) { throw error; }
            catch (Exception error) { throw new IllegalStateException(error); }
        };
    }

    private <T> T attach(Callable<T> request) throws Exception {
        synchronized (this) {
            if (closed) throw new InterruptedException("Request cancelled");
        }
        HttpCallScope previous = CURRENT.get();
        CURRENT.set(this);
        try { return request.call(); }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
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
