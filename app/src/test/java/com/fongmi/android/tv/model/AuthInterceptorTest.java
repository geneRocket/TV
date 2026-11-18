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

    @Test public void preemptiveCredentialsStayWithTheRequestThatProvidesThem() throws Exception {
        assertEquals(200, fetch("https://alice:secret@media.example/master.m3u8", null));
        assertEquals("Basic YWxpY2U6c2VjcmV0", sent.get().header("Authorization"));
        assertEquals(403, fetch("https://media.example/independent.m3u8", null));
        assertNull(sent.get().header("Authorization"));
    }

    @Test public void urlCredentialsDoNotReplaceExplicitAuthorization() throws Exception {
        fetch("https://alice:secret@media.example/master.m3u8", null);
        fetch("https://alice:secret@media.example/segment.ts", "Bearer token");
        assertEquals("Bearer token", sent.get().header("Authorization"));
    }

    @Test public void unrelatedRequestsAcrossOriginsStayUnauthenticated() throws Exception {
        fetch("https://alice:secret@media.example/master.m3u8", null);
        assertEquals(403, fetch("https://media.example:8443/segment.ts", null));
        assertEquals(403, fetch("http://media.example/segment.ts", null));
        assertEquals(403, fetch("https://other.example/segment.ts", null));
    }

    @Test public void mediaCredentialsAuthorizeOnlyRelatedSameOriginSegments() {
        HttpUrl playlist = HttpUrl.get("https://alice:secret@media.example/master.m3u8");
        Request segment = new Request.Builder().url("https://media.example/segment.ts").build();
        assertEquals("Basic YWxpY2U6c2VjcmV0", AuthInterceptor.apply(segment, playlist).header("Authorization"));
        Request alternateAccount = segment.newBuilder().url("https://bob:other@media.example/segment.ts").build();
        assertEquals("Basic Ym9iOm90aGVy", AuthInterceptor.apply(alternateAccount, playlist).header("Authorization"));
        Request alternateOrigin = segment.newBuilder().url("https://media.example:8443/segment.ts").build();
        assertNull(AuthInterceptor.apply(alternateOrigin, playlist).header("Authorization"));
        Request bearer = segment.newBuilder().header("Authorization", "Bearer token").build();
        assertEquals("Bearer token", AuthInterceptor.apply(bearer, playlist).header("Authorization"));
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
