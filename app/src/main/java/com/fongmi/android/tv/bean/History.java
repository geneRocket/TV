package com.fongmi.android.tv.bean;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.repository.HistoryStore;
import com.fongmi.android.tv.utils.PersistedEntryKey;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


@Entity
public class History {
    public static void clearCache() {
        HistoryStore.clearCache();
    }

    public static void cancelPendingWrites() {
        HistoryStore.cancelPendingWrites();
    }

    public static void withWritesPaused(Runnable action) {
        HistoryStore.withWritesPaused(action);
    }

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodFlag")
    private String vodFlag;
    @SerializedName("vodRemarks")
    private String vodRemarks;
    @SerializedName("episodeUrl")
    private String episodeUrl;
    @SerializedName("revSort")
    private boolean revSort;
    @SerializedName("revPlay")
    private boolean revPlay;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("opening")
    private long opening;
    @SerializedName("ending")
    private long ending;
    @SerializedName("position")
    private long position;
    @SerializedName("duration")
    private long duration;
    @SerializedName("speed")
    private float speed;
    @SerializedName("player")
    private int player;
    @SerializedName("scale")
    private int scale;
    @SerializedName("cid")
    private int cid;

    public static History objectFrom(String str) {
        return App.gson().fromJson(str, History.class);
    }

    public static List<History> arrayFrom(String str) {
        Type listType = new TypeToken<List<History>>() {}.getType();
        List<History> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public History() {
        this.speed = 1;
        this.scale = -1;
        this.player = -1;
    }

    public static History create(String key, int cid, Vod vod, float speed) {
        return HistoryStore.create(key, cid, vod, speed);
    }

    public static History prepare(History history, String key, int cid, Vod vod, float speed, String mark) {
        return HistoryStore.prepare(history, key, cid, vod, speed, mark);
    }

    public static String historyKey(String siteKey, String vodId, String vodName, int cid) {
        return PersistedEntryKey.create(siteKey, vodId, vodName, cid);
    }

    static String keyForConfig(String key, int cid) {
        return PersistedEntryKey.rebase(key, cid);
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodFlag() {
        return vodFlag;
    }

    public void setVodFlag(String vodFlag) {
        this.vodFlag = vodFlag;
    }

    public String getVodRemarks() {
        return vodRemarks == null ? "" : vodRemarks;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public String getEpisodeUrl() {
        return episodeUrl == null ? "" : episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    public boolean isRevSort() {
        return revSort;
    }

    public void setRevSort(boolean revSort) {
        this.revSort = revSort;
    }

    public boolean isRevPlay() {
        return revPlay;
    }

    public void setRevPlay(boolean revPlay) {
        this.revPlay = revPlay;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getOpening() {
        return opening;
    }

    public void setOpening(long opening) {
        this.opening = Math.max(0, opening);
    }

    public long getEnding() {
        return ending;
    }

    public void setEnding(long ending) {
        this.ending = Math.max(0, ending);
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = Math.max(0, position);
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = Math.max(0, duration);
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed > 0 && !Float.isNaN(speed) && !Float.isInfinite(speed) ? speed : 1;
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        this.player = player;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    public String getSiteName() {
        Site site = VodConfig.get().getSite(getSiteKey());
        if (!site.getName().isEmpty()) return site.getName();
        String rawKey = VodConfig.rawSiteKey(PersistedEntryKey.site(getKey()));
        site = VodConfig.get().getSite(rawKey);
        if (!site.getName().isEmpty()) return site.getName();
        return rawKey;
    }

    public String getSiteKey() {
        return VodConfig.siteKey(getCid(), PersistedEntryKey.site(getKey()));
    }

    public String getVodId() {
        return PersistedEntryKey.video(getKey());
    }

    static String vodIdFromKey(String key) {
        return PersistedEntryKey.video(key);
    }

    private static History copy(History item) {
        if (item == null) return null;
        History copy = new History();
        copy.setKey(item.getKey());
        copy.setVodPic(item.getVodPic());
        copy.setVodName(item.getVodName());
        copy.setVodFlag(item.getVodFlag());
        copy.setVodRemarks(item.vodRemarks);
        copy.setEpisodeUrl(item.episodeUrl);
        copy.setRevSort(item.isRevSort());
        copy.setRevPlay(item.isRevPlay());
        copy.setCreateTime(item.getCreateTime());
        copy.setOpening(item.getOpening());
        copy.setEnding(item.getEnding());
        copy.setPosition(item.getPosition());
        copy.setDuration(item.getDuration());
        copy.setSpeed(item.getSpeed());
        copy.setPlayer(item.getPlayer());
        copy.setScale(item.getScale());
        copy.setCid(item.getCid());
        return copy;
    }

    private static List<History> copy(List<History> items) {
        List<History> copied = new ArrayList<>(items.size());
        for (History item : items) copied.add(copy(item));
        return copied;
    }

    public Flag getFlag() {
        return Flag.create(getVodFlag());
    }

    public Episode getEpisode() {
        return Episode.create(getVodRemarks(), getEpisodeUrl());
    }

    public History copy() {
        return copy(this);
    }

    public int getSiteVisible() {
        return TextUtils.isEmpty(getSiteName()) ? View.GONE : View.VISIBLE;
    }

    public int getRevPlayText() {
        return isRevPlay() ? R.string.play_backward : R.string.play_forward;
    }

    public int getRevPlayHint() {
        return isRevPlay() ? R.string.play_backward_hint : R.string.play_forward_hint;
    }

    public boolean isNew() {
        return getCreateTime() == 0 && getPosition() == 0;
    }

    public static List<History> get() {
        return HistoryStore.get();
    }

    public static List<History> getLoaded() {
        return HistoryStore.getLoaded();
    }

    public static List<History> get(int cid) {
        return HistoryStore.get(cid);
    }

    public static boolean isNewerOrBetterSameVersion(History item, History old) {
        return HistoryStore.isNewerOrBetterSameVersion(item, old);
    }

    public static History find(String key) {
        return HistoryStore.find(key);
    }

    public static void delete(int cid) {
        HistoryStore.delete(cid);
    }

    public static void deleteLoaded() {
        HistoryStore.deleteLoaded();
    }

    public static Runnable prepareDeleteLoaded() {
        return HistoryStore.prepareDeleteLoaded();
    }

    public void update() {
        HistoryStore.update(this);
    }

    public History update(int cid) {
        return HistoryStore.update(this, cid);
    }

    public History update(int cid, List<History> items) {
        return HistoryStore.update(this, cid, items);
    }

    public History save() {
        return HistoryStore.save(this);
    }

    public void saveAsync() {
        HistoryStore.saveAsync(this);
    }

    public History delete() {
        return HistoryStore.delete(this);
    }

    public List<History> find() {
        return HistoryStore.find(this);
    }

    List<History> find(int cid) {
        return HistoryStore.find(this, cid);
    }

    public History findBestMatch() {
        return HistoryStore.findBestMatch(this);
    }

    public void findEpisode(List<Flag> flags) {
        HistoryStore.findEpisode(this, flags);
    }

    public static void sync(List<History> targets) {
        HistoryStore.sync(targets);
    }

    public static void sync(int cid, List<History> targets, boolean force) {
        HistoryStore.sync(cid, targets, force);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof History)) return false;
        History other = (History) obj;
        return Objects.equals(getKey(), other.getKey())
                && getCid() == other.getCid()
                && getCreateTime() == other.getCreateTime()
                && getPosition() == other.getPosition()
                && getDuration() == other.getDuration()
                && Objects.equals(getVodName(), other.getVodName())
                && Objects.equals(getVodPic(), other.getVodPic())
                && Objects.equals(getVodRemarks(), other.getVodRemarks())
                && Objects.equals(getEpisodeUrl(), other.getEpisodeUrl());
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(getKey());
        result = 31 * result + getCid();
        result = 31 * result + Long.hashCode(getCreateTime());
        result = 31 * result + Long.hashCode(getPosition());
        result = 31 * result + Long.hashCode(getDuration());
        result = 31 * result + Objects.hashCode(getVodName());
        result = 31 * result + Objects.hashCode(getVodPic());
        result = 31 * result + Objects.hashCode(getVodRemarks());
        result = 31 * result + Objects.hashCode(getEpisodeUrl());
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
