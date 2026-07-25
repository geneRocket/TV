package com.fongmi.android.tv.repository;

import android.net.Uri;
import android.text.TextUtils;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Url;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SpiderRepository {

    private static final SpiderRepository INSTANCE = new SpiderRepository();

    public static SpiderRepository get() {
        return INSTANCE;
    }

    public Result homeContent(String key) throws Exception {
        Site site = TextUtils.isEmpty(key) ? VodConfig.get().getHome() : VodConfig.get().getSite(key);
        if (site.isEmpty()) return Result.empty();
        if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String homeContent = spider.homeContent(true);
            SpiderDebug.log(homeContent);
            Result result = Result.fromJson(homeContent);
            if (!result.getList().isEmpty()) return result;
            String homeVideoContent = spider.homeVideoContent();
            SpiderDebug.log(homeVideoContent);
            result.setList(Result.fromJson(homeVideoContent).getList());
            return result;
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("filter", "true");
            String homeContent = call(site, params);
            SpiderDebug.log(homeContent);
            return Result.fromJson(homeContent);
        } else {
            String homeContent = call(OkHttp.newCall(site.getApi(), site.getHeaders()));
            SpiderDebug.log(homeContent);
            return fetchPic(site, Result.fromType(site.getType(), homeContent));
        }
    }

    public Result categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty()) return Result.empty();
        if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String categoryContent = spider.categoryContent(tid, page, filter, extend);
            SpiderDebug.log(categoryContent);
            return Result.fromJson(categoryContent);
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            if (site.getType() == 1 && !extend.isEmpty()) params.put("f", App.gson().toJson(extend));
            if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(extend), Util.URL_SAFE));
            params.put("ac", site.getType() == 0 ? "videolist" : "detail");
            params.put("t", tid);
            params.put("pg", page);
            String categoryContent = call(site, params);
            SpiderDebug.log(categoryContent);
            return Result.fromType(site.getType(), categoryContent);
        }
    }

    public Result detailContent(String key, String id) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String detailContent = spider.detailContent(java.util.Collections.singletonList(id));
            SpiderDebug.log(detailContent);
            return prepareDetailResult(Result.fromJson(detailContent));
        } else if (site.isEmpty() && "push_agent".equals(key)) {
            return Result.vod(createPushVod(id));
        } else if (site.isEmpty()) {
            return Result.empty();
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("ac", site.getType() == 0 ? "videolist" : "detail");
            params.put("ids", id);
            String detailContent = call(site, params);
            SpiderDebug.log(detailContent);
            return prepareDetailResult(Result.fromType(site.getType(), detailContent));
        }
    }

    public Result action(String key, String action) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty()) return Result.empty();
        switch (site.getType()) {
            case 3:
                return Result.fromJson(site.recent().spider().action(action));
            case 4:
                return Result.fromJson(OkHttp.string(action));
            default:
                return Result.empty();
        }
    }

    public Result playerContent(String key, String flag, String id) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String playerContent = spider.playerContent(flag, id, VodConfig.get().getFlags());
            return preparePlayerResult(key, flag, site, playerContent);
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("play", id);
            params.put("flag", flag);
            String playerContent = call(site, params);
            return preparePlayerResult(key, flag, site, playerContent);
        } else if (site.isEmpty() && "push_agent".equals(key)) {
            Result result = new Result();
            result.setParse(0);
            result.setFlag(flag);
            result.setUrl(Url.create().add(UrlUtil.normalize(id, "")));
            result.setUrl(UrlUtil.normalize(Source.get().fetch(result), ""));
            return result;
        } else if (site.isEmpty()) {
            return Result.empty();
        } else {
            Result result = new Result();
            Url url = Url.create().add(UrlUtil.normalize(id, site.getApi()));
            String type = Uri.parse(id).getQueryParameter("type");
            if ("json".equals(type)) {
                result = Result.fromJson(call(OkHttp.newCall(id, site.getHeaders())));
                url = result.getUrl().normalize(site.getApi());
            }
            result.setUrl(url);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setHeader(site.getHeader());
            if (result.getPlayUrl().isEmpty()) result.setPlayUrl(site.getPlayUrl());
            result.setKey(key);
            result.setUrl(UrlUtil.normalize(Source.get().fetch(result), site.getApi()));
            if (!"json".equals(type)) result.setParse(Sniffer.isVideoFormat(url.v()) && result.getPlayUrl().isEmpty() ? 0 : 1);
            SpiderDebug.log(result.toString());
            return result;
        }
    }

    private Result preparePlayerResult(String key, String flag, Site site, String content) throws Exception {
        SpiderDebug.log(content);
        Result result = Result.fromJson(content);
        if (result.getFlag().isEmpty()) result.setFlag(flag);
        result.setHeader(site.getHeader());
        result.getUrl().normalize(site.getApi());
        result.setUrl(UrlUtil.normalize(Source.get().fetch(result), site.getApi()));
        result.setKey(key);
        return result;
    }

    public Result searchContent(Site site, String keyword, boolean quick) throws Exception {
        return searchContent(site, keyword, quick, "1");
    }

    public Result searchContent(Site site, String keyword, boolean quick, String page) throws Exception {
        return searchContent(site, keyword, quick, page, null);
    }

    public Result searchContent(Site site, String keyword, boolean quick, String page, java.util.function.Consumer<Call> callTracker) throws Exception {
        if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String searchContent = spider.searchContent(keyword, quick, page);
            SpiderDebug.log(site.getName() + "," + searchContent);
            return attachSite(Result.fromJson(searchContent), site);
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("wd", keyword);
            params.put("pg", page);
            params.put("quick", String.valueOf(quick));
            String searchContent = call(site, params, callTracker);
            SpiderDebug.log(site.getName() + "," + searchContent);
            Result result = Result.fromType(site.getType(), searchContent);
            if (!quick) result = fetchPic(site, result, callTracker);
            return attachSite(result, site);
        }
    }

    private Vod createPushVod(String id) {
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(id);
        vod.setVodPic(ResUtil.getString(R.string.push_image));
        vod.setVodFlags(Flag.create(ResUtil.getString(R.string.push), ResUtil.getString(R.string.play), id));
        return vod;
    }

    private Result prepareDetailResult(Result result) {
        if (!result.getList().isEmpty()) result.getList().get(0).setVodFlags();
        return result;
    }

    private Result attachSite(Result result, Site site) {
        for (Vod vod : result.getList()) vod.setSite(site);
        return result;
    }

    private String call(Site site, ArrayMap<String, String> params) throws IOException {
        return call(site, params, null);
    }

    private String call(Call call) throws IOException {
        return call(call, null);
    }

    private String call(Call call, java.util.function.Consumer<Call> callTracker) throws IOException {
        throwIfInterrupted();
        if (callTracker != null) callTracker.accept(call);
        try (Response res = call.execute()) {
            if (!res.isSuccessful()) throw new IOException(res.code() + " " + res.message());
            ResponseBody body = res.body();
            return body == null ? "" : body.string();
        } finally {
            if (callTracker != null) callTracker.accept(call);
        }
    }

    private void throwIfInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Request cancelled");
    }

    private String call(Site site, ArrayMap<String, String> params, java.util.function.Consumer<Call> callTracker) throws IOException {
        String extend = site.getExt();
        if (extend.startsWith("http")) {
            Call extCall = OkHttp.newCall(extend, site.getHeaders());
            try {
                extend = call(extCall, callTracker);
                if (!extend.isEmpty()) site.setExt(extend);
            } catch (IOException error) {
                if (extCall.isCanceled() || Thread.currentThread().isInterrupted()) throw error;
                extend = "";
            }
        }
        if (!extend.isEmpty()) params.put("extend", extend);

        Call call = params.toString().length() <= 1000 ? OkHttp.newCall(site.getApi(), site.getHeaders(), params) : OkHttp.newCall(site.getApi(), site.getHeaders(), OkHttp.toBody(params));
        return call(call, callTracker);
    }

    private Result fetchPic(Site site, Result result) throws Exception {
        return fetchPic(site, result, null);
    }

    private Result fetchPic(Site site, Result result, java.util.function.Consumer<Call> callTracker) throws Exception {
        if (site.getType() > 2 || result.getList().isEmpty() || !result.getList().get(0).getVodPic().isEmpty()) return result;
        ArrayList<String> ids = new ArrayList<>();
        if (site.getCategories().isEmpty()) for (Vod item : result.getList()) ids.add(item.getVodId());
        else for (Vod item : result.getList()) if (site.getCategories().contains(item.getTypeName())) ids.add(item.getVodId());
        if (ids.isEmpty()) return result.clear();
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("ac", site.getType() == 0 ? "videolist" : "detail");
        params.put("ids", TextUtils.join(",", ids));
        result.setList(Result.fromType(site.getType(), call(OkHttp.newCall(site.getApi(), site.getHeaders(), params), callTracker)).getList());
        return result;
    }
}
