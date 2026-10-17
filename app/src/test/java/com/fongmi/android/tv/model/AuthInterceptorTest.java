package com.fongmi.android.tv.model;

import com.github.catvod.net.interceptor.AuthInterceptor;
import okhttp3.*;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class AuthInterceptorTest {
    private final AuthInterceptor auth = new AuthInterceptor();
    private final AtomicReference<Request> sent = new AtomicReference<>();
    private final OkHttpClient client = new OkHttpClient.Builder().addInterceptor(auth)
            .addInterceptor(chain -> {
                Request request = chain.request();
                sent.set(request);
                boolean authorized = request.header("Authorization") != null;
                return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(authorized ? 200 : 403).message("test")
                        .body(ResponseBody.create(null, "")).build();
            }).build();

    private int fetch(String url, String authorization) throws Exception {
        Request.Builder request = new Request.Builder().url(url);
        if (authorization != null) request.header("Authorization", authorization);
        try (Response response = client.newCall(request.build()).execute()) { return response.code(); }
    }

    @Test public void playlistCredentialsAreSentPreemptivelyToSegments() throws Exception {
        assertEquals(200, fetch("https://alice:secret@media.example/master.m3u8", null));
        String credentials = sent.get().header("Authorization");
        assertEquals("Basic YWxpY2U6c2VjcmV0", credentials);
        assertEquals(200, fetch("https://media.example/segment.ts", null));
        assertEquals(credentials, sent.get().header("Authorization"));
    }

    @Test public void cachedCredentialsDoNotReplaceExplicitAuthorization() throws Exception {
        fetch("https://alice:secret@media.example/master.m3u8", null);
        fetch("https://media.example/segment.ts", "Bearer token");
        assertEquals("Bearer token", sent.get().header("Authorization"));
    }

    @Test public void cachedCredentialsStayWithinTheirOrigin() throws Exception {
        fetch("https://alice:secret@media.example/master.m3u8", null);
        assertEquals(403, fetch("https://media.example:8443/segment.ts", null));
        assertEquals(403, fetch("http://media.example/segment.ts", null));
        assertEquals(403, fetch("https://other.example/segment.ts", null));
    }

    @Test public void clearingConfigurationForgetsCredentials() throws Exception {
        fetch("https://alice:secret@media.example/master.m3u8", null);
        auth.clear();
        assertEquals(403, fetch("https://media.example/segment.ts", null));
    }

    @Test public void challengeOnAnotherPortCannotReuseCachedCredentials() throws Exception {
        fetch("https://alice:secret@media.example/master.m3u8", null);
        OkHttpClient challenged = new OkHttpClient.Builder().addInterceptor(auth)
                .addInterceptor(chain -> {
                    sent.set(chain.request());
                    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(401).message("Unauthorized").body(ResponseBody.create(null, "")).build();
                }).build();
        try (Response ignored = challenged.newCall(new Request.Builder()
                .url("https://media.example:8443/private").build()).execute()) {
            assertNull(sent.get().header("Authorization"));
        }
    }
}
