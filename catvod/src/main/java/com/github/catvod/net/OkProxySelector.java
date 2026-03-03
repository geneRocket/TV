package com.github.catvod.net;

import android.net.Uri;

import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

public class OkProxySelector extends ProxySelector {

    private final Set<String> hosts;
    private final List<Proxy> proxyRules;
    private java.net.Proxy proxy;
    private final ProxySelector system;

    public OkProxySelector() {
        this.hosts = new ConcurrentSkipListSet<>();
        this.proxyRules = new CopyOnWriteArrayList<>();
        this.system = ProxySelector.getDefault();
    }

    public void addAll(List<String> hosts) {
        this.hosts.addAll(hosts);
    }

    public synchronized void addProxyAll(List<Proxy> items) {
        items.forEach(Proxy::init);
        proxyRules.addAll(items);
        proxyRules.sort(Proxy::compareTo);
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
        this.proxyRules.clear();
        this.proxy = null;
        clearAuthenticator();
    }

    public boolean hasProxy() {
        return proxy != null && proxy != java.net.Proxy.NO_PROXY;
    }

    public void setProxy(String proxy) {
        clearAuthenticator();
        this.proxy = getProxy(proxy);
    }

    @Override
    public List<java.net.Proxy> select(URI uri) {
        if (uri.getHost() == null || "127.0.0.1".equals(uri.getHost())) return fallback(uri);
        for (Proxy item : proxyRules) for (String host : item.getHosts()) if (Util.containOrMatch(uri.getHost(), host)) return !item.getProxies().isEmpty() ? item.getProxies() : fallback(uri);
        if (hasProxy() && !hosts.isEmpty() && contains(uri.getHost())) return Collections.singletonList(proxy);
        return fallback(uri);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
        if (system != null) system.connectFailed(uri, socketAddress, e);
    }

    private List<java.net.Proxy> fallback(URI uri) {
        return system != null ? system.select(uri) : Collections.singletonList(java.net.Proxy.NO_PROXY);
    }

    private java.net.Proxy getProxy(String proxy) {
        Uri uri = Uri.parse(proxy);
        String userInfo = uri.getUserInfo();
        if (userInfo != null && userInfo.contains(":")) setAuthenticator(userInfo);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getPort() <= 0) return java.net.Proxy.NO_PROXY;
        if (uri.getScheme().startsWith("http")) return new java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        if (uri.getScheme().startsWith("socks")) return new java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        return java.net.Proxy.NO_PROXY;
    }

    private void setAuthenticator(String userInfo) {
        String[] auth = userInfo.split(":", 2);
        if (auth.length < 2) return;
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(auth[0], auth[1].toCharArray());
            }
        });
    }

    private void clearAuthenticator() {
        Authenticator.setDefault(null);
    }
}
