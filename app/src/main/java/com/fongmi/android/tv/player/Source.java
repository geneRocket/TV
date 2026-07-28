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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Source {

    private static final int MAX_PRELOAD_TASKS_PER_FLAG = 8;
    private static final int MAX_PARSE_CACHE_SIZE = 64;

    private final List<Extractor> extractors;
    private final Map<String, List<Episode>> parseCache;
    private volatile Extractor activeExtractor;

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
        if (episode == null || TextUtils.isEmpty(episode.getUrl())) return null;
        String url = episode.getUrl();
        if (Thunder.Parser.match(url)) return new ParseTask(index, url, Thunder.Parser.get(url));
        if (Youtube.Parser.match(url)) return new ParseTask(index, url, Youtube.Parser.get(url));
        return null;
    }

    public void parse(List<Flag> flags) throws Exception {
        if (flags == null || flags.isEmpty()) return;
        ExecutorService executor = ThreadPools.preloadParse();
        for (Flag flag : flags) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (flag == null || flag.getEpisodes() == null || flag.getEpisodes().isEmpty()) continue;
            List<Episode> originals = new ArrayList<>(flag.getEpisodes());
            List<ParseTask> tasks = new ArrayList<>();
            Map<Integer, List<Episode>> replacements = new HashMap<>();
            for (int i = 0; i < originals.size(); i++) {
                Episode episode = originals.get(i);
                String url = episode == null ? "" : episode.getUrl();
                List<Episode> cached = getCachedEpisodes(url);
                if (cached != null) {
                    replacements.put(i, cached);
                    continue;
                }
                if (tasks.size() >= MAX_PRELOAD_TASKS_PER_FLAG) continue;
                ParseTask task = getTask(episode, i);
                if (task == null) continue;
                tasks.add(task);
            }
            if (!tasks.isEmpty()) {
                List<ParseTask> accepted = new ArrayList<>(tasks.size());
                List<Future<List<Episode>>> futures = new ArrayList<>(tasks.size());
                for (ParseTask task : tasks) {
                    try {
                        futures.add(executor.submit(task.callable));
                        accepted.add(task);
                    } catch (RejectedExecutionException error) {
                        ThreadPools.log(error, "Episode preload queue is full; keeping original episode.");
                    }
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
                for (int i = 0; i < futures.size(); i++) {
                    try {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) throw new TimeoutException();
                        List<Episode> episodes = futures.get(i).get(remaining, TimeUnit.NANOSECONDS);
                        if (episodes != null && !episodes.isEmpty()) {
                            String key = accepted.get(i).url;
                            putCachedEpisodes(key, episodes);
                            replacements.put(accepted.get(i).index, copyEpisodes(episodes));
                        }
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            cancel(futures);
                            throw e;
                        }
                        if (e instanceof TimeoutException) {
                            cancel(futures);
                            ThreadPools.log(e, "Episode preload parse timed out.");
                            break;
                        }
                        ThreadPools.log(e, "Episode preload parse failed.");
                    }
                }
            }
            if (replacements.isEmpty()) continue;
            flag.getEpisodes().clear();
            for (int i = 0; i < originals.size(); i++) {
                List<Episode> episodes = replacements.get(i);
                if (episodes != null) flag.getEpisodes().addAll(episodes);
                else flag.getEpisodes().add(originals.get(i));
            }
        }
    }

    private void cancel(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) future.cancel(true);
    }

    public String fetch(Result result) throws Exception {
        return fetch(result.getUrl().v(), result::setParse);
    }

    public String fetch(Channel channel) throws Exception {
        return fetch(channel.getCurrent(), channel::setParse);
    }

    private String fetch(String url, java.util.function.Consumer<Integer> parseSetter) throws Exception {
        if (TextUtils.isEmpty(url)) return "";
        Extractor extractor = getExtractor(UrlUtil.uri(url));
        if (extractor != null) parseSetter.accept(0);
        prepare(extractor);
        return extractor == null ? url : extractor.fetch(url);
    }

    private void prepare(Extractor extractor) {
        Extractor previous = activeExtractor;
        if (previous != null) previous.stop();
        activeExtractor = extractor;
    }

    public void stop() {
        if (extractors == null) return;
        extractors.forEach(Extractor::stop);
        activeExtractor = null;
    }

    public void exit() {
        if (extractors == null) return;
        parseCache.clear();
        activeExtractor = null;
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
