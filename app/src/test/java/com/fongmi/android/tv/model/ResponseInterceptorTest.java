package com.fongmi.android.tv.model;

import com.github.catvod.net.interceptor.ResponseInterceptor;
import okhttp3.*;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import static org.junit.Assert.*;

public class ResponseInterceptorTest {
    @Test(timeout = 10000) public void compressedRedirectStillRecordsItsFallback() throws Exception {
        followFallback(true, false);
    }

    @Test(timeout = 10000) public void compressedRejectionStillReturnsToTheOriginalUrl() throws Exception {
        followFallback(false, true);
    }

    @Test(timeout = 10000) public void ordinaryRedirectFallbackIsPreserved() throws Exception {
        followFallback(false, false);
    }

    private void followFallback(boolean compressedRedirect, boolean compressedRejection) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        OkHttpClient client = new OkHttpClient.Builder().addNetworkInterceptor(new ResponseInterceptor()).build();
        AtomicInteger requests = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(3000);
            Future<?> serving = worker.submit(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        try (Socket socket = server.accept()) {
                            socket.setSoTimeout(3000);
                            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                            String path = input.readLine().split(" ")[1];
                            while (!input.readLine().isEmpty()) {}
                            requests.incrementAndGet();
                            boolean redirect = i == 0;
                            boolean rejected = path.equals("/target");
                            boolean compressed = redirect ? compressedRedirect : rejected && compressedRejection;
                            byte[] data = (redirect ? "redirect" : rejected ? "rejected" : "success").getBytes(StandardCharsets.UTF_8);
                            if (compressed) data = deflate(data);
                            String status = redirect ? "302 Found" : rejected ? "406 Not Acceptable" : "200 OK";
                            String headers = "HTTP/1.1 " + status + "\r\nConnection: close\r\nContent-Length: " + data.length + "\r\n"
                                    + (redirect ? "Location: /target\r\n" : "")
                                    + (compressed ? "Content-Encoding: deflate\r\n" : "") + "\r\n";
                            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                            socket.getOutputStream().write(data);
                            socket.getOutputStream().flush();
                        }
                    }
                } catch (IOException error) {
                    if (!server.isClosed()) throw new RuntimeException(error);
                }
            });
            try (Response response = client.newCall(new Request.Builder()
                    .url("http://127.0.0.1:" + server.getLocalPort() + "/origin").build()).execute()) {
                assertEquals(200, response.code());
                assertEquals("success", response.body().string());
            }
            serving.get(3, TimeUnit.SECONDS);
            assertEquals(3, requests.get());
        } finally {
            worker.shutdownNow();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdownNow();
        }
    }

    private static byte[] deflate(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try (DeflaterOutputStream stream = new DeflaterOutputStream(output, deflater)) { stream.write(input); }
        finally { deflater.end(); }
        return output.toByteArray();
    }
}
