package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import com.fongmi.android.tv.player.Source;
import com.github.catvod.utils.Path;
import com.p2p.P2PClass;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class JianPian implements Source.Extractor {

    private P2PClass p2p;
    private String path;
    private Map<String, Boolean> pathPaused;

    @Override
    public boolean match(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        return "tvbox-xg".equals(scheme) || "jianpian".equals(scheme) || "ftp".equals(scheme);
    }

    private void init() {
        if (p2p == null) p2p = new P2PClass();
        if (pathPaused == null) pathPaused = new HashMap<>();
    }

    @Override
    public String fetch(String url) throws Exception {
        init();
        stop();
        start(url);
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("Invalid jianpian url");
        if (p2p.port <= 0) throw new IllegalArgumentException("Invalid jianpian port");
        String name = Uri.parse(path).getLastPathSegment();
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Invalid jianpian path");
        return "http://127.0.0.1:" + p2p.port + "/" + encodePathSegment(name);
    }

    private void start(String url) {
        try {
            String lastPath = path;
            path = parsePath(url);
            path = path.replace("tvbox-xg://", "").replace("tvbox-xg:", "");
            path = path.replace("xg://", "ftp://").replace("xgplay://", "ftp://");
            if (path.isEmpty()) return;
            boolean isDiff = lastPath != null && !lastPath.equals(path);
            if (isDiff) p2p.P2Pdoxdel(lastPath.getBytes("GBK"));
            p2p.P2Pdoxstart(path.getBytes("GBK"));
            if (lastPath == null || isDiff) p2p.P2Pdoxadd(path.getBytes("GBK"));
            if (isDiff && pathPaused.containsKey(lastPath)) pathPaused.remove(lastPath);
            pathPaused.put(path, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String parsePath(String url) {
        String value = stripOption(url);
        String prefix = "jianpian://pathtype=url&path=";
        if (value.startsWith(prefix)) return decodeQueryValue(value.substring(prefix.length()));
        return Uri.decode(value);
    }

    private String stripOption(String url) {
        int optionIndex = url.indexOf('|');
        return optionIndex >= 0 ? url.substring(0, optionIndex) : url;
    }

    private String decodeQueryValue(String url) {
        try {
            return URLDecoder.decode(url, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return url;
        }
    }

    private String encodePathSegment(String name) throws Exception {
        return URLEncoder.encode(name, "GBK").replace("+", "%20");
    }

    @Override
    public void stop() {
        try {
            if (p2p == null || path == null) return;
            if (pathPaused.containsKey(path) && pathPaused.get(path)) return;
            p2p.P2Pdoxpause(path.getBytes("GBK"));
            pathPaused.put(path, true);
            Path.clear(Path.jpa());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void exit() {
        Path.clear(Path.jpa());
    }
}
