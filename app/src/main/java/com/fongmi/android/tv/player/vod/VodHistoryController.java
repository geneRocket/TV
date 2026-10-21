package com.fongmi.android.tv.player.vod;

import android.text.TextUtils;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.player.vod.VodPlaybackPhase;
import com.fongmi.android.tv.player.vod.VodPlaybackState;

/** Owns VOD history mutation and throttled persistence rules. */
public final class VodHistoryController {

    private static final long SAVE_INTERVAL_MS = 3000;

    private long lastSaveAt;
    private long lastSavedPosition = -1;
    private long lastSavedDuration = -1;
    private boolean mutatedDuringLoad;

    public void beginLoad() {
        mutatedDuringLoad = false;
    }

    public History applyLoaded(History current, History loaded) {
        return mutatedDuringLoad ? current : loaded;
    }

    public boolean shouldCommit(VodPlaybackPhase phase) {
        return phase == VodPlaybackPhase.READY || phase == VodPlaybackPhase.PAUSED || phase == VodPlaybackPhase.ENDED;
    }

    public void updateFields(History history, long position, long duration) {
        if (history == null) return;
        if (position >= 0) history.setPosition(position);
        if (duration > 0) history.setDuration(duration);
    }

    public boolean updateProgress(History history, boolean canCommit, long position, long duration) {
        if (history == null || !canCommit || position < 0) return false;
        updateFields(history, position, duration);
        return true;
    }

    public void saveSnapshot(History history) {
        saveSnapshot(history, true);
    }

    public void saveSnapshot(History history, boolean async) {
        if (history == null || Setting.isIncognito()) return;
        mutatedDuringLoad = true;
        lastSavedPosition = history.getPosition();
        lastSavedDuration = history.getDuration();
        if (async) history.saveAsync();
        else history.save();
    }

    public boolean saveNow(History history, boolean canCommit, long position, long duration) {
        if (history == null || Setting.isIncognito()) return false;
        if (!updateProgress(history, canCommit, position, duration)) return false;
        markChanged(history, System.currentTimeMillis());
        saveSnapshot(history, true);
        return true;
    }

    public boolean saveTick(History history, boolean canCommit, long position, long duration, long now) {
        if (Setting.isIncognito()) return false;
        if (!updateProgress(history, canCommit, position, duration)) return false;
        if (now - lastSaveAt < SAVE_INTERVAL_MS) return false;
        lastSaveAt = now;
        if (position == lastSavedPosition && duration == lastSavedDuration) return false;
        markChanged(history, now);
        saveSnapshot(history);
        return true;
    }

    public boolean commitEpisode(History history, String flag, Episode episode, VodPlaybackState state) {
        if (history == null || Setting.isIncognito() || episode == null || state == null || (TextUtils.equals(history.getEpisodeUrl(), episode.getUrl())
                && TextUtils.equals(history.getVodFlag(), flag)
                && TextUtils.equals(history.getVodRemarks(), episode.getName()))) return false;
        history.setEpisodeUrl(episode.getUrl());
        history.setVodRemarks(episode.getName());
        history.setVodFlag(flag);
        history.setPosition(state.getPosition());
        history.setDuration(0);
        markChanged(history, System.currentTimeMillis());
        saveSnapshot(history);
        return true;
    }

    private void markChanged(History history, long now) {
        if (history == null) return;
        history.setCreateTime(now);
        lastSaveAt = now;
    }
}
