package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/** One bounded request per source; one failed source cannot erase the others' results. */
final class SubtitleFanOut implements SubtitleGateway.Request {
    private final SubtitleGateway.Callback<SubtitleGateway.Page> callback;
    private final List<SubtitleGateway.Request> requests = new ArrayList<>();
    private final Map<SubtitleSource, SubtitleGateway.Page> pages = new EnumMap<>(SubtitleSource.class);
    private final Set<SubtitleSource> failures = EnumSet.noneOf(SubtitleSource.class);
    private final int page;
    private int remaining;
    private boolean cancelled;

    private SubtitleFanOut(int page, int count, SubtitleGateway.Callback<SubtitleGateway.Page> callback) {
        this.page = page; this.remaining = count; this.callback = callback;
    }
    static SubtitleGateway.Request start(int page, BiFunction<SubtitleSource, SubtitleGateway.Callback<SubtitleGateway.Page>, SubtitleGateway.Request> start,
                                         SubtitleGateway.Callback<SubtitleGateway.Page> callback) {
        SubtitleSource[] sources = {SubtitleSource.ASSRT, SubtitleSource.SUBHD, SubtitleSource.WEBSITE};
        SubtitleFanOut batch = new SubtitleFanOut(page, sources.length, callback);
        for (SubtitleSource source : sources) {
            AtomicBoolean answered = new AtomicBoolean();
            SubtitleGateway.Callback<SubtitleGateway.Page> response = new SubtitleGateway.Callback<SubtitleGateway.Page>() {
                public void success(SubtitleGateway.Page value) { if (answered.compareAndSet(false,true)) batch.complete(source,value); }
                public void failure(Throwable error) { if (answered.compareAndSet(false,true)) batch.complete(source,null); }
            };
            try { batch.track(start.apply(source, response)); }
            catch (RuntimeException error) { response.failure(error); }
        }
        return batch;
    }

    private synchronized void track(SubtitleGateway.Request request) {
        if (request == null) return;
        if (cancelled) request.cancel(); else requests.add(request);
    }

    private void complete(SubtitleSource source, SubtitleGateway.Page result) {
        SubtitleGateway.Page merged; boolean failed;
        synchronized (this) {
            if (cancelled) return;
            if (result == null) failures.add(source); else pages.put(source,result);
            if (--remaining != 0) return;
            LinkedHashMap<String,Subtitle> items = new LinkedHashMap<>();
            List<String> unavailable = new ArrayList<>();
            for (SubtitleSource unavailableSource : failures) unavailable.add(unavailableSource.name());
            boolean next = false, unknownTotal = false; int total = -1;
            for (SubtitleGateway.Page found : pages.values()) {
                for (Subtitle item : found.items) items.putIfAbsent(item.getUrl(),item);
                next |= found.hasNext; total = Math.max(total,found.total);
                unknownTotal |= found.hasNext && found.total <= 0;
                unavailable.addAll(found.unavailable);
            }
            failed = pages.isEmpty() || (page > 1 && items.isEmpty() && !failures.isEmpty());
            merged = new SubtitleGateway.Page(new ArrayList<>(items.values()),false,next,unknownTotal ? -1 : total,unavailable);
        }
        if (failed) callback.failure(new IOException("Subtitle sources unavailable")); else callback.success(merged);
    }

    @Override public synchronized void cancel() {
        cancelled = true;
        for (SubtitleGateway.Request request : requests) request.cancel();
        requests.clear();
    }
}
