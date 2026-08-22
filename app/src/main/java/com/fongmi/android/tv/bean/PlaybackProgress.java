package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(primaryKeys = {"historyKey", "episodeId"})
public class PlaybackProgress {

    @NonNull
    private String historyKey;
    @NonNull
    private String episodeId;
    private int cid;
    private String episodeName;
    private String episodeUrl;
    private long position;
    private long duration;
    private long updatedAt;

    public PlaybackProgress() {
        historyKey = "";
        episodeId = "";
        episodeName = "";
        episodeUrl = "";
    }

    @NonNull public String getHistoryKey() { return historyKey; }
    public void setHistoryKey(@NonNull String historyKey) { this.historyKey = historyKey; }
    @NonNull public String getEpisodeId() { return episodeId; }
    public void setEpisodeId(@NonNull String episodeId) { this.episodeId = episodeId; }
    public int getCid() { return cid; }
    public void setCid(int cid) { this.cid = cid; }
    public String getEpisodeName() { return episodeName; }
    public void setEpisodeName(String episodeName) { this.episodeName = episodeName == null ? "" : episodeName; }
    public String getEpisodeUrl() { return episodeUrl; }
    public void setEpisodeUrl(String episodeUrl) { this.episodeUrl = episodeUrl == null ? "" : episodeUrl; }
    public long getPosition() { return position; }
    public void setPosition(long position) { this.position = Math.max(position, 0); }
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = Math.max(duration, 0); }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
