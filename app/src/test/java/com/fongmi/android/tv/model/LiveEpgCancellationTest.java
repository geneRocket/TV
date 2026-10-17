package com.fongmi.android.tv.model;

import com.github.catvod.net.HttpCallScope;
import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.*;
import okhttp3.*;
import okio.Timeout;
import static org.junit.Assert.*;

public class LiveEpgCancellationTest {
    @Test(timeout = 10000) public void cancelledOldRequestCannotCancelTheNewChannelRequest() throws Exception {
        LiveViewModel model = new LiveViewModel();
        HttpCallScope oldOwner = new HttpCallScope(), newOwner = new HttpCallScope();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        TestCall current = new TestCall(true), stale = new TestCall(false);
        try {
            Future<String> result = worker.submit(() -> newOwner.call(() -> model.requestString(current)));
            assertTrue(current.started.await(2, TimeUnit.SECONDS));
            // The cancelled worker resumes after creating its call, while the new call is active.
            oldOwner.call(() -> {
                oldOwner.close();
                try { model.requestString(stale); }
                catch (IOException expected) {}
                return null;
            });
            assertFalse("Old EPG request cancelled the current channel", current.cancelled);
            assertTrue("Late call must inherit its owner's cancellation", stale.cancelled);
            current.release.countDown();
            assertEquals("epg", result.get(2, TimeUnit.SECONDS));
        } finally {
            current.release.countDown();
            oldOwner.close(); newOwner.close(); worker.shutdownNow(); model.onCleared();
        }
    }

    private static class TestCall implements Call {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release;
        volatile boolean cancelled;
        TestCall(boolean block) { release = new CountDownLatch(block ? 1 : 0); }
        public Request request() { return new Request.Builder().url("http://localhost/epg").build(); }
        public Response execute() throws IOException {
            started.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test timed out"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            if (cancelled) throw new IOException("Cancelled");
            return new Response.Builder().request(request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(MediaType.get("text/plain"), "epg")).build();
        }
        public void enqueue(Callback callback) { throw new UnsupportedOperationException(); }
        public void cancel() { cancelled = true; }
        public boolean isExecuted() { return started.getCount() == 0; }
        public boolean isCanceled() { return cancelled; }
        public Timeout timeout() { return new Timeout(); }
        public <T> T tag(Class<? extends T> type) { return null; }
        public <T> T tag(kotlin.reflect.KClass<T> type) { return null; }
        public <T> T tag(Class<T> type, kotlin.jvm.functions.Function0<? extends T> factory) { return factory.invoke(); }
        public <T> T tag(kotlin.reflect.KClass<T> type, kotlin.jvm.functions.Function0<? extends T> factory) { return factory.invoke(); }
        public TestCall clone() { return new TestCall(false); }
    }
}
