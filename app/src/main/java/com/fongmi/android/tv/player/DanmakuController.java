package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Danmaku;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.ui.widget.DanmakuView;

/** Owns danmaku selection and the view lifecycle independently of the playback engine. */
final class DanmakuController implements DrawHandler.Callback {

    private DanmakuView view;
    private final Runnable preparedCallback;
    private List<Danmaku> sources = new ArrayList<>();
    private boolean visible;
    private Method setSpeed;
    private Method setSpeedFactor;
    private boolean speedMethodsResolved;

    DanmakuController(boolean visible, Runnable preparedCallback) {
        this.visible = visible;
        this.preparedCallback = preparedCallback;
    }

    void setView(DanmakuView view) {
        view.setCallback(this);
        this.view = view;
        speedMethodsResolved = false;
        setSpeed = null;
        setSpeedFactor = null;
        resolveSpeedMethods();
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
        if (isPrepared()) view.seekTo(position);
    }

    void pause() {
        if (isPrepared()) view.pause();
    }

    void stop() {
        if (isPrepared()) view.stop();
    }

    void release() {
        if (isPrepared()) view.release();
        view = null;
    }

    void setVisible(boolean visible, boolean playing, boolean buffering, long position, float speed) {
        this.visible = visible;
        updatePlayingState(playing, buffering, position, speed);
    }

    void updatePlayingState(boolean playing, boolean buffering, long position, float speed) {
        if (!isPrepared()) return;
        if (!visible) {
            view.hide();
            view.pause();
            return;
        }
        view.show();
        applySpeed(speed);
        if (playing && !buffering) view.start(position);
        else view.pause();
    }

    void applySpeed(float speed) {
        if (!isPrepared()) return;
        if (!speedMethodsResolved) resolveSpeedMethods();
        try {
            if (setSpeed != null) {
                setSpeed.invoke(view, speed);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            if (setSpeedFactor != null) setSpeedFactor.invoke(view, speed);
        } catch (Exception ignored) {
        }
    }

    private boolean isPrepared() {
        return view != null && view.isPrepared();
    }

    private void resolveSpeedMethods() {
        speedMethodsResolved = true;
        if (view == null) return;
        try {
            setSpeed = view.getClass().getMethod("setSpeed", float.class);
        } catch (Exception ignored) {
            setSpeed = null;
        }
        try {
            setSpeedFactor = view.getClass().getMethod("setSpeedFactor", float.class);
        } catch (Exception ignored) {
            setSpeedFactor = null;
        }
    }

    @Override
    public void prepared() {
        preparedCallback.run();
    }

    @Override
    public void updateTimer(DanmakuTimer timer) {
    }

    @Override
    public void danmakuShown(BaseDanmaku danmaku) {
    }

    @Override
    public void drawingFinished() {
    }
}
