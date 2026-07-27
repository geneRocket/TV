package com.fongmi.android.tv.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
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
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.AdBlocker;
import com.fongmi.android.tv.utils.M3u8AdFilter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import java.util.*;
import java.util.regex.Pattern;

import master.flame.danmaku.ui.widget.DanmakuView;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class Players implements Player.Listener, IMediaPlayer.Listener, ParseCallback {

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
    private final ExternalPlayer externalPlayer;
    private final DanmakuController danmaku;
    private final MediaSessionController mediaSession;

    private IPlayer engine;
    private PlayerView exoView;
    private IjkVideoView ijkView;
    private Map<String, String> headers;
    private ParseJob parseJob;
    private List<Sub> subs;
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

    private static final Pattern PATTERN_VOD = Pattern.compile(".*\\.(mp4|mkv|avi|mov|wmv|m4v)(\\?.*)?$");

    public static Players create(Activity activity) {
        Players previous = Server.get().getPlayer();
        if (previous != null) previous.releasePlayer();
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
        externalPlayer = new ExternalPlayer(this);
        danmaku = new DanmakuController(Setting.isDanmu(), this::prepared);
        formatter = new Formatter(builder, Locale.getDefault());
        position = C.TIME_UNSET;
        playerState = Player.STATE_IDLE;
        timeout = Constant.TIMEOUT_PLAY;
        mediaSession = new MediaSessionController(activity, this);
    }

    public void init(PlayerView exo, IjkVideoView ijk) {
        this.exoView = exo;
        this.ijkView = ijk;
        setupEngine();
    }

    private void setupEngine() {
        releaseEngine();
        if (exoView == null || ijkView == null) return;
        if (isExo()) {
            exoView.setVisibility(View.VISIBLE);
            ijkView.setVisibility(View.GONE);
            try {
                engine = new ExoPlayerImpl(exoView, decode, this);
            } catch (RuntimeException e) {
                ThreadPools.log(e, "Exo initialization failed; falling back to IJK.");
                player = IJK;
                decode = getDecode(player);
                setupEngine();
            }
        } else {
            exoView.setVisibility(View.GONE);
            ijkView.setVisibility(View.VISIBLE);
            engine = new IjkPlayerImpl(ijkView, player, decode, this);
        }
    }

    private void releaseEngine() {
        if (engine == null) return;
        try {
            engine.release();
        } catch (RuntimeException e) {
            ThreadPools.log(e, "Player release failed.");
        } finally {
            engine = null;
        }
    }

    public void setDanmuView(DanmakuView view) {
        danmaku.setView(view);
    }

    public ExoPlayer exo() {
        return engine instanceof ExoPlayerImpl ? ((ExoPlayerImpl) engine).getPlayer() : null;
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
        if (isIjk()) setPlayer(EXO);
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
        danmaku.clearSources();
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
        return engine != null ? engine.getVideoWidth() : 0;
    }

    public int getVideoHeight() {
        return engine != null ? engine.getVideoHeight() : 0;
    }

    public float getSpeed() {
        return engine != null ? engine.getSpeed() : 1.0f;
    }

    public long getPosition() {
        return engine != null ? engine.getPosition() : 0;
    }

    public long getDuration() {
        return engine != null ? engine.getDuration() : -1;
    }

    public long getBuffered() {
        return engine != null ? engine.getBuffered() : 0;
    }

    public boolean haveTrack(int type) {
        if (isExo() && engine != null) return ExoUtil.haveTrack(((ExoPlayerImpl) engine).getPlayer().getCurrentTracks(), type);
        if (isIjk() && ijkView != null) return ijkView.haveTrack(type);
        return false;
    }

    public boolean isPlaying() {
        return engine != null && engine.isPlaying();
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
        if (isExo() && engine != null) return ((ExoPlayerImpl) engine).getPlayer().getPlaybackState() == Player.STATE_ENDED;
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
        if (engine != null) engine.setSpeed(speed);
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
        if (engine != null) engine.seekTo(time);
    }

    public void play() {
        if (isPlaying() || isEnd()) return;
        Server.get().setPlayer(this);
        mediaSession.setActive(true);
        if (engine != null) engine.play();
        updateDanmuPlayingState();
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    public void pause() {
        if (engine != null) engine.pause();
        mediaSession.setActive(false);
        pauseDanmu();
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void stop() {
        removeTimeoutCheck();
        removeReadyFallback();
        stopParse();
        if (engine != null) engine.stop();
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
        removeReadyFallback();
        mediaSession.release();
        releaseEngine();
        danmaku.release();
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
            failIllegalUrl();
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
        if (isIllegal(this.url)) {
            pendingReady = false;
            failIllegalUrl();
            return;
        }
        if (this.drm != null && isIjk()) setPlayer(EXO);
        // Wait for an actual render/play signal before reporting READY.
        this.pendingReady = true;
        try {
            if (engine != null) engine.setMediaSource(this.headers, this.url, this.format, this.drm, this.subs, position, this.forceLive);
        } catch (RuntimeException e) {
            pendingReady = false;
            removeTimeoutCheck();
            ThreadPools.log(e, "Player media source setup failed.");
            ErrorEvent.url(ExoUtil.getRetry(PlaybackException.ERROR_CODE_UNSPECIFIED), PlaybackException.ERROR_CODE_UNSPECIFIED);
            return;
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
        if (!pendingReady || !isIjk() || engine == null) return;
        dispatchReadyState();
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) {
            if (engine == null) return;
            if (track.isExo(player) || track.isIjk(player)) engine.setTrack(track);
        }
    }

    private void setPlaybackState(int state) {
        mediaSession.setPlaybackState(state, getPosition(), getSpeed());
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
        externalPlayer.share(activity, title);
    }

    public void choose(Activity activity, CharSequence title) {
        externalPlayer.choose(activity, title);
    }

    public void checkData(Intent data) {
        externalPlayer.checkResult(data);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (Setting.isRemoveAd() && AdBlocker.isAdUrl(url)) {
            ErrorEvent.parse();
            return;
        }
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
        if (isExo() && engine != null) {
            forceLive = forceLive || ((ExoPlayerImpl) engine).getPlayer().isCurrentMediaItemLive();
        }
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

    public void prepared() {
        App.post(this::updateDanmuPlayingState);
    }

    public void setDanmuVisible(boolean visible) {
        danmaku.setVisible(visible, isPlaying(), isBuffering(), getPosition(), getSpeed());
    }

    private void updateDanmuPlayingState() {
        danmaku.updatePlayingState(isPlaying(), isBuffering(), getPosition(), getSpeed());
    }

    public void applyDanmuSpeed() {
        danmaku.applySpeed(getSpeed());
    }
}
