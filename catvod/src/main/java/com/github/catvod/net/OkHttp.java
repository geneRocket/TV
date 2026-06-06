package com.github.catvod.net;

import android.text.TextUtils;

import androidx.collection.ArrayMap;

import com.github.catvod.bean.Doh;
import com.github.catvod.net.interceptor.AuthInterceptor;
import com.github.catvod.net.interceptor.ProxyRequestInterceptor;
import com.github.catvod.net.interceptor.RequestInterceptor;
import com.github.catvod.net.interceptor.ResponseInterceptor;

import java.net.ProxySelector;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp {

    private static final int TIMEOUT = 30 * 1000;
    private static final int CACHE = 100 * 1024 * 1024;

    private OkDns dns;
    private OkHttpClient client;
    private OkAuthenticator authenticator;
    private AuthInterceptor authInterceptor;
    private RequestInterceptor requestInterceptor;
    private ResponseInterceptor responseInterceptor;
    private OkProxySelector selector;

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }

    public static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static OkDns dns() {
        if (get().dns != null) return get().dns;
        return get().dns = new OkDns();
    }

    public void setDoh(Doh doh) {
        dns().setDoh(doh);
        client = null;
    }

    public void setProxy(String proxy) {
        ProxySelector.setDefault(selector());
        if (!TextUtils.isEmpty(proxy)) selector().setProxy(proxy);
        client = null;
    }

    public static OkProxySelector selector() {
        if (get().selector != null) return get().selector;
        return get().selector = new OkProxySelector();
    }

    public static OkAuthenticator authenticator() {
        if (get().authenticator != null) return get().authenticator;
        return get().authenticator = new OkAuthenticator();
    }

    public static AuthInterceptor authInterceptor() {
        if (get().authInterceptor != null) return get().authInterceptor;
        return get().authInterceptor = new AuthInterceptor();
    }

    public static RequestInterceptor requestInterceptor() {
        if (get().requestInterceptor != null) return get().requestInterceptor;
        return get().requestInterceptor = new RequestInterceptor();
    }

    public static ResponseInterceptor responseInterceptor() {
        if (get().responseInterceptor != null) return get().responseInterceptor;
        return get().responseInterceptor = new ResponseInterceptor();
    }

    public static void clearConfig() {
        dns().clear();
        selector().clear();
        authenticator().clear();
        authInterceptor().clear();
        requestInterceptor().clear();
        responseInterceptor().clear();
        get().client = null;
    }

    public static OkHttpClient client() {
        if (get().client != null) return get().client;
        return get().client = getBuilder().build();
    }

    public static OkHttpClient client(int timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).build();
    }

    public static OkHttpClient noRedirect(int timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build();
    }

    public static OkHttpClient client(boolean redirect, int timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    public static String string(String url) {
        try {
            if (!url.startsWith("http")) return "";
            try (Response response = newCall(url).execute()) {
                return response.body() == null ? "" : response.body().string();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String string(String url, Map<String, String> headers) {
        try {
            try (Response response = newCall(url, headers).execute()) {
                return response.body() == null ? "" : response.body().string();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static Call newCall(String url) {
        return client().newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url) {
        return client.newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(String url, Map<String, String> headers) {
        return client().newCall(new Request.Builder().url(url).headers(Headers.of(headers)).build());
    }

    public static Call newCall(String url, Headers headers) {
        return client().newCall(new Request.Builder().url(url).headers(headers).build());
    }

    public static Call newCall(String url, Headers headers, ArrayMap<String, String> params) {
        return client().newCall(new Request.Builder().url(buildUrl(url, params)).headers(headers).build());
    }

    public static Call newCall(String url, Headers headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(headers).post(body).build());
    }

    public static Call newCall(OkHttpClient client, String url, RequestBody body) {
        return client.newCall(new Request.Builder().url(url).post(body).build());
    }

    public static FormBody toBody(ArrayMap<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) body.add(entry.getKey(), entry.getValue());
        return body.build();
    }

    private static HttpUrl buildUrl(String url, ArrayMap<String, String> params) {
        HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) builder.addQueryParameter(entry.getKey(), entry.getValue());
        return builder.build();
    }

    private static OkHttpClient.Builder getBuilder() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(requestInterceptor())
                .addInterceptor(authInterceptor())
                .addNetworkInterceptor(responseInterceptor())
                .addInterceptor(new ProxyRequestInterceptor(selector()))
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .dns(dns())
                .hostnameVerifier((hostname, session) -> true)
                .followRedirects(true)
                .sslSocketFactory(new SSLCompat(), SSLCompat.TM);
        builder.proxyAuthenticator(authenticator());
        builder.proxySelector(selector());
        return builder;
    }
}
