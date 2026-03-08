package com.fongmi.android.tv.player;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.custom.CustomWebView;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.ResponseBody;

public class ParseJob implements ParseCallback {

    private final List<CustomWebView> webViews;
    private final CopyOnWriteArrayList<Future<?>> parseTasks;
    private ExecutorService executor;
    private ExecutorService infinite;
    private ParseCallback callback;
    private Parse parse;
    private volatile boolean stopped;
    private final AtomicBoolean completed;

    public static ParseJob create(ParseCallback callback) {
        return new ParseJob(callback);
    }

    public ParseJob(ParseCallback callback) {
        this.executor = Executors.newFixedThreadPool(2);
        this.infinite = ThreadPools.parse();
        this.webViews = new ArrayList<>();
        this.parseTasks = new CopyOnWriteArrayList<>();
        this.callback = callback;
        this.completed = new AtomicBoolean(false);
    }

    public ParseJob start(Result result, boolean useParse) {
        stopped = false;
        completed.set(false);
        setParse(result, useParse);
        execute(result);
        return this;
    }

    private void setParse(Result result, boolean useParse) {
        if (useParse) parse = VodConfig.get().getParse();
        if (result.getPlayUrl().startsWith("json:")) parse = Parse.get(1, result.getPlayUrl().substring(5));
        if (result.getPlayUrl().startsWith("parse:")) parse = VodConfig.get().getParse(result.getPlayUrl().substring(6));
        if (parse == null || parse.isEmpty()) parse = Parse.get(0, result.getPlayUrl());
        else parse = parse.copy();
        parse.getExt().setHeader(App.gson().toJsonTree(getMergedHeaders(result.getHeader())));
        parse.setClick(getClick(result));
    }

    private String getClick(Result result) {
        String click = VodConfig.get().getSite(result.getKey()).getClick();
        if (!TextUtils.isEmpty(click)) return click;
        return result.getClick();
    }

    private void execute(Result result) {
        executor.execute(() -> {
            try {
                executor.submit(getTask(result)).get(Constant.TIMEOUT_PARSE_DEF, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                onParseError();
            }
        });
    }

    private Runnable getTask(Result result) {
        return () -> {
            try {
                doInBackground(result.getKey(), result.getUrl().v(), result.getFlag());
            } catch (Throwable e) {
                onParseError();
            }
        };
    }

    private void doInBackground(String key, String webUrl, String flag) throws Throwable {
        switch (parse.getType()) {
            case 0: //嗅探
                startWeb(key, parse, webUrl);
                break;
            case 1: //Json
                jsonParse(parse, webUrl, true);
                break;
            case 2: //Json擴展
                jsonExtend(webUrl);
                break;
            case 3: //Json聚合
                jsonMix(webUrl, flag);
                break;
            case 4: //超級解析
                godParse(webUrl, flag);
                break;
        }
    }

    private void jsonParse(Parse item, String webUrl, boolean error) throws Exception {
        String body = requestString(item.getUrl() + webUrl, item.getHeaders());
        if (TextUtils.isEmpty(body)) {
            if (error) onParseError();
            return;
        }
        JsonObject object = Json.parse(body).getAsJsonObject();
        String url = Json.safeString(object, "url");
        JsonObject data = object.getAsJsonObject("data");
        if (url.isEmpty()) url = Json.safeString(data, "url");
        checkResult(getHeader(object), url, item.getName(), error);
    }

    private String requestString(String url, Map<String, String> headers) throws Exception {
        return OkHttp.string(url, headers);
    }

    private void jsonExtend(String webUrl) throws Throwable {
        LinkedHashMap<String, String> jxs = new LinkedHashMap<>();
        for (Parse item : VodConfig.get().getParses()) if (item.getType() == 1) jxs.put(item.getName(), item.extUrl());
        checkResult(Result.fromObject(BaseLoader.get().jsonExt(parse.getUrl(), jxs, webUrl)));
    }

    private void jsonMix(String webUrl, String flag) throws Throwable {
        LinkedHashMap<String, HashMap<String, String>> jxs = new LinkedHashMap<>();
        for (Parse item : VodConfig.get().getParses()) jxs.put(item.getName(), item.mixMap());
        checkResult(Result.fromObject(BaseLoader.get().jsonExtMix(flag, parse.getUrl(), parse.getName(), jxs, webUrl)));
    }

    private void godParse(String webUrl, String flag) throws Exception {
        List<Parse> json = VodConfig.get().getParses(1, flag);
        List<Parse> webs = VodConfig.get().getParses(0, flag);
        if (json.isEmpty()) {
            if (!webs.isEmpty()) startWeb(webs, webUrl);
            else onParseError();
            return;
        }
        CountDownLatch latch = new CountDownLatch(json.size());
        for (Parse item : json) parseTasks.add(infinite.submit(() -> jsonParse(latch, item, webUrl)));
        if (!webs.isEmpty()) startWeb(webs, webUrl);
        latch.await();
        if (webs.isEmpty()) onParseError();
    }

    private void jsonParse(CountDownLatch latch, Parse item, String webUrl) {
        try {
            jsonParse(item, webUrl, false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            latch.countDown();
        }
    }

    private void checkResult(Map<String, String> headers, String url, String from, boolean error) {
        if (!TextUtils.isEmpty(url)) {
            onParseSuccess(headers, url, from);
        } else if (error) {
            onParseError();
        }
    }

    private void checkResult(Result result) {
        result.setHeader(App.gson().toJsonTree(getMergedHeaders(result.getHeader())));
        if (result.getUrl().isEmpty()) onParseError();
        else if (result.getParse() == 1) startWeb(result.getHeaders(), UrlUtil.convert(result.getUrl().v()));
        else onParseSuccess(result.getHeaders(), result.getUrl().v(), result.getJxFrom());
    }

    private void startWeb(List<Parse> items, String webUrl) {
        StringBuilder sb = new StringBuilder();
        for (Parse item : items) sb.append(item.getUrl()).append(";");
        String jxs = URLEncoder.encode(Util.substring(sb.toString()));
        String url = URLEncoder.encode(webUrl);
        startWeb(new HashMap<>(), Server.get().getAddress("/parse?jxs=" + jxs + "&url=" + url));
    }

    private void startWeb(String key, Parse item, String webUrl) {
        startWeb(key, item.getName(), item.getHeaders(), item.getUrl() + webUrl, item.getClick());
    }

    private void startWeb(Map<String, String> headers, String url) {
        startWeb("", "", headers, url, "");
    }

    private void startWeb(String key, String from, Map<String, String> headers, String url, String click) {
        App.post(() -> {
            if (stopped) return;
            webViews.add(CustomWebView.create(App.get()).start(key, from, headers, url, click, this, !url.contains("player/?url=")));
        });
    }

    private Map<String, String> getHeader(JsonObject object) {
        Map<String, String> headers = new HashMap<>(parse.getHeaders());
        if (object.has("header") && object.get("header").isJsonObject()) putHeaders(headers, Json.toMap(object.get("header")));
        JsonObject data = object.has("data") && object.get("data").isJsonObject() ? object.getAsJsonObject("data") : null;
        if (data != null && data.has("header") && data.get("header").isJsonObject()) putHeaders(headers, Json.toMap(data.get("header")));
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) if (!entry.getValue().isJsonNull() && (entry.getKey().equalsIgnoreCase(HttpHeaders.USER_AGENT) || entry.getKey().equalsIgnoreCase(HttpHeaders.REFERER) || entry.getKey().equalsIgnoreCase("ua"))) headers.put(UrlUtil.fixHeader(entry.getKey()), entry.getValue().getAsString());
        return headers;
    }

    private void putHeaders(Map<String, String> target, Map<String, String> source) {
        for (Map.Entry<String, String> entry : source.entrySet()) target.put(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
    }

    private Map<String, String> getMergedHeaders(JsonElement header) {
        Map<String, String> headers = new HashMap<>(parse.getHeaders());
        if (header != null && header.isJsonObject()) putHeaders(headers, Json.toMap(header));
        return headers;
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!completed.compareAndSet(false, true)) return;
        App.post(() -> {
            if (stopped) return;
            if (callback != null) callback.onParseSuccess(headers, url, from);
            stop();
        });
    }

    @Override
    public void onParseError() {
        if (!completed.compareAndSet(false, true)) return;
        App.post(() -> {
            if (stopped) return;
            if (callback != null) callback.onParseError();
            stop();
        });
    }

    private void stopWeb() {
        for (CustomWebView webView : webViews) webView.stop(false);
        webViews.clear();
    }

    public void stop() {
        stopped = true;
        completed.set(true);
        if (executor != null) executor.shutdownNow();
        for (Future<?> task : parseTasks) task.cancel(true);
        parseTasks.clear();
        infinite = null;
        executor = null;
        callback = null;
        stopWeb();
    }
}
