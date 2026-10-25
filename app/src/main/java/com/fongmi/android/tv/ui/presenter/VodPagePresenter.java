package com.fongmi.android.tv.ui.presenter;

import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.player.vod.VodHistoryController;
import com.fongmi.android.tv.repository.*;
import com.fongmi.android.tv.utils.TaskScope;
import com.fongmi.android.tv.utils.PersistedEntryKey;
import java.util.*;
import java.util.function.LongSupplier;

/** Page business state. Views/adapters render these models but do not decide their selection. */
public final class VodPagePresenter implements AutoCloseable {
    public interface View { void historyReady(History history, Vod detail); }
    private final HistoryRepository histories;
    private final KeepRepository favorites;
    private final VodHistoryController historyController;
    private final TaskScope tasks;
    private final View view;
    private final LongSupplier clock;
    private final long timeout;
    private final VodEpisodeSelection selection = new VodEpisodeSelection();
    private HistoryRepository requestHistory;
    private KeepRepository requestFavorites;
    private PersistenceScope requestScope;
    private String token = "", key = "", mark = "";
    private int cid;
    private float speed;
    private boolean incognito, closed;
    private History history, preloaded;
    private String preloadedKey;

    public VodPagePresenter(HistoryRepository histories, KeepRepository favorites,
                            VodHistoryController historyController, TaskScope tasks,
                            long timeout, LongSupplier clock, View view) {
        this.histories = histories; this.favorites = favorites; this.historyController = historyController;
        this.tasks = tasks; this.timeout = timeout; this.clock = clock; this.view = view;
    }

    /** Must be called before launching detail work; every asynchronous read uses this captured scope. */
    public void begin(String token, String key, int cid, String mark, float speed, boolean incognito) {
        if (closed) return;
        tasks.cancel("history"); tasks.cancel("prefetch");
        PersistenceScope scope = Objects.equals(key, this.key) && requestScope != null
                ? requestScope : histories.captureScope();
        if (!scope.ids().contains(cid)) scope = PersistenceScope.single(cid);
        requestScope = scope;
        requestHistory = histories.in(scope); requestFavorites = favorites.in(scope);
        this.token = token; this.key = key; this.cid = cid; this.mark = mark; this.speed = speed;
        this.incognito = incognito; preloaded = null; preloadedKey = null;
        // The previous playing history remains readable until detail is rendered, so a handoff can save it.
    }

    public void prefetchHistory() {
        if (closed || requestHistory == null) return;
        String expected = token, requestedKey = key;
        HistoryRepository repository = requestHistory;
        tasks.submit("prefetch", () -> repository.find(requestedKey), timeout, value -> {
            if (accepts(expected)) { preloaded = value; preloadedKey = requestedKey; }
        }, error -> null, null);
    }

    public void loadHistory(Vod detail, String requestedToken) {
        if (!accepts(requestedToken) || requestHistory == null) return;
        String expected = token, requestedKey = key, requestedMark = mark;
        int requestedCid = cid; float requestedSpeed = speed; boolean privateMode = incognito;
        HistoryRepository repository = requestHistory;
        History cached = Objects.equals(preloadedKey, requestedKey) ? preloaded : null;
        preloaded = null; preloadedKey = null; tasks.cancel("prefetch");
        history = repository.prepareDefault(requestedKey, requestedCid, detail, requestedSpeed, requestedMark);
        historyController.beginLoad();
        tasks.submit("history", () -> {
            History result = cached == null
                    ? repository.prepareBestMatch(requestedKey, requestedCid, detail, requestedSpeed, requestedMark)
                    : repository.prepare(cached, requestedKey, requestedCid, detail, requestedSpeed, requestedMark);
            if (privateMode) repository.delete(result);
            return result;
        }, timeout, loaded -> {
            if (!accepts(expected)) return;
            history = historyController.applyLoaded(history, loaded);
            view.historyReady(history, detail);
        }, error -> repository.prepareDefault(requestedKey, requestedCid, detail, requestedSpeed, requestedMark), null);
    }

    public boolean accepts(String expected) { return !closed && Objects.equals(token, expected); }
    public void cancelRequest() { token = ""; tasks.cancel("history"); tasks.cancel("prefetch"); }
    public History history() { return history; }
    public void history(History value) { history = value; }
    public int configId(String site, String video, int fallback) {
        int explicit = PersistedEntryKey.config(site, -1);
        if (explicit >= 0) return explicit;
        return !key.isEmpty() && Objects.equals(PersistedEntryKey.site(key), site)
                && Objects.equals(PersistedEntryKey.video(key), video) ? cid : fallback;
    }

    public void detail(Vod detail) { selection.detail(detail); }
    public void clearSelection() { selection.clear(); }
    public List<Flag> flags() { return selection.flags(); }
    public int flagIndex() { return selection.flagIndex(); }
    public Flag flag() { return selection.flag(); }
    public int episodeIndex() { return selection.episodeIndex(); }
    public boolean selectFlag(Flag flag, boolean fallback) { return selection.selectFlag(flag,fallback); }
    public boolean selectEpisode(Episode episode) { return selection.selectEpisode(episode); }
    public Episode chooseEpisode(Flag flag,String name,int fallback) { return selection.chooseEpisode(flag,name,fallback); }
    public void reverseEpisodes() { selection.reverseEpisodes(); }

    private KeepRepository favoriteRepository() { return requestFavorites == null ? favorites : requestFavorites; }
    public boolean hasFavorite(String key) { return favoriteRepository().find(key) != null; }
    public void createFavorite(String key, int cid, String site, String title, String picture) {
        Keep keep = new Keep(); keep.setKey(key); keep.setCid(cid); keep.setSiteName(site);
        keep.setVodName(title); keep.setVodPic(picture); keep.setCreateTime(clock.getAsLong());
        favoriteRepository().save(keep);
    }
    public boolean toggleFavorite(String key, int cid, String site, String title, String picture) {
        Keep keep = favoriteRepository().find(key);
        if (keep != null) { favoriteRepository().delete(keep); return false; }
        createFavorite(key, cid, site, title, picture); return true;
    }
    @Override public void close() {
        if (closed) return;
        closed = true; tasks.close(); preloaded = null; selection.clear();
    }
}
