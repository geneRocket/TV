package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.bean.Header;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.Inflater;

import okhttp3.Interceptor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Buffer;
import okio.InflaterSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;

public class ResponseInterceptor implements Interceptor {

    private final List<Header> headers;
    private final ConcurrentHashMap<String, String> redirectMap;

    public ResponseInterceptor() {
        headers = new CopyOnWriteArrayList<>();
        redirectMap = new ConcurrentHashMap<>();
    }

    public void addAll(List<Header> items) {
        headers.addAll(items);
    }

    public void clear() {
        headers.clear();
        redirectMap.clear();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = check(chain.request());
        Response response = chain.proceed(request);
        if ("deflate".equalsIgnoreCase(response.header(HttpHeaders.CONTENT_ENCODING))) response = deflate(response);
        String location = response.code() == 406 ? redirectMap.remove(request.url().toString()) : null;
        if (location != null) {
            response.close();
            return redirect(request, response, location);
        }
        if (response.code() == 302) rememberRedirect(request, response.header(HttpHeaders.LOCATION));
        return response;
    }

    private Request check(Request request) {
        String host = request.url().host();
        Request.Builder builder = request.newBuilder();
        for (Header item : headers) {
            String pattern = item.getHost().trim();
            if (!pattern.isEmpty() && Util.containOrMatch(host, pattern)) {
                Json.toMap(item.getHeader()).forEach(builder::header);
            }
        }
        return builder.build();
    }

    private Response redirect(Request request, Response response, String location) {
        return new Response.Builder().request(request).protocol(response.protocol()).code(302).message("Found").header(HttpHeaders.LOCATION, location).build();
    }

    private void rememberRedirect(Request request, String location) {
        if (location == null || location.isEmpty()) return;
        HttpUrl target = request.url().resolve(location);
        if (target != null) redirectMap.put(target.toString(), request.url().toString());
    }

    private Response deflate(Response response) {
        ResponseBody body = response.body();
        if (body == null) return response;
        BufferedSource compressed = body.source();
        BufferedSource decoded = Okio.buffer(new Source() {
            private InflaterSource inflater;
            private boolean closed;

            @Override public long read(Buffer sink, long byteCount) throws IOException {
                if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0");
                if (closed) throw new IOException("closed");
                if (byteCount == 0) return 0;
                if (inflater == null) {
                    // Detect on first read, so closing an unused redirect never waits for its body.
                    boolean zlib = false;
                    if (compressed.request(2)) {
                        int cmf = compressed.getBuffer().getByte(0) & 0xff;
                        int flg = compressed.getBuffer().getByte(1) & 0xff;
                        zlib = (cmf & 0x0f) == 8 && (cmf >> 4) <= 7 && ((cmf << 8) | flg) % 31 == 0;
                    }
                    inflater = new InflaterSource(compressed, new Inflater(!zlib));
                }
                return inflater.read(sink, byteCount);
            }

            @Override public Timeout timeout() { return compressed.timeout(); }

            @Override public void close() throws IOException {
                if (closed) return;
                closed = true;
                if (inflater == null) compressed.close();
                else inflater.close();
            }
        });
        return response.newBuilder().removeHeader(HttpHeaders.CONTENT_ENCODING).removeHeader(HttpHeaders.CONTENT_LENGTH).body(new ResponseBody() {
            @Nullable
            @Override
            public MediaType contentType() {
                return body.contentType();
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @NonNull
            @Override
            public BufferedSource source() {
                return decoded;
            }
        }).build();
    }
}
