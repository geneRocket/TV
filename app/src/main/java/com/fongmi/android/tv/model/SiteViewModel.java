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
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.utils.TaskScope;
import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.utils.Trans;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Call;

public class SiteViewModel extends BaseSiteViewModel {

    private final MutableLiveData<Episode> ep;
    private final MutableLiveData<Episode> episode;
    private final MutableLiveData<Result> player;
    private final MutableLiveData<Result> search;
    private final MutableLiveData<Result> action;
    private final MutableLiveData<Danmu> danmaku;
    private final MutableLiveData<Result> download;
    private final TaskScope playbackRequests;
    private final ConcurrentHashMap<String, Set<Call>> activeSearchCalls = new ConcurrentHashMap<>();
    private final Set<String> cancelledSearchTokens = ConcurrentHashMap.newKeySet();

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
        this.playbackRequests = new TaskScope(ThreadPools.newSingle("site-playback"), AppTaskScheduler.get(), error -> ThreadPools.log(error, "Playback request failed."));
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
        requests.cancel(REQUEST_RESULT);
    }

    public void detailContent(String key, String id) {
        detailContent(key, id, "");
    }

    public void detailContent(String key, String id, String token) {
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD,
                () -> SpiderRepository.get().detailContent(key, id),
                data -> result.postValue(withRequest(data, key, id, null, token)),
                this::requestRequestFallback);
    }

    public void playerContent(String key, String flag, String id) {
        playerContent(key, flag, id, "");
    }

    public void playerContent(String key, String flag, String id, String token) {
        submitPlaybackRequest(REQUEST_PLAYER, player, key, flag, id, token);
    }

    public void download(String key, String flag, String id) {
        submitPlaybackRequest(REQUEST_DOWNLOAD, download, key, flag, id, "");
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
        if (cancelledSearchTokens.contains(token)) {
            call.cancel();
            return;
        }
        if (call.isExecuted()) {
            removeSearchCall(token, call);
            return;
        }
        activeSearchCalls.computeIfAbsent(token, key -> ConcurrentHashMap.newKeySet()).add(call);
        if (cancelledSearchTokens.contains(token)) {
            removeSearchCall(token, call);
            call.cancel();
        }
    }

    private void removeSearchCall(String token, Call call) {
        Set<Call> calls = activeSearchCalls.get(token);
        if (calls == null) return;
        calls.remove(call);
        if (calls.isEmpty()) activeSearchCalls.remove(token, calls);
    }

    public void cancelSearch(String token) {
        if (token == null) return;
        cancelledSearchTokens.add(token);
        Set<Call> calls = activeSearchCalls.remove(token);
        if (calls == null) return;
        for (Call call : calls) call.cancel();
    }

    private void post(Site site, Result result, String keyword, String token) {
        if (result.getList().isEmpty() || cancelledSearchTokens.contains(token)) return;
        result.setKey(site.getKey());
        result.setKeyword(keyword);
        result.setRequestToken(token);
        App.post(() -> {
            if (!cancelledSearchTokens.contains(token)) this.search.setValue(result);
        });
    }

    private void submitPlaybackRequest(String requestKey, MutableLiveData<Result> target, String key, String flag, String id, String token) {
        Callable<Result> request = () -> SpiderRepository.get().playerContent(key, flag, id);
        playbackRequests.submit(requestKey, request, Constant.TIMEOUT_PLAY,
                data -> target.postValue(withRequest(data, key, id, flag, token)),
                this::requestRequestFallback,
                Source.get()::stop);
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
        cancelledSearchTokens.clear();
        playbackRequests.close();
        super.onCleared();
    }
}
