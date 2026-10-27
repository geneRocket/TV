package com.fongmi.android.tv.test;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Subtitle;
import com.fongmi.android.tv.subtitle.SubtitleGateway;
import com.fongmi.android.tv.ui.dialog.SearchSubtitleDialog;
import com.fongmi.android.tv.viewmodel.SubtitleViewModel;
import java.io.IOException;
import java.util.Collections;

/** Native dialog/adapter fixture. Provider HTTP/parsing is tested separately, without live sites. */
public final class SubtitleFixtureDialog extends SearchSubtitleDialog {
    static String selected = "";
    static int searches, details;
    static boolean slowResolve;
    @NonNull @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
        return new ViewModelProvider.Factory() {
            @NonNull @Override @SuppressWarnings("unchecked") public <T extends ViewModel> T create(@NonNull Class<T> type) {
                return (T) new SubtitleViewModel(new SubtitleGateway() {
                    int secondAttempts;
                    public Request search(com.fongmi.android.tv.subtitle.SubtitleSource source,String keyword,int page,Callback<Page> callback) {
                        searches++;
                        if (source != com.fongmi.android.tv.subtitle.SubtitleSource.SUBHD) return search(keyword,page,callback);
                        Subtitle traditional = new Subtitle("Movie traditional","https://subhd.me/a/traditional",false);
                        traditional.setMovieTitle("Movie");traditional.setSource("SubHD");traditional.setLanguage("繁体");traditional.setExternal(true);
                        Subtitle cantonese = new Subtitle("Movie Cantonese","https://subhd.me/a/cantonese",false);
                        cantonese.setMovieTitle("Movie");cantonese.setSource("SubHD");cantonese.setLanguage("粤语");cantonese.setExternal(true);
                        callback.success(new Page(java.util.Arrays.asList(traditional,cantonese),false,false,1));return () -> {};
                    }
                    public Request search(String keyword,int page,Callback<Page> callback) {
                        if (keyword.equals("Empty")) callback.success(new Page(Collections.emptyList(),false,false,1));
                        else if (page == 1) callback.success(new Page(Collections.singletonList(
                                new Subtitle("Movie archive","https://fixture.test/archive",true)),true,true,3));
                        else if (page == 2 && secondAttempts++ == 0) callback.failure(new IOException("fixture page failure"));
                        else callback.success(new Page(Collections.singletonList(
                                new Subtitle("Movie archive " + page,"https://fixture.test/archive/" + page,true)),true,page < 3,3));
                        return () -> {};
                    }
                    public Request fallback(String keyword,Callback<Page> callback) {
                        callback.failure(new AssertionError("later page must not fall back")); return () -> {};
                    }
                    public Request details(Subtitle subtitle,Callback<Page> callback) {
                        details++;
                        callback.success(new Page(Collections.singletonList(new Subtitle("Movie.srt","https://fixture.test/movie.srt",false)),false,false,-1));
                        return () -> {};
                    }
                    public Request resolve(Subtitle subtitle,Callback<String> callback) {
                        if (slowResolve) App.post(() -> callback.success(subtitle.getUrl()), 1000);
                        else callback.success(subtitle.getUrl());
                        return () -> {};
                    }
                },task -> App.post(task));
            }
        };
    }
}
