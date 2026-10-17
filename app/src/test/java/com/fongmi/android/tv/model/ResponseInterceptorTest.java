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

    @Test(timeout = 10000) public void decodesStandardZlibWrappedDeflate() throws Exception {
        withCompressedResponse(false, "deflate", response -> {
            assertEquals("playlist body", response.body().string());
            assertNull(response.header("Content-Encoding"));
            assertNull(response.header("Content-Length"));
        });
    }

    @Test(timeout = 10000) public void stillDecodesLegacyRawDeflate() throws Exception {
        withCompressedResponse(true, "deflate", response -> assertEquals("playlist body", response.body().string()));
    }

    @Test(timeout = 10000) public void peekingDecodedBodyDoesNotConsumeTheActualResponse() throws Exception {
        withCompressedResponse(true, "deflate", response -> {
            assertEquals("play", response.peekBody(4).string());
            assertEquals("playlist body", response.body().string());
        });
    }

    @Test(timeout = 10000) public void contentCodingNameIsCaseInsensitive() throws Exception {
        withCompressedResponse(true, "DeFlAtE", response -> assertEquals("playlist body", response.body().string()));
    }

    @Test public void closingUnreadCompressedResponseDoesNotReadItsBody() throws Exception {
        AtomicInteger closes = new AtomicInteger();
        okio.BufferedSource input = okio.Okio.buffer(new okio.Source() {
            @Override public long read(okio.Buffer sink, long count) { throw new AssertionError("Unread response body was consumed"); }
            @Override public okio.Timeout timeout() { return okio.Timeout.NONE; }
            @Override public void close() { closes.incrementAndGet(); }
        });
        ResponseBody body = new ResponseBody() {
            @Override public MediaType contentType() { return MediaType.parse("text/plain"); }
            @Override public long contentLength() { return 10; }
            @Override public okio.BufferedSource source() { return input; }
        };
        Response original = new Response.Builder().request(new Request.Builder().url("http://localhost/").build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build();
        java.lang.reflect.Method method = ResponseInterceptor.class.getDeclaredMethod("deflate", Response.class);
        method.setAccessible(true);
        Response decoded = (Response) method.invoke(new ResponseInterceptor(), original);
        decoded.close();
        assertEquals(1, closes.get());
    }

    private interface Reader { void read(Response response) throws Exception; }

    private void withCompressedResponse(boolean raw, String encoding, Reader reader) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        OkHttpClient client = new OkHttpClient.Builder().addNetworkInterceptor(new ResponseInterceptor()).build();
        byte[] encoded = deflate("playlist body".getBytes(StandardCharsets.UTF_8), raw);
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(3000);
            Future<?> serving = worker.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    while (!input.readLine().isEmpty()) { }
                    String headers = "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: " + encoded.length
                            + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Encoding: " + encoding + "\r\n\r\n";
                    socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(encoded);
                    socket.getOutputStream().flush();
                } catch (IOException error) { throw new RuntimeException(error); }
            });
            try (Response response = client.newCall(new Request.Builder()
                    .url("http://127.0.0.1:" + server.getLocalPort() + "/playlist").build()).execute()) {
                reader.read(response);
            }
            serving.get(3, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdownNow();
        }
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
        return deflate(input, true);
    }

    private static byte[] deflate(byte[] input, boolean raw) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, raw);
        try (DeflaterOutputStream stream = new DeflaterOutputStream(output, deflater)) { stream.write(input); }
        finally { deflater.end(); }
        return output.toByteArray();
    }
}
