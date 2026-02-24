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
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

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
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

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

    public void setEpisode(Episode value) {
        episode.setValue(value);
    }

    public void setDownload(Episode value) {
        ep.setValue(value);
    }

    public void homeContent() {
        execute(result, () -> {
            Site site = VodConfig.get().getHome();
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
        });
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        execute(result, () -> {
            Site site = VodConfig.get().getSite(key);
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
                String categoryContent = call(site, params, true);
                SpiderDebug.log(categoryContent);
                return Result.fromType(site.getType(), categoryContent);
            }
        });
    }

    public void detailContent(String key, String id) {
        execute(result, () -> {
            Site site = VodConfig.get().getSite(key);
            if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String detailContent = spider.detailContent(Arrays.asList(id));
                SpiderDebug.log(detailContent);
                Result result = Result.fromJson(detailContent);
                if (!result.getList().isEmpty()) result.getList().get(0).setVodFlags();
                if (!result.getList().isEmpty()) Source.get().parse(result.getList().get(0).getVodFlags());
                return result;
            } else if (site.isEmpty() && "push_agent".equals(key)) {
                Vod vod = new Vod();
                vod.setVodId(id);
                vod.setVodName(id);
                vod.setVodPic(ResUtil.getString(R.string.push_image));
                vod.setVodFlags(Flag.create(ResUtil.getString(R.string.push), ResUtil.getString(R.string.play), id));
                Source.get().parse(vod.getVodFlags());
                return Result.vod(vod);
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("ac", site.getType() == 0 ? "videolist" : "detail");
                params.put("ids", id);
                String detailContent = call(site, params, true);
                SpiderDebug.log(detailContent);
                Result result = Result.fromType(site.getType(), detailContent);
                if (!result.getList().isEmpty()) result.getList().get(0).setVodFlags();
                if (!result.getList().isEmpty()) Source.get().parse(result.getList().get(0).getVodFlags());
                return result;
            }
        });
    }

    private void executePlayer(MutableLiveData<Result> data, String key, String flag, String id) {
        execute(data, () -> {
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
                return result;
            } else if (site.isEmpty() && "push_agent".equals(key)) {
                Result result = new Result();
                result.setParse(0);
                result.setFlag(flag);
                result.setUrl(Url.create().add(id));
                result.setUrl(Source.get().fetch(result));
                return result;
            } else {
                Url url = Url.create().add(id);
                String type = Uri.parse(id).getQueryParameter("type");
                if ("json".equals(type)) url = Result.fromJson(call(OkHttp.newCall(id, site.getHeaders()))).getUrl();
                Result result = new Result();
                result.setUrl(url);
                result.setFlag(flag);
                result.setHeader(site.getHeader());
                result.setPlayUrl(site.getPlayUrl());
                result.setUrl(Source.get().fetch(result));
                result.setParse(Sniffer.isVideoFormat(url.v()) && result.getPlayUrl().isEmpty() ? 0 : 1);
                SpiderDebug.log(result.toString());
                return result;
            }
        });
    }

    public void playerContent(String key, String flag, String id) {
        executePlayer(player, key, flag, id);
    }

    public void download(String key, String flag, String id) {
        executePlayer(download, key, flag, id);
    }

    public void action(String key, String action) {
        execute(this.action, () -> {
            Site site = VodConfig.get().getSite(key);
            if (site.getType() == 3) return Result.fromJson(site.recent().spider().action(action));
            if (site.getType() == 4) return Result.fromJson(OkHttp.string(action));
            return Result.empty();
        });
    }

    public void searchContent(Site site, String keyword, boolean quick) throws Throwable {
        if (site.getType() == 3) {
            String searchContent = site.spider().searchContent(Trans.t2s(keyword), quick);
            SpiderDebug.log(site.getName() + "," + searchContent);
            post(site, Result.fromJson(searchContent));
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("wd", Trans.t2s(keyword));
            params.put("quick", String.valueOf(quick));
            String searchContent = call(site, params, true);
            SpiderDebug.log(site.getName() + "," + searchContent);
            post(site, fetchPic(site, Result.fromType(site.getType(), searchContent)));
        }
    }

    public void searchContent(Site site, String keyword, String page) {
        execute(result, () -> {
            if (site.getType() == 3) {
                String searchContent = site.spider().searchContent(Trans.t2s(keyword), false, page);
                SpiderDebug.log(site.getName() + "," + searchContent);
                Result result = Result.fromJson(searchContent);
                for (Vod vod : result.getList()) vod.setSite(site);
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("wd", Trans.t2s(keyword));
                params.put("pg", page);
                String searchContent = call(site, params, true);
                SpiderDebug.log(site.getName() + "," + searchContent);
                Result result = fetchPic(site, Result.fromType(site.getType(), searchContent));
                for (Vod vod : result.getList()) vod.setSite(site);
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
        if (limit && extend.length() > 1000) extend = extend.substring(0, 1000);
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

    private void post(Site site, Result result) {
        if (result.getList().isEmpty()) return;
        for (Vod vod : result.getList()) vod.setSite(site);
        this.search.postValue(result);
    }

    private void execute(MutableLiveData<Result> result, Callable<Result> callable) {
        // 单线程执行：同一线程内完成 callable 与超时处理
        executor.execute(() -> {
            try {
                Result data = callable.call();
                result.postValue(data);
            } catch (TimeoutException e) {
                result.postValue(Result.empty()); // 或者 Result.timeout()
                e.printStackTrace();
            } catch (ExtractException e) {
                result.postValue(Result.error(e.getMessage()));
                e.printStackTrace();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.postValue(Result.empty());
            } catch (Exception e) {
                result.postValue(Result.empty());
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (executor != null) executor.shutdownNow();
    }
}
