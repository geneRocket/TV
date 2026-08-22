package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AsyncPlaybackResolverTest {

    @Test
    public void resolvedResultKeepsRequestIdentity() throws Exception {
        AsyncPlaybackResolver resolver = new AsyncPlaybackResolver(Executors.newSingleThreadExecutor(), Runnable::run, (site, flag, url) -> Result.empty());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger resolvedId = new AtomicInteger();

        resolver.resolve(new ResolveRequest(7, "site", "flag", "episode"), new PlaybackResolver.Callback() {
            @Override public void onResolved(ResolveResult result) { resolvedId.set((int) result.getRequestId()); latch.countDown(); }
            @Override public void onFailure(ResolveFailure failure) { latch.countDown(); }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(7, resolvedId.get());
        resolver.close();
    }

    @Test
    public void cancelledRequestDoesNotDeliverCallback() throws Exception {
        CountDownLatch gatewayStarted = new CountDownLatch(1);
        CountDownLatch releaseGateway = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        AsyncPlaybackResolver resolver = new AsyncPlaybackResolver(Executors.newSingleThreadExecutor(), Runnable::run, (site, flag, url) -> {
            gatewayStarted.countDown();
            releaseGateway.await(2, TimeUnit.SECONDS);
            return Result.empty();
        });

        ResolveHandle handle = resolver.resolve(new ResolveRequest(1, "site", "flag", "episode"), new PlaybackResolver.Callback() {
            @Override public void onResolved(ResolveResult result) { callbacks.incrementAndGet(); }
            @Override public void onFailure(ResolveFailure failure) { callbacks.incrementAndGet(); }
        });
        assertTrue(gatewayStarted.await(2, TimeUnit.SECONDS));
        handle.cancel();
        releaseGateway.countDown();
        Thread.sleep(50);

        assertEquals(0, callbacks.get());
        resolver.close();
    }
}
