package com.fongmi.quickjs.utils;

import android.net.Uri;
import android.util.Base64;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Headers;

public class Module {

    private final ConcurrentHashMap<String, String> cache;

    private static class Loader {
        static volatile Module INSTANCE = new Module();
    }

    public static Module get() {
        return Loader.INSTANCE;
    }

    public Module() {
        this.cache = new ConcurrentHashMap<>();
    }

    public String fetch(String name) {
        String cached = cache.get(name);
        if (cached != null) return cached;
        String content = load(name);
        if (!content.isEmpty()) cache.putIfAbsent(name, content);
        return content;
    }

    private String load(String name) {
        if (name.startsWith("http")) return request(name);
        if (name.startsWith("assets")) return Asset.read(name);
        if (name.startsWith("lib/")) return Asset.read("js/" + name);
        return "";
    }

    private String request(String url) {
        try {
            Uri uri = Uri.parse(url);
            File file = file(url);
            boolean cache = !"127.0.0.1".equals(uri.getHost());
            try (okhttp3.Response response = OkHttp.newCall(url, Headers.of(HttpHeaders.USER_AGENT, "Mozilla/5.0")).execute()) {
                if (!response.isSuccessful() || response.body() == null) return cache(url);
                byte[] data = response.body().bytes();
                if (cache) new Thread(() -> Path.write(file, data)).start();
                return new String(data, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return cache(url);
        }
    }

    private String cache(String url) {
        try {
            File file = file(url);
            return file.exists() ? Path.read(file) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private File file(String url) {
        return Path.js(Util.md5(url).concat(".js"));
    }

    public byte[] bb(String content) {
        byte[] bytes = Base64.decode(content.substring(4), Base64.DEFAULT);
        byte[] newBytes = new byte[bytes.length - 4];
        newBytes[0] = 1;
        System.arraycopy(bytes, 5, newBytes, 1, bytes.length - 5);
        return newBytes;
    }
}
