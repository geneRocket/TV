package com.github.catvod.net;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class OkAuthenticator implements Authenticator {

    private final List<Proxy> proxy;
    private volatile String manualHost;
    private volatile String manualUserInfo;
    private volatile int manualPort;

    public OkAuthenticator() {
        proxy = new CopyOnWriteArrayList<>();
    }

    public void addAll(List<Proxy> items) {
        proxy.addAll(items);
    }

    public void clear() {
        proxy.clear();
        setProxy("");
    }

    public void setProxy(String url) {
        Uri uri = Uri.parse(url == null ? "" : url);
        String userInfo = uri.getUserInfo();
        if (uri.getHost() == null || uri.getPort() <= 0 || userInfo == null || userInfo.isEmpty()) {
            manualHost = null;
            manualUserInfo = null;
            manualPort = -1;
            return;
        }
        manualHost = uri.getHost();
        manualPort = uri.getPort();
        manualUserInfo = userInfo;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) {
        if (route == null || response.request().header(HttpHeaders.PROXY_AUTHORIZATION) != null) return null;
        if (!(route.proxy().address() instanceof InetSocketAddress)) return null;
        InetSocketAddress proxyAddress = (InetSocketAddress) route.proxy().address();
        String requestHost = response.request().url().host();
        String proxyHost = proxyAddress.getHostName();
        int proxyPort = proxyAddress.getPort();
        if (matchesManualProxy(proxyHost, proxyPort)) return authorize(response, manualUserInfo);
        for (Proxy item : proxy) {
            for (String host : item.getHosts()) {
                if (Util.containOrMatch(requestHost, host)) {
                    for (String url : item.getUrls()) {
                        Uri uri = Uri.parse(url);
                        if (!proxyHost.equalsIgnoreCase(uri.getHost())) continue;
                        if (proxyPort > 0 && uri.getPort() > 0 && proxyPort != uri.getPort()) continue;
                        String userInfo = uri.getUserInfo();
                        if (userInfo != null) return authorize(response, userInfo);
                    }
                }
            }
        }
        return null;
    }

    private boolean matchesManualProxy(String host, int port) {
        return manualHost != null && manualUserInfo != null && manualHost.equalsIgnoreCase(host) && manualPort == port;
    }

    private Request authorize(Response response, String userInfo) {
        return response.request().newBuilder().header(HttpHeaders.PROXY_AUTHORIZATION, Util.basic(userInfo)).build();
    }
}
