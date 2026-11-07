package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.ArrayObjectAdapter;
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
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.repository.KeepRepository;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Track;
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
import com.fongmi.android.tv.ui.custom.LiveListSelection;
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
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyDownLive;
import com.fongmi.android.tv.ui.custom.CustomLiveListView;
import com.fongmi.android.tv.ui.dialog.LoudnessDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.PassDialog;
import com.fongmi.android.tv.ui.dialog.PlayerDialog;
import com.fongmi.android.tv.ui.dialog.VolumeDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.presenter.ChannelPresenter;
import com.fongmi.android.tv.ui.presenter.EpgDataPresenter;
import com.fongmi.android.tv.ui.presenter.GroupPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class LiveActivity extends BaseActivity implements Clock.Callback, GroupPresenter.OnClickListener, ChannelPresenter.OnClickListener, EpgDataPresenter.OnClickListener, CustomKeyDownLive.Listener, CustomLiveListView.Callback, TrackDialog.Listener, PlayerDialog.Listener, PassCallback, LiveCallback {

    private static String textOf(TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString();
    }

    private ActivityLiveBinding mBinding;
    private ArrayObjectAdapter mChannelAdapter;
    private ArrayObjectAdapter mEpgDataAdapter;
    private ArrayObjectAdapter mGroupAdapter;
    private CustomKeyDownLive mKeyDown;
    private LiveViewModel mViewModel;
    private boolean mHistoricalReplay;
    private LiveDataConnection mLiveData;
    private LiveChannelNavigator mNavigation;
    private LiveListLayout mListLayout;
    private DirectPlaybackFlow mPlaybackFlow;
    private DirectPlaybackLifecycle mPlaybackLifecycle;
    private final CallbackScope mUiCallbacks = new CallbackScope(AppTaskScheduler.get());
    private List<Group> mHides;
    private PlaybackController mPlayers;
    private Channel mChannel;
    private View mOldView;
    private Group mGroup;
    private Runnable mR0;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mSeekRunnable;
    private Clock mClock;
    private LiveListSelection mListSelection;
    private String mInfoLogoUrl;
    private final PlaybackSeekGesture mSeekGesture = new PlaybackSeekGesture();
    private int count;

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
        return (Group) mGroupAdapter.get(0);
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
    protected void initView() {
        mClock = Clock.create(Arrays.asList(mBinding.widget.clock, mBinding.display.clock));
        mKeyDown = CustomKeyDownLive.create(this);
        mPlayers = PlaybackController.create(this, this::onPlaybackError, this::onPlaybackCommand);
        setPlaybackFlow();
        mHides = new ArrayList<>();
        mR0 = this::setActivated;
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::hideInfo;
        mR4 = this::hideUI;
        // App prewarms the local proxy server. Keep this fallback off the UI
        // thread so a port bind never delays the first live frame.
        App.execute(() -> Server.get().start());
        setRecyclerView();
        setVideoView();
        setDisplayView();
        setViewModel();
        checkLive();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.group.setListener(this);
        mBinding.channel.setListener(this);
        mBinding.control.seek.setListener(mPlayers);
        mBinding.control.text.setOnClickListener(this::onTrack);
        mBinding.control.audio.setOnClickListener(this::onTrack);
        mBinding.control.video.setOnClickListener(this::onTrack);
        mBinding.control.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.speed.setDownListener(this::onSpeedSub);
        mBinding.control.text.setUpListener(this::onSubtitleUpDown);
        mBinding.control.text.setDownListener(this::onSubtitleUpDown);
        mBinding.control.home.setOnClickListener(view -> onHome());
        mBinding.control.line.setOnClickListener(view -> onLine());
        mBinding.control.scale.setOnClickListener(view -> onScale());
        mBinding.control.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.invert.setOnClickListener(view -> onInvert());
        mBinding.control.across.setOnClickListener(view -> onAcross());
        mBinding.control.change.setOnClickListener(view -> onChange());
        mBinding.control.player.setOnClickListener(view -> onPlayer());
        mBinding.control.decode.setOnClickListener(view -> onDecode());
        mBinding.control.volume.setOnClickListener(view -> VolumeDialog.create(this).show());
        mBinding.control.loudness.setOnClickListener(view -> LoudnessDialog.create(this).show());
        mBinding.control.player.setOnLongClickListener(view -> onChoose());
        mBinding.control.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.widget.error.setOnClickListener(view -> fetch());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.group.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (position < 0 || position >= mGroupAdapter.size()) return;
                onChildSelected(child, mGroup = (Group) mGroupAdapter.get(position));
            }
        });
    }

    private void setRecyclerView() {
        mListLayout = new LiveListLayout(mBinding.group, mBinding.channel, mBinding.widget.epgData, mBinding.divide, true);
        mNavigation = new LiveChannelNavigator(new LiveChannelNavigator.Port() {
            @Override public Group currentGroup() { return mGroup; }
            @Override public int groupCount() { return mGroupAdapter.size(); }
            @Override public int selectedGroup() { return mBinding.group.getSelectedPosition(); }
            @Override public Group groupAt(int index) { return (Group) mGroupAdapter.get(index); }
            @Override public void selectGroup(int index, Group group) {
                mGroup = group;
                mBinding.group.setSelectedPosition(index);
                setChannels(group);
            }
            @Override public void play(Channel channel) { setChannel(channel); }
        });

        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.widget.epgData.setItemAnimator(null);
        mBinding.group.setHasFixedSize(true);
        mBinding.channel.setHasFixedSize(true);
        mBinding.widget.epgData.setHasFixedSize(true);
        mBinding.group.setItemViewCacheSize(10);
        mBinding.channel.setItemViewCacheSize(10);
        mBinding.widget.epgData.setItemViewCacheSize(10);
        mBinding.group.setAdapter(new ItemBridgeAdapter(mGroupAdapter = new ArrayObjectAdapter(new GroupPresenter(this))));
        mBinding.channel.setAdapter(new ItemBridgeAdapter(mChannelAdapter = new ArrayObjectAdapter(new ChannelPresenter(this))));
        mBinding.widget.epgHistory.setOnClickListener(view -> showHistory());
        mBinding.widget.epgData.setAdapter(new ItemBridgeAdapter(mEpgDataAdapter = new ArrayObjectAdapter(new EpgDataPresenter(this))));
        mListSelection = new LiveListSelection(
                LiveListSelection.rows(mChannelAdapter, mBinding.channel),
                LiveListSelection.rows(mEpgDataAdapter, mBinding.widget.epgData));
    }

    private void setPlayerView() {
        PlaybackVideoViews.render(mPlayers, getExo(), getIjk(), mBinding.control.player, mBinding.control.speed);
        setTextIfChanged(mBinding.control.speed, mPlayers.getSpeedText());
    }

    private void setDecodeView() {
        setTextIfChanged(mBinding.control.decode, mPlayers.getDecodeText());
    }

    private void setVideoView() {
        mPlayers.init(getExo(), getIjk());
        setScale(Setting.getLiveScale());
        ExoUtil.setSubtitleView(mBinding.exo);
        IjkUtil.setSubtitleView(mBinding.ijk);
        mBinding.control.invert.setActivated(Setting.isInvert());
        mBinding.control.across.setActivated(Setting.isAcross());
        mBinding.control.change.setActivated(Setting.isChange());
        findViewById(R.id.timeBar).setNextFocusUpId(R.id.player);
        setVisibilityIfChanged(mBinding.control.home, LiveConfig.isOnly() ? View.GONE : View.VISIBLE);
    }

    private void setDisplayView() {
        mBinding.display.getRoot().setVisibility(View.VISIBLE);
        mBinding.display.progress.setVisibility(View.GONE);
        showDisplayInfo();
    }

    private void setTextIfChanged(TextView view, CharSequence text) {
        CharSequence current = view.getText();
        if (TextUtils.equals(current, text)) return;
        view.setText(text);
    }

    private void setVisibilityIfChanged(View view, int visibility) {
        if (view.getVisibility() == visibility) return;
        view.setVisibility(visibility);
    }

    private void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        setTextIfChanged(mBinding.control.scale, ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mLiveData = new LiveDataConnection(this, LiveDataConnection.source(mViewModel), false, new LiveDataConnection.Port() {
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
        mBinding.control.home.setText(getHome().getName());
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
        mGroupAdapter.setItems(items, null);
        setPosition(LiveConfig.get().find(items));
    }



    private Group setWidth(Group group) { return mListLayout.render(group); }

    private void setWidth(Epg epg) { mListLayout.render(epg); }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.size();
        if (size == 0 || position[0] >= size) return;
        mGroup = (Group) mGroupAdapter.get(position[0]);
        mBinding.group.setSelectedPosition(position[0]);
        int channelPosition = Math.max(0, Math.min(position[1], Math.max(0, mGroup.getChannel().size() - 1)));
        mGroup.setPosition(channelPosition);
        onItemClick(mGroup);
        onItemClick(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        if (position < 0) return;
        boolean change = mBinding.group.getSelectedPosition() != position;
        if (change) mBinding.group.setSelectedPosition(position);
        if (change) setChannels(mGroup);
        int channelPosition = Math.max(0, Math.min(mGroup.getPosition(), Math.max(0, mChannelAdapter.size() - 1)));
        mBinding.channel.setSelectedPosition(channelPosition);
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child, Group group) {
        if (mOldView != null) mOldView.setSelected(false);
        if (child == null) return;
        mOldView = child.itemView;
        mOldView.setSelected(true);
        onItemClick(group);
        resetPass();
    }

    private void setActivated() {
        mListSelection.activate(mChannel);
        fetch();
    }

    private void setActivated(EpgData item) {
        mListSelection.activate(item);
    }

    private void checkPlay() {
        if (mPlayers.isPlaying()) mPlayers.pause();
        else mPlayers.play();
    }

    private void onTrack(View view) {
        TrackDialog.create().player(mPlayers).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private boolean onSubtitleUpDown() {
        onSubtitleClick();
        return true;
    }

    private void onHome() {
        LiveDialog.create(this).show();
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onScale() {
        int index = Setting.getLiveScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        Setting.putLiveScale(index = index == array.length - 1 ? 0 : ++index);
        setScale(index);
    }

    private void onSpeed() {
        setTextIfChanged(mBinding.control.speed, mPlayers.addSpeed());
    }

    private boolean onSpeedAdd() {
        if (mPlayers.getSpeed() >= 5.0f) return false;
        setTextIfChanged(mBinding.control.speed, mPlayers.addSpeed(0.25f));
        return true;
    }

    private boolean onSpeedSub() {
        if (mPlayers.getSpeed() <= 0.2f) return false;
        setTextIfChanged(mBinding.control.speed, mPlayers.subSpeed(0.25f));
        return true;
    }

    private boolean onSpeedLong() {
        setTextIfChanged(mBinding.control.speed, mPlayers.toggleSpeed());
        return true;
    }

    private void onInvert() {
        Setting.putInvert(!Setting.isInvert());
        mBinding.control.invert.setActivated(Setting.isInvert());
    }

    private void onAcross() {
        Setting.putAcross(!Setting.isAcross());
        mBinding.control.across.setActivated(Setting.isAcross());
    }

    private void onChange() {
        Setting.putChange(!Setting.isChange());
        mBinding.control.change.setActivated(Setting.isChange());
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

    private void applyDecode(boolean save) {
        mPlayers.toggleDecode(save);
        mPlayers.init(getExo(), getIjk());
        mPlayers.setMediaSource();
        setDecodeView();
    }

    private void hideUI() {
        mUiCallbacks.remove(mR4);
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    @Override
    public void showUI() {
        if (isVisible(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.channel.requestFocus();
        setPosition();
        setUITimer();
        hideEpg();
    }

    @Override
    public void showEpg(Channel item) {
        if (mChannel == null || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        boolean history = hasHistory(mChannel);
        if (mChannel.getData().getList().isEmpty() && !history) return;
        setEpg();
        mBinding.widget.epgHistory.setVisibility(history ? View.VISIBLE : View.GONE);
        int position = mChannel.getData().getSelected();
        if (position >= 0) mBinding.widget.epgData.setSelectedPosition(position);
        mBinding.widget.epg.setVisibility(View.VISIBLE);
        if (position >= 0 || !history) mBinding.widget.epgData.requestFocus();
        else mBinding.widget.epgHistory.requestFocus();
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

    private void showProgress() {
        hideCenter();
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        startProgressPolling();
        hideError();
    }

    private void hideProgress() {
        mBinding.widget.progress.setVisibility(View.GONE);
        mUiCallbacks.remove(mR2);
    }

    private void showError(String text) {
        setVisibilityIfChanged(mBinding.widget.error, View.VISIBLE);
        setTextIfChanged(mBinding.widget.text, text);
        mBinding.widget.error.requestFocus();
        hideProgress();
    }

    private void hideError() {
        setVisibilityIfChanged(mBinding.widget.error, View.GONE);
        setTextIfChanged(mBinding.widget.text, "");
    }

    private void showControl(View view) {
        setVisibilityIfChanged(mBinding.control.getRoot(), View.VISIBLE);
        setVisibilityIfChanged(mBinding.widget.top, View.VISIBLE);
        mUiCallbacks.post(view::requestFocus, 25);
        view.requestFocus();
        setR1Callback();
        hideInfo();
        hideEpg();
    }

    private void hideControl() {
        setVisibilityIfChanged(mBinding.control.getRoot(), View.GONE);
        setVisibilityIfChanged(mBinding.widget.top, View.GONE);
        mUiCallbacks.remove(mR1);
    }

    private void showDisplayInfo() {
        showDisplayInfo(hasDialog());
    }

    private void showDisplayInfo(boolean hasDialog) {
        boolean controlVisible = isVisible(mBinding.control.getRoot());
        boolean visible = !controlVisible && !hasDialog;
        boolean showNetSpeed = Setting.isDisplaySpeed() && visible && !isVisible(mBinding.widget.progress);
        setVisibilityIfChanged(mBinding.display.clock, Setting.isDisplayTime() && visible ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.display.netspeed, showNetSpeed ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.display.duration, View.GONE);
        setVisibilityIfChanged(mBinding.display.titleLayout, Setting.isDisplayVideoTitle() && visible ? View.VISIBLE : View.GONE);
    }

    private boolean hasDialog() {
        for (Fragment f : getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return true;
        return false;
    }

    private void onTimeChangeDisplaySpeed() {
        boolean hasDialog = hasDialog();
        boolean controlVisible = isVisible(mBinding.control.getRoot());
        boolean visible = !controlVisible && !hasDialog && !isVisible(mBinding.widget.progress);
        if (Setting.isDisplaySpeed() && visible) Traffic.setSpeed(mBinding.display.netspeed);
        showDisplayInfo(hasDialog);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        setVisibilityIfChanged(mBinding.widget.center, View.GONE);
    }

    private void showInfo() {
        setVisibilityIfChanged(mBinding.widget.bottom, View.VISIBLE);
        setR3Callback();
        hideEpg();
        setInfo();
    }

    private void hideInfo() {
        setVisibilityIfChanged(mBinding.widget.bottom, View.GONE);
        mUiCallbacks.remove(mR3);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        mUiCallbacks.post(mR2, Constant.INTERVAL_TRAFFIC);
    }

    private void startProgressPolling() {
        mUiCallbacks.remove(mR2);
        mUiCallbacks.post(mR2, 0);
    }

    private void setR1Callback() {
        mUiCallbacks.remove(mR1);
        mUiCallbacks.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR3Callback() {
        mUiCallbacks.remove(mR3);
        mUiCallbacks.post(mR3, Constant.INTERVAL_HIDE);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else showUI();
        hideInfo();
    }

    private void resetPass() {
        this.count = 0;
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
        setChannels(item);
        int position = Math.max(0, Math.min(item.getPosition(), Math.max(0, mChannelAdapter.size() - 1)));
        mBinding.channel.setSelectedPosition(position);
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        PassDialog.create().show(this);
        App.removeCallbacks(mR4);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        if (item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)
                && (!mChannel.getData().getList().isEmpty() || hasHistory(mChannel))) {
            showEpg(item);
        } else {
            int position = Math.max(0, Math.min(mBinding.channel.getSelectedPosition(), Math.max(0, mChannelAdapter.size() - 1)));
            mGroup.setPosition(position);
            setChannel(item.group(mGroup));
            hideUI();
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
            mHistoricalReplay = historical;
            if (!historical) setActivated(item);
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
        if (mChannelAdapter.size() == 0) mBinding.group.requestFocus();
        getKeep().getChannel().remove(item);
        KeepRepository.get().delete(item.getName());
    }

    private void setChannel(Channel item) {
        mPlaybackFlow.beginSource();
        mPlayers.setPlayer(Setting.getLivePlayer());
        setArtwork(item.getLogo());
        mUiCallbacks.post(mR0, 100);
        mChannel = item;
        setPlayerView();
        showInfo();
    }

    private void setInfo() {
        mLiveData.refreshEpg();
        setTextIfChanged(mBinding.widget.play, "");
        String logo = mChannel.getLogo();
        if (!TextUtils.equals(mInfoLogoUrl, logo)) {
            mChannel.loadLogo(mBinding.widget.logo);
            mInfoLogoUrl = logo;
        }
        setTextIfChanged(mBinding.widget.name, mChannel.getName());
        setTextIfChanged(mBinding.widget.title, mChannel.getName());
        setTextIfChanged(mBinding.display.title, mChannel.getName());
        setTextIfChanged(mBinding.widget.line, mChannel.getLineText());
        setTextIfChanged(mBinding.widget.number, mChannel.getNumber());
        setTextIfChanged(mBinding.control.line, mChannel.getLineText());
        mBinding.widget.name.setMaxEms(mChannel.getName().length());
        setVisibilityIfChanged(mBinding.widget.line, mChannel.getLineVisible());
        setVisibilityIfChanged(mBinding.control.line, mChannel.getLineVisible());
    }

    private void setEpg() {
        String epg = mChannel.getData().getEpg();
        if (epg.length() > 0) mBinding.widget.name.setMaxEms(12);
        Epg data = mChannel.getData();
        mListSelection.showPrograms(data.getList());
        setTextIfChanged(mBinding.widget.play, epg);
        setWidth(data);
        setMetadata();
    }





    private void fetch() {
        if (mChannel == null) return;
        mHistoricalReplay = false;
        LiveConfig.get().setKeep(mChannel);
        mLiveData.loadChannel();
        mPlayers.clear();
        mPlayers.stop();
        showProgress();
    }

    private void resetAdapter() {
        mListLayout.reset();
        mListSelection.reset();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
        mArtwork.reset();
        mInfoLogoUrl = null;
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

    private void unlock(String pass) {
        boolean first = true;
        int position = mGroupAdapter.size();
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(mGroupAdapter.size(), item);
            if (first) mBinding.group.setSelectedPosition(position);
            if (first) onItemClick(mGroup = item);
            iterator.remove();
            first = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (!isCurrentPlayer()) return;
        if (ActionEvent.PLAY.equals(event.getAction())) {
            mPlayers.play();
        } else if (ActionEvent.PAUSE.equals(event.getAction())) {
            mPlayers.pause();
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
                break;
            case READY:

                setMetadata();
                hideProgress();
                mPlayers.reset();
                setTrackVisible(true);
                String sizeText = mPlayers.getSizeText();
                setTextIfChanged(mBinding.widget.size, sizeText);
                setTextIfChanged(mBinding.display.size, sizeText);
                break;
            case ENDED:
                break;
            default:
                break;
        }
    }

    private void setTrackVisible(boolean visible) {
        setVisibilityIfChanged(mBinding.control.text, visible && mPlayers.haveTrack(C.TRACK_TYPE_TEXT) ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.speed, visible && mPlayers.isVod() ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.volume, visible && mPlayers.isExo() ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.audio, visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        setVisibilityIfChanged(mBinding.control.video, visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    private void setMetadata() {
        if (mChannel == null) return;
        String title = textOf(mBinding.widget.name);
        String artist = textOf(mBinding.widget.play);
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
            @Override public void ended() { if (mChannel != null) nextEpg(); }
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

    private boolean isCurrentPlayer() {
        return mPlayers.isCurrent();
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
        if (!Setting.isChange() || mChannel == null) return;
        if (!mChannel.isLast()) {
            nextLine(true);
        } else {
            mChannel.setLine(0);
            nextChannel();
        }
    }

    private void prevChannel() { mNavigation.channel(false, Setting.isAcross()); }

    private void nextChannel() { mNavigation.channel(true, Setting.isAcross()); }

    public void nextEpg() {
        if (mHistoricalReplay) return;
        int position = mChannel.getData().getSelected() + 1;
        boolean limit = position > mEpgDataAdapter.size() - 1;
        if (!limit) onItemClick(mChannel.getData().getList().get(position));
        else nextChannel();
    }

    private void prevLine() {
        if (mChannel == null || mChannel.isOnly()) return;
        mChannel.prevLine();
        showInfo();
        fetch();
    }

    private void nextLine(boolean show) {
        if (mChannel == null || mChannel.isOnly()) return;
        mChannel.nextLine();
        if (show) showInfo();
        else setInfo();
        fetch();
    }

    private void seekTo(long position) {
        mPlayers.seekTo(position);
        mKeyDown.resetTime();
        hideCenter();
        if (mPlaybackFlow.isBuffering()) showProgress();
    }

    private void postSeekTo(int offset) {
        if (offset == 0) return;
        if (mSeekRunnable != null) mUiCallbacks.remove(mSeekRunnable);
        mSeekRunnable = () -> {
            seekTo(mSeekGesture.commit(mPlayers.getPosition(), mPlayers.getDuration(), offset));
            mSeekRunnable = null;
        };
        mUiCallbacks.post(mSeekRunnable, 250);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (mKeyDown.hasEvent(event) && dispatch(true)) {
            mKeyDown.onKeyDown(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void setUITimer() {
        mUiCallbacks.remove(mR4);
        mUiCallbacks.post(mR4, Constant.INTERVAL_HIDE);
    }

    @Override
    public boolean nextGroup(boolean skip) { return mNavigation.group(true, skip); }

    @Override
    public boolean prevGroup(boolean skip) { return mNavigation.group(false, skip); }

    @Override
    public boolean dispatch(boolean check) {
        return !check || isGone(mBinding.recycler) && isGone(mBinding.control.getRoot()) && isGone(mBinding.widget.epg);
    }

    @Override
    public void onShow(String number) {
        setTextIfChanged(mBinding.widget.digital, number);
        setVisibilityIfChanged(mBinding.widget.digital, View.VISIBLE);
    }

    @Override
    public void onFind(String number) {
        setVisibilityIfChanged(mBinding.widget.digital, View.GONE);
        setPosition(LiveConfig.get().find(number, mGroupAdapter.unmodifiableList()));
    }

    @Override
    public void onSeeking(int time) {
        if (!mPlayers.isVod()) return;
        if (mSeekRunnable != null) {
            mUiCallbacks.remove(mSeekRunnable);
            mSeekRunnable = null;
        }
        setTextIfChanged(mBinding.widget.exoDuration, mPlayers.getDurationTime());
        setTextIfChanged(mBinding.widget.exoPosition, mPlayers.stringToTime(mSeekGesture.preview(mPlayers.getPosition(), mPlayers.getDuration(), time)));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        setVisibilityIfChanged(mBinding.widget.center, View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onKeyUp() {
        prevChannel();
    }

    @Override
    public void onKeyDown() {
        nextChannel();
    }

    @Override
    public void onKeyLeft(int time) {
        if (!mPlayers.isVod()) prevLine();
        else postSeekTo(time);
    }

    @Override
    public void onKeyRight(int time) {
        if (!mPlayers.isVod()) nextLine(true);
        else postSeekTo(time);
    }

    @Override
    public void onKeyCenter() {
        hideInfo();
        showUI();
    }

    @Override
    public void onMenu() {
        showControl(mBinding.control.player);
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
        else onMenu();
    }

    @Override
    public void onPlayerClick(Integer item) {
        mPlaybackFlow.dispatch(PlaybackInput.player(item));
    }

    private void selectPlaybackPlayer(int item) {
        mPlayers.setPlayer(item);
        Setting.putLivePlayer(mPlayers.getPlayer());
        setPlayerView();
        fetch();
    }

    @Override
    public void onPlayerShare(String title) {
        this.onChoose();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPlaybackLifecycle.resume();
        mClock.start();
    }

    @Override
    protected void onPause() {
        mKeyDown.release();
        if (mSeekRunnable != null) mUiCallbacks.remove(mSeekRunnable);
        mSeekRunnable = null;
        mSeekGesture.reset();
        super.onPause();
        mPlaybackLifecycle.pause();
        mClock.stop();
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.bottom)) {
            hideInfo();
        } else if (isVisible(mBinding.widget.epg)) {
            hideEpg();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (mLiveData != null) mLiveData.close();
        if (mListSelection != null) mListSelection.close();
        mUiCallbacks.close();
        mArtwork.close();
        super.onDestroy();
        if (mPlaybackFlow != null) mPlaybackFlow.close();
        mPlayers.release();
        mKeyDown.release();
        mArtwork.reset();
        if (mSeekRunnable != null) mUiCallbacks.remove(mSeekRunnable);
        mSeekRunnable = null;
        mUiCallbacks.remove(mR0, mR1, mR2, mR3, mR4);
    }

    private void setChannels(Group group) {
        mListSelection.showGroup(setWidth(group));
    }
}
