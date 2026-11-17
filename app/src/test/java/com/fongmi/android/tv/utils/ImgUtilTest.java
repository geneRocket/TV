package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ImgUtilTest {
    @Test public void coordinateCommasBeforeAProxyTargetAreNotImageSeparators() {
        String image = "https://images.example.com/resize?size=300,450&url=https://cdn.example.com/poster.jpg";
        assertEquals(image, ImgUtil.firstPic(image));
    }

    @Test public void coordinateCommasInAProxyPathAreNotImageSeparators() {
        String image = "https://images.example.com/resize/300,450/https://cdn.example.com/poster.jpg";
        assertEquals(image, ImgUtil.firstPic(image));
    }

    @Test public void imageListsPreserveCommasInsideTheirFirstUrl() {
        String image = "https://images.example.com/resize?size=300,450&url=https://cdn.example.com/poster.jpg";
        assertEquals(image, ImgUtil.firstPic(image + ", https://cdn.example.com/second.jpg"));
    }

    @Test public void imageSpecificCookieReplacesTheSiteCookie() {
        Headers site = new Headers.Builder().add("Cookie", "site=old").add("X-Site", "retained").build();
        Map<String, String> headers = ImgUtil.imageHeaders("https://example.com/poster.jpg@Cookie=image=new", site);
        assertEquals("image=new", headers.get("Cookie"));
        assertEquals("retained", headers.get("X-Site"));
        assertEquals("site=old", site.get("Cookie"));
    }

    @Test public void individualTagsOverrideJsonWhichOverridesSiteHeadersIgnoringCase() {
        Headers site = new Headers.Builder().add("cookie", "site-cookie").add("referer", "site-referrer")
                .add("user-agent", "SiteAgent").add("Authorization", "Bearer site").build();
        Map<String, String> headers = ImgUtil.imageHeaders("https://example.com/poster.jpg"
                + "@Headers={\"COOKIE\":\"json-cookie\",\"REFERER\":\"json-referrer\",\"USER-AGENT\":\"JsonAgent\",\"authorization\":\"Bearer image\"}"
                + "@Cookie=final-cookie@Referer=https://example.com/detail@User-Agent=ImageAgent", site);
        assertEquals(4, headers.size());
        assertEquals("final-cookie", headers.get("Cookie"));
        assertEquals("https://example.com/detail", headers.get("Referer"));
        assertEquals("ImageAgent", headers.get("User-Agent"));
        assertEquals("Bearer image", headers.get("Authorization"));
    }

    @Test public void plainImageRetainsAllSiteHeaders() {
        Headers site = new Headers.Builder().add("User-Agent", "SiteAgent").add("Cookie", "site-cookie").build();
        Map<String, String> headers = ImgUtil.imageHeaders("https://example.com/poster.jpg", site);
        assertEquals(2, headers.size());
        assertEquals("SiteAgent", headers.get("User-Agent"));
        assertEquals("site-cookie", headers.get("Cookie"));
        assertEquals(0, ImgUtil.imageHeaders("https://example.com/poster.jpg", null).size());
    }

    @Test public void redirectKeepsImageCredentialsOnlyOnTheOriginalOrigin() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        OkHttpClient client = OkHttpGlideModule.imageClient(new OkHttpClient());
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket source = new ServerSocket(0, 1, loopback);
             ServerSocket target = new ServerSocket(0, 1, loopback)) {
            source.setSoTimeout(3000);
            target.setSoTimeout(3000);
            Future<Headers[]> served = worker.submit(() -> {
                serveImageRequest(source, "302 Found\r\nLocation: /local");
                Headers local = serveImageRequest(source, "302 Found\r\nLocation: http://127.0.0.1:" + target.getLocalPort() + "/image");
                Headers remote = serveImageRequest(target, "200 OK");
                return new Headers[]{local, remote};
            });
            Request request = new Request.Builder()
                    .url("http://127.0.0.1:" + source.getLocalPort() + "/start")
                    .header("Cookie", "session=123")
                    .header("X-Api-Key", "key")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                assertEquals(200, response.code());
            }
            Headers[] received = served.get(3, TimeUnit.SECONDS);
            assertEquals("session=123", received[0].get("Cookie"));
            assertEquals("key", received[0].get("X-Api-Key"));
            assertNull(received[1].get("Cookie"));
            assertNull(received[1].get("X-Api-Key"));
        } finally {
            worker.shutdownNow();
            client.connectionPool().evictAll();
        }
    }

    private Headers serveImageRequest(ServerSocket server, String status) throws IOException {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(3000);
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            input.readLine();
            Headers.Builder headers = new Headers.Builder();
            String line;
            while ((line = input.readLine()) != null && !line.isEmpty()) headers.add(line);
            String response = "HTTP/1.1 " + status + "\r\nConnection: close\r\nContent-Length: 0\r\n\r\n";
            socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            return headers.build();
        }
    }

    @Test public void malformedJsonHeadersDoNotDiscardTheOtherHeaderSources() {
        Headers site = new Headers.Builder().add("X-Site", "retained").build();
        Map<String, String> headers = ImgUtil.imageHeaders("https://example.com/poster.jpg@Headers={broken@Cookie=image-cookie", site);
        assertEquals("retained", headers.get("X-Site"));
        assertEquals("image-cookie", headers.get("Cookie"));
    }
    @Test
    public void headerMetadataIsNotMistakenForAnImageList() {
        String image = "https://example.com/poster.jpg@Headers={\"Cookie\":\"token=a&&b\",\"Referer\":\"https://example.com/detail\"}";
        assertEquals(image, ImgUtil.firstPic(image));
        String cookie = "https://example.com/poster.jpg@Cookie=token=a&&b";
        assertEquals(cookie, ImgUtil.firstPic(cookie));
        String referer = "https://example.com/poster.jpg?crop=1,2@Referer=https://example.com/detail";
        assertEquals(referer, ImgUtil.firstPic(referer));
        assertEquals(image, ImgUtil.firstPic(image + "$$$https://example.com/second.jpg"));
    }

    @Test
    public void imageListsStillSelectTheFirstImage() {
        String first = "https://example.com/first.jpg";
        for (String separator : new String[]{"$$$", "&&", "\n", "\r", "\t", ","}) {
            assertEquals(first, ImgUtil.firstPic(first + separator + "https://example.com/second.jpg"));
        }
    }
}
