package com.fongmi.android.tv.bean;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Entity
public class History {

    private static final Map<String, History> CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        CACHE.clear();
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
        String rawKey = VodConfig.rawSiteKey(getKeyPart(0));
        site = VodConfig.get().getSite(rawKey);
        if (!site.getName().isEmpty()) return site.getName();
        return rawKey;
    }

    public String getSiteKey() {
        return VodConfig.siteKey(getCid(), getKeyPart(0));
    }

    public String getVodId() {
        return getKeyPart(1);
    }

    private String getKeyPart(int index) {
        String[] values = splitKey(getKey());
        return index >= 0 && index < values.length ? values[index] : "";
    }

    private static String cacheKey(int cid, String key) {
        return cid + "@" + key;
    }

    private static String[] splitKey(String key) {
        return key == null ? new String[0] : key.split(AppDatabase.SYMBOL);
    }

    private static History cache(History item) {
        if (item == null) return null;
        CACHE.put(cacheKey(item.getCid(), item.getKey()), item);
        return item;
    }

    private static List<History> cache(List<History> items) {
        for (History item : items) cache(item);
        return items;
    }

    private static History copy(History item) {
        if (item == null) return null;
        History copy = new History();
        copy.setKey(item.getKey());
        copy.setVodPic(item.getVodPic());
        copy.setVodName(item.getVodName());
        copy.setVodFlag(item.getVodFlag());
        copy.setVodRemarks(item.getVodRemarks());
        copy.setEpisodeUrl(item.getEpisodeUrl());
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

    private static void removeCache(int cid, String key) {
        CACHE.remove(cacheKey(cid, key));
    }

    private static int keyCid(String key) {
        String[] values = splitKey(key);
        if (values.length >= 3) {
            try {
                return Integer.parseInt(values[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        return VodConfig.siteCid(values.length > 0 ? values[0] : "", VodConfig.getCid());
    }

    private static void removeCache(int cid) {
        for (String key : new ArrayList<>(CACHE.keySet())) if (key.startsWith(cid + "@")) CACHE.remove(key);
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
        return get(VodConfig.getCid());
    }

    public static List<History> getLoaded() {
        List<Integer> cids = getLoadedCids();
        if (cids.size() == 1) return get(cids.get(0));
        List<History> items = cache(AppDatabase.get().getHistoryDao().find(cids));
        items.sort(Comparator.comparingLong(History::getCreateTime).reversed());
        return copy(unique(items, true));
    }

    public static List<History> get(int cid) {
        List<History> items = cache(AppDatabase.get().getHistoryDao().find(cid));
        items.sort(Comparator.comparingLong(History::getCreateTime).reversed());
        return copy(unique(items, false));
    }

    private static List<History> unique(List<History> items, boolean crossCid) {
        Map<String, History> unique = new LinkedHashMap<>();
        for (History item : items) {
            String key = uniqueKey(item, crossCid);
            History old = unique.get(key);
            if (old == null || item.getCreateTime() > old.getCreateTime()) unique.put(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    private static String uniqueKey(History item, boolean crossCid) {
        String name = normalizedName(item.getVodName());
        String scope = crossCid ? "" : item.getCid() + "@";
        if (TextUtils.isEmpty(name)) return scope + item.getCid() + "@" + item.getKey();
        return scope + name;
    }

    private static String normalizedName(String name) {
        if (TextUtils.isEmpty(name)) return "";
        String value = Trans.s2t(name).trim().toLowerCase(Locale.US);
        String old;
        do {
            old = value;
            value = value.replaceAll("(?i)[\\s\\p{P}\\p{S}]+$", "");
            value = value.replaceAll("(?i)(?:粤语版|国语版|普通话版|粤语中字|国语中字|国粤双语|双语版|中英双字|中文字幕|中字|粤语|国语|普通话|高清版|hd中字|hd|bd|正片|全集)$", "");
        } while (!old.equals(value));
        return value.replaceAll("[\\s\\p{P}\\p{S}]+", "");
    }

    private static List<Integer> getLoadedCids() {
        Set<Integer> cids = new LinkedHashSet<>();
        Map<String, Integer> idMap = new HashMap<>();
        for (Config item : Config.findUrls()) idMap.put(item.getUrl(), item.getId());
        List<String> urls = VodConfig.get().getLoadUrls();
        if (urls.isEmpty()) {
            for (String value : Setting.getVodConfigUrls().split("[\\n\\r,，;；|]+")) {
                String url = value.trim();
                if (!url.isEmpty()) urls.add(url);
            }
        }
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) continue;
            Integer cid = idMap.get(url.trim());
            if (cid != null && cid > 0) cids.add(cid);
        }
        if (cids.isEmpty()) cids.add(VodConfig.getCid());
        return new ArrayList<>(cids);
    }

    public static History find(String key) {
        int cid = keyCid(key);
        History item = CACHE.get(cacheKey(cid, key));
        if (item != null) return copy(item);
        return copy(cache(AppDatabase.get().getHistoryDao().find(cid, key)));
    }

    public static void delete(int cid) {
        AppDatabase.get().getHistoryDao().delete(cid);
        removeCache(cid);
    }

    public static void deleteLoaded() {
        List<Integer> cids = getLoadedCids();
        if (cids.isEmpty()) return;
        if (cids.size() == 1) {
            delete(cids.get(0));
            return;
        }
        AppDatabase.get().getHistoryDao().delete(cids);
        for (Integer cid : cids) removeCache(cid);
    }

    private void checkParam(History item) {
        if (getOpening() == 0) setOpening(item.getOpening());
        if (getEnding() == 0) setEnding(item.getEnding());
        if (getSpeed() == 1) setSpeed(item.getSpeed());
    }

    private void checkProgress(History item) {
        if (item == null) return;
        if (item.getCreateTime() < getCreateTime()) return;
        setPosition(item.getPosition());
        if (item.getDuration() > 0) setDuration(item.getDuration());
        if (item.getCreateTime() > getCreateTime()) setCreateTime(item.getCreateTime());
    }

    private void merge(List<History> items, boolean force) {
        for (History item : items) {
            if (getDuration() > 0 && item.getDuration() > 0) {
                long diff = Math.abs(getDuration() - item.getDuration());
                long threshold = Math.min(getDuration() / 10, 10 * 60 * 1000);
                if (diff > threshold) continue;
            }
            if (!force && getKey().equals(item.getKey())) continue;
            checkParam(item);
            checkProgress(item);
            item.deleteSelf();
        }
    }

    public void update() {
        merge(find(), false);
        save();
    }

    public History update(int cid) {
        return update(cid, find());
    }

    public History update(int cid, List<History> items) {
        setCid(cid);
        merge(items, true);
        return save();
    }

    public History save() {
        if (TextUtils.isEmpty(getKey())) return this;
        AppDatabase.get().getHistoryDao().insertOrUpdate(this);
        return cache(this);
    }

    public History delete() {
        for (History item : find()) item.deleteSelf();
        return deleteSelf();
    }

    private History deleteSelf() {
        AppDatabase.get().getHistoryDao().delete(getCid(), getKey());
        AppDatabase.get().getTrackDao().delete(getKey());
        removeCache(getCid(), getKey());
        return this;
    }

    public List<History> find() {
        String name = normalizedName(getVodName());
        List<Integer> cids = getLoadedCids();
        List<History> items = TextUtils.isEmpty(name) ? AppDatabase.get().getHistoryDao().findByName(cids, getVodName()) : findByNormalizedName(name, cids);
        Map<String, History> unique = new LinkedHashMap<>();
        for (History item : cache(items)) unique.put(item.getKey(), item);
        return copy(new ArrayList<>(unique.values()));
    }

    private List<History> findByNormalizedName(String name, List<Integer> cids) {
        List<History> result = new ArrayList<>();
        for (History item : AppDatabase.get().getHistoryDao().find(cids)) {
            if (name.equals(normalizedName(item.getVodName()))) result.add(item);
        }
        return result;
    }

    public void findEpisode(List<Flag> flags) {
        if (flags.size() > 0 && TextUtils.isEmpty(getVodFlag())) {
            setVodFlag(flags.get(0).getFlag());
        }
        if (flags.size() > 0 && TextUtils.isEmpty(getVodRemarks()) && flags.get(0).getEpisodes().size() > 0) {
            setVodRemarks(flags.get(0).getEpisodes().get(0).getName());
        }
        EpisodeMatch best = null;
        for (History item : find()) {
            EpisodeMatch match = findEpisode(flags, item.getVodRemarks());
            if (match == null) continue;
            if (best == null || item.getCreateTime() > best.history.getCreateTime() || (best.history.getPosition() <= 0 && item.getPosition() > 0)) best = match.history(item);
        }
        if (best == null) return;
        setVodFlag(best.flag.getFlag());
        setVodRemarks(best.episode.getName());
        setEpisodeUrl(best.episode.getUrl());
        checkParam(best.history);
        checkProgress(best.history);
    }

    private EpisodeMatch findEpisode(List<Flag> flags, String remarks) {
        if (TextUtils.isEmpty(remarks)) return null;
        for (Flag flag : flags) {
            Episode episode = flag.find(remarks, true);
            if (episode != null) return new EpisodeMatch(flag, episode);
        }
        return null;
    }

    private boolean isSameEpisode(History item) {
        if (item == null) return false;
        if (!TextUtils.isEmpty(getEpisodeUrl()) && TextUtils.equals(getEpisodeUrl(), item.getEpisodeUrl())) return true;
        return isSameEpisodeName(getVodRemarks(), item.getVodRemarks());
    }

    private boolean isSameEpisode(EpisodeMatch target, EpisodeMatch match) {
        if (match == null) return false;
        if (target == null) return true;
        return isSameEpisodeName(target.episode.getName(), match.episode.getName());
    }

    private boolean isSameEpisodeName(String first, String second) {
        if (TextUtils.isEmpty(first) || TextUtils.isEmpty(second)) return false;
        return first.equalsIgnoreCase(second) || Util.getDigit(first) == Util.getDigit(second) && Util.getDigit(first) != -1;
    }

    private static class EpisodeMatch {

        private final Flag flag;
        private final Episode episode;
        private History history;

        private EpisodeMatch(Flag flag, Episode episode) {
            this.flag = flag;
            this.episode = episode;
        }

        private EpisodeMatch history(History history) {
            this.history = history;
            return this;
        }
    }

    private static void startSync(List<History> targets) {
        for (History target : targets) {
            List<History> items = target.find();
            if (items.isEmpty()) {
                target.update(VodConfig.getCid(), items);
                continue;
            }
            for (History item : items) {
                if (target.getCreateTime() > item.getCreateTime()) {
                    target.update(VodConfig.getCid(), items);
                    break;
                }
            }
        }
    }

    public static void sync(List<History> targets) {
        App.execute(() -> {
            startSync(targets);
            RefreshEvent.history();
        });
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
