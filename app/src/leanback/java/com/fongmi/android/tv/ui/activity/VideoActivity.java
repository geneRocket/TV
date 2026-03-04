package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Html;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import okhttp3.Call;
import okhttp3.Response;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class VideoActivity extends BaseActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, TrackDialog.ChooserListener, PlayerDialog.Listener, ArrayPresenter.OnClickListener, Clock.Callback {

    private static final long SEEK_READY_STABLE_MS = 300;
    private static final long SEEK_BOUNCE_WINDOW_MS = 1500;
    private static final int REQUEST_DANMAKU_FILE = 9998;

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
            if (event.getCode() == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    || event.getCode() >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                    && event.getCode() <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) {
                host.mPlayers.setFormat(ExoUtil.getMimeType(event.getCode()));
            }
            host.mPlayers.setMediaSource();
            return true;
        }

        private boolean tryRecoverByPlayerSwitch(ErrorEvent event) {
            boolean canSwitch = host.getSite().getPlayerType() == -1
                    && event.isUrl()
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
            if (host.mFlagAdapter.size() == 0 || item.isActivated()) return;
            if (host.mFlagAdapter.indexOf(item) == -1) item.setFlag(((Flag) host.mFlagAdapter.get(0)).getFlag());
            for (int i = 0; i < host.mFlagAdapter.size(); i++) ((Flag) host.mFlagAdapter.get(i)).setActivated(item);
            host.mBinding.flag.setSelectedPosition(host.mFlagAdapter.indexOf(item));
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
            host.setEpisodeSelectedPosition(host.getEpisodePosition());
            host.notifyItemChanged(host.getEpisodeView(), host.mEpisodeAdapter);
            host.onRefresh();
        }

        public void switchParse(Parse item, boolean notify) {
            VodConfig.get().setParse(item);
            host.notifyItemChanged(host.mBinding.control.parse, host.mParseAdapter);
            if (notify) Notify.show(host.getString(R.string.play_switch_parse, item.getName()));
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
            Vod item = (Vod) host.mQuickAdapter.get(0);
            host.mQuickAdapter.removeItems(0, 1);
            host.mBroken.add(host.getId());
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
            host.setMetadata();
            host.mErrorRecovery.onPlayerReady();
            host.hideProgress();
            host.mPlayers.reset();
            host.setDefaultTrack();
            host.setTrackVisible(true);
            if (host.mHistory != null) host.mHistory.setPlayer(host.mPlayers.getPlayer());
            host.mBinding.widget.size.setText(host.mPlayers.getSizeText());
            host.mBinding.display.size.setText(host.mPlayers.getSizeText());
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
            host.mViewModel.detailContent(host.getKey(), host.getId());
        }

        public void handleMissingDetail() {
            setEmpty(false);
        }

        public void openDetail(Vod item) {
            prepareDetailRequest(item);
            requestDetail();
        }

        public void setDetail(Result result) {
            if (result.getList().isEmpty()) setEmpty(result.hasMsg());
            else bindDetail(result.getList().get(0));
            Notify.show(result.getMsg());
        }

        public void setSearch(Result result) {
            if (!host.mSearchActive) return;
            List<Vod> items = result.getList();
            Iterator<Vod> iterator = items.iterator();
            while (iterator.hasNext()) if (mismatch(iterator.next())) iterator.remove();
            host.mQuickAdapter.addAll(host.mQuickAdapter.size(), items);
            host.mBinding.quick.setVisibility(View.VISIBLE);
            if (host.isInitAuto()) host.mPlaybackNavigation.nextSite();
            if (items.isEmpty()) return;
            App.removeCallbacks(host.mR4);
        }

        public void setSearch(Vod item) {
            host.setAutoMode(false);
            host.mPlaybackNavigation.showDetail(item, false);
        }

        public void advanceSearch(boolean force) {
            if (host.mQuickAdapter.size() == 0) initSearch(host.mBinding.name.getText().toString(), true);
            else if (host.isAutoMode() || force) host.mPlaybackNavigation.nextSite();
        }

        public void stopSearch() {
            if (host.mExecutor == null) return;
            if (host.mExecutor != ThreadPools.search()) host.mExecutor.shutdownNow();
            host.mExecutor = null;
            host.mSearchActive = false;
        }

        public void showEmpty() {
            host.mBinding.progressLayout.showEmpty();
            stopSearch();
        }

        public void checkFlag(Vod item) {
            boolean empty = item.getVodFlags().isEmpty();
            host.mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (empty) {
                ErrorEvent.flag();
            } else {
                host.mPlaybackNavigation.switchFlag(host.mHistory.getFlag(), false);
                if (host.mHistory.isRevSort()) host.reverseEpisode(true);
            }
        }

        public void checkHistory(Vod item) {
            host.mHistory = History.find(host.getHistoryKey());
            host.mHistory = host.mHistory == null ? createHistory(item) : host.mHistory;
            if (!TextUtils.isEmpty(host.getMark())) host.mHistory.setVodRemarks(host.getMark());
            if (Setting.isIncognito() && host.mHistory.getKey().equals(host.getHistoryKey())) host.mHistory.delete();
            host.mBinding.control.opening.setText(host.mHistory.getOpening() == 0 ? host.getString(R.string.play_op) : host.mPlayers.stringToTime(host.mHistory.getOpening()));
            host.mBinding.control.ending.setText(host.mHistory.getEnding() == 0 ? host.getString(R.string.play_ed) : host.mPlayers.stringToTime(host.mHistory.getEnding()));
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
            host.mBinding.scroll.scrollTo(0, 0);
            host.stopActivePlayback();
        }

        private void setEmpty(boolean finish) {
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
            host.setText(host.mBinding.actor, R.string.detail_actor, Html.fromHtml(item.getVodActor()).toString());
            host.setText(host.mBinding.content, R.string.detail_content, Html.fromHtml(item.getVodContent()).toString());
            host.setText(host.mBinding.director, R.string.detail_director, Html.fromHtml(item.getVodDirector()).toString());
            host.mFlagAdapter.setItems(item.getVodFlags(), null);
            host.mBinding.content.setMaxLines(host.getMaxLines());
            host.mBinding.video.requestFocus();
            host.setArtwork(item.getVodPic());
            host.getPart(item.getVodName());
            App.removeCallbacks(host.mR4);
            checkHistory(item);
            checkFlag(item);
            host.checkKeep();
        }

        private History createHistory(Vod item) {
            History history = new History();
            history.setKey(host.getHistoryKey());
            history.setCid(VodConfig.getCid());
            history.setVodName(item.getVodName());
            history.findEpisode(item.getVodFlags());
            history.setSpeed(Setting.getPlaySpeed());
            return history;
        }

        private void initSearch(String keyword, boolean auto) {
            stopSearch();
            host.setAutoMode(auto);
            host.setInitAuto(auto);
            startSearch(keyword);
            host.mBinding.part.setTag(keyword);
        }

        private void startSearch(String keyword) {
            host.mQuickAdapter.clear();
            List<Site> sites = new ArrayList<>();
            host.mExecutor = ThreadPools.search();
            host.mSearchActive = true;
            for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
            for (Site site : sites) host.mExecutor.execute(() -> search(site, keyword));
        }

        private void search(Site site, String keyword) {
            try {
                host.mViewModel.searchContent(site, keyword, true);
            } catch (Throwable ignored) {
            }
        }

        private boolean mismatch(Vod item) {
            if (host.getId().equals(item.getVodId())) return true;
            if (host.mBroken.contains(item.getVodId())) return true;
            String keyword = Objects.toString(host.mBinding.part.getTag(), "");
            if (host.isAutoMode()) return !item.getVodName().equals(keyword);
            else return !item.getVodName().contains(keyword);
        }

        private boolean isPass(Site item) {
            if (host.isAutoMode() && !item.isChangeable()) return false;
            return item.isSearchable();
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
    private SiteViewModel mViewModel;
    private List<Danmaku> mDanmakus;
    private List<String> mBroken;
    private History mHistory;
    private Players mPlayers;
    private boolean background;
    private boolean fullscreen;
    private boolean initTrack;
    private boolean initAuto;
    private boolean autoMode;
    private boolean useParse;
    private volatile boolean mSearchActive;
    private final ContentController mContent = new ContentController(this);
    private final ErrorRecoveryController mErrorRecovery = new ErrorRecoveryController(this);
    private final PlaybackNavigationController mPlaybackNavigation = new PlaybackNavigationController(this);
    private final PlaybackStateController mPlaybackState = new PlaybackStateController(this);
    private int groupSize;
    private long mLastHistorySaveAt;
    private boolean mShouldSkipOpening;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private boolean hasKeyEvent;
    private boolean mResumeOnForeground = true;

    public static void push(FragmentActivity activity, String text) {
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

    private String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
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
        for (int i = 0; i < mFlagAdapter.size(); i++) if (((Flag) mFlagAdapter.get(i)).isActivated()) return i;
        return 0;
    }

    private int getEpisodePosition() {
        for (int i = 0; i < mEpisodeAdapter.size(); i++) if (((Episode) mEpisodeAdapter.get(i)).isActivated()) return i;
        return 0;
    }

    private int getParsePosition() {
        for (int i = 0; i < mParseAdapter.size(); i++) if (((Parse) mParseAdapter.get(i)).isActivated()) return i;
        return 0;
    }

    private int getPlayer() {
        return mHistory != null && mHistory.getPlayer() != -1 ? mHistory.getPlayer() : getSite().getPlayerType() != -1 ? getSite().getPlayerType() : Setting.getPlayer();
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
        mBroken = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = mContent::showEmpty;
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
        mBinding.control.text.setUpListener(this::onSubtitleClick);
        mBinding.control.text.setDownListener(this::onSubtitleClick);
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
                if (mFlagAdapter.size() > 0) mPlaybackNavigation.switchFlag((Flag) mFlagAdapter.get(position), false);
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
        int itemCount = getEpisodeView().getAdapter().getItemCount();
        if (itemCount <= 0) return;
        int columns = mEpisodePresenter.getNumColumns();
        if ((position + columns >= itemCount) && ((position % columns) + 1 > (itemCount % columns))) {
            child.itemView.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
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
        mBinding.control.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
    }

    private void setDanmuViewSettings() {
        float[] range = {2.4f, 1.8f, 1.2f, 0.8f};
        float speed = range[Setting.getDanmuSpeed()] / Math.max(mPlayers.getSpeed(), 0.1f);
        float alpha = Setting.getDanmuAlpha() / 100.0f;
        float sizeScale = isFullscreen() ? 1.2f * Setting.getDanmuSize() : 0.8f * Setting.getDanmuSize();
        int maxLine = Setting.getDanmuLine(3);
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLine);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLine);
        mDanmakuContext.setMaximumLines(maxLines).setScrollSpeedFactor(speed).setDanmakuTransparency(alpha).setScaleTextSize(sizeScale);
    }

    private void setDanmuView() {
        mPlayers.setDanmuView(mBinding.danmaku);
        setDanmuViewSettings();
        mDanmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3).setDanmakuMargin(8);
        mBinding.control.danmu.setActivated(Setting.isDanmu());
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
        mBinding.control.player.setText(mPlayers.getPlayerText());
        mBinding.control.speed.setEnabled(mPlayers.canAdjustSpeed());
        getExo().setVisibility(mPlayers.isExo() ? View.VISIBLE : View.GONE);
        getIjk().setVisibility(mPlayers.isIjk() ? View.VISIBLE : View.GONE);
        float speed = mHistory == null ? Setting.getPlaySpeed() : mHistory.getSpeed();
        mBinding.control.speed.setText(mPlayers.setSpeed(speed));
        setDanmuViewSettings();
        
        
    }

    private void setDecodeView() {
        mBinding.control.decode.setText(mPlayers.getDecodeText());
    }

    private void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        mBinding.control.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void stopActivePlayback() {
        mPlaybackState.clear();
        mClock.setCallback(null);
        mPlayers.reset();
        mPlayers.stop();
    }

    private void requestPlayback(Flag flag, Episode episode, boolean replay) {
        mBinding.widget.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
        mBinding.display.title.setText(mBinding.widget.title.getText());
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateHistory(episode, replay);
        mPlayers.clear();
        mPlayers.stop();
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
        result.getUrl().set(mQualityAdapter.getPosition());
        setUseParse(VodConfig.hasParse() && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(result.getFlag())) || result.getJx() == 1));
        mPlayers.start(result, isUseParse(), getSite().isChangeable() ? getSite().getTimeout() : -1);
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
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
        mPlayers.setDanmaku(item);
        mDanmakus = mPlayers.getDanmakus();
        prepareDanmaku(mPlayers.getDanmaku());
    }

    private void prepareDanmaku(Danmaku item) {
        mBinding.danmaku.release();
        if (!Setting.isDanmuLoad()) {
            mBinding.danmaku.setVisibility(View.GONE);
            return;
        }
        mBinding.danmaku.setVisibility(item == null || item.isEmpty() ? View.GONE : View.VISIBLE);
        if (item != null && !item.isEmpty()) {
            App.execute(() -> {
                Parser parser = new Parser(item.getUrl());
                App.post(() -> {
                    mBinding.danmaku.prepare(parser, mDanmakuContext);
                    showDanmu();
                });
            });
        }
    }

    private int getMaxLines() {
        int lines = 1;
        if (isGone(mBinding.actor)) ++lines;
        if (isGone(mBinding.remark)) ++lines;
        if (isGone(mBinding.director)) ++lines;
        return lines;
    }

    private void setText(TextView view, int resId, String text) {
        view.setText(getSpan(resId, text), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
        view.setTag(text);
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
        getEpisodeView().setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        if (isVisible(mBinding.episodeVert)) setEpisodeView(items);
        mEpisodeAdapter.setItems(items, null);
        setArrayAdapter(items.size());
        setR2Callback(50);
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
        ViewGroup.LayoutParams params = mBinding.episodeVert.getLayoutParams();
        params.width = ResUtil.getScreenWidth();
        params.height = rowNum > 6 ? ResUtil.dp2px(300) : ResUtil.dp2px(rowNum * 44);
        mBinding.episodeVert.setNumColumns(numColumns);
        mBinding.episodeVert.setColumnWidth((width - ((numColumns - 1) * ResUtil.dp2px(8))) / numColumns);
        mBinding.episodeVert.setLayoutParams(params);
        mBinding.episodeVert.setWindowAlignmentOffsetPercent(10f);
        mEpisodePresenter.setNumColumns(numColumns);
        mEpisodePresenter.setNumRows(rowNum);
    }

    private void seamless(Flag flag) {
        Episode episode = flag.find(mHistory.getVodRemarks(), getMark().isEmpty());
        setQualityVisible(episode != null && episode.isActivated() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isActivated()) return;
        if (Setting.getFlag() == 1) {
            episode.setActivated(true);
            if (!isFullscreen()) getEpisodeView().requestFocus();
            setEpisodeSelectedPosition(getEpisodePosition());
            episode.setActivated(false);
        } else {
            mHistory.setVodRemarks(episode.getName());
            mPlaybackNavigation.switchEpisode(episode);
            hidePreview();
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
            mPlayers.start(result, isUseParse(), getSite().isChangeable() ? getSite().getTimeout() : -1);
            mBinding.danmaku.hide();
        } catch (Exception e) {
            ErrorEvent.extract(e.getMessage());
            e.printStackTrace();
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
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) for (int i = size; i > 0; i -= getGroupSize()) items.add(i + "-" + Math.max(i - (getGroupSize() - 1), 1));
        else for (int i = 0; i < size; i += getGroupSize()) items.add((i + 1) + "-" + Math.min(i + getGroupSize(), size));
        mArrayAdapter.setItems(items, null);
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
        boolean hasDialog = false;
        for (Fragment f : getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) hasDialog = true;
        mBinding.display.clock.setVisibility(Setting.isDisplayTime() || isVisible(mBinding.widget.info)  ? View.VISIBLE : View.GONE);
        mBinding.display.titleLayout.setVisibility(Setting.isDisplayVideoTitle() && !isVisible(mBinding.control.getRoot()) ? View.VISIBLE : View.GONE);
        mBinding.display.netspeed.setVisibility(Setting.isDisplaySpeed() && !isVisible(mBinding.control.getRoot()) && !hasDialog ? View.VISIBLE : View.GONE);
        mBinding.display.duration.setVisibility(Setting.isDisplayDuration() && !isVisible(mBinding.control.getRoot()) && (mPlayers.isVod()) && !hasDialog ? View.VISIBLE : View.GONE);
        mBinding.display.progress.setVisibility(Setting.isDisplayMiniProgress() && !isVisible(mBinding.control.getRoot()) && (mPlayers.isVod()) && !hasDialog ? View.VISIBLE : View.GONE);
    }

    private void onTimeChangeDisplaySpeed() {
        boolean visible = !isVisible(mBinding.control.getRoot());
        long position = mPlayers.getPosition();
        if (Setting.isDisplaySpeed() && visible) Traffic.setSpeed(mBinding.display.netspeed);
        if (Setting.isDisplayDuration() && visible && position > 0) mBinding.display.duration.setText(mPlayers.getPositionTime(0) + "/" + mPlayers.getDurationTime());
        if (Setting.isDisplayMiniProgress() && visible && position > 0 && (mPlayers.isVod())) mBinding.display.progress.setProgress((int)(position * 100 / mPlayers.getDuration()));
        showDisplayInfo();
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
        mBinding.control.danmu.setActivated(Setting.isDanmu());
        showDanmu();
        if (Setting.isDanmu()) mPlayers.prepared();
    }

    private void showDanmu() {
        if (Setting.isDanmu()) mBinding.danmaku.show();
        else mBinding.danmaku.hide();
    }

    private void onDanmuAdd() {
        int line = Setting.getDanmuLine(3);
        line = Math.min(line + 1, 15);
        Setting.putDanmuLine(line);
        mBinding.control.danmu.setText(line + ResUtil.getString(R.string.lines));
        setDanmuViewSettings();
    }

    private void onDanmuSub() {
        int line = Setting.getDanmuLine(3);
        line = Math.max(line - 1, 1);
        Setting.putDanmuLine(line);
        mBinding.control.danmu.setText(line + ResUtil.getString(R.string.lines));
        setDanmuViewSettings();
    }

    private boolean onDanmakuSource() {
        if (mDanmakus == null || mDanmakus.isEmpty()) {
            FileChooserDialog.create().player(mPlayers).mode(FileChooserDialog.MODE_DANMAKU).show(this);
            return true;
        }
        int current = 0;
        for (int i = 0; i < mDanmakus.size(); i++) if (mDanmakus.get(i).isSelected()) current = i;
        int next = (current + 1) % mDanmakus.size();
        setDanmaku(mDanmakus.get(next));
        Notify.show(mDanmakus.get(next).getName());
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
        mBinding.control.speed.setText(mPlayers.addSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
    }

    private void onSpeedAdd() {
        mBinding.control.speed.setText(mPlayers.addSpeed(0.25f));
        mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
    }

    private void onSpeedSub() {
        mBinding.control.speed.setText(mPlayers.subSpeed(0.25f));
        mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
    }

    private boolean onSpeedLong() {
        mBinding.control.speed.setText(mPlayers.toggleSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
        return true;
    }


    private void onRefresh() {
        onReset(false);
    }

    private void saveHistoryNow() {
        if (mHistory == null) return;
        long position = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        mHistory.setPosition(position);
        mHistory.setDuration(duration);
        if (position >= 0 && duration > 0 && !Setting.isIncognito()) {
            mLastHistorySaveAt = System.currentTimeMillis();
            App.execute(() -> mHistory.update());
        }
    }

    private void releaseForCastIfNeeded() {
        if (!isBackground() || isFinishing() || mPlayers.isRelease()) return;
        if (!(App.activity() instanceof CastActivity)) return;
        saveHistoryNow();
        mPlayers.releasePlayer();
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
        mBinding.control.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onOpening() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || current > duration / 2) return;
        setOpening(current);
    }

    private void onOpeningAdd() {
        setOpening(Math.min(mHistory.getOpening() + 1000, mPlayers.getDuration() / 2));
    }

    private void onOpeningSub() {
        setOpening(Math.max(0, mHistory.getOpening() - 1000));
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        mBinding.control.opening.setText(opening == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
    }

    private void onEnding() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || current < duration / 2) return;
        setEnding(duration - current);
    }

    private void onEndingAdd() {
        setEnding(Math.min(mPlayers.getDuration() / 2, mHistory.getEnding() + 1000));
    }

    private void onEndingSub() {
        setEnding(Math.max(0, mHistory.getEnding() - 1000));
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        mBinding.control.ending.setText(ending == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mHistory.getEnding()));
    }

    private boolean onChoose() {
        if (mPlayers.isEmpty()) return false;
        mPlayers.choose(this, mBinding.widget.title.getText());
        return true;
    }

    private void onPlayer() {
        PlayerDialog.create().select(mPlayers.getPlayer()).title(mBinding.widget.title.getText().toString()).show(this);
        hideControl();
    }

    private void onDecode() {
        onDecode(true);
    }

    private void onDecode(boolean save) {
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
        mBinding.widget.progress.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            App.post(mR3, 0);
            hideError();
        } else {
            App.removeCallbacks(mR3);
            Traffic.reset();
        }
    }

    private void showProgress() {
        setProgressVisible(true);
    }

    private void hideProgress() {
        setProgressVisible(false);
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void setInfoVisible(boolean visible) {
        mBinding.widget.info.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        mBinding.widget.exoDuration.setText(mPlayers.stringToTime(safeDuration));
        mBinding.widget.exoPosition.setText(mPlayers.stringToTime(safePosition));
        mBinding.widget.seekBar.setPosition(safePosition);
        mBinding.widget.seekBar.setDuration(safeDuration);
    }

    private void setCenterAction(int resId) {
        mBinding.widget.action.setImageResource(resId);
    }

    private void setCenterVisible(boolean visible) {
        mBinding.widget.center.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        mBinding.control.danmu.setVisibility(mBinding.danmaku.isPrepared() ? View.VISIBLE : View.GONE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        mBinding.control.episodes.setVisibility(Setting.getFullscreenMenuKey() == 0 ? View.VISIBLE : View.GONE);
        view.requestFocus();
        setControlNextFocus();
        setR1Callback();
    }

    private void hideControl() {
        hideControl(true);
    }

    private void hideControl(boolean hideInfo) {
        if (hideInfo) hideInfo();
        mBinding.control.text.setText(R.string.play_track_text);
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        setCenterAction(R.drawable.ic_widget_play);
        setCenterVisible(false);
    }

    private void showPreview(Drawable preview) {
        if (Setting.getFlag() == 0 || isGone(mBinding.widget.preview)) return;
        mBinding.widget.preview.setVisibility(View.VISIBLE);
        mBinding.widget.preview.setImageDrawable(preview);
    }

    private void hidePreview() {
        mBinding.widget.preview.setVisibility(View.GONE);
        mBinding.widget.preview.setImageDrawable(null);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        if (!isBuffering()) {
            hideProgress();
            return;
        }
        App.post(mR3, Constant.INTERVAL_TRAFFIC);
    }

    private boolean isBuffering() {
        return mPlayers.isBuffering();
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback(long delayMillis) {
        App.post(mR2, delayMillis);
    }

    private void setArtwork(String url) {
        ImgUtil.load(url, R.drawable.radio, new CustomTarget<>() {
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
        });
    }

    private void getPart(String source) {
        OkHttp.newCall("https://api.yesapi.cn/?service=App.Scws.GetWords&app_key=CEE4B8A091578B252AC4C92FB4E893C3&text=" + URLEncoder.encode(source.trim())).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<String> items = Part.get(response.body().string());
                items.removeIf(source::equals);
                App.post(() -> setPartAdapter(items), 1000);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                List<String> items = Collections.emptyList();
                App.post(() -> setPartAdapter(items), 1000);
            }
        });
    }

    private void setPartAdapter(List<String> items) {
        mBinding.part.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mPartAdapter.setItems(items, null);
        setR2Callback(1000);
    }

    private void onSearchPage() {
        String keyword = mHistory == null ? mBinding.name.getText().toString().trim() : mHistory.getVodName();
        if (TextUtils.isEmpty(keyword)) keyword = getName();
        if (TextUtils.isEmpty(keyword)) return;
        SearchActivity.start(this, keyword, true);
    }

    private void updateHistory(Episode item, boolean replay) {
        boolean sameEpisode = item.equals(mHistory.getEpisode());
        replay = replay || !sameEpisode;
        long position = replay ? 0 : mHistory.getPosition();
        mHistory.setPosition(position);
        mShouldSkipOpening = replay;
        mHistory.setEpisodeUrl(item.getUrl());
        mHistory.setVodRemarks(item.getName());
        mHistory.setVodFlag(getFlag().getFlag());
        mHistory.setCreateTime(System.currentTimeMillis());
        mPlayers.setPosition(Math.max(mHistory.getOpening(), mHistory.getPosition()));
    }

    private void checkKeep() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setSiteName(getSite().getName());
        keep.setVodPic(mBinding.video.getTag().toString());
        keep.setVodName(mBinding.name.getText().toString());
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
        App.post(this::hideControl, 200);
        SubtitleView subtitleView = mPlayers.isIjk() ? getIjk().getSubtitleView() : getExo().getSubtitleView();
        String videoName = getName(); // 获取视频名称
        String finalVideoName;
        if (videoName.isEmpty() && getEpisode() != null) {
            finalVideoName = getEpisode().getName();
        } else if (videoName.isEmpty() && getTitle() != null) {
            finalVideoName = getTitle().toString();
        } else {
            finalVideoName = videoName;
        }
        App.post(() -> SubtitleDialog.create().view(subtitleView).name(finalVideoName).full(isFullscreen()).show(this), 200);
    }

    @Override
    public void onTimeChanged() {
        onTimeChangeDisplaySpeed();
        long position, duration;
        mHistory.setPosition(position = mPlayers.getPosition());
        mHistory.setDuration(duration = mPlayers.getDuration());
        if (position >= 0 && duration > 0 && !Setting.isIncognito()) {
            long now = System.currentTimeMillis();
            if (now - mLastHistorySaveAt >= 3000) {
                mLastHistorySaveAt = now;
                App.execute(() -> mHistory.update());
            }
        }

        // 片头跳过检测（仅在开播阶段执行一次，避免后续手动回拖被再次强制跳过）
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (isBackground()) return;
        if (ActionEvent.PLAY.equals(event.getAction()) || ActionEvent.PAUSE.equals(event.getAction())) {
            onKeyCenter();
        } else if (ActionEvent.NEXT.equals(event.getAction())) {
            mBinding.control.next.performClick();
        } else if (ActionEvent.PREV.equals(event.getAction())) {
            mBinding.control.prev.performClick();
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
        mBinding.control.text.setVisibility(visible && (mPlayers.haveTrack(C.TRACK_TYPE_TEXT) || mPlayers.isExo()) ? View.VISIBLE : View.GONE);
        mBinding.control.volume.setVisibility(visible && mPlayers.isExo() ? View.VISIBLE : View.GONE);
        mBinding.control.audio.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.video.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    private void setDefaultTrack() {
        if (isInitTrack()) {
            setInitTrack(false);
            mPlayers.prepared();
            mPlayers.setTrack(Track.find(getHistoryKey()));
        }
    }

    private void setMetadata() {
        String title = mHistory == null ? mBinding.name.getText().toString() : mHistory.getVodName();
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
        mPlayers.nextPlayer();
        setPlayerView();
        setDecodeView();
        onRefresh();
    }

    private void advanceRecoveryFlow() {
        if (!getSite().isChangeable()) return;
        if (isUseParse()) advanceParse();
        else advanceFlag();
    }

    private void advanceParse() {
        int position = getParsePosition();
        boolean last = position == mParseAdapter.size() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) advanceFlag();
        else mPlaybackNavigation.nextParse();
    }

    private void initParse() {
        if (mParseAdapter.size() == 0) return;
        VodConfig.get().setParse((Parse) mParseAdapter.get(0));
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
    }

    private void advanceFlag() {
        int position = isGone(mBinding.flag) ? -1 : getFlagPosition();
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
        if (mBinding.progressLayout.isContent() && !isFullscreen() && KeyUtil.isBackKey(event) && Setting.getSmallWindowBackKey() == 1 && getCurrentFocus() != mBinding.video) {
            mFocus1 = mBinding.video;
            getFocus1().requestFocus();
            return true;
        }
        if (isFullscreen() && KeyUtil.isMenuKey(event) && Setting.getFullscreenMenuKey() == 0) onToggle();
        if (isFullscreen() && KeyUtil.isMenuKey(event) && Setting.getFullscreenMenuKey() == 1) onEpisodes();
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event)) return mKeyDown.onKeyDown(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onBrightEnd() {
        mBinding.widget.bright.setVisibility(View.GONE);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onVolumeEnd() {
        mBinding.widget.volume.setVisibility(View.GONE);
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
        mBinding.control.speed.setText(mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        setDanmuViewSettings();
        
        
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.widget.speed.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        mBinding.control.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
        setDanmuViewSettings();
        
        
        mBinding.widget.speed.setVisibility(View.GONE);
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
        } else if (mResumeOnForeground) {
            onPlay();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        setBackground(true);
        saveHistoryNow();
        mPlayers.pause();
        mClock.stop();
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
        mPlaybackState.release();
        mContent.stopSearch();
        mClock.release();
        mPlayers.release();
        Source.get().stop();
        RefreshEvent.history();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
    }
}
