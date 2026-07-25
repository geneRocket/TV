package com.fongmi.android.tv.model;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Danmu;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.repository.SpiderRepository;
import com.github.catvod.utils.Trans;

import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Call;

public class SiteViewModel extends BaseSiteViewModel {

    private final MutableLiveData<Episode> ep;
    private final MutableLiveData<Episode> episode;
    private final MutableLiveData<Result> player;
    private final MutableLiveData<Result> search;
    private final MutableLiveData<Result> action;
    private final MutableLiveData<Danmu> danmaku;
    private final MutableLiveData<Result> download;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Call>> activeSearchCalls = new ConcurrentHashMap<>();

    private static final String REQUEST_PLAYER = "player";
    private static final String REQUEST_DOWNLOAD = "download";
    private static final String REQUEST_ACTION = "action";

    public SiteViewModel() {
        super();
        this.ep = new MutableLiveData<>();
        this.episode = new MutableLiveData<>();
        this.player = new MutableLiveData<>();
        this.search = new MutableLiveData<>();
        this.action = new MutableLiveData<>();
        this.danmaku = new MutableLiveData<>();
        this.download = new MutableLiveData<>();
    }

    public void setEpisode(Episode value) {
        episode.setValue(value);
    }

    public LiveData<Episode> ep() { return ep; }

    public LiveData<Episode> episode() { return episode; }

    public LiveData<Result> player() { return player; }

    public LiveData<Result> search() { return search; }

    public LiveData<Result> actionResult() { return action; }

    public LiveData<Danmu> danmaku() { return danmaku; }

    public LiveData<Result> downloadResult() { return download; }

    public void setDownload(Episode value) {
        ep.setValue(value);
    }

    public void homeContent() {
        homeContent("");
    }

    public void homeContent(String key) {
        homeContent(key, "");
    }

    public void homeContent(String key, String token) {
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD, () -> SpiderRepository.get().homeContent(key), data -> result.postValue(withHomeRequest(data, key, token)), this::homeFallback);
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        HashMap<String, String> extendSnapshot = extend == null ? new HashMap<>() : new HashMap<>(extend);
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD, () -> SpiderRepository.get().categoryContent(key, tid, page, filter, extendSnapshot), data -> result.postValue(withCategoryRequest(data, key, tid, page, extendSnapshot)), this::categoryFallback);
    }

    public void cancelCategoryContent() {
        requests.cancel(REQUEST_RESULT, null);
    }

    public void detailContent(String key, String id) {
        detailContent(key, id, "");
    }

    public void detailContentFast(String key, String id, String token) {
        detailContent(key, id, token);
    }

    public void detailContent(String key, String id, String token) {
        executeRequest(result, () -> SpiderRepository.get().detailContent(key, id), key, id, null, token);
    }

    public void playerContent(String key, String flag, String id) {
        playerContent(key, flag, id, "");
    }

    public void playerContent(String key, String flag, String id, String token) {
        executeRequest(player, () -> SpiderRepository.get().playerContent(key, flag, id), key, id, flag, token);
    }

    public void download(String key, String flag, String id) {
        executeRequest(download, () -> SpiderRepository.get().playerContent(key, flag, id), key, id, flag, "");
    }

    public void action(String key, String action) {
        executeAsync(REQUEST_ACTION, Constant.TIMEOUT_PARSE_DEF, () -> SpiderRepository.get().action(key, action), this.action::postValue, this::requestFallback);
    }

    public void searchContent(Site site, String keyword, boolean quick) throws Throwable {
        searchContent(site, keyword, quick, "");
    }

    public void searchContent(Site site, String keyword, String page) {
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD, () -> SpiderRepository.get().searchContent(site, Trans.t2s(keyword), false, page), data -> {
            Result res = java.util.Objects.requireNonNullElseGet(data, Result::empty);
            res.setKey(site.getKey());
            res.setKeyword(keyword);
            this.result.postValue(res);
        }, this::requestFallback);
    }

    public void searchContent(Site site, String keyword, boolean quick, String token) throws Throwable {
        String original = keyword == null ? "" : keyword.trim();
        String query = Trans.t2s(original);
        throwIfInterrupted();
        Result result = SpiderRepository.get().searchContent(site, query, quick, "1", call -> trackSearchCall(token, call));
        throwIfInterrupted();
        post(site, result, original, token);
    }

    private void trackSearchCall(String token, Call call) {
        if (TextUtils.isEmpty(token) || call == null) return;
        if (call.isExecuted()) {
            removeSearchCall(token, call);
            return;
        }
        activeSearchCalls.computeIfAbsent(token, key -> new CopyOnWriteArrayList<>()).add(call);
    }

    private void removeSearchCall(String token, Call call) {
        CopyOnWriteArrayList<Call> calls = activeSearchCalls.get(token);
        if (calls == null) return;
        calls.remove(call);
        if (calls.isEmpty()) activeSearchCalls.remove(token, calls);
    }

    public void cancelSearch(String token) {
        if (token == null) return;
        CopyOnWriteArrayList<Call> calls = activeSearchCalls.remove(token);
        if (calls == null) return;
        for (Call call : calls) call.cancel();
    }

    private void post(Site site, Result result, String keyword, String token) {
        if (result.getList().isEmpty()) return;
        result.setKey(site.getKey());
        result.setKeyword(keyword);
        result.setRequestToken(token);
        App.post(() -> this.search.setValue(result));
    }

    private void executeRequest(MutableLiveData<Result> result, Callable<Result> callable, String key, String id, String flag, String token) {
        String requestKey = result == player ? REQUEST_PLAYER : result == download ? REQUEST_DOWNLOAD : REQUEST_RESULT;
        long timeout = result == player || result == download ? Constant.TIMEOUT_PLAY : Constant.TIMEOUT_VOD;
        executeAsync(requestKey, timeout, callable, data -> result.postValue(withRequest(data, key, id, flag, token)), this::requestRequestFallback);
    }

    @Override
    protected void executeAsync(String requestKey, long timeoutMs, Callable<Result> callable, java.util.function.Consumer<Result> poster, java.util.function.Function<Throwable, Result> fallback) {
        Runnable stopPlayback = isPlaybackRequest(requestKey) ? Source.get()::stop : null;
        requests.submit(requestKey, callable, timeoutMs, poster, fallback, stopPlayback, stopPlayback);
    }

    private boolean isPlaybackRequest(String requestKey) {
        return REQUEST_PLAYER.equals(requestKey) || REQUEST_DOWNLOAD.equals(requestKey);
    }

    private Result requestFallback(Throwable error) {
        return error instanceof ExtractException ? Result.error(error.getMessage()) : Result.empty();
    }

    private Result categoryFallback(Throwable error) {
        Result result = requestFallback(error);
        result.setRequestFailed(true);
        return result;
    }

    private Result homeFallback(Throwable error) {
        Result result = requestFallback(error);
        result.setRequestFailed(true);
        return result;
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

    private Result withCategoryRequest(Result result, String key, String tid, String page, HashMap<String, String> extend) {
        Result value = result == null ? Result.empty() : result;
        value.setKey(key);
        value.setRequestTypeId(tid);
        value.setRequestPage(page);
        value.setRequestExtend(getRequestExtend(extend));
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

    private void throwIfInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    @Override
    protected void onCleared() {
        for (String token : activeSearchCalls.keySet()) cancelSearch(token);
        activeSearchCalls.clear();
        super.onCleared();
    }
}
