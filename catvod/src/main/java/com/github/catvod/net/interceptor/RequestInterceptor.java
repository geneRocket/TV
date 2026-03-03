package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import com.github.catvod.Proxy;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RequestInterceptor implements Interceptor {

    private final Map<String, String> authMap;

    public RequestInterceptor() {
        this.authMap = new ConcurrentHashMap<>();
    }

    public void clear() {
        authMap.clear();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        boolean local = url.contains(":" + Proxy.getPort() + "/");
        Request.Builder builder = request.newBuilder();
        if (url.contains("+") && local) builder.url(url.replace("+", "%2B"));
        if (url.contains("gitcode.net")) builder.header(HttpHeaders.USER_AGENT, Util.CHROME);
        checkAuthUser(request.url(), builder);
        return chain.proceed(builder.build());
    }

    private void checkAuthUser(HttpUrl url, Request.Builder builder) {
        String auth = url.queryParameter("auth");
        if (auth != null) authMap.put(url.host(), auth);
        if (authMap.containsKey(url.host()) && auth == null) builder.url(url.newBuilder().addQueryParameter("auth", authMap.get(url.host())).build());
    }
}
