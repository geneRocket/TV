package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.db.AppDatabase;

import java.util.List;

/** Persistence boundary for live-source metadata reused across config refreshes. */
public final class LiveRepository {

    private static final LiveRepository INSTANCE = new LiveRepository();

    public static LiveRepository get() {
        return INSTANCE;
    }

    private LiveRepository() {
    }

    public List<Live> all() {
        return AppDatabase.get().getLiveDao().getAll();
    }
}
