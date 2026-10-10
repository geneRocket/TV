package com.fongmi.android.tv.model;

import com.github.catvod.net.HttpCallScope;
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
import java.util.concurrent.ConcurrentLinkedDeque;

import okhttp3.Call;

public class SiteViewModel extends BaseSiteViewModel {

    private static final int CANCELLED_SEARCH_TOKEN_LIMIT = 128;

    private final OneShotLiveData<Episode> ep;
    private final OneShotLiveData<Episode> episode;
    private final MutableLiveData<Result> search;
    private final OneShotLiveData<Result> action;
    private final MutableLiveData<Danmu> danmaku;
    private final OneShotLiveData<Result> download;
    private final TaskScope downloadRequests;
    private volatile boolean cleared;
    private final ConcurrentHashMap<String, Set<HttpCallScope>> searchScopes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Call>> activeSearchCalls = new ConcurrentHashMap<>();
    private final Set<String> cancelledSearchTokens = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedDeque<String> cancelledSearchTokenOrder = new ConcurrentLinkedDeque<>();

    private static final String REQUEST_DOWNLOAD = "download";
    private static final String REQUEST_ACTION = "action";
    private static final String REQUEST_SEARCH_PAGE = "search-page";

    public SiteViewModel() {
        super();
        this.ep = new OneShotLiveData<>();
        this.episode = new OneShotLiveData<>();
        this.search = new MutableLiveData<>();
        this.action = new OneShotLiveData<>();
        this.danmaku = new MutableLiveData<>();
        this.download = new OneShotLiveData<>();
        this.downloadRequests = new TaskScope(ThreadPools.newSingle("site-download"), AppTaskScheduler.get(), error -> ThreadPools.log(error, "Download request failed."));
    }

    public void setEpisode(Episode value) {
        episode.setValue(value);
    }

    public LiveData<Episode> ep() { return ep; }

    public LiveData<Episode> episode() { return episode; }

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
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD, () -> SpiderRepository.get().homeContent(key), data -> result.setValue(withHomeRequest(data, key, token)), this::homeFallback);
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        HashMap<String, String> extendSnapshot = extend == null ? new HashMap<>() : new HashMap<>(extend);
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD, () -> SpiderRepository.get().categoryContent(key, tid, page, filter, extendSnapshot), data -> result.setValue(withCategoryRequest(data, key, tid, page, extendSnapshot)), this::categoryFallback);
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
                data -> result.setValue(withRequest(data, key, id, null, token)),
                this::requestRequestFallback);
    }

    public void download(String key, String flag, String id) {
        submitDownloadRequest(key, flag, id);
    }

    public void cancelDownload() {
        downloadRequests.cancel(REQUEST_DOWNLOAD);
        download.discard();
    }

    public void discardPlaybackCommands() {
        cancelDownload();
        ep.discard();
        episode.discard();
    }

    public void action(String key, String action) {
        executeAsync(REQUEST_ACTION, Constant.TIMEOUT_PARSE_DEF, () -> SpiderRepository.get().action(key, action), data -> this.action.setValue(withHomeRequest(data, key, "")), this::requestFallback);
    }

    public void searchContent(Site site, String keyword, boolean quick) throws Throwable {
        searchContent(site, keyword, quick, "");
    }

    public void searchContent(Site site, String keyword, String page) {
        searchContent(site, keyword, page, "");
    }

    public void searchContent(Site site, String keyword, String page, String token) {
        String query = keyword == null ? "" : keyword.trim();
        String requestToken = TextUtils.isEmpty(token) ? SearchRequestToken.next("page") : token;
        executeAsync(REQUEST_SEARCH_PAGE, Constant.TIMEOUT_VOD,
                () -> SpiderRepository.get().searchContent(site, Trans.t2s(query), false, page,
                        call -> trackSearchCall(requestToken, call)), data -> {
            if (cancelledSearchTokens.contains(requestToken)) return;
            Result res = java.util.Objects.requireNonNullElseGet(data, Result::empty);
            res.setKey(site.getKey());
            res.setKeyword(query);
            res.setRequestPage(page);
            res.setRequestToken(requestToken);
            finishSearch(requestToken);
            this.result.setValue(res);
        }, this::categoryFallback);
    }

    public void cancelSearchPage(String token) {
        requests.cancel(REQUEST_SEARCH_PAGE);
        cancelSearch(token);
    }

    public void searchContent(Site site, String keyword, boolean quick, String token) throws Throwable {
        String original = keyword == null ? "" : keyword.trim();
        String query = Trans.t2s(original);
        throwIfInterrupted();
        HttpCallScope calls = new HttpCallScope();
        String ownerToken = token == null ? "" : token;
        searchScopes.compute(ownerToken, (key, scopes) -> {
            if (scopes == null) scopes = ConcurrentHashMap.newKeySet();
            scopes.add(calls);
            return scopes;
        });
        if (cleared || cancelledSearchTokens.contains(ownerToken)) calls.close();
        Result result;
        try {
            result = calls.call(() -> SpiderRepository.get().searchContent(site, query, quick, "1", call -> trackSearchCall(ownerToken, call)));
        } finally {
            searchScopes.computeIfPresent(ownerToken, (key, scopes) -> {
                scopes.remove(calls);
                return scopes.isEmpty() ? null : scopes;
            });
        }
        throwIfInterrupted();
        post(site, result, original, ownerToken);
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
        activeSearchCalls.compute(token, (key, calls) -> {
            if (calls == null) calls = ConcurrentHashMap.newKeySet();
            calls.add(call);
            return calls;
        });
        if (cancelledSearchTokens.contains(token)) {
            removeSearchCall(token, call);
            call.cancel();
        }
    }

    private void removeSearchCall(String token, Call call) {
        activeSearchCalls.computeIfPresent(token, (key, calls) -> {
            calls.remove(call);
            return calls.isEmpty() ? null : calls;
        });
    }

    public void cancelSearch(String token) {
        if (token == null) return;
        markSearchCancelled(token);
        Set<HttpCallScope> scopes = searchScopes.remove(token);
        if (scopes != null) for (HttpCallScope scope : scopes) scope.close();
        Set<Call> calls = activeSearchCalls.remove(token);
        if (calls == null) return;
        for (Call call : calls) call.cancel();
    }

    private void markSearchCancelled(String token) {
        if (!cancelledSearchTokens.add(token)) return;
        cancelledSearchTokenOrder.addLast(token);
        while (cancelledSearchTokenOrder.size() > CANCELLED_SEARCH_TOKEN_LIMIT) {
            String expired = cancelledSearchTokenOrder.pollFirst();
            if (expired != null) cancelledSearchTokens.remove(expired);
        }
    }

    public void finishSearch(String token) {
        if (token == null) return;
        activeSearchCalls.remove(token);
    }

    private void post(Site site, Result result, String keyword, String token) {
        if (cleared || result.getList().isEmpty() || cancelledSearchTokens.contains(token)) return;
        result.setKey(site.getKey());
        result.setKeyword(keyword);
        result.setRequestToken(token);
        App.post(() -> {
            if (!cleared && !cancelledSearchTokens.contains(token)) this.search.setValue(result);
        });
    }

    private void submitDownloadRequest(String key, String flag, String id) {
        HttpCallScope calls = new HttpCallScope();
        Callable<Result> request = () -> calls.call(() -> SpiderRepository.get().downloadContent(key, flag, id));
        downloadRequests.submit(REQUEST_DOWNLOAD, request, Constant.TIMEOUT_PLAY,
                data -> download.setValue(withRequest(data, key, id, flag, "")),
                this::requestRequestFallback,
                calls::close);
    }

    private Result requestFallback(Throwable error) {
        Result failed = error instanceof ExtractException ? Result.error(error.getMessage()) : Result.empty();
        failed.setRequestFailed(true);
        return failed;
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
        Result failed = error instanceof ExtractException ? Result.error(error.getMessage()) : emptyRequestResult();
        failed.setRequestFailed(true);
        return failed;
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
        if (cleared || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    @Override
    protected void onCleared() {
        cleared = true;
        for (String token : searchScopes.keySet()) cancelSearch(token);
        for (String token : activeSearchCalls.keySet()) cancelSearch(token);
        activeSearchCalls.clear();
        cancelledSearchTokens.clear();
        cancelledSearchTokenOrder.clear();
        downloadRequests.close();
        super.onCleared();
    }
}
