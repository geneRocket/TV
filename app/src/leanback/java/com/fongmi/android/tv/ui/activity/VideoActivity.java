package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.DiffCallback;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.ui.presenter.VodPagePresenter;
import com.fongmi.android.tv.repository.HistoryRepository;
import com.fongmi.android.tv.repository.DoubanRecommendRepository;
import com.fongmi.android.tv.repository.KeepRepository;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Part;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.repository.TrackRepository;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.PlaybackCommand;
import com.fongmi.android.tv.player.PlaybackInput;
import com.fongmi.android.tv.player.vod.VodPlaybackFlow;
import com.fongmi.android.tv.player.vod.VodSessionAdapter;
import com.fongmi.android.tv.player.vod.VodPlayerPort;
import com.fongmi.android.tv.player.vod.VodHistorySession;
import com.fongmi.android.tv.player.vod.VodPlaybackLifecycle;
import com.fongmi.android.tv.ui.custom.PlaybackArtwork;
import com.fongmi.android.tv.utils.CallbackScope;
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.player.vod.VodRecoveryFlow;
import com.fongmi.android.tv.utils.MainThreadExecutor;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SearchRequestToken;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.model.SiteSearchSession;
import com.fongmi.android.tv.player.IjkUtil;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.ui.custom.PlaybackVideoViews;
import com.fongmi.android.tv.ui.custom.PlaybackSubtitleLauncher;
import com.fongmi.android.tv.player.PlaybackMarkers;
import com.fongmi.android.tv.player.PlaybackSeekGesture;
import com.fongmi.android.tv.player.session.SwitchablePlayerSession;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.player.resolver.VodPlaybackResolver;
import com.fongmi.android.tv.player.resolver.SpiderPlaybackGateway;
import com.fongmi.android.tv.player.vod.StartPolicy;
import com.fongmi.android.tv.player.vod.DefaultPlaybackItemFactory;
import com.fongmi.android.tv.player.vod.VodEpisodeCatalog;
import com.fongmi.android.tv.player.vod.VodSourceMatcher;
import com.fongmi.android.tv.player.vod.VodPlaybackCoordinator;
import com.fongmi.android.tv.player.vod.VodPlaybackPhase;
import com.fongmi.android.tv.player.vod.VodPlaybackState;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.player.danmu.Parser;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.dialog.LoudnessDialog;
import com.fongmi.android.tv.ui.dialog.DescDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeDialog;
import com.fongmi.android.tv.ui.dialog.FileChooserDialog;
import com.fongmi.android.tv.ui.dialog.PlayerDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.dialog.VolumeDialog;
import com.fongmi.android.tv.ui.presenter.ArrayPresenter;
import com.fongmi.android.tv.ui.presenter.EpisodePresenter;
import com.fongmi.android.tv.ui.presenter.FlagPresenter;
import com.fongmi.android.tv.ui.presenter.ParsePresenter;
import com.fongmi.android.tv.ui.presenter.PartPresenter;
import com.fongmi.android.tv.ui.presenter.QuickPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.ui.player.TvVodPlaybackStateBinder;
import com.fongmi.android.tv.ui.player.TvVodPlaybackNavigation;
import com.fongmi.android.tv.ui.presenter.VodDetailPresenter;
import com.fongmi.android.tv.player.vod.VodHistoryController;
import com.fongmi.android.tv.ui.player.TvVodSourceCandidates;
import com.fongmi.android.tv.player.vod.VodSourceSearch;
import com.fongmi.android.tv.ui.player.TvVodSelectionState;
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.EpisodeMatcher;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.TaskScope;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchSorter;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Trans;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class VideoActivity extends BaseActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, TrackDialog.ChooserListener, PlayerDialog.Listener, ArrayPresenter.OnClickListener, Clock.Callback {

    private static final long SOURCE_SWITCH_DETAIL_TIMEOUT_MS = 5000;
    private static final long HISTORY_LOAD_TIMEOUT_MS = 5000;
    private static final String TASK_HISTORY = "history";
    private static final int QUICK_FLUSH_DELAY_MS = 100;
    private static final int SOURCE_SEARCH_CONCURRENCY = Math.min(10, ThreadPools.searchConcurrency());
    private static final DiffCallback<Vod> QUICK_DIFF = new DiffCallback<Vod>() {
        @Override
        public boolean areItemsTheSame(@NonNull Vod oldItem, @NonNull Vod newItem) {
            return SearchSorter.key(oldItem).equals(SearchSorter.key(newItem));
        }

        @Override
        public boolean areContentsTheSame(@NonNull Vod oldItem, @NonNull Vod newItem) {
            return oldItem.getVodName().equals(newItem.getVodName())
                    && oldItem.getSiteName().equals(newItem.getSiteName());
        }
    };
    private static final int REQUEST_DANMAKU_FILE = 9998;
    private static final Map<String, List<String>> PART_CACHE = new LinkedHashMap<String, List<String>>(24, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            return size() > 24;
        }
    };

    private static int safeIndex(int index, int length) {
        if (length <= 0) return 0;
        return Math.max(0, Math.min(index, length - 1));
    }

    private static String textOf(TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString();
    }

    private final class PlaybackStatePort implements TvVodPlaybackStateBinder.Port {

        @Override public boolean isBackground() { return VideoActivity.this.isBackground(); }
        @Override public boolean isBuffering() { return VideoActivity.this.isBuffering(); }

        @Override public void beginPreparing() {
            setInitTrack(true);
            setTrackVisible(false);
            mClock.setCallback(VideoActivity.this);
        }

        @Override public void clearPlaybackOverlay() { hideInfoAndCenter(); }
        @Override public void showProgress() { VideoActivity.this.showProgress(); }
        @Override public void stopContentSearch() { mPlaybackFlow.sources().finish(); }
        @Override public void clearPlaybackTimeout() { VideoActivity.this.clearPlaybackTimeout(); }
        @Override public void updateMetadata() { setMetadata(); }
        @Override public void clearBrokenSources() { mSourceCandidates.clearBroken(); }
        @Override public void hideProgress() { VideoActivity.this.hideProgress(); }
        @Override public void resetTransientPlayerState() { mPlayers.reset(); }
        @Override public void applyDefaultTrack() { setDefaultTrack(); }
        @Override public void showTracks() { setTrackVisible(true); }

        @Override public void persistPlayerSelection() {
            if (mPage.history() != null) mPage.history().setPlayer(mPlayers.getPlayer());
        }

        @Override public void updateVideoSize() {
            String text = mPlayers.getSizeText();
            setPlainTextIfChanged(mBinding.widget.size, text);
            setPlainTextIfChanged(mBinding.display.size, text);
        }
    }

    private final class NavigationPort implements TvVodPlaybackNavigation.Port {

        @Override public int currentEpisodeIndex() { return getEpisodePosition(); }
        @Override public void capturePosition() { capturePlaybackPosition(); }
        @Override public boolean isSourceSwitching() { return VideoActivity.this.isSourceSwitching(); }
        @Override public void keepCurrentPositionOnNextPlay() { mPlaybackFlow.intent().keepCurrentPositionOnNextPlay(); }
        @Override public void playFlag(Flag flag, int episode) { VideoActivity.this.playFlag(flag, episode); }

        @Override public boolean selectFlag(Flag item, boolean notify) {
            if (mFlagAdapter.size() == 0 || item == null || item.getEpisodes().isEmpty()) return false;
            if (item.isActivated() && !isSourceSwitching()) return false;
            int index = mFlagAdapter.indexOf(item);
            if (index < 0) return false;
            if (!mPage.selectFlag(item, false)) return false;
            mSelection.selectFlag(index, mFlagAdapter.size());
            mBinding.flag.setSelectedPosition(index);
            notifyItemChanged(mBinding.flag, mFlagAdapter);
            setEpisodeAdapter(item.getEpisodes());
            setQualityVisible(false);
            if (notify) Notify.show(getString(R.string.play_switch_flag, item.getFlag()));
            return true;
        }

        @Override public void selectEpisode(Episode item) {
            int flag = getFlagPosition();
            if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
            mPage.selectEpisode(item);
            mSelection.selectEpisode(getFlag().getEpisodes().indexOf(item), mEpisodeAdapter.size());
            setEpisodeSelectedPosition(getEpisodePosition());
            notifyItemChanged(getEpisodeView(), mEpisodeAdapter);
            onReset(false);
            clearSourceSwitch();
        }

        @Override public boolean selectParse(Parse item, boolean notify) {
            if (item.isActivated()) return false;
            VodConfig.get().setParse(item);
            mSelection.selectParse(mParseAdapter.indexOf(item), mParseAdapter.size());
            notifyItemChanged(mBinding.control.parse, mParseAdapter);
            if (notify) Notify.show(getString(R.string.play_switch_parse, item.getName()));
            return true;
        }

        @Override public void refreshPlayback() { onRefresh(); }
        @Override public void stopSourceSearch() { mPlaybackFlow.sources().stop(); }
        @Override public void openDetail(Vod item, boolean notify) {
            if (notify) Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
            mDetail.open(item);
        }

        @Override public Flag nextPlayableFlag() {
            Flag current = getCurrentSwitchFlag();
            boolean singleEpisode = current != null && current.getEpisodes().size() == 1;
            String episode = getCurrentSwitchEpisode();
            for (int i = getFlagPosition() + 1; i < mFlagAdapter.size(); i++) {
                Flag item = (Flag) mFlagAdapter.get(i);
                if (VodSourceMatcher.canResume(item, episode, singleEpisode)) return item;
            }
            return null;
        }

        @Override public Vod nextPlayableSite() {
            Vod item = SearchSorter.pollMatchingSource(() -> mSourceCandidates.next(getCurrentBrokenKey()), getSourceSwitchKeyword());
            syncQuickItems();
            return item;
        }

        @Override public void beginSourceSwitch() { VideoActivity.this.beginSourceSwitch(); }
        @Override public void markCurrentSourceBroken() { VideoActivity.this.markCurrentSourceBroken(); }
        @Override public void startSourceLoading() { VideoActivity.this.startSourceLoading(); }
        @Override public void markSourceSelected() { mSiteSearch.markSelected(); }
    }

    private final class SourceSearchPort implements VodSourceSearch.Port {

        @Override public boolean accepts(Result result) { return mSiteSearch.accepts(result); }
        @Override public String expectedKeyword() { return Objects.toString(mBinding.part.getTag(), "").trim(); }
        @Override public String sourceSwitchKeyword() { return getSourceSwitchKeyword(); }
        @Override public boolean automatic() { return mSiteSearch.isAutomatic(); }
        @Override public String sourceKey(Vod vod) { return getSourceKey(vod); }
        @Override public void mergeCandidates(List<Vod> items, String token) { mergeQuickItems(items, token); }
        @Override public void cancelEmptyFallback() { mUiCallbacks.remove(mR4); }

        @Override public void removeCandidate(Vod item) {
            int index = mQuickAdapter.indexOf(item);
            if (index >= 0) mQuickAdapter.removeItems(index, 1);
            mSourceCandidates.remove(item);
        }

        @Override public void beginSourceSwitch() { VideoActivity.this.beginSourceSwitch(); }
        @Override public void markCurrentSourceBroken() { VideoActivity.this.markCurrentSourceBroken(); }
        @Override public void setManual() { mSiteSearch.setManual(); }
        @Override public void startSourceLoading() { VideoActivity.this.startSourceLoading(); }
        @Override public void openDetail(Vod vod) { mPlaybackNavigation.showDetail(vod, false); }
        @Override public int candidateCount() { return mSourceCandidates.size(); }
        @Override public void selectNextCandidate() { if (!advanceSourceSwitchCandidate()) continueSourceSwitch(); }
        @Override public void cancelSearch() { if (mSiteSearch != null) mSiteSearch.cancel(); }

        @Override public void clearCandidates() {
            resetQuickResults();
        }

        @Override public void setSearchKeyword(String keyword) {
            mBinding.part.setTag(keyword);
            mSourceCandidates.setKeyword(keyword);
            mQuickAdapter.clear();
        }

        @Override public void startSearch(List<Site> sites, String keyword, boolean automatic, Runnable settled) {
            mSiteSearch.start(sites, keyword, automatic, settled);
        }

        @Override public boolean loading() { return isSourceLoading(); }
        @Override public boolean hasPending() { return mSiteSearch.hasPending(); }
        @Override public boolean shouldSelectFirst() { return mSiteSearch.shouldSelectFirst(); }
        @Override public boolean switching() { return isSourceSwitching(); }
        @Override public boolean earlyThresholdReached() { return mSourceCandidates.shouldAutoFlush(); }
        @Override public void flushCandidates() { flushQuickItems(); }
        @Override public void clearSourceSwitch() { VideoActivity.this.clearSourceSwitch(); }
        @Override public void empty(boolean automatic) { if (!automatic) Notify.show(R.string.play_switch_empty); }
    }

    private final class DetailPort implements VodDetailPresenter.Port {
        @Override public String nextRequestToken() { return SearchRequestToken.next("detail"); }
        @Override public String siteKey() { return getKey(); }
        @Override public String videoId() { return getId(); }
        @Override public void setPendingRequest(String token) { setPendingDetailRequest(token); }
        @Override public void cancelPendingRequest() { mPage.cancelRequest(); }
        @Override public void scheduleSourceTimeout() { scheduleSourceSwitchTimeout(); }
        @Override public void requestDetail(String token) { mViewModel.detailContent(getKey(), getId(), token); }
        @Override public boolean accepts(Result result) { return !isFinishing() && !isDestroyed(); }
        @Override public void clearSourceTimeout() { clearSourceSwitchTimeout(); }
        @Override public void prepareFor(Vod vod) { prepareDetail(vod); }
        @Override public void render(Vod vod) { renderDetail(vod); }
        @Override public boolean isSourceSwitching() { return VideoActivity.this.isSourceSwitching(); }
        @Override public void resumeSourceSearch() { VideoActivity.this.resumeSourceSearch(); }
        @Override public void continueSourceSwitch() { VideoActivity.this.continueSourceSwitch(); }
        @Override public boolean shouldFinishWhenEmpty() { return isFromCollect(); }
        @Override public boolean hasFallbackName() { return !getName().isEmpty(); }
        @Override public void finishScreen() { finish(); }
        @Override public void showEmptyState() { showDetailEmpty(); }
        @Override public void showFallbackName() { mBinding.name.setText(getName()); }
        @Override public void scheduleEmptyFallback() { mUiCallbacks.post(mR4, 10000); }
        @Override public void recoverSource() { mPlaybackFlow.sources().recover(); }
        @Override public void notifyMessage(String message) { Notify.show(message); }
    }

    private final class RecoveryPort implements VodRecoveryFlow.Port {
        @Override public boolean mayRecover() { return isCurrentPlayer() && !isBackground(); }
        @Override public int playerType() { return mPlayers.getPlayer(); }
        @Override public void started() { Notify.show(getString(R.string.play_recovery_running)); }
        @Override public void capturePosition() { capturePlaybackPosition(); }
        @Override public void cancelSourceWork() {
            mDetail.invalidate();
            mPlaybackFlow.sources().stop();
            clearSourceSwitch();
        }
        @Override public void cancelled(boolean expired) {
            mPlaybackState.clear();
            stopProgressPolling();
            mClock.setCallback(null);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mPlayers.reset();
            clearDanmakuView();
            showError(getString(expired ? R.string.play_recovery_expired : R.string.play_recovery_cancelled));
        }
        @Override public void retry() { onRefresh(); }
        @Override public void toggleDecode() { onDecode(false); }
        @Override public void reloadExo(ErrorEvent error) { capturePlaybackPosition(); mPlaybackFlow.reload(mPlayers.getRecoveryFormat(error)); }
        @Override public void switchPlayer(ErrorEvent error) { capturePlaybackPosition(); mPlaybackFlow.nextPlayer(); }
        @Override public void stopWithError(ErrorEvent error, boolean advancing) { VideoActivity.this.stopWithError(error); }
        @Override public boolean mayChangeSource() { return Setting.isChange() && getSite().isChangeable(); }
        @Override public boolean usesParse() { return isUseParse(); }

        @Override public boolean selectNextParse() {
            int position = getParsePosition();
            if (position >= mParseAdapter.size() - 1) return false;
            mPlaybackNavigation.switchParse((Parse) mParseAdapter.get(position + 1), true);
            return true;
        }

        @Override public void resetParse() { initParse(); }
        @Override public boolean selectNextFlag() { return mPlaybackNavigation.nextFlag(); }
        @Override public void recoverSource() { mPlaybackFlow.sources().recover(); }
        @Override public boolean hasRecoveryWork() { return mSiteSearch.isActive() || mSourceCandidates.hasItems() || isSourceSwitching(); }
        @Override public boolean isSourceSwitching() { return VideoActivity.this.isSourceSwitching(); }
        @Override public void continueSourceSwitch() { VideoActivity.this.continueSourceSwitch(); }
    }

    private void prepareDetail(Vod item) {
        getIntent().putExtra("key", item.getSiteKey()).putExtra("pic", item.getVodPic())
                .putExtra("id", item.getVodId()).putExtra("name", item.getVodName());
        mBinding.scroll.scrollTo(0, 0);
        clearPartRequest();
        setPartAdapter(Collections.emptyList());
        stopActivePlayback();
        mPage.clearSelection();
        mFlagAdapter.clear();
        setEpisodeAdapter(Collections.emptyList());
        mPlaybackCoordinator.setEpisodes(Collections.emptyList());
        mPlaybackFlow.resetHistoryGate();
    }

    private void showDetailEmpty() {
        clearSourceSwitchTimeout();
        mBinding.progressLayout.showEmpty();
        clearSourceSwitch();
        mPlaybackFlow.sources().stop();
    }

    private void applyDetailHistory() {
        if (mPage.history() == null) return;
        setPlainTextIfChanged(mBinding.control.opening, mPage.history().getOpening() == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mPage.history().getOpening()));
        setPlainTextIfChanged(mBinding.control.ending, mPage.history().getEnding() == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mPage.history().getEnding()));
        mPlayers.setPlayer(getPlayer());
        replacePlayerSession();
        setScale(getScale());
        setPlayerView();
        setDecodeView();
    }

    private void checkDetailFlags(Vod item) {
        boolean empty = mPage.flags().isEmpty();
        setVisibilityIfChanged(mBinding.flag, item.getVodFlags().size() > 1 ? View.VISIBLE : View.GONE);
        if (empty) {
            if (isSourceLoading()) { resumeSourceSearch(); continueSourceSwitch(); }
            else mPlayers.reportError(ErrorEvent.flag());
            return;
        }
        Flag target = findTargetFlag(mPage.flags());
        if (target == null) { resumeSourceSearch(); continueSourceSwitch(); return; }
        resumeSourceSearch();
        mPlaybackNavigation.switchFlag(target, false);
        if (mPage.history() != null && mPage.history().isRevSort()) reverseEpisode(true);
    }

    private void renderDetail(Vod item) {
        mPage.detail(item);
        String token = mDetail.token();
        mBinding.progressLayout.showContent();
        mBinding.video.setTag(item.getVodPic(getPic()));
        mBinding.name.setText(item.getVodName(getName()));
        setText(mBinding.remark, 0, item.getVodRemarks());
        setText(mBinding.year, R.string.detail_year, item.getVodYear());
        setText(mBinding.area, R.string.detail_area, item.getVodArea());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        App.execute(() -> {
            SpannableStringBuilder actor = getSpan(R.string.detail_actor, Util.fromHtml(item.getVodActor()).toString());
            SpannableStringBuilder content = getSpan(R.string.detail_content, Util.fromHtml(item.getVodContent()).toString());
            SpannableStringBuilder director = getSpan(R.string.detail_director, Util.fromHtml(item.getVodDirector()).toString());
            mUiCallbacks.post(() -> {
                if (isFinishing() || isDestroyed() || !TextUtils.equals(token, mDetail.token())) return;
                setText(mBinding.actor, actor); setText(mBinding.content, content); setText(mBinding.director, director);
                mBinding.content.setMaxLines(getMaxLines());
            });
        });
        mFlagAdapter.setItems(mPage.flags(), null);
        if (!isSourceSwitching()) mBinding.video.requestFocus();
        setArtwork(item.getVodPic());
        getPart(item.getVodName());
        if (!isSourceSwitching()) loadRecommendations(item);
        mUiCallbacks.remove(mR4);
        mSelection.selectFlag(0, mFlagAdapter.size());
        mSelection.resetEpisode();
        loadHistory(item, mDetail.token());
    }

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private EpisodePresenter mEpisodePresenter;
    private ArrayObjectAdapter mEpisodeAdapter;
    private ArrayObjectAdapter mArrayAdapter;
    private ArrayObjectAdapter mParseAdapter;
    private ArrayObjectAdapter mQuickAdapter;
    private ArrayObjectAdapter mFlagAdapter;
    private ArrayObjectAdapter mPartAdapter;
    private ArrayObjectAdapter mRecommendAdapter;
    private QualityAdapter mQualityAdapter;
    private DanmakuContext mDanmakuContext;
    private ArrayPresenter mArrayPresenter;
    private FlagPresenter mFlagPresenter;
    private PartPresenter mPartPresenter;
    private CustomKeyDownVod mKeyDown;
    private SiteSearchSession mSiteSearch;
    private SiteViewModel mViewModel;
    private List<Danmaku> mDanmakus;
    private PlaybackController mPlayers;
    private VodPlaybackResolver mVodResolver;
    private String mRenderedEpisode = "";
    private VodPlaybackCoordinator mPlaybackCoordinator;
    private VodPlaybackFlow mPlaybackFlow;
    private VodSessionAdapter mSessionAdapter;
    private VodHistorySession mHistorySession;
    private VodPlaybackLifecycle mPlaybackLifecycle;
    private final CallbackScope mUiCallbacks = new CallbackScope(AppTaskScheduler.get());
    private final PlaybackArtwork mArtwork = new PlaybackArtwork(new PlaybackArtwork.Listener() {
        @Override public void loaded(Drawable drawable) {
            getExo().setDefaultArtwork(drawable);
            getIjk().setDefaultArtwork(drawable);
            showPreview(drawable);
        }
        @Override public void failed(Drawable drawable) {
            getExo().setDefaultArtwork(drawable);
            getIjk().setDefaultArtwork(drawable);
            hidePreview();
        }
    });

    private void createPlaybackFlow() {
        mSessionAdapter = new VodSessionAdapter(mPlayers, mPlayerSession, () -> mPlayers.init(getExo(), getIjk()), () -> {
            setPlayerView();
            setDecodeView();
        });
        mPlaybackFlow = new VodPlaybackFlow(mPlaybackCoordinator, new VodPlayerPort(mPlayers, mSessionAdapter) {
            @Override public void seekPresentation(long position) {
                mPlaybackState.onSeek();
                mPlayers.syncDanmakuPosition(position);
            }
            @Override public boolean reversePlayback() { return mPage.history() != null && mPage.history().isRevPlay(); }
            @Override public void navigated(boolean next, boolean moved) {
                if (!moved) onEpisodeBoundary(next != reversePlayback() ? R.string.error_play_next : R.string.error_play_prev);
            }
            @Override public void saveBeforeStop() { saveHistoryNow(); }
            @Override public void finish() { VideoActivity.this.finish(); }
        }, MainThreadExecutor.get());
        mPlaybackFlow.installRecovery(new RecoveryPort(), com.fongmi.android.tv.utils.AppTaskScheduler.get());
        mPlaybackFlow.installSources(new SourceSearchPort());
        mHistorySession = new VodHistorySession(mPlaybackCoordinator, mPlaybackFlow, mHistoryController, () -> mPage.history());
        mPlaybackLifecycle = new VodPlaybackLifecycle(mPlaybackFlow, new VodPlaybackLifecycle.Port() {
            @Override public void acquireOwnership() { mPlayers.activateAsCurrentPlayer(); }
            @Override public void restoreInitialSelection() { if (mPage.history() != null) playSelected(false); }
            @Override public boolean needsEngine() { return mPlayers.isRelease(); }
            @Override public void rebuildAndAttachEngine() {
                mPlayers.init(getExo(), getIjk());
                replacePlayerSession();
                setPlayerView();
                setDecodeView();
            }
        });
        mPlayers.setInputHandler(mPlaybackFlow);
    }
    private Subscription mPlaybackSubscription;
    private SwitchablePlayerSession mPlayerSession;
    private boolean background;
    private boolean fullscreen;
    private boolean initTrack;
    private boolean mDanmuVisible;
    private boolean useParse;
    private final TvVodSourceCandidates mSourceCandidates = new TvVodSourceCandidates();
    private final Runnable mQuickFlush = this::flushQuickItems;
    private boolean mQuickFlushScheduled;
    private final VodDetailPresenter mDetail = new VodDetailPresenter(new DetailPort());
    private final PlaybackSeekGesture mSeekGesture = new PlaybackSeekGesture();
    private final TvVodPlaybackNavigation mPlaybackNavigation = new TvVodPlaybackNavigation(new NavigationPort());
    private final TvVodPlaybackStateBinder mPlaybackState = new TvVodPlaybackStateBinder(new PlaybackStatePort());
    private int groupSize;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mR5;
    private Runnable mR6;
    private Runnable mR8;
    private final com.fongmi.android.tv.utils.DeferredTask<List<Vod>> mRecommendations =
            new com.fongmi.android.tv.utils.DeferredTask<>(ThreadPools.recommendations(),
                    com.fongmi.android.tv.utils.AppTaskScheduler.get(), error -> ThreadPools.log(error, "Recommendation failed."));
    private volatile Call mPartCall;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private boolean hasKeyEvent;
    private final TvVodSelectionState mSelection = new TvVodSelectionState();
    private final VodHistoryController mHistoryController = new VodHistoryController();
    private final VodPagePresenter mPage = new VodPagePresenter(HistoryRepository.get(), KeepRepository.get(),
            mHistoryController, new TaskScope(ThreadPools.newSingle("video-history"), AppTaskScheduler.get(),
            error -> ThreadPools.log(error, "Page history load failed.")), Constant.TIMEOUT_VOD,
            System::currentTimeMillis, this::onPageHistoryReady);
    private int mEpisodeNumColumns;
    private int mEpisodeColumnWidth;
    private int mArraySize = -1;
    private int mDanmakuRequestId;
    private boolean mArrayRevSort;
    private boolean mDisplayTrafficPolling;
    private boolean mProgressTrafficPolling;
    private float mSpeedBeforeLongPress = Float.NaN;
    private final StringBuilder mEpisodeInput = new StringBuilder();
    private final Runnable mEpisodeInputCommit = this::playNumberedEpisode;

    public static void push(FragmentActivity activity, String text) {
        if (TextUtils.isEmpty(text)) return;
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(activity, Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
            if (allGranted) start(activity, "push_agent", "file://" + path, name, true);
        });
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, true, true, false);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, false, false, true);
    }

    public static void start(Activity activity, String url) {
        start(activity, url, true);
    }

    public static void start(Activity activity, String url, boolean clear) {
        start(activity, "push_agent", url, url, clear);
    }

    public static void start(Activity activity, String id, String name, String pic) {
        start(activity, VodConfig.get().getHome().getKey(), id, name, pic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, boolean clear) {
        start(activity, key, id, name, null, null, clear, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean clear) {
        start(activity, key, id, name, pic, mark, clear, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean clear, boolean cast, boolean collect) {
        Intent intent = new Intent(activity, VideoActivity.class);
        if (clear) intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private int getSiteCid() {
        return mPage.configId(getKey(), getId(), VodConfig.getCid());
    }

    private String getHistoryKey() {
        return History.historyKey(getKey(), getId(), getName(), getSiteCid());
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        return mPage.flag();
    }

    private Episode getEpisode() {
        return (Episode) mEpisodeAdapter.get(getEpisodePosition());
    }

    private int getFlagPosition() { return mPage.flagIndex(); }
    private int getEpisodePosition() { return mPage.episodeIndex(); }

    private int getParsePosition() {
        for (int i = 0; i < mParseAdapter.size(); i++) if (((Parse) mParseAdapter.get(i)).isActivated()) mSelection.selectParse(i, mParseAdapter.size());
        return mSelection.parse(mParseAdapter.size());
    }

    private int getPlayer() {
        return mPage.history() != null && mPage.history().getPlayer() != -1 ? mPage.history().getPlayer() : Setting.getPlayer();
    }

    private int getScale() {
        return mPage.history() != null && mPage.history().getScale() != -1 ? mPage.history().getScale() : Setting.getScale();
    }

    private PlayerView getExo() {
        return mBinding.exo;
    }

    private IjkVideoView getIjk() {
        return mBinding.ijk;
    }

    private Drawable getDefaultArtwork() {
        if (mPlayers.isExo()) return getExo().getDefaultArtwork();
        return getIjk().getDefaultArtwork();
    }

    private BaseGridView getEpisodeView() {
        return Setting.getEpisode() == 0 ? mBinding.episodeHori : mBinding.episodeVert;
    }

    private void setEpisodeSelectedPosition(int position) {
        BaseGridView view = getEpisodeView();
        if (position < 0 || position >= mEpisodeAdapter.size() || view.getSelectedPosition() == position) return;
        if (view.getChildCount() > 0) {
            view.setSelectedPosition(position);
            return;
        }
        view.addOnLayoutCompletedListener(new BaseGridView.OnLayoutCompletedListener() {
            @Override
            public void onLayoutCompleted(@NonNull RecyclerView.State state) {
                view.removeOnLayoutCompletedListener(this);
                view.setSelectedPosition(position);
            }
        });
    }

    private boolean isReplay() {
        return Setting.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        mKeyDown = CustomKeyDownVod.create(this, mBinding.video);
        mFrameParams = mBinding.video.getLayoutParams();
        mClock = Clock.create(mBinding.display.clock);
        mDanmakuContext = DanmakuContext.create();
        mPlayers = PlaybackController.create(this, this::onPlaybackError, this::onPlaybackCommand);
        setVideoView();
        mVodResolver = new VodPlaybackResolver(ThreadPools.newSingle("vod-resolver"), App::post, new SpiderPlaybackGateway(),
                this::prepareResolvedResult, result -> isUseParse());
        mPlayerSession = new SwitchablePlayerSession(mPlayers.session());
        mPlaybackCoordinator = new VodPlaybackCoordinator(mVodResolver, new DefaultPlaybackItemFactory(mPlayers::getSub), mPlayerSession);
        mPlaybackCoordinator.configureTimeout(com.fongmi.android.tv.utils.AppTaskScheduler.get(), Constant.TIMEOUT_PLAY);
        createPlaybackFlow();
        mPlaybackSubscription = mPlaybackFlow.observe(this::renderPlayback);
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showDetailEmpty;
        mR5 = this::setDisplayTraffic;
        mR6 = this::onSourceSwitchTimeout;
        mR8 = this::onFlagSelected;
        setBackground(false);
        setRecyclerView();
        setViewModel();
        // Detail loading only needs the view model and result adapters. Start it
        // before the remaining player and display setup so its I/O overlaps them.
        checkId();
        setEpisodeView();
        setDisplayView();
        setDanmuView();
        checkCast();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.widget.error.setOnClickListener(view -> { takePlaybackControl(); onRefresh(); });
        mBinding.control.seek.setListener(mPlayers);
        mBinding.desc.setOnClickListener(view -> onDesc());
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
        mBinding.search1.setOnClickListener(view -> onSearchPage());
        mBinding.control.text.setOnClickListener(this::onTrack);
        mBinding.control.volume.setOnClickListener(view -> VolumeDialog.create(this).show());
        mBinding.control.loudness.setOnClickListener(view -> LoudnessDialog.create(this).show());
        mBinding.control.audio.setOnClickListener(this::onTrack);
        mBinding.control.video.setOnClickListener(this::onTrack);
        mBinding.control.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.speed.setDownListener(this::onSpeedSub);
        mBinding.control.ending.setUpListener(this::onEndingAdd);
        mBinding.control.ending.setDownListener(this::onEndingSub);
        mBinding.control.opening.setUpListener(this::onOpeningAdd);
        mBinding.control.opening.setDownListener(this::onOpeningSub);
        mBinding.control.text.setUpListener(this::onSubtitleUpDown);
        mBinding.control.text.setDownListener(this::onSubtitleUpDown);
        mBinding.control.loop.setOnClickListener(view -> onLoop());
        mBinding.control.danmu.setOnClickListener(view -> onDanmu());
        mBinding.control.danmu.setOnLongClickListener(view -> onDanmakuSource());
        mBinding.control.danmu.setUpListener(this::onDanmuAdd);
        mBinding.control.danmu.setDownListener(this::onDanmuSub);
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.scale.setOnClickListener(view -> onScale());
        mBinding.control.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.reset.setOnClickListener(view -> onReset());
        mBinding.control.player.setOnClickListener(view -> onPlayer());
        mBinding.control.decode.setOnClickListener(view -> onDecode());
        mBinding.control.ending.setOnClickListener(view -> onEnding());
        mBinding.control.opening.setOnClickListener(view -> onOpening());
        mBinding.control.player.setOnLongClickListener(view -> onChoose());
        mBinding.control.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                mUiCallbacks.remove(mR8);
                mUiCallbacks.post(mR8, 200);
            }
        });
        getEpisodeView().addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null) mFocus1 = child.itemView;
                setEpisodeChildKeyListener(child, position);
            }
        });
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mEpisodeAdapter.size() > getGroupSize() && position >= 0 && hasKeyEvent) setEpisodeSelectedPosition(position * getGroupSize());
            }
        });
    }

    private void setEpisodeChildKeyListener(RecyclerView.ViewHolder child, int position) {
        if (getEpisodeView() != mBinding.episodeVert) return;
        if (child == null) return;
        child.itemView.setOnKeyListener(null);
        int itemCount = getEpisodeView().getAdapter().getItemCount();
        if (itemCount <= 0) return;
        int columns = mEpisodePresenter.getNumColumns();
        if ((position + columns >= itemCount) && ((position % columns) + 1 > (itemCount % columns))) {
            child.itemView.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && KeyUtil.isDownKey(event)) {
                        View lastItem =  getEpisodeView().getLayoutManager().findViewByPosition(itemCount - 1);
                        if (lastItem != null) lastItem.requestFocus();
                    }
                    return false;
                }
            });
        }
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.setItemViewCacheSize(10);
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(new ItemBridgeAdapter(mFlagAdapter = new ArrayObjectAdapter(mFlagPresenter = new FlagPresenter(this::onFlagClick))));
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.setItemViewCacheSize(10);
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this::onQualityClick));
        mBinding.array.setHasFixedSize(true);
        mBinding.array.setItemAnimator(null);
        mBinding.array.setItemViewCacheSize(10);
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(new ItemBridgeAdapter(mArrayAdapter = new ArrayObjectAdapter(mArrayPresenter = new ArrayPresenter(this))));
        mBinding.part.setHasFixedSize(true);
        mBinding.part.setItemAnimator(null);
        mBinding.part.setItemViewCacheSize(10);
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(new ItemBridgeAdapter(mPartAdapter = new ArrayObjectAdapter(mPartPresenter = new PartPresenter(item -> SearchActivity.start(this, item, true)))));
        mBinding.quick.setHasFixedSize(true);
        mBinding.quick.setItemAnimator(null);
        mBinding.quick.setItemViewCacheSize(10);
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(new ItemBridgeAdapter(mQuickAdapter = new ArrayObjectAdapter(new QuickPresenter(item -> { capturePlaybackPosition(); takePlaybackControl(); mPlaybackFlow.sources().select(item); }))));
        mBinding.recommend.setHasFixedSize(true);
        mBinding.recommend.setItemAnimator(null);
        mBinding.recommend.setItemViewCacheSize(10);
        mBinding.recommend.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.recommend.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        VodPresenter.OnClickListener recommendListener = new VodPresenter.OnClickListener() {
            @Override
            public void onItemClick(Vod item) {
                CollectActivity.start(VideoActivity.this, item.getVodName());
            }

            @Override
            public boolean onLongClick(Vod item) {
                return false;
            }
        };
        mBinding.recommend.setAdapter(new ItemBridgeAdapter(mRecommendAdapter = new ArrayObjectAdapter(new VodPresenter(recommendListener))));
        mBinding.control.parse.setHasFixedSize(true);
        mBinding.control.parse.setItemAnimator(null);
        mBinding.control.parse.setItemViewCacheSize(10);
        mBinding.control.parse.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.control.parse.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.control.parse.setAdapter(new ItemBridgeAdapter(mParseAdapter = new ArrayObjectAdapter(new ParsePresenter(this::onParseClick))));
        mParseAdapter.setItems(VodConfig.get().getParses(), null);
        mSelection.selectParse(getParsePosition(), mParseAdapter.size());
    }

    private void setEpisodeView() {
        mBinding.episodeVert.setHasFixedSize(true);
        mBinding.episodeHori.setHasFixedSize(true);
        mBinding.episodeVert.setItemAnimator(null);
        mBinding.episodeHori.setItemAnimator(null);
        mBinding.episodeVert.setItemViewCacheSize(20);
        mBinding.episodeHori.setItemViewCacheSize(20);
        mBinding.episodeVert.setVerticalSpacing(ResUtil.dp2px(8));
        mBinding.episodeHori.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episodeVert.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episodeHori.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        getEpisodeView().setAdapter(new ItemBridgeAdapter(mEpisodeAdapter = new ArrayObjectAdapter(mEpisodePresenter = new EpisodePresenter(this::setEpisodeActivated))));
    }

    private void setVideoView() {
        mPlayers.init(getExo(), getIjk());
        ExoUtil.setSubtitleView(mBinding.exo);
        IjkUtil.setSubtitleView(mBinding.ijk);
        mBinding.control.reset.setText(getResetText());
        setPlainTextIfChanged(mBinding.control.decode, mPlayers.getDecodeText());
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.getSpeedText());
        String[] scale = ResUtil.getStringArray(R.array.select_scale);
        setPlainTextIfChanged(mBinding.control.scale, scale[safeIndex(Setting.getScale(), scale.length)]);
        setPlainTextIfChanged(mBinding.control.opening, getString(R.string.play_op));
        setPlainTextIfChanged(mBinding.control.ending, getString(R.string.play_ed));
    }

    private void setDanmuViewSettings() {
        float[] range = {2.4f, 1.8f, 1.2f, 0.8f};
        float speed = range[safeIndex(Setting.getDanmuSpeed(), range.length)];
        float alpha = Setting.getDanmuAlpha() / 100.0f;
        float sizeScale = isFullscreen() ? 1.2f * Setting.getDanmuSize() : 0.8f * Setting.getDanmuSize();
        int maxLine = Setting.getDanmuLine(3);
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        HashMap<Integer, Boolean> overlapping = new HashMap<>();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLine);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLine);
        overlapping.put(BaseDanmaku.TYPE_FIX_TOP, true);
        overlapping.put(BaseDanmaku.TYPE_SCROLL_RL, true);
        overlapping.put(BaseDanmaku.TYPE_SCROLL_LR, true);
        overlapping.put(BaseDanmaku.TYPE_FIX_BOTTOM, true);
        mDanmakuContext.setMaximumLines(maxLines).setScrollSpeedFactor(speed).setDanmakuTransparency(alpha).setScaleTextSize(sizeScale).setDuplicateMergingEnabled(true).preventOverlapping(overlapping);
    }

    private String getResetText() {
        String[] reset = ResUtil.getStringArray(R.array.select_reset);
        return reset[safeIndex(Setting.getReset(), reset.length)];
    }

    private void setDanmuView() {
        mPlayers.setDanmuView(mBinding.danmaku);
        setDanmuViewSettings();
        mDanmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3).setDanmakuMargin(8);
        syncDanmuVisible();
    }

    private void setDisplayView() {
        mBinding.display.getRoot().setVisibility(View.VISIBLE);
        showDisplayInfo();
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mSiteSearch = new SiteSearchSession(mViewModel, "video-search", SOURCE_SEARCH_CONCURRENCY);
        mViewModel.result().observe(this, mDetail::accept);
        mViewModel.search().observe(this, mPlaybackFlow::acceptSourceResult);
    }

    private void checkCast() {
        if (isCast()) onVideo();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", "push_agent").putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) mDetail.missing();
        else mDetail.request();
    }

    private void setPlayerView() {
        PlaybackVideoViews.render(mPlayers, getExo(), getIjk(), mBinding.control.player, mBinding.control.speed);
        setPlainTextIfChanged(mBinding.control.player, mPlayers.getPlayerText());
        float speed = mPage.history() == null ? Setting.getPlaySpeed() : mPage.history().getSpeed();
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.setSpeed(speed));
        setDanmuViewSettings();


    }

    private void loadHistory(Vod item, String detailToken) { mPage.loadHistory(item, detailToken); }

    private void onPageHistoryReady(History history, Vod item) {
        if (isFinishing() || isDestroyed()) return;
        applyDetailHistory(); checkDetailFlags(item); checkKeep();
    }

    private void setDecodeView() {
        setPlainTextIfChanged(mBinding.control.decode, mPlayers.getDecodeText());
    }

    private void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        setPlainTextIfChanged(mBinding.control.scale, ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void stopActivePlayback() {
        clearPlaybackTimeout();
        mPlaybackState.clear();
        stopProgressPolling();
        mClock.setCallback(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPlayers.reset();
        if (mPlaybackFlow != null) mPlaybackFlow.stop();
        else mPlayers.stop();
        clearDanmakuView();
    }

    private void requestPlayback(Flag flag, Episode episode, boolean replay) {
        if (flag == null || episode == null) return;
        int index = flag.getEpisodes().indexOf(episode);
        if (index < 0) {
            showError(getString(R.string.error_play_timeout));
            return;
        }
        clearPlaybackTimeout();
        mPlaybackState.clear();
        CharSequence title = getString(R.string.detail_title, mBinding.name.getText(), episode.getName());
        if (!TextUtils.equals(mBinding.widget.title.getText(), title)) mBinding.widget.title.setText(title);
        if (!TextUtils.equals(mBinding.display.title.getText(), title)) mBinding.display.title.setText(title);
        mKeyDown.release();
        mSeekGesture.reset();
        StartPolicy policy = mPlaybackFlow.intent().consumeStartPolicy(replay, isSourceSwitching());
        Source.get().cancelPreloads();
        mPlaybackFlow.playEpisode(index, policy);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clearDanmakuView();
        showProgress();
        setMetadata();
        hidePreview();
        hideCenter();
    }

    private void playSelected(boolean replay) {
        if (mFlagAdapter.size() == 0 || mEpisodeAdapter.size() == 0) return;
        requestPlayback(getFlag(), getEpisode(), replay);
    }

    private void prepareResolvedResult(Result result) {
        ensureCompatiblePlayer(result);
        result.getUrl().set(mQualityAdapter.getPosition());
        setUseParse(VodConfig.hasParse() && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(result.getFlag())) || result.getJx() == 1));
        setVisibilityIfChanged(mBinding.control.parse, isUseParse() ? View.VISIBLE : View.GONE);
        setQualityVisible(result.getUrl().isMulti());
        setDanmakus(result.getDanmakus());
        mQualityAdapter.addAll(result);
    }

    private void ensureCompatiblePlayer(Result result) {
        mSessionAdapter.ensureCompatible(result.getDrm());
    }

    private void setDanmakus(List<Danmaku> items) {
        mPlayers.setDanmakus(items);
        mDanmakus = mPlayers.getDanmakus();
        prepareDanmaku(mPlayers.getDanmaku());
    }

    private void setDanmaku(Danmaku item) {
        if (item == null || item.isEmpty()) {
            mPlayers.setDanmakus(null);
            clearDanmakuView();
            return;
        }
        mPlayers.setDanmaku(item);
        mDanmakus = mPlayers.getDanmakus();
        prepareDanmaku(mPlayers.getDanmaku());
    }

    private void clearDanmakuView() {
        mDanmakus = mPlayers.getDanmakus();
        mPlayers.resetDanmu();
        mBinding.danmaku.setVisibility(View.GONE);
        ++mDanmakuRequestId;
    }

    private void prepareDanmaku(Danmaku item) {
        final int requestId = ++mDanmakuRequestId;
        if (!Setting.isDanmuLoad() || !mDanmuVisible) {
            mPlayers.resetDanmu();
            showDanmu();
            mBinding.danmaku.setVisibility(View.GONE);
            mPlayers.prepared();
            return;
        }
        boolean hasSource = item != null && !item.isEmpty();
        setVisibilityIfChanged(mBinding.control.danmu, View.VISIBLE);
        mBinding.danmaku.setVisibility(hasSource ? View.VISIBLE : View.GONE);
        if (!hasSource) {
            mPlayers.resetDanmu();
            return;
        }
        mPlayers.resetDanmu();
        showDanmu();
        App.execute(() -> {
            try {
                Parser parser = new Parser(item.getUrl());
                mUiCallbacks.post(() -> {
                    if (isFinishing() || isDestroyed() || requestId != mDanmakuRequestId) return;
                    mPlayers.prepareDanmu(parser, mDanmakuContext);
                    showDanmu();
                    mPlayers.prepared();
                });
            } catch (Throwable e) {
                ThreadPools.log(e, "Danmaku prepare failed.");
                mUiCallbacks.post(() -> {
                    if (isFinishing() || isDestroyed() || requestId != mDanmakuRequestId) return;
                    mPlayers.resetDanmu();
                    mBinding.danmaku.setVisibility(View.GONE);
                });
            }
        });
    }

    private void refreshDanmaku() {
        mDanmuVisible = Setting.isDanmu();
        setDanmuText();
        setDanmuViewSettings();
        prepareDanmaku(mPlayers.getDanmaku());
    }

    private int getMaxLines() {
        int lines = 1;
        if (isGone(mBinding.actor)) ++lines;
        if (isGone(mBinding.remark)) ++lines;
        if (isGone(mBinding.director)) ++lines;
        return lines;
    }

    private void setText(TextView view, SpannableStringBuilder span) {
        view.setText(span, TextView.BufferType.SPANNABLE);
        view.setVisibility(span.length() > 0 ? View.VISIBLE : View.GONE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
        view.setTag(span.toString());
    }

    private void setText(TextView view, int resId, String text) {
        view.setText(getSpan(resId, text), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
        view.setTag(text);
    }

    private void setPlainTextIfChanged(TextView view, CharSequence text) {
        if (TextUtils.equals(view.getText(), text)) return;
        view.setText(text);
    }

    private void setVisibilityIfChanged(View view, int visibility) {
        if (view.getVisibility() == visibility) return;
        view.setVisibility(visibility);
    }

    private SpannableStringBuilder getSpan(int resId, String text) {
        if (resId > 0) text = getString(resId, text);
        Map<String, String> map = new HashMap<>();
        Matcher m = Sniffer.CLICKER.matcher(text);
        while (m.find()) {
            String key = Trans.s2t(m.group(2)).trim();
            text = text.replace(m.group(), key);
            map.put(key, m.group(1));
        }
        SpannableStringBuilder span = SpannableStringBuilder.valueOf(text);
        for (String s : map.keySet()) {
            int index = text.indexOf(s);
            span.setSpan(getClickSpan(s,map.get(s)), index, index + s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private ClickableSpan getClickSpan(String key,String value) {
        String scheme=Json.safeString(Json.safeObject(Json.parse(value)),"scheme");
        if(Objects.equals("search",scheme)){
            return new ClickableSpan() {
                @Override
                public void onClick(@NonNull View view) {
                    CollectActivity.start(getActivity(), key);
                }
            };
        }
        Result result = Result.type(value);
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                VodActivity.start(getActivity(), getKey(), result);
            }
        };
    }

    private void setEpisodeAdapter(List<Episode> items) {
        int visibility = items.isEmpty() ? View.GONE : View.VISIBLE;
        if (getEpisodeView().getVisibility() != visibility) getEpisodeView().setVisibility(visibility);
        if (isVisible(mBinding.episodeVert)) setEpisodeView(items);
        mEpisodeAdapter.setItems(items, null);
        if (mPlaybackCoordinator != null && mFlagAdapter != null && mFlagAdapter.size() > 0) {
            VodEpisodeCatalog.bind(mPlaybackCoordinator, getKey(), getFlag().getFlag(), items, mPage.history());
        }
        setEpisodeControlVisible(items.size() > 1);
        mSelection.selectEpisode(findActivatedEpisodePosition(items), items.size());
        setArrayAdapter(items.size());
        setR2Callback(50);
    }

    private void setEpisodeControlVisible(boolean multiple) {
        setVisibilityIfChanged(mBinding.control.next, multiple ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.prev, multiple ? View.VISIBLE : View.GONE);
        boolean showEpisodes = multiple && Setting.getFullscreenMenuKey() == 0;
        setVisibilityIfChanged(mBinding.control.episodes, showEpisodes ? View.VISIBLE : View.GONE);
    }

    private int findActivatedEpisodePosition(List<Episode> items) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).isActivated()) return i;
        return 0;
    }

    private void setEpisodeView(List<Episode> items) {
        int size = items.size();
        int episodeNameLength = items.isEmpty() ? 0 : items.get(0).getName().length();
        for (int i = 0; i < size; i++) {
            items.get(i).setIndex(i);
            int length = items.get(i).getName() == null ? 0 : items.get(i).getName().length();
            if (length > episodeNameLength) episodeNameLength = length;
        }
        int numColumns = 10;
        if (episodeNameLength > 40) numColumns = 1;
        else if (episodeNameLength > 30) numColumns = 2;
        else if (episodeNameLength > 15) numColumns = 3;
        else if (episodeNameLength > 10) numColumns = 4;
        else if (episodeNameLength > 6) numColumns = 6;
        else if (episodeNameLength > 4) numColumns = 8;
        int rowNum = (int) Math.ceil((double) size / (double) numColumns);
        int width = ResUtil.getScreenWidth() - ResUtil.dp2px(48);
        int columnWidth = (width - ((numColumns - 1) * ResUtil.dp2px(8))) / numColumns;
        int visibleRows = Math.min(rowNum, 6);
        int height = ResUtil.dp2px(visibleRows * 48 + Math.max(visibleRows - 1, 0) * 8);
        ViewGroup.LayoutParams params = mBinding.episodeVert.getLayoutParams();
        int screenWidth = ResUtil.getScreenWidth();
        boolean changed = false;
        if (params.width != screenWidth) {
            params.width = screenWidth;
            changed = true;
        }
        if (params.height != height) {
            params.height = height;
            changed = true;
        }
        if (mEpisodeNumColumns != numColumns) {
            mBinding.episodeVert.setNumColumns(numColumns);
            mEpisodeNumColumns = numColumns;
        }
        if (mEpisodeColumnWidth != columnWidth) {
            mBinding.episodeVert.setColumnWidth(columnWidth);
            mEpisodeColumnWidth = columnWidth;
        }
        if (changed) mBinding.episodeVert.setLayoutParams(params);
        mBinding.episodeVert.setWindowAlignmentOffsetPercent(10f);
        if (mEpisodePresenter.getNumColumns() != numColumns) mEpisodePresenter.setNumColumns(numColumns);
        if (mEpisodePresenter.getNumRows() != rowNum) mEpisodePresenter.setNumRows(rowNum);
    }

    private void playFlag(Flag flag, int episodePosition) {
        String name = isSourceSwitching() ? mPlaybackFlow.sourceSwitch().getEpisode() : (mPage.history() == null ? "" : mPage.history().getVodRemarks());
        Episode episode = mPage.chooseEpisode(flag, name, episodePosition);
        if (episode == null) return;
        mPlaybackNavigation.switchEpisode(episode);
        hidePreview();
    }

    public void setEpisodeActivated(Episode item) {
        if (item.isActivated()) {
            if (!isFullscreen()) enterFullscreen();
            return;
        }
        takePlaybackControl();
        mPlaybackNavigation.switchEpisode(item);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        setR2Callback(100);
    }

    private void setQualityActivated(Result result) {
        takePlaybackControl();
        try {
            mPlaybackFlow.reload();
            mBinding.danmaku.hide();
        } catch (Exception e) {
            ThreadPools.log(e, "Quality switch failed.");
            showError(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.error_play_timeout) : e.getMessage());
            mPlayers.reportError(ErrorEvent.extract(e.getMessage()));
        }
    }

    private void onQualityClick(Result result, boolean activated) {
        if (activated) {
            if (!isFullscreen()) enterFullscreen();
            return;
        }
        setQualityActivated(result);
    }

    private void reverseEpisode(boolean scroll) {
        mPage.reverseEpisodes();
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) setEpisodeSelectedPosition(getEpisodePosition());
    }

    private void setArrayAdapter(int size) {
        if (size > 200) setGroupSize(100);
        else if (size > 100) setGroupSize(40);
        else setGroupSize(20);
        boolean revSort = mPage.history() != null && mPage.history().isRevSort();
        if (mArraySize == size && mArrayRevSort == revSort) return;
        List<String> items = new ArrayList<>();
        if (revSort) for (int i = size; i > 0; i -= getGroupSize()) items.add(i + "-" + Math.max(i - (getGroupSize() - 1), 1));
        else for (int i = 0; i < size; i += getGroupSize()) items.add((i + 1) + "-" + Math.min(i + getGroupSize(), size));
        setVisibilityIfChanged(mBinding.array, items.size() > 1 ? View.VISIBLE : View.GONE);
        mArrayAdapter.setItems(items, null);
        mArraySize = size;
        mArrayRevSort = revSort;
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episodeHori, R.id.array, R.id.episodeVert, R.id.part, R.id.quick, R.id.recommend);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episodeHori, R.id.array, R.id.episodeVert, R.id.part, R.id.quick, R.id.recommend);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        hasKeyEvent = false;
        boolean episodeChanged = mEpisodePresenter.setNextFocusDown(findFocusDown(Setting.getEpisode() == 0 ? 2 : 4));
        episodeChanged |= mEpisodePresenter.setNextFocusUp(findFocusUp(Setting.getEpisode() == 0 ? 2 : 4));
        boolean qualityChanged = mQualityAdapter.setNextFocusDown(findFocusDown(1));
        boolean arrayChanged = mArrayPresenter.setNextFocusDown(findFocusDown(3));
        arrayChanged |= mArrayPresenter.setNextFocusUp(findFocusUp(3));
        boolean flagChanged = mFlagPresenter.setNextFocusDown(findFocusDown(0));
        boolean partChanged = mPartPresenter.setNextFocusUp(findFocusUp(5));
        if (flagChanged) notifyItemChanged(mBinding.flag, mFlagAdapter);
        if (qualityChanged) notifyItemChanged(mBinding.quality, mQualityAdapter);
        if (arrayChanged) notifyItemChanged(mBinding.array, mArrayAdapter);
        if (episodeChanged) notifyItemChanged(getEpisodeView(), mEpisodeAdapter);
        if (partChanged) notifyItemChanged(mBinding.part, mPartAdapter);
    }

    private void showDisplayInfo() {
        boolean hasDialog = hasBottomSheetDialog();
        boolean playing = mPlayers.isPlaying();
        setVisibilityIfChanged(mBinding.display.clock, !playing && (Setting.isDisplayTime() || isVisible(mBinding.widget.info)) ? View.VISIBLE : View.GONE);
        boolean paused = mPlaybackCoordinator != null && mPlaybackFlow.state().getPhase() == VodPlaybackPhase.PAUSED;
        setVisibilityIfChanged(mBinding.display.titleLayout, paused && !playing && Setting.isDisplayVideoTitle() && !isVisible(mBinding.control.getRoot()) ? View.VISIBLE : View.GONE);
        boolean showNetSpeed = shouldShowDisplaySpeed(hasDialog);
        setVisibilityIfChanged(mBinding.display.netspeed, showNetSpeed ? View.VISIBLE : View.GONE);
        if (showNetSpeed) startDisplayTrafficPolling();
        else stopDisplayTrafficPolling();
        setVisibilityIfChanged(mBinding.display.duration, Setting.isDisplayDuration() && !isVisible(mBinding.control.getRoot()) && (mPlayers.isVod()) && !hasDialog ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.display.progress, Setting.isDisplayMiniProgress() && !isVisible(mBinding.control.getRoot()) && (mPlayers.isVod()) && !hasDialog ? View.VISIBLE : View.GONE);
    }

    private void onTimeChangeDisplaySpeed() {
        long position = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (isVisible(mBinding.display.duration) && position > 0) setPlainTextIfChanged(mBinding.display.duration, mPlayers.getPositionTime(0) + "/" + mPlayers.getDurationTime());
        if (isVisible(mBinding.display.progress) && position > 0 && duration > 0) mBinding.display.progress.setProgress((int)(position * 100 / duration));
        showDisplayInfo();
    }

    private boolean hasBottomSheetDialog() {
        for (Fragment f : getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return true;
        return false;
    }

    private boolean shouldShowDisplaySpeed(boolean hasDialog) {
        return Setting.isDisplaySpeed() && !isVisible(mBinding.control.getRoot()) && !hasDialog && !isVisible(mBinding.widget.progress);
    }

    private void startDisplayTrafficPolling() {
        if (mDisplayTrafficPolling) return;
        mDisplayTrafficPolling = true;
        mUiCallbacks.remove(mR5);
        mUiCallbacks.post(mR5, 0);
    }

    private void stopDisplayTrafficPolling() {
        mDisplayTrafficPolling = false;
        mUiCallbacks.remove(mR5);
    }

    private void setDisplayTraffic() {
        if (!shouldShowDisplaySpeed(hasBottomSheetDialog())) {
            stopDisplayTrafficPolling();
            return;
        }
        Traffic.setSpeed(mBinding.display.netspeed);
        mUiCallbacks.post(mR5, Constant.INTERVAL_TRAFFIC);
    }

    @Override
    public boolean onArrayItemTouch() {
        hasKeyEvent = true;
        return false;
    }

    @Override
    public void onRevSort() {
        if (mPage.history() == null) return;
        mPage.history().setRevSort(!mPage.history().isRevSort());
        reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        if (mPage.history() == null) return;
        mPage.history().setRevPlay(!mPage.history().isRevPlay());
        view.setText(mPage.history().getRevPlayText());
        Notify.show(mPage.history().getRevPlayHint());
    }

    void enterFullscreen() {
        mFocus1 = getCurrentFocus();
        mBinding.video.requestFocus();
        mBinding.video.setForeground(null);
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.flag.setSelectedPosition(getFlagPosition());
        mDanmakuContext.setScaleTextSize(1.2f * Setting.getDanmuSize());
        mKeyDown.setFull(true);
        setFullscreen(true);
        mFocus2 = null;
        onPlay();
    }

    private void exitFullscreen() {
        mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
        mBinding.video.setLayoutParams(mFrameParams);
        mDanmakuContext.setScaleTextSize(0.8f * Setting.getDanmuSize());
        getFocus1().requestFocus();
        mKeyDown.setFull(false);
        setFullscreen(false);
        mFocus2 = null;
        hideInfo();
    }

    private void onDesc() {
        CharSequence desc = mBinding.content.getText();
        if (desc.length() > 3) DescDialog.show(this, desc.subSequence(3, desc.length()));
    }

    private void onKeep() {
        boolean added = mPage.toggleFavorite(getHistoryKey(), getSiteCid(), getSite().getName(),
                mBinding.name.getText().toString(), Objects.toString(mBinding.video.getTag(), ""));
        Notify.show(added ? R.string.keep_add : R.string.keep_del);
        RefreshEvent.keep();
        checkKeep();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        mPlaybackFlow.sources().recover();
    }

    private void onLoop() {
        mPlaybackFlow.setLoop(!mBinding.control.loop.isActivated());
        mBinding.control.loop.setActivated(!mBinding.control.loop.isActivated());
    }

    private void onDanmu() {
        Setting.putDanmu(!Setting.isDanmu());
        refreshDanmaku();
    }

    private void syncDanmuVisible() {
        mDanmuVisible = Setting.isDanmu();
        setDanmuText();
        showDanmu();
    }

    private void setDanmuText() {
        mBinding.control.danmu.setActivated(mDanmuVisible);
        setPlainTextIfChanged(mBinding.control.danmu, ResUtil.getString(R.string.play_danmu));
    }

    private void showDanmu() {
        mPlayers.setDanmuVisible(mDanmuVisible);
    }

    private boolean onDanmuAdd() {
        if (mDanmuVisible) return false;
        onDanmu();
        return true;
    }

    private boolean onDanmuSub() {
        if (!mDanmuVisible) return false;
        onDanmu();
        return true;
    }

    private boolean onDanmakuSource() {
        if (mDanmakus == null || mDanmakus.isEmpty()) {
            FileChooserDialog.create().player(mPlayers).mode(FileChooserDialog.MODE_DANMAKU).show(this);
            return true;
        }
        int current = -1;
        for (int i = 0; i < mDanmakus.size(); i++) if (mDanmakus.get(i).isSelected()) current = i;
        if (current == mDanmakus.size() - 1) {
            FileChooserDialog.create().player(mPlayers).mode(FileChooserDialog.MODE_DANMAKU).show(this);
        } else {
            int next = (current + 1) % mDanmakus.size();
            setDanmaku(mDanmakus.get(next));
            Notify.show(mDanmakus.get(next).getName());
        }
        return true;
    }

    public List<Episode> playbackEpisodes() {
        if (mFlagAdapter == null || mFlagAdapter.size() == 0) return new ArrayList<>();
        Flag flag = getFlag();
        return flag == null ? new ArrayList<>() : new ArrayList<>(flag.getEpisodes());
    }

    public void restoreEpisodePanelFocus(View previous) {
        if (isFinishing() || isDestroyed()) return;
        if (previous != null && previous.isAttachedToWindow() && previous.isShown()) previous.requestFocus();
        else mBinding.video.requestFocus();
    }

    private void onEpisodes() {
        if (mEpisodeAdapter.size() <= 1) return;
        EpisodeDialog.create().episodes(getFlag().getEpisodes()).show(this);
        hideControl();
    }

    private void checkNext() {
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.NEXT));
    }

    private void checkPrev() {
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.PREVIOUS));
    }

    private void onEpisodeBoundary(int message) {
        if (isFullscreen()) exitFullscreen();
        Notify.show(message);
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        index = index == array.length - 1 ? 0 : ++index;
        if (mPage.history() != null) mPage.history().setScale(index);
        setScale(index);
    }

    private void onSpeed() {
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.addSpeed());
        if (mPage.history() != null) mPage.history().setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();


    }

    private boolean onSpeedAdd() {
        if (mPlayers.getSpeed() >= 5.0f) return false;
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.addSpeed(0.25f));
        if (mPage.history() != null) mPage.history().setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        return true;
    }

    private boolean onSpeedSub() {
        if (mPlayers.getSpeed() <= 0.2f) return false;
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.subSpeed(0.25f));
        if (mPage.history() != null) mPage.history().setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        return true;
    }

    private boolean onSpeedLong() {
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.toggleSpeed());
        if (mPage.history() != null) mPage.history().setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();


        return true;
    }
    private void onRefresh() {
        onReset(false);
    }

    private void capturePlaybackPosition() {
        long position = getCurrentSwitchPosition();
        mPlaybackFlow.preservePosition(position);
        mPlayers.setPosition(position);
        mHistorySession.save();
    }

    private void saveHistoryNow() {
        if (mHistorySession != null) mHistorySession.save();
    }


    private boolean onSubtitleUpDown() {
        onSubtitleClick();
        return true;
    }

    private void onReset() {
        takePlaybackControl();
        onReset(isReplay());
    }

    private void onReset(boolean replay) {
        mClock.setCallback(null);
        playSelected(replay);
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        setPlainTextIfChanged(mBinding.control.reset, getResetText());
        return true;
    }

    private void onOpening() {
        long opening = PlaybackMarkers.openingAt(mPlayers.getPosition(), mPlayers.getDuration());
        if (opening != PlaybackMarkers.INVALID) setOpening(opening);
    }

    private boolean onOpeningAdd() {
        if (mPage.history() == null) return false;
        if (mPage.history().getOpening() >= mPlayers.getDuration() / 2) return false;
        setOpening(Math.min(mPage.history().getOpening() + 1000, mPlayers.getDuration() / 2));
        return true;
    }

    private boolean onOpeningSub() {
        if (mPage.history() == null) return false;
        if (mPage.history().getOpening() <= 0) return false;
        setOpening(Math.max(0, mPage.history().getOpening() - 1000));
        return true;
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        if (mPage.history() == null) return;
        mPage.history().setOpening(opening);
        mPlaybackCoordinator.setOpening(opening);
        setPlainTextIfChanged(mBinding.control.opening, opening == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mPage.history().getOpening()));
    }

    private void onEnding() {
        long ending = PlaybackMarkers.endingAt(mPlayers.getPosition(), mPlayers.getDuration());
        if (ending != PlaybackMarkers.INVALID) setEnding(ending);
    }

    private boolean onEndingAdd() {
        if (mPage.history() == null) return false;
        if (mPage.history().getEnding() >= mPlayers.getDuration() / 2) return false;
        setEnding(Math.min(mPlayers.getDuration() / 2, mPage.history().getEnding() + 1000));
        return true;
    }

    private boolean onEndingSub() {
        if (mPage.history() == null) return false;
        if (mPage.history().getEnding() <= 0) return false;
        setEnding(Math.max(0, mPage.history().getEnding() - 1000));
        return true;
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        if (mPage.history() == null) return;
        mPage.history().setEnding(ending);
        setPlainTextIfChanged(mBinding.control.ending, ending == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mPage.history().getEnding()));
    }

    private boolean onChoose() {
        if (mPlayers.isEmpty()) return false;
        mPlayers.choose(this, mBinding.widget.title.getText());
        return true;
    }

    private void onPlayer() {
        PlayerDialog.create().select(mPlayers.getPlayer()).title(textOf(mBinding.widget.title)).show(this);
        hideControl();
    }

    private void onDecode() {
        onDecode(true);
    }

    private void onDecode(boolean save) {
        mPlaybackFlow.dispatch(PlaybackInput.decode(save));
    }

    private void replacePlayerSession() {
        if (mSessionAdapter != null) mSessionAdapter.attach();
    }

    private void onTrack(View view) {
        TrackDialog.create().player(mPlayers).chooser(this).vod(true).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl(getFocus2());
    }

    private void setProgressVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        boolean changed = mBinding.widget.progress.getVisibility() != visibility;
        setVisibilityIfChanged(mBinding.widget.progress, visibility);
        if (visible) {
            hideError();
        } else {
            stopProgressPolling();
        }
        if (changed) showDisplayInfo();
    }

    private void startProgressPolling() {
        if (mProgressTrafficPolling) return;
        mProgressTrafficPolling = true;
        mUiCallbacks.post(mR3, 0);
    }

    private void stopProgressPolling() {
        mProgressTrafficPolling = false;
        mUiCallbacks.remove(mR3);
    }

    private void showProgress() {
        setProgressVisible(true);
        startProgressPolling();
    }

    private void hideProgress() {
        setProgressVisible(false);
    }

    private void showError(String text) {
        clearPlaybackTimeout();
        setVisibilityIfChanged(mBinding.widget.error, View.VISIBLE);
        setPlainTextIfChanged(mBinding.widget.text, text);
        hideProgress();
        mUiCallbacks.post(() -> {
            if (!isBackground() && isVisible(mBinding.widget.error)) mBinding.widget.error.requestFocus();
        });
    }

    private void hideError() {
        setVisibilityIfChanged(mBinding.widget.error, View.GONE);
        setPlainTextIfChanged(mBinding.widget.text, "");
    }

    private void setInfoVisible(boolean visible) {
        setVisibilityIfChanged(mBinding.widget.info, visible ? View.VISIBLE : View.GONE);
        showDisplayInfo();
    }

    private void showInfo() {
        setInfoVisible(true);
    }

    private void hideInfo() {
        setInfoVisible(false);
    }

    private void updateCenterProgress(long position, long duration) {
        long safePosition = Math.max(position, 0);
        long safeDuration = Math.max(duration, 0);
        setPlainTextIfChanged(mBinding.widget.exoDuration, mPlayers.stringToTime(safeDuration));
        setPlainTextIfChanged(mBinding.widget.exoPosition, mPlayers.stringToTime(safePosition));
        mBinding.widget.seekBar.setPosition(safePosition);
        mBinding.widget.seekBar.setDuration(safeDuration);
    }

    private void setCenterAction(int resId) {
        mBinding.widget.action.setImageResource(resId);
    }

    private void setCenterVisible(boolean visible) {
        setVisibilityIfChanged(mBinding.widget.center, visible ? View.VISIBLE : View.GONE);
    }

    private void showInfoAndCenter() {
        showInfo();
        updateCenterProgress(mPlayers.getPosition(), mPlayers.getDuration());
        setCenterVisible(true);
    }

    private void hideInfoAndCenter() {
        hideInfo();
        setCenterVisible(false);
    }

    private void setControlNextFocus() {
        int count = mBinding.control.actionLayout.getChildCount();
        for(int i=0; i<count-1; i++) {
            View btn = mBinding.control.actionLayout.getChildAt(i);
            if (btn == null || !isVisible(btn) || !btn.isEnabled()) continue;
            for(int j=i+1; j<count; j++) {
                View next = mBinding.control.actionLayout.getChildAt(j);
                if (next == null || !isVisible(next) || !next.isEnabled()) continue;
                btn.setNextFocusRightId(next.getId());
                next.setNextFocusLeftId(btn.getId());
                break;
            }
        }
    }

    private void showControl(View view) {
        boolean changed = !isVisible(mBinding.control.getRoot());
        syncDanmuVisible();
        setVisibilityIfChanged(mBinding.control.danmu, View.VISIBLE);
        setVisibilityIfChanged(mBinding.control.getRoot(), View.VISIBLE);
        setEpisodeControlVisible(mEpisodeAdapter.size() > 1);
        view.requestFocus();
        setControlNextFocus();
        if (changed) showDisplayInfo();
        setR1Callback();
    }

    private void hideControl() {
        hideControl(true);
    }

    private void hideControl(boolean hideInfo) {
        boolean infoChanged = hideInfo && isVisible(mBinding.widget.info);
        if (hideInfo) setVisibilityIfChanged(mBinding.widget.info, View.GONE);
        boolean changed = isVisible(mBinding.control.getRoot());
        setPlainTextIfChanged(mBinding.control.text, getString(R.string.play_track_text));
        setVisibilityIfChanged(mBinding.control.getRoot(), View.GONE);
        mUiCallbacks.remove(mR1);
        if (infoChanged || changed) showDisplayInfo();
    }

    private void hideCenter() {
        setCenterAction(R.drawable.ic_widget_play);
        setCenterVisible(false);
    }

    private void showPreview(Drawable preview) {
        if (Setting.getFlag() == 0 || isGone(mBinding.widget.preview)) return;
        setVisibilityIfChanged(mBinding.widget.preview, View.VISIBLE);
        mBinding.widget.preview.setImageDrawable(preview);
    }

    private void hidePreview() {
        setVisibilityIfChanged(mBinding.widget.preview, View.GONE);
        mBinding.widget.preview.setImageDrawable(null);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        if (!isBuffering()) {
            stopProgressPolling();
            return;
        }
        mUiCallbacks.post(mR3, Constant.INTERVAL_TRAFFIC);
    }

    private void reconcilePlaybackUiState() {
        refreshDanmaku();
        if (isBuffering()) {
            mPlaybackState.onBuffering();
        } else if (mPlaybackFlow.state().getPhase() == VodPlaybackPhase.READY) {
            mPlaybackState.onReady();
        } else if (mPlaybackFlow.state().getPhase() == VodPlaybackPhase.PAUSED) {
            mPlaybackState.onPaused();
        }
    }

    private boolean isBuffering() {
        if (mPlaybackFlow == null) return mPlayers.isBuffering();
        VodPlaybackPhase phase = mPlaybackFlow.state().getPhase();
        return phase == VodPlaybackPhase.RESOLVING || phase == VodPlaybackPhase.PREPARING || phase == VodPlaybackPhase.BUFFERING;
    }

    private void setR1Callback() {
        mUiCallbacks.remove(mR1);
        mUiCallbacks.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback(long delayMillis) {
        mUiCallbacks.post(mR2, delayMillis);
    }

    private void setArtwork(String url) {
        mArtwork.load(url);
    }

    private void clearArtworkTarget() {
        mArtwork.close();
    }

    private void getPart(String source) {
        clearPartRequest();
        String keyword = source.trim();
        if (keyword.isEmpty()) {
            setPartAdapter(Collections.emptyList());
            return;
        }
        List<String> cached;
        synchronized (PART_CACHE) {
            cached = PART_CACHE.get(keyword);
        }
        if (cached != null) {
            setPartAdapter(cached);
            return;
        }
        mPartCall = OkHttp.newCall(OkHttp.client(5000), "https://api.yesapi.cn/?service=App.Scws.GetWords&app_key=CEE4B8A091578B252AC4C92FB4E893C3&text=" + URLEncoder.encode(keyword));
        mPartCall.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<String> items;
                try (Response res = response) {
                    if (call != mPartCall) return;
                    ResponseBody body = res.body();
                    items = body == null ? new ArrayList<>() : new ArrayList<>(Part.get(body.string()));
                }
                Iterator<String> iterator = items.iterator();
                while (iterator.hasNext()) if (iterator.next().equals(keyword)) iterator.remove();
                synchronized (PART_CACHE) {
                    PART_CACHE.put(keyword, new ArrayList<>(items));
                }
                mUiCallbacks.post(() -> {
                    if (call != mPartCall || isFinishing() || isDestroyed()) return;
                    mPartCall = null;
                    setPartAdapter(items);
                }, 200);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ThreadPools.log(e, "Part suggestion load failed.");
                List<String> items = Collections.emptyList();
                mUiCallbacks.post(() -> {
                    if (call != mPartCall || isFinishing() || isDestroyed()) return;
                    mPartCall = null;
                    setPartAdapter(items);
                }, 200);
            }
        });
    }

    private void clearPartRequest() {
        if (mPartCall != null) mPartCall.cancel();
        mPartCall = null;
    }

    private void updateRecommendationReadiness() {
        mRecommendations.setReady(mPlaybackCoordinator != null
                && mPlaybackFlow.state().getPhase() == VodPlaybackPhase.READY && !mSeekGesture.isActive());
    }

    private void loadRecommendations(Vod item) {
        mRecommendAdapter.clear();
        mBinding.recommendGroup.setVisibility(View.GONE);
        String title = item.getVodName(getName());
        String year = item.getVodYear();
        mRecommendations.load(title.isEmpty() ? null : () -> DoubanRecommendRepository.request(title, year), items -> {
            if (isFinishing() || isDestroyed()) return;
            mRecommendAdapter.setItems(items, null);
            setVisibilityIfChanged(mBinding.recommendGroup, items.isEmpty() ? View.GONE : View.VISIBLE);
            updateFocus();
        });
        updateRecommendationReadiness();
    }

    private void setPartAdapter(List<String> items) {
        mBinding.part.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mPartAdapter.setItems(items, null);
        setR2Callback(1000);
    }

    private void onSearchPage() {
        String keyword = mPage.history() == null ? textOf(mBinding.name).trim() : mPage.history().getVodName();
        if (TextUtils.isEmpty(keyword)) keyword = getName();
        if (TextUtils.isEmpty(keyword)) return;
        SearchActivity.start(this, keyword, true);
    }

    private void checkKeep() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(!mPage.hasFavorite(getHistoryKey()) ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        mPage.createFavorite(getHistoryKey(), getSiteCid(), getSite().getName(),
                mBinding.name.getText().toString(), Objects.toString(mBinding.video.getTag(), ""));
    }

    @Override
    public void showChooser(TrackDialog dialog) {
        FileChooserDialog.create().player(mPlayers).trackDialog(dialog).show(this);
    }

    @Override
    public void onTrackClick(Track item) {
        TrackRepository.get().save(item, getHistoryKey());
    }

    @Override
    public void onSubtitleClick() {
        long request = mPlaybackFlow.requestGeneration();
        mUiCallbacks.post(this::hideControl, 200);
        SubtitleView view = mPlayers.isIjk() ? getIjk().getSubtitleView() : getExo().getSubtitleView();
        Episode current = mEpisodeAdapter != null && mEpisodeAdapter.size() > 0 ? getEpisode() : null;
        String name = PlaybackSubtitleLauncher.name(getName(), current == null ? "" : current.getName(), getTitle());
        PlaybackSubtitleLauncher.show(this, mUiCallbacks, view, name, isFullscreen(),
                () -> mPlaybackFlow.acceptsRequest(request), this::setSubtitle);
    }

    @Override
    public void onTimeChanged() {
        onTimeChangeDisplaySpeed();
        mHistorySession.tick(System.currentTimeMillis());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (!isCurrentPlayer() || isBackground()) return;
        if (ActionEvent.PLAY.equals(event.getAction())) {
            onPlay();
        } else if (ActionEvent.PAUSE.equals(event.getAction())) {
            onPaused();
        } else if (ActionEvent.NEXT.equals(event.getAction())) {
            checkNext();
        } else if (ActionEvent.PREV.equals(event.getAction())) {
            checkPrev();
        } else if (ActionEvent.STOP.equals(event.getAction())) {
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.CONFIG) {
            if (mParseAdapter != null) {
                mParseAdapter.setItems(VodConfig.get().getParses(), null);
                mSelection.selectParse(getParsePosition(), mParseAdapter.size());
            }
            return;
        }
    }

    private void onPlaybackCommand(PlaybackCommand command) {
        if (!isCurrentPlayer() || isBackground()) return;
        switch (command.getType()) {
            case DETAIL: takePlaybackControl(); mDetail.request(); break;
            case RELOAD: takePlaybackControl(); onRefresh(); break;
            case DANMAKU: setDanmaku(Danmaku.from(command.getPath())); break;
            case SUBTITLE: setSubtitle(Sub.from(command.getPath())); break;
        }
    }

    private void onPlaybackPhase(VodPlaybackPhase phase) {
        if (!isCurrentPlayer() || isBackground()) return;
        switch (phase) {
            case RESOLVING:
            case PREPARING:
                setPlayerView();
                mPlaybackState.onPreparing();
                break;
            case IDLE:
            case STOPPED:
            case ERROR:
            case RELEASED:
                mPlaybackState.clear();
                break;
            case BUFFERING:
                mPlaybackState.onBuffering();
                break;
            case READY:
                showPlayingState();
                mPlaybackState.onReady();
                break;
            case PAUSED:
                showPausedState();
                break;
            case ENDED:
                mPlaybackState.clear();
                checkEnded();
                break;
            default:
                break;
        }
    }

    private void renderPlayback(VodPlaybackState state) {
        mRecommendations.setReady(state.getPhase() == VodPlaybackPhase.READY && !mSeekGesture.isActive());
        if (state.getPhase() == VodPlaybackPhase.RESOLVING || state.getPhase() == VodPlaybackPhase.BUFFERING) {
            if (mSiteSearch != null) mSiteSearch.cancel();
        }
        if (!isCurrentPlayer() || isBackground()) return;
        boolean commitHistory = canCommitCurrentHistory();
        if (commitHistory) mHistoryController.updateFields(mPage.history(), state.getPosition(), state.getDuration());
        String identity = state.getEpisode() == null ? "" : state.getEpisode().getIdentity();
        if (!identity.equals(mRenderedEpisode) && state.getEpisodeIndex() >= 0 && state.getEpisodeIndex() < mEpisodeAdapter.size()) {
            mRenderedEpisode = identity;
            Episode episode = (Episode) mEpisodeAdapter.get(state.getEpisodeIndex());
            mSelection.selectEpisode(state.getEpisodeIndex(), mEpisodeAdapter.size());
            mPage.selectEpisode(episode);
            notifyItemChanged(getEpisodeView(), mEpisodeAdapter);
            if (!getEpisodeView().hasFocus()) setEpisodeSelectedPosition(state.getEpisodeIndex());
            CharSequence title = getString(R.string.detail_title, mBinding.name.getText(), state.getEpisode().getName());
            setPlainTextIfChanged(mBinding.widget.title, title);
            setPlainTextIfChanged(mBinding.display.title, title);
            if (commitHistory && getFlag() != null) mHistoryController.commitEpisode(mPage.history(), getFlag().getFlag(), episode, state);
        }
        if (commitHistory && getFlag() != null && state.getEpisodeIndex() >= 0 && state.getEpisodeIndex() < mEpisodeAdapter.size()) {
            mHistoryController.commitEpisode(mPage.history(), getFlag().getFlag(), (Episode) mEpisodeAdapter.get(state.getEpisodeIndex()), state);
        }
        onPlaybackPhase(state.getPhase());
    }

    private boolean canCommitCurrentHistory() {
        return mHistorySession != null && mHistorySession.canCommit();
    }

    private void checkEnded() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void setTrackVisible(boolean visible) {
        setVisibilityIfChanged(mBinding.control.text, visible ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.volume, visible && mPlayers.isExo() ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.audio, visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.video, visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    private void setSubtitle(Sub subtitle) {
        mPlaybackFlow.dispatch(PlaybackInput.subtitle(subtitle));
    }

    private void setDefaultTrack() {
        if (isInitTrack()) {
            setInitTrack(false);
            mPlayers.prepared();
            mPlayers.setTrack(TrackRepository.get().find(getHistoryKey()));
        }
    }

    private void setMetadata() {
        String title = mPage.history() == null ? textOf(mBinding.name) : mPage.history().getVodName();
        Episode current = mEpisodeAdapter.size() > 0 ? getEpisode() : null;
        String episode = current == null ? title : current.getName();
        String artist = title.equals(episode) ? "" : getString(R.string.play_now, episode);
        String artwork = mPage.history() == null ? getPic() : mPage.history().getVodPic();
        mPlayers.setMetadata(title, artist, artwork);
    }

    private void onPlaybackError(ErrorEvent event) {
        mPlaybackFlow.onError(event);
    }

    private void stopWithError(ErrorEvent event) {
        TrackRepository.get().delete(getHistoryKey());
        showError(event.getMsg());
        markCurrentSourceBroken(event);
        capturePlaybackPosition();
        stopActivePlayback();
    }

    private void initParse() {
        if (mParseAdapter.size() == 0) return;
        VodConfig.get().setParse((Parse) mParseAdapter.get(0));
        mSelection.selectParse(0, mParseAdapter.size());
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
    }

    private void onPaused() {
        showPausedState();
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
    }

    private void showPausedState() {
        mPlaybackState.onPaused();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setCenterAction(R.drawable.ic_widget_play);
        updateCenterProgress(mPlayers.getPosition(), mPlayers.getDuration());
        if (isFullscreen()) showInfoAndCenter();
        else hideInfoAndCenter();
    }

    private void showPlayingState() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // A transient pause while advancing episodes may have shown the title.
        // Clear both overlays when playback starts, including automatic next/skip-ending.
        hideInfoAndCenter();
        setVisibilityIfChanged(mBinding.display.titleLayout, View.GONE);
    }

    private void onPlay() {
        showPlayingState();
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.PLAY));
    }

    public boolean isBackground() {
        return background;
    }

    private boolean isCurrentPlayer() {
        return mPlayers.isCurrent();
    }

    public void setBackground(boolean background) {
        this.background = background;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    private boolean isInitTrack() {
        return initTrack;
    }

    private void setInitTrack(boolean initTrack) {
        this.initTrack = initTrack;
    }

    private boolean isSourceSwitching() {
        return mPlaybackFlow.sourceSwitch().isActive();
    }

    private boolean isSourceLoading() {
        return mPlaybackFlow.sourceSwitch().isLoading();
    }

    private void startSourceLoading() {
        if (!isSourceSwitching()) return;
        mPlaybackFlow.sourceSwitch().startLoading();
    }

    private void resumeSourceSearch() {
        if (!isSourceSwitching()) return;
        mPlaybackFlow.sourceSwitch().resumeSearch();
    }

    private void beginSourceSwitch() {
        if (isSourceSwitching()) return;
        capturePlaybackPosition();
        mPlaybackFlow.intent().keepCurrentPositionOnNextPlay();
        mPlaybackFlow.sourceSwitch().search(getCurrentSwitchEpisode(), getCurrentSwitchFlag());
    }

    private void clearSourceSwitch() {
        clearSourceSwitchTimeout();
        mPlaybackFlow.sourceSwitch().clear();
    }

    private void continueSourceSwitch() {
        if (!isSourceSwitching()) return;
        if (mSourceCandidates.hasItems() && advanceSourceSwitchCandidate()) return;
        if (!mSiteSearch.hasPending()) clearSourceSwitch();
    }

    private boolean advanceSourceSwitchCandidate() {
        return mPlaybackNavigation.nextSite();
    }

    private String getCurrentSwitchEpisode() {
        if (mPlaybackCoordinator != null && mPlaybackFlow.state().getEpisode() != null)
            return mPlaybackFlow.state().getEpisode().getName();
        if (mEpisodeAdapter != null && mEpisodeAdapter.size() > 0) return getEpisode().getName();
        if (mPage.history() != null && !TextUtils.isEmpty(mPage.history().getVodRemarks())) return mPage.history().getVodRemarks();
        return getMark();
    }

    private String getCurrentSwitchKeyword() {
        if (mPage.history() != null && !TextUtils.isEmpty(mPage.history().getVodName())) return mPage.history().getVodName();
        return mBinding == null ? getName() : textOf(mBinding.name);
    }

    private long getCurrentSwitchPosition() {
        long coordinatorPosition = mPlaybackCoordinator == null ? 0 : mPlaybackCoordinator.currentPosition();
        if (mPlaybackCoordinator != null && mPlaybackFlow.state().getEpisode() != null) return coordinatorPosition;
        long position = mPlayers == null ? 0 : mPlayers.getPosition();
        if (position > 0) return position;
        return mPage.history() == null ? 0 : Math.max(mPage.history().getPosition(), 0);
    }

    private void setPendingDetailRequest(String token) {
        mDetail.expect(token, getKey(), getId());
        mPage.begin(token, getHistoryKey(), getSiteCid(), getMark(), Setting.getPlaySpeed(), Setting.isIncognito());
    }

    private void scheduleSourceSwitchTimeout() {
        clearSourceSwitchTimeout();
        if (!isSourceSwitching()) return;
        mUiCallbacks.post(mR6, SOURCE_SWITCH_DETAIL_TIMEOUT_MS);
    }

    private void clearSourceSwitchTimeout() {
        mUiCallbacks.remove(mR6);
    }

    private void onSourceSwitchTimeout() {
        if (!isSourceLoading()) return;
        resumeSourceSearch();
        continueSourceSwitch();
    }

    private void clearPlaybackTimeout() {
        if (mPlaybackCoordinator != null) mPlaybackCoordinator.clearTimeout();
    }

    private void onFlagSelected() {
        int position = mBinding.flag.getSelectedPosition();
        if (position < 0 || position >= mFlagAdapter.size()) return;
        mPlaybackNavigation.switchFlag((Flag) mFlagAdapter.get(position), false);
    }

    private void onFlagClick(Flag item) {
        if (item.isActivated()) {
            if (!isFullscreen()) enterFullscreen();
            return;
        }
        takePlaybackControl();
        mPlaybackNavigation.switchFlag(item, false);
    }

    private void onParseClick(Parse item) {
        if (item.isActivated()) return;
        takePlaybackControl();
        mPlaybackNavigation.switchParse(item, false);
    }



    private boolean isCurrentDetailResult(Result result) {
        return mDetail.accepts(result);
    }

    private String getSourceSwitchKeyword() {
        String keyword = Objects.toString(mBinding.part.getTag(), "");
        return keyword.isEmpty() ? getCurrentSwitchKeyword() : keyword;
    }

    private void markCurrentSourceBroken() {
        mSourceCandidates.markBroken(getCurrentBrokenKey());
    }

    private void markCurrentSourceBroken(ErrorEvent event) {
        if (event == null || (!event.isUrl() && !event.isExtract() && !event.isTimeout())) return;
        markCurrentSourceBroken();
    }

    private String getCurrentBrokenKey() {
        return getSourceKey(getKey(), getId(), getCurrentSourceName());
    }

    private String getSourceKey(Vod item) {
        return SearchSorter.key(item);
    }

    private String getSourceKey(String siteKey, String vodId, String vodName) {
        return SearchSorter.key(siteKey, vodId, vodName);
    }

    private String getCurrentSourceName() {
        String name = mBinding == null ? "" : textOf(mBinding.name);
        return TextUtils.isEmpty(name) ? getName() : name;
    }

    private void mergeQuickItems(List<Vod> items, String token) {
        if (!mSiteSearch.isActive() || !TextUtils.equals(token, mSiteSearch.getToken())) return;
        mSourceCandidates.setKeyword(getSourceSwitchKeyword());
        if (mSourceCandidates.add(items)) scheduleQuickFlush();
    }

    private void scheduleQuickFlush() {
        if (mQuickFlushScheduled) return;
        mQuickFlushScheduled = true;
        mUiCallbacks.post(mQuickFlush, QUICK_FLUSH_DELAY_MS);
    }

    private void flushQuickItems() {
        mUiCallbacks.remove(mQuickFlush);
        mQuickFlushScheduled = false;
        if (!mSiteSearch.isActive()) return;
        mSourceCandidates.setKeyword(getSourceSwitchKeyword());
        syncQuickItems();
    }

    private void syncQuickItems() {
        List<Vod> items = mSourceCandidates.sortedItems();
        mQuickAdapter.setItems(items, QUICK_DIFF);
        setVisibilityIfChanged(mBinding.quick, items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void resetQuickResults() {
        mUiCallbacks.remove(mQuickFlush);
        mQuickFlushScheduled = false;
        mSourceCandidates.clearCandidates();
    }

    private Flag findTargetFlag(List<Flag> flags) {
        if (isSourceLoading()) return mPlaybackFlow.sourceSwitch().findTargetFlag(flags);
        String target = mPage.history() != null ? mPage.history().getVodFlag() : null;
        for (Flag flag : flags) if (!flag.getEpisodes().isEmpty() && flag.getFlag().equals(target)) return flag;
        return firstPlayableFlag(flags);
    }

    private Flag firstPlayableFlag(List<Flag> flags) {
        for (Flag flag : flags) if (!flag.getEpisodes().isEmpty()) return flag;
        return null;
    }

    private Flag getCurrentSwitchFlag() {
        if (mFlagAdapter == null || mFlagAdapter.size() == 0) return null;
        return getFlag();
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(int size) {
        groupSize = size;
    }

    private View getFocus1() {
        return mFocus1 == null ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        if (mFocus2 != null && mFocus2 != mBinding.control.opening && mFocus2 != mBinding.control.ending && isVisible(mFocus2) && mFocus2.isEnabled()) return mFocus2;
        int count = mBinding.control.actionLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            View view = mBinding.control.actionLayout.getChildAt(i);
            if (view != null && isVisible(view) && view.isEnabled()) return view;
        }
        return mBinding.video;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        hasKeyEvent = true;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        if (!isFullscreen() && getEpisodeView().hasFocus() && KeyUtil.isDigitKey(event)) {
            if (up) appendEpisodeNumber(event.getKeyCode());
            return true;
        }
        if (mBinding.progressLayout.isContent() && !isFullscreen() && KeyUtil.isBackKey(event) && Setting.getSmallWindowBackKey() == 1 && getCurrentFocus() != mBinding.video) {
            if (up) {
                mFocus1 = mBinding.video;
                getFocus1().requestFocus();
            }
            return true;
        }
        if (isFullscreen() && KeyUtil.isMenuKey(event)) {
            if (up) {
                if (Setting.getFullscreenMenuKey() == 0) onToggle();
                else onEpisodes();
            }
            return true;
        }
        if (isVisible(mBinding.control.getRoot())) {
            setR1Callback();
            mFocus2 = getCurrentFocus();
        }
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event)) {
            return mKeyDown.onKeyDown(event);
        }
        return super.dispatchKeyEvent(event);
    }

    private void appendEpisodeNumber(int keyCode) {
        if (mEpisodeInput.length() >= 4) return;
        mEpisodeInput.append(keyCode >= KeyEvent.KEYCODE_NUMPAD_0 ? keyCode - KeyEvent.KEYCODE_NUMPAD_0 : keyCode - KeyEvent.KEYCODE_0);
        Notify.show(mEpisodeInput.toString());
        mUiCallbacks.remove(mEpisodeInputCommit);
        mUiCallbacks.post(mEpisodeInputCommit, 1200);
    }

    private void playNumberedEpisode() {
        if (mEpisodeInput.length() == 0) return;
        String number = mEpisodeInput.toString();
        mEpisodeInput.setLength(0);
        if (mEpisodeAdapter.size() == 0) return;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < mEpisodeAdapter.size(); i++) names.add(((Episode) mEpisodeAdapter.get(i)).getName());
        int position = EpisodeMatcher.find(names, number, true, -1);
        if (position < 0) {
            try {
                position = Integer.parseInt(number) - 1;
            } catch (NumberFormatException ignored) {
            }
        }
        if (position < 0 || position >= mEpisodeAdapter.size()) return;
        setEpisodeSelectedPosition(position);
        setEpisodeActivated((Episode) mEpisodeAdapter.get(position));
    }

    @Override
    public void onBright(int progress) {
        setVisibilityIfChanged(mBinding.widget.bright, View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onBrightEnd() {
        setVisibilityIfChanged(mBinding.widget.bright, View.GONE);
    }

    @Override
    public void onVolume(int progress) {
        setVisibilityIfChanged(mBinding.widget.volume, View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onVolumeEnd() {
        setVisibilityIfChanged(mBinding.widget.volume, View.GONE);
    }

    @Override
    public void onSeeking(int time) {
        Source.get().cancelPreloads();
        mPlaybackFlow.sources().stop();
        mRecommendations.setReady(false);
        setCenterAction(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        updateCenterProgress(mSeekGesture.preview(mPlayers.getPosition(), mPlayers.getDuration(), time), mPlayers.getDuration());
        setCenterVisible(true);
        hideProgress();
    }

    @Override
    public void onSeekTo(int time) {
        mPlaybackFlow.dispatch(PlaybackInput.seek(mSeekGesture.commit(mPlayers.getPosition(), mPlayers.getDuration(), time)));
        mKeyDown.resetTime();
        updateRecommendationReadiness();
        // Seeking preserves the engine's play intent. isPlaying() is false
        // during buffering too, so it must never decide whether to pause here.
        if (mPlaybackFlow.state().getPhase() == VodPlaybackPhase.PAUSED) showPausedState();
        else hideInfoAndCenter();
    }

    @Override
    public void onSpeedUp() {
        if (!mPlayers.isPlaying() || !mPlayers.canAdjustSpeed()) return;
        if (Float.isNaN(mSpeedBeforeLongPress)) mSpeedBeforeLongPress = mPlayers.getSpeed();
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        setVisibilityIfChanged(mBinding.widget.speed, View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        if (!Float.isNaN(mSpeedBeforeLongPress)) setPlainTextIfChanged(mBinding.control.speed, mPlayers.setSpeed(mSpeedBeforeLongPress));
        mSpeedBeforeLongPress = Float.NaN;
        setVisibilityIfChanged(mBinding.widget.speed, View.GONE);
        mBinding.widget.speed.clearAnimation();
    }

    @Override
    public void onKeyUp() {
        checkPrev();
    }

    @Override
    public void onKeyDown() {
        checkNext();
    }

    @Override
    public void onKeyCenter() {
        if (mPlayers.isPlaying()) {
            onPaused();
            hideControl(false);
        } else {
            onPlay();
            hideControl(true);
        }
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
    }

    @Override
    public void onPlayerClick(Integer item) {
        mPlaybackFlow.dispatch(PlaybackInput.player(item));
    }

    @Override
    public void onPlayerShare(String title) {
        this.onChoose();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        switch (requestCode) {
            case 1001:
                mPlayers.checkData(data);
                break;
            case REQUEST_DANMAKU_FILE:
                if (data != null && data.getData() != null) setDanmaku(Danmaku.from(FileChooser.getPathFromUri(this, data.getData())));
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setBackground(false);
        mClock.start();
        if (mPlaybackLifecycle.resume()) hideInfoAndCenter();
        else reconcilePlaybackUiState();
    }

    @Override
    protected void onPause() {
        mKeyDown.release();
        mSeekGesture.reset();
        super.onPause();
        setBackground(true);
        mPlaybackState.clear();
        saveHistoryNow();
        mPlaybackLifecycle.pause();
        mClock.stop();
        stopDisplayTrafficPolling();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void takePlaybackControl() {
        mPlaybackFlow.recovery().takeControl();
    }

    @Override
    public void onBackPressed() {
        if (mSeekGesture.isActive()) {
            mKeyDown.release();
            mSeekGesture.reset();
            updateRecommendationReadiness();
            hideCenter();
            return;
        }
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else if (isFullscreen()) {
            exitFullscreen();
        } else {
            mPlaybackFlow.sources().stop();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        mUiCallbacks.close();
        mArtwork.close();
        mPlaybackFlow.recovery().close();
        mRecommendations.close();
        mUiCallbacks.remove(mEpisodeInputCommit);
        mEpisodeInput.setLength(0);
        mKeyDown.release();
        saveHistoryNow();
        mPage.close();
        super.onDestroy();
        clearPartRequest();
        mPlaybackState.release();
        mPlaybackFlow.sources().stop();
        mClock.release();
        if (mPlaybackSubscription != null) mPlaybackSubscription.cancel();
        mPlayers.setInputHandler(null);
        if (mPlaybackFlow != null) mPlaybackFlow.close();
        if (mPlaybackCoordinator != null) mPlaybackCoordinator.close();
        if (mVodResolver != null) mVodResolver.close();
        mPlayers.release();
        clearArtworkTarget();
        RefreshEvent.history();
        mUiCallbacks.remove(mR1, mR2, mR3, mR4, mR5, mR6, mR8);
    }
}
