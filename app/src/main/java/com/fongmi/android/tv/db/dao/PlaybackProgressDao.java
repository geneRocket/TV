package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.PlaybackProgress;

import java.util.List;

@Dao
public abstract class PlaybackProgressDao extends BaseDao<PlaybackProgress> {

    @Query("SELECT * FROM PlaybackProgress WHERE historyKey = :historyKey AND episodeId = :episodeId")
    public abstract PlaybackProgress find(String historyKey, String episodeId);

    @Query("SELECT * FROM PlaybackProgress WHERE historyKey = :historyKey ORDER BY updatedAt DESC")
    public abstract List<PlaybackProgress> find(String historyKey);

    @Query("DELETE FROM PlaybackProgress WHERE historyKey = :historyKey")
    public abstract void delete(String historyKey);

    @Query("DELETE FROM PlaybackProgress WHERE cid = :cid")
    public abstract void delete(int cid);

    @Query("DELETE FROM PlaybackProgress")
    public abstract void delete();
}
