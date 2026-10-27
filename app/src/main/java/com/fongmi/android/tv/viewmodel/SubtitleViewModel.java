package com.fongmi.android.tv.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.fongmi.android.tv.bean.Subtitle;
import com.fongmi.android.tv.subtitle.SubtitleSearchState;
import com.fongmi.android.tv.subtitle.HttpSubtitleGateway;
import com.fongmi.android.tv.subtitle.SubtitleSearchController;
import com.fongmi.android.tv.subtitle.SubtitleGateway;
import com.fongmi.android.tv.subtitle.SubtitleSource;
import com.fongmi.android.tv.subtitle.SubtitlePreference;
import com.fongmi.android.tv.subtitle.SubtitleQueries;
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;

/** Android lifecycle/observation adapter; provider and request policy live outside the ViewModel. */
public class SubtitleViewModel extends ViewModel {
    private final MutableLiveData<SubtitleSearchState> results = new MutableLiveData<>();
    private final SubtitleSearchController controller;

    public SubtitleViewModel() {
        this(new HttpSubtitleGateway(OkHttp.client().newBuilder()
                .callTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(true).build(), AppTaskScheduler.get(), 8_000),
                task -> AppTaskScheduler.get().post(task, 0));
    }

    public SubtitleViewModel(SubtitleGateway gateway, Executor delivery) {
        controller = new SubtitleSearchController(gateway, delivery, results::setValue, SubtitleSource.ALL,
                new SubtitleQueries(Trans::toSimplified, Trans::toTraditional));
    }

    public LiveData<SubtitleSearchState> searchResults() { return results; }
    public void searchResult(String title, int page) { controller.search(title, page); }
    public void previous() { controller.previous(); }
    public void next() { controller.next(); }
    public void retry() { controller.retry(); }
    public boolean backToSearch() { return controller.backToSearch(); }
    public void source(SubtitleSource value) { controller.source(value); }
    public void preference(SubtitlePreference value) { controller.preference(value); }
    public void getSearchResultSubtitleUrls(Subtitle subtitle) { controller.details(subtitle); }
    public void getSubtitleUrl(Subtitle subtitle, SubtitleLoader loader) {
        controller.resolve(subtitle, loader == null ? null : loader::loadSubtitle);
    }
    public void cancelSubtitleResolve() { controller.cancelResolve(); }
    @Override protected void onCleared() { controller.close(); super.onCleared(); }
    public interface SubtitleLoader { void loadSubtitle(Subtitle subtitle); }
}
