package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.utils.ConfigUrlParser;

import com.fongmi.android.tv.Setting;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Entity
public class Keep {

    private static final Map<String, Keep> VOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Keep> LIVE_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 256;

    public static void clearCache() {
        VOD_CACHE.clear();
        LIVE_CACHE.clear();
    }

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;
    @SerializedName("siteName")
    private String siteName;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("type")
    private int type;
    @SerializedName("cid")
    private int cid;

    public static List<Keep> arrayFrom(String str) {
        Type listType = new TypeToken<List<Keep>>() {}.getType();
        List<Keep> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getSiteName() {
        if (siteName != null && !siteName.isEmpty()) return siteName;
        Site site = VodConfig.get().getSite(getSiteKey());
        if (!site.getName().isEmpty()) return site.getName();
        String rawKey = VodConfig.rawSiteKey(getKeyPart(0));
        site = VodConfig.get().getSite(rawKey);
        if (!site.getName().isEmpty()) return site.getName();
        return rawKey;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
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

    private static String vodCacheKey(int cid, String key) {
        return cid + "@" + key;
    }

    private static String[] splitKey(String key) {
        return key == null ? new String[0] : key.split(AppDatabase.SYMBOL);
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

    private static Keep cache(Keep item) {
        if (item == null) return null;
        if (item.getType() == 1) {
            LIVE_CACHE.put(item.getKey(), item);
            trimCache(LIVE_CACHE);
        } else {
            VOD_CACHE.put(vodCacheKey(item.getCid(), item.getKey()), item);
            trimCache(VOD_CACHE);
        }
        return item;
    }

    private static void trimCache(Map<String, Keep> cache) {
        while (cache.size() > MAX_CACHE_SIZE) {
            String eldest = cache.keySet().stream().findFirst().orElse(null);
            if (eldest == null) return;
            cache.remove(eldest);
        }
    }

    private static Keep copy(Keep item) {
        if (item == null) return null;
        Keep copy = new Keep();
        copy.setKey(item.getKey());
        copy.setSiteName(item.getSiteName());
        copy.setVodPic(item.getVodPic());
        copy.setVodName(item.getVodName());
        copy.setCreateTime(item.getCreateTime());
        copy.setCid(item.getCid());
        copy.setType(item.getType());
        return copy;
    }

    private static List<Keep> copy(List<Keep> items) {
        List<Keep> copied = new ArrayList<>(items.size());
        for (Keep item : items) copied.add(copy(item));
        return copied;
    }

    public static Keep find(String key) {
        int cid = keyCid(key);
        Keep item = VOD_CACHE.get(vodCacheKey(cid, key));
        if (item != null) return copy(item);
        return copy(cache(AppDatabase.get().getKeepDao().find(cid, key)));
    }

    public static Keep find(int cid, String key) {
        Keep item = VOD_CACHE.get(vodCacheKey(cid, key));
        return item != null ? copy(item) : copy(cache(AppDatabase.get().getKeepDao().find(cid, key)));
    }

    public static boolean exist(String key) {
        Keep item = LIVE_CACHE.get(key);
        return item != null || cache(AppDatabase.get().getKeepDao().find(key)) != null;
    }

    public static void deleteAll() {
        AppDatabase.get().getKeepDao().delete();
        clearCache();
    }

    public static void delete(int cid) {
        AppDatabase.get().getKeepDao().delete(cid);
        for (String key : new ArrayList<>(VOD_CACHE.keySet())) if (key.startsWith(cid + "@")) VOD_CACHE.remove(key);
    }

    public static void deleteLoaded() {
        Map<String, Integer> idMap = new HashMap<>();
        Set<Integer> cids = new LinkedHashSet<>();
        for (Config item : ConfigRepository.get().urls()) idMap.put(item.getUrl(), item.getId());
        List<String> urls = VodConfig.get().getLoadUrls();
        if (urls.isEmpty()) {
            urls.addAll(ConfigUrlParser.parse(Setting.getVodConfigUrls()));
        }
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) continue;
            Integer cid = idMap.get(url.trim());
            if (cid != null && cid > 0) cids.add(cid);
        }
        if (cids.isEmpty()) {
            if (VodConfig.getCid() > 0) delete(VodConfig.getCid());
            return;
        }
        for (Integer cid : cids) delete(cid);
    }

    public static void delete(String key) {
        AppDatabase.get().getKeepDao().delete(key);
        LIVE_CACHE.remove(key);
    }

    public static List<Keep> getVod() {
        List<Keep> items = AppDatabase.get().getKeepDao().getVod();
        for (Keep item : items) cache(item);
        return copy(items);
    }

    public static List<Keep> getLive() {
        List<Keep> items = AppDatabase.get().getKeepDao().getLive();
        for (Keep item : items) cache(item);
        return copy(items);
    }

    public void save(int cid) {
        setCid(cid);
        AppDatabase.get().getKeepDao().insertOrUpdate(this);
        cache(this);
    }

    public void save() {
        AppDatabase.get().getKeepDao().insert(this);
        cache(this);
    }

    public Keep delete() {
        AppDatabase.get().getKeepDao().delete(getCid(), getKey());
        if (getType() == 1) LIVE_CACHE.remove(getKey());
        else VOD_CACHE.remove(vodCacheKey(getCid(), getKey()));
        return this;
    }

    private static void startSync(List<Config> configs, List<Keep> targets) {
        for (Keep target : targets) {
            for (Config config : configs) {
                if (target.getCid() == config.getId()) {
                    target.save(ConfigRepository.get().find(config).getId());
                }
            }
        }
    }

    public static void sync(List<Config> configs, List<Keep> targets) {
        App.execute(() -> {
            startSync(configs, targets);
            RefreshEvent.keep();
        });
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Keep)) return false;
        Keep other = (Keep) obj;
        return Objects.equals(getKey(), other.getKey())
                && getCid() == other.getCid()
                && getCreateTime() == other.getCreateTime()
                && Objects.equals(getVodName(), other.getVodName())
                && Objects.equals(getVodPic(), other.getVodPic())
                && Objects.equals(getSiteName(), other.getSiteName());
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(getKey());
        result = 31 * result + getCid();
        result = 31 * result + Long.hashCode(getCreateTime());
        result = 31 * result + Objects.hashCode(getVodName());
        result = 31 * result + Objects.hashCode(getVodPic());
        result = 31 * result + Objects.hashCode(getSiteName());
        return result;
    }
}
