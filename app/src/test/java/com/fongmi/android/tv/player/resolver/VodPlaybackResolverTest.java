package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VodPlaybackResolverTest {

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
