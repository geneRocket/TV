package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Track;

import java.util.List;

/** Data-access boundary for per-video player track selections. */
public final class TrackRepository {

    private static final TrackRepository INSTANCE = new TrackRepository();

    public static TrackRepository get() {
        return INSTANCE;
    }

    private TrackRepository() {
    }

    public List<Track> find(String historyKey) {
        return Track.find(historyKey);
    }

    public void save(Track track, String historyKey) {
        track.setKey(historyKey);
        track.save();
    }

    public void delete(String historyKey) {
        Track.delete(historyKey);
    }
}
