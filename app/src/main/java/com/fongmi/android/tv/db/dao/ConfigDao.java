package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.Config;

import java.util.List;

@Dao
public abstract class ConfigDao extends BaseDao<Config> {

    @Query("SELECT * FROM Config WHERE type = :type ORDER BY time DESC")
    public abstract List<Config> findByType(int type);

    @Query("SELECT id, name, url, type, time FROM Config WHERE type = :type ORDER BY time DESC")
    public abstract List<Config.UrlItem> findUrlByType(int type);

    @Query("SELECT * FROM Config WHERE id = :id")
    public abstract Config findById(int id);

    @Query("SELECT * FROM Config WHERE type = :type ORDER BY time DESC LIMIT 1")
    public abstract Config findOne(int type);

    @Query("SELECT * FROM Config WHERE url = :url AND type = :type")
    public abstract Config find(String url, int type);

    @Query("UPDATE Config SET name = :name WHERE url = :url AND type = :type")
    public abstract int rename(String url, String name, int type);

    @Query("UPDATE OR IGNORE Config SET url = :url, name = :name, json = :json, time = :time "
            + "WHERE id = :id AND url = :previousUrl")
    public abstract int commitUrlEdit(int id, String previousUrl, String url, String name, String json, long time);

    @Query("DELETE FROM Config WHERE url = :url AND type = :type")
    public abstract void delete(String url, int type);

    @Query("DELETE FROM Config WHERE url = :url")
    public abstract void delete(String url);

    @Query("DELETE FROM Config WHERE type = :type")
    public abstract void delete(int type);

    @Query("UPDATE Config SET home = :home WHERE id = :id")
    public abstract void updateHome(int id, String home);

    @Query("UPDATE Config SET `parse` = :parse WHERE id = :id")
    public abstract void updateParse(int id, String parse);

    // Refresh payload only; metadata may have changed while the request was in flight.
    @Query("UPDATE Config SET json = :json, time = :time WHERE id = :id AND url = :url AND type = :type AND time <= :started")
    public abstract int updateCache(int id, String url, int type, String json, long time, long started);

    @Query("SELECT count(*) FROM Config WHERE type = :type")
    public abstract int countByType(int type);
}
