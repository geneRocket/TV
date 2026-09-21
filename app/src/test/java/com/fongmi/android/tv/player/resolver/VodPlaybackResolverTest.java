package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VodPlaybackResolverTest {

    @Test
    public void prefetchIsSingleUseAndFresh() throws Exception {
        checkPrefetch(false, false, 1);
    }

    @Test
    public void expiredPrefetchResolvesAgain() throws Exception {
        checkPrefetch(true, false, 2);
    }

    @Test
    public void cancelledPrefetchIsNeverUsed() throws Exception {
        checkPrefetch(false, true, 2);
    }

    private void checkPrefetch(boolean expired, boolean cancelled, int expectedCalls) throws Exception {
        java.util.concurrent.ExecutorService foreground = Executors.newSingleThreadExecutor();
        java.util.concurrent.ExecutorService background = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        VodPlaybackResolver resolver = new VodPlaybackResolver(foreground, Runnable::run,
                (site, flag, url) -> { calls.incrementAndGet(); return Result.empty(); },
                result -> { throw new IllegalStateException("stop before Android media preparation"); },
                result -> false, background, now::get);
        try {
            ResolveRequest request = new ResolveRequest(1, "site", "flag", "next-episode");
            resolver.prefetch(request);
            background.submit(() -> {}).get(3, TimeUnit.SECONDS);
            if (expired) now.set(TimeUnit.SECONDS.toNanos(31));
            if (cancelled) resolver.cancelPrefetch();
            resolver.resolve(request, new PlaybackResolver.Callback() {
                @Override public void onResolved(ResolveResult result) { done.countDown(); }
                @Override public void onFailure(ResolveFailure failure) { done.countDown(); }
            });
            assertTrue(done.await(3, TimeUnit.SECONDS));
            org.junit.Assert.assertEquals(expectedCalls, calls.get());
        } finally {
            resolver.close();
            background.shutdownNow();
        }
    }

    @Test
    public void nullGatewayResultIsReportedAsFailure() throws Exception {
        assertFailure((site, flag, url) -> null, result -> {}, result -> false);
    }

    @Test
    public void preparationExceptionIsReportedAsFailure() throws Exception {
        assertFailure((site, flag, url) -> Result.empty(), result -> {
            throw new IllegalStateException("prepare failed");
        }, result -> false);
    }

    @Test
    public void closedResolverRejectsNewRequests() {
        VodPlaybackResolver resolver = new VodPlaybackResolver(Executors.newSingleThreadExecutor(), Runnable::run,
                (site, flag, url) -> Result.empty(), result -> {}, result -> false);
        resolver.close();

        try {
            resolver.resolve(new ResolveRequest(1, "site", "flag", "episode"), new PlaybackResolver.Callback() {
                @Override public void onResolved(ResolveResult result) {}
                @Override public void onFailure(ResolveFailure failure) {}
            });
            fail("Expected closed resolver to reject work");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    @Test
    public void queuedMediaPreparationIsDiscardedAfterClose() throws Exception {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicBoolean prepared = new AtomicBoolean();
        CountDownLatch dispatched = new CountDownLatch(1);
        VodPlaybackResolver resolver = new VodPlaybackResolver(Executors.newSingleThreadExecutor(), task -> {
            queued.set(task);
            dispatched.countDown();
        }, (site, flag, url) -> Result.empty(), result -> prepared.set(true), result -> false);

        resolver.resolve(new ResolveRequest(1, "site", "flag", "episode"), new PlaybackResolver.Callback() {
            @Override public void onResolved(ResolveResult result) {}
            @Override public void onFailure(ResolveFailure failure) {}
        });
        assertTrue(dispatched.await(3, TimeUnit.SECONDS));
        resolver.close();
        queued.get().run();

        assertTrue(!prepared.get());
    }

    @Test
    public void closeDuringResultPreparationDoesNotStartMediaResolution() throws Exception {
        AtomicReference<VodPlaybackResolver> reference = new AtomicReference<>();
        AtomicBoolean mediaResolutionStarted = new AtomicBoolean();
        CountDownLatch prepared = new CountDownLatch(1);
        VodPlaybackResolver resolver = new VodPlaybackResolver(Executors.newSingleThreadExecutor(), Runnable::run,
                (site, flag, url) -> Result.empty(), result -> {
                    reference.get().close();
                    prepared.countDown();
                }, result -> {
                    mediaResolutionStarted.set(true);
                    return false;
                });
        reference.set(resolver);

        resolver.resolve(new ResolveRequest(1, "site", "flag", "episode"), new PlaybackResolver.Callback() {
            @Override public void onResolved(ResolveResult result) {}
            @Override public void onFailure(ResolveFailure failure) {}
        });

        assertTrue(prepared.await(3, TimeUnit.SECONDS));
        assertTrue(!mediaResolutionStarted.get());
    }

    private void assertFailure(PlaybackGateway gateway, java.util.function.Consumer<Result> prepare,
                               java.util.function.Predicate<Result> useParse) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<ResolveFailure> failure = new AtomicReference<>();
        VodPlaybackResolver resolver = new VodPlaybackResolver(Executors.newSingleThreadExecutor(), Runnable::run,
                gateway, prepare, useParse);
        try {
            resolver.resolve(new ResolveRequest(1, "site", "flag", "episode"), new PlaybackResolver.Callback() {
                @Override public void onResolved(ResolveResult result) {}
                @Override public void onFailure(ResolveFailure value) { failure.set(value); done.countDown(); }
            });

            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertNotNull(failure.get());
            assertNotNull(failure.get().getCause());
        } finally {
            resolver.close();
        }
    }
}
