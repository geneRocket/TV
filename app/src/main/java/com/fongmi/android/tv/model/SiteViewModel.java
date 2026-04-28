package com.fongmi.android.tv.model;

import android.net.Uri;
import android.text.TextUtils;

import androidx.collection.ArrayMap;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmu;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Url;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SiteViewModel extends ViewModel {

    public MutableLiveData<Episode> ep;
    public MutableLiveData<Episode> episode;
    public MutableLiveData<Result> result;
    public MutableLiveData<Result> player;
    public MutableLiveData<Result> search;
    public MutableLiveData<Result> action;
    public MutableLiveData<Danmu> danmaku;
    public MutableLiveData<Result> download;
    private final ExecutorService executor = ThreadPools.newFixed("site-vm", Math.max(2, Constant.THREAD_POOL / 2));
    private final CopyOnWriteArrayList<PendingRequest> pendingRequests = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, PendingRequest> activeRequests = new ConcurrentHashMap<>();

    private static final String REQUEST_RESULT = "result";
    private static final String REQUEST_PLAYER = "player";
    private static final String REQUEST_DOWNLOAD = "download";
    private static final String REQUEST_ACTION = "action";

    public SiteViewModel() {
        this.ep = new MutableLiveData<>();
        this.episode = new MutableLiveData<>();
        this.result = new MutableLiveData<>();
        this.player = new MutableLiveData<>();
        this.search = new MutableLiveData<>();
        this.action = new MutableLiveData<>();
        this.danmaku = new MutableLiveData<>();
        this.download = new MutableLiveData<>();
    }

    private static final class PendingRequest {
        private String key;
        private Future<?> future;
        private Runnable timeout;
    }

    private interface ResultPoster {
        void post(Result result);
    }

    private interface ResultFallback {
        Result create(Throwable error);
    }

    public void setEpisode(Episode value) {
        episode.setValue(value);
    }

    public void setDownload(Episode value) {
        ep.setValue(value);
    }

    public void homeContent() {
        execute(result, () -> loadHomeResult(VodConfig.get().getHome().getKey()));
    }

    public void homeContent(String key, String token) {
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD, () -> loadHomeResult(key), data -> result.postValue(withHomeRequest(data, key, token)), this::requestFallback);
    }

    private Result loadHomeResult(String key) throws Exception {
        Site site = TextUtils.isEmpty(key) ? VodConfig.get().getHome() : VodConfig.get().getSite(key);
        if (site.isEmpty()) return Result.empty();
        if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String homeContent = spider.homeContent(true);
            SpiderDebug.log(homeContent);
            Result result = Result.fromJson(homeContent);
            if (result.getList().size() > 0) return result;
            String homeVideoContent = spider.homeVideoContent();
            SpiderDebug.log(homeVideoContent);
            result.setList(Result.fromJson(homeVideoContent).getList());
            return result;
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("filter", "true");
            String homeContent = call(site, params, false);
            SpiderDebug.log(homeContent);
            return Result.fromJson(homeContent);
        } else {
            String homeContent = call(OkHttp.newCall(site.getApi(), site.getHeaders()));
            SpiderDebug.log(homeContent);
            return fetchPic(site, Result.fromType(site.getType(), homeContent));
        }
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        HashMap<String, String> extendSnapshot = extend == null ? new HashMap<>() : new HashMap<>(extend);
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD, () -> {
            Site site = VodConfig.get().getSite(key);
            if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String categoryContent = spider.categoryContent(tid, page, filter, extendSnapshot);
                SpiderDebug.log(categoryContent);
                return Result.fromJson(categoryContent);
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                if (site.getType() == 1 && !extendSnapshot.isEmpty()) params.put("f", App.gson().toJson(extendSnapshot));
                if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(extendSnapshot), Util.URL_SAFE));
                params.put("ac", site.getType() == 0 ? "videolist" : "detail");
                params.put("t", tid);
                params.put("pg", page);
                String categoryContent = call(site, params, true);
                SpiderDebug.log(categoryContent);
                return Result.fromType(site.getType(), categoryContent);
            }
        }, data -> result.postValue(withCategoryRequest(data, key, tid, page, extendSnapshot)), this::requestFallback);
    }

    private Result withCategoryRequest(Result result, String key, String tid, String page, HashMap<String, String> extend) {
        Result value = result == null ? Result.empty() : result;
        value.setKey(key);
        value.setRequestTypeId(tid);
        value.setRequestPage(page);
        value.setRequestExtend(getRequestExtend(extend));
        return value;
    }

    public void detailContent(String key, String id) {
        detailContent(key, id, "");
    }

    public void detailContent(String key, String id, String token) {
        detailContent(key, id, token, true);
    }

    public void detailContentFast(String key, String id, String token) {
        detailContent(key, id, token, false);
    }

    private void detailContent(String key, String id, String token, boolean preloadFlags) {
        executeRequest(result, () -> loadDetailResult(key, id, preloadFlags), key, id, null, token);
    }

    private Result loadDetailResult(String key, String id, boolean preloadFlags) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String detailContent = spider.detailContent(Arrays.asList(id));
            SpiderDebug.log(detailContent);
            return prepareDetailResult(Result.fromJson(detailContent), preloadFlags);
        } else if (site.isEmpty() && "push_agent".equals(key)) {
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(id);
            vod.setVodPic(ResUtil.getString(R.string.push_image));
            vod.setVodFlags(Flag.create(ResUtil.getString(R.string.push), ResUtil.getString(R.string.play), id));
            return prepareDetailResult(Result.vod(vod), preloadFlags);
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("ac", site.getType() == 0 ? "videolist" : "detail");
            params.put("ids", id);
            String detailContent = call(site, params, true);
            SpiderDebug.log(detailContent);
            return prepareDetailResult(Result.fromType(site.getType(), detailContent), preloadFlags);
        }
    }

    private Result prepareDetailResult(Result result, boolean preloadFlags) throws Exception {
        if (result.getList().isEmpty()) return result;
        Vod vod = result.getList().get(0);
        vod.setVodFlags();
        if (preloadFlags) Source.get().parse(vod.getVodFlags());
        return result;
    }

    private void executePlayer(MutableLiveData<Result> data, String key, String flag, String id) {
        executePlayer(data, key, flag, id, "");
    }

    private void executePlayer(MutableLiveData<Result> data, String key, String flag, String id, String token) {
        executeRequest(data, () -> {
            Source.get().stop();
            Site site = VodConfig.get().getSite(key);
            if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String playerContent = spider.playerContent(flag, id, VodConfig.get().getFlags());
                SpiderDebug.log(playerContent);
                Result result = Result.fromJson(playerContent);
                if (result.getFlag().isEmpty()) result.setFlag(flag);
                result.setUrl(Source.get().fetch(result));
                result.setHeader(site.getHeader());
                result.setKey(key);
                return result;
            } else if (site.getType() == 4) {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("play", id);
                params.put("flag", flag);
                String playerContent = call(site, params, true);
                SpiderDebug.log(playerContent);
                Result result = Result.fromJson(playerContent);
                if (result.getFlag().isEmpty()) result.setFlag(flag);
                result.setUrl(Source.get().fetch(result));
                result.setHeader(site.getHeader());
                result.setKey(key);
                return result;
            } else if (site.isEmpty() && "push_agent".equals(key)) {
                Result result = new Result();
                result.setParse(0);
                result.setFlag(flag);
                result.setUrl(Url.create().add(id));
                result.setUrl(Source.get().fetch(result));
                return result;
            } else {
                Result result = new Result();
                Url url = Url.create().add(id);
                String type = Uri.parse(id).getQueryParameter("type");
                if ("json".equals(type)) {
                    result = Result.fromJson(call(OkHttp.newCall(id, site.getHeaders())));
                    url = result.getUrl();
                }
                result.setUrl(url);
                if (result.getFlag().isEmpty()) result.setFlag(flag);
                result.setHeader(site.getHeader());
                if (result.getPlayUrl().isEmpty()) result.setPlayUrl(site.getPlayUrl());
                result.setKey(key);
                result.setUrl(Source.get().fetch(result));
                if (!"json".equals(type)) result.setParse(Sniffer.isVideoFormat(url.v()) && result.getPlayUrl().isEmpty() ? 0 : 1);
                SpiderDebug.log(result.toString());
                return result;
            }
        }, key, id, flag, token);
    }

    public void playerContent(String key, String flag, String id) {
        playerContent(key, flag, id, "");
    }

    public void playerContent(String key, String flag, String id, String token) {
        executePlayer(player, key, flag, id, token);
    }

    public void download(String key, String flag, String id) {
        executePlayer(download, key, flag, id);
    }

    public void action(String key, String action) {
        executeAsync(REQUEST_ACTION, Constant.TIMEOUT_PARSE_DEF, () -> {
            Site site = VodConfig.get().getSite(key);
            if (site.getType() == 3) return Result.fromJson(site.recent().spider().action(action));
            if (site.getType() == 4) return Result.fromJson(OkHttp.string(action));
            return Result.empty();
        }, this.action::postValue, this::requestFallback);
    }

    public void searchContent(Site site, String keyword, boolean quick) throws Throwable {
        searchContent(site, keyword, quick, "");
    }

    public void searchContent(Site site, String keyword, boolean quick, String token) throws Throwable {
        String original = keyword == null ? "" : keyword.trim();
        String query = Trans.t2s(keyword);
        if (site.getType() == 3) {
            String searchContent = site.spider().searchContent(query, quick);
            SpiderDebug.log(site.getName() + "," + searchContent);
            post(site, Result.fromJson(searchContent), original, token);
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("wd", query);
            params.put("quick", String.valueOf(quick));
            String searchContent = call(site, params, true);
            SpiderDebug.log(site.getName() + "," + searchContent);
            Result result = Result.fromType(site.getType(), searchContent);
            post(site, quick ? result : fetchPic(site, result), original, token);
        }
    }

    public void searchContent(Site site, String keyword, String page) {
        execute(result, () -> {
            if (site.getType() == 3) {
                String searchContent = site.spider().searchContent(Trans.t2s(keyword), false, page);
                SpiderDebug.log(site.getName() + "," + searchContent);
                Result result = Result.fromJson(searchContent);
                for (Vod vod : result.getList()) vod.setSite(site);
                result.setKey(site.getKey());
                result.setKeyword(keyword);
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("wd", Trans.t2s(keyword));
                params.put("pg", page);
                String searchContent = call(site, params, true);
                SpiderDebug.log(site.getName() + "," + searchContent);
                Result result = fetchPic(site, Result.fromType(site.getType(), searchContent));
                for (Vod vod : result.getList()) vod.setSite(site);
                result.setKey(site.getKey());
                result.setKeyword(keyword);
                return result;
            }
        });
    }

    private String call(Site site, ArrayMap<String, String> params, boolean limit) throws IOException {
        Call call = fetchExt(site, params, limit).length() <= 1000 ? OkHttp.newCall(site.getApi(), site.getHeaders(), params) : OkHttp.newCall(site.getApi(), site.getHeaders(), OkHttp.toBody(params));
        return call(call);
    }

    private String call(Call call) throws IOException {
        try (Response res = call.execute()) {
            return body(res);
        }
    }

    private String body(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private String fetchExt(Site site, ArrayMap<String, String> params, boolean limit) throws IOException {
        String extend = site.getExt();
        if (extend.startsWith("http")) extend = fetchExt(site);
        if (!extend.isEmpty()) params.put("extend", extend);
        return extend;
    }

    private String fetchExt(Site site) throws IOException {
        try (Response res = OkHttp.newCall(site.getExt(), site.getHeaders()).execute()) {
            if (res.code() != 200) return "";
            site.setExt(body(res));
            return site.getExt();
        }
    }

    private Result fetchPic(Site site, Result result) throws Exception {
        if (site.getType() > 2 || result.getList().isEmpty() || result.getList().get(0).getVodPic().length() > 0) return result;
        ArrayList<String> ids = new ArrayList<>();
        if (site.getCategories().isEmpty()) for (Vod item : result.getList()) ids.add(item.getVodId());
        else for (Vod item : result.getList()) if (site.getCategories().contains(item.getTypeName())) ids.add(item.getVodId());
        if (ids.isEmpty()) return result.clear();
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("ac", site.getType() == 0 ? "videolist" : "detail");
        params.put("ids", TextUtils.join(",", ids));
        String response = call(OkHttp.newCall(site.getApi(), site.getHeaders(), params));
        result.setList(Result.fromType(site.getType(), response).getList());
        return result;
    }

    private void post(Site site, Result result, String keyword, String token) {
        if (result.getList().isEmpty()) return;
        result.setKey(site.getKey());
        result.setKeyword(keyword);
        result.setRequestToken(token);
        for (Vod vod : result.getList()) vod.setSite(site);
        this.search.postValue(result);
    }

    private void execute(MutableLiveData<Result> result, Callable<Result> callable) {
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD, callable, result::postValue, this::requestFallback);
    }

    private void executeRequest(MutableLiveData<Result> result, Callable<Result> callable, String key, String id, String flag, String token) {
        String requestKey = result == player ? REQUEST_PLAYER : result == download ? REQUEST_DOWNLOAD : REQUEST_RESULT;
        long timeout = result == player || result == download ? Constant.TIMEOUT_PLAY : Constant.TIMEOUT_VOD;
        executeAsync(requestKey, timeout, callable, data -> result.postValue(withRequest(data, key, id, flag, token)), this::requestRequestFallback);
    }

    private void executeAsync(String requestKey, long timeoutMs, Callable<Result> callable, ResultPoster poster, ResultFallback fallback) {
        AtomicBoolean completed = new AtomicBoolean(false);
        PendingRequest request = new PendingRequest();
        request.key = requestKey;
        request.timeout = () -> {
            if (!completed.compareAndSet(false, true)) return;
            if (request.future != null) request.future.cancel(true);
            finishRequest(request);
            poster.post(fallback.create(new TimeoutException()));
        };
        try {
            cancelRequest(requestKey);
            pendingRequests.add(request);
            if (!TextUtils.isEmpty(requestKey)) activeRequests.put(requestKey, request);
            request.future = executor.submit(() -> {
                try {
                    Result data = callable.call();
                    if (!completed.compareAndSet(false, true)) return;
                    finishRequest(request);
                    poster.post(data);
                } catch (Throwable e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    if (!completed.compareAndSet(false, true)) return;
                    finishRequest(request);
                    poster.post(fallback.create(e));
                    ThreadPools.log(e, "Site request failed.");
                }
            });
            App.post(request.timeout, timeoutMs);
        } catch (RejectedExecutionException e) {
            finishRequest(request);
            poster.post(fallback.create(e));
        }
    }

    private void finishRequest(PendingRequest request) {
        if (request.timeout != null) App.removeCallbacks(request.timeout);
        pendingRequests.remove(request);
        if (!TextUtils.isEmpty(request.key)) activeRequests.remove(request.key, request);
    }

    private void cancelRequest(String requestKey) {
        if (TextUtils.isEmpty(requestKey)) return;
        PendingRequest previous = activeRequests.remove(requestKey);
        if (previous == null) return;
        if (previous.timeout != null) App.removeCallbacks(previous.timeout);
        if (previous.future != null) previous.future.cancel(true);
        pendingRequests.remove(previous);
    }

    private Result requestFallback(Throwable error) {
        return error instanceof ExtractException ? Result.error(error.getMessage()) : Result.empty();
    }

    private Result requestRequestFallback(Throwable error) {
        return error instanceof ExtractException ? Result.error(error.getMessage()) : emptyRequestResult();
    }

    private Result withRequest(Result result, String key, String id, String flag, String token) {
        Result value = result == null ? emptyRequestResult() : result;
        value.setKey(key);
        value.setRequestId(id);
        value.setRequestFlag(flag);
        value.setRequestToken(token);
        return value;
    }

    private Result withHomeRequest(Result result, String key, String token) {
        Result value = result == null ? Result.empty() : result;
        value.setKey(key);
        value.setRequestToken(token);
        return value;
    }

    private String getRequestExtend(HashMap<String, String> extend) {
        if (extend == null || extend.isEmpty()) return "";
        return App.gson().toJson(new TreeMap<>(extend));
    }

    private Result emptyRequestResult() {
        Result result = Result.empty();
        result.setParse(0);
        return result;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        for (PendingRequest request : pendingRequests) {
            if (request.timeout != null) App.removeCallbacks(request.timeout);
            if (request.future != null) request.future.cancel(true);
        }
        pendingRequests.clear();
        activeRequests.clear();
        ThreadPools.shutdown(executor);
    }
}
