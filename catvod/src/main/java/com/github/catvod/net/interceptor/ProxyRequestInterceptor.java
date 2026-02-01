package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import com.github.catvod.net.OkProxySelector;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class ProxyRequestInterceptor implements Interceptor {

    private final OkProxySelector selector;

    public ProxyRequestInterceptor(OkProxySelector selector) {
        this.selector = selector;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String host = request.url().host();
        if (!selector.hasProxy() || "127.0.0.1".equals(host) || selector.contains(host)) return chain.proceed(request);
        try {
            Response response = chain.proceed(request);
            if (response.isSuccessful()) return response;
            response.close();
        } catch (IOException ignored) {
        }
        try {
            selector.add(host);
            return chain.proceed(request);
        } catch (IOException e) {
            selector.remove(host);
            throw e;
        }
    }
}
