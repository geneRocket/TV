package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.utils.PersistedEntryKey;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.repository.KeepStore;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
public class Keep {
    public static void clearCache() {
        KeepStore.clearCache();
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
        String rawKey = VodConfig.rawSiteKey(PersistedEntryKey.site(getKey()));
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
        return VodConfig.siteKey(getCid(), PersistedEntryKey.site(getKey()));
    }

    public String getVodId() {
        return PersistedEntryKey.video(getKey());
    }

    private static Keep copy(Keep item) {
        if (item == null) return null;
        Keep copy = new Keep();
        copy.setKey(item.getKey());
        copy.setSiteName(item.siteName);
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
        return KeepStore.find(key);
    }

    public static Keep find(int cid, String key) {
        return KeepStore.find(cid, key);
    }

    public static boolean exist(String key) {
        return KeepStore.exist(key);
    }

    public static void deleteAll() {
        KeepStore.deleteAll();
    }

    public static void delete(int cid) {
        KeepStore.delete(cid);
    }

    public static void deleteLoaded() {
        KeepStore.deleteLoaded();
    }

    public static void delete(String key) {
        KeepStore.delete(key);
    }

    public static List<Keep> getVod() {
        return KeepStore.getVod();
    }

    public static List<Keep> getLive() {
        return KeepStore.getLive();
    }

    public void save(int cid) {
        KeepStore.save(this, cid);
    }

    public void save() {
        KeepStore.save(this);
    }

    public Keep delete() {
        return KeepStore.delete(this);
    }

    public static void sync(List<Config> configs, List<Keep> targets) {
        KeepStore.sync(configs, targets);
    }

    public static void sync(List<Config> configs, List<Keep> targets, boolean force) {
        KeepStore.sync(configs, targets, force);
    }

    public Keep copy() { return copy(this); }

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
