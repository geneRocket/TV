package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.viewbinding.ViewBinding;

import com.android.cast.dlna.dmr.CastAction;
import com.android.cast.dlna.dmr.RenderControl;
import com.android.cast.dlna.dmr.RenderState;
import com.android.cast.dlna.dmr.service.RendererInterfaceKt;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.ui.player.CastRendererConnection;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.ActivityCastBinding;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.PlaybackCommand;
import com.fongmi.android.tv.player.IjkUtil;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.ui.custom.PlaybackVideoViews;
import com.fongmi.android.tv.ui.custom.PlaybackSubtitleLauncher;
import com.fongmi.android.tv.player.PlaybackSeekGesture;
import com.fongmi.android.tv.player.DirectPlaybackFlow;
import com.fongmi.android.tv.player.PlaybackInput;
import com.fongmi.android.tv.player.CastMediaTitle;
import com.fongmi.android.tv.utils.MainThreadExecutor;
import com.fongmi.android.tv.utils.CallbackScope;
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyDownCast;
import com.fongmi.android.tv.ui.dialog.PlayerDialog;
import com.fongmi.android.tv.ui.dialog.LoudnessDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.dialog.VolumeDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class CastActivity extends BaseActivity implements CustomKeyDownCast.Listener, TrackDialog.Listener, PlayerDialog.Listener, RenderControl, Clock.Callback {

    private static String textOf(TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString();
    }

    private ActivityCastBinding mBinding;
    private CastRendererConnection mRenderer;
    private CustomKeyDownCast mKeyDown;
    private RenderState mState = RenderState.IDLE;
    private CastAction mAction;
    private PlaybackController mPlayers;
    private DirectPlaybackFlow mPlaybackFlow;
    private final CallbackScope mUiCallbacks = new CallbackScope(AppTaskScheduler.get());
    private final PlaybackSeekGesture mSeekGesture = new PlaybackSeekGesture();
    private Runnable mR1;
    private Runnable mR2;
    private Clock mClock;
    private long position;
    private long duration;
    private int scale;

    private PlayerView getExo() {
        return mBinding.exo;
    }

    private IjkVideoView getIjk() {
        return mBinding.ijk;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCastBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyCastIntent(intent);
    }

    private void applyCastIntent(Intent intent) {
        if (!intent.hasExtra(RendererInterfaceKt.keyExtraCastAction)) return;
        mUiCallbacks.reset();
        getIntent().putExtras(intent);
        checkAction();
    }

    @Override
    protected void initView() {
        mRenderer = new CastRendererConnection(this, this);
        mRenderer.bind();
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownCast.create(this);
        mPlayers = PlaybackController.create(this, this::onPlaybackError, this::onPlaybackCommand);
        setPlaybackFlow();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        setVideoView();
        checkAction();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.control.seek.setListener(mPlayers);
        mBinding.control.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.speed.setDownListener(this::onSpeedSub);
        mBinding.control.text.setUpListener(this::onSubtitleUpDown);
        mBinding.control.text.setDownListener(this::onSubtitleUpDown);
        mBinding.control.text.setOnClickListener(this::onTrack);
        mBinding.control.audio.setOnClickListener(this::onTrack);
        mBinding.control.video.setOnClickListener(this::onTrack);
        mBinding.control.scale.setOnClickListener(view -> onScale());
        mBinding.control.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.reset.setOnClickListener(view -> onReset());
        mBinding.control.player.setOnClickListener(view -> onPlayer());
        mBinding.control.decode.setOnClickListener(view -> onDecode());
        mBinding.control.volume.setOnClickListener(view -> VolumeDialog.create(this).show());
        mBinding.control.loudness.setOnClickListener(view -> LoudnessDialog.create(this).show());
        mBinding.control.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
    }

    private String getName() {
        return CastMediaTitle.read(mAction.getCurrentURI(), mAction.getCurrentURIMetaData());
    }

    private void checkAction() {
        mAction = getIntent().getParcelableExtra(RendererInterfaceKt.keyExtraCastAction);
        if (mAction == null || TextUtils.isEmpty(mAction.getCurrentURI())) {
            finish();
            return;
        }
        mPlaybackFlow.beginSource();
        mBinding.widget.title.setText(getName());
        position = duration = 0;
        start();
    }

    private void start() {
        mPlayers.setMediaSource(mAction.getCurrentURI());
        showProgress();
        setMetadata();
        hideCenter();
    }

    private void setVideoView() {
        mPlayers.init(getExo(), getIjk());
        mPlayers.setPlayer(Setting.getPlayer());
        findViewById(R.id.timeBar).setNextFocusUpId(R.id.reset);
        mBinding.control.reset.setText(ResUtil.getStringArray(R.array.select_reset)[0]);
        setScale(scale = Setting.getScale());
        ExoUtil.setSubtitleView(mBinding.exo);
        IjkUtil.setSubtitleView(mBinding.ijk);
        setPlayerView();
        setDecodeView();
    }

    private void setPlayerView() {
        PlaybackVideoViews.render(mPlayers, getExo(), getIjk(), mBinding.control.player, mBinding.control.speed);
        mBinding.control.speed.setText(mPlayers.getSpeedText());
        mBinding.control.decode.setVisibility(mPlayers.isExo() ? View.VISIBLE : View.GONE);
    }

    private void setDecodeView() {
        mBinding.control.decode.setText(mPlayers.getDecodeText());
    }

    private void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        mBinding.control.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void onScale() {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        scale = scale == array.length - 1 ? 0 : ++scale;
        setScale(scale);
    }

    private void onSpeed() {
        mBinding.control.speed.setText(mPlayers.addSpeed());
    }

    private boolean onSpeedAdd() {
        if (mPlayers.getSpeed() >= 5.0f) return false;
        mBinding.control.speed.setText(mPlayers.addSpeed(0.25f));
        return true;
    }

    private boolean onSpeedSub() {
        if (mPlayers.getSpeed() <= 0.2f) return false;
        mBinding.control.speed.setText(mPlayers.subSpeed(0.25f));
        return true;
    }

    private boolean onSpeedLong() {
        mBinding.control.speed.setText(mPlayers.toggleSpeed());
        return true;
    }

    private boolean onSubtitleUpDown() {
        onSubtitleClick();
        return true;
    }

    private void onReset() {
        start();
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

    private void onTrack(View view) {
        TrackDialog.create().player(mPlayers).vod(true).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    private void showProgress() {
        hideInfo();
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        startProgressPolling();
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

    private void showInfo() {
        hideProgress();
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.info.setVisibility(View.VISIBLE);
    }

    private void hideInfo() {
        mBinding.widget.center.setVisibility(View.GONE);
        mBinding.widget.info.setVisibility(View.GONE);
    }

    private void showControl() {
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        mBinding.control.reset.requestFocus();
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.text.setText(R.string.play_track_text);
        mBinding.control.getRoot().setVisibility(View.GONE);
        mUiCallbacks.remove(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        hideInfo();
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        if (!mPlaybackFlow.isBuffering()) {
            hideProgress();
            return;
        }
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (!isCurrentPlayer()) return;
        if (ActionEvent.PLAY.equals(event.getAction())) {
            onPlay();
        } else if (ActionEvent.PAUSE.equals(event.getAction())) {
            onPaused();
        } else if (ActionEvent.STOP.equals(event.getAction())) {
            finish();
        }
    }

    private void onPlaybackCommand(PlaybackCommand command) {
        if (isCurrentPlayer() && command.getType() == PlaybackCommand.Type.SUBTITLE) mPlayers.setSub(Sub.from(command.getPath()));
    }

    private void onPlaybackState(PlaybackState state) {
        if (!isCurrentPlayer()) return;
        switch (state.getPhase()) {
            case PREPARING:
                setTrackVisible(false);
                mClock.setCallback(this);
                setState(RenderState.PREPARING);
                break;
            case IDLE:
            case STOPPED:
                setState(RenderState.IDLE);
                break;
            case BUFFERING:
                showProgress();
                setState(RenderState.PREPARING);
                break;
            case READY:
            case PAUSED:
                setMetadata();
                hideProgress();
                mPlayers.reset();
                setTrackVisible(true);
                setState(state.getPhase() == com.fongmi.android.tv.player.session.PlaybackPhase.READY ? RenderState.PLAYING : RenderState.PAUSED);
                mBinding.widget.size.setText(mPlayers.getSizeText());
                break;
            case ENDED:
                showControl();
                setState(RenderState.STOPPED);
                break;
            default:
                break;
        }
    }

    private void setTrackVisible(boolean visible) {
        mBinding.control.text.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.control.volume.setVisibility(visible && mPlayers.isExo() ? View.VISIBLE : View.GONE);
        mBinding.control.audio.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.video.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    private void setMetadata() {
        mPlayers.setMetadata(textOf(mBinding.widget.title), "", "");
    }

    private void setPlaybackFlow() {
        mPlaybackFlow = new DirectPlaybackFlow(mPlayers, new DirectPlaybackFlow.Port() {
            @Override public void render(PlaybackState state) { onPlaybackState(state); }
            @Override public void navigate(boolean next) { }
            @Override public void finish() { CastActivity.this.finish(); }
            @Override public void selectPlayer(int player) { selectPlaybackPlayer(player); }
            @Override public void decode(boolean persist) { applyDecode(persist); }
            @Override public boolean hasPlayableItem() { return mAction != null; }
            @Override public void retry() { onReset(); }
            @Override public void reloadExo(ErrorEvent error) { onExoCheck(error); }
            @Override public void switchPlayer() { mPlayers.nextPlayer(); setPlayerView(); onReset(); }
            @Override public void advance(ErrorEvent error) { onError(error); }
            @Override public void error(ErrorEvent error) { onError(error); }
            @Override public void ended() { }
            @Override public void userPlayback(boolean playing) { if (playing) hideCenter(); else presentPaused(); }
        }, MainThreadExecutor.get());
        mPlayers.setInputHandler(mPlaybackFlow);
    }

    private void onPlaybackError(ErrorEvent event) {
        mPlaybackFlow.onError(event);
    }

    private boolean isCurrentPlayer() {
        return mPlayers.isCurrent();
    }

    private void onExoCheck(ErrorEvent event) {
        if (event.getCode() == PlaybackException.ERROR_CODE_IO_UNSPECIFIED || event.getCode() >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED && event.getCode() <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) mPlayers.setFormat(ExoUtil.getMimeType(event.getCode()));
        mPlayers.setMediaSource();
    }

    private void onError(ErrorEvent event) {
        showError(event.getMsg());
        onStopped();
    }

    private void onPaused() {
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
    }

    private void presentPaused() {
        mBinding.widget.exoDuration.setText(mPlayers.getDurationTime());
        mBinding.widget.exoPosition.setText(mPlayers.getPositionTime(0));
        mBinding.widget.seekBar.setPosition(mPlayers.getPosition());
        mBinding.widget.seekBar.setDuration(mPlayers.getDuration());
        setState(RenderState.PAUSED);
        showInfo();
    }

    private void onPlay() {
        mPlaybackFlow.dispatch(PlaybackInput.action(PlaybackInput.Type.PLAY));
        hideCenter();
    }

    private void onStopped() {
        setState(RenderState.STOPPED);
        mPlayers.reset();
        mPlayers.stop();
    }

    private void setState(RenderState state) {
        mState = state;
        if (mRenderer != null) mRenderer.state(state);
    }

    @NonNull
    @Override
    public RenderState getState() {
        return mState;
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
        position = mPlayers.getPosition();
        duration = mPlayers.getDuration();
    }

    @Override
    public long getCurrentPosition() {
        return position;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public void seek(long time) {
        mUiCallbacks.post(() -> mPlayers.seekTo(time));
    }

    @Override
    public void pause() {
        mUiCallbacks.post(this::onPaused);
    }

    @Override
    public void play(@Nullable Double speed) {
        mUiCallbacks.post(this::onPlay);
    }

    @Override
    public void stop() {
        mUiCallbacks.post(this::finish);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) {
            onToggle();
            return true;
        }
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event)) return mKeyDown.onKeyDown(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onSeeking(int time) {
        long target = mSeekGesture.preview(mPlayers.getPosition(), mPlayers.getDuration(), time);
        mBinding.widget.exoDuration.setText(mPlayers.getDurationTime());
        mBinding.widget.exoPosition.setText(mPlayers.stringToTime(target));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.seekBar.setPosition(target);
        mBinding.widget.seekBar.setDuration(mPlayers.getDuration());
        hideProgress();
    }

    @Override
    public void onSeekTo(int time) {
        mKeyDown.resetTime();
        boolean paused = mState == RenderState.PAUSED;
        if (time == 0) {
            mSeekGesture.reset();
        } else {
            mPlayers.seekTo(mSeekGesture.commit(mPlayers.getPosition(), mPlayers.getDuration(), time));
        }
        if (paused) {
            mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
            showInfo();
        } else {
            hideCenter();
            if (mPlaybackFlow.isBuffering()) showProgress();
        }
    }

    @Override
    public void onSpeedUp() {
        if (!mPlayers.isPlaying() || !mPlayers.canAdjustSpeed()) return;
        mBinding.control.speed.setText(mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.widget.speed.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        mBinding.control.speed.setText(mPlayers.setSpeed(1.0f));
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
    }

    @Override
    public void onKeyUp() {
        showControl();
    }

    @Override
    public void onKeyDown() {
        showControl();
    }

    @Override
    public void onKeyCenter() {
        if (mPlayers.isPlaying()) onPaused();
        else onPlay();
        hideControl();
    }

    @Override
    public void onSingleTap() {
        onToggle();
    }

    @Override
    public void onDoubleTap() {
        onKeyCenter();
    }

    @Override
    public void onPlayerClick(Integer item) {
        mPlaybackFlow.dispatch(PlaybackInput.player(item));
    }

    private void selectPlaybackPlayer(int item) {
        mPlayers.setPlayer(item);
        setPlayerView();
        onReset();
    }

    @Override
    public void onPlayerShare(String title) {
        if (mPlayers.isEmpty()) return;
        mPlayers.choose(this, mBinding.widget.title.getText());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        mPlayers.activateAsCurrentPlayer();
        mPlaybackFlow.setPlaybackAllowed(true);
    }

    @Override
    protected void onPause() {
        mKeyDown.release();
        mSeekGesture.reset();
        super.onPause();
        mPlaybackFlow.setPlaybackAllowed(false);
        mClock.stop();
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        mUiCallbacks.close();
        if (mRenderer != null) mRenderer.close();
        super.onDestroy();
        mClock.release();
        if (mPlaybackFlow != null) mPlaybackFlow.close();
        mPlayers.release();
        mKeyDown.release();
        mUiCallbacks.remove(mR1, mR2);
    }
}
