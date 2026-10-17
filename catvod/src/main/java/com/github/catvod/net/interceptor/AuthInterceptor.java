package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.ByteString;

public class AuthInterceptor implements Interceptor {

    private final ConcurrentHashMap<String, String> userMap;

    public AuthInterceptor() {
        userMap = new ConcurrentHashMap<>();
    }

    public void clear() {
        userMap.clear();
    }

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = check(chain.request());
        Response response = chain.proceed(request);
        if (response.code() != 401 || request.header(HttpHeaders.AUTHORIZATION) != null) return response;
        String user = request.url().uri().getUserInfo();
        if (user == null) user = userMap.get(origin(request));
        if (user == null) return response;
        response.close();
        return chain.proceed(request.newBuilder().header(HttpHeaders.AUTHORIZATION, basic(user)).build());
    }

    private Request check(Request request) {
        URI uri = request.url().uri();
        String user = uri.getUserInfo();
        if (user != null) userMap.put(origin(request), user);
        else user = userMap.get(origin(request));
        if (user == null || request.header(HttpHeaders.AUTHORIZATION) != null) return request;
        return request.newBuilder().header(HttpHeaders.AUTHORIZATION, basic(user)).build();
    }

    private String origin(Request request) {
        return request.url().scheme() + "://" + request.url().host() + ":" + request.url().port();
    }

    private String basic(String user) {
        return "Basic " + ByteString.encodeUtf8(user).base64();
    }
}
