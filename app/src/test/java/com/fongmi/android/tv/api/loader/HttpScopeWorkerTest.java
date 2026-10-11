package com.fongmi.android.tv.api.loader;

import com.github.catvod.net.HttpCallScope;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class HttpScopeWorkerTest {
    @Test public void scopeOwnsCallsCreatedOnRuntimeWorker() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpCallScope scope = new HttpCallScope();
        try {
            Call call = scope.call(() -> {
                Call child = executor.submit(HttpCallScope.bind(() -> HttpCallScope.track(
                        new OkHttpClient().newCall(new Request.Builder().url("https://example.com/").build()))))
                        .get(2, TimeUnit.SECONDS);
                HttpCallScope.throwIfCancelled();
                assertFalse(child.isCanceled());
                return child;
            });
            assertTrue(call.isCanceled());
        } finally { executor.shutdownNow(); }
    }

    @Test public void cancelledOwnerDiscardsQueuedRuntimeWork() throws Exception {
        HttpCallScope scope = new HttpCallScope();
        AtomicBoolean ran = new AtomicBoolean();
        AtomicReference<Callable<Boolean>> queued = new AtomicReference<>();
        scope.call(() -> {
            queued.set(HttpCallScope.bind(() -> { ran.set(true); return true; }));
            return null;
        });
        try { queued.get().call(); fail("Cancelled work ran"); }
        catch (InterruptedException expected) { }
        assertFalse(ran.get());
    }

    @Test public void chainedAsyncCallbacksRetainTheirOwnerAndIgnoreLateDelivery() throws Exception {
        HttpCallScope scope = new HttpCallScope();
        AtomicReference<java.util.function.Consumer<String>> callback = new AtomicReference<>();
        AtomicReference<Call> child = new AtomicReference<>();
        scope.call(() -> {
            callback.set(HttpCallScope.bindConsumer(url -> child.set(HttpCallScope.track(
                    new OkHttpClient().newCall(new Request.Builder().url(url).build())))));
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try { executor.submit(() -> callback.get().accept("https://example.com/")).get(2, TimeUnit.SECONDS); }
            finally { executor.shutdownNow(); }
            return null;
        });
        assertTrue(child.get().isCanceled());
        Call completed = child.get();
        callback.get().accept("https://example.com/late");
        assertSame(completed, child.get());
    }
}
