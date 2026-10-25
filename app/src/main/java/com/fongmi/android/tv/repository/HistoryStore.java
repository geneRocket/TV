package com.fongmi.android.tv.repository;
import com.fongmi.android.tv.bean.*;
import java.util.*;
/** Legacy entity API adapter. New application code uses an injected HistoryRepository. */
public final class HistoryStore {
    private HistoryStore() {}
    private static HistoryStorage data() { return PersistenceServices.history(); }
    private static PersistenceScope scope() { return PersistenceServices.captureScope(); }
    public static void clearCache() { data().clearCache(); }
    public static void cancelPendingWrites() { data().cancelPendingWrites(); }
    public static void withWritesPaused(Runnable action) { data().withWritesPaused(action); }
    public static History create(String key, int cid, Vod vod, float speed) { return data().create(key,cid,vod,speed,scope()); }
    public static History prepare(History history, String key, int cid, Vod vod, float speed, String mark) { return data().prepare(history,key,cid,vod,speed,mark,scope()); }
    public static int keyCid(String key) { return data().keyCid(key,PersistenceServices.currentId()); }
    public static List<History> get() { return data().get(scope().currentId()); }
    public static List<History> getLoaded() { return data().getLoaded(scope()); }
    public static List<History> get(int cid) { return data().get(cid); }
    public static boolean isNewerOrBetterSameVersion(History item, History old) { return HistoryStorage.isNewerOrBetterSameVersion(item,old); }
    public static History find(String key) { return data().find(key,PersistenceServices.currentId()); }
    public static void delete(int cid) { data().delete(cid); }
    public static void deleteLoaded() { data().deleteLoaded(scope()); }
    public static void update(History subject) { data().update(subject,scope()); }
    public static History update(History subject, int cid) { return data().update(subject,cid,scope()); }
    public static History update(History subject, int cid, List<History> items) { return data().update(subject,cid,items); }
    public static History save(History subject) { return data().save(subject); }
    public static void saveAsync(History subject) { data().saveAsync(subject); }
    public static History delete(History subject) { return data().delete(subject,scope()); }
    public static List<History> find(History subject) { return data().find(subject,scope()); }
    public static List<History> find(History subject, int cid) { return data().find(subject,cid); }
    public static History findBestMatch(History subject) { return data().findBestMatch(subject,scope()); }
    public static void findEpisode(History subject,List<Flag> flags) { data().findEpisode(subject,flags,scope()); }
    public static void startSync(int cid,List<History> targets) { data().startSync(cid,targets); }
    public static void sync(List<History> targets) { data().sync(scope().currentId(),targets,false); }
    public static void sync(int cid,List<History> targets,boolean force) { data().sync(cid,targets,force); }
    public interface SyncAccess extends HistoryStorage.SyncAccess {}
    public static void startSync(int cid, List<History> targets, SyncAccess access) { HistoryStorage.startSync(cid, targets, access); }
    public static Runnable prepareDeleteLoaded() { PersistenceScope captured = scope(); return () -> data().deleteLoaded(captured); }
    public static Runnable prepareDeleteLoaded(java.util.function.Supplier<List<String>> urls, java.util.function.IntSupplier fallback,
                                               java.util.function.BiConsumer<List<String>,Integer> delete) {
        List<String> captured = Collections.unmodifiableList(new ArrayList<>(urls.get())); int cid = fallback.getAsInt();
        return () -> delete.accept(captured,cid);
    }
}
