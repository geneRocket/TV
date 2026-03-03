package com.fongmi.android.tv.player;

import android.net.Uri;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.UrlUtil;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Source {

    private final List<Extractor> extractors;

    private static class Loader {
        static volatile Source INSTANCE = new Source();
    }

    public static Source get() {
        return Loader.INSTANCE;
    }

    public Source() {
        extractors = new ArrayList<>();
        extractors.add(new Force());
        extractors.add(new JianPian());
        extractors.add(new Proxy());
        extractors.add(new Push());
        extractors.add(new Strm());
        extractors.add(new Thunder());
        extractors.add(new TVBus());
        extractors.add(new Video());
        extractors.add(new Youtube());
        extractors.add(new ZLive());
    }

    private Extractor getExtractor(Uri uri) {
        for (Extractor extractor : extractors) if (extractor.match(uri)) return extractor;
        return null;
    }

    private void addCallable(Iterator<Episode> iterator, List<Callable<List<Episode>>> items) {
        String url = iterator.next().getUrl();
        if (Thunder.Parser.match(url)) {
            items.add(Thunder.Parser.get(url));
            iterator.remove();
        } else if (Youtube.Parser.match(url)) {
            items.add(Youtube.Parser.get(url));
            iterator.remove();
        }
    }

    public void parse(List<Flag> flags) throws Exception {
        ExecutorService executor = ThreadPools.parse();
        for (Flag flag : flags) {
            List<Callable<List<Episode>>> items = new ArrayList<>();
            Iterator<Episode> iterator = flag.getEpisodes().iterator();
            while (iterator.hasNext()) addCallable(iterator, items);
            for (Future<List<Episode>> future : executor.invokeAll(items, 30, TimeUnit.SECONDS)) {
                try {
                    List<Episode> episodes = future.get();
                    if (episodes != null) flag.getEpisodes().addAll(episodes);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public String fetch(Result result) throws Exception {
        String url = result.getUrl().v();
        Extractor extractor = getExtractor(UrlUtil.uri(url));
        if (extractor != null) result.setParse(0);
        if (extractor instanceof Video) result.setParse(1);
        return extractor == null ? url : extractor.fetch(url);
    }

    public String fetch(Channel channel) throws Exception {
        String url = channel.getCurrent();
        Extractor extractor = getExtractor(Uri.parse(url));
        if (extractor != null) channel.setParse(0);
        if (extractor instanceof Video) channel.setParse(1);
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
}
