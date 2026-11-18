package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        return chain.proceed(apply(chain.request(), null));
    }

    /** A media source can authorize its own same-origin manifest and segment requests. */
    public static Request apply(Request request, @Nullable HttpUrl mediaUrl) {
        if (request.header(HttpHeaders.AUTHORIZATION) != null) return request;
        HttpUrl source = request.url();
        if (!hasCredentials(source) && mediaUrl != null && sameOrigin(source, mediaUrl)) source = mediaUrl;
        if (!hasCredentials(source)) return request;
        String authorization = Credentials.basic(source.username(), source.password(), StandardCharsets.UTF_8);
        return request.newBuilder().header(HttpHeaders.AUTHORIZATION, authorization).build();
    }

    public static boolean hasCredentials(HttpUrl url) {
        return !url.encodedUsername().isEmpty() || !url.encodedPassword().isEmpty();
    }

    private static boolean sameOrigin(HttpUrl first, HttpUrl second) {
        return first.scheme().equals(second.scheme()) && first.host().equals(second.host()) && first.port() == second.port();
    }
}
