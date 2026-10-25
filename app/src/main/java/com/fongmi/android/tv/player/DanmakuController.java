package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Danmaku;

import android.os.SystemClock;
import com.fongmi.android.tv.ui.custom.PlaybackDanmakuView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;

/** Owns danmaku selection and the view lifecycle independently of the playback engine. */
final class DanmakuController implements DrawHandler.Callback {

    interface Output {
        boolean isPrepared();
        void show(); void hide(); void pause(); void resume();
        void start(long position); void seekTo(long position); void stop(); void release();
        void prepare(BaseDanmakuParser parser, DanmakuContext context);
    }

    private Output view;
    private final DanmakuClock clock;
    private final Runnable preparedCallback;
    private List<Danmaku> sources = new ArrayList<>();
    private boolean visible;
    private boolean started, drawing, shown;

    DanmakuController(boolean visible, Runnable preparedCallback) {
        this(visible, preparedCallback, SystemClock::uptimeMillis, null);
    }

    DanmakuController(boolean visible, Runnable preparedCallback, LongSupplier uptime, Output view) {
        this.visible = visible;
        this.preparedCallback = preparedCallback;
        this.clock = new DanmakuClock(uptime);
        this.view = view;
    }

    void setView(PlaybackDanmakuView view) {
        view.setCallback(this);
        view.setPlaybackTime(clock::position);
        this.view = new Output() {
            public boolean isPrepared() { return view.isPrepared(); }
            public void show() { view.show(); }
            public void hide() { view.hide(); }
            public void pause() { view.pause(); }
            public void resume() { view.resume(); }
            public void start(long position) { view.start(position); }
            public void seekTo(long position) { view.seekTo(position); }
            public void stop() { view.stop(); }
            public void release() { view.release(); }
            public void prepare(BaseDanmakuParser parser, DanmakuContext context) { view.prepare(parser, context); }
        };
        started = drawing = shown = false;
    }

    void setSources(List<Danmaku> items) {
        sources = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    List<Danmaku> getSources() {
        return new ArrayList<>(sources);
    }

    Danmaku getSource() {
        for (Danmaku item : sources) if (item.isSelected()) return item;
        return sources.isEmpty() ? Danmaku.empty() : sources.get(0);
    }

    void select(Danmaku item) {
        if (item == null || item.isEmpty()) return;
        boolean exists = false;
        for (Danmaku source : sources) {
            boolean selected = source.getUrl().equals(item.getUrl());
            source.setSelected(selected);
            exists |= selected;
        }
        if (!exists) {
            item.setSelected(true);
            sources.add(0, item);
        }
    }

    void clearSources() {
        sources.clear();
    }

    void seekTo(long position) {
        clock.seek(position);
        if (isPrepared()) view.seekTo(position);
    }

    void pause() {
        clock.pause();
        if (isPrepared() && drawing) view.pause();
        drawing = false;
    }

    void stop() {
        clock.pause();
        started = drawing = false;
        if (isPrepared()) view.stop();
    }

    void reset() {
        clock.update(0, 1, false);
        started = drawing = shown = false;
        if (view != null) view.release();
    }

    void prepare(BaseDanmakuParser parser, DanmakuContext context) {
        started = drawing = shown = false;
        if (view != null) view.prepare(parser, context);
    }

    void release() {
        reset();
        view = null;
    }

    void setVisible(boolean visible, boolean playing, boolean buffering, long position, float speed) {
        this.visible = visible;
        updatePlayingState(playing, buffering, position, speed);
    }

    void updatePlayingState(boolean playing, boolean buffering, long position, float speed) {
        boolean running = playing && !buffering;
        clock.update(position, speed, running);
        if (!isPrepared()) return;
        if (!visible) {
            if (shown) view.hide();
            if (drawing) view.pause();
            shown = drawing = false;
            return;
        }
        if (!shown) {
            view.show();
            if (started) view.seekTo(position);
            shown = true;
        }
        if (running) {
            if (!started) { view.start(position); started = true; }
            else if (!drawing) view.resume();
            drawing = true;
        } else {
            if (drawing) view.pause();
            drawing = false;
        }
    }

    void applySpeed(float speed) {
        clock.speed(speed);
    }

    boolean needsPositionSync() { return drawing && visible && isPrepared(); }

    private boolean isPrepared() {
        return view != null && view.isPrepared();
    }

    @Override
    public void prepared() {
        preparedCallback.run();
    }

    @Override
    public void updateTimer(DanmakuTimer timer) {
        timer.update(clock.position());
    }

    @Override
    public void danmakuShown(BaseDanmaku danmaku) {
    }

    @Override
    public void drawingFinished() {
    }
}
