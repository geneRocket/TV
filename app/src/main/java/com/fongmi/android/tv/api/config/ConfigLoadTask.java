package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.net.HttpCallScope;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

/** Owns a configuration generation, its parallel HTTP work and deferred UI callbacks. */
public final class ConfigLoadTask {
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private Session current;

    public synchronized void submit(String key, Runnable task) {
        if (current != null) current.cancel();
        Session next = current = new Session();
        ThreadPools.executeLatestConfig(key, () -> next.run(task));
    }

    public static void check() {
        Session session = CURRENT.get();
        if (Thread.currentThread().isInterrupted() || (session != null && session.cancelled)) throw new CancellationException("Config load superseded");
    }

    public static long startedAt() {
        Session session = CURRENT.get();
        return session == null ? System.currentTimeMillis() : session.started;
    }

    public static void post(Runnable task) {
        App.post(guard(task));
    }

    static Runnable guard(Runnable task) {
        Session session = CURRENT.get();
        return () -> { if (session == null || !session.cancelled) task.run(); };
    }

    public static void background(Runnable task) {
        Session session = CURRENT.get();
        ThreadPools.config().execute(() -> {
            if (session == null) task.run();
            else session.run(task);
        });
    }

    static <I, O> Function<I, O> bind(Function<I, O> task) {
        Session session = CURRENT.get();
        return input -> session == null ? task.apply(input) : session.call(() -> task.apply(input));
    }

    static final class Session {
        final long started = System.currentTimeMillis();
        final Set<HttpCallScope> calls = new HashSet<>();
        volatile boolean cancelled;

        synchronized void cancel() {
            cancelled = true;
            for (HttpCallScope scope : calls) scope.close();
        }

        void run(Runnable task) {
            try { call(() -> { task.run(); return null; }); }
            catch (CancellationException ignored) { }
        }

        <T> T call(java.util.concurrent.Callable<T> task) {
            HttpCallScope scope = new HttpCallScope();
            synchronized (this) {
                if (cancelled) throw new CancellationException();
                calls.add(scope);
            }
            Session previous = CURRENT.get();
            CURRENT.set(this);
            try {
                check();
                return scope.call(task);
            } catch (CancellationException error) {
                throw error;
            } catch (Exception error) {
                check();
                throw new IllegalStateException(error);
            } finally {
                scope.close();
                synchronized (this) { calls.remove(scope); }
                if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            }
        }
    }
}
