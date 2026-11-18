package com.fongmi.quickjs.crawler;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SpiderLifecycleTest {

    @Test
    public void waitsForAsynchronousDestroyBeforeReleasingTheSpider() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean cleanup = new AtomicBoolean();
        AtomicBoolean releasedAfterCleanup = new AtomicBoolean();
        JSFunction then = function(args -> {
            // QuickJS HTTP and timer callbacks are queued on this same executor.
            executor.execute(() -> {
                cleanup.set(true);
                ((JSCallFunction) args[0]).call();
            });
            return null;
        });
        JSObject promise = object(name -> "then".equals(name) ? then : null, () -> {});
        Spider spider = new Spider("site", "script.js",
                object(name -> function(args -> promise), () -> releasedAfterCleanup.set(cleanup.get())), executor);
        try {
            spider.destroy();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            assertTrue(releasedAfterCleanup.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void passesRemoteExtendDirectlyToTheScript() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicReference<Object> received = new AtomicReference<>();
        Spider spider = new Spider("site", "script.js", object(name -> function(args -> {
            received.set(args[0]);
            return null;
        }), () -> {}), executor);
        String extend = "https://extend.invalid/file.json";
        try {
            spider.init(null, extend);
            assertEquals(extend, received.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void releasingTheSpiderCancelsUnsettledCalls() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch awaitingResult = new CountDownLatch(1);
        JSFunction then = function(args -> {
            awaitingResult.countDown();
            return null;
        });
        JSObject promise = object(name -> "then".equals(name) ? then : null, () -> {});
        Spider spider = new Spider("site", "script.js",
                object(name -> function(args -> "home".equals(name) ? promise : null), () -> {}), executor);
        FutureTask<String> caller = new FutureTask<>(() -> spider.homeContent(false));
        try {
            new Thread(caller).start();
            assertTrue(awaitingResult.await(2, TimeUnit.SECONDS));
            spider.destroy();
            try {
                caller.get(2, TimeUnit.SECONDS);
                fail("Expected the unsettled call to be canceled");
            } catch (ExecutionException error) {
                assertTrue(error.getCause() instanceof CancellationException);
            }
        } finally {
            caller.cancel(true);
            executor.shutdownNow();
        }
    }

    private static JSObject object(Function<String, JSFunction> property, Runnable release) {
        return (JSObject) Proxy.newProxyInstance(JSObject.class.getClassLoader(), new Class<?>[]{JSObject.class},
                (proxy, method, args) -> {
                    if ("getJSFunction".equals(method.getName())) return property.apply((String) args[0]);
                    if ("release".equals(method.getName())) release.run();
                    return null;
                });
    }

    private static JSFunction function(Function<Object[], Object> call) {
        return (JSFunction) Proxy.newProxyInstance(JSFunction.class.getClassLoader(), new Class<?>[]{JSFunction.class},
                (proxy, method, args) -> "call".equals(method.getName()) || "callVoid".equals(method.getName())
                        ? call.apply((Object[]) args[0]) : null);
    }
}
