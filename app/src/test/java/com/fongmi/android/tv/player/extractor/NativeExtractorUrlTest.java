package com.fongmi.android.tv.player.extractor;

import com.fongmi.android.tv.bean.Core;
import com.google.gson.Gson;
import com.xunlei.downloadlib.ThunderUrl;

import org.junit.Test;

import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class NativeExtractorUrlTest {

    @Test public void normalizesMagnetQueryWithoutChangingTrackersOrLocalTorrentTasks() {
        assertEquals("magnet:?xt=urn:btih:abc&tr=https%3A%2F%2Ftracker#part",
                ThunderUrl.normalize("MAGNET://?%78t=urn%3abtih%3Aabc&tr=https%3A%2F%2Ftracker#part"));
        assertEquals("magnet:///a.torrent?index=1", ThunderUrl.normalize("MAGNET:///a.torrent?index=1"));
    }

    @Test public void identifiesTorrentFilesByThePathBeforeHeadersAndQueryParameters() {
        assertTrue(Thunder.Parser.match("https://example.org/file.TORRENT?token=abc"));
        assertTrue(Thunder.Parser.match("https://example.org/file.torrent;Cookie=abc"));
        assertFalse(Thunder.Parser.match("https://example.org/video.mp4?name=file.torrent"));
    }

    @Test public void jianPianPreservesLiteralPlusAndAnEscapedPipeInTheMediaPath() throws Exception {
        assertEquals("ftp://host/a+b|c.mp4",
                JianPian.decodePath("jianpian://pathtype=url&path=xg://host/a+b%7Cc.mp4|metadata"));
        assertEquals("ftp://host/中文.mp4", JianPian.decodePath("tvbox-xg:xgplay://host/%E4%B8%AD%E6%96%87.mp4"));
    }

    @Test public void changingTheTvBusDomainOrOptionsRequiresANewCore() {
        Gson gson = new Gson();
        Core first = gson.fromJson("{\"so\":\"core\",\"domain\":\"a\",\"option\":[{\"key\":\"k\",\"values\":[\"v\"]}]}", Core.class);
        Core second = gson.fromJson("{\"so\":\"core\",\"domain\":\"b\"}", Core.class);
        Core third = gson.fromJson("{\"so\":\"core\",\"domain\":\"a\"}", Core.class);
        assertEquals("a", first.getDomain());
        assertEquals(first, gson.fromJson(gson.toJson(first), Core.class));
        assertNotEquals(first, second);
        assertNotEquals(first, third);
    }

    @Test public void optionalTvBusDomainLookupHasABoundedBudgetAndRejectsInvalidResponses() {
        assertEquals(".live.example.org", readDomainResponse(200, ".live.example.org\n"));
        assertEquals("", readDomainResponse(404, "error.example.org"));
        assertEquals("", readDomainResponse(200, "<html>error</html>"));
    }

    @Test public void optionalTvBusDomainLookupReturnsEvenWhenDnsIsBlocked() {
        CountDownLatch releaseDns = new CountDownLatch(1);
        OkHttpClient client = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).dns(host -> {
            try {
                releaseDns.await(8, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            throw new UnknownHostException(host);
        }).build();
        try {
            long start = System.nanoTime();
            assertEquals("", TVBus.readDomainSuffix(client, "https://example.org/"));
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(6));
        } finally {
            releaseDns.countDown();
            client.dispatcher().executorService().shutdownNow();
        }
    }

    private String readDomainResponse(int status, String body) {
        AtomicLong observedTimeout = new AtomicLong();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            observedTimeout.set(chain.call().timeout().timeoutNanos());
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(status).message("fixture").body(ResponseBody.create(null, body)).build();
        }).build();
        try {
            String suffix = TVBus.readDomainSuffix(client, "https://example.org/");
            long timeout = observedTimeout.get();
            assertTrue(timeout > 0 && timeout <= TimeUnit.SECONDS.toNanos(3));
            return suffix;
        } finally {
            client.dispatcher().executorService().shutdown();
        }
    }
}
