package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.ui.presenter.VodPagePresenter;
import com.fongmi.android.tv.ui.presenter.VodDetailPresenter;
import com.fongmi.android.tv.repository.HistoryRepository;
import com.fongmi.android.tv.repository.DoubanRecommendRepository;
import com.fongmi.android.tv.repository.KeepRepository;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.repository.TrackRepository;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.CastEvent;
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
import com.fongmi.android.tv.player.vod.VodHistoryController;
import com.fongmi.android.tv.player.vod.VodRecoveryFlow;
import com.fongmi.android.tv.player.vod.VodSourceSearch;
import com.fongmi.android.tv.utils.MainThreadExecutor;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.model.SearchRequestToken;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.model.SiteSearchSession;
import com.fongmi.android.tv.player.IjkUtil;
import com.fongmi.android.tv.ui.dialog.PlayerDialog;
import com.fongmi.android.tv.utils.Downloader;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.ui.custom.PlaybackVideoViews;
import com.fongmi.android.tv.ui.custom.PlaybackSubtitleLauncher;
import com.fongmi.android.tv.player.PlaybackMarkers;
import com.fongmi.android.tv.player.PlaybackSeekGesture;
import com.fongmi.android.tv.player.resolver.SpiderPlaybackGateway;
import com.fongmi.android.tv.player.resolver.VodPlaybackResolver;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.player.session.SwitchablePlayerSession;
import com.fongmi.android.tv.player.vod.StartPolicy;
import com.fongmi.android.tv.player.vod.DefaultPlaybackItemFactory;
import com.fongmi.android.tv.player.vod.VodEpisodeCatalog;
import com.fongmi.android.tv.player.vod.VodSourceMatcher;
import com.fongmi.android.tv.player.vod.VodPlaybackCoordinator;
import com.fongmi.android.tv.player.vod.VodPlaybackPhase;
import com.fongmi.android.tv.player.vod.VodPlaybackState;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.player.danmu.Parser;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmuDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.IDMUtil;
import com.fongmi.android.tv.utils.TaskScope;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchSorter;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.TaskScheduler;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;
import com.github.catvod.utils.Trans;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class VideoActivity extends BaseActivity implements Clock.Callback, CustomKeyDownVod.Listener, TrackDialog.Listener, PlayerDialog.Listener, ControlDialog.Listener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    private static final int REQUEST_DANMAKU_FILE = 9998;
    private long mDanmakuPickerGeneration = -1;
    private static final double SOURCE_MIN_SCORE = 0.5;

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private DanmakuContext mDanmakuContext;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ControlDialog mControlDialog;
    private QuickAdapter mQuickAdapter;
    private ParseAdapter mParseAdapter;
    private VodAdapter mRecommendAdapter;
    private CustomKeyDownVod mKeyDown;
    private Downloader mDownloader;
    private SiteSearchSession mSiteSearch;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private List<Dialog> mDialogs;
    private List<Danmaku> mDanmakus;
    private Set<String> mBroken;
    private PlaybackController mPlayers;
    private VodPlaybackResolver mVodResolver;
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
    private final VodHistoryController mHistoryController = new VodHistoryController();
    private final VodPagePresenter mPage = new VodPagePresenter(HistoryRepository.get(), KeepRepository.get(),
            mHistoryController, new TaskScope(ThreadPools.newSingle("video-history"), AppTaskScheduler.get(),
            error -> ThreadPools.log(error, "Page history load failed.")), Constant.TIMEOUT_VOD,
            System::currentTimeMillis, this::onPageHistoryReady);
    private String mSourceKeyword = "";

    private void createPlaybackFlow() {
        mSessionAdapter = new VodSessionAdapter(mPlayers, mPlayerSession, () -> mPlayers.init(getExo(), getIjk()), () -> {
            setPlayerView();
            setDecodeView();
            setR1Callback();
        });
        mPlaybackFlow = new VodPlaybackFlow(mPlaybackCoordinator, new VodPlayerPort(mPlayers, mSessionAdapter) {
            @Override public void seekPresentation(long position) { mPlayers.syncDanmakuPosition(position); }
            @Override public void navigated(boolean next, boolean moved) {
                setR1Callback();
                if (!moved) Notify.show(next ? R.string.error_play_next : R.string.error_play_prev);
            }
            @Override public void saveBeforeStop() { saveHistoryNow(); }
            @Override public void finish() { VideoActivity.this.finish(); }
        }, MainThreadExecutor.get());
        mPlaybackFlow.installRecovery(new RecoveryPort(), com.fongmi.android.tv.utils.AppTaskScheduler.get());
        mPlaybackFlow.installSources(new SourceSearchPort());
        mHistorySession = new VodHistorySession(mPlaybackCoordinator, mPlaybackFlow, mHistoryController, () -> mPage.history());
        mPlaybackLifecycle = new VodPlaybackLifecycle(mPlaybackFlow, new VodPlaybackLifecycle.Port() {
            @Override public void acquireOwnership() { mPlayers.activateAsCurrentPlayer(); }
            @Override public void restoreInitialSelection() { if (mPage.history() != null) onReset(false); }
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
    private float mSpeedBeforeLongPress = Float.NaN;
    private boolean foreground;
    private boolean fullscreen;
    private boolean initTrack;
    private boolean useParse;
    private boolean redirect;
    private boolean rotate;
    private boolean stop;
    private boolean lock;
    private int mDanmakuRequestId;
    private final com.fongmi.android.tv.utils.DeferredTask<List<Vod>> mRecommendations =
            new com.fongmi.android.tv.utils.DeferredTask<>(ThreadPools.recommendations(),
                    com.fongmi.android.tv.utils.AppTaskScheduler.get(), error -> ThreadPools.log(error, "Recommendation failed."));
    private final PlaybackSeekGesture mSeekGesture = new PlaybackSeekGesture();
    private Runnable mR0;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private PiP mPiP;

    public static void push(FragmentActivity activity, String text) {
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(activity, Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
            if (allGranted) start(activity, "push_agent", "file://" + path, name);
        });
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void download(Activity activity, String id, String name, String pic) {
        start(activity, "push_agent", id, name, pic, null, false, true);
    }

    public static void start(Activity activity, String url) {
        start(activity, "push_agent", url, url, null);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null, null, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        start(activity, key, id, name, pic, mark, collect, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean download) {
        Intent intent = new Intent(activity, VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("download", download);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
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

    private final VodDetailPresenter mDetail = new VodDetailPresenter(new VodDetailPresenter.Port() {
        @Override public String nextRequestToken() { return SearchRequestToken.next("detail"); }
        @Override public String siteKey() { return getKey(); }
        @Override public String videoId() { return getId(); }
        @Override public void setPendingRequest(String token) { setPendingDetailRequest(token); }
        @Override public void cancelPendingRequest() { mPage.cancelRequest(); }
        @Override public void scheduleSourceTimeout() {}
        @Override public void requestDetail(String token) {
            mViewModel.detailContent(getKey(), getId(), token); mPage.prefetchHistory();
        }
        @Override public boolean accepts(Result result) { return !isFinishing() && !isDestroyed(); }
        @Override public void clearSourceTimeout() { mBinding.swipeLayout.setRefreshing(false); }
        @Override public void prepareFor(Vod vod) {}
        @Override public void render(Vod vod) { setDetail(vod); }
        @Override public boolean isSourceSwitching() { return false; }
        @Override public void resumeSourceSearch() {}
        @Override public void continueSourceSwitch() {}
        @Override public boolean shouldFinishWhenEmpty() { return isFromCollect(); }
        @Override public boolean hasFallbackName() { return !getName().isEmpty(); }
        @Override public void finishScreen() { finish(); }
        @Override public void showEmptyState() { showEmpty(); }
        @Override public void showFallbackName() { mBinding.name.setText(getName()); }
        @Override public void scheduleEmptyFallback() { mUiCallbacks.post(mR4, 10000); }
        @Override public void recoverSource() { checkSearch(false); }
        @Override public void notifyMessage(String message) { Notify.show(message); }
    });

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
        return mEpisodeAdapter.getActivated();
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

    private boolean isReplay() {
        return Setting.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private boolean isFromDownload() {
        return getIntent().getBooleanExtra("download", false);
    }

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return mBinding.getRoot().getTag().equals("land");
    }

    private boolean isPort() {
        return mBinding.getRoot().getTag().equals("port");
    }

    @Override
    protected boolean transparent() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        String key = Objects.toString(intent.getStringExtra("key"), "");
        if (TextUtils.isEmpty(id)) return;
        if (id.equals(getId()) && key.equals(getKey())) {
            setIntent(intent);
            return;
        }
        mUiCallbacks.reset();
        mArtwork.reset();
        hideSheet();
        mUiCallbacks.post(mR0, 1000);
        saveHistoryNow();
        clearPlaybackTimeout();
        mPlaybackFlow.recovery().reset();
        mPlaybackFlow.intent().clear();
        if (mPlaybackCoordinator != null) mPlaybackFlow.resetFilm();
        mPlayers.reset();
        mPlayers.clear();
        clearDanmakuView();
        mPage.history(null);
        mPlaybackFlow.resetHistoryGate();
        mViewModel.discardPlaybackCommands();
        Notify.dismiss();
        mPage.clearSelection();
        mFlagAdapter.addAll(new ArrayList<>());
        setEpisodeAdapter(new ArrayList<>());
        mQualityAdapter.addAll(Result.empty());
        mPlaybackCoordinator.setEpisodes(new ArrayList<>());
        mBinding.swipeLayout.setRefreshing(true);
        setIntent(intent);
        stopSearch();
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mKeyDown = CustomKeyDownVod.create(this, mBinding.video);
        mDownloader = Downloader.create();
        mFrameParams = mBinding.video.getLayoutParams();
        mDanmakuContext = DanmakuContext.create();
        mBinding.progressLayout.showProgress();
        mBinding.swipeLayout.setEnabled(false);
        mPlayers = PlaybackController.create(this, this::onPlaybackError, this::onPlaybackCommand);
        mPlayers.setForegroundSource(this::isForeground);
        TaskScheduler scheduler = new TaskScheduler() {
            @Override public void post(Runnable task, long delayMillis) { mUiCallbacks.post(task, delayMillis); }
            @Override public void remove(Runnable task) { mUiCallbacks.remove(task); }
        };
        mDialogs = new ArrayList<>();
        mBroken = new HashSet<>();
        mClock = Clock.create(Arrays.asList(mBinding.display.clock, mBinding.control.time));
        mR0 = this::stopService;
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mPiP = new PiP().owner(mPlayers);
        setForeground(true);
        setRecyclerView();
        setVideoView();
        setPlaybackCoordinator();
        setDisplayView();
        setDanmuView();
        setViewModel();
        showProgress();
        checkId();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.widget.error.setOnClickListener(view -> { takePlaybackControl(); retryPlayback(); });
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.actor.setOnClickListener(view -> onActor());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.download.setOnClickListener(view -> onDownload());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.full.setOnClickListener(view -> onFull());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.danmu.setOnClickListener(view -> onDanmu());
        mBinding.control.danmuSetting.setOnClickListener(view -> onDanmuSetting());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.right.back.setOnClickListener(view -> onFull());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.loop.setOnClickListener(view -> onLoop());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.player.setOnLongClickListener(view -> onChoose());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
        mBinding.control.seek.setListener(mPlayers);
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.episode.setHasFixedSize(true);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(item -> { takePlaybackControl(); onItemClick(item); }, ViewType.HORI));
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.control.parse.setHasFixedSize(true);
        mBinding.control.parse.setItemAnimator(null);
        mBinding.control.parse.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(item -> { takePlaybackControl(); onItemClick(item); }, ViewType.DARK));
        mBinding.recommend.setHasFixedSize(true);
        mBinding.recommend.setItemAnimator(null);
        mBinding.recommend.addItemDecoration(new SpaceItemDecoration(8));
        VodAdapter.OnClickListener recommendListener = new VodAdapter.OnClickListener() {
            @Override
            public void onItemClick(Vod item) {
                CollectActivity.start(VideoActivity.this, item.getVodName());
            }

            @Override
            public boolean onLongClick(Vod item) {
                return false;
            }
        };
        mBinding.recommend.setAdapter(mRecommendAdapter = new VodAdapter(recommendListener, Style.rect(), Product.getSpec(this)));
    }

    private void setPlayerView() {
        PlaybackVideoViews.render(mPlayers, getExo(), getIjk(), mBinding.control.action.player, mBinding.control.action.speed);
        float speed = mPage.history() == null ? Setting.getPlaySpeed() : mPage.history().getSpeed();
        mBinding.control.action.speed.setText(mPlayers.setSpeed(speed));
        setDanmuViewSettings();
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.updatePlayer();
    }

    private void setDecodeView() {
        mBinding.control.action.decode.setText(mPlayers.getDecodeText());
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.updateDecode();
    }

    private void setVideoView() {
        mPlayers.init(getExo(), getIjk());
        ExoUtil.setSubtitleView(mBinding.exo);
        IjkUtil.setSubtitleView(mBinding.ijk);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(getActivity(), view));
    }

    private void setPlaybackCoordinator() {
        mVodResolver = new VodPlaybackResolver(ThreadPools.newSingle("vod-resolver"), App::post,
                new SpiderPlaybackGateway(), this::prepareResolvedResult, result -> isUseParse());
        mPlayerSession = new SwitchablePlayerSession(mPlayers.session());
        mPlaybackCoordinator = new VodPlaybackCoordinator(mVodResolver, new DefaultPlaybackItemFactory(mPlayers::getSub), mPlayerSession);
        mPlaybackCoordinator.configureTimeout(com.fongmi.android.tv.utils.AppTaskScheduler.get(), Constant.TIMEOUT_PLAY);
        createPlaybackFlow();
        mPlaybackSubscription = mPlaybackFlow.observe(this::renderPlayback);
    }

    private void renderPlayback(VodPlaybackState state) {
        renderEpisodeSelection(state);
        mRecommendations.setReady(state.getPhase() == VodPlaybackPhase.READY && !mSeekGesture.isActive());
        if (state.getPhase() == VodPlaybackPhase.RESOLVING || state.getPhase() == VodPlaybackPhase.BUFFERING) {
            if (mSiteSearch != null) mSiteSearch.cancel();
        }
        if (state.getPhase() == VodPlaybackPhase.READY && state.getEpisode() != null
                && mPage.history() != null && getFlag() != null && getEpisode() != null) {
            updateHistory(getEpisode());
            mPage.history().setPosition(state.getPosition());
        }
        if (canCommitCurrentHistory()) mHistoryController.updateFields(mPage.history(), state.getPosition(), state.getDuration());
        renderPlaybackPhase(state);
    }

    private void renderEpisodeSelection(VodPlaybackState state) {
        int index = state.getEpisodeIndex();
        if (index < 0 || index >= mEpisodeAdapter.getItemCount()) return;
        Episode episode = mEpisodeAdapter.getItems().get(index);
        int previous = mEpisodeAdapter.getPosition();
        if (previous != index) {
            mPage.selectEpisode(episode);
            if (previous >= 0) mEpisodeAdapter.notifyItemChanged(previous);
            mEpisodeAdapter.notifyItemChanged(index);
            mBinding.episode.scrollToPosition(index);
        }
        CharSequence title = getString(R.string.detail_title, mBinding.name.getText(), episode.getName());
        mBinding.control.title.setText(title);
        mBinding.display.title.setText(title);
    }

    private void replacePlayerSession() {
        if (mSessionAdapter != null) mSessionAdapter.attach();
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setLayoutParams(mFrameParams);
        }
    }

    public void setDanmuViewSettings() {
        float[] range = {2.4f, 1.8f, 1.2f, 0.8f};
        float speed = range[Setting.getDanmuSpeed()] / Math.max(mPlayers.getSpeed(), 0.1f);
        float alpha = Setting.getDanmuAlpha() / 100.0f;
        float sizeScale = Setting.getDanmuSize();
        int maxLine = Setting.getDanmuLine(2);
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
        mPlayers.setDanmuVisible(Setting.isDanmu());
        checkDanmuImg();
    }

    private void setDisplayView() {
        mBinding.display.getRoot().setVisibility(View.VISIBLE);
        showDisplayInfo();
    }

    private void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mSiteSearch = new SiteSearchSession(mViewModel, "video-search", Math.min(6, ThreadPools.searchConcurrency()));
        mViewModel.result().observe(this, this::setDetail);
        mViewModel.search().observe(this, this::setSearch);
        mViewModel.downloadResult().observe(this, this::setDownload);
        mViewModel.episode().observe(this, episode -> {
            onItemClick(episode);
            hideSheet();
        });
        mViewModel.ep().observe(this, episode -> {
            Notify.progress(this);
            mDownloader.title(mBinding.name.getText() + "-" + episode.getName());
            mViewModel.download(getKey(), getFlag().getFlag(), episode.getUrl());
        });
    }

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", "push_agent").putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    private void getDetail() { clearPlaybackTimeout(); mDetail.request(); }

    private void getDetail(Vod item) {
        capturePlaybackPosition();
        mViewModel.discardPlaybackCommands();
        Notify.dismiss();
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getVodPic());
        getIntent().putExtra("id", item.getVodId());
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        mPlayers.reset();
        if (mPlaybackFlow != null) mPlaybackFlow.stop();
        else mPlayers.stop();
        mPlaybackFlow.resetHistoryGate();
        mPage.clearSelection();
        mFlagAdapter.addAll(new ArrayList<>());
        setEpisodeAdapter(new ArrayList<>());
        mPlaybackCoordinator.setEpisodes(new ArrayList<>());
        mQualityAdapter.addAll(Result.empty());
        clearDanmakuView();
        getDetail();
    }

    private void setDetail(Result result) { mDetail.accept(result); }

    private void setEmpty(boolean finish) {
        if (isFromCollect() || finish) {
            finish();
        } else if (getName().isEmpty()) {
            showEmpty();
        } else {
            mBinding.name.setText(getName());
            mUiCallbacks.post(mR4, 10000);
            checkSearch(false);
        }
    }

    private void showEmpty() {
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
        stopSearch();
    }

    private void setDetail(Vod item) {
        mPage.detail(item);
        mBinding.progressLayout.showContent();
        if (isFromDownload()) item.setVodName("");
        if (isFromDownload()) item.setVodPic("");
        mBinding.video.setTag(item.getVodPic(getPic()));
        mBinding.name.setText(item.getVodName(getName()));
        setText(mBinding.remark, 0, item.getVodRemarks());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.content, 0, Html.fromHtml(item.getVodContent()).toString());
        setText(mBinding.actor, R.string.detail_actor, Html.fromHtml(item.getVodActor()).toString());
        setText(mBinding.director, R.string.detail_director, Html.fromHtml(item.getVodDirector()).toString());
        mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
        mFlagAdapter.addAll(mPage.flags());
        setOther(mBinding.other, item);
        setArtwork(item.getVodPic());
        loadRecommendations(item);
        mUiCallbacks.remove(mR4);
        checkHistory(item);
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
        SpannableStringBuilder span = new SpannableStringBuilder(text);
        for (String s : map.keySet()) {
            int index = text.indexOf(s);
            Result result = Result.type(map.get(s));
            span.setSpan(getClickSpan(result), index, index + s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private ClickableSpan getClickSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                FolderActivity.start(getActivity(), getKey(), result);
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
                setRedirect(true);
            }
        };
    }

    private void setOther(TextView view, Vod item) {
        StringBuilder sb = new StringBuilder();
        if (!item.getVodYear().isEmpty()) sb.append(getString(R.string.detail_year, item.getVodYear())).append("  ");
        if (!item.getVodArea().isEmpty()) sb.append(getString(R.string.detail_area, item.getVodArea())).append("  ");
        if (!item.getTypeName().isEmpty()) sb.append(getString(R.string.detail_type, item.getTypeName())).append("  ");
        view.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        view.setText(Util.substring(sb.toString(), 2));
    }

    private void getPlayer(Flag flag, Episode episode, boolean replay) {
        if (flag == null || episode == null || TextUtils.isEmpty(episode.getUrl())) {
            mPlayers.reportError(ErrorEvent.url(0));
            return;
        }
        int index = flag.getEpisodes().indexOf(episode);
        if (index < 0) {
            showError(getString(R.string.error_play_timeout));
            return;
        }
        mBinding.control.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
        mBinding.display.title.setText(mBinding.control.title.getText());
        mKeyDown.release();
        mSeekGesture.reset();
        StartPolicy policy = mPlaybackFlow.intent().consumeStartPolicy(replay, false);
        Source.get().cancelPreloads();
        mPlaybackFlow.playEpisode(index, policy);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clearDanmakuView();
        showProgress();
        setMetadata();
        hidePreview();
    }

    private void prepareResolvedResult(Result result) {
        ensureCompatiblePlayer(result);
        result.getUrl().set(mQualityAdapter.getPosition());
        setUseParse(VodConfig.hasParse() && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(result.getFlag())) || result.getJx() == 1));
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.setParseVisible(isUseParse());
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        setQualityVisible(result.getUrl().isMulti());
        mBinding.swipeLayout.setRefreshing(false);
        setDanmakus(result.getDanmakus());
        mQualityAdapter.addAll(result);
    }

    private void ensureCompatiblePlayer(Result result) {
        mSessionAdapter.ensureCompatible(result.getDrm());
    }

    private void setDownload(Result result) {
        Notify.dismiss();
        if (result == null || result.isRequestFailed()) {
            Notify.show(result != null && result.hasMsg() ? result.getMsg() : getString(R.string.vod_load_failed));
            return;
        }
        mDownloader.result(result).start(this);
    }

    private void setDanmakus(List<Danmaku> items) {
        clearDanmakuView();
        mPlayers.setDanmakus(items);
        mDanmakus = mPlayers.getDanmakus();
        prepareDanmaku(mPlayers.getDanmaku());
    }

    public List<Danmaku> getDanmakus() {
        return mDanmakus == null ? List.of() : new ArrayList<>(mDanmakus);
    }

    public void setDanmaku(Danmaku item) {
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

    public void chooseDanmakuFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/*", "application/xml", "application/json"});
        mDanmakuPickerGeneration = mPlaybackFlow.requestGeneration();
        startActivityForResult(Intent.createChooser(intent, ""), REQUEST_DANMAKU_FILE);
    }

    private void prepareDanmaku(Danmaku item) {
        final int requestId = ++mDanmakuRequestId;
        boolean blocked = !Setting.isDanmuLoad() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode());
        if (blocked) {
            mPlayers.resetDanmu();
            mBinding.danmaku.setVisibility(View.GONE);
            showDanmu();
            mPlayers.prepared();
            return;
        }
        boolean hasSource = item != null && !item.isEmpty();
        mBinding.danmaku.setVisibility(hasSource ? View.VISIBLE : View.GONE);
        if (!hasSource) {
            mPlayers.resetDanmu();
            return;
        }
        mPlayers.resetDanmu();
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

    @Override
    public void onItemClick(Flag item) {
        if (item == null || item.isActivated()) return;
        takePlaybackControl();
        switchFlag(item);
    }

    private boolean selectFlag(Flag item) {
        if (item == null || item.isActivated()) return false;
        int previous = mPage.flagIndex();
        if (!mPage.selectFlag(item, true)) return false;
        item = mPage.flag();
        mFlagAdapter.selectionChanged(previous, mPage.flagIndex());
        mBinding.flag.scrollToPosition(mFlagAdapter.getPosition());
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
        return true;
    }

    private void switchFlag(Flag item) {
        int episodePosition = mEpisodeAdapter.getPosition();
        capturePlaybackPosition();
        if (!selectFlag(item)) return;
        item = mPage.flag();
        if (item.getEpisodes().isEmpty()) return;
        String name = mPage.history() == null ? "" : mPage.history().getVodRemarks();
        Episode episode = mPage.chooseEpisode(item, name, episodePosition);
        mPlaybackFlow.intent().keepCurrentPositionOnNextPlay();
        onItemClick(episode);
        hidePreview();
    }

    @Override
    public void onItemClick(Episode item) {
        if (item == null) return;
        int oldPosition = mEpisodeAdapter.getPosition();
        if (!mPage.selectEpisode(item)) return;
        int newPosition = mEpisodeAdapter.getPosition();
        if (oldPosition == newPosition) {
            mEpisodeAdapter.notifyItemChanged(newPosition);
        } else {
            mEpisodeAdapter.notifyItemChanged(oldPosition);
            mEpisodeAdapter.notifyItemChanged(newPosition);
        }
        mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
        onRefresh();
    }

    @Override
    public void onItemClick(Result result) {
        takePlaybackControl();
        try {
            showProgress();
            mPlaybackFlow.reload();
            mBinding.danmaku.hide();
        } catch (Exception e) {
            ThreadPools.log(e, "Quality switch failed.");
            showError(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.error_play_timeout) : e.getMessage());
            mPlayers.reportError(ErrorEvent.extract(e.getMessage()));
        }
    }

    @Override
    public void onItemClick(Vod item) {
        capturePlaybackPosition();
        takePlaybackControl();
        mPlaybackFlow.sources().select(item);
    }

    @Override
    public void onItemClick(Parse item) {
        setParse(item);
        onRefresh();
    }

    private void setParse(Parse item) {
        int oldPosition = mParseAdapter.getPosition();
        VodConfig.get().setParse(item);
        int newPosition = mParseAdapter.getPosition();
        if (oldPosition == newPosition) {
            mParseAdapter.notifyItemChanged(newPosition);
        } else {
            mParseAdapter.notifyItemChanged(oldPosition);
            mParseAdapter.notifyItemChanged(newPosition);
        }
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.updateParse();
    }

    private void setEpisodeAdapter(List<Episode> items) {
        if (items == null) items = new ArrayList<>();
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.nextRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prevRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.size() == 0 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(items.size() < 3 ? View.GONE : View.VISIBLE);
        mEpisodeAdapter.addAll(items);
        if (mPlaybackCoordinator != null && !mFlagAdapter.isEmpty()) {
            VodEpisodeCatalog.bind(mPlaybackCoordinator, getKey(), getFlag().getFlag(), items, mPage.history());
        }
    }

    private void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void reverseEpisode(boolean scroll) {
        mPage.reverseEpisodes();
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        initSearch(name, false);
    }

    private void onMore() {
        if (mPage.history() == null) return;
        EpisodeGridDialog.create().reverse(mPage.history().isRevSort()).episodes(mEpisodeAdapter.getItems()).show(this);
    }

    private void onDownload() {
        if (mPage.history() == null) return;
        EpisodeGridDialog.create().reverse(mPage.history().isRevSort()).episodes(mEpisodeAdapter.getItems()).download(true).show(this);
    }

    private void onActor() {
        mBinding.actor.setMaxLines(mBinding.actor.getMaxLines() == 1 ? Integer.MAX_VALUE : 1);
    }

    private void onContent() {
        mBinding.content.setMaxLines(mBinding.content.getMaxLines() == 2 ? Integer.MAX_VALUE : 2);
    }

    private void onReverse() {
        if (mPage.history() == null) return;
        mPage.history().setRevSort(!mPage.history().isRevSort());
        reverseEpisode(false);
    }

    private boolean onChange() {
        checkSearch(true);
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.content.getText().toString());
        return true;
    }

    private void onCast() {
        if (mPage.history() == null) return;
        CastDialog.create().history(mPage.history()).video(CastVideo.get(mBinding.name.getText().toString(), mPlayers.getUrl())).fm(true).show(this);
    }

    private void onInfo() {
        InfoDialog.create(this).title(mBinding.control.title.getText()).headers(mPlayers.getHeaders()).url(mPlayers.getUrl()).show();
    }

    private void onFull() {
        setR1Callback();
        toggleFullscreen();
    }

    private void onKeep() {
        boolean added = mPage.toggleFavorite(getHistoryKey(), getSiteCid(), getSite().getName(),
                mBinding.name.getText().toString(), Objects.toString(mBinding.video.getTag(), ""));
        Notify.show(added ? R.string.keep_add : R.string.keep_del);
        RefreshEvent.keep();
        checkKeepImg();
    }

    private void onDanmu() {
        if (!Setting.isDanmu()) {
            Setting.putDanmu(true);
            prepareDanmaku(mPlayers.getDanmaku());
        } else {
            Setting.putDanmu(false);
            showDanmu();
        }
        checkDanmuImg();
        mPlayers.prepared();
    }

    private void onDanmuSetting() {
        DanmuDialog.create().show(this);
    }

    private void showDanmu() {
        mPlayers.setDanmuVisible(Setting.isDanmu());
    }

    private void checkPlay() {
        setR1Callback();
        if (mPlayers.isPlaying()) onPaused();
        else if (mPlayers.isEmpty()) onRefresh();
        else onPlay();
    }

    private void checkNext() {
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.NEXT));
    }

    private void checkPrev() {
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.PREVIOUS));
    }

    private void onSetting() {
        if (mPage.history() == null) return;
        mControlDialog = ControlDialog.create().parent(mBinding).history(mPage.history()).player(mPlayers).parse(isUseParse()).show(this);
    }

    private void onLock() {
        setLock(!isLock());
        setRequestedOrientation(getLockOrient());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onRotate() {
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void onTrack(View view) {
        String videoName = getName(); // 获取视频名称
        TrackDialog.create().name(videoName).player(mPlayers).vod(true).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onLoop() {
        mPlaybackFlow.setLoop(!mBinding.control.action.loop.isActivated());
        mBinding.control.action.loop.setActivated(!mBinding.control.action.loop.isActivated());
    }

    private void onScale() {
        if (mPage.history() == null) return;
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        mPage.history().setScale(index = index == array.length - 1 ? 0 : ++index);
        setScale(index);
        setR1Callback();
    }

    private void onSpeed() {
        if (mPage.history() == null) return;
        mBinding.control.action.speed.setText(mPlayers.addSpeed());
        mPage.history().setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
        setR1Callback();
    }

    private boolean onSpeedLong() {
        if (mPage.history() == null) return false;
        mBinding.control.action.speed.setText(mPlayers.toggleSpeed());
        mPage.history().setSpeed(mPlayers.getSpeed());
        setDanmuViewSettings();
        
        
        setR1Callback();
        return true;
    }

  


    private void onRefresh() {
        onReset(false);
    }

    private void onReset() {
        takePlaybackControl();
        onReset(isReplay());
    }

    private void onReset(boolean replay) {
        mClock.setCallback(null);
        if (mFlagAdapter.isEmpty()) return;
        if (mEpisodeAdapter.isEmpty()) return;
        getPlayer(getFlag(), getEpisode(), replay);
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onPlayer() {
        PlayerDialog.create().select(mPlayers.getPlayer()).title(getName()).show(this);
        hideControl();
    }

    private void onDecode() {
        onDecode(true);
    }

    private void onDecode(boolean save) {
        mPlaybackFlow.dispatch(PlaybackInput.decode(save));
    }

    private void onEnding() {
        if (mPage.history() == null) return;
        long ending = PlaybackMarkers.endingAt(mPlayers.getPosition(), mPlayers.getDuration());
        if (ending == PlaybackMarkers.INVALID) return;
        mPage.history().setEnding(ending);
        mBinding.control.action.ending.setText(mPlayers.stringToTime(mPage.history().getEnding()));
        setR1Callback();
    }

    private boolean onEndingReset() {
        if (mPage.history() == null) return false;
        mPage.history().setEnding(0);
        mBinding.control.action.ending.setText(R.string.play_ed);
        setR1Callback();
        return true;
    }

    private void onOpening() {
        if (mPage.history() == null) return;
        long opening = PlaybackMarkers.openingAt(mPlayers.getPosition(), mPlayers.getDuration());
        if (opening == PlaybackMarkers.INVALID) return;
        mPage.history().setOpening(opening);
        mPlaybackCoordinator.setOpening(opening);
        mBinding.control.action.opening.setText(mPlayers.stringToTime(mPage.history().getOpening()));
        setR1Callback();
    }

    private boolean onOpeningReset() {
        if (mPage.history() == null) return false;
        mPage.history().setOpening(0);
        mPlaybackCoordinator.setOpening(0);
        mBinding.control.action.opening.setText(R.string.play_op);
        setR1Callback();
        return true;
    }

    private void onEpisodes() {
        mDialogs.add(EpisodeListDialog.create(this).episodes(mEpisodeAdapter.getItems()).show());
    }

    private boolean onChoose() {
        if (mPlayers.isEmpty()) return false;
        mPlayers.choose(this, mBinding.control.title.getText());
        setRedirect(true);
        return true;
    }

    private boolean onTextLong() {
        onSubtitleClick();
        return true;
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) getDetail();
        else onRefresh();
    }

    private void showDisplayInfo() {
        boolean pictureMode = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) pictureMode = true;
        boolean hasDialog = false;
        for (Fragment f : getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) hasDialog = true;
        boolean controlVisible = isVisible(mBinding.control.getRoot());
        boolean visible = (!controlVisible || isLock()) && !pictureMode && !hasDialog;
        boolean showNetSpeed = Setting.isDisplaySpeed() && visible && !isVisible(mBinding.widget.progress);
        mBinding.display.clock.setVisibility(Setting.isDisplayTime() && visible  ? View.VISIBLE : View.GONE);
        mBinding.display.netspeed.setVisibility(showNetSpeed ? View.VISIBLE : View.GONE);
        mBinding.display.duration.setVisibility(Setting.isDisplayDuration() && visible && (mPlayers.isVod()) ? View.VISIBLE : View.GONE);
        mBinding.display.progress.setVisibility(Setting.isDisplayMiniProgress() && visible && (mPlayers.isVod()) ? View.VISIBLE : View.GONE);
        boolean paused = mPlayers.session().state().getPhase() == com.fongmi.android.tv.player.session.PlaybackPhase.PAUSED;
        mBinding.display.titleLayout.setVisibility(paused && Setting.isDisplayVideoTitle() && visible ? View.VISIBLE : View.GONE);
    }

    private void onTimeChangeDisplaySpeed() {
        boolean controlVisible = isVisible(mBinding.control.getRoot());
        boolean pictureMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
        boolean hasDialog = false;
        for (Fragment f : getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) hasDialog = true;
        boolean visible = (!controlVisible || isLock()) && !pictureMode && !hasDialog && !isVisible(mBinding.widget.progress);
        long position = mPlayers.getPosition();
        if (Setting.isDisplaySpeed() && visible) Traffic.setSpeed(mBinding.display.netspeed);
        if (Setting.isDisplayDuration() && visible && position > 0) mBinding.display.duration.setText(mPlayers.getPositionTime(0) + "/" + mPlayers.getDurationTime());
        if (Setting.isDisplayMiniProgress() && visible && position > 0 && (mPlayers.isVod())) mBinding.display.progress.setProgress((int)(position * 100 / mPlayers.getDuration()));
        showDisplayInfo();
    }

    private void toggleFullscreen() {
        if (isFullscreen()) exitFullscreen();
        else enterFullscreen();
    }

    private void enterFullscreen() {
        if (isFullscreen()) return;
        mUiCallbacks.post(() -> mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)), 50);
        setRequestedOrientation(mPlayers.isPortrait() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        mBinding.control.full.setVisibility(View.GONE);
        mDanmakuContext.setScaleTextSize(1.0f * Setting.getDanmuSize());
        setRotate(mPlayers.isPortrait(), true);
        Util.hideSystemUI(this);
        mUiCallbacks.post(mR3, 2000);
        hideControl();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        setRequestedOrientation(isPort() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
        mBinding.control.full.setVisibility(View.VISIBLE);
        mBinding.video.setLayoutParams(mFrameParams);
        mDanmakuContext.setScaleTextSize(0.8f * Setting.getDanmuSize());
        setRotate(false, false);
        mUiCallbacks.post(mR3, 2000);
        hideControl();
    }

    private int getLockOrient() {
        if (isLock()) {
            return ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        } else if (isRotate()) {
            return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        } else if (isPort() && isAutoRotate()) {
            return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        } else {
            return ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        }
    }

    private void showProgress() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        mUiCallbacks.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.widget.progress.setVisibility(View.GONE);
        mUiCallbacks.remove(mR2);
    }

    private void showError(String text) {
        clearPlaybackTimeout();
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showControl() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) return;
        mBinding.control.danmu.setVisibility(isLock() || !mBinding.danmaku.isPrepared() ? View.GONE : View.VISIBLE);
        mBinding.control.danmuSetting.setVisibility(isLock() || !Setting.isDanmuLoad() || !isVisible(mBinding.danmaku) ? View.GONE : View.VISIBLE);
        mBinding.control.setting.setVisibility(mPage.history() == null || isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.batteryInfo.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.keep.setVisibility(mPage.history() == null ? View.GONE : View.VISIBLE);
        mBinding.control.right.back.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(mPlayers.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(mPlayers.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        showDisplayInfo();
        checkPlayImg(mPlayers.isPlaying());
        checkBatteryImg();
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        mUiCallbacks.remove(mR1);
        showDisplayInfo();
    }

    private void hideSheet() {
        for (Dialog dialog : mDialogs) {
            try {
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof BottomSheetDialogFragment) ((BottomSheetDialogFragment) fragment).dismissAllowingStateLoss();
        }
        mDialogs.clear();
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
        mUiCallbacks.post(mR2, Constant.INTERVAL_TRAFFIC);
    }

    private boolean isBuffering() {
        if (mPlaybackFlow == null) return mPlayers.isBuffering();
        VodPlaybackPhase phase = mPlaybackFlow.state().getPhase();
        return phase == VodPlaybackPhase.RESOLVING || phase == VodPlaybackPhase.PREPARING || phase == VodPlaybackPhase.BUFFERING;
    }

    private void setOrient() {
        if (isPort() && isAutoRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        if (isLand() && isAutoRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
    }

    private void setR1Callback() {
        mUiCallbacks.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setArtwork(String url) {
        mArtwork.load(url);
    }

    private void checkFlag(Vod item) {
        if (mPage.history() == null) return;
        boolean empty = mFlagAdapter.isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            mPlayers.reportError(ErrorEvent.flag());
        } else {
            switchFlag(mPage.history().getFlag());
            if (mPage.history().isRevSort()) reverseEpisode(true);
        }
    }

    private void checkHistory(Vod item) { mPage.loadHistory(item, mDetail.token()); }
    private void onPageHistoryReady(History history, Vod item) {
        if (isFinishing() || isDestroyed()) return;
        applyHistory(history); checkFlag(item); checkKeepImg();
    }

    private void applyHistory(History history) {
        mPage.history(history);
        mPlaybackFlow.resetHistoryGate();
        mBinding.control.action.opening.setText(mPage.history().getOpening() == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mPage.history().getOpening()));
        mBinding.control.action.ending.setText(mPage.history().getEnding() == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mPage.history().getEnding()));
        mPlayers.setPlayer(getPlayer());
        replacePlayerSession();
        setScale(getScale());
        setPlayerView();
        setDecodeView();
    }

    private void updateHistory(Episode item) {
        if (getFlag() != null) mHistoryController.commitEpisode(mPage.history(), getFlag().getFlag(), item, mPlaybackFlow.state());
    }

    private void capturePlaybackPosition() {
        long position = getCurrentSwitchPosition();
        mPlaybackFlow.preservePosition(position);
        mPlayers.setPosition(position);
        mHistorySession.save();
    }

    private long getCurrentSwitchPosition() {
        long coordinatorPosition = mPlaybackCoordinator == null ? 0 : mPlaybackCoordinator.currentPosition();
        if (mPlaybackCoordinator != null) {
            if (mPlaybackFlow.state().getEpisode() != null) return coordinatorPosition;
            return mPage.history() == null ? 0 : Math.max(mPage.history().getPosition(), 0);
        }
        long position = mPlayers == null ? 0 : mPlayers.getPosition();
        if (position > 0) return position;
        return mPage.history() == null ? 0 : Math.max(mPage.history().getPosition(), 0);
    }

    private void checkPlayImg(boolean playing) {
        mBinding.control.play.setImageResource(playing ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mPiP.update(this, playing);
        ActionEvent.update();
    }

    private void checkKeepImg() {
        mBinding.control.keep.setImageResource(!mPage.hasFavorite(getHistoryKey()) ? R.drawable.ic_control_keep_off : R.drawable.ic_control_keep_on);
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void checkDanmuImg() {
        mBinding.control.danmu.setImageResource(Setting.isDanmu() ? R.drawable.ic_control_danmu_on : R.drawable.ic_control_danmu_off);
    }

    private void checkBatteryImg() {
        int batteryLevel = Util.batteryLevel();
        int resId = R.drawable.ic_battery_00;
        if (batteryLevel >= 90) resId = R.drawable.ic_battery_full;
        else if (batteryLevel >= 60) resId = R.drawable.ic_battery_75;
        else if (batteryLevel >= 40) resId = R.drawable.ic_battery_50;
        else if (batteryLevel >= 10) resId = R.drawable.ic_battery_25;
        mBinding.control.battery.setImageResource(resId);
    }

    private void createKeep() {
        mPage.createFavorite(getHistoryKey(), getSiteCid(), getSite().getName(),
                mBinding.name.getText().toString(), Objects.toString(mBinding.video.getTag(), ""));
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
        Episode current = mEpisodeAdapter != null && !mEpisodeAdapter.isEmpty() ? getEpisode() : null;
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
    public void onCastEvent(CastEvent event) {
        if (!isCurrentPlayer() || isRedirect()) return;
        ReceiveDialog.create().event(event).show(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (!isCurrentPlayer() || isRedirect()) return;
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
            if (mParseAdapter != null) mParseAdapter.refresh();
            if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.updateParse();
            return;
        }
    }

    private void onPlaybackCommand(PlaybackCommand command) {
        if (!isCurrentPlayer() || isRedirect()) return;
        switch (command.getType()) {
            case DETAIL: takePlaybackControl(); getDetail(); break;
            case RELOAD: takePlaybackControl(); onRefresh(); break;
            case DANMAKU: setDanmaku(Danmaku.from(command.getPath())); break;
            case SUBTITLE: setSubtitle(Sub.from(command.getPath())); break;
        }
    }

    private void renderPlaybackPhase(VodPlaybackState state) {
        if (!isCurrentPlayer() || isRedirect()) return;
        switch (state.getPhase()) {
            case RESOLVING:
                showProgress();
                break;
            case PREPARING:
                setPlayerView();
                setInitTrack(true);
                setTrackVisible(false);
                mClock.setCallback(this);
                break;
            case IDLE:
            case STOPPED:
                break;
            case BUFFERING:
                showProgress();
                break;
            case PAUSED:
                hideProgress();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                checkPlayImg(false);
                showDisplayInfo();
                break;
            case READY:
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                mBinding.display.titleLayout.setVisibility(View.GONE);
                clearPlaybackTimeout();
                stopSearch();
                mBroken.clear();
                checkRotate();
                setMetadata();
                hideProgress();
                mPlayers.reset();
                setDefaultTrack();
                setTrackVisible(true);
                checkPlayImg(true);
                if (mPage.history() != null) mPage.history().setPlayer(mPlayers.getPlayer());
                mBinding.control.size.setText(mPlayers.getSizeText());
                mBinding.display.size.setText(mPlayers.getSizeText());
                if (isVisible(mBinding.control.getRoot())) showControl();
                break;
            case ENDED:
                checkEnded();
                break;
            default:
                break;
        }
    }

    private void checkRotate() {
        if (isFullscreen() && !isRotate() && mPlayers.isPortrait()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            setRotate(true);
        }
    }

    private void checkEnded() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        checkPlayImg(false);
    }

    private void setTrackVisible(boolean visible) {
        mBinding.control.action.text.setVisibility(visible && (mPlayers.haveTrack(C.TRACK_TYPE_TEXT) || mPlayers.isExo()) ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.setTrackVisible();
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
        String title = mPage.history() == null ? mBinding.name.getText().toString() : mPage.history().getVodName();
        Episode current = mEpisodeAdapter.isEmpty() ? null : getEpisode();
        String episode = current == null ? title : current.getName();
        String artist = title.equals(episode) ? "" : getString(R.string.play_now, episode);
        String artwork = mPage.history() == null ? getPic() : mPage.history().getVodPic();
        mPlayers.setMetadata(title, artist, artwork);
    }

    private void onPlaybackError(ErrorEvent event) {
        mPlaybackFlow.onError(event);
    }

    private final class RecoveryPort implements VodRecoveryFlow.Port {
        @Override public boolean mayRecover() { return isCurrentPlayer() && !isRedirect() && (isForeground() || Setting.isBackgroundOn()); }
        @Override public int playerType() { return mPlayers.getPlayer(); }
        @Override public void started() { Notify.show(getString(R.string.play_recovery_running)); }
        @Override public void capturePosition() { capturePlaybackPosition(); }
        @Override public void cancelSourceWork() { mDetail.invalidate(); stopSearch(); }
        @Override public void cancelled(boolean expired) {
            showError(getString(expired ? R.string.play_recovery_expired : R.string.play_recovery_cancelled));
        }
        @Override public void retry() { retryPlayback(); }
        @Override public void toggleDecode() { onDecode(false); }
        @Override public void reloadExo(ErrorEvent error) {
            capturePlaybackPosition();
            mPlaybackFlow.reload(mPlayers.getRecoveryFormat(error));
        }
        @Override public void switchPlayer(ErrorEvent error) { markBrokenCurrent(error); nextPlayer(); }
        @Override public void stopWithError(ErrorEvent error, boolean advancing) {
            if (advancing) markBrokenCurrent(error);
            onErrorPlayer(error);
        }
        @Override public boolean mayChangeSource() { return getSite().isChangeable(); }
        @Override public boolean usesParse() { return isUseParse(); }
        @Override public boolean selectNextParse() {
            int position = mParseAdapter.getPosition();
            if (position <= 0 || position >= mParseAdapter.getItemCount() - 1) return false;
            nextParse(position);
            return true;
        }
        @Override public void resetParse() {
            if (mParseAdapter.getPosition() == mParseAdapter.getItemCount() - 1) initParse();
        }
        @Override public boolean selectNextFlag() {
            if (mFlagAdapter.isEmpty()) return false;
            return nextFlag(isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition());
        }
        @Override public void recoverSource() { checkSearch(mFlagAdapter.isEmpty()); }
        @Override public boolean hasRecoveryWork() { return mSiteSearch.isActive() || !mQuickAdapter.isEmpty(); }
        @Override public boolean isSourceSwitching() { return false; }
        @Override public void continueSourceSwitch() { nextSite(); }
    }

    private void markBrokenCurrent(ErrorEvent event) {
        if (event == null || (!event.isUrl() && !event.isExtract() && !event.isTimeout())) return;
        String key = getBrokenKey();
        if (!TextUtils.isEmpty(key)) mBroken.add(key);
    }

    private void nextPlayer() {
        capturePlaybackPosition();
        mPlaybackFlow.nextPlayer();
    }

    private void retryPlayback() {
        onRefresh();
    }

    private void onErrorPlayer(ErrorEvent event) {
        mBinding.swipeLayout.setEnabled(true);
        TrackRepository.get().delete(getHistoryKey());
        showError(event.getMsg());
        capturePlaybackPosition();
        mClock.setCallback(null);
        mPlayers.reset();
        if (mPlaybackFlow != null) mPlaybackFlow.stop();
        else mPlayers.stop();
        clearDanmakuView();
    }

    private void initParse() {
        if (mParseAdapter.isEmpty()) return;
        setParse(mParseAdapter.first());
    }

    private void checkSearch(boolean force) {
        if (mQuickAdapter.isEmpty()) initSearch(mBinding.name.getText().toString(), true);
        else if (mSiteSearch.isAutomatic() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        mPlaybackFlow.sources().start(keyword, auto);
    }

    private void stopSearch() {
        mPlaybackFlow.sources().finish();
    }

    private void setSearch(Result result) {
        mPlaybackFlow.acceptSourceResult(result);
    }

    private final class SourceSearchPort implements VodSourceSearch.Port {
        @Override public boolean accepts(Result result) { return mSiteSearch.accepts(result); }
        @Override public String expectedKeyword() { return mSourceKeyword; }
        @Override public String sourceSwitchKeyword() { return mSourceKeyword; }
        @Override public boolean automatic() { return mSiteSearch.isAutomatic(); }
        @Override public String sourceKey(Vod vod) { return getBrokenKey(vod); }
        @Override public void mergeCandidates(List<Vod> items, String token) {
            mBinding.quick.setVisibility(View.VISIBLE);
            mQuickAdapter.addAllSorted(items, SearchSorter.comparator(mSourceKeyword, ""));
        }
        @Override public void cancelEmptyFallback() { mUiCallbacks.remove(mR4); }
        @Override public void removeCandidate(Vod vod) {
            for (int i = 0; i < mQuickAdapter.getItemCount(); i++) {
                if (mQuickAdapter.get(i) == vod) { mQuickAdapter.remove(i); return; }
            }
        }
        @Override public void beginSourceSwitch() {
            mPlaybackFlow.sourceSwitch().search(mPage.history() == null ? "" : mPage.history().getVodRemarks(), getFlag());
            mPlaybackFlow.intent().keepCurrentPositionOnNextPlay();
        }
        @Override public void markCurrentSourceBroken() { markBrokenCurrent(ErrorEvent.url(0)); }
        @Override public void setManual() { mSiteSearch.setManual(); }
        @Override public void startSourceLoading() { mPlaybackFlow.sourceSwitch().startLoading(); }
        @Override public void openDetail(Vod vod) { getDetail(vod); }
        @Override public int candidateCount() { return mQuickAdapter.getItemCount(); }
        @Override public void selectNextCandidate() { nextSite(); }
        @Override public void cancelSearch() { if (mSiteSearch != null) mSiteSearch.cancel(); }
        @Override public void clearCandidates() { mQuickAdapter.clear(); }
        @Override public void setSearchKeyword(String keyword) { mSourceKeyword = keyword; }
        @Override public void startSearch(List<Site> sites, String keyword, boolean automatic, Runnable settled) {
            mPlaybackFlow.sourceSwitch().resumeSearch();
            mSiteSearch.start(sites, keyword, automatic, settled);
        }
        @Override public boolean loading() { return mPlaybackFlow.sourceSwitch().isLoading(); }
        @Override public boolean hasPending() { return mSiteSearch.hasPending(); }
        @Override public boolean shouldSelectFirst() { return mSiteSearch.shouldSelectFirst(); }
        @Override public boolean switching() { return mPlaybackFlow.sourceSwitch().isActive(); }
        @Override public boolean earlyThresholdReached() { return mQuickAdapter.getItemCount() >= 10; }
        @Override public void flushCandidates() { }
        @Override public void clearSourceSwitch() { mPlaybackFlow.sourceSwitch().clear(); }
        @Override public void empty(boolean automatic) { if (automatic) showEmpty(); }
    }

    private String getBrokenKey(Vod item) {
        return SearchSorter.key(item);
    }

    private String getBrokenKey() {
        return SearchSorter.key(getKey(), getId(), mBinding.name.getText().toString());
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        onItemClick(parse);
    }

    private boolean nextFlag(int position) {
        boolean singleEpisode = getFlag() != null && getFlag().getEpisodes().size() == 1;
        String episode = mPage.history() == null ? "" : mPage.history().getVodRemarks();
        for (int next = position + 1; next < mFlagAdapter.getItemCount(); next++) {
            Flag flag = mFlagAdapter.get(next);
            if (!VodSourceMatcher.canResume(flag, episode, singleEpisode)) continue;
            Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
            switchFlag(flag);
            return true;
        }
        return false;
    }

    private void nextSite() {
        Vod item = SearchSorter.pollMatchingSource(() -> mQuickAdapter.poll(mBroken, getBrokenKey()), mBinding.name.getText().toString());
        if (item == null) {
            if (!mSiteSearch.isActive() || !mSiteSearch.hasPending()) showEmpty();
            return;
        }
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mBroken.add(getBrokenKey());
        mSiteSearch.markSelected();
        mPlaybackFlow.sourceSwitch().search(mPage.history() == null ? "" : mPage.history().getVodRemarks(), getFlag());
        mPlaybackFlow.sourceSwitch().startLoading();
        mPlaybackFlow.intent().keepCurrentPositionOnNextPlay();
        getDetail(item);
    }

    private void onPaused() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        checkPlayImg(false);
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
    }

    private void onPlay() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        checkPlayImg(true);
        hidePreview();
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.PLAY));
    }

    public boolean isForeground() {
        return foreground;
    }

    private boolean isCurrentPlayer() {
        return mPlayers.isCurrent();
    }

    public void setForeground(boolean foreground) {
        this.foreground = foreground;
    }

    private void setPendingDetailRequest(String token) {
        mDetail.expect(token, getKey(), getId());
        mPage.begin(token, getHistoryKey(), getSiteCid(), getMark(), Setting.getPlaySpeed(), Setting.isIncognito());
    }

    private void clearPlaybackTimeout() {
        if (mPlaybackCoordinator != null) mPlaybackCoordinator.clearTimeout();
    }



    private boolean isCurrentDetailResult(Result result) {
        return mDetail.accepts(result);
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, this.fullscreen = fullscreen);
    }

    private boolean isInitTrack() {
        return initTrack;
    }

    private void setInitTrack(boolean initTrack) {
        this.initTrack = initTrack;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isRedirect() {
        return redirect;
    }

    public void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate, boolean fullscreen) {
        this.rotate = rotate;
        setFullscreen(fullscreen);
        if (!fullscreen || rotate) noPadding(mBinding.control.getRoot());
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (fullscreen && rotate) noPadding(mBinding.control.getRoot());
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public boolean isLock() {
        return lock;
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    private void stopService() {
        PlaybackService.stop(mPlayers);
    }

    @Override
    public void onCasted() {
        onPaused();
        showPreview(getDefaultArtwork());
    }

    @Override
    public void onScale(int tag) {
        if (mPage.history() == null) return;
        mPage.history().setScale(tag);
        setScale(tag);
    }

    @Override
    public void onParse(Parse item) {
        takePlaybackControl();
        onItemClick(item);
    }

    @Override
    public void onSpeedUp() {
        if (!mPlayers.isPlaying() || !mPlayers.canAdjustSpeed()) return;
        if (Float.isNaN(mSpeedBeforeLongPress)) mSpeedBeforeLongPress = mPlayers.getSpeed();
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        setDanmuViewSettings();
        
        
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.widget.speed.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        if (!Float.isNaN(mSpeedBeforeLongPress)) mBinding.control.action.speed.setText(mPlayers.setSpeed(mSpeedBeforeLongPress));
        mSpeedBeforeLongPress = Float.NaN;
        setDanmuViewSettings();
        
        
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
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
    public void onSeek(int time) {
        Source.get().cancelPreloads();
        stopSearch();
        mRecommendations.setReady(false);
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(mPlayers.stringToTime(mSeekGesture.preview(mPlayers.getPosition(), mPlayers.getDuration(), time)));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(int time) {
        mBinding.widget.seek.setVisibility(View.GONE);
        if (isBuffering()) showProgress();
        if (time == 0) {
            mSeekGesture.reset();
            updateRecommendationReadiness();
            return;
        }
        // Seeking preserves the engine's play intent, including while buffering.
        mPlaybackFlow.dispatch(PlaybackInput.seek(mSeekGesture.commit(mPlayers.getPosition(), mPlayers.getDuration(), time)));
        updateRecommendationReadiness();
    }

    @Override
    public void onSeekCancel() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mSeekGesture.reset();
        if (isBuffering()) showProgress();
        updateRecommendationReadiness();
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onDoubleTap() {
        if (!isFullscreen()) {
            mUiCallbacks.post(this::enterFullscreen, 250);
        } else if (mPlayers.isPlaying()) {
            onPaused();
        } else {
            hideControl();
            onPlay();
        }
    }

    @Override
    public void onShare(CharSequence title) {
        boolean idm = IDMUtil.downloadFile(this, UrlUtil.fixDownloadUrl(mPlayers.getUrl()), title.toString(), mPlayers.getHeaders(), false, false);
        if (!idm) mPlayers.share(this, title);
        setRedirect(true);
    }

    @Override
    public void onPlayerClick(Integer item) {
        mPlaybackFlow.dispatch(PlaybackInput.player(item));
    }

    @Override
    public void onPlayerShare(String title) {
        this.onShare(title);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQUEST_DANMAKU_FILE && data != null && data.getData() != null
                && mPlaybackFlow.acceptsRequest(mDanmakuPickerGeneration)) {
            setDanmaku(Danmaku.from(FileChooser.getPathFromUri(this, data.getData())));
            return;
        }
        mPlayers.checkData(data);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isRedirect()) return;
        if (isLock()) mUiCallbacks.post(this::onLock, 500);
        if (mPlayers.haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, mPlayers.getVideoWidth(), mPlayers.getVideoHeight(), getScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (!isFullscreen()) setVideoView(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            PlaybackService.start(mPlayers);
            mBinding.danmaku.hide();
            hideControl();
            hideSheet();
        } else {
            showDanmu();
            mUiCallbacks.post(mR0, 1000);
            setForeground(true);
            if (isStop()) finish();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isAutoRotate() && isPort() && newConfig.orientation == 1 && !isRotate()) exitFullscreen();
        if (isAutoRotate() && isPort() && newConfig.orientation == 2) enterFullscreen();
        if (isFullscreen()) Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        setStop(false);
        mPlaybackFlow.setPlaybackAllowed(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isForeground()) return;
        mPlaybackLifecycle.resume();
        mUiCallbacks.post(mR0, 1000);
        setForeground(true);
        setRedirect(false);
        if (mPlayers.isPlaying()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause() {
        mKeyDown.release();
        mSeekGesture.reset();
        super.onPause();
        setForeground(false);
        mUiCallbacks.remove(mR0);
        saveHistoryNow();
        if (isRedirect() || Setting.isBackgroundOff()) mPlaybackLifecycle.pause();
        if (!isRedirect() && Setting.isBackgroundOn() && !isFinishing()) PlaybackService.start(mPlayers);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Setting.isBackgroundOff()) mPlaybackFlow.setPlaybackAllowed(false);
        if (Setting.isBackgroundOff()) mClock.stop();
        setStop(true);
    }

    private void takePlaybackControl() {
        mPlaybackFlow.recovery().takeControl();
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (!isLock()) {
            stopSearch();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        mUiCallbacks.close();
        mArtwork.close();
        mPlaybackFlow.recovery().close();
        mRecommendations.close();
        saveHistoryNow();
        super.onDestroy();
        stopSearch();
        clearPlaybackTimeout();
        mPage.close();
        mClock.release();
        if (mPlaybackSubscription != null) mPlaybackSubscription.cancel();
        mPlayers.setInputHandler(null);
        if (mPlaybackFlow != null) mPlaybackFlow.close();
        if (mPlaybackCoordinator != null) mPlaybackCoordinator.close();
        if (mVodResolver != null) mVodResolver.close();
        mPlayers.release();
        Timer.get().reset();
        PlaybackController owner = mPlayers;
        App.post(() -> PlaybackService.stop(owner), 1000);
        RefreshEvent.history();
        mUiCallbacks.remove(mR1, mR2, mR3, mR4);
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
            mRecommendAdapter.addAll(items);
            mBinding.recommendGroup.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        });
        updateRecommendationReadiness();
    }

    private boolean canCommitCurrentHistory() {
        return mHistorySession != null && mHistorySession.canCommit();
    }

    private void saveHistoryNow() {
        if (mHistorySession != null) mHistorySession.save();
    }

}
