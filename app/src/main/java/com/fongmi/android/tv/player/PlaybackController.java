package com.fongmi.android.tv.player;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.View;

import androidx.media3.common.C;
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
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.engine.EngineSnapshot;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.engine.IjkPlayerEngine;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.session.DefaultPlayerSession;
import com.fongmi.android.tv.player.session.PlaybackItem;
import com.fongmi.android.tv.player.session.PlaybackPhase;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.player.session.PlayerSession;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.player.resolver.PlaybackSourceResolver;
import com.fongmi.android.tv.player.resolver.ResolvedMedia;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.AdBlocker;
import com.fongmi.android.tv.utils.M3u8AdFilter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.orhanobut.logger.Logger;

import java.util.*;
import java.util.regex.Pattern;

import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.ui.widget.DanmakuView;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class PlaybackController {

    private static final String TAG = PlaybackController.class.getSimpleName();
    private final StringBuilder builder;
    private final Formatter formatter;
    private final Runnable runnable;
    private final ExternalPlayer externalPlayer;
    private final DanmakuController danmaku;
    private final MediaSessionController mediaSession;
    private final Set<com.fongmi.android.tv.player.session.PlaybackObserver> observers;

    private PlayerEngine engine;
    private PlayerSession playbackSession;
    private Subscription playbackSubscription;
    private PlayerView exoView;
    private IjkVideoView ijkView;
    private Map<String, String> headers;
    private final PlaybackSourceResolver sourceResolver;
    private List<Sub> subs;
    private String format;
    private String url;
    private Drm drm;
    private Sub sub;
    private boolean forceLive;
    private int timeout;
    private long activeSessionId;

    private long position;
    private int decode;
    private int player;
    private int playerState;

    private static final Pattern PATTERN_VOD = Pattern.compile(".*\\.(mp4|mkv|avi|mov|wmv|m4v)(\\?.*)?$");

    public static PlaybackController create(Activity activity) {
        PlaybackController previous = Server.get().getPlayer();
        if (previous != null) previous.releasePlayer();
        PlaybackController player = new PlaybackController(activity);
        Server.get().setPlayer(player);
        return player;
    }

    public boolean isHard() {
        return decode == DecodeMode.HARDWARE;
    }

    public boolean isExo() {
        return player == PlayerType.EXO;
    }

    public boolean isIjk() {
        return player == PlayerType.SYSTEM || player == PlayerType.IJK;
    }

    private PlaybackController(Activity activity) {
        player = Setting.getPlayer();
        decode = Setting.getDecode(player);
        builder = new StringBuilder();
        runnable = ErrorEvent::timeout;
        externalPlayer = new ExternalPlayer(this);
        danmaku = new DanmakuController(Setting.isDanmu(), this::prepared);
        formatter = new Formatter(builder, Locale.getDefault());
        position = C.TIME_UNSET;
        playerState = Player.STATE_IDLE;
        timeout = Constant.TIMEOUT_PLAY;
        mediaSession = new MediaSessionController(activity, this);
        observers = new LinkedHashSet<>();
        sourceResolver = new PlaybackSourceResolver();
    }

    public void init(PlayerView exo, IjkVideoView ijk) {
        this.exoView = exo;
        this.ijkView = ijk;
        setupEngine();
    }

    public PlayerSession session() {
        ensureSession();
        return playbackSession;
    }

    private void setupEngine() {
        releaseEngine();
        if (exoView == null || ijkView == null) return;
        if (isExo()) {
            exoView.setVisibility(View.VISIBLE);
            ijkView.setVisibility(View.GONE);
            try {
                engine = new ExoPlayerEngine(exoView, decode, Setting.isPlayWithOthers(), Setting.isTunnel());
            } catch (RuntimeException e) {
                ThreadPools.log(e, "Exo initialization failed; falling back to IJK.");
                player = PlayerType.IJK;
                decode = getDecode(player);
                setupEngine();
            }
        } else {
            exoView.setVisibility(View.GONE);
            ijkView.setVisibility(View.VISIBLE);
            engine = new IjkPlayerEngine(ijkView, player, Setting.getRender(), decode);
        }
        playbackSession = new DefaultPlayerSession(engine);
        playbackSubscription = playbackSession.observe(this::onPlaybackState);
    }

    private void releaseEngine() {
        if (playbackSession == null && engine == null) return;
        try {
            if (playbackSubscription != null) playbackSubscription.cancel();
            if (playbackSession != null) playbackSession.release();
            else engine.release();
        } catch (RuntimeException e) {
            ThreadPools.log(e, "Player release failed.");
        } finally {
            engine = null;
            playbackSession = null;
            playbackSubscription = null;
        }
    }

    public void setDanmuView(DanmakuView view) {
        danmaku.setView(view);
    }

    public void resetDanmu() {
        danmaku.reset();
    }

    public void prepareDanmu(BaseDanmakuParser parser, DanmakuContext context) {
        danmaku.prepare(parser, context);
    }

    public ExoPlayer exo() {
        return engine instanceof ExoPlayerEngine ? ((ExoPlayerEngine) engine).player() : null;
    }

    public IjkVideoView ijk() {
        return ijkView;
    }

    public MediaSessionCompat getSession() {
        return mediaSession.getSession();
    }

    public String getUrl() {
        return java.util.Objects.requireNonNullElse(url, "");
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public void setSub(Sub sub) {
        if (isSameSub(sub)) return;
        this.sub = sub;
        if (TextUtils.isEmpty(url)) return;
        long current = getPosition();
        if (isIjk()) setPlayer(PlayerType.EXO);
        setPosition(current);
        setMediaSource();
    }

    private boolean isSameSub(Sub target) {
        if (sub == null || target == null) return sub == target;
        return TextUtils.equals(sub.getUrl(), target.getUrl());
    }

    public void setDanmakus(List<Danmaku> items) {
        danmaku.setSources(items);
    }

    public List<Danmaku> getDanmakus() {
        return danmaku.getSources();
    }

    public Danmaku getDanmaku() {
        return danmaku.getSource();
    }

    public void setDanmaku(Danmaku item) {
        danmaku.select(item);
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void recoverExo(ErrorEvent error) {
        int code = error.getCode();
        if (code == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                || code >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                && code <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) {
            setFormat(ExoUtil.getMimeType(code));
        }
        setMediaSource();
    }

    public void setMetadata(MediaMetadataCompat metadata) {
        mediaSession.setMetadata(metadata);
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        boolean changed = this.player != player;
        if (changed) reset();
        if (changed) stop();
        this.player = player;
        this.decode = getDecode(player);
        if (changed || engine == null) setupEngine();
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

    public void setNextStartPosition(long startPosition) {
        position = Math.max(startPosition, 0);
    }

    private void ensureSession() {
        if (position == C.TIME_UNSET) position = 0;
    }

    public void reset() {
        position = C.TIME_UNSET;
        removeTimeoutCheck();
        stopParse();
    }

    public void clear() {
        headers = null;
        format = null;
        sub = null;
        subs = null;
        danmaku.clearSources();
        drm = null;
        url = null;
        forceLive = false;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public int getVideoWidth() {
        return snapshot().getVideoWidth();
    }

    public int getVideoHeight() {
        return snapshot().getVideoHeight();
    }

    public float getSpeed() {
        return snapshot().getSpeed();
    }

    public long getPosition() {
        return snapshot().getPosition();
    }

    public long getDuration() {
        return snapshot().getDuration();
    }

    public long getBuffered() {
        return snapshot().getBufferedPosition();
    }

    private EngineSnapshot snapshot() {
        return playbackSession == null ? EngineSnapshot.EMPTY : playbackSession.snapshot();
    }

    public boolean haveTrack(int type) {
        if (isExo() && exo() != null) return ExoUtil.haveTrack(exo().getCurrentTracks(), type);
        if (isIjk() && ijkView != null) return ijkView.haveTrack(type);
        return false;
    }

    public boolean isPlaying() {
        return snapshot().isPlaying();
    }

    private void pauseDanmu() {
        danmaku.pause();
    }

    public boolean canAdjustSpeed() {
        return isIjk() || !Setting.isTunnel();
    }

    public boolean isBuffering() {
        return playerState == Player.STATE_BUFFERING;
    }

    public boolean isReady() {
        return playerState == Player.STATE_READY;
    }

    public boolean isEnd() {
        if (isExo() && exo() != null) return exo().getPlaybackState() == Player.STATE_ENDED;
        if (isIjk() && ijkView != null) return ijkView.getPlaybackState() == IjkVideoView.STATE_ENDED;
        return false;
    }

    public boolean isRelease() {
        return engine == null;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        if (forceLive) return true;
        return getDuration() < 5 * 60 * 1000;
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
        if (playbackSession != null) playbackSession.setSpeed(speed);
        danmaku.applySpeed(getSpeed());
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

    public void nextPlayer() {
        setPlayer(isExo() ? PlayerType.IJK : PlayerType.EXO);
    }

    public void toggleDecode(boolean save) {
        decode = isHard() ? DecodeMode.SOFTWARE : DecodeMode.HARDWARE;
        if (save) setDecode(player, decode);
    }

    public String getPositionTime(long timeOffset) {
        return stringToTime(getNewTime(timeOffset));
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
        danmaku.seekTo(time);
        if (engine != null) {
            // Keep the published state in sync with seek-driven UI feedback. The engine's
            // subsequent READY callback will now be observable even after a smooth seek.
            setPlayerState(Player.STATE_BUFFERING);
            playbackSession.seekTo(time);
        }
    }

    public void play() {
        if (isPlaying()) return;
        if (isEnd()) seekTo(0);
        Server.get().setPlayer(this);
        mediaSession.setActive(true);
        if (playbackSession != null) playbackSession.play();
        updateDanmuPlayingState();
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    public void pause() {
        if (playbackSession != null) playbackSession.pause();
        mediaSession.setActive(false);
        pauseDanmu();
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void stop() {
        removeTimeoutCheck();
        stopParse();
        if (playbackSession != null) playbackSession.stop();
        mediaSession.setActive(false);
        danmaku.stop();
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
        setPlayerState(Player.STATE_IDLE);
    }

    private void failIllegalUrl() {
        stop();
        ErrorEvent.url(0);
    }

    public void release() {
        boolean current = Server.get().getPlayer() == this;
        stopParse();
        mediaSession.release();
        releaseEngine();
        danmaku.release();
        observers.clear();
        clear();
        removeTimeoutCheck();
        if (current) {
            M3u8AdFilter.clearSubtitlePlaylistWhitelist();
            Server.get().setPlayer(null);
            App.execute(() -> Source.get().stop());
        }
    }

    public void releasePlayer() {
        stopParse();
        releaseEngine();
        removeTimeoutCheck();
        pauseDanmu();
    }

    public void start(Channel channel, int timeout) {
        ensureSession();
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
            failIllegalUrl();
        } else {
            setMediaSource(channel, timeout);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        ensureSession();
        this.timeout = timeout;
        this.forceLive = false;
        if (result.hasMsg()) {
            ErrorEvent.extract(result.getMsg());
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (Setting.isRemoveAd() && AdBlocker.isAdUrl(result.getRealUrl())) {
            ErrorEvent.url(0);
        } else if (isIllegal(result.getRealUrl())) {
            failIllegalUrl();
        } else {
            setMediaSource(result, timeout);
        }
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        sourceResolver.resolve(result, useParse, forceLive, new PlaybackSourceResolver.Callback() {
            @Override
            public void onResolved(ResolvedMedia media) {
                if (Setting.isRemoveAd() && AdBlocker.isAdUrl(media.getUrl())) {
                    ErrorEvent.parse();
                    return;
                }
                if (!TextUtils.isEmpty(media.getSource())) Notify.show(ResUtil.getString(R.string.parse_from, media.getSource()));
                setMediaSource(media.getHeaders(), media.getUrl(), media.getFormat(), media.getDrm(), media.getSubtitles(), timeout, media.isForceLive());
            }

            @Override
            public void onFailure() {
                ErrorEvent.parse();
            }
        });
    }

    private void stopParse() {
        sourceResolver.cancel();
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
        this.headers = PlaybackHeaders.applyUserAgent(mergeInlineHeaders(headers, url));
        this.url = UrlUtil.stripTag(UrlUtil.normalize(url, ""));
        this.format = format;
        this.drm = drm;
        this.forceLive = forceLive;
        this.subs = checkSub(subs);
        if (isIllegal(this.url)) {
            failIllegalUrl();
            return;
        }
        if (this.drm != null && isIjk()) setPlayer(PlayerType.EXO);
        try {
            if (playbackSession == null) throw new IllegalStateException("player session is not initialized");
            long startPosition = position == C.TIME_UNSET ? 0 : Math.max(position, 0);
            position = C.TIME_UNSET;
            activeSessionId = playbackSession.prepare(new PlaybackItem(this.url, this.url, this.headers, this.format, this.drm, this.subs, startPosition, this.forceLive));
        } catch (RuntimeException e) {
            removeTimeoutCheck();
            ThreadPools.log(e, "Player media source setup failed.");
            ErrorEvent.url(ExoUtil.getRetry(PlaybackException.ERROR_CODE_UNSPECIFIED), PlaybackException.ERROR_CODE_UNSPECIFIED);
            return;
        }
        removeTimeoutCheck();
        App.post(runnable, timeout);
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

    private void setPlayerState(int state) {
        if (playerState == state) return;
        playerState = state;
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) {
            if (engine == null) return;
            if (track.isExo(player) || track.isIjk(player)) playbackSession.setTrack(track);
        }
    }

    private void setPlaybackState(int state) {
        mediaSession.setPlaybackState(state, getPosition(), getSpeed());
        ActionEvent.update();
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        switch (scheme) {
            case "data":
                return false;
            case "":
            case "file":
                return !com.github.catvod.utils.Path.exists(url);
            default:
                return host.isEmpty();
        }
    }

    private List<Sub> checkSub(List<Sub> subs) {
        if (subs == null) subs = new ArrayList<>();
        else subs = new ArrayList<>(subs);
        if (sub == null) return subs;

        // 检查是否已存在相同URL的字幕
        String subUrl = sub.getUrl();
        if (TextUtils.isEmpty(subUrl)) return subs;
        boolean exists = false;
        for (Sub existingSub : subs) {
            if (TextUtils.equals(existingSub.getUrl(), subUrl)) {
                exists = true;
                break;
            }
        }

        // 如果不存在相同URL的字幕，则添加新字幕
        if (!exists) {
            subs.add(0, sub);
        }

        return subs;
    }

    public void setMetadata(String title, String artist, String artUri) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        setMetadata(builder.build());
        ActionEvent.update();
    }

    public void share(Activity activity, CharSequence title) {
        externalPlayer.share(activity, title);
    }

    public void choose(Activity activity, CharSequence title) {
        externalPlayer.choose(activity, title);
    }

    public void checkData(Intent data) {
        externalPlayer.checkResult(data);
    }

    private void onPlaybackState(PlaybackState state) {
        if (state.getItem() != null) {
            PlaybackItem item = state.getItem();
            url = item.getUrl();
            headers = item.getHeaders();
            format = item.getFormat();
            drm = item.getDrm();
            subs = item.getSubtitles();
            forceLive = item.isForceLive();
        }
        if (state.getSessionId() != 0) activeSessionId = state.getSessionId();
        for (com.fongmi.android.tv.player.session.PlaybackObserver observer : new LinkedHashSet<>(observers)) {
            try {
                observer.onPlaybackState(state);
            } catch (RuntimeException error) {
                ThreadPools.log(error, "Playback observer failed.");
            }
        }
        PlaybackPhase phase = state.getPhase();
        switch (phase) {
            case IDLE:
            case STOPPED:
                setPlayerState(Player.STATE_IDLE);
                setPlaybackState(phase == PlaybackPhase.IDLE ? PlaybackStateCompat.STATE_NONE : PlaybackStateCompat.STATE_STOPPED);
                break;
            case PREPARING:
            case BUFFERING:
                setPlayerState(Player.STATE_BUFFERING);
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                updateDanmuPlayingState();
                break;
            case READY:
            case PAUSED:
                removeTimeoutCheck();
                setPlayerState(Player.STATE_READY);
                updateLiveDecision();
                setPlaybackState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                updateDanmuPlayingState();
                break;
            case ENDED:
                setPlayerState(Player.STATE_ENDED);
                pauseDanmu();
                setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                break;
            case ERROR:
                removeTimeoutCheck();
                setPlayerState(Player.STATE_IDLE);
                setPlaybackState(PlaybackStateCompat.STATE_ERROR);
                pauseDanmu();
                dispatchPlaybackError(state.getError());
                break;
            case RELEASED:
                break;
        }
    }

    public Subscription observe(com.fongmi.android.tv.player.session.PlaybackObserver observer) {
        if (observer == null) throw new IllegalArgumentException("observer must not be null");
        observers.add(observer);
        observer.onPlaybackState(playbackSession == null ? PlaybackState.IDLE : playbackSession.state());
        return () -> observers.remove(observer);
    }

    private boolean isLikelyLive(String url, String format) {
        if (url == null) return false;
        String value = url.toLowerCase(Locale.US);
        String type = format == null ? "" : format.toLowerCase(Locale.US);
        if (type.contains("m3u8") || type.contains("hls") || type.contains("mpd") || type.contains("dash")) return true;
        if (type.contains("mp4") || type.contains("mkv") || type.contains("mov") || type.contains("avi")) return false;
        if (value.contains(".m3u8") || value.contains(".mpd") || value.contains(".m3u")) return true;
        if (value.contains("/live/") || value.contains("playlist") || value.contains("manifest")) return true;
        return !PATTERN_VOD.matcher(value).matches();
    }

    private void updateLiveDecision() {
        if (exo() != null) forceLive = forceLive || exo().isCurrentMediaItemLive();
    }

    private void dispatchPlaybackError(Throwable throwable) {
        if (!(throwable instanceof PlaybackException)) {
            ErrorEvent.url(1);
            return;
        }
        PlaybackException error = (PlaybackException) throwable;
        Logger.t(TAG).e(error.errorCode + "," + url);
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

    public void prepared() {
        App.post(this::updateDanmuPlayingState);
    }

    public void setDanmuVisible(boolean visible) {
        danmaku.setVisible(visible, isPlaying(), isBuffering(), getPosition(), getSpeed());
    }

    private void updateDanmuPlayingState() {
        danmaku.updatePlayingState(isPlaying(), isBuffering(), getPosition(), getSpeed());
    }

}
