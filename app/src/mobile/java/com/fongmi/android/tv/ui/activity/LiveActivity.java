package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.repository.KeepRepository;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.databinding.ActivityLiveBinding;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.PlaybackCommand;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.LiveCallback;
import com.fongmi.android.tv.impl.PassCallback;
import com.fongmi.android.tv.model.LiveViewModel;
import com.fongmi.android.tv.ui.dialog.LiveHistoryDialog;
import com.fongmi.android.tv.player.live.LiveChannelNavigator;
import com.fongmi.android.tv.player.live.LiveDataConnection;
import com.fongmi.android.tv.ui.custom.LiveListLayout;
import com.fongmi.android.tv.player.IjkUtil;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.ui.custom.PlaybackVideoViews;
import com.fongmi.android.tv.ui.custom.PlaybackSubtitleLauncher;
import com.fongmi.android.tv.ui.custom.PlaybackArtwork;
import com.fongmi.android.tv.player.PlaybackSeekGesture;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.player.DirectPlaybackFlow;
import com.fongmi.android.tv.player.DirectPlaybackLifecycle;
import com.fongmi.android.tv.player.PlaybackInput;
import com.fongmi.android.tv.utils.MainThreadExecutor;
import com.fongmi.android.tv.utils.CallbackScope;
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.ChannelAdapter;
import com.fongmi.android.tv.ui.adapter.EpgDataAdapter;
import com.fongmi.android.tv.ui.adapter.GroupAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyDownLive;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.PassDialog;
import com.fongmi.android.tv.ui.dialog.PlayerDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Biometric;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.IDMUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class LiveActivity extends BaseActivity implements Clock.Callback, CustomKeyDownLive.Listener, TrackDialog.Listener, PlayerDialog.Listener, Biometric.Callback, PassCallback, LiveCallback, GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    private ActivityLiveBinding mBinding;
    private ChannelAdapter mChannelAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private CustomKeyDownLive mKeyDown;
    private float mSpeedBeforeLongPress = Float.NaN;
    private GroupAdapter mGroupAdapter;
    private LiveViewModel mViewModel;
    private LiveDataConnection mLiveData;
    private LiveChannelNavigator mNavigation;
    private LiveListLayout mListLayout;
    private DirectPlaybackFlow mPlaybackFlow;
    private DirectPlaybackLifecycle mPlaybackLifecycle;
    private final CallbackScope mUiCallbacks = new CallbackScope(AppTaskScheduler.get());
    private List<Group> mHides;
    private PlaybackController mPlayers;
    private Channel mChannel;
    private boolean historicalReplay;
    private Group mGroup;
    private Runnable mR0;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Clock mClock;
    private boolean foreground;
    private boolean redirect;
    private boolean rotate;
    private boolean stop;
    private boolean lock;
    private final PlaybackSeekGesture mSeekGesture = new PlaybackSeekGesture();
    private int passCount;
    private PiP mPiP;

    public static void start(Context context) {
        if (!LiveConfig.isEmpty()) context.startActivity(new Intent(context, LiveActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("empty", false));
    }

    private boolean isEmpty() {
        return getIntent().getBooleanExtra("empty", true);
    }

    private PlayerView getExo() {
        return mBinding.exo;
    }

    private IjkVideoView getIjk() {
        return mBinding.ijk;
    }

    private Group getKeep() {
        return mGroupAdapter.get(0);
    }

    private Live getHome() {
        return LiveConfig.get().getHome();
    }

    private int getTimeout() {
        return getHome().isEmpty() ? Constant.TIMEOUT_PLAY : getHome().getTimeout();
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.hideSystemUI(this);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mKeyDown = CustomKeyDownLive.create(this, mBinding.video);
        mClock = Clock.create(Arrays.asList(mBinding.widget.clock, mBinding.display.clock));
        setPadding(mBinding.control.getRoot());
        setPadding(mBinding.widget.epg, true);
        setPadding(mBinding.recycler, true);
        mPlayers = PlaybackController.create(this, this::onPlaybackError, this::onPlaybackCommand);
        mPlayers.setForegroundSource(this::isForeground);
        setPlaybackFlow();
        mHides = new ArrayList<>();
        mR0 = this::stopService;
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::hideInfo;
        mPiP = new PiP().owner(mPlayers);
        Server.get().start();
        setForeground(true);
        setRecyclerView();
        setVideoView();
        setDisplayView();
        setViewModel();
        checkLive();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.control.seek.setListener(mPlayers);
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.right.back.setOnClickListener(view -> onBack());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.home.setOnClickListener(view -> onHome());
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        mBinding.control.action.change.setOnClickListener(view -> onChange());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.player.setOnLongClickListener(view -> onChoose());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.widget.error.setOnClickListener(view -> fetch());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
    }

    private void setRecyclerView() {
        mListLayout = new LiveListLayout(mBinding.group, mBinding.channel, mBinding.widget.epgData, mBinding.divide, false);
        mNavigation = new LiveChannelNavigator(new LiveChannelNavigator.Port() {
            @Override public Group currentGroup() { return mGroup; }
            @Override public int groupCount() { return mGroupAdapter.getItemCount(); }
            @Override public int selectedGroup() { return mGroupAdapter.getPosition(); }
            @Override public Group groupAt(int index) { return mGroupAdapter.get(index); }
            @Override public void selectGroup(int index, Group group) {
                mGroup = group;
                mGroupAdapter.setSelected(index);
                mChannelAdapter.addAll(group.getChannel());
            }
            @Override public void play(Channel channel) { onItemClick(channel); }
        });

        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.widget.epgData.setItemAnimator(null);
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.widget.epgHistory.setOnClickListener(view -> showHistory());
        mBinding.widget.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
    }

    private void setPlayerView() {
        PlaybackVideoViews.render(mPlayers, getExo(), getIjk(), mBinding.control.action.player, mBinding.control.action.speed);
        mBinding.control.action.speed.setText(mPlayers.getSpeedText());
    }

    private void setDecodeView() {
        mBinding.control.action.decode.setText(mPlayers.getDecodeText());
    }

    private void setVideoView() {
        mPlayers.init(getExo(), getIjk());
        setScale(Setting.getLiveScale());
        ExoUtil.setSubtitleView(mBinding.exo);
        IjkUtil.setSubtitleView(mBinding.ijk);
        mBinding.control.action.invert.setActivated(Setting.isInvert());
        mBinding.control.action.across.setActivated(Setting.isAcross());
        mBinding.control.action.change.setActivated(Setting.isChange());
        mBinding.control.action.home.setVisibility(LiveConfig.isOnly() ? View.GONE : View.VISIBLE);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(getActivity(), view));
    }

    private void setDisplayView() {
        mBinding.display.getRoot().setVisibility(View.VISIBLE);
        mBinding.display.progress.setVisibility(View.GONE);
        showDisplayInfo();
    }

    private void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mLiveData = new LiveDataConnection(this, LiveDataConnection.source(mViewModel), true, new LiveDataConnection.Port() {
            @Override public Channel channel() { return mChannel; }
            @Override public void play(Channel channel) { mPlayers.start(channel, getTimeout()); }
            @Override public void renderEpg() { setEpg(); }
            @Override public void renderLive(Live live) {
                hideProgress();
                setGroup(live);
                mListLayout.render(live);
                if (live.getLoadError() != null) showError(live.getLoadError());
            }
        });
        mLiveData.connect();
    }

    private void checkLive() {
        if (isEmpty()) {
            LiveConfig.reload(getCallback());
        } else {
            getLive();
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                getLive();
            }

            @Override
            public void error(String msg) {
                if (mLiveData != null && !mLiveData.isClosed()) Notify.show(msg);
            }
        };
    }

    private void getLive() {
        if (mLiveData == null || mLiveData.isClosed()) return;
        mBinding.control.action.home.setText(getHome().getName());
        mPlayers.setPlayer(Setting.getLivePlayer());
        mViewModel.getLive(getHome());
        setPlayerView();
        setDecodeView();
        showProgress();
    }

    private void setGroup(Live live) {
        List<Group> items = new ArrayList<>();
        mHides.clear();
        for (Group group : live.getGroups()) (group.isHidden() ? mHides : items).add(group);
        mGroupAdapter.addAll(items);
        setPosition(LiveConfig.get().find(items));
    }



    @Override
    public void setWidth(Group group) { mListLayout.render(group); }

    private void setWidth(Epg epg) { mListLayout.render(epg); }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.getItemCount();
        if (size == 0 || position[0] >= size) return;
        mGroup = mGroupAdapter.get(position[0]);
        mGroup.setPosition(position[1]);
        onItemClick(mGroup);
        onItemClick(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mGroupAdapter.getPosition() != position;
        if (change) mGroupAdapter.setSelected(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel());
        if (change) mChannelAdapter.setSelected(mGroup.getPosition());
        mBinding.channel.scrollToPosition(mGroup.getPosition());
        mBinding.group.scrollToPosition(position);
    }

    private void onCast() {
        CastDialog.create().video(CastVideo.get(mBinding.control.title.getText().toString(), mPlayers.getUrl())).fm(false).show(this);
    }

    private void onInfo() {
        InfoDialog.create(this).title(mBinding.control.title.getText()).headers(mPlayers.getHeaders()).url(mPlayers.getUrl()).show();
    }

    private void onBack() {
        finish();
    }

    private void onLock() {
        setLock(!isLock());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onRotate() {
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void checkPlay() {
        if (mPlayers.isPlaying()) onPaused();
        else onPlay();
    }

    private void onTrack(View view) {
        TrackDialog.create().player(mPlayers).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onHome() {
        LiveDialog.create(this).show();
        hideControl();
    }

    private void onLine() {
        nextLine(false, true);
    }

    private void onScale() {
        int index = Setting.getLiveScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        Setting.putLiveScale(index = index == array.length - 1 ? 0 : ++index);
        setScale(index);
        setR1Callback();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(mPlayers.addSpeed());
        setR1Callback();
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(mPlayers.toggleSpeed());
        setR1Callback();
        return true;
    }

    private void onInvert() {
        setR1Callback();
        Setting.putInvert(!Setting.isInvert());
        mBinding.control.action.invert.setActivated(Setting.isInvert());
    }

    private void onAcross() {
        setR1Callback();
        Setting.putAcross(!Setting.isAcross());
        mBinding.control.action.across.setActivated(Setting.isAcross());
    }

    private void onChange() {
        setR1Callback();
        Setting.putChange(!Setting.isChange());
        mBinding.control.action.change.setActivated(Setting.isChange());
    }

    private void onPlayer() {
        PlayerDialog.create().select(mPlayers.getPlayer()).title(mBinding.control.title.getText().toString()).show(this);
        hideControl();
    }

    private void onDecode() {
        onDecode(true);
    }

    private void onDecode(boolean save) {
        mPlaybackFlow.dispatch(PlaybackInput.decode(save));
    }

    private void applyDecode(boolean save) {
        mPlayers.toggleDecode(save);
        mPlayers.init(getExo(), getIjk());
        mPlayers.setMediaSource();
        setDecodeView();
        setR1Callback();
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
        if (e.getAction() == MotionEvent.ACTION_UP) setR1Callback();
        return false;
    }

    private void hideUI() {
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.channel.requestFocus();
        setPosition();
        hideEpg();
    }

    private void showEpg(Channel item) {
        if (mChannel == null || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        boolean history = hasHistory(mChannel);
        if (mChannel.getData().getList().isEmpty() && !history) return;
        setEpg();
        mBinding.widget.epgHistory.setVisibility(history ? View.VISIBLE : View.GONE);
        int position = mChannel.getData().getSelected();
        if (position >= 0) mBinding.widget.epgData.scrollToPosition(position);
        mBinding.widget.epg.setVisibility(View.VISIBLE);
        hideUI();
    }

    private boolean hasHistory(Channel channel) {
        return mViewModel.hasHistory(LiveConfig.get().getHome(), channel);
    }

    private void showHistory() {
        Channel channel = mChannel;
        if (channel == null || !hasHistory(channel)) return;
        int line = channel.getLine();
        Live source = LiveConfig.get().getHome();
        LiveHistoryDialog.show(this, mViewModel, source, channel, program -> {
            if (mChannel == channel && channel.getLine() == line && LiveConfig.get().getHome() == source) replayProgram(program, true);
        });
    }

    private void hideEpg() {
        mBinding.widget.epg.setVisibility(View.GONE);
    }

    void showProgress() {
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
        mBinding.control.info.setVisibility(mPlayers.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(mPlayers.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.right.rotate.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.right.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        setR1Callback();
        hideInfo();
        hideEpg();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        mUiCallbacks.remove(mR1);
    }

    private void showDisplayInfo() {
        boolean controlVisible = isVisible(mBinding.control.getRoot()) || isVisible(mBinding.widget.info);
        boolean visible = (!controlVisible && !isLock());
        boolean showNetSpeed = Setting.isDisplaySpeed() && visible && !isVisible(mBinding.widget.progress);
        mBinding.display.clock.setVisibility(Setting.isDisplayTime() && visible  ? View.VISIBLE : View.GONE);
        mBinding.display.netspeed.setVisibility(showNetSpeed ? View.VISIBLE : View.GONE);
        mBinding.display.duration.setVisibility(View.GONE);
        mBinding.display.titleLayout.setVisibility(Setting.isDisplayVideoTitle()&& visible ? View.VISIBLE : View.GONE);
    }

    private void onTimeChangeDisplaySpeed() {
        boolean controlVisible = isVisible(mBinding.control.getRoot()) || isVisible(mBinding.widget.info);
        boolean visible = (!controlVisible && !isLock() && !isVisible(mBinding.widget.progress));
        if (Setting.isDisplaySpeed() && visible) Traffic.setSpeed(mBinding.display.netspeed);
        showDisplayInfo();
    }

    private void showInfo() {
        boolean pip = Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInPictureInPictureMode();
        mBinding.widget.infoPip.setVisibility(pip ? View.VISIBLE : View.GONE);
        mBinding.widget.info.setVisibility(pip ? View.GONE : View.VISIBLE);
        setR3Callback();
        hideControl();
        hideEpg();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.infoPip.setVisibility(View.GONE);
        mBinding.widget.info.setVisibility(View.GONE);
        mUiCallbacks.remove(mR3);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        mUiCallbacks.post(mR2, Constant.INTERVAL_TRAFFIC);
    }

    private void setR1Callback() {
        mUiCallbacks.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR3Callback() {
        mUiCallbacks.post(mR3, Constant.INTERVAL_HIDE);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else showUI();
        hideInfo();
    }

    private void resetPass() {
        this.passCount = 0;
    }

    private final PlaybackArtwork mArtwork = new PlaybackArtwork(new PlaybackArtwork.Listener() {
        @Override public void loaded(Drawable drawable) { applyArtwork(drawable); }
        @Override public void failed(Drawable drawable) { applyArtwork(drawable); }
    });

    private void applyArtwork(Drawable drawable) {
        getExo().setDefaultArtwork(drawable);
        getIjk().setDefaultArtwork(drawable);
    }

    private void setArtwork(String url) { mArtwork.load(url); }

    @Override
    public void onItemClick(Group item) {
        mGroupAdapter.setSelected(mGroup = item);
        mChannelAdapter.addAll(item.getChannel());
        mChannelAdapter.setSelected(item.getPosition());
        mBinding.channel.scrollToPosition(Math.max(item.getPosition(), 0));
        if (!item.isKeep() || ++passCount < 5 || mHides.isEmpty()) return;
        if (Biometric.enable()) Biometric.show(this);
        else PassDialog.create().show(this);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        if (item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)
                && (!mChannel.getData().getList().isEmpty() || hasHistory(mChannel))) {
            showEpg(item);
        } else {
            mPlaybackFlow.beginSource();
            mGroup.setPosition(mChannelAdapter.setSelected(item.group(mGroup)));
            mPlayers.setPlayer(Setting.getLivePlayer());
            setArtwork(item.getLogo());
            mChannel = item;
            setPlayerView();
            showInfo();
            hideUI();
            fetch();
        }
    }

    @Override
    public boolean onLongClick(Channel item) {
        if (mGroup.isHidden()) return false;
        boolean exist = Keep.exist(item.getName());
        Notify.show(exist ? R.string.keep_del : R.string.keep_add);
        if (exist) delKeep(item);
        else addKeep(item);
        return true;
    }

    @Override
    public void onItemClick(EpgData item) {
        replayProgram(item, false);
    }

    private void replayProgram(EpgData item, boolean historical) {
        mLiveData.replay(item, () -> {
            mPlaybackFlow.beginSource();
            Notify.show(getString(R.string.play_ready, item.getTitle()));
            historicalReplay = historical;
            if (!historical) mEpgDataAdapter.setSelected(item);
            mPlayers.clear();
            mPlayers.stop();
            showProgress();
            hideEpg();
        });
    }

    private void addKeep(Channel item) {
        getKeep().add(item);
        Keep keep = new Keep();
        keep.setKey(item.getName());
        keep.setType(1);
        KeepRepository.get().save(keep);
    }

    private void delKeep(Channel item) {
        if (mGroup.isKeep()) mChannelAdapter.remove(item);
        getKeep().getChannel().remove(item);
        KeepRepository.get().delete(item.getName());
    }

    private void setInfo() {
        mLiveData.refreshEpg();
        mBinding.widget.play.setText("");
        mChannel.loadLogo(mBinding.widget.logo);
        mBinding.widget.name.setText(mChannel.getName());
        mBinding.control.title.setText(mChannel.getName());
        mBinding.display.title.setText(mChannel.getName());
        mBinding.widget.namePip.setText(mChannel.getName());
        mBinding.widget.line.setText(mChannel.getLineText());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.widget.numberPip.setText(mChannel.getNumber());
        mBinding.widget.name.setMaxEms(mChannel.getName().length());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setText(mBinding.widget.line.getText());
        mBinding.control.action.line.setVisibility(mBinding.widget.line.getVisibility());
    }

    private void setEpg() {
        String epg = mChannel.getData().getEpg();
        List<EpgData> data = mChannel.getData().getList();
        if (epg.length() > 0) mBinding.widget.name.setMaxEms(12);
        mBinding.widget.play.setText(epg);
        mChannelAdapter.changed(mChannel);
        mEpgDataAdapter.addAll(data);
        setWidth(mChannel.getData());
        setMetadata();
    }





    private void fetch() {
        if (mChannel == null) return;
        historicalReplay = false;
        LiveConfig.get().setKeep(mChannel);
        mLiveData.loadChannel();
        mPlayers.clear();
        mPlayers.stop();
        showProgress();
    }



    private void checkPlayImg(boolean playing) {
        mPiP.update(this, playing);
        ActionEvent.update();
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void resetAdapter() {
        mListLayout.reset();
        mEpgDataAdapter.clear();
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
    }

    @Override
    public void onTrackClick(Track item) {
    }

    @Override
    public void onSubtitleClick() {
        long request = mPlaybackFlow.generation();
        mUiCallbacks.post(this::hideControl, 200);
        SubtitleView view = mPlayers.isIjk() ? getIjk().getSubtitleView() : getExo().getSubtitleView();
        PlaybackSubtitleLauncher.show(this, mUiCallbacks, view, "", true,
                () -> mPlaybackFlow.accepts(request), mPlayers::setSub);
    }

    @Override
    public void onTimeChanged() {
        onTimeChangeDisplaySpeed();
    }

    @Override
    public void setLive(Live item) {
        if (item.isActivated()) item.getGroups().clear();
        LiveConfig.get().setHome(item);
        mPlayers.reset();
        mPlayers.stop();
        resetAdapter();
        hideControl();
        getLive();
    }

    @Override
    public void setPass(String pass) {
        unlock(pass);
    }

    @Override
    public void onBiometricSuccess() {
        unlock(null);
    }

    private void unlock(String pass) {
        boolean first = true;
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(item);
            if (first) onItemClick(item);
            iterator.remove();
            first = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (!isCurrentPlayer()) return;
        if (ActionEvent.PLAY.equals(event.getAction())) {
            onPlay();
        } else if (ActionEvent.PAUSE.equals(event.getAction())) {
            onPaused();
        } else if (ActionEvent.NEXT.equals(event.getAction())) {
            nextChannel();
        } else if (ActionEvent.PREV.equals(event.getAction())) {
            prevChannel();
        } else if (ActionEvent.STOP.equals(event.getAction())) {
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (!isCurrentPlayer()) return;
        switch (event.getType()) {
            case LIVE:
                setLive(getHome());
                break;
        }
    }

    private void onPlaybackCommand(PlaybackCommand command) {
        if (isCurrentPlayer() && command.getType() == PlaybackCommand.Type.RELOAD) fetch();
    }

    private void onPlaybackState(PlaybackState state) {
        if (!isCurrentPlayer()) return;
        switch (state.getPhase()) {
            case PREPARING:
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
                checkPlayImg(false);
                break;
            case READY:
                setMetadata();

                hideProgress();
                mPlayers.reset();
                setTrackVisible(true);
                checkPlayImg(true);
                mBinding.control.size.setText(mPlayers.getSizeText());
                mBinding.display.size.setText(mPlayers.getSizeText());
                if (isVisible(mBinding.control.getRoot())) showControl();
                break;
            case ENDED:
                break;
            default:
                break;
        }
    }

    private void setTrackVisible(boolean visible) {
        mBinding.control.action.text.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_TEXT) ? View.VISIBLE : View.GONE);
        mBinding.control.action.speed.setVisibility(visible && mPlayers.isVod() ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    private void setMetadata() {
        String title = mBinding.widget.name.getText().toString();
        String artist = mBinding.widget.play.getText().toString();
        mPlayers.setMetadata(title, artist, mChannel.getLogo());
    }

    private void setPlaybackFlow() {
        mPlaybackFlow = new DirectPlaybackFlow(mPlayers, new DirectPlaybackFlow.Port() {
            @Override public void render(PlaybackState state) { onPlaybackState(state); }
            @Override public void navigate(boolean next) { if (next) nextChannel(); else prevChannel(); }
            @Override public void finish() { LiveActivity.this.finish(); }
            @Override public void selectPlayer(int player) { selectPlaybackPlayer(player); }
            @Override public void decode(boolean persist) { applyDecode(persist); }
            @Override public boolean hasPlayableItem() { return mChannel != null; }
            @Override public void retry() { fetch(); }
            @Override public void reloadExo(ErrorEvent error) { mPlayers.recoverExo(error); }
            @Override public void switchPlayer() { nextPlayer(); }
            @Override public void advance(ErrorEvent error) { onError(error); }
            @Override public void error(ErrorEvent error) { onErrorPlayer(error); }
            @Override public void ended() { if (mChannel != null) checkNext(); }
        }, MainThreadExecutor.get());
        mPlaybackLifecycle = new DirectPlaybackLifecycle(mPlaybackFlow, new DirectPlaybackLifecycle.Port() {
            @Override public void acquireOwnership() { mPlayers.activateAsCurrentPlayer(); }
            @Override public boolean needsEngine() { return mPlayers.isRelease(); }
            @Override public void rebuildEngine() {
                mPlayers.init(getExo(), getIjk());
                setPlayerView();
                setDecodeView();
            }
            @Override public void restoreSource() {
                if (mPlayers.isEmpty()) { fetch(); }
                else mPlayers.restoreCheckpoint(mPlaybackFlow.checkpoint());
            }
        });
        mPlayers.setInputHandler(mPlaybackFlow);
    }

    private void onPlaybackError(ErrorEvent event) {
        mPlaybackFlow.onError(event);
    }

    private void nextPlayer() {
        mPlayers.nextPlayer();
        setPlayerView();
        fetch();
    }

    private void onErrorPlayer(ErrorEvent event) {
        showError(event.getMsg());
        mPlayers.reset();
        mPlayers.stop();
    }

    private void onError(ErrorEvent event) {
        onErrorPlayer(event);
        startFlow();
    }

    private void startFlow() {
        if (!Setting.isChange()) return;
        if (!mChannel.isLast()) {
            nextLine(true, false);
        } else if (isGone(mBinding.recycler)) {
            mChannel.setLine(0);
            nextChannel();
        }
    }





    private void prevChannel() { mNavigation.channel(false, Setting.isAcross()); }

    private void nextChannel() { mNavigation.channel(true, Setting.isAcross()); }

    private void checkNext() {
        if (historicalReplay) return;
        int current = mChannel.getData().getInRange();
        int position = mChannel.getData().getSelected() + 1;
        boolean hasNext = position <= current && position > 0;
        if (hasNext) onItemClick(mChannel.getData().getList().get(position));
        else nextChannel();
    }

    private void prevLine() {
        if (mChannel == null || mChannel.isOnly()) return;
        mPlaybackFlow.beginSource();
        mChannel.prevLine();
        showInfo();
        fetch();
    }

    private void nextLine(boolean show, boolean userSelected) {
        if (mChannel == null || mChannel.isOnly()) return;
        if (userSelected) mPlaybackFlow.beginSource();
        mChannel.nextLine();
        if (show) showInfo();
        else setInfo();
        fetch();
    }

    private void onPaused() {
        checkPlayImg(false);
        mPlayers.pause();
    }

    private void onPlay() {
        checkPlayImg(true);
        mPlayers.play();
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

    public boolean isRedirect() {
        return redirect;
    }

    public void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (rotate) {
            noPadding(mBinding.recycler);
            noPadding(mBinding.widget.epg);
            noPadding(mBinding.control.getRoot());
        } else {
            setPadding(mBinding.recycler, true);
            setPadding(mBinding.widget.epg, true);
            setPadding(mBinding.control.getRoot());
        }
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
    }

    @Override
    public void onSpeedUp() {
        if (!mPlayers.isVod() || !mPlayers.isPlaying() || !mPlayers.canAdjustSpeed()) return;
        if (Float.isNaN(mSpeedBeforeLongPress)) mSpeedBeforeLongPress = mPlayers.getSpeed();
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.widget.speed.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        if (!Float.isNaN(mSpeedBeforeLongPress)) mBinding.control.action.speed.setText(mPlayers.setSpeed(mSpeedBeforeLongPress));
        mSpeedBeforeLongPress = Float.NaN;
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
    public void onFlingUp() {
        if (!mPlayers.isVod()) prevChannel();
    }

    @Override
    public void onFlingDown() {
        if (!mPlayers.isVod()) nextChannel();
    }

    @Override
    public void onFlingLeft() {
        if (!mPlayers.isVod()) prevLine();
    }

    @Override
    public void onFlingRight() {
        if (!mPlayers.isVod()) nextLine(true, true);
    }

    @Override
    public void onSeek(int time) {
        if (!mPlayers.isVod()) return;
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(mPlayers.stringToTime(mSeekGesture.preview(mPlayers.getPosition(), mPlayers.getDuration(), time)));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(int time) {
        mBinding.widget.seek.setVisibility(View.GONE);
        if (mPlaybackFlow.isBuffering()) showProgress();
        if (!mPlayers.isVod()) {
            mSeekGesture.reset();
            return;
        }
        if (time == 0) {
            mSeekGesture.reset();
            return;
        }
        mPlayers.seekTo(mSeekGesture.commit(mPlayers.getPosition(), mPlayers.getDuration(), time));
    }

    @Override
    public void onSeekCancel() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mSeekGesture.reset();
        if (mPlaybackFlow.isBuffering()) showProgress();
    }

    @Override
    public void onSingleTap() {
        onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isVisible(mBinding.recycler)) hideUI();
        else if (isVisible(mBinding.widget.epg)) hideEpg();
        else if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
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

    private void selectPlaybackPlayer(int item) {
        mPlayers.setPlayer(item);
        Setting.putLivePlayer(mPlayers.getPlayer());
        setPlayerView();
        setR1Callback();
        fetch();
    }

    @Override
    public void onPlayerShare(String title) {
        this.onShare(title);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isRedirect()) return;
        if (isLock()) mUiCallbacks.post(this::onLock, 500);
        if (mPlayers.haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, mPlayers.getVideoWidth(), mPlayers.getVideoHeight(), Setting.getLiveScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            PlaybackService.start(mPlayers);
            hideControl();
            hideInfo();
            hideUI();
        } else {
            hideInfo();
            mUiCallbacks.post(mR0, 1000);
            setForeground(true);
            if (isStop()) finish();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
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
    }

    @Override
    protected void onPause() {
        mKeyDown.release();
        mSeekGesture.reset();
        super.onPause();
        setForeground(false);
        mUiCallbacks.remove(mR0);
        if (isRedirect()) mPlaybackLifecycle.pause();
        else if (Setting.isBackgroundOn() && !isFinishing()) PlaybackService.start(mPlayers);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Setting.isBackgroundOff()) mPlaybackLifecycle.pause();
        if (Setting.isBackgroundOff()) mClock.stop();
        setStop(true);
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.info)) {
            hideInfo();
        } else if (isVisible(mBinding.widget.epg)) {
            hideEpg();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else if (!isLock()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (mLiveData != null) mLiveData.close();
        mUiCallbacks.close();
        mArtwork.close();
        super.onDestroy();
        mClock.release();
        if (mPlaybackFlow != null) mPlaybackFlow.close();
        mPlayers.release();
        PlaybackController previous = mPlayers;
        App.post(() -> PlaybackService.stop(previous), 1000);
        mUiCallbacks.remove(mR1, mR2, mR3);
    }

}
