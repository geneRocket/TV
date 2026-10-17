package com.fongmi.android.tv.player.extractor;

import com.fongmi.android.tv.Constant;
import com.github.catvod.net.OkHttp;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.*;

public class StrmTest {
    @Test public void inlineHeadersAreSentAsHeadersRatherThanAsPartOfThePath() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            assertEquals("/movie.strm", chain.request().url().encodedPath());
            assertEquals("session=abc", chain.request().header("Cookie"));
            assertEquals("https://example.com/", chain.request().header("Referer"));
            return response(chain.request(), 200, "\uFEFF\nhttps://media.example/video.m3u8\n");
        }).build();
        withClient(client, () -> assertEquals("https://media.example/video.m3u8", new Strm().fetch(
                "https://example.com/movie.strm@Cookie=session=abc@Referer=https://example.com/")));
    }

    @Test public void failedDownloadCannotTurnAnErrorPageIntoAPlaybackUrl() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> response(chain.request(), 403, "Access denied")).build();
        withClient(client, () -> {
            try {
                new Strm().fetch("https://example.com/movie.strm");
                fail("HTTP error page was accepted as media");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("403"));
            }
        });
    }

    @Test public void redirectKeepsTheOriginalMediaUrlForThePlayer() throws Exception {
        String url = "https://example.com/movie.strm";
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> response(chain.request(), 302, "<html>Redirecting</html>")
                .newBuilder().header("Location", "https://media.example/video.m3u8").build()).build();
        withClient(client, () -> assertEquals(url, new Strm().fetch(url)));
    }

    private static Response response(okhttp3.Request request, int code, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test response")
                .body(ResponseBody.create(null, body)).build();
    }

    @SuppressWarnings("unchecked")
    private static void withClient(OkHttpClient client, CheckedRunnable test) throws Exception {
        Field field = OkHttp.class.getDeclaredField("noRedirectClients");
        field.setAccessible(true);
        Map<Integer, OkHttpClient> clients = (Map<Integer, OkHttpClient>) field.get(OkHttp.get());
        OkHttpClient previous = clients.put(Constant.TIMEOUT_PARSE_LIVE, client);
        try { test.run(); }
        finally {
            if (previous == null) clients.remove(Constant.TIMEOUT_PARSE_LIVE);
            else clients.put(Constant.TIMEOUT_PARSE_LIVE, previous);
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
        }
    }

    private interface CheckedRunnable { void run() throws Exception; }
}
