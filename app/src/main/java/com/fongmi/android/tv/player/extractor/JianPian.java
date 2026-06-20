package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.player.Source;
import com.github.catvod.utils.Path;
import com.p2p.P2PClass;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JianPian implements Source.Extractor {

    private P2PClass p2p;
    private String path;
    private final Map<String, Boolean> pathPaused;

    public JianPian() {
        this.pathPaused = new ConcurrentHashMap<>();
    }

    @Override
    public boolean match(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        return "tvbox-xg".equals(scheme) || "jianpian".equals(scheme) || "xg".equals(scheme) || "xgplay".equals(scheme) || "ftp".equals(scheme);
    }

    private synchronized void init() {
        if (p2p == null) p2p = new P2PClass();
    }

    @Override
    public synchronized String fetch(String url) throws Exception {
        init();
        stop();
        start(url);
        if (TextUtils.isEmpty(path)) throw new IllegalArgumentException("Invalid jianpian url");
        if (p2p.port <= 0) throw new IllegalArgumentException("Invalid jianpian port");
        String name = Uri.parse(path).getLastPathSegment();
        if (TextUtils.isEmpty(name)) throw new IllegalArgumentException("Invalid jianpian path");
        return "http://127.0.0.1:" + p2p.port + "/" + encodePathSegment(name);
    }

    private void start(String url) throws Exception {
        String lastPath = path;
        path = parsePath(url);
        path = path.replace("tvbox-xg://", "").replace("tvbox-xg:", "");
        path = path.replace("xg://", "ftp://").replace("xgplay://", "ftp://");
        if (path.isEmpty()) return;
        boolean isDiff = lastPath != null && !lastPath.equals(path);
        if (isDiff) p2p.P2Pdoxdel(lastPath.getBytes("GBK"));
        p2p.P2Pdoxstart(path.getBytes("GBK"));
        if (lastPath == null || isDiff) p2p.P2Pdoxadd(path.getBytes("GBK"));
        if (isDiff) pathPaused.remove(lastPath);
        pathPaused.put(path, false);
    }

    private String parsePath(String url) throws Exception {
        String value = stripOption(url);
        String prefix = "jianpian://pathtype=url&path=";
        if (value.startsWith(prefix)) return decodeQueryValue(value.substring(prefix.length()));
        return URLDecoder.decode(value, "GBK");
    }

    private String stripOption(String url) {
        int optionIndex = url.indexOf('|');
        return optionIndex >= 0 ? url.substring(0, optionIndex) : url;
    }

    private String decodeQueryValue(String url) throws Exception {
        return URLDecoder.decode(url, "GBK");
    }

    private String encodePathSegment(String name) throws Exception {
        return URLEncoder.encode(name, "GBK").replace("+", "%20");
    }

    @Override
    public synchronized void stop() {
        try {
            if (p2p == null || path == null) return;
            if (Boolean.TRUE.equals(pathPaused.get(path))) return;
            p2p.P2Pdoxpause(path.getBytes("GBK"));
            pathPaused.put(path, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void exit() {
        if (p2p != null) p2p.P2Pdoxendhttpd();
        p2p = null;
        pathPaused.clear();
        Path.clear(Path.jpa());
    }
}
