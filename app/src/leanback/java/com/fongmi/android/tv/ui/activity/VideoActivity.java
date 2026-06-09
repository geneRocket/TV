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
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
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
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Part;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.IjkUtil;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.player.Source;
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
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class VideoActivity extends BaseActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, TrackDialog.ChooserListener, PlayerDialog.Listener, ArrayPresenter.OnClickListener, Clock.Callback {

    private static final long SEEK_READY_STABLE_MS = 300;
    private static final long SEEK_BOUNCE_WINDOW_MS = 1500;
    private static final long SOURCE_SWITCH_DETAIL_TIMEOUT_MS = 5000;
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

    private static class RecoveryState {

        private int toggleCount;
        private int errorCount;

        public int getToggleCount() {
            return toggleCount;
        }

        public void addToggle() {
            toggleCount++;
        }

        public void resetToggle() {
            toggleCount = 0;
        }

        public int addError() {
            return ++errorCount;
        }

        public void resetError() {
            errorCount = 0;
        }
    }

    private static class ErrorRecoveryController {

        private static final int MAX_ERROR_COUNT = 20;
        private final VideoActivity host;
        private final RecoveryState state = new RecoveryState();

        public ErrorRecoveryController(VideoActivity host) {
            this.host = host;
        }

        public void onPlayerReady() {
            host.mPlayers.reset();
            state.resetToggle();
            state.resetError();
        }

        public void onPlayerError(ErrorEvent event) {
            if (state.addError() > MAX_ERROR_COUNT) {
                stopWithError(event, true);
                return;
            }
            if (host.mPlayers.exceedRetry(event.getRetry())) {
                if (tryRecoverByPlayerSwitch(event)) return;
                state.resetToggle();
                stopWithError(event, false);
                return;
            }
            if (tryRecoverByDecode(event)) return;
            if (tryRecoverByExoFormat(event)) return;
            host.onRefresh();
        }

        private boolean tryRecoverByDecode(ErrorEvent event) {
            if (!event.isDecode() || !host.mPlayers.canToggleDecode()) return false;
            host.onDecode(false);
            return true;
        }

        private boolean tryRecoverByExoFormat(ErrorEvent event) {
            if (!event.isExo() || !host.mPlayers.isExo()) return false;
            host.capturePlaybackPosition();
            if (event.getCode() == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    || event.getCode() >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                    && event.getCode() <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) {
                host.mPlayers.setFormat(ExoUtil.getMimeType(event.getCode()));
            }
            host.mPlayers.setMediaSource();
            return true;
        }

        private boolean tryRecoverByPlayerSwitch(ErrorEvent event) {
            boolean canSwitch = event.isUrl()
                    && event.getRetry() > 0
                    && state.getToggleCount() < 2
                    && host.mPlayers.getPlayer() != Players.SYS;
            if (!canSwitch) return false;
            state.addToggle();
            host.retryWithNextPlayer();
            return true;
        }

        private void stopWithError(ErrorEvent event, boolean terminal) {
            Track.delete(host.getHistoryKey());
            host.showError(event.getMsg());
            host.stopActivePlayback();
            if (terminal) state.resetError();
            else host.advanceRecoveryFlow();
        }
    }

    private static class PlaybackNavigationController {

        private final VideoActivity host;

        public PlaybackNavigationController(VideoActivity host) {
            this.host = host;
        }

        public void switchFlag(Flag item, boolean notify) {
            if (host.mFlagAdapter.size() == 0) return;
            if (item.isActivated()) {
                if (host.isPendingSiteSwitch() || host.isSourceSwitching()) {
                    host.setEpisodeAdapter(item.getEpisodes());
                    host.setQualityVisible(false);
                    host.seamless(item);
                }
                return;
            }
            int index = host.mFlagAdapter.indexOf(item);
            if (index == -1) {
                item.setFlag(((Flag) host.mFlagAdapter.get(0)).getFlag());
                index = host.mFlagAdapter.indexOf(item);
            }
            for (int i = 0; i < host.mFlagAdapter.size(); i++) ((Flag) host.mFlagAdapter.get(i)).setActivated(item);
            host.mSelectedFlagPosition = Math.max(0, index);
            host.mBinding.flag.setSelectedPosition(host.mSelectedFlagPosition);
            host.notifyItemChanged(host.mBinding.flag, host.mFlagAdapter);
            host.setEpisodeAdapter(item.getEpisodes());
            host.setQualityVisible(false);
            if (notify) Notify.show(host.getString(R.string.play_switch_flag, item.getFlag()));
            host.seamless(item);
        }

        public void switchEpisode(Episode item) {
            int flagPosition = host.getFlagPosition();
            if (host.shouldEnterFullscreen(item)) return;
            if (host.isFullscreen()) Notify.show(host.getString(R.string.play_ready, item.getName()));
            for (int i = 0; i < host.mFlagAdapter.size(); i++) ((Flag) host.mFlagAdapter.get(i)).toggle(flagPosition == i, item);
            host.mSelectedEpisodePosition = Math.max(0, host.getFlag().getEpisodes().indexOf(item));
            host.setEpisodeSelectedPosition(host.getEpisodePosition());
            if (host.shouldApplySourceSwitchProgress(host.getFlag(), item)) host.applySourceSwitchProgress(host.getFlag(), item);
            else if (host.shouldApplyFlagSwitchProgress(host.getFlag(), item)) host.applyFlagSwitchProgress(host.getFlag(), item);
            host.clearSourceSwitch();
            host.clearFlagSwitchTarget();
            host.mContent.stopSearch();
            host.notifyItemChanged(host.getEpisodeView(), host.mEpisodeAdapter);
            host.onRefresh();
        }

        public void switchParse(Parse item, boolean notify) {
            VodConfig.get().setParse(item);
            host.mSelectedParsePosition = Math.max(0, host.mParseAdapter.indexOf(item));
            host.notifyItemChanged(host.mBinding.control.parse, host.mParseAdapter);
            if (notify) Notify.show(host.getString(R.string.play_switch_parse, item.getName()));
            if (!host.isSourceSwitching()) host.mContent.stopSearch();
            host.onRefresh();
        }

        public void showDetail(Vod item, boolean notify) {
            if (notify) Notify.show(host.getString(R.string.play_switch_site, item.getSiteName()));
            host.mContent.openDetail(item);
        }

        public void nextParse() {
            int position = host.getParsePosition();
            if (position >= 0 && position < host.mParseAdapter.size() - 1) switchParse((Parse) host.mParseAdapter.get(position + 1), true);
        }

        public void nextFlag() {
            int position = host.isGone(host.mBinding.flag) ? -1 : host.getFlagPosition();
            if (position >= 0 && position < host.mFlagAdapter.size() - 1) switchFlag((Flag) host.mFlagAdapter.get(position + 1), true);
        }

        public void nextSite() {
            if (host.mQuickAdapter.size() == 0) return;
            host.beginSourceSwitch();
            Vod item = (Vod) host.mQuickAdapter.get(0);
            host.mQuickAdapter.removeItems(0, 1);
            host.markCurrentSourceBroken();
            host.setPendingSiteSwitch(true);
            host.setInitAuto(false);
            showDetail(item, true);
        }

        public void playNext() {
            int target = Math.min(host.getEpisodePosition() + 1, host.mEpisodeAdapter.size() - 1);
            switchEpisodeByIndex(target, host.mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
        }

        public void playPrev() {
            int target = Math.max(host.getEpisodePosition() - 1, 0);
            switchEpisodeByIndex(target, host.mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
        }

        private void switchEpisodeByIndex(int index, int errorRes) {
            Episode item = (Episode) host.mEpisodeAdapter.get(index);
            if (item.isActivated()) {
                if (host.isFullscreen()) host.exitFullscreen();
                Notify.show(errorRes);
            } else {
                switchEpisode(item);
            }
        }
    }

    private static class PlaybackStateController {

        private final VideoActivity host;
        private long lastSeekAt;
        private boolean pendingSeek;
        private final Runnable readyCommit = this::commitReady;

        public PlaybackStateController(VideoActivity host) {
            this.host = host;
        }

        public void onPreparing() {
            cancelReadyCommit();
            host.setInitTrack(true);
            host.setTrackVisible(false);
            host.mClock.setCallback(host);
            host.showProgress();
        }

        public void onBuffering() {
            cancelReadyCommit();
            host.showProgress();
        }

        public void onReady() {
            if (!shouldDebounceReady()) {
                commitReady();
                return;
            }
            App.post(readyCommit, SEEK_READY_STABLE_MS);
        }

        public void onSeek() {
            pendingSeek = true;
            lastSeekAt = System.currentTimeMillis();
            cancelReadyCommit();
            host.showProgress();
        }

        public void clear() {
            pendingSeek = false;
            cancelReadyCommit();
        }

        public void release() {
            clear();
        }

        private boolean shouldDebounceReady() {
            return pendingSeek && System.currentTimeMillis() - lastSeekAt < SEEK_BOUNCE_WINDOW_MS;
        }

        private void commitReady() {
            if (host.isBackground() || host.mPlayers.isBuffering()) return;
            pendingSeek = false;
            host.mContent.stopSearch();
            host.clearPlaybackTimeout();
            host.commitPendingHistoryUpdate();
            host.setMetadata();
            host.mErrorRecovery.onPlayerReady();
            host.hideProgress();
            host.mPlayers.reset();
            host.setDefaultTrack();
            host.setTrackVisible(true);
            if (host.mHistory != null) host.mHistory.setPlayer(host.mPlayers.getPlayer());
            String sizeText = host.mPlayers.getSizeText();
            host.setPlainTextIfChanged(host.mBinding.widget.size, sizeText);
            host.setPlainTextIfChanged(host.mBinding.display.size, sizeText);
        }

        private void cancelReadyCommit() {
            App.removeCallbacks(readyCommit);
        }
    }

    private static class ContentController {

        private final VideoActivity host;

        public ContentController(VideoActivity host) {
            this.host = host;
        }

        public void requestDetail() {
            host.cancelDetailPreload();
            String token = host.nextRequestToken("detail");
            host.setPendingDetailRequest(token);
            host.scheduleSourceSwitchTimeout();
            host.mViewModel.detailContentFast(host.getKey(), host.getId(), token);
        }

        public void handleMissingDetail() {
            setEmpty(false);
        }

        public void openDetail(Vod item) {
            prepareDetailRequest(item);
            requestDetail();
        }

        public void setDetail(Result result) {
            if (!host.isCurrentDetailResult(result)) return;
            host.clearSourceSwitchTimeout();
            if (result.getList().isEmpty()) setEmpty(result.hasMsg());
            else bindDetail(result.getList().get(0));
            if (!TextUtils.isEmpty(result.getMsg()) && !host.isSourceSwitching()) Notify.show(result.getMsg());
        }

        public void setSearch(Result result) {
            if (!host.mSearchActive) return;
            if (!host.isCurrentSearchResult(result)) return;
            if (!result.getKeyword().equals(Objects.toString(host.mBinding.part.getTag(), "").trim())) return;
            List<Vod> items = result.getList();
            if (items.isEmpty()) return;
            Iterator<Vod> iterator = items.iterator();
            while (iterator.hasNext()) {
                Vod item = iterator.next();
                if (mismatch(item) || !host.mQuickKeys.add(getQuickKey(item))) iterator.remove();
            }
            if (items.isEmpty()) return;
            host.mergeQuickItems(items, result.getRequestToken());
            host.setVisibilityIfChanged(host.mBinding.quick, View.VISIBLE);
            App.removeCallbacks(host.mR4);
        }

        public void setSearch(Vod item) {
            int index = host.mQuickAdapter.indexOf(item);
            if (index >= 0) host.mQuickAdapter.removeItems(index, 1);
            host.beginSourceSwitch();
            host.markCurrentSourceBroken();
            host.setAutoMode(false);
            host.setInitAuto(false);
            host.setPendingSiteSwitch(true);
            host.mPlaybackNavigation.showDetail(item, false);
        }

        public void advanceSearch(boolean force) {
            if (host.mQuickAdapter.size() == 0) initSearch(host.getSourceSwitchKeyword(), !force, true);
            else if (host.isAutoMode() || force || host.isSourceSwitching()) host.mPlaybackNavigation.nextSite();
        }

        public void stopSearch() {
            String token = host.pendingSearchToken;
            host.mSearchActive = false;
            host.mViewModel.cancelSearch(token);
            host.setPendingSearchToken(null);
            host.resetSearchTaskState();
            host.mQuickKeys.clear();
            host.cancelSearchTasks();
            if (host.mExecutor == null) return;
            host.mExecutor.shutdownNow();
            host.mExecutor = null;
        }

        public void showEmpty() {
            host.clearSourceSwitchTimeout();
            host.mBinding.progressLayout.showEmpty();
            host.clearSourceSwitch();
            stopSearch();
        }

        public void checkFlag(Vod item) {
            boolean empty = item.getVodFlags().isEmpty();
            host.setVisibilityIfChanged(host.mBinding.flag, empty ? View.GONE : View.VISIBLE);
            if (empty) {
                if (host.isPendingSiteSwitch()) {
                    host.setPendingSiteSwitch(false);
                    host.continueSourceSwitch();
                    return;
                }
                ErrorEvent.flag();
            } else {
                Flag target = host.findTargetFlag(item.getVodFlags());
                if (target == null) {
                    host.setPendingSiteSwitch(false);
                    host.continueSourceSwitch();
                    return;
                }
                host.setPendingSiteSwitch(false);
                host.mPlaybackNavigation.switchFlag(target, false);
                if (host.mHistory.isRevSort()) host.reverseEpisode(true);
            }
        }

        public void checkHistory(Vod item) {
            host.mHistory = History.find(host.getHistoryKey());
            host.mHistory = host.mHistory == null ? createHistory(item) : host.mHistory;
            if (!TextUtils.isEmpty(host.getMark())) host.mHistory.setVodRemarks(host.getMark());
            host.mHistory.findEpisode(item.getVodFlags());
            if (Setting.isIncognito() && host.mHistory.getKey().equals(host.getHistoryKey())) host.mHistory.delete();
            host.setPlainTextIfChanged(host.mBinding.control.opening, host.mHistory.getOpening() == 0 ? host.getString(R.string.play_op) : host.mPlayers.stringToTime(host.mHistory.getOpening()));
            host.setPlainTextIfChanged(host.mBinding.control.ending, host.mHistory.getEnding() == 0 ? host.getString(R.string.play_ed) : host.mPlayers.stringToTime(host.mHistory.getEnding()));
            host.mHistory.setVodPic(item.getVodPic());
            host.mPlayers.setPlayer(host.getPlayer());
            host.setScale(host.getScale());
            host.setPlayerView();
            host.setDecodeView();
        }

        private void prepareDetailRequest(Vod item) {
            host.getIntent().putExtra("key", item.getSiteKey());
            host.getIntent().putExtra("pic", item.getVodPic());
            host.getIntent().putExtra("id", item.getVodId());
            host.getIntent().putExtra("name", item.getVodName());
            host.mBinding.scroll.scrollTo(0, 0);
            host.clearPartRequest();
            host.setPartAdapter(Collections.emptyList());
            host.stopActivePlayback();
        }

        private void setEmpty(boolean finish) {
            if (host.isSourceSwitching()) {
                host.setPendingSiteSwitch(false);
                host.continueSourceSwitch();
                return;
            }
            if (host.isFromCollect() || finish) {
                host.finish();
            } else if (host.getName().isEmpty()) {
                showEmpty();
            } else {
                host.mBinding.name.setText(host.getName());
                App.post(host.mR4, 10000);
                advanceSearch(false);
            }
        }

        private void bindDetail(Vod item) {
            host.mBinding.progressLayout.showContent();
            host.mBinding.video.setTag(item.getVodPic(host.getPic()));
            host.mBinding.name.setText(item.getVodName(host.getName()));
            host.setText(host.mBinding.remark, 0, item.getVodRemarks());
            host.setText(host.mBinding.year, R.string.detail_year, item.getVodYear());
            host.setText(host.mBinding.area, R.string.detail_area, item.getVodArea());
            host.setText(host.mBinding.type, R.string.detail_type, item.getTypeName());
            host.setText(host.mBinding.site, R.string.detail_site, host.getSite().getName());
            App.execute(() -> {
                SpannableStringBuilder actor = host.getSpan(R.string.detail_actor, Util.fromHtml(item.getVodActor()).toString());
                SpannableStringBuilder content = host.getSpan(R.string.detail_content, Util.fromHtml(item.getVodContent()).toString());
                SpannableStringBuilder director = host.getSpan(R.string.detail_director, Util.fromHtml(item.getVodDirector()).toString());
                App.post(() -> {
                    if (host.isFinishing() || host.isDestroyed()) return;
                    host.setText(host.mBinding.actor, actor);
                    host.setText(host.mBinding.content, content);
                    host.setText(host.mBinding.director, director);
                });
            });
            if (!host.isSourceSwitching()) host.setSourceSearchActor(item.getVodActor());
            host.mFlagAdapter.setItems(item.getVodFlags(), null);
            host.mBinding.content.setMaxLines(host.getMaxLines());
            if (!host.isSourceSwitching()) host.mBinding.video.requestFocus();
            host.setArtwork(item.getVodPic());
            host.getPart(item.getVodName());
            App.removeCallbacks(host.mR4);
            host.mSelectedFlagPosition = 0;
            host.mSelectedEpisodePosition = 0;
            checkHistory(item);
            checkFlag(item);
            host.checkKeep();
            host.preloadDetailFlags(item);
        }

        private History createHistory(Vod item) {
            History history = new History();
            history.setKey(host.getHistoryKey());
            history.setCid(host.getSiteCid());
            history.setVodName(item.getVodName());
            history.findEpisode(item.getVodFlags());
            history.setSpeed(Setting.getPlaySpeed());
            return history;
        }

        private void initSearch(String keyword, boolean auto, boolean advance) {
            stopSearch();
            host.setAutoMode(auto);
            host.setInitAuto(advance);
            host.setManualSourceSearch(!auto);
            String token = host.nextRequestToken("search");
            host.setPendingSearchToken(token);
            host.mBinding.part.setTag(keyword);
            startSearch(keyword, token);
        }

        private void startSearch(String keyword, String token) {
            host.mQuickAdapter.clear();
            host.mQuickKeys.clear();
            List<Site> sites = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            host.mExecutor = ThreadPools.newFixed("video-search", Constant.THREAD_POOL);
            host.mSearchActive = true;
            for (Site site : VodConfig.get().getSites()) {
                if (!isPass(site)) continue;
                if (!keys.add(site.getKey())) continue;
                sites.add(site);
            }
            int generation = host.beginSearchTaskState(sites.size());
            for (Site site : sites) host.mSearchTasks.add(host.mExecutor.submit(() -> search(site, keyword, generation, token)));
            if (sites.isEmpty()) host.onSearchTasksSettled(generation);
        }

        private void search(Site site, String keyword, int generation, String token) {
            try {
                if (!host.isSearchExecutionActive(generation, token)) return;
                host.mViewModel.searchContent(site, keyword, true, token);
            } catch (Throwable e) {
                ThreadPools.log(e, "Video search failed for " + site.getName());
            } finally {
                host.pruneSearchTasks();
                if (host.onSearchTaskFinished(generation)) App.post(() -> host.onSearchTasksSettled(generation), 100);
            }
        }

        private boolean mismatch(Vod item) {
            String brokenKey = host.getBrokenKey(item);
            if (!brokenKey.isEmpty() && brokenKey.equals(host.getCurrentBrokenKey())) return true;
            if (!brokenKey.isEmpty() && host.mBroken.contains(brokenKey)) return true;
            return !host.matchSourceTitle(item.getVodName(), host.getSourceSwitchKeyword());
        }

        private boolean isPass(Site item) {
            if (host.isAutoMode() && !item.isChangeable()) return false;
            return item.isSearchable();
        }

        private String getQuickKey(Vod item) {
            return host.getSourceKey(item);
        }
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
    private QualityAdapter mQualityAdapter;
    private DanmakuContext mDanmakuContext;
    private ArrayPresenter mArrayPresenter;
    private FlagPresenter mFlagPresenter;
    private PartPresenter mPartPresenter;
    private CustomKeyDownVod mKeyDown;
    private ExecutorService mExecutor;
    private final ExecutorService mDetailExecutor = ThreadPools.newSingle("video-detail");
    private final Set<Future<?>> mSearchTasks = Collections.synchronizedSet(new HashSet<>());
    private SiteViewModel mViewModel;
    private List<Danmaku> mDanmakus;
    private final Set<String> mBroken = new HashSet<>();
    private History mHistory;
    private Players mPlayers;
    private boolean background;
    private boolean fullscreen;
    private boolean initTrack;
    private boolean initAuto;
    private boolean autoMode;
    private boolean mDanmuVisible;
    private boolean useParse;
    private volatile boolean mSearchActive;
    private int mSearchGeneration;
    private int mSearchPendingCount;
    private final Set<String> mQuickKeys = new HashSet<>();
    private final ContentController mContent = new ContentController(this);
    private final ErrorRecoveryController mErrorRecovery = new ErrorRecoveryController(this);
    private final PlaybackNavigationController mPlaybackNavigation = new PlaybackNavigationController(this);
    private final PlaybackStateController mPlaybackState = new PlaybackStateController(this);
    private int groupSize;
    private long mLastHistorySaveAt;
    private long mLastSavedHistoryPosition = -1;
    private long mLastSavedHistoryDuration = -1;
    private boolean mShouldSkipOpening;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mR5;
    private Runnable mR6;
    private Runnable mR7;
    private Runnable mR8;
    private Call mPartCall;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private boolean hasKeyEvent;
    private boolean mResumeOnForeground = true;
    private int mSelectedFlagPosition;
    private int mSelectedEpisodePosition;
    private int mSelectedParsePosition;
    private String mArtworkUrl;
    private long requestTokenSeed;
    private String pendingDetailToken;
    private Future<?> mDetailParseTask;
    private String pendingPlaybackKey;
    private String pendingPlaybackFlag;
    private String pendingPlaybackId;
    private String pendingPlaybackToken;
    private String pendingPlaybackTimeoutToken;
    private String pendingSearchToken;
    private int mEpisodeNumColumns;
    private int mEpisodeColumnWidth;
    private int mArraySize = -1;
    private int mDanmakuRequestId;
    private boolean mArrayRevSort;
    private boolean mArrayRevPlay;
    private boolean mDisplayTrafficPolling;
    private boolean mProgressTrafficPolling;
    private boolean mFocusUpdateScheduled;
    private boolean pendingSiteSwitch;
    private boolean sourceSwitching;
    private boolean manualSourceSearch;
    private boolean sourceSwitchSingleEpisode;
    private String sourceSwitchEpisode;
    private String sourceSwitchKeyword;
    private String sourceSwitchTargetFlag;
    private String sourceSwitchTargetEpisodeKey;
    private String sourceSearchActor;
    private String flagSwitchTargetFlag;
    private String flagSwitchTargetEpisodeKey;
    private long flagSwitchPosition;
    private long sourceSwitchPosition;
    private Flag pendingHistoryFlag;
    private Episode pendingHistoryEpisode;
    private long pendingHistoryPosition;
    private long lastPlaybackAttemptPosition;
    private boolean pendingHistorySkipOpening;
    private String pendingProgressFlag;
    private String pendingProgressEpisodeKey;
    private long pendingProgressPosition;
    private CustomTarget<Drawable> mArtworkTarget;

    public static void push(FragmentActivity activity, String text) {
        if (TextUtils.isEmpty(text)) return;
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(activity, Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> start(activity, "push_agent", "file://" + path, name, true));
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
        activity.startActivityForResult(intent, 1000);
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
        return VodConfig.rawSiteKey(getKey()).concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + getSiteCid();
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
        if (mSelectedFlagPosition >= 0 && mSelectedFlagPosition < mFlagAdapter.size()) return mSelectedFlagPosition;
        for (int i = 0; i < mFlagAdapter.size(); i++) if (((Flag) mFlagAdapter.get(i)).isActivated()) return mSelectedFlagPosition = i;
        return mSelectedFlagPosition = 0;
    }

    private int getEpisodePosition() {
        if (mSelectedEpisodePosition >= 0 && mSelectedEpisodePosition < mEpisodeAdapter.size()) return mSelectedEpisodePosition;
        for (int i = 0; i < mEpisodeAdapter.size(); i++) if (((Episode) mEpisodeAdapter.get(i)).isActivated()) return mSelectedEpisodePosition = i;
        return mSelectedEpisodePosition = 0;
    }

    private int getParsePosition() {
        if (mSelectedParsePosition >= 0 && mSelectedParsePosition < mParseAdapter.size()) return mSelectedParsePosition;
        for (int i = 0; i < mParseAdapter.size(); i++) if (((Parse) mParseAdapter.get(i)).isActivated()) return mSelectedParsePosition = i;
        return mSelectedParsePosition = 0;
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
        getEpisodeView().setSelectedPosition(position);
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
        mPlayers = Players.create(this);
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = mContent::showEmpty;
        mR5 = this::setDisplayTraffic;
        mR6 = this::onSourceSwitchTimeout;
        mR7 = this::onPlaybackTimeout;
        mR8 = this::onFlagSelected;
        setBackground(false);
        setRecyclerView();
        setEpisodeView();
        setVideoView();
        setDisplayView();
        setDanmuView();
        setViewModel();
        checkCast();
        checkId();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
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
                if (mEpisodeAdapter.size() > getGroupSize() && position > 1 && hasKeyEvent) setEpisodeSelectedPosition((position - 2) * getGroupSize());
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
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(new ItemBridgeAdapter(mFlagAdapter = new ArrayObjectAdapter(mFlagPresenter = new FlagPresenter(item -> mPlaybackNavigation.switchFlag(item, false)))));
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this::setQualityActivated));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(new ItemBridgeAdapter(mArrayAdapter = new ArrayObjectAdapter(mArrayPresenter = new ArrayPresenter(this))));
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(new ItemBridgeAdapter(mPartAdapter = new ArrayObjectAdapter(mPartPresenter = new PartPresenter(item -> SearchActivity.start(this, item, true)))));
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(new ItemBridgeAdapter(mQuickAdapter = new ArrayObjectAdapter(new QuickPresenter(item -> mContent.setSearch(item)))));
        mBinding.control.parse.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.control.parse.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.control.parse.setAdapter(new ItemBridgeAdapter(mParseAdapter = new ArrayObjectAdapter(new ParsePresenter(item -> mPlaybackNavigation.switchParse(item, false)))));
        mParseAdapter.setItems(VodConfig.get().getParses(), null);
        mSelectedParsePosition = getParsePosition();
    }

    private void setEpisodeView() {
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
        mViewModel.result.observe(this, mContent::setDetail);
        mViewModel.player.observe(this, this::applyPlayerResult);
        mViewModel.search.observe(this, mContent::setSearch);
    }

    private void checkCast() {
        if (isCast()) onVideo();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", "push_agent").putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) mContent.handleMissingDetail();
        else mContent.requestDetail();
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

    private void setDecodeView() {
        setPlainTextIfChanged(mBinding.control.decode, mPlayers.getDecodeText());
    }

    private void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        setPlainTextIfChanged(mBinding.control.scale, ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void stopActivePlayback() {
        clearPendingPlaybackRequest();
        clearPlaybackTimeout();
        mPlaybackState.clear();
        stopProgressPolling();
        mClock.setCallback(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPlayers.reset();
        mPlayers.stop();
        clearDanmakuView();
    }

    private void requestPlayback(Flag flag, Episode episode, boolean replay) {
        clearPlaybackTimeout();
        mPlaybackState.clear();
        CharSequence title = getString(R.string.detail_title, mBinding.name.getText(), episode.getName());
        if (!TextUtils.equals(mBinding.widget.title.getText(), title)) mBinding.widget.title.setText(title);
        if (!TextUtils.equals(mBinding.display.title.getText(), title)) mBinding.display.title.setText(title);
        String token = nextRequestToken("play");
        setPendingPlaybackRequest(getKey(), flag.getFlag(), episode.getUrl(), token);
        schedulePlaybackTimeout(token);
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl(), token);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prepareHistoryUpdate(flag, episode, replay);
        mPlayers.clear();
        mPlayers.stop();
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

    private void applyPlayerResult(Result result) {
        if (!isCurrentPlayerResult(result)) return;
        String token = pendingPlaybackToken;
        result.getUrl().set(mQualityAdapter.getPosition());
        setUseParse(VodConfig.hasParse() && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(result.getFlag())) || result.getJx() == 1));
        mPlayers.start(result, isUseParse(), getSite().getTimeout());
        if (!TextUtils.equals(token, pendingPlaybackToken)) return;
        setVisibilityIfChanged(mBinding.control.parse, isUseParse() ? View.VISIBLE : View.GONE);
        setQualityVisible(result.getUrl().isMulti());
        setDanmakus(result.getDanmakus());
        mQualityAdapter.addAll(result);
    }

    private void checkDanmu(String danmu) {
        setDanmakus(Danmaku.arrayFrom(danmu));
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
        mBinding.danmaku.release();
        mBinding.danmaku.setVisibility(View.GONE);
        ++mDanmakuRequestId;
    }

    private void prepareDanmaku(Danmaku item) {
        final int requestId = ++mDanmakuRequestId;
        syncDanmuVisible();
        if (!Setting.isDanmuLoad() || !mDanmuVisible) {
            mBinding.danmaku.release();
            mBinding.danmaku.setVisibility(View.GONE);
            showDanmu();
            mPlayers.prepared();
            return;
        }
        boolean hasSource = item != null && !item.isEmpty();
        setVisibilityIfChanged(mBinding.control.danmu, View.VISIBLE);
        mBinding.danmaku.setVisibility(hasSource ? View.VISIBLE : View.GONE);
        if (!hasSource) {
            mBinding.danmaku.release();
            return;
        }
        mBinding.danmaku.release();
        App.execute(() -> {
            try {
                Parser parser = new Parser(item.getUrl());
                App.post(() -> {
                    if (isFinishing() || isDestroyed() || requestId != mDanmakuRequestId) return;
                    mBinding.danmaku.prepare(parser, mDanmakuContext);
                    showDanmu();
                    mPlayers.prepared();
                });
            } catch (Throwable e) {
                ThreadPools.log(e, "Danmaku prepare failed.");
                App.post(() -> {
                    if (isFinishing() || isDestroyed() || requestId != mDanmakuRequestId) return;
                    mBinding.danmaku.release();
                    mBinding.danmaku.setVisibility(View.GONE);
                });
            }
        });
    }

    private void refreshDanmaku() {
        syncDanmuVisible();
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
        mSelectedEpisodePosition = findActivatedEpisodePosition(items);
        setArrayAdapter(items.size());
        setR2Callback(50);
    }

    private int findActivatedEpisodePosition(List<Episode> items) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).isActivated()) return i;
        return 0;
    }

    private void preloadDetailFlags(Vod item) {
        cancelDetailPreload();
        if (item == null || item.getVodFlags().isEmpty()) return;
        String key = getKey();
        String id = getId();
        String token = pendingDetailToken;
        boolean revSort = mHistory != null && mHistory.isRevSort();
        List<Flag> snapshot = copyFlags(item.getVodFlags());
        if (snapshot.isEmpty()) return;
        mDetailParseTask = mDetailExecutor.submit(() -> {
            try {
                Source.get().parse(snapshot);
                if (Thread.currentThread().isInterrupted()) return;
                App.post(() -> applyParsedDetailFlags(key, id, token, snapshot, revSort));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable e) {
                ThreadPools.log(e, "Detail preload failed.");
            } finally {
                mDetailParseTask = null;
            }
        });
    }

    private List<Flag> copyFlags(List<Flag> flags) {
        List<Flag> copies = new ArrayList<>();
        for (Flag source : flags) {
            Flag target = Flag.create(source.getFlag());
            for (Episode episode : source.getEpisodes()) target.getEpisodes().add(Episode.create(episode.getName(), episode.getDesc(), episode.getUrl()));
            copies.add(target);
        }
        return copies;
    }

    private void applyParsedDetailFlags(String key, String id, String token, List<Flag> flags, boolean revSortSnapshot) {
        if (!TextUtils.equals(key, getKey())
                || !TextUtils.equals(id, getId())
                || !TextUtils.equals(token, pendingDetailToken)
                || flags.isEmpty()) return;
        if ((mHistory != null && mHistory.isRevSort()) != revSortSnapshot) {
            for (Flag flag : flags) Collections.reverse(flag.getEpisodes());
        }
        String currentFlag = getCurrentSwitchFlag();
        String currentEpisode = getCurrentSwitchEpisode();
        Flag target = findFlag(flags, currentFlag);
        if (target == null) target = findTargetFlag(flags);
        if (target == null) return;
        Episode episode = target.find(currentEpisode, !TextUtils.isEmpty(currentEpisode));
        if (episode != null) target.toggle(true, episode);
        for (Flag flag : flags) flag.setActivated(target);
        mFlagAdapter.setItems(flags, null);
        mSelectedFlagPosition = Math.max(0, flags.indexOf(target));
        mBinding.flag.setSelectedPosition(mSelectedFlagPosition);
        setEpisodeAdapter(target.getEpisodes());
        if (!target.getEpisodes().isEmpty()) setEpisodeSelectedPosition(getEpisodePosition());
    }

    private Flag findFlag(List<Flag> flags, String name) {
        for (Flag flag : flags) if (TextUtils.equals(flag.getFlag(), name)) return flag;
        return null;
    }

    private void cancelDetailPreload() {
        if (mDetailParseTask == null) return;
        mDetailParseTask.cancel(true);
        mDetailParseTask = null;
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
        int height = rowNum > 6 ? ResUtil.dp2px(300) : ResUtil.dp2px(rowNum * 44);
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

    private void seamless(Flag flag) {
        String episodeName = isSourceSwitching() ? sourceSwitchEpisode : mHistory.getVodRemarks();
        Episode episode = isSourceSwitchSingleEpisode()
                ? getDefaultSourceSwitchEpisode(flag)
                : flag.find(episodeName, !TextUtils.isEmpty(episodeName));
        setQualityVisible(episode != null && episode.isActivated() && mQualityAdapter.getItemCount() > 1);
        if (episode == null) return;
        if (episode.isActivated()) {
            applySourceSwitchProgress(flag, episode);
            clearSourceSwitch();
            hidePreview();
            onRefresh();
            return;
        }
        if (Setting.getFlag() == 1) {
            if (isSourceSwitching()) setSourceSwitchTarget(flag, episode);
            else setFlagSwitchTarget(flag, episode);
            episode.setActivated(true);
            mSelectedEpisodePosition = Math.max(0, flag.getEpisodes().indexOf(episode));
            if (!isFullscreen()) getEpisodeView().requestFocus();
            setEpisodeSelectedPosition(mSelectedEpisodePosition);
            episode.setActivated(false);
        } else {
            applySwitchProgress(flag, episode);
            mHistory.setVodRemarks(episode.getName());
            mPlaybackNavigation.switchEpisode(episode);
            hidePreview();
            clearSourceSwitch();
        }
    }

    public void setEpisodeActivated(Episode item) {
        mPlaybackNavigation.switchEpisode(item);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        setR2Callback(100);
    }

    private void setQualityActivated(Result result) {
        try {
            showProgress();
            mPlayers.start(result, isUseParse(), getSite().getTimeout());
            mBinding.danmaku.hide();
        } catch (Exception e) {
            ThreadPools.log(e, "Quality switch failed.");
            showError(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.error_play_timeout) : e.getMessage());
            ErrorEvent.extract(e.getMessage());
        }
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
        boolean revSort = mHistory.isRevSort();
        boolean revPlay = mHistory.isRevPlay();
        if (mArraySize == size && mArrayRevSort == revSort && mArrayRevPlay == revPlay) return;
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        setVisibilityIfChanged(mBinding.array, size > 1 ? View.VISIBLE : View.GONE);
        if (revSort) for (int i = size; i > 0; i -= getGroupSize()) items.add(i + "-" + Math.max(i - (getGroupSize() - 1), 1));
        else for (int i = 0; i < size; i += getGroupSize()) items.add((i + 1) + "-" + Math.min(i + getGroupSize(), size));
        mArrayAdapter.setItems(items, null);
        mArraySize = size;
        mArrayRevSort = revSort;
        mArrayRevPlay = revPlay;
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episodeHori, R.id.array, R.id.episodeVert, R.id.part, R.id.quick);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episodeHori, R.id.array, R.id.episodeVert, R.id.part, R.id.quick);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        mFocusUpdateScheduled = false;
        hasKeyEvent = false;
        mEpisodePresenter.setNextFocusDown(findFocusDown(Setting.getEpisode() == 0 ? 2 : 4));
        mEpisodePresenter.setNextFocusUp(findFocusUp(Setting.getEpisode() == 0 ? 2 : 4));
        mQualityAdapter.setNextFocusDown(findFocusDown(1));
        mArrayPresenter.setNextFocusDown(findFocusDown(3));
        mFlagPresenter.setNextFocusDown(findFocusDown(0));
        mArrayPresenter.setNextFocusUp(findFocusUp(3));
        mPartPresenter.setNextFocusUp(findFocusUp(5));
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        notifyItemChanged(mBinding.quality, mQualityAdapter);
        notifyItemChanged(mBinding.array, mArrayAdapter);
        notifyItemChanged(getEpisodeView(), mEpisodeAdapter);
        notifyItemChanged(mBinding.part, mPartAdapter);
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
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        mHistory.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isActivated();
        if (enter) enterFullscreen();
        return enter;
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
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        RefreshEvent.keep();
        checkKeep();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        mContent.advanceSearch(true);
    }

    private void onLoop() {
        mBinding.control.loop.setActivated(!mBinding.control.loop.isActivated());
    }

    private void onDanmu() {
        Setting.putDanmu(!Setting.isDanmu());
        refreshDanmaku();
        mPlayers.prepared();
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
        EpisodeDialog.create().episodes(getFlag().getEpisodes()).show(this);
        hideControl();
    }

    private void checkNext() {
        if (mHistory.isRevPlay()) mPlaybackNavigation.playPrev();
        else mPlaybackNavigation.playNext();
    }

    private void checkPrev() {
        if (mHistory.isRevPlay()) mPlaybackNavigation.playNext();
        else mPlaybackNavigation.playPrev();
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        mHistory.setScale(index = index == array.length - 1 ? 0 : ++index);
        setScale(index);
    }

    private void onSpeed() {
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.addSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
    }

    private boolean onSpeedAdd() {
        if (mPlayers.getSpeed() >= 5.0f) return false;
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.addSpeed(0.25f));
        mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        return true;
    }

    private boolean onSpeedSub() {
        if (mPlayers.getSpeed() <= 0.2f) return false;
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.subSpeed(0.25f));
        mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        return true;
    }

    private boolean onSpeedLong() {
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.toggleSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
        return true;
    }
    private void onRefresh() {
        onReset(false);
    }

    private void capturePlaybackPosition() {
        long position = Math.max(mPlayers.getPosition(), 0);
        mPlayers.setPosition(position);
        if (mHistory != null) mHistory.setPosition(position);
    }

    private void saveHistoryNow() {
        if (mHistory == null) return;
        long position = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        mHistory.setPosition(position);
        mHistory.setDuration(duration);
        if (position >= 0 && duration > 0 && !Setting.isIncognito()) {
            mLastHistorySaveAt = System.currentTimeMillis();
            saveHistorySnapshot();
        }
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
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || current > duration / 2) return;
        setOpening(current);
    }

    private boolean onOpeningAdd() {
        if (mHistory.getOpening() >= mPlayers.getDuration() / 2) return false;
        setOpening(Math.min(mHistory.getOpening() + 1000, mPlayers.getDuration() / 2));
        return true;
    }

    private boolean onOpeningSub() {
        if (mHistory.getOpening() <= 0) return false;
        setOpening(Math.max(0, mHistory.getOpening() - 1000));
        return true;
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        setPlainTextIfChanged(mBinding.control.opening, opening == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
    }

    private void onEnding() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || current < duration / 2) return;
        setEnding(duration - current);
    }

    private boolean onEndingAdd() {
        if (mHistory.getEnding() >= mPlayers.getDuration() / 2) return false;
        setEnding(Math.min(mPlayers.getDuration() / 2, mHistory.getEnding() + 1000));
        return true;
    }

    private boolean onEndingSub() {
        if (mHistory.getEnding() <= 0) return false;
        setEnding(Math.max(0, mHistory.getEnding() - 1000));
        return true;
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
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
        mPlayers.setMediaSource();
        setDecodeView();
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
        setVisibilityIfChanged(mBinding.control.episodes, Setting.getFullscreenMenuKey() == 0 ? View.VISIBLE : View.GONE);
        view.requestFocus();
        setControlNextFocus();
        if (changed) showDisplayInfo();
        setR1Callback();
    }

    private void hideControl() {
        hideControl(true);
    }

    private void hideControl(boolean hideInfo) {
        cancelPendingSourceSwitchTarget();
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
        if (mFocusUpdateScheduled) App.removeCallbacks(mR2);
        mFocusUpdateScheduled = true;
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

    private void prepareHistoryUpdate(Flag flag, Episode item, boolean replay) {
        boolean hasProgressOverride = hasPendingProgressOverride(flag, item);
        boolean sameEpisode = hasProgressOverride || isSameHistoryEpisode(item);
        replay = replay || !sameEpisode;
        pendingHistoryFlag = flag;
        pendingHistoryEpisode = item;
        pendingHistoryPosition = hasProgressOverride ? pendingProgressPosition : replay ? 0 : mHistory.getPosition();
        lastPlaybackAttemptPosition = pendingHistoryPosition;
        pendingHistorySkipOpening = replay;
        mPlayers.setPosition(Math.max(mHistory.getOpening(), pendingHistoryPosition));
    }

    private void commitPendingHistoryUpdate() {
        if (pendingHistoryFlag == null || pendingHistoryEpisode == null || mHistory == null) return;
        mHistory.setPosition(pendingHistoryPosition);
        mShouldSkipOpening = pendingHistorySkipOpening;
        mHistory.setEpisodeUrl(pendingHistoryEpisode.getUrl());
        mHistory.setVodRemarks(pendingHistoryEpisode.getName());
        mHistory.setVodFlag(pendingHistoryFlag.getFlag());
        mHistory.setCreateTime(System.currentTimeMillis());
        mLastHistorySaveAt = System.currentTimeMillis();
        saveHistorySnapshot();
        lastPlaybackAttemptPosition = 0;
        clearPendingHistoryUpdate();
    }

    private void clearPendingHistoryUpdate() {
        pendingHistoryFlag = null;
        pendingHistoryEpisode = null;
        pendingHistoryPosition = 0;
        pendingHistorySkipOpening = false;
        pendingProgressFlag = null;
        pendingProgressEpisodeKey = null;
        pendingProgressPosition = 0;
    }

    private boolean hasPendingProgressOverride(Flag flag, Episode episode) {
        return flag != null
                && episode != null
                && TextUtils.equals(pendingProgressFlag, flag.getFlag())
                && TextUtils.equals(pendingProgressEpisodeKey, getSourceSwitchEpisodeKey(episode));
    }

    private boolean isSameHistoryEpisode(Episode item) {
        if (item == null || mHistory == null) return false;
        if (item.equals(mHistory.getEpisode())) return true;
        return item.getName() != null && item.getName().equalsIgnoreCase(mHistory.getVodRemarks());
    }

    private void checkKeep() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(getSiteCid());
        keep.setSiteName(getSite().getName());
        keep.setVodPic(mBinding.video.getTag().toString());
        keep.setVodName(textOf(mBinding.name));
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    @Override
    public void showChooser(TrackDialog dialog) {
        FileChooserDialog.create().player(mPlayers).trackDialog(dialog).show(this);
    }

    @Override
    public void onTrackClick(Track item) {
        item.setKey(getHistoryKey());
        item.save();
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
                int oldPlayer = mPlayers.getPlayer();
                mPlayers.setSub(Sub.from(subtitle.getUrl()));
                if (oldPlayer != mPlayers.getPlayer()) {
                    setPlayerView();
                    setDecodeView();
                }
            }).name(finalVideoName).full(isFullscreen()).show(this);
        }, 200);
    }

    @Override
    public void onTimeChanged() {
        onTimeChangeDisplaySpeed();
        if (hasPendingHistoryUpdate()) return;
        long position, duration;
        mHistory.setPosition(position = mPlayers.getPosition());
        mHistory.setDuration(duration = mPlayers.getDuration());
        if (position >= 0 && duration > 0 && !Setting.isIncognito()) {
            long now = System.currentTimeMillis();
            if (now - mLastHistorySaveAt >= 3000) {
                mLastHistorySaveAt = now;
                if (position != mLastSavedHistoryPosition || duration != mLastSavedHistoryDuration) {
                    saveHistorySnapshot();
                }
            }
        }

        // 片头跳过检测
        if (mShouldSkipOpening) {
            long opening = mHistory.getOpening();
            if (opening <= 0 || position >= opening) {
                mShouldSkipOpening = false;
            } else if (position > 0 && position < opening) {
                mPlayers.seekTo(opening);
                mShouldSkipOpening = false;
            }
        }
        
        // 片尾跳过检测
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            mClock.setCallback(null);
            checkNext();
        }
    }

    private boolean hasPendingHistoryUpdate() {
        return pendingHistoryEpisode != null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (isBackground()) return;
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
        if (isBackground()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) mContent.requestDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.DANMAKU) setDanmaku(Danmaku.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) mPlayers.setSub(Sub.from(event.getPath()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerEvent(PlayerEvent event) {
        if (isBackground()) return;
        switch (event.getState()) {
            case 0:
                setPlayerView();
                mPlaybackState.onPreparing();
                break;
            case Player.STATE_IDLE:
                break;
            case Player.STATE_BUFFERING:
                mPlaybackState.onBuffering();
                break;
            case Player.STATE_READY:
                mPlaybackState.onReady();
                break;
            case Player.STATE_ENDED:
                checkEnded();
                break;
        }
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

    private void setDefaultTrack() {
        if (isInitTrack()) {
            setInitTrack(false);
            mPlayers.prepared();
            mPlayers.setTrack(Track.find(getHistoryKey()));
        }
    }

    private void setMetadata() {
        String title = mHistory == null ? textOf(mBinding.name) : mHistory.getVodName();
        Episode current = mEpisodeAdapter.size() > 0 ? getEpisode() : null;
        String episode = current == null ? title : current.getName();
        String artist = title.equals(episode) ? "" : getString(R.string.play_now, episode);
        String artwork = mHistory == null ? getPic() : mHistory.getVodPic();
        mPlayers.setMetadata(title, artist, artwork, getDefaultArtwork());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onErrorEvent(ErrorEvent event) {
        if (isBackground()) return;
        mErrorRecovery.onPlayerError(event);
    }

    private void retryWithNextPlayer() {
        capturePlaybackPosition();
        mPlayers.nextPlayer();
        setPlayerView();
        setDecodeView();
        onRefresh();
    }

    private void advanceRecoveryFlow() {
        if (!Setting.isChange() || !getSite().isChangeable()) return;
        if (isUseParse()) advanceParse();
        else advanceFlag();
    }

    private void advanceParse() {
        int position = getParsePosition();
        boolean last = position == mParseAdapter.size() - 1;
        if (last) initParse();
        if (last) advanceFlag();
        else mPlaybackNavigation.nextParse();
    }

    private void initParse() {
        if (mParseAdapter.size() == 0) return;
        VodConfig.get().setParse((Parse) mParseAdapter.get(0));
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
    }

    private void advanceFlag() {
        int position = getFlagPosition();
        if (position == mFlagAdapter.size() - 1) mContent.advanceSearch(false);
        else mPlaybackNavigation.nextFlag();
    }

    private void onPaused() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setCenterAction(R.drawable.ic_widget_play);
        updateCenterProgress(mPlayers.getPosition(), mPlayers.getDuration());
        if (isFullscreen()) showInfoAndCenter();
        else hideInfoAndCenter();
        mResumeOnForeground = false;
        mPlayers.pause();
    }

    private void onPlay() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mResumeOnForeground = true;
        mPlayers.play();
        hideCenter();
    }

    public boolean isBackground() {
        return background;
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

    private boolean isInitAuto() {
        return initAuto;
    }

    private void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    private boolean isAutoMode() {
        return autoMode;
    }

    public void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    private boolean isManualSourceSearch() {
        return manualSourceSearch;
    }

    private void setManualSourceSearch(boolean manualSourceSearch) {
        this.manualSourceSearch = manualSourceSearch;
    }

    private boolean isSourceSwitching() {
        return sourceSwitching;
    }

    private boolean isSourceSwitchSingleEpisode() {
        return sourceSwitchSingleEpisode;
    }

    private boolean isPendingSiteSwitch() {
        return pendingSiteSwitch;
    }

    private void setPendingSiteSwitch(boolean pendingSiteSwitch) {
        this.pendingSiteSwitch = pendingSiteSwitch;
    }

    private void beginSourceSwitch() {
        if (isSourceSwitching()) return;
        sourceSwitching = true;
        mBroken.clear();
        sourceSwitchSingleEpisode = isCurrentSingleEpisode();
        sourceSwitchEpisode = getCurrentSwitchEpisode();
        sourceSwitchKeyword = getCurrentSwitchKeyword();
        if (!isAutoMode() || TextUtils.isEmpty(sourceSearchActor)) setSourceSearchActor(getCurrentSwitchActor());
        setSourceSwitchTarget(null, null);
        sourceSwitchPosition = getCurrentSwitchPosition();
    }

    private void clearSourceSwitch() {
        clearSourceSwitchTimeout();
        setPendingSiteSwitch(false);
        sourceSwitching = false;
        manualSourceSearch = false;
        sourceSwitchSingleEpisode = false;
        sourceSwitchEpisode = null;
        sourceSwitchKeyword = null;
        setSourceSwitchTarget(null, null);
        sourceSwitchPosition = 0;
    }

    private void continueSourceSwitch() {
        if (!isSourceSwitching()) return;
        if (mQuickAdapter.size() > 0) {
            mPlaybackNavigation.nextSite();
        } else if (!hasPendingSearchTasks()) {
            clearSourceSwitch();
        }
    }

    private boolean canAdvancePendingSourceSwitch() {
        return isSourceSwitching() && !isPendingSiteSwitch() && mQuickAdapter.size() > 0;
    }

    private String getCurrentSwitchFlag() {
        if (mFlagAdapter != null && mFlagAdapter.size() > 0) return getFlag().getFlag();
        if (mHistory != null) return mHistory.getVodFlag();
        return "";
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

    private String getCurrentSwitchActor() {
        if (mBinding == null || mBinding.actor.getTag() == null) return "";
        return mBinding.actor.getTag().toString();
    }

    private long getCurrentSwitchPosition() {
        long position = mPlayers == null ? 0 : mPlayers.getPosition();
        if (position > 0) return position;
        if (pendingHistoryEpisode != null && pendingHistoryPosition > 0) return pendingHistoryPosition;
        if (lastPlaybackAttemptPosition > 0) return lastPlaybackAttemptPosition;
        return mHistory == null ? 0 : Math.max(mHistory.getPosition(), 0);
    }

    private void saveHistorySnapshot() {
        if (mHistory == null || Setting.isIncognito()) return;
        mLastSavedHistoryPosition = mHistory.getPosition();
        mLastSavedHistoryDuration = mHistory.getDuration();
        History snapshot = mHistory.copy();
        App.execute(snapshot::update);
    }

    private boolean isCurrentSingleEpisode() {
        return mEpisodeAdapter != null && mEpisodeAdapter.size() == 1;
    }

    private String nextRequestToken(String prefix) {
        return prefix + ":" + (++requestTokenSeed);
    }

    private void setPendingDetailRequest(String token) {
        pendingDetailToken = token;
    }

    private void setPendingPlaybackRequest(String key, String flag, String id, String token) {
        pendingPlaybackKey = key;
        pendingPlaybackFlag = flag;
        pendingPlaybackId = id;
        pendingPlaybackToken = token;
    }

    private void clearPendingPlaybackRequest() {
        pendingPlaybackKey = null;
        pendingPlaybackFlag = null;
        pendingPlaybackId = null;
        pendingPlaybackToken = null;
        clearPendingHistoryUpdate();
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
        if (!isSourceSwitching() || !isPendingSiteSwitch()) return;
        setPendingSiteSwitch(false);
        continueSourceSwitch();
    }

    private void schedulePlaybackTimeout(String token) {
        pendingPlaybackTimeoutToken = token;
        App.removeCallbacks(mR7);
        App.post(mR7, Constant.TIMEOUT_PLAY);
    }

    private void clearPlaybackTimeout() {
        pendingPlaybackTimeoutToken = null;
        App.removeCallbacks(mR7);
    }

    private void onFlagSelected() {
        int position = mBinding.flag.getSelectedPosition();
        if (position < 0 || position >= mFlagAdapter.size()) return;
        mPlaybackNavigation.switchFlag((Flag) mFlagAdapter.get(position), false);
    }

    private void onPlaybackTimeout() {
        String token = pendingPlaybackTimeoutToken;
        if (TextUtils.isEmpty(token) || !TextUtils.equals(token, pendingPlaybackToken) || isBackground()) return;
        boolean recoverable = isSourceSwitching() || isAutoMode();
        stopActivePlayback();
        showError(getString(R.string.error_play_timeout));
        if (recoverable) advanceRecoveryFlow();
    }

    private void setPendingSearchToken(String token) {
        pendingSearchToken = token;
    }

    private synchronized boolean isSearchExecutionActive(int generation, String token) {
        return mSearchActive
                && generation == mSearchGeneration
                && TextUtils.equals(token, pendingSearchToken)
                && !Thread.currentThread().isInterrupted();
    }

    private boolean isCurrentDetailResult(Result result) {
        return result != null
                && TextUtils.equals(result.getKey(), getKey())
                && TextUtils.equals(result.getRequestId(), getId())
                && TextUtils.equals(result.getRequestToken(), pendingDetailToken);
    }

    private boolean isCurrentPlayerResult(Result result) {
        return result != null
                && TextUtils.equals(result.getKey(), pendingPlaybackKey)
                && TextUtils.equals(result.getRequestFlag(), pendingPlaybackFlag)
                && TextUtils.equals(result.getRequestId(), pendingPlaybackId)
                && TextUtils.equals(result.getRequestToken(), pendingPlaybackToken);
    }

    private boolean isCurrentSearchResult(Result result) {
        return result != null && TextUtils.equals(result.getRequestToken(), pendingSearchToken);
    }

    private String getSourceSwitchKeyword() {
        String keyword = Objects.toString(mBinding.part.getTag(), "");
        return keyword.isEmpty() ? getCurrentSwitchKeyword() : keyword;
    }

    private synchronized int beginSearchTaskState(int count) {
        mSearchGeneration++;
        mSearchPendingCount = Math.max(count, 0);
        return mSearchGeneration;
    }

    private synchronized void resetSearchTaskState() {
        mSearchGeneration++;
        mSearchPendingCount = 0;
    }

    private synchronized boolean onSearchTaskFinished(int generation) {
        if (generation != mSearchGeneration) return false;
        if (mSearchPendingCount > 0) mSearchPendingCount--;
        return mSearchPendingCount == 0;
    }

    private synchronized boolean hasPendingSearchTasks() {
        return mSearchPendingCount > 0;
    }

    private void cancelSearchTasks() {
        synchronized (mSearchTasks) {
            for (Future<?> task : mSearchTasks) task.cancel(true);
            mSearchTasks.clear();
        }
    }

    private void pruneSearchTasks() {
        synchronized (mSearchTasks) {
            Iterator<Future<?>> iterator = mSearchTasks.iterator();
            while (iterator.hasNext()) {
                Future<?> task = iterator.next();
                if (task.isDone() || task.isCancelled()) iterator.remove();
            }
        }
    }

    private void onSearchTasksSettled(int generation) {
        if (generation != mSearchGeneration || isPendingSiteSwitch() || hasPendingSearchTasks()) return;
        if (isInitAuto() && mQuickAdapter.size() > 0) {
            setInitAuto(false);
            mPlaybackNavigation.nextSite();
        } else if (isSourceSwitching() && mQuickAdapter.size() == 0) {
            clearSourceSwitch();
        } else if (isManualSourceSearch()) {
            setManualSourceSearch(false);
            mContent.stopSearch();
            Notify.show(R.string.play_switch_empty);
        }
    }

    private boolean matchSourceTitle(String title, String keyword) {
        String source = Util.normalize(title);
        String target = Util.normalize(keyword);
        if (source.isEmpty() || target.isEmpty()) return false;
        return source.equals(target) || source.contains(target) || target.contains(source) || Util.similarity(source, target) >= 0.7;
    }

    private void setSourceSearchActor(String actor) {
        sourceSearchActor = Objects.toString(actor, "");
    }

    private void markCurrentSourceBroken() {
        String brokenKey = getCurrentBrokenKey();
        if (!brokenKey.isEmpty()) mBroken.add(brokenKey);
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
        App.post(() -> {
            if (!mSearchActive || !TextUtils.equals(token, pendingSearchToken)) return;
            for (Vod item : items) {
                int index = findQuickItemInsertPosition(item);
                mQuickAdapter.add(index, item);
            }
            if (isInitAuto()) {
                if (!hasPendingSearchTasks() || mQuickAdapter.size() >= 10) {
                    setInitAuto(false);
                    mPlaybackNavigation.nextSite();
                }
            } else if (canAdvancePendingSourceSwitch()) {
                mPlaybackNavigation.nextSite();
            }
        }, 100);
    }

    private int findQuickItemInsertPosition(Vod item) {
        double score = Util.similarity(item.getVodName(), getSourceSwitchKeyword());
        item.setScore(score);
        for (int i = 0; i < mQuickAdapter.size(); i++) {
            if (compareQuickItem(item, score, (Vod) mQuickAdapter.get(i)) < 0) return i;
        }
        return mQuickAdapter.size();
    }

    private int compareQuickItem(Vod left, double scoreLeft, Vod right) {
        double scoreRight = right.getScore();
        if (scoreLeft != scoreRight) return Double.compare(scoreRight, scoreLeft);
        int result = Integer.compare(getQuickActorRank(left), getQuickActorRank(right));
        if (result != 0) return result;
        return left.getVodActor().compareToIgnoreCase(right.getVodActor());
    }

    private int getQuickActorRank(Vod item) {
        String target = Objects.toString(sourceSearchActor, "");
        String source = item.getVodActor();
        if (TextUtils.isEmpty(target) || TextUtils.isEmpty(source)) return 2;
        if (Util.similarity(source, target) >= 0.8) return 0;
        return hasActorOverlap(source, target) ? 1 : 3;
    }

    private boolean hasActorOverlap(String source, String target) {
        for (String sourceActor : splitActors(source)) {
            if (sourceActor.isEmpty()) continue;
            for (String targetActor : splitActors(target)) {
                if (targetActor.isEmpty()) continue;
                if (sourceActor.equals(targetActor)) return true;
            }
        }
        return false;
    }

    private String[] splitActors(String text) {
        return Objects.toString(text, "").trim().toLowerCase().split("[\\s,，、/／;；|｜]+");
    }

    private Flag findTargetFlag(List<Flag> flags) {
        if (isPendingSiteSwitch()) {
            for (Flag flag : flags) if (hasEpisode(flag)) return flag;
            return flags.isEmpty() ? null : flags.get(0);
        }
        String target = mHistory != null ? mHistory.getVodFlag() : null;
        for (Flag flag : flags) {
            if (flag.getFlag().equals(target)) return flag;
        }
        return flags.isEmpty() ? null : flags.get(0);
    }

    private boolean hasEpisode(Flag flag) {
        if (sourceSwitchSingleEpisode) return flag != null && !flag.getEpisodes().isEmpty();
        if (flag != null && flag.getEpisodes().size() == 1) return false;
        return flag != null && flag.find(sourceSwitchEpisode, !TextUtils.isEmpty(sourceSwitchEpisode)) != null;
    }

    private Episode getDefaultSourceSwitchEpisode(Flag flag) {
        if (flag == null || flag.getEpisodes().isEmpty()) return null;
        if (flag.getPosition() >= 0 && flag.getPosition() < flag.getEpisodes().size()) return flag.getEpisodes().get(flag.getPosition());
        for (Episode episode : flag.getEpisodes()) if (episode.isActivated()) return episode;
        return flag.getEpisodes().get(0);
    }

    private void setSourceSwitchTarget(Flag flag, Episode episode) {
        sourceSwitchTargetFlag = flag == null ? null : flag.getFlag();
        sourceSwitchTargetEpisodeKey = getSourceSwitchEpisodeKey(episode);
    }

    private void setFlagSwitchTarget(Flag flag, Episode episode) {
        flagSwitchTargetFlag = flag == null ? null : flag.getFlag();
        flagSwitchTargetEpisodeKey = getSourceSwitchEpisodeKey(episode);
        flagSwitchPosition = getCurrentSwitchPosition();
    }

    private void clearFlagSwitchTarget() {
        flagSwitchTargetFlag = null;
        flagSwitchTargetEpisodeKey = null;
        flagSwitchPosition = 0;
    }

    private boolean hasPendingSourceSwitchTarget() {
        return isSourceSwitching() && !TextUtils.isEmpty(sourceSwitchTargetEpisodeKey);
    }

    private void cancelPendingSourceSwitchTarget() {
        if (hasPendingSourceSwitchTarget()) clearSourceSwitch();
    }

    private String getSourceSwitchEpisodeKey(Episode episode) {
        if (episode == null) return null;
        return TextUtils.isEmpty(episode.getUrl()) ? episode.getName() : episode.getUrl();
    }

    private boolean shouldApplySourceSwitchProgress(Flag flag, Episode episode) {
        if (!isSourceSwitching() || flag == null || episode == null) return false;
        if (TextUtils.isEmpty(sourceSwitchTargetEpisodeKey)) return true;
        return TextUtils.equals(sourceSwitchTargetFlag, flag.getFlag()) && TextUtils.equals(sourceSwitchTargetEpisodeKey, getSourceSwitchEpisodeKey(episode));
    }

    private boolean shouldApplyFlagSwitchProgress(Flag flag, Episode episode) {
        if (isSourceSwitching() || flag == null || episode == null || TextUtils.isEmpty(flagSwitchTargetEpisodeKey)) return false;
        return TextUtils.equals(flagSwitchTargetFlag, flag.getFlag()) && TextUtils.equals(flagSwitchTargetEpisodeKey, getSourceSwitchEpisodeKey(episode));
    }

    private void applySwitchProgress(Flag flag, Episode episode) {
        if (isSourceSwitching()) applySourceSwitchProgress(flag, episode);
        else applyFlagSwitchProgress(flag, episode);
    }

    private void applySourceSwitchProgress(Flag flag, Episode episode) {
        if (!isSourceSwitching() || mHistory == null || episode == null) return;
        setPendingProgressOverride(flag, episode, sourceSwitchPosition);
    }

    private void applyFlagSwitchProgress(Flag flag, Episode episode) {
        if (mHistory == null || flag == null || episode == null) return;
        long position = getCurrentSwitchPosition();
        if (position <= 0) position = flagSwitchPosition;
        setPendingProgressOverride(flag, episode, position);
    }

    private void setPendingProgressOverride(Flag flag, Episode episode, long position) {
        pendingProgressFlag = flag == null ? null : flag.getFlag();
        pendingProgressEpisodeKey = getSourceSwitchEpisodeKey(episode);
        pendingProgressPosition = Math.max(position, 0);
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
        return mFocus2 == null || mFocus2 == mBinding.control.opening || mFocus2 == mBinding.control.ending ? mBinding.control.next : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        hasKeyEvent = true;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
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
        updateCenterProgress(mPlayers.getNewTime(time), mPlayers.getDuration());
        setCenterVisible(true);
        hideProgress();
    }

    @Override
    public void onSeekTo(int time) {
        mPlaybackState.onSeek();
        mPlayers.seekTo(time);
        mKeyDown.resetTime();
        onPlay();
    }

    @Override
    public void onSpeedUp() {
        if (!mPlayers.isPlaying() || !mPlayers.canAdjustSpeed()) return;
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        setDanmuViewSettings();
        
        
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        setVisibilityIfChanged(mBinding.widget.speed, View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        setPlainTextIfChanged(mBinding.control.speed, mPlayers.setSpeed(mHistory.getSpeed()));
        setDanmuViewSettings();
        
        
        setVisibilityIfChanged(mBinding.widget.speed, View.GONE);
        mBinding.widget.speed.clearAnimation();
    }

    @Override
    public void onKeyUp() {
        long current = mPlayers.getPosition();
        long half = mPlayers.getDuration() / 2;
        showInfo();
        showControl(current < half ? mBinding.control.opening : mBinding.control.ending);
    }

    @Override
    public void onKeyDown() {
        showInfo();
        showControl(getFocus2());
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
        mPlayers.setPlayer(item);
        setPlayerView();
        setDecodeView();
        onRefresh();
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
            case 1000:
                setResult(RESULT_OK);
                finish();
                break;
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
            setPlayerView();
            setDecodeView();
            onRefresh();
        } else {
            reconcilePlaybackUiState();
            if (mResumeOnForeground) onPlay();
        }
    }

    @Override
    protected void onPause() {
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
            mContent.stopSearch();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveHistoryNow();
        clearPartRequest();
        mPlaybackState.release();
        mKeyDown.release();
        mContent.stopSearch();
        cancelDetailPreload();
        ThreadPools.shutdown(mDetailExecutor);
        mClock.release();
        mPlayers.release();
        clearArtworkTarget();
        Source.get().stop();
        RefreshEvent.history();
        App.removeCallbacks(mR1, mR2, mR3, mR4, mR5, mR6, mR7, mR8);
    }
}
