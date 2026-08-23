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
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.repository.HistoryRepository;
import com.fongmi.android.tv.repository.DoubanRecommendRepository;
import com.fongmi.android.tv.bean.Keep;
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
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.model.SiteSearchSession;
import com.fongmi.android.tv.player.IjkUtil;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.PlaybackRecovery;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.player.PlaybackMarkers;
import com.fongmi.android.tv.player.PlaybackSeekGesture;
import com.fongmi.android.tv.player.session.SwitchablePlayerSession;
import com.fongmi.android.tv.player.resolver.VodPlaybackResolver;
import com.fongmi.android.tv.player.resolver.SpiderPlaybackGateway;
import com.fongmi.android.tv.player.vod.StartPolicy;
import com.fongmi.android.tv.player.vod.DefaultPlaybackItemFactory;
import com.fongmi.android.tv.player.vod.VodEpisode;
import com.fongmi.android.tv.player.vod.VodPlaybackCoordinator;
import com.fongmi.android.tv.player.vod.VodPlaybackPhase;
import com.fongmi.android.tv.player.vod.VodPlaybackState;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.player.danmu.Parser;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.dialog.DescDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeDialog;
import com.fongmi.android.tv.ui.dialog.FileChooserDialog;
import com.fongmi.android.tv.ui.dialog.PlayerDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
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
import com.fongmi.android.tv.ui.player.TvVodHistorySession;
import com.fongmi.android.tv.ui.player.TvVodDetailController;
import com.fongmi.android.tv.ui.player.TvVodRecoveryController;
import com.fongmi.android.tv.ui.player.TvVodSourceSwitchState;
import com.fongmi.android.tv.ui.player.TvVodSourceSearch;
import com.fongmi.android.tv.ui.player.TvVodSelectionState;
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.EpisodeMatcher;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
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
    private static final int QUICK_RESULT_LIMIT = 50;
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
        @Override public boolean isBuffering() { return mPlayers.isBuffering(); }

        @Override public void beginPreparing() {
            setInitTrack(true);
            setTrackVisible(false);
            mClock.setCallback(VideoActivity.this);
        }

        @Override public void showProgress() { VideoActivity.this.showProgress(); }
        @Override public void stopContentSearch() { mSourceSearch.stop(); }
        @Override public void clearPlaybackTimeout() { VideoActivity.this.clearPlaybackTimeout(); }
        @Override public void updateMetadata() { setMetadata(); }
        @Override public void resetRecovery() { mRecovery.reset(); }
        @Override public void clearBrokenSources() { mBroken.clear(); }
        @Override public void hideProgress() { VideoActivity.this.hideProgress(); }
        @Override public void resetTransientPlayerState() { mPlayers.reset(); }
        @Override public void applyDefaultTrack() { setDefaultTrack(); }
        @Override public void showTracks() { setTrackVisible(true); }

        @Override public void persistPlayerSelection() {
            if (mHistory != null) mHistory.setPlayer(mPlayers.getPlayer());
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
        @Override public long currentPosition() { return getCurrentSwitchPosition(); }
        @Override public void rememberFlagSwitchPosition(long position) { mHistorySession.preserveFlagPosition(position); }
        @Override public void playFlag(Flag flag, int episode) { VideoActivity.this.playFlag(flag, episode); }

        @Override public boolean selectFlag(Flag item, boolean notify) {
            if (mFlagAdapter.size() == 0 || item == null || item.getEpisodes().isEmpty()) return false;
            if (item.isActivated() && !isSourceSwitching()) return false;
            int index = mFlagAdapter.indexOf(item);
            if (index < 0) return false;
            for (int i = 0; i < mFlagAdapter.size(); i++) ((Flag) mFlagAdapter.get(i)).setActivated(item);
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
            for (int i = 0; i < mFlagAdapter.size(); i++) ((Flag) mFlagAdapter.get(i)).toggle(flag == i, item);
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
        @Override public void stopSourceSearch() { mSourceSearch.stop(); }
        @Override public void openDetail(Vod item, boolean notify) {
            if (notify) Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
            mDetail.open(item);
        }

        @Override public Flag nextPlayableFlag() {
            for (int i = getFlagPosition() + 1; i < mFlagAdapter.size(); i++) {
                Flag item = (Flag) mFlagAdapter.get(i);
                if (!item.getEpisodes().isEmpty()) return item;
            }
            return null;
        }

        @Override public Vod nextPlayableSite() {
            while (mQuickAdapter.size() > 0) {
                Vod item = (Vod) mQuickAdapter.get(0);
                mQuickAdapter.removeItems(0, 1);
                mQuickQueue.remove(item);
                String key = getBrokenKey(item);
                if (!TextUtils.isEmpty(key) && (key.equals(getCurrentBrokenKey()) || mBroken.contains(key))) continue;
                return item;
            }
            return null;
        }

        @Override public void beginSourceSwitch() { VideoActivity.this.beginSourceSwitch(); }
        @Override public void markCurrentSourceBroken() { VideoActivity.this.markCurrentSourceBroken(); }
        @Override public void startSourceLoading() { VideoActivity.this.startSourceLoading(); }
        @Override public void markSourceSelected() { mSiteSearch.markSelected(); }
    }

    private final class SourceSearchPort implements TvVodSourceSearch.Port {

        @Override public boolean accepts(Result result) { return mSiteSearch.accepts(result); }
        @Override public String expectedKeyword() { return Objects.toString(mBinding.part.getTag(), "").trim(); }
        @Override public String sourceSwitchKeyword() { return getSourceSwitchKeyword(); }
        @Override public String sourceKey(Vod vod) { return getSourceKey(vod); }
        @Override public void mergeCandidates(List<Vod> items, String token) { mergeQuickItems(items, token); }
        @Override public void cancelEmptyFallback() { App.removeCallbacks(mR4); }

        @Override public void removeCandidate(Vod item) {
            int index = mQuickAdapter.indexOf(item);
            if (index >= 0) mQuickAdapter.removeItems(index, 1);
            mQuickQueue.remove(item);
        }

        @Override public void beginSourceSwitch() { VideoActivity.this.beginSourceSwitch(); }
        @Override public void markCurrentSourceBroken() { VideoActivity.this.markCurrentSourceBroken(); }
        @Override public void setManual() { mSiteSearch.setManual(); }
        @Override public void startSourceLoading() { VideoActivity.this.startSourceLoading(); }
        @Override public void openDetail(Vod vod) { mPlaybackNavigation.showDetail(vod, false); }
        @Override public int candidateCount() { return mQuickAdapter.size(); }
        @Override public void selectNextCandidate() { mPlaybackNavigation.nextSite(); }
        @Override public void cancelSearch() { if (mSiteSearch != null) mSiteSearch.cancel(); }

        @Override public void clearCandidates() {
            resetQuickResults();
        }

        @Override public void setSearchKeyword(String keyword) {
            mBinding.part.setTag(keyword);
            mQuickAdapter.clear();
        }

        @Override public void startSearch(List<Site> sites, String keyword, boolean automatic, Runnable settled) {
            mSiteSearch.start(sites, keyword, automatic, settled);
        }

        @Override public void onSearchSettled() { onSearchTasksSettled(); }
    }

    private final class DetailPort implements TvVodDetailController.Port {
        @Override public String nextRequestToken() { return VideoActivity.this.nextRequestToken("detail"); }
        @Override public void setPendingRequest(String token) { setPendingDetailRequest(token); }
        @Override public void scheduleSourceTimeout() { scheduleSourceSwitchTimeout(); }
        @Override public void requestDetail(String token) { mViewModel.detailContent(getKey(), getId(), token); }
        @Override public boolean accepts(Result result) { return isCurrentDetailResult(result); }
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
        @Override public void scheduleEmptyFallback() { App.post(mR4, 10000); }
        @Override public void recoverSource() { mSourceSearch.recover(); }
        @Override public void notifyMessage(String message) { Notify.show(message); }
    }

    private final class RecoveryPort implements TvVodRecoveryController.Port {
        @Override public PlaybackRecovery.Action decide(ErrorEvent error) { return mRecovery.next(error, mPlayers, true); }
        @Override public void retry() { onRefresh(); }
        @Override public void toggleDecode() { onDecode(false); }
        @Override public void reloadExo(ErrorEvent error) { capturePlaybackPosition(); mPlaybackCoordinator.reload(); }
        @Override public void switchPlayerAndRetry() { capturePlaybackPosition(); mPlayers.nextPlayer(); replacePlayerSession(); setPlayerView(); setDecodeView(); mPlaybackCoordinator.reload(); }
        @Override public void stopWithError(ErrorEvent error) { VideoActivity.this.stopWithError(error); }
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
        @Override public void recoverSource() { mSourceSearch.recover(); }
        @Override public boolean hasRecoveryWork() { return mSiteSearch.isActive() || mQuickAdapter.size() > 0 || isSourceSwitching(); }
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
    }

    private void showDetailEmpty() {
        clearSourceSwitchTimeout();
        mBinding.progressLayout.showEmpty();
        clearSourceSwitch();
        mSourceSearch.stop();
    }

    private void applyDetailHistory() {
        if (mHistory == null) return;
        if (Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) HistoryRepository.get().delete(mHistory);
        setPlainTextIfChanged(mBinding.control.opening, mHistory.getOpening() == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
        setPlainTextIfChanged(mBinding.control.ending, mHistory.getEnding() == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mHistory.getEnding()));
        mPlayers.setPlayer(getPlayer());
        replacePlayerSession();
        setScale(getScale());
        setPlayerView();
        setDecodeView();
    }

    private void checkDetailFlags(Vod item) {
        boolean empty = item.getVodFlags().isEmpty();
        setVisibilityIfChanged(mBinding.flag, item.getVodFlags().size() > 1 ? View.VISIBLE : View.GONE);
        if (empty) {
            if (isSourceLoading()) { resumeSourceSearch(); continueSourceSwitch(); }
            else ErrorEvent.flag();
            return;
        }
        Flag target = findTargetFlag(item.getVodFlags());
        if (target == null) { resumeSourceSearch(); continueSourceSwitch(); return; }
        resumeSourceSearch();
        mPlaybackNavigation.switchFlag(target, false);
        if (mHistory != null && mHistory.isRevSort()) reverseEpisode(true);
    }

    private void renderDetail(Vod item) {
        String token = pendingDetailToken;
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
            App.post(() -> {
                if (isFinishing() || isDestroyed() || !TextUtils.equals(token, pendingDetailToken)) return;
                setText(mBinding.actor, actor); setText(mBinding.content, content); setText(mBinding.director, director);
                mBinding.content.setMaxLines(getMaxLines());
            });
        });
        mFlagAdapter.setItems(item.getVodFlags(), null);
        if (!isSourceSwitching()) mBinding.video.requestFocus();
        setArtwork(item.getVodPic());
        getPart(item.getVodName());
        if (!isSourceSwitching()) loadRecommendations(item);
        App.removeCallbacks(mR4);
        mSelection.selectFlag(0, mFlagAdapter.size());
        mSelection.resetEpisode();
        loadHistory(item, pendingDetailToken);
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
    private final TaskScope mHistoryTasks = new TaskScope(ThreadPools.newSingle("video-history"), AppTaskScheduler.get(), error -> ThreadPools.log(error, "History load failed."));
    private SiteViewModel mViewModel;
    private List<Danmaku> mDanmakus;
    private final Set<String> mBroken = new HashSet<>();
    private History mHistory;
    private PlaybackController mPlayers;
    private VodPlaybackResolver mVodResolver;
    private VodPlaybackCoordinator mPlaybackCoordinator;
    private SwitchablePlayerSession mPlayerSession;
    private boolean background;
    private boolean fullscreen;
    private boolean initTrack;
    private boolean mDanmuVisible;
    private boolean useParse;
    private int mRecommendGeneration;
    private final PriorityQueue<Vod> mQuickQueue = new PriorityQueue<>(QUICK_RESULT_LIMIT + 1, this::compareQuickForQueue);
    private final Runnable mQuickFlush = this::flushQuickItems;
    private boolean mQuickFlushScheduled;
    private final TvVodDetailController mDetail = new TvVodDetailController(new DetailPort());
    private final TvVodSourceSearch mSourceSearch = new TvVodSourceSearch(new SourceSearchPort());
    private final PlaybackRecovery mRecovery = new PlaybackRecovery();
    private final PlaybackSeekGesture mSeekGesture = new PlaybackSeekGesture();
    private final TvVodRecoveryController mRecoveryController = new TvVodRecoveryController(new RecoveryPort());
    private final TvVodPlaybackNavigation mPlaybackNavigation = new TvVodPlaybackNavigation(new NavigationPort());
    private final TvVodPlaybackStateBinder mPlaybackState = new TvVodPlaybackStateBinder(new PlaybackStatePort());
    private int groupSize;
    private long mLastHistorySaveAt;
    private long mLastSavedHistoryPosition = -1;
    private long mLastSavedHistoryDuration = -1;
    private boolean mHistorySessionModified;
    private final TvVodHistorySession mHistorySession = new TvVodHistorySession();
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mR5;
    private Runnable mR6;
    private Runnable mR7;
    private Runnable mR8;
    private Runnable mRecommendRequest;
    private Call mPartCall;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private boolean hasKeyEvent;
    private boolean mResumeOnForeground = true;
    private final TvVodSelectionState mSelection = new TvVodSelectionState();
    private String mArtworkUrl;
    private long requestTokenSeed;
    private String pendingDetailToken;
    private int mEpisodeNumColumns;
    private int mEpisodeColumnWidth;
    private int mArraySize = -1;
    private int mDanmakuRequestId;
    private boolean mArrayRevSort;
    private boolean mDisplayTrafficPolling;
    private boolean mProgressTrafficPolling;
    private float mSpeedBeforeLongPress = Float.NaN;
    private final TvVodSourceSwitchState mSourceSwitch = new TvVodSourceSwitchState();
    private CustomTarget<Drawable> mArtworkTarget;
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
        return VodConfig.siteCid(getKey(), VodConfig.getCid());
    }

    private String getHistoryKey() {
        return History.historyKey(getKey(), getId(), getName(), getSiteCid());
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        return (Flag) mFlagAdapter.get(getFlagPosition());
    }

    private Episode getEpisode() {
        return (Episode) mEpisodeAdapter.get(getEpisodePosition());
    }

    private int getFlagPosition() {
        for (int i = 0; i < mFlagAdapter.size(); i++) if (((Flag) mFlagAdapter.get(i)).isActivated()) mSelection.selectFlag(i, mFlagAdapter.size());
        return mSelection.flag(mFlagAdapter.size());
    }

    private int getEpisodePosition() {
        for (int i = 0; i < mEpisodeAdapter.size(); i++) if (((Episode) mEpisodeAdapter.get(i)).isActivated()) mSelection.selectEpisode(i, mEpisodeAdapter.size());
        return mSelection.episode(mEpisodeAdapter.size());
    }

    private int getParsePosition() {
        for (int i = 0; i < mParseAdapter.size(); i++) if (((Parse) mParseAdapter.get(i)).isActivated()) mSelection.selectParse(i, mParseAdapter.size());
        return mSelection.parse(mParseAdapter.size());
    }

    private int getPlayer() {
        return mHistory != null && mHistory.getPlayer() != -1 ? mHistory.getPlayer() : Setting.getPlayer();
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : Setting.getScale();
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
        mPlayers = PlaybackController.create(this);
        setVideoView();
        mVodResolver = new VodPlaybackResolver(ThreadPools.newSingle("vod-resolver"), App::post, new SpiderPlaybackGateway(),
                this::prepareResolvedResult, result -> isUseParse());
        mPlayerSession = new SwitchablePlayerSession(mPlayers.session());
        mPlaybackCoordinator = new VodPlaybackCoordinator(mVodResolver, new DefaultPlaybackItemFactory(mPlayers::getSub), mPlayerSession);
        mPlaybackCoordinator.observe(this::renderPlayback);
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showDetailEmpty;
        mR5 = this::setDisplayTraffic;
        mR6 = this::onSourceSwitchTimeout;
        mR7 = this::onPlaybackTimeout;
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
        mBinding.widget.error.setOnClickListener(view -> onRefresh());
        mBinding.control.seek.setListener(mPlayers);
        mBinding.desc.setOnClickListener(view -> onDesc());
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
        mBinding.search1.setOnClickListener(view -> onSearchPage());
        mBinding.control.text.setOnClickListener(this::onTrack);
        mBinding.control.volume.setOnClickListener(view -> VolumeDialog.create(this).show());
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
                App.removeCallbacks(mR8);
                App.post(mR8, 200);
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
        mBinding.quick.setAdapter(new ItemBridgeAdapter(mQuickAdapter = new ArrayObjectAdapter(new QuickPresenter(mSourceSearch::select))));
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
        mBinding.control.parse.setAdapter(new ItemBridgeAdapter(mParseAdapter = new ArrayObjectAdapter(new ParsePresenter(item -> mPlaybackNavigation.switchParse(item, false)))));
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
        setPlainTextIfChanged(mBinding.control.player, mPlayers.getPlayerText());
        setPlainTextIfChanged(mBinding.control.decode, mPlayers.getDecodeText());
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.getSpeedText());
        String[] scale = ResUtil.getStringArray(R.array.select_scale);
        setPlainTextIfChanged(mBinding.control.scale, scale[safeIndex(Setting.getScale(), scale.length)]);
        setPlainTextIfChanged(mBinding.control.opening, getString(R.string.play_op));
        setPlainTextIfChanged(mBinding.control.ending, getString(R.string.play_ed));
    }

    private void setDanmuViewSettings() {
        float[] range = {2.4f, 1.8f, 1.2f, 0.8f};
        float speed = range[safeIndex(Setting.getDanmuSpeed(), range.length)] / Math.max(mPlayers.getSpeed(), 0.1f);
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
        mViewModel.search().observe(this, mSourceSearch::accept);
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
        getIjk().setPlayer(mPlayers.getPlayer());
        setPlainTextIfChanged(mBinding.control.player, mPlayers.getPlayerText());
        mBinding.control.speed.setEnabled(mPlayers.canAdjustSpeed());
        setVisibilityIfChanged(getExo(), mPlayers.isExo() ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(getIjk(), mPlayers.isIjk() ? View.VISIBLE : View.GONE);
        float speed = mHistory == null ? Setting.getPlaySpeed() : mHistory.getSpeed();
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.setSpeed(speed));
        setDanmuViewSettings();
        
        
    }

    private void loadHistory(Vod item, String detailToken) {
        final String historyKey = getHistoryKey();
        final int siteCid = getSiteCid();
        final String mark = getMark();
        mHistory = HistoryRepository.get().prepare(null, historyKey, siteCid, item, Setting.getPlaySpeed(), mark);
        mHistorySessionModified = false;
        mHistoryTasks.submit(TASK_HISTORY,
                () -> HistoryRepository.get().prepareBestMatch(historyKey, siteCid, item, Setting.getPlaySpeed(), mark),
                HISTORY_LOAD_TIMEOUT_MS,
                history -> App.post(() -> applyHistory(history, item, historyKey, detailToken)),
                error -> HistoryRepository.get().prepare(null, historyKey, siteCid, item, Setting.getPlaySpeed(), mark),
                null);
    }

    private void applyHistory(History history, Vod item, String historyKey, String detailToken) {
        if (isFinishing() || isDestroyed()) return;
        if (!TextUtils.equals(detailToken, pendingDetailToken) || !TextUtils.equals(historyKey, getHistoryKey())) return;
        if (!mHistorySessionModified) mHistory = history;
        applyDetailHistory();
        checkDetailFlags(item);
        checkKeep();
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
        if (mPlaybackCoordinator != null) mPlaybackCoordinator.stop();
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
        mPlayers.clear();
        mPlayers.stop();
        StartPolicy policy = replay ? StartPolicy.FRESH : (isSourceSwitching() || mHistorySession.hasPreservedFlagPosition()
                ? StartPolicy.KEEP_CURRENT_POSITION : StartPolicy.RESUME_SAVED);
        mHistorySession.consumeFlagPosition();
        schedulePlaybackTimeout();
        mPlaybackCoordinator.play(index, policy);
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
        if (!mPlayers.ensureCompatiblePlayer(result.getDrm())) return;
        replacePlayerSession();
        setPlayerView();
        setDecodeView();
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
                App.post(() -> {
                    if (isFinishing() || isDestroyed() || requestId != mDanmakuRequestId) return;
                    mPlayers.prepareDanmu(parser, mDanmakuContext);
                    showDanmu();
                    mPlayers.prepared();
                });
            } catch (Throwable e) {
                ThreadPools.log(e, "Danmaku prepare failed.");
                App.post(() -> {
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
            List<VodEpisode> episodes = new ArrayList<>(items.size());
            String flag = getFlag().getFlag();
            for (Episode item : items) episodes.add(new VodEpisode(VodEpisode.resumeIdentity(getKey(), flag, item.getName()), getKey(), flag, item.getName(), item.getUrl()));
            mPlaybackCoordinator.setEpisodes(episodes);
            String episodeId = mHistory == null ? "" : VodEpisode.resumeIdentity(getKey(), mHistory.getVodFlag(), mHistory.getVodRemarks());
            mPlaybackCoordinator.setResumePosition(episodeId, mHistory == null ? 0 : mHistory.getPosition());
            mPlaybackCoordinator.setOpening(mHistory == null ? 0 : mHistory.getOpening());
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
        String name = isSourceSwitching() ? mSourceSwitch.getEpisode() : (mHistory == null ? "" : mHistory.getVodRemarks());
        Episode episode = flag.find(name, !TextUtils.isEmpty(name));
        if (episode == null && episodePosition >= 0 && episodePosition < flag.getEpisodes().size()) episode = flag.getEpisodes().get(episodePosition);
        if (episode == null) episode = flag.getEpisodes().get(0);
        mPlaybackNavigation.switchEpisode(episode);
        hidePreview();
    }

    public void setEpisodeActivated(Episode item) {
        if (item.isActivated()) {
            if (!isFullscreen()) enterFullscreen();
            return;
        }
        mPlaybackNavigation.switchEpisode(item);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        setR2Callback(100);
    }

    private void setQualityActivated(Result result) {
        try {
            showProgress();
            mPlaybackCoordinator.reload();
            mBinding.danmaku.hide();
        } catch (Exception e) {
            ThreadPools.log(e, "Quality switch failed.");
            showError(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.error_play_timeout) : e.getMessage());
            ErrorEvent.extract(e.getMessage());
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
        for (int i = 0; i < mFlagAdapter.size(); i++) Collections.reverse(((Flag) mFlagAdapter.get(i)).getEpisodes());
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) setEpisodeSelectedPosition(getEpisodePosition());
    }

    private void setArrayAdapter(int size) {
        if (size > 200) setGroupSize(100);
        else if (size > 100) setGroupSize(40);
        else setGroupSize(20);
        boolean revSort = mHistory != null && mHistory.isRevSort();
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
        setVisibilityIfChanged(mBinding.display.clock, Setting.isDisplayTime() || isVisible(mBinding.widget.info)  ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.display.titleLayout, Setting.isDisplayVideoTitle() && !isVisible(mBinding.control.getRoot()) ? View.VISIBLE : View.GONE);
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
        App.removeCallbacks(mR5);
        App.post(mR5, 0);
    }

    private void stopDisplayTrafficPolling() {
        mDisplayTrafficPolling = false;
        App.removeCallbacks(mR5);
    }

    private void setDisplayTraffic() {
        if (!shouldShowDisplaySpeed(hasBottomSheetDialog())) {
            stopDisplayTrafficPolling();
            return;
        }
        Traffic.setSpeed(mBinding.display.netspeed);
        App.post(mR5, Constant.INTERVAL_TRAFFIC);
    }

    @Override
    public boolean onArrayItemTouch() {
        hasKeyEvent = true;
        return false;
    }

    @Override
    public void onRevSort() {
        if (mHistory == null) return;
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        if (mHistory == null) return;
        mHistory.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    private void enterFullscreen() {
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
        Keep keep = KeepRepository.get().find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) KeepRepository.get().delete(keep);
        else createKeep();
        RefreshEvent.keep();
        checkKeep();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        mSourceSearch.recover();
    }

    private void onLoop() {
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

    private void onEpisodes() {
        if (mEpisodeAdapter.size() <= 1) return;
        EpisodeDialog.create().episodes(getFlag().getEpisodes()).show(this);
        hideControl();
    }

    private void checkNext() {
        boolean moved = mHistory != null && mHistory.isRevPlay() ? mPlaybackCoordinator.previous() : mPlaybackCoordinator.next();
        if (!moved) Notify.show(mHistory != null && mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void checkPrev() {
        boolean moved = mHistory != null && mHistory.isRevPlay() ? mPlaybackCoordinator.next() : mPlaybackCoordinator.previous();
        if (!moved) Notify.show(mHistory != null && mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        index = index == array.length - 1 ? 0 : ++index;
        if (mHistory != null) mHistory.setScale(index);
        setScale(index);
    }

    private void onSpeed() {
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.addSpeed());
        if (mHistory != null) mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
    }

    private boolean onSpeedAdd() {
        if (mPlayers.getSpeed() >= 5.0f) return false;
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.addSpeed(0.25f));
        if (mHistory != null) mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        return true;
    }

    private boolean onSpeedSub() {
        if (mPlayers.getSpeed() <= 0.2f) return false;
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.subSpeed(0.25f));
        if (mHistory != null) mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        return true;
    }

    private boolean onSpeedLong() {
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.toggleSpeed());
        if (mHistory != null) mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
        return true;
    }
    private void onRefresh() {
        onReset(false);
    }

    private void capturePlaybackPosition() {
        long position = getCurrentSwitchPosition();
        if (position <= 0) return;
        mPlayers.setPosition(position);
        if (mPlaybackCoordinator != null && mEpisodeAdapter != null && mEpisodeAdapter.size() > 0) {
            Episode episode = getEpisode();
            mPlaybackCoordinator.preservePosition(VodEpisode.resumeIdentity(getKey(), getFlag().getFlag(), episode.getName()), position);
        }
        if (mHistory != null) {
            mHistory.setPosition(position);
            saveHistorySnapshot();
        }
    }

    private void saveHistoryNow() {
        if (mHistory == null || Setting.isIncognito()) return;
        long position = getCurrentSwitchPosition();
        long duration = mPlayers.getDuration();
        if (position <= 0) return;
        mHistory.setPosition(position);
        if (duration > 0) mHistory.setDuration(duration);
        mHistory.setCreateTime(System.currentTimeMillis());
        mLastHistorySaveAt = System.currentTimeMillis();
        saveHistorySnapshot(false);
    }

    private void releaseForCastIfNeeded() {
        if (!isBackground() || isFinishing() || mPlayers.isRelease()) return;
        if (!(App.activity() instanceof CastActivity)) return;
        saveHistoryNow();
        mPlayers.releasePlayer();
    }

    private boolean onSubtitleUpDown() {
        onSubtitleClick();
        return true;
    }

    private void onReset() {
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
        if (mHistory == null) return false;
        if (mHistory.getOpening() >= mPlayers.getDuration() / 2) return false;
        setOpening(Math.min(mHistory.getOpening() + 1000, mPlayers.getDuration() / 2));
        return true;
    }

    private boolean onOpeningSub() {
        if (mHistory == null) return false;
        if (mHistory.getOpening() <= 0) return false;
        setOpening(Math.max(0, mHistory.getOpening() - 1000));
        return true;
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        if (mHistory == null) return;
        mHistory.setOpening(opening);
        setPlainTextIfChanged(mBinding.control.opening, opening == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
    }

    private void onEnding() {
        long ending = PlaybackMarkers.endingAt(mPlayers.getPosition(), mPlayers.getDuration());
        if (ending != PlaybackMarkers.INVALID) setEnding(ending);
    }

    private boolean onEndingAdd() {
        if (mHistory == null) return false;
        if (mHistory.getEnding() >= mPlayers.getDuration() / 2) return false;
        setEnding(Math.min(mPlayers.getDuration() / 2, mHistory.getEnding() + 1000));
        return true;
    }

    private boolean onEndingSub() {
        if (mHistory == null) return false;
        if (mHistory.getEnding() <= 0) return false;
        setEnding(Math.max(0, mHistory.getEnding() - 1000));
        return true;
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        if (mHistory == null) return;
        mHistory.setEnding(ending);
        setPlainTextIfChanged(mBinding.control.ending, ending == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mHistory.getEnding()));
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
        capturePlaybackPosition();
        mPlayers.toggleDecode(save);
        mPlayers.init(getExo(), getIjk());
        replacePlayerSession();
        mPlaybackCoordinator.reload();
        setDecodeView();
    }

    private void replacePlayerSession() {
        if (mPlayerSession != null) mPlayerSession.replace(mPlayers.session());
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
        App.post(mR3, 0);
    }

    private void stopProgressPolling() {
        mProgressTrafficPolling = false;
        App.removeCallbacks(mR3);
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
        App.post(() -> {
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
        App.removeCallbacks(mR1);
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
        App.post(mR3, Constant.INTERVAL_TRAFFIC);
    }

    private void reconcilePlaybackUiState() {
        refreshDanmaku();
        if (mPlayers.isBuffering()) {
            mPlaybackState.onBuffering();
        } else if (mPlayers.isReady()) {
            mPlaybackState.onReady();
        }
    }

    private boolean isBuffering() {
        return mPlayers.isBuffering();
    }

    private void setR1Callback() {
        App.removeCallbacks(mR1);
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback(long delayMillis) {
        App.post(mR2, delayMillis);
    }

    private void setArtwork(String url) {
        if (url == null) url = "";
        if (url.equals(mArtworkUrl)) return;
        mArtworkUrl = url;
        clearArtworkTarget();
        mArtworkTarget = new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                getExo().setDefaultArtwork(resource);
                getIjk().setDefaultArtwork(resource);
                showPreview(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable error) {
                getExo().setDefaultArtwork(error);
                getIjk().setDefaultArtwork(error);
                hidePreview();
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        };
        ImgUtil.load(url, R.drawable.radio, mArtworkTarget);
    }

    private void clearArtworkTarget() {
        if (mArtworkTarget == null) return;
        Glide.with(App.get()).clear(mArtworkTarget);
        mArtworkTarget = null;
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
                if (call != mPartCall) return;
                List<String> items;
                try (Response res = response) {
                    ResponseBody body = res.body();
                    items = body == null ? new ArrayList<>() : new ArrayList<>(Part.get(body.string()));
                }
                Iterator<String> iterator = items.iterator();
                while (iterator.hasNext()) if (iterator.next().equals(keyword)) iterator.remove();
                synchronized (PART_CACHE) {
                    PART_CACHE.put(keyword, new ArrayList<>(items));
                }
                App.post(() -> {
                    if (call != mPartCall || isFinishing() || isDestroyed()) return;
                    mPartCall = null;
                    setPartAdapter(items);
                }, 200);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ThreadPools.log(e, "Part suggestion load failed.");
                List<String> items = Collections.emptyList();
                App.post(() -> {
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

    private void loadRecommendations(Vod item) {
        int generation = ++mRecommendGeneration;
        App.removeCallbacks(mRecommendRequest);
        mRecommendAdapter.clear();
        setVisibilityIfChanged(mBinding.recommendGroup, View.GONE);
        String title = item.getVodName(getName());
        String year = item.getVodYear();
        if (title.isEmpty()) return;
        mRecommendRequest = () -> App.execute(() -> {
            List<Vod> items = DoubanRecommendRepository.get(title, year);
            App.post(() -> {
                if (generation != mRecommendGeneration || isFinishing() || isDestroyed()) return;
                mRecommendAdapter.setItems(items, null);
                setVisibilityIfChanged(mBinding.recommendGroup, items.isEmpty() ? View.GONE : View.VISIBLE);
                updateFocus();
            });
        });
        App.post(mRecommendRequest, 1500);
    }

    private void setPartAdapter(List<String> items) {
        mBinding.part.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mPartAdapter.setItems(items, null);
        setR2Callback(1000);
    }

    private void onSearchPage() {
        String keyword = mHistory == null ? textOf(mBinding.name).trim() : mHistory.getVodName();
        if (TextUtils.isEmpty(keyword)) keyword = getName();
        if (TextUtils.isEmpty(keyword)) return;
        SearchActivity.start(this, keyword, true);
    }

    private void checkKeep() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(KeepRepository.get().find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(getSiteCid());
        keep.setSiteName(getSite().getName());
        keep.setVodPic(mBinding.video.getTag().toString());
        keep.setVodName(textOf(mBinding.name));
        keep.setCreateTime(System.currentTimeMillis());
        KeepRepository.get().save(keep);
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
        App.post(mR1, 200);
        SubtitleView subtitleView = mPlayers.isIjk() ? getIjk().getSubtitleView() : getExo().getSubtitleView();
        String videoName = getName();
        Episode current = mEpisodeAdapter != null && mEpisodeAdapter.size() > 0 ? getEpisode() : null;
        String finalVideoName;
        if (videoName.isEmpty() && current != null) {
            finalVideoName = current.getName();
        } else if (videoName.isEmpty() && getTitle() != null) {
            finalVideoName = getTitle().toString();
        } else {
            finalVideoName = videoName;
        }
        App.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            SubtitleDialog.create().view(subtitleView).listener(subtitle -> {
                setSubtitle(Sub.from(subtitle.getUrl()));
            }).name(finalVideoName).full(isFullscreen()).show(this);
        }, 200);
    }

    @Override
    public void onTimeChanged() {
        onTimeChangeDisplaySpeed();
        if (mHistory == null) return;
        long position, duration;
        position = getCurrentSwitchPosition();
        mHistory.setPosition(position);
        duration = mPlayers.getDuration();
        if (duration > 0) mHistory.setDuration(duration);
        if (position > 0 && !Setting.isIncognito()) {
            long now = System.currentTimeMillis();
            if (now - mLastHistorySaveAt >= 3000) {
                mLastHistorySaveAt = now;
                if (position != mLastSavedHistoryPosition || duration != mLastSavedHistoryDuration) {
                    mHistory.setCreateTime(now);
                    saveHistorySnapshot();
                }
            }
        }

        // 片尾跳过检测
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            mClock.setCallback(null);
            checkNext();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (!isCurrentPlayer() || isBackground()) return;
        if (ActionEvent.PLAY.equals(event.getAction()) || ActionEvent.PAUSE.equals(event.getAction())) {
            onKeyCenter();
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
        if (!isCurrentPlayer() || isBackground()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) mDetail.request();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.DANMAKU) setDanmaku(Danmaku.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) setSubtitle(Sub.from(event.getPath()));
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
                break;
            case BUFFERING:
                mPlaybackState.onBuffering();
                break;
            case READY:
                mPlaybackState.onReady();
                break;
            case ENDED:
                checkEnded();
                break;
            default:
                break;
        }
    }

    private void renderPlayback(VodPlaybackState state) {
        if (!isCurrentPlayer() || isBackground()) return;
        if (mHistory != null) {
            if (state.getPosition() > 0) mHistory.setPosition(state.getPosition());
            if (state.getDuration() > 0) mHistory.setDuration(state.getDuration());
        }
        if (state.getEpisodeIndex() >= 0 && state.getEpisodeIndex() < mEpisodeAdapter.size()) {
            Episode episode = (Episode) mEpisodeAdapter.get(state.getEpisodeIndex());
            mSelection.selectEpisode(state.getEpisodeIndex(), mEpisodeAdapter.size());
            for (int i = 0; i < mFlagAdapter.size(); i++) ((Flag) mFlagAdapter.get(i)).toggle(i == getFlagPosition(), episode);
            notifyItemChanged(getEpisodeView(), mEpisodeAdapter);
            setEpisodeSelectedPosition(state.getEpisodeIndex());
            CharSequence title = getString(R.string.detail_title, mBinding.name.getText(), state.getEpisode().getName());
            setPlainTextIfChanged(mBinding.widget.title, title);
            setPlainTextIfChanged(mBinding.display.title, title);
            if (mHistory != null && !TextUtils.equals(mHistory.getEpisodeUrl(), episode.getUrl())) {
                mHistory.setEpisodeUrl(episode.getUrl());
                mHistory.setVodRemarks(episode.getName());
                mHistory.setVodFlag(getFlag().getFlag());
                mHistory.setPosition(state.getPosition());
                mHistory.setDuration(0);
                mHistory.setCreateTime(System.currentTimeMillis());
                mLastHistorySaveAt = System.currentTimeMillis();
                saveHistorySnapshot();
            }
        }
        onPlaybackPhase(state.getPhase());
    }

    private void checkEnded() {
        if (mBinding.control.loop.isActivated()) {
            onReset(true);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            checkNext();
        }
    }

    private void setTrackVisible(boolean visible) {
        setVisibilityIfChanged(mBinding.control.text, visible ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.volume, visible && mPlayers.isExo() ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.audio, visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.video, visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    private void setSubtitle(Sub subtitle) {
        int player = mPlayers.getPlayer();
        mPlayers.setSub(subtitle);
        if (player != mPlayers.getPlayer()) {
            replacePlayerSession();
            setPlayerView();
            setDecodeView();
        }
    }

    private void setDefaultTrack() {
        if (isInitTrack()) {
            setInitTrack(false);
            mPlayers.prepared();
            mPlayers.setTrack(TrackRepository.get().find(getHistoryKey()));
        }
    }

    private void setMetadata() {
        String title = mHistory == null ? textOf(mBinding.name) : mHistory.getVodName();
        Episode current = mEpisodeAdapter.size() > 0 ? getEpisode() : null;
        String episode = current == null ? title : current.getName();
        String artist = title.equals(episode) ? "" : getString(R.string.play_now, episode);
        String artwork = mHistory == null ? getPic() : mHistory.getVodPic();
        mPlayers.setMetadata(title, artist, artwork);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onErrorEvent(ErrorEvent event) {
        if (!isCurrentPlayer() || isBackground()) return;
        mRecoveryController.handle(event);
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
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setCenterAction(R.drawable.ic_widget_play);
        updateCenterProgress(mPlayers.getPosition(), mPlayers.getDuration());
        if (isFullscreen()) showInfoAndCenter();
        else hideInfoAndCenter();
        mResumeOnForeground = false;
        mPlaybackCoordinator.pause();
    }

    private void onPlay() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mResumeOnForeground = true;
        mPlaybackCoordinator.play();
        hideCenter();
    }

    public boolean isBackground() {
        return background;
    }

    private boolean isCurrentPlayer() {
        return Server.get().getPlayer() == mPlayers;
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
        return mSourceSwitch.isActive();
    }

    private boolean isSourceLoading() {
        return mSourceSwitch.isLoading();
    }

    private void startSourceLoading() {
        if (!isSourceSwitching()) return;
        mSourceSwitch.startLoading();
    }

    private void resumeSourceSearch() {
        if (!isSourceSwitching()) return;
        mSourceSwitch.resumeSearch();
    }

    private void beginSourceSwitch() {
        if (isSourceSwitching()) return;
        mSourceSwitch.search(getCurrentSwitchEpisode(), getCurrentSwitchPosition());
    }

    private void clearSourceSwitch() {
        clearSourceSwitchTimeout();
        mSourceSwitch.clear();
    }

    private void continueSourceSwitch() {
        if (!isSourceSwitching()) return;
        if (mQuickAdapter.size() > 0) {
            mPlaybackNavigation.nextSite();
        } else if (!mSiteSearch.hasPending()) {
            clearSourceSwitch();
        }
    }

    private boolean canAdvancePendingSourceSwitch() {
        return isSourceSwitching() && !isSourceLoading() && mQuickAdapter.size() > 0;
    }

    private String getCurrentSwitchEpisode() {
        if (!TextUtils.isEmpty(getMark())) return getMark();
        if (mEpisodeAdapter != null && mEpisodeAdapter.size() > 0) return getEpisode().getName();
        if (mHistory != null) return mHistory.getVodRemarks();
        return "";
    }

    private String getCurrentSwitchKeyword() {
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodName())) return mHistory.getVodName();
        return mBinding == null ? getName() : textOf(mBinding.name);
    }

    private long getCurrentSwitchPosition() {
        if (mPlaybackCoordinator != null) return mPlaybackCoordinator.currentPosition();
        long position = mPlayers == null ? 0 : mPlayers.getPosition();
        if (position > 0) return position;
        return mHistory == null ? 0 : Math.max(mHistory.getPosition(), 0);
    }

    private void saveHistorySnapshot() {
        saveHistorySnapshot(true);
    }

    private void saveHistorySnapshot(boolean async) {
        if (mHistory == null || Setting.isIncognito()) return;
        mHistorySessionModified = true;
        mLastSavedHistoryPosition = mHistory.getPosition();
        mLastSavedHistoryDuration = mHistory.getDuration();
        if (async) mHistory.saveAsync();
        else mHistory.save();
    }

    private String nextRequestToken(String prefix) {
        return prefix + ":" + (++requestTokenSeed);
    }

    private void setPendingDetailRequest(String token) {
        pendingDetailToken = token;
    }

    private void scheduleSourceSwitchTimeout() {
        clearSourceSwitchTimeout();
        if (!isSourceSwitching()) return;
        App.post(mR6, SOURCE_SWITCH_DETAIL_TIMEOUT_MS);
    }

    private void clearSourceSwitchTimeout() {
        App.removeCallbacks(mR6);
    }

    private void onSourceSwitchTimeout() {
        if (!isSourceLoading()) return;
        resumeSourceSearch();
        continueSourceSwitch();
    }

    private void schedulePlaybackTimeout() {
        App.removeCallbacks(mR7);
        App.post(mR7, Constant.TIMEOUT_PLAY);
    }

    private void clearPlaybackTimeout() {
        App.removeCallbacks(mR7);
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
        mPlaybackNavigation.switchFlag(item, false);
    }

    private void onPlaybackTimeout() {
        if (isBackground()) return;
        ErrorEvent.timeout();
    }

    private boolean isCurrentDetailResult(Result result) {
        return result != null
                && TextUtils.equals(result.getKey(), getKey())
                && TextUtils.equals(result.getRequestId(), getId())
                && TextUtils.equals(result.getRequestToken(), pendingDetailToken);
    }

    private String getSourceSwitchKeyword() {
        String keyword = Objects.toString(mBinding.part.getTag(), "");
        return keyword.isEmpty() ? getCurrentSwitchKeyword() : keyword;
    }

    private void onSearchTasksSettled() {
        if (isSourceLoading() || mSiteSearch.hasPending()) return;
        flushQuickItems();
        if (mSiteSearch.shouldSelectFirst() && mQuickAdapter.size() > 0) {
            mSiteSearch.markSelected();
            mPlaybackNavigation.nextSite();
        } else if (isSourceSwitching() && mQuickAdapter.size() == 0) {
            clearSourceSwitch();
        } else if (!mSiteSearch.isAutomatic()) {
            boolean empty = mQuickAdapter.size() == 0;
            mSourceSearch.stop();
            if (empty) Notify.show(R.string.play_switch_empty);
        }
    }

    private void markCurrentSourceBroken() {
        String brokenKey = getCurrentBrokenKey();
        if (!brokenKey.isEmpty()) mBroken.add(brokenKey);
    }

    private void markCurrentSourceBroken(ErrorEvent event) {
        if (event == null || (!event.isUrl() && !event.isExtract() && !event.isTimeout())) return;
        markCurrentSourceBroken();
    }

    private String getCurrentBrokenKey() {
        return getSourceKey(getKey(), getId(), getCurrentSourceName());
    }

    private String getBrokenKey(Vod item) {
        return getSourceKey(item);
    }

    private String getSourceKey(Vod item) {
        return item == null ? "" : getSourceKey(item.getSiteKey(), item.getVodId(), item.getVodName());
    }

    private String getSourceKey(String siteKey, String vodId, String vodName) {
        if (TextUtils.isEmpty(siteKey)) return "";
        if (!TextUtils.isEmpty(vodId)) return siteKey + "@" + vodId;
        String name = Util.normalize(vodName);
        return TextUtils.isEmpty(name) ? "" : siteKey + "@" + name;
    }

    private String getCurrentSourceName() {
        String name = mBinding == null ? "" : textOf(mBinding.name);
        return TextUtils.isEmpty(name) ? getName() : name;
    }

    private void mergeQuickItems(List<Vod> items, String token) {
        if (!mSiteSearch.isActive() || !TextUtils.equals(token, mSiteSearch.getToken())) return;
        String keyword = getSourceSwitchKeyword();
        boolean changed = false;
        for (Vod item : items) {
            if (mQuickQueue.size() < QUICK_RESULT_LIMIT) {
                mQuickQueue.offer(item);
                changed = true;
            } else if (mQuickQueue.peek() != null && SearchSorter.compare(item, mQuickQueue.peek(), keyword, "") < 0) {
                mQuickQueue.poll();
                mQuickQueue.offer(item);
                changed = true;
            }
        }
        if (changed) scheduleQuickFlush();
        if (mSiteSearch.shouldSelectFirst()) {
            if (!mSiteSearch.hasPending() || mQuickQueue.size() >= 10) {
                flushQuickItems();
                mSiteSearch.markSelected();
                mPlaybackNavigation.nextSite();
            }
        } else if (isSourceSwitching() && !isSourceLoading() && !mQuickQueue.isEmpty()) {
            flushQuickItems();
            if (canAdvancePendingSourceSwitch()) mPlaybackNavigation.nextSite();
        }
    }

    private int compareQuickForQueue(Vod left, Vod right) {
        return SearchSorter.compare(right, left, getSourceSwitchKeyword(), "");
    }

    private void scheduleQuickFlush() {
        if (mQuickFlushScheduled) return;
        mQuickFlushScheduled = true;
        App.post(mQuickFlush, QUICK_FLUSH_DELAY_MS);
    }

    private void flushQuickItems() {
        App.removeCallbacks(mQuickFlush);
        mQuickFlushScheduled = false;
        if (!mSiteSearch.isActive()) return;
        List<Vod> items = new ArrayList<>(mQuickQueue);
        items.sort(SearchSorter.comparator(getSourceSwitchKeyword(), ""));
        mQuickAdapter.setItems(items, QUICK_DIFF);
        setVisibilityIfChanged(mBinding.quick, items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void resetQuickResults() {
        App.removeCallbacks(mQuickFlush);
        mQuickFlushScheduled = false;
        mQuickQueue.clear();
    }

    private Flag findTargetFlag(List<Flag> flags) {
        if (isSourceLoading()) {
            for (Flag flag : flags) if (hasEpisode(flag)) return flag;
            return firstPlayableFlag(flags);
        }
        String target = mHistory != null ? mHistory.getVodFlag() : null;
        for (Flag flag : flags) if (!flag.getEpisodes().isEmpty() && flag.getFlag().equals(target)) return flag;
        return firstPlayableFlag(flags);
    }

    private Flag firstPlayableFlag(List<Flag> flags) {
        for (Flag flag : flags) if (!flag.getEpisodes().isEmpty()) return flag;
        return null;
    }

    private boolean hasEpisode(Flag flag) {
        return flag != null && (flag.getEpisodes().size() == 1 || flag.find(mSourceSwitch.getEpisode(), !TextUtils.isEmpty(mSourceSwitch.getEpisode())) != null);
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
        App.removeCallbacks(mEpisodeInputCommit);
        App.post(mEpisodeInputCommit, 1200);
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
        setCenterAction(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        updateCenterProgress(mSeekGesture.preview(mPlayers.getPosition(), mPlayers.getDuration(), time), mPlayers.getDuration());
        setCenterVisible(true);
        hideProgress();
    }

    @Override
    public void onSeekTo(int time) {
        mPlaybackState.onSeek();
        mPlaybackCoordinator.seekTo(mSeekGesture.commit(mPlayers.getPosition(), mPlayers.getDuration(), time));
        mKeyDown.resetTime();
        onPlay();
    }

    @Override
    public void onSpeedUp() {
        if (!mPlayers.isPlaying() || !mPlayers.canAdjustSpeed()) return;
        if (Float.isNaN(mSpeedBeforeLongPress)) mSpeedBeforeLongPress = mPlayers.getSpeed();
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        setDanmuViewSettings();
        
        
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        setVisibilityIfChanged(mBinding.widget.speed, View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        if (!Float.isNaN(mSpeedBeforeLongPress)) setPlainTextIfChanged(mBinding.control.speed, mPlayers.setSpeed(mSpeedBeforeLongPress));
        mSpeedBeforeLongPress = Float.NaN;
        setDanmuViewSettings();
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
        capturePlaybackPosition();
        mPlayers.setPlayer(item);
        replacePlayerSession();
        setPlayerView();
        setDecodeView();
        mPlaybackCoordinator.reload();
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
        boolean needInit = mPlayers.isRelease()
                || (mPlayers.isExo() && mPlayers.exo() == null)
                || (mPlayers.isIjk() && mPlayers.ijk() == null);
        if (needInit) {
            mPlayers.init(getExo(), getIjk());
            replacePlayerSession();
            setPlayerView();
            setDecodeView();
            mPlaybackCoordinator.reload();
        } else {
            reconcilePlaybackUiState();
            if (mResumeOnForeground) onPlay();
        }
    }

    @Override
    protected void onPause() {
        mKeyDown.release();
        mSeekGesture.reset();
        super.onPause();
        setBackground(true);
        saveHistoryNow();
        mPlayers.pause();
        mClock.stop();
        stopDisplayTrafficPolling();
    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseForCastIfNeeded();
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else if (isFullscreen()) {
            exitFullscreen();
        } else {
            mSourceSearch.stop();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        mRecommendGeneration++;
        App.removeCallbacks(mRecommendRequest);
        App.removeCallbacks(mEpisodeInputCommit);
        mEpisodeInput.setLength(0);
        mKeyDown.release();
        saveHistoryNow();
        mHistoryTasks.close();
        super.onDestroy();
        clearPartRequest();
        mPlaybackState.release();
        mSourceSearch.stop();
        mClock.release();
        if (mPlaybackCoordinator != null) mPlaybackCoordinator.close();
        if (mVodResolver != null) mVodResolver.close();
        mPlayers.release();
        clearArtworkTarget();
        Source.get().stop();
        RefreshEvent.history();
        App.removeCallbacks(mR1, mR2, mR3, mR4, mR5, mR6, mR7, mR8);
    }
}
