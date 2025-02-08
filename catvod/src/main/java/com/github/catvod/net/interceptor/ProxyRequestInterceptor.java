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
        Response response;
        try {
            response = chain.proceed(request);
            if (response.isSuccessful()) {
                return response;
            }
            response.close();
        } catch (Exception e) {
            if (selector.contains(request.url().host())) {
                throw e;
            }
        }
        try {
            selector.add(request.url().host());
            return chain.proceed(request);
        } catch (Exception e) {
            selector.remove(request.url().host());
            throw e;
        }
    }
}
