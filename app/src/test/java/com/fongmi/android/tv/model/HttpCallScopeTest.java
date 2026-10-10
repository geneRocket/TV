package com.fongmi.android.tv.model;

import com.fongmi.android.tv.utils.TaskScope;
import com.fongmi.android.tv.utils.TaskScheduler;
import com.github.catvod.net.HttpCallScope;
import okhttp3.*;
import org.junit.Test;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class HttpCallScopeTest {
    @Test public void scopeCancellationAbortsAnHttpBodyStillWaitingForData() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build();
        AtomicReference<Call> current = new AtomicReference<>();
        CountDownLatch reading = new CountDownLatch(1), exited = new CountDownLatch(1);
        TaskScheduler scheduler = new TaskScheduler() {
            public void post(Runnable r, long delay) {}
            public void remove(Runnable r) {}
        };
        TaskScope task = new TaskScope(worker, scheduler, error -> {}, true);
        HttpCallScope calls = new HttpCallScope();
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(3000);
            task.submit("result", () -> calls.call(() -> {
                Call call = HttpCallScope.track(client.newCall(new Request.Builder()
                        .url("http://127.0.0.1:" + server.getLocalPort() + "/").build()));
                current.set(call);
                try (Response response = call.execute()) {
                    reading.countDown();
                    return response.body().string();
                } finally { exited.countDown(); }
            }), 0, value -> {}, error -> "", calls::close);
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(3000);
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while (!input.readLine().isEmpty()) {}
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n".getBytes());
                socket.getOutputStream().flush();
                assertTrue(reading.await(2, TimeUnit.SECONDS));
                task.cancel("result");
                assertTrue(current.get().isCanceled());
                assertTrue(exited.await(2, TimeUnit.SECONDS));
            }
        } finally {
            calls.close(); task.close(); client.dispatcher().executorService().shutdownNow(); client.connectionPool().evictAll();
        }
    }

    @Test public void nestedRequestRestoresOuterOwnership() throws Exception {
        OkHttpClient client = new OkHttpClient();
        HttpCallScope outer = new HttpCallScope(), inner = new HttpCallScope();
        AtomicReference<Call> outerCall = new AtomicReference<>();
        outer.call(() -> {
            inner.call(() -> {
                Call call = HttpCallScope.track(client.newCall(new Request.Builder().url("http://127.0.0.1/").build()));
                inner.close();
                assertTrue(call.isCanceled());
                return null;
            });
            outerCall.set(HttpCallScope.track(client.newCall(new Request.Builder().url("http://127.0.0.1/").build())));
            assertFalse(outerCall.get().isCanceled());
            return null;
        });
        assertTrue(outerCall.get().isCanceled());
        client.connectionPool().evictAll();
    }
}
