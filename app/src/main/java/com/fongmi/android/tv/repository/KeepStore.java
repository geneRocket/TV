package com.fongmi.android.tv.repository;
import com.fongmi.android.tv.bean.*;
import java.util.*;
/** Legacy entity API adapter. No cache or persistence state lives here. */
public final class KeepStore {
    private KeepStore() {}
    private static KeepStorage data() { return PersistenceServices.keep(); }
    public static void clearCache() { data().clearCache(); }
    public static int keyCid(String key) { return data().keyCid(key,PersistenceServices.currentId()); }
    public static Keep find(String key) { return data().find(key,PersistenceServices.currentId()); }
    public static Keep find(int cid,String key) { return data().find(cid,key); }
    public static boolean exist(String key) { return data().exist(key); }
    public static void deleteAll() { data().deleteAll(); }
    public static void delete(int cid) { data().delete(cid); }
    public static void deleteLoaded() { data().deleteLoaded(PersistenceServices.captureScope()); }
    public static void delete(String key) { data().delete(key); }
    public static List<Keep> getVod() { return data().getVod(); }
    public static List<Keep> getLive() { return data().getLive(); }
    public static void save(Keep subject,int cid) { data().save(subject,cid); }
    public static void save(Keep subject) { data().save(subject); }
    public static Keep delete(Keep subject) { return data().delete(subject); }
    public static void startSync(List<Config> configs,List<Keep> targets) { data().startSync(PersistenceServices.importIds(configs),targets); }
    public static void sync(List<Config> configs,List<Keep> targets) { sync(configs,targets,false); }
    public static void sync(List<Config> configs,List<Keep> targets,boolean force) { data().sync(PersistenceServices.importIds(configs),targets,force); }
}
