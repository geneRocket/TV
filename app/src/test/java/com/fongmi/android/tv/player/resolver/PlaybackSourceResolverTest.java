package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class PlaybackSourceResolverTest {

    @Test
    public void directCallbackDoesNotHoldResolverLock() throws Exception {
        PlaybackSourceResolver resolver = new PlaybackSourceResolver();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        try {
            Future<?> resolve = executor.submit(() -> resolver.resolve(Result.empty(), false, false, new PlaybackSourceResolver.Callback() {
                @Override public void onResolved(ResolvedMedia media) {
                    callbackEntered.countDown();
                    try {
                        releaseCallback.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override public void onFailure() {}
            }));
            assertTrue(callbackEntered.await(3, TimeUnit.SECONDS));

            Future<?> close = executor.submit(resolver::close);
            close.get(1, TimeUnit.SECONDS);
            releaseCallback.countDown();
            resolve.get(1, TimeUnit.SECONDS);
        } finally {
            releaseCallback.countDown();
            resolver.close();
            executor.shutdownNow();
        }
    }
}
