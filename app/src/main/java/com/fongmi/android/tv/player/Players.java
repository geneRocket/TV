package com.fongmi.android.tv.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.impl.SessionCallback;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import java.lang.reflect.Method;
import java.util.*;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.ui.widget.DanmakuView;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class Players implements Player.Listener, IMediaPlayer.Listener, ParseCallback, DrawHandler.Callback {

    private static final String TAG = Players.class.getSimpleName();
    private static final long IJK_READY_FALLBACK_MS = 1000;

    public static final int SYS = 0;
    public static final int IJK = 1;
    public static final int EXO = 2;

    public static final int SOFT = 0;
    public static final int HARD = 1;

    private final StringBuilder builder;
    private final Formatter formatter;
    private final Runnable runnable;
    private final Runnable readyFallback;

    private Map<String, String> headers;
    private MediaSessionCompat session;
    private IjkVideoView ijkPlayer;
    private DanmakuView danmuView;
    private ExoPlayer exoPlayer;
    private ParseJob parseJob;
    private List<Danmaku> danmakus;
    private List<Sub> subs;
    private boolean danmuVisible;
    private Method danmuSetSpeed;
    private Method danmuSetSpeedFactor;
    private boolean danmuMethodResolved;
    private String format;
    private String url;
    private Drm drm;
    private Sub sub;
    private boolean forceLive;
    private boolean pendingReady;
    private int timeout;

    private long position;
    private int decode;
    private int count;
    private int player;
    private int playerState;
    private int retry;

    public static Players create(Activity activity) {
        Players player = new Players(activity);
        Server.get().setPlayer(player);
        return player;
    }

    public static boolean isExo(int type) {
        return type == EXO;
    }

    public static boolean isHard(int decode) {
        return decode == HARD;
    }

    public boolean isHard() {
        return decode == HARD;
    }

    public boolean isSoft() {
        return decode == SOFT;
    }

    public boolean isExo() {
        return player == EXO;
    }

    public boolean isIjk() {
        return player == SYS || player == IJK;
    }

    private Players(Activity activity) {
        player = Setting.getPlayer();
        decode = Setting.getDecode(player);
        builder = new StringBuilder();
        runnable = ErrorEvent::timeout;
        readyFallback = this::dispatchIjkReadyFallback;
        formatter = new Formatter(builder, Locale.getDefault());
        position = C.TIME_UNSET;
        playerState = Player.STATE_IDLE;
        timeout = Constant.TIMEOUT_PLAY;
        danmakus = new ArrayList<>();
        danmuVisible = Setting.isDanmu();
        createSession(activity);
    }

    private void createSession(Activity activity) {
        session = new MediaSessionCompat(activity, "TV");
        session.setCallback(SessionCallback.create(this));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(App.get(), 0, new Intent(App.get(), activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    public void init(PlayerView exo, IjkVideoView ijk) {
        releaseExo();
        releaseIjk();
        initExo(exo);
        initIjk(ijk);
    }

    private void initExo(PlayerView view) {
        view.setPlayer(null);
        // 2. 建议：尝试让 View 不可见再可见，强制触发 Surface 的某些重绘机制（针对顽固的绿屏设备）
        view.setVisibility(View.GONE);
        view.setVisibility(View.VISIBLE);
        exoPlayer = new ExoPlayer.Builder(App.get()).setLoadControl(ExoUtil.buildLoadControl()).setTrackSelector(ExoUtil.buildTrackSelector()).setRenderersFactory(ExoUtil.buildRenderersFactory(decode)).setMediaSourceFactory(ExoUtil.buildMediaSourceFactory()).build();
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, !Setting.isPlayWithOthers());
        exoPlayer.setHandleAudioBecomingNoisy(true);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        view.setPlayer(exoPlayer);
    }

    private void initIjk(IjkVideoView view) {
        ijkPlayer = view.render(Setting.getRender()).decode(decode);
        ijkPlayer.addListener(this);
        ijkPlayer.setPlayer(player);
    }

    public void setDanmuView(DanmakuView view) {
        view.setCallback(this);
        danmuView = view;
        danmuMethodResolved = false;
        danmuSetSpeed = null;
        danmuSetSpeedFactor = null;
        resolveDanmuSpeedMethod();
    }

    public ExoPlayer exo() {
        return exoPlayer;
    }

    public IjkVideoView ijk() {
        return ijkPlayer;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public void setSub(Sub sub) {
        this.sub = sub;
        if (TextUtils.isEmpty(url)) return;
        long current = getPosition();
        if (isIjk()) setPlayer(EXO);
        setPosition(current);
        setMediaSource();
    }

    public void setDanmakus(List<Danmaku> items) {
        danmakus = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public List<Danmaku> getDanmakus() {
        return danmakus == null ? new ArrayList<>() : new ArrayList<>(danmakus);
    }

    public Danmaku getDanmaku() {
        for (Danmaku item : getDanmakus()) if (item.isSelected()) return item;
        return danmakus.isEmpty() ? Danmaku.empty() : danmakus.get(0);
    }

    public void setDanmaku(Danmaku item) {
        if (item == null || item.isEmpty()) return;
        if (danmakus == null) danmakus = new ArrayList<>();
        boolean exists = false;
        for (Danmaku source : danmakus) {
            boolean selected = source.getUrl().equals(item.getUrl());
            source.setSelected(selected);
            exists |= selected;
        }
        if (!exists) {
            item.setSelected(true);
            danmakus.add(0, item);
        }
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setMetadata(MediaMetadataCompat metadata) {
        if (session != null) session.setMetadata(metadata);
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        if (this.player != player) reset();
        if (this.player != player) stop();
        this.player = player;
        this.decode = getDecode(player);
        if (ijkPlayer != null) ijkPlayer.setPlayer(player);
    }

    public int getDecode(int player) {
        return Setting.getDecode(player);
    }

    public void setDecode(int player, int decode) {
        Setting.putDecode(player, decode);
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public boolean canToggleDecode() {
        return isExo() && ++count <= 1;
    }

    public void reset() {
        position = C.TIME_UNSET;
        removeTimeoutCheck();
        removeReadyFallback();
        stopParse();
        count = 0;
        retry = 0;
        pendingReady = false;
    }

    public void clear() {
        headers = null;
        format = null;
        sub = null;
        subs = null;
        danmakus.clear();
        drm = null;
        url = null;
        forceLive = false;
        removeReadyFallback();
        pendingReady = false;
    }

    public int addRetry() {
        return ++retry;
    }

    public boolean exceedRetry(int retryLimit) {
        return addRetry() > retryLimit;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public int getVideoWidth() {
        if (isExo()) return exoPlayer != null ? exoPlayer.getVideoSize().width : 0;
        return ijkPlayer != null ? ijkPlayer.getVideoWidth() : 0;
    }

    public int getVideoHeight() {
        if (isExo()) return exoPlayer != null ? exoPlayer.getVideoSize().height : 0;
        return ijkPlayer != null ? ijkPlayer.getVideoHeight() : 0;
    }

    public float getSpeed() {
        if (isExo() && exoPlayer != null) return exoPlayer.getPlaybackParameters().speed;
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getSpeed();
        return 1.0f;
    }

    public long getPosition() {
        if (isExo() && exoPlayer != null) return exoPlayer.getCurrentPosition();
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getCurrentPosition();
        return 0;
    }

    public long getDuration() {
        if (isExo() && exoPlayer != null) return exoPlayer.getDuration();
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getDuration();
        return -1;
    }

    public long getBuffered() {
        if (isExo() && exoPlayer != null) return exoPlayer.getBufferedPosition();
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getBufferedPosition();
        return 0;
    }

    private boolean haveDanmu() {
        return danmuView != null && danmuView.isPrepared();
    }

    private void pauseDanmu() {
        if (haveDanmu()) danmuView.pause();
    }

    public boolean canAdjustSpeed() {
        return isIjk() || !Setting.isTunnel();
    }

    public boolean haveTrack(int type) {
        if (isExo() && exoPlayer != null) return ExoUtil.haveTrack(exoPlayer.getCurrentTracks(), type);
        if (isIjk() && ijkPlayer != null) return ijkPlayer.haveTrack(type);
        return false;
    }

    public boolean isPlaying() {
        return isExo() ? exoPlayer != null && exoPlayer.isPlaying() : ijkPlayer != null && ijkPlayer.isPlaying();
    }

    public boolean isBuffering() {
        return playerState == Player.STATE_BUFFERING;
    }

    public boolean isReady() {
        return playerState == Player.STATE_READY;
    }

    public boolean isEnd() {
        if (isExo() && exoPlayer != null) return exoPlayer.getPlaybackState() == Player.STATE_ENDED;
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getPlaybackState() == IjkVideoView.STATE_ENDED;
        return false;
    }

    public boolean isRelease() {
        return exoPlayer == null && ijkPlayer == null;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        if (forceLive) return true;
        long duration = getDuration();
        return duration == C.TIME_UNSET || duration < 0 || duration < 5 * 60 * 1000;
    }

    public boolean isVod() {
        if (forceLive) return false;
        return getDuration() > 5 * 60 * 1000;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public String getSizeText() {
        return getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_player)[player];
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        if (exoPlayer != null && !Setting.isTunnel()) exoPlayer.setPlaybackSpeed(speed);
        if (ijkPlayer != null) ijkPlayer.setSpeed(speed);
        applyDanmuSpeed();
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.2f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? 3f : 1f;
        return setSpeed(speed);
    }

    public void togglePlayer() {
        setPlayer(isExo() ? SYS : ++player);
    }

    public void nextPlayer() {
        setPlayer(isExo() ? IJK : EXO);
    }

    public void toggleDecode(boolean save) {
        decode = isHard() ? SOFT : HARD;
        if (save) setDecode(player, decode);
    }

    public String getPositionTime(long time) {
        time = getNewTime(time);
        return stringToTime(time);
    }

    public long getNewTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return time;
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        seekTo(getPosition() + time);
    }

    public void seekTo(long time) {
        if (haveDanmu()) danmuView.seekTo(time);
        if (isExo() && exoPlayer != null) exoPlayer.seekTo(time);
        if (isIjk() && ijkPlayer != null) ijkPlayer.seekTo(time);
    }

    public void play() {
        if (isPlaying() || isEnd()) return;
        Server.get().setPlayer(this);
        if (session != null) session.setActive(true);
        if (isExo()) playExo();
        if (isIjk()) playIjk();
        updateDanmuPlayingState();
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    public void pause() {
        if (isExo()) pauseExo();
        if (isIjk()) pauseIjk();
        if (session != null) session.setActive(false);
        pauseDanmu();
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void stop() {
        removeTimeoutCheck();
        removeReadyFallback();
        stopParse();
        if (isExo()) stopExo();
        if (isIjk()) stopIjk();
        if (session != null) session.setActive(false);
        if (haveDanmu()) danmuView.stop();
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
        setPlayerState(Player.STATE_IDLE);
    }

    public void release() {
        boolean current = Server.get().getPlayer() == this;
        stopParse();
        removeReadyFallback();
        if (session != null) {
            session.release();
            session = null;
        }
        releaseExo();
        releaseIjk();
        if (haveDanmu()) danmuView.release();
        removeTimeoutCheck();
        if (current) {
            Server.get().setPlayer(null);
            App.execute(() -> Path.clear(Path.exo()));
            App.execute(() -> Source.get().stop());
        }
    }

    public void releasePlayer() {
        stopParse();
        releaseExo();
        releaseIjk();
        removeTimeoutCheck();
        removeReadyFallback();
        pauseDanmu();
    }

    public void start(Channel channel, int timeout) {
        setPlayer(Setting.getLivePlayer());
        String url = getChannelUrl(channel);
        boolean forceLive = isLikelyLive(url, channel.getFormat());
        this.timeout = timeout;
        this.forceLive = forceLive;
        if (channel.hasMsg()) {
            ErrorEvent.extract(channel.getMsg());
        } else if (channel.getParse() == 1) {
            startParse(channel.result(), false);
        } else if (isIllegal(url)) {
            ErrorEvent.url(0);
        } else {
            setMediaSource(channel, timeout);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        this.timeout = timeout;
        this.forceLive = false;
        if (result.hasMsg()) {
            ErrorEvent.extract(result.getMsg());
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (isIllegal(result.getRealUrl())) {
            ErrorEvent.url(0);
        } else {
            setMediaSource(result, timeout);
        }
    }

    private void playExo() {
        if (exoPlayer == null) return;
        exoPlayer.play();
    }

    private void playIjk() {
        if (ijkPlayer == null) return;
        ijkPlayer.start();
    }

    private void pauseExo() {
        if (exoPlayer == null) return;
        exoPlayer.pause();
    }

    private void pauseIjk() {
        if (ijkPlayer == null) return;
        ijkPlayer.pause();
    }

    private void stopExo() {
        if (exoPlayer == null) return;
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
    }

    private void stopIjk() {
        if (ijkPlayer == null) return;
        ijkPlayer.stop();
    }

    private void releaseExo() {
        if (exoPlayer == null) return;
        exoPlayer.stop();
        exoPlayer.clearVideoSurface();
        exoPlayer.removeListener(this);
        exoPlayer.release();
        exoPlayer = null;
    }

    private void releaseIjk() {
        if (ijkPlayer == null) return;
        ijkPlayer.release();
        ijkPlayer = null;
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    public void setMediaSource() {
        if (TextUtils.isEmpty(url)) return;
        setMediaSource(headers, url, format, drm, subs, timeout, this.forceLive);
    }

    public void setMediaSource(String url) {
        setMediaSource(new HashMap<>(), url);
    }

    private void setMediaSource(Map<String, String> headers, String url) {
        setMediaSource(headers, url, null, null, new ArrayList<>(), Constant.TIMEOUT_PLAY);
    }

    private void setMediaSource(Channel channel, int timeout) {
        String url = getChannelUrl(channel);
        boolean forceLive = isLikelyLive(url, channel.getFormat());
        setMediaSource(channel.getHeaders(), url, channel.getFormat(), channel.getDrm(), new ArrayList<>(), timeout, forceLive);
    }

    private String getChannelUrl(Channel channel) {
        return TextUtils.isEmpty(channel.getUrl()) ? channel.getCurrent() : channel.getUrl();
    }

    private void setMediaSource(Result result, int timeout) {
        setMediaSource(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), timeout, false);
    }

    private void setMediaSource(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout) {
        setMediaSource(headers, url, format, drm, subs, timeout, false);
    }

    private void setMediaSource(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout, boolean forceLive) {
        stopParse();
        removeReadyFallback();
        this.headers = checkUa(mergeInlineHeaders(headers, url));
        this.url = UrlUtil.stripTag(UrlUtil.normalize(url, ""));
        this.format = format;
        this.drm = drm;
        this.forceLive = forceLive;
        this.subs = checkSub(subs);
        if (this.drm != null && isIjk()) setPlayer(EXO);
        // Wait for an actual render/play signal before reporting READY.
        this.pendingReady = true;
        if (isIjk() && ijkPlayer != null) ijkPlayer.setMediaSource(IjkUtil.getSource(this.headers, this.url), position);
        if (isExo() && exoPlayer != null) {
            MediaItem item = ExoUtil.getMediaItem(this.headers, UrlUtil.uri(this.url), this.format, this.drm, this.subs, decode, this.forceLive);
            exoPlayer.setMediaItem(item, position);
            exoPlayer.prepare();
        }
        removeTimeoutCheck();
        App.post(runnable, timeout);
        PlayerEvent.prepare();
        Logger.t(TAG).d(url);
    }

    private Map<String, String> mergeInlineHeaders(Map<String, String> headers, String url) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers != null) result.putAll(headers);
        result.putAll(UrlUtil.getTagHeaders(url));
        return result;
    }

    private void removeTimeoutCheck() {
        App.removeCallbacks(runnable);
    }

    private void removeReadyFallback() {
        App.removeCallbacks(readyFallback);
    }

    private void setPlayerState(int state) {
        if (playerState == state) return;
        playerState = state;
        PlayerEvent.state(state);
    }

    private void dispatchReadyState() {
        removeTimeoutCheck();
        removeReadyFallback();
        pendingReady = false;
        setPlayerState(Player.STATE_READY);
    }

    private void scheduleReadyFallback() {
        if (!isIjk()) return;
        App.post(readyFallback, IJK_READY_FALLBACK_MS);
    }

    private void dispatchIjkReadyFallback() {
        if (!pendingReady || !isIjk() || ijkPlayer == null) return;
        dispatchReadyState();
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) setTrack(track);
    }

    private void setTrack(Track item) {
        if (item.isExo(player)) setTrackExo(item);
        if (item.isIjk(player)) setTrackIjk(item);
    }

    private void setTrackExo(Track item) {
        if (item.isSelected()) {
            ExoUtil.selectTrack(exoPlayer, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(exoPlayer, item.getGroup(), item.getTrack());
        }
    }

    private void setTrackIjk(Track item) {
        if (item.isSelected()) {
            ijkPlayer.selectTrack(item.getType(), item.getTrack());
        } else {
            ijkPlayer.deselectTrack(item.getType(), item.getTrack());
        }
    }

    private void setPlaybackState(int state) {
        if (session == null) return;
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, getPosition(), getSpeed()).build());
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        if ("data".equals(scheme)) return false;
        return scheme.isEmpty() || "file".equals(scheme) ? !Path.exists(url) : host.isEmpty();
    }

    public static Map<String, String> checkUa(Map<String, String> headers) {
        if (headers == null) headers = new HashMap<>();
        else headers = new HashMap<>(headers);
        if (Setting.getUa().isEmpty()) return headers;
        for (Map.Entry<String, String> header : headers.entrySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(header.getKey())) return headers;
        headers.put(HttpHeaders.USER_AGENT, Setting.getUa());
        return headers;
    }

    private List<Sub> checkSub(List<Sub> subs) {
        if (subs == null) subs = new ArrayList<>();
        else subs = new ArrayList<>(subs);
        if (sub == null) return subs;
        
        // 检查是否已存在相同URL的字幕
        boolean exists = false;
        String subUrl = sub.getUrl();
        if (TextUtils.isEmpty(subUrl)) return subs;
        for (Sub existingSub : subs) {
            if (TextUtils.equals(existingSub.getUrl(), subUrl)) {
                exists = true;
                break;
            }
        }
        
        // 如果不存在相同URL的字幕，则添加新字幕
        // 这样可以保留历史字幕，实现新老字幕同时展示
        if (!exists) {
            subs.add(0, sub);
        }
        
        return subs;
    }

    public Uri getUri() {
        return getUrl().startsWith("file://") || getUrl().startsWith("/") ? FileUtil.getShareUri(getUrl()) : Uri.parse(getUrl());
    }

    public String[] getHeaderArray() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : getHeaders().entrySet()) list.addAll(Arrays.asList(entry.getKey(), entry.getValue()));
        return list.toArray(new String[0]);
    }

    public Bundle getHeaderBundle() {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : getHeaders().entrySet()) bundle.putString(entry.getKey(), entry.getValue());
        return bundle;
    }

    private MediaMetadataCompat.Builder putBitmap(MediaMetadataCompat.Builder builder, Drawable drawable) {
        try {
            return builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, ((BitmapDrawable) drawable).getBitmap());
        } catch (Exception ignored) {
            return builder;
        }
    }

    public void setMetadata(String title, String artist, String artUri, Drawable drawable) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        setMetadata(putBitmap(builder, drawable).build());
        ActionEvent.update();
    }

    public void share(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, UrlUtil.fixDownloadUrl(getUrl()));
            intent.putExtra("extra_headers", getHeaderBundle());
            intent.putExtra("title", title);
            intent.putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(Util.getChooser(intent));
        } catch (Exception e) {
            ThreadPools.log(e, "Share playback url failed.");
        }
    }

    public void choose(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(getUri(), "video/*");
            intent.putExtra("title", title);
            intent.putExtra("return_result", isVod());
            intent.putExtra("headers", getHeaderArray());
            if (isVod()) intent.putExtra("position", (int) getPosition());
            activity.startActivityForResult(Util.getChooser(intent), 1001);
        } catch (Exception e) {
            ThreadPools.log(e, "Open external player failed.");
        }
    }

    public void checkData(Intent data) {
        try {
            if (data == null || data.getExtras() == null) return;
            int position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) ActionEvent.next();
            if ("user".equals(endBy)) seekTo(position);
        } catch (Exception e) {
            ThreadPools.log(e, "Handle external player result failed.");
        }
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        setMediaSource(headers, url, format, drm, subs, timeout, forceLive);
    }

    @Override
    public void onParseError() {
        ErrorEvent.parse();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (!events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_PLAYER_ERROR)) return;
        switch (player.getPlaybackState()) {
            case Player.STATE_IDLE:
                setPlayerState(Player.STATE_IDLE);
                if (events.contains(Player.EVENT_PLAYER_ERROR)) pauseDanmu();
                setPlaybackState(events.contains(Player.EVENT_PLAYER_ERROR) ? PlaybackStateCompat.STATE_ERROR : PlaybackStateCompat.STATE_NONE);
                break;
            case Player.STATE_READY:
                if (!pendingReady || !isExo() || !player.getPlayWhenReady()) dispatchReadyState();
                updateLiveDecision();
                setPlaybackState(player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                updateDanmuPlayingState();
                break;
            case Player.STATE_BUFFERING:
                setPlayerState(Player.STATE_BUFFERING);
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                updateDanmuPlayingState();
                break;
            case Player.STATE_ENDED:
                setPlayerState(Player.STATE_ENDED);
                pauseDanmu();
                setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                break;
        }
    }

    private boolean isLikelyLive(String url, String format) {
        String value = url == null ? "" : url.toLowerCase(Locale.US);
        String type = format == null ? "" : format.toLowerCase(Locale.US);
        if (type.contains("m3u8") || type.contains("hls") || type.contains("mpd") || type.contains("dash")) return true;
        if (type.contains("mp4") || type.contains("mkv") || type.contains("mov") || type.contains("avi")) return false;
        if (value.contains(".m3u8") || value.contains(".mpd")) return true;
        if (value.contains("/live/") || value.contains("playlist") || value.contains("manifest")) return true;
        return !value.matches(".*\\.(mp4|mkv|avi|mov|wmv|m4v)(\\?.*)?$");
    }

    private void updateLiveDecision() {
        if (!isExo() || exoPlayer == null) return;
        forceLive = forceLive || exoPlayer.isCurrentMediaItemLive();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isPlaying) dispatchReadyState();
        updateDanmuPlayingState();
    }

    @Override
    public void onRenderedFirstFrame() {
        if (isExo() && pendingReady) dispatchReadyState();
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
        setPlaybackState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Logger.t(TAG).e(error.errorCode + "," + url);
        pauseDanmu();
        if (isPlaylistStuck(error)) {
            ErrorEvent.url(0, error.errorCode);
            return;
        }
        ErrorEvent.url(ExoUtil.getRetry(error.errorCode), error.errorCode);
    }

    private boolean isPlaylistStuck(PlaybackException error) {
        Throwable cause = error.getCause();
        while (cause != null) {
            if (cause.getClass().getName().contains("PlaylistStuckException")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public void onPlaybackStateChanged(int state) {
    }

    @Override
    public void onInfo(IMediaPlayer mp, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                setPlayerState(Player.STATE_BUFFERING);
                updateDanmuPlayingState();
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                if (!pendingReady) setPlayerState(Player.STATE_READY);
                updateDanmuPlayingState();
                break;
            case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_AUDIO_SEEK_RENDERING_START:
                dispatchReadyState();
                updateDanmuPlayingState();
                break;
        }
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        setPlaybackState(PlaybackStateCompat.STATE_ERROR);
        pauseDanmu();
        ErrorEvent.url(1);
        return true;
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        if (!pendingReady) setPlayerState(Player.STATE_READY);
        else scheduleReadyFallback();
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        setPlayerState(Player.STATE_ENDED);
        pauseDanmu();
    }

    @Override
    public void prepared() {
        App.post(() -> {
            if (danmuView == null) return;
            updateDanmuPlayingState();
        });
    }

    public void setDanmuVisible(boolean visible) {
        this.danmuVisible = visible;
        updateDanmuPlayingState();
    }

    private void updateDanmuPlayingState() {
        if (danmuView == null || !danmuView.isPrepared()) return;

        if (!danmuVisible) {
            danmuView.hide();
            danmuView.pause();
            return;
        }
        danmuView.show();
        applyDanmuSpeed();
        if (isPlaying() && !isBuffering()) {
            danmuView.start(getPosition());
        } else {
            danmuView.pause();
        }
    }

    public void applyDanmuSpeed() {
        if (danmuView == null) return;
        if (!danmuView.isPrepared()) return;
        if (!danmuMethodResolved) resolveDanmuSpeedMethod();
        float speed = getSpeed();
        try {
            if (danmuSetSpeed != null) {
                danmuSetSpeed.invoke(danmuView, speed);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            if (danmuSetSpeedFactor != null) danmuSetSpeedFactor.invoke(danmuView, speed);
        } catch (Exception ignored) {
        }
    }

    private void resolveDanmuSpeedMethod() {
        danmuMethodResolved = true;
        if (danmuView == null) return;
        try {
            danmuSetSpeed = danmuView.getClass().getMethod("setSpeed", float.class);
        } catch (Exception ignored) {
            danmuSetSpeed = null;
        }
        try {
            danmuSetSpeedFactor = danmuView.getClass().getMethod("setSpeedFactor", float.class);
        } catch (Exception ignored) {
            danmuSetSpeedFactor = null;
        }
    }

    @Override
    public void updateTimer(DanmakuTimer timer) {

    }

    @Override
    public void danmakuShown(BaseDanmaku danmaku) {
    }

    @Override
    public void drawingFinished() {
    }
}
