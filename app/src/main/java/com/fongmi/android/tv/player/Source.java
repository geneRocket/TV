package com.fongmi.android.tv.player;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.extractor.Force;
import com.fongmi.android.tv.player.extractor.JianPian;
import com.fongmi.android.tv.player.extractor.Proxy;
import com.fongmi.android.tv.player.extractor.Push;
import com.fongmi.android.tv.player.extractor.Strm;
import com.fongmi.android.tv.player.extractor.TVBus;
import com.fongmi.android.tv.player.extractor.Thunder;
import com.fongmi.android.tv.player.extractor.Video;
import com.fongmi.android.tv.player.extractor.Youtube;
import com.fongmi.android.tv.player.extractor.ZLive;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Source {

    private static final int MAX_PRELOAD_TASKS_PER_FLAG = 8;
    private static final int MAX_PARSE_CACHE_SIZE = 64;

    private final List<Extractor> extractors;
    private final Map<String, List<Episode>> parseCache;

    private static class Loader {
        static volatile Source INSTANCE = new Source();
    }

    public static Source get() {
        return Loader.INSTANCE;
    }

    public Source() {
        extractors = new ArrayList<>();
        parseCache = Collections.synchronizedMap(new LinkedHashMap<String, List<Episode>>(MAX_PARSE_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Episode>> eldest) {
                return size() > MAX_PARSE_CACHE_SIZE;
            }
        });
        extractors.add(new Force());
        extractors.add(new Thunder());
        extractors.add(new JianPian());
        extractors.add(new Proxy());
        extractors.add(new Push());
        extractors.add(new Strm());
        extractors.add(new TVBus());
        extractors.add(new Video());
        extractors.add(new Youtube());
        extractors.add(new ZLive());
    }

    private Extractor getExtractor(Uri uri) {
        for (Extractor extractor : extractors) if (extractor.match(uri)) return extractor;
        return null;
    }

    private ParseTask getTask(Episode episode, int index) {
        String url = episode.getUrl();
        List<Episode> cached = getCachedEpisodes(url);
        if (cached != null) return new ParseTask(index, url, () -> cached);
        if (Thunder.Parser.match(url)) return new ParseTask(index, url, Thunder.Parser.get(url));
        if (Youtube.Parser.match(url)) return new ParseTask(index, url, Youtube.Parser.get(url));
        return null;
    }

    public void parse(List<Flag> flags) throws Exception {
        ExecutorService executor = ThreadPools.preloadParse();
        for (Flag flag : flags) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            List<Episode> originals = new ArrayList<>(flag.getEpisodes());
            List<ParseTask> tasks = new ArrayList<>();
            List<Callable<List<Episode>>> callables = new ArrayList<>();
            Map<Integer, List<Episode>> replacements = new HashMap<>();
            for (int i = 0; i < originals.size(); i++) {
                if (tasks.size() >= MAX_PRELOAD_TASKS_PER_FLAG) break;
                ParseTask task = getTask(originals.get(i), i);
                if (task == null) continue;
                tasks.add(task);
                callables.add(task.callable);
            }
            if (callables.isEmpty()) continue;
            List<Future<List<Episode>>> futures = executor.invokeAll(callables, 60, TimeUnit.SECONDS);
            for (int i = 0; i < futures.size(); i++) {
                try {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    List<Episode> episodes = futures.get(i).get();
                    if (episodes != null && !episodes.isEmpty()) {
                        String key = tasks.get(i).url;
                        putCachedEpisodes(key, episodes);
                        replacements.put(tasks.get(i).index, copyEpisodes(episodes));
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) throw e;
                    ThreadPools.log(e, "Episode preload parse failed.");
                }
            }
            flag.getEpisodes().clear();
            for (int i = 0; i < originals.size(); i++) {
                List<Episode> episodes = replacements.get(i);
                if (episodes != null) flag.getEpisodes().addAll(episodes);
                else flag.getEpisodes().add(originals.get(i));
            }
        }
    }

    public String fetch(Result result) throws Exception {
        String url = result.getUrl().v();
        if (TextUtils.isEmpty(url)) return "";
        Extractor extractor = getExtractor(UrlUtil.uri(url));
        if (extractor != null) result.setParse(0);
        return extractor == null ? url : extractor.fetch(url);
    }

    public String fetch(Channel channel) throws Exception {
        String url = channel.getCurrent();
        if (TextUtils.isEmpty(url)) return "";
        Extractor extractor = getExtractor(UrlUtil.uri(url));
        if (extractor != null) channel.setParse(0);
        return extractor == null ? url : extractor.fetch(url);
    }

    public void stop() {
        if (extractors == null) return;
        extractors.forEach(Extractor::stop);
    }

    public void exit() {
        if (extractors == null) return;
        App.execute(() -> extractors.forEach(Extractor::exit));
    }

    public interface Extractor {

        boolean match(Uri uri);

        String fetch(String url) throws Exception;

        void stop();

        void exit();
    }

    private static class ParseTask {

        private final int index;
        private final String url;
        private final Callable<List<Episode>> callable;

        public ParseTask(int index, String url, Callable<List<Episode>> callable) {
            this.index = index;
            this.url = url;
            this.callable = callable;
        }
    }

    private List<Episode> getCachedEpisodes(String url) {
        List<Episode> items = parseCache.get(url);
        return items == null ? null : copyEpisodes(items);
    }

    private void putCachedEpisodes(String url, List<Episode> episodes) {
        if (url == null || url.isEmpty() || episodes == null || episodes.isEmpty()) return;
        parseCache.put(url, copyEpisodes(episodes));
    }

    private List<Episode> copyEpisodes(List<Episode> episodes) {
        List<Episode> copies = new ArrayList<>();
        for (Episode item : episodes) copies.add(Episode.create(item.getName(), item.getDesc(), item.getUrl()));
        return copies;
    }
}
