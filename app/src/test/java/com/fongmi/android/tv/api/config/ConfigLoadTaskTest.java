package com.fongmi.android.tv.api.config;

import com.github.catvod.net.HttpCallScope;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

import static org.junit.Assert.*;

public class ConfigLoadTaskTest {
    @Test public void cancellingOneNestedConfigLoadDoesNotCancelTheOuterIndependentLoad() {
        ConfigLoadTask.Session outer = new ConfigLoadTask.Session();
        ConfigLoadTask.Session inner = new ConfigLoadTask.Session();
        AtomicInteger committed = new AtomicInteger();
        AtomicReference<Runnable> old = new AtomicReference<>(), current = new AtomicReference<>();
        outer.run(() -> {
            inner.run(() -> old.set(ConfigLoadTask.guard(() -> committed.addAndGet(100))));
            inner.cancel();
            ConfigLoadTask.check();
            current.set(ConfigLoadTask.guard(committed::incrementAndGet));
        });
        old.get().run(); current.get().run();
        assertEquals(1, committed.get());
    }

    @Test public void cancelledLoadCannotStartItsDeferredHttpWorker() {
        ConfigLoadTask.Session session = new ConfigLoadTask.Session();
        AtomicReference<Function<Integer, Integer>> worker = new AtomicReference<>();
        AtomicInteger fetched = new AtomicInteger();
        session.run(() -> worker.set(ConfigLoadTask.bind(value -> fetched.incrementAndGet())));
        session.cancel();
        try { worker.get().apply(1); fail("Cancelled load fetched a remote resource"); }
        catch (CancellationException expected) { }
        assertEquals(0, fetched.get());
    }
    @Test public void queuedCallbackIsDiscardedAfterSupersedingLoad() {
        ConfigLoadTask.Session session = new ConfigLoadTask.Session();
        AtomicInteger delivered = new AtomicInteger();
        AtomicReference<Runnable> callback = new AtomicReference<>();
        session.run(() -> callback.set(ConfigLoadTask.guard(delivered::incrementAndGet)));
        session.cancel();
        callback.get().run();
        assertEquals(0, delivered.get());
    }

    @Test public void successfulLoadStillDeliversCallbackAfterWorkerFinishes() {
        ConfigLoadTask.Session session = new ConfigLoadTask.Session();
        AtomicInteger delivered = new AtomicInteger();
        AtomicReference<Runnable> callback = new AtomicReference<>();
        session.run(() -> callback.set(ConfigLoadTask.guard(delivered::incrementAndGet)));
        callback.get().run();
        assertEquals(1, delivered.get());
    }

    @Test public void cancellationStopsFurtherStateUpdatesEvenWhenInterruptionIsIgnored() {
        ConfigLoadTask.Session session = new ConfigLoadTask.Session();
        AtomicBoolean committed = new AtomicBoolean();
        session.run(() -> {
            session.cancel();
            Thread.interrupted();
            ConfigLoadTask.check();
            committed.set(true);
        });
        assertFalse(committed.get());
    }

    @Test public void parallelCallsRemainOwnedAfterTheParentWorkerFinishes() throws Exception {
        ConfigLoadTask.Session session = new ConfigLoadTask.Session();
        AtomicReference<Function<Integer, Integer>> worker = new AtomicReference<>();
        OkHttpClient client = new OkHttpClient();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), finish = new CountDownLatch(1);
        ConcurrentLinkedQueue<Call> calls = new ConcurrentLinkedQueue<>();
        try {
            session.run(() -> worker.set(ConfigLoadTask.bind(index -> {
                Call call = HttpCallScope.track(client.newCall(new Request.Builder().url("http://127.0.0.1/").build()));
                calls.add(call);
                ready.countDown();
                try { finish.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                ConfigLoadTask.check();
                return index;
            })));
            Future<Integer> one = executor.submit(() -> worker.get().apply(1));
            Future<Integer> two = executor.submit(() -> worker.get().apply(2));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            session.cancel();
            for (Call call : calls) assertTrue(call.isCanceled());
            finish.countDown();
            for (Future<Integer> future : java.util.Arrays.asList(one, two)) {
                try { future.get(2, TimeUnit.SECONDS); fail("Cancelled worker committed a result"); }
                catch (ExecutionException error) { assertTrue(error.getCause() instanceof CancellationException); }
            }
        } finally {
            session.cancel(); finish.countDown(); executor.shutdownNow(); client.connectionPool().evictAll();
        }
    }
    @Test public void missingConfigContinuationRunsAfterTheInitialWorkerFinishes() {
        ConfigLoadTask.Session session = new ConfigLoadTask.Session();
        java.util.List<Runnable> pending = new java.util.ArrayList<>();
        AtomicInteger appended = new AtomicInteger();
        session.run(() -> ConfigLoadTask.continueLoad(appended::incrementAndGet, pending::add));
        assertEquals(0, appended.get());
        pending.get(0).run();
        assertEquals(1, appended.get());
    }

    @Test public void switchingConfigDiscardsAnAlreadyQueuedMissingConfigContinuation() {
        ConfigLoadTask.Session session = new ConfigLoadTask.Session();
        java.util.List<Runnable> pending = new java.util.ArrayList<>();
        AtomicInteger appended = new AtomicInteger();
        session.run(() -> ConfigLoadTask.continueLoad(appended::incrementAndGet, pending::add));
        session.cancel();
        pending.get(0).run();
        assertEquals(0, appended.get());
    }
}
