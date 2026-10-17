package com.fongmi.android.tv.impl;

import com.github.catvod.net.HttpCallScope;
import com.github.catvod.net.OkHttp;
import org.junit.Test;
import org.schabi.newpipe.extractor.downloader.Request;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import static org.junit.Assert.*;

public class NewPipeImplTest {
    @Test(timeout = 10000) public void cancellingOwnerAbortsAStalledPlaylistResponseBody() throws Exception {
        Field field = OkHttp.class.getDeclaredField("client");
        field.setAccessible(true);
        Object previous = field.get(OkHttp.get());
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build();
        field.set(OkHttp.get(), client);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        HttpCallScope owner = new HttpCallScope();
        CountDownLatch responseStarted = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            Future<?> serving = workers.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\n\r\nx".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    responseStarted.countDown();
                    releaseServer.await(5, TimeUnit.SECONDS);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            Request request = Request.newBuilder().get("http://127.0.0.1:" + server.getLocalPort() + "/playlist")
                    .automaticLocalizationHeader(false).build();
            Future<?> fetching = workers.submit(() -> owner.call(() -> NewPipeImpl.get().execute(request)));
            assertTrue(responseStarted.await(3, TimeUnit.SECONDS));

            owner.close();

            try {
                fetching.get(2, TimeUnit.SECONDS);
                fail("Cancelled playlist request completed successfully");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof IOException);
            }
            releaseServer.countDown();
            serving.get(2, TimeUnit.SECONDS);
        } finally {
            releaseServer.countDown();
            owner.close();
            workers.shutdownNow();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdownNow();
            field.set(OkHttp.get(), previous);
        }
    }
}
