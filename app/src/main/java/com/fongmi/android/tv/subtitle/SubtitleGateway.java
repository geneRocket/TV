package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Provider I/O. Callbacks may be immediate or arrive on any thread. */
public interface SubtitleGateway {
    interface Request { void cancel(); }
    interface Callback<T> {
        void success(T value);
        void failure(Throwable error);
    }
    final class Page {
        public final List<Subtitle> items;
        public final boolean zip, hasNext;
        public final int total;
        public final List<String> unavailable;
        public Page(List<Subtitle> items, boolean zip, boolean hasNext, int total) {
            this(items, zip, hasNext, total, Collections.emptyList());
        }
        public Page(List<Subtitle> items, boolean zip, boolean hasNext, int total, List<String> unavailable) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.zip = zip; this.hasNext = hasNext; this.total = total;
            this.unavailable = Collections.unmodifiableList(new ArrayList<>(unavailable));
        }
    }
    Request search(String keyword, int page, Callback<Page> callback);
    default Request search(SubtitleSource source, String keyword, int page, Callback<Page> callback) {
        return source == SubtitleSource.WEBSITE ? fallback(keyword, callback) : search(keyword, page, callback);
    }
    Request fallback(String keyword, Callback<Page> callback);
    Request details(Subtitle subtitle, Callback<Page> callback);
    Request resolve(Subtitle subtitle, Callback<String> callback);
}
