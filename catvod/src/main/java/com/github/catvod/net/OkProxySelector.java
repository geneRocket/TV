package com.github.catvod.net;

import android.net.Uri;

import com.github.catvod.utils.Util;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class OkProxySelector extends ProxySelector {

    private final Set<String> hosts;
    private Proxy proxy;

    public OkProxySelector() {
        this.hosts = new ConcurrentSkipListSet<>();
    }

    public void addAll(List<String> hosts) {
        this.hosts.addAll(hosts);
    }

    public void add(String host) {
        this.hosts.add(host);
    }

    public void remove(String host) {
        this.hosts.remove(host);
    }

    public boolean contains(String host) {
        if (host == null) return false;
        for (String h : hosts) if (Util.containOrMatch(host, h)) return true;
        return false;
    }

    public void clear() {
        this.hosts.clear();
    }

    public boolean hasProxy() {
        return proxy != null && proxy != Proxy.NO_PROXY;
    }

    public void setProxy(String proxy) {
        this.proxy = getProxy(proxy);
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (!hasProxy() || hosts.isEmpty() || uri.getHost() == null || "127.0.0.1".equals(uri.getHost()))
            return Collections.singletonList(Proxy.NO_PROXY);
        if (contains(uri.getHost())) return Collections.singletonList(proxy);
        return Collections.singletonList(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
    }

    private Proxy getProxy(String proxy) {
        Uri uri = Uri.parse(proxy);
        String userInfo = uri.getUserInfo();
        if (userInfo != null && userInfo.contains(":")) setAuthenticator(userInfo);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getPort() <= 0) return Proxy.NO_PROXY;
        if (uri.getScheme().startsWith("http")) return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        if (uri.getScheme().startsWith("socks")) return new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        return Proxy.NO_PROXY;
    }

    private void setAuthenticator(String userInfo) {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userInfo.split(":")[0], userInfo.split(":")[1].toCharArray());
            }
        });
    }
}
