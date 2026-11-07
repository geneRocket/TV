package com.fongmi.android.tv.ui.dialog;

import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.SiteSearchSession;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.utils.SearchSorter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class DetailFallbackDialog {

    private DetailFallbackDialog() {}

    public static void show(FragmentActivity activity, SiteViewModel model, String originalSite, String title, Consumer<Vod> open) {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (site.isSearchable() && !originalSite.equals(site.getKey())) sites.add(site);
        }

        SiteSearchSession search = new SiteSearchSession(model, "detail-fallback", 3);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        List<Vod> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.detail_fallback_searching)
                .setNegativeButton(android.R.string.cancel, null)
                .setAdapter(adapter, (ignored, index) -> open.accept(candidates.get(index)))
                .create();
        Observer<Result> observer = result -> {
            if (!dialog.isShowing() || !search.accepts(result)) return;
            for (Vod candidate : result.getList()) {
                if (candidate == null || candidate.getVodId().isEmpty() ||
                        !SearchSorter.matchesDetailFallback(title, candidate.getVodName(), candidate.getVodYear())) continue;
                if (!seen.add(SearchSorter.key(candidate))) continue;
                candidates.add(candidate);
                String year = candidate.getVodYear();
                String name = candidate.getVodName() + (year.isEmpty() ? "" : " (" + year + ")");
                adapter.add(name + " · " + candidate.getSiteName());
            }
            if (!candidates.isEmpty()) dialog.setTitle(R.string.detail_fallback_title);
        };
        DefaultLifecycleObserver lifecycle = new DefaultLifecycleObserver() {
            @Override public void onDestroy(LifecycleOwner owner) { dialog.dismiss(); }
        };
        dialog.setOnDismissListener(ignored -> {
            search.close();
            model.search().removeObserver(observer);
            activity.getLifecycle().removeObserver(lifecycle);
        });
        dialog.show();
        activity.getLifecycle().addObserver(lifecycle);
        model.search().observe(activity, observer);
        search.start(sites, SearchSorter.detailFallbackKeyword(title), false, () -> {
            if (dialog.isShowing() && candidates.isEmpty()) dialog.setTitle(R.string.detail_fallback_empty);
        });
    }
}
