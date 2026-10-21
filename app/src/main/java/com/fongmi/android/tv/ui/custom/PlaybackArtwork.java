package com.fongmi.android.tv.ui.custom;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ImgUtil;

/** Owns cover requests so delayed artwork cannot write into a newer film or a destroyed page. */
public final class PlaybackArtwork implements AutoCloseable {
    public interface Listener {
        void loaded(Drawable drawable);
        void failed(Drawable drawable);
    }
    public interface Loader {
        void load(String url, CustomTarget<Drawable> target);
        void clear(CustomTarget<Drawable> target);
    }

    private final Loader loader;
    private final Listener listener;
    private CustomTarget<Drawable> target;
    private String url;
    private long generation;
    private boolean closed;

    public PlaybackArtwork(Listener listener) {
        this(listener, new Loader() {
            @Override public void load(String url, CustomTarget<Drawable> target) { ImgUtil.load(url, R.drawable.radio, target); }
            @Override public void clear(CustomTarget<Drawable> target) { Glide.with(App.get()).clear(target); }
        });
    }

    public PlaybackArtwork(Listener listener, Loader loader) { this.listener = listener; this.loader = loader; }

    public void load(String next) {
        if (closed) return;
        next = next == null ? "" : next;
        if (next.equals(url)) return;
        reset();
        url = next;
        long expected = generation;
        target = new CustomTarget<>() {
            private boolean current() { return !closed && expected == generation; }
            @Override public void onResourceReady(@NonNull Drawable drawable, @Nullable Transition<? super Drawable> transition) {
                if (current()) listener.loaded(drawable);
            }
            @Override public void onLoadFailed(@Nullable Drawable drawable) { if (current()) listener.failed(drawable); }
            @Override public void onLoadCleared(@Nullable Drawable drawable) {}
        };
        loader.load(url, target);
    }

    public void reset() {
        generation++;
        url = null;
        if (target != null) loader.clear(target);
        target = null;
    }

    @Override public void close() { closed = true; reset(); }
}
