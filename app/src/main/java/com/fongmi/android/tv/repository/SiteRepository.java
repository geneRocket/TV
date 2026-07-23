package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;

import java.util.List;

/** Persistence boundary for VOD site metadata reused across config refreshes. */
public final class SiteRepository {

    private static final SiteRepository INSTANCE = new SiteRepository();

    public static SiteRepository get() {
        return INSTANCE;
    }

    private SiteRepository() {
    }

    public List<Site> all() {
        return AppDatabase.get().getSiteDao().getAll();
    }
}
