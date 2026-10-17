package com.fongmi.android.tv.server.process;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.*;

public class LocalRangeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final OkHttpClient client = new OkHttpClient();
    private NanoHTTPD server;
    private File file;

    @Before public void startServer() throws Exception {
        file = temporary.newFile("video.mp4");
        Files.write(file.toPath(), "0123456789".getBytes(StandardCharsets.UTF_8));
        Method method = Local.class.getDeclaredMethod("getFile", Map.class, File.class, String.class);
        method.setAccessible(true);
        Local local = new Local();
        server = new NanoHTTPD("127.0.0.1", 0) {
            @Override public NanoHTTPD.Response serve(IHTTPSession session) {
                try {
                    return (NanoHTTPD.Response) method.invoke(local, session.getHeaders(), file, "video/mp4");
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            }
        };
        server.start();
    }

    @After public void stopServer() {
        if (server != null) server.stop();
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdownNow();
    }

    @Test public void boundedRangeSendsOnlyTheRequestedBytes() throws Exception {
        check("bytes=2-4", 206, "bytes 2-4/10", "234");
    }

    @Test public void openRangeSendsTheRemainder() throws Exception {
        check("bytes=7-", 206, "bytes 7-9/10", "789");
    }

    @Test public void suffixRangeSendsTheLastBytes() throws Exception {
        check("bytes=-3", 206, "bytes 7-9/10", "789");
    }

    @Test public void zeroSuffixIsUnsatisfiable() throws Exception {
        check("bytes=-0", 416, "bytes */10", "");
    }

    @Test public void veryLargeEndStillPreservesTheRequestedStart() throws Exception {
        check("bytes=7-999999999999999999999999999", 206, "bytes 7-9/10", "789");
    }

    @Test public void veryLargeStartIsUnsatisfiable() throws Exception {
        check("bytes=999999999999999999999999999-", 416, "bytes */10", "");
    }

    @Test public void veryLargeSuffixIncludesTheWholeFile() throws Exception {
        check("bytes=-999999999999999999999999999", 206, "bytes 0-9/10", "0123456789");
    }

    @Test public void malformedRangesFallBackToAFullResponse() throws Exception {
        for (String range : new String[]{"bytes=bad", "bytes=-", "bytes=5--1", "bytes=5-3", "items=2-4", "bytes=0-0,9-9"}) {
            check(range, 200, null, "0123456789");
        }
    }

    @Test public void outOfBoundsStartIsUnsatisfiable() throws Exception {
        check("bytes=10-", 416, "bytes */10", "");
    }

    @Test public void mismatchedIfRangeReturnsTheWholeFile() throws Exception {
        try (Response response = client.newCall(request("bytes=7-").header("If-Range", "stale").build()).execute()) {
            assertEquals(200, response.code());
            assertNull(response.header("Content-Range"));
            assertEquals("0123456789", response.body().string());
        }
    }

    private Request.Builder request(String range) {
        return new Request.Builder().url("http://127.0.0.1:" + server.getListeningPort() + "/video.mp4").header("Range", range);
    }

    private void check(String range, int status, String contentRange, String body) throws Exception {
        try (Response response = client.newCall(request(range).build()).execute()) {
            assertEquals(range, status, response.code());
            assertEquals(range, contentRange, response.header("Content-Range"));
            assertEquals(range, body, response.body().string());
        }
    }
}
